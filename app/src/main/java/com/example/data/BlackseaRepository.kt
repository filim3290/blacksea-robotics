package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Base64
import android.util.Log
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
import com.google.firebase.firestore.snapshots
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
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
    val textContent: String = ""
)

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
            ?: throw IllegalStateException("User must be signed in with Google to access Firestore.")
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
            Log.w("Repository", "Could not query file metadata: ${e.message}")
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
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val bitmap = BitmapFactory.decodeStream(inputStream) ?: return null
            inputStream.close()

            val w = bitmap.width
            val h = bitmap.height
            val scale = if (w > maxDimension || h > maxDimension) {
                val maxSide = maxOf(w, h)
                maxDimension.toFloat() / maxSide
            } else 1.0f

            val scaledBitmap = if (scale < 1.0f) {
                Bitmap.createScaledBitmap(
                    bitmap,
                    (w * scale).toInt().coerceAtLeast(1),
                    (h * scale).toInt().coerceAtLeast(1),
                    true
                )
            } else bitmap

            val out = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            out.toByteArray()
        } catch (e: Exception) {
            Log.w("Repository", "Image compression failed: ${e.message}")
            null
        }
    }

    fun extractVideoThumbnail(context: Context, uri: Uri): String {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val frame = retriever.getFrameAtTime(1_000_000) ?: retriever.frameAtTime
            retriever.release()
            if (frame != null) {
                val scaled = Bitmap.createScaledBitmap(frame, 320, (320f * frame.height / frame.width).toInt().coerceAtLeast(1), true)
                val stream = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, 70, stream)
                val base64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
                "data:image/jpeg;base64,$base64"
            } else ""
        } catch (e: Exception) {
            Log.w("Repository", "Video thumbnail extraction failed: ${e.message}")
            ""
        }
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
            Log.w("Repository", "Could not cache file locally: ${e.message}")
            null
        }
    }

    fun detectFileType(fileName: String): String {
        val ext = fileName.substringAfterLast(".", "").lowercase()
        return when (ext) {
            "png", "jpg", "jpeg", "webp", "gif", "bmp", "svg" -> "image"
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
    }

    suspend fun uploadFile(
        context: Context,
        uri: Uri,
        folder: String = "uploads",
        onProgress: (Int) -> Unit = {}
    ): FileUploadResult = withContext(Dispatchers.IO) {
        val (name, size) = queryFileMetadata(context, uri)
        val fileType = detectFileType(name)
        val safeName = name.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        var downloadUrl = ""
        var textContent = ""
        var storagePath = ""

        // 1. Text & Code files: read text directly for in-app viewing
        if (fileType == "code" || fileType == "text" || name.endsWith(".json")) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    textContent = stream.bufferedReader().readText().take(150_000)
                }
            } catch (e: Exception) {
                Log.w("Repository", "Could not read text content: ${e.message}")
            }
        }

        // 2. Images: compress for mobile optimization (typically becomes 80-150 KB)
        var compressedImageBytes: ByteArray? = null
        if (fileType == "image") {
            compressedImageBytes = compressImage(context, uri)
        }

        // 3. Videos: extract thumbnail poster
        var videoThumbnailDataUri = ""
        if (fileType == "video") {
            videoThumbnailDataUri = extractVideoThumbnail(context, uri)
        }

        // 4. Cache file locally so it can always be opened / previewed on device
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
                Log.w("Repository", "Storage bucket upload skipped: ${e.message}")
            }
        }

        // 6. Resilient Fallbacks if Cloud Storage is unavailable or unprovisioned:
        if (downloadUrl.isBlank()) {
            if (compressedImageBytes != null && compressedImageBytes.isNotEmpty()) {
                // Images: Use compressed JPEG Base64 (~80-160KB, crystal clear & fits safely in Firestore)
                val base64Str = Base64.encodeToString(compressedImageBytes, Base64.NO_WRAP)
                downloadUrl = "data:image/jpeg;base64,$base64Str"
            } else if (fileType == "video") {
                // Videos: Use extracted thumbnail data URI for instant video preview card
                downloadUrl = videoThumbnailDataUri
                if (textContent.isBlank()) {
                    val mb = if (size > 0) "${size / (1024 * 1024)} MB" else "Video"
                    textContent = "Video: $name ($mb)"
                }
            } else if (fileType == "audio" || size in 1..(350 * 1024L)) {
                // Audio & small files under 350KB: Safe Base64
                try {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    if (bytes != null && bytes.size <= 350 * 1024) {
                        val mime = context.contentResolver.getType(uri) ?: when (fileType) {
                            "audio" -> "audio/m4a"
                            "pdf" -> "application/pdf"
                            else -> "application/octet-stream"
                        }
                        val base64Str = Base64.encodeToString(bytes, Base64.NO_WRAP)
                        downloadUrl = "data:$mime;base64,$base64Str"
                    }
                } catch (readEx: Exception) {
                    Log.w("Repository", "Base64 encoding fallback: ${readEx.message}")
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
            textContent = textContent
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
                Log.w("Repository", "uploadLocalFile Storage bucket attempt: ${e.message}")
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
                Log.e("Repository", "uploadLocalFile Base64 fallback failed: ${readEx.message}")
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
            Log.w("Repository", "Failed to get user profile: ${e.message}")
            null
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
        val isFirstOrConfiguredAdmin = try {
            val existingUsers = db.collection("users").limit(1).get().await()
            existingUsers.isEmpty || email.equals("filim3290@gmail.com", ignoreCase = true)
        } catch (e: Exception) {
            email.equals("filim3290@gmail.com", ignoreCase = true)
        }

        val finalRoles = if (isFirstOrConfiguredAdmin) {
            (roles + listOf("Yönetici", "Takım Kaptanı")).distinct()
        } else {
            if (roles.isNotEmpty()) roles else listOf(PredefinedRoles.getDefaultTeamRole(team))
        }

        // Admins are automatically ACTIVE; other new users are PENDING until approved
        val finalStatus = if (isFirstOrConfiguredAdmin) UserStatus.ACTIVE.value else UserStatus.PENDING.value
        val formattedDisplayName = "$firstName $lastName".trim().ifBlank { username.ifBlank { email.substringBefore("@") } }

        val payload = mapOf(
            "userId" to userId,
            "firstName" to firstName.trim(),
            "lastName" to lastName.trim(),
            "username" to username.trim(),
            "displayName" to formattedDisplayName,
            "email" to email,
            "team" to team,
            "role" to (finalRoles.firstOrNull() ?: "Takım Üyesi"),
            "roles" to finalRoles,
            "status" to finalStatus,
            "photoUrl" to photoUrl,
            "isSuperAdmin" to isFirstOrConfiguredAdmin,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        try {
            docRef.set(payload, SetOptions.merge()).await()

            // If new user is pending, send notification to managers
            if (finalStatus == UserStatus.PENDING.value) {
                createNotification(
                    recipientUserId = "managers",
                    title = "Yeni üye onayı",
                    message = "$formattedDisplayName Blacksea Robotics'e katılmak için kayıt oldu.",
                    type = "member_pending",
                    targetId = userId,
                    senderName = formattedDisplayName
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
            docRef.set(payload, SetOptions.merge()).await()

            // If user is approved, notify them
            if (UserStatus.isActive(status)) {
                createNotification(
                    recipientUserId = cleanId,
                    title = "Üyeliğiniz Onaylandı",
                    message = "Blacksea Robotics üyeliğiniz yönetici tarafından onaylandı. Takım alanlarına hoş geldiniz!",
                    type = "member_approved",
                    targetId = cleanId
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            throw e
        }
    }

    // ==========================================
    // NOTIFICATIONS
    // ==========================================

    fun observeNotifications(
        userId: String,
        isManager: Boolean,
        userTeam: String = ""
    ): Flow<List<TeamNotification>> {
        return db.collection("notifications")
            .snapshots()
            .map { snapshot ->
                val all = snapshot.toObjects(TeamNotification::class.java)
                val filtered = all.filter { notif ->
                    // 1. Direct notification to this specific user
                    if (notif.recipientUserId == userId) return@filter true

                    // 2. Manager-only notifications
                    if (notif.recipientUserId == "managers") return@filter isManager

                    // 3. Broadcast notifications sent to everyone
                    if (notif.type == "broadcast" || notif.type == "announcement") return@filter true

                    // 4. Room message notifications: filtered strictly by team
                    // e.g. A user in PR will not receive notifications for software room messages,
                    // but will receive general and PR room messages! Managers receive all room notifications.
                    if (notif.type == "message") {
                        if (isManager) return@filter true
                        val rId = notif.targetRoomId.lowercase()
                        val teamLower = userTeam.lowercase()
                        when {
                            rId == "general" || rId.isBlank() -> true
                            rId == "software" -> teamLower.contains("yazılım") || teamLower.contains("software")
                            rId == "mechanic" -> teamLower.contains("mekanik") || teamLower.contains("mechanic")
                            rId == "pr" -> teamLower.contains("pr")
                            else -> teamLower.contains(rId)
                        }
                    } else {
                        notif.recipientUserId == "all"
                    }
                }
                filtered.sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
            }
            .catch { error ->
                handleFirestoreError(error, OperationType.LIST, "notifications")
                emit(emptyList())
            }
    }

    suspend fun sendBroadcastNotification(
        title: String,
        message: String,
        senderName: String
    ) {
        // Creates a broadcast notification for all team members
        createNotification(
            recipientUserId = "all",
            title = title,
            message = message,
            type = "broadcast",
            senderName = senderName
        )
        // Also register as a team announcement so it is visible in the activity feed
        try {
            addAnnouncement(title = title, content = message, authorName = senderName, isPinned = false)
        } catch (e: Exception) {
            Log.w("Repository", "Could not create team announcement: ${e.message}")
        }
    }

    suspend fun createNotification(
        recipientUserId: String,
        title: String,
        message: String,
        type: String,
        targetId: String = "",
        targetRoomId: String = "",
        senderName: String = ""
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
            "isRead" to false,
            "createdAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
        } catch (e: Exception) {
            Log.w("Repository", "Notification creation failed: ${e.message}")
        }
    }

    suspend fun markNotificationAsRead(notificationId: String, userId: String = "") {
        if (notificationId.isBlank()) return
        try {
            val updates = mutableMapOf<String, Any>("isRead" to true)
            if (userId.isNotBlank()) {
                updates["readBy"] = FieldValue.arrayUnion(userId)
            }
            db.collection("notifications").document(notificationId).update(updates).await()
        } catch (e: Exception) {
            Log.w("Repository", "Failed to mark notification as read: ${e.message}")
        }
    }

    suspend fun markAllNotificationsAsRead(userId: String, notificationIds: List<String>) {
        if (notificationIds.isEmpty()) return
        try {
            val batch = db.batch()
            for (id in notificationIds) {
                if (id.isNotBlank()) {
                    val ref = db.collection("notifications").document(id)
                    val updates = if (userId.isNotBlank()) {
                        mapOf(
                            "isRead" to true,
                            "readBy" to FieldValue.arrayUnion(userId)
                        )
                    } else {
                        mapOf("isRead" to true)
                    }
                    batch.update(ref, updates)
                }
            }
            batch.commit().await()
        } catch (e: Exception) {
            Log.w("Repository", "Failed to mark all as read: ${e.message}")
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
        createNotification(
            recipientUserId = "managers",
            title = "Yeniden Üyelik Başvurusu",
            message = "${profile?.getFullName() ?: "Bir üye"} (${selectedTeam.ifBlank { profile?.team ?: "Genel Ekip" }}) yeniden onay başvurusunda bulundu.",
            type = "member_pending",
            targetId = userId,
            senderName = profile?.getFullName() ?: "Üye"
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
                    .sortedBy { it.createdAt?.toDate()?.time ?: 0L }
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
        replyToId: String = "",
        replyToSender: String = "",
        replyToText: String = ""
    ) {
        val uid = requireUserId()
        val path = "rooms/$roomId/messages"
        val docRef = db.collection("rooms").document(roomId).collection("messages").document()
        val safeFileUrl = if (fileUrl.startsWith("data:") && fileUrl.length > 500_000) "" else fileUrl
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
            createNotification(
                recipientUserId = "all",
                title = "Yeni mesaj ($roomName)",
                message = "$senderName: ${text.take(60)}",
                type = "message",
                targetRoomId = roomId,
                senderName = senderName
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
                filtered.sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
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
        fileUrl: String = "",
        fileSize: Long = 0L
    ) {
        val uid = requireUserId()
        val docRef = db.collection("ideas").document()
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
            "fileUrl" to fileUrl,
            "fileSize" to fileSize,
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
                senderName = authorName
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
                    targetId = ideaId
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
                    .sortedBy { it.createdAt?.toDate()?.time ?: 0L }
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
                        senderName = authorName
                    )
                }
            } catch (e: Exception) {
                Log.w("Repository", "Could not notify idea author: ${e.message}")
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
                filtered.sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
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
        uploadedByName: String,
        uploadedByTeam: String = "",
        uploadedByRoles: List<String> = emptyList()
    ) {
        val uid = requireUserId()
        val docRef = db.collection("files").document()
        val safeContent = content.take(200_000)
        val safeDownloadUrl = if (downloadUrl.startsWith("data:") && downloadUrl.length > 500_000) "" else downloadUrl
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
            "createdAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
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
                            .thenByDescending { it.createdAt?.toDate()?.time ?: 0L }
                    )
            }
            .catch { error ->
                handleFirestoreError(error, OperationType.LIST, "announcements")
                emit(emptyList())
            }
    }

    suspend fun addAnnouncement(title: String, content: String, authorName: String, isPinned: Boolean) {
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

            // Notify all
            createNotification(
                recipientUserId = "all",
                title = "Yeni duyuru",
                message = "$authorName yeni bir duyuru yayınladı: \"$title\"",
                type = "announcement",
                targetId = docRef.id,
                senderName = authorName
            )
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
}
