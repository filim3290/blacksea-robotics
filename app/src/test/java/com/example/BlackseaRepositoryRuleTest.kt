package com.example

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.BlackseaRepository
import com.example.model.UserProfile
import com.example.model.UserRole
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
  fun testUnauthenticatedUserCannotCreateProfile() = runBlocking {
    val repository = BlackseaRepository(firestore)
    auth.signOut()

    try {
      withTimeout(5000L) {
        repository.createOrUpdateUserProfile(
          userId = "unauth_user",
          firstName = "Anon",
          lastName = "User",
          username = "anon",
          email = "anon@test.com",
          team = "Yazılım Ekibi",
          roles = listOf(UserRole.MEMBER.displayName),
          photoUrl = ""
        )
      }
      fail("Expected exception for unauthenticated write")
    } catch (e: Exception) {
      val isExpected = e is IllegalStateException || e.unwrapFirestoreException()?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
      assertTrue("Expected permission or auth failure, got: ${e.message}", isExpected)
    }
  }

  @Test
  fun testAuthenticatedUserCanCreateProfile() = runBlocking {
    val email = "member1_${System.nanoTime()}@blacksea.test"
    val uid = signInTestUser(email)
    val repository = BlackseaRepository(firestore)

    withTimeout(5000L) {
      repository.createOrUpdateUserProfile(
        userId = uid,
        firstName = "Kerem",
        lastName = "Kaptan",
        username = "kerem",
        email = email,
        team = "Yazılım Ekibi",
        roles = listOf(UserRole.CAPTAIN.displayName),
        photoUrl = ""
      )
    }

    val profile = withTimeout(5000L) { repository.observeUserProfile(uid).first { it != null } }
    assertNotNull(profile)
    assertEquals(uid, profile?.userId)
    assertEquals("Kerem Kaptan", profile?.getFullName())
  }

  @Test
  fun testSendAndObserveMessage() = runBlocking {
    val email = "writer_${System.nanoTime()}@blacksea.test"
    val uid = signInTestUser(email)
    val repository = BlackseaRepository(firestore)

    withTimeout(5000L) {
      repository.createOrUpdateUserProfile(
        userId = uid,
        firstName = "Mühendis",
        lastName = "Ali",
        username = "ali",
        email = email,
        team = "Yazılım Ekibi",
        roles = listOf(UserRole.MEMBER.displayName),
        photoUrl = ""
      )
    }
    signInTestUser("filim3290@gmail.com")
    withTimeout(5000L) {
      repository.updateUserRolesAndTeam(
        targetUserId = uid,
        team = "Yazılım Ekibi",
        roles = listOf(UserRole.MEMBER.displayName),
        status = UserStatus.APPROVED.value
      )
    }
    signInTestUser(email)

    withTimeout(5000L) {
      repository.sendMessage(
        roomId = "software",
        text = "Robot kolu PID parametreleri güncellendi.",
        senderName = "Mühendis Ali",
        senderTeam = "Yazılım Ekibi"
      )
    }

    val messages = withTimeout(5000L) {
      repository.observeMessages("software").first { list -> list.any { it.senderId == uid } }
    }
    assertTrue("Should have at least 1 message", messages.isNotEmpty())
    val lastMsg = messages.last { it.senderId == uid }
    assertEquals("software", lastMsg.roomId)
    assertEquals(uid, lastMsg.senderId)
    assertEquals("Robot kolu PID parametreleri güncellendi.", lastMsg.text)
  }

  @Test
  fun testPendingUserNotifiesManagersAndIsBlockedUntilApproved() = runBlocking {
    val founderUid = signInTestUser("filim3290@gmail.com")
    val repository = BlackseaRepository(firestore)
    withTimeout(5000L) {
      repository.createOrUpdateUserProfile(
        userId = founderUid,
        firstName = "Kurucu",
        lastName = "Yönetici",
        username = "kurucu",
        email = "filim3290@gmail.com",
        team = "Genel Yönetim",
        roles = listOf(UserRole.ADMIN.displayName),
        photoUrl = ""
      )
    }

    val applicantEmail = "applicant_${System.nanoTime()}@blacksea.test"
    val applicantUid = signInTestUser(applicantEmail)
    val created = withTimeout(5000L) {
      repository.createOrUpdateUserProfile(
        userId = applicantUid,
        firstName = "Yeni",
        lastName = "Aday",
        username = "aday",
        email = applicantEmail,
        team = "Mekanik Ekibi",
        roles = listOf("Mekanik Üyesi"),
        photoUrl = ""
      )
    }
    assertEquals(UserStatus.PENDING.value, created.status)

    // Pending user cannot send chat messages yet
    try {
      withTimeout(5000L) {
        repository.sendMessage(
          roomId = "general",
          text = "Onaysız mesaj",
          senderName = "Yeni Aday",
          senderTeam = "Mekanik Ekibi"
        )
      }
      fail("Pending user must be denied room message creation")
    } catch (e: Exception) {
      assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.unwrapFirestoreException()?.code)
    }

    // Manager should receive member_pending notification
    signInTestUser("filim3290@gmail.com")
    val managerNotifications = withTimeout(5000L) {
      repository.observeNotifications(
        userId = founderUid,
        isManager = true,
        userTeam = "Genel Yönetim"
      ).first { list ->
        list.any { it.type == "member_pending" && it.recipientUserId == "managers" && it.targetId == applicantUid }
      }
    }
    assertTrue(
      "Managers should receive member_pending notification",
      managerNotifications.any {
        it.type == "member_pending" &&
          it.recipientUserId == "managers" &&
          it.targetId == applicantUid
      }
    )
  }

  @Test
  fun testManagerCanRevokeReapplyAndDeleteUserAccount() = runBlocking {
    val founderUid = signInTestUser("filim3290@gmail.com")
    val repository = BlackseaRepository(firestore)
    withTimeout(5000L) {
      repository.createOrUpdateUserProfile(
        userId = founderUid,
        firstName = "Kurucu",
        lastName = "Yönetici",
        username = "kurucu",
        email = "filim3290@gmail.com",
        team = "Genel Yönetim",
        roles = listOf(UserRole.ADMIN.displayName),
        photoUrl = ""
      )
    }

    val memberEmail = "target_${System.nanoTime()}@blacksea.test"
    val memberUid = signInTestUser(memberEmail)
    withTimeout(5000L) {
      repository.createOrUpdateUserProfile(
        userId = memberUid,
        firstName = "Deneme",
        lastName = "Üye",
        username = "deneme",
        email = memberEmail,
        team = "Yazılım Ekibi",
        roles = listOf("Yazılım Üyesi"),
        photoUrl = ""
      )
    }

    // Founder approves then revokes member
    signInTestUser("filim3290@gmail.com")
    withTimeout(5000L) {
      repository.updateUserRolesAndTeam(
        targetUserId = memberUid,
        team = "Yazılım Ekibi",
        roles = listOf("Yazılım Üyesi"),
        status = UserStatus.APPROVED.value
      )
      repository.revokeUserAccess(memberUid)
    }
    val revokedProfile = withTimeout(5000L) { repository.getUserProfile(memberUid) }
    assertEquals(UserStatus.REVOKED.value, revokedProfile?.status)

    // Revoked user cannot write messages
    signInTestUser(memberEmail)
    try {
      withTimeout(5000L) {
        repository.sendMessage(
          roomId = "general",
          text = "Yetkisiz mesaj",
          senderName = "Deneme Üye",
          senderTeam = "Yazılım Ekibi"
        )
      }
      fail("Revoked user must be denied message creation")
    } catch (e: Exception) {
      assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.unwrapFirestoreException()?.code)
    }

    // Revoked user can reapply
    withTimeout(5000L) {
      repository.reapplyForMembership(memberUid, "Yazılım Ekibi")
    }
    val reappliedProfile = withTimeout(5000L) { repository.getUserProfile(memberUid) }
    assertEquals(UserStatus.PENDING.value, reappliedProfile?.status)

    // Founder can delete member
    signInTestUser("filim3290@gmail.com")
    withTimeout(5000L) {
      repository.deleteUserAccount(memberUid)
    }
    val deletedProfile = withTimeout(5000L) { repository.getUserProfile(memberUid) }
    assertEquals(null, deletedProfile)
  }
}
