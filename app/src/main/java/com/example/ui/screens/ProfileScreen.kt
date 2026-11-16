package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import com.example.data.BlackseaRepository
import com.example.model.PredefinedRoles
import com.example.model.TeamType
import com.example.model.UserProfile
import com.example.ui.components.BlackseaLogo
import com.example.ui.components.RoleBadge
import com.example.ui.components.TeamBadge
import com.example.ui.theme.blackseaColors
import com.example.util.AppMessages
import com.example.util.sanitizeUserErrorMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    currentUserProfile: UserProfile?,
    repository: BlackseaRepository? = null,
    updateManager: com.example.update.AppUpdateManager? = null,
    onShowUpdateDialog: (com.example.update.AppReleaseInfo) -> Unit = {},
    onNavigateToAdmin: () -> Unit = {},
    onSignOut: () -> Unit,
    onAccountDeleted: () -> Unit = onSignOut,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val ext = MaterialTheme.blackseaColors
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showSignOutConfirmation by remember { mutableStateOf(false) }
    var showDeleteStep1 by remember { mutableStateOf(false) }
    var showDeleteStep2 by remember { mutableStateOf(false) }
    var isDeletingAccount by remember { mutableStateOf(false) }

    val isAdmin = currentUserProfile?.isManagerAccount() == true

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("profile_screen"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // User Profile Header
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, ext.cardBorder),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = if (ext.isDark) 2.dp else 1.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = CircleShape,
                    color = ext.accentBlueContainer,
                    modifier = Modifier.size(72.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = currentUserProfile?.getFullName()?.take(1)?.uppercase() ?: "U",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = ext.accentBlue
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = currentUserProfile?.getFullName() ?: "Takım Üyesi",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (currentUserProfile?.username?.isNotBlank() == true) {
                    Text(
                        text = "@${currentUserProfile.username}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = ext.accentBlue
                    )
                }

                Text(
                    text = currentUserProfile?.email ?: "",
                    fontSize = 13.sp,
                    color = ext.textSecondary
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TeamBadge(teamName = currentUserProfile?.team ?: "Yazılım Ekibi")
                }

                Spacer(modifier = Modifier.height(8.dp))

                com.example.ui.components.UserRolesRow(
                    roles = currentUserProfile?.roles.orEmpty().ifEmpty { listOf(currentUserProfile?.role ?: "Üye") }
                )

                if (repository != null && currentUserProfile != null) {
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedButton(
                        onClick = { showEditDialog = true },
                        modifier = Modifier.fillMaxWidth().height(42.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ext.accentBlue)
                    ) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Profili ve Ekibi Düzenle", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                if (isAdmin) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = onNavigateToAdmin,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ext.adminRedSolid,
                            contentColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth().height(42.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Yönetici Paneline Git", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Team & Platform Info
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, ext.cardBorder),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = if (ext.isDark) 2.dp else 1.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Takım Bilgileri",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.School,
                        contentDescription = null,
                        tint = ext.accentBlue,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(text = "Kurum / Okul", fontSize = 11.sp, color = ext.textMuted)
                        Text(
                            text = "Ünye Fen Lisesi Robotik Takımı",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = ext.amberText,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(text = "Resmi Takım Adı", fontSize = 11.sp, color = ext.textMuted)
                        Text(
                            text = "BLACKSEA ROBOTICS (BSR)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, ext.cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "\"ONE TEAM. ONE VISION. ONE ROBOT.\"",
                        color = ext.amberText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 10.dp)
                    )
                }
            }
        }

        // App Updates & Version Card
        val mgr = updateManager ?: remember { com.example.update.AppUpdateManager(context) }
        var isCheckingUpdate by remember { mutableStateOf(false) }
        var showHistoryDialog by remember { mutableStateOf(false) }
        var releaseHistory by remember { mutableStateOf<List<com.example.update.AppReleaseInfo>>(emptyList()) }
        var isLoadingHistory by remember { mutableStateOf(false) }
        var latestAvailableRelease by remember { mutableStateOf<com.example.update.AppReleaseInfo?>(null) }
        var updateStatusText by remember { mutableStateOf<String?>(null) }

        Card(
            modifier = Modifier.fillMaxWidth(),
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
                                    is com.example.update.UpdateCheckResult.UpdateAvailable -> {
                                        latestAvailableRelease = result.release
                                        updateStatusText = "Yeni sürüm bulundu: v${result.release.versionName}"
                                        onShowUpdateDialog(result.release)
                                    }
                                    is com.example.update.UpdateCheckResult.UpToDate -> {
                                        latestAvailableRelease = null
                                        updateStatusText = "Uygulamanız güncel (v${result.currentVersionName})"
                                        com.example.notifications.TeamNotificationDispatcher.clearPendingUpdateNotification(context)
                                        Toast.makeText(
                                            context,
                                            "Uygulamanız güncel (v${result.currentVersionName})",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                    is com.example.update.UpdateCheckResult.NoReleaseFound -> {
                                        latestAvailableRelease = null
                                        updateStatusText = "Uygulamanız güncel."
                                        com.example.notifications.TeamNotificationDispatcher.clearPendingUpdateNotification(context)
                                        Toast.makeText(
                                            context,
                                            "Uygulamanız güncel.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                    is com.example.update.UpdateCheckResult.ApkAssetMissing,
                                    is com.example.update.UpdateCheckResult.NetworkError,
                                    is com.example.update.UpdateCheckResult.ApiError -> {
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
                        modifier = Modifier.weight(1f).height(42.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ext.accentBlueSolid,
                            contentColor = Color.White
                        )
                    ) {
                        if (isCheckingUpdate) {
                            androidx.compose.material3.CircularProgressIndicator(
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
                        modifier = Modifier.height(42.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                    ) {
                        Text("Geçmiş", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                latestAvailableRelease?.let { pendingRelease ->
                    Button(
                        onClick = { onShowUpdateDialog(pendingRelease) },
                        modifier = Modifier.fillMaxWidth().height(42.dp),
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

        // Release History Dialog
        if (showHistoryDialog) {
            AlertDialog(
                onDismissRequest = { showHistoryDialog = false },
                title = {
                    Text("Sürüm Güncelleme Geçmişi", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                },
                text = {
                    if (isLoadingHistory) {
                        Box(modifier = Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
                            androidx.compose.material3.CircularProgressIndicator(color = ext.accentBlue)
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

        // Sign Out Button
        OutlinedButton(
            onClick = {
                showSignOutConfirmation = true
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("sign_out_button"),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, ext.dangerText.copy(alpha = 0.45f)),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = ext.dangerText
            )
        ) {
            Icon(
                imageVector = Icons.Default.ExitToApp,
                contentDescription = null,
                tint = ext.dangerText
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Oturumu Kapat",
                fontWeight = FontWeight.Bold,
                color = ext.dangerText
            )
        }

        if (repository != null) {
            TextButton(
                onClick = { showDeleteStep1 = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("delete_own_account_button"),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = ext.dangerText
                )
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteForever,
                    contentDescription = null,
                    tint = ext.dangerText,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Hesabımı Kalıcı Olarak Sil",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ext.dangerText
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "BLACKSEA ROBOTICS Platformu • v${mgr.getCurrentVersionName()}",
            fontSize = 11.sp,
            color = ext.textMuted
        )
    }

    if (showDeleteStep1) {
        AlertDialog(
            onDismissRequest = { showDeleteStep1 = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.DeleteForever,
                    contentDescription = null,
                    tint = ext.dangerText,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text("Hesabınızı Silmek İstiyor musunuz?", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "Hesabınız ve üyelik profiliniz sistemden silinecektir. Yanlışlıkla silmeyi önlemek için bir sonraki adımda tekrar onayınız istenecektir.",
                    color = ext.textSecondary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteStep1 = false
                        showDeleteStep2 = true
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ext.dangerSolid,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("delete_own_account_step1_confirm")
                ) {
                    Text("Devam Et", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteStep1 = false }) {
                    Text("Vazgeç")
                }
            }
        )
    }

    if (showDeleteStep2 && repository != null) {
        AlertDialog(
            onDismissRequest = { if (!isDeletingAccount) showDeleteStep2 = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.DeleteForever,
                    contentDescription = null,
                    tint = ext.dangerText,
                    modifier = Modifier.size(34.dp)
                )
            },
            title = {
                Text(
                    "Son Onay: Hesap Kalıcı Olarak Silinecek",
                    fontWeight = FontWeight.Bold,
                    color = ext.dangerText
                )
            },
            text = {
                Text(
                    "Bu işlem geri alınamaz! Onayladığınızda Blacksea Robotics hesabınız kalıcı olarak silinecek ve oturumunuz kapatılacaktır. Emin misiniz?",
                    color = ext.textSecondary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        isDeletingAccount = true
                        scope.launch {
                            try {
                                repository.deleteOwnAccount()
                                showDeleteStep2 = false
                                Toast.makeText(context, "Hesabınız kalıcı olarak silindi.", Toast.LENGTH_LONG).show()
                                onAccountDeleted()
                            } catch (ce: CancellationException) {
                                throw ce
                            } catch (e: Exception) {
                                Toast.makeText(
                                    context,
                                    sanitizeUserErrorMessage(e, "Hesap silinemedi. Lütfen tekrar deneyin."),
                                    Toast.LENGTH_LONG
                                ).show()
                            } finally {
                                isDeletingAccount = false
                            }
                        }
                    },
                    enabled = !isDeletingAccount,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ext.dangerSolid,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("delete_own_account_step2_confirm")
                ) {
                    if (isDeletingAccount) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Evet, Hesabımı Sil", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteStep2 = false },
                    enabled = !isDeletingAccount
                ) {
                    Text("İptal")
                }
            }
        )
    }

    if (showSignOutConfirmation) {
        AlertDialog(
            onDismissRequest = { showSignOutConfirmation = false },
            title = {
                Text("Oturumu Kapat", fontWeight = FontWeight.Bold)
            },
            text = {
                Text("Oturumu kapatmak istediğinize emin misiniz? Tekrar giriş yapmanız gerekecektir.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSignOutConfirmation = false
                        appSignOut(
                            credentialManager = credentialManager,
                            scope = scope,
                            onSignOutComplete = onSignOut
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ext.dangerSolid,
                        contentColor = Color.White
                    )
                ) {
                    Text("Çıkış Yap", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutConfirmation = false }) {
                    Text("İptal")
                }
            }
        )
    }

    if (showEditDialog && currentUserProfile != null && repository != null) {
        var editFirstName by remember { mutableStateOf(currentUserProfile.firstName) }
        var editLastName by remember { mutableStateOf(currentUserProfile.lastName) }
        var editUsername by remember { mutableStateOf(currentUserProfile.username) }
        var editTeam by remember { mutableStateOf(currentUserProfile.team) }
        var teamExpanded by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = {
                Text("Profili Düzenle", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editFirstName,
                        onValueChange = { editFirstName = it },
                        label = { Text("Ad") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    OutlinedTextField(
                        value = editLastName,
                        onValueChange = { editLastName = it },
                        label = { Text("Soyad") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    OutlinedTextField(
                        value = editUsername,
                        onValueChange = { editUsername = it },
                        label = { Text("Kullanıcı Adı") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    ExposedDropdownMenuBox(
                        expanded = teamExpanded,
                        onExpandedChange = { teamExpanded = !teamExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = editTeam,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Ekip") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = teamExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            shape = RoundedCornerShape(10.dp)
                        )

                        ExposedDropdownMenu(
                            expanded = teamExpanded,
                            onDismissRequest = { teamExpanded = false }
                        ) {
                            listOf(
                                TeamType.MANAGEMENT,
                                TeamType.SOFTWARE,
                                TeamType.MECHANIC,
                                TeamType.PR
                            ).forEach { team ->
                                DropdownMenuItem(
                                    text = { Text(team.displayName) },
                                    onClick = {
                                        editTeam = team.displayName
                                        teamExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmedFirst = editFirstName.trim()
                        val trimmedLast = editLastName.trim()
                        if (trimmedFirst.isNotBlank() && trimmedLast.isNotBlank()) {
                            scope.launch {
                                try {
                                    repository.updateOwnProfile(
                                        userId = currentUserProfile.userId.ifBlank { currentUserProfile.id },
                                        firstName = trimmedFirst,
                                        lastName = trimmedLast,
                                        username = editUsername.trim(),
                                        team = editTeam
                                    )
                                    showEditDialog = false
                                    Toast.makeText(context, "Profil güncellendi!", Toast.LENGTH_SHORT).show()
                                } catch (ce: CancellationException) {
                                    throw ce
                                } catch (e: Exception) {
                                    Toast.makeText(context, sanitizeUserErrorMessage(e), Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ext.accentBlueSolid,
                        contentColor = Color.White
                    )
                ) {
                    Text("Kaydet", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("İptal")
                }
            }
        )
    }
}
