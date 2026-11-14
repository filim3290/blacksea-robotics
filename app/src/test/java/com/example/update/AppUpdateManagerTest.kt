package com.example.update

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.BuildConfig
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
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
}
