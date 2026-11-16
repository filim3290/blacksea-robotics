package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.model.TeamNotification
import com.example.update.AppReleaseInfo
import com.example.update.AppUpdateManager
import com.example.update.UpdateCheckResult
import com.example.util.logDebugWarn
import org.json.JSONArray

data class DispatchSelection(
    val shouldSeedBaseline: Boolean,
    val seedIds: List<String>,
    val toDispatch: List<TeamNotification>,
    val newBaselineTimestampMs: Long
)

object TeamNotificationDispatcher {

    private const val TAG = "TeamNotifDispatcher"
    private const val PREFS_NAME = "bsr_notif_dispatch_prefs"
    private const val MAX_STORED_IDS = 1000
    private const val CLOCK_SKEW_TOLERANCE_MS = 3 * 60_000L

    const val CHANNEL_TEAM = "bsr_team_notifications"
    const val CHANNEL_ANNOUNCEMENTS = "bsr_announcements"
    const val CHANNEL_UPDATES = "bsr_updates"

    private const val LEGACY_CHANNEL_REALTIME_SYNC = "bsr_realtime_sync"
    private const val LEGACY_PREF_REALTIME_SYNC_ENABLED = "realtime_sync_opt_in"
    private const val LEGACY_FOREGROUND_SYNC_NOTIFICATION_ID = 1099

    const val EXTRA_NOTIF_ID = "notif_id"
    const val EXTRA_NOTIF_TYPE = "notif_type"
    const val EXTRA_NOTIF_ROOM_ID = "notif_room_id"
    const val EXTRA_OPEN_UPDATE_DIALOG = "open_update_dialog"

    private val dispatchLock = Any()

