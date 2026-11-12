package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.BlackseaRepository
import com.example.data.FileUploadResult
import com.example.model.SharedProjectFile
import com.example.model.TeamNotification
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.PhoenixAmber
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.ui.viewinterop.AndroidView
import com.example.util.VoiceNotePlayer
import com.example.util.downloadFileToDevice

fun getFileIcon(fileType: String): ImageVector {
    return when (fileType.lowercase()) {
        "video" -> Icons.Default.Videocam
        "audio" -> Icons.Default.Mic
        "pdf" -> Icons.Default.PictureAsPdf
        "image" -> Icons.Default.Image
        "code" -> Icons.Default.Code
        "cad" -> Icons.Default.ViewInAr
        "zip" -> Icons.Default.Archive
        "doc" -> Icons.Default.Article
        else -> Icons.Default.Description
    }
}

fun getFileIconColor(fileType: String): Color {
    return when (fileType.lowercase()) {
        "video" -> Color(0xFFF43F5E)
        "audio" -> Color(0xFF06B6D4)
        "pdf" -> Color(0xFFEF4444)
        "image" -> Color(0xFF10B981)
        "code" -> ElectricBlue
        "cad" -> PhoenixAmber
        "zip" -> Color(0xFF8B5CF6)
        "doc" -> Color(0xFF3B82F6)
        else -> Color(0xFF94A3B8)
    }
}

fun openFileExternally(context: Context, url: String) {
    if (url.isBlank()) {
        Toast.makeText(context, "İndirme bağlantısı bulunamadı", Toast.LENGTH_SHORT).show()
        return
    }
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Dosyayı aç"))
    } catch (e: Exception) {
        Toast.makeText(context, "Harici uygulama açılamadı: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

@Composable
fun SharedFileCard(
    file: SharedProjectFile,
    onOpenFile: (SharedProjectFile) -> Unit,
    onDownloadFile: (SharedProjectFile) -> Unit,
    canDelete: Boolean = false,
    onDelete: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val timeFormat = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }
    val formattedTime = file.createdAt?.toDate()?.let { timeFormat.format(it) } ?: "Yeni"

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("file_card_${file.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // File Type Icon Badge
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = getFileIconColor(file.fileType).copy(alpha = 0.15f),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = getFileIcon(file.fileType),
                            contentDescription = file.fileType,
                            tint = getFileIconColor(file.fileType),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = file.fileName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${file.fileType.uppercase()} · ${file.getFormattedSize()}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = getFileIconColor(file.fileType)
                    )
                }

                if (canDelete) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Sil",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (file.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = file.description,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Author & Time Details
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = file.uploadedByName.ifBlank { "Takım Üyesi" },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${file.uploadedByTeam.ifBlank { "Robotik Ekibi" }} • $formattedTime",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { onOpenFile(file) },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(imageVector = Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Aç", fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            downloadFileToDevice(context, file.fileName, file.downloadUrl, file.content)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("İndir", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun FileUploadProgressDialog(
    fileName: String,
    fileSizeFormatted: String,
    fileType: String,
    previewUri: Uri?,
    isUploading: Boolean,
    uploadProgress: Int,
    errorMessage: String?,
    onCancel: () -> Unit,
    onConfirmUpload: () -> Unit,
    onRetry: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isUploading) onCancel() },
        title = {
            Text(text = "Dosya Gönder", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // File info card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = getFileIconColor(fileType).copy(alpha = 0.2f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = getFileIcon(fileType),
                                    contentDescription = fileType,
                                    tint = getFileIconColor(fileType)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = fileName,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "$fileSizeFormatted · Limit: 100 MB",
                                fontSize = 12.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                }

                // Image preview if image
                if (fileType == "image" && previewUri != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                    ) {
                        AsyncImage(
                            model = previewUri,
                            contentDescription = "Önizleme",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // Upload progress
                if (isUploading) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Dosya yükleniyor...", fontSize = 12.sp, color = ElectricBlue)
                            Text(text = "%$uploadProgress", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ElectricBlue)
                        }
                        LinearProgressIndicator(
                            progress = { uploadProgress / 100f },
                            modifier = Modifier.fillMaxWidth().height(6.dp),
                            color = ElectricBlue
                        )
                    }
                }

                // Error message
                if (errorMessage != null) {
                    Text(
                        text = "Hata: $errorMessage",
                        color = Color(0xFFEF4444),
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            if (errorMessage != null) {
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                ) {
                    Text("Tekrar Dene")
                }
            } else {
                Button(
                    onClick = onConfirmUpload,
                    enabled = !isUploading,
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                ) {
                    if (isUploading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Yükleniyor...")
                    } else {
                        Text("Gönder")
                    }
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancel,
                enabled = !isUploading
            ) {
                Text("İptal")
            }
        }
    )
}

