package com.example

import com.example.data.shouldUserReceiveNotification
import com.example.model.BLACKSEA_AUTHORIZED_TEACHER_EMAIL
import com.example.model.BLACKSEA_AUTHORIZED_TEACHER_NAME
import com.example.model.PLATFORM_FOUNDER_EMAIL
import com.example.model.PLATFORM_MAIN_TEAM_ID
import com.example.model.PredefinedRoles
import com.example.model.SupportTicket
import com.example.model.TeamAppealStatus
import com.example.model.TeamInfo
import com.example.model.TeamMembership
import com.example.model.TeamNotification
import com.example.model.TeamReopenWatch
import com.example.model.TeamStatus
import com.example.model.TeamTaskStatus
import com.example.model.TeamType
import com.example.model.UserProfile
import com.example.model.UserStatus
import com.example.model.computeTeamActiveMemberCounts
import com.example.model.isLegacyBsrMainMember
import com.example.model.withTeamContext
import com.example.notifications.TeamNotificationDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MultiTeamArchitectureTest {

    @Test
    fun `mainBlackseaTeam factory and TeamStatus helpers behave deterministically`() {
        val main = TeamInfo.mainBlackseaTeam()
        assertEquals(PLATFORM_MAIN_TEAM_ID, main.id)
        assertTrue(main.isMainTeam)
        assertTrue(main.isApproved())
        assertFalse(main.isPending())
        assertFalse(main.isRejected())
        assertEquals("BSR", main.shortCode)

        assertEquals("Onay Bekliyor", TeamStatus.displayName(TeamStatus.PENDING))
        assertEquals("Onaylandı", TeamStatus.displayName(TeamStatus.APPROVED))
        assertEquals("Durduruldu", TeamStatus.displayName(TeamStatus.SUSPENDED))
        assertEquals("İncelemede", TeamStatus.displayName(TeamStatus.UNDER_REVIEW))
        assertEquals("Reddedildi", TeamStatus.displayName(TeamStatus.REJECTED))
        assertEquals("Onay Bekliyor", TeamStatus.displayName("unknown"))

        val suspendedTeam = main.copy(id = "team_test", isMainTeam = false, status = TeamStatus.SUSPENDED)
        assertTrue(suspendedTeam.isSuspended())
        assertTrue(suspendedTeam.isWorkspaceBlocked())
        assertFalse(suspendedTeam.isUnderReview())

        val reviewTeam = main.copy(id = "team_test2", isMainTeam = false, status = TeamStatus.UNDER_REVIEW)
        assertTrue(reviewTeam.isUnderReview())
        assertTrue(reviewTeam.isWorkspaceBlocked())
        assertEquals("İtiraz İnceleniyor", TeamAppealStatus.displayName(TeamAppealStatus.PENDING))
        assertFalse(PredefinedRoles.ASSIGNABLE_MANAGEMENT_ROLES.contains("Yönetici"))
        assertTrue(PredefinedRoles.ASSIGNABLE_MANAGEMENT_ROLES.contains("Yetkili Öğretmen"))
    }

    @Test
    fun `isPlatformManagerAccount returns true strictly for founder email`() {
        val fakeAdmin = UserProfile(
            id = "u_fake",
            userId = "u_fake",
            email = "teacher@example.com",
            role = "Yetkili Öğretmen",
            roles = listOf("Yetkili Öğretmen"),
            isPlatformAdmin = true,
            isSuperAdmin = true
        )
        assertFalse(fakeAdmin.isPlatformManagerAccount())
        assertTrue(fakeAdmin.isManagerAccount())

        val founder = fakeAdmin.copy(email = "  $PLATFORM_FOUNDER_EMAIL ")
        assertTrue(founder.isPlatformManagerAccount())
        assertTrue(founder.isManagerAccount())
    }

    @Test
    fun `TeamMembership isManagerInTeam requires active status and admin privilege`() {
        val activeCaptain = TeamMembership(
            teamId = "team_fatsa",
            userId = "u1",
            role = "Takım Kaptanı",
            roles = listOf("Takım Kaptanı"),
            status = UserStatus.ACTIVE.value,
            isTeamAdmin = false
        )
        assertTrue(activeCaptain.isManagerInTeam())

        val pendingCaptain = activeCaptain.copy(status = UserStatus.PENDING.value)
        assertFalse(pendingCaptain.isManagerInTeam())

        val revokedAdmin = activeCaptain.copy(status = UserStatus.REVOKED.value, isTeamAdmin = true)
        assertFalse(revokedAdmin.isManagerInTeam())

        val regularMember = TeamMembership(
            teamId = "team_fatsa",
            userId = "u2",
            role = "Yazılım Üyesi",
            roles = listOf("Yazılım Üyesi"),
            status = UserStatus.ACTIVE.value,
            isTeamAdmin = false
        )
        assertFalse(regularMember.isManagerInTeam())
    }

    @Test
    fun `withTeamContext isolates role and admin scope per team without leaking platform admin`() {
        val student = UserProfile(
            id = "u_ahmet",
            userId = "u_ahmet",
            firstName = "Ahmet",
            lastName = "Yılmaz",
            email = "ahmet@example.com",
            team = TeamType.GENERAL.displayName,
            role = "Takım Üyesi",
            roles = listOf("Takım Üyesi"),
            isPlatformAdmin = false,
            isSuperAdmin = false
        )

        val fatsaAdminMembership = TeamMembership(
            teamId = "team_fatsa",
            userId = "u_ahmet",
            subTeam = TeamType.MANAGEMENT.displayName,
            role = "Yönetici",
            roles = listOf("Yönetici", "Takım Kaptanı"),
            status = UserStatus.ACTIVE.value,
            isTeamAdmin = true
        )
        val bsrMemberMembership = TeamMembership(
            teamId = PLATFORM_MAIN_TEAM_ID,
            userId = "u_ahmet",
            subTeam = TeamType.SOFTWARE.displayName,
            role = "Yazılım Üyesi",
            roles = listOf("Yazılım Üyesi"),
            status = UserStatus.ACTIVE.value,
            isTeamAdmin = false
        )

        val fatsaCtx = student.withTeamContext("team_fatsa", fatsaAdminMembership)
        assertEquals("team_fatsa", fatsaCtx.activeTeamId)
        assertTrue(fatsaCtx.isSuperAdmin)
        assertTrue(fatsaCtx.isManagerAccount())
        assertFalse(fatsaCtx.isPlatformManagerAccount())
        assertEquals(TeamType.MANAGEMENT.displayName, fatsaCtx.team)

        val bsrCtx = student.withTeamContext(PLATFORM_MAIN_TEAM_ID, bsrMemberMembership)
        assertEquals(PLATFORM_MAIN_TEAM_ID, bsrCtx.activeTeamId)
        assertFalse(bsrCtx.isSuperAdmin)
        assertFalse(bsrCtx.isManagerAccount())
        assertFalse(bsrCtx.isPlatformManagerAccount())
        assertEquals(TeamType.SOFTWARE.displayName, bsrCtx.team)
    }

    @Test
    fun `shouldUserReceiveNotification isolates messages files and approvals strictly between teams`() {
        val ahmetMemberships = mapOf(
            PLATFORM_MAIN_TEAM_ID to TeamMembership(
                teamId = PLATFORM_MAIN_TEAM_ID,
                userId = "ahmet",
                subTeam = TeamType.SOFTWARE.displayName,
                role = "Yazılım Üyesi",
                roles = listOf("Yazılım Üyesi"),
                status = UserStatus.ACTIVE.value,
                isTeamAdmin = false
            ),
            "team_fatsa" to TeamMembership(
                teamId = "team_fatsa",
                userId = "ahmet",
                subTeam = TeamType.GENERAL.displayName,
                role = "Yönetici",
                roles = listOf("Yönetici"),
                status = UserStatus.ACTIVE.value,
                isTeamAdmin = true
            ),
            "team_drone" to TeamMembership(
                teamId = "team_drone",
                userId = "ahmet",
                subTeam = TeamType.MECHANIC.displayName,
                role = "Mekanik Üyesi",
                roles = listOf("Mekanik Üyesi"),
                status = UserStatus.PENDING.value,
                isTeamAdmin = false
            )
        )

        val bsrSoftwareMsg = TeamNotification(
            id = "n_bsr_sw",
            teamId = PLATFORM_MAIN_TEAM_ID,
            recipientUserId = "all",
            type = "message",
            targetRoomId = "software",
            senderId = "other"
        )
        val fatsaMechanicMsg = TeamNotification(
            id = "n_fatsa_mech",
            teamId = "team_fatsa",
            recipientUserId = "all",
            type = "message",
            targetRoomId = "mechanic",
            senderId = "other"
        )
        val unyeAiAnnouncement = TeamNotification(
            id = "n_unye_ann",
            teamId = "team_unye_ai",
            recipientUserId = "all",
            type = "announcement",
            senderId = "other"
        )
        val dronePendingMsg = TeamNotification(
            id = "n_drone_msg",
            teamId = "team_drone",
            recipientUserId = "all",
            type = "message",
            targetRoomId = "general",
            senderId = "other"
        )
        val fatsaPendingMember = TeamNotification(
            id = "n_fatsa_join",
            teamId = "team_fatsa",
            recipientUserId = "managers",
            type = "member_pending",
            senderId = "student_x"
        )
        val bsrPendingMember = TeamNotification(
            id = "n_bsr_join",
            teamId = PLATFORM_MAIN_TEAM_ID,
            recipientUserId = "managers",
            type = "member_pending",
            senderId = "student_y"
        )

        assertTrue(shouldUserReceiveNotification(bsrSoftwareMsg, "ahmet", ahmetMemberships, isPlatformAdmin = false))
        assertTrue(shouldUserReceiveNotification(fatsaMechanicMsg, "ahmet", ahmetMemberships, isPlatformAdmin = false))
        assertFalse("Must block alerts from teams user is not a member of",
            shouldUserReceiveNotification(unyeAiAnnouncement, "ahmet", ahmetMemberships, isPlatformAdmin = false))
        assertFalse("Must block alerts while membership is still pending approval",
            shouldUserReceiveNotification(dronePendingMsg, "ahmet", ahmetMemberships, isPlatformAdmin = false))
        assertTrue("Team admin of Fatsa receives Fatsa member_pending alerts",
            shouldUserReceiveNotification(fatsaPendingMember, "ahmet", ahmetMemberships, isPlatformAdmin = false))
        assertFalse("Non-admin of BSR must NOT receive BSR member_pending alerts",
            shouldUserReceiveNotification(bsrPendingMember, "ahmet", ahmetMemberships, isPlatformAdmin = false))
    }

    @Test
    fun `shouldUserReceiveNotification routes team applications strictly to BSR platform admins and founder`() {
        val teamAppPending = TeamNotification(
            id = "n_app_1",
            teamId = "platform_admin",
            recipientUserId = "platform_admins",
            type = "team_application_pending",
            senderId = "founder_1"
        )
        val appApprovedPersonal = TeamNotification(
            id = "n_app_ok",
            teamId = "team_new",
            recipientUserId = "founder_1",
            type = "team_application_approved",
            senderId = "bsr_admin"
        )
        val globalUpdate = TeamNotification(
            id = "n_upd_1",
            teamId = "platform",
            recipientUserId = "all",
            type = "update",
            senderId = "bsr_admin"
        )

        assertTrue(shouldUserReceiveNotification(teamAppPending, "bsr_admin", emptyMap(), isPlatformAdmin = true))
        assertFalse(shouldUserReceiveNotification(teamAppPending, "other_team_admin", emptyMap(), isPlatformAdmin = false))
        assertFalse(shouldUserReceiveNotification(teamAppPending, "founder_1", emptyMap(), isPlatformAdmin = true))

        val supportTicketNotif = TeamNotification(
            id = "n_sup_1",
            teamId = "platform_admin",
            recipientUserId = "platform_admins",
            type = "support_ticket",
            senderId = "student_a"
        )
        val appealNotif = TeamNotification(
            id = "n_app_appeal",
            teamId = "platform_admin",
            recipientUserId = "platform_admins",
            type = "team_appeal",
            senderId = "fatsa_teacher"
        )
        val nonFounderBsrTeacher = mapOf(
            PLATFORM_MAIN_TEAM_ID to TeamMembership(
                teamId = PLATFORM_MAIN_TEAM_ID,
                userId = "bsr_teacher",
                role = "Yetkili Öğretmen",
                roles = listOf("Yetkili Öğretmen"),
                status = UserStatus.ACTIVE.value,
                isTeamAdmin = true
            )
        )
        assertTrue(shouldUserReceiveNotification(supportTicketNotif, "founder_admin", emptyMap(), isPlatformAdmin = true))
        assertFalse("Yetkili Öğretmen in BSR must NOT receive platform support_ticket alerts",
            shouldUserReceiveNotification(supportTicketNotif, "bsr_teacher", nonFounderBsrTeacher, isPlatformAdmin = false))
        assertTrue(shouldUserReceiveNotification(appealNotif, "founder_admin", emptyMap(), isPlatformAdmin = true))
        assertFalse("Yetkili Öğretmen must NOT receive team_appeal alerts",
            shouldUserReceiveNotification(appealNotif, "bsr_teacher", nonFounderBsrTeacher, isPlatformAdmin = false))

        assertTrue(shouldUserReceiveNotification(appApprovedPersonal, "founder_1", emptyMap(), isPlatformAdmin = false))
        assertFalse(shouldUserReceiveNotification(appApprovedPersonal, "stranger", emptyMap(), isPlatformAdmin = false))

        assertTrue(shouldUserReceiveNotification(globalUpdate, "anyone", emptyMap(), isPlatformAdmin = false))
    }

    @Test
    fun `TeamTaskStatus and channel resolution work as expected`() {
        assertEquals(TeamTaskStatus.TODO, TeamTaskStatus.normalize(null))
        assertEquals(TeamTaskStatus.IN_PROGRESS, TeamTaskStatus.normalize("IN_PROGRESS"))
        assertEquals(TeamTaskStatus.DONE, TeamTaskStatus.normalize("done"))
        assertEquals("Yapılacak", TeamTaskStatus.displayName(TeamTaskStatus.TODO))
        assertEquals("Devam Ediyor", TeamTaskStatus.displayName(TeamTaskStatus.IN_PROGRESS))
        assertEquals("Tamamlandı", TeamTaskStatus.displayName(TeamTaskStatus.DONE))

        assertEquals(TeamNotificationDispatcher.CHANNEL_TEAM, TeamNotificationDispatcher.resolveChannelId("team_application_pending"))
        assertEquals(TeamNotificationDispatcher.CHANNEL_TEAM, TeamNotificationDispatcher.resolveChannelId("team_application_approved"))
        assertEquals(TeamNotificationDispatcher.CHANNEL_ANNOUNCEMENTS, TeamNotificationDispatcher.resolveChannelId("team_suspended"))
        assertEquals(TeamNotificationDispatcher.CHANNEL_ANNOUNCEMENTS, TeamNotificationDispatcher.resolveChannelId("team_under_review"))
        assertEquals(TeamNotificationDispatcher.CHANNEL_TEAM, TeamNotificationDispatcher.resolveChannelId("task"))
    }

    @Test
    fun `SupportTicket originTeamName and originSubTeamName parse team context reliably`() {
        val multiTeamTicket = SupportTicket(
            userName = "Ahmet Demir",
            userTeam = "Fatsa Robotik • Yazılım Ekibi"
        )
        assertEquals("Fatsa Robotik", multiTeamTicket.originTeamName())
        assertEquals("Yazılım Ekibi", multiTeamTicket.originSubTeamName())

        val bsrTicket = SupportTicket(
            userName = "Ayşe Yılmaz",
            userTeam = "BlackSea Robotics • Mekanik Ekibi"
        )
        assertEquals("BlackSea Robotics", bsrTicket.originTeamName())
        assertEquals("Mekanik Ekibi", bsrTicket.originSubTeamName())

        val legacyTicket = SupportTicket(
            userName = "Mehmet Kaya",
            userTeam = "PR & Medya"
        )
        assertEquals("BlackSea Robotics", legacyTicket.originTeamName())
        assertEquals("PR & Medya", legacyTicket.originSubTeamName())
    }

    @Test
    fun `TeamMembership suspension and appeal helpers behave deterministically`() {
        val revokedWithAppeal = TeamMembership(
            teamId = "team_fatsa",
            userId = "student_a",
            role = "Takım Kaptanı",
            roles = listOf("Takım Kaptanı"),
            status = UserStatus.REVOKED.value,
            isTeamAdmin = true,
            suspensionReason = "İzinsiz devamsızlık",
            appealText = "Sağlık raporum mevcuttur.",
            appealStatus = TeamAppealStatus.PENDING
        )
        assertTrue(revokedWithAppeal.isRevoked())
        assertTrue(revokedWithAppeal.hasPendingAppeal())
        assertFalse("Revoked membership cannot retain manager privileges", revokedWithAppeal.isManagerInTeam())

        val rejectedAppeal = revokedWithAppeal.copy(appealStatus = TeamAppealStatus.REJECTED)
        assertFalse(rejectedAppeal.hasPendingAppeal())
    }

    @Test
    fun `shouldUserReceiveNotification routes member appeals to team managers and isolates founder from BSR alerts`() {
        val fatsaManagerMemberships = mapOf(
            "team_fatsa" to TeamMembership(
                teamId = "team_fatsa",
                userId = "fatsa_mgr",
                role = "Yetkili Öğretmen",
                roles = listOf("Yetkili Öğretmen"),
                status = UserStatus.ACTIVE.value,
                isTeamAdmin = true
            )
        )
        val fatsaRevokedStudentMemberships = mapOf(
            "team_fatsa" to TeamMembership(
                teamId = "team_fatsa",
                userId = "student_a",
                role = "Yazılım Üyesi",
                roles = listOf("Yazılım Üyesi"),
                status = UserStatus.REVOKED.value,
                isTeamAdmin = false
            )
        )

        val memberAppealNotif = TeamNotification(
            id = "n_mem_appeal",
            teamId = "team_fatsa",
            recipientUserId = "managers",
            type = "member_appeal",
            targetId = "student_a",
            senderId = "student_a"
        )
        val personalRevokedNotif = TeamNotification(
            id = "n_revoked",
            teamId = "team_fatsa",
            recipientUserId = "student_a",
            type = "access_revoked",
            targetId = "team_fatsa",
            senderId = "fatsa_mgr"
        )
        val bsrRoomMsg = TeamNotification(
            id = "n_bsr_only",
            teamId = PLATFORM_MAIN_TEAM_ID,
            recipientUserId = "all",
            type = "message",
            targetRoomId = "general",
            senderId = "bsr_member"
        )

        assertTrue(shouldUserReceiveNotification(memberAppealNotif, "fatsa_mgr", fatsaManagerMemberships, isPlatformAdmin = false))
        assertFalse("Suspended member must not receive manager-only member_appeal alerts",
            shouldUserReceiveNotification(memberAppealNotif, "student_a", fatsaRevokedStudentMemberships, isPlatformAdmin = false))
        assertTrue("Suspended member still receives personal access_revoked notification",
            shouldUserReceiveNotification(personalRevokedNotif, "student_a", fatsaRevokedStudentMemberships, isPlatformAdmin = false))
        assertFalse("Founder without BSR membership should not receive BSR room chat notifications",
            shouldUserReceiveNotification(bsrRoomMsg, "founder_u", emptyMap(), isPlatformAdmin = true))
    }

    @Test
    fun `mehmetilkerbas is configured as BlackSea Robotics authorized teacher without platform admin`() {
        assertEquals("mehmetilkerbas@gmail.com", BLACKSEA_AUTHORIZED_TEACHER_EMAIL)
        val mainTeam = TeamInfo.mainBlackseaTeam()
        assertEquals(BLACKSEA_AUTHORIZED_TEACHER_NAME, mainTeam.managerName)
        assertEquals(BLACKSEA_AUTHORIZED_TEACHER_EMAIL, mainTeam.managerContact)

        val teacherProfile = UserProfile(
            id = "ilker_u",
            userId = "ilker_u",
            email = "MEHMETILKERBAS@gmail.com",
            displayName = BLACKSEA_AUTHORIZED_TEACHER_NAME
        )
        assertTrue(teacherProfile.isBlackseaAuthorizedTeacherAccount())
        assertTrue(teacherProfile.isManagerAccount())
        assertFalse("Yetkili öğretmen must not gain platform manager powers", teacherProfile.isPlatformManagerAccount())

        val bsrMembership = TeamMembership(
            teamId = PLATFORM_MAIN_TEAM_ID,
            userId = "ilker_u",
            subTeam = TeamType.MANAGEMENT.displayName,
            role = "Yetkili Öğretmen",
            roles = listOf("Yetkili Öğretmen"),
            status = UserStatus.ACTIVE.value,
            isTeamAdmin = true
        )
        val otherStudentMembership = TeamMembership(
            teamId = "other_team",
            userId = "ilker_u",
            subTeam = TeamType.SOFTWARE.displayName,
            role = "Yazılım Üyesi",
            roles = listOf("Yazılım Üyesi"),
            status = UserStatus.ACTIVE.value,
            isTeamAdmin = false
        )
        val inBsr = teacherProfile.withTeamContext(PLATFORM_MAIN_TEAM_ID, bsrMembership)
        assertTrue(inBsr.isSuperAdmin)
        assertFalse(inBsr.isPlatformAdmin)
        assertEquals("Yetkili Öğretmen", inBsr.role)

        val inOther = teacherProfile.withTeamContext("other_team", otherStudentMembership)
        assertFalse("Authority in other teams follows that team's membership", inOther.isSuperAdmin)
    }

    @Test
    fun `teamless users receive team_reopened_watch and support_reply personal notifications`() {
        val watch = TeamReopenWatch(
            id = "team_fatsa_teamless_u",
            teamId = "team_fatsa",
            userId = "teamless_u"
        )
        assertEquals("team_fatsa", watch.teamId)

        val reopenedNotif = TeamNotification(
            id = "n_reopen_1",
            teamId = "team_fatsa",
            teamName = "Fatsa Robotik",
            recipientUserId = "teamless_u",
            type = "team_reopened_watch",
            targetId = "team_fatsa",
            senderId = "founder_u"
        )
        val supportReplyNotif = TeamNotification(
            id = "n_sup_reply_1",
            teamId = "blacksea_robotics",
            recipientUserId = "teamless_u",
            type = "support_reply",
            targetId = "ticket_9",
            senderId = "founder_u"
        )

        assertEquals(TeamNotificationDispatcher.CHANNEL_ANNOUNCEMENTS, TeamNotificationDispatcher.resolveChannelId("team_reopened_watch"))
        assertEquals(TeamNotificationDispatcher.CHANNEL_ANNOUNCEMENTS, TeamNotificationDispatcher.resolveChannelId("team_resumed"))
        assertTrue("Teamless user must receive team_reopened_watch alert",
            shouldUserReceiveNotification(reopenedNotif, "teamless_u", emptyMap(), isPlatformAdmin = false))
        assertTrue("Teamless user must receive personal support_reply alert",
            shouldUserReceiveNotification(supportReplyNotif, "teamless_u", emptyMap(), isPlatformAdmin = false))
    }

    @Test
    fun `computeTeamActiveMemberCounts and isLegacyBsrMainMember eliminate count drift across screens`() {
        val founderProfile = UserProfile(
            id = "u_founder",
            userId = "u_founder",
            email = PLATFORM_FOUNDER_EMAIL,
            role = "Yönetici",
            roles = listOf("Yönetici"),
            status = UserStatus.ACTIVE.value,
            activeTeamId = PLATFORM_MAIN_TEAM_ID
        )
        val bsrTeacherProfile = UserProfile(
            id = "u_ilker",
            userId = "u_ilker",
            email = BLACKSEA_AUTHORIZED_TEACHER_EMAIL,
            role = "Yetkili Öğretmen",
            roles = listOf("Yetkili Öğretmen"),
            status = UserStatus.ACTIVE.value,
            activeTeamId = ""
        )
        val legacyRemovedUser = UserProfile(
            id = "u_removed_legacy",
            userId = "u_removed_legacy",
            email = "removed@example.com",
            team = TeamType.GENERAL.displayName,
            role = "Takım Üyesi",
            roles = listOf("Takım Üyesi"),
            status = UserStatus.ACTIVE.value,
            activeTeamId = ""
        )
        val revokedLegacyBsrUser = UserProfile(
            id = "u_revoked_bsr",
            userId = "u_revoked_bsr",
            email = "revoked@example.com",
            role = "Yazılım Üyesi",
            roles = listOf("Yazılım Üyesi"),
            status = UserStatus.ACTIVE.value,
            activeTeamId = PLATFORM_MAIN_TEAM_ID
        )

        assertFalse("Founder must never count as a legacy BSR member", founderProfile.isLegacyBsrMainMember())
        assertTrue("Authorized BSR teacher must be recognized in legacy fallback", bsrTeacherProfile.isLegacyBsrMainMember())
        assertFalse("Reset teamless user must NOT be recognized as BSR member", legacyRemovedUser.isLegacyBsrMainMember())

        val memberships = listOf(
            // Stray founder doc that must be ignored
            TeamMembership(
                teamId = PLATFORM_MAIN_TEAM_ID,
                userId = "u_founder",
                userEmail = PLATFORM_FOUNDER_EMAIL,
                status = UserStatus.ACTIVE.value
            ),
            // Two active BSR students
            TeamMembership(
                teamId = PLATFORM_MAIN_TEAM_ID,
                userId = "u_s1",
                userEmail = "s1@example.com",
                status = UserStatus.ACTIVE.value
            ),
            TeamMembership(
                teamId = PLATFORM_MAIN_TEAM_ID,
                userId = "u_s2",
                userEmail = "s2@example.com",
                status = UserStatus.ACTIVE.value
            ),
            // Pending & revoked BSR docs (must not count, and u_revoked_bsr must not be resurrected from /users)
            TeamMembership(
                teamId = PLATFORM_MAIN_TEAM_ID,
                userId = "u_pending",
                userEmail = "pending@example.com",
                status = UserStatus.PENDING.value
            ),
            TeamMembership(
                teamId = PLATFORM_MAIN_TEAM_ID,
                userId = "u_revoked_bsr",
                userEmail = "revoked@example.com",
                status = UserStatus.REVOKED.value
            ),
            // Another team with 2 active + 1 pending + 1 rejected
            TeamMembership(
                teamId = "team_fatsa",
                userId = "u_s1",
                userEmail = "s1@example.com",
                status = UserStatus.ACTIVE.value
            ),
            TeamMembership(
                teamId = "team_fatsa",
                userId = "u_f2",
                userEmail = "f2@example.com",
                status = UserStatus.ACTIVE.value
            ),
            TeamMembership(
                teamId = "team_fatsa",
                userId = "u_f_pending",
                userEmail = "fp@example.com",
                status = UserStatus.PENDING.value
            ),
            TeamMembership(
                teamId = "team_fatsa",
                userId = "u_f_rejected",
                userEmail = "fr@example.com",
                status = UserStatus.REJECTED.value
            )
        )

        val counts = computeTeamActiveMemberCounts(
            memberships = memberships,
            users = listOf(founderProfile, bsrTeacherProfile, legacyRemovedUser, revokedLegacyBsrUser)
        )
        // Expected BSR: u_s1 + u_s2 + bsrTeacherProfile = 3 (founder ignored, pending/revoked ignored, reset legacy ignored)
        assertEquals(3, counts[PLATFORM_MAIN_TEAM_ID])
        assertEquals(2, counts["team_fatsa"])

        val afterRemoval = computeTeamActiveMemberCounts(
            memberships = memberships,
            users = listOf(founderProfile, bsrTeacherProfile, legacyRemovedUser, revokedLegacyBsrUser),
            excludeUserId = "u_s1"
        )
        assertEquals(2, afterRemoval[PLATFORM_MAIN_TEAM_ID])
        assertEquals(1, afterRemoval["team_fatsa"])
    }

    @Test
    fun `TeamMembership normalizes manager roles and UserProfile stores app version metadata`() {
        val asciiTeacher = TeamMembership(
            teamId = "t_1",
            userId = "u_1",
            role = "yetkili ogretmen",
            roles = emptyList(),
            status = UserStatus.ACTIVE.value
        )
        val asciiCaptain = TeamMembership(
            teamId = "t_1",
            userId = "u_2",
            role = "Takim Kaptani",
            roles = listOf("takim kaptani"),
            status = UserStatus.ACTIVE.value
        )
        val regularMember = TeamMembership(
            teamId = "t_1",
            userId = "u_3",
            role = "Yazılım Üyesi",
            roles = listOf("Yazılım Üyesi"),
            status = UserStatus.ACTIVE.value,
            isTeamAdmin = false
        )

        assertTrue(asciiTeacher.isManagerInTeam())
        assertTrue(asciiCaptain.isManagerInTeam())
        assertFalse(regularMember.isManagerInTeam())

        val userWithVersion = UserProfile(
            id = "u_1",
            userId = "u_1",
            appVersionName = "1.0.12",
            appVersionCode = 13
        )
        assertEquals("1.0.12", userWithVersion.appVersionName)
        assertEquals(13, userWithVersion.appVersionCode)
    }

    @Test
    fun `resolvePlatformUserTeamSummaries excludes non-existent deleted rejected and pending teams`() {
        val existingApprovedTeam = TeamInfo(
            id = "team_existing",
            name = "Trabzon Tech",
            status = TeamStatus.APPROVED
        )
        val existingSuspendedTeam = TeamInfo(
            id = "team_suspended",
            name = "Rize Robo",
            status = TeamStatus.SUSPENDED
        )
        val pendingCreationTeam = TeamInfo(
            id = "team_pending_creation",
            name = "Henüz Onaysız",
            status = TeamStatus.PENDING
        )
        val rejectedCreationTeam = TeamInfo(
            id = "team_rejected_creation",
            name = "Reddedilmiş Ekip",
            status = TeamStatus.REJECTED
        )
        val allTeams = listOf(
            TeamInfo.mainBlackseaTeam(),
            existingApprovedTeam,
            existingSuspendedTeam,
            pendingCreationTeam,
            rejectedCreationTeam
        )

        val student = UserProfile(
            id = "student_1",
            userId = "student_1",
            email = "student1@example.com",
            team = TeamType.GENERAL.displayName,
            role = "Takım Üyesi",
            roles = listOf("Takım Üyesi"),
            status = UserStatus.ACTIVE.value,
            activeTeamId = ""
        )

        val memberships = listOf(
            // Phantom orphaned membership pointing to a deleted team that does NOT exist in allTeams
            TeamMembership(
                teamId = "team_deleted_phantom_99",
                userId = "student_1",
                subTeam = TeamType.SOFTWARE.displayName,
                role = "Yazılım Üyesi",
                roles = listOf("Yazılım Üyesi"),
                status = UserStatus.ACTIVE.value
            ),
            // Membership pointing to rejected/unapproved teams must be hidden
            TeamMembership(
                teamId = "team_rejected_creation",
                userId = "student_1",
                subTeam = TeamType.MECHANIC.displayName,
                role = "Mekanik Üyesi",
                roles = listOf("Mekanik Üyesi"),
                status = UserStatus.ACTIVE.value
            ),
            TeamMembership(
                teamId = "team_pending_creation",
                userId = "student_1",
                subTeam = TeamType.PR.displayName,
                role = "PR Üyesi",
                roles = listOf("PR Üyesi"),
                status = UserStatus.ACTIVE.value
            ),
            // Rejected join request on an existing team must NOT appear
            TeamMembership(
                teamId = "team_existing",
                userId = "student_1",
                subTeam = TeamType.SOFTWARE.displayName,
                role = "Yazılım Üyesi",
                roles = listOf("Yazılım Üyesi"),
                status = UserStatus.REJECTED.value
            ),
            // Pending join request on an existing suspended team must be separated as pending application
            TeamMembership(
                teamId = "team_suspended",
                userId = "student_1",
                subTeam = TeamType.SOFTWARE.displayName,
                role = "Yazılım Üyesi",
                roles = listOf("Yazılım Üyesi"),
                status = UserStatus.PENDING.value
            )
        )

        val resolved = com.example.ui.screens.resolvePlatformUserTeamSummaries(
            user = student,
            userMemberships = memberships,
            allTeams = allTeams
        )
        assertEquals(1, resolved.size)
        assertEquals("Rize Robo", resolved.first().teamName)
        assertTrue(resolved.first().isPendingApplication)
        assertTrue("Pending applicant should have 0 active/revoked teams", resolved.none { !it.isPendingApplication })

        // Active member on an existing team shows up normally; phantom deleted team still excluded
        val activeStudentSummaries = com.example.ui.screens.resolvePlatformUserTeamSummaries(
            user = student,
            userMemberships = listOf(
                TeamMembership(
                    teamId = "team_deleted_phantom_99",
                    userId = "student_1",
                    status = UserStatus.ACTIVE.value
                ),
                TeamMembership(
                    teamId = "team_existing",
                    userId = "student_1",
                    subTeam = TeamType.SOFTWARE.displayName,
                    role = "Yazılım Üyesi",
                    roles = listOf("Yazılım Üyesi"),
                    status = UserStatus.ACTIVE.value
                )
            ),
            allTeams = allTeams
        )
        assertEquals(1, activeStudentSummaries.size)
        assertEquals("Trabzon Tech", activeStudentSummaries.first().teamName)
        assertFalse(activeStudentSummaries.first().isPendingApplication)

        // Rejected BSR membership must NOT resurrect user as BlackSea Robotics active member
        val legacyWithRejectedBsr = student.copy(
            role = "Yazılım Üyesi",
            roles = listOf("Yazılım Üyesi"),
            activeTeamId = PLATFORM_MAIN_TEAM_ID
        )
        val rejectedBsrSummaries = com.example.ui.screens.resolvePlatformUserTeamSummaries(
            user = legacyWithRejectedBsr,
            userMemberships = listOf(
                TeamMembership(
                    teamId = PLATFORM_MAIN_TEAM_ID,
                    userId = "student_1",
                    status = UserStatus.REJECTED.value
                )
            ),
            allTeams = allTeams
        )
        assertTrue(rejectedBsrSummaries.isEmpty())
    }
}
