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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import com.example.model.TeamInfo
import com.example.model.TeamMembership
import com.example.model.TeamNotification
import com.example.model.TeamStatus
import com.example.model.TeamType
import com.example.model.UserProfile
import com.example.ui.TeamNotificationsDialog
import com.example.ui.components.BlackseaLogo
import com.example.ui.components.TeamBadge
import com.example.ui.components.TeamLogoBadge
import com.example.ui.components.parseTeamColor
import com.example.ui.theme.blackseaColors
import com.example.update.AppReleaseInfo
import com.example.util.sanitizeUserErrorMessage
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TeamHubScreen(
    currentUserId: String,
    currentUserProfile: UserProfile?,
    approvedTeams: List<TeamInfo>,
    myMemberships: List<TeamMembership>,
    myTeamApplications: List<TeamInfo>,
    repository: BlackseaRepository,
    notifications: List<TeamNotification>,
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
    onOpenPlatformAdminReview: (() -> Unit)? = null,
    onSignOut: () -> Unit,
    onAccountDeleted: () -> Unit = onSignOut,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val ext = MaterialTheme.blackseaColors
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showNotificationsDialog by remember { mutableStateOf(false) }
    var showCreateTeamOverlay by rememberSaveable { mutableStateOf(false) }
    var showCreateTeamSuccessDialog by rememberSaveable { mutableStateOf(false) }
    var joinTargetTeam by remember { mutableStateOf<TeamInfo?>(null) }
    var showAccountSheet by remember { mutableStateOf(false) }
    var showSignOutConfirmation by remember { mutableStateOf(false) }

    val pendingTeamApplicationsForAdmin by remember(isPlatformAdmin) {
        if (isPlatformAdmin) repository.observePendingTeamApplications()
        else kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsState(initial = emptyList())

    val membershipByTeamId = remember(myMemberships) {
        myMemberships.associateBy { it.teamId }
    }

    val activeMemberships = remember(myMemberships) {
        myMemberships.filter { it.isActive() }
    }

    val teamMap = remember(approvedTeams) {
        val map = approvedTeams.associateBy { it.id }.toMutableMap()
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

    val myActiveTeamIds = remember(myActiveTeams) {
        myActiveTeams.map { it.first.id }.toSet()
    }

    val otherApprovedTeams = remember(approvedTeams, myActiveTeamIds, searchQuery) {
        val allApproved = if (approvedTeams.any { it.id == PLATFORM_MAIN_TEAM_ID || it.isMainTeam }) {
            approvedTeams
        } else {
            listOf(TeamInfo.mainBlackseaTeam()) + approvedTeams
        }
        val query = searchQuery.trim().lowercase()
        allApproved
            .filter { !myActiveTeamIds.contains(it.id) }
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

    BackHandler(enabled = showCreateTeamOverlay) {
        showCreateTeamOverlay = false
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
                                text = "BlackSea Robotics Platformu",
                                fontSize = 10.sp,
                                color = ext.textMuted
                            )
                        }
                    }
                },
                actions = {
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

                // BSR Platform Admin alert banner when there are pending team applications
                if (isPlatformAdmin && pendingTeamApplicationsForAdmin.isNotEmpty() && onOpenPlatformAdminReview != null) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenPlatformAdminReview() }
                                .testTag("platform_admin_pending_teams_banner"),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.5.dp, ext.adminRedSolid.copy(alpha = 0.4f)),
                            colors = CardDefaults.cardColors(containerColor = ext.adminRedSolid.copy(alpha = 0.10f))
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
                                    color = ext.adminRedSolid,
                                    modifier = Modifier.size(40.dp)
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
                                        text = "${pendingTeamApplicationsForAdmin.size} Yeni Takım Başvurusu Bekliyor",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ext.adminRedText
                                    )
                                    Text(
                                        text = "İncelemek, onaylamak veya reddetmek için dokunun",
                                        fontSize = 12.sp,
                                        color = ext.textSecondary
                                    )
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "İncele",
                                    tint = ext.adminRedText
                                )
                            }
                        }
                    }
                }

                // Section 1: "Takımlarım"
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Takımlarım (${myActiveTeams.size})",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }

                if (myActiveTeams.isEmpty()) {
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
                                    text = "Henüz aktif üyesi olduğunuz bir takım yok",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "Aşağıdaki 'Diğer Takımlar' listesinden istediğiniz takıma katılma başvurusu gönderebilir veya 'Kendi Ekibini Oluştur' butonunu kullanabilirsiniz.",
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
                                    "Aramanızla eşleşen aktif takım bulunamadı."
                                } else {
                                    "Platformdaki tüm aktif takımlara zaten üyesiniz."
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
                            onApplyClick = { joinTargetTeam = team }
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

    // Account & Sign Out Sheet
    if (showAccountSheet) {
        AlertDialog(
            onDismissRequest = { showAccountSheet = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = ext.accentBlueContainer,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = currentUserProfile?.getFullName()?.take(1)?.uppercase() ?: "K",
                                fontWeight = FontWeight.Black,
                                color = ext.accentBlue
                            )
                        }
                    }
                    Column {
                        Text(
                            text = currentUserProfile?.getFullName()?.ifBlank { "Kullanıcı" } ?: "Kullanıcı",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = currentUserProfile?.email.orEmpty(),
                            fontSize = 12.sp,
                            color = ext.textMuted
                        )
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Aktif üyelik sayınız: ${myActiveTeams.size} takım",
                        fontSize = 13.sp,
                        color = ext.textSecondary
                    )
                    OutlinedButton(
                        onClick = {
                            showAccountSheet = false
                            showCreateTeamOverlay = true
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.AddCircleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Kendi Ekibini Oluştur", fontWeight = FontWeight.SemiBold)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showAccountSheet = false
                        showSignOutConfirmation = true
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ext.dangerSolid,
                        contentColor = Color.White
                    )
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Oturumu Kapat", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAccountSheet = false }) {
                    Text("Kapat")
                }
            }
        )
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

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEnterClick() }
            .testTag("my_team_card_${team.id}"),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.2.dp, accent.copy(alpha = 0.45f)),
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
                if (membership.isManagerInTeam()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = ext.adminRedSolid.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = "Takım Yöneticisi",
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

            Button(
                onClick = onEnterClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accent,
                    contentColor = Color.White
                )
            ) {
                Text("Çalışma Alanına Gir", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun DiscoverableTeamCard(
    team: TeamInfo,
    membership: TeamMembership?,
    onApplyClick: () -> Unit
) {
    val ext = MaterialTheme.blackseaColors
    val accent = parseTeamColor(team.accentColorHex, ext.accentBlue)
    val isPending = membership?.isPending() == true
    val isRejected = membership?.isRejected() == true || membership?.isRevoked() == true

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("other_team_card_${team.id}"),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, ext.cardBorder),
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
                                .heightIn(min = 40.dp)
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
        team.isRejected() -> ext.dangerSolid.copy(alpha = 0.14f) to ext.dangerText
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
