package com.example.ui.components

import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.BorderStroke
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.PhoenixAmber
import com.example.ui.theme.blackseaColors
import com.example.update.AppReleaseInfo
import com.example.update.AppUpdateManager
import com.example.update.InstallResultState
import com.example.update.UpdateDownloadState
import com.example.update.UpdateInstallResultBus
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
    val installState by UpdateInstallResultBus.state.collectAsState()

    LaunchedEffect(releaseInfo.tagName) {
        UpdateInstallResultBus.reset()
    }

    LaunchedEffect(installState) {
        if (installState is InstallResultState.Success) {
            updateManager.markVersionInstalled(releaseInfo.versionCode)
            Toast.makeText(context, "Blacksea Robotics v${releaseInfo.versionName} başarıyla kuruldu!", Toast.LENGTH_LONG).show()
            UpdateInstallResultBus.reset()
            onDismiss()
        }
    }

    fun startInstallation(file: File) {
        if (updateManager.canInstallApks()) {
            scope.launch {
                val res = updateManager.installApkWithPackageInstaller(file)
                if (res.isFailure) {
                    Toast.makeText(
                        context,
                        com.example.util.AppMessages.UPDATE_INSTALL_ERROR,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        } else {
            showPermissionAlert = true
        }
    }

    DisposableEffect(lifecycleOwner, showPermissionAlert, downloadedApkFile) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && showPermissionAlert && updateManager.canInstallApks()) {
                showPermissionAlert = false
                downloadedApkFile?.let { file ->
                    startInstallation(file)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val isBusy = downloadState is UpdateDownloadState.Downloading || installState is InstallResultState.Installing

    Dialog(
        onDismissRequest = {
            if (!releaseInfo.isForceUpdate && !isBusy) {
                UpdateInstallResultBus.reset()
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = !releaseInfo.isForceUpdate && !isBusy,
            dismissOnClickOutside = !releaseInfo.isForceUpdate && !isBusy,
            usePlatformDefaultWidth = false
        )
    ) {
        val ext = MaterialTheme.blackseaColors
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 520.dp)
                .padding(vertical = 24.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, ext.cardBorder),
            shadowElevation = 12.dp,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = CircleShape,
                    color = ElectricBlue.copy(alpha = 0.16f),
                    modifier = Modifier.size(64.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.RocketLaunch,
                            contentDescription = null,
                            tint = ext.accentBlue,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "BLACKSEA ROBOTICS",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Yeni sürüm bulundu: v${releaseInfo.versionName}",
                        color = ext.accentBlue,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (releaseInfo.isForceUpdate) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = ext.dangerSolid
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

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(1.dp, ext.cardBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Mevcut Sürüm", fontSize = 11.sp, color = ext.textSecondary)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "v${updateManager.getCurrentVersionName()}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = ext.amberText,
                            modifier = Modifier.size(18.dp)
                        )

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Yeni Sürüm", fontSize = 11.sp, color = ext.accentBlue)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "v${releaseInfo.versionName}",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = ext.accentBlue
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = ext.surfaceElevated),
                    border = BorderStroke(1.dp, ext.cardBorder)
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
                            color = ext.amberText
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = releaseInfo.changelog,
                            fontSize = 12.sp,
                            color = ext.textPrimary,
                            lineHeight = 17.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // PackageInstaller failure card takes precedence if an install attempt failed
                val installErr = installState as? InstallResultState.Error
                if (installErr != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = ext.dangerContainer
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Güncelleme Kurulamadı",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = ext.dangerText
                            )
                            Text(
                                text = installErr.userMessage,
                                fontSize = 12.sp,
                                color = ext.textPrimary,
                                lineHeight = 16.sp
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        UpdateInstallResultBus.reset()
                                        val file = downloadedApkFile
                                        if (file != null && file.exists()) {
                                            startInstallation(file)
                                        } else {
                                            downloadState = UpdateDownloadState.Idle
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue, contentColor = Color.White)
                                ) {
                                    Text("Tekrar Dene", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                val file = downloadedApkFile
                                if (file != null && file.exists()) {
                                    OutlinedButton(
                                        onClick = {
                                            val fallbackRes = updateManager.installApkViaFileProvider(file)
                                            if (fallbackRes.isFailure) {
                                                Toast.makeText(
                                                    context,
                                                    com.example.util.AppMessages.UPDATE_INSTALL_ERROR,
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("Kurulumu Aç", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    when (val state = downloadState) {
                        is UpdateDownloadState.Idle -> {
                            val isSignatureCompatible = remember(releaseInfo) {
                                updateManager.isReleaseSignatureCompatible(releaseInfo)
                            }

                            if (!isSignatureCompatible) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    color = ext.dangerContainer
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "Doğrudan Güncelleme Yapılamıyor",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = ext.dangerText
                                        )
                                        Text(
                                            text = "Bu güncelleme mevcut sürümün üzerine doğrudan yüklenemiyor. Yeni sürümü tarayıcıdan indirip yeniden kurabilirsiniz.",
                                            fontSize = 11.sp,
                                            color = ext.textPrimary,
                                            lineHeight = 16.sp
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = {
                                        try {
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, Uri.parse(releaseInfo.apkDownloadUrl)).apply {
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                            )
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Tarayıcı açılamadı.", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(46.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = PhoenixAmber, contentColor = Color.Black)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                        contentDescription = null,
                                        tint = Color.Black,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Tarayıcıdan İndir", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            } else {
                                Button(
                                    onClick = {
                                        UpdateInstallResultBus.reset()
                                        scope.launch {
                                            downloadState = UpdateDownloadState.Downloading(0, 0, releaseInfo.apkSize)
                                            val file = updateManager.downloadApk(releaseInfo) { progress ->
                                                downloadState = progress
                                            }
                                            if (file != null) {
                                                downloadedApkFile = file
                                                startInstallation(file)
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("now_update_button"),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue, contentColor = Color.White)
                                ) {
                                    Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("ŞİMDİ GÜNCELLE", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            }

                            if (!releaseInfo.isForceUpdate) {
                                Spacer(modifier = Modifier.height(8.dp))
                                TextButton(
                                    onClick = {
                                        UpdateInstallResultBus.reset()
                                        onDismiss()
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(if (isSignatureCompatible) "Daha Sonra" else "Kapat", color = ext.textSecondary, fontSize = 13.sp)
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
                                    Text("Güncelleme indiriliyor...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                                    Text("%${state.progressPercent}", fontSize = 12.sp, color = ext.accentBlue, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { state.progressPercent / 100f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp),
                                    color = ElectricBlue,
                                    trackColor = ext.cardBorder
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Lütfen indirme tamamlanana kadar bekleyin",
                                    fontSize = 11.sp,
                                    color = ext.textSecondary
                                )
                            }
                        }

                        is UpdateDownloadState.Ready -> {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                if (installState is InstallResultState.Installing || installState is InstallResultState.PendingUserAction) {
                                    CircularProgressIndicator(color = ElectricBlue, modifier = Modifier.size(32.dp))
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = if (installState is InstallResultState.PendingUserAction) {
                                            "Lütfen kurulum onayını tamamlayın..."
                                        } else {
                                            "Güncelleme kuruluma hazırlanıyor..."
                                        },
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        textAlign = TextAlign.Center
                                    )
                                } else {
                                    Surface(
                                        shape = CircleShape,
                                        color = ext.emeraldSolid.copy(alpha = 0.18f),
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = ext.emeraldText)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Güncelleme Kuruluma Hazır!", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                    Spacer(modifier = Modifier.height(10.dp))

                                    Button(
                                        onClick = {
                                            val file = downloadedApkFile ?: state.apkFile
                                            startInstallation(file)
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = ext.emeraldSolid, contentColor = Color.White)
                                    ) {
                                        Icon(imageVector = Icons.Default.InstallMobile, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("KURULUMU BAŞLAT", fontWeight = FontWeight.Bold)
                                    }
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
                                    color = ext.dangerText,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 17.sp
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                if (state.isSignatureMismatch) {
                                    Button(
                                        onClick = {
                                            val targetUrl = state.manualDownloadUrl.ifBlank { releaseInfo.apkDownloadUrl }
                                            try {
                                                context.startActivity(
                                                    Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    }
                                                )
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Tarayıcı açılamadı.", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = PhoenixAmber, contentColor = Color.Black)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                            contentDescription = null,
                                            tint = Color.Black,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Tarayıcıdan İndir",
                                            color = Color.Black,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                                Button(
                                    onClick = {
                                        UpdateInstallResultBus.reset()
                                        downloadState = UpdateDownloadState.Idle
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue, contentColor = Color.White)
                                ) {
                                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Tekrar Dene")
                                }
                            }
                        }
                    }
                }

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
                                Icon(imageVector = Icons.Default.Security, contentDescription = null, tint = ext.amberText)
                                Text("Yükleme İzni Gerekli", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = ext.amberText)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Güncellemeleri yükleyebilmek için cihaz ayarlarından uygulama yükleme iznini açmanız gerekir.",
                                fontSize = 11.sp,
                                color = ext.textPrimary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = { showPermissionAlert = false }) {
                                    Text("Vazgeç", fontSize = 11.sp, color = ext.textSecondary)
                                }
                                Button(
                                    onClick = {
                                        updateManager.openInstallPermissionSettings()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = PhoenixAmber, contentColor = Color.Black),
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
