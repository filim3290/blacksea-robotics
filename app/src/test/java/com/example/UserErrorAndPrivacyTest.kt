package com.example

import android.content.Context
import android.content.pm.PackageInstaller
import androidx.test.core.app.ApplicationProvider
import com.example.update.AppUpdateManager
import com.example.update.UpdateInstallReceiver
import com.example.util.AppMessages
import com.example.util.sanitizeUserErrorMessage
import com.google.firebase.firestore.FirebaseFirestoreException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UserErrorAndPrivacyTest {

    @Test
    fun `sanitizeUserErrorMessage maps network exceptions cleanly without leaking URLs or stack text`() {
        val unknownHost = UnknownHostException("Unable to resolve host api.github.com: No address associated with hostname")
        val timeout = SocketTimeoutException("failed to connect to firestore.googleapis.com/142.250.1.1")
        val io = IOException("HTTP 502 Bad Gateway from https://api.github.com/repos/foo/bar")

        assertEquals(AppMessages.CONNECTION_ERROR, sanitizeUserErrorMessage(unknownHost))
        assertEquals(AppMessages.CONNECTION_ERROR, sanitizeUserErrorMessage(timeout))
        assertEquals(AppMessages.CONNECTION_ERROR, sanitizeUserErrorMessage(io))
    }

    @Test
    fun `sanitizeUserErrorMessage maps Firestore PERMISSION_DENIED and UNAVAILABLE cleanly`() {
        val denied = FirebaseFirestoreException(
            "PERMISSION_DENIED: Missing or insufficient permissions on projects/gen-lang-client/databases/(default)",
            FirebaseFirestoreException.Code.PERMISSION_DENIED
        )
        val unavailable = FirebaseFirestoreException(
            "UNAVAILABLE: upstream connect error",
            FirebaseFirestoreException.Code.UNAVAILABLE
        )

        assertEquals(AppMessages.PERMISSION_ERROR, sanitizeUserErrorMessage(denied))
        assertEquals(AppMessages.CONNECTION_ERROR, sanitizeUserErrorMessage(unavailable))
    }

    @Test
    fun `sanitizeUserErrorMessage never exposes raw technical exception details`() {
        val rawTechError = RuntimeException(
            "Firebase AuthenticationException: FAILED_PRECONDITION keystore sha256 mismatch at https://api.github.com/repos/owner/repo"
        )
        val sanitized = sanitizeUserErrorMessage(rawTechError)

        assertEquals(AppMessages.GENERIC_ERROR, sanitized)
        assertFalse(sanitized.contains("Firebase", ignoreCase = true))
        assertFalse(sanitized.contains("GitHub", ignoreCase = true))
        assertFalse(sanitized.contains("FAILED_PRECONDITION", ignoreCase = true))
        assertFalse(sanitized.contains("keystore", ignoreCase = true))
    }

    @Test
    fun `UpdateInstallReceiver buildUserFriendlyMessage hides raw PackageInstaller error tokens`() {
        val conflictMsg = UpdateInstallReceiver.buildUserFriendlyMessage(
            PackageInstaller.STATUS_FAILURE_CONFLICT,
            "INSTALL_FAILED_UPDATE_INCOMPATIBLE: Existing package com.aistudio.foo signatures do not match keystore"
        )
        assertEquals(AppMessages.UPDATE_INSTALL_ERROR, conflictMsg)
        assertFalse(conflictMsg.contains("INSTALL_FAILED", ignoreCase = true))
        assertFalse(conflictMsg.contains("STATUS_FAILURE", ignoreCase = true))
        assertFalse(conflictMsg.contains("keystore", ignoreCase = true))
        assertFalse(conflictMsg.contains("com.aistudio", ignoreCase = true))

        val storageMsg = UpdateInstallReceiver.buildUserFriendlyMessage(
            PackageInstaller.STATUS_FAILURE_STORAGE,
            "INSTALL_FAILED_INSUFFICIENT_STORAGE"
        )
        assertTrue(storageMsg.contains("depolama"))
        assertFalse(storageMsg.contains("INSTALL_FAILED", ignoreCase = true))
    }

    @Test
    fun `AppUpdateManager removes any legacy persisted github_token on init`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val prefs = context.getSharedPreferences("bsr_update_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("github_token", "ghp_legacy_secret_12345").commit()

        AppUpdateManager(context)

        assertFalse(prefs.contains("github_token"))
    }

    @Test
    fun `parseReleaseJson strips technical metadata lines from user-facing changelog`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val mgr = AppUpdateManager(context)
        val payload = JSONObject(
            """
            {
              "tag_name": "v1.2.0",
              "name": "v1.2.0",
              "body": "versionCode: 12\napplicationId: com.aistudio.test\n**Sürüm Kodu:** 12\nİndirme: https://github.com/filim3290/blacksea-robotics/releases\nDosya: BlackseaRobotics-v1.2.0.apk\nİmza Sertifikası SHA-256: abcd1234\nKeystore bilgisi gizli\n\n• Yeni görev takibi eklendi\n• Performans iyileştirmeleri",
              "assets": [
                {
                  "name": "BlackseaRobotics-v1.2.0.apk",
                  "state": "uploaded",
                  "size": 20000000,
                  "browser_download_url": "https://github.com/filim3290/blacksea-robotics/releases/download/v1.2.0/BlackseaRobotics-v1.2.0.apk"
                }
              ]
            }
            """.trimIndent()
        )

        val parsed = mgr.parseReleaseJson(payload)
        assertNotNull(parsed)
        requireNotNull(parsed)

        assertTrue(parsed.changelog.contains("Yeni görev takibi eklendi"))
        assertTrue(parsed.changelog.contains("Performans iyileştirmeleri"))
        assertFalse(parsed.changelog.contains("versionCode", ignoreCase = true))
        assertFalse(parsed.changelog.contains("applicationId", ignoreCase = true))
        assertFalse(parsed.changelog.contains("github.com", ignoreCase = true))
        assertFalse(parsed.changelog.contains(".apk", ignoreCase = true))
        assertFalse(parsed.changelog.contains("keystore", ignoreCase = true))
        assertFalse(parsed.changelog.contains("SHA-256", ignoreCase = true))
        assertFalse(parsed.changelog.contains("Sürüm Kodu", ignoreCase = true))
    }
}
