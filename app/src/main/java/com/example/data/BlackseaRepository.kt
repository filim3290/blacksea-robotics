package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Base64
import com.example.util.logDebugError
import com.example.util.logDebugWarn
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import com.example.R
import com.example.model.ChatMessage
import com.example.model.ChatRoom
import com.example.model.IdeaComment
import com.example.model.IdeaItem
import com.example.model.IdeaStatus
import com.example.model.PredefinedRoles
import com.example.model.SharedProjectFile
import com.example.model.SupportTicket
import com.example.model.SupportTicketStatus
import com.example.model.TeamActivity
import com.example.model.TeamAnnouncement
import com.example.model.TeamNotification
import com.example.model.UserProfile
import com.example.model.UserStatus
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

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
 * Determines whether [userId] should see and be notified about [notif].
 * Rules:
 * 1. Never notify a user for actions/messages/files/ideas/broadcasts they initiated themselves.
 * 2. Historical team/room notifications created before [userCreatedAtMs] are hidden from new members.
 * 3. Direct personal notifications & manager-only notifications follow recipientUserId.
 * 4. Broadcast/announcement, update, idea, and new_member notifications are delivered team-wide.
 * 5. Message and file notifications are filtered strictly by general room or user's own team
 *    (managers see all rooms except their own sent content).
 */
