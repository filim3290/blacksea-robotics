package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Restore
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
import com.example.ui.theme.blackseaColors
import com.example.util.AppMessages
import com.example.util.sanitizeUserErrorMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun AdminScreen(
    repository: BlackseaRepository,
    currentUserProfile: UserProfile?,
    updateManager: com.example.update.AppUpdateManager? = null,
    initialTabIndex: Int = 0,
    onShowUpdateDialog: (com.example.update.AppReleaseInfo) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ext = MaterialTheme.blackseaColors

    val allUsers by repository.observeAllUsers().collectAsState(initial = emptyList())
    val announcements by repository.observeAnnouncements().collectAsState(initial = emptyList())

    val pendingUsers = allUsers.filter { UserStatus.isPending(it.status) }
    val approvedUsers = allUsers.filter { UserStatus.isActive(it.status) }
    val restrictedUsers = allUsers.filter { UserStatus.isRevoked(it.status) || UserStatus.isRejected(it.status) }
    val currentUid = currentUserProfile?.userId?.ifBlank { currentUserProfile.id }.orEmpty()

    fun isProtectedUser(user: UserProfile): Boolean {
        val targetId = user.userId.ifBlank { user.id }
        return (currentUid.isNotBlank() && targetId == currentUid) ||
            user.email.equals("filim3290@gmail.com", ignoreCase = true)
    }

    var selectedTabIndex by remember { mutableIntStateOf(initialTabIndex.coerceIn(0, 4)) }
    val tabs = remember {
        listOf("Kullanıcılar", "Onaylar", "Bildirim Gönder", "Duyurular", "Uygulama Güncellemesi")
    }
    LaunchedEffect(initialTabIndex) {
        if (initialTabIndex in tabs.indices) {
            selectedTabIndex = initialTabIndex
        }
    }

    // Broadcast Notification State
    var broadcastTitle by remember { mutableStateOf("") }
    var broadcastMessage by remember { mutableStateOf("") }
    var isSendingBroadcast by remember { mutableStateOf(false) }

    // Edit Member & Account Action Dialog State
    var memberToEdit by remember { mutableStateOf<UserProfile?>(null) }
    var userToRevoke by remember { mutableStateOf<UserProfile?>(null) }
    var userToDelete by remember { mutableStateOf<UserProfile?>(null) }
    var isPerformingUserAction by remember { mutableStateOf(false) }
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
            shadowElevation = if (ext.isDark) 2.dp else 1.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = ext.adminRedText,
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
                    text = "Üyelerin gerçek isimlerini, ekiplerini, özel rollerini, erişim yetkilerini ve hesap durumlarını yönetin.",
                    fontSize = 12.sp,
                    color = ext.textSecondary
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
                            color = if (selectedTabIndex == index) ext.accentBlue else ext.textMuted
                        )
                    }
                )
            }
        }

        when (selectedTabIndex) {
            0 -> {
                // Approved & Restricted Members List
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(
                        items = approvedUsers,
                        key = { index, u -> "active_${u.userId.ifBlank { u.id }}_$index" }
                    ) { _, user ->
                        val protected = isProtectedUser(user)
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, ext.cardBorder),
                            elevation = CardDefaults.cardElevation(defaultElevation = if (ext.isDark) 2.dp else 1.dp)
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
                                        color = ext.accentBlueContainer,
                                        modifier = Modifier.size(42.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = user.getFullName().take(1).uppercase(),
                                                fontWeight = FontWeight.Bold,
                                                color = ext.accentBlue,
                                                fontSize = 16.sp
                                            )
                                        }
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = user.getFullName(),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${user.email} • @${user.username.ifBlank { "bsr_uye" }}",
                                            fontSize = 11.sp,
                                            color = ext.textMuted
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

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { memberToEdit = user }) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Rol ve Ekip Düzenle",
                                            tint = ext.accentBlue
                                        )
                                    }
                                    if (!protected) {
                                        IconButton(
                                            onClick = { userToRevoke = user },
                                            modifier = Modifier.testTag("revoke_user_${user.userId.ifBlank { user.id }}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Block,
                                                contentDescription = "Erişimi Kaldır",
                                                tint = ext.amberText
                                            )
                                        }
                                        IconButton(
                                            onClick = { userToDelete = user },
                                            modifier = Modifier.testTag("delete_user_${user.userId.ifBlank { user.id }}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Kullanıcıyı Sil",
                                                tint = ext.dangerText
                                            )
                                        }
                                    } else {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = ext.accentBlueContainer
                                        ) {
                                            Text(
                                                text = "Korumalı",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = ext.accentBlue,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (restrictedUsers.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Erişimi Kaldırılan / Reddedilen Kullanıcılar (${restrictedUsers.size})",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = ext.dangerText,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }

                        itemsIndexed(
                            items = restrictedUsers,
                            key = { index, u -> "restricted_${u.userId.ifBlank { u.id }}_$index" }
                        ) { _, user ->
                            val isRevoked = UserStatus.isRevoked(user.status)
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, ext.dangerText.copy(alpha = 0.3f)),
                                elevation = CardDefaults.cardElevation(defaultElevation = if (ext.isDark) 1.dp else 0.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = user.getFullName(),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "${user.email} • @${user.username.ifBlank { "bsr_uye" }}",
                                                fontSize = 11.sp,
                                                color = ext.textMuted
                                            )
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = ext.dangerContainer
                                        ) {
                                            Text(
                                                text = if (isRevoked) "Erişim Kaldırıldı" else "Reddedildi",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = ext.dangerText,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedButton(
                                            onClick = { userToDelete = user },
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = null,
                                                tint = ext.dangerText,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Sil", color = ext.dangerText, fontSize = 12.sp)
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        Button(
                                            onClick = {
                                                scope.launch {
                                                    try {
                                                        repository.updateUserRolesAndTeam(
                                                            targetUserId = user.userId.ifBlank { user.id },
                                                            team = user.team,
                                                            roles = user.roles.ifEmpty { listOf(PredefinedRoles.getDefaultTeamRole(user.team)) },
                                                            status = UserStatus.APPROVED.value
                                                        )
                                                        Toast.makeText(context, "${user.getFullName()} erişimi geri verildi.", Toast.LENGTH_SHORT).show()
                                                    } catch (ce: CancellationException) {
                                                        throw ce
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, sanitizeUserErrorMessage(e), Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = ext.emeraldSolid,
                                                contentColor = Color.White
                                            )
                                        ) {
                                            Icon(imageVector = Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Erişimi Geri Ver", fontSize = 12.sp)
                                        }
                                    }
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
                                    color = ext.textMuted,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }

                    itemsIndexed(
                        items = pendingUsers,
                        key = { index, u -> "${u.userId.ifBlank { u.id }}_$index" }
                    ) { _, user ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, ext.cardBorder),
                            elevation = CardDefaults.cardElevation(defaultElevation = if (ext.isDark) 2.dp else 1.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(text = user.getFullName(), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                                Text(text = "${user.email} • @${user.username}", fontSize = 12.sp, color = ext.textMuted)
                                Spacer(modifier = Modifier.height(6.dp))
                                TeamBadge(teamName = user.team)

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(onClick = { userToDelete = user }) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Başvuruyu Sil",
                                            tint = ext.dangerText
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(4.dp))

                                    OutlinedButton(
                                        onClick = {
                                            scope.launch {
                                                try {
                                                    repository.updateUserRolesAndTeam(
                                                        targetUserId = user.userId.ifBlank { user.id },
                                                        team = user.team,
                                                        roles = user.roles,
                                                        status = UserStatus.REJECTED.value
                                                    )
                                                    Toast.makeText(context, "Kullanıcı başvurusu reddedildi", Toast.LENGTH_SHORT).show()
                                                } catch (ce: CancellationException) {
                                                    throw ce
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, sanitizeUserErrorMessage(e), Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.Close, contentDescription = null, tint = ext.dangerText, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Reddet", color = ext.dangerText)
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Button(
                                        onClick = {
                                            scope.launch {
                                                try {
                                                    repository.updateUserRolesAndTeam(
                                                        targetUserId = user.userId.ifBlank { user.id },
                                                        team = user.team,
                                                        roles = user.roles.ifEmpty { listOf(PredefinedRoles.getDefaultTeamRole(user.team)) },
                                                        status = UserStatus.APPROVED.value
                                                    )
                                                    Toast.makeText(context, "Kullanıcı onaylandı!", Toast.LENGTH_SHORT).show()
                                                } catch (ce: CancellationException) {
                                                    throw ce
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, sanitizeUserErrorMessage(e), Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = ext.accentBlueSolid,
                                            contentColor = Color.White
                                        )
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
                            border = BorderStroke(1.dp, ext.cardBorder),
                            elevation = CardDefaults.cardElevation(defaultElevation = if (ext.isDark) 2.dp else 1.dp)
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
                                        color = ext.accentBlueContainer,
                                        modifier = Modifier.size(42.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Campaign,
                                                contentDescription = null,
                                                tint = ext.accentBlue,
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
                                            color = ext.textSecondary
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
                                            } catch (ce: CancellationException) {
                                                throw ce
                                            } catch (e: Exception) {
                                                Toast.makeText(context, sanitizeUserErrorMessage(e), Toast.LENGTH_SHORT).show()
                                            } finally {
                                                isSendingBroadcast = false
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = ext.accentBlueSolid,
                                        contentColor = Color.White
                                    ),
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
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ext.accentBlueSolid,
                            contentColor = Color.White
                        ),
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
                        itemsIndexed(
                            items = announcements,
                            key = { index, ann -> "${ann.id.ifBlank { ann.title }}_$index" }
                        ) { _, announcement ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, ext.cardBorder)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(text = announcement.title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                                        IconButton(
                                            onClick = {
                                                scope.launch {
                                                    try {
                                                        repository.deleteAnnouncement(announcement.id)
                                                        Toast.makeText(context, "Duyuru silindi", Toast.LENGTH_SHORT).show()
                                                    } catch (ce: CancellationException) {
                                                        throw ce
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, sanitizeUserErrorMessage(e), Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                        ) {
                                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Sil", tint = ext.dangerText)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(text = announcement.content, fontSize = 13.sp, color = ext.textSecondary)
                                }
                            }
                        }
                    }
                }
            }

            4 -> {
                // Tab 4: Uygulama Güncellemesi (Clean release management)
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
                            checkStatusMessage = "Yeni sürüm bulundu: v${res.release.versionName}"
                        }
                        is com.example.update.UpdateCheckResult.UpToDate -> {
                            latestRelease = null
                            checkStatusMessage = "Uygulamanız güncel (v${res.currentVersionName})"
                        }
                        is com.example.update.UpdateCheckResult.NoReleaseFound -> {
                            latestRelease = null
                            checkStatusMessage = "Uygulamanız güncel."
                        }
                        is com.example.update.UpdateCheckResult.ApkAssetMissing,
                        is com.example.update.UpdateCheckResult.NetworkError,
                        is com.example.update.UpdateCheckResult.ApiError -> {
                            checkStatusMessage = AppMessages.UPDATE_CHECK_ERROR
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
                            border = BorderStroke(1.dp, ext.cardBorder),
                            elevation = CardDefaults.cardElevation(defaultElevation = if (ext.isDark) 2.dp else 1.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Mevcut Uygulama Sürümü", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = ext.accentBlueContainer
                                    ) {
                                        Text(
                                            text = "v${mgr.getCurrentVersionName()}",
                                            color = ext.accentBlue,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Güncelleme Durumu", fontSize = 13.sp, color = ext.textSecondary)
                                    Text(
                                        text = checkStatusMessage ?: "Kontrol ediliyor...",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (latestRelease != null) ext.emeraldText else ext.textMuted
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
                                                    checkStatusMessage = "Yeni sürüm bulundu: v${res.release.versionName}"
                                                    onShowUpdateDialog(res.release)
                                                }
                                                is com.example.update.UpdateCheckResult.UpToDate -> {
                                                    latestRelease = null
                                                    checkStatusMessage = "Uygulamanız güncel (v${res.currentVersionName})"
                                                    Toast.makeText(context, "Uygulamanız güncel (v${res.currentVersionName})", Toast.LENGTH_SHORT).show()
                                                }
                                                is com.example.update.UpdateCheckResult.NoReleaseFound -> {
                                                    latestRelease = null
                                                    checkStatusMessage = "Uygulamanız güncel."
                                                    Toast.makeText(context, "Uygulamanız güncel.", Toast.LENGTH_SHORT).show()
                                                }
                                                is com.example.update.UpdateCheckResult.ApkAssetMissing,
                                                is com.example.update.UpdateCheckResult.NetworkError,
                                                is com.example.update.UpdateCheckResult.ApiError -> {
                                                    checkStatusMessage = AppMessages.UPDATE_CHECK_ERROR
                                                    Toast.makeText(context, AppMessages.UPDATE_CHECK_ERROR, Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        }
                                    },
                                    enabled = !isChecking,
                                    modifier = Modifier.fillMaxWidth().height(44.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = ext.accentBlueSolid,
                                        contentColor = Color.White
                                    )
                                ) {
                                    if (isChecking) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            color = Color.White,
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Text("Güncellemeleri Kontrol Et", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }

                                Button(
                                    onClick = { showBroadcastUpdateDialog = true },
                                    modifier = Modifier.fillMaxWidth().height(42.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = PhoenixAmber,
                                        contentColor = Color.Black
                                    )
                                ) {
                                    Text("Yeni Sürüm Duyurusu Gönder", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, ext.cardBorder),
                                elevation = CardDefaults.cardElevation(defaultElevation = if (ext.isDark) 2.dp else 1.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Yeni sürüm bulundu: v${rel.versionName}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (rel.isForceUpdate) ext.dangerSolid else ext.emeraldSolid
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

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        border = BorderStroke(1.dp, ext.cardBorder),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = rel.changelog,
                                            fontSize = 12.sp,
                                            color = ext.textSecondary,
                                            modifier = Modifier.padding(10.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Button(
                                        onClick = { onShowUpdateDialog(rel) },
                                        modifier = Modifier.fillMaxWidth().height(42.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = ext.emeraldSolid,
                                            contentColor = Color.White
                                        )
                                    ) {
                                        Text("Güncelle", fontWeight = FontWeight.Bold)
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
                    try {
                        repository.updateUserRolesAndTeam(
                            targetUserId = user.userId.ifBlank { user.id },
                            team = newTeam,
                            roles = updatedRoles,
                            status = user.status
                        )
                        memberToEdit = null
                        Toast.makeText(context, "${user.getFullName()} güncellendi", Toast.LENGTH_SHORT).show()
                    } catch (ce: CancellationException) {
                        throw ce
                    } catch (e: Exception) {
                        Toast.makeText(context, sanitizeUserErrorMessage(e), Toast.LENGTH_SHORT).show()
                    }
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
                    try {
                        repository.addAnnouncement(
                            title = title,
                            content = content,
                            authorName = currentUserProfile?.getFullName() ?: "Yönetici",
                            isPinned = isPinned
                        )
                        showNewAnnouncementDialog = false
                        Toast.makeText(context, "Duyuru yayınlandı!", Toast.LENGTH_SHORT).show()
                    } catch (ce: CancellationException) {
                        throw ce
                    } catch (e: Exception) {
                        Toast.makeText(context, sanitizeUserErrorMessage(e), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    // Broadcast Update Dialog
    if (showBroadcastUpdateDialog) {
        val mgr = updateManager ?: remember { com.example.update.AppUpdateManager(context) }
        val currentVerName = remember { mgr.getCurrentVersionName() }
        val nextVerCode = remember { mgr.getCurrentVersionCode() + 1 }
        var vName by remember { mutableStateOf(currentVerName) }
        var vTitle by remember { mutableStateOf("Blacksea Robotics v$currentVerName Yayında") }
        var vChangelog by remember { mutableStateOf("• Görsel ve video önizleme sistemi eklendi\n• Otomatik uygulama güncelleme sistemi geliştirildi\n• Kararlılık iyileştirmeleri") }
        var isForce by remember { mutableStateOf(false) }
        var isBroadcasting by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isBroadcasting) showBroadcastUpdateDialog = false },
            title = {
                Text("Yeni Sürüm Duyurusu Gönder", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Tüm takım üyelerine yeni sürüm bildirimini ve değişiklik notlarını iletir.", fontSize = 12.sp, color = ext.textSecondary)

                    OutlinedTextField(
                        value = vName,
                        onValueChange = { vName = it },
                        label = { Text("Sürüm") },
                        placeholder = { Text("1.1.0") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = vTitle,
                        onValueChange = { vTitle = it },
                        label = { Text("Güncelleme Başlığı") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = vChangelog,
                        onValueChange = { vChangelog = it },
                        label = { Text("Yenilikler / Değişiklikler") },
                        minLines = 3,
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
                        val cleanVName = vName.trim()
                        val resolvedUrl = mgr.normalizeDownloadUrl(null, cleanVName)
                        isBroadcasting = true
                        scope.launch {
                            val res = updateManager?.broadcastUpdateToTeam(
                                versionName = cleanVName,
                                versionCode = nextVerCode,
                                title = vTitle.trim(),
                                changelog = vChangelog.trim(),
                                apkDownloadUrl = resolvedUrl,
                                isForceUpdate = isForce
                            )
                            isBroadcasting = false
                            showBroadcastUpdateDialog = false
                            if (res != null && res.isSuccess) {
                                Toast.makeText(context, "Yeni sürüm duyurusu tüm üyelere gönderildi!", Toast.LENGTH_LONG).show()
                                val rel = com.example.update.AppReleaseInfo(
                                    tagName = "v$cleanVName",
                                    versionName = cleanVName,
                                    versionCode = nextVerCode,
                                    title = vTitle.trim(),
                                    changelog = vChangelog.trim(),
                                    publishedAt = "Bugün",
                                    apkDownloadUrl = resolvedUrl,
                                    apkFileName = "BlackseaRobotics-v$cleanVName.apk",
                                    apkSize = 20 * 1024 * 1024L,
                                    isForceUpdate = isForce
                                )
                                onShowUpdateDialog(rel)
                            } else {
                                Toast.makeText(context, AppMessages.GENERIC_ERROR, Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    enabled = !isBroadcasting && vName.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PhoenixAmber,
                        contentColor = Color.Black
                    )
                ) {
                    if (isBroadcasting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.Black)
                    } else {
                        Text("Yayınla ve Bildir", fontWeight = FontWeight.Bold)
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

    // Revoke User Access Confirmation Dialog
    if (userToRevoke != null) {
        val target = userToRevoke!!
        AlertDialog(
            onDismissRequest = { if (!isPerformingUserAction) userToRevoke = null },
            title = { Text("Uygulama Erişimini Kaldır", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "${target.getFullName()} kullanıcısının uygulama erişimi kaldırılacak ve aktif oturumu kapatılacaktır. Daha sonra isterseniz erişimi geri verebilirsiniz.",
                    color = ext.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val targetId = target.userId.ifBlank { target.id }
                        isPerformingUserAction = true
                        scope.launch {
                            try {
                                repository.revokeUserAccess(targetId)
                                Toast.makeText(context, "${target.getFullName()} erişimi kaldırıldı.", Toast.LENGTH_SHORT).show()
                                userToRevoke = null
                            } catch (ce: CancellationException) {
                                throw ce
                            } catch (e: Exception) {
                                Toast.makeText(context, sanitizeUserErrorMessage(e), Toast.LENGTH_SHORT).show()
                            } finally {
                                isPerformingUserAction = false
                            }
                        }
                    },
                    enabled = !isPerformingUserAction,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ext.dangerSolid,
                        contentColor = Color.White
                    )
                ) {
                    if (isPerformingUserAction) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Erişimi Kaldır", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { userToRevoke = null }, enabled = !isPerformingUserAction) {
                    Text("Vazgeç")
                }
            }
        )
    }

    // Delete User Account Confirmation Dialog
    if (userToDelete != null) {
        val target = userToDelete!!
        AlertDialog(
            onDismissRequest = { if (!isPerformingUserAction) userToDelete = null },
            title = { Text("Kullanıcıyı Sil", fontWeight = FontWeight.Bold, color = ext.dangerText) },
            text = {
                Text(
                    "${target.getFullName()} (${target.email}) hesabı sistemden kalıcı olarak silinecek ve kullanıcının uygulama erişimi derhal sonlandırılacaktır. Devam etmek istiyor musunuz?",
                    color = ext.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val targetId = target.userId.ifBlank { target.id }
                        isPerformingUserAction = true
                        scope.launch {
                            try {
                                repository.deleteUserAccount(targetId)
                                Toast.makeText(context, "${target.getFullName()} silindi.", Toast.LENGTH_SHORT).show()
                                userToDelete = null
                            } catch (ce: CancellationException) {
                                throw ce
                            } catch (e: Exception) {
                                Toast.makeText(context, sanitizeUserErrorMessage(e), Toast.LENGTH_SHORT).show()
                            } finally {
                                isPerformingUserAction = false
                            }
                        }
                    },
                    enabled = !isPerformingUserAction,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ext.dangerSolid,
                        contentColor = Color.White
                    )
                ) {
                    if (isPerformingUserAction) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Kullanıcıyı Sil", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { userToDelete = null }, enabled = !isPerformingUserAction) {
                    Text("Vazgeç")
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

    val ext = MaterialTheme.blackseaColors

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(user.getFullName(), fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(user.email, fontSize = 11.sp, color = ext.textMuted)
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
                            color = ext.accentBlueContainer
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 8.dp, top = 2.dp, bottom = 2.dp, end = 4.dp)
                            ) {
                                Text(text = role, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ext.accentBlue)
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
                Text("Hızlı Rol Ekle:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = ext.textMuted)
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
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Ekle", tint = ext.accentBlue)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(selectedTeam, currentRoles.ifEmpty { listOf(PredefinedRoles.getDefaultTeamRole(selectedTeam)) }) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = ext.accentBlueSolid,
                    contentColor = Color.White
                )
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
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricBlue,
                    contentColor = Color.White
                )
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
