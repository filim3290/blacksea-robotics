package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Base64
import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.SubcomposeAsyncImage
import com.example.ui.theme.ElectricBlue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

data class LocalVideoMetadata(
    val thumbnail: Bitmap? = null,
    val durationMs: Long = 0L
)

/**
 * Converts a media URL or local path into a Coil-compatible image model.
 * Notably decodes `data:image/...;base64,...` data URIs to `ByteArray` because
 * Coil 2.7.0 does not decode `data:` URI strings directly.
 */
fun resolveMediaImageModel(urlOrPath: String): Any? {
    val trimmed = urlOrPath.trim()
    if (trimmed.isBlank()) return null

    if (trimmed.startsWith("data:image/", ignoreCase = true) && trimmed.contains(";base64,", ignoreCase = true)) {
        return try {
            val base64Payload = trimmed.substringAfter(";base64,")
            val decoded = Base64.decode(base64Payload, Base64.DEFAULT)
            if (decoded.isNotEmpty()) decoded else null
        } catch (_: Exception) {
            null
        }
    }

    if (trimmed.startsWith("local:")) {
        val localFile = File(trimmed.removePrefix("local:"))
        return if (localFile.exists() && localFile.length() > 0L) localFile else null
    }

    if (trimmed.startsWith("file://")) {
        val path = Uri.parse(trimmed).path
        val localFile = if (!path.isNullOrBlank()) File(path) else null
        return if (localFile != null && localFile.exists()) localFile else trimmed
    }

    return trimmed
}

/**
 * Resolves a playable video Uri from a downloadUrl or a `local:` storagePath.
 */
fun resolvePlayableVideoUri(downloadUrl: String, storagePath: String): Uri? {
    val url = downloadUrl.trim()
    if (url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)) {
        return Uri.parse(url)
    }

    if (storagePath.startsWith("local:")) {
        val f = File(storagePath.removePrefix("local:"))
        if (f.exists() && f.length() > 0L) return Uri.fromFile(f)
    }

    if (url.startsWith("local:")) {
        val f = File(url.removePrefix("local:"))
        if (f.exists() && f.length() > 0L) return Uri.fromFile(f)
    }

    if (url.startsWith("content://") || url.startsWith("file://")) {
        return Uri.parse(url)
    }

    return null
}

