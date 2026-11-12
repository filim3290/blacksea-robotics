package com.example

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.BlackseaRepository
import com.example.model.UserProfile
import com.example.model.UserRole
import com.example.model.UserStatus
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
      fail("Expected exception for unauthenticated write")
    } catch (e: Exception) {
      // Expected: either IllegalStateException or FirebaseFirestoreException PERMISSION_DENIED
      val isExpected = e is IllegalStateException || e.unwrapFirestoreException()?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
      assertTrue("Expected permission or auth failure, got: ${e.message}", isExpected)
    }
  }

  @Test
  fun testAuthenticatedUserCanCreateProfile() = runBlocking {
    val email = "member1@blacksea.test"
    val uid = try {
      signInTestUser(email)
    } catch (e: Exception) {
      org.junit.Assume.assumeNoException("Auth emulator not available", e)
      return@runBlocking
    }
    val repository = BlackseaRepository(firestore)

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

    val profile = repository.observeUserProfile(uid).first()
    assertNotNull(profile)
    assertEquals(uid, profile?.userId)
    assertEquals("Kerem Kaptan", profile?.getFullName())
  }

  @Test
  fun testSendAndObserveMessage() = runBlocking {
    val email = "writer@blacksea.test"
    val uid = try {
      signInTestUser(email)
    } catch (e: Exception) {
      org.junit.Assume.assumeNoException("Auth emulator not available", e)
      return@runBlocking
    }
    val repository = BlackseaRepository(firestore)

    repository.sendMessage(
      roomId = "software",
      text = "Robot kolu PID parametreleri güncellendi.",
      senderName = "Mühendis Ali",
      senderTeam = "Yazılım Ekibi"
    )

    val messages = repository.observeMessages("software").first()
    assertTrue("Should have at least 1 message", messages.isNotEmpty())
    val lastMsg = messages.last()
    assertEquals("software", lastMsg.roomId)
    assertEquals(uid, lastMsg.senderId)
    assertEquals("Robot kolu PID parametreleri güncellendi.", lastMsg.text)
  }
}
