package com.example.util

import android.app.DownloadManager
import android.content.ContentValues
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ElectricBlue
import kotlinx.coroutines.delay
import java.io.File

/**
 * Downloads a file directly into the Android device's public Downloads directory.
 */
fun downloadFileToDevice(
    context: Context,
    fileName: String,
    url: String,
    content: String = ""
) {
    try {
        val safeName = fileName.ifBlank { "dosya" }.replace("[/\\\\:*?\"<>|]".toRegex(), "_")

        if (url.startsWith("http://") || url.startsWith("https://")) {
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setTitle(safeName)
                setDescription("BLACKSEA ROBOTICS Dosyası İndiriliyor")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, safeName)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)
            Toast.makeText(context, "$safeName İndirilenler klasörüne indiriliyor...", Toast.LENGTH_LONG).show()
        } else if (url.startsWith("data:") || content.isNotBlank()) {
            val bytes = if (url.startsWith("data:") && url.contains(";base64,")) {
                val base64Data = url.substringAfter(";base64,")
                Base64.decode(base64Data, Base64.DEFAULT)
            } else {
                content.ifBlank { url }.toByteArray(Charsets.UTF_8)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val mime = when {
                    safeName.endsWith(".png", true) -> "image/png"
                    safeName.endsWith(".jpg", true) || safeName.endsWith(".jpeg", true) -> "image/jpeg"
                    safeName.endsWith(".mp4", true) -> "video/mp4"
                    safeName.endsWith(".m4a", true) || safeName.endsWith(".mp3", true) -> "audio/mp4"
                    safeName.endsWith(".pdf", true) -> "application/pdf"
                    safeName.endsWith(".txt", true) || safeName.endsWith(".py", true) || safeName.endsWith(".ino", true) -> "text/plain"
                    else -> "application/octet-stream"
                }

                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, safeName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mime)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }

                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { outStream ->
                        outStream.write(bytes)
                        outStream.flush()
                    }
                    Toast.makeText(context, "$safeName İndirilenler klasörüne kaydedildi!", Toast.LENGTH_LONG).show()
                } else {
                    throw Exception("MediaStore indirme kaydı oluşturulamadı")
                }
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                val targetFile = File(downloadsDir, safeName)
                targetFile.writeBytes(bytes)
                MediaScannerConnection.scanFile(context, arrayOf(targetFile.absolutePath), null) { _, _ -> }
                Toast.makeText(context, "$safeName İndirilenler klasörüne kaydedildi!", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(context, "İndirme adresi veya içerik bulunamadı", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        Log.e("AudioHelper", "Download error: ${e.message}", e)
        Toast.makeText(context, "İndirme hatası: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
    }
}

/**
 * Audio Recorder Manager using Android MediaRecorder.
 */
class AudioRecorderManager(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var currentFile: File? = null

    fun startRecording(): Boolean {
        return try {
            val file = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
            currentFile = file

            recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            true
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Recording start failed: ${e.message}", e)
            recorder?.release()
            recorder = null
            false
        }
    }

    fun stopRecording(): File? {
        return try {
            recorder?.apply {
                stop()
                release()
            }
            recorder = null
            currentFile
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Recording stop failed: ${e.message}", e)
            recorder?.release()
            recorder = null
            null
        }
    }

    fun cancelRecording() {
        try {
            recorder?.apply {
                stop()
                release()
            }
        } catch (_: Exception) {}
        recorder = null
        currentFile?.delete()
        currentFile = null
    }
}

/**
 * Voice Note Audio Player Composable with Play/Pause, Duration, Progress and Download.
 */
@Composable
fun VoiceNotePlayer(
    audioUrl: String,
    fileName: String = "Ses Kaydı.m4a",
    isMyMessage: Boolean,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var currentDurationSeconds by remember { mutableIntStateOf(0) }
    var totalDurationSeconds by remember { mutableIntStateOf(0) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(audioUrl) {
        onDispose {
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    // Periodic progress update while playing
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    val current = player.currentPosition
                    val total = player.duration
                    if (total > 0) {
                        progress = (current.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                        currentDurationSeconds = current / 1000
                        totalDurationSeconds = total / 1000
                    }
                }
            }
            delay(200)
        }
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isMyMessage) Color.White.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Play / Pause Button
            IconButton(
                onClick = {
                    if (isPlaying) {
                        mediaPlayer?.pause()
                        isPlaying = false
                    } else {
                        if (mediaPlayer == null) {
                            isLoading = true
                            try {
                                val player = MediaPlayer().apply {
                                    setAudioAttributes(
                                        AudioAttributes.Builder()
                                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                            .setUsage(AudioAttributes.USAGE_MEDIA)
                                            .build()
                                    )
                                    setDataSource(context, Uri.parse(audioUrl))
                                    prepareAsync()
                                    setOnPreparedListener { mp ->
                                        isLoading = false
                                        totalDurationSeconds = mp.duration / 1000
                                        mp.start()
                                        isPlaying = true
                                    }
                                    setOnCompletionListener {
                                        isPlaying = false
                                        progress = 0f
                                        currentDurationSeconds = 0
                                    }
                                    setOnErrorListener { _, _, _ ->
                                        isLoading = false
                                        isPlaying = false
                                        Toast.makeText(context, "Ses oynatılamadı", Toast.LENGTH_SHORT).show()
                                        true
                                    }
                                }
                                mediaPlayer = player
                            } catch (e: Exception) {
                                isLoading = false
                                Toast.makeText(context, "Hata: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            mediaPlayer?.start()
                            isPlaying = true
                        }
                    }
                },
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        if (isMyMessage) Color.White.copy(alpha = 0.3f) else ElectricBlue.copy(alpha = 0.15f),
                        CircleShape
                    )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = if (isMyMessage) Color.White else ElectricBlue
                    )
                } else {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Duraklat" else "Oynat",
                        tint = if (isMyMessage) Color.White else ElectricBlue,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Waveform / Progress bar & duration
            Column(modifier = Modifier.weight(1f)) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp),
                    color = if (isMyMessage) Color.White else ElectricBlue,
                    trackColor = if (isMyMessage) Color.White.copy(alpha = 0.3f) else Color(0xFFCBD5E1)
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val currentMin = currentDurationSeconds / 60
                    val currentSec = currentDurationSeconds % 60
                    val totalMin = totalDurationSeconds / 60
                    val totalSec = totalDurationSeconds % 60

                    Text(
                        text = String.format("%02d:%02d", currentMin, currentSec),
                        fontSize = 10.sp,
                        color = if (isMyMessage) Color.White.copy(alpha = 0.8f) else Color(0xFF64748B),
                        fontWeight = FontWeight.Medium
                    )

                    Text(
                        text = if (totalDurationSeconds > 0) String.format("%02d:%02d", totalMin, totalSec) else "Ses Notu",
                        fontSize = 10.sp,
                        color = if (isMyMessage) Color.White.copy(alpha = 0.8f) else Color(0xFF64748B),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Download icon
            IconButton(
                onClick = onDownload,
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "İndir",
                    tint = if (isMyMessage) Color.White.copy(alpha = 0.9f) else ElectricBlue,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