fun formatMediaDuration(durationMs: Long): String {
    if (durationMs <= 0L) return ""
    val totalSeconds = (durationMs / 1000L).coerceAtLeast(1L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

suspend fun extractLocalVideoMetadata(context: Context, uri: Uri): LocalVideoMetadata = withContext(Dispatchers.IO) {
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(context, uri)
        val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull() ?: 0L
        val frame = retriever.getFrameAtTime(500_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            ?: retriever.frameAtTime
        LocalVideoMetadata(thumbnail = frame, durationMs = duration)
    } catch (_: Exception) {
        LocalVideoMetadata()
    } finally {
        try {
            retriever.release()
        } catch (_: Exception) {
        }
    }
}

@Composable
fun MediaThumbnailPreview(
    mediaType: String,
    mediaUrl: String,
    thumbnailUrl: String = "",
    storagePath: String = "",
    localPreviewUri: Uri? = null,
    fileName: String = "",
    fileSizeText: String = "",
    durationMs: Long = 0L,
    height: Dp = 180.dp,
    onClick: (() -> Unit)? = null,
    onDownload: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    testTag: String = "media_thumbnail_preview"
) {
    val context = LocalContext.current
    val isVideo = mediaType.equals("video", ignoreCase = true)

    val imageModel = remember(mediaUrl, thumbnailUrl, localPreviewUri, isVideo) {
        if (isVideo) {
            resolveMediaImageModel(thumbnailUrl)
                ?: if (mediaUrl.startsWith("data:image/", ignoreCase = true)) resolveMediaImageModel(mediaUrl) else null
        } else {
            localPreviewUri ?: resolveMediaImageModel(mediaUrl.ifBlank { thumbnailUrl })
        }
    }

    val playableLocalUri = remember(localPreviewUri, mediaUrl, storagePath, isVideo) {
        if (!isVideo) null else localPreviewUri ?: resolvePlayableVideoUri(mediaUrl, storagePath)
    }

    val localVideoMeta by produceState(
        initialValue = LocalVideoMetadata(),
        key1 = isVideo,
        key2 = imageModel,
        key3 = playableLocalUri
    ) {
        value = if (isVideo && (imageModel == null || durationMs <= 0L) && playableLocalUri != null &&
            (playableLocalUri.scheme == "content" || playableLocalUri.scheme == "file")
        ) {
            extractLocalVideoMetadata(context, playableLocalUri)
        } else {
            LocalVideoMetadata()
        }
    }

    val resolvedDurationMs = if (durationMs > 0L) durationMs else localVideoMeta.durationMs
    val durationLabel = remember(resolvedDurationMs) { formatMediaDuration(resolvedDurationMs) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF0F172A),
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .testTag(testTag)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                imageModel != null -> {
                    SubcomposeAsyncImage(
                        model = imageModel,
                        contentDescription = fileName.ifBlank { if (isVideo) "Video önizleme" else "Görsel önizleme" },
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        loading = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0xFF1E293B)),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = ElectricBlue,
                                    modifier = Modifier.size(26.dp),
                                    strokeWidth = 2.5.dp
                                )
                            }
                        },
                        error = {
                            MediaFallbackSurface(isVideo = isVideo)
                        }
                    )
                }
                isVideo && localVideoMeta.thumbnail != null -> {
                    Image(
                        bitmap = localVideoMeta.thumbnail!!.asImageBitmap(),
                        contentDescription = fileName.ifBlank { "Video kapağı" },
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                else -> {
                    MediaFallbackSurface(isVideo = isVideo)
                }
            }

            // Video Play Badge Overlay
            if (isVideo) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFF43F5E).copy(alpha = 0.92f),
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .size(48.dp)
                        .align(Alignment.Center)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Videoyu Oynat",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
            } else if (onClick != null) {
                // Subtle zoom hint badge on top right for images
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.55f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(30.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.ZoomIn,
                            contentDescription = "Görseli Büyüt",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Video Duration Badge on top right
            if (isVideo && durationLabel.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color.Black.copy(alpha = 0.72f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    Text(
                        text = durationLabel,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            // Bottom gradient info & download bar
            if (fileName.isNotBlank() || onDownload != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))
                            )
                        )
                        .padding(start = 10.dp, end = 4.dp, top = 16.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        if (fileName.isNotBlank()) {
                            Text(
                                text = fileName,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (fileSizeText.isNotBlank()) {
                            Text(
                                text = fileSizeText,
                                color = Color.White.copy(alpha = 0.78f),
                                fontSize = 10.sp,
                                maxLines = 1
                            )
                        }
                    }

                    if (onDownload != null) {
                        IconButton(
                            onClick = onDownload,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Cihaza İndir",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaFallbackSurface(isVideo: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF0F172A), Color(0xFF1E293B))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isVideo) Icons.Default.Videocam else Icons.Default.BrokenImage,
            contentDescription = if (isVideo) "Video" else "Görsel yüklenemedi",
            tint = Color(0xFF64748B),
            modifier = Modifier.size(40.dp)
        )
    }
}

@Composable
fun ZoomableImageViewer(
    model: Any?,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    var scale by remember(model) { mutableFloatStateOf(1f) }
    var offset by remember(model) { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .clipToBounds()
            .background(Color(0xFF0B1120))
            .onSizeChanged { containerSize = it }
            .pointerInput(model) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1.2f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                        }
                    }
                )
            }
            .pointerInput(model) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(1f, 4f)
                    scale = newScale
                    if (newScale <= 1.01f) {
                        offset = Offset.Zero
                    } else {
                        val maxX = (containerSize.width * (newScale - 1f)) / 2f
                        val maxY = (containerSize.height * (newScale - 1f)) / 2f
                        offset = Offset(
                            x = (offset.x + pan.x).coerceIn(-maxX, maxX),
                            y = (offset.y + pan.y).coerceIn(-maxY, maxY)
                        )
                    }
                }
            }
            .testTag("zoomable_image_viewer"),
        contentAlignment = Alignment.Center
    ) {
        if (model == null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.BrokenImage,
                    contentDescription = "Görsel bulunamadı",
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("Görsel yüklenemedi", color = Color(0xFF94A3B8), fontSize = 13.sp)
            }
        } else {
            SubcomposeAsyncImage(
                model = model,
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    ),
                loading = {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ElectricBlue)
                    }
                },
                error = {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.BrokenImage,
                            contentDescription = "Görsel açılamadı",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Görsel yüklenirken hata oluştu.",
                            color = Color.White,
                            fontSize = 13.sp
                        )
                    }
                }
            )
        }

        // Subtle zoom hint / reset pill
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.Black.copy(alpha = 0.6f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(10.dp)
                .clickable(enabled = scale > 1.05f) {
                    scale = 1f
                    offset = Offset.Zero
                }
        ) {
            Text(
                text = if (scale > 1.05f) {
                    "Yakınlaştırma: %.1fx (Sıfırlamak için dokunun)".format(Locale.US, scale)
                } else {
                    "Büyütmek için çift dokunun veya iki parmakla yakınlaştırın"
                },
                color = Color.White,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun ExoVideoPlayerView(
    videoUri: Uri,
    autoPlay: Boolean = true,
    onOpenExternally: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isPlaying by remember(videoUri) { mutableStateOf(autoPlay) }
    var isBuffering by remember(videoUri) { mutableStateOf(true) }
    var isMuted by remember(videoUri) { mutableStateOf(false) }
    var isEnded by remember(videoUri) { mutableStateOf(false) }
    var errorMessage by remember(videoUri) { mutableStateOf<String?>(null) }
    var currentPosMs by remember(videoUri) { mutableLongStateOf(0L) }
    var durationMs by remember(videoUri) { mutableLongStateOf(0L) }
    var isSeeking by remember { mutableStateOf(false) }
    var seekFraction by remember { mutableFloatStateOf(0f) }

    val exoPlayer = remember(videoUri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUri))
            playWhenReady = autoPlay
            prepare()
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                isEnded = playbackState == Player.STATE_ENDED
                if (exoPlayer.duration > 0) {
                    durationMs = exoPlayer.duration
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                isBuffering = false
                isPlaying = false
                errorMessage = error.localizedMessage ?: "Video oynatılamadı (${error.errorCodeName})"
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                exoPlayer.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(exoPlayer, isPlaying, isSeeking) {
        while (true) {
            if (!isSeeking) {
                val dur = exoPlayer.duration
                if (dur > 0) durationMs = dur
                currentPosMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            }
            delay(250)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black)
            .testTag("exo_video_player")
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    player = exoPlayer
                }
            },
            update = { playerView ->
                if (playerView.player !== exoPlayer) {
                    playerView.player = exoPlayer
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .clickable {
                    if (errorMessage == null) {
                        if (isEnded) {
                            exoPlayer.seekTo(0)
                            exoPlayer.play()
                        } else if (exoPlayer.isPlaying) {
                            exoPlayer.pause()
                        } else {
                            exoPlayer.play()
                        }
                    }
                }
        )

        if (isBuffering && errorMessage == null) {
            CircularProgressIndicator(
                color = ElectricBlue,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        if (errorMessage != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0F172A).copy(alpha = 0.92f))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = null,
                    tint = Color(0xFFEF4444),
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errorMessage ?: "Video oynatılamadı",
                    color = Color.White,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            errorMessage = null
                            isBuffering = true
                            exoPlayer.setMediaItem(MediaItem.fromUri(videoUri))
                            exoPlayer.prepare()
                            exoPlayer.play()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Tekrar Dene", fontSize = 12.sp)
                    }
                    if (onOpenExternally != null) {
                        OutlinedButton(onClick = onOpenExternally) {
                            Icon(imageVector = Icons.Default.OpenInNew, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Dışarıda Aç", color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
            }
        } else {
            // Compose Video Control Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                        )
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                val sliderValue = if (isSeeking) {
                    seekFraction
                } else if (durationMs > 0L) {
                    (currentPosMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
                }

                Slider(
                    value = sliderValue,
                    onValueChange = { frac ->
                        isSeeking = true
                        seekFraction = frac
                    },
                    onValueChangeFinished = {
                        if (durationMs > 0L) {
                            val targetMs = (seekFraction * durationMs).toLong()
                            exoPlayer.seekTo(targetMs)
                            currentPosMs = targetMs
                        }
                        isSeeking = false
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = ElectricBlue,
                        activeTrackColor = ElectricBlue,
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                if (isEnded) {
                                    exoPlayer.seekTo(0)
                                    exoPlayer.play()
                                } else if (exoPlayer.isPlaying) {
                                    exoPlayer.pause()
                                } else {
                                    exoPlayer.play()
                                }
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = when {
                                    isEnded -> Icons.Default.Replay
                                    isPlaying -> Icons.Default.Pause
                                    else -> Icons.Default.PlayArrow
                                },
                                contentDescription = if (isPlaying) "Duraklat" else "Oynat",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        val curText = formatMediaDuration(currentPosMs).ifBlank { "00:00" }
                        val totText = formatMediaDuration(durationMs).ifBlank { "--:--" }
                        Text(
                            text = "$curText / $totText",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    IconButton(
                        onClick = {
                            val nextMuted = !isMuted
                            isMuted = nextMuted
                            exoPlayer.volume = if (nextMuted) 0f else 1f
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = if (isMuted) "Sesi Aç" else "Sessize Al",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
