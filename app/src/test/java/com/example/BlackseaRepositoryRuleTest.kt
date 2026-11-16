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

  @Test
  fun testFirstUserInEmptySystemBecomesAutoAdminAndCanDeleteOwnAccount() = runBlocking {
    // Clear emulator database so users collection is guaranteed empty regardless of test order
    val clearUrl = java.net.URL(
      "http://$EMULATOR_HOST:$FIRESTORE_PORT/emulator/v1/projects/$PROJECT_ID/databases/$databaseId/documents"
    )
    val conn = (clearUrl.openConnection() as java.net.HttpURLConnection).apply {
      requestMethod = "DELETE"
      connectTimeout = 3000
      readTimeout = 3000
    }
    assertEquals(200, conn.responseCode)
    conn.disconnect()

    val repository = BlackseaRepository(firestore)
    val firstEmail = "first_user_${System.nanoTime()}@blacksea.test"
    val firstUid = signInTestUser(firstEmail)

    assertTrue(
      "Empty users collection should detect first user eligibility",
      withTimeout(5000L) { repository.isSystemEmptyForNewUser(excludeUserId = firstUid) }
    )

    val created = withTimeout(5000L) {
      repository.createOrUpdateUserProfile(
        userId = firstUid,
        firstName = "İlk",
        lastName = "Kurucu",
        username = "ilkkurucu",
        email = firstEmail,
        team = "Yazılım Ekibi",
        roles = listOf("Yazılım Üyesi"),
        photoUrl = ""
      )
    }

    assertEquals(UserStatus.ACTIVE.value, created.status)
    assertTrue("First user must receive super admin privilege", created.isSuperAdmin)
    assertTrue("First user must receive Yönetici role", created.roles.contains(UserRole.ADMIN.displayName))
    assertTrue("First user must receive active access immediately", UserStatus.isActive(created.status))

    // First user can immediately send room messages without waiting for approval
    withTimeout(5000L) {
      repository.sendMessage(
        roomId = "general",
        text = "İlk yönetici olarak sistem açıldı.",
        senderName = "İlk Kurucu",
        senderTeam = "Yazılım Ekibi"
      )
    }

    // Create a personal notification targeting this user, then verify deleteOwnAccount cleans both
    withTimeout(5000L) {
      repository.createNotification(
        recipientUserId = firstUid,
        title = "Hoş Geldiniz",
        message = "Kişisel bildirim",
        type = "member_approved",
        targetId = firstUid,
        senderId = "system_admin"
      )
    }

    withTimeout(5000L) {
      repository.deleteOwnAccount()
    }

    // Verify user document is deleted (re-authenticate as same email because deleteOwnAccount may delete auth user)
    val checkUid = signInTestUser(firstEmail)
    val afterDeleteProfile = withTimeout(5000L) { repository.getUserProfile(firstUid) }
    assertEquals(null, afterDeleteProfile)
    assertTrue(checkUid.isNotBlank())
  }

  @Test
  fun testLegacyUserWithoutStatusFieldCanSendAndObserveMessages() = runBlocking {
    val legacyEmail = "legacy_${System.nanoTime()}@blacksea.test"
    val legacyUid = signInTestUser(legacyEmail)
    val repository = BlackseaRepository(firestore)

    // Write a legacy-shaped user document without 'status' field (as older app versions did)
    withTimeout(5000L) {
      val legacyPayload = mapOf(
        "userId" to legacyUid,
        "firstName" to "Eski",
        "lastName" to "Sürüm",
        "username" to "eskisurum",
        "displayName" to "Eski Sürüm",
        "email" to legacyEmail,
        "team" to "Mekanik Ekibi",
        "role" to "Mekanik Üyesi",
        "roles" to listOf("Mekanik Üyesi")
      )
      firestore.collection("users").document(legacyUid).set(legacyPayload).let { task ->
        com.google.android.gms.tasks.Tasks.await(task)
      }
    }

    val loaded = withTimeout(5000L) { repository.getUserProfile(legacyUid) }
    assertNotNull(loaded)

    withTimeout(5000L) {
      repository.sendMessage(
        roomId = "mechanic",
        text = "Eski sürümden gönderilen mesaj",
        senderName = "Eski Sürüm",
        senderTeam = "Mekanik Ekibi"
      )
    }

    val messages = withTimeout(5000L) {
      repository.observeMessages("mechanic").first { list -> list.any { it.senderId == legacyUid } }
    }
    assertTrue("Legacy user message must be readable and writable", messages.any { it.senderId == legacyUid })
  }

  @Test
  fun testEditMessageWithinOneHourAndRejectExpiredOrNonOwner() = runBlocking {
    val senderEmail = "editor_${System.nanoTime()}@blacksea.test"
    val senderUid = signInTestUser(senderEmail)
    val repository = BlackseaRepository(firestore)

    // Ensure sender is active/approved
    withTimeout(5000L) {
      val senderPayload = mapOf(
        "userId" to senderUid,
        "firstName" to "Mesaj",
        "lastName" to "Yazarı",
        "username" to "mesajyazari",
        "displayName" to "Mesaj Yazarı",
        "email" to senderEmail,
        "team" to "Yazılım Ekibi",
        "role" to "Yazılım Üyesi",
        "roles" to listOf("Yazılım Üyesi"),
        "status" to UserStatus.APPROVED.value
      )
      com.google.android.gms.tasks.Tasks.await(
        firestore.collection("users").document(senderUid).set(senderPayload)
      )
    }

    // 1. Fresh message can be edited by its sender
    withTimeout(5000L) {
      repository.sendMessage(
        roomId = "general",
        text = "Yanlış yazılmış mesaj",
        senderName = "Mesaj Yazarı",
        senderTeam = "Yazılım Ekibi"
      )
    }
    val freshMsg = withTimeout(5000L) {
      repository.observeMessages("general").first { list -> list.any { it.senderId == senderUid && it.text == "Yanlış yazılmış mesaj" } }
    }.last { it.senderId == senderUid && it.text == "Yanlış yazılmış mesaj" }

    withTimeout(5000L) {
      repository.editMessage("general", freshMsg.id, "Düzeltilmiş mesaj")
    }
    val updatedMessages = withTimeout(5000L) {
      repository.observeMessages("general").first { list -> list.any { it.id == freshMsg.id && it.text == "Düzeltilmiş mesaj" } }
    }
    val editedMsg = updatedMessages.first { it.id == freshMsg.id }
    assertTrue("Edited flag must be set", editedMsg.isEdited)
    assertEquals("Düzeltilmiş mesaj", editedMsg.text)

    // 2. Old (> 1 hour) message cannot be edited
    val oldDate = java.util.Date(System.currentTimeMillis() - 75L * 60L * 1000L)
    val oldMsgRef = firestore.collection("rooms").document("general").collection("messages").document()
    withTimeout(5000L) {
      com.google.android.gms.tasks.Tasks.await(
        oldMsgRef.set(
          mapOf(
            "id" to oldMsgRef.id,
            "roomId" to "general",
            "senderId" to senderUid,
            "senderName" to "Mesaj Yazarı",
            "senderTeam" to "Yazılım Ekibi",
            "text" to "Çok eski mesaj",
            "createdAt" to com.google.firebase.Timestamp(oldDate),
            "updatedAt" to com.google.firebase.Timestamp(oldDate)
          )
        )
      )
    }

    try {
      withTimeout(5000L) {
        repository.editMessage("general", oldMsgRef.id, "Süre aşımı denemesi")
      }
      fail("Editing a message older than 1 hour must fail")
    } catch (e: Exception) {
      assertTrue(
        "Should fail with 1-hour restriction or permission denied, got: ${e.message}",
        e is IllegalStateException || e.unwrapFirestoreException()?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
      )
    }

    // 3. Another user cannot edit sender's fresh message
    val otherEmail = "other_${System.nanoTime()}@blacksea.test"
    val otherUid = signInTestUser(otherEmail)
    withTimeout(5000L) {
      com.google.android.gms.tasks.Tasks.await(
        firestore.collection("users").document(otherUid).set(
          mapOf(
            "userId" to otherUid,
            "email" to otherEmail,
            "status" to UserStatus.APPROVED.value,
            "role" to "Yazılım Üyesi",
            "roles" to listOf("Yazılım Üyesi")
          )
        )
      )
    }
    try {
      withTimeout(5000L) {
        repository.editMessage("general", freshMsg.id, "Başkasının mesajını değiştir")
      }
      fail("Non-owner must not be allowed to edit someone else's message")
    } catch (e: Exception) {
      assertTrue(
        "Expected ownership error or permission denied, got: ${e.message}",
        e is IllegalStateException || e.unwrapFirestoreException()?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
      )
    }
  }
}
