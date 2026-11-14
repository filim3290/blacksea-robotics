package com.example.ui.components

import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.PhoenixAmber
import com.example.update.AppReleaseInfo
import com.example.update.AppUpdateManager
import com.example.update.UpdateDownloadState
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun AppUpdateDialog(
    releaseInfo: AppReleaseInfo,
    updateManager: AppUpdateManager,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    var downloadState by remember { mutableStateOf<UpdateDownloadState>(UpdateDownloadState.Idle) }
    var downloadedApkFile by remember { mutableStateOf<File?>(null) }
    var showPermissionAlert by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner, showPermissionAlert, downloadedApkFile) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && showPermissionAlert && updateManager.canInstallApks()) {
                showPermissionAlert = false
                downloadedApkFile?.let { file ->
                    updateManager.markVersionInstalled(releaseInfo.versionCode)
                    val res = updateManager.installApk(file)
                    if (res.isFailure) {
                        Toast.makeText(context, "Kurulum başlatılamadı: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                    } else {
                        onDismiss()
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Dialog(
        onDismissRequest = {
            if (!releaseInfo.isForceUpdate && downloadState !is UpdateDownloadState.Downloading) {
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = !releaseInfo.isForceUpdate && downloadState !is UpdateDownloadState.Downloading,
            dismissOnClickOutside = !releaseInfo.isForceUpdate && downloadState !is UpdateDownloadState.Downloading,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 520.dp)
                .padding(vertical = 24.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF1E293B),
            shadowElevation = 12.dp,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Badge
                Surface(
                    shape = CircleShape,
                    color = ElectricBlue.copy(alpha = 0.18f),
                    modifier = Modifier.size(64.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.RocketLaunch,
                            contentDescription = null,
                            tint = ElectricBlue,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "BLACKSEA ROBOTICS",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "🚀 Yeni Güncelleme Mevcut",
                        color = ElectricBlue,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (releaseInfo.isForceUpdate) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFEF4444)
                        ) {
                            Text(
                                text = "ZORUNLU",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Versions Comparison Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Mevcut Sürüm", fontSize = 11.sp, color = Color(0xFF94A3B8))
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "v${updateManager.getCurrentVersionName()}",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFCBD5E1)
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = null,
                            tint = PhoenixAmber,
                            modifier = Modifier.size(18.dp)
                        )

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Yeni Sürüm", fontSize = 11.sp, color = ElectricBlue)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "v${releaseInfo.versionName}",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = ElectricBlue
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Changelog Box
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0B1120))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = "Bu Güncellemede Yapılanlar:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = PhoenixAmber
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = releaseInfo.changelog,
                            fontSize = 12.sp,
                            color = Color(0xFFE2E8F0),
                            lineHeight = 17.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Download Progress / Status Handling
                when (val state = downloadState) {
                    is UpdateDownloadState.Idle -> {
                        // Regular buttons
                        Button(
                            onClick = {
                                scope.launch {
                                    downloadState = UpdateDownloadState.Downloading(0, 0, releaseInfo.apkSize)
                                    val file = updateManager.downloadApk(releaseInfo) { progress ->
                                        downloadState = progress
                                    }
                                    if (file != null) {
                                        downloadedApkFile = file
                                        // Attempt installation
                                        if (updateManager.canInstallApks()) {
                                            updateManager.markVersionInstalled(releaseInfo.versionCode)
                                            val installRes = updateManager.installApk(file)
                                            if (installRes.isFailure) {
                                                Toast.makeText(context, "Kurulum başlatılamadı: ${installRes.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                            }
                                        } else {
                                            showPermissionAlert = true
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("now_update_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                        ) {
                            Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("ŞİMDİ GÜNCELLE", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }

                        if (!releaseInfo.isForceUpdate) {
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(
                                onClick = onDismiss,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Daha Sonra", color = Color(0xFF94A3B8), fontSize = 13.sp)
                            }
                        }
                    }

                    is UpdateDownloadState.Downloading -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Güncelleme indiriliyor...", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Medium)
                                Text("%${state.progressPercent}", fontSize = 12.sp, color = ElectricBlue, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { state.progressPercent / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp),
                                color = ElectricBlue,
                                trackColor = Color(0xFF334155)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Lütfen uygulama ekranından ayrılmayın",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    is UpdateDownloadState.Ready -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF10B981).copy(alpha = 0.2f),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981))
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Güncelleme Hazır!", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Spacer(modifier = Modifier.height(10.dp))

                            Button(
                                onClick = {
                                    val file = downloadedApkFile ?: state.apkFile
                                    if (updateManager.canInstallApks()) {
                                        updateManager.markVersionInstalled(releaseInfo.versionCode)
                                        val installRes = updateManager.installApk(file)
                                        if (installRes.isFailure) {
                                            Toast.makeText(context, "Kurulum başlatılamadı: ${installRes.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                        } else {
                                            onDismiss()
                                        }
                                    } else {
                                        showPermissionAlert = true
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                            ) {
                                Icon(imageVector = Icons.Default.InstallMobile, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("YÜKLEMEYİ BAŞLAT", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    is UpdateDownloadState.Error -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = state.message,
                                color = Color(0xFFEF4444),
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    downloadState = UpdateDownloadState.Idle
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                            ) {
                                Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Tekrar Dene")
                            }
                        }
                    }
                }

                // Android Install Permission Dialog if needed
                if (showPermissionAlert) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = PhoenixAmber.copy(alpha = 0.15f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Security, contentDescription = null, tint = PhoenixAmber)
                                Text("Yükleme İzni Gerekli", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = PhoenixAmber)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Android güvenlik sistemi nedeniyle uygulamanın kendi kendini güncelleyebilmesi için 'Bilinmeyen uygulamaları yükle' iznini açmanız gerekir.",
                                fontSize = 11.sp,
                                color = Color(0xFFE2E8F0)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = { showPermissionAlert = false }) {
                                    Text("Vazgeç", fontSize = 11.sp, color = Color(0xFF94A3B8))
                                }
                                Button(
                                    onClick = {
                                        showPermissionAlert = false
                                        updateManager.openInstallPermissionSettings()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = PhoenixAmber),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("Ayarlarda Aç", fontSize = 11.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
