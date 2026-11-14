package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BlackseaRepository
import com.example.model.ChatMessage
import com.example.model.SharedProjectFile
import com.example.model.UserProfile
import com.example.ui.components.FileUploadProgressDialog
import com.example.ui.components.InAppFileViewerDialog
import com.example.ui.components.MediaThumbnailPreview
import com.example.ui.components.TeamBadge
import com.example.ui.components.UserRolesRow
import com.example.ui.components.getFileIcon
import com.example.ui.components.getFileIconColor
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.PhoenixAmber
import com.example.util.AudioRecorderManager
import com.example.util.VoiceNotePlayer
import com.example.util.downloadFileToDevice
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun RoomsScreen(
    repository: BlackseaRepository,
    currentUserProfile: UserProfile?,
    initialRoomId: String = "general",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedRoomId by remember { mutableStateOf(initialRoomId) }
    LaunchedEffect(initialRoomId) {
        selectedRoomId = initialRoomId
    }
    val messages by repository.observeMessages(selectedRoomId).collectAsState(initial = emptyList())

    val listState = rememberLazyListState()

    // Message Input State
    var messageText by remember { mutableStateOf("") }
    var replyingTo by remember { mutableStateOf<ChatMessage?>(null) }
    var isSending by remember { mutableStateOf(false) }

    // Quick Code / Paste Dialog State
    var showAttachmentDialog by remember { mutableStateOf(false) }
    var showAttachmentOptions by remember { mutableStateOf(false) }
    var attachedFileName by remember { mutableStateOf("") }
    var attachedFileType by remember { mutableStateOf("") }
    var attachedFileContent by remember { mutableStateOf("") }

    // Real File Upload State
    var pickedFileUri by remember { mutableStateOf<Uri?>(null) }
    var pickedFileName by remember { mutableStateOf("") }
    var pickedFileSizeFormatted by remember { mutableStateOf("") }
    var pickedFileType by remember { mutableStateOf("other") }
    var isUploadingFile by remember { mutableStateOf(false) }
    var uploadProgress by remember { mutableIntStateOf(0) }
    var uploadErrorMessage by remember { mutableStateOf<String?>(null) }
    var showUploadModal by remember { mutableStateOf(false) }

    // In-App File Preview Modal
    var activePreviewFile by remember { mutableStateOf<SharedProjectFile?>(null) }

    // Voice Recording State
    val audioRecorder = remember { AudioRecorderManager(context) }
    var isRecordingVoice by remember { mutableStateOf(false) }
    var recordDurationSeconds by remember { mutableIntStateOf(0) }
    var isVoiceUploading by remember { mutableStateOf(false) }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val started = audioRecorder.startRecording()
            if (started) {
                isRecordingVoice = true
                recordDurationSeconds = 0
            } else {
                Toast.makeText(context, "Ses kaydı başlatılamadı", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Ses kaydı için mikrofon izni gereklidir", Toast.LENGTH_SHORT).show()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (isRecordingVoice) {
                audioRecorder.cancelRecording()
            }
        }
    }

    LaunchedEffect(isRecordingVoice) {
        if (isRecordingVoice) {
            recordDurationSeconds = 0
            while (isRecordingVoice) {
                delay(1000)
                recordDurationSeconds++
            }
        }
    }

    fun handlePickedUri(uri: Uri) {
        pickedFileUri = uri
        val (name, size) = repository.queryFileMetadata(context, uri)
        val mimeType = try { context.contentResolver.getType(uri) } catch (_: Exception) { null }
        val type = repository.detectFileType(name, mimeType)
        pickedFileName = name
        pickedFileType = type

        val kb = size / 1024.0
        val mb = kb / 1024.0
        pickedFileSizeFormatted = if (mb >= 1.0) {
            String.format(Locale.US, "%.1f MB", mb)
        } else if (kb >= 1.0) {
            String.format(Locale.US, "%.0f KB", kb)
        } else {
            "$size B"
        }

        uploadErrorMessage = if (size > 100L * 1024L * 1024L) {
            "Dosya boyutu 100 MB sınırını aşıyor."
        } else {
            null
        }
        uploadProgress = 0
        isUploadingFile = false
        showUploadModal = true
    }

    // Zero-permission Photo & Video Picker Launcher (Google Play Policy compliant)
    val visualMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) handlePickedUri(uri)
    }

    // General Device File Picker Launcher (PDF, Code, CAD, ZIP, etc.)
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) handlePickedUri(uri)
    }

    // Auto scroll to bottom when new message arrives
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val rooms = listOf(
        Pair("general", "GENEL EKİP ODASI"),
        Pair("software", "YAZILIM EKİBİ"),
        Pair("mechanic", "MEKANİK EKİBİ"),
        Pair("pr", "PR EKİBİ")
    )

    val isAdmin = currentUserProfile?.isSuperAdmin == true ||
            currentUserProfile?.email.equals("filim3290@gmail.com", ignoreCase = true) ||
            currentUserProfile?.roles?.contains("Yönetici") == true ||
            currentUserProfile?.roles?.contains("Takım Kaptanı") == true

    val pinnedMessages = messages.filter { it.isPinned }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .testTag("rooms_screen")
    ) {
        // Room Filter Tabs (All rooms are open to everyone!)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 2.dp
        ) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(rooms) { (id, title) ->
                    FilterChip(
                        selected = selectedRoomId == id,
                        onClick = { selectedRoomId = id },
                        label = {
                            Text(
                                text = title,
                                fontSize = 12.sp,
                                fontWeight = if (selectedRoomId == id) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ElectricBlue,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
        }

        // Pinned Messages Banner
        if (pinnedMessages.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = PhoenixAmber.copy(alpha = 0.12f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = "Sabitli Mesaj",
                        tint = PhoenixAmber,
                        modifier = Modifier.size(16.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Sabitlenmiş Mesaj: ${pinnedMessages.last().text}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${pinnedMessages.last().senderName} • ${pinnedMessages.last().senderTeam}",
                            fontSize = 10.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }
            }
        }

        // Message Stream
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Bu odada henüz mesaj yok. İlk mesajı siz yazın!",
                            fontSize = 13.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }
            }

            itemsIndexed(
                items = messages,
                key = { index, msg -> "${msg.id.ifBlank { "msg_${msg.createdAt?.seconds ?: 0}" }}_$index" }
            ) { _, message ->
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { 20 })
                ) {
                    val isMyMessage = message.senderId == currentUserProfile?.userId
                    MessageItemBubble(
                        message = message,
                        isMyMessage = isMyMessage,
                        canManage = isMyMessage || isAdmin,
                        onReply = { replyingTo = message },
                        onTogglePin = {
                            scope.launch {
                                repository.togglePinMessage(selectedRoomId, message.id, !message.isPinned)
                            }
                        },
                        onDelete = {
                            scope.launch {
                                repository.deleteMessage(selectedRoomId, message.id)
                            }
                        },
                        onOpenFile = { projectFile ->
                            activePreviewFile = projectFile
                        },
                        onDownloadFile = { url ->
                            downloadFileToDevice(context, message.fileName.ifBlank { "indirilen_dosya" }, url)
                        }
                    )
                }
            }
        }

        // Replying to Banner
        if (replyingTo != null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(imageVector = Icons.Default.Reply, contentDescription = null, tint = ElectricBlue, modifier = Modifier.size(16.dp))
                        Text(
                            text = "${replyingTo?.senderName}: ${replyingTo?.text}",
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { replyingTo = null },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Kapat", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // Attached file preview before sending
        if (attachedFileName.isNotBlank()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                shape = RoundedCornerShape(8.dp),
                color = ElectricBlue.copy(alpha = 0.15f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(imageVector = Icons.Default.AttachFile, contentDescription = null, tint = ElectricBlue, modifier = Modifier.size(16.dp))
                        Text(text = "Ek: $attachedFileName ($attachedFileType)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ElectricBlue)
                    }
                    IconButton(
                        onClick = {
                            attachedFileName = ""
                            attachedFileType = ""
                            attachedFileContent = ""
                        },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Kaldır", modifier = Modifier.size(14.dp))
                    }
                }
            }
        }

        // Bottom Input Bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp
        ) {
            if (isRecordingVoice) {
                // Active Voice Recording View
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = {
                            audioRecorder.cancelRecording()
                            isRecordingVoice = false
                            recordDurationSeconds = 0
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Kaydı İptal Et",
                            tint = Color(0xFFEF4444)
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(Color.Red, CircleShape)
                        )
                        val minutes = recordDurationSeconds / 60
                        val seconds = recordDurationSeconds % 60
                        Text(
                            text = String.format(Locale.getDefault(), "Ses Kaydediliyor... %02d:%02d", minutes, seconds),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    IconButton(
                        onClick = {
                            val file = audioRecorder.stopRecording()
                            isRecordingVoice = false
                            if (file != null && file.exists() && file.length() > 0) {
                                val duration = recordDurationSeconds
                                val realName = currentUserProfile?.getFullName() ?: "Takım Üyesi"
                                val roles = currentUserProfile?.roles ?: listOf("Takım Üyesi")
                                val team = currentUserProfile?.team ?: "Genel Ekip"
                                val durationText = String.format(Locale.getDefault(), "%02d:%02d", duration / 60, duration % 60)

                                scope.launch {
                                    isVoiceUploading = true
                                    try {
                                        val uploadResult = repository.uploadLocalFile(
                                            file = file,
                                            folder = "rooms_attachments/$selectedRoomId/audio",
                                            fileType = "audio"
                                        )

                                        repository.sendMessage(
                                            roomId = selectedRoomId,
                                            text = "🎙️ Ses Kaydı ($durationText)",
                                            senderName = realName,
                                            senderTeam = team,
                                            senderRoles = roles,
                                            senderPhotoUrl = currentUserProfile?.photoUrl ?: "",
                                            fileName = "Ses_Kaydı_${System.currentTimeMillis()}.m4a",
                                            fileType = "audio",
                                            fileUrl = uploadResult.downloadUrl,
                                            fileSize = uploadResult.fileSize
                                        )

                                        repository.addSharedFile(
                                            roomId = selectedRoomId,
                                            fileName = "Ses_Kaydı_${System.currentTimeMillis()}.m4a",
                                            fileType = "audio",
                                            fileSize = uploadResult.fileSize,
                                            description = "Ses kaydı ($durationText)",
                                            content = "",
                                            downloadUrl = uploadResult.downloadUrl,
                                            storagePath = "",
                                            uploadedByName = realName,
                                            uploadedByTeam = team,
                                            uploadedByRoles = roles
                                        )

                                        Toast.makeText(context, "Ses kaydı başarıyla gönderildi!", Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Ses kaydı gönderilemedi: ${e.message}", Toast.LENGTH_SHORT).show()
                                    } finally {
                                        file.delete()
                                        isVoiceUploading = false
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .background(ElectricBlue, CircleShape)
                    ) {
                        if (isVoiceUploading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(imageVector = Icons.Default.Send, contentDescription = "Kaydı Gönder", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            } else {
                // Standard Chat Input Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = { showAttachmentOptions = true },
                        modifier = Modifier.size(42.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AttachFile,
                            contentDescription = "Dosya, Video veya Kod Ekle",
                            tint = ElectricBlue
                        )
                    }

                    OutlinedTextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        placeholder = { Text("Mesajınızı yazın...", fontSize = 14.sp) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_input_field"),
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 4
                    )

                    // Microphone button for quick voice notes
                    IconButton(
                        onClick = {
                            audioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .background(ElectricBlue.copy(alpha = 0.15f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Ses Kaydı Al",
                            tint = ElectricBlue,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Send text or attachment message
                    IconButton(
                        onClick = {
                            if ((messageText.isNotBlank() || attachedFileName.isNotBlank()) && !isSending) {
                                val reply = replyingTo
                                val currentText = messageText.trim()
                                val fileNameToSend = attachedFileName
                                val fileTypeToSend = attachedFileType
                                val fileContentToSend = attachedFileContent

                                // User real name & roles
                                val realName = currentUserProfile?.getFullName() ?: "Takım Üyesi"
                                val roles = currentUserProfile?.roles ?: listOf("Takım Üyesi")
                                val team = currentUserProfile?.team ?: "Genel Ekip"

                                // Reset fields
                                messageText = ""
                                replyingTo = null
                                attachedFileName = ""
                                attachedFileType = ""
                                attachedFileContent = ""
                                isSending = true

                                scope.launch {
                                    try {
                                        repository.sendMessage(
                                            roomId = selectedRoomId,
                                            text = currentText.ifBlank { "[$fileNameToSend paylaştı]" },
                                            senderName = realName,
                                            senderTeam = team,
                                            senderRoles = roles,
                                            senderPhotoUrl = currentUserProfile?.photoUrl ?: "",
                                            fileName = fileNameToSend,
                                            fileType = fileTypeToSend,
                                            fileUrl = fileContentToSend,
                                            replyToId = reply?.id ?: "",
                                            replyToSender = reply?.senderName ?: "",
                                            replyToText = reply?.text ?: ""
                                        )
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Mesaj gönderilemedi: ${e.message}", Toast.LENGTH_SHORT).show()
                                    } finally {
                                        isSending = false
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .background(ElectricBlue, CircleShape)
                            .testTag("send_message_button")
                    ) {
                        if (isSending) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(imageVector = Icons.Default.Send, contentDescription = "Gönder", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }

    // Attachment Option Selector Dialog (Real Device File vs Quick Code Snippet)
    if (showAttachmentOptions) {
        AlertDialog(
            onDismissRequest = { showAttachmentOptions = false },
            title = {
                Text("Dosya veya İçerik Ekle", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Görsel/video galeriden veya tüm cihaz dosyalarından yükleyebilir ya da kod yapıştırabilirsiniz:", fontSize = 13.sp, color = Color(0xFF94A3B8))

                    // Option 1: Zero-permission Photo & Video Picker with Preview
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showAttachmentOptions = false
                                visualMediaLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                )
                            }
                            .testTag("pick_visual_media_option"),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF10B981).copy(alpha = 0.15f),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(22.dp))
                                }
                            }
                            Column {
                                Text("Fotoğraf / Video Seç", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Galeriden önizlemeli fotoğraf veya video paylaşın", fontSize = 11.sp, color = Color(0xFF94A3B8))
                            }
                        }
                    }

                    // Option 2: Real Device File Picker
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showAttachmentOptions = false
                                filePickerLauncher.launch("*/*")
                            }
                            .testTag("pick_any_file_option"),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = ElectricBlue.copy(alpha = 0.15f),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.UploadFile, contentDescription = null, tint = ElectricBlue, modifier = Modifier.size(22.dp))
                                }
                            }
                            Column {
                                Text("Cihazdan Dosya Seç", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("PDF, Kod (.py, .cpp, .ino), CAD, ZIP, Ses (Maks 100 MB)", fontSize = 11.sp, color = Color(0xFF94A3B8))
                            }
                        }
                    }

                    // Option 3: Quick Code Snippet / Link Paste
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showAttachmentOptions = false
                                showAttachmentDialog = true
                            },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = PhoenixAmber.copy(alpha = 0.15f),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Code, contentDescription = null, tint = PhoenixAmber, modifier = Modifier.size(22.dp))
                                }
                            }
                            Column {
                                Text("Kod Bloğu / Link Yapıştır", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Hızlıca kod veya link metni ekleyin", fontSize = 11.sp, color = Color(0xFF94A3B8))
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAttachmentOptions = false }) {
                    Text("İptal")
                }
            }
        )
    }

    // Attachment Chooser Dialog (Snippet/Link)
    if (showAttachmentDialog) {
        AttachmentPickerDialog(
            onDismiss = { showAttachmentDialog = false },
            onFileSelected = { name, type, content ->
                attachedFileName = name
                attachedFileType = type
                attachedFileContent = content
                showAttachmentDialog = false
            }
        )
    }

    // Real Device File Upload Progress Modal
    if (showUploadModal && pickedFileUri != null) {
        FileUploadProgressDialog(
            fileName = pickedFileName,
            fileSizeFormatted = pickedFileSizeFormatted,
            fileType = pickedFileType,
            previewUri = pickedFileUri,
            isUploading = isUploadingFile,
            uploadProgress = uploadProgress,
            errorMessage = uploadErrorMessage,
            onCancel = {
                showUploadModal = false
                pickedFileUri = null
            },
            onConfirmUpload = {
                val uri = pickedFileUri ?: return@FileUploadProgressDialog
                scope.launch {
                    isUploadingFile = true
                    uploadErrorMessage = null
                    try {
                        val result = repository.uploadFile(
                            context = context,
                            uri = uri,
                            folder = "rooms_attachments/$selectedRoomId",
                            onProgress = { prog -> uploadProgress = prog }
                        )

                        // 1. Add file to workspace 'files' collection
                        repository.addSharedFile(
                            roomId = selectedRoomId,
                            fileName = result.fileName,
                            fileType = result.fileType,
                            fileSize = result.fileSize,
                            description = "Oda içi paylaşılan dosya ($selectedRoomId)",
                            content = result.textContent,
                            downloadUrl = result.downloadUrl,
                            storagePath = result.storagePath,
                            thumbnailUrl = result.thumbnailUrl,
                            mediaDurationMs = result.mediaDurationMs,
                            uploadedByName = currentUserProfile?.getFullName() ?: "Takım Üyesi",
                            uploadedByTeam = currentUserProfile?.team ?: "",
                            uploadedByRoles = currentUserProfile?.roles ?: emptyList()
                        )

                        // 2. Post chat message with file attachment
                        repository.sendMessage(
                            roomId = selectedRoomId,
                            text = "[${result.fileName} dosyasını paylaştı]",
                            senderName = currentUserProfile?.getFullName() ?: "Takım Üyesi",
                            senderTeam = currentUserProfile?.team ?: "Genel Ekip",
                            senderRoles = currentUserProfile?.roles ?: emptyList(),
                            senderPhotoUrl = currentUserProfile?.photoUrl ?: "",
                            fileName = result.fileName,
                            fileType = result.fileType,
                            fileUrl = result.downloadUrl.ifBlank { result.textContent },
                            fileSize = result.fileSize,
                            thumbnailUrl = result.thumbnailUrl,
                            storagePath = result.storagePath,
                            mediaDurationMs = result.mediaDurationMs
                        )

                        isUploadingFile = false
                        showUploadModal = false
                        pickedFileUri = null
                        Toast.makeText(context, "Dosya başarıyla gönderildi!", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        isUploadingFile = false
                        uploadErrorMessage = e.localizedMessage ?: "Dosya yüklenemedi"
                    }
                }
            },
            onRetry = {
                uploadErrorMessage = null
            }
        )
    }

    // In-App File Viewer Modal
    if (activePreviewFile != null) {
        InAppFileViewerDialog(
            file = activePreviewFile!!,
            onDismiss = { activePreviewFile = null }
        )
    }
}

@Composable
fun MessageItemBubble(
    message: ChatMessage,
    isMyMessage: Boolean,
    canManage: Boolean,
    onReply: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit,
    onOpenFile: (SharedProjectFile) -> Unit,
    onDownloadFile: (String) -> Unit
) {
    val context = LocalContext.current
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val formattedTime = message.createdAt?.toDate()?.let { timeFormatter.format(it) } ?: ""

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalAlignment = if (isMyMessage) Alignment.End else Alignment.Start
    ) {
        // Sender header with Real Name + Roles
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(bottom = 2.dp)
        ) {
            if (!isMyMessage) {
                Surface(
                    shape = CircleShape,
                    color = ElectricBlue.copy(alpha = 0.2f),
                    modifier = Modifier.size(26.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = message.senderName.take(1).uppercase(),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = ElectricBlue
                        )
                    }
                }
                Text(
                    text = message.senderName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                TeamBadge(teamName = message.senderTeam)
            }

            // Roles badges
            if (message.senderRoles.isNotEmpty()) {
                UserRolesRow(roles = message.senderRoles.take(2))
            }
        }

        Card(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isMyMessage) 16.dp else 4.dp,
                bottomEnd = if (isMyMessage) 4.dp else 16.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (isMyMessage) ElectricBlue else MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.widthIn(max = 310.dp)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                // Reply Quote
                if (message.replyToSender.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color.Black.copy(alpha = 0.15f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                    ) {
                        Column(modifier = Modifier.padding(6.dp)) {
                            Text(
                                text = message.replyToSender,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = if (isMyMessage) Color.White else ElectricBlue
                            )
                            Text(
                                text = message.replyToText,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (isMyMessage) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Main Text
                Text(
                    text = message.text,
                    fontSize = 14.sp,
                    color = if (isMyMessage) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                )

                val projectFile = SharedProjectFile(
                    id = message.id,
                    roomId = message.roomId,
                    fileName = message.fileName.ifBlank {
                        when (message.fileType) {
                            "image" -> "Görsel.jpg"
                            "video" -> "Video.mp4"
                            "audio" -> "Ses_Kaydı.m4a"
                            else -> "Dosya"
                        }
                    },
                    fileType = message.fileType,
                    fileSize = message.fileSize,
                    content = if (message.fileType == "code" || message.fileType == "text") message.fileUrl else "",
                    downloadUrl = message.fileUrl,
                    storagePath = message.storagePath,
                    thumbnailUrl = message.thumbnailUrl,
                    mediaDurationMs = message.mediaDurationMs,
                    uploadedByName = message.senderName,
                    uploadedByTeam = message.senderTeam
                )

                // Voice Note Audio Player
                if (message.fileType == "audio" && message.fileUrl.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    VoiceNotePlayer(
                        audioUrl = message.fileUrl,
                        fileName = message.fileName.ifBlank { "Ses Kaydı.m4a" },
                        isMyMessage = isMyMessage,
                        onDownload = {
                            downloadFileToDevice(context, message.fileName.ifBlank { "Ses_Kaydı.m4a" }, message.fileUrl)
                        }
                    )
                } else if (message.fileType == "image" && (message.fileUrl.isNotBlank() || message.thumbnailUrl.isNotBlank() || message.storagePath.isNotBlank())) {
                    // INLINE IMAGE PREVIEW with Base64/local support and tap to zoom
                    Spacer(modifier = Modifier.height(8.dp))
                    MediaThumbnailPreview(
                        mediaType = "image",
                        mediaUrl = message.fileUrl,
                        thumbnailUrl = message.thumbnailUrl,
                        storagePath = message.storagePath,
                        fileName = message.fileName.ifBlank { "Görsel" },
                        fileSizeText = projectFile.getFormattedSize(),
                        height = 190.dp,
                        onClick = { onOpenFile(projectFile) },
                        onDownload = {
                            downloadFileToDevice(
                                context,
                                message.fileName.ifBlank { "gorsel.jpg" },
                                message.fileUrl.ifBlank { message.storagePath }
                            )
                        },
                        testTag = "chat_image_preview_${message.id}"
                    )
                } else if (message.fileType == "video" && (message.fileUrl.isNotBlank() || message.thumbnailUrl.isNotBlank() || message.storagePath.isNotBlank())) {
                    // INLINE VIDEO THUMBNAIL PREVIEW with real frame, duration badge, and tap to play in ExoPlayer
                    Spacer(modifier = Modifier.height(8.dp))
                    MediaThumbnailPreview(
                        mediaType = "video",
                        mediaUrl = message.fileUrl,
                        thumbnailUrl = message.thumbnailUrl,
                        storagePath = message.storagePath,
                        fileName = message.fileName.ifBlank { "Video.mp4" },
                        fileSizeText = projectFile.getFormattedSize(),
                        durationMs = message.mediaDurationMs,
                        height = 190.dp,
                        onClick = { onOpenFile(projectFile) },
                        onDownload = {
                            downloadFileToDevice(
                                context,
                                message.fileName.ifBlank { "video.mp4" },
                                message.fileUrl.ifBlank { message.storagePath }
                            )
                        },
                        testTag = "chat_video_preview_${message.id}"
                    )
                } else if (message.fileName.isNotBlank()) {
                    // DOCUMENT / CODE / CAD / ZIP PREVIEW CARD
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isMyMessage) Color.White.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenFile(projectFile) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = getFileIcon(message.fileType),
                                    contentDescription = null,
                                    tint = if (isMyMessage) Color.White else getFileIconColor(message.fileType),
                                    modifier = Modifier.size(22.dp)
                                )
                                Column {
                                    Text(
                                        text = message.fileName,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isMyMessage) Color.White else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${message.fileType.uppercase()} · Önizlemek İçin Dokunun",
                                        fontSize = 10.sp,
                                        color = if (isMyMessage) Color.White.copy(alpha = 0.8f) else Color(0xFF94A3B8)
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = (if (isMyMessage) Color.White else ElectricBlue).copy(alpha = 0.15f),
                                    modifier = Modifier.clickable { onOpenFile(projectFile) }
                                ) {
                                    Text(
                                        text = "Aç",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isMyMessage) Color.White else ElectricBlue,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                if (message.fileUrl.isNotBlank()) {
                                    IconButton(
                                        onClick = {
                                            downloadFileToDevice(context, message.fileName.ifBlank { "dosya" }, message.fileUrl)
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Download,
                                            contentDescription = "Cihaza İndir",
                                            tint = if (isMyMessage) Color.White else ElectricBlue,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Footer: Time & Action Buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (message.isPinned) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = "Sabitlendi",
                            tint = PhoenixAmber,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    Text(
                        text = formattedTime,
                        fontSize = 10.sp,
                        color = if (isMyMessage) Color.White.copy(alpha = 0.7f) else Color(0xFF94A3B8)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Icon(
                        imageVector = Icons.Default.Reply,
                        contentDescription = "Cevapla",
                        tint = if (isMyMessage) Color.White.copy(alpha = 0.8f) else Color(0xFF94A3B8),
                        modifier = Modifier
                            .size(14.dp)
                            .clickable { onReply() }
                    )

                    if (canManage) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = "Sabitle",
                            tint = if (isMyMessage) Color.White.copy(alpha = 0.8f) else Color(0xFF94A3B8),
                            modifier = Modifier
                                .size(14.dp)
                                .clickable { onTogglePin() }
                        )

                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Sil",
                            tint = if (isMyMessage) Color.White.copy(alpha = 0.8f) else Color(0xFFEF4444),
                            modifier = Modifier
                                .size(14.dp)
                                .clickable { onDelete() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AttachmentPickerDialog(
    onDismiss: () -> Unit,
    onFileSelected: (name: String, type: String, content: String) -> Unit
) {
    var fileName by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("code") }
    var content by remember { mutableStateOf("") }

    val fileTypes = listOf(
        "code" to "Kod Dosyası",
        "doc" to "Teknik Doküman",
        "cad" to "CAD / 3D Model",
        "image" to "Görsel Linki"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Dosya veya Kod Ekle", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    label = { Text("Dosya Adı") },
                    placeholder = { Text("Örn: autonomous_drive.cpp") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                Text("Dosya Türü:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(fileTypes) { (typeKey, typeLabel) ->
                        FilterChip(
                            selected = selectedType == typeKey,
                            onClick = { selectedType = typeKey },
                            label = { Text(typeLabel, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors()
                        )
                    }
                }

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text(if (selectedType == "code") "Kod İçeriği" else "Açıklama / Dosya Linki") },
                    placeholder = { Text(if (selectedType == "code") "// Kodunuzu buraya yapıştırın..." else "https://drive.google.com/... veya Onshape linki") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    shape = RoundedCornerShape(10.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (fileName.isNotBlank()) {
                        onFileSelected(fileName.trim(), selectedType, content.trim())
                    }
                },
                enabled = fileName.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
            ) {
                Text("Ekle")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("İptal")
            }
        }
    )
}
