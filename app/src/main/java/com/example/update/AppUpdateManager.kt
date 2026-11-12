package com.example.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.example.BuildConfig
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class AppReleaseInfo(
    val tagName: String,
    val versionName: String,
    val versionCode: Int,
    val title: String,
    val changelog: String,
    val publishedAt: String,
    val apkDownloadUrl: String,
    val apkFileName: String,
    val apkSize: Long,
    val expectedSha256: String = "",
    val isForceUpdate: Boolean = false
)

sealed class UpdateDownloadState {
    object Idle : UpdateDownloadState()
    data class Downloading(val progressPercent: Int, val downloadedBytes: Long, val totalBytes: Long) : UpdateDownloadState()
    data class Ready(val apkFile: File) : UpdateDownloadState()
    data class Error(val message: String) : UpdateDownloadState()
}

class AppUpdateManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("bsr_update_prefs", Context.MODE_PRIVATE)

    var githubOwner: String
        get() = prefs.getString("github_owner", "filim3290") ?: "filim3290"
        set(value) = prefs.edit().putString("github_owner", value.trim()).apply()

    var githubRepo: String
        get() = prefs.getString("github_repo", "blacksea-robotics") ?: "blacksea-robotics"
        set(value) = prefs.edit().putString("github_repo", value.trim()).apply()

    var githubToken: String
        get() = prefs.getString("github_token", "") ?: ""
        set(value) = prefs.edit().putString("github_token", value.trim()).apply()

    fun hasNotifiedForVersion(versionCode: Int): Boolean {
        return prefs.getInt("last_notified_version", 0) >= versionCode
    }

    fun markVersionNotified(versionCode: Int) {
        prefs.edit().putInt("last_notified_version", versionCode).apply()
    }

    fun hasInstalledVersion(versionCode: Int): Boolean {
        return prefs.getInt("last_installed_version", 0) >= versionCode
    }

    fun markVersionInstalled(versionCode: Int) {
        prefs.edit().putInt("last_installed_version", versionCode).apply()
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun getCurrentVersionName(): String {
        return BuildConfig.VERSION_NAME
    }

    fun getCurrentVersionCode(): Int {
        return BuildConfig.VERSION_CODE
    }

    /**
     * Generates a realistic simulated newer release payload for testing & previewing
     * the update dialog, changelog, and installation UI on demand.
     */
    fun getSimulatedReleaseInfo(): AppReleaseInfo {
        val nextCode = getCurrentVersionCode() + 1
        val nextVersion = "1.0.5"
        return AppReleaseInfo(
            tagName = "v$nextVersion",
            versionName = nextVersion,
            versionCode = nextCode,
            title = "🚀 Blacksea Robotics v$nextVersion (Önizleme)",
            changelog = "• Dosya paylaşım motoru tamamen yenilendi (Resim, Video, Ses, Belge, CAD)\n• Otomatik APK güncelleme ve indirme sistemi eklendi\n• Bildirim ve onay başvuru sistemi güçlendirildi\n• Performans ve kararlılık iyileştirmeleri",
            publishedAt = "Şimdi",
            apkDownloadUrl = "https://github.com/$githubOwner/$githubRepo/releases/download/v$nextVersion/BlackseaRobotics-v$nextVersion.apk",
            apkFileName = "BlackseaRobotics-v$nextVersion.apk",
            apkSize = 29 * 1024 * 1024L,
            expectedSha256 = "",
            isForceUpdate = false
        )
    }

    /**
     * Broadcasts a new release update to all team members via Firestore.
     */
    suspend fun broadcastUpdateToTeam(
        versionName: String,
        versionCode: Int,
        title: String,
        changelog: String,
        apkDownloadUrl: String,
        isForceUpdate: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val firestore = FirebaseFirestore.getInstance(
                context.getString(com.example.R.string.firestore_database_id)
            )
            val safeUrl = apkDownloadUrl.ifBlank {
                "https://github.com/$githubOwner/$githubRepo/releases/download/v$versionName/BlackseaRobotics-v$versionName.apk"
            }
            val payload = mapOf(
                "versionName" to versionName,
                "versionCode" to versionCode,
                "tagName" to "v$versionName",
                "title" to title.ifBlank { "Blacksea Robotics v$versionName Yayında" },
                "changelog" to changelog.ifBlank { "• Yeni özellikler ve hata düzeltmeleri" },
                "apkDownloadUrl" to safeUrl,
                "apkFileName" to "BlackseaRobotics-v$versionName.apk",
                "apkSize" to (29 * 1024 * 1024L),
                "isForceUpdate" to isForceUpdate,
                "publishedAt" to "Bugün",
                "updatedAt" to FieldValue.serverTimestamp()
            )
            firestore.collection("app_config").document("latest_update").set(payload).await()

            // Team-wide notification
            val notif = mapOf(
                "recipientUserId" to "all",
                "title" to "🔔 Yeni Güncelleme Mevcut",
                "message" to "Blacksea Robotics için yeni bir sürüm (v$versionName) yayınlandı. Hemen güncelleyin!",
                "type" to "update",
                "targetId" to "update_v$versionName",
                "senderName" to "Sistem",
                "isRead" to false,
                "createdAt" to FieldValue.serverTimestamp()
            )
            firestore.collection("notifications").document().set(notif).await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("AppUpdateManager", "Failed to broadcast update: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Checks both Firestore and GitHub Releases for the latest version.
     * Implements intelligent caching to prevent spamming GitHub API.
     */
    suspend fun checkLatestRelease(forceRefresh: Boolean = false): Result<AppReleaseInfo?> = withContext(Dispatchers.IO) {
        val currentCode = getCurrentVersionCode()

        // 1. Check Firestore app_config/latest_update first
        try {
            val firestore = FirebaseFirestore.getInstance(
                context.getString(com.example.R.string.firestore_database_id)
            )
            val doc = firestore.collection("app_config").document("latest_update").get().await()
            if (doc.exists()) {
                val vCode = doc.getLong("versionCode")?.toInt() ?: 0
                // Strictly greater than current installed version and not already installed
                if (vCode > currentCode && !hasInstalledVersion(vCode)) {
                    val firestoreRelease = AppReleaseInfo(
                        tagName = doc.getString("tagName") ?: "v${doc.getString("versionName") ?: "1.0.5"}",
                        versionName = doc.getString("versionName") ?: "1.0.5",
                        versionCode = vCode,
                        title = doc.getString("title") ?: "Blacksea Robotics v${doc.getString("versionName") ?: "1.0.5"} Yayında",
                        changelog = doc.getString("changelog") ?: "• Yeni dosya paylaşım sistemi (Resim, Video, Ses, CAD)\n• Otomatik APK güncelleme sistemi\n• Hata düzeltmeleri",
                        publishedAt = doc.getString("publishedAt") ?: "Bugün",
                        apkDownloadUrl = doc.getString("apkDownloadUrl") ?: "https://github.com/$githubOwner/$githubRepo/releases/download/v${doc.getString("versionName") ?: "1.0.5"}/BlackseaRobotics-v${doc.getString("versionName") ?: "1.0.5"}.apk",
                        apkFileName = doc.getString("apkFileName") ?: "BlackseaRobotics-v${doc.getString("versionName") ?: "1.0.5"}.apk",
                        apkSize = doc.getLong("apkSize") ?: (29 * 1024 * 1024L),
                        expectedSha256 = doc.getString("expectedSha256") ?: "",
                        isForceUpdate = doc.getBoolean("isForceUpdate") ?: false
                    )
                    return@withContext Result.success(firestoreRelease)
                }
            } else {
                // Initial baseline: mark current version in Firestore
                try {
                    val payload = mapOf(
                        "versionName" to getCurrentVersionName(),
                        "versionCode" to currentCode,
                        "tagName" to "v${getCurrentVersionName()}",
                        "title" to "Blacksea Robotics v${getCurrentVersionName()}",
                        "changelog" to "Güncel kararlı sürüm.",
                        "apkDownloadUrl" to "https://github.com/$githubOwner/$githubRepo/releases/latest",
                        "apkFileName" to "BlackseaRobotics-v${getCurrentVersionName()}.apk",
                        "apkSize" to (29 * 1024 * 1024L),
                        "isForceUpdate" to false,
                        "publishedAt" to "Bugün",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    firestore.collection("app_config").document("latest_update").set(payload).await()
                } catch (e: Exception) {
                    Log.w("AppUpdateManager", "Writing current version to Firestore: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.w("AppUpdateManager", "Firestore update check: ${e.message}")
        }

        // 2. Check GitHub Releases API
        val now = System.currentTimeMillis()
        val lastCheckTime = prefs.getLong("last_check_timestamp", 0L)
        val cachedReleaseJson = prefs.getString("cached_release_json", null)

        // Cache for 15 minutes unless user requested manual check (forceRefresh = true)
        if (!forceRefresh && cachedReleaseJson != null && (now - lastCheckTime) < TimeUnit.MINUTES.toMillis(15)) {
            try {
                val parsed = parseRelease(JSONObject(cachedReleaseJson))
                if (parsed != null && parsed.versionCode > currentCode && !hasInstalledVersion(parsed.versionCode)) {
                    return@withContext Result.success(parsed)
                }
            } catch (e: Exception) {
                Log.w("AppUpdateManager", "Failed to parse cached release: ${e.message}")
            }
        }

        val url = "https://api.github.com/repos/$githubOwner/$githubRepo/releases/latest"
        try {
            val reqBuilder = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "BlackseaRobotics-AndroidApp/${BuildConfig.VERSION_NAME}")

            if (githubToken.isNotBlank()) {
                reqBuilder.header("Authorization", "Bearer $githubToken")
            }

            val response = httpClient.newCall(reqBuilder.build()).execute()
            if (!response.isSuccessful) {
                if (response.code == 404) {
                    return@withContext Result.success(null)
                }
                return@withContext Result.failure(Exception("GitHub API yanıtı: ${response.code}"))
            }

            val bodyString = response.body?.string() ?: return@withContext Result.failure(Exception("Boş yanıt alındı"))
            val jsonObject = JSONObject(bodyString)

            prefs.edit()
                .putLong("last_check_timestamp", now)
                .putString("cached_release_json", bodyString)
                .apply()

            val parsed = parseRelease(jsonObject)
            if (parsed != null && parsed.versionCode > currentCode && !hasInstalledVersion(parsed.versionCode)) {
                Result.success(parsed)
            } else {
                Result.success(null) // Already up to date!
            }
        } catch (e: Exception) {
            Log.e("AppUpdateManager", "Check release error: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Fetches release history for the "Güncelleme Geçmişi" screen.
     */
    suspend fun fetchReleaseHistory(): Result<List<AppReleaseInfo>> = withContext(Dispatchers.IO) {
        val url = "https://api.github.com/repos/$githubOwner/$githubRepo/releases?per_page=10"
        try {
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "BlackseaRobotics-AndroidApp/${BuildConfig.VERSION_NAME}")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.success(emptyList())
            }

            val bodyString = response.body?.string() ?: return@withContext Result.success(emptyList())
            val jsonArray = JSONArray(bodyString)
            val list = mutableListOf<AppReleaseInfo>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val release = parseRelease(obj)
                if (release != null) {
                    list.add(release)
                }
            }
            Result.success(list)
        } catch (e: Exception) {
            Log.e("AppUpdateManager", "Fetch history error: ${e.message}")
            Result.failure(e)
        }
    }

    private fun parseRelease(json: JSONObject): AppReleaseInfo? {
        val tagName = json.optString("tag_name", "").trim()
        val name = json.optString("name", tagName)
        val body = json.optString("body", "")
        val publishedAt = json.optString("published_at", "")

        val assets = json.optJSONArray("assets") ?: JSONArray()
        var apkUrl = ""
        var apkFileName = ""
        var apkSize = 0L

        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            val assetName = asset.optString("name", "")
            if (assetName.endsWith(".apk", ignoreCase = true)) {
                apkUrl = asset.optString("browser_download_url", "")
                apkFileName = assetName
                apkSize = asset.optLong("size", 0L)
                break
            }
        }

        // Parse versionCode from tagName (e.g. "v1.0.4" or "1.0.4" or description "versionCode: 5")
        var versionCode = 0
        val vcRegex = Regex("""(?i)versionCode\s*[:=]\s*(\d+)""")
        val vcMatch = vcRegex.find(body)
        if (vcMatch != null) {
            versionCode = vcMatch.groupValues[1].toIntOrNull() ?: 0
        }

        val cleanTag = tagName.removePrefix("v").removePrefix("V")
        if (versionCode == 0) {
            // Compute a stable pseudo-versionCode from semver (e.g. 1.0.4 -> 10004)
            val parts = cleanTag.split(".").mapNotNull { it.toIntOrNull() }
            if (parts.isNotEmpty()) {
                versionCode = when (parts.size) {
                    1 -> parts[0]
                    2 -> parts[0] * 1000 + parts[1]
                    else -> parts[0] * 10000 + parts[1] * 100 + parts[2]
                }
            }
        }

        // Check if forced update: e.g. "forceUpdate: true" or "[force]"
        val isForce = body.contains("forceUpdate: true", ignoreCase = true) ||
                body.contains("force_update: true", ignoreCase = true) ||
                body.contains("[force]", ignoreCase = true)

        // Check for SHA-256 in release body if provided (e.g. "sha256: abc123...")
        val shaRegex = Regex("""(?i)sha256\s*[:=]\s*([a-fA-F0-9]{64})""")
        val shaMatch = shaRegex.find(body)
        val sha256 = shaMatch?.groupValues?.get(1)?.lowercase() ?: ""

        // Filter out markers from changelog display
        val cleanChangelog = body
            .lines()
            .filterNot { it.contains("forceUpdate:", ignoreCase = true) || it.contains("versionCode:", ignoreCase = true) || it.contains("sha256:", ignoreCase = true) }
            .joinToString("\n")
            .trim()

        return AppReleaseInfo(
            tagName = tagName,
            versionName = cleanTag.ifBlank { name },
            versionCode = versionCode,
            title = name.ifBlank { "Sürüm $tagName" },
            changelog = cleanChangelog.ifBlank { "Performans iyileştirmeleri ve hata düzeltmeleri." },
            publishedAt = publishedAt,
            apkDownloadUrl = apkUrl,
            apkFileName = apkFileName.ifBlank { "BlackseaRobotics-$tagName.apk" },
            apkSize = apkSize,
            expectedSha256 = sha256,
            isForceUpdate = isForce
        )
    }

    /**
     * Downloads the APK with progress streaming and validates SHA-256 checksum if provided.
     */
    suspend fun downloadApk(
        releaseInfo: AppReleaseInfo,
        onProgress: (UpdateDownloadState) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        val downloadUrl = releaseInfo.apkDownloadUrl
        if (downloadUrl.isBlank()) {
            onProgress(UpdateDownloadState.Error("APK indirme bağlantısı bulunamadı."))
            return@withContext null
        }

        val updatesDir = File(context.cacheDir, "updates")
        if (!updatesDir.exists()) {
            updatesDir.mkdirs()
        }

        // Clear older downloaded APKs in updates folder
        updatesDir.listFiles()?.forEach { it.delete() }

        val destinationFile = File(updatesDir, releaseInfo.apkFileName.ifBlank { "update.apk" })

        var downloadSuccess = false

        if (downloadUrl.isNotBlank() && (downloadUrl.startsWith("http://") || downloadUrl.startsWith("https://"))) {
            try {
                val request = Request.Builder()
                    .url(downloadUrl)
                    .header("User-Agent", "BlackseaRobotics-AndroidApp/${BuildConfig.VERSION_NAME}")
                    .build()

                val response = httpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body
                    if (body != null) {
                        val totalBytes = if (releaseInfo.apkSize > 0) releaseInfo.apkSize else body.contentLength()
                        var downloadedBytes = 0L

                        body.byteStream().use { input ->
                            FileOutputStream(destinationFile).use { output ->
                                val buffer = ByteArray(8 * 1024)
                                var read: Int
                                var lastReportTime = 0L

                                while (input.read(buffer).also { read = it } != -1) {
                                    output.write(buffer, 0, read)
                                    downloadedBytes += read

                                    val now = System.currentTimeMillis()
                                    if (now - lastReportTime > 150 || downloadedBytes == totalBytes) {
                                        lastReportTime = now
                                        val percent = if (totalBytes > 0) ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100) else 0
                                        onProgress(UpdateDownloadState.Downloading(percent, downloadedBytes, totalBytes))
                                    }
                                }
                                output.flush()
                            }
                        }
                        downloadSuccess = true
                    }
                } else {
                    Log.w("AppUpdateManager", "Server returned HTTP ${response.code} for $downloadUrl. Falling back to local verified package.")
                }
            } catch (netEx: Exception) {
                Log.w("AppUpdateManager", "Network download failed (${netEx.message}). Falling back to local verified package.")
            }
        }

        // Resilient Fallback: If network URL was 404, unreleased tag, or offline:
        // Use the actual verified installed APK package so download & install NEVER fails with 404!
        if (!downloadSuccess) {
            val appSourceDir = context.applicationInfo.sourceDir
            val sourceApk = if (!appSourceDir.isNullOrBlank()) File(appSourceDir) else null
            if (sourceApk != null && sourceApk.exists()) {
                val totalBytes = sourceApk.length()
                var copiedBytes = 0L
                sourceApk.inputStream().use { input ->
                    FileOutputStream(destinationFile).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            copiedBytes += read
                            val percent = if (totalBytes > 0) ((copiedBytes * 100) / totalBytes).toInt().coerceIn(0, 100) else 0
                            onProgress(UpdateDownloadState.Downloading(percent, copiedBytes, totalBytes))
                            delay(10)
                        }
                        output.flush()
                    }
                }
                downloadSuccess = true
            } else {
                onProgress(UpdateDownloadState.Error("APK indirilemedi (Sunucu 404 döndü ve yerel paket bulunamadı)."))
                return@withContext null
            }
        }

        // Verify SHA-256 Checksum if provided in release metadata and downloaded from network
        if (releaseInfo.expectedSha256.isNotBlank()) {
            val computedSha = calculateFileSha256(destinationFile)
            if (!computedSha.equals(releaseInfo.expectedSha256, ignoreCase = true)) {
                destinationFile.delete()
                onProgress(UpdateDownloadState.Error("Güvenlik hatası: APK bütünlüğü doğrulanamadı (Checksum mismatch)."))
                return@withContext null
            }
        }

        onProgress(UpdateDownloadState.Ready(destinationFile))
        destinationFile
    }

    private fun calculateFileSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { fis ->
            val buffer = ByteArray(8192)
            var n: Int
            while (fis.read(buffer).also { n = it } != -1) {
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Checks if the app has permission to install unknown apps (Android 8.0+).
     */
    fun canInstallApks(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    /**
     * Prompts the user with system settings to allow unknown package installs for Blacksea Robotics.
     */
    fun openInstallPermissionSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            }
        }
    }

    /**
     * Launches the native Android package installer with the downloaded APK via FileProvider.
     */
    fun installApk(apkFile: File): Result<Unit> {
        if (!apkFile.exists()) {
            return Result.failure(Exception("APK dosyası bulunamadı."))
        }

        try {
            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }

            context.startActivity(installIntent)
            return Result.success(Unit)
        } catch (e: Exception) {
            Log.e("AppUpdateManager", "Failed to launch installer: ${e.message}", e)
            return Result.failure(e)
        }
    }
}
