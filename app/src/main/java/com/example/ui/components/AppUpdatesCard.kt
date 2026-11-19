package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.notifications.TeamNotificationDispatcher
import com.example.ui.theme.blackseaColors
import com.example.update.AppReleaseInfo
import com.example.update.AppUpdateManager
import com.example.update.UpdateCheckResult
import com.example.util.AppMessages
import kotlinx.coroutines.launch

@Composable
fun AppUpdatesCard(
    updateManager: AppUpdateManager? = null,
    onShowUpdateDialog: (AppReleaseInfo) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val ext = MaterialTheme.blackseaColors
    val scope = rememberCoroutineScope()
    val mgr = updateManager ?: remember { AppUpdateManager(context) }

    var isCheckingUpdate by remember { mutableStateOf(false) }
    var showHistoryDialog by remember { mutableStateOf(false) }
    var releaseHistory by remember { mutableStateOf<List<AppReleaseInfo>>(emptyList()) }
    var isLoadingHistory by remember { mutableStateOf(false) }
    var latestAvailableRelease by remember { mutableStateOf<AppReleaseInfo?>(null) }
    var updateStatusText by remember { mutableStateOf<String?>(null) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("app_updates_card"),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, ext.cardBorder),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = if (ext.isDark) 2.dp else 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = ext.accentBlue)
                    Text(
                        text = "Uygulama & Güncellemeler",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = ext.accentBlueContainer
                ) {
                    Text(
                        text = "v${mgr.getCurrentVersionName()}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = ext.accentBlue,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = updateStatusText ?: "Uygulama sürümünüzü buradan kontrol edebilir ve yenilikleri görüntüleyebilirsiniz.",
                fontSize = 12.sp,
                color = if (latestAvailableRelease != null) ext.emeraldText else ext.textSecondary,
                fontWeight = if (latestAvailableRelease != null) FontWeight.SemiBold else FontWeight.Normal
            )

            Spacer(modifier = Modifier.height(2.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        isCheckingUpdate = true
                        scope.launch {
                            val result = mgr.checkLatestReleaseDetailed(forceRefresh = true)
                            isCheckingUpdate = false
                            when (result) {
                                is UpdateCheckResult.UpdateAvailable -> {
                                    latestAvailableRelease = result.release
                                    updateStatusText = "Yeni sürüm bulundu: v${result.release.versionName}"
                                    onShowUpdateDialog(result.release)
                                }
                                is UpdateCheckResult.UpToDate -> {
                                    latestAvailableRelease = null
                                    updateStatusText = "Uygulamanız güncel (v${result.currentVersionName})"
                                    TeamNotificationDispatcher.clearPendingUpdateNotification(context)
                                    Toast.makeText(
                                        context,
                                        "Uygulamanız güncel (v${result.currentVersionName})",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                is UpdateCheckResult.NoReleaseFound -> {
                                    latestAvailableRelease = null
                                    updateStatusText = "Uygulamanız güncel."
                                    TeamNotificationDispatcher.clearPendingUpdateNotification(context)
                                    Toast.makeText(
                                        context,
                                        "Uygulamanız güncel.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                is UpdateCheckResult.ApkAssetMissing,
                                is UpdateCheckResult.NetworkError,
                                is UpdateCheckResult.ApiError -> {
                                    updateStatusText = AppMessages.UPDATE_CHECK_ERROR
                                    Toast.makeText(
                                        context,
                                        AppMessages.UPDATE_CHECK_ERROR,
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    },
                    enabled = !isCheckingUpdate,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("check_updates_button"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ext.accentBlueSolid,
                        contentColor = Color.White
                    )
                ) {
                    if (isCheckingUpdate) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Güncellemeleri Kontrol Et", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                OutlinedButton(
                    onClick = {
                        showHistoryDialog = true
                        isLoadingHistory = true
                        scope.launch {
                            val res = mgr.fetchReleaseHistory()
                            isLoadingHistory = false
                            releaseHistory = res.getOrDefault(emptyList())
                        }
                    },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("release_history_button"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                ) {
                    Text("Geçmiş", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            latestAvailableRelease?.let { pendingRelease ->
                Button(
                    onClick = { onShowUpdateDialog(pendingRelease) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("install_update_button"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ext.emeraldSolid,
                        contentColor = Color.White
                    )
                ) {
                    Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Güncelle (v${pendingRelease.versionName})", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showHistoryDialog = false },
            title = {
                Text("Sürüm Güncelleme Geçmişi", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                if (isLoadingHistory) {
                    Box(modifier = Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ext.accentBlue)
                    }
                } else if (releaseHistory.isEmpty()) {
                    Text("Henüz yayınlanmış bir sürüm geçmişi bulunamadı.", fontSize = 13.sp, color = ext.textSecondary)
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(
                            items = releaseHistory,
                            key = { it.tagName.ifBlank { it.versionCode.toString() } }
                        ) { item ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(1.dp, ext.cardBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "v${item.versionName}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = ext.accentBlue
                                        )
                                        if (item.versionCode == mgr.getCurrentVersionCode()) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = ext.emeraldSolid
                                            ) {
                                                Text(
                                                    text = "Şu Anki",
                                                    color = Color.White,
                                                    fontSize = 10.sp,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(text = item.changelog, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showHistoryDialog = false }) {
                    Text("Kapat")
                }
            }
        )
    }
}
