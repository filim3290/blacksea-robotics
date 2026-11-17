package com.example.notifications

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.data.BlackseaRepository
import com.example.data.UserProfileLoadState
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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Persistent foreground listener service (`dataSync`) that keeps Firestore notification
 * and release listeners active continuously in the background so team chat messages,
 * announcements, ideas, files, support updates, and member events arrive immediately.
 */
class RealtimeNotificationService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var mainJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        promoteToForeground()
        _isRunning.value = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        promoteToForeground()

        if (!isContinuousSyncEnabled(this) || Firebase.auth.currentUser == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (mainJob?.isActive != true) {
            startRealtimeListeners()
        }
        return START_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        logDebugWarn(TAG, "dataSync foreground timeout reached; handing off to WorkManager")
        BackgroundNotificationWorker.triggerImmediateSync(applicationContext)
        stopSelf()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (isContinuousSyncEnabled(applicationContext) && Firebase.auth.currentUser != null) {
            BackgroundNotificationWorker.schedulePeriodicSync(applicationContext)
            BackgroundNotificationWorker.triggerOneTimeUpdateCheck(applicationContext, delaySeconds = 2L)
        }
    }

    override fun onDestroy() {
        _isRunning.value = false
        mainJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun promoteToForeground() {
        try {
            val notification = TeamNotificationDispatcher.buildRealtimeServiceNotification(this)
            val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            }
            ServiceCompat.startForeground(
                this,
                TeamNotificationDispatcher.BACKGROUND_SERVICE_NOTIFICATION_ID,
                notification,
                serviceType
            )
        } catch (e: Exception) {
            logDebugWarn(TAG, "Failed to promote RealtimeNotificationService to foreground", e)
            stopSelf()
        }
    }

    private fun startRealtimeListeners() {
        val repository = BlackseaRepository(applicationContext)
        val updateManager = AppUpdateManager(applicationContext)

        mainJob?.cancel()
        mainJob = serviceScope.launch {
            observeAuthUidFlow().collectLatest { uid ->
                if (uid.isNullOrBlank() || !isContinuousSyncEnabled(applicationContext)) {
                    stopSelf()
                    return@collectLatest
                }

                // Parallel listener for pushed updates while authenticated
                val updateJob = launch {
                    observeRemoteAppUpdates(repository, updateManager)
                }

                try {
                    repository.observeUserProfileState(uid).collectLatest { state ->
                        when (state) {
                            is UserProfileLoadState.Loading -> Unit
                            is UserProfileLoadState.Missing -> {
                                val isFounderEmail = Firebase.auth.currentUser?.email
                                    .equals("filim3290@gmail.com", ignoreCase = true)
                                if (!isFounderEmail) {
                                    stopSelf()
                                }
                            }
                            is UserProfileLoadState.Found -> {
                                val profile = state.profile
                                val currentEmail = Firebase.auth.currentUser?.email
                                val isFounder = profile.isSuperAdmin ||
                                    profile.email.equals("filim3290@gmail.com", ignoreCase = true) ||
                                    currentEmail.equals("filim3290@gmail.com", ignoreCase = true)
                                val isApproved = UserStatus.isActive(profile.status) || isFounder
                                if (!isApproved) {
                                    return@collectLatest
                                }

                                val isManager = profile.isManagerAccount()
                                val profileJoinMs = profile.createdAt?.toDate()?.time ?: 0L
                                if (profileJoinMs > 0L) {
                                    TeamNotificationDispatcher.recordUserJoinTimestampIfMissing(
                                        applicationContext,
                                        uid,
                                        profileJoinMs
                                    )
                                }
                                val effectiveJoinMs = if (profileJoinMs > 0L) {
                                    profileJoinMs
                                } else {
                                    TeamNotificationDispatcher.getUserJoinTimestampMs(applicationContext, uid)
                                }

                                if (!TeamNotificationDispatcher.hasSeededBaseline(applicationContext, uid)) {
                                    val initialSnapshot = repository.fetchNotificationsOnce(
                                        userId = uid,
                                        isManager = isManager,
                                        userTeam = profile.team,
                                        userCreatedAtMs = effectiveJoinMs
                                    )
                                    if (initialSnapshot != null) {
                                        TeamNotificationDispatcher.syncBaselineSilently(
                                            context = applicationContext,
                                            userId = uid,
                                            notifications = initialSnapshot
                                        )
                                    }
                                }

                                repository.observeNotifications(
                                    userId = uid,
                                    isManager = isManager,
                                    userTeam = profile.team,
                                    userCreatedAtMs = effectiveJoinMs
                                ).collect { notifications ->
                                    TeamNotificationDispatcher.processNotifications(
                                        context = applicationContext,
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
        }
    }

    private suspend fun observeRemoteAppUpdates(
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
                        TeamNotificationDispatcher.clearPendingUpdateNotification(applicationContext)
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
                            context = applicationContext,
                            updateManager = updateManager,
                            release = release
                        )
                    }
                }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            logDebugWarn(TAG, "Remote app update observation error in service", e)
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

    companion object {
        private const val TAG = "RealtimeNotifService"
        private const val PREFS_NAME = "bsr_service_prefs"
        private const val KEY_CONTINUOUS_SYNC_ENABLED = "continuous_bg_sync_enabled"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        fun isContinuousSyncEnabled(context: Context): Boolean {
            return context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_CONTINUOUS_SYNC_ENABLED, true)
        }

        fun setContinuousSyncEnabled(context: Context, enabled: Boolean) {
            val appCtx = context.applicationContext
            appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_CONTINUOUS_SYNC_ENABLED, enabled)
                .apply()
            if (enabled) {
                startIfEnabled(appCtx)
            } else {
                stop(appCtx)
            }
        }

        fun startIfEnabled(context: Context) {
            val appCtx = context.applicationContext
            if (!isContinuousSyncEnabled(appCtx)) return
            if (Firebase.auth.currentUser == null) return
            try {
                val intent = Intent(appCtx, RealtimeNotificationService::class.java)
                ContextCompat.startForegroundService(appCtx, intent)
            } catch (e: Exception) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    e is ForegroundServiceStartNotAllowedException
                ) {
                    logDebugWarn(TAG, "Foreground service start restricted from background; falling back to WorkManager", e)
                    BackgroundNotificationWorker.triggerImmediateSync(appCtx)
                } else {
                    logDebugWarn(TAG, "Could not start RealtimeNotificationService", e)
                }
            }
        }

        fun stop(context: Context) {
            val appCtx = context.applicationContext
            try {
                appCtx.stopService(Intent(appCtx, RealtimeNotificationService::class.java))
            } catch (e: Exception) {
                logDebugWarn(TAG, "Failed to stop RealtimeNotificationService", e)
            }
            _isRunning.value = false
        }
    }
}
