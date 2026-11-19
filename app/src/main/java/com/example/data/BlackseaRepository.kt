package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Base64
import com.example.R
import com.example.model.BLACKSEA_AUTHORIZED_TEACHER_EMAIL
import com.example.model.BLACKSEA_AUTHORIZED_TEACHER_NAME
import com.example.model.ChatMessage
import com.example.model.ChatRoom
import com.example.model.IdeaComment
import com.example.model.IdeaItem
import com.example.model.IdeaStatus
import com.example.model.PLATFORM_FOUNDER_EMAIL
import com.example.model.PLATFORM_MAIN_TEAM_ID
import com.example.model.PredefinedRoles
import com.example.model.SharedProjectFile
import com.example.model.SupportTicket
import com.example.model.SupportTicketStatus
import com.example.model.TeamActivity
import com.example.model.TeamAnnouncement
import com.example.model.TeamAppealStatus
import com.example.model.TeamInfo
import com.example.model.TeamLogoPreset
import com.example.model.TeamMembership
import com.example.model.TeamNotification
import com.example.model.TeamStatus
import com.example.model.TeamTask
import com.example.model.TeamTaskStatus
import com.example.model.TeamType
import com.example.model.UserProfile
import com.example.model.UserStatus
import com.example.util.logDebugError
import com.example.util.logDebugWarn
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.snapshots
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class FileUploadResult(
    val fileName: String,
    val fileType: String,
    val fileSize: Long,
    val downloadUrl: String,
    val textContent: String = "",
    val storagePath: String = "",
    val thumbnailUrl: String = "",
    val mediaDurationMs: Long = 0L
)

sealed interface UserProfileLoadState {
    data object Loading : UserProfileLoadState
    data class Found(val profile: UserProfile) : UserProfileLoadState
    data object Missing : UserProfileLoadState
}

/**
 * Legacy-compatible single-team notification evaluator.
 */
fun shouldUserReceiveNotification(
    notif: TeamNotification,
    userId: String,
    isManager: Boolean,
    userTeam: String = "",
    userCreatedAtMs: Long = 0L
): Boolean {
    if (userId.isNotBlank() && notif.senderId.isNotBlank() && notif.senderId == userId) {
        return false
    }

    if (userId.isNotBlank() && notif.recipientUserId == userId) {
        return true
    }

    if (userCreatedAtMs > 0L) {
        val notifTimeMs = notif.createdAt?.toDate()?.time
        if (notifTimeMs != null && notifTimeMs < (userCreatedAtMs - 5_000L)) {
            return false
        }
    }

    if (notif.recipientUserId == "managers") {
        return isManager
    }

    return when (notif.type.lowercase()) {
        "update" -> true
        "broadcast", "announcement" -> true
        "team_suspended", "team_under_review", "team_resumed" -> true
        "idea" -> true
        "new_member" -> userId.isBlank() || notif.targetId != userId
        "support_ticket" -> isManager
        "support_reply" -> userId.isNotBlank() && notif.recipientUserId == userId
        "message", "file" -> {
            val teamLower = userTeam.lowercase()
            if (isManager || teamLower.contains("yönetim") || teamLower.contains("yonetim")) {
                true
            } else {
                val rId = notif.targetRoomId.lowercase()
                when {
                    rId == "general" || rId.isBlank() -> true
                    rId == "software" -> teamLower.contains("yazılım") || teamLower.contains("yazilim") || teamLower.contains("software")
                    rId == "mechanic" -> teamLower.contains("mekanik") || teamLower.contains("mechanic")
                    rId == "pr" -> teamLower.contains("pr")
                    else -> teamLower.contains(rId)
                }
            }
        }
        else -> notif.recipientUserId == "all"
    }
}

/**
 * Multi-team isolated notification evaluator.
 * Strictly isolates notifications per [TeamMembership] and restricts platform-wide admin
 * notifications (team applications, appeals, and support tickets) solely to [isPlatformAdmin].
 */
fun shouldUserReceiveNotification(
    notif: TeamNotification,
    userId: String,
    membershipsByTeam: Map<String, TeamMembership>,
    isPlatformAdmin: Boolean,
    userCreatedAtMs: Long = 0L
): Boolean {
    if (userId.isNotBlank() && notif.senderId.isNotBlank() && notif.senderId == userId) {
        return false
    }

    if (userId.isNotBlank() && notif.recipientUserId == userId) {
        return true
    }

    val typeLower = notif.type.lowercase()
    if (typeLower == "update" || notif.teamId == "platform") {
        return true
    }

    if (typeLower == "team_application_pending" ||
        typeLower == "support_ticket" ||
        typeLower == "team_appeal" ||
        notif.teamId == "platform_admin" ||
        notif.recipientUserId == "platform_admins"
    ) {
        return isPlatformAdmin
    }

    val effectiveTeamId = notif.effectiveTeamId()
    val membership = membershipsByTeam[effectiveTeamId] ?: return false
    if (!membership.isActive()) return false

    val membershipJoinMs = membership.createdAt?.toDate()?.time ?: userCreatedAtMs
    val effectiveJoinMs = maxOf(membershipJoinMs, userCreatedAtMs)

    return shouldUserReceiveNotification(
        notif = notif,
        userId = userId,
        isManager = membership.isManagerInTeam(),
        userTeam = membership.subTeam,
        userCreatedAtMs = effectiveJoinMs
    )
}

