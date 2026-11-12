package com.example.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.example.model.UserStatus
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BlackseaRepository
import com.example.model.UserProfile
import com.example.ui.components.BlackseaTeamBanner
import com.example.ui.components.IdeaCategoryBadge
import com.example.ui.components.TeamBadge
import com.example.ui.components.UserRolesRow
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.PhoenixAmber

@Composable
fun HomeScreen(
    repository: BlackseaRepository,
    currentUserProfile: UserProfile?,
    onNavigateToRoom: (String) -> Unit,
    onNavigateToIdeas: () -> Unit,
    onNavigateToFiles: () -> Unit,
    onNavigateToAdmin: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val announcements by repository.observeAnnouncements().collectAsState(initial = emptyList())
    val rooms by repository.observeRooms().collectAsState(initial = emptyList())
    val ideas by repository.observeIdeas().collectAsState(initial = emptyList())
    val files by repository.observeSharedFiles().collectAsState(initial = emptyList())
    val allUsers by repository.observeAllUsers().collectAsState(initial = emptyList())
    val approvedUsers = remember(allUsers) {
        allUsers.filter { UserStatus.isActive(it.status) }
    }
    val activities by repository.observeTeamActivity().collectAsState(initial = emptyList())

    val isAdmin = currentUserProfile?.isSuperAdmin == true ||
            currentUserProfile?.email.equals("filim3290@gmail.com", ignoreCase = true) ||
            currentUserProfile?.roles?.any { it in listOf("Yönetici", "Takım Kaptanı", "Yetkili Öğretmen") } == true

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("home_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Branding Banner
        item {
            BlackseaTeamBanner()
        }

        // Welcome Card with Real Name & Multi-Roles
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
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
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TeamBadge(teamName = currentUserProfile?.team ?: "Yazılım Ekibi")
                        UserRolesRow(roles = currentUserProfile?.roles ?: listOf(currentUserProfile?.role ?: "Üye"))
                    }

                    if (isAdmin) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFE11D48).copy(alpha = 0.12f),
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
                                        tint = Color(0xFFE11D48),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Yönetici Paneli (Rol ve Duyuru Yönetimi)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFE11D48)
                                    )
                                }
                                Text("Aç >", fontSize = 11.sp, color = Color(0xFFE11D48), fontWeight = FontWeight.Bold)
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
                QuickStatCard(title = "Odalar", count = "4", icon = Icons.Default.ChatBubble, color = ElectricBlue, modifier = Modifier.weight(1f))
                QuickStatCard(title = "Fikirler", count = ideas.size.toString(), icon = Icons.Default.Lightbulb, color = PhoenixAmber, modifier = Modifier.weight(1f))
                QuickStatCard(title = "Dosyalar", count = files.size.toString(), icon = Icons.Default.Folder, color = Color(0xFF10B981), modifier = Modifier.weight(1f))
                QuickStatCard(title = "Üyeler", count = approvedUsers.size.toString(), icon = Icons.Default.Person, color = Color(0xFF8B5CF6), modifier = Modifier.weight(1f))
            }
        }

        // "Bugün Takımda Neler Oldu?" - Activity Feed Stream
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.ElectricBolt, contentDescription = null, tint = PhoenixAmber, modifier = Modifier.size(20.dp))
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
                                    color = ElectricBlue.copy(alpha = 0.15f),
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
                                        color = Color(0xFF94A3B8)
                                    )
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "Bugün henüz yeni bir aktivite kaydedilmedi. Odalarda mesajlaşabilir, yeni bir fikir ekleyebilir veya teknik çalışma dosyası yükleyebilirsiniz.",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8),
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
                        Icon(imageVector = Icons.Default.Campaign, contentDescription = null, tint = PhoenixAmber, modifier = Modifier.size(20.dp))
                        Text(text = "Takım Duyuruları", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    announcements.take(2).forEach { announcement ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (announcement.isPinned) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (announcement.isPinned) {
                                        Icon(imageVector = Icons.Default.PushPin, contentDescription = "Sabitlendi", tint = PhoenixAmber, modifier = Modifier.size(14.dp))
                                    }
                                    Text(text = announcement.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = announcement.content, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(text = "Yayınlayan: ${announcement.authorName}", fontSize = 11.sp, color = Color(0xFF94A3B8))
                            }
                        }
                    }
                }
            }
        }

        // Quick Room Shortcuts (Open to all!)
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(text = "Sohbet & Çalışma Odaları", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                Spacer(modifier = Modifier.height(8.dp))

                val roomList = if (rooms.isNotEmpty()) rooms else listOf(
                    com.example.model.ChatRoom(roomId = "general", name = "GENEL EKİP ODASI", description = "Tüm takım üyelerinin ortak iletişim alanı"),
                    com.example.model.ChatRoom(roomId = "software", name = "YAZILIM EKİBİ ODASI", description = "Otonom ve algoritma geliştirme"),
                    com.example.model.ChatRoom(roomId = "mechanic", name = "MEKANİK EKİBİ ODASI", description = "Robot şasisi ve mekanik montaj"),
                    com.example.model.ChatRoom(roomId = "pr", name = "PR EKİBİ ODASI", description = "Sponsorluk ve sosyal medya")
                )

                roomList.forEach { room ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { onNavigateToRoom(room.roomId) },
                        shape = RoundedCornerShape(12.dp),
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
                                color = ElectricBlue.copy(alpha = 0.15f),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(imageVector = Icons.Default.QuestionAnswer, contentDescription = null, tint = ElectricBlue, modifier = Modifier.size(20.dp))
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = room.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Text(text = room.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }

        // Active Team Members Carousel
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(text = "Takım Üyeleri (${approvedUsers.size})", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(approvedUsers) { user ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.width(140.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = ElectricBlue.copy(alpha = 0.2f),
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(text = user.getFullName().take(1).uppercase(), fontWeight = FontWeight.Bold, color = ElectricBlue, fontSize = 15.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(text = user.getFullName(), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Spacer(modifier = Modifier.height(3.dp))
                                TeamBadge(teamName = user.team)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QuickStatCard(
    title: String,
    count: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = count, fontSize = 16.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
            Text(text = title, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
        }
    }
}
