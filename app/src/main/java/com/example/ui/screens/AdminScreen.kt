package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Security
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
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BlackseaRepository
import com.example.model.PLATFORM_FOUNDER_EMAIL
import com.example.model.PLATFORM_MAIN_TEAM_ID
import com.example.model.PredefinedRoles
import com.example.model.SupportTicketStatus
import com.example.model.TeamAnnouncement
import com.example.model.TeamInfo
import com.example.model.TeamMembership
import com.example.model.TeamType
import com.example.model.UserProfile
import com.example.model.UserRole
import com.example.model.UserStatus
import com.example.ui.components.AdminSupportTab
import com.example.ui.components.RoleBadge
import com.example.ui.components.TeamBadge
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.PhoenixAmber
import com.example.ui.theme.blackseaColors
import com.example.util.sanitizeUserErrorMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

enum class AdminSection(val key: String, val label: String) {
    MEMBERS("members", "Üyeler"),
    APPROVALS("approvals", "Onaylar"),
    BROADCAST("broadcast", "Bildirim Gönder"),
    ANNOUNCEMENTS("announcements", "Duyurular"),
    TEAM_INFO("team_info", "Takım Bilgileri"),
    SUPPORT("support", "Destek"),
    TEAM_APPLICATIONS("team_applications", "Takım Başvuruları"),
    PLATFORM_TEAMS("platform_teams", "Platform Takımları")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminScreen(
    repository: BlackseaRepository,
    currentUserProfile: UserProfile?,
    activeTeam: TeamInfo = TeamInfo.mainBlackseaTeam(),
    isPlatformAdmin: Boolean = false,
    @Suppress("UNUSED_PARAMETER")
    updateManager: com.example.update.AppUpdateManager? = null,
    initialSectionKey: String = "members",
    @Suppress("UNUSED_PARAMETER")
    onShowUpdateDialog: (com.example.update.AppReleaseInfo) -> Unit = {},
    onTeamDeleted: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ext = MaterialTheme.blackseaColors

    val activeTeamId = activeTeam.id.ifBlank { PLATFORM_MAIN_TEAM_ID }
    val isBsrPlatformAdminSession = (activeTeamId == PLATFORM_MAIN_TEAM_ID && isPlatformAdmin)

    val teamMemberships by repository.observeTeamMemberships(activeTeamId).collectAsState(initial = emptyList())
    val announcements by repository.observeAnnouncements(activeTeamId).collectAsState(initial = emptyList())
    val supportTickets by remember(isBsrPlatformAdminSession, activeTeamId) {
        if (isBsrPlatformAdminSession) repository.observeAllSupportTickets(activeTeamId) else flowOf(emptyList())
    }.collectAsState(initial = emptyList())

    val pendingTeamApplications by remember(isBsrPlatformAdminSession) {
        if (isBsrPlatformAdminSession) repository.observePendingTeamApplications() else flowOf(emptyList())
    }.collectAsState(initial = emptyList())
    val allPlatformTeams by remember(isBsrPlatformAdminSession) {
        if (isBsrPlatformAdminSession) repository.observeAllTeamsForPlatformAdmin() else flowOf(emptyList())
    }.collectAsState(initial = emptyList())

    val openSupportCount = remember(supportTickets) {
        supportTickets.count { SupportTicketStatus.normalize(it.status) != SupportTicketStatus.RESOLVED }
    }

    val pendingMembers = teamMemberships.filter { it.isPending() }
    val approvedMembers = teamMemberships.filter { it.isActive() }
    val restrictedMembers = teamMemberships.filter { it.isRevoked() || it.isRejected() }
    val currentUid = currentUserProfile?.userId?.ifBlank { currentUserProfile.id }.orEmpty()

    fun isProtectedMember(member: TeamMembership): Boolean {
        val targetId = member.userId
        return (currentUid.isNotBlank() && targetId == currentUid) ||
            (activeTeamId == PLATFORM_MAIN_TEAM_ID && member.userEmail.trim().equals(PLATFORM_FOUNDER_EMAIL, ignoreCase = true))
    }

    val sections = remember(isBsrPlatformAdminSession) {
        buildList {
            add(AdminSection.MEMBERS)
            add(AdminSection.APPROVALS)
            add(AdminSection.BROADCAST)
            add(AdminSection.ANNOUNCEMENTS)
            add(AdminSection.TEAM_INFO)
            if (isBsrPlatformAdminSession) {
                add(AdminSection.SUPPORT)
                add(AdminSection.TEAM_APPLICATIONS)
                add(AdminSection.PLATFORM_TEAMS)
            }
        }
    }

    var selectedTabIndex by remember(sections) {
        mutableIntStateOf(
            sections.indexOfFirst { it.key == initialSectionKey }.takeIf { it >= 0 } ?: 0
        )
    }
    LaunchedEffect(initialSectionKey, sections) {
        val idx = sections.indexOfFirst { it.key == initialSectionKey }
        if (idx >= 0) {
            selectedTabIndex = idx
        }
    }

    // Broadcast Notification State
    var broadcastTitle by remember { mutableStateOf("") }
    var broadcastMessage by remember { mutableStateOf("") }
    var isSendingBroadcast by remember { mutableStateOf(false) }

    // Edit Member & Account Action Dialog State
    var memberToEdit by remember { mutableStateOf<TeamMembership?>(null) }
    var memberToRevoke by remember { mutableStateOf<TeamMembership?>(null) }
    var memberToRemove by remember { mutableStateOf<TeamMembership?>(null) }
    var announcementToDelete by remember { mutableStateOf<TeamAnnouncement?>(null) }
    var isPerformingUserAction by remember { mutableStateOf(false) }
    var showNewAnnouncementDialog by remember { mutableStateOf(false) }

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
                        text = "${activeTeam.name} • ${if (isPlatformAdmin) "Yönetici Paneli" else "Yetkili Öğretmen Paneli"}",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isBsrPlatformAdminSession) {
                        "Takım üyelerini, destek taleplerini, takım başvurularını ve tüm platform takımlarını yönetebilirsiniz."
                    } else {
                        "${activeTeam.name} üyelerini, alt ekiplerini, takım bilgilerini ve duyurularını yönetin."
                    },
                    fontSize = 12.sp,
                    color = ext.textSecondary
                )
            }
        }

        // Dynamic Tabs
        val safeSelectedIndex = selectedTabIndex.coerceIn(0, (sections.size - 1).coerceAtLeast(0))
        ScrollableTabRow(
            selectedTabIndex = safeSelectedIndex,
            edgePadding = 16.dp,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            sections.forEachIndexed { index, section ->
                Tab(
                    selected = safeSelectedIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = {
                        val badge = when (section) {
                            AdminSection.MEMBERS -> " (${approvedMembers.size})"
                            AdminSection.APPROVALS -> if (pendingMembers.isNotEmpty()) " (${pendingMembers.size})" else ""
                            AdminSection.SUPPORT -> if (openSupportCount > 0) " ($openSupportCount)" else ""
                            AdminSection.TEAM_APPLICATIONS -> if (pendingTeamApplications.isNotEmpty()) " (${pendingTeamApplications.size})" else ""
                            AdminSection.PLATFORM_TEAMS -> " (${allPlatformTeams.size})"
                            else -> ""
                        }
                        Text(
                            text = "${section.label}$badge",
                            fontWeight = if (safeSelectedIndex == index) FontWeight.Bold else FontWeight.Normal,
                            color = if (safeSelectedIndex == index) ext.accentBlue else ext.textMuted
                        )
                    }
                )
            }
        }

        val currentSection = sections.getOrElse(safeSelectedIndex) { AdminSection.MEMBERS }

        when (currentSection) {
            AdminSection.MEMBERS -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (approvedMembers.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(top = 36.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Bu takımda henüz onaylı üye bulunmuyor.", color = ext.textMuted, fontSize = 13.sp)
                            }
                        }
                    }

                    itemsIndexed(
                        items = approvedMembers,
                        key = { index, m -> "active_${m.userId}_$index" }
                    ) { _, member ->
                        val protected = isProtectedMember(member)
                        val memberRoles = member.roles.ifEmpty { listOf(member.role) }.filter { it.isNotBlank() }.distinct()
                        val displayName = member.userName.ifBlank { member.userEmail.substringBefore("@").ifBlank { "Takım Üyesi" } }
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, ext.cardBorder),
                            elevation = CardDefaults.cardElevation(defaultElevation = if (ext.isDark) 2.dp else 1.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = ext.accentBlueContainer,
                                        modifier = Modifier.size(42.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = displayName.take(1).uppercase(),
                                                fontWeight = FontWeight.Bold,
                                                color = ext.accentBlue,
                                                fontSize = 16.sp
                                            )
                                        }
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = displayName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = member.userEmail,
                                            fontSize = 11.sp,
                                            color = ext.textMuted,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (protected) ext.accentBlueContainer else ext.emeraldSolid.copy(alpha = 0.14f)
                                    ) {
                                        Text(
                                            text = if (protected) "Korumalı" else "Aktif",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (protected) ext.accentBlue else ext.emeraldText,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    TeamBadge(teamName = member.subTeam.ifBlank { TeamType.GENERAL.displayName })
                                    memberRoles.forEach { role ->
                                        RoleBadge(role = role)
                                    }
                                }

                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { memberToEdit = member },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier.heightIn(min = 38.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Rol ve Ekip Düzenle",
                                            tint = ext.accentBlue,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text("Düzenle", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = ext.accentBlue)
                                    }

                                    if (!protected) {
                                        OutlinedButton(
                                            onClick = { memberToRevoke = member },
                                            shape = RoundedCornerShape(8.dp),
                                            border = BorderStroke(1.dp, ext.amberText.copy(alpha = 0.45f)),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                            modifier = Modifier
                                                .heightIn(min = 38.dp)
                                                .testTag("revoke_user_${member.userId}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Block,
                                                contentDescription = "Takım Erişimini Kaldır",
                                                tint = ext.amberText,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Text("Askıya Al", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = ext.amberText)
                                        }

                                        OutlinedButton(
                                            onClick = { memberToRemove = member },
                                            shape = RoundedCornerShape(8.dp),
                                            border = BorderStroke(1.dp, ext.dangerText.copy(alpha = 0.45f)),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                            modifier = Modifier
                                                .heightIn(min = 38.dp)
                                                .testTag("delete_user_${member.userId}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Takımdan Çıkar",
                                                tint = ext.dangerText,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Text("Çıkar", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = ext.dangerText)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (restrictedMembers.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Erişimi Askıya Alınan / Reddedilenler (${restrictedMembers.size})",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = ext.dangerText,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }

                        itemsIndexed(
                            items = restrictedMembers,
                            key = { index, m -> "restricted_${m.userId}_$index" }
                        ) { _, member ->
                            val isRevoked = member.isRevoked()
                            val displayName = member.userName.ifBlank { member.userEmail.substringBefore("@") }
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
                                            Text(text = displayName, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                                            Text(text = member.userEmail, fontSize = 11.sp, color = ext.textMuted)
                                        }
                                        Surface(shape = RoundedCornerShape(6.dp), color = ext.dangerContainer) {
                                            Text(
                                                text = if (isRevoked) "Askıda" else "Reddedildi",
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
                                        OutlinedButton(onClick = { memberToRemove = member }, shape = RoundedCornerShape(8.dp)) {
                                            Icon(imageVector = Icons.Default.Delete, contentDescription = null, tint = ext.dangerText, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Sil", color = ext.dangerText, fontSize = 12.sp)
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        Button(
                                            onClick = {
                                                scope.launch {
                                                    val res = repository.updateMembershipRolesAndStatus(
                                                        team = activeTeam,
                                                        targetUserId = member.userId,
                                                        subTeam = member.subTeam,
                                                        roles = member.roles.ifEmpty { listOf(PredefinedRoles.getDefaultTeamRole(member.subTeam)) },
                                                        status = UserStatus.ACTIVE.value
                                                    )
                                                    if (res.isSuccess) {
                                                        Toast.makeText(context, "$displayName erişimi geri verildi.", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        Toast.makeText(context, sanitizeUserErrorMessage(res.exceptionOrNull()), Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = ext.emeraldSolid, contentColor = Color.White)
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

            AdminSection.APPROVALS -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (pendingMembers.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${activeTeam.name} için onay bekleyen katılma başvurusu bulunmuyor.",
                                    color = ext.textMuted,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }

                    itemsIndexed(
                        items = pendingMembers,
                        key = { index, m -> "pending_${m.userId}_$index" }
                    ) { _, member ->
                        val displayName = member.userName.ifBlank { member.userEmail.substringBefore("@") }
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, ext.cardBorder),
                            elevation = CardDefaults.cardElevation(defaultElevation = if (ext.isDark) 2.dp else 1.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = displayName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = member.userEmail,
                                            fontSize = 12.sp,
                                            color = ext.textMuted,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = PhoenixAmber.copy(alpha = 0.16f)
                                    ) {
                                        Text(
                                            text = "Onay Bekliyor",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = ext.amberText,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    TeamBadge(teamName = member.subTeam.ifBlank { TeamType.GENERAL.displayName })
                                    member.roles.filter { it.isNotBlank() }.distinct().forEach { role ->
                                        RoleBadge(role = role)
                                    }
                                }

                                if (member.joinNote.isNotBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "Başvuru Notu: ${member.joinNote}",
                                            fontSize = 12.sp,
                                            color = ext.textSecondary,
                                            modifier = Modifier.padding(10.dp)
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(onClick = { memberToRemove = member }) {
                                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Başvuruyu Sil", tint = ext.dangerText)
                                    }

                                    Spacer(modifier = Modifier.width(4.dp))

                                    OutlinedButton(
                                        onClick = {
                                            scope.launch {
                                                val res = repository.updateMembershipRolesAndStatus(
                                                    team = activeTeam,
                                                    targetUserId = member.userId,
                                                    subTeam = member.subTeam,
                                                    roles = member.roles,
                                                    status = UserStatus.REJECTED.value
                                                )
                                                if (res.isSuccess) {
                                                    Toast.makeText(context, "Katılma başvurusu reddedildi", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(context, sanitizeUserErrorMessage(res.exceptionOrNull()), Toast.LENGTH_SHORT).show()
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
                                                val res = repository.updateMembershipRolesAndStatus(
                                                    team = activeTeam,
                                                    targetUserId = member.userId,
                                                    subTeam = member.subTeam,
                                                    roles = member.roles.ifEmpty { listOf(PredefinedRoles.getDefaultTeamRole(member.subTeam)) },
                                                    status = UserStatus.ACTIVE.value
                                                )
                                                if (res.isSuccess) {
                                                    Toast.makeText(context, "$displayName takıma kabul edildi!", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(context, sanitizeUserErrorMessage(res.exceptionOrNull()), Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = ext.accentBlueSolid, contentColor = Color.White)
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

            AdminSection.BROADCAST -> {
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
                                            text = "${activeTeam.name} Üyelerine Bildirim Gönder",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Yalnızca ${activeTeam.name} üyelerine anlık bildirim ve takım duyurusu iletilir.",
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
                                    placeholder = { Text("Örn: Yarın saat 15:30'da tüm ekipler test sahasında toplanacaktır.") },
                                    modifier = Modifier.fillMaxWidth().height(130.dp),
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
                                                    senderName = currentUserProfile?.getFullName() ?: if (isPlatformAdmin) "Yönetici" else "Yetkili Öğretmen",
                                                    teamId = activeTeamId,
                                                    teamName = activeTeam.name
                                                )
                                                broadcastTitle = ""
                                                broadcastMessage = ""
                                                Toast.makeText(context, "Bildirim ${activeTeam.name} üyelerine gönderildi!", Toast.LENGTH_LONG).show()
                                            } catch (ce: CancellationException) {
                                                throw ce
                                            } catch (e: Exception) {
                                                Toast.makeText(context, sanitizeUserErrorMessage(e), Toast.LENGTH_SHORT).show()
                                            } finally {
                                                isSendingBroadcast = false
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = ext.accentBlueSolid, contentColor = Color.White),
                                    shape = RoundedCornerShape(10.dp),
                                    enabled = !isSendingBroadcast
                                ) {
                                    if (isSendingBroadcast) {
                                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    } else {
                                        Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Takıma Bildirimi Gönder", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            AdminSection.ANNOUNCEMENTS -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { showNewAnnouncementDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = ext.accentBlueSolid, contentColor = Color.White),
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
                                        IconButton(onClick = { announcementToDelete = announcement }) {
                                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Duyuruyu Sil", tint = ext.dangerText)
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

            AdminSection.TEAM_INFO -> {
                TeamInfoEditorTab(
                    activeTeam = activeTeam,
                    repository = repository,
                    onTeamDeleted = onTeamDeleted
                )
            }

            AdminSection.SUPPORT -> {
                AdminSupportTab(
                    tickets = supportTickets,
                    repository = repository,
                    currentUserProfile = currentUserProfile,
                    teamId = activeTeamId,
                    teamName = activeTeam.name
                )
            }

            AdminSection.TEAM_APPLICATIONS -> {
                TeamApplicationsTab(
                    pendingApplications = pendingTeamApplications,
                    repository = repository
                )
            }

            AdminSection.PLATFORM_TEAMS -> {
                PlatformTeamsOverviewTab(
                    teams = allPlatformTeams,
                    repository = repository
                )
            }
        }
    }

    // Comprehensive Member & Role Management Dialog
    if (memberToEdit != null) {
        val member = memberToEdit!!
        EditMemberRolesDialog(
            member = member,
            isPlatformAdmin = isPlatformAdmin,
            onDismiss = { memberToEdit = null },
            onSave = { newSubTeam, updatedRoles ->
                scope.launch {
                    val res = repository.updateMembershipRolesAndStatus(
                        team = activeTeam,
                        targetUserId = member.userId,
                        subTeam = newSubTeam,
                        roles = updatedRoles,
                        status = member.status
                    )
                    if (res.isSuccess) {
                        memberToEdit = null
                        Toast.makeText(context, "${member.userName.ifBlank { "Üye" }} güncellendi", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, sanitizeUserErrorMessage(res.exceptionOrNull()), Toast.LENGTH_SHORT).show()
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
                            authorName = currentUserProfile?.getFullName() ?: if (isPlatformAdmin) "Yönetici" else "Yetkili Öğretmen",
                            isPinned = isPinned,
                            teamId = activeTeamId,
                            teamName = activeTeam.name
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

    // Revoke Team Membership Confirmation Dialog
    if (memberToRevoke != null) {
        val target = memberToRevoke!!
        val displayName = target.userName.ifBlank { target.userEmail }
        AlertDialog(
            onDismissRequest = { if (!isPerformingUserAction) memberToRevoke = null },
            title = { Text("Takım Erişimini Askıya Al", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "$displayName kullanıcısının ${activeTeam.name} erişimi askıya alınacaktır. Daha sonra isterseniz erişimi geri verebilirsiniz.",
                    color = ext.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        isPerformingUserAction = true
                        scope.launch {
                            try {
                                val res = repository.revokeTeamMembership(activeTeam, target.userId)
                                if (res.isSuccess) {
                                    Toast.makeText(context, "$displayName erişimi askıya alındı.", Toast.LENGTH_SHORT).show()
                                    memberToRevoke = null
                                } else {
                                    Toast.makeText(context, sanitizeUserErrorMessage(res.exceptionOrNull()), Toast.LENGTH_SHORT).show()
                                }
                            } finally {
                                isPerformingUserAction = false
                            }
                        }
                    },
                    enabled = !isPerformingUserAction,
                    colors = ButtonDefaults.buttonColors(containerColor = ext.dangerSolid, contentColor = Color.White)
                ) {
                    if (isPerformingUserAction) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Askıya Al", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { memberToRevoke = null }, enabled = !isPerformingUserAction) {
                    Text("Vazgeç")
                }
            }
        )
    }

    // Remove Member From Team Confirmation Dialog
    if (memberToRemove != null) {
        val target = memberToRemove!!
        val displayName = target.userName.ifBlank { target.userEmail }
        AlertDialog(
            onDismissRequest = { if (!isPerformingUserAction) memberToRemove = null },
            title = { Text("Üyeyi Takımdan Çıkar", fontWeight = FontWeight.Bold, color = ext.dangerText) },
            text = {
                Text(
                    "$displayName (${target.userEmail}) kullanıcısının ${activeTeam.name} üyeliği silinecektir. Devam etmek istiyor musunuz?",
                    color = ext.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        isPerformingUserAction = true
                        scope.launch {
                            try {
                                val res = repository.removeTeamMembership(activeTeam, target.userId)
                                if (res.isSuccess) {
                                    Toast.makeText(context, "$displayName takımdan çıkarıldı.", Toast.LENGTH_SHORT).show()
                                    memberToRemove = null
                                } else {
                                    Toast.makeText(context, sanitizeUserErrorMessage(res.exceptionOrNull()), Toast.LENGTH_SHORT).show()
                                }
                            } finally {
                                isPerformingUserAction = false
                            }
                        }
                    },
                    enabled = !isPerformingUserAction,
                    colors = ButtonDefaults.buttonColors(containerColor = ext.dangerSolid, contentColor = Color.White)
                ) {
                    if (isPerformingUserAction) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Takımdan Çıkar", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { memberToRemove = null }, enabled = !isPerformingUserAction) {
                    Text("Vazgeç")
                }
            }
        )
    }

    // Delete Announcement Confirmation Dialog
    if (announcementToDelete != null) {
        val targetAnnouncement = announcementToDelete!!
        AlertDialog(
            onDismissRequest = { if (!isPerformingUserAction) announcementToDelete = null },
            title = { Text("Duyuruyu Sil", fontWeight = FontWeight.Bold, color = ext.dangerText) },
            text = {
                Text(
                    "\"${targetAnnouncement.title}\" duyurusu kalıcı olarak silinsin mi? Emin misiniz?",
                    color = ext.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        isPerformingUserAction = true
                        scope.launch {
                            try {
                                repository.deleteAnnouncement(targetAnnouncement.id, teamId = activeTeamId)
                                announcementToDelete = null
                                Toast.makeText(context, "Duyuru silindi.", Toast.LENGTH_SHORT).show()
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
                    colors = ButtonDefaults.buttonColors(containerColor = ext.dangerSolid, contentColor = Color.White),
                    modifier = Modifier.testTag("confirm_delete_announcement_button")
                ) {
                    if (isPerformingUserAction) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Evet, Sil", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { announcementToDelete = null }, enabled = !isPerformingUserAction) {
                    Text("Vazgeç")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditMemberRolesDialog(
    member: TeamMembership,
    isPlatformAdmin: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (newTeam: String, updatedRoles: List<String>) -> Unit
) {
    val context = LocalContext.current
    var selectedTeam by remember { mutableStateOf(member.subTeam.ifBlank { TeamType.GENERAL.displayName }) }
    var currentRoles by remember { mutableStateOf(member.roles.ifEmpty { listOf(member.role.ifBlank { "Takım Üyesi" }) }) }
    var customRoleInput by remember { mutableStateOf("") }
    var teamMenuExpanded by remember { mutableStateOf(false) }

    val teams = listOf(
        TeamType.GENERAL.displayName,
        TeamType.MANAGEMENT.displayName,
        TeamType.SOFTWARE.displayName,
        TeamType.MECHANIC.displayName,
        TeamType.PR.displayName
    )

    val quickRoles = remember(isPlatformAdmin) {
        val managementRoles = if (isPlatformAdmin) {
            PredefinedRoles.MANAGEMENT_ROLES
        } else {
            PredefinedRoles.ASSIGNABLE_MANAGEMENT_ROLES
        }
        managementRoles + PredefinedRoles.TEAM_ROLES + PredefinedRoles.SPECIAL_ROLES
    }

    val ext = MaterialTheme.blackseaColors

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(member.userName.ifBlank { member.userEmail }, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(member.userEmail, fontSize = 11.sp, color = ext.textMuted)
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                ExposedDropdownMenuBox(
                    expanded = teamMenuExpanded,
                    onExpandedChange = { teamMenuExpanded = !teamMenuExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedTeam,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Alt Ekip") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = teamMenuExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
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

                Text("Hızlı Rol Ekle:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = ext.textMuted)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    quickRoles.filter { !currentRoles.contains(it) }.take(8).forEach { role ->
                        FilterChip(
                            selected = false,
                            onClick = { currentRoles = currentRoles + role },
                            label = { Text(text = "+ $role", fontSize = 10.sp) },
                            colors = FilterChipDefaults.filterChipColors()
                        )
                    }
                }

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
                            if (!isPlatformAdmin && trimmed.equals(UserRole.ADMIN.displayName, ignoreCase = true)) {
                                Toast.makeText(
                                    context,
                                    "'Yönetici' rolü yalnızca sistem yöneticisi tarafından atanabilir.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else if (trimmed.isNotBlank() && !currentRoles.contains(trimmed)) {
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
                colors = ButtonDefaults.buttonColors(containerColor = ext.accentBlueSolid, contentColor = Color.White)
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
                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue, contentColor = Color.White)
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
