package com.example.notifications

import android.content.Context
import android.util.Log
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
import com.example.model.UserRole
import com.example.model.UserStatus
import com.example.update.AppReleaseInfo
import com.example.update.AppUpdateManager
import com.example.update.UpdateCheckResult
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
                Log.w(TAG, "Failed to schedule periodic notification sync: ${e.message}")
            }
        }

        fun cancelPeriodicSync(context: Context) {
            try {
                WorkManager.getInstance(context.applicationContext).cancelUniqueWork(WORK_NAME)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to cancel periodic notification sync: ${e.message}")
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
                val isManager = isSuperAdmin ||
                    profile.role == UserRole.ADMIN.displayName ||
                    profile.roles.contains("Yönetici") ||
                    profile.roles.contains("Takım Kaptanı") ||
                    profile.roles.contains("Yetkili Öğretmen")

                val snapshot = repository.db.collection("notifications").get().await()
                val allNotifications = snapshot.toObjects(TeamNotification::class.java)
                val filtered = allNotifications.filter { notif ->
                    shouldUserReceiveNotification(
                        notif = notif,
                        userId = currentUser.uid,
                        isManager = isManager,
                        userTeam = profile.team
                    )
                }

                TeamNotificationDispatcher.processNotifications(
                    context = applicationContext,
                    userId = currentUser.uid,
                    notifications = filtered
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
            Log.w(TAG, "Background sync iteration failed: ${e.message}")
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
            Log.w(TAG, "Firestore background update check error: ${e.message}")
        }
    }
}