@Composable
fun InAppFileViewerDialog(
    file: SharedProjectFile,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isImage = file.fileType == "image" || file.downloadUrl.contains("jpg|png|webp|jpeg|gif".toRegex(RegexOption.IGNORE_CASE))
    val isVideo = file.fileType == "video" || file.downloadUrl.contains("mp4|mkv|webm|mov|3gp".toRegex(RegexOption.IGNORE_CASE))
    val isAudio = file.fileType == "audio" || file.downloadUrl.contains("m4a|mp3|wav|aac|ogg".toRegex(RegexOption.IGNORE_CASE))
    val isCodeOrText = file.fileType == "code" || file.fileType == "text" || file.content.isNotBlank()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = getFileIcon(file.fileType),
                            contentDescription = null,
                            tint = getFileIconColor(file.fileType),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = file.fileName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                downloadFileToDevice(context, file.fileName, file.downloadUrl, file.content)
                            }
                        ) {
                            Icon(imageVector = Icons.Default.Download, contentDescription = "İndir", tint = ElectricBlue)
                        }

                        IconButton(onClick = onDismiss) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Kapat")
                        }
                    }
                }

                // Body content based on type
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isImage && file.downloadUrl.isNotBlank()) {
                        // Image Viewer
                        AsyncImage(
                            model = file.downloadUrl,
                            contentDescription = file.fileName,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (isVideo) {
                        // Video Player or Poster Preview
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            val localPath = if (file.storagePath.startsWith("local:")) file.storagePath.removePrefix("local:") else ""
                            val localFile = if (localPath.isNotBlank()) java.io.File(localPath) else null

                            if (file.downloadUrl.startsWith("http")) {
                                AndroidView(
                                    factory = { ctx ->
                                        VideoView(ctx).apply {
                                            val mediaController = MediaController(ctx)
                                            mediaController.setAnchorView(this)
                                            setMediaController(mediaController)
                                            setVideoURI(Uri.parse(file.downloadUrl))
                                            start()
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(280.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                )
                            } else if (localFile != null && localFile.exists()) {
                                AndroidView(
                                    factory = { ctx ->
                                        VideoView(ctx).apply {
                                            val mediaController = MediaController(ctx)
                                            mediaController.setAnchorView(this)
                                            setMediaController(mediaController)
                                            setVideoPath(localFile.absolutePath)
                                            start()
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(280.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                )
                            } else if (file.downloadUrl.startsWith("data:image/")) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(260.dp)
                                        .clip(RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AsyncImage(
                                        model = file.downloadUrl,
                                        contentDescription = file.fileName,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    Surface(
                                        shape = CircleShape,
                                        color = Color.Black.copy(alpha = 0.6f),
                                        modifier = Modifier.size(54.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(imageVector = Icons.Default.Videocam, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                                        }
                                    }
                                }
                            } else {
                                Icon(imageVector = Icons.Default.Videocam, contentDescription = null, tint = getFileIconColor("video"), modifier = Modifier.size(64.dp))
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(text = "Video: ${file.fileName}", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(text = file.description.ifBlank { "Önizleme hazır" }, fontSize = 11.sp, color = Color(0xFF94A3B8))
                        }
                    } else if (isAudio && file.downloadUrl.isNotBlank()) {
                        // Audio / Voice Note Player
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = null,
                                tint = ElectricBlue,
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = file.fileName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            VoiceNotePlayer(
                                audioUrl = file.downloadUrl,
                                fileName = file.fileName,
                                isMyMessage = false,
                                onDownload = {
                                    downloadFileToDevice(context, file.fileName, file.downloadUrl, file.content)
                                }
                            )
                        }
                    } else if (isCodeOrText && file.content.isNotBlank()) {
                        // Monospace Code Viewer with line numbers
                        val lines = remember(file.content) { file.content.lines() }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF0F172A),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .horizontalScroll(rememberScrollState())
                                    .padding(12.dp)
                            ) {
                                // Line Numbers
                                Column(modifier = Modifier.padding(end = 12.dp)) {
                                    lines.indices.forEach { idx ->
                                        Text(
                                            text = "${idx + 1}",
                                            color = Color(0xFF64748B),
                                            fontSize = 12.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }

                                // Code Text
                                Column {
                                    lines.forEach { line ->
                                        Text(
                                            text = line,
                                            color = Color(0xFFE2E8F0),
                                            fontSize = 12.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // Fallback for PDF, STEP, CAD, ZIP, etc.
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                imageVector = getFileIcon(file.fileType),
                                contentDescription = null,
                                tint = getFileIconColor(file.fileType),
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = file.fileName,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${file.fileType.uppercase()} Dosyası • ${file.getFormattedSize()}",
                                fontSize = 12.sp,
                                color = Color(0xFF94A3B8)
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(
                                onClick = {
                                    downloadFileToDevice(context, file.fileName, file.downloadUrl, file.content)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Cihaza İndir (İndirilenler)")
                            }
                        }
                    }
                }

                // Footer Actions
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            downloadFileToDevice(context, file.fileName, file.downloadUrl, file.content)
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("İndir")
                    }

                    if (file.downloadUrl.isNotBlank() && file.downloadUrl.startsWith("http")) {
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = { openFileExternally(context, file.downloadUrl) },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Dışarıda Aç")
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Kapat")
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationCenterDialog(
    notifications: List<TeamNotification>,
    onDismiss: () -> Unit,
    onNotificationClick: (TeamNotification) -> Unit,
    onMarkAllRead: () -> Unit
) {
    val unreadCount = notifications.count { !it.isRead }
    val timeFormat = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.80f),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Bildirimler",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        if (unreadCount > 0) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFFEF4444)
                            ) {
                                Text(
                                    text = "$unreadCount",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (unreadCount > 0) {
                            TextButton(onClick = onMarkAllRead) {
                                Text("Tümünü Oku", fontSize = 12.sp, color = ElectricBlue)
                            }
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Kapat")
                        }
                    }
                }

                // List
                if (notifications.isEmpty()) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Henüz bildiriminiz yok",
                            color = Color(0xFF94A3B8),
                            fontSize = 14.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(notifications) { notif ->
                            val icon = when (notif.type) {
                                "member_pending", "member_approved" -> Icons.Default.Article
                                "message" -> Icons.Default.Description
                                "idea" -> Icons.Default.ViewInAr
                                "announcement" -> Icons.Default.UploadFile
                                else -> Icons.Default.Article
                            }
                            val timeStr = notif.createdAt?.toDate()?.let { timeFormat.format(it) } ?: ""

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onNotificationClick(notif) },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (!notif.isRead) ElectricBlue.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = if (!notif.isRead) ElectricBlue else Color(0xFF94A3B8),
                                        modifier = Modifier.size(8.dp)
                                    ) {}

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = notif.title,
                                            fontWeight = if (!notif.isRead) FontWeight.Bold else FontWeight.SemiBold,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = notif.message,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (timeStr.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(3.dp))
                                            Text(
                                                text = timeStr,
                                                fontSize = 10.sp,
                                                color = Color(0xFF94A3B8)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
