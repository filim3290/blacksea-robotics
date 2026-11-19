package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.credentials.CredentialManager
import com.example.data.BlackseaRepository
import com.example.model.PLATFORM_MAIN_TEAM_ID
import com.example.model.SupportTicket
import com.example.model.SupportTicketStatus
import com.example.model.TeamAppealStatus
import com.example.model.TeamInfo
import com.example.model.TeamMembership
import com.example.model.TeamNotification
import com.example.model.TeamStatus
import com.example.model.TeamType
import com.example.model.UserProfile
import com.example.ui.TeamNotificationsDialog
import com.example.ui.components.AdminSupportTab
import com.example.ui.components.AppUpdatesCard
import com.example.ui.components.BlackseaLogo
import com.example.ui.components.SupportCenterButtonCard
import com.example.ui.components.TeamBadge
import com.example.ui.components.TeamLogoBadge
import com.example.ui.components.parseTeamColor
import com.example.ui.theme.blackseaColors
import com.example.update.AppReleaseInfo
import com.example.update.AppUpdateManager
import com.example.util.sanitizeUserErrorMessage
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TeamHubScreen(
    currentUserId: String,
    currentUserProfile: UserProfile?,
    approvedTeams: List<TeamInfo>,
    myKnownTeams: List<TeamInfo> = emptyList(),
    myMemberships: List<TeamMembership>,
    myTeamApplications: List<TeamInfo>,
    repository: BlackseaRepository,
    notifications: List<TeamNotification>,
    updateManager: AppUpdateManager? = null,
    watchedTeamIds: Set<String> = emptySet(),
    availableUpdate: AppReleaseInfo? = null,
    locallyReadIds: Set<String> = emptySet(),
    unreadNotificationsCount: Int = 0,
    hasUnreadUpdate: Boolean = false,
    isPlatformAdmin: Boolean = false,
    onMarkAsRead: (String) -> Unit = {},
    onMarkAllAsRead: () -> Unit = {},
    onNotificationClick: (TeamNotification) -> Unit = {},
    onShowUpdateDialog: (AppReleaseInfo) -> Unit = {},
    onReadUpdateClick: (String) -> Unit = {},
    onEnterTeam: (String) -> Unit,
    requestedPlatformConsoleTab: String? = null,
    onConsumePlatformConsoleTab: () -> Unit = {},
    requestedOpenAccountSheet: Boolean = false,
    onConsumeOpenAccountSheet: () -> Unit = {},
    onSignOut: () -> Unit,
    onAccountDeleted: () -> Unit = onSignOut,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val ext = MaterialTheme.blackseaColors
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    val resolvedUpdateManager = updateManager ?: remember { AppUpdateManager(context) }

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showNotificationsDialog by remember { mutableStateOf(false) }
    var showCreateTeamOverlay by rememberSaveable { mutableStateOf(false) }
    var showCreateTeamSuccessDialog by rememberSaveable { mutableStateOf(false) }
    var joinTargetTeam by remember { mutableStateOf<TeamInfo?>(null) }
    var appealTargetPair by remember { mutableStateOf<Pair<TeamInfo, TeamMembership>?>(null) }
    var showAccountSheet by remember { mutableStateOf(false) }
    var showSignOutConfirmation by remember { mutableStateOf(false) }
    var showPlatformConsoleDialog by rememberSaveable { mutableStateOf(false) }
    var platformConsoleTabKey by rememberSaveable { mutableStateOf("applications") }
    var watchUpdatingTeamIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(requestedPlatformConsoleTab, isPlatformAdmin) {
        if (isPlatformAdmin && !requestedPlatformConsoleTab.isNullOrBlank()) {
            platformConsoleTabKey = requestedPlatformConsoleTab
            showPlatformConsoleDialog = true
            onConsumePlatformConsoleTab()
        }
    }

    LaunchedEffect(requestedOpenAccountSheet) {
        if (requestedOpenAccountSheet) {
            showAccountSheet = true
            onConsumeOpenAccountSheet()
        }
    }

    val pendingTeamApplicationsForAdmin by remember(isPlatformAdmin) {
        if (isPlatformAdmin) repository.observePendingTeamApplications()
        else flowOf(emptyList())
    }.collectAsState(initial = emptyList())

    val allPlatformTeamsForAdmin by remember(isPlatformAdmin) {
        if (isPlatformAdmin) repository.observeAllTeamsForPlatformAdmin()
        else flowOf(emptyList())
    }.collectAsState(initial = emptyList())

    val allSupportTicketsForAdmin by remember(isPlatformAdmin) {
        if (isPlatformAdmin) repository.observeAllSupportTickets(PLATFORM_MAIN_TEAM_ID)
        else flowOf(emptyList())
    }.collectAsState(initial = emptyList())

    val openSupportCountForAdmin = remember(allSupportTicketsForAdmin) {
        allSupportTicketsForAdmin.count { SupportTicketStatus.normalize(it.status) != SupportTicketStatus.RESOLVED }
    }
    val pendingAppealsCountForAdmin = remember(allPlatformTeamsForAdmin) {
        allPlatformTeamsForAdmin.count { it.appealStatus.equals(TeamAppealStatus.PENDING, ignoreCase = true) }
    }
    val developerAlertCount = pendingTeamApplicationsForAdmin.size + pendingAppealsCountForAdmin + openSupportCountForAdmin

    val membershipByTeamId = remember(myMemberships) {
        myMemberships.associateBy { it.teamId }
    }

    val activeMemberships = remember(myMemberships) {
        myMemberships.filter { it.isActive() }
    }
    val revokedMemberships = remember(myMemberships) {
        myMemberships.filter { it.isRevoked() }
    }

    val teamMap = remember(approvedTeams, myKnownTeams) {
        val map = approvedTeams.associateBy { it.id }.toMutableMap()
        myKnownTeams.forEach { team ->
            if (team.id.isNotBlank()) {
                map[team.id] = team
            }
        }
        if (!map.containsKey(PLATFORM_MAIN_TEAM_ID)) {
            map[PLATFORM_MAIN_TEAM_ID] = TeamInfo.mainBlackseaTeam()
        }
        map
    }

    val myActiveTeams = remember(activeMemberships, teamMap) {
        activeMemberships.mapNotNull { mem ->
            val team = teamMap[mem.teamId]
                ?: if (mem.teamId == PLATFORM_MAIN_TEAM_ID) TeamInfo.mainBlackseaTeam() else null
            if (team != null) team to mem else null
        }.sortedWith(
            compareByDescending<Pair<TeamInfo, TeamMembership>> {
                it.first.isMainTeam || it.first.id == PLATFORM_MAIN_TEAM_ID
            }.thenBy { it.first.name.lowercase() }
        )
    }

    val mySuspendedTeams = remember(revokedMemberships, teamMap) {
        revokedMemberships.mapNotNull { mem ->
            val team = teamMap[mem.teamId]
                ?: if (mem.teamId == PLATFORM_MAIN_TEAM_ID) TeamInfo.mainBlackseaTeam() else null
            if (team != null) team to mem else null
        }.sortedBy { it.first.name.lowercase() }
    }

    val excludedFromDirectoryIds = remember(myActiveTeams, mySuspendedTeams) {
        (myActiveTeams.map { it.first.id } + mySuspendedTeams.map { it.first.id }).toSet()
    }

    val otherApprovedTeams = remember(approvedTeams, excludedFromDirectoryIds, searchQuery) {
        val allApproved = if (approvedTeams.any { it.id == PLATFORM_MAIN_TEAM_ID || it.isMainTeam }) {
            approvedTeams
        } else {
            listOf(TeamInfo.mainBlackseaTeam()) + approvedTeams
        }
        val query = searchQuery.trim().lowercase()
        allApproved
            .filter { !excludedFromDirectoryIds.contains(it.id) }
            .filter { team ->
                if (query.isBlank()) true
                else {
                    team.name.lowercase().contains(query) ||
                        team.shortCode.lowercase().contains(query) ||
                        team.schoolName.lowercase().contains(query) ||
                        team.city.lowercase().contains(query) ||
                        team.competition.lowercase().contains(query) ||
                        team.description.lowercase().contains(query)
                }
            }
    }

    BackHandler(enabled = showCreateTeamOverlay || showPlatformConsoleDialog) {
        when {
            showCreateTeamOverlay -> showCreateTeamOverlay = false
            showPlatformConsoleDialog -> showPlatformConsoleDialog = false
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("team_hub_screen"),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BlackseaLogo(size = 30)
                        Column {
                            Text(
                                text = "Takımlar Merkezi",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isPlatformAdmin) "Uygulama Geliştirici Konsolu" else "BlackSea Robotics Platformu",
                                fontSize = 10.sp,
                                color = if (isPlatformAdmin) ext.adminRedText else ext.textMuted
                            )
                        }
                    }
                },
                actions = {
                    if (isPlatformAdmin) {
                        IconButton(
                            onClick = { showPlatformConsoleDialog = true },
                            modifier = Modifier.testTag("team_hub_platform_console_button")
                        ) {
                            BadgedBox(
                                badge = {
                                    if (developerAlertCount > 0) {
                                        Badge(
                                            containerColor = ext.adminRedSolid,
                                            contentColor = Color.White
                                        ) {
                                            Text(
                                                text = if (developerAlertCount > 9) "9+" else "$developerAlertCount",
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AdminPanelSettings,
                                    contentDescription = "Geliştirici Yönetim Merkezi",
                                    tint = ext.adminRedText
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = { showNotificationsDialog = true },
                        modifier = Modifier.testTag("team_hub_notifications_button")
                    ) {
                        BadgedBox(
                            badge = {
                                if (unreadNotificationsCount > 0) {
                                    Badge(
                                        containerColor = ext.dangerSolid,
                                        contentColor = Color.White
                                    ) {
                                        Text(
                                            text = if (unreadNotificationsCount > 9) "9+" else "$unreadNotificationsCount",
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = "Bildirimler",
                                tint = if (unreadNotificationsCount > 0) ext.accentBlue else ext.textMuted
                            )
                        }
                    }
                    IconButton(
                        onClick = { showAccountSheet = true },
                        modifier = Modifier.testTag("team_hub_account_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Hesap ve Çıkış",
                            tint = ext.textSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 760.dp),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Hero & Create Team CTA
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                        elevation = CardDefaults.cardElevation(defaultElevation = if (ext.isDark) 4.dp else 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Brush.horizontalGradient(ext.bannerGradientColors))
                                .padding(18.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    text = "Merhaba, ${currentUserProfile?.getFullName()?.ifBlank { "Kullanıcı" } ?: "Kullanıcı"}",
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    text = "Üyesi olduğun takımların çalışma alanlarına geçiş yapabilir, aktif takımlara katılma başvurusu gönderebilir veya kendi yarışma ekibini kurabilirsin.",
                                    color = Color.White.copy(alpha = 0.86f),
                                    fontSize = 12.5.sp,
                                    lineHeight = 18.sp
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color.White.copy(alpha = 0.16f)
                                    ) {
                                        Text(
                                            text = "${myActiveTeams.size} Aktif Takımım",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.weight(1f))

                                    Button(
                                        onClick = { showCreateTeamOverlay = true },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color.White,
                                            contentColor = ext.accentBlue
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                        modifier = Modifier
                                            .heightIn(min = 42.dp)
                                            .testTag("team_hub_create_team_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AddCircleOutline,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Kendi Ekibini Oluştur",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Platform Developer Console card (for filim3290@gmail.com only) directly in Team Hub
                if (isPlatformAdmin) {
                    val hasUrgentDeveloperAction = pendingTeamApplicationsForAdmin.isNotEmpty() || pendingAppealsCountForAdmin > 0 || openSupportCountForAdmin > 0
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showPlatformConsoleDialog = true }
                                .testTag("platform_developer_console_banner"),
                            shape = RoundedCornerShape(18.dp),
                            border = BorderStroke(
                                width = 1.5.dp,
                                color = if (hasUrgentDeveloperAction) ext.adminRedSolid.copy(alpha = 0.5f) else ext.cardBorder
                            ),
                            colors = CardDefaults.cardColors(
                                containerColor = if (hasUrgentDeveloperAction) {
                                    ext.adminRedSolid.copy(alpha = 0.08f)
                                } else {
                                    MaterialTheme.colorScheme.surface
                                }
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = ext.adminRedSolid,
                                        modifier = Modifier.size(42.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.AdminPanelSettings,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Uygulama Geliştirici Yönetimi",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Black,
                                            color = ext.adminRedText
                                        )
                                        Text(
                                            text = "Takım başvuruları, platform takımları, üye listeleri ve destek talepleri",
                                            fontSize = 12.sp,
                                            color = ext.textSecondary
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = "Geliştirici Paneli",
                                        tint = ext.adminRedText
                                    )
                                }

                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (pendingTeamApplicationsForAdmin.isNotEmpty()) ext.amberText.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier.clickable {
                                            platformConsoleTabKey = "applications"
                                            showPlatformConsoleDialog = true
                                        }
                                    ) {
                                        Text(
                                            text = "Başvurular (${pendingTeamApplicationsForAdmin.size})",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (pendingTeamApplicationsForAdmin.isNotEmpty()) ext.amberText else ext.textSecondary,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (pendingAppealsCountForAdmin > 0) ext.dangerSolid.copy(alpha = 0.14f) else ext.accentBlueContainer,
                                        modifier = Modifier.clickable {
                                            platformConsoleTabKey = "teams"
                                            showPlatformConsoleDialog = true
                                        }
                                    ) {
                                        Text(
                                            text = if (pendingAppealsCountForAdmin > 0) {
                                                "Takımlar (${allPlatformTeamsForAdmin.size}) • $pendingAppealsCountForAdmin İtiraz"
                                            } else {
                                                "Takımlar (${allPlatformTeamsForAdmin.size})"
                                            },
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (pendingAppealsCountForAdmin > 0) ext.dangerText else ext.accentBlue,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (openSupportCountForAdmin > 0) ext.adminRedSolid.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier.clickable {
                                            platformConsoleTabKey = "support"
                                            showPlatformConsoleDialog = true
                                        }
                                    ) {
                                        Text(
                                            text = "Destek ($openSupportCountForAdmin Açık)",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (openSupportCountForAdmin > 0) ext.adminRedText else ext.textSecondary,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Section 1: "Takımlarım"
                val totalMyTeamCount = myActiveTeams.size + mySuspendedTeams.size
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Takımlarım ($totalMyTeamCount)",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }

                if (myActiveTeams.isEmpty() && mySuspendedTeams.isEmpty()) {
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
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Groups,
                                    contentDescription = null,
                                    tint = ext.accentBlue,
                                    modifier = Modifier.size(34.dp)
                                )
                                Text(
                                    text = if (isPlatformAdmin) {
                                        "Geliştirici hesabı bağımsız yönetimde"
                                    } else {
                                        "Henüz aktif üyesi olduğunuz bir takım yok"
                                    },
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = if (isPlatformAdmin) {
                                        "BlackSea Robotics takımı dahil tüm ekipleri yukarıdaki Uygulama Geliştirici Yönetimi panelinden inceleyebilir, üye listelerine erişebilirsiniz."
                                    } else {
                                        "Aşağıdaki 'Diğer Takımlar' listesinden istediğiniz takıma katılma başvurusu gönderebilir veya 'Kendi Ekibini Oluştur' butonunu kullanabilirsiniz."
                                    },
                                    fontSize = 12.sp,
                                    color = ext.textSecondary,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 17.sp
                                )
                            }
                        }
                    }
                } else {
                    items(
                        items = myActiveTeams,
                        key = { (team, _) -> "my_team_${team.id}" }
                    ) { (team, membership) ->
                        MyActiveTeamCard(
                            team = team,
                            membership = membership,
                            onEnterClick = { onEnterTeam(team.id) }
                        )
                    }

                    items(
                        items = mySuspendedTeams,
                        key = { (team, _) -> "suspended_team_${team.id}" }
                    ) { (team, membership) ->
                        MySuspendedTeamCard(
                            team = team,
                            membership = membership,
                            onAppealClick = { appealTargetPair = team to membership }
                        )
                    }
                }

                // Section 2: "Takım Kurma Başvurularım" (if user submitted any team applications)
                if (myTeamApplications.isNotEmpty()) {
                    item {
                        Text(
                            text = "Takım Kurma Başvurularım (${myTeamApplications.size})",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    items(
                        items = myTeamApplications,
                        key = { "my_app_${it.id}" }
                    ) { appTeam ->
                        MyTeamApplicationStatusCard(team = appTeam)
                    }
                }

                // Section 3: "Diğer Takımlar"
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Diğer Takımlar",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(top = 4.dp)
                        )

                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Takım, okul, şehir veya yarışma ara...", fontSize = 13.sp) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Ara",
                                    tint = ext.textMuted
                                )
                            },
                            trailingIcon = {
                                if (searchQuery.isNotBlank()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Temizle",
                                            tint = ext.textMuted
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("team_hub_search_input")
                        )
                    }
                }

                if (otherApprovedTeams.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, ext.cardBorder),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Text(
                                text = if (searchQuery.isNotBlank()) {
                                    "Aramanızla eşleşen takım bulunamadı."
                                } else {
                                    "Platformdaki tüm takımlara zaten üyesiniz."
                                },
                                fontSize = 13.sp,
                                color = ext.textSecondary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp)
                            )
                        }
                    }
                } else {
                    items(
                        items = otherApprovedTeams,
                        key = { "other_team_${it.id}" }
                    ) { team ->
                        DiscoverableTeamCard(
                            team = team,
                            membership = membershipByTeamId[team.id],
                            isWatching = watchedTeamIds.contains(team.id),
                            isWatchUpdating = watchUpdatingTeamIds.contains(team.id),
                            onApplyClick = { joinTargetTeam = team },
                            onToggleWatchClick = {
                                if (watchUpdatingTeamIds.contains(team.id)) return@DiscoverableTeamCard
                                val nextWatch = !watchedTeamIds.contains(team.id)
                                watchUpdatingTeamIds = watchUpdatingTeamIds + team.id
                                scope.launch {
                                    val res = repository.setTeamReopenWatch(team, nextWatch)
                                    watchUpdatingTeamIds = watchUpdatingTeamIds - team.id
                                    if (res.isSuccess) {
                                        Toast.makeText(
                                            context,
                                            if (nextWatch) {
                                                "${team.name} çalışma alanı açıldığında size bildirim gönderilecek."
                                            } else {
                                                "${team.name} için açılış bildirimi takibi iptal edildi."
                                            },
                                            Toast.LENGTH_LONG
                                        ).show()
                                    } else {
                                        Toast.makeText(
                                            context,
                                            sanitizeUserErrorMessage(res.exceptionOrNull(), "İşlem tamamlanamadı."),
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            }
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(20.dp)) }
            }
        }
    }

    // Sub-team & join note dialog
    joinTargetTeam?.let { targetTeam ->
        val existingMembership = membershipByTeamId[targetTeam.id]
        var selectedSubTeam by remember(targetTeam.id) {
            mutableStateOf(
                existingMembership?.subTeam?.takeIf { it.isNotBlank() } ?: TeamType.SOFTWARE.displayName
            )
        }
        var joinNote by remember(targetTeam.id) { mutableStateOf("") }
        var isSubmitting by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isSubmitting) joinTargetTeam = null },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TeamLogoBadge(team = targetTeam, size = 38)
                    Column {
                        Text(
                            text = targetTeam.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "Katılma Başvurusu",
                            fontSize = 12.sp,
                            color = ext.textSecondary
                        )
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Katılmak istediğiniz alt ekibi seçin:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TeamType.entries.forEach { teamType ->
                            FilterChip(
                                selected = selectedSubTeam == teamType.displayName,
                                onClick = { if (!isSubmitting) selectedSubTeam = teamType.displayName },
                                label = { Text(teamType.displayName, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ext.accentBlueContainer,
                                    selectedLabelColor = ext.accentBlue
                                )
                            )
                        }
                    }

                    OutlinedTextField(
                        value = joinNote,
                        onValueChange = { if (it.length <= 250) joinNote = it },
                        label = { Text("Başvuru Notu (Opsiyonel)") },
                        placeholder = { Text("Deneyiminizden veya ilginizden kısaca bahsedin...") },
                        minLines = 2,
                        maxLines = 4,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val fallbackUser = Firebase.auth.currentUser
                        val profile = currentUserProfile ?: UserProfile(
                            id = currentUserId,
                            userId = currentUserId,
                            displayName = fallbackUser?.displayName ?: "Öğrenci",
                            email = fallbackUser?.email.orEmpty(),
                            photoUrl = fallbackUser?.photoUrl?.toString().orEmpty()
                        )
                        isSubmitting = true
                        scope.launch {
                            val res = repository.requestJoinTeam(
                                team = targetTeam,
                                profile = profile,
                                subTeam = selectedSubTeam,
                                joinNote = joinNote
                            )
                            isSubmitting = false
                            if (res.isSuccess) {
                                joinTargetTeam = null
                                Toast.makeText(
                                    context,
                                    "${targetTeam.name} takımına başvurunuz gönderildi.",
                                    Toast.LENGTH_LONG
                                ).show()
                            } else {
                                Toast.makeText(
                                    context,
                                    sanitizeUserErrorMessage(
                                        res.exceptionOrNull() ?: IllegalStateException("Başvuru gönderilemedi.")
                                    ),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    },
                    enabled = !isSubmitting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ext.accentBlueSolid,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("confirm_join_team_button")
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Başvuruyu Gönder", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { joinTargetTeam = null }, enabled = !isSubmitting) {
                    Text("İptal")
                }
            }
        )
    }

    // Create Team Fullscreen Dialog
    if (showCreateTeamOverlay) {
        val firebaseUser = Firebase.auth.currentUser
        Dialog(
            onDismissRequest = { showCreateTeamOverlay = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(colors = ext.authGradientColors))
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(16.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 640.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    TeamCreationApplicationCard(
                        repository = repository,
                        connectedEmail = currentUserProfile?.email?.ifBlank { null }
                            ?: firebaseUser?.email.orEmpty(),
                        defaultManagerName = currentUserProfile?.getFullName()?.ifBlank { null }
                            ?: firebaseUser?.displayName.orEmpty(),
                        founderPhotoUrl = currentUserProfile?.photoUrl?.ifBlank { null }
                            ?: firebaseUser?.photoUrl?.toString().orEmpty(),
                        onBackClick = { showCreateTeamOverlay = false },
                        onSubmitted = {
                            showCreateTeamOverlay = false
                            showCreateTeamSuccessDialog = true
                        }
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }

    if (showCreateTeamSuccessDialog) {
        AlertDialog(
            onDismissRequest = { showCreateTeamSuccessDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = ext.emeraldText,
                    modifier = Modifier.size(34.dp)
                )
            },
            title = {
                Text("Başvurunuz Gönderildi", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    text = "Takım oluşturma başvurunuz BlackSea Robotics yöneticilerine gönderildi. Onaylandığında bildirim alacaksınız ve ekibinizin bağımsız çalışma alanı otomatik olarak açılacaktır.",
                    fontSize = 13.sp,
                    color = ext.textSecondary,
                    lineHeight = 19.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { showCreateTeamSuccessDialog = false },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ext.accentBlueSolid,
                        contentColor = Color.White
                    )
                ) {
                    Text("Tamam", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Account, App Updates, Support Center & Sign Out Dialog
    if (showAccountSheet) {
        val fallbackAuthUser = Firebase.auth.currentUser
        val resolvedSupportProfile = currentUserProfile ?: UserProfile(
            id = currentUserId,
            userId = currentUserId,
            displayName = fallbackAuthUser?.displayName?.takeIf { it.isNotBlank() } ?: "Kullanıcı",
            email = fallbackAuthUser?.email.orEmpty(),
            photoUrl = fallbackAuthUser?.photoUrl?.toString().orEmpty()
        )
        val primaryPair = myActiveTeams.firstOrNull()
        val resolvedTeamId = primaryPair?.first?.id ?: PLATFORM_MAIN_TEAM_ID
        val resolvedTeamName = primaryPair?.first?.name ?: "Takımlar Merkezi"
        val resolvedSubTeam = primaryPair?.second?.subTeam?.takeIf { it.isNotBlank() } ?: "Genel Kullanıcı"

        Dialog(
            onDismissRequest = { showAccountSheet = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            BackHandler { showAccountSheet = false }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 24.dp)
                    .widthIn(max = 540.dp)
                    .imePadding()
                    .testTag("team_hub_account_dialog"),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.background,
                tonalElevation = 4.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = ext.accentBlueContainer,
                            modifier = Modifier.size(46.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = resolvedSupportProfile.getFullName().take(1).uppercase().ifBlank { "K" },
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black,
                                    color = ext.accentBlue
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = resolvedSupportProfile.getFullName().ifBlank { "Kullanıcı" },
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (resolvedSupportProfile.email.isNotBlank()) {
                                Text(
                                    text = resolvedSupportProfile.email,
                                    fontSize = 12.sp,
                                    color = ext.textMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = ext.accentBlueContainer
                            ) {
                                Text(
                                    text = if (myActiveTeams.isEmpty()) "Henüz takım üyeliği yok" else "${myActiveTeams.size} Aktif Takım Üyeliği",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ext.accentBlue,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                        IconButton(
                            onClick = { showAccountSheet = false },
                            modifier = Modifier.testTag("close_team_hub_account_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Kapat",
                                tint = ext.textMuted
                            )
                        }
                    }

                    HorizontalDivider(color = ext.cardBorder)

                    AppUpdatesCard(
                        updateManager = resolvedUpdateManager,
                        onShowUpdateDialog = { release ->
                            showAccountSheet = false
                            onShowUpdateDialog(release)
                        }
                    )

                    SupportCenterButtonCard(
                        currentUserProfile = resolvedSupportProfile,
                        teamId = resolvedTeamId,
                        teamName = resolvedTeamName,
                        userSubTeam = resolvedSubTeam,
                        repository = repository,
                        updateManager = resolvedUpdateManager
                    )

                    OutlinedButton(
                        onClick = {
                            showAccountSheet = false
                            showCreateTeamOverlay = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("account_dialog_create_team_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.AddCircleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Kendi Ekibini Oluştur", fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = {
                            showAccountSheet = false
                            showSignOutConfirmation = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("account_dialog_sign_out_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ext.dangerSolid,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Oturumu Kapat", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showSignOutConfirmation) {
        AlertDialog(
            onDismissRequest = { showSignOutConfirmation = false },
            title = { Text("Oturumu Kapat", fontWeight = FontWeight.Bold) },
            text = { Text("Google hesabınızdan çıkış yapmak istediğinize emin misiniz?") },
            confirmButton = {
                Button(
                    onClick = {
                        showSignOutConfirmation = false
                        appSignOut(credentialManager, scope, onSignOut)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ext.dangerSolid,
                        contentColor = Color.White
                    )
                ) {
                    Text("Çıkış Yap", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutConfirmation = false }) {
                    Text("İptal")
                }
            }
        )
    }

    // Member suspension appeal dialog
    appealTargetPair?.let { (targetTeam, targetMembership) ->
        var memberAppealText by remember(targetTeam.id) { mutableStateOf("") }
        var isSubmittingMemberAppeal by remember { mutableStateOf(false) }
        val trimmedAppeal = memberAppealText.trim()
        val isAppealValid = trimmedAppeal.length >= 10

        AlertDialog(
            onDismissRequest = { if (!isSubmittingMemberAppeal) appealTargetPair = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.Gavel,
                    contentDescription = null,
                    tint = ext.accentBlue,
                    modifier = Modifier.size(30.dp)
                )
            },
            title = {
                Text(
                    text = "${targetTeam.name} • Askıya Alma İtirazı",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (targetMembership.suspensionReason.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = ext.dangerContainer.copy(alpha = 0.7f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Gerekçe: ${targetMembership.suspensionReason}",
                                fontSize = 12.sp,
                                color = ext.dangerText,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                    Text(
                        text = "Yeniden üyelik başvurusu yapmanıza gerek yoktur. Askıya alma kararına itirazınızı takım yetkililerine doğrudan iletebilirsiniz.",
                        fontSize = 12.sp,
                        color = ext.textSecondary,
                        lineHeight = 18.sp
                    )
                    OutlinedTextField(
                        value = memberAppealText,
                        onValueChange = { if (it.length <= 1000) memberAppealText = it },
                        label = { Text("İtiraz Açıklamanız (En az 10 karakter)") },
                        placeholder = { Text("Durumla ilgili açıklamanızı yazın...") },
                        minLines = 3,
                        maxLines = 6,
                        isError = memberAppealText.isNotBlank() && !isAppealValid,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("member_appeal_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isSubmittingMemberAppeal = true
                        val fallbackUser = Firebase.auth.currentUser
                        val displayName = currentUserProfile?.getFullName()?.ifBlank { null }
                            ?: fallbackUser?.displayName
                            ?: targetMembership.userName.ifBlank { "Takım Üyesi" }
                        scope.launch {
                            try {
                                val res = repository.submitMembershipAppeal(
                                    team = targetTeam,
                                    memberName = displayName,
                                    appealText = trimmedAppeal
                                )
                                if (res.isSuccess) {
                                    appealTargetPair = null
                                    Toast.makeText(
                                        context,
                                        "İtirazınız ${targetTeam.name} yetkililerine gönderildi.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } else {
                                    Toast.makeText(
                                        context,
                                        sanitizeUserErrorMessage(res.exceptionOrNull(), "İtiraz gönderilemedi."),
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            } catch (ce: CancellationException) {
                                throw ce
                            } finally {
                                isSubmittingMemberAppeal = false
                            }
                        }
                    },
                    enabled = isAppealValid && !isSubmittingMemberAppeal,
                    colors = ButtonDefaults.buttonColors(containerColor = ext.accentBlueSolid, contentColor = Color.White),
                    modifier = Modifier.testTag("confirm_member_appeal_button")
                ) {
                    if (isSubmittingMemberAppeal) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("İtirazı Gönder", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { appealTargetPair = null }, enabled = !isSubmittingMemberAppeal) {
                    Text("Vazgeç")
                }
            }
        )
    }

    if (showPlatformConsoleDialog && isPlatformAdmin) {
        PlatformDeveloperConsoleDialog(
            selectedTabKey = platformConsoleTabKey,
            onSelectTab = { platformConsoleTabKey = it },
            pendingApplications = pendingTeamApplicationsForAdmin,
            allTeams = allPlatformTeamsForAdmin,
            supportTickets = allSupportTicketsForAdmin,
            openSupportCount = openSupportCountForAdmin,
            currentUserProfile = currentUserProfile,
            repository = repository,
            onDismiss = { showPlatformConsoleDialog = false }
        )
    }

    if (showNotificationsDialog) {
        TeamNotificationsDialog(
            notifications = notifications,
            currentUserId = currentUserId,
            locallyReadIds = locallyReadIds,
            availableUpdate = availableUpdate,
            isUpdateRead = !hasUnreadUpdate,
            onUpdateClick = { release ->
                onReadUpdateClick(release.tagName)
                showNotificationsDialog = false
                onShowUpdateDialog(release)
            },
            onDismiss = { showNotificationsDialog = false },
            onMarkAsRead = onMarkAsRead,
            onNotificationClick = { notif ->
                showNotificationsDialog = false
                onNotificationClick(notif)
            },
            onMarkAllAsRead = onMarkAllAsRead
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MyActiveTeamCard(
    team: TeamInfo,
    membership: TeamMembership,
    onEnterClick: () -> Unit
) {
    val ext = MaterialTheme.blackseaColors
    val accent = parseTeamColor(team.accentColorHex, ext.accentBlue)
    val isBlocked = team.isWorkspaceBlocked()
    val canAppealBlocked = membership.isManagerInTeam()
    val blockColor = if (team.isSuspended()) ext.dangerText else ext.amberText

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEnterClick() }
            .testTag("my_team_card_${team.id}"),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            width = 1.2.dp,
            color = if (isBlocked) blockColor.copy(alpha = 0.55f) else accent.copy(alpha = 0.45f)
        ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = if (ext.isDark) 2.dp else 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TeamLogoBadge(team = team, size = 52)
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = team.name,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (team.shortCode.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = accent.copy(alpha = 0.16f)
                            ) {
                                Text(
                                    text = team.shortCode,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = accent,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    val loc = listOf(team.schoolName, team.city).filter { it.isNotBlank() }.joinToString(" • ")
                    if (loc.isNotBlank()) {
                        Text(
                            text = loc,
                            fontSize = 12.sp,
                            color = ext.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (team.competition.isNotBlank()) {
                        Text(
                            text = team.competition,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = accent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TeamBadge(teamName = membership.subTeam.ifBlank { TeamType.GENERAL.displayName })
                if (isBlocked) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = blockColor.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = TeamStatus.displayName(team.status),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = blockColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                if (membership.isManagerInTeam()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = ext.adminRedSolid.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = "Yetkili Öğretmen / Kaptan",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = ext.adminRedText,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                } else if (membership.role.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = membership.role,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = ext.textSecondary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                if (team.isMainTeam || team.id == PLATFORM_MAIN_TEAM_ID) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = ext.amberText.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = "Ana Takım",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = ext.amberText,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            if (isBlocked && team.moderationReason.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = blockColor.copy(alpha = 0.10f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Durum Notu: ${team.moderationReason}",
                        fontSize = 12.sp,
                        color = blockColor,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            Button(
                onClick = onEnterClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isBlocked) blockColor else accent,
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = when {
                        isBlocked && canAppealBlocked -> "Durumu Görüntüle / İtiraz Et"
                        isBlocked -> "Çalışma Alanı Kapalı (Durumu Gör)"
                        else -> "Çalışma Alanına Gir"
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = if (isBlocked) Icons.Default.PauseCircle else Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun MySuspendedTeamCard(
    team: TeamInfo,
    membership: TeamMembership,
    onAppealClick: () -> Unit
) {
    val ext = MaterialTheme.blackseaColors
    val isPendingAppeal = membership.hasPendingAppeal()
    val isRejectedAppeal = membership.appealStatus.equals(TeamAppealStatus.REJECTED, ignoreCase = true)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("my_suspended_team_card_${team.id}"),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.2.dp, ext.dangerText.copy(alpha = 0.45f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TeamLogoBadge(team = team, size = 48)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = team.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = membership.subTeam.ifBlank { TeamType.GENERAL.displayName },
                        fontSize = 12.sp,
                        color = ext.textSecondary
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = ext.dangerContainer
                ) {
                    Text(
                        text = "Üyelik Askıda",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = ext.dangerText,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = ext.dangerContainer.copy(alpha = 0.65f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Askıya Alma Gerekçesi",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = ext.dangerText
                    )
                    Text(
                        text = membership.suspensionReason.ifBlank { "Takım yetkilisi tarafından erişiminiz geçici olarak askıya alındı." },
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 18.sp
                    )
                }
            }

            if (membership.appealText.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isPendingAppeal) ext.accentBlueContainer else MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, if (isPendingAppeal) ext.accentBlue.copy(alpha = 0.35f) else ext.cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = when {
                                isPendingAppeal -> "İtirazınız İnceleniyor"
                                isRejectedAppeal -> "Önceki İtiraz Reddedildi"
                                else -> "Gönderilen İtiraz"
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isPendingAppeal) ext.accentBlue else if (isRejectedAppeal) ext.dangerText else ext.textMuted
                        )
                        Text(
                            text = membership.appealText,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Button(
                onClick = onAppealClick,
                enabled = !isPendingAppeal,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .testTag("member_appeal_button_${team.id}"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ext.accentBlueSolid,
                    contentColor = Color.White
                )
            ) {
                Icon(imageVector = Icons.Default.Gavel, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = when {
                        isPendingAppeal -> "İtiraz İnceleniyor"
                        isRejectedAppeal -> "Yeniden İtiraz Et"
                        else -> "İtiraz Et"
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun DiscoverableTeamCard(
    team: TeamInfo,
    membership: TeamMembership?,
    isWatching: Boolean = false,
    isWatchUpdating: Boolean = false,
    onApplyClick: () -> Unit,
    onToggleWatchClick: () -> Unit = {}
) {
    val ext = MaterialTheme.blackseaColors
    val accent = parseTeamColor(team.accentColorHex, ext.accentBlue)
    val isPending = membership?.isPending() == true
    val isRejected = membership?.isRejected() == true
    val isBlocked = team.isWorkspaceBlocked()
    val blockColor = if (team.isSuspended()) ext.dangerText else ext.amberText

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("other_team_card_${team.id}"),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = if (isBlocked) 1.2.dp else 1.dp,
            color = if (isBlocked) blockColor.copy(alpha = 0.45f) else ext.cardBorder
        ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TeamLogoBadge(team = team, size = 48)
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = team.name,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (team.shortCode.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = accent.copy(alpha = 0.14f)
                            ) {
                                Text(
                                    text = team.shortCode,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = accent,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                        if (isBlocked) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = blockColor.copy(alpha = 0.14f)
                            ) {
                                Text(
                                    text = TeamStatus.displayName(team.status),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = blockColor,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (team.schoolName.isNotBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                modifier = Modifier.weight(1f, fill = false)
                            ) {
                                Icon(imageVector = Icons.Default.School, contentDescription = null, tint = ext.textMuted, modifier = Modifier.size(13.dp))
                                Text(
                                    text = team.schoolName,
                                    fontSize = 11.sp,
                                    color = ext.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (team.city.isNotBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(imageVector = Icons.Default.LocationOn, contentDescription = null, tint = ext.textMuted, modifier = Modifier.size(13.dp))
                                Text(
                                    text = team.city,
                                    fontSize = 11.sp,
                                    color = ext.textSecondary,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    if (team.competition.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(imageVector = Icons.Default.EmojiEvents, contentDescription = null, tint = accent, modifier = Modifier.size(13.dp))
                            Text(
                                text = team.competition,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = accent,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            if (team.description.isNotBlank()) {
                Text(
                    text = team.description,
                    fontSize = 12.5.sp,
                    color = ext.textSecondary,
                    lineHeight = 18.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isBlocked) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = blockColor.copy(alpha = 0.10f),
                    border = BorderStroke(1.dp, blockColor.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("blocked_team_notice_${team.id}")
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = blockColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Çalışma Alanı Kullanılamıyor",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = blockColor
                            )
                        }
                        Text(
                            text = "Şu anda bu çalışma alanı kullanılamıyor. Açıldığında haber almak istiyorsanız Haber Et tuşuna basınız.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 17.sp
                        )
                        if (team.moderationReason.isNotBlank()) {
                            Text(
                                text = "Durum Notu: ${team.moderationReason}",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = blockColor,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${team.memberCount.coerceAtLeast(0L)} Üye",
                    fontSize = 11.sp,
                    color = ext.textMuted,
                    fontWeight = FontWeight.Medium
                )

                when {
                    isBlocked -> {
                        if (isWatching) {
                            OutlinedButton(
                                onClick = onToggleWatchClick,
                                enabled = !isWatchUpdating,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.2.dp, ext.emeraldSolid),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = ext.emeraldText),
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("watch_team_button_${team.id}")
                            ) {
                                if (isWatchUpdating) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = ext.emeraldText, strokeWidth = 2.dp)
                                } else {
                                    Icon(imageVector = Icons.Default.NotificationsOff, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Haber Alınacak (İptal)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        } else {
                            Button(
                                onClick = onToggleWatchClick,
                                enabled = !isWatchUpdating,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ext.accentBlueSolid,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("watch_team_button_${team.id}")
                            ) {
                                if (isWatchUpdating) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                } else {
                                    Icon(imageVector = Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Haber Et",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    isPending -> {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = ext.amberText.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, ext.amberText.copy(alpha = 0.35f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.HourglassTop,
                                    contentDescription = null,
                                    tint = ext.amberText,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "⏳ Onay Bekliyor",
                                    color = ext.amberText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    else -> {
                        OutlinedButton(
                            onClick = onApplyClick,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.2.dp, accent),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = accent),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("apply_team_button_${team.id}")
                        ) {
                            Text(
                                text = if (isRejected) "Yeniden Başvur" else "Katılma Başvurusu Yap",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MyTeamApplicationStatusCard(team: TeamInfo) {
    val ext = MaterialTheme.blackseaColors
    val (statusBg, statusFg) = when {
        team.isApproved() -> ext.emeraldSolid.copy(alpha = 0.14f) to ext.emeraldText
        team.isSuspended() || team.isRejected() -> ext.dangerSolid.copy(alpha = 0.14f) to ext.dangerText
        else -> ext.amberText.copy(alpha = 0.14f) to ext.amberText
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, ext.cardBorder),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    TeamLogoBadge(team = team, size = 36)
                    Column {
                        Text(
                            text = team.name,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${team.schoolName} • ${team.city}",
                            fontSize = 11.sp,
                            color = ext.textSecondary
                        )
                    }
                }
                Surface(shape = RoundedCornerShape(8.dp), color = statusBg) {
                    Text(
                        text = TeamStatus.displayName(team.status),
                        color = statusFg,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            if (team.isRejected() && team.rejectionReason.isNotBlank()) {
                Text(
                    text = "Red Nedeni: ${team.rejectionReason}",
                    fontSize = 12.sp,
                    color = ext.dangerText
                )
            }
        }
    }
}

@Composable
private fun PlatformDeveloperConsoleDialog(
    selectedTabKey: String,
    onSelectTab: (String) -> Unit,
    pendingApplications: List<TeamInfo>,
    allTeams: List<TeamInfo>,
    supportTickets: List<SupportTicket>,
    openSupportCount: Int,
    currentUserProfile: UserProfile?,
    repository: BlackseaRepository,
    onDismiss: () -> Unit
) {
    val ext = MaterialTheme.blackseaColors
    val tabs = remember {
        listOf(
            "applications" to "Başvurular",
            "teams" to "Takımlar",
            "support" to "Destek"
        )
    }
    val selectedIdx = tabs.indexOfFirst { it.first == selectedTabKey }.takeIf { it >= 0 } ?: 0

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BackHandler { onDismiss() }
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .testTag("platform_developer_console_dialog"),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 2.dp
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = ext.adminRedSolid,
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.AdminPanelSettings,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Geliştirici Yönetim Merkezi",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Tüm platform takımları, üyeleri, başvurular ve destek talepleri",
                                    fontSize = 11.sp,
                                    color = ext.textMuted
                                )
                            }
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.testTag("close_platform_console_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Kapat",
                                    tint = ext.textMuted
                                )
                            }
                        }

                        TabRow(
                            selectedTabIndex = selectedIdx,
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            tabs.forEachIndexed { index, (key, title) ->
                                val count = when (key) {
                                    "applications" -> pendingApplications.size
                                    "teams" -> allTeams.size
                                    else -> openSupportCount
                                }
                                Tab(
                                    selected = selectedIdx == index,
                                    onClick = { onSelectTab(key) },
                                    text = {
                                        Text(
                                            text = if (count > 0 || key == "teams") "$title ($count)" else title,
                                            fontSize = 12.sp,
                                            fontWeight = if (selectedIdx == index) FontWeight.Bold else FontWeight.Medium,
                                            color = if (selectedIdx == index) ext.accentBlue else ext.textMuted,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                )
                            }
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    when (tabs[selectedIdx].first) {
                        "applications" -> TeamApplicationsTab(
                            pendingApplications = pendingApplications,
                            repository = repository
                        )
                        "teams" -> PlatformTeamsOverviewTab(
                            teams = allTeams,
                            repository = repository
                        )
                        else -> AdminSupportTab(
                            tickets = supportTickets,
                            repository = repository,
                            currentUserProfile = currentUserProfile,
                            teamId = PLATFORM_MAIN_TEAM_ID,
                            teamName = "BlackSea Robotics"
                        )
                    }
                }
            }
        }
    }
}

