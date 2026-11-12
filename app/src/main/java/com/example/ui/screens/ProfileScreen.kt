package com.example.ui.screens

import android.widget.Toast
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
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.PhoenixAmber
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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showSignOutConfirmation by remember { mutableStateOf(false) }

    val isAdmin = currentUserProfile?.isSuperAdmin == true ||
            currentUserProfile?.email.equals("filim3290@gmail.com", ignoreCase = true) ||
            currentUserProfile?.roles?.any { it in listOf("Yönetici", "Takım Kaptanı", "Yetkili Öğretmen") } == true

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
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = CircleShape,
                    color = ElectricBlue.copy(alpha = 0.2f),
                    modifier = Modifier.size(72.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = currentUserProfile?.getFullName()?.take(1)?.uppercase() ?: "U",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = ElectricBlue
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
                        color = ElectricBlue
                    )
                }

                Text(
                    text = currentUserProfile?.email ?: "",
                    fontSize = 13.sp,
                    color = Color(0xFF94A3B8)
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
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Profili ve Ekibi Düzenle", fontSize = 13.sp)
                    }
                }

                if (isAdmin) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = onNavigateToAdmin,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE11D48)),
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
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                        tint = ElectricBlue,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(text = "Kurum / Okul", fontSize = 11.sp, color = Color(0xFF94A3B8))
                        Text(
                            text = "Ünye Fen Lisesi Robotik Takımı",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
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
                        tint = PhoenixAmber,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(text = "Resmi Takım Adı", fontSize = 11.sp, color = Color(0xFF94A3B8))
                        Text(
                            text = "BLACKSEA ROBOTICS (BSR)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "\"ONE TEAM. ONE VISION. ONE ROBOT.\"",
                        color = PhoenixAmber,
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

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                        Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = ElectricBlue)
                        Text(
                            text = "Uygulama & Güncellemeler",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = ElectricBlue.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "v${mgr.getCurrentVersionName()} (${mgr.getCurrentVersionCode()})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = ElectricBlue,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Text(
                    text = "Blacksea Robotics APK güncellemelerini doğrudan GitHub Releases üzerinden otomatik denetler.",
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8)
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            isCheckingUpdate = true
                            scope.launch {
                                val result = mgr.checkLatestRelease(forceRefresh = true)
                                isCheckingUpdate = false
                                result.fold(
                                    onSuccess = { release ->
                                        if (release != null) {
                                            onShowUpdateDialog(release)
                                        } else {
                                            Toast.makeText(context, "Uygulamanız güncel! En son sürümü kullanıyorsunuz.", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onFailure = { err ->
                                        Toast.makeText(context, "Güncelleme kontrol edilemedi. İnternet bağlantınızı kontrol edin.", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        },
                        enabled = !isCheckingUpdate,
                        modifier = Modifier.weight(1f).height(42.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
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
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Geçmiş", fontSize = 12.sp)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val downloadUrl = "https://github.com/${mgr.githubOwner}/${mgr.githubRepo}/releases/latest"
                            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_SUBJECT, "Blacksea Robotics Android APK İndir")
                                putExtra(
                                    android.content.Intent.EXTRA_TEXT,
                                    "🤖 Blacksea Robotics Android uygulamasını doğrudan indirmek için tıkla:\n$downloadUrl"
                                )
                            }
                            context.startActivity(android.content.Intent.createChooser(shareIntent, "Uygulama İndirme Bağlantısını Paylaş"))
                        },
                        modifier = Modifier.weight(1f).height(38.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.ExitToApp, contentDescription = null, modifier = Modifier.size(16.dp), tint = ElectricBlue)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("İndirme Linkini Paylaş", fontSize = 11.sp, color = ElectricBlue)
                    }

                    OutlinedButton(
                        onClick = {
                            val browserIntent = android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse("https://github.com/${mgr.githubOwner}/${mgr.githubRepo}/releases/latest")
                            )
                            context.startActivity(browserIntent)
                        },
                        modifier = Modifier.weight(1f).height(38.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF10B981))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Web'den İndir", fontSize = 11.sp, color = Color(0xFF10B981))
                    }
                }

                TextButton(
                    onClick = {
                        val sim = mgr.getSimulatedReleaseInfo()
                        onShowUpdateDialog(sim)
                    },
                    modifier = Modifier.fillMaxWidth().height(36.dp)
                ) {
                    Text("🚀 Güncelleme Ekranını Önizle (Test)", fontSize = 11.sp, color = ElectricBlue)
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
                            androidx.compose.material3.CircularProgressIndicator(color = ElectricBlue)
                        }
                    } else if (releaseHistory.isEmpty()) {
                        Text("Henüz yayınlanmış bir sürüm geçmişi bulunamadı.", fontSize = 13.sp, color = Color(0xFF94A3B8))
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
                                                color = ElectricBlue
                                            )
                                            if (item.versionCode == mgr.getCurrentVersionCode()) {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = Color(0xFF10B981)
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
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = Color(0xFFEF4444)
            )
        ) {
            Icon(
                imageVector = Icons.Default.ExitToApp,
                contentDescription = null,
                tint = Color(0xFFEF4444)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Oturumu Kapat",
                fontWeight = FontWeight.Bold,
                color = Color(0xFFEF4444)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "BLACKSEA ROBOTICS Platformu • v${mgr.getCurrentVersionName()}",
            fontSize = 11.sp,
            color = Color(0xFF94A3B8)
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
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("Çıkış Yap", color = Color.White)
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
                                    val currentRoles = currentUserProfile.roles.ifEmpty {
                                        listOf(PredefinedRoles.getDefaultTeamRole(editTeam))
                                    }
                                    repository.createOrUpdateUserProfile(
                                        userId = currentUserProfile.userId.ifBlank { currentUserProfile.id },
                                        firstName = trimmedFirst,
                                        lastName = trimmedLast,
                                        username = editUsername.trim(),
                                        email = currentUserProfile.email,
                                        team = editTeam,
                                        roles = currentRoles,
                                        photoUrl = currentUserProfile.photoUrl
                                    )
                                    showEditDialog = false
                                    Toast.makeText(context, "Profil güncellendi!", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Hata: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                ) {
                    Text("Kaydet")
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
