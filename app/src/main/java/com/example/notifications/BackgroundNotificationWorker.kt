package com.example.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
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

        fun schedulePeriodicSync(context: Context) {
            try {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val request = PeriodicWorkRequestBuilder<BackgroundNotificationWorker>(
                    15,
                    TimeUnit.MINUTES
                )
                    .setConstraints(constraints)
                    .build()

                WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
            } catch (e: Exception) {
                logDebugWarn(TAG, "Failed to schedule periodic notification sync", e)
            }
        }

        fun cancelPeriodicSync(context: Context) {
            try {
                WorkManager.getInstance(context.applicationContext).cancelUniqueWork(WORK_NAME)
            } catch (e: Exception) {
                logDebugWarn(TAG, "Failed to cancel periodic notification sync", e)
            }
        }
    }

    override suspend fun doWork(): Result {
        val currentUser = Firebase.auth.currentUser ?: return Result.success()
        val repository = BlackseaRepository(applicationContext)
        val updateManager = AppUpdateManager(applicationContext)

        return try {
            val profile = repository.getUserProfile(currentUser.uid)
            val isSuperAdmin = profile?.isSuperAdmin == true ||
                currentUser.email.equals("filim3290@gmail.com", ignoreCase = true)

            if (profile != null && (UserStatus.isActive(profile.status) || isSuperAdmin)) {
                val isManager = profile.isManagerAccount()
                val joinMs = profile.createdAt?.toDate()?.time
                    ?: TeamNotificationDispatcher.getUserJoinTimestampMs(applicationContext, currentUser.uid)

                val snapshot = repository.db.collection("notifications").get().await()
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
            }

            // Check GitHub Releases & Firestore app_config/latest_update for new app versions
            val releaseCheck = updateManager.checkLatestReleaseDetailed(forceRefresh = true)
            if (releaseCheck is UpdateCheckResult.UpdateAvailable && releaseCheck.isSignatureCompatible) {
                TeamNotificationDispatcher.dispatchUpdateNotificationIfNeeded(
                    context = applicationContext,
                    updateManager = updateManager,
                    release = releaseCheck.release
                )
            } else {
                checkFirestoreLatestUpdate(repository, updateManager)
            }

            Result.success()
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            logDebugWarn(TAG, "Background sync iteration failed", e)
            if (runAttemptCount < 2) Result.retry() else Result.success()
        }
    }

    private suspend fun checkFirestoreLatestUpdate(
        repository: BlackseaRepository,
        updateManager: AppUpdateManager
    ) {
        try {
            val doc = repository.db.collection("app_config").document("latest_update").get().await()
            if (!doc.exists()) return
            val vCode = doc.getLong("versionCode")?.toInt() ?: 0
            if (vCode <= updateManager.getCurrentVersionCode()) return

            val vName = doc.getString("versionName") ?: return
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
