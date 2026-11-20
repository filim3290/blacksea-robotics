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
import androidx.core.content.FileProvider
import com.example.BuildConfig
import com.example.util.AppMessages
import com.example.util.logDebug
import com.example.util.logDebugError
import com.example.util.logDebugWarn
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
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
        const val FORCE_UPDATE_MAX_VERSION_LAG = 6

        fun hasNumericSemver(version: String): Boolean {
            return Regex("""\d+(?:\.\d+)+""").containsMatchIn(version.trim())
        }

        fun compareSemver(candidate: String, current: String): Int {
            val p1 = candidate.trim().removePrefix("v").removePrefix("V").split(".").map { it.toIntOrNull() ?: 0 }
            val p2 = current.trim().removePrefix("v").removePrefix("V").split(".").map { it.toIntOrNull() ?: 0 }
            val maxLen = maxOf(p1.size, p2.size)
            for (i in 0 until maxLen) {
                val a = p1.getOrElse(i) { 0 }
                val b = p2.getOrElse(i) { 0 }
                if (a != b) return a.compareTo(b)
            }
            return 0
        }

        /**
         * Calculates how many release steps [currentName]/[currentCode] is behind [latestName]/[latestCode].
         * Supports the project's 12-rollover minor progression (e.g. 1.0.12 -> 1.1.0 is 1 step;
         * 1.1.6 -> 1.1.12 is 6 steps).
         */
        fun calculateVersionLagSteps(
            latestName: String,
            latestCode: Int,
            currentName: String,
            currentCode: Int
        ): Int {
            if (hasNumericSemver(latestName) && hasNumericSemver(currentName)) {
                val cmp = compareSemver(latestName, currentName)
                if (cmp <= 0) return 0
                val lParts = latestName.trim().removePrefix("v").removePrefix("V").split(".").map { it.toIntOrNull() ?: 0 }
                val cParts = currentName.trim().removePrefix("v").removePrefix("V").split(".").map { it.toIntOrNull() ?: 0 }
                val lMaj = lParts.getOrElse(0) { 0 }
                val lMin = lParts.getOrElse(1) { 0 }
                val lPat = lParts.getOrElse(2) { 0 }
                val cMaj = cParts.getOrElse(0) { 0 }
                val cMin = cParts.getOrElse(1) { 0 }
                val cPat = cParts.getOrElse(2) { 0 }
                val semverLag = ((lMaj - cMaj) * 100 + (lMin - cMin) * 13 + (lPat - cPat)).coerceAtLeast(1)
                return semverLag
            }
            if (latestCode > 0 && currentCode > 0 && latestCode > currentCode) {
                return latestCode - currentCode
            }
            return 0
        }

        fun isVersionForceUpdateRequired(
            release: AppReleaseInfo,
            currentCode: Int,
            currentName: String,
            minSupportedVersionName: String = "",
            minSupportedVersionCode: Int = 0
        ): Boolean {
            val isNewer = if (hasNumericSemver(release.versionName) && hasNumericSemver(currentName)) {
                compareSemver(release.versionName, currentName) > 0
            } else {
                release.versionCode > currentCode && release.versionCode > 0
            }
            if (!isNewer) return false
            if (release.isForceUpdate) return true
            if (minSupportedVersionName.isNotBlank() &&
                hasNumericSemver(minSupportedVersionName) &&
                hasNumericSemver(currentName) &&
                compareSemver(currentName, minSupportedVersionName) < 0
            ) {
                return true
            }
            if (minSupportedVersionCode > 0 && currentCode > 0 && currentCode < minSupportedVersionCode) {
                return true
            }
            return calculateVersionLagSteps(
                latestName = release.versionName,
                latestCode = release.versionCode,
                currentName = currentName,
                currentCode = currentCode
            ) >= FORCE_UPDATE_MAX_VERSION_LAG
        }
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
                .remove("cached_release_etag")
                .remove("last_check_timestamp")
                .apply()
        }

        // Never trust a stale "last_installed_version" higher than the actual running BuildConfig.VERSION_CODE
        val savedInstalled = prefs.getInt("last_installed_version", 0)
        if (savedInstalled > BuildConfig.VERSION_CODE) {
            prefs.edit().remove("last_installed_version").apply()
        }

        // Remove any legacy plaintext token entry if present
        if (prefs.contains("github_token")) {
            prefs.edit().remove("github_token").apply()
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
                .remove("cached_release_etag")
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
                .remove("cached_release_etag")
                .remove("last_check_timestamp")
                .apply()
        }

    fun resetToDefaultRepo() {
        prefs.edit()
            .putString("github_owner", DEFAULT_GITHUB_OWNER)
            .putString("github_repo", DEFAULT_GITHUB_REPO)
            .putInt("repo_migration_version", REPO_MIGRATION_VERSION)
            .remove("cached_release_json")
            .remove("cached_release_etag")
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

    fun hasNotifiedForRelease(versionCode: Int, tagName: String): Boolean {
        if (versionCode > 0 && prefs.getInt("last_notified_version", 0) >= versionCode) {
            return true
        }
        val normalizedTag = tagName.trim().lowercase()
        if (normalizedTag.isNotBlank()) {
            val notifiedTags = prefs.getString("notified_tags_csv", "")
                .orEmpty()
                .split(",")
                .map { it.trim().lowercase() }
                .filter { it.isNotBlank() }
                .toSet()
            if (normalizedTag in notifiedTags) return true
        }
        return false
    }

    fun markReleaseNotified(versionCode: Int, tagName: String) {
        val editor = prefs.edit()
        if (versionCode > 0) {
            val currentMax = prefs.getInt("last_notified_version", 0)
            editor.putInt("last_notified_version", maxOf(currentMax, versionCode))
        }
        val normalizedTag = tagName.trim().lowercase()
        if (normalizedTag.isNotBlank()) {
            val updated = (prefs.getString("notified_tags_csv", "")
                .orEmpty()
                .split(",")
                .map { it.trim().lowercase() }
                .filter { it.isNotBlank() } + normalizedTag)
                .distinct()
                .takeLast(30)
            editor.putString("notified_tags_csv", updated.joinToString(","))
        }
        editor.apply()
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
        val pkgName = try {
            val pm = context.packageManager
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, 0)
            }
            info.versionName?.trim().orEmpty()
        } catch (_: Exception) {
            ""
        }
        return if (pkgName.isNotBlank() && compareSemver(pkgName, BuildConfig.VERSION_NAME) >= 0) {
            pkgName
        } else {
            BuildConfig.VERSION_NAME
        }
    }

    fun getCurrentVersionCode(): Int {
        val pkgCode = try {
            val pm = context.packageManager
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, 0)
            }
            getLongVersionCodeCompat(info).toInt()
        } catch (_: Exception) {
            0
        }
        return maxOf(BuildConfig.VERSION_CODE, pkgCode)
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
            logDebugWarn(TAG, "Failed to read installed signing certs", e)
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

    private fun hasNumericSemver(version: String): Boolean = AppUpdateManager.hasNumericSemver(version)

    private fun compareSemver(candidate: String, current: String): Int = AppUpdateManager.compareSemver(candidate, current)

    fun isUpdateAlreadyInstalled(
        remoteVersionCode: Int,
        remoteVersionName: String,
        currentCode: Int = getCurrentVersionCode(),
        currentName: String = getCurrentVersionName()
    ): Boolean {
        if (hasNumericSemver(remoteVersionName) && hasNumericSemver(currentName)) {
            if (compareSemver(remoteVersionName, currentName) <= 0) return true
        }
        if (remoteVersionCode > 0 && remoteVersionCode <= currentCode) return true
        return false
    }

    fun isNotificationUpdateObsolete(
        type: String,
        targetId: String,
        message: String,
        currentName: String = getCurrentVersionName()
    ): Boolean {
        if (!type.equals("update", ignoreCase = true)) return false
        val versionRegex = Regex("""v?(\d+\.\d+(?:\.\d+)*)""")
        val extracted = versionRegex.find(targetId)?.groupValues?.getOrNull(1)
            ?: versionRegex.find(message)?.groupValues?.getOrNull(1)
            ?: return false
        return hasNumericSemver(currentName) && compareSemver(extracted, currentName) <= 0
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

            val currentUid = try {
                com.google.firebase.Firebase.auth.currentUser?.uid ?: ""
            } catch (_: Exception) {
                ""
            }
            val notif = mapOf(
                "teamId" to "platform",
                "teamName" to "BlackSea Robotics Platform",
                "recipientUserId" to "all",
                "title" to "🔔 Yeni Güncelleme Mevcut",
                "message" to "Blacksea Robotics için yeni bir sürüm (v$versionName) yayınlandı. Hemen güncelleyin!",
                "type" to "update",
                "targetId" to "update_v$versionName",
                "senderId" to currentUid,
                "senderName" to "Sistem",
                "isRead" to false,
                "createdAt" to FieldValue.serverTimestamp()
            )
            firestore.collection("notifications").document().set(notif).await()

            Result.success(Unit)
        } catch (e: Exception) {
            logDebugError(TAG, "Failed to broadcast update", e)
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

        if (hasNumericSemver(release.versionName) && hasNumericSemver(currentName)) {
            val semverCmp = compareSemver(release.versionName, currentName)
            when {
                semverCmp < 0 -> return UpdateCheckResult.UpToDate(currentName, currentCode, release, isRemoteOlder = true)
                semverCmp == 0 -> return UpdateCheckResult.UpToDate(currentName, currentCode, release, isRemoteOlder = false)
            }
        }

        val effectiveForce = isVersionForceUpdateRequired(
            release = release,
            currentCode = currentCode,
            currentName = currentName
        )
        val effectiveRelease = if (effectiveForce == release.isForceUpdate) {
            release
        } else {
            release.copy(isForceUpdate = effectiveForce)
        }

        return if (effectiveRelease.versionCode > 0) {
            when {
                effectiveRelease.versionCode > currentCode -> UpdateCheckResult.UpdateAvailable(
                    release = effectiveRelease,
                    isSignatureCompatible = signatureCompatible,
                    installedCertSha256 = installedFirst
                )
                effectiveRelease.versionCode == currentCode -> UpdateCheckResult.UpToDate(currentName, currentCode, effectiveRelease, isRemoteOlder = false)
                else -> UpdateCheckResult.UpToDate(currentName, currentCode, effectiveRelease, isRemoteOlder = true)
            }
        } else {
            val semverCmp = compareSemver(effectiveRelease.versionName, currentName)
            when {
                semverCmp > 0 -> UpdateCheckResult.UpdateAvailable(
                    release = effectiveRelease.copy(versionCode = currentCode + 1),
                    isSignatureCompatible = signatureCompatible,
                    installedCertSha256 = installedFirst
                )
                semverCmp == 0 -> UpdateCheckResult.UpToDate(currentName, currentCode, effectiveRelease, isRemoteOlder = false)
                else -> UpdateCheckResult.UpToDate(currentName, currentCode, effectiveRelease, isRemoteOlder = true)
            }
        }
    }

    internal fun parseReleaseJson(json: JSONObject): AppReleaseInfo? = parseRelease(json)

    internal fun isRateLimitedOrTransientFailure(code: Int): Boolean {
        return code == 403 || code == 429 || code >= 500
    }

    internal fun parseReleaseRedirectTag(locationHeader: String?): String? {
        if (locationHeader.isNullOrBlank()) return null
        val marker = "/releases/tag/"
        val idx = locationHeader.indexOf(marker, ignoreCase = true)
        if (idx < 0) return null
        val rawTag = locationHeader
            .substring(idx + marker.length)
            .substringBefore("?")
            .substringBefore("#")
            .trim()
            .trimEnd('/')
        if (rawTag.isBlank() || rawTag.equals("latest", ignoreCase = true)) return null
        return rawTag
    }

    private fun fetchLatestReleaseFromWebFallback(currentName: String): AppReleaseInfo? {
        return try {
            val noRedirectClient = httpClient.newBuilder()
                .followRedirects(false)
                .followSslRedirects(false)
                .build()
            val latestWebUrl = "https://github.com/$githubOwner/$githubRepo/releases/latest"
            val req = Request.Builder()
                .url(latestWebUrl)
                .header("User-Agent", "BlackseaRobotics-AndroidApp/$currentName")
                .build()

            var resolvedTag: String? = null
            noRedirectClient.newCall(req).execute().use { response ->
                resolvedTag = parseReleaseRedirectTag(response.header("Location"))
                if (resolvedTag == null && response.isSuccessful) {
                    val html = response.body?.string().orEmpty()
                    val match = Regex("""/releases/tag/(v?\d+(?:\.\d+)+)""").find(html)
                    resolvedTag = match?.groupValues?.getOrNull(1)
                }
            }

            val tag = resolvedTag ?: return null
            val cleanVersion = tag.removePrefix("v").removePrefix("V")
            var parsedVersionCode = 0
            var parsedSha256 = ""
            var parsedCertSha256 = ""
            var apkFileName = "BlackseaRobotics-$tag.apk"
            var apkDownloadUrl = "https://github.com/$githubOwner/$githubRepo/releases/download/$tag/$apkFileName"

            try {
                val tagPageReq = Request.Builder()
                    .url("https://github.com/$githubOwner/$githubRepo/releases/tag/$tag")
                    .header("User-Agent", "BlackseaRobotics-AndroidApp/$currentName")
                    .build()
                httpClient.newCall(tagPageReq).execute().use { tagResp ->
                    if (tagResp.isSuccessful) {
                        val pageHtml = tagResp.body?.string().orEmpty()
                        Regex("""(?i)versionCode[\s)*`:=]+(\d+)""").find(pageHtml)?.let {
                            parsedVersionCode = it.groupValues[1].toIntOrNull() ?: 0
                        }
                        Regex("""(?i)sha256\s*[:=]\s*`?([a-fA-F0-9]{64})`?""").find(pageHtml)?.let {
                            parsedSha256 = it.groupValues[1].lowercase()
                        }
                        Regex("""(?i)certSha256\s*[:=]\s*`?([a-fA-F0-9]{64})`?""").find(pageHtml)?.let {
                            parsedCertSha256 = it.groupValues[1].lowercase()
                        }
                        Regex("""/$githubOwner/$githubRepo/releases/download/[^"'\s<>]+/([^"'\s<>]+\.apk)""", RegexOption.IGNORE_CASE)
                            .find(pageHtml)
                            ?.let { assetMatch ->
                                val relPath = assetMatch.value
                                val file = assetMatch.groupValues[1]
                                if (!file.contains("unsigned", ignoreCase = true)) {
                                    apkFileName = file
                                    apkDownloadUrl = "https://github.com$relPath"
                                }
                            }
                    }
                }
            } catch (e: Exception) {
                logDebugWarn(TAG, "Web fallback tag page inspection skipped for $tag", e)
            }

            AppReleaseInfo(
                tagName = tag,
                versionName = cleanVersion,
                versionCode = parsedVersionCode,
                title = "Blacksea Robotics $tag",
                changelog = "Yeni özellikler ve iyileştirmeler.",
                publishedAt = "",
                apkDownloadUrl = apkDownloadUrl,
                apkFileName = apkFileName,
                apkSize = 0L,
                expectedSha256 = parsedSha256,
                expectedCertSha256 = parsedCertSha256,
                isForceUpdate = false
            )
        } catch (e: Exception) {
            logDebugWarn(TAG, "Web fallback release lookup failed", e)
            null
        }
    }

    /**
     * Checks GitHub Releases API (with ETag + web redirect fallback) with explicit status reporting.
     */
    suspend fun checkLatestReleaseDetailed(
        forceRefresh: Boolean = false,
        bypassEtag: Boolean = false
    ): UpdateCheckResult = withContext(Dispatchers.IO) {
        val currentCode = getCurrentVersionCode()
        val currentName = getCurrentVersionName()

        // 1. Check short 2-minute cache if not forcing a fresh check
        val now = System.currentTimeMillis()
        val lastCheckTime = prefs.getLong("last_check_timestamp", 0L)
        val cachedReleaseJson = prefs.getString("cached_release_json", null)
        val cachedEtag = prefs.getString("cached_release_etag", null)

        if (!forceRefresh && cachedReleaseJson != null && (now - lastCheckTime) < TimeUnit.MINUTES.toMillis(2)) {
            try {
                val parsed = parseRelease(JSONObject(cachedReleaseJson))
                if (parsed != null) {
                    val eval = evaluateReleaseComparison(parsed, currentCode, currentName)
                    if (eval is UpdateCheckResult.UpdateAvailable) {
                        return@withContext eval
                    }
                }
            } catch (e: Exception) {
                logDebugWarn(TAG, "Failed to parse cached release", e)
            }
        }

        // 2. Query GitHub Releases API (/releases/latest) with conditional ETag validation (304 saves API quota)
        val latestUrl = "https://api.github.com/repos/$githubOwner/$githubRepo/releases/latest"
        try {
            val reqBuilder = Request.Builder()
                .url(latestUrl)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "BlackseaRobotics-AndroidApp/$currentName")

            if (!bypassEtag && !cachedReleaseJson.isNullOrBlank() && !cachedEtag.isNullOrBlank()) {
                reqBuilder.header("If-None-Match", cachedEtag)
            }

            httpClient.newCall(reqBuilder.build()).execute().use { response ->
                if (response.code == 304 && !cachedReleaseJson.isNullOrBlank()) {
                    try {
                        val cachedObj = JSONObject(cachedReleaseJson)
                        val parsedCached = parseRelease(cachedObj)
                        if (parsedCached != null) {
                            prefs.edit().putLong("last_check_timestamp", now).apply()
                            return@withContext evaluateReleaseComparison(parsedCached, currentCode, currentName)
                        }
                    } catch (e: Exception) {
                        logDebugWarn(TAG, "Failed to parse cached release after 304", e)
                    }
                }

                if (response.isSuccessful) {
                    val bodyString = response.body?.string().orEmpty()
                    if (bodyString.isBlank()) {
                        return@withContext UpdateCheckResult.ApiError(response.code, AppMessages.UPDATE_CHECK_ERROR)
                    }
                    val jsonObject = JSONObject(bodyString)
                    val isDraft = jsonObject.optBoolean("draft", false)
                    val isPrerelease = jsonObject.optBoolean("prerelease", false)
                    if (!isDraft && !isPrerelease) {
                        val responseEtag = response.header("ETag").orEmpty()
                        val editor = prefs.edit()
                            .putLong("last_check_timestamp", now)
                            .putString("cached_release_json", bodyString)
                        if (responseEtag.isNotBlank()) {
                            editor.putString("cached_release_etag", responseEtag)
                        }
                        editor.apply()

                        val parsed = parseRelease(jsonObject)
                            ?: return@withContext UpdateCheckResult.NoReleaseFound
                        return@withContext evaluateReleaseComparison(parsed, currentCode, currentName)
                    }
                } else if (isRateLimitedOrTransientFailure(response.code)) {
                    logDebugWarn(TAG, "GitHub API returned HTTP ${response.code}; trying web redirect fallback")
                    fetchLatestReleaseFromWebFallback(currentName)?.let { webRelease ->
                        return@withContext evaluateReleaseComparison(webRelease, currentCode, currentName)
                    }
                    return@withContext UpdateCheckResult.ApiError(response.code, AppMessages.UPDATE_CHECK_ERROR)
                } else if (response.code != 404) {
                    logDebugError(TAG, "Release check server error: HTTP ${response.code}")
                    return@withContext UpdateCheckResult.ApiError(response.code, AppMessages.UPDATE_CHECK_ERROR)
                }
            }

            // 3. Fallback to /releases list if /releases/latest returned 404 or only prereleases
            val listUrl = "https://api.github.com/repos/$githubOwner/$githubRepo/releases?per_page=10"
            val listReq = Request.Builder()
                .url(listUrl)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "BlackseaRobotics-AndroidApp/$currentName")
                .build()

            httpClient.newCall(listReq).execute().use { listResponse ->
                if (!listResponse.isSuccessful) {
                    logDebugError(TAG, "Release list error: HTTP ${listResponse.code}")
                    if (isRateLimitedOrTransientFailure(listResponse.code)) {
                        fetchLatestReleaseFromWebFallback(currentName)?.let { webRelease ->
                            return@withContext evaluateReleaseComparison(webRelease, currentCode, currentName)
                        }
                    }
                    return@withContext UpdateCheckResult.ApiError(
                        listResponse.code,
                        AppMessages.UPDATE_CHECK_ERROR
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
                    val isNewerCandidate = bestParsed == null ||
                        rel.versionCode > bestParsed.versionCode ||
                        (rel.versionCode == bestParsed.versionCode && compareSemver(rel.versionName, bestParsed.versionName) > 0)
                    if (isNewerCandidate) {
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
                    .remove("cached_release_etag")
                    .apply()

                return@withContext evaluateReleaseComparison(bestParsed, currentCode, currentName)
            }
        } catch (e: java.net.UnknownHostException) {
            logDebugError(TAG, "Network error (UnknownHost)", e)
            UpdateCheckResult.NetworkError(AppMessages.UPDATE_CHECK_ERROR)
        } catch (e: java.net.SocketTimeoutException) {
            logDebugError(TAG, "Network timeout", e)
            UpdateCheckResult.NetworkError(AppMessages.UPDATE_CHECK_ERROR)
        } catch (e: java.io.IOException) {
            logDebugError(TAG, "IO error during update check", e)
            UpdateCheckResult.NetworkError(AppMessages.UPDATE_CHECK_ERROR)
        } catch (e: Exception) {
            logDebugError(TAG, "Unexpected update check error", e)
            UpdateCheckResult.ApiError(-1, AppMessages.UPDATE_CHECK_ERROR)
        }
    }

    /**
     * Legacy Result wrapper preserved for callers that only need an optional AppReleaseInfo.
     */
    suspend fun checkLatestRelease(forceRefresh: Boolean = false): Result<AppReleaseInfo?> {
        return when (val detailed = checkLatestReleaseDetailed(forceRefresh)) {
            is UpdateCheckResult.UpdateAvailable -> Result.success(detailed.release)
            is UpdateCheckResult.UpToDate -> Result.success(null)
            is UpdateCheckResult.NoReleaseFound -> Result.failure(Exception("Yeni sürüm bulunamadı."))
            is UpdateCheckResult.ApkAssetMissing -> Result.failure(Exception(AppMessages.UPDATE_CHECK_ERROR))
            is UpdateCheckResult.NetworkError -> Result.failure(Exception(AppMessages.UPDATE_CHECK_ERROR))
            is UpdateCheckResult.ApiError -> Result.failure(Exception(AppMessages.UPDATE_CHECK_ERROR))
        }
    }

    /**
     * Resolves a verified public download URL internally.
     */
    suspend fun resolvePublicDownloadUrl(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val latestApiUrl = "https://api.github.com/repos/$githubOwner/$githubRepo/releases/latest"
            val reqBuilder = Request.Builder()
                .url(latestApiUrl)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "BlackseaRobotics-AndroidApp/${BuildConfig.VERSION_NAME}")

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
                .build()

            httpClient.newCall(repoReq).execute().use { repoRes ->
                if (repoRes.isSuccessful) {
                    val repoJson = JSONObject(repoRes.body?.string().orEmpty())
                    val isPrivate = repoJson.optBoolean("private", false)
                    if (isPrivate) {
                        return@withContext Result.failure(
                            Exception(AppMessages.UPDATE_CHECK_ERROR)
                        )
                    }
                    return@withContext Result.success("https://github.com/$githubOwner/$githubRepo/releases")
                } else {
                    return@withContext Result.failure(
                        Exception(AppMessages.UPDATE_CHECK_ERROR)
                    )
                }
            }
        } catch (e: Exception) {
            logDebugError(TAG, "resolvePublicDownloadUrl error", e)
            Result.failure(Exception(AppMessages.UPDATE_CHECK_ERROR))
        }
    }

    /**
     * Fetches release history for the version history dialog.
     */
    suspend fun fetchReleaseHistory(): Result<List<AppReleaseInfo>> = withContext(Dispatchers.IO) {
        val url = "https://api.github.com/repos/$githubOwner/$githubRepo/releases?per_page=10"
        try {
            val reqBuilder = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "BlackseaRobotics-AndroidApp/${BuildConfig.VERSION_NAME}")

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
            logDebugError(TAG, "Fetch history error", e)
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
            .filterNot { line ->
                val lower = line.lowercase()
                lower.contains("forceupdate") ||
                    lower.contains("force_update") ||
                    lower.contains("versioncode") ||
                    lower.contains("versionname") ||
                    lower.contains("applicationid") ||
                    lower.contains("certsha256") ||
                    lower.contains("sha256") ||
                    lower.contains("sha-256") ||
                    lower.contains("sha1") ||
                    lower.contains("sha-1") ||
                    lower.contains("sürüm kodu") ||
                    lower.contains("keystore") ||
                    lower.contains("github.com") ||
                    lower.contains(".apk") ||
                    lower.contains("imza sertifikası") ||
                    lower.contains("imza özeti")
            }
            .joinToString("\n")
            .trim()

        return AppReleaseInfo(
            tagName = tagName,
            versionName = cleanTag.ifBlank { name },
            versionCode = versionCode,
            title = name.ifBlank { "Sürüm $tagName" },
            changelog = cleanChangelog.ifBlank { "Yeni özellikler ve iyileştirmeler." },
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
            onProgress(UpdateDownloadState.Error(AppMessages.UPDATE_DOWNLOAD_ERROR))
            return@withContext null
        }

        if (!downloadUrl.endsWith(".apk", ignoreCase = true) && !downloadUrl.contains("/releases/download/", ignoreCase = true)) {
            onProgress(UpdateDownloadState.Error(AppMessages.UPDATE_DOWNLOAD_ERROR))
            return@withContext null
        }

        val installedPreCheck = getInstalledSigningCertSha256()
        if (!isReleaseSignatureCompatible(releaseInfo, installedPreCheck)) {
            logDebugWarn(TAG, "Signature mismatch pre-check failed for ${releaseInfo.tagName}")
            val msg = "Bu güncelleme mevcut sürümün üzerine doğrudan yüklenemiyor. Lütfen yeni sürümü tarayıcıdan indirip yeniden kurun."
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

            httpClient.newCall(reqBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    logDebugError(TAG, "Download failed: HTTP ${response.code} for $downloadUrl")
                    onProgress(UpdateDownloadState.Error(AppMessages.UPDATE_DOWNLOAD_ERROR))
                    return@withContext null
                }

                val contentType = response.header("Content-Type").orEmpty().lowercase()
                if (contentType.contains("text/html") || contentType.contains("application/xhtml")) {
                    logDebugError(TAG, "Download returned HTML ($contentType) instead of APK")
                    onProgress(UpdateDownloadState.Error(AppMessages.UPDATE_DOWNLOAD_ERROR))
                    return@withContext null
                }

                val body = response.body
                if (body == null) {
                    onProgress(UpdateDownloadState.Error(AppMessages.UPDATE_DOWNLOAD_ERROR))
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
                    logDebugError(TAG, "Downloaded file too small: $downloadedBytes bytes")
                    onProgress(UpdateDownloadState.Error(AppMessages.UPDATE_DOWNLOAD_ERROR))
                    return@withContext null
                }

                if (headerLength > 0 && downloadedBytes != headerLength) {
                    tempFile.delete()
                    logDebugError(TAG, "Incomplete download: $downloadedBytes / $headerLength bytes")
                    onProgress(UpdateDownloadState.Error(AppMessages.UPDATE_DOWNLOAD_ERROR))
                    return@withContext null
                }
            }

            // 2. ZIP / APK Magic Bytes Verification (PK\x03\x04)
            val magic = ByteArray(4)
            FileInputStream(tempFile).use { fis ->
                val n = fis.read(magic)
                if (n < 4 || magic[0] != 0x50.toByte() || magic[1] != 0x4B.toByte() || magic[2] != 0x03.toByte() || magic[3] != 0x04.toByte()) {
                    tempFile.delete()
                    logDebugError(TAG, "Invalid APK archive magic bytes")
                    onProgress(UpdateDownloadState.Error(AppMessages.UPDATE_DOWNLOAD_ERROR))
                    return@withContext null
                }
            }

            // 3. SHA-256 File Integrity Verification
            if (releaseInfo.expectedSha256.isNotBlank()) {
                val computedSha = calculateFileSha256(tempFile)
                if (!computedSha.equals(releaseInfo.expectedSha256, ignoreCase = true)) {
                    tempFile.delete()
                    logDebugError(TAG, "SHA-256 mismatch! Expected=${releaseInfo.expectedSha256}, Actual=$computedSha")
                    onProgress(UpdateDownloadState.Error(AppMessages.UPDATE_DOWNLOAD_ERROR))
                    return@withContext null
                }
            }

            // 4. Parse AndroidManifest & Signing Certificates from Downloaded APK BEFORE Installing
            val archiveInfo = getArchivePackageInfo(tempFile)
            if (archiveInfo == null) {
                tempFile.delete()
                logDebugError(TAG, "Failed to read package info from downloaded APK")
                onProgress(UpdateDownloadState.Error(AppMessages.UPDATE_DOWNLOAD_ERROR))
                return@withContext null
            }

            if (archiveInfo.packageName != context.packageName) {
                tempFile.delete()
                logDebugError(TAG, "Package name mismatch: expected=${context.packageName}, got=${archiveInfo.packageName}")
                onProgress(UpdateDownloadState.Error(AppMessages.UPDATE_DOWNLOAD_ERROR))
                return@withContext null
            }

            val downloadedVCode = getLongVersionCodeCompat(archiveInfo)
            val currentVCode = getCurrentVersionCode().toLong()
            if (downloadedVCode <= currentVCode) {
                tempFile.delete()
                logDebugError(TAG, "Downloaded versionCode ($downloadedVCode) <= current ($currentVCode)")
                onProgress(UpdateDownloadState.Error(AppMessages.UPDATE_DOWNLOAD_ERROR))
                return@withContext null
            }

            val installedCerts = getInstalledSigningCertSha256()
            val downloadedCerts = extractSigningSha256(archiveInfo)
            logDebug(TAG, "APK check OK -> pkg=${archiveInfo.packageName}, vCode=$downloadedVCode")

            if (installedCerts.isNotEmpty() && downloadedCerts.isNotEmpty() && installedCerts.intersect(downloadedCerts).isEmpty()) {
                tempFile.delete()
                logDebugError(TAG, "Signature mismatch! Installed=$installedCerts, Downloaded=$downloadedCerts")
                val msg = "Bu güncelleme mevcut sürümün üzerine doğrudan yüklenemiyor. Lütfen yeni sürümü tarayıcıdan indirip yeniden kurun."
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
            logDebugError(TAG, "Download error", e)
            onProgress(UpdateDownloadState.Error(AppMessages.UPDATE_DOWNLOAD_ERROR))
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
            return@withContext Result.failure(Exception(AppMessages.UPDATE_INSTALL_ERROR))
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
            logDebug(TAG, "PackageInstaller session $sessionId committed for ${apkFile.name}")
            Result.success(Unit)
        } catch (e: Exception) {
            session?.abandon()
            logDebugError(TAG, "PackageInstaller commit failed", e)
            UpdateInstallResultBus.update(
                InstallResultState.Error(
                    statusCode = PackageInstaller.STATUS_FAILURE,
                    statusName = "SESSION_EXCEPTION",
                    rawMessage = "",
                    userMessage = AppMessages.UPDATE_INSTALL_ERROR
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
            return Result.failure(Exception(AppMessages.UPDATE_INSTALL_ERROR))
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
            logDebugError(TAG, "Failed to launch FileProvider installer", e)
            Result.failure(e)
        }
    }
}
