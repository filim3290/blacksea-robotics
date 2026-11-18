package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Home
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.example.model.TeamNotification
import com.example.notifications.TeamNotificationDispatcher
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.flowOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.BlackseaRepository
import com.example.model.PLATFORM_FOUNDER_EMAIL
import com.example.model.PLATFORM_MAIN_TEAM_ID
import com.example.model.TeamInfo
import com.example.model.TeamMembership
import com.example.model.UserProfile
import com.example.model.UserStatus
import com.example.model.withTeamContext
import com.example.ui.components.BlackseaLogo
import com.example.ui.components.TeamLogoBadge
import com.example.ui.components.TeamModerationWarningScreen
import com.example.ui.components.parseTeamColor
import com.example.ui.screens.AdminScreen
import com.example.ui.screens.FilesScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.IdeasScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.RoomsScreen
import com.example.ui.screens.TeamHubScreen
import com.example.ui.theme.blackseaColors
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.style.TextOverflow
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

enum class NavItem(val route: String, val title: String) {
    HOME("home", "Ana Sayfa"),
    ROOMS("rooms", "Odalar"),
    IDEAS("ideas", "Fikirler"),
    FILES("files", "Dosyalar"),
    ADMIN("admin", "Yönetici"),
    PROFILE("profile", "Profil")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    currentUserId: String,
    repository: BlackseaRepository,
    updateManager: com.example.update.AppUpdateManager? = null,
    availableUpdate: com.example.update.AppReleaseInfo? = null,
    notificationNavEvent: com.example.NotificationNavEvent? = null,
    onConsumeNotificationNav: () -> Unit = {},
    onShowUpdateDialog: (com.example.update.AppReleaseInfo) -> Unit = {},
    onSignOut: () -> Unit,
    onAccountDeleted: () -> Unit = onSignOut
) {
    val context = LocalContext.current
    val ext = MaterialTheme.blackseaColors
    val scope = rememberCoroutineScope()

    val currentUserProfile by repository.observeUserProfile(currentUserId).collectAsState(initial = null)
    val approvedTeams by repository.observeApprovedTeams().collectAsState(initial = emptyList())
    val myMemberships by repository.observeMyMemberships(currentUserId).collectAsState(initial = emptyList())
    val myTeamApplications by repository.observeMyTeamApplications(currentUserId).collectAsState(initial = emptyList())

    val isPlatformAdmin = remember(currentUserProfile) {
        currentUserProfile?.isPlatformManagerAccount() == true ||
            Firebase.auth.currentUser?.email?.trim().equals(PLATFORM_FOUNDER_EMAIL, ignoreCase = true)
    }

    LaunchedEffect(currentUserId, currentUserProfile?.email, isPlatformAdmin) {
        val p = currentUserProfile ?: return@LaunchedEffect
        repository.migrateLegacyUserToMainTeamMembershipIfNeeded(p)
        if (isPlatformAdmin) {
            repository.ensureMainTeamExists()
        }
    }

    val membershipsByTeam = remember(myMemberships, currentUserProfile, isPlatformAdmin) {
        val map = myMemberships.associateBy { it.teamId }.toMutableMap()
        val p = currentUserProfile
        if (p != null && isPlatformAdmin && !map.containsKey(PLATFORM_MAIN_TEAM_ID)) {
            val uid = p.userId.ifBlank { p.id }.ifBlank { currentUserId }
            map[PLATFORM_MAIN_TEAM_ID] = TeamMembership(
                id = "${PLATFORM_MAIN_TEAM_ID}_$uid",
                teamId = PLATFORM_MAIN_TEAM_ID,
                userId = uid,
                userName = p.getFullName(),
                userEmail = p.email,
                userPhotoUrl = p.photoUrl,
                subTeam = p.team,
                role = p.role,
                roles = p.roles,
                status = UserStatus.ACTIVE.value,
                isTeamAdmin = true
            )
        }
        map
    }

    val activeTeamIds = remember(membershipsByTeam, isPlatformAdmin) {
        val set = membershipsByTeam.values.filter { it.isActive() }.map { it.teamId }.toMutableSet()
        if (isPlatformAdmin) set.add(PLATFORM_MAIN_TEAM_ID)
        set.toSet()
    }

    val myKnownTeams by remember(activeTeamIds) {
        if (activeTeamIds.isEmpty()) flowOf(emptyList())
        else repository.observeTeamsByIds(activeTeamIds)
    }.collectAsState(initial = emptyList())

    val knownTeamsById = remember(approvedTeams, myKnownTeams) {
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

    var activeTeamId by rememberSaveable(currentUserId) { mutableStateOf<String?>(null) }
    var currentTab by remember { mutableStateOf(NavItem.HOME) }
    var selectedRoomIdForNavigation by remember { mutableStateOf("general") }
    var adminInitialSection by remember { mutableStateOf("members") }

    // Ensure removed/revoked membership safely exits the closed workspace back to Team Hub
    LaunchedEffect(activeTeamId, activeTeamIds, myMemberships, isPlatformAdmin) {
        val selected = activeTeamId ?: return@LaunchedEffect
        if (selected !in activeTeamIds && !(selected == PLATFORM_MAIN_TEAM_ID && isPlatformAdmin)) {
            if (myMemberships.isNotEmpty() || !isPlatformAdmin) {
                activeTeamId = null
                currentTab = NavItem.HOME
            }
        }
    }

    // Ensure deleted non-main teams also exit cleanly back to Team Hub while keeping suspended/under_review teams visible
    LaunchedEffect(activeTeamId, approvedTeams, myKnownTeams, knownTeamsById) {
        val selected = activeTeamId ?: return@LaunchedEffect
        if (selected != PLATFORM_MAIN_TEAM_ID &&
            (approvedTeams.isNotEmpty() || myKnownTeams.isNotEmpty()) &&
            !knownTeamsById.containsKey(selected)
        ) {
            activeTeamId = null
            currentTab = NavItem.HOME
        }
    }

    fun navigateForNotification(type: String, roomId: String, targetTeamId: String) {
        val lower = type.lowercase()
        when (lower) {
            "team_application_pending", "team_appeal" -> {
                if (isPlatformAdmin) {
                    activeTeamId = PLATFORM_MAIN_TEAM_ID
                    adminInitialSection = if (lower == "team_appeal") "platform_teams" else "team_applications"
                    currentTab = NavItem.ADMIN
                } else {
                    activeTeamId = null
                }
                return
            }
            "team_application_rejected" -> {
                activeTeamId = null
                return
            }
            "team_application_approved", "team_suspended", "team_under_review", "team_resumed" -> {
                if (targetTeamId.isNotBlank() && targetTeamId in activeTeamIds) {
                    activeTeamId = targetTeamId
                    currentTab = NavItem.HOME
                } else {
                    activeTeamId = null
                }
                return
            }
            "support_ticket" -> {
                if (isPlatformAdmin) {
                    activeTeamId = PLATFORM_MAIN_TEAM_ID
                    adminInitialSection = "support"
                    currentTab = NavItem.ADMIN
                } else {
                    val fallbackTeam = activeTeamId ?: activeTeamIds.firstOrNull()
                    if (fallbackTeam != null) {
                        activeTeamId = fallbackTeam
                        currentTab = NavItem.PROFILE
                    } else {
                        activeTeamId = null
                    }
                }
                return
            }
        }

        val effectiveTeam = targetTeamId.ifBlank { activeTeamId ?: PLATFORM_MAIN_TEAM_ID }
        if (effectiveTeam !in activeTeamIds) {
            activeTeamId = null
            return
        }

        activeTeamId = effectiveTeam
        val targetMembership = membershipsByTeam[effectiveTeam]
        val isManagerInTarget = targetMembership?.isManagerInTeam() == true ||
            (effectiveTeam == PLATFORM_MAIN_TEAM_ID && isPlatformAdmin)

        when (lower) {
            "message" -> {
                selectedRoomIdForNavigation = roomId.ifBlank { "general" }
                currentTab = NavItem.ROOMS
            }
            "file" -> currentTab = NavItem.FILES
            "idea", "comment" -> currentTab = NavItem.IDEAS
            "member_pending" -> {
                if (isManagerInTarget) {
                    adminInitialSection = "approvals"
                    currentTab = NavItem.ADMIN
                } else {
                    currentTab = NavItem.HOME
                }
            }
            "support_reply" -> currentTab = NavItem.PROFILE
            else -> currentTab = NavItem.HOME
        }
    }

    LaunchedEffect(notificationNavEvent?.nonce, currentUserProfile, activeTeamIds) {
        val event = notificationNavEvent ?: return@LaunchedEffect
        if (currentUserProfile == null) return@LaunchedEffect
        if (!event.openUpdateDialog && event.type.isNotBlank()) {
            navigateForNotification(event.type, event.roomId, event.teamId)
            onConsumeNotificationNav()
        }
    }

    val effectiveJoinMs = remember(currentUserId, currentUserProfile?.createdAt) {
        val profileTs = currentUserProfile?.createdAt?.toDate()?.time ?: 0L
        if (profileTs > 0L) {
            TeamNotificationDispatcher.recordUserJoinTimestampIfMissing(context, currentUserId, profileTs)
            profileTs
        } else {
            TeamNotificationDispatcher.getUserJoinTimestampMs(context, currentUserId)
        }
    }

    val notifications by remember(
        currentUserId,
        membershipsByTeam,
        isPlatformAdmin,
        effectiveJoinMs,
        currentUserProfile != null
    ) {
        if (currentUserProfile == null) {
            flowOf(emptyList())
        } else {
            repository.observeNotificationsForUser(
                userId = currentUserId,
                membershipsByTeam = membershipsByTeam,
                isPlatformAdmin = isPlatformAdmin,
                userCreatedAtMs = effectiveJoinMs
            )
        }
    }.collectAsState(initial = emptyList())

    val visibleNotifications = remember(notifications, updateManager) {
        notifications.filterNot { notif ->
            updateManager?.isNotificationUpdateObsolete(
                type = notif.type,
                targetId = notif.targetId,
                message = notif.message
            ) == true
        }
    }
    var locallyReadIds by remember(currentUserId) { mutableStateOf(setOf<String>()) }
    var readUpdateTag by remember(currentUserId) { mutableStateOf<String?>(null) }
    val hasUnreadUpdate = availableUpdate != null && readUpdateTag != availableUpdate.tagName
    val unreadNotificationsCount = remember(visibleNotifications, locallyReadIds, hasUnreadUpdate) {
        visibleNotifications.count { notif ->
            !notif.isReadBy(currentUserId) && !locallyReadIds.contains(notif.id)
        } + if (hasUnreadUpdate) 1 else 0
    }
    var showNotificationsDialog by remember { mutableStateOf(false) }

    val activeTeamIdNonNull = activeTeamId
    if (activeTeamIdNonNull == null) {
        TeamHubScreen(
            currentUserId = currentUserId,
            currentUserProfile = currentUserProfile,
            approvedTeams = approvedTeams,
            myKnownTeams = myKnownTeams,
            myMemberships = myMemberships,
            myTeamApplications = myTeamApplications,
            repository = repository,
            notifications = visibleNotifications,
            availableUpdate = availableUpdate,
            locallyReadIds = locallyReadIds,
            unreadNotificationsCount = unreadNotificationsCount,
            hasUnreadUpdate = hasUnreadUpdate,
            isPlatformAdmin = isPlatformAdmin,
            onMarkAsRead = { notifId ->
                locallyReadIds = locallyReadIds + notifId
                val target = visibleNotifications.firstOrNull { it.id == notifId }
                val isShared = target != null && target.recipientUserId != currentUserId
                scope.launch {
                    repository.markNotificationAsRead(notifId, currentUserId, isShared = isShared)
                }
            },
            onMarkAllAsRead = {
                if (availableUpdate != null) {
                    readUpdateTag = availableUpdate.tagName
                }
                val allIds = visibleNotifications.map { it.id }.filter { it.isNotBlank() }.toSet()
                val personalIds = visibleNotifications
                    .filter { it.recipientUserId == currentUserId && it.id.isNotBlank() }
                    .map { it.id }
                    .toSet()
                locallyReadIds = locallyReadIds + allIds
                scope.launch {
                    repository.markAllNotificationsAsRead(
                        userId = currentUserId,
                        notificationIds = allIds.toList(),
                        personalNotificationIds = personalIds
                    )
                }
            },
            onNotificationClick = { notif ->
                navigateForNotification(notif.type, notif.targetRoomId, notif.effectiveTeamId())
            },
            onShowUpdateDialog = onShowUpdateDialog,
            onReadUpdateClick = { tag -> readUpdateTag = tag },
            onEnterTeam = { selectedTeamId ->
                activeTeamId = selectedTeamId
                currentTab = NavItem.HOME
                selectedRoomIdForNavigation = "general"
                scope.launch {
                    repository.setActiveTeamId(currentUserId, selectedTeamId)
                    val targetTeam = knownTeamsById[selectedTeamId]
                    if (targetTeam == null || !targetTeam.isWorkspaceBlocked()) {
                        repository.ensureDefaultRoomsExist(selectedTeamId)
                    }
                }
            },
            onOpenPlatformAdminReview = if (isPlatformAdmin) {
                {
                    activeTeamId = PLATFORM_MAIN_TEAM_ID
                    adminInitialSection = "team_applications"
                    currentTab = NavItem.ADMIN
                }
            } else null,
            onSignOut = onSignOut,
            onAccountDeleted = onAccountDeleted
        )
        return
    }

    val activeTeam = remember(knownTeamsById, activeTeamIdNonNull) {
        knownTeamsById[activeTeamIdNonNull]
            ?: if (activeTeamIdNonNull == PLATFORM_MAIN_TEAM_ID) TeamInfo.mainBlackseaTeam()
            else TeamInfo(id = activeTeamIdNonNull, name = "Takım Alanı", shortCode = "TKM")
    }
    val activeMembership = membershipsByTeam[activeTeamIdNonNull]
    val effectiveProfile = remember(currentUserProfile, activeTeamIdNonNull, activeMembership) {
        currentUserProfile?.withTeamContext(activeTeamIdNonNull, activeMembership)
    }
    val isAdmin = remember(activeMembership, isPlatformAdmin, activeTeamIdNonNull) {
        activeMembership?.isManagerInTeam() == true ||
            (activeTeamIdNonNull == PLATFORM_MAIN_TEAM_ID && isPlatformAdmin)
    }

    if (activeTeam.isWorkspaceBlocked() && !isPlatformAdmin) {
        TeamModerationWarningScreen(
            team = activeTeam,
            repository = repository,
            currentUserProfile = effectiveProfile ?: currentUserProfile,
            onBackToHub = {
                activeTeamId = null
                currentTab = NavItem.HOME
            }
        )
        return
    }

    val teamAccent = parseTeamColor(activeTeam.accentColorHex, ext.accentBlue)

    // BackHandler: When on sub-tabs, return to HOME; when on HOME, return to TeamHubScreen
    BackHandler {
        if (currentTab != NavItem.HOME) {
            currentTab = NavItem.HOME
        } else {
            activeTeamId = null
        }
    }

    key(currentUserId, activeTeamIdNonNull) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isWideScreen = maxWidth >= 600.dp

            if (isWideScreen) {
                // Adaptive Tablet / Desktop Layout with Navigation Rail
                Row(modifier = Modifier.fillMaxSize()) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        header = {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clickable {
                                        activeTeamId = null
                                        currentTab = NavItem.HOME
                                    }
                                    .padding(vertical = 12.dp, horizontal = 6.dp)
                            ) {
                                TeamLogoBadge(team = activeTeam, size = 40)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = activeTeam.shortCode.ifBlank { activeTeam.name.take(4).uppercase() },
                                    color = teamAccent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    maxLines = 1
                                )
                                Text(
                                    text = "Takımlar",
                                    color = ext.textMuted,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        },
                        modifier = Modifier.fillMaxHeight().testTag("adaptive_navigation_rail")
                    ) {
                        val railColors = NavigationRailItemDefaults.colors(
                            selectedIconColor = ext.accentBlue,
                            selectedTextColor = ext.accentBlue,
                            unselectedIconColor = ext.textMuted,
                            unselectedTextColor = ext.textMuted,
                            indicatorColor = ext.accentBlueContainer
                        )

                        NavigationRailItem(
                            selected = currentTab == NavItem.HOME,
                            onClick = { currentTab = NavItem.HOME },
                            icon = { Icon(Icons.Default.Home, contentDescription = "Ana Sayfa") },
                            label = { Text("Ana Sayfa", fontSize = 11.sp) },
                            colors = railColors,
                            modifier = Modifier.testTag("nav_rail_home")
                        )

                        NavigationRailItem(
                            selected = currentTab == NavItem.ROOMS,
                            onClick = { currentTab = NavItem.ROOMS },
                            icon = { Icon(Icons.Default.ChatBubble, contentDescription = "Odalar") },
                            label = { Text("Odalar", fontSize = 11.sp) },
                            colors = railColors,
                            modifier = Modifier.testTag("nav_rail_rooms")
                        )

                        NavigationRailItem(
                            selected = currentTab == NavItem.IDEAS,
                            onClick = { currentTab = NavItem.IDEAS },
                            icon = { Icon(Icons.Default.Lightbulb, contentDescription = "Fikirler") },
                            label = { Text("Fikirler", fontSize = 11.sp) },
                            colors = railColors,
                            modifier = Modifier.testTag("nav_rail_ideas")
                        )

                        NavigationRailItem(
                            selected = currentTab == NavItem.FILES,
                            onClick = { currentTab = NavItem.FILES },
                            icon = { Icon(Icons.Default.Folder, contentDescription = "Dosyalar") },
                            label = { Text("Dosyalar", fontSize = 11.sp) },
                            colors = railColors,
                            modifier = Modifier.testTag("nav_rail_files")
                        )

                        if (isAdmin) {
                            val leaderLabel = if (isPlatformAdmin) "Yönetici" else "Yetkili"
                            NavigationRailItem(
                                selected = currentTab == NavItem.ADMIN,
                                onClick = {
                                    adminInitialSection = "members"
                                    currentTab = NavItem.ADMIN
                                },
                                icon = { Icon(Icons.Default.Security, contentDescription = leaderLabel) },
                                label = { Text(leaderLabel, fontSize = 11.sp) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = ext.adminRedText,
                                    selectedTextColor = ext.adminRedText,
                                    unselectedIconColor = ext.textMuted,
                                    unselectedTextColor = ext.textMuted,
                                    indicatorColor = ext.adminRedSolid.copy(alpha = 0.14f)
                                ),
                                modifier = Modifier.testTag("nav_rail_admin")
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        NavigationRailItem(
                            selected = currentTab == NavItem.PROFILE,
                            onClick = { currentTab = NavItem.PROFILE },
                            icon = { Icon(Icons.Default.Person, contentDescription = "Profil") },
                            label = { Text("Profil", fontSize = 11.sp) },
                            colors = railColors,
                            modifier = Modifier.testTag("nav_rail_profile")
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .statusBarsPadding()
                            .navigationBarsPadding()
                            .consumeWindowInsets(WindowInsets.navigationBars)
                    ) {
                        ScreenContent(
                            currentTab = currentTab,
                            repository = repository,
                            activeTeam = activeTeam,
                            activeMembership = activeMembership,
                            currentUserProfile = effectiveProfile,
                            isPlatformAdmin = isPlatformAdmin,
                            updateManager = updateManager,
                            adminInitialSection = adminInitialSection,
                            onShowUpdateDialog = onShowUpdateDialog,
                            selectedRoomIdForNavigation = selectedRoomIdForNavigation,
                            onNavigateToRoom = { roomId ->
                                selectedRoomIdForNavigation = roomId
                                currentTab = NavItem.ROOMS
                            },
                            onNavigateToIdeas = { currentTab = NavItem.IDEAS },
                            onNavigateToFiles = { currentTab = NavItem.FILES },
                            onNavigateToAdmin = {
                                adminInitialSection = "members"
                                currentTab = NavItem.ADMIN
                            },
                            onSwitchTeam = {
                                activeTeamId = null
                                currentTab = NavItem.HOME
                            },
                            onTeamDeleted = {
                                activeTeamId = null
                                currentTab = NavItem.HOME
                            },
                            onSignOut = onSignOut,
                            onAccountDeleted = onAccountDeleted
                        )
                    }
                }
            } else {
                // Standard Mobile Layout with TopAppBar and NavigationBar
                Scaffold(
                    topBar = {
                        CenterAlignedTopAppBar(
                            navigationIcon = {
                                IconButton(
                                    onClick = {
                                        activeTeamId = null
                                        currentTab = NavItem.HOME
                                    },
                                    modifier = Modifier.testTag("top_bar_switch_team_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SwapHoriz,
                                        contentDescription = "Takımlar Merkezi",
                                        tint = teamAccent
                                    )
                                }
                            },
                            title = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clickable {
                                            activeTeamId = null
                                            currentTab = NavItem.HOME
                                        }
                                        .testTag("app_top_bar_title")
                                ) {
                                    TeamLogoBadge(team = activeTeam, size = 30)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = activeTeam.name.uppercase(),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 0.4.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.widthIn(max = 165.dp)
                                    )
                                    if (activeTeam.shortCode.isNotBlank()) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = teamAccent.copy(alpha = 0.16f)
                                        ) {
                                            Text(
                                                text = activeTeam.shortCode,
                                                color = teamAccent,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                            },
                            actions = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(end = 6.dp)
                                ) {
                                    IconButton(
                                        onClick = { showNotificationsDialog = true },
                                        modifier = Modifier.testTag("top_bar_notifications_button")
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
                                                contentDescription = "Takım Bildirimleri",
                                                tint = if (unreadNotificationsCount > 0) ext.accentBlue else ext.textMuted
                                            )
                                        }
                                    }

                                    if (isAdmin) {
                                        val leaderLabel = if (isPlatformAdmin) "Yönetici" else "Yetkili"
                                        Surface(
                                            shape = RoundedCornerShape(20.dp),
                                            color = if (currentTab == NavItem.ADMIN) ext.adminRedSolid else ext.adminRedSolid.copy(alpha = 0.14f),
                                            border = if (!ext.isDark && currentTab != NavItem.ADMIN) {
                                                BorderStroke(1.dp, ext.adminRedSolid.copy(alpha = 0.25f))
                                            } else null,
                                            modifier = Modifier
                                                .clickable {
                                                    adminInitialSection = "members"
                                                    currentTab = if (currentTab == NavItem.ADMIN) NavItem.HOME else NavItem.ADMIN
                                                }
                                                .testTag("top_bar_admin_button")
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Security,
                                                    contentDescription = if (isPlatformAdmin) "Yönetici Paneli" else "Yetkili Öğretmen Paneli",
                                                    tint = if (currentTab == NavItem.ADMIN) Color.White else ext.adminRedText,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                                Text(
                                                    text = leaderLabel,
                                                    color = if (currentTab == NavItem.ADMIN) Color.White else ext.adminRedText,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            },
                            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                    },
                    bottomBar = {
                        val navBarColors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ext.accentBlue,
                            selectedTextColor = ext.accentBlue,
                            unselectedIconColor = ext.textMuted,
                            unselectedTextColor = ext.textMuted,
                            indicatorColor = ext.accentBlueContainer
                        )
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = if (ext.isDark) 8.dp else 2.dp
                        ) {
                            NavigationBarItem(
                                selected = currentTab == NavItem.HOME,
                                onClick = { currentTab = NavItem.HOME },
                                icon = { Icon(Icons.Default.Home, contentDescription = "Ana Sayfa") },
                                label = { Text("Ana Sayfa", fontSize = 11.sp) },
                                colors = navBarColors,
                                modifier = Modifier.testTag("nav_home")
                            )

                            NavigationBarItem(
                                selected = currentTab == NavItem.ROOMS,
                                onClick = { currentTab = NavItem.ROOMS },
                                icon = { Icon(Icons.Default.ChatBubble, contentDescription = "Odalar") },
                                label = { Text("Odalar", fontSize = 11.sp) },
                                colors = navBarColors,
                                modifier = Modifier.testTag("nav_rooms")
                            )

                            NavigationBarItem(
                                selected = currentTab == NavItem.IDEAS,
                                onClick = { currentTab = NavItem.IDEAS },
                                icon = { Icon(Icons.Default.Lightbulb, contentDescription = "Fikirler") },
                                label = { Text("Fikirler", fontSize = 11.sp) },
                                colors = navBarColors,
                                modifier = Modifier.testTag("nav_ideas")
                            )

                            NavigationBarItem(
                                selected = currentTab == NavItem.FILES,
                                onClick = { currentTab = NavItem.FILES },
                                icon = { Icon(Icons.Default.Folder, contentDescription = "Dosyalar") },
                                label = { Text("Dosyalar", fontSize = 11.sp) },
                                colors = navBarColors,
                                modifier = Modifier.testTag("nav_files")
                            )

                            NavigationBarItem(
                                selected = currentTab == NavItem.PROFILE,
                                onClick = { currentTab = NavItem.PROFILE },
                                icon = { Icon(Icons.Default.Person, contentDescription = "Profil") },
                                label = { Text("Profil", fontSize = 11.sp) },
                                colors = navBarColors,
                                modifier = Modifier.testTag("nav_profile")
                            )
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .consumeWindowInsets(innerPadding)
                    ) {
                        ScreenContent(
                            currentTab = currentTab,
                            repository = repository,
                            activeTeam = activeTeam,
                            activeMembership = activeMembership,
                            currentUserProfile = effectiveProfile,
                            isPlatformAdmin = isPlatformAdmin,
                            updateManager = updateManager,
                            adminInitialSection = adminInitialSection,
                            onShowUpdateDialog = onShowUpdateDialog,
                            selectedRoomIdForNavigation = selectedRoomIdForNavigation,
                            onNavigateToRoom = { roomId ->
                                selectedRoomIdForNavigation = roomId
                                currentTab = NavItem.ROOMS
                            },
                            onNavigateToIdeas = { currentTab = NavItem.IDEAS },
                            onNavigateToFiles = { currentTab = NavItem.FILES },
                            onNavigateToAdmin = {
                                adminInitialSection = "members"
                                currentTab = NavItem.ADMIN
                            },
                            onSwitchTeam = {
                                activeTeamId = null
                                currentTab = NavItem.HOME
                            },
                            onTeamDeleted = {
                                activeTeamId = null
                                currentTab = NavItem.HOME
                            },
                            onSignOut = onSignOut,
                            onAccountDeleted = onAccountDeleted
                        )
                    }
                }
            }

            if (showNotificationsDialog) {
                TeamNotificationsDialog(
                    notifications = visibleNotifications,
                    currentUserId = currentUserId,
                    locallyReadIds = locallyReadIds,
                    availableUpdate = availableUpdate,
                    isUpdateRead = !hasUnreadUpdate,
                    onUpdateClick = { release ->
                        readUpdateTag = release.tagName
                        showNotificationsDialog = false
                        onShowUpdateDialog(release)
                    },
                    onDismiss = { showNotificationsDialog = false },
                    onMarkAsRead = { notifId ->
                        locallyReadIds = locallyReadIds + notifId
                        val target = visibleNotifications.firstOrNull { it.id == notifId }
                        val isShared = target != null && target.recipientUserId != currentUserId
                        scope.launch {
                            repository.markNotificationAsRead(notifId, currentUserId, isShared = isShared)
                        }
                    },
                    onNotificationClick = { notif ->
                        showNotificationsDialog = false
                        navigateForNotification(notif.type, notif.targetRoomId, notif.effectiveTeamId())
                    },
                    onMarkAllAsRead = {
                        if (availableUpdate != null) {
                            readUpdateTag = availableUpdate.tagName
                        }
                        val allIds = visibleNotifications.map { it.id }.filter { it.isNotBlank() }.toSet()
                        val personalIds = visibleNotifications
                            .filter { it.recipientUserId == currentUserId && it.id.isNotBlank() }
                            .map { it.id }
                            .toSet()
                        locallyReadIds = locallyReadIds + allIds
                        scope.launch {
                            repository.markAllNotificationsAsRead(
                                userId = currentUserId,
                                notificationIds = allIds.toList(),
                                personalNotificationIds = personalIds
                            )
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ScreenContent(
    currentTab: NavItem,
    repository: BlackseaRepository,
    activeTeam: TeamInfo,
    activeMembership: TeamMembership?,
    currentUserProfile: UserProfile?,
    isPlatformAdmin: Boolean,
    updateManager: com.example.update.AppUpdateManager? = null,
    adminInitialSection: String = "members",
    onShowUpdateDialog: (com.example.update.AppReleaseInfo) -> Unit = {},
    selectedRoomIdForNavigation: String,
    onNavigateToRoom: (String) -> Unit,
    onNavigateToIdeas: () -> Unit,
    onNavigateToFiles: () -> Unit,
    onNavigateToAdmin: () -> Unit = {},
    onSwitchTeam: () -> Unit = {},
    onTeamDeleted: () -> Unit = onSwitchTeam,
    onSignOut: () -> Unit,
    onAccountDeleted: () -> Unit = onSignOut
) {
    AnimatedContent(
        targetState = currentTab,
        transitionSpec = {
            fadeIn(animationSpec = tween(140)) togetherWith fadeOut(animationSpec = tween(140))
        },
        label = "screen_transition"
    ) { targetTab ->
        when (targetTab) {
            NavItem.HOME -> HomeScreen(
                repository = repository,
                currentUserProfile = currentUserProfile,
                activeTeam = activeTeam,
                onNavigateToRoom = onNavigateToRoom,
                onNavigateToIdeas = onNavigateToIdeas,
                onNavigateToFiles = onNavigateToFiles,
                onNavigateToAdmin = onNavigateToAdmin,
                onSwitchTeam = onSwitchTeam
            )
            NavItem.ROOMS -> RoomsScreen(
                repository = repository,
                currentUserProfile = currentUserProfile,
                activeTeam = activeTeam,
                initialRoomId = selectedRoomIdForNavigation
            )
            NavItem.IDEAS -> IdeasScreen(
                repository = repository,
                currentUserProfile = currentUserProfile,
                activeTeam = activeTeam
            )
            NavItem.FILES -> FilesScreen(
                repository = repository,
                currentUserProfile = currentUserProfile,
                activeTeam = activeTeam
            )
            NavItem.ADMIN -> AdminScreen(
                repository = repository,
                currentUserProfile = currentUserProfile,
                activeTeam = activeTeam,
                isPlatformAdmin = isPlatformAdmin,
                updateManager = updateManager,
                initialSectionKey = adminInitialSection,
                onShowUpdateDialog = onShowUpdateDialog,
                onTeamDeleted = onTeamDeleted
            )
            NavItem.PROFILE -> ProfileScreen(
                currentUserProfile = currentUserProfile,
                activeTeam = activeTeam,
                activeMembership = activeMembership,
                repository = repository,
                updateManager = updateManager,
                onShowUpdateDialog = onShowUpdateDialog,
                onNavigateToAdmin = onNavigateToAdmin,
                onSwitchTeam = onSwitchTeam,
                onSignOut = onSignOut,
                onAccountDeleted = onAccountDeleted
            )
        }
    }
}

@Composable
fun TeamNotificationsDialog(
    notifications: List<TeamNotification>,
    currentUserId: String,
    locallyReadIds: Set<String>,
    availableUpdate: com.example.update.AppReleaseInfo? = null,
    isUpdateRead: Boolean = false,
    onUpdateClick: (com.example.update.AppReleaseInfo) -> Unit = {},
    onDismiss: () -> Unit,
    onMarkAsRead: (String) -> Unit,
    onNotificationClick: (TeamNotification) -> Unit = {},
    onMarkAllAsRead: () -> Unit
) {
    val context = LocalContext.current
    val ext = MaterialTheme.blackseaColors
    val areSystemNotificationsEnabled = remember {
        NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    val unreadCount = remember(notifications, locallyReadIds, availableUpdate, isUpdateRead) {
        notifications.count { notif ->
            !notif.isReadBy(currentUserId) && !locallyReadIds.contains(notif.id)
        } + if (availableUpdate != null && !isUpdateRead) 1 else 0
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 520.dp)
                .height(540.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, ext.cardBorder),
            tonalElevation = 4.dp,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Fixed Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = ext.accentBlueContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Notifications,
                                    contentDescription = null,
                                    tint = ext.accentBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Bildirimler",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (unreadCount > 0) "$unreadCount okunmamış" else "Hepsi okundu",
                                fontSize = 11.sp,
                                color = if (unreadCount > 0) ext.accentBlue else ext.textMuted,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    if (unreadCount > 0) {
                        Button(
                            onClick = onMarkAllAsRead,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ext.accentBlueContainer,
                                contentColor = ext.accentBlue
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(imageVector = Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Tümünü Oku", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // System Notification Warning Banner (If system blocked)
                if (!areSystemNotificationsEnabled) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = ext.dangerContainer,
                        border = BorderStroke(1.dp, ext.dangerText.copy(alpha = 0.25f))
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = ext.dangerText,
                                modifier = Modifier.size(20.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Sistem Bildirimleri Kapalı",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ext.dangerText
                                )
                                Text(
                                    text = "Cihaz ayarlarından bildirimlere izin verin.",
                                    fontSize = 10.sp,
                                    color = ext.textSecondary
                                )
                            }
                            TextButton(
                                onClick = {
                                    try {
                                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = Uri.fromParts("package", context.packageName, null)
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                        context.startActivity(intent)
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("Ayarlarda Aç", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ext.accentBlue)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Scrollable List area locked with weight(1f) to prevent any jittering
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (notifications.isEmpty() && availableUpdate == null) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.DoneAll,
                                    contentDescription = null,
                                    tint = ext.textMuted,
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Henüz yeni bir bildirim yok.",
                                    fontSize = 13.sp,
                                    color = ext.textSecondary
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (availableUpdate != null) {
                                item(key = "app_update_${availableUpdate.tagName}") {
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onUpdateClick(availableUpdate) }
                                            .testTag("notification_update_card"),
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isUpdateRead) ext.cardBorder else ext.emeraldText.copy(alpha = 0.5f)
                                        ),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isUpdateRead) {
                                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (ext.isDark) 0.5f else 0.65f)
                                            } else {
                                                ext.emeraldSolid.copy(alpha = if (ext.isDark) 0.2f else 0.12f)
                                            }
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.Top,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Surface(
                                                shape = CircleShape,
                                                color = ext.emeraldText.copy(alpha = 0.16f),
                                                modifier = Modifier.size(34.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = Icons.Default.SystemUpdate,
                                                        contentDescription = "Yeni Güncelleme",
                                                        tint = ext.emeraldText,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "Yeni Güncelleme Mevcut: v${availableUpdate.versionName}",
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    if (!isUpdateRead) {
                                                        Surface(
                                                            shape = CircleShape,
                                                            color = ext.emeraldText,
                                                            modifier = Modifier.size(8.dp)
                                                        ) {}
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = "Sürümü incelemek ve güncellemek için dokunun.",
                                                    fontSize = 12.sp,
                                                    color = ext.textSecondary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            itemsIndexed(
                                items = notifications,
                                key = { index, notif -> "${notif.id.ifBlank { "notif" }}_$index" }
                            ) { _, notif ->
                                val isRead = notif.isReadBy(currentUserId) || locallyReadIds.contains(notif.id)
                                val (notifIcon, notifTint) = when (notif.type.lowercase()) {
                                    "broadcast", "announcement", "team_resumed" -> Pair(Icons.Default.Campaign, ext.amberText)
                                    "update" -> Pair(Icons.Default.SystemUpdate, ext.emeraldText)
                                    "idea", "comment" -> Pair(Icons.Default.Lightbulb, ext.amberText)
                                    "file" -> Pair(Icons.Default.Folder, if (ext.isDark) Color(0xFFA78BFA) else Color(0xFF6D28D9))
                                    "new_member", "member_pending", "member_approved" -> Pair(Icons.Default.GroupAdd, ext.emeraldText)
                                    "support_ticket", "support_reply" -> Pair(Icons.Default.SupportAgent, ext.accentBlue)
                                    "team_suspended", "access_revoked" -> Pair(Icons.Default.Block, ext.dangerText)
                                    "team_under_review" -> Pair(Icons.Default.ReportProblem, ext.amberText)
                                    "team_appeal" -> Pair(Icons.Default.Gavel, ext.accentBlue)
                                    else -> Pair(Icons.Default.ChatBubble, ext.accentBlue)
                                }
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (!isRead && notif.id.isNotBlank()) {
                                                onMarkAsRead(notif.id)
                                            }
                                            onNotificationClick(notif)
                                        },
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, if (isRead) ext.cardBorder else ext.accentBlue.copy(alpha = 0.35f)),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isRead) {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (ext.isDark) 0.5f else 0.65f)
                                        } else {
                                            ext.accentBlueContainer.copy(alpha = if (ext.isDark) 0.45f else 0.7f)
                                        }
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.Top,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = notifTint.copy(alpha = 0.16f),
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = notifIcon,
                                                    contentDescription = null,
                                                    tint = notifTint,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = notif.title,
                                                    fontSize = 13.sp,
                                                    fontWeight = if (isRead) FontWeight.Medium else FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                if (!isRead) {
                                                    Surface(
                                                        shape = CircleShape,
                                                        color = ext.accentBlue,
                                                        modifier = Modifier.size(8.dp)
                                                    ) {}
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = notif.message,
                                                fontSize = 12.sp,
                                                color = ext.textSecondary
                                            )
                                            if (notif.senderName.isNotBlank()) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = "Gönderen: ${notif.senderName}",
                                                    fontSize = 10.sp,
                                                    color = ext.textMuted
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Fixed Footer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ext.accentBlueSolid,
                            contentColor = Color.White
                        )
                    ) {
                        Text("Kapat", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
