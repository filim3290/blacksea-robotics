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
import com.example.model.PLATFORM_MAIN_TEAM_ID
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
    private const val CLOCK_SKEW_TOLERANCE_MS = 10 * 60_000L

    const val CHANNEL_TEAM = "bsr_team_notifications"
    const val CHANNEL_MESSAGES = "bsr_chat_messages_v3"
    const val CHANNEL_MESSAGES_SILENT = CHANNEL_MESSAGES
    const val CHANNEL_ANNOUNCEMENTS = "bsr_announcements"
    const val CHANNEL_UPDATES = "bsr_updates"
    const val CHANNEL_BACKGROUND_SERVICE = "bsr_background_listener_v1"

    private const val LEGACY_CHANNEL_REALTIME_SYNC = "bsr_realtime_sync"
    private const val LEGACY_CHANNEL_MESSAGES_SILENT = "bsr_chat_messages_silent"
    private const val LEGACY_CHANNEL_MESSAGES_V2 = "bsr_chat_messages_v2"
    private const val LEGACY_PREF_REALTIME_SYNC_ENABLED = "realtime_sync_opt_in"
    private const val LEGACY_FOREGROUND_SYNC_NOTIFICATION_ID = 1099
    const val BACKGROUND_SERVICE_NOTIFICATION_ID = 1098
    private const val PENDING_MSG_PREFIX = "pending_msg_count_"
    private const val UPDATE_NOTIFICATION_ID = 1001

    const val EXTRA_NOTIF_ID = "notif_id"
    const val EXTRA_NOTIF_TYPE = "notif_type"
    const val EXTRA_NOTIF_ROOM_ID = "notif_room_id"
    const val EXTRA_NOTIF_TEAM_ID = "notif_team_id"
    const val EXTRA_OPEN_UPDATE_DIALOG = "open_update_dialog"

    private val dispatchLock = Any()

    @Volatile
    private var isAppForeground: Boolean = false

    @Volatile
    private var activeChatRoomId: String? = null

    @Volatile
    private var activeChatTeamId: String = PLATFORM_MAIN_TEAM_ID

    fun setAppForeground(inForeground: Boolean) {
        isAppForeground = inForeground
    }

    fun setAppForeground(context: Context, userId: String?, inForeground: Boolean) {
        isAppForeground = inForeground
        val currentRoom = activeChatRoomId
        val currentTeam = activeChatTeamId
        if (inForeground && !userId.isNullOrBlank() && !currentRoom.isNullOrBlank()) {
            clearPendingMessagesForRoom(context, userId, currentRoom, currentTeam)
        }
    }

    fun setActiveChatRoom(
        context: Context,
        userId: String,
        roomId: String?,
        teamId: String = PLATFORM_MAIN_TEAM_ID
    ) {
        val normalized = roomId?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
        val cleanTeam = normalizeTeamKey(teamId)
        activeChatRoomId = normalized
        activeChatTeamId = cleanTeam
        if (normalized != null && userId.isNotBlank()) {
            clearPendingMessagesForRoom(context, userId, normalized, cleanTeam)
        }
    }

    fun isActivelyChattingInRoom(roomId: String, teamId: String = PLATFORM_MAIN_TEAM_ID): Boolean {
        val active = activeChatRoomId ?: return false
        return isAppForeground &&
            active == normalizeRoomKey(roomId) &&
            activeChatTeamId == normalizeTeamKey(teamId)
    }

    fun getPendingMessageCount(
        context: Context,
        userId: String,
        roomId: String,
        teamId: String = PLATFORM_MAIN_TEAM_ID
    ): Int {
        if (userId.isBlank()) return 0
        val key = pendingCountKey(userId, normalizeRoomKey(roomId), normalizeTeamKey(teamId))
        return context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(key, 0)
    }

    fun clearPendingMessagesForRoom(
        context: Context,
        userId: String,
        roomId: String,
        teamId: String = PLATFORM_MAIN_TEAM_ID
    ) {
        if (userId.isBlank()) return
        val roomKey = normalizeRoomKey(roomId)
        val cleanTeam = normalizeTeamKey(teamId)
        val appCtx = context.applicationContext
        synchronized(dispatchLock) {
            appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(pendingCountKey(userId, roomKey, cleanTeam))
                .apply()
        }
        try {
            appCtx.getSystemService(NotificationManager::class.java)
                ?.cancel(roomSummaryNotificationId(roomKey, cleanTeam))
        } catch (_: Exception) {
        }
    }

    fun clearPendingUpdateNotification(context: Context) {
        try {
            context.applicationContext
                .getSystemService(NotificationManager::class.java)
                ?.cancel(UPDATE_NOTIFICATION_ID)
        } catch (_: Exception) {
        }
    }

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
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
            .remove("seeded_$userId")
            .remove("baseline_ts_$userId")
            .remove("dispatched_list_$userId")
            .remove("join_ts_$userId")
        val prefix = "$PENDING_MSG_PREFIX${userId}_"
        prefs.all.keys.filter { it.startsWith(prefix) }.forEach { editor.remove(it) }
        editor.apply()
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
                description = "Fikirler, dosyalar, yeni üyeler ve başvurular"
                enableLights(true)
                enableVibration(true)
            }

            val messagesChannel = NotificationChannel(
                CHANNEL_MESSAGES,
                "Sohbet Mesajları",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Aktif olmadığınız sohbet odalarından gelen yeni mesajlar"
                enableLights(true)
                enableVibration(true)
                setShowBadge(true)
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

            val backgroundServiceChannel = NotificationChannel(
                CHANNEL_BACKGROUND_SERVICE,
                context.getString(R.string.bg_service_channel_name),
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = context.getString(R.string.bg_service_channel_desc)
                enableLights(false)
                enableVibration(false)
                setShowBadge(false)
                setSound(null, null)
            }

            notificationManager.createNotificationChannel(generalChannel)
            notificationManager.createNotificationChannel(messagesChannel)
            notificationManager.createNotificationChannel(announcementsChannel)
            notificationManager.createNotificationChannel(updatesChannel)
            notificationManager.createNotificationChannel(backgroundServiceChannel)
            notificationManager.deleteNotificationChannel(LEGACY_CHANNEL_REALTIME_SYNC)
            notificationManager.deleteNotificationChannel(LEGACY_CHANNEL_MESSAGES_SILENT)
            notificationManager.deleteNotificationChannel(LEGACY_CHANNEL_MESSAGES_V2)
        }
    }

    fun buildRealtimeServiceNotification(context: Context): android.app.Notification {
        val appCtx = context.applicationContext
        ensureNotificationChannels(appCtx)
        val openIntent = Intent(appCtx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            appCtx,
            BACKGROUND_SERVICE_NOTIFICATION_ID,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(appCtx, CHANNEL_BACKGROUND_SERVICE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(appCtx.getString(R.string.bg_service_notif_title))
            .setContentText(appCtx.getString(R.string.bg_service_notif_text))
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(pendingIntent)
            .build()
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
            "message" -> CHANNEL_MESSAGES
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
                newBaselineTimestampMs = maxCreatedMs
            )
        }

        val minAllowedTimestampMs = if (baselineTimestampMs > 0L) {
            (baselineTimestampMs - CLOCK_SKEW_TOLERANCE_MS).coerceAtLeast(0L)
        } else {
            0L
        }
        val effectiveJoinFloorMs = if (userCreatedAtMs > 0L) (userCreatedAtMs - 5_000L).coerceAtLeast(0L) else 0L
        val candidates = validItems.filter { notif ->
            val alreadyDispatched = dispatchedIds.contains(notif.id)
            val alreadyRead = notif.isReadBy(userId)
            val isOwnAction = userId.isNotBlank() &&
                ((notif.senderId.isNotBlank() && notif.senderId == userId) ||
                    (notif.type.equals("new_member", ignoreCase = true) && notif.targetId == userId))
            val createdMs = notif.createdAt?.toDate()?.time
            val withinTimeWindow = minAllowedTimestampMs == 0L || createdMs == null || createdMs >= minAllowedTimestampMs
            val afterUserJoined = notif.recipientUserId == userId ||
                effectiveJoinFloorMs == 0L ||
                createdMs == null ||
                createdMs >= effectiveJoinFloorMs

            !alreadyDispatched && !alreadyRead && !isOwnAction && withinTimeWindow && afterUserJoined
        }.sortedBy { it.createdAt?.toDate()?.time ?: nowMs }

        val maxObservedCreatedMs = validItems.mapNotNull { it.createdAt?.toDate()?.time }.maxOrNull() ?: 0L
        val advancedBaselineMs = maxOf(baselineTimestampMs, maxObservedCreatedMs)

        return DispatchSelection(
            shouldSeedBaseline = false,
            seedIds = emptyList(),
            toDispatch = candidates,
            newBaselineTimestampMs = advancedBaselineMs
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
        @Suppress("UNUSED_PARAMETER") nowMs: Long = System.currentTimeMillis()
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
            val newBaseline = maxOf(existingBaseline, maxCreatedMs)
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
     * as an Android OS notification (except chat messages inside the currently open active room).
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

            if (selection.newBaselineTimestampMs > baselineTs) {
                prefs.edit().putLong(baselineTsKey, selection.newBaselineTimestampMs).apply()
            }

            val candidateSelection = selection.toDispatch
            if (candidateSelection.isEmpty()) {
                return 0
            }

            val updateManager = AppUpdateManager(appCtx)
            val (obsoleteUpdates, activeToDispatch) = candidateSelection.partition { notif ->
                updateManager.isNotificationUpdateObsolete(notif.type, notif.targetId, notif.message)
            }

            if (obsoleteUpdates.isNotEmpty()) {
                markIdsAsDispatched(prefs, idsKey, obsoleteUpdates.map { it.id })
            }
            if (activeToDispatch.isEmpty()) return 0

            val (messageNotifs, nonMessageNotifs) = activeToDispatch.partition {
                it.type.equals("message", ignoreCase = true)
            }

            // Suppress chat messages for the exact room+team the user is currently viewing in foreground
            val (activeRoomMsgs, inactiveRoomMsgs) = messageNotifs.partition {
                isActivelyChattingInRoom(normalizeRoomKey(it.targetRoomId), it.effectiveTeamId())
            }
            if (activeRoomMsgs.isNotEmpty()) {
                markIdsAsDispatched(prefs, idsKey, activeRoomMsgs.map { it.id })
                activeRoomMsgs.map { normalizeRoomKey(it.targetRoomId) to it.effectiveTeamId() }
                    .distinct()
                    .forEach { (roomKey, teamId) ->
                        clearPendingMessagesForRoom(appCtx, userId, roomKey, teamId)
                    }
            }

            if (inactiveRoomMsgs.isEmpty() && nonMessageNotifs.isEmpty()) {
                return 0
            }

            // Never mark unseen notifications as dispatched when OS permission is missing;
            // once the user grants notification permission, they will dispatch cleanly.
            if (!hasNotificationPermission(appCtx)) {
                return 0
            }

            ensureNotificationChannels(appCtx)
            val notifManager = appCtx.getSystemService(NotificationManager::class.java) ?: return 0

            var postedCount = 0
            val dispatchedIdsToRecord = mutableListOf<String>()

        // 1. Dispatch chat notifications immediately per (teamId, room) for inactive rooms
        if (inactiveRoomMsgs.isNotEmpty()) {
            inactiveRoomMsgs.groupBy { normalizeTeamKey(it.effectiveTeamId()) to normalizeRoomKey(it.targetRoomId) }
                .forEach { (teamAndRoom, roomMsgs) ->
                    val (teamKey, roomKey) = teamAndRoom
                    if (isActivelyChattingInRoom(roomKey, teamKey)) {
                        clearPendingMessagesForRoom(appCtx, userId, roomKey, teamKey)
                        dispatchedIdsToRecord.addAll(roomMsgs.map { it.id })
                        return@forEach
                    }
                    try {
                        val countKey = pendingCountKey(userId, roomKey, teamKey)
                        val totalUnreadInRoom = synchronized(dispatchLock) {
                            val updated = prefs.getInt(countKey, 0) + roomMsgs.size
                            prefs.edit().putInt(countKey, updated).apply()
                            updated
                        }
                        val latestMsg = roomMsgs.last()
                        val summaryId = roomSummaryNotificationId(roomKey, teamKey)
                        val tapIntent = Intent(appCtx, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            putExtra(EXTRA_NOTIF_ID, latestMsg.id)
                            putExtra(EXTRA_NOTIF_TYPE, "message")
                            putExtra(EXTRA_NOTIF_ROOM_ID, roomKey)
                            putExtra(EXTRA_NOTIF_TEAM_ID, teamKey)
                            putExtra(EXTRA_OPEN_UPDATE_DIALOG, false)
                        }
                        val pendingIntent = PendingIntent.getActivity(
                            appCtx,
                            summaryId,
                            tapIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        val teamPrefix = if (latestMsg.teamName.isNotBlank() && teamKey != PLATFORM_MAIN_TEAM_ID) {
                            "[${latestMsg.teamName}] "
                        } else ""
                        val titleText = if (totalUnreadInRoom <= 1) {
                            "$teamPrefix${latestMsg.title.ifBlank { "Yeni mesaj (${roomDisplayName(roomKey)})" }}"
                        } else {
                            "$teamPrefix${roomDisplayName(roomKey)} • $totalUnreadInRoom yeni mesaj"
                        }
                        val bodyText = latestMsg.message.ifBlank { latestMsg.title.ifBlank { "Yeni mesaj" } }
                        val builder = NotificationCompat.Builder(appCtx, CHANNEL_MESSAGES)
                            .setSmallIcon(R.drawable.ic_notification)
                            .setContentTitle(titleText)
                            .setContentText(bodyText)
                            .setStyle(NotificationCompat.BigTextStyle().bigText(bodyText))
                            .setNumber(totalUnreadInRoom)
                            .setPriority(NotificationCompat.PRIORITY_HIGH)
                            .setDefaults(NotificationCompat.DEFAULT_ALL)
                            .setContentIntent(pendingIntent)
                            .setAutoCancel(true)

                        notifManager.notify(summaryId, builder.build())
                        dispatchedIdsToRecord.addAll(roomMsgs.map { it.id })
                        postedCount++
                    } catch (sec: SecurityException) {
                        logDebugWarn(TAG, "Missing notification permission for chat notification", sec)
                    } catch (e: Exception) {
                        logDebugWarn(TAG, "Failed to post chat notification for $roomKey", e)
                    }
                }
        }

        // 2. Dispatch non-chat notifications immediately (capped burst size to avoid OS rate limiting)
        val nonMsgBatch = nonMessageNotifs.takeLast(8)
        if (nonMessageNotifs.size > nonMsgBatch.size) {
            dispatchedIdsToRecord.addAll(nonMessageNotifs.dropLast(nonMsgBatch.size).map { it.id })
        }
        nonMsgBatch.forEach { notif ->
            try {
                val channelId = resolveChannelId(notif.type)
                val stableId = (notif.id.hashCode() and 0x7FFFFFFF) + 2000
                val notifTeamId = notif.effectiveTeamId()
                val tapIntent = Intent(appCtx, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(EXTRA_NOTIF_ID, notif.id)
                    putExtra(EXTRA_NOTIF_TYPE, notif.type)
                    putExtra(EXTRA_NOTIF_ROOM_ID, notif.targetRoomId)
                    putExtra(EXTRA_NOTIF_TEAM_ID, notifTeamId)
                    putExtra(EXTRA_OPEN_UPDATE_DIALOG, notif.type.equals("update", ignoreCase = true))
                }
                val pendingIntent = PendingIntent.getActivity(
                    appCtx,
                    stableId,
                    tapIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val bodyText = notif.message.ifBlank { notif.title }
                val teamPrefix = if (
                    notif.teamName.isNotBlank() &&
                    notifTeamId != PLATFORM_MAIN_TEAM_ID &&
                    notifTeamId != "platform" &&
                    notifTeamId != "platform_admin" &&
                    !notif.title.contains(notif.teamName, ignoreCase = true)
                ) {
                    "[${notif.teamName}] "
                } else ""
                val builder = NotificationCompat.Builder(appCtx, channelId)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle("$teamPrefix${notif.title.ifBlank { "BLACKSEA ROBOTICS" }}")
                    .setContentText(bodyText)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(bodyText))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setDefaults(NotificationCompat.DEFAULT_ALL)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)

                notifManager.notify(stableId, builder.build())
                dispatchedIdsToRecord.add(notif.id)
                postedCount++
            } catch (sec: SecurityException) {
                logDebugWarn(TAG, "Missing notification permission", sec)
            } catch (e: Exception) {
                logDebugWarn(TAG, "Failed to post system notification for ${notif.id}", e)
            }
        }

            if (dispatchedIdsToRecord.isNotEmpty()) {
                markIdsAsDispatched(prefs, idsKey, dispatchedIdsToRecord)
            }
            return postedCount
        }
    }

    private fun markIdsAsDispatched(
        prefs: android.content.SharedPreferences,
        idsKey: String,
        newIds: List<String>
    ) {
        if (newIds.isEmpty()) return
        synchronized(dispatchLock) {
            val storedList = readStoredIdList(prefs.getString(idsKey, null))
            val updatedIds = (storedList + newIds)
                .filter { it.isNotBlank() }
                .distinct()
                .takeLast(MAX_STORED_IDS)
            prefs.edit()
                .putString(idsKey, writeStoredIdList(updatedIds))
                .apply()
        }
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
        val isNewer = updateManager.evaluateReleaseComparison(release) is UpdateCheckResult.UpdateAvailable
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
                UPDATE_NOTIFICATION_ID,
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
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("🔔 Yeni Güncelleme Mevcut")
                .setContentText(contentText)
                .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
            notifManager.notify(UPDATE_NOTIFICATION_ID, builder.build())
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

    private fun normalizeTeamKey(teamId: String?): String {
        return teamId?.trim()?.takeIf { it.isNotBlank() } ?: PLATFORM_MAIN_TEAM_ID
    }

    private fun normalizeRoomKey(roomId: String?): String {
        return roomId?.trim()?.lowercase()?.takeIf { it.isNotBlank() } ?: "general"
    }

    private fun pendingCountKey(
        userId: String,
        roomKey: String,
        teamId: String = PLATFORM_MAIN_TEAM_ID
    ): String {
        return if (teamId == PLATFORM_MAIN_TEAM_ID) {
            "$PENDING_MSG_PREFIX${userId}_$roomKey"
        } else {
            "$PENDING_MSG_PREFIX${userId}_${teamId}_$roomKey"
        }
    }

    private fun roomSummaryNotificationId(
        roomKey: String,
        teamId: String = PLATFORM_MAIN_TEAM_ID
    ): Int {
        val composite = if (teamId == PLATFORM_MAIN_TEAM_ID) roomKey else "${teamId}_$roomKey"
        return 3000 + (composite.hashCode() and 0x7FFF)
    }

    internal fun roomDisplayName(roomKey: String): String {
        return when (normalizeRoomKey(roomKey)) {
            "general" -> "Genel Ekip Odası"
            "software" -> "Yazılım Ekibi"
            "mechanic" -> "Mekanik Ekibi"
            "pr" -> "PR Ekibi"
            else -> "Takım Sohbeti"
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
