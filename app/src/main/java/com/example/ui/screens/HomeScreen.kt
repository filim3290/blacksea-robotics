package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BlackseaRepository
import com.example.model.ChatRoom
import com.example.model.PLATFORM_MAIN_TEAM_ID
import com.example.model.TeamInfo
import com.example.model.TeamTask
import com.example.model.TeamTaskStatus
import com.example.model.TeamType
import com.example.model.UserProfile
import com.example.ui.components.DynamicTeamBanner
import com.example.ui.components.TeamBadge
import com.example.ui.components.UserRolesRow
import com.example.ui.theme.blackseaColors
import com.example.util.sanitizeUserErrorMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    repository: BlackseaRepository,
    currentUserProfile: UserProfile?,
    activeTeam: TeamInfo = TeamInfo.mainBlackseaTeam(),
    onNavigateToRoom: (String) -> Unit,
    onNavigateToIdeas: () -> Unit,
    onNavigateToFiles: () -> Unit,
    onNavigateToAdmin: () -> Unit = {},
    onSwitchTeam: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val activeTeamId = activeTeam.id.ifBlank { PLATFORM_MAIN_TEAM_ID }

    val announcements by remember(activeTeamId) {
        repository.observeAnnouncements(activeTeamId)
    }.collectAsState(initial = emptyList())
    val rooms by remember(activeTeamId) {
        repository.observeRooms(activeTeamId)
    }.collectAsState(initial = emptyList())
    val ideas by remember(activeTeamId) {
        repository.observeIdeas(teamId = activeTeamId)
    }.collectAsState(initial = emptyList())
    val files by remember(activeTeamId) {
        repository.observeSharedFiles(teamId = activeTeamId)
    }.collectAsState(initial = emptyList())
    val teamMemberships by remember(activeTeamId) {
        repository.observeTeamMemberships(activeTeamId)
    }.collectAsState(initial = emptyList())
    val approvedMembers = remember(teamMemberships) {
        teamMemberships.filter { it.isActive() }
    }
    val activities by remember(activeTeamId) {
        repository.observeTeamActivity(activeTeamId)
    }.collectAsState(initial = emptyList())
    val tasks by remember(activeTeamId) {
        repository.observeTeamTasks(activeTeamId)
    }.collectAsState(initial = emptyList())

    val ext = MaterialTheme.blackseaColors
    val isAdmin = currentUserProfile?.isManagerAccount() == true
    val currentUid = currentUserProfile?.userId?.ifBlank { currentUserProfile.id }.orEmpty()

    var showAddTaskDialog by remember { mutableStateOf(false) }

    LaunchedEffect(activeTeamId, approvedMembers.size, activeTeam.memberCount, currentUid) {
        if (currentUid.isNotBlank() && approvedMembers.isNotEmpty() && activeTeam.memberCount != approvedMembers.size.toLong()) {
            repository.syncTeamMemberCountIfDrifted(activeTeamId, approvedMembers.size.toLong())
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("home_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Dynamic Hero Team Banner
        item {
            DynamicTeamBanner(
                team = activeTeam,
                memberCount = approvedMembers.size.toLong(),
                onSwitchTeamClick = onSwitchTeam
            )
        }

        // Welcome Card with Real Name & Team-Scoped Roles
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, ext.cardBorder),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Hoş Geldin, ${currentUserProfile?.getFullName() ?: "Takım Üyesi"}",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TeamBadge(teamName = currentUserProfile?.team ?: TeamType.GENERAL.displayName)
                        UserRolesRow(roles = currentUserProfile?.roles?.ifEmpty { listOf(currentUserProfile.role) } ?: listOf("Takım Üyesi"))
                    }

                    if (isAdmin) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = ext.adminRedSolid.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, ext.adminRedSolid.copy(alpha = 0.25f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigateToAdmin() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Security,
                                        contentDescription = null,
                                        tint = ext.adminRedText,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "${activeTeam.name} ${if (currentUserProfile?.isPlatformManagerAccount() == true) "Yönetici Paneli" else "Yetkili Öğretmen Paneli"}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ext.adminRedText
                                    )
                                }
                                Text("Aç >", fontSize = 11.sp, color = ext.adminRedText, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Quick Stats Grid
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickStatCard(
                    title = "Odalar",
                    count = rooms.size.coerceAtLeast(4).toString(),
                    icon = Icons.Default.ChatBubble,
                    color = ext.accentBlue,
                    modifier = Modifier.weight(1f).clickable { onNavigateToRoom("general") }
                )
                QuickStatCard(
                    title = "Fikirler",
                    count = ideas.size.toString(),
                    icon = Icons.Default.Lightbulb,
                    color = ext.amberText,
                    modifier = Modifier.weight(1f).clickable { onNavigateToIdeas() }
                )
                QuickStatCard(
                    title = "Dosyalar",
                    count = files.size.toString(),
                    icon = Icons.Default.Folder,
                    color = ext.emeraldText,
                    modifier = Modifier.weight(1f).clickable { onNavigateToFiles() }
                )
                QuickStatCard(
                    title = "Üyeler",
                    count = approvedMembers.size.toString(),
                    icon = Icons.Default.Person,
                    color = if (ext.isDark) Color(0xFFA78BFA) else Color(0xFF6D28D9),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Team Tasks Section ("Görevler")
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
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
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Assignment,
                            contentDescription = null,
                            tint = ext.accentBlue,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Takım Görevleri (${tasks.size})",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = ext.accentBlueContainer,
                        modifier = Modifier
                            .clickable { showAddTaskDialog = true }
                            .testTag("add_task_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Görev Ekle",
                                tint = ext.accentBlue,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Görev Ekle",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = ext.accentBlue
                            )
                        }
                    }
                }

                if (tasks.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, ext.cardBorder),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Text(
                            text = "${activeTeam.name} için henüz görev oluşturulmadı. Ekip arkadaşlarınla iş bölümü yapmak için 'Görev Ekle' butonunu kullanabilirsin.",
                            fontSize = 12.sp,
                            color = ext.textSecondary,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                } else {
                    tasks.forEach { task ->
                        TeamTaskCard(
                            task = task,
                            canDelete = isAdmin || (currentUid.isNotBlank() && task.createdById == currentUid),
                            onCycleStatus = {
                                val next = when (TeamTaskStatus.normalize(task.status)) {
                                    TeamTaskStatus.TODO -> TeamTaskStatus.IN_PROGRESS
                                    TeamTaskStatus.IN_PROGRESS -> TeamTaskStatus.DONE
                                    else -> TeamTaskStatus.TODO
                                }
                                scope.launch {
                                    val res = repository.updateTeamTaskStatus(activeTeamId, task.id, next)
                                    if (res.isFailure) {
                                        Toast.makeText(
                                            context,
                                            sanitizeUserErrorMessage(res.exceptionOrNull()),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            },
                            onDelete = {
                                scope.launch {
                                    val res = repository.deleteTeamTask(activeTeamId, task.id)
                                    if (res.isSuccess) {
                                        Toast.makeText(context, "Görev silindi.", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(
                                            context,
                                            sanitizeUserErrorMessage(res.exceptionOrNull()),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }

        // "Bugün Takımda Neler Oldu?" - Activity Feed Stream
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, ext.cardBorder),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = if (ext.isDark) 2.dp else 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.ElectricBolt, contentDescription = null, tint = ext.amberText, modifier = Modifier.size(20.dp))
                        Text(
                            text = "Bugün Takımda Neler Oldu?",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (activities.isNotEmpty()) {
                        activities.take(5).forEach { act ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = ext.accentBlue,
                                    modifier = Modifier.size(8.dp)
                                ) {}

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${act.userName} ${act.actionText}: \"${act.targetTitle}\"",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${act.userTeam} • ${act.timeAgo}",
                                        fontSize = 11.sp,
                                        color = ext.textMuted
                                    )
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "Bugün henüz yeni bir aktivite kaydedilmedi. Odalarda mesajlaşabilir, yeni bir fikir ekleyebilir, görev oluşturabilir veya teknik çalışma dosyası yükleyebilirsiniz.",
                            fontSize = 12.sp,
                            color = ext.textSecondary,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }

        // Announcements Section
        if (announcements.isNotEmpty()) {
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Campaign, contentDescription = null, tint = ext.amberText, modifier = Modifier.size(20.dp))
                        Text(text = "Takım Duyuruları", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    announcements.take(3).forEach { announcement ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, ext.cardBorder),
                            colors = CardDefaults.cardColors(
                                containerColor = if (announcement.isPinned) ext.accentBlueContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (announcement.isPinned) {
                                        Icon(imageVector = Icons.Default.PushPin, contentDescription = "Sabitlendi", tint = ext.amberText, modifier = Modifier.size(14.dp))
                                    }
                                    Text(text = announcement.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = announcement.content, fontSize = 13.sp, color = ext.textSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(text = "Yayınlayan: ${announcement.authorName}", fontSize = 11.sp, color = ext.textMuted)
                            }
                        }
                    }
                }
            }
        }

        // Quick Room Shortcuts (Team Scoped)
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(text = "Sohbet & Çalışma Odaları", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                Spacer(modifier = Modifier.height(8.dp))

                val roomList = if (rooms.isNotEmpty()) rooms else listOf(
                    ChatRoom(roomId = "general", teamId = activeTeamId, name = "GENEL EKİP ODASI", description = "Tüm takım üyelerinin ortak iletişim alanı"),
                    ChatRoom(roomId = "software", teamId = activeTeamId, name = "YAZILIM EKİBİ ODASI", description = "Otonom ve algoritma geliştirme"),
                    ChatRoom(roomId = "mechanic", teamId = activeTeamId, name = "MEKANİK EKİBİ ODASI", description = "Robot şasisi ve mekanik montaj"),
                    ChatRoom(roomId = "pr", teamId = activeTeamId, name = "PR EKİBİ ODASI", description = "Sponsorluk ve sosyal medya")
                )

                roomList.forEach { room ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { onNavigateToRoom(room.roomId) },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, ext.cardBorder),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = ext.accentBlueContainer,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(imageVector = Icons.Default.QuestionAnswer, contentDescription = null, tint = ext.accentBlue, modifier = Modifier.size(20.dp))
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = room.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Text(text = room.description, fontSize = 12.sp, color = ext.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }

        // Active Team Members Carousel (Team Scoped)
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(text = "Takım Üyeleri (${approvedMembers.size})", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    itemsIndexed(
                        items = approvedMembers,
                        key = { index, m -> "${m.userId.ifBlank { m.id }}_$index" }
                    ) { _, member ->
                        val displayName = member.userName.ifBlank {
                            member.userEmail.substringBefore("@").ifBlank { "Takım Üyesi" }
                        }
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, ext.cardBorder),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.width(146.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = ext.accentBlueContainer,
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(text = displayName.take(1).uppercase(), fontWeight = FontWeight.Bold, color = ext.accentBlue, fontSize = 15.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = displayName,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                TeamBadge(teamName = member.subTeam.ifBlank { TeamType.GENERAL.displayName })
                                if (member.role.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = member.role,
                                        fontSize = 10.sp,
                                        color = ext.textMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddTaskDialog) {
        AddTeamTaskDialog(
            members = approvedMembers,
            onDismiss = { showAddTaskDialog = false },
            onSave = { title, description, subTeam, priority, dueDateText, assignedToId, assignedToName ->
                scope.launch {
                    try {
                        val res = repository.addTeamTask(
                            teamId = activeTeamId,
                            title = title,
                            description = description,
                            subTeam = subTeam,
                            assignedToId = assignedToId,
                            assignedToName = assignedToName,
                            createdByName = currentUserProfile?.getFullName() ?: "Takım Üyesi",
                            priority = priority,
                            dueDateText = dueDateText,
                            teamName = activeTeam.name
                        )
                        if (res.isSuccess) {
                            showAddTaskDialog = false
                            Toast.makeText(context, "Görev takıma eklendi!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(
                                context,
                                sanitizeUserErrorMessage(res.exceptionOrNull()),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } catch (ce: CancellationException) {
                        throw ce
                    } catch (e: Exception) {
                        Toast.makeText(context, sanitizeUserErrorMessage(e), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }
}

@Composable
private fun TeamTaskCard(
    task: TeamTask,
    canDelete: Boolean,
    onCycleStatus: () -> Unit,
    onDelete: () -> Unit
) {
    val ext = MaterialTheme.blackseaColors
    val normalizedStatus = TeamTaskStatus.normalize(task.status)
    val (statusColor, statusBg) = when (normalizedStatus) {
        TeamTaskStatus.DONE -> Pair(ext.emeraldText, ext.emeraldSolid.copy(alpha = 0.14f))
        TeamTaskStatus.IN_PROGRESS -> Pair(ext.amberText, ext.amberText.copy(alpha = 0.16f))
        else -> Pair(ext.accentBlue, ext.accentBlueContainer)
    }
    val priorityColor = when (task.priority) {
        "Yüksek" -> ext.dangerText
        "Düşük" -> ext.emeraldText
        else -> ext.amberText
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, ext.cardBorder),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statusBg,
                    modifier = Modifier
                        .clickable { onCycleStatus() }
                        .testTag("task_status_chip_${task.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (normalizedStatus == TeamTaskStatus.DONE) Icons.Default.CheckCircle else Icons.Default.Schedule,
                            contentDescription = "Durum Değiştir",
                            tint = statusColor,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = TeamTaskStatus.displayName(normalizedStatus),
                            color = statusColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = priorityColor.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = "${task.priority} Öncelik",
                            color = priorityColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                    if (canDelete) {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Görevi Sil",
                                tint = ext.dangerText,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Text(
                text = task.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (task.description.isNotBlank()) {
                Text(
                    text = task.description,
                    fontSize = 12.sp,
                    color = ext.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TeamBadge(teamName = task.subTeam.ifBlank { TeamType.GENERAL.displayName })
                    if (task.assignedToName.isNotBlank()) {
                        Text(
                            text = "• ${task.assignedToName}",
                            fontSize = 11.sp,
                            color = ext.textSecondary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                if (task.dueDateText.isNotBlank()) {
                    Text(
                        text = task.dueDateText,
                        fontSize = 11.sp,
                        color = ext.textMuted,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun AddTeamTaskDialog(
    members: List<com.example.model.TeamMembership>,
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        description: String,
        subTeam: String,
        priority: String,
        dueDateText: String,
        assignedToId: String,
        assignedToName: String
    ) -> Unit
) {
    val ext = MaterialTheme.blackseaColors
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedSubTeam by remember { mutableStateOf(TeamType.GENERAL.displayName) }
    var selectedPriority by remember { mutableStateOf("Orta") }
    var dueDateText by remember { mutableStateOf("") }
    var selectedAssigneeId by remember { mutableStateOf("") }
    var selectedAssigneeName by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }

    val subTeams = listOf(
        TeamType.GENERAL.displayName,
        TeamType.SOFTWARE.displayName,
        TeamType.MECHANIC.displayName,
        TeamType.PR.displayName,
        TeamType.MANAGEMENT.displayName
    )
    val priorities = listOf("Düşük", "Orta", "Yüksek")

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = {
            Text("Yeni Takım Görevi", fontWeight = FontWeight.Bold, fontSize = 17.sp)
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Görev Başlığı *") },
                    placeholder = { Text("Örn: Swerve Drive PID Kalibrasyonu") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Açıklama") },
                    placeholder = { Text("Görev detaylarını yazın...") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    minLines = 2,
                    maxLines = 4
                )

                Text("İlgili Alt Ekip", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ext.textSecondary)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    subTeams.forEach { st ->
                        FilterChip(
                            selected = selectedSubTeam == st,
                            onClick = { selectedSubTeam = st },
                            label = { Text(st, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ext.accentBlueContainer,
                                selectedLabelColor = ext.accentBlue
                            )
                        )
                    }
                }

                Text("Öncelik", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ext.textSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    priorities.forEach { prio ->
                        FilterChip(
                            selected = selectedPriority == prio,
                            onClick = { selectedPriority = prio },
                            label = { Text(prio, fontSize = 11.sp) }
                        )
                    }
                }

                if (members.isNotEmpty()) {
                    Text("Sorumlu Üye (İsteğe Bağlı)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ext.textSecondary)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = selectedAssigneeId.isBlank(),
                            onClick = {
                                selectedAssigneeId = ""
                                selectedAssigneeName = ""
                            },
                            label = { Text("Atanmadı", fontSize = 11.sp) }
                        )
                        members.forEach { mem ->
                            val name = mem.userName.ifBlank { mem.userEmail.substringBefore("@") }
                            FilterChip(
                                selected = selectedAssigneeId == mem.userId,
                                onClick = {
                                    selectedAssigneeId = mem.userId
                                    selectedAssigneeName = name
                                },
                                label = { Text(name, fontSize = 11.sp) }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = dueDateText,
                    onValueChange = { dueDateText = it },
                    label = { Text("Hedef Zaman (İsteğe Bağlı)") },
                    placeholder = { Text("Örn: 18 Mayıs • 17:00") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.trim().length >= 2) {
                        isSaving = true
                        onSave(
                            title.trim(),
                            description.trim(),
                            selectedSubTeam,
                            selectedPriority,
                            dueDateText.trim(),
                            selectedAssigneeId,
                            selectedAssigneeName
                        )
                    }
                },
                enabled = !isSaving && title.trim().length >= 2,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ext.accentBlueSolid,
                    contentColor = Color.White
                )
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text("Görevi Ekle", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text("İptal")
            }
        }
    )
}

@Composable
fun QuickStatCard(
    title: String,
    count: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    val ext = MaterialTheme.blackseaColors
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, ext.cardBorder),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = count, fontSize = 16.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
            Text(text = title, fontSize = 10.sp, color = ext.textSecondary, fontWeight = FontWeight.Medium)
        }
    }
}