    fun cleanupLegacyRealtimeSyncArtifacts(context: Context) {
        val appCtx = context.applicationContext
        try {
            val nm = appCtx.getSystemService(NotificationManager::class.java)
            nm?.cancel(LEGACY_FOREGROUND_SYNC_NOTIFICATION_ID)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                nm?.deleteNotificationChannel(LEGACY_CHANNEL_REALTIME_SYNC)
            }
        } catch (_: Exception) {
        }
        try {
            appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(LEGACY_PREF_REALTIME_SYNC_ENABLED)
                .apply()
        } catch (_: Exception) {
        }
    }

    fun clearUserSyncState(context: Context, userId: String) {
        if (userId.isBlank()) return
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove("seeded_$userId")
            .remove("baseline_ts_$userId")
            .remove("dispatched_list_$userId")
            .remove("join_ts_$userId")
            .apply()
    }

    fun recordUserJoinTimestampIfMissing(
        context: Context,
        userId: String,
        timestampMs: Long = System.currentTimeMillis()
    ) {
        if (userId.isBlank() || timestampMs <= 0L) return
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = "join_ts_$userId"
        if (!prefs.contains(key)) {
            prefs.edit().putLong(key, timestampMs).apply()
        }
    }

    fun getUserJoinTimestampMs(context: Context, userId: String): Long {
        if (userId.isBlank()) return 0L
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong("join_ts_$userId", 0L)
    }

    fun hasSeededBaseline(context: Context, userId: String): Boolean {
        if (userId.isBlank()) return false
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean("seeded_$userId", false)
    }

    fun ensureNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(NotificationManager::class.java) ?: return

            val generalChannel = NotificationChannel(
                CHANNEL_TEAM,
                "BLACKSEA ROBOTICS Bildirimleri",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Takım mesajları, fikirler, dosyalar, yeni üyeler ve başvurular"
                enableLights(true)
                enableVibration(true)
            }

            val announcementsChannel = NotificationChannel(
                CHANNEL_ANNOUNCEMENTS,
                "Takım Duyuruları",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Yönetici ve takım kaptanının yayınladığı özel bildirim ve duyurular"
                enableLights(true)
                enableVibration(true)
            }

            val updatesChannel = NotificationChannel(
                CHANNEL_UPDATES,
                "Uygulama Güncellemeleri",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Yeni sürüm ve uygulama güncelleme bildirimleri"
                enableLights(true)
                enableVibration(true)
            }

            notificationManager.createNotificationChannel(generalChannel)
            notificationManager.createNotificationChannel(announcementsChannel)
            notificationManager.createNotificationChannel(updatesChannel)
            notificationManager.deleteNotificationChannel(LEGACY_CHANNEL_REALTIME_SYNC)
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return false
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun resolveChannelId(type: String): String {
        return when (type.lowercase()) {
            "update" -> CHANNEL_UPDATES
            "broadcast", "announcement" -> CHANNEL_ANNOUNCEMENTS
            else -> CHANNEL_TEAM
        }
    }

    /**
     * Pure deterministic selector that separates initial backlog seeding (on first open / login)
     * from genuine newly occurred events. Never emits historical backlog notifications when the
     * user first opens the app.
     */
    fun selectNotificationsToDispatch(
        notifications: List<TeamNotification>,
        userId: String,
        hasSeededBaseline: Boolean,
        baselineTimestampMs: Long,
        dispatchedIds: Set<String>,
        nowMs: Long = System.currentTimeMillis(),
        userCreatedAtMs: Long = 0L
    ): DispatchSelection {
        val validItems = notifications.filter { it.id.isNotBlank() }

        if (!hasSeededBaseline) {
            val maxCreatedMs = validItems.mapNotNull { it.createdAt?.toDate()?.time }.maxOrNull() ?: 0L
            return DispatchSelection(
                shouldSeedBaseline = true,
                seedIds = validItems.map { it.id }.takeLast(MAX_STORED_IDS),
                toDispatch = emptyList(),
                newBaselineTimestampMs = maxOf(nowMs, maxCreatedMs)
            )
        }

        val minAllowedTimestampMs = (baselineTimestampMs - CLOCK_SKEW_TOLERANCE_MS).coerceAtLeast(0L)
        val effectiveJoinFloorMs = if (userCreatedAtMs > 0L) (userCreatedAtMs - 5_000L).coerceAtLeast(0L) else 0L
        val candidates = validItems.filter { notif ->
            val alreadyDispatched = dispatchedIds.contains(notif.id)
            val alreadyRead = notif.isReadBy(userId)
            val isOwnAction = userId.isNotBlank() &&
                ((notif.senderId.isNotBlank() && notif.senderId == userId) ||
                    (notif.type.equals("new_member", ignoreCase = true) && notif.targetId == userId))
            val createdMs = notif.createdAt?.toDate()?.time
            val withinTimeWindow = createdMs == null || createdMs >= minAllowedTimestampMs
            val afterUserJoined = notif.recipientUserId == userId ||
                effectiveJoinFloorMs == 0L ||
                createdMs == null ||
                createdMs >= effectiveJoinFloorMs

            !alreadyDispatched && !alreadyRead && !isOwnAction && withinTimeWindow && afterUserJoined
        }.sortedBy { it.createdAt?.toDate()?.time ?: nowMs }

        return DispatchSelection(
            shouldSeedBaseline = false,
            seedIds = emptyList(),
            toDispatch = candidates,
            newBaselineTimestampMs = baselineTimestampMs
        )
    }

    /**
     * Silently syncs baseline timestamp and known IDs for [userId] (for instance on foreground app
     * open) so missed/backlog notifications already in Firestore do NOT fire when opening the app.
     */
    fun syncBaselineSilently(
        context: Context,
        userId: String,
        notifications: List<TeamNotification>,
        nowMs: Long = System.currentTimeMillis()
    ) {
        if (userId.isBlank()) return
        val appCtx = context.applicationContext
        val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val seededKey = "seeded_$userId"
        val baselineTsKey = "baseline_ts_$userId"
        val idsKey = "dispatched_list_$userId"

        synchronized(dispatchLock) {
            val validItems = notifications.filter { it.id.isNotBlank() }
            val existingBaseline = prefs.getLong(baselineTsKey, 0L)
            val storedList = readStoredIdList(prefs.getString(idsKey, null))
            val maxCreatedMs = validItems.mapNotNull { it.createdAt?.toDate()?.time }.maxOrNull() ?: 0L
            val newBaseline = maxOf(existingBaseline, nowMs, maxCreatedMs)
            val mergedIds = (storedList + validItems.map { it.id })
                .distinct()
                .takeLast(MAX_STORED_IDS)

            prefs.edit()
                .putBoolean(seededKey, true)
                .putLong(baselineTsKey, newBaseline)
                .putString(idsKey, writeStoredIdList(mergedIds))
                .apply()
        }
    }

    /**
     * Processes a filtered list of notifications for [userId].
     * On first run for this [userId], seeds the baseline silently so historical notifications are
     * NOT re-fired on app startup. Any genuine new notification arriving afterwards fires immediately
     * as an Android OS notification.
     */
    fun processNotifications(
        context: Context,
        userId: String,
        notifications: List<TeamNotification>,
        userCreatedAtMs: Long = 0L
    ): Int {
        if (userId.isBlank()) return 0

        val appCtx = context.applicationContext
        val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val seededKey = "seeded_$userId"
        val baselineTsKey = "baseline_ts_$userId"
        val idsKey = "dispatched_list_$userId"

        val toDispatch: List<TeamNotification>
        synchronized(dispatchLock) {
            val hasSeeded = prefs.getBoolean(seededKey, false)
            if (!hasSeeded && notifications.isEmpty()) {
                return 0
            }

            val baselineTs = prefs.getLong(baselineTsKey, 0L)
            val storedList = readStoredIdList(prefs.getString(idsKey, null))

            val selection = selectNotificationsToDispatch(
                notifications = notifications,
                userId = userId,
                hasSeededBaseline = hasSeeded,
                baselineTimestampMs = baselineTs,
                dispatchedIds = storedList.toSet(),
                userCreatedAtMs = userCreatedAtMs
            )

            if (selection.shouldSeedBaseline) {
                prefs.edit()
                    .putBoolean(seededKey, true)
                    .putLong(baselineTsKey, selection.newBaselineTimestampMs)
                    .putString(idsKey, writeStoredIdList(selection.seedIds))
                    .apply()
                return 0
            }

            if (selection.toDispatch.isEmpty()) {
                return 0
            }

            // Record dispatched IDs immediately before posting so concurrent realtime + worker runs don't duplicate
            val updatedIds = (storedList + selection.toDispatch.map { it.id })
                .distinct()
                .takeLast(MAX_STORED_IDS)
            prefs.edit()
                .putString(idsKey, writeStoredIdList(updatedIds))
                .apply()

            toDispatch = selection.toDispatch
        }

        if (!hasNotificationPermission(appCtx)) {
            return 0
        }

        ensureNotificationChannels(appCtx)
        val notifManager = appCtx.getSystemService(NotificationManager::class.java) ?: return 0

        var postedCount = 0
        // Cap burst size to avoid Android's per-app notification rate-limit dropping notifications
        toDispatch.takeLast(5).forEach { notif ->
            try {
                val channelId = resolveChannelId(notif.type)
                val stableId = (notif.id.hashCode() and 0x7FFFFFFF) + 2000
                val tapIntent = Intent(appCtx, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(EXTRA_NOTIF_ID, notif.id)
                    putExtra(EXTRA_NOTIF_TYPE, notif.type)
                    putExtra(EXTRA_NOTIF_ROOM_ID, notif.targetRoomId)
                    putExtra(EXTRA_OPEN_UPDATE_DIALOG, notif.type.equals("update", ignoreCase = true))
                }
                val pendingIntent = PendingIntent.getActivity(
                    appCtx,
                    stableId,
                    tapIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val bodyText = notif.message.ifBlank { notif.title }
                val builder = NotificationCompat.Builder(appCtx, channelId)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle(notif.title.ifBlank { "BLACKSEA ROBOTICS" })
                    .setContentText(bodyText)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(bodyText))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)

                notifManager.notify(stableId, builder.build())
                postedCount++
            } catch (sec: SecurityException) {
                logDebugWarn(TAG, "Missing notification permission", sec)
            } catch (e: Exception) {
                logDebugWarn(TAG, "Failed to post system notification for ${notif.id}", e)
            }
        }
        return postedCount
    }

    /**
     * Dispatches an OS notification for a newer app release if we have not yet notified
     * for [AppReleaseInfo.versionCode] or [AppReleaseInfo.tagName].
     */
    fun dispatchUpdateNotificationIfNeeded(
        context: Context,
        updateManager: AppUpdateManager,
        release: AppReleaseInfo
    ): Boolean {
        val currentCode = updateManager.getCurrentVersionCode()
        val isNewer = when {
            release.versionCode > currentCode -> true
            release.versionCode <= 0 -> updateManager.evaluateReleaseComparison(release) is UpdateCheckResult.UpdateAvailable
            else -> false
        }
        if (!isNewer) return false
        if (updateManager.hasNotifiedForRelease(release.versionCode, release.tagName)) return false

        val appCtx = context.applicationContext
        if (!hasNotificationPermission(appCtx)) return false

        return try {
            ensureNotificationChannels(appCtx)
            val tapIntent = Intent(appCtx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_NOTIF_TYPE, "update")
                putExtra(EXTRA_OPEN_UPDATE_DIALOG, true)
            }
            val pendingIntent = PendingIntent.getActivity(
                appCtx,
                1001,
                tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notifManager = appCtx.getSystemService(NotificationManager::class.java) ?: return false
            val isCompatible = updateManager.isReleaseSignatureCompatible(release)
            val contentText = if (isCompatible) {
                "Blacksea Robotics için yeni bir sürüm (v${release.versionName}) yayınlandı."
            } else {
                "Blacksea Robotics için yeni bir sürüm (v${release.versionName}) yayınlandı. Tarayıcıdan indirebilirsiniz."
            }
            val builder = NotificationCompat.Builder(appCtx, CHANNEL_UPDATES)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("🔔 Yeni Güncelleme Mevcut")
                .setContentText(contentText)
                .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
            notifManager.notify(1001, builder.build())
            updateManager.markReleaseNotified(release.versionCode, release.tagName)
            true
        } catch (sec: SecurityException) {
            logDebugWarn(TAG, "Missing notification permission for update", sec)
            false
        } catch (e: Exception) {
            logDebugWarn(TAG, "Failed to post update notification", e)
            false
        }
    }

    private fun readStoredIdList(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList(arr.length()) {
                for (i in 0 until arr.length()) {
                    val value = arr.optString(i, "")
                    if (value.isNotBlank()) add(value)
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun writeStoredIdList(ids: List<String>): String {
        val arr = JSONArray()
        ids.takeLast(MAX_STORED_IDS).forEach { arr.put(it) }
        return arr.toString()
    }
}
