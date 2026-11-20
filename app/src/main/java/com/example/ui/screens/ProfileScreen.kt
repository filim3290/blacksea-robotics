package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SwapHoriz
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
import androidx.compose.material3.MenuAnchorType
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
import com.example.model.TeamInfo
import com.example.model.TeamMembership
import com.example.model.TeamType
import com.example.model.UserProfile
import com.example.ui.components.AppUpdatesCard
import com.example.ui.components.EditProfileDialog
import com.example.ui.components.TeamBadge
import com.example.ui.components.TeamLogoBadge
import com.example.ui.components.UserRolesRow
import com.example.ui.components.parseTeamColor
import com.example.ui.theme.blackseaColors
import com.example.util.sanitizeUserErrorMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    currentUserProfile: UserProfile?,
    activeTeam: TeamInfo = TeamInfo.mainBlackseaTeam(),
    activeMembership: TeamMembership? = null,
    repository: BlackseaRepository? = null,
    updateManager: com.example.update.AppUpdateManager? = null,
    onShowUpdateDialog: (com.example.update.AppReleaseInfo) -> Unit = {},
    onNavigateToAdmin: () -> Unit = {},
    onSwitchTeam: () -> Unit = {},
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
    var showLeaveTeamStep1 by remember { mutableStateOf(false) }
    var showLeaveTeamStep2 by remember { mutableStateOf(false) }
    var isLeavingTeam by remember { mutableStateOf(false) }
    var showDeleteStep1 by remember { mutableStateOf(false) }
    var showDeleteStep2 by remember { mutableStateOf(false) }
    var isDeletingAccount by remember { mutableStateOf(false) }

    val isAdmin = activeMembership?.isManagerInTeam() == true || currentUserProfile?.isManagerAccount() == true
    val activeSubTeam = activeMembership?.subTeam?.ifBlank { currentUserProfile?.team }
        ?: (currentUserProfile?.team ?: TeamType.GENERAL.displayName)
    val activeRoles = activeMembership?.roles?.ifEmpty { currentUserProfile?.roles.orEmpty() }
        ?: currentUserProfile?.roles.orEmpty().ifEmpty { listOf(currentUserProfile?.role ?: "Takım Üyesi") }
    val teamAccent = parseTeamColor(activeTeam.accentColorHex, ext.accentBlue)
    val canLeaveCurrentTeam = repository != null && activeMembership != null

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
                    TeamBadge(teamName = activeSubTeam)
                }

                Spacer(modifier = Modifier.height(8.dp))

                UserRolesRow(roles = activeRoles)

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
                        Text(
                            text = if (currentUserProfile?.isPlatformManagerAccount() == true) {
                                "Yönetici Paneline Git"
                            } else {
                                "Yetkili Öğretmen Paneline Git"
                            },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Active Team & Switcher Card
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TeamLogoBadge(team = activeTeam, size = 44)
                        Column {
                            Text(
                                text = "Aktif Çalışma Takımı",
                                fontSize = 11.sp,
                                color = ext.textMuted
                            )
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = activeTeam.name.ifBlank { "BlackSea Robotics" },
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (activeTeam.shortCode.isNotBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = teamAccent.copy(alpha = 0.14f)
                                    ) {
                                        Text(
                                            text = activeTeam.shortCode,
                                            color = teamAccent,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (activeTeam.schoolName.isNotBlank() || activeTeam.city.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.School,
                            contentDescription = null,
                            tint = teamAccent,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(text = "Kurum / Okul ve Şehir", fontSize = 11.sp, color = ext.textMuted)
                            Text(
                                text = listOf(activeTeam.schoolName, activeTeam.city).filter { it.isNotBlank() }.joinToString(" • "),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                if (activeTeam.competition.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = ext.amberText,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(text = "Yarışma / Alan", fontSize = 11.sp, color = ext.textMuted)
                            Text(
                                text = activeTeam.competition,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                val bannerQuote = activeTeam.motto.ifBlank { activeTeam.description }
                if (bannerQuote.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, ext.cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = bannerQuote,
                            color = ext.amberText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp)
                        )
                    }
                }

                OutlinedButton(
                    onClick = onSwitchTeam,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("profile_switch_team_button"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = teamAccent)
                ) {
                    Icon(imageVector = Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Takım Değiştir / Tüm Takımlar", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                if (canLeaveCurrentTeam) {
                    OutlinedButton(
                        onClick = { showLeaveTeamStep1 = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 44.dp)
                            .testTag("leave_team_button"),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, ext.dangerText.copy(alpha = 0.45f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ext.dangerText)
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Ekipten Ayrıl (Takım Kaydımı Sil)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // App Updates & Version Card
        val mgr = updateManager ?: remember { com.example.update.AppUpdateManager(context) }
        AppUpdatesCard(
            updateManager = mgr,
            onShowUpdateDialog = onShowUpdateDialog
        )

        com.example.ui.components.SupportCenterButtonCard(
            currentUserProfile = currentUserProfile,
            teamId = activeTeam.id,
            teamName = activeTeam.name,
            userSubTeam = activeSubTeam,
            repository = repository,
            updateManager = mgr
        )

        // Sign Out Button
        OutlinedButton(
            onClick = { showSignOutConfirmation = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("sign_out_button"),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, ext.dangerText.copy(alpha = 0.45f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = ext.dangerText)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ExitToApp,
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
                colors = ButtonDefaults.textButtonColors(contentColor = ext.dangerText)
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
            text = "${activeTeam.name.uppercase()} • BlackSea Robotics Platformu v${mgr.getCurrentVersionName()}",
            fontSize = 11.sp,
            color = ext.textMuted,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp).navigationBarsPadding())
    }

    if (showLeaveTeamStep1 && repository != null) {
        AlertDialog(
            onDismissRequest = { showLeaveTeamStep1 = false },
            icon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Logout,
                    contentDescription = null,
                    tint = ext.dangerText,
                    modifier = Modifier.size(30.dp)
                )
            },
            title = { Text("${activeTeam.name} Ekibinden Ayrılmak İstiyor musunuz?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "${activeTeam.name} ekibindeki üyelik ve rol kaydınız silinecektir. Diğer takımlardaki üyelikleriniz ve Google hesabınız korunur. Devam etmek için sonraki adımda son onay istenecektir.",
                    color = ext.textSecondary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLeaveTeamStep1 = false
                        showLeaveTeamStep2 = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ext.dangerSolid, contentColor = Color.White),
                    modifier = Modifier.testTag("leave_team_step1_confirm")
                ) {
                    Text("Devam Et", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveTeamStep1 = false }) {
                    Text("Vazgeç")
                }
            }
        )
    }

    if (showLeaveTeamStep2 && repository != null) {
        AlertDialog(
            onDismissRequest = { if (!isLeavingTeam) showLeaveTeamStep2 = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.DeleteForever,
                    contentDescription = null,
                    tint = ext.dangerText,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    "Son Onay: Takım Kaydınız Silinecek",
                    fontWeight = FontWeight.Bold,
                    color = ext.dangerText
                )
            },
            text = {
                Text(
                    "Onayladığınızda ${activeTeam.name} ekibinden ayrılacak ve bu takımdaki üyelik kaydınızı kalıcı olarak sileceksiniz. Yeniden katılmak isterseniz tekrar başvuru göndermeniz gerekir. Emin misiniz?",
                    color = ext.textSecondary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        isLeavingTeam = true
                        scope.launch {
                            val res = repository.leaveTeam(activeTeam.id)
                            isLeavingTeam = false
                            if (res.isSuccess) {
                                showLeaveTeamStep2 = false
                                Toast.makeText(context, "${activeTeam.name} ekibinden ayrıldınız ve takım kaydınız silindi.", Toast.LENGTH_LONG).show()
                                onSwitchTeam()
                            } else {
                                Toast.makeText(
                                    context,
                                    sanitizeUserErrorMessage(res.exceptionOrNull()),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    },
                    enabled = !isLeavingTeam,
                    colors = ButtonDefaults.buttonColors(containerColor = ext.dangerSolid, contentColor = Color.White),
                    modifier = Modifier.testTag("leave_team_step2_confirm")
                ) {
                    if (isLeavingTeam) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Evet, Ekipten Ayrıl", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveTeamStep2 = false }, enabled = !isLeavingTeam) {
                    Text("İptal")
                }
            }
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
                    "Hesabınız ve tüm takım üyelikleriniz sistemden silinecektir. Yanlışlıkla silmeyi önlemek için bir sonraki adımda tekrar onayınız istenecektir.",
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
                    "Bu işlem geri alınamaz! Onayladığınızda platform hesabınız kalıcı olarak silinecek ve oturumunuz kapatılacaktır. Emin misiniz?",
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
        var isSavingProfile by remember { mutableStateOf(false) }
        EditProfileDialog(
            currentProfile = currentUserProfile,
            activeSubTeam = activeSubTeam,
            showSubTeamPicker = true,
            isSaving = isSavingProfile,
            onDismiss = { if (!isSavingProfile) showEditDialog = false },
            onSave = { trimmedFirst, trimmedLast, trimmedUsername, selectedTeam ->
                val uid = currentUserProfile.userId.ifBlank { currentUserProfile.id }
                isSavingProfile = true
                scope.launch {
                    try {
                        repository.updateUserDisplayAcrossTeams(
                            userId = uid,
                            firstName = trimmedFirst,
                            lastName = trimmedLast,
                            username = trimmedUsername,
                            activeTeamId = activeTeam.id,
                            subTeam = selectedTeam
                        )
                        showEditDialog = false
                        Toast.makeText(context, "Profil güncellendi!", Toast.LENGTH_SHORT).show()
                    } catch (ce: CancellationException) {
                        throw ce
                    } catch (e: Exception) {
                        Toast.makeText(context, sanitizeUserErrorMessage(e), Toast.LENGTH_LONG).show()
                    } finally {
                        isSavingProfile = false
                    }
                }
            }
        )
    }
}
