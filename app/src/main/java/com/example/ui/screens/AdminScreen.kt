package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BlackseaRepository
import com.example.model.PredefinedRoles
import com.example.model.TeamType
import com.example.model.UserProfile
import com.example.model.UserStatus
import com.example.ui.components.RoleBadge
import com.example.ui.components.TeamBadge
import com.example.ui.components.UserRolesRow
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.PhoenixAmber
import kotlinx.coroutines.launch

@Composable
fun AdminScreen(
    repository: BlackseaRepository,
    currentUserProfile: UserProfile?,
    updateManager: com.example.update.AppUpdateManager? = null,
    onShowUpdateDialog: (com.example.update.AppReleaseInfo) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val allUsers by repository.observeAllUsers().collectAsState(initial = emptyList())
    val announcements by repository.observeAnnouncements().collectAsState(initial = emptyList())

    val pendingUsers = allUsers.filter { it.status == UserStatus.PENDING.value }
    val approvedUsers = allUsers.filter { it.status != UserStatus.PENDING.value && it.status != UserStatus.REJECTED.value }

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = remember(pendingUsers.size) {
        listOf("Kullanıcılar", "Onaylar (${pendingUsers.size})", "Bildirim Gönder", "Duyurular", "Uygulama Güncellemesi")
    }

    // Broadcast Notification State
    var broadcastTitle by remember { mutableStateOf("") }
    var broadcastMessage by remember { mutableStateOf("") }
    var isSendingBroadcast by remember { mutableStateOf(false) }

    // Edit Member Dialog State
    var memberToEdit by remember { mutableStateOf<UserProfile?>(null) }
    var showNewAnnouncementDialog by remember { mutableStateOf(false) }
    var showBroadcastUpdateDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("admin_screen")
    ) {
        // Admin Header
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 2.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = Color(0xFFE11D48),
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Yönetici & Rol Paneli",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Üyelerin gerçek isimlerini, ekiplerini, özel rollerini ve hesap durumlarını yönetin.",
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8)
                )
            }
        }

        // Tabs
        ScrollableTabRow(
            selectedTabIndex = selectedTabIndex,
            edgePadding = 16.dp,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = {
                        val count = when (index) {
                            0 -> " (${approvedUsers.size})"
                            1 -> if (pendingUsers.isNotEmpty()) " (${pendingUsers.size})" else ""
                            else -> ""
                        }
                        Text(
                            text = "$title$count",
                            fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTabIndex == index) ElectricBlue else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
            }
        }

        when (selectedTabIndex) {
            0 -> {
                // Approved Members List with Multi-Roles
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(approvedUsers) { user ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = ElectricBlue.copy(alpha = 0.2f),
                                        modifier = Modifier.size(42.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = user.getFullName().take(1).uppercase(),
                                                fontWeight = FontWeight.Bold,
                                                color = ElectricBlue,
                                                fontSize = 16.sp
                                            )
                                        }
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = user.getFullName(),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        )
                                        Text(
                                            text = "${user.email} • @${user.username.ifBlank { "bsr_uye" }}",
                                            fontSize = 11.sp,
                                            color = Color(0xFF94A3B8)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            TeamBadge(teamName = user.team)
                                            UserRolesRow(roles = user.roles.ifEmpty { listOf(user.role) })
                                        }
                                    }
                                }

                                IconButton(onClick = { memberToEdit = user }) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Rol ve Ekip Düzenle",
                                        tint = ElectricBlue
                                    )
                                }
                            }
                        }
                    }
                }
            }

            1 -> {
                // Pending Approvals
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (pendingUsers.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Onay bekleyen yeni üye kaydı bulunmuyor.",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }

                    items(pendingUsers) { user ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(text = user.getFullName(), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text(text = "${user.email} • @${user.username}", fontSize = 12.sp, color = Color(0xFF94A3B8))
                                Spacer(modifier = Modifier.height(6.dp))
                                TeamBadge(teamName = user.team)

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            scope.launch {
                                                repository.updateUserRolesAndTeam(
                                                    targetUserId = user.userId.ifBlank { user.id },
                                                    team = user.team,
                                                    roles = user.roles,
                                                    status = UserStatus.REJECTED.value
                                                )
                                                Toast.makeText(context, "Kullanıcı başvurusu reddedildi", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.Close, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Reddet", color = Color(0xFFEF4444))
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Button(
                                        onClick = {
                                            scope.launch {
                                                repository.updateUserRolesAndTeam(
                                                    targetUserId = user.userId.ifBlank { user.id },
                                                    team = user.team,
                                                    roles = user.roles.ifEmpty { listOf(PredefinedRoles.getDefaultTeamRole(user.team)) },
                                                    status = UserStatus.APPROVED.value
                                                )
                                                Toast.makeText(context, "Kullanıcı onaylandı!", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                                    ) {
                                        Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Onayla")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            2 -> {
                // Send Broadcast Notification to Entire Team
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = ElectricBlue.copy(alpha = 0.15f),
                                        modifier = Modifier.size(42.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Campaign,
                                                contentDescription = null,
                                                tint = ElectricBlue,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                    Column {
                                        Text(
                                            text = "Herkese Bildirim Gönder",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Tüm takım üyelerine anlık bildirim ve sistem duyurusu iletilir.",
                                            fontSize = 12.sp,
                                            color = Color(0xFF94A3B8)
                                        )
                                    }
                                }

                                OutlinedTextField(
                                    value = broadcastTitle,
                                    onValueChange = { broadcastTitle = it },
                                    label = { Text("Bildirim Başlığı") },
                                    placeholder = { Text("Örn: Yarın Acil Takım Toplantısı") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    singleLine = true
                                )

                                OutlinedTextField(
                                    value = broadcastMessage,
                                    onValueChange = { broadcastMessage = it },
                                    label = { Text("Bildirim Metni / Açıklama") },
                                    placeholder = { Text("Örn: Yarın saat 15:30'da tüm ekipler robot test sahasında toplanacaktır.") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(130.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    maxLines = 5
                                )

                                Button(
                                    onClick = {
                                        if (broadcastTitle.isBlank() || broadcastMessage.isBlank()) {
                                            Toast.makeText(context, "Lütfen başlık ve mesaj metnini doldurun.", Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }
                                        scope.launch {
                                            isSendingBroadcast = true
                                            try {
                                                repository.sendBroadcastNotification(
                                                    title = broadcastTitle.trim(),
                                                    message = broadcastMessage.trim(),
                                                    senderName = currentUserProfile?.getFullName() ?: "Yönetici"
                                                )
                                                broadcastTitle = ""
                                                broadcastMessage = ""
                                                Toast.makeText(context, "Bildirim tüm takıma başarıyla gönderildi!", Toast.LENGTH_LONG).show()
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Gönderilemedi: ${e.message}", Toast.LENGTH_SHORT).show()
                                            } finally {
                                                isSendingBroadcast = false
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                                    shape = RoundedCornerShape(10.dp),
                                    enabled = !isSendingBroadcast
                                ) {
                                    if (isSendingBroadcast) {
                                        CircularProgressIndicator(
                                            color = Color.White,
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(imageVector = Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Tüm Takıma Bildirimi Gönder", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            3 -> {
                // Announcements Section
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { showNewAnnouncementDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Campaign, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Yeni Takım Duyurusu Paylaş")
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(announcements) { announcement ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(text = announcement.title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                        IconButton(
                                            onClick = {
                                                scope.launch {
                                                    repository.deleteAnnouncement(announcement.id)
                                                    Toast.makeText(context, "Duyuru silindi", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        ) {
                                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Sil", tint = Color(0xFFEF4444))
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(text = announcement.content, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            4 -> {
                // Tab 4: Uygulama Güncellemesi (App Release & OTA Status)
                val mgr = updateManager ?: remember { com.example.update.AppUpdateManager(context) }
                var latestRelease by remember { mutableStateOf<com.example.update.AppReleaseInfo?>(null) }
                var isChecking by remember { mutableStateOf(false) }
                var checkStatusMessage by remember { mutableStateOf<String?>(null) }

                LaunchedEffect(Unit) {
                    isChecking = true
                    val res = mgr.checkLatestReleaseDetailed(forceRefresh = false)
                    isChecking = false
                    when (res) {
                        is com.example.update.UpdateCheckResult.UpdateAvailable -> {
                            latestRelease = res.release
                            checkStatusMessage = "Yeni sürüm bulundu: v${res.release.versionName} (Kod: ${res.release.versionCode})"
                        }
                        is com.example.update.UpdateCheckResult.UpToDate -> {
                            latestRelease = res.latestRemote
                            checkStatusMessage = "Uygulama güncel (v${res.currentVersionName} • Kod: ${res.currentVersionCode})"
                        }
                        is com.example.update.UpdateCheckResult.NoReleaseFound -> {
                            checkStatusMessage = "GitHub'da yayınlanmış sürüm bulunamadı."
                        }
                        is com.example.update.UpdateCheckResult.ApkAssetMissing -> {
                            checkStatusMessage = "APK dosyası bulunamadı (${res.tagName})."
                        }
                        is com.example.update.UpdateCheckResult.NetworkError -> {
                            checkStatusMessage = "İnternet bağlantısı hatası: ${res.detail}"
                        }
                        is com.example.update.UpdateCheckResult.ApiError -> {
                            checkStatusMessage = "GitHub API hatası (${res.httpCode}): ${res.detail}"
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Mevcut Kurulu Sürüm", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = ElectricBlue.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = "v${mgr.getCurrentVersionName()} (Code: ${mgr.getCurrentVersionCode()})",
                                            color = ElectricBlue,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                var showRepoEditDialog by remember { mutableStateOf(false) }
                                var repoOwnerInput by remember { mutableStateOf(mgr.githubOwner) }
                                var repoNameInput by remember { mutableStateOf(mgr.githubRepo) }
                                var repoTokenInput by remember { mutableStateOf(mgr.githubToken) }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("GitHub Releases Dağıtımı", fontSize = 13.sp, color = Color(0xFF94A3B8))
                                        Text("${mgr.githubOwner}/${mgr.githubRepo}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ElectricBlue)
                                    }
                                    TextButton(onClick = {
                                        repoOwnerInput = mgr.githubOwner
                                        repoNameInput = mgr.githubRepo
                                        repoTokenInput = mgr.githubToken
                                        showRepoEditDialog = true
                                    }) {
                                        Text("Değiştir", fontSize = 12.sp, color = ElectricBlue)
                                    }
                                }

                                if (showRepoEditDialog) {
                                    AlertDialog(
                                        onDismissRequest = { showRepoEditDialog = false },
                                        title = { Text("GitHub Repository Ayarla", fontWeight = FontWeight.Bold) },
                                        text = {
                                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Text("APK güncellemelerinin kontrol edileceği GitHub kullanıcı ve repo adınızı girin:", fontSize = 12.sp, color = Color(0xFF94A3B8))
                                                OutlinedTextField(
                                                    value = repoOwnerInput,
                                                    onValueChange = { repoOwnerInput = it },
                                                    label = { Text("Kullanıcı / Organizasyon Adı") },
                                                    placeholder = { Text("filim3290") },
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                                OutlinedTextField(
                                                    value = repoNameInput,
                                                    onValueChange = { repoNameInput = it },
                                                    label = { Text("Repository Adı") },
                                                    placeholder = { Text("blacksea-robotics") },
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                                OutlinedTextField(
                                                    value = repoTokenInput,
                                                    onValueChange = { repoTokenInput = it },
                                                    label = { Text("GitHub Token (İsteğe bağlı / Private Repo için)") },
                                                    placeholder = { Text("ghp_...") },
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                        },
                                        confirmButton = {
                                            Button(onClick = {
                                                if (repoOwnerInput.isNotBlank() && repoNameInput.isNotBlank()) {
                                                    mgr.githubOwner = repoOwnerInput.trim()
                                                    mgr.githubRepo = repoNameInput.trim()
                                                    mgr.githubToken = repoTokenInput.trim()
                                                    showRepoEditDialog = false
                                                    Toast.makeText(context, "GitHub repository güncellendi!", Toast.LENGTH_SHORT).show()
                                                }
                                            }) {
                                                Text("Kaydet")
                                            }
                                        },
                                        dismissButton = {
                                            Row {
                                                TextButton(onClick = {
                                                    mgr.resetToDefaultRepo()
                                                    repoOwnerInput = mgr.githubOwner
                                                    repoNameInput = mgr.githubRepo
                                                    Toast.makeText(context, "Varsayılan depo geri yüklendi!", Toast.LENGTH_SHORT).show()
                                                }) {
                                                    Text("Varsayılana Sıfırla", color = PhoenixAmber)
                                                }
                                                TextButton(onClick = { showRepoEditDialog = false }) {
                                                    Text("İptal")
                                                }
                                            }
                                        }
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Güncelleme Durumu", fontSize = 13.sp, color = Color(0xFF94A3B8))
                                    Text(
                                        text = checkStatusMessage ?: "Kontrol ediliyor...",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (latestRelease != null) Color(0xFF10B981) else Color(0xFF94A3B8)
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Button(
                                    onClick = {
                                        isChecking = true
                                        checkStatusMessage = "Kontrol ediliyor..."
                                        scope.launch {
                                            val res = mgr.checkLatestReleaseDetailed(forceRefresh = true)
                                            isChecking = false
                                            when (res) {
                                                is com.example.update.UpdateCheckResult.UpdateAvailable -> {
                                                    latestRelease = res.release
                                                    checkStatusMessage = "Yeni sürüm: v${res.release.versionName} (Kod: ${res.release.versionCode})"
                                                    onShowUpdateDialog(res.release)
                                                }
                                                is com.example.update.UpdateCheckResult.UpToDate -> {
                                                    latestRelease = res.latestRemote
                                                    checkStatusMessage = "Uygulama güncel (v${res.currentVersionName} • Kod: ${res.currentVersionCode})"
                                                    Toast.makeText(context, "Uygulamanız güncel! (v${res.currentVersionName})", Toast.LENGTH_SHORT).show()
                                                }
                                                is com.example.update.UpdateCheckResult.NoReleaseFound -> {
                                                    checkStatusMessage = "Yeni sürüm bulunamadı."
                                                    Toast.makeText(context, "GitHub'da yayınlanmış sürüm bulunamadı.", Toast.LENGTH_LONG).show()
                                                }
                                                is com.example.update.UpdateCheckResult.ApkAssetMissing -> {
                                                    checkStatusMessage = "APK bulunamadı (${res.tagName})."
                                                    Toast.makeText(context, "Sürüm (${res.tagName}) bulundu ancak APK dosyası eklenmemiş.", Toast.LENGTH_LONG).show()
                                                }
                                                is com.example.update.UpdateCheckResult.NetworkError -> {
                                                    checkStatusMessage = "İnternet bağlantısı hatası."
                                                    Toast.makeText(context, res.detail, Toast.LENGTH_LONG).show()
                                                }
                                                is com.example.update.UpdateCheckResult.ApiError -> {
                                                    checkStatusMessage = "GitHub API hatası (${res.httpCode})."
                                                    Toast.makeText(context, res.detail, Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        }
                                    },
                                    enabled = !isChecking,
                                    modifier = Modifier.fillMaxWidth().height(44.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                                ) {
                                    if (isChecking) {
                                        androidx.compose.material3.CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            color = Color.White,
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Text("Releases & Veritabanını Kontrol Et", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }

                                Button(
                                    onClick = { showBroadcastUpdateDialog = true },
                                    modifier = Modifier.fillMaxWidth().height(42.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = PhoenixAmber)
                                ) {
                                    Text("Yeni Sürüm Yayınla", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                }

                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            val res = mgr.resolvePublicDownloadUrl()
                                            res.fold(
                                                onSuccess = { targetUrl ->
                                                    try {
                                                        val browserIntent = android.content.Intent(
                                                            android.content.Intent.ACTION_VIEW,
                                                            android.net.Uri.parse(targetUrl)
                                                        )
                                                        context.startActivity(browserIntent)
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, "Tarayıcı açılamadı.", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                onFailure = { err ->
                                                    Toast.makeText(context, err.message ?: "Releases sayfası doğrulanamadı.", Toast.LENGTH_LONG).show()
                                                }
                                            )
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(40.dp),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("GitHub Releases Sayfasını Aç", fontSize = 12.sp, color = Color(0xFF10B981), fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }

                    if (latestRelease != null) {
                        item {
                            val rel = latestRelease!!
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "🚀 ${rel.title}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = Color.White
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (rel.isForceUpdate) Color(0xFFEF4444) else Color(0xFF10B981)
                                        ) {
                                            Text(
                                                text = if (rel.isForceUpdate) "ZORUNLU" else "YENİ",
                                                color = Color.White,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    Text(
                                        text = "APK Dosyası: ${rel.apkFileName} (${if (rel.apkSize > 0) "${rel.apkSize / (1024 * 1024)} MB" else "Hazır"})",
                                        fontSize = 12.sp,
                                        color = Color(0xFF94A3B8)
                                    )

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFF1E293B),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = rel.changelog,
                                            fontSize = 12.sp,
                                            color = Color(0xFFE2E8F0),
                                            modifier = Modifier.padding(10.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Button(
                                        onClick = { onShowUpdateDialog(rel) },
                                        modifier = Modifier.fillMaxWidth().height(42.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                                    ) {
                                        Text("Güncelleme Ekranını Başlat", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Comprehensive Member & Role Management Dialog
    if (memberToEdit != null) {
        val user = memberToEdit!!
        EditMemberRolesDialog(
            user = user,
            onDismiss = { memberToEdit = null },
            onSave = { newTeam, updatedRoles ->
                scope.launch {
                    repository.updateUserRolesAndTeam(
                        targetUserId = user.userId.ifBlank { user.id },
                        team = newTeam,
                        roles = updatedRoles,
                        status = user.status
                    )
                    memberToEdit = null
                    Toast.makeText(context, "${user.getFullName()} güncellendi", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // New Announcement Dialog
    if (showNewAnnouncementDialog) {
        NewAnnouncementDialog(
            onDismiss = { showNewAnnouncementDialog = false },
            onSubmit = { title, content, isPinned ->
                scope.launch {
                    repository.addAnnouncement(
                        title = title,
                        content = content,
                        authorName = currentUserProfile?.getFullName() ?: "Yönetici",
                        isPinned = isPinned
                    )
                    showNewAnnouncementDialog = false
                    Toast.makeText(context, "Duyuru yayınlandı!", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // Broadcast Update Dialog
    if (showBroadcastUpdateDialog) {
        var vName by remember { mutableStateOf("1.0.5") }
        var vCodeStr by remember { mutableStateOf("${updateManager?.getCurrentVersionCode()?.plus(1) ?: 6}") }
        var vTitle by remember { mutableStateOf("Blacksea Robotics v1.0.5 Yayında") }
        var vChangelog by remember { mutableStateOf("• GitHub Releases bağlantı kararlılığı ve 404 koruması eklendi\n• Otomatik APK güncelleme ve indirme sistemi geliştirildi\n• Bildirim ve onay başvuru sistemi güçlendirildi") }
        var vApkUrl by remember { mutableStateOf("https://github.com/${updateManager?.githubOwner ?: "filim3290"}/${updateManager?.githubRepo ?: "blacksea-robotics"}/releases/download/v1.0.5/BlackseaRobotics-v1.0.5.apk") }
        var isForce by remember { mutableStateOf(false) }
        var isBroadcasting by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isBroadcasting) showBroadcastUpdateDialog = false },
            title = {
                Text("🚀 Tüm Takıma Yeni Sürüm Yayınla", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Bu işlem veritabanına yeni sürüm kaydı ekler ve tüm takım üyelerine anında 'Yeni Güncelleme Mevcut' bildirimi gönderir.", fontSize = 12.sp, color = Color(0xFF94A3B8))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = vName,
                            onValueChange = { vName = it },
                            label = { Text("Sürüm Adı") },
                            placeholder = { Text("1.0.4") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = vCodeStr,
                            onValueChange = { vCodeStr = it },
                            label = { Text("Sürüm Kodu") },
                            placeholder = { Text("5") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        value = vTitle,
                        onValueChange = { vTitle = it },
                        label = { Text("Güncelleme Başlığı") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = vChangelog,
                        onValueChange = { vChangelog = it },
                        label = { Text("Değişiklik Notları") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = vApkUrl,
                        onValueChange = { vApkUrl = it },
                        label = { Text("APK İndirme Linki (İsteğe Bağlı)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Zorunlu Güncelleme", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Switch(checked = isForce, onCheckedChange = { isForce = it })
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsedCode = vCodeStr.toIntOrNull() ?: 5
                        isBroadcasting = true
                        scope.launch {
                            val res = updateManager?.broadcastUpdateToTeam(
                                versionName = vName.trim(),
                                versionCode = parsedCode,
                                title = vTitle.trim(),
                                changelog = vChangelog.trim(),
                                apkDownloadUrl = vApkUrl.trim(),
                                isForceUpdate = isForce
                            )
                            isBroadcasting = false
                            showBroadcastUpdateDialog = false
                            if (res != null && res.isSuccess) {
                                Toast.makeText(context, "Yeni sürüm tüm kullanıcılara yayınlandı!", Toast.LENGTH_LONG).show()
                                val cleanVName = vName.trim()
                                val rel = com.example.update.AppReleaseInfo(
                                    tagName = "v$cleanVName",
                                    versionName = cleanVName,
                                    versionCode = parsedCode,
                                    title = vTitle.trim(),
                                    changelog = vChangelog.trim(),
                                    publishedAt = "Bugün",
                                    apkDownloadUrl = vApkUrl.trim().ifBlank {
                                        "https://github.com/${updateManager.githubOwner}/${updateManager.githubRepo}/releases/download/v$cleanVName/BlackseaRobotics-v$cleanVName.apk"
                                    },
                                    apkFileName = "BlackseaRobotics-v$cleanVName.apk",
                                    apkSize = 20 * 1024 * 1024L,
                                    isForceUpdate = isForce
                                )
                                onShowUpdateDialog(rel)
                            } else {
                                Toast.makeText(context, "Yayınlama başarısız oldu.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    enabled = !isBroadcasting && vName.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = PhoenixAmber)
                ) {
                    if (isBroadcasting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.Black)
                    } else {
                        Text("Yayınla ve Bildir", fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showBroadcastUpdateDialog = false }, enabled = !isBroadcasting) {
                    Text("İptal")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditMemberRolesDialog(
    user: UserProfile,
    onDismiss: () -> Unit,
    onSave: (newTeam: String, updatedRoles: List<String>) -> Unit
) {
    var selectedTeam by remember { mutableStateOf(user.team) }
    var currentRoles by remember { mutableStateOf(user.roles.ifEmpty { listOf(user.role) }) }
    var customRoleInput by remember { mutableStateOf("") }
    var teamMenuExpanded by remember { mutableStateOf(false) }

    val teams = listOf(
        TeamType.MANAGEMENT.displayName,
        TeamType.SOFTWARE.displayName,
        TeamType.MECHANIC.displayName,
        TeamType.PR.displayName
    )

    val quickRoles = listOf(
        "Yetkili Öğretmen",
        "Takım Kaptanı",
        "Takım Yardımcısı",
        "Yönetici",
        "Yazılım Üyesi",
        "Mekanik Üyesi",
        "PR Üyesi",
        "Genel Yönetim Üyesi",
        "Elektronik Sorumlusu",
        "Tasarım Sorumlusu",
        "Sosyal Medya Sorumlusu",
        "Proje Sorumlusu"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(user.getFullName(), fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(user.email, fontSize = 11.sp, color = Color(0xFF94A3B8))
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Team Selector
                ExposedDropdownMenuBox(
                    expanded = teamMenuExpanded,
                    onExpandedChange = { teamMenuExpanded = !teamMenuExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedTeam,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Ekip") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = teamMenuExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    ExposedDropdownMenu(
                        expanded = teamMenuExpanded,
                        onDismissRequest = { teamMenuExpanded = false }
                    ) {
                        teams.forEach { team ->
                            DropdownMenuItem(
                                text = { TeamBadge(teamName = team) },
                                onClick = {
                                    selectedTeam = team
                                    teamMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                // Current Active Roles
                Text("Mevcut Roller:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    currentRoles.forEach { role ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = ElectricBlue.copy(alpha = 0.15f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 8.dp, top = 2.dp, bottom = 2.dp, end = 4.dp)
                            ) {
                                Text(text = role, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ElectricBlue)
                                IconButton(
                                    onClick = { currentRoles = currentRoles - role },
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Close, contentDescription = "Kaldır", modifier = Modifier.size(12.dp))
                                }
                            }
                        }
                    }
                }

                // Add Predefined Role
                Text("Hızlı Rol Ekle:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF94A3B8))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    quickRoles.filter { !currentRoles.contains(it) }.take(6).forEach { role ->
                        FilterChip(
                            selected = false,
                            onClick = { currentRoles = currentRoles + role },
                            label = { Text(text = "+ $role", fontSize = 10.sp) },
                            colors = FilterChipDefaults.filterChipColors()
                        )
                    }
                }

                // Custom Role Input
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = customRoleInput,
                        onValueChange = { customRoleInput = it },
                        label = { Text("Özel Rol Yaz") },
                        placeholder = { Text("Örn: Sensör Sorumlusu") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    )

                    IconButton(
                        onClick = {
                            val trimmed = customRoleInput.trim()
                            if (trimmed.isNotBlank() && !currentRoles.contains(trimmed)) {
                                currentRoles = currentRoles + trimmed
                                customRoleInput = ""
                            }
                        },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Ekle", tint = ElectricBlue)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(selectedTeam, currentRoles.ifEmpty { listOf(PredefinedRoles.getDefaultTeamRole(selectedTeam)) }) },
                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
            ) {
                Text("Kaydet")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("İptal")
            }
        }
    )
}

@Composable
fun NewAnnouncementDialog(
    onDismiss: () -> Unit,
    onSubmit: (title: String, content: String, isPinned: Boolean) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var isPinned by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Yeni Takım Duyurusu", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Duyuru Başlığı") },
                    placeholder = { Text("Örn: Robot Şasi Montaj Tarihi") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Duyuru İçeriği") },
                    placeholder = { Text("Duyuru detaylarını buraya yazın...") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    shape = RoundedCornerShape(10.dp)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { isPinned = !isPinned }
                ) {
                    Checkbox(
                        checked = isPinned,
                        onCheckedChange = { isPinned = it }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Önemli (Başa Sabitle)", fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank() && content.isNotBlank()) {
                        onSubmit(title.trim(), content.trim(), isPinned)
                    }
                },
                enabled = title.isNotBlank() && content.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
            ) {
                Text("Yayınla")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("İptal")
            }
        }
    )
}