fun shouldUserReceiveNotification(
    notif: TeamNotification,
    userId: String,
    isManager: Boolean,
    userTeam: String = "",
    userCreatedAtMs: Long = 0L
): Boolean {
    // 1. Exclude sender from receiving notifications for their own content/actions
    if (userId.isNotBlank() && notif.senderId.isNotBlank() && notif.senderId == userId) {
        return false
    }

    // 2. Direct notification to this specific user (still respects senderId check above)
    if (userId.isNotBlank() && notif.recipientUserId == userId) {
        return true
    }

    // 3. New users should not see historical shared notifications created before they joined
    if (userCreatedAtMs > 0L) {
        val notifTimeMs = notif.createdAt?.toDate()?.time
        if (notifTimeMs != null && notifTimeMs < (userCreatedAtMs - 5_000L)) {
            return false
        }
    }

        // Manager-only notifications (e.g. member_pending, support_ticket)
        if (notif.recipientUserId == "managers") {
            return isManager
        }

        return when (notif.type.lowercase()) {
            // App version update notifications
            "update" -> true

            // Manager custom broadcasts & announcements
            "broadcast", "announcement" -> true

            // New idea or idea status update
            "idea" -> true

            // New team member joined notification (do not alert the joining member themselves)
            "new_member" -> userId.isBlank() || notif.targetId != userId

            // Support tickets are for managers; support replies are direct to userId (handled above)
            "support_ticket" -> isManager
            "support_reply" -> userId.isNotBlank() && notif.recipientUserId == userId

            // Chat room messages & shared files: own team, management team, or general team only (managers see all rooms)
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
        } catch (e: Exception) {
            try {
                FirebaseStorage.getInstance("gs://gen-lang-client-0922417463.appspot.com")
            } catch (e2: Exception) {
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
            } catch (e: Exception) {
                // Ignore
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

        // Ensure extension is preserved when Android Photo Picker returns a numeric display name
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

        // 1. Text & Code files: read text directly for in-app viewing
        if (fileType == "code" || fileType == "text" || name.endsWith(".json", ignoreCase = true)) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    textContent = stream.bufferedReader().readText().take(150_000)
                }
            } catch (e: Exception) {
                logDebugWarn("Repository", "Could not read text content", e)
            }
        }

        // 2. Images: compress for mobile optimization (typically becomes 80-150 KB)
        var compressedImageBytes: ByteArray? = null
        if (fileType == "image") {
            compressedImageBytes = compressImage(context, uri)
        }

        // 3. Videos: extract thumbnail poster & duration
        var videoThumbnailDataUri = ""
        var mediaDurationMs = 0L
        if (fileType == "video") {
            val (thumb, duration) = extractVideoMetadata(context, uri)
            videoThumbnailDataUri = thumb
            mediaDurationMs = duration
        }

        // 4. Cache file locally so it can always be opened / previewed on uploader device
        val localSavedFile = saveFileLocally(context, uri, safeName)
        if (localSavedFile != null) {
            storagePath = "local:${localSavedFile.absolutePath}"
        }

        // 5. Attempt Cloud Storage upload if available
        val storageInstances = listOf(
            try { FirebaseStorage.getInstance("gs://gen-lang-client-0922417463.firebasestorage.app") } catch (e: Exception) { null },
            try { FirebaseStorage.getInstance("gs://gen-lang-client-0922417463.appspot.com") } catch (e: Exception) { null },
            try { FirebaseStorage.getInstance() } catch (e: Exception) { null }
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

        // 6. Resilient Fallbacks if Cloud Storage is unavailable or unprovisioned:
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
            try { FirebaseStorage.getInstance("gs://gen-lang-client-0922417463.firebasestorage.app") } catch (e: Exception) { null },
            try { FirebaseStorage.getInstance("gs://gen-lang-client-0922417463.appspot.com") } catch (e: Exception) { null },
            try { FirebaseStorage.getInstance() } catch (e: Exception) { null }
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

    // ==========================================
    // USER PROFILES
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

    suspend fun isSystemEmptyForNewUser(excludeUserId: String = ""): Boolean {
        return try {
            val serverSnap = try {
                db.collection("users").limit(2).get(Source.SERVER).await()
            } catch (_: Exception) {
                db.collection("users").limit(2).get().await()
            }
            serverSnap.documents.none { it.id != excludeUserId }
        } catch (e: Exception) {
            logDebugWarn("Repository", "Could not verify whether users collection is empty", e)
            false
        }
    }

    suspend fun createOrUpdateUserProfile(
        userId: String,
        firstName: String,
        lastName: String,
        username: String,
        email: String,
        team: String,
        roles: List<String>,
        photoUrl: String
    ): UserProfile {
        val path = "users/$userId"
        val docRef = db.collection("users").document(userId)

        // Check if this is the first user in the system or the configured admin email
        val isFirstOrConfiguredAdmin = email.equals("filim3290@gmail.com", ignoreCase = true) ||
            isSystemEmptyForNewUser(excludeUserId = userId)

        val finalRoles = if (isFirstOrConfiguredAdmin) {
            (roles + listOf("Yönetici", "Takım Kaptanı")).distinct()
        } else {
            if (roles.isNotEmpty()) roles else listOf(PredefinedRoles.getDefaultTeamRole(team))
        }

        // Admins are automatically ACTIVE; other new users are PENDING until approved
        val finalStatus = if (isFirstOrConfiguredAdmin) UserStatus.ACTIVE.value else UserStatus.PENDING.value
        val formattedDisplayName = "$firstName $lastName".trim().ifBlank { username.ifBlank { email.substringBefore("@") } }

        val existingSnap = try {
            docRef.get().await()
        } catch (_: Exception) {
            null
        }
        val shouldSetCreatedAt = existingSnap == null || !existingSnap.exists() || existingSnap.get("createdAt") == null

        val payload = buildMap<String, Any> {
            put("userId", userId)
            put("firstName", firstName.trim())
            put("lastName", lastName.trim())
            put("username", username.trim())
            put("displayName", formattedDisplayName)
            put("email", email)
            put("team", team)
            put("role", finalRoles.firstOrNull() ?: "Takım Üyesi")
            put("roles", finalRoles)
            put("status", finalStatus)
            put("photoUrl", photoUrl)
            put("isSuperAdmin", isFirstOrConfiguredAdmin)
            if (shouldSetCreatedAt) {
                put("createdAt", FieldValue.serverTimestamp())
            }
            put("updatedAt", FieldValue.serverTimestamp())
        }

        try {
            docRef.set(payload, SetOptions.merge()).await()

            // If new user is pending, send real-time notification to managers
            if (finalStatus == UserStatus.PENDING.value) {
                createNotification(
                    recipientUserId = "managers",
                    title = "Yeni Üye Başvurusu",
                    message = "$formattedDisplayName ($team) ekibe katılmak için onay bekliyor.",
                    type = "member_pending",
                    targetId = userId,
                    targetRoomId = team,
                    senderName = formattedDisplayName,
                    senderId = userId
                )
            }

            return UserProfile(
                id = userId,
                userId = userId,
                firstName = firstName.trim(),
                lastName = lastName.trim(),
                username = username.trim(),
                displayName = formattedDisplayName,
                email = email,
                team = team,
                role = finalRoles.firstOrNull() ?: "Takım Üyesi",
                roles = finalRoles,
                status = finalStatus,
                photoUrl = photoUrl,
                isSuperAdmin = isFirstOrConfiguredAdmin
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            throw e
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

    fun observeAllUsers(): Flow<List<UserProfile>> = db.collection("users")
        .snapshots()
        .map { snapshot -> snapshot.toObjects(UserProfile::class.java) }
        .catch { error ->
            handleFirestoreError(error, OperationType.LIST, "users")
            emit(emptyList())
        }

    suspend fun updateUserRolesAndTeam(
        targetUserId: String,
        team: String,
        roles: List<String>,
        status: String
    ) {
        val cleanId = targetUserId.trim()
        if (cleanId.isBlank()) return
        val path = "users/$cleanId"
        val docRef = db.collection("users").document(cleanId)
        val payload = mapOf(
            "team" to team,
            "roles" to roles,
            "role" to (roles.firstOrNull() ?: "Takım Üyesi"),
            "status" to status,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            val existingSnap = try {
                docRef.get().await()
            } catch (_: Exception) {
                null
            }
            val previousStatus = existingSnap?.getString("status")
            val memberProfile = existingSnap?.toObject(UserProfile::class.java)
            val memberName = memberProfile?.getFullName()?.takeIf { it.isNotBlank() } ?: "Yeni Üye"
            val transitionedToActive = !UserStatus.isActive(previousStatus) && UserStatus.isActive(status)

            docRef.set(payload, SetOptions.merge()).await()

            // Notify both the approved member and the team ONLY when transitioning into active
            if (transitionedToActive) {
                val approverId = auth.currentUser?.uid ?: ""
                createNotification(
                    recipientUserId = cleanId,
                    title = "Üyeliğiniz Onaylandı",
                    message = "Blacksea Robotics üyeliğiniz yönetici tarafından onaylandı. Takım alanlarına hoş geldiniz!",
                    type = "member_approved",
                    targetId = cleanId,
                    senderId = approverId
                )
                createNotification(
                    recipientUserId = "all",
                    title = "Yeni Takım Üyesi",
                    message = "$memberName ($team) ekibe katıldı!",
                    type = "new_member",
                    targetId = cleanId,
                    targetRoomId = team,
                    senderName = memberName,
                    senderId = approverId
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            throw e
        }
    }

    suspend fun revokeUserAccess(targetUserId: String) {
        val cleanId = targetUserId.trim()
        if (cleanId.isBlank()) return
        val currentUid = auth.currentUser?.uid
        if (cleanId == currentUid) {
            throw IllegalArgumentException("Kendi erişiminizi kaldıramazsınız.")
        }
        val path = "users/$cleanId"
        val docRef = db.collection("users").document(cleanId)
        try {
            val existingSnap = try { docRef.get().await() } catch (_: Exception) { null }
            val targetEmail = existingSnap?.getString("email").orEmpty()
            if (targetEmail.equals("filim3290@gmail.com", ignoreCase = true)) {
                throw IllegalArgumentException("Kurucu yönetici hesabı korunmaktadır.")
            }
            docRef.update(
                mapOf(
                    "status" to UserStatus.REVOKED.value,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            createNotification(
                recipientUserId = cleanId,
                title = "Erişiminiz Kaldırıldı",
                message = "Blacksea Robotics uygulama erişiminiz yönetici tarafından kaldırıldı.",
                type = "access_revoked",
                targetId = cleanId,
                senderId = currentUid ?: ""
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            throw e
        }
    }

    suspend fun deleteUserAccount(targetUserId: String) {
        val cleanId = targetUserId.trim()
        if (cleanId.isBlank()) return
        val currentUid = auth.currentUser?.uid
        if (cleanId == currentUid) {
            throw IllegalArgumentException("Kendi hesabınızı silemezsiniz.")
        }
        val path = "users/$cleanId"
        val docRef = db.collection("users").document(cleanId)
        try {
            val existingSnap = try { docRef.get().await() } catch (_: Exception) { null }
            val targetEmail = existingSnap?.getString("email").orEmpty()
            if (targetEmail.equals("filim3290@gmail.com", ignoreCase = true)) {
                throw IllegalArgumentException("Kurucu yönetici hesabı silinemez.")
            }
            docRef.delete().await()

            // Best-effort cleanup of personal notifications addressed directly to deleted user
            try {
                val personalNotifs = db.collection("notifications")
                    .whereEqualTo("recipientUserId", cleanId)
                    .get()
                    .await()
                if (!personalNotifs.isEmpty) {
                    val batch = db.batch()
                    personalNotifs.documents.forEach { batch.delete(it.reference) }
                    batch.commit().await()
                }
            } catch (cleanupEx: Exception) {
                logDebugWarn("Repository", "Personal notification cleanup skipped", cleanupEx)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            throw e
        }
    }

    suspend fun deleteOwnAccount() {
        val uid = requireUserId()
        val path = "users/$uid"
        try {
            // 1. Best-effort cleanup of notifications directly addressed to this user
            try {
                val personalNotifs = db.collection("notifications")
                    .whereEqualTo("recipientUserId", uid)
                    .get()
                    .await()
                if (!personalNotifs.isEmpty) {
                    val batch = db.batch()
                    personalNotifs.documents.forEach { batch.delete(it.reference) }
                    batch.commit().await()
                }
            } catch (cleanupEx: Exception) {
                logDebugWarn("Repository", "Self notification cleanup skipped", cleanupEx)
            }

            // 2. Permanently delete Firestore user profile document
            db.collection("users").document(uid).delete().await()

            // 3. Best-effort Firebase Auth user deletion (may throw recent-login-required on old sessions)
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
    // NOTIFICATIONS
    // ==========================================

    fun observeNotifications(
        userId: String,
        isManager: Boolean,
        userTeam: String = "",
        userCreatedAtMs: Long = 0L
    ): Flow<List<TeamNotification>> {
        return db.collection("notifications")
            .snapshots()
            .map { snapshot ->
                val all = snapshot.toObjects(TeamNotification::class.java)
                val filtered = all.filter { notif ->
                    shouldUserReceiveNotification(
                        notif = notif,
                        userId = userId,
                        isManager = isManager,
                        userTeam = userTeam,
                        userCreatedAtMs = userCreatedAtMs
                    )
                }
                filtered.sortedByDescending { it.createdAt?.toDate()?.time ?: Long.MAX_VALUE }
            }
            .catch { error ->
                handleFirestoreError(error, OperationType.LIST, "notifications")
                emit(emptyList())
            }
    }

    suspend fun fetchNotificationsOnce(
        userId: String,
        isManager: Boolean,
        userTeam: String = "",
        userCreatedAtMs: Long = 0L
    ): List<TeamNotification>? {
        return try {
            val snapshot = try {
                db.collection("notifications").get(Source.SERVER).await()
            } catch (_: Exception) {
                db.collection("notifications").get().await()
            }
            snapshot.toObjects(TeamNotification::class.java).filter { notif ->
                shouldUserReceiveNotification(
                    notif = notif,
                    userId = userId,
                    isManager = isManager,
                    userTeam = userTeam,
                    userCreatedAtMs = userCreatedAtMs
                )
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            logDebugWarn("Repository", "fetchNotificationsOnce failed", e)
            null
        }
    }

    suspend fun sendBroadcastNotification(
        title: String,
        message: String,
        senderName: String
    ) {
        val uid = auth.currentUser?.uid ?: ""
        // Creates a broadcast notification for all team members
        createNotification(
            recipientUserId = "all",
            title = title,
            message = message,
            type = "broadcast",
            senderName = senderName,
            senderId = uid
        )
        // Also register as a team announcement so it is visible in the activity feed (without sending a duplicate notification)
        try {
            addAnnouncement(
                title = title,
                content = message,
                authorName = senderName,
                isPinned = false,
                sendNotification = false
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
        senderId: String = auth.currentUser?.uid ?: ""
    ) {
        val docRef = db.collection("notifications").document()
        val payload = mapOf(
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
        } catch (firstErr: Exception) {
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

    suspend fun markAllNotificationsAsRead(userId: String, notificationIds: List<String>, personalNotificationIds: Set<String> = emptySet()) {
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

    suspend fun reapplyForMembership(userId: String, selectedTeam: String = "") {
        val updates = mutableMapOf<String, Any>(
            "status" to UserStatus.PENDING.value,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (selectedTeam.isNotBlank()) {
            updates["team"] = selectedTeam
        }
        db.collection("users").document(userId).update(updates).await()

        val profile = getUserProfile(userId)
        val applicantTeam = selectedTeam.ifBlank { profile?.team ?: "Genel Ekip" }
        val applicantName = profile?.getFullName()?.ifBlank { "Üye" } ?: "Üye"
        createNotification(
            recipientUserId = "managers",
            title = "Yeni Üye Başvurusu",
            message = "$applicantName ($applicantTeam) yeniden onay başvurusunda bulundu.",
            type = "member_pending",
            targetId = userId,
            targetRoomId = applicantTeam,
            senderName = applicantName,
            senderId = userId
        )
    }

    // ==========================================
    // ROOMS
    // ==========================================

    suspend fun ensureDefaultRoomsExist() {
        val defaultRooms = listOf(
            ChatRoom(
                roomId = "general",
                name = "GENEL EKİP ODASI",
                description = "Tüm takım üyelerinin ortak iletişim ve çalışma alanı",
                allowedTeam = "all"
            ),
            ChatRoom(
                roomId = "software",
                name = "YAZILIM EKİBİ ODASI",
                description = "Otonom sürüş, robot kontrolü, sensörler ve yapay zeka",
                allowedTeam = "all"
            ),
            ChatRoom(
                roomId = "mechanic",
                name = "MEKANİK EKİBİ ODASI",
                description = "Şasi, tasarım, 3D baskı, montaj ve robot mekaniği",
                allowedTeam = "all"
            ),
            ChatRoom(
                roomId = "pr",
                name = "PR EKİBİ ODASI",
                description = "Sosyal medya, sponsorluk, etkinlik ve tanıtım",
                allowedTeam = "all"
            )
        )

        for (room in defaultRooms) {
            val docRef = db.collection("rooms").document(room.roomId)
            try {
                val snapshot = docRef.get().await()
                if (!snapshot.exists()) {
                    docRef.set(
                        mapOf(
                            "roomId" to room.roomId,
                            "name" to room.name,
                            "description" to room.description,
                            "allowedTeam" to room.allowedTeam,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.WRITE, "rooms/${room.roomId}")
            }
        }
    }

    fun observeRooms(): Flow<List<ChatRoom>> = db.collection("rooms")
        .snapshots()
        .map { snapshot -> snapshot.toObjects(ChatRoom::class.java) }
        .catch { error ->
            handleFirestoreError(error, OperationType.LIST, "rooms")
            emit(emptyList())
        }

    // ==========================================
    // MESSAGES
    // ==========================================

    fun observeMessages(roomId: String): Flow<List<ChatMessage>> {
        val path = "rooms/$roomId/messages"
        return db.collection("rooms").document(roomId).collection("messages")
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
        replyToText: String = ""
    ) {
        val uid = requireUserId()
        val path = "rooms/$roomId/messages"
        val docRef = db.collection("rooms").document(roomId).collection("messages").document()
        val safeFileUrl = if (fileUrl.startsWith("data:") && fileUrl.length > 500_000) "" else fileUrl
        val safeThumbUrl = if (thumbnailUrl.startsWith("data:") && thumbnailUrl.length > 350_000) "" else thumbnailUrl
        val payload = mapOf(
            "roomId" to roomId,
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

            // Notification for team members (filtered by room / team in observeNotifications)
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
                senderId = uid
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            throw e
        }
    }

    suspend fun togglePinMessage(roomId: String, messageId: String, isPinned: Boolean) {
        val path = "rooms/$roomId/messages/$messageId"
        val docRef = db.collection("rooms").document(roomId).collection("messages").document(messageId)
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

    suspend fun editMessage(roomId: String, messageId: String, newText: String) {
        val uid = requireUserId()
        val trimmed = newText.trim()
        require(trimmed.isNotBlank()) { "Mesaj metni boş olamaz." }
        val path = "rooms/$roomId/messages/$messageId"
        val docRef = db.collection("rooms").document(roomId).collection("messages").document(messageId)
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

    suspend fun deleteMessage(roomId: String, messageId: String) {
        val path = "rooms/$roomId/messages/$messageId"
        val docRef = db.collection("rooms").document(roomId).collection("messages").document(messageId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            throw e
        }
    }

    suspend fun deleteMessages(roomId: String, messageIds: List<String>) {
        requireUserId()
        val cleanIds = messageIds.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (cleanIds.isEmpty()) return
        val path = "rooms/$roomId/messages"
        val messagesCol = db.collection("rooms").document(roomId).collection("messages")
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
    // IDEAS
    // ==========================================

    fun observeIdeas(roomId: String? = null): Flow<List<IdeaItem>> {
        val path = "ideas"
        return db.collection("ideas")
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
        mediaDurationMs: Long = 0L
    ) {
        val uid = requireUserId()
        val docRef = db.collection("ideas").document()
        val safeFileUrl = if (fileUrl.startsWith("data:") && fileUrl.length > 500_000) "" else fileUrl
        val safeThumbUrl = if (thumbnailUrl.startsWith("data:") && thumbnailUrl.length > 350_000) "" else thumbnailUrl
        val payload = mapOf(
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

            // Broadcast notification for new idea
            createNotification(
                recipientUserId = "all",
                title = "Yeni fikir",
                message = "$authorName yeni bir fikir ekledi: \"$title\"",
                type = "idea",
                targetId = docRef.id,
                targetRoomId = roomId,
                senderName = authorName,
                senderId = uid
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "ideas")
            throw e
        }
    }

    suspend fun toggleLikeIdea(ideaId: String, currentLikes: List<String>): Boolean {
        val uid = requireUserId()
        val docRef = db.collection("ideas").document(ideaId)
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
            handleFirestoreError(e, OperationType.UPDATE, "ideas/$ideaId")
            throw e
        }
    }

    suspend fun updateIdeaCategory(ideaId: String, newCategory: String) {
        val docRef = db.collection("ideas").document(ideaId)
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
                    senderId = auth.currentUser?.uid ?: ""
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "ideas/$ideaId")
            throw e
        }
    }

    suspend fun deleteIdea(ideaId: String) {
        val docRef = db.collection("ideas").document(ideaId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "ideas/$ideaId")
            throw e
        }
    }

    // ==========================================
    // COMMENTS
    // ==========================================

    fun observeIdeaComments(ideaId: String): Flow<List<IdeaComment>> {
        val path = "ideas/$ideaId/comments"
        return db.collection("ideas").document(ideaId).collection("comments")
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
        authorRoles: List<String> = emptyList()
    ) {
        val uid = requireUserId()
        val docRef = db.collection("ideas").document(ideaId).collection("comments").document()
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

            // Notify idea author
            try {
                val ideaDoc = db.collection("ideas").document(ideaId).get().await()
                val ideaAuthorId = ideaDoc.getString("authorId")
                if (!ideaAuthorId.isNullOrBlank() && ideaAuthorId != uid) {
                    createNotification(
                        recipientUserId = ideaAuthorId,
                        title = "Yeni yorum",
                        message = "$authorName fikrinize yorum yaptı: \"${text.take(40)}\"",
                        type = "comment",
                        targetId = ideaId,
                        senderName = authorName,
                        senderId = uid
                    )
                }
            } catch (e: Exception) {
                logDebugWarn("Repository", "Could not notify idea author", e)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "ideas/$ideaId/comments")
            throw e
        }
    }

    suspend fun deleteIdeaComment(ideaId: String, commentId: String) {
        val docRef = db.collection("ideas").document(ideaId).collection("comments").document(commentId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "ideas/$ideaId/comments/$commentId")
            throw e
        }
    }

    // ==========================================
    // WORKSPACE FILES
    // ==========================================

    fun observeSharedFiles(roomId: String? = null): Flow<List<SharedProjectFile>> {
        return db.collection("files")
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
                handleFirestoreError(error, OperationType.LIST, "files")
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
        notifyTeam: Boolean = false
    ) {
        val uid = requireUserId()
        val docRef = db.collection("files").document()
        val safeContent = content.take(200_000)
        val safeDownloadUrl = if (downloadUrl.startsWith("data:") && downloadUrl.length > 500_000) "" else downloadUrl
        val safeThumbUrl = if (thumbnailUrl.startsWith("data:") && thumbnailUrl.length > 350_000) "" else thumbnailUrl
        val payload = mapOf(
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
                    senderId = uid
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "files")
            throw e
        }
    }

    suspend fun deleteSharedFile(fileId: String) {
        val docRef = db.collection("files").document(fileId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "files/$fileId")
            throw e
        }
    }

    // ==========================================
    // ANNOUNCEMENTS
    // ==========================================

    fun observeAnnouncements(): Flow<List<TeamAnnouncement>> {
        return db.collection("announcements")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(TeamAnnouncement::class.java)
                    .sortedWith(
                        compareByDescending<TeamAnnouncement> { it.isPinned }
                            .thenByDescending { it.createdAt?.toDate()?.time ?: Long.MAX_VALUE }
                    )
            }
            .catch { error ->
                handleFirestoreError(error, OperationType.LIST, "announcements")
                emit(emptyList())
            }
    }

    suspend fun addAnnouncement(
        title: String,
        content: String,
        authorName: String,
        isPinned: Boolean,
        sendNotification: Boolean = true
    ) {
        val uid = requireUserId()
        val docRef = db.collection("announcements").document()
        val payload = mapOf(
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
                    senderId = uid
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "announcements")
            throw e
        }
    }

    suspend fun deleteAnnouncement(announcementId: String) {
        val docRef = db.collection("announcements").document(announcementId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "announcements/$announcementId")
            throw e
        }
    }

    // ==========================================
    // ACTIVITY FEED ("Bugün takımda neler oldu?")
    // ==========================================

    fun observeTeamActivity(): Flow<List<TeamActivity>> {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        return combine(
            observeIdeas(),
            observeSharedFiles(),
            observeAnnouncements()
        ) { ideas, files, announcements ->
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
    // SUPPORT & HELP CENTER TICKETS
    // ==========================================

    fun observeMySupportTickets(userId: String): Flow<List<SupportTicket>> {
        val cleanUid = userId.trim()
        if (cleanUid.isBlank()) return flowOf(emptyList())
        return db.collection("support_tickets")
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

    fun observeAllSupportTickets(): Flow<List<SupportTicket>> {
        return db.collection("support_tickets")
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
        deviceInfo: String
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

        val docRef = db.collection("support_tickets").document()
        val cleanName = userName.trim().ifBlank { "Takım Üyesi" }.take(100)
        val payload = mapOf(
            "userId" to uid,
            "userName" to cleanName,
            "userEmail" to userEmail.trim().take(150),
            "userTeam" to userTeam.trim().ifBlank { "Genel Ekip" }.take(80),
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
                recipientUserId = "managers",
                title = "Yeni Destek Talebi ($cleanCategory)",
                message = "$cleanName: $cleanSubject",
                type = "support_ticket",
                targetId = docRef.id,
                targetRoomId = cleanCategory,
                senderName = cleanName,
                senderId = uid
            )
            Result.success(docRef.id)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "support_tickets/${docRef.id}")
            Result.failure(e)
        }
    }

    suspend fun replyAndUpdateSupportTicket(
        ticketId: String,
        status: String,
        adminReply: String,
        repliedByName: String,
        targetUserId: String
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
            db.collection("support_tickets").document(cleanId).update(updates).await()
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
                    senderId = uid
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

