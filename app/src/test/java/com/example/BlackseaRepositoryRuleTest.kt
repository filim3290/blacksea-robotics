package com.example

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.BlackseaRepository
import com.example.model.PLATFORM_MAIN_TEAM_ID
import com.example.model.TeamInfo
import com.example.model.TeamStatus
import com.example.model.TeamType
import com.example.model.UserProfile
import com.example.model.UserStatus
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BlackseaRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun testUnauthenticatedUserCannotSubmitTeamOrMessage() = runBlocking {
    val repository = BlackseaRepository(firestore)
    auth.signOut()

    val teamResult = withTimeout(5000L) {
      repository.submitTeamCreationRequest(
        name = "Yetkisiz Takım",
        shortCode = "YT",
        competition = "TEKNOFEST",
        schoolName = "Deneme Lisesi",
        city = "Ordu",
        description = "Açıklama metni",
        motto = "",
        logoKey = "rocket",
        accentColorHex = "#0284C7",
        managerName = "Anon",
        managerContact = "anon@test.com",
        founderEmail = "anon@test.com",
        founderPhotoUrl = ""
      )
    }
    assertTrue("Expected failure for unauthenticated team submission", teamResult.isFailure)
  }

  @Test
  fun testGoogleSignInAutoCreatesProfileAndPendingJoinWorkflow() = runBlocking {
    val email = "student_${System.nanoTime()}@blacksea.test"
    val uid = signInTestUser(email)
    val repository = BlackseaRepository(firestore)

    val profileRes = withTimeout(5000L) {
      repository.ensureBaseUserProfileOnGoogleSignIn(
        userId = uid,
        email = email,
        displayName = "Kerem Yılmaz",
        photoUrl = ""
      )
    }
    assertTrue(profileRes.isSuccess)

    val loaded = withTimeout(5000L) { repository.observeUserProfile(uid).first { it != null } }
    assertNotNull(loaded)
    assertEquals(uid, loaded?.userId)
    assertEquals("Kerem Yılmaz", loaded?.getFullName())

    // Student applies to join BlackSea Robotics -> starts as pending
    val joinRes = withTimeout(5000L) {
      repository.requestJoinTeam(
        team = TeamInfo.mainBlackseaTeam(),
        profile = loaded!!,
        subTeam = TeamType.SOFTWARE.displayName,
        joinNote = "Otonom sürüş ekibinde yer almak istiyorum"
      )
    }
    assertTrue(joinRes.isSuccess)

    val memberships = withTimeout(5000L) {
      repository.observeMyMemberships(uid).first { it.any { m -> m.teamId == PLATFORM_MAIN_TEAM_ID } }
    }
    val bsrMem = memberships.first { it.teamId == PLATFORM_MAIN_TEAM_ID }
    assertTrue(bsrMem.isPending())

    // Pending member cannot post messages to BSR rooms before approval
    try {
      withTimeout(5000L) {
        repository.sendMessage(
          roomId = "software",
          text = "Onaysız mesaj",
          senderName = "Kerem Yılmaz",
          senderTeam = TeamType.SOFTWARE.displayName,
          teamId = PLATFORM_MAIN_TEAM_ID
        )
      }
      fail("Pending member must be denied room message creation")
    } catch (e: Exception) {
      assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.unwrapFirestoreException()?.code)
    }
  }

  @Test
  fun testTeamCreationApprovalAndIsolatedWorkspaceMessaging() = runBlocking {
    val founderUid = signInTestUser("filim3290@gmail.com")
    val repository = BlackseaRepository(firestore)
    withTimeout(5000L) {
      repository.ensureBaseUserProfileOnGoogleSignIn(
        userId = founderUid,
        email = "filim3290@gmail.com",
        displayName = "BSR Kurucu",
        photoUrl = ""
      )
    }

    val creatorEmail = "fatsa_${System.nanoTime()}@blacksea.test"
    val creatorUid = signInTestUser(creatorEmail)
    val teamReqRes = withTimeout(5000L) {
      repository.submitTeamCreationRequest(
        name = "Fatsa Robotik",
        shortCode = "FRT",
        competition = "TEKNOFEST",
        schoolName = "Fatsa Fen Lisesi",
        city = "Ordu",
        description = "TEKNOFEST İHA ve otonom araç ekibi",
        motto = "Geleceği Kodla",
        logoKey = "drone",
        accentColorHex = "#059669",
        managerName = "Ahmet Demir",
        managerContact = creatorEmail,
        founderEmail = creatorEmail,
        founderPhotoUrl = ""
      )
    }
    assertTrue(teamReqRes.isSuccess)
    val newTeamId = teamReqRes.getOrThrow()

    // Pending team appears for platform admin
    signInTestUser("filim3290@gmail.com")
    val pendingList = withTimeout(5000L) {
      repository.observePendingTeamApplications().first { list -> list.any { it.id == newTeamId } }
    }
    val pendingTeam = pendingList.first { it.id == newTeamId }
    assertEquals(TeamStatus.PENDING, pendingTeam.status)

    // Platform admin approves team -> activates workspace & creator membership
    val approveRes = withTimeout(5000L) {
      repository.approveTeamCreationRequest(pendingTeam)
    }
    assertTrue(approveRes.isSuccess)

    // Creator can now send isolated messages in Fatsa Robotik
    signInTestUser(creatorEmail)
    withTimeout(5000L) {
      repository.sendMessage(
        roomId = "general",
        text = "Fatsa Robotik odası açıldı!",
        senderName = "Ahmet Demir",
        senderTeam = TeamType.MANAGEMENT.displayName,
        teamId = newTeamId,
        teamName = "Fatsa Robotik"
      )
    }

    val fatsaMsgs = withTimeout(5000L) {
      repository.observeMessages("general", teamId = newTeamId).first { it.any { m -> m.senderId == creatorUid } }
    }
    assertEquals("Fatsa Robotik odası açıldı!", fatsaMsgs.last().text)
  }

  @Test
  fun testTeamAdminCanApproveRevokeAndRemoveMember() = runBlocking {
    val founderUid = signInTestUser("filim3290@gmail.com")
    val repository = BlackseaRepository(firestore)
    withTimeout(5000L) {
      repository.ensureBaseUserProfileOnGoogleSignIn(founderUid, "filim3290@gmail.com", "BSR Admin", "")
    }

    val memberEmail = "uye_${System.nanoTime()}@blacksea.test"
    val memberUid = signInTestUser(memberEmail)
    val memberProfile = withTimeout(5000L) {
      repository.ensureBaseUserProfileOnGoogleSignIn(memberUid, memberEmail, "Deneme Üye", "").getOrThrow()
    }
    withTimeout(5000L) {
      repository.requestJoinTeam(TeamInfo.mainBlackseaTeam(), memberProfile, TeamType.SOFTWARE.displayName)
    }

    signInTestUser("filim3290@gmail.com")
    withTimeout(5000L) {
      repository.updateMembershipRolesAndStatus(
        team = TeamInfo.mainBlackseaTeam(),
        targetUserId = memberUid,
        subTeam = TeamType.SOFTWARE.displayName,
        roles = listOf("Yazılım Üyesi"),
        status = UserStatus.ACTIVE.value
      )
      repository.revokeTeamMembership(TeamInfo.mainBlackseaTeam(), memberUid)
    }

    val revokedMem = withTimeout(5000L) {
      repository.observeTeamMemberships(PLATFORM_MAIN_TEAM_ID)
        .first { list -> list.any { it.userId == memberUid && it.isRevoked() } }
        .first { it.userId == memberUid }
    }
    assertEquals(UserStatus.REVOKED.value, revokedMem.status)

    withTimeout(5000L) {
      repository.removeTeamMembership(TeamInfo.mainBlackseaTeam(), memberUid)
    }
    val remaining = withTimeout(5000L) { repository.observeTeamMemberships(PLATFORM_MAIN_TEAM_ID).first() }
    assertTrue(remaining.none { it.userId == memberUid })
  }
}
