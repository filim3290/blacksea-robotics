package com.example.notifications

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.example.data.BlackseaRepository
import com.example.data.UserProfileLoadState
import com.example.model.PLATFORM_FOUNDER_EMAIL
import com.example.model.PLATFORM_MAIN_TEAM_ID
import com.example.model.TeamMembership
import com.example.model.UserStatus
import com.example.update.AppReleaseInfo
import com.example.update.AppUpdateManager
import com.example.util.logDebugWarn
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Always-on process-level notification & update listener coordinator.
 * Runs silently in-process without starting an Android Foreground Service or showing
 * any persistent status-bar notification, paired with [BackgroundNotificationWorker]
 * for reliable background delivery when the app process is stopped.
 */
object RealtimeNotificationService {
    private const val TAG = "RealtimeNotifCoordinator"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var mainJob: Job? = null
    private val networkObserverRegistered = AtomicBoolean(false)

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private data class ListenerConfigKey(
        val uid: String,
        val isPlatformAdmin: Boolean,
        val activeTeamsSig: String,
        val joinMs: Long
    )

    private data class ListenerConfig(
        val key: ListenerConfigKey,
        val membershipsByTeam: Map<String, TeamMembership>,
        val stopSession: Boolean = false
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ListenerConfig) return false
            return key == other.key && stopSession == other.stopSession
        }

        override fun hashCode(): Int {
            var result = key.hashCode()
            result = 31 * result + stopSession.hashCode()
            return result
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun isContinuousSyncEnabled(context: Context): Boolean = true

    fun registerNetworkObserver(context: Context) {
        val appCtx = context.applicationContext
        if (!networkObserverRegistered.compareAndSet(false, true)) return
        try {
            val cm = appCtx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (cm == null) {
                networkObserverRegistered.set(false)
                return
            }
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    if (Firebase.auth.currentUser != null) {
                        startIfEnabled(appCtx)
                        BackgroundNotificationWorker.triggerImmediateSync(appCtx)
                    }
                }
            })
        } catch (e: Exception) {
            networkObserverRegistered.set(false)
            logDebugWarn(TAG, "Network observer registration skipped", e)
        }
    }

    fun startIfEnabled(context: Context) {
        val appCtx = context.applicationContext
        registerNetworkObserver(appCtx)
        if (Firebase.auth.currentUser == null) return
        synchronized(this) {
            if (mainJob?.isActive == true) {
                _isRunning.value = true
                return
            }
            _isRunning.value = true
            mainJob = scope.launch {
                val repository = BlackseaRepository(appCtx)
                val updateManager = AppUpdateManager(appCtx)
                try {
                    observeAuthUidFlow().collectLatest { uid ->
                        if (uid.isNullOrBlank()) {
                            _isRunning.value = false
                            return@collectLatest
                        }

                        val updateJob = launch {
                            observeRemoteAppUpdates(appCtx, repository, updateManager)
                        }

                        try {
                            combine(
                                repository.observeUserProfileState(uid),
                                repository.observeMyMemberships(uid)
                            ) { state, memberships ->
                                when (state) {
                                    is UserProfileLoadState.Loading -> null
                                    is UserProfileLoadState.Missing -> {
                                        val isFounderEmail = Firebase.auth.currentUser?.email
                                            ?.trim()
                                            .equals(PLATFORM_FOUNDER_EMAIL, ignoreCase = true)
                                        if (!isFounderEmail) {
                                            ListenerConfig(
                                                key = ListenerConfigKey(uid, false, "", 0L),
                                                membershipsByTeam = emptyMap(),
                                                stopSession = true
                                            )
                                        } else {
                                            null
                                        }
                                    }
                                    is UserProfileLoadState.Found -> {
                                        val profile = state.profile
                                        val isPlatformAdmin = profile.isPlatformManagerAccount() ||
                                            Firebase.auth.currentUser?.email
                                                ?.trim()
                                                .equals(PLATFORM_FOUNDER_EMAIL, ignoreCase = true)

                                        val membershipsByTeam = memberships
                                            .filter { it.teamId.isNotBlank() }
                                            .associateBy { it.teamId }
                                            .toMutableMap()

                                        if (!isPlatformAdmin &&
                                            !membershipsByTeam.containsKey(PLATFORM_MAIN_TEAM_ID) &&
                                            profile.activeTeamId.isBlank() &&
                                            UserStatus.isActive(profile.status) &&
                                            profile.roles.any { it != "Takım Üyesi" && !it.equals("Yönetici", ignoreCase = true) }
                                        ) {
                                            membershipsByTeam[PLATFORM_MAIN_TEAM_ID] = TeamMembership(
                                                id = "${PLATFORM_MAIN_TEAM_ID}_$uid",
                                                teamId = PLATFORM_MAIN_TEAM_ID,
                                                userId = uid,
                                                userName = profile.getFullName(),
                                                userEmail = profile.email,
                                                subTeam = profile.team,
                                                role = profile.role,
                                                roles = profile.roles,
                                                status = UserStatus.ACTIVE.value,
                                                isTeamAdmin = profile.isManagerAccount()
                                            )
                                        }

                                        val profileJoinMs = profile.createdAt?.toDate()?.time ?: 0L
                                        if (profileJoinMs > 0L) {
                                            TeamNotificationDispatcher.recordUserJoinTimestampIfMissing(
                                                appCtx,
                                                uid,
                                                profileJoinMs
                                            )
                                        }
                                        val effectiveJoinMs = if (profileJoinMs > 0L) {
                                            profileJoinMs
                                        } else {
                                            TeamNotificationDispatcher.getUserJoinTimestampMs(appCtx, uid)
                                        }

                                        val activeSig = membershipsByTeam.values
                                            .filter { it.isActive() }
                                            .sortedBy { it.teamId }
                                            .joinToString("|") { m ->
                                                "${m.teamId}:${m.subTeam}:${m.isManagerInTeam()}:${m.role}:${m.roles.joinToString(",")}"
                                            }

                                        ListenerConfig(
                                            key = ListenerConfigKey(
                                                uid = uid,
                                                isPlatformAdmin = isPlatformAdmin,
                                                activeTeamsSig = activeSig,
                                                joinMs = effectiveJoinMs
                                            ),
                                            membershipsByTeam = membershipsByTeam.toMap(),
                                            stopSession = false
                                        )
                                    }
                                }
                            }
                                .distinctUntilChanged()
                                .collectLatest { config ->
                                    if (config == null) return@collectLatest
                                    if (config.stopSession) {
                                        stop(appCtx)
                                        return@collectLatest
                                    }

                                    val key = config.key
                                    if (!TeamNotificationDispatcher.hasSeededBaseline(appCtx, key.uid)) {
                                        var initialSnapshot: List<com.example.model.TeamNotification>? = null
                                        for (attempt in 0 until 2) {
                                            initialSnapshot = repository.fetchNotificationsOnceForUser(
                                                userId = key.uid,
                                                membershipsByTeam = config.membershipsByTeam,
                                                isPlatformAdmin = key.isPlatformAdmin,
                                                userCreatedAtMs = key.joinMs
                                            )
                                            if (initialSnapshot != null) break
                                            delay(600L)
                                        }
                                        if (initialSnapshot != null) {
                                            TeamNotificationDispatcher.syncBaselineSilently(
                                                context = appCtx,
                                                userId = key.uid,
                                                notifications = initialSnapshot
                                            )
                                        }
                                    }

                                    repository.observeNotificationsForUser(
                                        userId = key.uid,
                                        membershipsByTeam = config.membershipsByTeam,
                                        isPlatformAdmin = key.isPlatformAdmin,
                                        userCreatedAtMs = key.joinMs
                                    ).collect { notifications ->
                                        TeamNotificationDispatcher.processNotifications(
                                            context = appCtx,
                                            userId = key.uid,
                                            notifications = notifications,
                                            userCreatedAtMs = key.joinMs
                                        )
                                    }
                                }
                        } catch (ce: CancellationException) {
                            throw ce
                        } catch (e: Exception) {
                            logDebugWarn(TAG, "RealtimeNotificationService stream error", e)
                        } finally {
                            updateJob.cancel()
                        }
                    }
                } catch (ce: CancellationException) {
                    throw ce
                } finally {
                    _isRunning.value = false
                }
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun stop(context: Context) {
        synchronized(this) {
            mainJob?.cancel()
            mainJob = null
            _isRunning.value = false
        }
    }

    private suspend fun observeRemoteAppUpdates(
        appCtx: Context,
        repository: BlackseaRepository,
        updateManager: AppUpdateManager
    ) {
        try {
            var isInitialEmission = true
            repository.db.collection("app_config").document("latest_update")
                .snapshots()
                .collect { doc ->
                    val shouldNotify = !isInitialEmission
                    isInitialEmission = false
                    if (!doc.exists()) return@collect

                    val vCode = doc.getLong("versionCode")?.toInt() ?: 0
                    val vName = doc.getString("versionName") ?: return@collect
                    if (updateManager.isUpdateAlreadyInstalled(vCode, vName)) {
                        TeamNotificationDispatcher.clearPendingUpdateNotification(appCtx)
                        return@collect
                    }

                    val resolvedUrl = updateManager.normalizeDownloadUrl(doc.getString("apkDownloadUrl"), vName)
                    if (!resolvedUrl.startsWith("https://") || !resolvedUrl.endsWith(".apk", ignoreCase = true)) {
                        return@collect
                    }

                    if (shouldNotify) {
                        val release = AppReleaseInfo(
                            tagName = doc.getString("tagName") ?: "v$vName",
                            versionName = vName,
                            versionCode = vCode,
                            title = doc.getString("title") ?: "Yeni Güncelleme Mevcut",
                            changelog = doc.getString("changelog") ?: "• Performans iyileştirmeleri ve hata düzeltmeleri",
                            publishedAt = doc.getString("publishedAt") ?: "Bugün",
                            apkDownloadUrl = resolvedUrl,
                            apkFileName = doc.getString("apkFileName") ?: "BlackseaRobotics-v$vName.apk",
                            apkSize = doc.getLong("apkSize") ?: (20 * 1024 * 1024L),
                            expectedSha256 = doc.getString("expectedSha256") ?: "",
                            expectedCertSha256 = doc.getString("expectedCertSha256") ?: "",
                            isForceUpdate = doc.getBoolean("isForceUpdate") ?: false
                        )
                        TeamNotificationDispatcher.dispatchUpdateNotificationIfNeeded(
                            context = appCtx,
                            updateManager = updateManager,
                            release = release
                        )
                    }
                }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            logDebugWarn(TAG, "Remote app update observation error", e)
        }
    }

    private fun observeAuthUidFlow() = callbackFlow<String?> {
        trySend(Firebase.auth.currentUser?.uid)
        val listener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser?.uid)
        }
        Firebase.auth.addAuthStateListener(listener)
        awaitClose { Firebase.auth.removeAuthStateListener(listener) }
    }
}
