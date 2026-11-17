package com.example

import android.Manifest
import android.app.Activity
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import com.example.data.shouldUserReceiveNotification
import com.example.model.SupportFaqData
import com.example.model.SupportTicketStatus
import com.example.model.TeamNotification
import com.example.model.filterChatDisplayRoles
import com.example.notifications.RealtimeNotificationService
import com.example.notifications.TeamNotificationDispatcher
import com.example.ui.screens.findActivity
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotificationAndStabilityTest {

    @Test
    fun `shouldUserReceiveNotification excludes sender across all notification types`() {
        val uid = "user_1"
        val types = listOf("message", "file", "idea", "broadcast", "announcement", "update", "new_member", "member_approved")

        types.forEach { type ->
            val notif = TeamNotification(
                id = "n_$type",
                recipientUserId = "all",
                type = type,
                targetRoomId = "general",
                senderId = uid
            )
            assertFalse(
                "Sender must not receive own $type notification",
                shouldUserReceiveNotification(notif, userId = uid, isManager = true, userTeam = "Yazılım Ekibi")
            )
        }
    }

    @Test
    fun `shouldUserReceiveNotification excludes newly approved user from new_member team alert`() {
        val newMemberId = "new_uid"
        val adminId = "admin_uid"
        val notif = TeamNotification(
            id = "n_join",
            recipientUserId = "all",
            type = "new_member",
            targetId = newMemberId,
            senderId = adminId
        )

        assertFalse(
            "Newly approved user should not receive new_member notification about themselves",
            shouldUserReceiveNotification(notif, userId = newMemberId, isManager = false, userTeam = "Mekanik Ekibi")
        )
        assertTrue(
            "Teammates should receive new_member notification",
            shouldUserReceiveNotification(notif, userId = "other_uid", isManager = false, userTeam = "Yazılım Ekibi")
        )
    }

    @Test
    fun `shouldUserReceiveNotification enforces room filtering by user team and manager role`() {
        val softwareMsg = TeamNotification(
            id = "m_sw",
            recipientUserId = "all",
            type = "message",
            targetRoomId = "software",
            senderId = "sender_sw"
        )
        val mechanicFile = TeamNotification(
            id = "f_mech",
            recipientUserId = "all",
            type = "file",
            targetRoomId = "mechanic",
            senderId = "sender_mech"
        )
        val prMsg = TeamNotification(
            id = "m_pr",
            recipientUserId = "all",
            type = "message",
            targetRoomId = "pr",
            senderId = "sender_pr"
        )
        val generalMsg = TeamNotification(
            id = "m_gen",
            recipientUserId = "all",
            type = "message",
            targetRoomId = "general",
            senderId = "sender_gen"
        )

        // Software member sees general & software, not mechanic or pr
        assertTrue(shouldUserReceiveNotification(generalMsg, "u_sw", isManager = false, userTeam = "Yazılım Ekibi"))
        assertTrue(shouldUserReceiveNotification(softwareMsg, "u_sw", isManager = false, userTeam = "Yazılım Ekibi"))
        assertFalse(shouldUserReceiveNotification(mechanicFile, "u_sw", isManager = false, userTeam = "Yazılım Ekibi"))
        assertFalse(shouldUserReceiveNotification(prMsg, "u_sw", isManager = false, userTeam = "Yazılım Ekibi"))

        // Mechanic member sees general & mechanic, not software or pr
        assertTrue(shouldUserReceiveNotification(generalMsg, "u_mech", isManager = false, userTeam = "Mekanik Ekibi"))
        assertTrue(shouldUserReceiveNotification(mechanicFile, "u_mech", isManager = false, userTeam = "Mekanik Ekibi"))
        assertFalse(shouldUserReceiveNotification(softwareMsg, "u_mech", isManager = false, userTeam = "Mekanik Ekibi"))
        assertFalse(shouldUserReceiveNotification(prMsg, "u_mech", isManager = false, userTeam = "Mekanik Ekibi"))

        // PR member sees general & pr, not software or mechanic
        assertTrue(shouldUserReceiveNotification(generalMsg, "u_pr", isManager = false, userTeam = "PR & Medya"))
        assertTrue(shouldUserReceiveNotification(prMsg, "u_pr", isManager = false, userTeam = "PR & Medya"))
        assertFalse(shouldUserReceiveNotification(softwareMsg, "u_pr", isManager = false, userTeam = "PR & Medya"))
        assertFalse(shouldUserReceiveNotification(mechanicFile, "u_pr", isManager = false, userTeam = "PR & Medya"))

        // Manager sees all rooms (except own sent items)
        assertTrue(shouldUserReceiveNotification(softwareMsg, "u_mgr", isManager = true, userTeam = "Yazılım Ekibi"))
        assertTrue(shouldUserReceiveNotification(mechanicFile, "u_mgr", isManager = true, userTeam = "Yazılım Ekibi"))
        assertTrue(shouldUserReceiveNotification(prMsg, "u_mgr", isManager = true, userTeam = "Yazılım Ekibi"))
    }

    @Test
    fun `shouldUserReceiveNotification delivers ideas broadcasts updates globally and pending only to managers`() {
        val idea = TeamNotification(id = "i1", recipientUserId = "all", type = "idea", senderId = "other")
        val broadcast = TeamNotification(id = "b1", recipientUserId = "all", type = "broadcast", senderId = "mgr")
        val announcement = TeamNotification(id = "a1", recipientUserId = "all", type = "announcement", senderId = "mgr")
        val update = TeamNotification(id = "u1", recipientUserId = "all", type = "update", senderId = "mgr")
        val pending = TeamNotification(id = "p1", recipientUserId = "managers", type = "member_pending", senderId = "applicant")

        assertTrue(shouldUserReceiveNotification(idea, "u_pr", isManager = false, userTeam = "PR"))
        assertTrue(shouldUserReceiveNotification(broadcast, "u_pr", isManager = false, userTeam = "PR"))
        assertTrue(shouldUserReceiveNotification(announcement, "u_pr", isManager = false, userTeam = "PR"))
        assertTrue(shouldUserReceiveNotification(update, "u_pr", isManager = false, userTeam = "PR"))

        assertFalse(shouldUserReceiveNotification(pending, "u_pr", isManager = false, userTeam = "PR"))
        assertTrue(shouldUserReceiveNotification(pending, "u_mgr", isManager = true, userTeam = "Yazılım Ekibi"))
    }

    @Test
    fun `selectNotificationsToDispatch seeds baseline silently on first run without firing stale notifications`() {
        val now = 1_700_000_000_000L
        val historical = listOf(
            TeamNotification(
                id = "old_1",
                type = "message",
                senderId = "other",
                createdAt = Timestamp(Date(now - 600_000L))
            ),
            TeamNotification(
                id = "old_2",
                type = "idea",
                senderId = "other",
                createdAt = Timestamp(Date(now - 10_000L))
            )
        )

        val firstRun = TeamNotificationDispatcher.selectNotificationsToDispatch(
            notifications = historical,
            userId = "me",
            hasSeededBaseline = false,
            baselineTimestampMs = 0L,
            dispatchedIds = emptySet(),
            nowMs = now
        )

        assertTrue(firstRun.shouldSeedBaseline)
        assertTrue(firstRun.toDispatch.isEmpty())
        assertEquals(listOf("old_1", "old_2"), firstRun.seedIds)
        assertEquals(now - 10_000L, firstRun.newBaselineTimestampMs)
    }

    @Test
    fun `selectNotificationsToDispatch does not drop server notifications when device clock runs ahead`() {
        val serverNow = 1_700_000_000_000L
        val skewedDeviceNow = serverNow + 60 * 60_000L // device clock 1 hour ahead
        val initial = listOf(
            TeamNotification(
                id = "init_1",
                type = "idea",
                senderId = "other",
                createdAt = Timestamp(Date(serverNow))
            )
        )
        val firstRun = TeamNotificationDispatcher.selectNotificationsToDispatch(
            notifications = initial,
            userId = "me",
            hasSeededBaseline = false,
            baselineTimestampMs = 0L,
            dispatchedIds = emptySet(),
            nowMs = skewedDeviceNow
        )
        assertEquals(serverNow, firstRun.newBaselineTimestampMs)

        val genuineNew = TeamNotification(
            id = "new_idea_1",
            type = "idea",
            senderId = "other",
            createdAt = Timestamp(Date(serverNow + 15_000L))
        )
        val secondRun = TeamNotificationDispatcher.selectNotificationsToDispatch(
            notifications = initial + genuineNew,
            userId = "me",
            hasSeededBaseline = true,
            baselineTimestampMs = firstRun.newBaselineTimestampMs,
            dispatchedIds = firstRun.seedIds.toSet(),
            nowMs = skewedDeviceNow + 20_000L
        )
        assertEquals(listOf("new_idea_1"), secondRun.toDispatch.map { it.id })
    }

    @Test
    fun `selectNotificationsToDispatch dispatches only genuine new notifications in chronological order`() {
        val baseline = 1_700_000_000_000L
        val staleOutsideWindow = TeamNotification(
            id = "stale_old",
            type = "message",
            senderId = "other",
            createdAt = Timestamp(Date(baseline - 15 * 60_000L))
        )
        val alreadySeen = TeamNotification(
            id = "seen_id",
            type = "message",
            senderId = "other",
            createdAt = Timestamp(Date(baseline + 10_000L))
        )
        val alreadyRead = TeamNotification(
            id = "read_id",
            type = "idea",
            senderId = "other",
            readBy = listOf("me"),
            createdAt = Timestamp(Date(baseline + 20_000L))
        )
        val ownAction = TeamNotification(
            id = "own_id",
            type = "message",
            senderId = "me",
            createdAt = Timestamp(Date(baseline + 30_000L))
        )
        val newSecond = TeamNotification(
            id = "new_2",
            type = "broadcast",
            senderId = "mgr",
            createdAt = Timestamp(Date(baseline + 50_000L))
        )
        val newFirst = TeamNotification(
            id = "new_1",
            type = "message",
            senderId = "teammate",
            createdAt = Timestamp(Date(baseline + 40_000L))
        )

        val result = TeamNotificationDispatcher.selectNotificationsToDispatch(
            notifications = listOf(staleOutsideWindow, alreadySeen, alreadyRead, ownAction, newSecond, newFirst),
            userId = "me",
            hasSeededBaseline = true,
            baselineTimestampMs = baseline,
            dispatchedIds = setOf("seen_id"),
            nowMs = baseline + 60_000L
        )

        assertFalse(result.shouldSeedBaseline)
        assertEquals(listOf("new_1", "new_2"), result.toDispatch.map { it.id })
    }

    @Test
    fun `resolveChannelId maps notification types to dedicated OS channels`() {
        assertEquals(TeamNotificationDispatcher.CHANNEL_UPDATES, TeamNotificationDispatcher.resolveChannelId("update"))
        assertEquals(TeamNotificationDispatcher.CHANNEL_UPDATES, TeamNotificationDispatcher.resolveChannelId("UPDATE"))
        assertEquals(TeamNotificationDispatcher.CHANNEL_ANNOUNCEMENTS, TeamNotificationDispatcher.resolveChannelId("broadcast"))
        assertEquals(TeamNotificationDispatcher.CHANNEL_ANNOUNCEMENTS, TeamNotificationDispatcher.resolveChannelId("announcement"))
        assertEquals(TeamNotificationDispatcher.CHANNEL_MESSAGES_SILENT, TeamNotificationDispatcher.resolveChannelId("message"))
        assertEquals(TeamNotificationDispatcher.CHANNEL_TEAM, TeamNotificationDispatcher.resolveChannelId("file"))
        assertEquals(TeamNotificationDispatcher.CHANNEL_TEAM, TeamNotificationDispatcher.resolveChannelId("idea"))
        assertEquals(TeamNotificationDispatcher.CHANNEL_TEAM, TeamNotificationDispatcher.resolveChannelId("new_member"))
    }

    @Test
    fun `processNotifications seeds silently on first call and deduplicates across calls`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testUid = "test_user_${System.nanoTime()}"

        val firstBatch = listOf(
            TeamNotification(id = "hist_1", type = "message", senderId = "other", title = "Eski Mesaj")
        )
        // First run seeds silently -> 0 notifications posted
        assertEquals(0, TeamNotificationDispatcher.processNotifications(context, testUid, firstBatch))

        // Repeating same batch -> still 0
        assertEquals(0, TeamNotificationDispatcher.processNotifications(context, testUid, firstBatch))
    }

    @Test
    fun `shouldUserReceiveNotification hides historical shared alerts before userCreatedAtMs while keeping personal alerts`() {
        val joinTimeMs = 1_700_000_000_000L
        val oldMessage = TeamNotification(
            id = "old_msg",
            recipientUserId = "all",
            type = "message",
            targetRoomId = "general",
            senderId = "other",
            createdAt = Timestamp(Date(joinTimeMs - 120_000L))
        )
        val oldBroadcast = TeamNotification(
            id = "old_bcast",
            recipientUserId = "all",
            type = "broadcast",
            senderId = "mgr",
            createdAt = Timestamp(Date(joinTimeMs - 60_000L))
        )
        val newIdea = TeamNotification(
            id = "new_idea",
            recipientUserId = "all",
            type = "idea",
            senderId = "other",
            createdAt = Timestamp(Date(joinTimeMs + 10_000L))
        )
        val personalWelcome = TeamNotification(
            id = "personal_welcome",
            recipientUserId = "new_user",
            type = "member_approved",
            senderId = "mgr",
            createdAt = Timestamp(Date(joinTimeMs - 60_000L))
        )

        assertFalse(
            shouldUserReceiveNotification(
                notif = oldMessage,
                userId = "new_user",
                isManager = false,
                userTeam = "Yazılım Ekibi",
                userCreatedAtMs = joinTimeMs
            )
        )
        assertFalse(
            shouldUserReceiveNotification(
                notif = oldBroadcast,
                userId = "new_user",
                isManager = false,
                userTeam = "Yazılım Ekibi",
                userCreatedAtMs = joinTimeMs
            )
        )
        assertTrue(
            shouldUserReceiveNotification(
                notif = newIdea,
                userId = "new_user",
                isManager = false,
                userTeam = "Yazılım Ekibi",
                userCreatedAtMs = joinTimeMs
            )
        )
        assertTrue(
            shouldUserReceiveNotification(
                notif = personalWelcome,
                userId = "new_user",
                isManager = false,
                userTeam = "Yazılım Ekibi",
                userCreatedAtMs = joinTimeMs
            )
        )
    }

    @Test
    fun `selectNotificationsToDispatch excludes notifications created before userCreatedAtMs`() {
        val joinTimeMs = 1_700_000_500_000L
        val baseline = joinTimeMs - 30_000L
        val preJoin = TeamNotification(
            id = "pre_join",
            recipientUserId = "all",
            type = "message",
            senderId = "other",
            createdAt = Timestamp(Date(joinTimeMs - 20_000L))
        )
        val postJoin = TeamNotification(
            id = "post_join",
            recipientUserId = "all",
            type = "message",
            senderId = "other",
            createdAt = Timestamp(Date(joinTimeMs + 15_000L))
        )

        val result = TeamNotificationDispatcher.selectNotificationsToDispatch(
            notifications = listOf(preJoin, postJoin),
            userId = "new_user",
            hasSeededBaseline = true,
            baselineTimestampMs = baseline,
            dispatchedIds = emptySet(),
            nowMs = joinTimeMs + 20_000L,
            userCreatedAtMs = joinTimeMs
        )

        assertEquals(listOf("post_join"), result.toDispatch.map { it.id })
    }

    @Test
    fun `syncBaselineSilently marks backlog as seen and skips empty snapshot false seeding`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testUid = "sync_user_${System.nanoTime()}"

        // Empty snapshot on first call should not mark baseline seeded
        assertEquals(0, TeamNotificationDispatcher.processNotifications(context, testUid, emptyList()))
        assertFalse(TeamNotificationDispatcher.hasSeededBaseline(context, testUid))

        val backlog = listOf(
            TeamNotification(
                id = "backlog_1",
                type = "idea",
                senderId = "other",
                createdAt = Timestamp(Date(System.currentTimeMillis() - 10_000L))
            )
        )
        TeamNotificationDispatcher.syncBaselineSilently(context, testUid, backlog)
        assertTrue(TeamNotificationDispatcher.hasSeededBaseline(context, testUid))

        // Calling processNotifications with same backlog must not fire notifications
        assertEquals(0, TeamNotificationDispatcher.processNotifications(context, testUid, backlog))
    }

    @Test
    fun `findActivity safely unwraps nested ContextWrappers and returns null for Application context`() {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        assertNull(appContext.findActivity())

        val activity = Robolectric.buildActivity(Activity::class.java).create().get()
        val wrappedOnce = ContextWrapper(activity)
        val wrappedTwice = ContextWrapper(wrappedOnce)

        assertSame(activity, activity.findActivity())
        assertSame(activity, wrappedTwice.findActivity())
    }

    @Test
    fun `cleanupLegacyRealtimeSyncArtifacts clears legacy toggle pref and foreground sync channel`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("bsr_notif_dispatch_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("realtime_sync_opt_in", true).commit()

        val nm = context.getSystemService(android.app.NotificationManager::class.java)
        nm.createNotificationChannel(
            android.app.NotificationChannel(
                "bsr_realtime_sync",
                "Legacy",
                android.app.NotificationManager.IMPORTANCE_MIN
            )
        )
        nm.createNotificationChannel(
            android.app.NotificationChannel(
                "bsr_chat_messages_silent",
                "Legacy Silent",
                android.app.NotificationManager.IMPORTANCE_LOW
            )
        )
        nm.createNotificationChannel(
            android.app.NotificationChannel(
                "bsr_chat_messages_v2",
                "Legacy v2",
                android.app.NotificationManager.IMPORTANCE_DEFAULT
            )
        )

        TeamNotificationDispatcher.ensureNotificationChannels(context)
        TeamNotificationDispatcher.cleanupLegacyRealtimeSyncArtifacts(context)

        assertFalse(prefs.contains("realtime_sync_opt_in"))
        assertNull(nm.getNotificationChannel("bsr_realtime_sync"))
        assertNull(nm.getNotificationChannel("bsr_chat_messages_silent"))
        assertNull(nm.getNotificationChannel("bsr_chat_messages_v2"))
        assertTrue(nm.getNotificationChannel(TeamNotificationDispatcher.CHANNEL_TEAM) != null)
        val messagesChannel = nm.getNotificationChannel(TeamNotificationDispatcher.CHANNEL_MESSAGES)
        assertTrue(messagesChannel != null)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, messagesChannel!!.importance)
        assertTrue(messagesChannel.sound != null)
        assertTrue(nm.getNotificationChannel(TeamNotificationDispatcher.CHANNEL_ANNOUNCEMENTS) != null)
        assertTrue(nm.getNotificationChannel(TeamNotificationDispatcher.CHANNEL_UPDATES) != null)
    }

    @Test
    fun `filterChatDisplayRoles shows only Kaptan and Ogretmen and hides all other roles`() {
        assertEquals(
            emptyList<String>(),
            filterChatDisplayRoles(listOf("Yazılım Üyesi", "Elektronik Sorumlusu", "Yönetici", "Takım Kaptan Yardımcısı"))
        )
        assertEquals(
            listOf("Kaptan"),
            filterChatDisplayRoles(listOf("Takım Kaptanı", "Yazılım Üyesi", "Yönetici"))
        )
        assertEquals(
            listOf("Öğretmen"),
            filterChatDisplayRoles(listOf("Yetkili Öğretmen", "Danışman", "PR Sorumlusu"))
        )
        assertEquals(
            listOf("Öğretmen", "Kaptan"),
            filterChatDisplayRoles(listOf("Yetkili Öğretmen", "Takım Kaptanı", "Kaptan"))
        )
    }

    @Test
    fun `active chat room suppresses all OS notifications even beyond 10 messages`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val app = context.applicationContext as Application
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val notifManager = context.getSystemService(NotificationManager::class.java)!!
        val shadowNm = Shadows.shadowOf(notifManager)
        notifManager.cancelAll()

        val uid = "active_user_${System.nanoTime()}"
        try {
            TeamNotificationDispatcher.clearUserSyncState(context, uid)
            TeamNotificationDispatcher.syncBaselineSilently(context, uid, emptyList())
            TeamNotificationDispatcher.setAppForeground(context, uid, true)
            TeamNotificationDispatcher.setActiveChatRoom(context, uid, "general")

            val msgs = (1..12).map { idx ->
                TeamNotification(
                    id = "active_msg_$idx",
                    recipientUserId = "all",
                    type = "message",
                    targetRoomId = "general",
                    senderId = "other_sender",
                    title = "Yeni Mesaj",
                    message = "Mesaj $idx",
                    createdAt = Timestamp(Date(System.currentTimeMillis() + idx * 100L))
                )
            }

            val posted = TeamNotificationDispatcher.processNotifications(context, uid, msgs)
            assertEquals(0, posted)
            assertEquals(0, shadowNm.size())
            assertEquals(0, TeamNotificationDispatcher.getPendingMessageCount(context, uid, "general"))
        } finally {
            TeamNotificationDispatcher.setActiveChatRoom(context, uid, null)
            TeamNotificationDispatcher.setAppForeground(false)
            TeamNotificationDispatcher.clearUserSyncState(context, uid)
        }
    }

    @Test
    fun `inactive chat dispatches quiet room-grouped notifications immediately and clears on entering room`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val app = context.applicationContext as Application
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val notifManager = context.getSystemService(NotificationManager::class.java)!!
        val shadowNm = Shadows.shadowOf(notifManager)
        notifManager.cancelAll()

        val uid = "quiet_chat_user_${System.nanoTime()}"
        try {
            TeamNotificationDispatcher.clearUserSyncState(context, uid)
            TeamNotificationDispatcher.syncBaselineSilently(context, uid, emptyList())
            TeamNotificationDispatcher.setAppForeground(false)
            TeamNotificationDispatcher.setActiveChatRoom(context, uid, null)

            // 1st message in inactive room posts immediately as a quiet notification on CHANNEL_MESSAGES
            val first = TeamNotification(
                id = "sw_msg_1",
                recipientUserId = "all",
                type = "message",
                targetRoomId = "software",
                senderId = "other_sender",
                title = "Yazılım Odası",
                message = "İlk mesaj",
                createdAt = Timestamp(Date(System.currentTimeMillis() + 100L))
            )
            assertEquals(1, TeamNotificationDispatcher.processNotifications(context, uid, listOf(first)))
            assertEquals(1, shadowNm.size())
            assertEquals(1, TeamNotificationDispatcher.getPendingMessageCount(context, uid, "software"))

            val postedMsgNotif = shadowNm.allNotifications.first()
            assertEquals(TeamNotificationDispatcher.CHANNEL_MESSAGES, postedMsgNotif.channelId)

            // 2nd message updates the grouped room summary count and does not duplicate notification IDs
            val second = TeamNotification(
                id = "sw_msg_2",
                recipientUserId = "all",
                type = "message",
                targetRoomId = "software",
                senderId = "other_sender",
                title = "Yazılım Odası",
                message = "İkinci mesaj",
                createdAt = Timestamp(Date(System.currentTimeMillis() + 200L))
            )
            assertEquals(1, TeamNotificationDispatcher.processNotifications(context, uid, listOf(first, second)))
            assertEquals(2, TeamNotificationDispatcher.getPendingMessageCount(context, uid, "software"))

            // Opening the room clears pending count and summary notification
            TeamNotificationDispatcher.setActiveChatRoom(context, uid, "software")
            assertEquals(0, TeamNotificationDispatcher.getPendingMessageCount(context, uid, "software"))

            // Non-message notifications (e.g. idea) dispatch immediately on CHANNEL_TEAM
            val idea = TeamNotification(
                id = "instant_idea_1",
                recipientUserId = "all",
                type = "idea",
                senderId = "other_sender",
                title = "Yeni Fikir",
                message = "Otonom hedefleme",
                createdAt = Timestamp(Date(System.currentTimeMillis() + 3000L))
            )
            assertEquals(1, TeamNotificationDispatcher.processNotifications(context, uid, listOf(idea)))
            assertTrue(shadowNm.allNotifications.any { it.channelId == TeamNotificationDispatcher.CHANNEL_TEAM })
        } finally {
            TeamNotificationDispatcher.setAppForeground(false)
            TeamNotificationDispatcher.setActiveChatRoom(context, uid, null)
            TeamNotificationDispatcher.clearUserSyncState(context, uid)
        }
    }

    @Test
    fun `notifications arriving while POST_NOTIFICATIONS is denied are retained and dispatch after grant`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val app = context.applicationContext as Application
        val shadowApp = Shadows.shadowOf(app)
        shadowApp.denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val notifManager = context.getSystemService(NotificationManager::class.java)!!
        val shadowNm = Shadows.shadowOf(notifManager)
        notifManager.cancelAll()

        val uid = "perm_retry_user_${System.nanoTime()}"
        try {
            TeamNotificationDispatcher.clearUserSyncState(context, uid)
            TeamNotificationDispatcher.syncBaselineSilently(context, uid, emptyList())
            TeamNotificationDispatcher.setAppForeground(false)
            TeamNotificationDispatcher.setActiveChatRoom(context, uid, null)

            val pendingIdea = TeamNotification(
                id = "perm_idea_1",
                recipientUserId = "all",
                type = "idea",
                senderId = "other_sender",
                title = "Yeni Fikir",
                message = "İzin verildikten sonra gösterilmeli",
                createdAt = Timestamp(Date(System.currentTimeMillis() + 1000L))
            )

            // Denied permission -> 0 OS notifications posted, NOT dropped
            assertEquals(0, TeamNotificationDispatcher.processNotifications(context, uid, listOf(pendingIdea)))
            assertEquals(0, shadowNm.size())

            // Grant permission -> same notification dispatches cleanly
            shadowApp.grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
            assertEquals(1, TeamNotificationDispatcher.processNotifications(context, uid, listOf(pendingIdea)))
            assertEquals(1, shadowNm.size())
        } finally {
            shadowApp.grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
            TeamNotificationDispatcher.setAppForeground(false)
            TeamNotificationDispatcher.setActiveChatRoom(context, uid, null)
            TeamNotificationDispatcher.clearUserSyncState(context, uid)
        }
    }

    @Test
    fun `empty baseline seeding uses 0L so ahead device clock never drops first real messages`() {
        val serverNow = 1_700_000_000_000L
        val skewedDeviceNow = serverNow + 24 * 60 * 60_000L // device clock 24 hours ahead!
        val emptyFirstRun = TeamNotificationDispatcher.selectNotificationsToDispatch(
            notifications = emptyList(),
            userId = "me",
            hasSeededBaseline = false,
            baselineTimestampMs = 0L,
            dispatchedIds = emptySet(),
            nowMs = skewedDeviceNow
        )

        assertTrue(emptyFirstRun.shouldSeedBaseline)
        assertEquals(0L, emptyFirstRun.newBaselineTimestampMs)

        val incomingMsg = TeamNotification(
            id = "first_real_msg",
            recipientUserId = "all",
            type = "message",
            targetRoomId = "general",
            senderId = "teammate_1",
            createdAt = Timestamp(Date(serverNow))
        )
        val secondRun = TeamNotificationDispatcher.selectNotificationsToDispatch(
            notifications = listOf(incomingMsg),
            userId = "me",
            hasSeededBaseline = true,
            baselineTimestampMs = emptyFirstRun.newBaselineTimestampMs,
            dispatchedIds = emptySet(),
            nowMs = skewedDeviceNow + 5_000L
        )
        assertEquals(listOf("first_real_msg"), secondRun.toDispatch.map { it.id })
        assertEquals(serverNow, secondRun.newBaselineTimestampMs)
    }

    @Test
    fun `background service channel is silent low importance and continuous sync defaults enabled`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        TeamNotificationDispatcher.ensureNotificationChannels(context)
        val nm = context.getSystemService(NotificationManager::class.java)!!
        val bgChannel = nm.getNotificationChannel(TeamNotificationDispatcher.CHANNEL_BACKGROUND_SERVICE)
        assertTrue(bgChannel != null)
        assertEquals(NotificationManager.IMPORTANCE_MIN, bgChannel!!.importance)
        assertFalse(bgChannel.canShowBadge())

        assertTrue(RealtimeNotificationService.isContinuousSyncEnabled(context))
        val notif = TeamNotificationDispatcher.buildRealtimeServiceNotification(context)
        assertEquals(TeamNotificationDispatcher.CHANNEL_BACKGROUND_SERVICE, notif.channelId)
    }

    @Test
    fun `shouldUserReceiveNotification routes support tickets to managers and support replies to requester`() {
        val ticketNotif = TeamNotification(
            id = "sup_t1",
            recipientUserId = "managers",
            type = "support_ticket",
            senderId = "member_1",
            title = "Yeni Destek Talebi"
        )
        assertTrue(shouldUserReceiveNotification(ticketNotif, "mgr_1", isManager = true, userTeam = "Genel Yönetim"))
        assertFalse(shouldUserReceiveNotification(ticketNotif, "member_2", isManager = false, userTeam = "Yazılım Ekibi"))
        assertFalse(shouldUserReceiveNotification(ticketNotif, "member_1", isManager = true, userTeam = "Yazılım Ekibi"))

        val replyNotif = TeamNotification(
            id = "sup_r1",
            recipientUserId = "member_1",
            type = "support_reply",
            senderId = "mgr_1",
            title = "Destek Talebiniz Güncellendi"
        )
        assertTrue(shouldUserReceiveNotification(replyNotif, "member_1", isManager = false, userTeam = "Yazılım Ekibi"))
        assertFalse(shouldUserReceiveNotification(replyNotif, "member_2", isManager = false, userTeam = "Yazılım Ekibi"))

        val mechMsg = TeamNotification(
            id = "msg_mech_1",
            recipientUserId = "all",
            type = "message",
            targetRoomId = "mechanic",
            senderId = "mech_sender"
        )
        assertTrue(shouldUserReceiveNotification(mechMsg, "mgmt_u", isManager = false, userTeam = "Genel Yönetim"))
    }

    @Test
    fun `support models and curated FAQs provide valid categories and statuses`() {
        assertTrue(SupportFaqData.ITEMS.isNotEmpty())
        assertTrue(SupportFaqData.ITEMS.all { it.question.isNotBlank() && it.answer.isNotBlank() && it.category in SupportFaqData.CATEGORIES })
        assertEquals("Açık", SupportTicketStatus.displayName(SupportTicketStatus.OPEN))
        assertEquals("İnceleniyor", SupportTicketStatus.displayName(SupportTicketStatus.IN_PROGRESS))
        assertEquals("Çözüldü", SupportTicketStatus.displayName(SupportTicketStatus.RESOLVED))
        assertEquals(SupportTicketStatus.OPEN, SupportTicketStatus.normalize("unknown"))
    }
}