class BlackseaRepository(val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth get() = Firebase.auth

    private fun resolveFirebaseStorage(): FirebaseStorage {
        return try {
            FirebaseStorage.getInstance("gs://gen-lang-client-0922417463.firebasestorage.app")
        } catch (_: Exception) {
            try {
                FirebaseStorage.getInstance("gs://gen-lang-client-0922417463.appspot.com")
            } catch (_: Exception) {
                FirebaseStorage.getInstance()
            }
        }
    }

    private val storage get() = resolveFirebaseStorage()

    fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("Devam etmek için oturum açmanız gerekiyor.")
    }

    // ==========================================
    // MULTI-TEAM WORKSPACE COLLECTION ROUTING
    // ==========================================

    private fun isMainTeam(teamId: String): Boolean {
        return teamId.isBlank() || teamId == PLATFORM_MAIN_TEAM_ID
    }

    private fun roomsCollection(teamId: String): CollectionReference {
        return if (isMainTeam(teamId)) {
            db.collection("rooms")
        } else {
            db.collection("teams").document(teamId).collection("rooms")
        }
    }

    private fun ideasCollection(teamId: String): CollectionReference {
        return if (isMainTeam(teamId)) {
            db.collection("ideas")
        } else {
            db.collection("teams").document(teamId).collection("ideas")
        }
    }

    private fun filesCollection(teamId: String): CollectionReference {
        return if (isMainTeam(teamId)) {
            db.collection("files")
        } else {
            db.collection("teams").document(teamId).collection("files")
        }
    }

    private fun announcementsCollection(teamId: String): CollectionReference {
        return if (isMainTeam(teamId)) {
            db.collection("announcements")
        } else {
            db.collection("teams").document(teamId).collection("announcements")
        }
    }

    private fun supportTicketsCollection(teamId: String): CollectionReference {
        return if (isMainTeam(teamId)) {
            db.collection("support_tickets")
        } else {
            db.collection("teams").document(teamId).collection("support_tickets")
        }
    }

    private fun tasksCollection(teamId: String): CollectionReference {
        val cleanTeamId = teamId.ifBlank { PLATFORM_MAIN_TEAM_ID }
        return db.collection("teams").document(cleanTeamId).collection("tasks")
    }

    private fun workspacePathPrefix(teamId: String): String {
        return if (isMainTeam(teamId)) "" else "teams/$teamId/"
    }

    // ==========================================
    // REAL FILE UPLOADING & METADATA
    // ==========================================

    fun queryFileMetadata(context: Context, uri: Uri): Pair<String, Long> {
        var name = "file_${System.currentTimeMillis()}"
        var size = 0L
        try {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIdx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = it.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (nameIdx != -1) name = it.getString(nameIdx) ?: name
                    if (sizeIdx != -1) size = it.getLong(sizeIdx)
                }
            }
        } catch (e: Exception) {
            logDebugWarn("Repository", "Could not query file metadata", e)
        }
        if (size <= 0L) {
            try {
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                    if (afd.length > 0) size = afd.length
                }
            } catch (_: Exception) {
            }
        }
        return Pair(name, size)
    }

    fun compressImage(context: Context, uri: Uri, maxDimension: Int = 1280, quality: Int = 80): ByteArray? {
        return try {
            val orientation = try {
                context.contentResolver.openInputStream(uri)?.use { exifStream ->
                    ExifInterface(exifStream).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                } ?: ExifInterface.ORIENTATION_NORMAL
            } catch (_: Exception) {
                ExifInterface.ORIENTATION_NORMAL
            }

            val bitmap = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            } ?: return null

            val w = bitmap.width
            val h = bitmap.height
            val scale = if (w > maxDimension || h > maxDimension) {
                val maxSide = maxOf(w, h)
                maxDimension.toFloat() / maxSide
            } else 1.0f

            val matrix = Matrix().apply {
                if (scale < 1.0f) postScale(scale, scale)
                when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                    ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
                }
            }

            val finalBitmap = Bitmap.createBitmap(bitmap, 0, 0, w, h, matrix, true)
            if (finalBitmap !== bitmap) {
                bitmap.recycle()
            }

            val out = ByteArrayOutputStream()
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            finalBitmap.recycle()
            out.toByteArray()
        } catch (e: Exception) {
            logDebugWarn("Repository", "Image compression failed", e)
            null
        }
    }

    fun extractVideoMetadata(context: Context, uri: Uri): Pair<String, Long> {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            val frame = retriever.getFrameAtTime(500_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime
            val thumbDataUri = if (frame != null && frame.width > 0 && frame.height > 0) {
                val targetWidth = minOf(480, frame.width)
                val targetHeight = (targetWidth.toFloat() * frame.height / frame.width).toInt().coerceAtLeast(1)
                val scaled = Bitmap.createScaledBitmap(frame, targetWidth, targetHeight, true)
                val stream = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, 75, stream)
                if (scaled !== frame) scaled.recycle()
                frame.recycle()
                val base64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
                "data:image/jpeg;base64,$base64"
            } else {
                ""
            }
            Pair(thumbDataUri, durationMs)
        } catch (e: Exception) {
            logDebugWarn("Repository", "Video metadata extraction failed", e)
            Pair("", 0L)
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    fun extractVideoThumbnail(context: Context, uri: Uri): String {
        return extractVideoMetadata(context, uri).first
    }

    fun saveFileLocally(context: Context, uri: Uri, fileName: String): File? {
        return try {
            val dir = File(context.filesDir, "shared_files").apply { if (!exists()) mkdirs() }
            val destFile = File(dir, "${System.currentTimeMillis()}_$fileName")
            context.contentResolver.openInputStream(uri)?.use { inStream ->
                FileOutputStream(destFile).use { outStream ->
                    inStream.copyTo(outStream)
                }
            }
            destFile
        } catch (e: Exception) {
            logDebugWarn("Repository", "Could not cache file locally", e)
            null
        }
    }

    fun detectFileType(fileName: String, mimeType: String? = null): String {
        val ext = fileName.substringAfterLast(".", "").lowercase()
        val byExt = when (ext) {
            "png", "jpg", "jpeg", "webp", "gif", "bmp", "svg", "heic", "heif" -> "image"
            "mp4", "mkv", "webm", "mov", "avi", "3gp", "m4v" -> "video"
            "mp3", "wav", "m4a", "aac", "ogg", "flac", "opus", "amr" -> "audio"
            "pdf" -> "pdf"
            "txt", "md", "json", "xml", "csv", "log" -> "text"
            "py", "js", "ts", "html", "css", "c", "cpp", "h", "hpp", "ino", "java", "kt", "kts", "sh", "sql" -> "code"
            "step", "stp", "stl", "obj", "iges", "dwg", "dxf" -> "cad"
            "zip", "rar", "7z", "tar", "gz" -> "zip"
            "doc", "docx", "xls", "xlsx", "ppt", "pptx" -> "doc"
            else -> "other"
        }
        if (byExt != "other" || mimeType.isNullOrBlank()) return byExt
        val lowerMime = mimeType.lowercase()
        return when {
            lowerMime.startsWith("image/") -> "image"
            lowerMime.startsWith("video/") -> "video"
            lowerMime.startsWith("audio/") -> "audio"
            lowerMime == "application/pdf" -> "pdf"
            lowerMime.startsWith("text/") -> "text"
            else -> "other"
        }
    }

    suspend fun uploadFile(
        context: Context,
        uri: Uri,
        folder: String = "uploads",
        onProgress: (Int) -> Unit = {}
    ): FileUploadResult = withContext(Dispatchers.IO) {
        val (rawName, size) = queryFileMetadata(context, uri)
        val mimeType = try { context.contentResolver.getType(uri) } catch (_: Exception) { null }
        val fileType = detectFileType(rawName, mimeType)

        if (size > 100L * 1024L * 1024L) {
            throw IllegalArgumentException("Dosya boyutu 100 MB sınırını aşıyor.")
        }

        val name = if (!rawName.contains(".") && !mimeType.isNullOrBlank()) {
            val inferredExt = when {
                mimeType.contains("jpeg") || mimeType.contains("jpg") -> "jpg"
                mimeType.contains("png") -> "png"
                mimeType.contains("webp") -> "webp"
                mimeType.contains("mp4") -> "mp4"
                mimeType.contains("quicktime") -> "mov"
                mimeType.contains("webm") -> "webm"
                mimeType.contains("pdf") -> "pdf"
                else -> ""
            }
            if (inferredExt.isNotBlank()) "$rawName.$inferredExt" else rawName
        } else rawName

        val safeName = name.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        var downloadUrl = ""
        var textContent = ""
        var storagePath = ""

        if (fileType == "code" || fileType == "text" || name.endsWith(".json", ignoreCase = true)) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    textContent = stream.bufferedReader().readText().take(150_000)
                }
            } catch (e: Exception) {
                logDebugWarn("Repository", "Could not read text content", e)
            }
        }

        var compressedImageBytes: ByteArray? = null
        if (fileType == "image") {
            compressedImageBytes = compressImage(context, uri)
        }

        var videoThumbnailDataUri = ""
        var mediaDurationMs = 0L
        if (fileType == "video") {
            val (thumb, duration) = extractVideoMetadata(context, uri)
            videoThumbnailDataUri = thumb
            mediaDurationMs = duration
        }

        val localSavedFile = saveFileLocally(context, uri, safeName)
        if (localSavedFile != null) {
            storagePath = "local:${localSavedFile.absolutePath}"
        }

        val storageInstances = listOf(
            try { FirebaseStorage.getInstance("gs://gen-lang-client-0922417463.firebasestorage.app") } catch (_: Exception) { null },
            try { FirebaseStorage.getInstance("gs://gen-lang-client-0922417463.appspot.com") } catch (_: Exception) { null },
            try { FirebaseStorage.getInstance() } catch (_: Exception) { null }
        ).filterNotNull()

        val uniqueStoragePath = "$folder/${System.currentTimeMillis()}_$safeName"

        for (st in storageInstances) {
            try {
                val storageRef = st.reference.child(uniqueStoragePath)
                val uploadTask = if (compressedImageBytes != null && compressedImageBytes.isNotEmpty()) {
                    storageRef.putBytes(compressedImageBytes)
                } else {
                    storageRef.putFile(uri)
                }
                uploadTask.addOnProgressListener { taskSnapshot ->
                    val total = taskSnapshot.totalByteCount
                    if (total > 0) {
                        val progress = ((100.0 * taskSnapshot.bytesTransferred) / total).toInt()
                        onProgress(progress.coerceIn(0, 99))
                    }
                }
                uploadTask.await()
                downloadUrl = storageRef.downloadUrl.await().toString()
                if (downloadUrl.isNotBlank()) {
                    onProgress(100)
                    break
                }
            } catch (e: Exception) {
                logDebugWarn("Repository", "Storage bucket upload skipped", e)
            }
        }

        if (downloadUrl.isBlank()) {
            if (compressedImageBytes != null && compressedImageBytes.isNotEmpty()) {
                val base64Str = Base64.encodeToString(compressedImageBytes, Base64.NO_WRAP)
                downloadUrl = "data:image/jpeg;base64,$base64Str"
            } else if (fileType == "video") {
                downloadUrl = videoThumbnailDataUri
                if (textContent.isBlank()) {
                    val mb = if (size > 0) "${size / (1024 * 1024)} MB" else "Video"
                    textContent = "Video: $name ($mb)"
                }
            } else if (fileType == "audio" || size in 1..(350 * 1024L)) {
                try {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    if (bytes != null && bytes.size <= 350 * 1024) {
                        val mime = mimeType ?: when (fileType) {
                            "audio" -> "audio/m4a"
                            "pdf" -> "application/pdf"
                            else -> "application/octet-stream"
                        }
                        val base64Str = Base64.encodeToString(bytes, Base64.NO_WRAP)
                        downloadUrl = "data:$mime;base64,$base64Str"
                    }
                } catch (readEx: Exception) {
                    logDebugWarn("Repository", "Base64 encoding fallback failed", readEx)
                }
            }
            onProgress(100)
        }

        val effectiveSize = if (size > 0) size else localSavedFile?.length() ?: 0L

        FileUploadResult(
            fileName = name,
            fileType = fileType,
            fileSize = effectiveSize,
            downloadUrl = downloadUrl,
            textContent = textContent,
            storagePath = storagePath,
            thumbnailUrl = videoThumbnailDataUri,
            mediaDurationMs = mediaDurationMs
        )
    }

    suspend fun uploadLocalFile(
        file: File,
        folder: String = "voice_notes",
        fileType: String = "audio",
        onProgress: (Int) -> Unit = {}
    ): FileUploadResult = withContext(Dispatchers.IO) {
        val name = file.name
        val size = file.length()
        var downloadUrl = ""

        val storageInstances = listOf(
            try { FirebaseStorage.getInstance("gs://gen-lang-client-0922417463.firebasestorage.app") } catch (_: Exception) { null },
            try { FirebaseStorage.getInstance("gs://gen-lang-client-0922417463.appspot.com") } catch (_: Exception) { null },
            try { FirebaseStorage.getInstance() } catch (_: Exception) { null }
        ).filterNotNull()

        val safeName = name.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        val uniquePath = "$folder/${System.currentTimeMillis()}_$safeName"
        val uri = Uri.fromFile(file)

        for (st in storageInstances) {
            try {
                val storageRef = st.reference.child(uniquePath)
                val uploadTask = storageRef.putFile(uri)
                uploadTask.addOnProgressListener { taskSnapshot ->
                    val total = taskSnapshot.totalByteCount
                    if (total > 0) {
                        val progress = ((100.0 * taskSnapshot.bytesTransferred) / total).toInt()
                        onProgress(progress.coerceIn(0, 99))
                    }
                }
                uploadTask.await()
                downloadUrl = storageRef.downloadUrl.await().toString()
                if (downloadUrl.isNotBlank()) {
                    onProgress(100)
                    break
                }
            } catch (e: Exception) {
                logDebugWarn("Repository", "uploadLocalFile Storage bucket attempt failed", e)
            }
        }

        if (downloadUrl.isBlank()) {
            try {
                val bytes = file.readBytes()
                if (bytes.size <= 400 * 1024) {
                    val mime = if (fileType == "audio") "audio/m4a" else "application/octet-stream"
                    val base64Str = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    downloadUrl = "data:$mime;base64,$base64Str"
                }
                onProgress(100)
            } catch (readEx: Exception) {
                logDebugError("Repository", "uploadLocalFile Base64 fallback failed", readEx)
            }
        }

        onProgress(100)

        FileUploadResult(
            fileName = name,
            fileType = fileType,
            fileSize = size,
            downloadUrl = downloadUrl,
            textContent = ""
        )
    }

    suspend fun uploadTeamLogo(
        context: Context,
        uri: Uri,
        teamHint: String = "team"
    ): Result<String> = withContext(Dispatchers.IO) {
        val safeHint = teamHint.replace("[^a-zA-Z0-9_-]".toRegex(), "_").take(24).ifBlank { "team" }
        var bytes = compressImage(context, uri, maxDimension = 320, quality = 78)
            ?: return@withContext Result.failure(IllegalArgumentException("Seçilen görsel okunamadı."))

        val storageInstances = listOf(
            try { FirebaseStorage.getInstance("gs://gen-lang-client-0922417463.firebasestorage.app") } catch (_: Exception) { null },
            try { FirebaseStorage.getInstance("gs://gen-lang-client-0922417463.appspot.com") } catch (_: Exception) { null },
            try { FirebaseStorage.getInstance() } catch (_: Exception) { null }
        ).filterNotNull()

        val uniquePath = "team_logos/${System.currentTimeMillis()}_$safeHint.jpg"
        for (st in storageInstances) {
            try {
                val storageRef = st.reference.child(uniquePath)
                storageRef.putBytes(bytes).await()
                val downloadUrl = storageRef.downloadUrl.await().toString()
                if (downloadUrl.isNotBlank()) {
                    return@withContext Result.success(downloadUrl)
                }
            } catch (e: Exception) {
                logDebugWarn("Repository", "Team logo Storage upload skipped", e)
            }
        }

        var dataUri = "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
        if (dataUri.length > 150_000) {
            val smaller = compressImage(context, uri, maxDimension = 220, quality = 65)
            if (smaller != null) {
                bytes = smaller
                dataUri = "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
            }
        }
        if (dataUri.length > 175_000) {
            return@withContext Result.failure(IllegalArgumentException("Logo görseli çok büyük. Lütfen daha küçük bir görsel seçin."))
        }
        Result.success(dataUri)
    }

    // ==========================================
    // GLOBAL USER PROFILES & AUTO ONBOARDING
    // ==========================================

    fun observeUserProfile(userId: String): Flow<UserProfile?> {
        if (userId.isBlank()) return flowOf(null)
        return callbackFlow {
            val path = "users/$userId"
            val registration = db.collection("users").document(userId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        handleFirestoreError(error, OperationType.GET, path)
                        trySend(null)
                        return@addSnapshotListener
                    }
                    val profile = if (snapshot != null && snapshot.exists()) {
                        snapshot.toObject(UserProfile::class.java)
                    } else {
                        null
                    }
                    trySend(profile)
                }
            awaitClose { registration.remove() }
        }
    }

    fun observeUserProfileState(userId: String): Flow<UserProfileLoadState> {
        if (userId.isBlank()) return flowOf(UserProfileLoadState.Missing)
        return callbackFlow {
            trySend(UserProfileLoadState.Loading)
            val path = "users/$userId"
            val registration = db.collection("users").document(userId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        handleFirestoreError(error, OperationType.GET, path)
                        return@addSnapshotListener
                    }
                    if (snapshot != null && snapshot.exists()) {
                        val profile = snapshot.toObject(UserProfile::class.java)
                        if (profile != null) {
                            trySend(UserProfileLoadState.Found(profile))
                        } else {
                            trySend(UserProfileLoadState.Missing)
                        }
                    } else {
                        trySend(UserProfileLoadState.Missing)
                    }
                }
            awaitClose { registration.remove() }
        }
    }

    suspend fun getUserProfile(userId: String): UserProfile? {
        if (userId.isBlank()) return null
        return try {
            val snapshot = db.collection("users").document(userId).get().await()
            if (snapshot.exists()) {
                snapshot.toObject(UserProfile::class.java)
            } else {
                null
            }
        } catch (e: Exception) {
            logDebugWarn("Repository", "Failed to get user profile", e)
            null
        }
    }

    /**
     * Ensures a Google Sign-In user automatically receives a global [UserProfile] document
     * if they do not have one yet, preserving legacy BlackSea Robotics members seamlessly.
     */
    suspend fun ensureBaseUserProfileOnGoogleSignIn(
        userId: String,
        email: String,
        displayName: String,
        photoUrl: String
    ): Result<UserProfile> {
        val cleanUid = userId.trim()
        if (cleanUid.isBlank()) {
            return Result.failure(IllegalArgumentException("Geçersiz kullanıcı kimliği."))
        }
        val path = "users/$cleanUid"
        val docRef = db.collection("users").document(cleanUid)
        return try {
            val existingSnap = try {
                docRef.get(Source.SERVER).await()
            } catch (_: Exception) {
                docRef.get().await()
            }

            val cleanEmail = email.trim()
            val isFounder = cleanEmail.equals(PLATFORM_FOUNDER_EMAIL, ignoreCase = true)
            val isBsrAuthorizedTeacher = cleanEmail.equals(BLACKSEA_AUTHORIZED_TEACHER_EMAIL, ignoreCase = true)

            if (existingSnap.exists()) {
                val existingProfile = existingSnap.toObject(UserProfile::class.java)
                    ?: UserProfile(id = cleanUid, userId = cleanUid, email = email, displayName = displayName)

                val updates = mutableMapOf<String, Any>()
                if (photoUrl.isNotBlank() && existingProfile.photoUrl != photoUrl) {
                    updates["photoUrl"] = photoUrl
                }
                if (email.isNotBlank() && existingProfile.email.isBlank()) {
                    updates["email"] = email
                }
                if (updates.isNotEmpty()) {
                    updates["updatedAt"] = FieldValue.serverTimestamp()
                    try {
                        docRef.update(updates).await()
                    } catch (_: Exception) {
                    }
                }
                if (isFounder) {
                    if (existingProfile.activeTeamId == PLATFORM_MAIN_TEAM_ID) {
                        try {
                            docRef.update(
                                mapOf(
                                    "activeTeamId" to "",
                                    "updatedAt" to FieldValue.serverTimestamp()
                                )
                            ).await()
                        } catch (_: Exception) {
                        }
                    }
                    ensureMainTeamExists()
                    removeFounderAutoBsrMembershipIfNeeded(cleanUid)
                } else {
                    if (isBsrAuthorizedTeacher) {
                        ensureBsrAuthorizedTeacherMembershipIfNeeded(
                            existingProfile.copy(
                                id = cleanUid,
                                userId = cleanUid,
                                email = existingProfile.email.ifBlank { cleanEmail }
                            )
                        )
                    } else {
                        migrateLegacyUserToMainTeamMembershipIfNeeded(
                            profile = existingProfile,
                            hasActiveTeamField = existingSnap.contains("activeTeamId")
                        )
                    }
                }
                return Result.success(existingProfile)
            }

            val parts = displayName.trim().split(" ").filter { it.isNotBlank() }
            val firstName = parts.firstOrNull() ?: email.substringBefore("@").ifBlank { "Kullanıcı" }
            val lastName = if (parts.size > 1) parts.drop(1).joinToString(" ") else ""
            val cleanUsername = email.substringBefore("@")
                .lowercase(Locale.ROOT)
                .replace("[^a-z0-9._]".toRegex(), "")
                .ifBlank { "uye_${cleanUid.take(5)}" }
            val formattedName = displayName.trim().ifBlank { "$firstName $lastName".trim() }
            val initialTeam = if (isBsrAuthorizedTeacher) TeamType.MANAGEMENT.displayName else TeamType.GENERAL.displayName
            val initialRole = when {
                isFounder -> "Yönetici"
                isBsrAuthorizedTeacher -> "Yetkili Öğretmen"
                else -> "Takım Üyesi"
            }
            val initialRoles = listOf(initialRole)

            val payload = mapOf(
                "userId" to cleanUid,
                "firstName" to firstName,
                "lastName" to lastName,
                "username" to cleanUsername,
                "displayName" to formattedName,
                "email" to email,
                "team" to initialTeam,
                "role" to initialRole,
                "roles" to initialRoles,
                "status" to UserStatus.ACTIVE.value,
                "photoUrl" to photoUrl,
                "isSuperAdmin" to (isFounder || isBsrAuthorizedTeacher),
                "isPlatformAdmin" to isFounder,
                "activeTeamId" to "",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )

            docRef.set(payload, SetOptions.merge()).await()

            val createdProfile = UserProfile(
                id = cleanUid,
                userId = cleanUid,
                firstName = firstName,
                lastName = lastName,
                username = cleanUsername,
                displayName = formattedName,
                email = email,
                team = initialTeam,
                role = initialRole,
                roles = initialRoles,
                status = UserStatus.ACTIVE.value,
                photoUrl = photoUrl,
                isSuperAdmin = (isFounder || isBsrAuthorizedTeacher),
                isPlatformAdmin = isFounder,
                activeTeamId = ""
            )

            if (isFounder) {
                ensureMainTeamExists()
                removeFounderAutoBsrMembershipIfNeeded(cleanUid)
            } else if (isBsrAuthorizedTeacher) {
                ensureBsrAuthorizedTeacherMembershipIfNeeded(createdProfile)
            }

            Result.success(createdProfile)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    /**
     * Automatically provisions and protects [BLACKSEA_AUTHORIZED_TEACHER_EMAIL] as the official
     * "Yetkili Öğretmen" of BlackSea Robotics (`blacksea_robotics`) without granting platform admin.
     */
    suspend fun ensureBsrAuthorizedTeacherMembershipIfNeeded(profile: UserProfile) {
        val uid = profile.userId.ifBlank { profile.id }.trim()
        val email = profile.email.trim().ifBlank { auth.currentUser?.email.orEmpty().trim() }
        if (uid.isBlank() || !email.equals(BLACKSEA_AUTHORIZED_TEACHER_EMAIL, ignoreCase = true)) return

        ensureMainTeamExists()

        val membershipId = "${PLATFORM_MAIN_TEAM_ID}_$uid"
        val membershipRef = db.collection("team_memberships").document(membershipId)
        val userRef = db.collection("users").document(uid)

        try {
            val existingMembershipSnap = try { membershipRef.get().await() } catch (_: Exception) { null }
            val existingRoles = (existingMembershipSnap?.get("roles") as? List<*>)
                ?.mapNotNull { it as? String }
                ?.filter { it.isNotBlank() && !it.equals("Yönetici", ignoreCase = true) }
                ?: emptyList()
            val mergedRoles = (listOf("Yetkili Öğretmen") + existingRoles.filter { !it.equals("Takım Üyesi", ignoreCase = true) }).distinct()

            val displayName = profile.getFullName().ifBlank { BLACKSEA_AUTHORIZED_TEACHER_NAME }
            val subTeam = existingMembershipSnap?.getString("subTeam")
                ?.takeIf { it.isNotBlank() }
                ?: TeamType.MANAGEMENT.displayName

            val needsMembershipSync = existingMembershipSnap == null ||
                !existingMembershipSnap.exists() ||
                !UserStatus.isActive(existingMembershipSnap.getString("status")) ||
                existingMembershipSnap.getBoolean("isTeamAdmin") != true ||
                existingMembershipSnap.getString("role") != "Yetkili Öğretmen" ||
                existingMembershipSnap.getString("userEmail")?.equals(BLACKSEA_AUTHORIZED_TEACHER_EMAIL, ignoreCase = true) != true

            if (needsMembershipSync) {
                val payload = buildMap<String, Any> {
                    put("teamId", PLATFORM_MAIN_TEAM_ID)
                    put("userId", uid)
                    put("userName", displayName)
                    put("userEmail", BLACKSEA_AUTHORIZED_TEACHER_EMAIL)
                    put("userPhotoUrl", profile.photoUrl)
                    put("subTeam", subTeam)
                    put("role", "Yetkili Öğretmen")
                    put("roles", mergedRoles)
                    put("status", UserStatus.ACTIVE.value)
                    put("isTeamAdmin", true)
                    put("joinNote", "BlackSea Robotics Yetkili Öğretmeni")
                    put("suspensionReason", "")
                    put("appealText", "")
                    put("appealStatus", "")
                    if (existingMembershipSnap == null || !existingMembershipSnap.exists()) {
                        put("createdAt", FieldValue.serverTimestamp())
                    }
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                membershipRef.set(payload, SetOptions.merge()).await()
                syncTeamMemberCount(PLATFORM_MAIN_TEAM_ID)
            }

            if (profile.role != "Yetkili Öğretmen" ||
                !UserStatus.isActive(profile.status) ||
                !profile.isSuperAdmin ||
                profile.isPlatformAdmin
            ) {
                userRef.set(
                    mapOf(
                        "team" to subTeam,
                        "role" to "Yetkili Öğretmen",
                        "roles" to mergedRoles,
                        "status" to UserStatus.ACTIVE.value,
                        "isSuperAdmin" to true,
                        "isPlatformAdmin" to false,
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                ).await()
            }
        } catch (e: Exception) {
            logDebugWarn("Repository", "BSR authorized teacher sync skipped", e)
        }
    }

    /**
     * Removes any legacy auto-created BlackSea Robotics team membership for the platform founder
     * so [PLATFORM_FOUNDER_EMAIL] remains strictly the platform developer in Team Hub.
     */
    suspend fun removeFounderAutoBsrMembershipIfNeeded(userId: String) {
        val uid = userId.trim()
        if (uid.isBlank()) return
        val membershipRef = db.collection("team_memberships").document("${PLATFORM_MAIN_TEAM_ID}_$uid")
        try {
            val snap = membershipRef.get().await()
            if (snap.exists()) {
                membershipRef.delete().await()
                syncTeamMemberCount(PLATFORM_MAIN_TEAM_ID, excludeUserId = uid)
            }
        } catch (e: Exception) {
            logDebugWarn("Repository", "Founder BSR cleanup skipped", e)
        }
    }

    /**
     * Bridges legacy BlackSea Robotics users into deterministic `/team_memberships/blacksea_robotics_{uid}`
     * records while keeping the platform developer account strictly outside team rosters.
     */
    suspend fun migrateLegacyUserToMainTeamMembershipIfNeeded(
        profile: UserProfile,
        hasActiveTeamField: Boolean? = null
    ) {
        val uid = profile.userId.ifBlank { profile.id }.trim()
        if (uid.isBlank()) return
        val isFounder = profile.email.trim().equals(PLATFORM_FOUNDER_EMAIL, ignoreCase = true)
        if (isFounder) return
        if (profile.isBlackseaAuthorizedTeacherAccount()) {
            ensureBsrAuthorizedTeacherMembershipIfNeeded(profile)
            return
        }
        val membershipId = "${PLATFORM_MAIN_TEAM_ID}_$uid"
        val membershipRef = db.collection("team_memberships").document(membershipId)

        try {
            val existingMembership = membershipRef.get().await()
            if (existingMembership.exists()) return

            val isExplicitLegacyBsrUser = hasActiveTeamField == false && UserStatus.isActive(profile.status)

            if (!isExplicitLegacyBsrUser) return

            val roles = when {
                profile.roles.isNotEmpty() -> profile.roles.map {
                    if (it.equals("Yönetici", ignoreCase = true)) "Yetkili Öğretmen" else it
                }.distinct()
                else -> listOf(PredefinedRoles.getDefaultTeamRole(profile.team))
            }
            val isTeamAdmin = profile.isManagerAccount()
            val payload = mapOf(
                "teamId" to PLATFORM_MAIN_TEAM_ID,
                "userId" to uid,
                "userName" to profile.getFullName().ifBlank { "BSR Üyesi" },
                "userEmail" to profile.email,
                "userPhotoUrl" to profile.photoUrl,
                "subTeam" to profile.team.ifBlank { TeamType.GENERAL.displayName },
                "role" to (roles.firstOrNull() ?: "Takım Üyesi"),
                "roles" to roles,
                "status" to UserStatus.ACTIVE.value,
                "isTeamAdmin" to isTeamAdmin,
                "joinNote" to "BlackSea Robotics üyesi",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            membershipRef.set(payload, SetOptions.merge()).await()
        } catch (e: Exception) {
            logDebugWarn("Repository", "BSR membership migration skipped", e)
        }
    }

    suspend fun setActiveTeamId(userId: String, teamId: String) {
        val cleanUid = userId.trim()
        if (cleanUid.isBlank()) return
        try {
            db.collection("users").document(cleanUid).update(
                mapOf(
                    "activeTeamId" to teamId,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: Exception) {
            logDebugWarn("Repository", "Could not persist activeTeamId", e)
        }
    }

    suspend fun updateOwnProfile(
        userId: String,
        firstName: String,
        lastName: String,
        username: String,
        team: String
    ) {
        val cleanId = userId.trim()
        if (cleanId.isBlank()) return
        val path = "users/$cleanId"
        val formattedDisplayName = "$firstName $lastName".trim().ifBlank { username.trim() }
        val payload = mapOf(
            "firstName" to firstName.trim(),
            "lastName" to lastName.trim(),
            "username" to username.trim(),
            "displayName" to formattedDisplayName,
            "team" to team,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            db.collection("users").document(cleanId).update(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            throw e
        }
    }

    suspend fun updateUserDisplayAcrossTeams(
        userId: String,
        firstName: String,
        lastName: String,
        username: String,
        activeTeamId: String,
        subTeam: String
    ) {
        updateOwnProfile(
            userId = userId,
            firstName = firstName,
            lastName = lastName,
            username = username,
            team = subTeam
        )
        val cleanId = userId.trim()
        if (cleanId.isBlank()) return
        val displayName = "$firstName $lastName".trim().ifBlank { username.trim() }
        val membershipsSnap = try {
            db.collection("team_memberships")
                .whereEqualTo("userId", cleanId)
                .get()
                .await()
        } catch (_: Exception) {
            null
        } ?: return

        if (membershipsSnap.isEmpty) return
        membershipsSnap.documents.chunked(450).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { doc ->
                val updates = mutableMapOf<String, Any>(
                    "userName" to displayName,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (doc.getString("teamId") == activeTeamId && subTeam.isNotBlank()) {
                    updates["subTeam"] = subTeam.trim()
                }
                batch.update(doc.reference, updates)
            }
            batch.commit().await()
        }
    }

    fun observeAllUsers(): Flow<List<UserProfile>> = db.collection("users")
        .snapshots()
        .map { snapshot -> snapshot.toObjects(UserProfile::class.java) }
        .catch { error ->
            handleFirestoreError(error, OperationType.LIST, "users")
            emit(emptyList())
        }

    // ==========================================
    // MULTI-TEAM DIRECTORY & TEAM APPLICATIONS
    // ==========================================

    suspend fun ensureMainTeamExists() {
        val mainRef = db.collection("teams").document(PLATFORM_MAIN_TEAM_ID)
        try {
            val snap = mainRef.get().await()
            if (!snap.exists()) {
                val main = TeamInfo.mainBlackseaTeam()
                mainRef.set(
                    mapOf(
                        "name" to main.name,
                        "shortCode" to main.shortCode,
                        "competition" to main.competition,
                        "schoolName" to main.schoolName,
                        "city" to main.city,
                        "description" to main.description,
                        "motto" to main.motto,
                        "logoKey" to main.logoKey,
                        "accentColorHex" to main.accentColorHex,
                        "managerName" to main.managerName,
                        "managerContact" to main.managerContact,
                        "founderId" to main.founderId,
                        "founderEmail" to main.founderEmail,
                        "status" to TeamStatus.APPROVED,
                        "isMainTeam" to true,
                        "memberCount" to 1L,
                        "rejectionReason" to "",
                        "reviewedBy" to PLATFORM_MAIN_TEAM_ID,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                val existingContact = snap.getString("managerContact").orEmpty().trim()
                if (existingContact.isBlank() || existingContact.equals(PLATFORM_FOUNDER_EMAIL, ignoreCase = true)) {
                    mainRef.update(
                        mapOf(
                            "managerName" to BLACKSEA_AUTHORIZED_TEACHER_NAME,
                            "managerContact" to BLACKSEA_AUTHORIZED_TEACHER_EMAIL,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
        } catch (e: Exception) {
            logDebugWarn("Repository", "Main team seed skipped (may require platform admin)", e)
        }
    }

    fun observeApprovedTeams(): Flow<List<TeamInfo>> {
        return db.collection("teams")
            .whereEqualTo("status", TeamStatus.APPROVED)
            .snapshots()
            .map { snapshot ->
                val list = snapshot.toObjects(TeamInfo::class.java)
                val hasMain = list.any { it.id == PLATFORM_MAIN_TEAM_ID || it.isMainTeam }
                val combined = if (hasMain) list else listOf(TeamInfo.mainBlackseaTeam()) + list
                combined.sortedWith(
                    compareByDescending<TeamInfo> { it.isMainTeam || it.id == PLATFORM_MAIN_TEAM_ID }
                        .thenBy { it.name.lowercase(Locale("tr", "TR")) }
                )
            }
            .catch { error ->
                handleFirestoreError(error, OperationType.LIST, "teams")
                emit(listOf(TeamInfo.mainBlackseaTeam()))
            }
    }

    /**
     * Observes all directory-visible teams (approved as well as suspended / under_review teams)
     * so blocked teams remain visible in "Diğer Takımlar" with an unavailable banner and "Haber Et"
     * reopen-watch action rather than disappearing from the directory.
     */
    fun observeDiscoverableTeams(): Flow<List<TeamInfo>> {
        return db.collection("teams")
            .whereIn("status", listOf(TeamStatus.APPROVED, TeamStatus.SUSPENDED, TeamStatus.UNDER_REVIEW))
            .snapshots()
            .map { snapshot ->
                val list = snapshot.toObjects(TeamInfo::class.java)
                val hasMain = list.any { it.id == PLATFORM_MAIN_TEAM_ID || it.isMainTeam }
                val combined = if (hasMain) list else listOf(TeamInfo.mainBlackseaTeam()) + list
                combined.sortedWith(
                    compareByDescending<TeamInfo> { it.isMainTeam || it.id == PLATFORM_MAIN_TEAM_ID }
                        .thenBy { it.isWorkspaceBlocked() }
                        .thenBy { it.name.lowercase(Locale("tr", "TR")) }
                )
            }
            .catch { error ->
                handleFirestoreError(error, OperationType.LIST, "teams")
                emit(listOf(TeamInfo.mainBlackseaTeam()))
            }
    }

    fun observeWatchedTeamIds(userId: String): Flow<Set<String>> {
        val cleanUid = userId.trim()
        if (cleanUid.isBlank()) return flowOf(emptySet())
        return db.collection("team_reopen_watchers")
            .whereEqualTo("userId", cleanUid)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { it.getString("teamId")?.trim()?.takeIf { id -> id.isNotBlank() } }.toSet()
            }
            .catch { error ->
                handleFirestoreError(error, OperationType.LIST, "team_reopen_watchers")
                emit(emptySet())
            }
    }

    suspend fun setTeamReopenWatch(team: TeamInfo, watch: Boolean): Result<Unit> {
        val cleanTeamId = team.id.trim()
        if (cleanTeamId.isBlank()) {
            return Result.failure(IllegalArgumentException("Geçersiz takım."))
        }
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        if (watch && !team.isWorkspaceBlocked()) {
            return Result.failure(IllegalArgumentException("Bu takım çalışma alanı zaten kullanıma açık."))
        }
        val watchId = "${cleanTeamId}_$uid"
        val docRef = db.collection("team_reopen_watchers").document(watchId)
        return try {
            if (watch) {
                docRef.set(
                    mapOf(
                        "teamId" to cleanTeamId,
                        "userId" to uid,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.delete().await()
            }
            Result.success(Unit)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "team_reopen_watchers/$watchId")
            Result.failure(e)
        }
    }

    fun observePendingTeamApplications(): Flow<List<TeamInfo>> {
        return db.collection("teams")
            .whereEqualTo("status", TeamStatus.PENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(TeamInfo::class.java)
                    .sortedByDescending { it.createdAt?.toDate()?.time ?: Long.MAX_VALUE }
            }
            .catch { error ->
                handleFirestoreError(error, OperationType.LIST, "teams")
                emit(emptyList())
            }
    }

    fun observeAllTeamsForPlatformAdmin(): Flow<List<TeamInfo>> {
        return db.collection("teams")
            .snapshots()
            .map { snapshot ->
                val list = snapshot.toObjects(TeamInfo::class.java)
                val hasMain = list.any { it.id == PLATFORM_MAIN_TEAM_ID || it.isMainTeam }
                val combined = if (hasMain) list else listOf(TeamInfo.mainBlackseaTeam()) + list
                combined.sortedWith(
                    compareBy<TeamInfo> {
                        when {
                            it.isPending() -> 0
                            it.isMainTeam || it.id == PLATFORM_MAIN_TEAM_ID -> 1
                            it.isApproved() -> 2
                            else -> 3
                        }
                    }.thenByDescending { it.createdAt?.toDate()?.time ?: 0L }
                )
            }
            .catch { error ->
                handleFirestoreError(error, OperationType.LIST, "teams")
                emit(emptyList())
            }
    }

    fun observeMyTeamApplications(userId: String): Flow<List<TeamInfo>> {
        val cleanUid = userId.trim()
        if (cleanUid.isBlank()) return flowOf(emptyList())
        return db.collection("teams")
            .whereEqualTo("founderId", cleanUid)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(TeamInfo::class.java)
                    .filter { !it.isMainTeam && it.id != PLATFORM_MAIN_TEAM_ID }
                    .sortedByDescending { it.createdAt?.toDate()?.time ?: Long.MAX_VALUE }
            }
            .catch { error ->
                handleFirestoreError(error, OperationType.LIST, "teams")
                emit(emptyList())
            }
    }

    suspend fun submitTeamCreationRequest(
        name: String,
        shortCode: String,
        competition: String,
        schoolName: String,
        city: String,
        description: String,
        motto: String,
        logoKey: String,
        logoUrl: String = "",
        accentColorHex: String,
        managerName: String,
        managerContact: String,
        founderEmail: String,
        founderPhotoUrl: String
    ): Result<String> {
        val cleanName = name.trim().take(80)
        val cleanCode = shortCode.trim().uppercase(Locale.ROOT).take(12)
        val cleanComp = competition.trim().take(100)
        val cleanSchool = schoolName.trim().take(100)
        val cleanCity = city.trim().take(60)
        val cleanDesc = description.trim().take(500)
        val cleanMotto = motto.trim().take(120)
        val cleanLogoUrl = logoUrl.trim()
        val cleanMgrName = managerName.trim().take(80)
        val cleanMgrContact = managerContact.trim().take(120)

        if (cleanName.length < 2) return Result.failure(IllegalArgumentException("Takım adı en az 2 karakter olmalıdır."))
        if (cleanCode.length < 2) return Result.failure(IllegalArgumentException("Takım kısa kodu en az 2 karakter olmalıdır."))
        if (cleanComp.length < 2) return Result.failure(IllegalArgumentException("Yarışma/proje alanı boş bırakılamaz."))
        if (cleanSchool.length < 2) return Result.failure(IllegalArgumentException("Okul/kurum adı en az 2 karakter olmalıdır."))
        if (cleanCity.length < 2) return Result.failure(IllegalArgumentException("Şehir bilgisi en az 2 karakter olmalıdır."))
        if (cleanDesc.length < 5) return Result.failure(IllegalArgumentException("Takım açıklaması en az 5 karakter olmalıdır."))
        if (cleanMgrName.length < 2) return Result.failure(IllegalArgumentException("Takım yöneticisi adı en az 2 karakter olmalıdır."))
        if (cleanMgrContact.length < 3) return Result.failure(IllegalArgumentException("Yönetici iletişim bilgisi giriniz."))

        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }

        val slug = cleanName.lowercase(Locale.ROOT)
            .replace("[^a-z0-9]+".toRegex(), "_")
            .trim('_')
            .take(24)
            .ifBlank { "team" }
        val teamId = "team_${slug}_${UUID.randomUUID().toString().take(6)}"
        val path = "teams/$teamId"

        return try {
            ensureBaseUserProfileOnGoogleSignIn(
                userId = uid,
                email = founderEmail,
                displayName = cleanMgrName,
                photoUrl = founderPhotoUrl
            )

            val payload = mapOf(
                "name" to cleanName,
                "shortCode" to cleanCode,
                "competition" to cleanComp,
                "schoolName" to cleanSchool,
                "city" to cleanCity,
                "description" to cleanDesc,
                "motto" to cleanMotto,
                "logoKey" to logoKey.ifBlank { TeamLogoPreset.SHIELD.key },
                "logoUrl" to cleanLogoUrl,
                "accentColorHex" to accentColorHex.ifBlank { "#0284C7" },
                "managerName" to cleanMgrName,
                "managerContact" to cleanMgrContact,
                "founderId" to uid,
                "founderEmail" to founderEmail,
                "status" to TeamStatus.PENDING,
                "isMainTeam" to false,
                "memberCount" to 1L,
                "rejectionReason" to "",
                "reviewedBy" to "",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("teams").document(teamId).set(payload).await()

            createNotification(
                recipientUserId = "platform_admins",
                title = "Yeni Takım Başvurusu: $cleanName",
                message = "$cleanSchool ($cleanCity) • $cleanComp için oluşturulan başvuru incelemenizi bekliyor.",
                type = "team_application_pending",
                targetId = teamId,
                targetRoomId = teamId,
                senderName = cleanMgrName,
                senderId = uid,
                teamId = "platform_admin",
                teamName = "BlackSea Robotics Platform"
            )
            Result.success(teamId)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun approveTeamCreationRequest(team: TeamInfo): Result<Unit> {
        val approverUid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val path = "teams/${team.id}"
        return try {
            db.collection("teams").document(team.id).update(
                mapOf(
                    "status" to TeamStatus.APPROVED,
                    "rejectionReason" to "",
                    "reviewedBy" to approverUid,
                    "memberCount" to maxOf(1L, team.memberCount),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            val founderUid = team.founderId.trim()
            if (founderUid.isNotBlank()) {
                val founderMembershipId = "${team.id}_$founderUid"
                val founderProfile = getUserProfile(founderUid)
                val founderIsPlatformOwner = team.founderEmail.trim()
                    .equals(PLATFORM_FOUNDER_EMAIL, ignoreCase = true)
                val initialFounderRoles = if (founderIsPlatformOwner) {
                    listOf("Yönetici", "Takım Kaptanı")
                } else {
                    listOf("Yetkili Öğretmen", "Takım Kaptanı")
                }
                db.collection("team_memberships").document(founderMembershipId).set(
                    mapOf(
                        "teamId" to team.id,
                        "userId" to founderUid,
                        "userName" to team.managerName.ifBlank { founderProfile?.getFullName() ?: "Takım Kurucusu" },
                        "userEmail" to team.founderEmail.ifBlank { founderProfile?.email ?: "" },
                        "userPhotoUrl" to (founderProfile?.photoUrl ?: ""),
                        "subTeam" to TeamType.MANAGEMENT.displayName,
                        "role" to initialFounderRoles.first(),
                        "roles" to initialFounderRoles,
                        "status" to UserStatus.ACTIVE.value,
                        "isTeamAdmin" to true,
                        "joinNote" to if (founderIsPlatformOwner) "Platform kurucu yöneticisi" else "Takım kurucusu • Yetkili Öğretmen",
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                ).await()

                ensureDefaultRoomsExist(team.id)

                createNotification(
                    recipientUserId = founderUid,
                    title = "Takımınız Onaylandı!",
                    message = "${team.name} takım oluşturma başvurunuz BlackSea Robotics yöneticileri tarafından onaylandı.",
                    type = "team_application_approved",
                    targetId = team.id,
                    targetRoomId = "general",
                    senderName = "BlackSea Robotics",
                    senderId = approverUid,
                    teamId = team.id,
                    teamName = team.name
                )
            }
            Result.success(Unit)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun rejectTeamCreationRequest(team: TeamInfo, reason: String): Result<Unit> {
        val reviewerUid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val cleanReason = reason.trim().take(500)
        val path = "teams/${team.id}"
        return try {
            db.collection("teams").document(team.id).update(
                mapOf(
                    "status" to TeamStatus.REJECTED,
                    "rejectionReason" to cleanReason,
                    "reviewedBy" to reviewerUid,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            if (team.founderId.isNotBlank()) {
                val msg = if (cleanReason.isNotBlank()) {
                    "${team.name} başvurusu reddedildi: $cleanReason"
                } else {
                    "${team.name} takım oluşturma başvurunuz BlackSea Robotics yöneticileri tarafından reddedildi."
                }
                createNotification(
                    recipientUserId = team.founderId,
                    title = "Takım Başvurusu Sonucu",
                    message = msg,
                    type = "team_application_rejected",
                    targetId = team.id,
                    senderName = "BlackSea Robotics",
                    senderId = reviewerUid,
                    teamId = team.id,
                    teamName = team.name
                )
            }
            Result.success(Unit)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateTeamDetails(
        teamId: String,
        name: String,
        shortCode: String,
        competition: String,
        schoolName: String,
        city: String,
        description: String,
        motto: String,
        logoKey: String,
        logoUrl: String = "",
        accentColorHex: String,
        managerName: String,
        managerContact: String
    ): Result<Unit> {
        val cleanId = teamId.trim()
        if (cleanId.isBlank()) return Result.failure(IllegalArgumentException("Geçersiz takım."))
        return try {
            db.collection("teams").document(cleanId).update(
                mapOf(
                    "name" to name.trim().take(80),
                    "shortCode" to shortCode.trim().uppercase(Locale.ROOT).take(12),
                    "competition" to competition.trim().take(100),
                    "schoolName" to schoolName.trim().take(100),
                    "city" to city.trim().take(60),
                    "description" to description.trim().take(500),
                    "motto" to motto.trim().take(120),
                    "logoKey" to logoKey.ifBlank { TeamLogoPreset.SHIELD.key },
                    "logoUrl" to logoUrl.trim(),
                    "accentColorHex" to accentColorHex.ifBlank { "#0284C7" },
                    "managerName" to managerName.trim().take(80),
                    "managerContact" to managerContact.trim().take(120),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "teams/$cleanId")
            Result.failure(e)
        }
    }

    /**
     * Observes specific teams by ID (so members of suspended or under-review teams
     * can still see them in their workspace list and access the moderation warning & appeal flow).
     */
    fun observeTeamsByIds(teamIds: Set<String>): Flow<List<TeamInfo>> {
        val cleanIds = teamIds.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (cleanIds.isEmpty()) return flowOf(emptyList())
        return channelFlow {
            val map = ConcurrentHashMap<String, TeamInfo>()
            fun emitSorted() {
                trySend(
                    map.values.sortedWith(
                        compareByDescending<TeamInfo> { it.isMainTeam || it.id == PLATFORM_MAIN_TEAM_ID }
                            .thenBy { it.name.lowercase(Locale("tr", "TR")) }
                    )
                )
            }
            for (tId in cleanIds) {
                launch {
                    db.collection("teams").document(tId)
                        .snapshots()
                        .catch { err ->
                            logDebugWarn("Repository", "observeTeamsByIds snapshot error for $tId", err)
                        }
                        .collect { snap ->
                            if (snap.exists()) {
                                snap.toObject(TeamInfo::class.java)?.let { info ->
                                    map[tId] = info
                                }
                            } else if (tId == PLATFORM_MAIN_TEAM_ID) {
                                map[tId] = TeamInfo.mainBlackseaTeam()
                            } else {
                                map.remove(tId)
                            }
                            emitSorted()
                        }
                }
            }
        }
    }

    suspend fun suspendTeamWorkspace(team: TeamInfo, reason: String): Result<Unit> {
        return moderateTeamStatus(
            team = team,
            newStatus = TeamStatus.SUSPENDED,
            reason = reason,
            notifType = "team_suspended",
            notifTitle = "${team.name} Çalışma Alanı Durduruldu",
            notifBody = { r ->
                if (r.isNotBlank()) "Sebep: $r" else "Bu takımın çalışma alanı platform yöneticisi tarafından geçici olarak durduruldu."
            }
        )
    }

    suspend fun markTeamUnderReview(team: TeamInfo, reason: String): Result<Unit> {
        return moderateTeamStatus(
            team = team,
            newStatus = TeamStatus.UNDER_REVIEW,
            reason = reason,
            notifType = "team_under_review",
            notifTitle = "${team.name} İncelemeye Alındı",
            notifBody = { r ->
                if (r.isNotBlank()) "Sebep: $r" else "Takım çalışma alanınız inceleme sürecindedir."
            }
        )
    }

    suspend fun resumeTeamWorkspace(team: TeamInfo): Result<Unit> {
        val result = moderateTeamStatus(
            team = team,
            newStatus = TeamStatus.APPROVED,
            reason = "",
            notifType = "team_resumed",
            notifTitle = "${team.name} Yeniden Aktifleştirildi",
            notifBody = { _ -> "${team.name} çalışma alanı tekrar açıldı. Çalışmaya devam edebilirsiniz." }
        )
        if (result.isSuccess) {
            val cleanId = team.id.trim()
            val reviewerUid = auth.currentUser?.uid ?: ""
            try {
                val watchersSnap = db.collection("team_reopen_watchers")
                    .whereEqualTo("teamId", cleanId)
                    .get()
                    .await()
                val watcherDocs = watchersSnap.documents
                val notifiedUserIds = mutableSetOf<String>()
                for (doc in watcherDocs) {
                    val watcherUid = doc.getString("userId")?.trim().orEmpty()
                    if (watcherUid.isNotBlank() && watcherUid != reviewerUid && notifiedUserIds.add(watcherUid)) {
                        createNotification(
                            recipientUserId = watcherUid,
                            title = "${team.name} Çalışma Alanı Açıldı!",
                            message = "${team.name} çalışma alanı tekrar kullanıma açıldı. Takımı inceleyebilir ve katılım başvurusunda bulunabilirsiniz.",
                            type = "team_reopened_watch",
                            targetId = cleanId,
                            senderName = "BlackSea Platform Yönetimi",
                            senderId = reviewerUid,
                            teamId = cleanId,
                            teamName = team.name
                        )
                    }
                }
                if (watcherDocs.isNotEmpty()) {
                    watcherDocs.chunked(450).forEach { chunk ->
                        val batch = db.batch()
                        chunk.forEach { batch.delete(it.reference) }
                        batch.commit().await()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logDebugWarn("Repository", "Reopen watcher notification cleanup skipped", e)
            }
        }
        return result
    }

    suspend fun dismissTeamAppeal(team: TeamInfo, note: String = ""): Result<Unit> {
        val reviewerUid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val cleanId = team.id.trim()
        if (cleanId.isBlank() || team.isMainTeam || cleanId == PLATFORM_MAIN_TEAM_ID) {
            return Result.failure(IllegalArgumentException("Ana platform takımı üzerinde işlem yapılamaz."))
        }
        val cleanNote = note.trim().take(500)
        return try {
            val updates = mutableMapOf<String, Any>(
                "appealStatus" to TeamAppealStatus.REJECTED,
                "reviewedBy" to reviewerUid,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (cleanNote.isNotBlank()) {
                updates["moderationReason"] = cleanNote
            }
            db.collection("teams").document(cleanId).update(updates).await()
            Result.success(Unit)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "teams/$cleanId")
            Result.failure(e)
        }
    }

    private suspend fun moderateTeamStatus(
        team: TeamInfo,
        newStatus: String,
        reason: String,
        notifType: String,
        notifTitle: String,
        notifBody: (String) -> String
    ): Result<Unit> {
        val reviewerUid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val cleanId = team.id.trim()
        if (cleanId.isBlank() || team.isMainTeam || cleanId == PLATFORM_MAIN_TEAM_ID) {
            return Result.failure(IllegalArgumentException("Ana platform takımı durdurulamaz veya incelemeye alınamaz."))
        }
        val cleanReason = reason.trim().take(500)
        return try {
            val updates = mutableMapOf<String, Any>(
                "status" to newStatus,
                "moderationReason" to cleanReason,
                "reviewedBy" to reviewerUid,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (newStatus == TeamStatus.APPROVED) {
                updates["appealText"] = ""
                updates["appealStatus"] = ""
                updates["appealByUserId"] = ""
                updates["appealByName"] = ""
            } else {
                updates["appealStatus"] = TeamAppealStatus.NONE
            }
            db.collection("teams").document(cleanId).update(updates).await()

            createNotification(
                recipientUserId = "all",
                title = notifTitle,
                message = notifBody(cleanReason),
                type = notifType,
                targetId = cleanId,
                senderName = "BlackSea Platform Yönetimi",
                senderId = reviewerUid,
                teamId = cleanId,
                teamName = team.name
            )
            Result.success(Unit)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "teams/$cleanId")
            Result.failure(e)
        }
    }

    suspend fun submitTeamAppeal(
        team: TeamInfo,
        appellantName: String,
        appealText: String
    ): Result<Unit> {
        val cleanId = team.id.trim()
        if (cleanId.isBlank() || cleanId == PLATFORM_MAIN_TEAM_ID) {
            return Result.failure(IllegalArgumentException("Geçersiz takım."))
        }
        val cleanText = appealText.trim().take(1000)
        if (cleanText.length < 10) {
            return Result.failure(IllegalArgumentException("İtiraz açıklaması en az 10 karakter olmalıdır."))
        }
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val cleanName = appellantName.trim().ifBlank { "Takım Üyesi" }.take(120)
        return try {
            db.collection("teams").document(cleanId).update(
                mapOf(
                    "appealText" to cleanText,
                    "appealStatus" to TeamAppealStatus.PENDING,
                    "appealByUserId" to uid,
                    "appealByName" to cleanName,
                    "appealSubmittedAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            createNotification(
                recipientUserId = "platform_admins",
                title = "Takım İtirazı: ${team.name}",
                message = "$cleanName: ${cleanText.take(120)}",
                type = "team_appeal",
                targetId = cleanId,
                senderName = cleanName,
                senderId = uid,
                teamId = "platform_admin",
                teamName = "BlackSea Robotics Platform"
            )
            Result.success(Unit)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "teams/$cleanId")
            Result.failure(e)
        }
    }

    // ==========================================
    // PER-TEAM MEMBERSHIPS & APPROVALS
    // ==========================================

    fun observeMyMemberships(userId: String): Flow<List<TeamMembership>> {
        val cleanUid = userId.trim()
        if (cleanUid.isBlank()) return flowOf(emptyList())
        return db.collection("team_memberships")
            .whereEqualTo("userId", cleanUid)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(TeamMembership::class.java)
                    .sortedByDescending { it.isActive() }
            }
            .catch { error ->
                handleFirestoreError(error, OperationType.LIST, "team_memberships")
                emit(emptyList())
            }
    }

    fun observeTeamMemberships(teamId: String): Flow<List<TeamMembership>> {
        val cleanTeamId = teamId.trim().ifBlank { PLATFORM_MAIN_TEAM_ID }
        val membershipsFlow = db.collection("team_memberships")
            .whereEqualTo("teamId", cleanTeamId)
            .snapshots()
            .map { snapshot -> snapshot.toObjects(TeamMembership::class.java) }
            .catch { error ->
                handleFirestoreError(error, OperationType.LIST, "team_memberships")
                emit(emptyList())
            }

        if (cleanTeamId != PLATFORM_MAIN_TEAM_ID) {
            return membershipsFlow.map { list ->
                list.sortedWith(
                    compareBy<TeamMembership> {
                        when {
                            it.isPending() -> 0
                            it.isActive() -> 1
                            else -> 2
                        }
                    }.thenBy { it.userName.lowercase(Locale("tr", "TR")) }
                )
            }
        }

        // For BlackSea Robotics main team, merge explicit team_memberships with legacy BSR user docs (excluding platform founder developer account)
        return combine(membershipsFlow, observeAllUsers()) { memberships, legacyUsers ->
            val byUserId = memberships
                .filterNot { it.userEmail.trim().equals(PLATFORM_FOUNDER_EMAIL, ignoreCase = true) }
                .associateBy { it.userId }
                .toMutableMap()
            legacyUsers.forEach { legacy ->
                val uid = legacy.userId.ifBlank { legacy.id }
                val isFounder = legacy.email.trim().equals(PLATFORM_FOUNDER_EMAIL, ignoreCase = true)
                if (uid.isNotBlank() && !isFounder && !byUserId.containsKey(uid)) {
                    val isBsrTeacher = legacy.isBlackseaAuthorizedTeacherAccount()
                    val isBsrLegacyMember = isBsrTeacher ||
                        legacy.activeTeamId == PLATFORM_MAIN_TEAM_ID ||
                        (legacy.activeTeamId.isBlank() && legacy.roles.any { it != "Takım Üyesi" && !it.equals("Yönetici", ignoreCase = true) })
                    if (isBsrLegacyMember) {
                        val effectiveRoles = if (isBsrTeacher) {
                            (listOf("Yetkili Öğretmen") + legacy.roles.filter {
                                !it.equals("Yönetici", ignoreCase = true) && !it.equals("Takım Üyesi", ignoreCase = true)
                            }).distinct()
                        } else {
                            legacy.roles
                        }
                        byUserId[uid] = TeamMembership(
                            id = "${PLATFORM_MAIN_TEAM_ID}_$uid",
                            teamId = PLATFORM_MAIN_TEAM_ID,
                            userId = uid,
                            userName = legacy.getFullName().ifBlank {
                                if (isBsrTeacher) BLACKSEA_AUTHORIZED_TEACHER_NAME else "BSR Üyesi"
                            },
                            userEmail = legacy.email,
                            userPhotoUrl = legacy.photoUrl,
                            subTeam = if (isBsrTeacher) TeamType.MANAGEMENT.displayName else legacy.team,
                            role = if (isBsrTeacher) "Yetkili Öğretmen" else legacy.role,
                            roles = effectiveRoles,
                            status = if (isBsrTeacher) UserStatus.ACTIVE.value else legacy.status,
                            isTeamAdmin = isBsrTeacher || legacy.isManagerAccount(),
                            createdAt = legacy.createdAt,
                            updatedAt = legacy.updatedAt
                        )
                    }
                }
            }
            byUserId.values.sortedWith(
                compareBy<TeamMembership> {
                    when {
                        it.isPending() -> 0
                        it.isActive() -> 1
                        else -> 2
                    }
                }.thenBy { it.userName.lowercase(Locale("tr", "TR")) }
            )
        }
    }

    suspend fun requestJoinTeam(
        team: TeamInfo,
        profile: UserProfile,
        subTeam: String,
        joinNote: String = ""
    ): Result<Unit> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val cleanSubTeam = subTeam.ifBlank { TeamType.GENERAL.displayName }
        val membershipId = "${team.id}_$uid"
        val docRef = db.collection("team_memberships").document(membershipId)
        val userName = profile.getFullName().ifBlank { profile.email.substringBefore("@").ifBlank { "Öğrenci" } }

        return try {
            val existing = try { docRef.get().await() } catch (_: Exception) { null }
            val defaultRole = PredefinedRoles.getDefaultTeamRole(cleanSubTeam)
            val payload = mapOf(
                "teamId" to team.id,
                "userId" to uid,
                "userName" to userName,
                "userEmail" to profile.email,
                "userPhotoUrl" to profile.photoUrl,
                "subTeam" to cleanSubTeam,
                "role" to defaultRole,
                "roles" to listOf(defaultRole),
                "status" to UserStatus.PENDING.value,
                "isTeamAdmin" to false,
                "joinNote" to joinNote.trim().take(300),
                "createdAt" to (existing?.get("createdAt") ?: FieldValue.serverTimestamp()),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload, SetOptions.merge()).await()

            createNotification(
                recipientUserId = "managers",
                title = "${team.name}: Yeni Üye Başvurusu",
                message = "$userName ($cleanSubTeam) takıma katılmak için onay bekliyor.",
                type = "member_pending",
                targetId = uid,
                targetRoomId = cleanSubTeam,
                senderName = userName,
                senderId = uid,
                teamId = team.id,
                teamName = team.name
            )
            Result.success(Unit)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "team_memberships/$membershipId")
            Result.failure(e)
        }
    }

    suspend fun updateMembershipRolesAndStatus(
        team: TeamInfo,
        targetUserId: String,
        subTeam: String,
        roles: List<String>,
        status: String
    ): Result<Unit> {
        val cleanTargetUid = targetUserId.trim()
        if (cleanTargetUid.isBlank()) return Result.failure(IllegalArgumentException("Geçersiz üye."))
        val membershipId = "${team.id}_$cleanTargetUid"
        val docRef = db.collection("team_memberships").document(membershipId)
        val callerIsFounder = auth.currentUser?.email?.trim()
            .equals(PLATFORM_FOUNDER_EMAIL, ignoreCase = true)

        return try {
            val existingSnap = try { docRef.get().await() } catch (_: Exception) { null }
            val previousStatus = existingSnap?.getString("status")
            val targetProfile = getUserProfile(cleanTargetUid)
            val targetEmail = existingSnap?.getString("userEmail") ?: targetProfile?.email.orEmpty()
            val targetIsFounder = targetEmail.trim().equals(PLATFORM_FOUNDER_EMAIL, ignoreCase = true)

            val requestedRoles = if (roles.isNotEmpty()) roles else listOf(PredefinedRoles.getDefaultTeamRole(subTeam))
            val finalRoles = if (callerIsFounder && targetIsFounder) {
                requestedRoles
            } else {
                requestedRoles.filterNot { it.equals("Yönetici", ignoreCase = true) }
                    .ifEmpty { listOf(PredefinedRoles.getDefaultTeamRole(subTeam)) }
            }
            val isTeamAdmin = finalRoles.any { it in PredefinedRoles.MANAGEMENT_ROLES && it != "Takım Yardımcısı" }

            val memberName = existingSnap?.getString("userName")
                ?: targetProfile?.getFullName()?.takeIf { it.isNotBlank() }
                ?: "Üye"
            val transitionedToActive = !UserStatus.isActive(previousStatus) && UserStatus.isActive(status)

            val payload = buildMap<String, Any> {
                put("teamId", team.id)
                put("userId", cleanTargetUid)
                put("userName", memberName)
                put("userEmail", targetEmail)
                put("userPhotoUrl", existingSnap?.getString("userPhotoUrl") ?: targetProfile?.photoUrl.orEmpty())
                put("subTeam", subTeam.ifBlank { TeamType.GENERAL.displayName })
                put("roles", finalRoles)
                put("role", finalRoles.firstOrNull() ?: "Takım Üyesi")
                put("status", status)
                put("isTeamAdmin", isTeamAdmin)
                if (UserStatus.isActive(status)) {
                    put("suspensionReason", "")
                    put("appealText", "")
                    put("appealStatus", "")
                }
                if (existingSnap == null || !existingSnap.exists()) {
                    put("createdAt", FieldValue.serverTimestamp())
                }
                put("updatedAt", FieldValue.serverTimestamp())
            }
            docRef.set(payload, SetOptions.merge()).await()

            // Legacy sync for BlackSea Robotics main team only
            if (team.id == PLATFORM_MAIN_TEAM_ID) {
                try {
                    db.collection("users").document(cleanTargetUid).set(
                        mapOf(
                            "team" to subTeam,
                            "roles" to finalRoles,
                            "role" to (finalRoles.firstOrNull() ?: "Takım Üyesi"),
                            "status" to status,
                            "updatedAt" to FieldValue.serverTimestamp()
                        ),
                        SetOptions.merge()
                    ).await()
                } catch (_: Exception) {
                }
            }

            syncTeamMemberCount(team.id)

            if (transitionedToActive) {
                val approverId = auth.currentUser?.uid ?: ""
                createNotification(
                    recipientUserId = cleanTargetUid,
                    title = "${team.name} Üyeliğiniz Onaylandı",
                    message = "${team.name} takımına katılım başvurunuz onaylandı!",
                    type = "member_approved",
                    targetId = cleanTargetUid,
                    senderId = approverId,
                    teamId = team.id,
                    teamName = team.name
                )
                createNotification(
                    recipientUserId = "all",
                    title = "Yeni Takım Üyesi",
                    message = "$memberName ($subTeam) ekibe katıldı!",
                    type = "new_member",
                    targetId = cleanTargetUid,
                    targetRoomId = subTeam,
                    senderName = memberName,
                    senderId = approverId,
                    teamId = team.id,
                    teamName = team.name
                )
            }
            Result.success(Unit)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "team_memberships/$membershipId")
            Result.failure(e)
        }
    }

    suspend fun revokeTeamMembership(
        team: TeamInfo,
        targetUserId: String,
        reason: String
    ): Result<Unit> {
        val cleanTargetUid = targetUserId.trim()
        val currentUid = auth.currentUser?.uid
        if (cleanTargetUid == currentUid) {
            return Result.failure(IllegalArgumentException("Kendi erişiminizi kaldıramazsınız."))
        }
        val cleanReason = reason.trim().take(500)
        if (cleanReason.length < 5) {
            return Result.failure(IllegalArgumentException("Askıya alma gerekçesi en az 5 karakter olmalıdır."))
        }
        val membershipId = "${team.id}_$cleanTargetUid"
        return try {
            val snap = db.collection("team_memberships").document(membershipId).get().await()
            val email = snap.getString("userEmail").orEmpty()
            val callerIsFounder = auth.currentUser?.email?.trim()
                .equals(PLATFORM_FOUNDER_EMAIL, ignoreCase = true)
            if (team.id == PLATFORM_MAIN_TEAM_ID &&
                (email.equals(PLATFORM_FOUNDER_EMAIL, ignoreCase = true) ||
                 (!callerIsFounder && email.equals(BLACKSEA_AUTHORIZED_TEACHER_EMAIL, ignoreCase = true)))
            ) {
                return Result.failure(IllegalArgumentException("BlackSea Robotics yetkili öğretmen hesabı askıya alınamaz."))
            }
            db.collection("team_memberships").document(membershipId).update(
                mapOf(
                    "status" to UserStatus.REVOKED.value,
                    "suspensionReason" to cleanReason,
                    "appealText" to "",
                    "appealStatus" to "",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            if (team.id == PLATFORM_MAIN_TEAM_ID) {
                try {
                    db.collection("users").document(cleanTargetUid).update(
                        mapOf("status" to UserStatus.REVOKED.value, "updatedAt" to FieldValue.serverTimestamp())
                    ).await()
                } catch (_: Exception) {
                }
            }
            syncTeamMemberCount(team.id)
            createNotification(
                recipientUserId = cleanTargetUid,
                title = "${team.name} Üyeliğiniz Askıya Alındı",
                message = "${team.name} erişiminiz askıya alındı. Gerekçe: $cleanReason",
                type = "access_revoked",
                targetId = cleanTargetUid,
                senderId = currentUid ?: "",
                teamId = team.id,
                teamName = team.name
            )
            Result.success(Unit)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "team_memberships/$membershipId")
            Result.failure(e)
        }
    }

    suspend fun submitMembershipAppeal(
        team: TeamInfo,
        memberName: String,
        appealText: String
    ): Result<Unit> {
        val cleanTeamId = team.id.trim()
        if (cleanTeamId.isBlank()) {
            return Result.failure(IllegalArgumentException("Geçersiz takım."))
        }
        val cleanText = appealText.trim().take(1000)
        if (cleanText.length < 10) {
            return Result.failure(IllegalArgumentException("İtiraz açıklaması en az 10 karakter olmalıdır."))
        }
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val cleanName = memberName.trim().ifBlank { "Takım Üyesi" }.take(120)
        val membershipId = "${cleanTeamId}_$uid"
        return try {
            db.collection("team_memberships").document(membershipId).update(
                mapOf(
                    "appealText" to cleanText,
                    "appealStatus" to TeamAppealStatus.PENDING,
                    "appealSubmittedAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            createNotification(
                recipientUserId = "managers",
                title = "${team.name}: Üye Askıya Alma İtirazı",
                message = "$cleanName: ${cleanText.take(120)}",
                type = "member_appeal",
                targetId = uid,
                senderName = cleanName,
                senderId = uid,
                teamId = cleanTeamId,
                teamName = team.name
            )
            Result.success(Unit)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "team_memberships/$membershipId")
            Result.failure(e)
        }
    }

    suspend fun dismissMembershipAppeal(
        team: TeamInfo,
        targetUserId: String,
        note: String = ""
    ): Result<Unit> {
        val cleanTargetUid = targetUserId.trim()
        if (cleanTargetUid.isBlank()) return Result.failure(IllegalArgumentException("Geçersiz üye."))
        val reviewerId = auth.currentUser?.uid ?: ""
        val membershipId = "${team.id}_$cleanTargetUid"
        val cleanNote = note.trim().take(300)
        return try {
            db.collection("team_memberships").document(membershipId).update(
                mapOf(
                    "appealStatus" to TeamAppealStatus.REJECTED,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            val msg = if (cleanNote.isNotBlank()) {
                "${team.name} askıya alma itirazınız reddedildi: $cleanNote"
            } else {
                "${team.name} askıya alma itirazınız takım yetkilisi tarafından reddedildi."
            }
            createNotification(
                recipientUserId = cleanTargetUid,
                title = "${team.name}: İtiraz Sonucu",
                message = msg,
                type = "member_appeal_rejected",
                targetId = cleanTargetUid,
                senderId = reviewerId,
                teamId = team.id,
                teamName = team.name
            )
            Result.success(Unit)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "team_memberships/$membershipId")
            Result.failure(e)
        }
    }

    suspend fun removeTeamMembership(team: TeamInfo, targetUserId: String): Result<Unit> {
        val cleanTargetUid = targetUserId.trim()
        val currentUid = auth.currentUser?.uid
        if (cleanTargetUid == currentUid) {
            return Result.failure(IllegalArgumentException("Kendinizi yöneticiden silemezsiniz; takımdan ayrılmayı kullanın."))
        }
        val membershipId = "${team.id}_$cleanTargetUid"
        return try {
            val snap = db.collection("team_memberships").document(membershipId).get().await()
            val email = snap.getString("userEmail").orEmpty()
            val callerIsFounder = auth.currentUser?.email?.trim()
                .equals(PLATFORM_FOUNDER_EMAIL, ignoreCase = true)
            if (team.id == PLATFORM_MAIN_TEAM_ID &&
                (email.equals(PLATFORM_FOUNDER_EMAIL, ignoreCase = true) ||
                 (!callerIsFounder && email.equals(BLACKSEA_AUTHORIZED_TEACHER_EMAIL, ignoreCase = true)))
            ) {
                return Result.failure(IllegalArgumentException("BlackSea Robotics yetkili öğretmen hesabı ekipten çıkarılamaz."))
            }
            db.collection("team_memberships").document(membershipId).delete().await()
            syncTeamMemberCount(team.id)
            Result.success(Unit)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "team_memberships/$membershipId")
            Result.failure(e)
        }
    }

    suspend fun leaveTeam(teamId: String): Result<Unit> {
        val cleanTeamId = teamId.trim().ifBlank { PLATFORM_MAIN_TEAM_ID }
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val membershipId = "${cleanTeamId}_$uid"
        return try {
            // Recompute exact memberCount BEFORE deleting our membership so isActiveTeamMember(teamId) allows the count sync
            syncTeamMemberCount(cleanTeamId, excludeUserId = uid)
            db.collection("team_memberships").document(membershipId).delete().await()
            try {
                val userRef = db.collection("users").document(uid)
                val userSnap = userRef.get().await()
                if (userSnap.getString("activeTeamId") == cleanTeamId) {
                    userRef.update(
                        mapOf(
                            "activeTeamId" to "",
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            } catch (_: Exception) {
            }
            Result.success(Unit)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "team_memberships/$membershipId")
            Result.failure(e)
        }
    }

    suspend fun deleteTeamCompletely(teamId: String): Result<Unit> {
        val cleanTeamId = teamId.trim()
        if (cleanTeamId.isBlank() || cleanTeamId == PLATFORM_MAIN_TEAM_ID) {
            return Result.failure(IllegalArgumentException("Ana platform takımı silinemez."))
        }
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }

        return try {
            val teamRef = db.collection("teams").document(cleanTeamId)
            val teamSnap = teamRef.get().await()
            if (!teamSnap.exists()) {
                return Result.failure(IllegalArgumentException("Takım zaten silinmiş."))
            }
            if (teamSnap.getBoolean("isMainTeam") == true) {
                return Result.failure(IllegalArgumentException("Ana platform takımı silinemez."))
            }

            suspend fun deleteCollectionRefs(refs: List<com.google.firebase.firestore.DocumentReference>) {
                refs.chunked(450).forEach { chunk ->
                    val batch = db.batch()
                    chunk.forEach { batch.delete(it) }
                    batch.commit().await()
                }
            }

            // 1. Delete rooms and nested messages while caller is still team manager
            try {
                val roomsSnap = teamRef.collection("rooms").get().await()
                for (roomDoc in roomsSnap.documents) {
                    val msgsSnap = roomDoc.reference.collection("messages").get().await()
                    deleteCollectionRefs(msgsSnap.documents.map { it.reference })
                }
                deleteCollectionRefs(roomsSnap.documents.map { it.reference })
            } catch (e: Exception) {
                logDebugWarn("Repository", "Room cleanup skipped during team delete", e)
            }

            // 2. Delete ideas and nested comments
            try {
                val ideasSnap = teamRef.collection("ideas").get().await()
                for (ideaDoc in ideasSnap.documents) {
                    val commentsSnap = ideaDoc.reference.collection("comments").get().await()
                    deleteCollectionRefs(commentsSnap.documents.map { it.reference })
                }
                deleteCollectionRefs(ideasSnap.documents.map { it.reference })
            } catch (e: Exception) {
                logDebugWarn("Repository", "Idea cleanup skipped during team delete", e)
            }

            // 3. Delete files, announcements, tasks, support_tickets
            listOf("files", "announcements", "tasks", "support_tickets").forEach { subCol ->
                try {
                    val snap = teamRef.collection(subCol).get().await()
                    deleteCollectionRefs(snap.documents.map { it.reference })
                } catch (e: Exception) {
                    logDebugWarn("Repository", "$subCol cleanup skipped during team delete", e)
                }
            }

            // 4. Delete team notifications and reopen watchers
            try {
                val notifsSnap = db.collection("notifications")
                    .whereEqualTo("teamId", cleanTeamId)
                    .get()
                    .await()
                deleteCollectionRefs(notifsSnap.documents.map { it.reference })
            } catch (e: Exception) {
                logDebugWarn("Repository", "Notification cleanup skipped during team delete", e)
            }
            try {
                val watchersSnap = db.collection("team_reopen_watchers")
                    .whereEqualTo("teamId", cleanTeamId)
                    .get()
                    .await()
                deleteCollectionRefs(watchersSnap.documents.map { it.reference })
            } catch (e: Exception) {
                logDebugWarn("Repository", "Watcher cleanup skipped during team delete", e)
            }

            // 5. Delete other members' team_memberships while caller still has isTeamManager(cleanTeamId)
            val callerMembershipId = "${cleanTeamId}_$uid"
            val membershipsSnap = db.collection("team_memberships")
                .whereEqualTo("teamId", cleanTeamId)
                .get()
                .await()
            val otherMembershipRefs = membershipsSnap.documents
                .filter { it.id != callerMembershipId }
                .map { it.reference }
            deleteCollectionRefs(otherMembershipRefs)

            // 6. Delete the team registry document itself while caller is still team manager
            teamRef.delete().await()

            // 7. Finally delete caller's own membership doc
            val ownMembershipRef = db.collection("team_memberships").document(callerMembershipId)
            if (membershipsSnap.documents.any { it.id == callerMembershipId }) {
                ownMembershipRef.delete().await()
            } else {
                try {
                    ownMembershipRef.delete().await()
                } catch (_: Exception) {
                }
            }

            Result.success(Unit)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "teams/$cleanTeamId")
            Result.failure(e)
        }
    }

    suspend fun updateMyTeamMembershipProfile(
        teamId: String,
        userId: String,
        displayName: String,
        subTeam: String
    ): Result<Unit> {
        val membershipId = "${teamId}_$userId"
        return try {
            db.collection("team_memberships").document(membershipId).update(
                mapOf(
                    "userName" to displayName.trim(),
                    "subTeam" to subTeam.trim(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "team_memberships/$membershipId")
            Result.failure(e)
        }
    }

    suspend fun syncTeamMemberCountIfDrifted(teamId: String, liveActiveCount: Long) {
        val cleanTeamId = teamId.trim().ifBlank { PLATFORM_MAIN_TEAM_ID }
        try {
            val teamRef = db.collection("teams").document(cleanTeamId)
            val snap = teamRef.get().await()
            if (!snap.exists()) return
            val storedCount = snap.getLong("memberCount") ?: -1L
            if (storedCount != liveActiveCount && liveActiveCount >= 0L) {
                teamRef.update(
                    mapOf(
                        "memberCount" to liveActiveCount,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (_: Exception) {
        }
    }

    private suspend fun syncTeamMemberCount(teamId: String, excludeUserId: String? = null) {
        val cleanTeamId = teamId.trim().ifBlank { PLATFORM_MAIN_TEAM_ID }
        try {
            val activeSnap = db.collection("team_memberships")
                .whereEqualTo("teamId", cleanTeamId)
                .get()
                .await()
            val activeUserIds = mutableSetOf<String>()
            for (doc in activeSnap.documents) {
                val uid = doc.getString("userId")?.trim().orEmpty()
                val email = doc.getString("userEmail")?.trim().orEmpty()
                val excludeFounderInBsr = cleanTeamId == PLATFORM_MAIN_TEAM_ID &&
                    email.equals(PLATFORM_FOUNDER_EMAIL, ignoreCase = true)
                if (uid.isNotBlank() && uid != excludeUserId && !excludeFounderInBsr && UserStatus.isActive(doc.getString("status"))) {
                    activeUserIds.add(uid)
                }
            }
            if (cleanTeamId == PLATFORM_MAIN_TEAM_ID) {
                try {
                    val usersSnap = db.collection("users").get().await()
                    val allMembershipUids = activeSnap.documents.mapNotNull { it.getString("userId")?.trim() }.toSet()
                    for (userDoc in usersSnap.documents) {
                        val legacy = userDoc.toObject(UserProfile::class.java) ?: continue
                        val uid = legacy.userId.ifBlank { legacy.id }.trim()
                        val isFounder = legacy.email.trim().equals(PLATFORM_FOUNDER_EMAIL, ignoreCase = true)
                        if (uid.isNotBlank() && uid != excludeUserId && !isFounder && uid !in allMembershipUids) {
                            val isBsrLegacy = legacy.activeTeamId == PLATFORM_MAIN_TEAM_ID ||
                                (legacy.activeTeamId.isBlank() && legacy.roles.any { it != "Takım Üyesi" && !it.equals("Yönetici", ignoreCase = true) })
                            if (isBsrLegacy && UserStatus.isActive(legacy.status)) {
                                activeUserIds.add(uid)
                            }
                        }
                    }
                } catch (_: Exception) {
                }
            }
            val count = activeUserIds.size.toLong()
            db.collection("teams").document(cleanTeamId).update(
                mapOf("memberCount" to count, "updatedAt" to FieldValue.serverTimestamp())
            ).await()
        } catch (e: Exception) {
            logDebugWarn("Repository", "syncTeamMemberCount skipped for $cleanTeamId", e)
        }
    }

    suspend fun deleteOwnAccount() {
        val uid = requireUserId()
        val path = "users/$uid"
        try {
            val myMembershipsSnap = try {
                db.collection("team_memberships")
                    .whereEqualTo("userId", uid)
                    .get()
                    .await()
            } catch (e: Exception) {
                logDebugWarn("Repository", "Could not pre-query memberships before account delete", e)
                null
            }

            // Recompute memberCount for every team this user was actively in BEFORE deleting membership/user docs
            val affectedTeamIds = myMembershipsSnap?.documents
                ?.filter { UserStatus.isActive(it.getString("status")) }
                ?.mapNotNull { it.getString("teamId")?.trim()?.takeIf { id -> id.isNotBlank() } }
                ?.toMutableSet()
                ?: mutableSetOf()
            if (affectedTeamIds.contains(PLATFORM_MAIN_TEAM_ID) || myMembershipsSnap == null) {
                affectedTeamIds.add(PLATFORM_MAIN_TEAM_ID)
            }
            for (tId in affectedTeamIds) {
                syncTeamMemberCount(tId, excludeUserId = uid)
            }

            try {
                val personalNotifs = db.collection("notifications")
                    .whereEqualTo("recipientUserId", uid)
                    .get()
                    .await()
                if (!personalNotifs.isEmpty) {
                    personalNotifs.documents.chunked(450).forEach { chunk ->
                        val batch = db.batch()
                        chunk.forEach { batch.delete(it.reference) }
                        batch.commit().await()
                    }
                }
            } catch (cleanupEx: Exception) {
                logDebugWarn("Repository", "Self notification cleanup skipped", cleanupEx)
            }

            try {
                if (myMembershipsSnap != null && !myMembershipsSnap.isEmpty) {
                    myMembershipsSnap.documents.chunked(450).forEach { chunk ->
                        val batch = db.batch()
                        chunk.forEach { batch.delete(it.reference) }
                        batch.commit().await()
                    }
                }
            } catch (memCleanupEx: Exception) {
                logDebugWarn("Repository", "Self membership cleanup skipped", memCleanupEx)
            }

            db.collection("users").document(uid).delete().await()

            try {
                auth.currentUser?.delete()?.await()
            } catch (authDeleteEx: Exception) {
                logDebugWarn("Repository", "Auth account deletion skipped; proceeding with sign-out", authDeleteEx)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            throw e
        }
    }

    // ==========================================
    // NOTIFICATIONS (MULTI-TEAM ISOLATED)
    // ==========================================

    fun observeNotifications(
        userId: String,
        isManager: Boolean,
        userTeam: String = "",
        userCreatedAtMs: Long = 0L,
        teamId: String = PLATFORM_MAIN_TEAM_ID
    ): Flow<List<TeamNotification>> {
        if (userId.isBlank()) return flowOf(emptyList())
        val personalFlow = db.collection("notifications")
            .whereEqualTo("recipientUserId", userId)
            .snapshots()
            .map { it.toObjects(TeamNotification::class.java) }
            .catch { emit(emptyList()) }

        val teamFlow = db.collection("notifications")
            .whereEqualTo("teamId", teamId.ifBlank { PLATFORM_MAIN_TEAM_ID })
            .snapshots()
            .map { it.toObjects(TeamNotification::class.java) }
            .catch { emit(emptyList()) }

        return combine(personalFlow, teamFlow) { personal, teamNotifs ->
            (personal + teamNotifs)
                .distinctBy { it.id }
                .filter { notif ->
                    shouldUserReceiveNotification(
                        notif = notif,
                        userId = userId,
                        isManager = isManager,
                        userTeam = userTeam,
                        userCreatedAtMs = userCreatedAtMs
                    )
                }
                .sortedByDescending { it.createdAt?.toDate()?.time ?: Long.MAX_VALUE }
        }
    }

    fun observeNotificationsForUser(
        userId: String,
        membershipsByTeam: Map<String, TeamMembership>,
        isPlatformAdmin: Boolean,
        userCreatedAtMs: Long = 0L
    ): Flow<List<TeamNotification>> {
        if (userId.isBlank()) return flowOf(emptyList())
        val activeTeamIds = membershipsByTeam.values
            .filter { it.isActive() }
            .map { it.teamId }
            .distinct()

        return channelFlow {
            val buckets = ConcurrentHashMap<String, List<TeamNotification>>()

            fun emitMergedSnapshot() {
                val merged = LinkedHashMap<String, TeamNotification>()
                for (list in buckets.values) {
                    for (item in list) {
                        merged[item.id] = item
                    }
                }
                val filtered = merged.values
                    .filter { notif ->
                        shouldUserReceiveNotification(
                            notif = notif,
                            userId = userId,
                            membershipsByTeam = membershipsByTeam,
                            isPlatformAdmin = isPlatformAdmin,
                            userCreatedAtMs = userCreatedAtMs
                        )
                    }
                    .sortedByDescending { it.createdAt?.toDate()?.time ?: Long.MAX_VALUE }
                trySend(filtered)
            }

            fun subscribeQuery(key: String, query: com.google.firebase.firestore.Query) {
                launch {
                    query.snapshots()
                        .map { it.toObjects(TeamNotification::class.java) }
                        .catch { error ->
                            handleFirestoreError(error, OperationType.LIST, "notifications?$key")
                            emit(emptyList())
                        }
                        .collect { items ->
                            buckets[key] = items
                            emitMergedSnapshot()
                        }
                }
            }

            subscribeQuery("recipient=$userId", db.collection("notifications").whereEqualTo("recipientUserId", userId))
            subscribeQuery("platform", db.collection("notifications").whereEqualTo("teamId", "platform"))
            if (isPlatformAdmin) {
                subscribeQuery("platform_admin", db.collection("notifications").whereEqualTo("teamId", "platform_admin"))
            }
            for (tId in activeTeamIds) {
                subscribeQuery("teamId=$tId", db.collection("notifications").whereEqualTo("teamId", tId))
            }
        }
    }

    suspend fun fetchNotificationsOnceForUser(
        userId: String,
        membershipsByTeam: Map<String, TeamMembership>,
        isPlatformAdmin: Boolean,
        userCreatedAtMs: Long = 0L
    ): List<TeamNotification>? {
        if (userId.isBlank()) return emptyList()
        return try {
            val activeTeamIds = membershipsByTeam.values
                .filter { it.isActive() }
                .map { it.teamId }
                .distinct()

            val merged = LinkedHashMap<String, TeamNotification>()

            suspend fun collectQuery(query: com.google.firebase.firestore.Query) {
                try {
                    val snap = try {
                        query.get(Source.SERVER).await()
                    } catch (_: Exception) {
                        query.get().await()
                    }
                    snap.toObjects(TeamNotification::class.java).forEach { merged[it.id] = it }
                } catch (ce: CancellationException) {
                    throw ce
                } catch (_: Exception) {
                }
            }

            collectQuery(db.collection("notifications").whereEqualTo("recipientUserId", userId))
            collectQuery(db.collection("notifications").whereEqualTo("teamId", "platform"))
            if (isPlatformAdmin) {
                collectQuery(db.collection("notifications").whereEqualTo("teamId", "platform_admin"))
            }
            for (tId in activeTeamIds) {
                collectQuery(db.collection("notifications").whereEqualTo("teamId", tId))
            }

            merged.values.filter { notif ->
                shouldUserReceiveNotification(
                    notif = notif,
                    userId = userId,
                    membershipsByTeam = membershipsByTeam,
                    isPlatformAdmin = isPlatformAdmin,
                    userCreatedAtMs = userCreatedAtMs
                )
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            logDebugWarn("Repository", "fetchNotificationsOnceForUser failed", e)
            null
        }
    }

    suspend fun sendBroadcastNotification(
        title: String,
        message: String,
        senderName: String,
        teamId: String = PLATFORM_MAIN_TEAM_ID,
        teamName: String = "BlackSea Robotics"
    ) {
        val uid = auth.currentUser?.uid ?: ""
        createNotification(
            recipientUserId = "all",
            title = title,
            message = message,
            type = "broadcast",
            senderName = senderName,
            senderId = uid,
            teamId = teamId,
            teamName = teamName
        )
        try {
            addAnnouncement(
                title = title,
                content = message,
                authorName = senderName,
                isPinned = false,
                sendNotification = false,
                teamId = teamId,
                teamName = teamName
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logDebugWarn("Repository", "Could not create team announcement", e)
        }
    }

    suspend fun createNotification(
        recipientUserId: String,
        title: String,
        message: String,
        type: String,
        targetId: String = "",
        targetRoomId: String = "",
        senderName: String = "",
        senderId: String = auth.currentUser?.uid ?: "",
        teamId: String = PLATFORM_MAIN_TEAM_ID,
        teamName: String = "BlackSea Robotics"
    ) {
        val docRef = db.collection("notifications").document()
        val payload = mapOf(
            "teamId" to teamId.ifBlank { PLATFORM_MAIN_TEAM_ID },
            "teamName" to teamName,
            "recipientUserId" to recipientUserId,
            "title" to title,
            "message" to message,
            "type" to type,
            "targetId" to targetId,
            "targetRoomId" to targetRoomId,
            "senderName" to senderName,
            "senderId" to senderId,
            "isRead" to false,
            "createdAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            try {
                delay(300L)
                docRef.set(payload).await()
            } catch (ce: CancellationException) {
                throw ce
            } catch (retryErr: Exception) {
                logDebugWarn("Repository", "Notification creation failed after retry", retryErr)
            }
        }
    }

    suspend fun markNotificationAsRead(notificationId: String, userId: String = "", isShared: Boolean = false) {
        if (notificationId.isBlank()) return
        try {
            val updates = mutableMapOf<String, Any>()
            if (!isShared || userId.isBlank()) {
                updates["isRead"] = true
            }
            if (userId.isNotBlank()) {
                updates["readBy"] = FieldValue.arrayUnion(userId)
            }
            if (updates.isNotEmpty()) {
                db.collection("notifications").document(notificationId).update(updates).await()
            }
        } catch (e: Exception) {
            logDebugWarn("Repository", "Failed to mark notification as read", e)
        }
    }

    suspend fun markAllNotificationsAsRead(
        userId: String,
        notificationIds: List<String>,
        personalNotificationIds: Set<String> = emptySet()
    ) {
        val cleanIds = notificationIds.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (cleanIds.isEmpty()) return
        try {
            cleanIds.chunked(450).forEach { chunk ->
                val batch = db.batch()
                for (id in chunk) {
                    val ref = db.collection("notifications").document(id)
                    val updates = mutableMapOf<String, Any>()
                    if (userId.isNotBlank()) {
                        updates["readBy"] = FieldValue.arrayUnion(userId)
                    }
                    if (personalNotificationIds.contains(id) || userId.isBlank()) {
                        updates["isRead"] = true
                    }
                    if (updates.isNotEmpty()) {
                        batch.update(ref, updates)
                    }
                }
                batch.commit().await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logDebugWarn("Repository", "Failed to mark all as read", e)
        }
    }

    // ==========================================
    // ROOMS (TEAM SCOPED)
    // ==========================================

    suspend fun ensureDefaultRoomsExist(teamId: String = PLATFORM_MAIN_TEAM_ID) {
        val cleanTeamId = teamId.ifBlank { PLATFORM_MAIN_TEAM_ID }
        val defaultRooms = listOf(
            ChatRoom(
                roomId = "general",
                teamId = cleanTeamId,
                name = "GENEL EKİP ODASI",
                description = "Tüm takım üyelerinin ortak iletişim ve çalışma alanı",
                allowedTeam = "all"
            ),
            ChatRoom(
                roomId = "software",
                teamId = cleanTeamId,
                name = "YAZILIM EKİBİ ODASI",
                description = "Otonom sürüş, robot kontrolü, sensörler ve yapay zeka",
                allowedTeam = "all"
            ),
            ChatRoom(
                roomId = "mechanic",
                teamId = cleanTeamId,
                name = "MEKANİK EKİBİ ODASI",
                description = "Şasi, tasarım, 3D baskı, montaj ve robot mekaniği",
                allowedTeam = "all"
            ),
            ChatRoom(
                roomId = "pr",
                teamId = cleanTeamId,
                name = "PR EKİBİ ODASI",
                description = "Sosyal medya, sponsorluk, etkinlik ve tanıtım",
                allowedTeam = "all"
            )
        )

        val col = roomsCollection(cleanTeamId)
        for (room in defaultRooms) {
            val docRef = col.document(room.roomId)
            try {
                val snapshot = try { docRef.get().await() } catch (_: Exception) { null }
                if (snapshot == null || !snapshot.exists()) {
                    docRef.set(
                        mapOf(
                            "roomId" to room.roomId,
                            "teamId" to cleanTeamId,
                            "name" to room.name,
                            "description" to room.description,
                            "allowedTeam" to room.allowedTeam,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        ),
                        SetOptions.merge()
                    ).await()
                }
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.WRITE, "${workspacePathPrefix(cleanTeamId)}rooms/${room.roomId}")
            }
        }
    }

    fun observeRooms(teamId: String = PLATFORM_MAIN_TEAM_ID): Flow<List<ChatRoom>> {
        val cleanTeamId = teamId.ifBlank { PLATFORM_MAIN_TEAM_ID }
        return roomsCollection(cleanTeamId)
            .snapshots()
            .map { snapshot -> snapshot.toObjects(ChatRoom::class.java) }
            .catch { error ->
                handleFirestoreError(error, OperationType.LIST, "${workspacePathPrefix(cleanTeamId)}rooms")
                emit(emptyList())
            }
    }

    // ==========================================
    // MESSAGES (TEAM SCOPED)
    // ==========================================

    fun observeMessages(roomId: String, teamId: String = PLATFORM_MAIN_TEAM_ID): Flow<List<ChatMessage>> {
        val cleanTeamId = teamId.ifBlank { PLATFORM_MAIN_TEAM_ID }
        val path = "${workspacePathPrefix(cleanTeamId)}rooms/$roomId/messages"
        return roomsCollection(cleanTeamId).document(roomId).collection("messages")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(ChatMessage::class.java)
                    .sortedBy { it.createdAt?.toDate()?.time ?: Long.MAX_VALUE }
            }
            .catch { error ->
                handleFirestoreError(error, OperationType.LIST, path)
                emit(emptyList())
            }
    }

    suspend fun sendMessage(
        roomId: String,
        text: String,
        senderName: String,
        senderTeam: String,
        senderRoles: List<String> = emptyList(),
        senderPhotoUrl: String = "",
        fileName: String = "",
        fileUrl: String = "",
        fileType: String = "",
        fileSize: Long = 0L,
        thumbnailUrl: String = "",
        storagePath: String = "",
        mediaDurationMs: Long = 0L,
        replyToId: String = "",
        replyToSender: String = "",
        replyToText: String = "",
        teamId: String = PLATFORM_MAIN_TEAM_ID,
        teamName: String = "BlackSea Robotics"
    ) {
        val uid = requireUserId()
        val cleanTeamId = teamId.ifBlank { PLATFORM_MAIN_TEAM_ID }
        val path = "${workspacePathPrefix(cleanTeamId)}rooms/$roomId/messages"
        val docRef = roomsCollection(cleanTeamId).document(roomId).collection("messages").document()
        val safeFileUrl = if (fileUrl.startsWith("data:") && fileUrl.length > 500_000) "" else fileUrl
        val safeThumbUrl = if (thumbnailUrl.startsWith("data:") && thumbnailUrl.length > 350_000) "" else thumbnailUrl
        val payload = mapOf(
            "roomId" to roomId,
            "teamId" to cleanTeamId,
            "senderId" to uid,
            "senderName" to senderName,
            "senderTeam" to senderTeam,
            "senderRoles" to senderRoles,
            "senderPhotoUrl" to senderPhotoUrl,
            "text" to text,
            "fileName" to fileName,
            "fileUrl" to safeFileUrl,
            "fileType" to fileType,
            "fileSize" to fileSize,
            "thumbnailUrl" to safeThumbUrl,
            "storagePath" to storagePath,
            "mediaDurationMs" to mediaDurationMs,
            "isPinned" to false,
            "replyToId" to replyToId,
            "replyToSender" to replyToSender,
            "replyToText" to replyToText,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()

            val roomName = when (roomId) {
                "general" -> "Genel Ekip"
                "pr" -> "PR Odası"
                "mechanic" -> "Mekanik Odası"
                "software" -> "Yazılım Odası"
                else -> "$roomId Odası"
            }
            val previewBody = when {
                text.isNotBlank() -> "$senderName: ${text.take(60)}"
                fileName.isNotBlank() -> "$senderName: $fileName paylaştı"
                else -> "$senderName yeni bir içerik paylaştı"
            }
            createNotification(
                recipientUserId = "all",
                title = "Yeni mesaj ($roomName)",
                message = previewBody,
                type = "message",
                targetId = docRef.id,
                targetRoomId = roomId,
                senderName = senderName,
                senderId = uid,
                teamId = cleanTeamId,
                teamName = teamName
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            throw e
        }
    }

    suspend fun togglePinMessage(
        roomId: String,
        messageId: String,
        isPinned: Boolean,
        teamId: String = PLATFORM_MAIN_TEAM_ID
    ) {
        val cleanTeamId = teamId.ifBlank { PLATFORM_MAIN_TEAM_ID }
        val path = "${workspacePathPrefix(cleanTeamId)}rooms/$roomId/messages/$messageId"
        val docRef = roomsCollection(cleanTeamId).document(roomId).collection("messages").document(messageId)
        try {
            docRef.update(
                mapOf(
                    "isPinned" to isPinned,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            throw e
        }
    }

    suspend fun editMessage(
        roomId: String,
        messageId: String,
        newText: String,
        teamId: String = PLATFORM_MAIN_TEAM_ID
    ) {
        val uid = requireUserId()
        val trimmed = newText.trim()
        require(trimmed.isNotBlank()) { "Mesaj metni boş olamaz." }
        val cleanTeamId = teamId.ifBlank { PLATFORM_MAIN_TEAM_ID }
        val path = "${workspacePathPrefix(cleanTeamId)}rooms/$roomId/messages/$messageId"
        val docRef = roomsCollection(cleanTeamId).document(roomId).collection("messages").document(messageId)
        try {
            val existing = docRef.get().await()
            if (!existing.exists()) {
                throw IllegalStateException("Mesaj bulunamadı.")
            }
            val senderId = existing.getString("senderId") ?: ""
            if (senderId != uid) {
                throw IllegalStateException("Yalnızca kendi mesajınızı düzenleyebilirsiniz.")
            }
            val createdMs = existing.getTimestamp("createdAt")?.toDate()?.time
                ?: throw IllegalStateException("Mesaj zamanı doğrulanamadı.")
            if (System.currentTimeMillis() - createdMs > com.example.model.MESSAGE_EDIT_WINDOW_MS) {
                throw IllegalStateException("Mesaj yalnızca gönderildikten sonraki 1 saat içinde düzenlenebilir.")
            }
            docRef.update(
                mapOf(
                    "text" to trimmed,
                    "isEdited" to true,
                    "editedAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            throw e
        }
    }

    suspend fun deleteMessage(
        roomId: String,
        messageId: String,
        teamId: String = PLATFORM_MAIN_TEAM_ID
    ) {
        val cleanTeamId = teamId.ifBlank { PLATFORM_MAIN_TEAM_ID }
        val path = "${workspacePathPrefix(cleanTeamId)}rooms/$roomId/messages/$messageId"
        val docRef = roomsCollection(cleanTeamId).document(roomId).collection("messages").document(messageId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            throw e
        }
    }

    suspend fun deleteMessages(
        roomId: String,
        messageIds: List<String>,
        teamId: String = PLATFORM_MAIN_TEAM_ID
    ) {
        requireUserId()
        val cleanIds = messageIds.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (cleanIds.isEmpty()) return
        val cleanTeamId = teamId.ifBlank { PLATFORM_MAIN_TEAM_ID }
        val path = "${workspacePathPrefix(cleanTeamId)}rooms/$roomId/messages"
        val messagesCol = roomsCollection(cleanTeamId).document(roomId).collection("messages")
        try {
            cleanIds.chunked(450).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { msgId ->
                    batch.delete(messagesCol.document(msgId))
                }
                batch.commit().await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            throw e
        }
    }

    // ==========================================
    // IDEAS & COMMENTS (TEAM SCOPED)
    // ==========================================

    fun observeIdeas(roomId: String? = null, teamId: String = PLATFORM_MAIN_TEAM_ID): Flow<List<IdeaItem>> {
        val cleanTeamId = teamId.ifBlank { PLATFORM_MAIN_TEAM_ID }
        val path = "${workspacePathPrefix(cleanTeamId)}ideas"
        return ideasCollection(cleanTeamId)
            .snapshots()
            .map { snapshot ->
                val items = snapshot.toObjects(IdeaItem::class.java)
                val filtered = if (!roomId.isNullOrBlank() && roomId != "all") {
                    items.filter { it.roomId == roomId }
                } else {
                    items
                }
                filtered.sortedByDescending { it.createdAt?.toDate()?.time ?: Long.MAX_VALUE }
            }
            .catch { error ->
                handleFirestoreError(error, OperationType.LIST, path)
                emit(emptyList())
            }
    }

    suspend fun addIdea(
        roomId: String,
        title: String,
        description: String,
        category: String = IdeaStatus.NEW,
        authorName: String,
        authorTeam: String,
        authorRoles: List<String> = emptyList(),
        fileName: String = "",
        fileType: String = "",
        fileUrl: String = "",
        fileSize: Long = 0L,
        thumbnailUrl: String = "",
        storagePath: String = "",
        mediaDurationMs: Long = 0L,
        teamId: String = PLATFORM_MAIN_TEAM_ID,
        teamName: String = "BlackSea Robotics"
    ) {
        val uid = requireUserId()
        val cleanTeamId = teamId.ifBlank { PLATFORM_MAIN_TEAM_ID }
        val docRef = ideasCollection(cleanTeamId).document()
        val safeFileUrl = if (fileUrl.startsWith("data:") && fileUrl.length > 500_000) "" else fileUrl
        val safeThumbUrl = if (thumbnailUrl.startsWith("data:") && thumbnailUrl.length > 350_000) "" else thumbnailUrl
        val payload = mapOf(
            "teamId" to cleanTeamId,
            "roomId" to roomId,
            "authorId" to uid,
            "authorName" to authorName,
            "authorTeam" to authorTeam,
            "authorRoles" to authorRoles,
            "title" to title,
            "description" to description,
            "category" to category,
            "likes" to emptyList<String>(),
            "likeCount" to 0L,
            "fileName" to fileName,
            "fileType" to fileType,
            "fileUrl" to safeFileUrl,
            "fileSize" to fileSize,
            "thumbnailUrl" to safeThumbUrl,
            "storagePath" to storagePath,
            "mediaDurationMs" to mediaDurationMs,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()

            createNotification(
                recipientUserId = "all",
                title = "Yeni fikir",
                message = "$authorName yeni bir fikir ekledi: \"$title\"",
                type = "idea",
                targetId = docRef.id,
                targetRoomId = roomId,
                senderName = authorName,
                senderId = uid,
                teamId = cleanTeamId,
                teamName = teamName
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "${workspacePathPrefix(cleanTeamId)}ideas")
            throw e
        }
    }

    suspend fun toggleLikeIdea(
        ideaId: String,
        currentLikes: List<String>,
        teamId: String = PLATFORM_MAIN_TEAM_ID
    ): Boolean {
        val uid = requireUserId()
        val cleanTeamId = teamId.ifBlank { PLATFORM_MAIN_TEAM_ID }
        val docRef = ideasCollection(cleanTeamId).document(ideaId)
        val isCurrentlyLiked = currentLikes.contains(uid)
        val newLikes = if (isCurrentlyLiked) {
            currentLikes - uid
        } else {
            currentLikes + uid
        }
        try {
            docRef.update(
                mapOf(
                    "likes" to newLikes,
                    "likeCount" to newLikes.size.toLong(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            return !isCurrentlyLiked
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "${workspacePathPrefix(cleanTeamId)}ideas/$ideaId")
            throw e
        }
    }

    suspend fun updateIdeaCategory(
        ideaId: String,
        newCategory: String,
        teamId: String = PLATFORM_MAIN_TEAM_ID,
        teamName: String = "BlackSea Robotics"
    ) {
        val cleanTeamId = teamId.ifBlank { PLATFORM_MAIN_TEAM_ID }
        val docRef = ideasCollection(cleanTeamId).document(ideaId)
        try {
            docRef.update(
                mapOf(
                    "category" to newCategory,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            if (newCategory == IdeaStatus.ACCEPTED) {
                createNotification(
                    recipientUserId = "all",
                    title = "Fikir Kabul Edildi!",
                    message = "Takım yönetimi bir fikri kabul etti.",
                    type = "idea",
                    targetId = ideaId,
                    senderId = auth.currentUser?.uid ?: "",
                    teamId = cleanTeamId,
                    teamName = teamName
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "${workspacePathPrefix(cleanTeamId)}ideas/$ideaId")
            throw e
        }
    }

    suspend fun deleteIdea(ideaId: String, teamId: String = PLATFORM_MAIN_TEAM_ID) {
        val cleanTeamId = teamId.ifBlank { PLATFORM_MAIN_TEAM_ID }
        val docRef = ideasCollection(cleanTeamId).document(ideaId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "${workspacePathPrefix(cleanTeamId)}ideas/$ideaId")
            throw e
        }
    }

    fun observeIdeaComments(ideaId: String, teamId: String = PLATFORM_MAIN_TEAM_ID): Flow<List<IdeaComment>> {
        val cleanTeamId = teamId.ifBlank { PLATFORM_MAIN_TEAM_ID }
        val path = "${workspacePathPrefix(cleanTeamId)}ideas/$ideaId/comments"
        return ideasCollection(cleanTeamId).document(ideaId).collection("comments")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(IdeaComment::class.java)
                    .sortedBy { it.createdAt?.toDate()?.time ?: Long.MAX_VALUE }
            }
            .catch { error ->
                handleFirestoreError(error, OperationType.LIST, path)
                emit(emptyList())
            }
    }

    suspend fun addIdeaComment(
        ideaId: String,
        text: String,
        authorName: String,
        authorRoles: List<String> = emptyList(),
        teamId: String = PLATFORM_MAIN_TEAM_ID,
        teamName: String = "BlackSea Robotics"
    ) {
        val uid = requireUserId()
        val cleanTeamId = teamId.ifBlank { PLATFORM_MAIN_TEAM_ID }
        val docRef = ideasCollection(cleanTeamId).document(ideaId).collection("comments").document()
        val payload = mapOf(
            "ideaId" to ideaId,
            "authorId" to uid,
            "authorName" to authorName,
            "authorRoles" to authorRoles,
            "text" to text,
            "createdAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()

            try {
                val ideaDoc = ideasCollection(cleanTeamId).document(ideaId).get().await()
                val ideaAuthorId = ideaDoc.getString("authorId")
                if (!ideaAuthorId.isNullOrBlank() && ideaAuthorId != uid) {
                    createNotification(
                        recipientUserId = ideaAuthorId,
                        title = "Yeni yorum",
                        message = "$authorName fikrinize yorum yaptı: \"${text.take(40)}\"",
                        type = "comment",
                        targetId = ideaId,
                        senderName = authorName,
                        senderId = uid,
                        teamId = cleanTeamId,
                        teamName = teamName
                    )
                }
            } catch (e: Exception) {
                logDebugWarn("Repository", "Could not notify idea author", e)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "${workspacePathPrefix(cleanTeamId)}ideas/$ideaId/comments")
            throw e
        }
    }

    suspend fun deleteIdeaComment(
        ideaId: String,
        commentId: String,
        teamId: String = PLATFORM_MAIN_TEAM_ID
    ) {
        val cleanTeamId = teamId.ifBlank { PLATFORM_MAIN_TEAM_ID }
        val docRef = ideasCollection(cleanTeamId).document(ideaId).collection("comments").document(commentId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "${workspacePathPrefix(cleanTeamId)}ideas/$ideaId/comments/$commentId")
            throw e
        }
    }

    // ==========================================
    // WORKSPACE FILES (TEAM SCOPED)
    // ==========================================

    fun observeSharedFiles(roomId: String? = null, teamId: String = PLATFORM_MAIN_TEAM_ID): Flow<List<SharedProjectFile>> {
        val cleanTeamId = teamId.ifBlank { PLATFORM_MAIN_TEAM_ID }
        return filesCollection(cleanTeamId)
            .snapshots()
            .map { snapshot ->
                val items = snapshot.toObjects(SharedProjectFile::class.java)
                val filtered = if (!roomId.isNullOrBlank() && roomId != "all") {
                    items.filter { it.roomId == roomId }
                } else {
                    items
                }
                filtered.sortedByDescending { it.createdAt?.toDate()?.time ?: Long.MAX_VALUE }
            }
            .catch { error ->
                handleFirestoreError(error, OperationType.LIST, "${workspacePathPrefix(cleanTeamId)}files")
                emit(emptyList())
            }
    }

    suspend fun addSharedFile(
        roomId: String,
        fileName: String,
        fileType: String,
        fileSize: Long,
        description: String,
        content: String,
        downloadUrl: String = "",
        storagePath: String = "",
        thumbnailUrl: String = "",
        mediaDurationMs: Long = 0L,
        uploadedByName: String,
        uploadedByTeam: String = "",
        uploadedByRoles: List<String> = emptyList(),
        notifyTeam: Boolean = false,
        teamId: String = PLATFORM_MAIN_TEAM_ID,
        teamName: String = "BlackSea Robotics"
    ) {
        val uid = requireUserId()
        val cleanTeamId = teamId.ifBlank { PLATFORM_MAIN_TEAM_ID }
        val docRef = filesCollection(cleanTeamId).document()
        val safeContent = content.take(200_000)
        val safeDownloadUrl = if (downloadUrl.startsWith("data:") && downloadUrl.length > 500_000) "" else downloadUrl
        val safeThumbUrl = if (thumbnailUrl.startsWith("data:") && thumbnailUrl.length > 350_000) "" else thumbnailUrl
        val payload = mapOf(
            "teamId" to cleanTeamId,
            "roomId" to roomId,
            "uploadedById" to uid,
            "uploadedByName" to uploadedByName,
            "uploadedByTeam" to uploadedByTeam,
            "uploadedByRoles" to uploadedByRoles,
            "fileName" to fileName,
            "fileType" to fileType,
            "fileSize" to fileSize,
            "description" to description,
            "content" to safeContent,
            "downloadUrl" to safeDownloadUrl,
            "storagePath" to storagePath,
            "thumbnailUrl" to safeThumbUrl,
            "mediaDurationMs" to mediaDurationMs,
            "createdAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            if (notifyTeam) {
                createNotification(
                    recipientUserId = "all",
                    title = "Yeni dosya paylaşıldı",
                    message = "$uploadedByName: $fileName",
                    type = "file",
                    targetId = docRef.id,
                    targetRoomId = roomId,
                    senderName = uploadedByName,
                    senderId = uid,
                    teamId = cleanTeamId,
                    teamName = teamName
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "${workspacePathPrefix(cleanTeamId)}files")
            throw e
        }
    }

    suspend fun deleteSharedFile(fileId: String, teamId: String = PLATFORM_MAIN_TEAM_ID) {
        val cleanTeamId = teamId.ifBlank { PLATFORM_MAIN_TEAM_ID }
        val docRef = filesCollection(cleanTeamId).document(fileId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "${workspacePathPrefix(cleanTeamId)}files/$fileId")
            throw e
        }
    }

    // ==========================================
    // ANNOUNCEMENTS (TEAM SCOPED)
    // ==========================================

    fun observeAnnouncements(teamId: String = PLATFORM_MAIN_TEAM_ID): Flow<List<TeamAnnouncement>> {
        val cleanTeamId = teamId.ifBlank { PLATFORM_MAIN_TEAM_ID }
        return announcementsCollection(cleanTeamId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(TeamAnnouncement::class.java)
                    .sortedWith(
                        compareByDescending<TeamAnnouncement> { it.isPinned }
                            .thenByDescending { it.createdAt?.toDate()?.time ?: Long.MAX_VALUE }
                    )
            }
            .catch { error ->
                handleFirestoreError(error, OperationType.LIST, "${workspacePathPrefix(cleanTeamId)}announcements")
                emit(emptyList())
            }
    }

    suspend fun addAnnouncement(
        title: String,
        content: String,
        authorName: String,
        isPinned: Boolean,
        sendNotification: Boolean = true,
        teamId: String = PLATFORM_MAIN_TEAM_ID,
        teamName: String = "BlackSea Robotics"
    ) {
        val uid = requireUserId()
        val cleanTeamId = teamId.ifBlank { PLATFORM_MAIN_TEAM_ID }
        val docRef = announcementsCollection(cleanTeamId).document()
        val payload = mapOf(
            "teamId" to cleanTeamId,
            "authorId" to uid,
            "authorName" to authorName,
            "title" to title,
            "content" to content,
            "isPinned" to isPinned,
            "createdAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()

            if (sendNotification) {
                createNotification(
                    recipientUserId = "all",
                    title = "Yeni duyuru",
                    message = "$authorName yeni bir duyuru yayınladı: \"$title\"",
                    type = "announcement",
                    targetId = docRef.id,
                    senderName = authorName,
                    senderId = uid,
                    teamId = cleanTeamId,
                    teamName = teamName
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "${workspacePathPrefix(cleanTeamId)}announcements")
            throw e
        }
    }

    suspend fun deleteAnnouncement(announcementId: String, teamId: String = PLATFORM_MAIN_TEAM_ID) {
        val cleanTeamId = teamId.ifBlank { PLATFORM_MAIN_TEAM_ID }
        val docRef = announcementsCollection(cleanTeamId).document(announcementId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "${workspacePathPrefix(cleanTeamId)}announcements/$announcementId")
            throw e
        }
    }

    // ==========================================
    // TEAM TASKS ("GÖREVLER" — TEAM SCOPED)
    // ==========================================

    fun observeTeamTasks(teamId: String = PLATFORM_MAIN_TEAM_ID): Flow<List<TeamTask>> {
        val cleanTeamId = teamId.ifBlank { PLATFORM_MAIN_TEAM_ID }
        return tasksCollection(cleanTeamId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(TeamTask::class.java)
                    .sortedWith(
                        compareBy<TeamTask> {
                            when (TeamTaskStatus.normalize(it.status)) {
                                TeamTaskStatus.IN_PROGRESS -> 0
                                TeamTaskStatus.TODO -> 1
                                else -> 2
                            }
                        }.thenByDescending { it.createdAt?.toDate()?.time ?: Long.MAX_VALUE }
                    )
            }
            .catch { error ->
                handleFirestoreError(error, OperationType.LIST, "teams/$cleanTeamId/tasks")
                emit(emptyList())
            }
    }

    suspend fun addTeamTask(
        teamId: String,
        title: String,
        description: String,
        subTeam: String,
        assignedToId: String = "",
        assignedToName: String = "",
        createdByName: String,
        priority: String = "Orta",
        dueDateText: String = "",
        teamName: String = ""
    ): Result<String> {
        val cleanTeamId = teamId.ifBlank { PLATFORM_MAIN_TEAM_ID }
        val cleanTitle = title.trim().take(140)
        if (cleanTitle.length < 2) {
            return Result.failure(IllegalArgumentException("Görev başlığı en az 2 karakter olmalıdır."))
        }
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val docRef = tasksCollection(cleanTeamId).document()
        val payload = mapOf(
            "teamId" to cleanTeamId,
            "title" to cleanTitle,
            "description" to description.trim().take(1000),
            "subTeam" to subTeam.ifBlank { TeamType.GENERAL.displayName }.take(80),
            "assignedToId" to assignedToId.trim().take(128),
            "assignedToName" to assignedToName.trim().take(120),
            "createdById" to uid,
            "createdByName" to createdByName.trim().ifBlank { "Takım Üyesi" }.take(120),
            "priority" to (priority.takeIf { it in listOf("Düşük", "Orta", "Yüksek") } ?: "Orta"),
            "status" to TeamTaskStatus.TODO,
            "dueDateText" to dueDateText.trim().take(40),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            if (assignedToId.isNotBlank() && assignedToId != uid) {
                createNotification(
                    recipientUserId = assignedToId,
                    title = "Yeni Görev Atandı",
                    message = "$createdByName size \"$cleanTitle\" görevini atadı.",
                    type = "task",
                    targetId = docRef.id,
                    senderName = createdByName,
                    senderId = uid,
                    teamId = cleanTeamId,
                    teamName = teamName
                )
            }
            Result.success(docRef.id)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "teams/$cleanTeamId/tasks/${docRef.id}")
            Result.failure(e)
        }
    }

    suspend fun updateTeamTaskStatus(
        teamId: String,
        taskId: String,
        newStatus: String
    ): Result<Unit> {
        val cleanTeamId = teamId.ifBlank { PLATFORM_MAIN_TEAM_ID }
        val normalized = TeamTaskStatus.normalize(newStatus)
        return try {
            tasksCollection(cleanTeamId).document(taskId).update(
                mapOf(
                    "status" to normalized,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "teams/$cleanTeamId/tasks/$taskId")
            Result.failure(e)
        }
    }

    suspend fun deleteTeamTask(teamId: String, taskId: String): Result<Unit> {
        val cleanTeamId = teamId.ifBlank { PLATFORM_MAIN_TEAM_ID }
        return try {
            tasksCollection(cleanTeamId).document(taskId).delete().await()
            Result.success(Unit)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "teams/$cleanTeamId/tasks/$taskId")
            Result.failure(e)
        }
    }

    // ==========================================
    // ACTIVITY FEED (TEAM SCOPED)
    // ==========================================

    fun observeTeamActivity(teamId: String = PLATFORM_MAIN_TEAM_ID): Flow<List<TeamActivity>> {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val cleanTeamId = teamId.ifBlank { PLATFORM_MAIN_TEAM_ID }

        return combine(
            observeIdeas(teamId = cleanTeamId),
            observeSharedFiles(teamId = cleanTeamId),
            observeAnnouncements(teamId = cleanTeamId),
            observeTeamTasks(teamId = cleanTeamId)
        ) { ideas, files, announcements, tasks ->
            val activities = mutableListOf<TeamActivity>()

            announcements.take(3).forEach { ann ->
                val time = ann.createdAt?.toDate()?.let { timeFormat.format(it) } ?: "Bugün"
                activities.add(
                    TeamActivity(
                        id = "ann_${ann.id}",
                        userName = ann.authorName,
                        userTeam = "Yönetim",
                        actionText = "yeni bir duyuru yayınladı",
                        targetTitle = ann.title,
                        type = "announcement",
                        timeAgo = time
                    )
                )
            }

            tasks.take(3).forEach { task ->
                val time = task.createdAt?.toDate()?.let { timeFormat.format(it) } ?: "Bugün"
                activities.add(
                    TeamActivity(
                        id = "task_${task.id}",
                        userName = task.createdByName,
                        userTeam = task.subTeam,
                        actionText = if (task.status == TeamTaskStatus.DONE) "görevi tamamladı" else "görev oluşturdu",
                        targetTitle = task.title,
                        type = "task",
                        timeAgo = time
                    )
                )
            }

            ideas.take(4).forEach { idea ->
                val time = idea.createdAt?.toDate()?.let { timeFormat.format(it) } ?: "Bugün"
                activities.add(
                    TeamActivity(
                        id = "idea_${idea.id}",
                        userName = idea.authorName,
                        userTeam = idea.authorTeam,
                        actionText = "yeni bir fikir paylaştı",
                        targetTitle = idea.title,
                        type = "idea",
                        timeAgo = time
                    )
                )
            }

            files.take(4).forEach { file ->
                val time = file.createdAt?.toDate()?.let { timeFormat.format(it) } ?: "Bugün"
                activities.add(
                    TeamActivity(
                        id = "file_${file.id}",
                        userName = file.uploadedByName,
                        userTeam = "Çalışma Alanı",
                        actionText = "çalışma dosyası yükledi",
                        targetTitle = file.fileName,
                        type = "file",
                        timeAgo = time
                    )
                )
            }

            activities
        }
    }

    // ==========================================
    // SUPPORT & HELP CENTER TICKETS (CENTRALIZED TO BLACKSEA PLATFORM ADMIN)
    // ==========================================

    @Suppress("UNUSED_PARAMETER")
    fun observeMySupportTickets(
        userId: String,
        teamId: String = PLATFORM_MAIN_TEAM_ID
    ): Flow<List<SupportTicket>> {
        val cleanUid = userId.trim()
        if (cleanUid.isBlank()) return flowOf(emptyList())
        return supportTicketsCollection(PLATFORM_MAIN_TEAM_ID)
            .whereEqualTo("userId", cleanUid)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(SupportTicket::class.java)
                    .sortedByDescending { it.createdAt?.toDate()?.time ?: Long.MAX_VALUE }
            }
            .catch { error ->
                handleFirestoreError(error, OperationType.LIST, "support_tickets")
                emit(emptyList())
            }
    }

    @Suppress("UNUSED_PARAMETER")
    fun observeAllSupportTickets(teamId: String = PLATFORM_MAIN_TEAM_ID): Flow<List<SupportTicket>> {
        return supportTicketsCollection(PLATFORM_MAIN_TEAM_ID)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(SupportTicket::class.java)
                    .sortedWith(
                        compareBy<SupportTicket> {
                            when (SupportTicketStatus.normalize(it.status)) {
                                SupportTicketStatus.OPEN -> 0
                                SupportTicketStatus.IN_PROGRESS -> 1
                                else -> 2
                            }
                        }.thenByDescending { it.createdAt?.toDate()?.time ?: Long.MAX_VALUE }
                    )
            }
            .catch { error ->
                handleFirestoreError(error, OperationType.LIST, "support_tickets")
                emit(emptyList())
            }
    }

    suspend fun submitSupportTicket(
        category: String,
        subject: String,
        description: String,
        userName: String,
        userEmail: String,
        userTeam: String,
        appVersion: String,
        deviceInfo: String,
        teamId: String = PLATFORM_MAIN_TEAM_ID,
        teamName: String = "BlackSea Robotics"
    ): Result<String> {
        val cleanSubject = subject.trim().take(120)
        val cleanDesc = description.trim().take(2000)
        val cleanCategory = category.trim().ifBlank { "Uygulama Hatası" }.take(60)
        if (cleanSubject.length < 3) {
            return Result.failure(IllegalArgumentException("Konu başlığı en az 3 karakter olmalıdır."))
        }
        if (cleanDesc.length < 10) {
            return Result.failure(IllegalArgumentException("Sorun açıklaması en az 10 karakter olmalıdır."))
        }

        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }

        val cleanTeamName = teamName.trim().ifBlank { "BlackSea Robotics" }.take(48)
        val cleanSubTeam = userTeam.trim().ifBlank { "Genel Ekip" }
        val maxSubLen = (80 - cleanTeamName.length - 3).coerceAtLeast(0)
        val formattedTeamContext = if (maxSubLen > 0) {
            "$cleanTeamName • ${cleanSubTeam.take(maxSubLen)}"
        } else {
            cleanTeamName.take(80)
        }
        val docRef = supportTicketsCollection(PLATFORM_MAIN_TEAM_ID).document()
        val cleanName = userName.trim().ifBlank { "Takım Üyesi" }.take(100)
        val payload = mapOf(
            "teamId" to PLATFORM_MAIN_TEAM_ID,
            "userId" to uid,
            "userName" to cleanName,
            "userEmail" to userEmail.trim().take(150),
            "userTeam" to formattedTeamContext,
            "category" to cleanCategory,
            "subject" to cleanSubject,
            "description" to cleanDesc,
            "appVersion" to appVersion.trim().take(40),
            "deviceInfo" to deviceInfo.trim().take(120),
            "status" to SupportTicketStatus.OPEN,
            "adminReply" to "",
            "repliedByName" to "",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            createNotification(
                recipientUserId = "platform_admins",
                title = "Yeni Destek Talebi ($cleanCategory)",
                message = "$cleanName ($formattedTeamContext): $cleanSubject",
                type = "support_ticket",
                targetId = docRef.id,
                targetRoomId = cleanCategory,
                senderName = cleanName,
                senderId = uid,
                teamId = "platform_admin",
                teamName = "BlackSea Robotics Platform"
            )
            Result.success(docRef.id)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "support_tickets/${docRef.id}")
            Result.failure(e)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    suspend fun replyAndUpdateSupportTicket(
        ticketId: String,
        status: String,
        adminReply: String,
        repliedByName: String,
        targetUserId: String,
        teamId: String = PLATFORM_MAIN_TEAM_ID,
        teamName: String = "BlackSea Robotics"
    ): Result<Unit> {
        val cleanId = ticketId.trim()
        if (cleanId.isBlank()) {
            return Result.failure(IllegalArgumentException("Destek talebi bulunamadı."))
        }
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val normalizedStatus = SupportTicketStatus.normalize(status)
        val cleanReply = adminReply.trim().take(2000)
        val cleanReplier = repliedByName.trim().ifBlank { "Yönetici" }.take(100)

        val updates = mapOf(
            "status" to normalizedStatus,
            "adminReply" to cleanReply,
            "repliedByName" to cleanReplier,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            supportTicketsCollection(PLATFORM_MAIN_TEAM_ID).document(cleanId).update(updates).await()
            if (targetUserId.isNotBlank() && targetUserId != uid) {
                val statusLabel = SupportTicketStatus.displayName(normalizedStatus)
                val body = if (cleanReply.isNotBlank()) {
                    "Durum: $statusLabel • ${cleanReply.take(80)}"
                } else {
                    "Destek talebinizin durumu '$statusLabel' olarak güncellendi."
                }
                createNotification(
                    recipientUserId = targetUserId,
                    title = "Destek Talebiniz Güncellendi",
                    message = body,
                    type = "support_reply",
                    targetId = cleanId,
                    senderName = cleanReplier,
                    senderId = uid,
                    teamId = "platform_admin",
                    teamName = "BlackSea Robotics"
                )
            }
            Result.success(Unit)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "support_tickets/$cleanId")
            Result.failure(e)
        }
    }
}
