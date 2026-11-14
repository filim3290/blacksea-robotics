package com.example.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
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
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
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
    val expectedCertSha256: String = "",
    val isForceUpdate: Boolean = false
)

sealed class UpdateDownloadState {
    object Idle : UpdateDownloadState()
    data class Downloading(val progressPercent: Int, val downloadedBytes: Long, val totalBytes: Long) : UpdateDownloadState()
    data class Ready(val apkFile: File) : UpdateDownloadState()
    data class Error(
        val message: String,
        val isSignatureMismatch: Boolean = false,
        val manualDownloadUrl: String = ""
    ) : UpdateDownloadState()
}

sealed class UpdateCheckResult {
    data class UpdateAvailable(
        val release: AppReleaseInfo,
        val isSignatureCompatible: Boolean = true,
        val installedCertSha256: String = ""
    ) : UpdateCheckResult()
    data class UpToDate(
        val currentVersionName: String,
        val currentVersionCode: Int,
        val latestRemote: AppReleaseInfo? = null,
        val isRemoteOlder: Boolean = false
    ) : UpdateCheckResult()
    object NoReleaseFound : UpdateCheckResult()
    data class ApkAssetMissing(val tagName: String) : UpdateCheckResult()
    data class NetworkError(val detail: String) : UpdateCheckResult()
    data class ApiError(val httpCode: Int, val detail: String) : UpdateCheckResult()
}

class AppUpdateManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("bsr_update_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "AppUpdateManager"
        const val DEFAULT_GITHUB_OWNER = "filim3290"
        const val DEFAULT_GITHUB_REPO = "blacksea-robotics"
        private const val REPO_MIGRATION_VERSION = 2
        private val LEGACY_OWNERS = setOf("baslider52-ui", "mehmetilkerbas", "filmi3290")
        private val LEGACY_REPOS = setOf("filmi3290", "blacksearobotics", "blacksea_robotics")
        private const val MIN_VALID_APK_BYTES = 200L * 1024L // 200 KB minimum sanity check
        const val AI_STUDIO_DEBUG_CERT_SHA256 = "5371103212da06ed36fe2d071cd18613e3296dcff6cf66f581020bc763904e88"
    }

    init {
        val currentMigration = prefs.getInt("repo_migration_version", 0)
        val savedOwner = prefs.getString("github_owner", null)?.trim().orEmpty()
        val savedRepo = prefs.getString("github_repo", null)?.trim().orEmpty()
        val isLegacyOwner = savedOwner.lowercase() in LEGACY_OWNERS
        val isLegacyRepo = savedRepo.lowercase() in LEGACY_REPOS

        if (currentMigration < REPO_MIGRATION_VERSION || isLegacyOwner || isLegacyRepo) {
            prefs.edit()
                .putString("github_owner", DEFAULT_GITHUB_OWNER)
                .putString("github_repo", DEFAULT_GITHUB_REPO)
                .putInt("repo_migration_version", REPO_MIGRATION_VERSION)
                .remove("cached_release_json")
                .remove("last_check_timestamp")
                .apply()
        }

        // Never trust a stale "last_installed_version" higher than the actual running BuildConfig.VERSION_CODE
        val savedInstalled = prefs.getInt("last_installed_version", 0)
        if (savedInstalled > BuildConfig.VERSION_CODE) {
            prefs.edit().remove("last_installed_version").apply()
        }
    }

    var githubOwner: String
        get() {
            val v = prefs.getString("github_owner", DEFAULT_GITHUB_OWNER)?.trim().orEmpty()
            return if (v.isBlank() || v.lowercase() in LEGACY_OWNERS) DEFAULT_GITHUB_OWNER else v
        }
        set(value) {
            prefs.edit()
                .putString("github_owner", value.trim().ifBlank { DEFAULT_GITHUB_OWNER })
                .remove("cached_release_json")
                .remove("last_check_timestamp")
                .apply()
        }

    var githubRepo: String
        get() {
            val v = prefs.getString("github_repo", DEFAULT_GITHUB_REPO)?.trim().orEmpty()
            return if (v.isBlank() || v.lowercase() in LEGACY_REPOS) DEFAULT_GITHUB_REPO else v
        }
        set(value) {
            prefs.edit()
                .putString("github_repo", value.trim().ifBlank { DEFAULT_GITHUB_REPO })
                .remove("cached_release_json")
                .remove("last_check_timestamp")
                .apply()
        }

    var githubToken: String
        get() = prefs.getString("github_token", "") ?: ""
        set(value) = prefs.edit().putString("github_token", value.trim()).apply()

    fun resetToDefaultRepo() {
        prefs.edit()
            .putString("github_owner", DEFAULT_GITHUB_OWNER)
            .putString("github_repo", DEFAULT_GITHUB_REPO)
            .putInt("repo_migration_version", REPO_MIGRATION_VERSION)
            .remove("cached_release_json")
            .remove("last_check_timestamp")
            .apply()
    }

    fun normalizeDownloadUrl(rawUrl: String?, versionName: String): String {
        val defaultUrl = "https://github.com/$githubOwner/$githubRepo/releases/download/v$versionName/BlackseaRobotics-v$versionName.apk"
        if (rawUrl.isNullOrBlank()) return defaultUrl
        val lower = rawUrl.lowercase()
        val hasLegacyRef = LEGACY_OWNERS.any { lower.contains("/$it/") } ||
            LEGACY_REPOS.any { lower.contains("/$it/") }
        return if (hasLegacyRef) defaultUrl else rawUrl
    }

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
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun getCurrentVersionName(): String {
        return BuildConfig.VERSION_NAME
    }

    fun getCurrentVersionCode(): Int {
        return BuildConfig.VERSION_CODE
    }

    /**
     * Returns SHA-256 hex digest(s) of the signing certificate(s) of the currently installed app.
     */
    fun getInstalledSigningCertSha256(): Set<String> {
        return try {
            val pm = context.packageManager
            val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
            }
            extractSigningSha256(pkgInfo)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read installed signing certs: ${e.message}")
            emptySet()
        }
    }

    fun getInstalledSigningCertSha256Short(): String {
        val cert = getInstalledSigningCertSha256().firstOrNull() ?: return "Bilinmiyor"
        return cert.uppercase().chunked(2).take(8).joinToString(":") + "..."
    }

    fun isRunningAiStudioDebugSignature(): Boolean {
        return getInstalledSigningCertSha256().any {
            it.equals(AI_STUDIO_DEBUG_CERT_SHA256, ignoreCase = true)
        }
    }

    /**
     * Checks whether the remote release's published signing certificate SHA-256 matches the
     * currently installed APK's certificate before downloading any bytes.
     */
    fun isReleaseSignatureCompatible(
        release: AppReleaseInfo,
        installedCerts: Set<String> = getInstalledSigningCertSha256()
    ): Boolean {
        if (release.expectedCertSha256.isBlank() || installedCerts.isEmpty()) return true
        return installedCerts.any { it.equals(release.expectedCertSha256, ignoreCase = true) }
    }

    private fun extractSigningSha256(pkgInfo: PackageInfo): Set<String> {
        val result = mutableSetOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = pkgInfo.signingInfo ?: return emptySet()
            val signers = if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
            signers?.forEach { sig ->
                result.add(sha256Hex(sig.toByteArray()))
            }
        } else {
            @Suppress("DEPRECATION")
            pkgInfo.signatures?.forEach { sig ->
                result.add(sha256Hex(sig.toByteArray()))
            }
        }
        return result
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun getArchivePackageInfo(apkFile: File): PackageInfo? {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageArchiveInfo(apkFile.absolutePath, flags)
        }
    }

    private fun getLongVersionCodeCompat(pkgInfo: PackageInfo): Long {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pkgInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            pkgInfo.versionCode.toLong()
        }
    }

    private fun compareSemver(candidate: String, current: String): Int {
        val p1 = candidate.removePrefix("v").removePrefix("V").split(".").map { it.toIntOrNull() ?: 0 }
        val p2 = current.removePrefix("v").removePrefix("V").split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(p1.size, p2.size)
        for (i in 0 until maxLen) {
            val a = p1.getOrElse(i) { 0 }
            val b = p2.getOrElse(i) { 0 }
            if (a != b) return a.compareTo(b)
        }
        return 0
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
                "apkSize" to (20 * 1024 * 1024L),
                "isForceUpdate" to isForceUpdate,
                "publishedAt" to "Bugün",
                "updatedAt" to FieldValue.serverTimestamp()
            )
            firestore.collection("app_config").document("latest_update").set(payload).await()

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
            Log.e(TAG, "Failed to broadcast update: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun isReleaseNewerThanCurrent(release: AppReleaseInfo): Boolean {
        return evaluateReleaseComparison(release) is UpdateCheckResult.UpdateAvailable
    }

    /**
     * Evaluates a parsed GitHub release strictly by numeric versionCode (falling back to numeric
     * semver segments ONLY if versionCode was not provided in release metadata).
     */
    internal fun evaluateReleaseComparison(
        release: AppReleaseInfo,
        currentCode: Int = getCurrentVersionCode(),
        currentName: String = getCurrentVersionName()
    ): UpdateCheckResult {
        if (release.apkDownloadUrl.isBlank()) {
            return UpdateCheckResult.ApkAssetMissing(release.tagName.ifBlank { release.versionName })
        }

        val installedCerts = getInstalledSigningCertSha256()
        val installedFirst = installedCerts.firstOrNull().orEmpty()
        val signatureCompatible = isReleaseSignatureCompatible(release, installedCerts)

        return if (release.versionCode > 0) {
            when {
                release.versionCode > currentCode -> UpdateCheckResult.UpdateAvailable(
                    release = release,
                    isSignatureCompatible = signatureCompatible,
                    installedCertSha256 = installedFirst
                )
                release.versionCode == currentCode -> UpdateCheckResult.UpToDate(currentName, currentCode, release, isRemoteOlder = false)
                else -> UpdateCheckResult.UpToDate(currentName, currentCode, release, isRemoteOlder = true)
            }
        } else {
            val semverCmp = compareSemver(release.versionName, currentName)
            when {
                semverCmp > 0 -> UpdateCheckResult.UpdateAvailable(
                    release = release.copy(versionCode = currentCode + 1),
                    isSignatureCompatible = signatureCompatible,
                    installedCertSha256 = installedFirst
                )
                semverCmp == 0 -> UpdateCheckResult.UpToDate(currentName, currentCode, release, isRemoteOlder = false)
                else -> UpdateCheckResult.UpToDate(currentName, currentCode, release, isRemoteOlder = true)
            }
        }
    }

    internal fun parseReleaseJson(json: JSONObject): AppReleaseInfo? = parseRelease(json)

    /**
     * Checks GitHub Releases API (and Firestore config fallback) with rich, explicit status reporting.
     */
    suspend fun checkLatestReleaseDetailed(forceRefresh: Boolean = false): UpdateCheckResult = withContext(Dispatchers.IO) {
        val currentCode = getCurrentVersionCode()
        val currentName = getCurrentVersionName()

        // 1. Check 15-minute cache if not forcing a fresh check
        val now = System.currentTimeMillis()
        val lastCheckTime = prefs.getLong("last_check_timestamp", 0L)
        val cachedReleaseJson = prefs.getString("cached_release_json", null)

        if (!forceRefresh && cachedReleaseJson != null && (now - lastCheckTime) < TimeUnit.MINUTES.toMillis(15)) {
            try {
                val parsed = parseRelease(JSONObject(cachedReleaseJson))
                if (parsed != null) {
                    val eval = evaluateReleaseComparison(parsed, currentCode, currentName)
                    if (eval is UpdateCheckResult.UpdateAvailable) {
                        return@withContext eval
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse cached release: ${e.message}")
            }
        }

        // 2. Query GitHub Releases API (/releases/latest)
        val latestUrl = "https://api.github.com/repos/$githubOwner/$githubRepo/releases/latest"
        try {
            val reqBuilder = Request.Builder()
                .url(latestUrl)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "BlackseaRobotics-AndroidApp/$currentName")

            if (githubToken.isNotBlank()) {
                reqBuilder.header("Authorization", "Bearer $githubToken")
            }

            httpClient.newCall(reqBuilder.build()).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string().orEmpty()
                    if (bodyString.isBlank()) {
                        return@withContext UpdateCheckResult.ApiError(response.code, "GitHub API boş yanıt döndürdü.")
                    }
                    val jsonObject = JSONObject(bodyString)
                    val isDraft = jsonObject.optBoolean("draft", false)
                    val isPrerelease = jsonObject.optBoolean("prerelease", false)
                    if (!isDraft && !isPrerelease) {
                        prefs.edit()
                            .putLong("last_check_timestamp", now)
                            .putString("cached_release_json", bodyString)
                            .apply()

                        val parsed = parseRelease(jsonObject)
                            ?: return@withContext UpdateCheckResult.NoReleaseFound
                        return@withContext evaluateReleaseComparison(parsed, currentCode, currentName)
                    }
                } else if (response.code != 404) {
                    val msg = when (response.code) {
                        403, 429 -> "GitHub API hız sınırına (rate limit) ulaşıldı veya erişim reddedildi."
                        401 -> "GitHub yetkilendirme hatası (HTTP 401)."
                        else -> "GitHub sunucu hatası (HTTP ${response.code})."
                    }
                    Log.e(TAG, "GitHub /releases/latest error: HTTP ${response.code}")
                    return@withContext UpdateCheckResult.ApiError(response.code, msg)
                }
            }

            // 3. If /releases/latest returned 404 (no 'latest' release or only prereleases/repo missing), query /releases list
            val listUrl = "https://api.github.com/repos/$githubOwner/$githubRepo/releases?per_page=10"
            val listReq = Request.Builder()
                .url(listUrl)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "BlackseaRobotics-AndroidApp/$currentName")
                .apply {
                    if (githubToken.isNotBlank()) header("Authorization", "Bearer $githubToken")
                }
                .build()

            httpClient.newCall(listReq).execute().use { listResponse ->
                if (!listResponse.isSuccessful) {
                    if (listResponse.code == 404) {
                        return@withContext UpdateCheckResult.ApiError(
                            404,
                            "GitHub deposu bulunamadı veya gizli ($githubOwner/$githubRepo)."
                        )
                    }
                    return@withContext UpdateCheckResult.ApiError(
                        listResponse.code,
                        "GitHub Releases listesi alınamadı (HTTP ${listResponse.code})."
                    )
                }

                val listBody = listResponse.body?.string().orEmpty()
                val arr = JSONArray(listBody)
                var bestPublished: JSONObject? = null
                var bestParsed: AppReleaseInfo? = null

                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    if (obj.optBoolean("draft", false) || obj.optBoolean("prerelease", false)) continue
                    val rel = parseRelease(obj) ?: continue
                    if (bestParsed == null || rel.versionCode > bestParsed.versionCode) {
                        bestPublished = obj
                        bestParsed = rel
                    }
                }

                if (bestPublished == null || bestParsed == null) {
                    return@withContext UpdateCheckResult.NoReleaseFound
                }

                prefs.edit()
                    .putLong("last_check_timestamp", now)
                    .putString("cached_release_json", bestPublished.toString())
                    .apply()

                return@withContext evaluateReleaseComparison(bestParsed, currentCode, currentName)
            }
        } catch (e: java.net.UnknownHostException) {
            Log.e(TAG, "Network error (UnknownHost): ${e.message}")
            UpdateCheckResult.NetworkError("İnternet bağlantısı yok veya GitHub sunucusuna ulaşılamıyor.")
        } catch (e: java.net.SocketTimeoutException) {
            Log.e(TAG, "Network timeout: ${e.message}")
            UpdateCheckResult.NetworkError("Bağlantı zaman aşımına uğradı. İnternet bağlantınızı kontrol edin.")
        } catch (e: java.io.IOException) {
            Log.e(TAG, "IO error during update check: ${e.message}", e)
            UpdateCheckResult.NetworkError("Ağ bağlantı hatası: ${e.localizedMessage ?: "Bağlantı kurulamadı"}")
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected update check error: ${e.message}", e)
            UpdateCheckResult.ApiError(-1, e.localizedMessage ?: "Bilinmeyen güncelleme kontrolü hatası.")
        }
    }

    /**
     * Legacy Result wrapper preserved for callers that only need an optional AppReleaseInfo.
     */
    suspend fun checkLatestRelease(forceRefresh: Boolean = false): Result<AppReleaseInfo?> {
        return when (val detailed = checkLatestReleaseDetailed(forceRefresh)) {
            is UpdateCheckResult.UpdateAvailable -> Result.success(detailed.release)
            is UpdateCheckResult.UpToDate -> Result.success(null)
            is UpdateCheckResult.NoReleaseFound -> Result.failure(Exception("GitHub üzerinde yayınlanmış sürüm bulunamadı."))
            is UpdateCheckResult.ApkAssetMissing -> Result.failure(Exception("Sürüm (${detailed.tagName}) bulundu ancak .apk dosyası eklenmemiş."))
            is UpdateCheckResult.NetworkError -> Result.failure(Exception(detailed.detail))
            is UpdateCheckResult.ApiError -> Result.failure(Exception(detailed.detail))
        }
    }

    /**
     * Resolves a verified, non-404 public download or releases URL for sharing and browser opening.
     */
    suspend fun resolvePublicDownloadUrl(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val latestApiUrl = "https://api.github.com/repos/$githubOwner/$githubRepo/releases/latest"
            val reqBuilder = Request.Builder()
                .url(latestApiUrl)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "BlackseaRobotics-AndroidApp/${BuildConfig.VERSION_NAME}")

            if (githubToken.isNotBlank()) {
                reqBuilder.header("Authorization", "Bearer $githubToken")
            }

            httpClient.newCall(reqBuilder.build()).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string().orEmpty()
                    if (bodyStr.isNotBlank()) {
                        val json = JSONObject(bodyStr)
                        val parsed = parseRelease(json)
                        if (parsed != null && parsed.apkDownloadUrl.isNotBlank()) {
                            return@withContext Result.success(parsed.apkDownloadUrl)
                        }
                        val htmlUrl = json.optString("html_url", "")
                        if (htmlUrl.isNotBlank()) {
                            return@withContext Result.success(htmlUrl)
                        }
                    }
                    return@withContext Result.success("https://github.com/$githubOwner/$githubRepo/releases")
                }
            }

            val repoApiUrl = "https://api.github.com/repos/$githubOwner/$githubRepo"
            val repoReq = Request.Builder()
                .url(repoApiUrl)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "BlackseaRobotics-AndroidApp/${BuildConfig.VERSION_NAME}")
                .apply {
                    if (githubToken.isNotBlank()) header("Authorization", "Bearer $githubToken")
                }
                .build()

            httpClient.newCall(repoReq).execute().use { repoRes ->
                if (repoRes.isSuccessful) {
                    val repoJson = JSONObject(repoRes.body?.string().orEmpty())
                    val isPrivate = repoJson.optBoolean("private", false)
                    if (isPrivate) {
                        return@withContext Result.failure(
                            Exception("Depo ($githubOwner/$githubRepo) şu anda gizli (Private). Herkese açık yapmak için GitHub Settings > Danger Zone'dan Public yapın.")
                        )
                    }
                    return@withContext Result.success("https://github.com/$githubOwner/$githubRepo/releases")
                } else {
                    return@withContext Result.failure(
                        Exception("GitHub deposu bulunamadı veya gizli (Private): https://github.com/$githubOwner/$githubRepo")
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "resolvePublicDownloadUrl error: ${e.message}")
            Result.failure(Exception("GitHub'a erişilemedi. İnternet bağlantınızı kontrol edin."))
        }
    }

    /**
     * Fetches release history for the "Güncelleme Geçmişi" screen.
     */
    suspend fun fetchReleaseHistory(): Result<List<AppReleaseInfo>> = withContext(Dispatchers.IO) {
        val url = "https://api.github.com/repos/$githubOwner/$githubRepo/releases?per_page=10"
        try {
            val reqBuilder = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "BlackseaRobotics-AndroidApp/${BuildConfig.VERSION_NAME}")

            if (githubToken.isNotBlank()) {
                reqBuilder.header("Authorization", "Bearer $githubToken")
            }

            httpClient.newCall(reqBuilder.build()).execute().use { response ->
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
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fetch history error: ${e.message}")
            Result.failure(e)
        }
    }

    private fun parseRelease(json: JSONObject): AppReleaseInfo? {
        if (json.optBoolean("draft", false)) return null

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
            val assetName = asset.optString("name", "").trim()
            val downloadUrl = asset.optString("browser_download_url", "").trim()
            val assetState = asset.optString("state", "uploaded")
            val isApkFile = assetName.endsWith(".apk", ignoreCase = true)
            val isUploaded = assetState.equals("uploaded", ignoreCase = true) || assetState.isBlank()
            val isUnsigned = assetName.contains("unsigned", ignoreCase = true)

            if (isApkFile && isUploaded && !isUnsigned && downloadUrl.startsWith("https://")) {
                apkUrl = downloadUrl
                apkFileName = assetName
                apkSize = asset.optLong("size", 0L)
                break
            }
        }

        // Parse explicit integer versionCode from release body (supports "versionCode: 6" AND legacy "**Sürüm Kodu (versionCode):** 5")
        var versionCode = 0
        val vcRegex = Regex("""(?i)versionCode[\s)*`]*[:=][\s*`]*(\d+)""")
        val vcMatch = vcRegex.find(body)
        if (vcMatch != null) {
            versionCode = vcMatch.groupValues[1].toIntOrNull() ?: 0
        }

        val cleanTag = tagName.removePrefix("v").removePrefix("V")

        val isForce = body.contains("forceUpdate: true", ignoreCase = true) ||
            body.contains("force_update: true", ignoreCase = true) ||
            body.contains("[force]", ignoreCase = true)

        val shaRegex = Regex("""(?i)sha256(?:\s*\*\*)?\s*[:=]\s*`?([a-fA-F0-9]{64})`?""")
        val shaMatch = shaRegex.find(body)
        val sha256 = shaMatch?.groupValues?.get(1)?.lowercase() ?: ""

        val certShaRegex = Regex("""(?i)certSha256\s*[:=]\s*`?([a-fA-F0-9]{64})`?""")
        val certShaMatch = certShaRegex.find(body)
        val certSha256 = certShaMatch?.groupValues?.get(1)?.lowercase() ?: ""

        val cleanChangelog = body
            .lines()
            .filterNot {
                it.contains("forceUpdate:", ignoreCase = true) ||
                    it.contains("versionCode:", ignoreCase = true) ||
                    it.contains("versionName:", ignoreCase = true) ||
                    it.contains("applicationId:", ignoreCase = true) ||
                    it.contains("certSha256:", ignoreCase = true) ||
                    it.contains("sha256:", ignoreCase = true)
            }
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
            expectedCertSha256 = certSha256,
            isForceUpdate = isForce
        )
    }

    /**
     * Downloads the APK strictly verifying HTTP content type, completeness, ZIP/APK format,
     * SHA-256 checksum, packageName, versionCode, and signing certificate compatibility.
     */
    suspend fun downloadApk(
        releaseInfo: AppReleaseInfo,
        onProgress: (UpdateDownloadState) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        val downloadUrl = releaseInfo.apkDownloadUrl.trim()
        if (downloadUrl.isBlank() || !(downloadUrl.startsWith("http://") || downloadUrl.startsWith("https://"))) {
            onProgress(UpdateDownloadState.Error("Geçerli bir APK indirme bağlantısı bulunamadı."))
            return@withContext null
        }

        if (!downloadUrl.endsWith(".apk", ignoreCase = true) && !downloadUrl.contains("/releases/download/", ignoreCase = true)) {
            onProgress(UpdateDownloadState.Error("İndirme bağlantısı doğrudan bir APK dosyasına işaret etmiyor ($downloadUrl)."))
            return@withContext null
        }

        val installedPreCheck = getInstalledSigningCertSha256()
        if (!isReleaseSignatureCompatible(releaseInfo, installedPreCheck)) {
            val installedShort = installedPreCheck.firstOrNull().orEmpty().take(16).uppercase()
            val expectedShort = releaseInfo.expectedCertSha256.take(16).uppercase()
            val msg = "İmza Uyuşmazlığı: Bu sürüm mevcut uygulamanın imza sertifikasıyla eşleşmiyor; üzerine güncellenemez.\n• Kurulu imza: $installedShort...\n• Release imza: $expectedShort...\nTemiz kurulum için eski uygulamayı kaldırıp yeni APK'yı tarayıcıdan kurabilirsiniz."
            onProgress(
                UpdateDownloadState.Error(
                    message = msg,
                    isSignatureMismatch = true,
                    manualDownloadUrl = downloadUrl
                )
            )
            return@withContext null
        }

        val updatesDir = File(context.cacheDir, "updates")
        if (!updatesDir.exists()) {
            updatesDir.mkdirs()
        }
        updatesDir.listFiles()?.forEach { it.delete() }

        val tempFile = File(updatesDir, "download_in_progress.apk.tmp")
        val destinationFile = File(updatesDir, releaseInfo.apkFileName.ifBlank { "BlackseaRobotics-update.apk" })

        try {
            val reqBuilder = Request.Builder()
                .url(downloadUrl)
                .header("User-Agent", "BlackseaRobotics-AndroidApp/${BuildConfig.VERSION_NAME}")
                .header("Accept", "application/vnd.android.package-archive, application/octet-stream, */*")
            if (githubToken.isNotBlank() && downloadUrl.contains("github.com", ignoreCase = true)) {
                reqBuilder.header("Authorization", "Bearer $githubToken")
            }

            httpClient.newCall(reqBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    val msg = "GitHub üzerinden APK indirilemedi (HTTP ${response.code}). Lütfen release'in yayında ve deponun herkese açık olduğundan emin olun."
                    Log.e(TAG, "Download failed: HTTP ${response.code} for $downloadUrl")
                    onProgress(UpdateDownloadState.Error(msg))
                    return@withContext null
                }

                val contentType = response.header("Content-Type").orEmpty().lowercase()
                if (contentType.contains("text/html") || contentType.contains("application/xhtml")) {
                    val msg = "APK yerine HTML web sayfası döndü (Content-Type: $contentType). İndirme bağlantısı doğrudan APK dosyası değil."
                    Log.e(TAG, msg)
                    onProgress(UpdateDownloadState.Error(msg))
                    return@withContext null
                }

                val body = response.body
                if (body == null) {
                    onProgress(UpdateDownloadState.Error("Sunucudan boş yanıt alındı."))
                    return@withContext null
                }

                val headerLength = body.contentLength()
                val totalBytes = if (headerLength > 0) headerLength else releaseInfo.apkSize
                var downloadedBytes = 0L

                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var read: Int
                        var lastReportTime = 0L

                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            downloadedBytes += read

                            val now = System.currentTimeMillis()
                            if (now - lastReportTime > 120 || (totalBytes > 0 && downloadedBytes == totalBytes)) {
                                lastReportTime = now
                                val percent = if (totalBytes > 0) ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100) else 0
                                onProgress(UpdateDownloadState.Downloading(percent, downloadedBytes, totalBytes))
                            }
                        }
                        output.flush()
                    }
                }

                // 1. Completeness & Size verification
                if (!tempFile.exists() || downloadedBytes < MIN_VALID_APK_BYTES) {
                    tempFile.delete()
                    val msg = "İndirilen dosya geçersiz veya çok küçük (${downloadedBytes} bayt). Eksik indirme veya hata sayfası olabilir."
                    Log.e(TAG, msg)
                    onProgress(UpdateDownloadState.Error(msg))
                    return@withContext null
                }

                if (headerLength > 0 && downloadedBytes != headerLength) {
                    tempFile.delete()
                    val msg = "APK indirmesi yarım kaldı ($downloadedBytes / $headerLength bayt). Lütfen tekrar deneyin."
                    Log.e(TAG, msg)
                    onProgress(UpdateDownloadState.Error(msg))
                    return@withContext null
                }
            }

            // 2. ZIP / APK Magic Bytes Verification (PK\x03\x04)
            val magic = ByteArray(4)
            FileInputStream(tempFile).use { fis ->
                val n = fis.read(magic)
                if (n < 4 || magic[0] != 0x50.toByte() || magic[1] != 0x4B.toByte() || magic[2] != 0x03.toByte() || magic[3] != 0x04.toByte()) {
                    tempFile.delete()
                    val msg = "İndirilen dosya geçerli bir APK/ZIP formatında değil (HTML veya bozuk dosya)."
                    Log.e(TAG, msg)
                    onProgress(UpdateDownloadState.Error(msg))
                    return@withContext null
                }
            }

            // 3. SHA-256 File Integrity Verification
            if (releaseInfo.expectedSha256.isNotBlank()) {
                val computedSha = calculateFileSha256(tempFile)
                if (!computedSha.equals(releaseInfo.expectedSha256, ignoreCase = true)) {
                    tempFile.delete()
                    val msg = "Güvenlik hatası: İndirilen APK'nın SHA-256 bütünlüğü doğrulanamadı."
                    Log.e(TAG, "$msg Expected=${releaseInfo.expectedSha256}, Actual=$computedSha")
                    onProgress(UpdateDownloadState.Error(msg))
                    return@withContext null
                }
            }

            // 4. Parse AndroidManifest & Signing Certificates from Downloaded APK BEFORE Installing
            val archiveInfo = getArchivePackageInfo(tempFile)
            if (archiveInfo == null) {
                tempFile.delete()
                val msg = "Android paket bilgisi okunamadı. İndirilen APK bozuk olabilir."
                Log.e(TAG, msg)
                onProgress(UpdateDownloadState.Error(msg))
                return@withContext null
            }

            if (archiveInfo.packageName != context.packageName) {
                tempFile.delete()
                val msg = "Paket adı (applicationId) uyuşmuyor!\nKurulu: ${context.packageName}\nİndirilen APK: ${archiveInfo.packageName}"
                Log.e(TAG, msg)
                onProgress(UpdateDownloadState.Error(msg))
                return@withContext null
            }

            val downloadedVCode = getLongVersionCodeCompat(archiveInfo)
            val currentVCode = getCurrentVersionCode().toLong()
            if (downloadedVCode <= currentVCode) {
                tempFile.delete()
                val msg = "Sürüm kodu (versionCode) hatası: İndirilen APK sürüm kodu ($downloadedVCode), telefondaki kurulu sürümden ($currentVCode) büyük değil. Yeni sürüm oluşturulmadan güncelleme yapılamaz."
                Log.e(TAG, msg)
                onProgress(UpdateDownloadState.Error(msg))
                return@withContext null
            }

            val installedCerts = getInstalledSigningCertSha256()
            val downloadedCerts = extractSigningSha256(archiveInfo)
            Log.i(TAG, "APK check OK -> pkg=${archiveInfo.packageName}, vCode=$downloadedVCode (current=$currentVCode), installedCerts=$installedCerts, apkCerts=$downloadedCerts")

            if (installedCerts.isNotEmpty() && downloadedCerts.isNotEmpty() && installedCerts.intersect(downloadedCerts).isEmpty()) {
                tempFile.delete()
                val installedShort = installedCerts.first().take(16).uppercase()
                val downloadedShort = downloadedCerts.first().take(16).uppercase()
                val sourceHint = if (isRunningAiStudioDebugSignature()) {
                    "Telefondaki uygulama geliştirme/önizleme (debug) imzası taşıyor; GitHub Release APK ise kalıcı release keystore ile imzalanmış."
                } else {
                    "Telefondaki uygulama ile indirilen APK farklı imza sertifikalarıyla imzalanmış."
                }
                val msg = "İmza Uyuşmazlığı: $sourceHint\n• Kurulu imza: $installedShort...\n• Yeni APK imzası: $downloadedShort...\nAndroid güvenlik kuralı gereği farklı imzalı APK eski sürümün üzerine yazılamaz. İlk kurulum için mevcut uygulamayı kaldırıp yeni imzalı APK'yı kurabilir veya GitHub Actions'ta kurulu sürümle aynı keystore'u kullanabilirsiniz."
                Log.e(TAG, "Signature mismatch! Installed=$installedCerts, Downloaded=$downloadedCerts")
                onProgress(
                    UpdateDownloadState.Error(
                        message = msg,
                        isSignatureMismatch = true,
                        manualDownloadUrl = releaseInfo.apkDownloadUrl
                    )
                )
                return@withContext null
            }

            if (!tempFile.renameTo(destinationFile)) {
                tempFile.copyTo(destinationFile, overwrite = true)
                tempFile.delete()
            }

            onProgress(UpdateDownloadState.Ready(destinationFile))
            destinationFile
        } catch (e: Exception) {
            tempFile.delete()
            val msg = "İndirme hatası: ${e.message ?: "Bilinmeyen ağ hatası"}"
            Log.e(TAG, msg, e)
            onProgress(UpdateDownloadState.Error(msg))
            null
        }
    }

    private fun calculateFileSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            var n: Int
            while (fis.read(buffer).also { n = it } != -1) {
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun canInstallApks(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

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
     * Installs the verified APK using Android's PackageInstaller Session API so we capture
     * exact installation status codes and EXTRA_STATUS_MESSAGE via UpdateInstallReceiver.
     */
    suspend fun installApkWithPackageInstaller(apkFile: File): Result<Unit> = withContext(Dispatchers.IO) {
        if (!apkFile.exists() || apkFile.length() < MIN_VALID_APK_BYTES) {
            return@withContext Result.failure(Exception("Geçerli APK dosyası bulunamadı."))
        }

        var session: PackageInstaller.Session? = null
        try {
            UpdateInstallResultBus.update(InstallResultState.Installing)
            val packageInstaller = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(context.packageName)
                setSize(apkFile.length())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    setInstallReason(PackageManager.INSTALL_REASON_USER)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }
            }

            val sessionId = packageInstaller.createSession(params)
            session = packageInstaller.openSession(sessionId)

            FileInputStream(apkFile).use { input ->
                session.openWrite("base.apk", 0, apkFile.length()).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                    }
                    session.fsync(output)
                }
            }

            val receiverIntent = Intent(context, UpdateInstallReceiver::class.java)
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = PendingIntent.getBroadcast(context, sessionId, receiverIntent, flags)

            session.commit(pendingIntent.intentSender)
            Log.i(TAG, "PackageInstaller session $sessionId committed for ${apkFile.name}")
            Result.success(Unit)
        } catch (e: Exception) {
            session?.abandon()
            Log.e(TAG, "PackageInstaller commit failed: ${e.message}", e)
            UpdateInstallResultBus.update(
                InstallResultState.Error(
                    statusCode = PackageInstaller.STATUS_FAILURE,
                    statusName = "SESSION_EXCEPTION",
                    rawMessage = e.message.orEmpty(),
                    userMessage = "PackageInstaller başlatılamadı: ${e.message}"
                )
            )
            Result.failure(e)
        }
    }

    /**
     * Fallback installer using FileProvider content:// URI and ACTION_VIEW.
     */
    fun installApkViaFileProvider(apkFile: File): Result<Unit> {
        if (!apkFile.exists()) {
            return Result.failure(Exception("APK dosyası bulunamadı."))
        }

        return try {
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
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch FileProvider installer: ${e.message}", e)
            Result.failure(e)
        }
    }
}
