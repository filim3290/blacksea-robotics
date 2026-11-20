package com.example.update

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.BuildConfig
import com.example.notifications.TeamNotificationDispatcher
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppUpdateManagerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("bsr_update_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    private fun liveGithubV105Json(): JSONObject = JSONObject(
        """
        {
          "tag_name": "v1.0.5",
          "name": "Blacksea Robotics v1.0.5",
          "draft": false,
          "prerelease": false,
          "published_at": "2026-11-14T08:34:57Z",
          "body": "versionCode: 6\nversionName: 1.0.5\napplicationId: com.aistudio.blacksearobotics.kxnqpv\ncertSha256: 5136f564a112fda07fa70a344e73eb2b30ab18f1efa68e730acd86baa1f84b16\nsha256: e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855\n\n• Güncelleme doğrulama iyileştirmeleri",
          "assets": [
            {
              "name": "BlackseaRobotics-v1.0.5.apk",
              "state": "uploaded",
              "size": 20688197,
              "content_type": "application/vnd.android.package-archive",
              "browser_download_url": "https://github.com/filim3290/blacksea-robotics/releases/download/v1.0.5/BlackseaRobotics-v1.0.5.apk"
            }
          ]
        }
        """.trimIndent()
    )

    @Test
    fun `parses live GitHub v1_0_5 release payload accurately`() {
        val mgr = AppUpdateManager(context)
        val parsed = mgr.parseReleaseJson(liveGithubV105Json())

        assertNotNull(parsed)
        requireNotNull(parsed)
        assertEquals("v1.0.5", parsed.tagName)
        assertEquals("1.0.5", parsed.versionName)
        assertEquals(6, parsed.versionCode)
        assertEquals("BlackseaRobotics-v1.0.5.apk", parsed.apkFileName)
        assertEquals(20688197L, parsed.apkSize)
        assertEquals(
            "https://github.com/filim3290/blacksea-robotics/releases/download/v1.0.5/BlackseaRobotics-v1.0.5.apk",
            parsed.apkDownloadUrl
        )
        assertEquals(
            "5136f564a112fda07fa70a344e73eb2b30ab18f1efa68e730acd86baa1f84b16",
            parsed.expectedCertSha256
        )
        assertFalse(parsed.changelog.contains("versionCode:"))
        assertTrue(parsed.changelog.contains("Güncelleme doğrulama iyileştirmeleri"))
    }

    @Test
    fun `detects update available when installed versionCode is lower than remote`() {
        val mgr = AppUpdateManager(context)
        val parsed = mgr.parseReleaseJson(liveGithubV105Json())!!

        val result = mgr.evaluateReleaseComparison(
            release = parsed,
            currentCode = 5,
            currentName = "1.0.4"
        )

        assertTrue(result is UpdateCheckResult.UpdateAvailable)
        val available = result as UpdateCheckResult.UpdateAvailable
        assertEquals(6, available.release.versionCode)
        assertEquals("v1.0.5", available.release.tagName)
    }

    @Test
    fun `reports up to date when installed versionCode equals remote`() {
        val mgr = AppUpdateManager(context)
        val parsed = mgr.parseReleaseJson(liveGithubV105Json())!!

        val result = mgr.evaluateReleaseComparison(
            release = parsed,
            currentCode = 6,
            currentName = "1.0.5"
        )

        assertTrue(result is UpdateCheckResult.UpToDate)
        val upToDate = result as UpdateCheckResult.UpToDate
        assertFalse(upToDate.isRemoteOlder)
    }

    @Test
    fun `reports remote older when installed versionCode is higher than remote`() {
        val mgr = AppUpdateManager(context)
        val parsed = mgr.parseReleaseJson(liveGithubV105Json())!!

        val result = mgr.evaluateReleaseComparison(
            release = parsed,
            currentCode = 7,
            currentName = "1.0.6"
        )

        assertTrue(result is UpdateCheckResult.UpToDate)
        val upToDate = result as UpdateCheckResult.UpToDate
        assertTrue(upToDate.isRemoteOlder)
    }

    @Test
    fun `clears poisoned last_installed_version preference above BuildConfig VERSION_CODE`() {
        val prefs = context.getSharedPreferences("bsr_update_prefs", Context.MODE_PRIVATE)
        prefs.edit().putInt("last_installed_version", BuildConfig.VERSION_CODE + 100).commit()

        val mgr = AppUpdateManager(context)
        assertEquals(0, prefs.getInt("last_installed_version", 0))

        val parsed = mgr.parseReleaseJson(liveGithubV105Json())!!
        val result = mgr.evaluateReleaseComparison(parsed, currentCode = 5, currentName = "1.0.4")
        assertTrue(result is UpdateCheckResult.UpdateAvailable)
    }

    @Test
    fun `falls back to numeric semver comparison when versionCode is absent`() {
        val mgr = AppUpdateManager(context)
        val json = JSONObject(
            """
            {
              "tag_name": "v1.0.10",
              "name": "v1.0.10",
              "draft": false,
              "prerelease": false,
              "body": "Bug fixes",
              "assets": [
                {
                  "name": "BlackseaRobotics-v1.0.10.apk",
                  "state": "uploaded",
                  "size": 20000000,
                  "browser_download_url": "https://github.com/filim3290/blacksea-robotics/releases/download/v1.0.10/BlackseaRobotics-v1.0.10.apk"
                }
              ]
            }
            """.trimIndent()
        )
        val parsed = mgr.parseReleaseJson(json)!!
        assertEquals(0, parsed.versionCode)

        // Lexicographically "1.0.10" < "1.0.9", but numerically 10 > 9
        val result = mgr.evaluateReleaseComparison(parsed, currentCode = 9, currentName = "1.0.9")
        assertTrue(result is UpdateCheckResult.UpdateAvailable)
    }

    @Test
    fun `returns ApkAssetMissing when release has no valid apk asset`() {
        val mgr = AppUpdateManager(context)
        val json = JSONObject(
            """
            {
              "tag_name": "v1.0.6",
              "name": "v1.0.6",
              "draft": false,
              "prerelease": false,
              "body": "versionCode: 7",
              "assets": [
                {
                  "name": "app-release-unsigned.apk",
                  "state": "uploaded",
                  "size": 19000000,
                  "browser_download_url": "https://github.com/filim3290/blacksea-robotics/releases/download/v1.0.6/app-release-unsigned.apk"
                }
              ]
            }
            """.trimIndent()
        )
        val parsed = mgr.parseReleaseJson(json)!!
        val result = mgr.evaluateReleaseComparison(parsed, currentCode = 6, currentName = "1.0.5")
        assertTrue(result is UpdateCheckResult.ApkAssetMissing)
    }

    @Test
    fun `parses legacy markdown bold versionCode format`() {
        val mgr = AppUpdateManager(context)
        val json = JSONObject(
            """
            {
              "tag_name": "v1.0.5",
              "name": "v1.0.5",
              "draft": false,
              "prerelease": false,
              "body": "**Sürüm Kodu (versionCode):** 6",
              "assets": [
                {
                  "name": "BlackseaRobotics-v1.0.5.apk",
                  "state": "uploaded",
                  "size": 20688197,
                  "browser_download_url": "https://github.com/filim3290/blacksea-robotics/releases/download/v1.0.5/BlackseaRobotics-v1.0.5.apk"
                }
              ]
            }
            """.trimIndent()
        )
        val parsed = mgr.parseReleaseJson(json)!!
        assertEquals(6, parsed.versionCode)
    }

    @Test
    fun `exposes AI Studio debug cert constant and signature mismatch download state`() {
        assertEquals(
            64,
            AppUpdateManager.AI_STUDIO_DEBUG_CERT_SHA256.length
        )
        val err = UpdateDownloadState.Error(
            message = "İmza Uyuşmazlığı",
            isSignatureMismatch = true,
            manualDownloadUrl = "https://github.com/filim3290/blacksea-robotics/releases/latest"
        )
        assertTrue(err.isSignatureMismatch)
        assertTrue(err.manualDownloadUrl.startsWith("https://"))
    }

    @Test
    fun `detects incompatible release certificate before downloading`() {
        val mgr = AppUpdateManager(context)
        val parsed = mgr.parseReleaseJson(liveGithubV105Json())!!
        val matchingInstalled = setOf("5136f564a112fda07fa70a344e73eb2b30ab18f1efa68e730acd86baa1f84b16")
        val differentInstalled = setOf(AppUpdateManager.AI_STUDIO_DEBUG_CERT_SHA256)

        assertTrue(mgr.isReleaseSignatureCompatible(parsed, matchingInstalled))
        assertFalse(mgr.isReleaseSignatureCompatible(parsed, differentInstalled))
    }

    @Test
    fun `evaluates 12-rollover semver progression correctly`() {
        val mgr = AppUpdateManager(context)
        fun releaseFor(tag: String) = AppReleaseInfo(
            tagName = tag,
            versionName = tag.removePrefix("v"),
            versionCode = 0,
            title = "Blacksea Robotics $tag",
            changelog = "Improvements",
            publishedAt = "",
            apkDownloadUrl = "https://github.com/filim3290/blacksea-robotics/releases/download/$tag/BlackseaRobotics-$tag.apk",
            apkFileName = "BlackseaRobotics-$tag.apk",
            apkSize = 20_000_000L
        )

        val v110 = mgr.evaluateReleaseComparison(releaseFor("v1.1.0"), currentCode = 13, currentName = "1.0.12")
        assertTrue("v1.1.0 should be newer than v1.0.12", v110 is UpdateCheckResult.UpdateAvailable)

        val v111 = mgr.evaluateReleaseComparison(releaseFor("v1.1.1"), currentCode = 14, currentName = "1.1.0")
        assertTrue("v1.1.1 should be newer than v1.1.0", v111 is UpdateCheckResult.UpdateAvailable)

        val v120 = mgr.evaluateReleaseComparison(releaseFor("v1.2.0"), currentCode = 26, currentName = "1.1.12")
        assertTrue("v1.2.0 should be newer than v1.1.12", v120 is UpdateCheckResult.UpdateAvailable)

        val older = mgr.evaluateReleaseComparison(releaseFor("v1.0.12"), currentCode = 14, currentName = "1.1.0")
        assertTrue("v1.0.12 should be marked older than v1.1.0", older is UpdateCheckResult.UpToDate && older.isRemoteOlder)
    }

    @Test
    fun `parses release redirect location headers cleanly`() {
        val mgr = AppUpdateManager(context)

        assertEquals(
            "v1.1.0",
            mgr.parseReleaseRedirectTag("https://github.com/filim3290/blacksea-robotics/releases/tag/v1.1.0")
        )
        assertEquals(
            "v1.0.12",
            mgr.parseReleaseRedirectTag("/filim3290/blacksea-robotics/releases/tag/v1.0.12?utm_source=test")
        )
        assertNull(mgr.parseReleaseRedirectTag("https://github.com/filim3290/blacksea-robotics/releases/latest"))
        assertNull(mgr.parseReleaseRedirectTag(null))
        assertNull(mgr.parseReleaseRedirectTag(""))
    }

    @Test
    fun `identifies rate limit and transient HTTP codes for fallback`() {
        val mgr = AppUpdateManager(context)

        assertTrue(mgr.isRateLimitedOrTransientFailure(403))
        assertTrue(mgr.isRateLimitedOrTransientFailure(429))
        assertTrue(mgr.isRateLimitedOrTransientFailure(500))
        assertTrue(mgr.isRateLimitedOrTransientFailure(503))
        assertFalse(mgr.isRateLimitedOrTransientFailure(200))
        assertFalse(mgr.isRateLimitedOrTransientFailure(404))
    }

    @Test
    fun `deduplicates release notifications by both versionCode and tagName`() {
        val mgr = AppUpdateManager(context)

        assertFalse(mgr.hasNotifiedForRelease(0, "v1.1.0"))
        mgr.markReleaseNotified(0, "v1.1.0")

        // Same tag, even if later resolved with explicit versionCode, must not double-notify
        assertTrue(mgr.hasNotifiedForRelease(0, "v1.1.0"))
        assertTrue(mgr.hasNotifiedForRelease(14, "v1.1.0"))

        // Next release (v1.1.1 / versionCode 15) should not be blocked
        assertFalse(mgr.hasNotifiedForRelease(15, "v1.1.1"))
    }

    @Test
    fun `dispatches update OS notification even when cert differs and avoids duplicates`() {
        val app = context.applicationContext as Application
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val mgr = AppUpdateManager(context)
        val notifManager = context.getSystemService(NotificationManager::class.java)!!
        val shadowNm = Shadows.shadowOf(notifManager)
        notifManager.cancelAll()

        val incompatibleNewRelease = AppReleaseInfo(
            tagName = "v9.9.9",
            versionName = "9.9.9",
            versionCode = BuildConfig.VERSION_CODE + 50,
            title = "Blacksea Robotics v9.9.9",
            changelog = "Major release",
            publishedAt = "2026-11-16T00:00:00Z",
            apkDownloadUrl = "https://github.com/filim3290/blacksea-robotics/releases/download/v9.9.9/BlackseaRobotics-v9.9.9.apk",
            apkFileName = "BlackseaRobotics-v9.9.9.apk",
            apkSize = 21_000_000L,
            expectedCertSha256 = "0000000000000000000000000000000000000000000000000000000000000000"
        )

        val firstDispatch = TeamNotificationDispatcher.dispatchUpdateNotificationIfNeeded(
            context = context,
            updateManager = mgr,
            release = incompatibleNewRelease
        )
        assertTrue("First update notification must dispatch", firstDispatch)
        assertEquals(1, shadowNm.size())

        val secondDispatch = TeamNotificationDispatcher.dispatchUpdateNotificationIfNeeded(
            context = context,
            updateManager = mgr,
            release = incompatibleNewRelease
        )
        assertFalse("Duplicate update notification for same release must be suppressed", secondDispatch)
    }

    @Test
    fun `treats same installed semver as UpToDate even when remote versionCode metadata is higher`() {
        val mgr = AppUpdateManager(context)
        val sameVersionDifferentCode = AppReleaseInfo(
            tagName = "v1.1.0",
            versionName = "1.1.0",
            versionCode = 999,
            title = "Blacksea Robotics v1.1.0",
            changelog = "Bug fixes",
            publishedAt = "2026-11-16T00:00:00Z",
            apkDownloadUrl = "https://github.com/filim3290/blacksea-robotics/releases/download/v1.1.0/BlackseaRobotics-v1.1.0.apk",
            apkFileName = "BlackseaRobotics-v1.1.0.apk",
            apkSize = 21_000_000L
        )

        val result = mgr.evaluateReleaseComparison(
            release = sameVersionDifferentCode,
            currentCode = 14,
            currentName = "1.1.0"
        )
        assertTrue(result is UpdateCheckResult.UpToDate)
        assertFalse((result as UpdateCheckResult.UpToDate).isRemoteOlder)
    }

    @Test
    fun `identifies already installed updates and obsolete update notifications accurately`() {
        val mgr = AppUpdateManager(context)

        assertTrue(mgr.isUpdateAlreadyInstalled(remoteVersionCode = 99, remoteVersionName = "1.1.0", currentCode = 14, currentName = "1.1.0"))
        assertTrue(mgr.isUpdateAlreadyInstalled(remoteVersionCode = 12, remoteVersionName = "1.0.12", currentCode = 14, currentName = "1.1.0"))
        assertFalse(mgr.isUpdateAlreadyInstalled(remoteVersionCode = 15, remoteVersionName = "1.1.1", currentCode = 14, currentName = "1.1.0"))

        assertTrue(mgr.isNotificationUpdateObsolete(type = "update", targetId = "update_v1.0.12", message = "v1.0.12 yayında", currentName = "1.1.0"))
        assertTrue(mgr.isNotificationUpdateObsolete(type = "update", targetId = "update_v1.1.0", message = "v1.1.0 yayında", currentName = "1.1.0"))
        assertFalse(mgr.isNotificationUpdateObsolete(type = "update", targetId = "update_v1.2.0", message = "v1.2.0 yayında", currentName = "1.1.0"))
        assertFalse(mgr.isNotificationUpdateObsolete(type = "message", targetId = "update_v1.0.12", message = "v1.0.12", currentName = "1.1.0"))
    }

    @Test
    fun `enforces mandatory force update when client is 6 or more versions behind`() {
        val mgr = AppUpdateManager(context)
        val v1112 = AppReleaseInfo(
            tagName = "v1.1.12",
            versionName = "1.1.12",
            versionCode = 26,
            title = "Blacksea Robotics v1.1.12",
            changelog = "Major stability release",
            publishedAt = "2026-11-20T00:00:00Z",
            apkDownloadUrl = "https://github.com/filim3290/blacksea-robotics/releases/download/v1.1.12/BlackseaRobotics-v1.1.12.apk",
            apkFileName = "BlackseaRobotics-v1.1.12.apk",
            apkSize = 22_000_000L,
            isForceUpdate = false
        )

        // 1.1.6 is 6 releases behind 1.1.12 -> FORCE UPDATE
        assertEquals(6, AppUpdateManager.calculateVersionLagSteps("1.1.12", 26, "1.1.6", 20))
        val eval116 = mgr.evaluateReleaseComparison(v1112, currentCode = 20, currentName = "1.1.6")
        assertTrue(eval116 is UpdateCheckResult.UpdateAvailable && eval116.release.isForceUpdate)

        // 1.0.12 is 13 releases behind 1.1.12 -> FORCE UPDATE
        assertEquals(13, AppUpdateManager.calculateVersionLagSteps("1.1.12", 26, "1.0.12", 13))
        val eval1012 = mgr.evaluateReleaseComparison(v1112, currentCode = 13, currentName = "1.0.12")
        assertTrue(eval1012 is UpdateCheckResult.UpdateAvailable && eval1012.release.isForceUpdate)

        // 1.1.7 is 5 releases behind 1.1.12 -> OPTIONAL update
        assertEquals(5, AppUpdateManager.calculateVersionLagSteps("1.1.12", 26, "1.1.7", 21))
        val eval117 = mgr.evaluateReleaseComparison(v1112, currentCode = 21, currentName = "1.1.7")
        assertTrue(eval117 is UpdateCheckResult.UpdateAvailable && !eval117.release.isForceUpdate)

        // 1.1.0 is only 1 step ahead of 1.0.12 -> OPTIONAL update
        assertEquals(1, AppUpdateManager.calculateVersionLagSteps("1.1.0", 14, "1.0.12", 13))
        assertFalse(
            AppUpdateManager.isVersionForceUpdateRequired(
                release = v1112.copy(versionName = "1.1.0", versionCode = 14),
                currentCode = 13,
                currentName = "1.0.12"
            )
        )

        // minSupportedVersionName also forces update even if lag < 6
        assertTrue(
            AppUpdateManager.isVersionForceUpdateRequired(
                release = v1112,
                currentCode = 24,
                currentName = "1.1.10",
                minSupportedVersionName = "1.1.11"
            )
        )
    }
}
