package com.example.notifications

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.BlackseaRepository
import com.example.data.shouldUserReceiveNotification
import com.example.model.TeamNotification
import com.example.model.UserProfile
import com.example.model.UserStatus
import com.example.update.AppReleaseInfo
import com.example.update.AppUpdateManager
import com.example.util.logDebugWarn
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Play-Policy-compliant `dataSync` foreground service that keeps live Firestore snapshot listeners
 * active so new team messages, ideas, broadcasts, member applications, and app updates fire OS
 * notifications at the exact moment the event occurs—even while the app UI is in background.
 */
class RealtimeNotificationService : Service() {

    companion object {
        private const val TAG = "RealtimeNotifService"
        private const val FOREGROUND_NOTIFICATION_ID = 1099

        fun startIfEligible(context: Context) {
            val appCtx = context.applicationContext
            if (!TeamNotificationDispatcher.isRealtimeSyncEnabled(appCtx)) return
            if (!TeamNotificationDispatcher.hasNotificationPermission(appCtx)) return
            if (Firebase.auth.currentUser == null) return

            val intent = Intent(appCtx, RealtimeNotificationService::class.java)
            try {
                ContextCompat.startForegroundService(appCtx, intent)
            } catch (e: Exception) {
                logDebugWarn(TAG, "Could not start RealtimeNotificationService from current state", e)
            }
        }

        fun stop(context: Context) {
            val appCtx = context.applicationContext
            try {
                appCtx.stopService(Intent(appCtx, RealtimeNotificationService::class.java))
            } catch (e: Exception) {
                logDebugWarn(TAG, "Failed to stop RealtimeNotificationService", e)
            }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var profileListener: ListenerRegistration? = null
    private var notificationsListener: ListenerRegistration? = null
    private var updateConfigListener: ListenerRegistration? = null
    private var periodicReleaseCheckJob: Job? = null

    @Volatile
    private var activeUid: String? = null

    @Volatile
    private var currentProfile: UserProfile? = null

    @Volatile
    private var initialNotifSnapshotHandled: Boolean = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundSafely()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundSafely()
        val user = Firebase.auth.currentUser
        if (user == null ||
            !TeamNotificationDispatcher.isRealtimeSyncEnabled(applicationContext) ||
            !TeamNotificationDispatcher.hasNotificationPermission(applicationContext)
        ) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (activeUid != user.uid || profileListener == null) {
            attachListeners(user.uid, user.email.orEmpty())
        }
        return START_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        logDebugWarn(TAG, "dataSync FGS timeout reached; falling back to WorkManager")
        BackgroundNotificationWorker.schedulePeriodicSync(applicationContext)
        stopSelf()
    }

    override fun onDestroy() {
        clearListeners()
        periodicReleaseCheckJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startForegroundSafely() {
        TeamNotificationDispatcher.ensureNotificationChannels(applicationContext)
        val notification = buildForegroundNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    FOREGROUND_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(FOREGROUND_NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            logDebugWarn(TAG, "startForeground failed", e)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val openIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            FOREGROUND_NOTIFICATION_ID,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(applicationContext, TeamNotificationDispatcher.CHANNEL_REALTIME_SYNC)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("BLACKSEA ROBOTICS")
            .setContentText("Anlık takım bildirimleri aktif")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun attachListeners(uid: String, email: String) {
        clearListeners()
        activeUid = uid
        initialNotifSnapshotHandled = TeamNotificationDispatcher.hasSeededBaseline(applicationContext, uid)

        val repository = BlackseaRepository(applicationContext)
        val updateManager = AppUpdateManager(applicationContext)

        profileListener = repository.db.collection("users").document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                if (snapshot == null || !snapshot.exists()) {
                    if (!email.equals("filim3290@gmail.com", ignoreCase = true)) {
                        stopSelf()
                    }
                    return@addSnapshotListener
                }
                val profile = snapshot.toObject(UserProfile::class.java)
                currentProfile = profile
                val isFounder = email.equals("filim3290@gmail.com", ignoreCase = true) ||
                    profile?.isSuperAdmin == true
                val isActive = profile != null && (UserStatus.isActive(profile.status) || isFounder)
                if (!isActive) {
                    stopSelf()
                }
            }

        notificationsListener = repository.db.collection("notifications")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val profile = currentProfile ?: return@addSnapshotListener
                val isFounder = email.equals("filim3290@gmail.com", ignoreCase = true) ||
                    profile.isSuperAdmin
                if (!UserStatus.isActive(profile.status) && !isFounder) return@addSnapshotListener

                val isManager = profile.isManagerAccount()
                val joinMs = profile.createdAt?.toDate()?.time
                    ?: TeamNotificationDispatcher.getUserJoinTimestampMs(applicationContext, uid)

                val allNotifications = snapshot.toObjects(TeamNotification::class.java)
                val filtered = allNotifications.filter { notif ->
                    shouldUserReceiveNotification(
                        notif = notif,
                        userId = uid,
                        isManager = isManager,
                        userTeam = profile.team,
                        userCreatedAtMs = joinMs
                    )
                }

                if (!initialNotifSnapshotHandled) {
                    TeamNotificationDispatcher.syncBaselineSilently(
                        context = applicationContext,
                        userId = uid,
                        notifications = filtered
                    )
                    initialNotifSnapshotHandled = true
                } else {
                    TeamNotificationDispatcher.processNotifications(
                        context = applicationContext,
                        userId = uid,
                        notifications = filtered,
                        userCreatedAtMs = joinMs
                    )
                }
            }

        updateConfigListener = repository.db.collection("app_config").document("latest_update")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
                val vCode = snapshot.getLong("versionCode")?.toInt() ?: return@addSnapshotListener
                if (vCode <= updateManager.getCurrentVersionCode()) return@addSnapshotListener
                val vName = snapshot.getString("versionName") ?: return@addSnapshotListener
                val resolvedUrl = updateManager.normalizeDownloadUrl(snapshot.getString("apkDownloadUrl"), vName)
                if (!resolvedUrl.startsWith("https://") || !resolvedUrl.endsWith(".apk", ignoreCase = true)) return@addSnapshotListener

                val release = AppReleaseInfo(
                    tagName = snapshot.getString("tagName") ?: "v$vName",
                    versionName = vName,
                    versionCode = vCode,
                    title = snapshot.getString("title") ?: "Yeni Güncelleme Mevcut",
                    changelog = snapshot.getString("changelog") ?: "• Yeni özellikler ve iyileştirmeler",
                    publishedAt = snapshot.getString("publishedAt") ?: "Bugün",
                    apkDownloadUrl = resolvedUrl,
                    apkFileName = snapshot.getString("apkFileName") ?: "BlackseaRobotics-v$vName.apk",
                    apkSize = snapshot.getLong("apkSize") ?: (20 * 1024 * 1024L),
                    expectedSha256 = snapshot.getString("expectedSha256") ?: "",
                    expectedCertSha256 = snapshot.getString("expectedCertSha256") ?: "",
                    isForceUpdate = snapshot.getBoolean("isForceUpdate") ?: false
                )
                TeamNotificationDispatcher.dispatchUpdateNotificationIfNeeded(
                    context = applicationContext,
                    updateManager = updateManager,
                    release = release
                )
            }

        periodicReleaseCheckJob?.cancel()
        periodicReleaseCheckJob = serviceScope.launch {
            while (isActive) {
                delay(15 * 60_000L)
                try {
                    val releaseCheck = updateManager.checkLatestReleaseDetailed(forceRefresh = true)
                    if (releaseCheck is com.example.update.UpdateCheckResult.UpdateAvailable && releaseCheck.isSignatureCompatible) {
                        TeamNotificationDispatcher.dispatchUpdateNotificationIfNeeded(
                            context = applicationContext,
                            updateManager = updateManager,
                            release = releaseCheck.release
                        )
                    }
                } catch (e: Exception) {
                    logDebugWarn(TAG, "Periodic update check in service failed", e)
                }
            }
        }
    }

    private fun clearListeners() {
        profileListener?.remove()
        profileListener = null
        notificationsListener?.remove()
        notificationsListener = null
        updateConfigListener?.remove()
        updateConfigListener = null
    }
}
