package com.example.notifications

import android.content.Context
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

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

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    @Suppress("UNUSED_PARAMETER")
    fun isContinuousSyncEnabled(context: Context): Boolean = true

    fun startIfEnabled(context: Context) {
        val appCtx = context.applicationContext
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
                            ) { profileState, memberships ->
                                profileState to memberships
                            }.collectLatest { (state, memberships) ->
                                when (state) {
                                    is UserProfileLoadState.Loading -> Unit
                                    is UserProfileLoadState.Missing -> {
                                        val isFounderEmail = Firebase.auth.currentUser?.email
                                            .equals(PLATFORM_FOUNDER_EMAIL, ignoreCase = true)
                                        if (!isFounderEmail) {
                                            stop(appCtx)
                                        }
                                    }
                                    is UserProfileLoadState.Found -> {
                                        val profile = state.profile
                                        val isPlatformAdmin = profile.isPlatformManagerAccount() ||
                                            Firebase.auth.currentUser?.email.equals(PLATFORM_FOUNDER_EMAIL, ignoreCase = true)

                                        val membershipsByTeam = memberships
                                            .filter { it.teamId.isNotBlank() }
                                            .associateBy { it.teamId }
                                            .toMutableMap()

                                        if (!membershipsByTeam.containsKey(PLATFORM_MAIN_TEAM_ID) &&
                                            (isPlatformAdmin || (profile.activeTeamId.isBlank() && UserStatus.isActive(profile.status) && profile.roles.any { it != "Takım Üyesi" }))
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
                                                isTeamAdmin = profile.isManagerAccount() || isPlatformAdmin
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

                                        if (!TeamNotificationDispatcher.hasSeededBaseline(appCtx, uid)) {
                                            val initialSnapshot = repository.fetchNotificationsOnceForUser(
                                                userId = uid,
                                                membershipsByTeam = membershipsByTeam,
                                                isPlatformAdmin = isPlatformAdmin,
                                                userCreatedAtMs = effectiveJoinMs
                                            )
                                            if (initialSnapshot != null) {
                                                TeamNotificationDispatcher.syncBaselineSilently(
                                                    context = appCtx,
                                                    userId = uid,
                                                    notifications = initialSnapshot
                                                )
                                            }
                                        }

                                        repository.observeNotificationsForUser(
                                            userId = uid,
                                            membershipsByTeam = membershipsByTeam,
                                            isPlatformAdmin = isPlatformAdmin,
                                            userCreatedAtMs = effectiveJoinMs
                                        ).collect { notifications ->
                                            TeamNotificationDispatcher.processNotifications(
                                                context = appCtx,
                                                userId = uid,
                                                notifications = notifications,
                                                userCreatedAtMs = effectiveJoinMs
                                            )
                                        }
                                    }
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
