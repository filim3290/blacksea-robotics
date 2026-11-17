package com.example.notifications

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.BlackseaRepository
import com.example.data.shouldUserReceiveNotification
import com.example.model.TeamNotification
import com.example.model.UserStatus
import com.example.update.AppReleaseInfo
import com.example.update.AppUpdateManager
import com.example.update.UpdateCheckResult
import com.example.util.logDebugWarn
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

class BackgroundNotificationWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "BgNotifWorker"
        const val WORK_NAME = "bsr_background_notification_sync"
        const val ONE_TIME_WORK_NAME = "bsr_onetime_update_sync"
        private const val SCHEDULE_VERSION = 4

        fun schedulePeriodicSync(context: Context) {
            try {
                val appCtx = context.applicationContext
                val prefs = appCtx.getSharedPreferences("bsr_worker_prefs", Context.MODE_PRIVATE)
                val savedVer = prefs.getInt("worker_schedule_ver", 0)
                val policy = if (savedVer < SCHEDULE_VERSION) {
                    ExistingPeriodicWorkPolicy.UPDATE
                } else {
                    ExistingPeriodicWorkPolicy.KEEP
                }

                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val request = PeriodicWorkRequestBuilder<BackgroundNotificationWorker>(
                    15,
                    TimeUnit.MINUTES
                )
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .build()

                WorkManager.getInstance(appCtx).enqueueUniquePeriodicWork(
                    WORK_NAME,
                    policy,
                    request
                )
                if (savedVer < SCHEDULE_VERSION) {
                    prefs.edit().putInt("worker_schedule_ver", SCHEDULE_VERSION).apply()
                }
            } catch (e: Exception) {
                logDebugWarn(TAG, "Failed to schedule periodic notification sync", e)
            }
        }

        fun triggerImmediateSync(context: Context) {
            triggerOneTimeUpdateCheck(context, delaySeconds = 0L)
        }

        fun triggerOneTimeUpdateCheck(context: Context, delaySeconds: Long = 15L) {
            try {
                val appCtx = context.applicationContext
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
                val builder = OneTimeWorkRequestBuilder<BackgroundNotificationWorker>()
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                if (delaySeconds > 0L) {
                    builder.setInitialDelay(delaySeconds, TimeUnit.SECONDS)
                }

                WorkManager.getInstance(appCtx).enqueueUniqueWork(
                    ONE_TIME_WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    builder.build()
                )
            } catch (e: Exception) {
                logDebugWarn(TAG, "Failed to schedule one-time update check", e)
            }
        }

        fun cancelPeriodicSync(context: Context) {
            try {
                val wm = WorkManager.getInstance(context.applicationContext)
                wm.cancelUniqueWork(WORK_NAME)
                wm.cancelUniqueWork(ONE_TIME_WORK_NAME)
            } catch (e: Exception) {
                logDebugWarn(TAG, "Failed to cancel periodic notification sync", e)
            }
        }
    }

    override suspend fun doWork(): Result {
        val repository = BlackseaRepository(applicationContext)
        val updateManager = AppUpdateManager(applicationContext)
        var shouldRetry = false

        // 1. Sync Firestore notifications independently if signed in
        val currentUser = Firebase.auth.currentUser
        if (currentUser != null) {
            try {
                val profile = repository.getUserProfile(currentUser.uid)
                val isSuperAdmin = profile?.isSuperAdmin == true ||
                    currentUser.email.equals("filim3290@gmail.com", ignoreCase = true)

                if (profile != null && (UserStatus.isActive(profile.status) || isSuperAdmin)) {
                    val isManager = profile.isManagerAccount()
                    val joinMs = profile.createdAt?.toDate()?.time
                        ?: TeamNotificationDispatcher.getUserJoinTimestampMs(applicationContext, currentUser.uid)

                    val snapshot = try {
                        repository.db.collection("notifications").get(Source.SERVER).await()
                    } catch (_: Exception) {
                        repository.db.collection("notifications").get().await()
                    }
                    val allNotifications = snapshot.toObjects(TeamNotification::class.java)
                    val filtered = allNotifications.filter { notif ->
                        shouldUserReceiveNotification(
                            notif = notif,
                            userId = currentUser.uid,
                            isManager = isManager,
                            userTeam = profile.team,
                            userCreatedAtMs = joinMs
                        )
                    }

                    TeamNotificationDispatcher.processNotifications(
                        context = applicationContext,
                        userId = currentUser.uid,
                        notifications = filtered,
                        userCreatedAtMs = joinMs
                    )
                    RealtimeNotificationService.startIfEnabled(applicationContext)
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                logDebugWarn(TAG, "Background Firestore notification sync failed", e)
                shouldRetry = true
            }
        }

        // 2. Always check GitHub Releases & Firestore app_config/latest_update independently
        try {
            val releaseCheck = updateManager.checkLatestReleaseDetailed(forceRefresh = true)
            when (releaseCheck) {
                is UpdateCheckResult.UpdateAvailable -> {
                    TeamNotificationDispatcher.dispatchUpdateNotificationIfNeeded(
                        context = applicationContext,
                        updateManager = updateManager,
                        release = releaseCheck.release
                    )
                }
                is UpdateCheckResult.UpToDate -> {
                    if (currentUser != null) {
                        checkFirestoreLatestUpdate(repository, updateManager)
                    } else {
                        TeamNotificationDispatcher.clearPendingUpdateNotification(applicationContext)
                    }
                }
                is UpdateCheckResult.NoReleaseFound -> {
                    TeamNotificationDispatcher.clearPendingUpdateNotification(applicationContext)
                    if (currentUser != null) {
                        checkFirestoreLatestUpdate(repository, updateManager)
                    }
                }
                is UpdateCheckResult.ApkAssetMissing,
                is UpdateCheckResult.NetworkError,
                is UpdateCheckResult.ApiError -> {
                    if (currentUser != null) {
                        checkFirestoreLatestUpdate(repository, updateManager)
                    }
                }
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            logDebugWarn(TAG, "Background update check failed", e)
            shouldRetry = true
        }

        return if (shouldRetry && runAttemptCount < 2) Result.retry() else Result.success()
    }

    private suspend fun checkFirestoreLatestUpdate(
        repository: BlackseaRepository,
        updateManager: AppUpdateManager
    ) {
        try {
            val doc = repository.db.collection("app_config").document("latest_update").get().await()
            if (!doc.exists()) return
            val vCode = doc.getLong("versionCode")?.toInt() ?: 0
            val vName = doc.getString("versionName") ?: return
            if (updateManager.isUpdateAlreadyInstalled(vCode, vName)) {
                TeamNotificationDispatcher.clearPendingUpdateNotification(applicationContext)
                return
            }
            val resolvedUrl = updateManager.normalizeDownloadUrl(doc.getString("apkDownloadUrl"), vName)
            if (!resolvedUrl.startsWith("https://") || !resolvedUrl.endsWith(".apk", ignoreCase = true)) return

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
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            logDebugWarn(TAG, "Firestore background update check error", e)
        }
    }
}
