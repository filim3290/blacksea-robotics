package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Folder
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
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.model.UserProfile
import com.example.model.UserRole
import com.example.ui.components.BlackseaLogo
import com.example.ui.screens.AdminScreen
import com.example.ui.screens.FilesScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.IdeasScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.RoomsScreen
import com.example.ui.theme.ElectricBlue

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
    onShowUpdateDialog: (com.example.update.AppReleaseInfo) -> Unit = {},
    onSignOut: () -> Unit
) {
    val currentUserProfile by repository.observeUserProfile(currentUserId).collectAsState(initial = null)
    var currentTab by remember { mutableStateOf(NavItem.HOME) }
    var selectedRoomIdForNavigation by remember { mutableStateOf("general") }
    val scope = rememberCoroutineScope()

    val isAdmin = currentUserProfile?.isSuperAdmin == true ||
            currentUserProfile?.role == UserRole.ADMIN.displayName ||
            currentUserProfile?.roles?.contains("Yönetici") == true ||
            currentUserProfile?.roles?.contains("Takım Kaptanı") == true ||
            currentUserProfile?.roles?.contains("Yetkili Öğretmen") == true ||
            currentUserProfile?.email.equals("filim3290@gmail.com", ignoreCase = true)

    // Team-targeted notifications (General + User's Team + Broadcasts + Direct)
    val notifications by repository.observeNotifications(
        userId = currentUserId,
        isManager = isAdmin,
        userTeam = currentUserProfile?.team ?: ""
    ).collectAsState(initial = emptyList())
    var locallyReadIds by remember { mutableStateOf(setOf<String>()) }
    val unreadNotificationsCount = remember(notifications, locallyReadIds) {
        notifications.count { notif ->
            !notif.isReadBy(currentUserId) && !locallyReadIds.contains(notif.id)
        }
    }
    var showNotificationsDialog by remember { mutableStateOf(false) }

    // BackHandler: When on sub-screens, navigate back to Home
    BackHandler(enabled = currentTab != NavItem.HOME) {
        currentTab = NavItem.HOME
    }

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
                            modifier = Modifier.padding(vertical = 12.dp)
                        ) {
                            BlackseaLogo(size = 40)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "BSR",
                                color = ElectricBlue,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    },
                    modifier = Modifier.fillMaxHeight().testTag("adaptive_navigation_rail")
                ) {
                    NavigationRailItem(
                        selected = currentTab == NavItem.HOME,
                        onClick = { currentTab = NavItem.HOME },
                        icon = { Icon(Icons.Default.Home, contentDescription = "Ana Sayfa") },
                        label = { Text("Ana Sayfa", fontSize = 11.sp) },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = ElectricBlue,
                            indicatorColor = ElectricBlue.copy(alpha = 0.15f)
                        ),
                        modifier = Modifier.testTag("nav_rail_home")
                    )

                    NavigationRailItem(
                        selected = currentTab == NavItem.ROOMS,
                        onClick = { currentTab = NavItem.ROOMS },
                        icon = { Icon(Icons.Default.ChatBubble, contentDescription = "Odalar") },
                        label = { Text("Odalar", fontSize = 11.sp) },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = ElectricBlue,
                            indicatorColor = ElectricBlue.copy(alpha = 0.15f)
                        ),
                        modifier = Modifier.testTag("nav_rail_rooms")
                    )

                    NavigationRailItem(
                        selected = currentTab == NavItem.IDEAS,
                        onClick = { currentTab = NavItem.IDEAS },
                        icon = { Icon(Icons.Default.Lightbulb, contentDescription = "Fikirler") },
                        label = { Text("Fikirler", fontSize = 11.sp) },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = ElectricBlue,
                            indicatorColor = ElectricBlue.copy(alpha = 0.15f)
                        ),
                        modifier = Modifier.testTag("nav_rail_ideas")
                    )

                    NavigationRailItem(
                        selected = currentTab == NavItem.FILES,
                        onClick = { currentTab = NavItem.FILES },
                        icon = { Icon(Icons.Default.Folder, contentDescription = "Dosyalar") },
                        label = { Text("Dosyalar", fontSize = 11.sp) },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = ElectricBlue,
                            indicatorColor = ElectricBlue.copy(alpha = 0.15f)
                        ),
                        modifier = Modifier.testTag("nav_rail_files")
                    )

                    if (isAdmin) {
                        NavigationRailItem(
                            selected = currentTab == NavItem.ADMIN,
                            onClick = { currentTab = NavItem.ADMIN },
                            icon = { Icon(Icons.Default.Security, contentDescription = "Yönetici") },
                            label = { Text("Yönetici", fontSize = 11.sp) },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = Color(0xFFE11D48),
                                indicatorColor = Color(0xFFE11D48).copy(alpha = 0.15f)
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
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = ElectricBlue,
                            indicatorColor = ElectricBlue.copy(alpha = 0.15f)
                        ),
                        modifier = Modifier.testTag("nav_rail_profile")
                    )
                }

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    ScreenContent(
                        currentTab = currentTab,
                        repository = repository,
                        currentUserProfile = currentUserProfile,
                        updateManager = updateManager,
                        onShowUpdateDialog = onShowUpdateDialog,
                        selectedRoomIdForNavigation = selectedRoomIdForNavigation,
                        onNavigateToRoom = { roomId ->
                            selectedRoomIdForNavigation = roomId
                            currentTab = NavItem.ROOMS
                        },
                        onNavigateToIdeas = { currentTab = NavItem.IDEAS },
                        onNavigateToFiles = { currentTab = NavItem.FILES },
                        onNavigateToAdmin = { currentTab = NavItem.ADMIN },
                        onSignOut = onSignOut
                    )
                }
            }
        } else {
            // Standard Mobile Layout with TopAppBar and NavigationBar
            Scaffold(
                topBar = {
                    CenterAlignedTopAppBar(
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.testTag("app_top_bar_title")
                            ) {
                                BlackseaLogo(size = 32)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "BLACKSEA ROBOTICS",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.5.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = ElectricBlue.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = "BSR",
                                        color = ElectricBlue,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
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
                                                    containerColor = Color(0xFFEF4444),
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
                                            tint = if (unreadNotificationsCount > 0) ElectricBlue else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                if (isAdmin) {
                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = if (currentTab == NavItem.ADMIN) Color(0xFFE11D48) else Color(0xFFE11D48).copy(alpha = 0.15f),
                                        modifier = Modifier
                                            .clickable { currentTab = if (currentTab == NavItem.ADMIN) NavItem.HOME else NavItem.ADMIN }
                                            .testTag("top_bar_admin_button")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Security,
                                                contentDescription = "Yönetici Paneli",
                                                tint = if (currentTab == NavItem.ADMIN) Color.White else Color(0xFFE11D48),
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Text(
                                                text = "Yönetici",
                                                color = if (currentTab == NavItem.ADMIN) Color.White else Color(0xFFE11D48),
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
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 8.dp
                    ) {
                        NavigationBarItem(
                            selected = currentTab == NavItem.HOME,
                            onClick = { currentTab = NavItem.HOME },
                            icon = { Icon(Icons.Default.Home, contentDescription = "Ana Sayfa") },
                            label = { Text("Ana Sayfa", fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = ElectricBlue,
                                indicatorColor = ElectricBlue.copy(alpha = 0.15f)
                            ),
                            modifier = Modifier.testTag("nav_home")
                        )

                        NavigationBarItem(
                            selected = currentTab == NavItem.ROOMS,
                            onClick = { currentTab = NavItem.ROOMS },
                            icon = { Icon(Icons.Default.ChatBubble, contentDescription = "Odalar") },
                            label = { Text("Odalar", fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = ElectricBlue,
                                indicatorColor = ElectricBlue.copy(alpha = 0.15f)
                            ),
                            modifier = Modifier.testTag("nav_rooms")
                        )

                        NavigationBarItem(
                            selected = currentTab == NavItem.IDEAS,
                            onClick = { currentTab = NavItem.IDEAS },
                            icon = { Icon(Icons.Default.Lightbulb, contentDescription = "Fikirler") },
                            label = { Text("Fikirler", fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = ElectricBlue,
                                indicatorColor = ElectricBlue.copy(alpha = 0.15f)
                            ),
                            modifier = Modifier.testTag("nav_ideas")
                        )

                        NavigationBarItem(
                            selected = currentTab == NavItem.FILES,
                            onClick = { currentTab = NavItem.FILES },
                            icon = { Icon(Icons.Default.Folder, contentDescription = "Dosyalar") },
                            label = { Text("Dosyalar", fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = ElectricBlue,
                                indicatorColor = ElectricBlue.copy(alpha = 0.15f)
                            ),
                            modifier = Modifier.testTag("nav_files")
                        )

                        NavigationBarItem(
                            selected = currentTab == NavItem.PROFILE,
                            onClick = { currentTab = NavItem.PROFILE },
                            icon = { Icon(Icons.Default.Person, contentDescription = "Profil") },
                            label = { Text("Profil", fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = ElectricBlue,
                                indicatorColor = ElectricBlue.copy(alpha = 0.15f)
                            ),
                            modifier = Modifier.testTag("nav_profile")
                        )
                    }
                }
            ) { innerPadding ->
                Box(modifier = Modifier.padding(innerPadding)) {
                    ScreenContent(
                        currentTab = currentTab,
                        repository = repository,
                        currentUserProfile = currentUserProfile,
                        updateManager = updateManager,
                        onShowUpdateDialog = onShowUpdateDialog,
                        selectedRoomIdForNavigation = selectedRoomIdForNavigation,
                        onNavigateToRoom = { roomId ->
                            selectedRoomIdForNavigation = roomId
                            currentTab = NavItem.ROOMS
                        },
                        onNavigateToIdeas = { currentTab = NavItem.IDEAS },
                        onNavigateToFiles = { currentTab = NavItem.FILES },
                        onNavigateToAdmin = { currentTab = NavItem.ADMIN },
                        onSignOut = onSignOut
                    )
                }
            }
        }

        if (showNotificationsDialog) {
            TeamNotificationsDialog(
                notifications = notifications,
                currentUserId = currentUserId,
                locallyReadIds = locallyReadIds,
                onDismiss = { showNotificationsDialog = false },
                onMarkAsRead = { notifId ->
                    locallyReadIds = locallyReadIds + notifId
                    val target = notifications.firstOrNull { it.id == notifId }
                    val isShared = target != null && target.recipientUserId != currentUserId
                    scope.launch {
                        repository.markNotificationAsRead(notifId, currentUserId, isShared = isShared)
                    }
                },
                onMarkAllAsRead = {
                    val allIds = notifications.map { it.id }.filter { it.isNotBlank() }.toSet()
                    val personalIds = notifications
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

@Composable
private fun ScreenContent(
    currentTab: NavItem,
    repository: BlackseaRepository,
    currentUserProfile: UserProfile?,
    updateManager: com.example.update.AppUpdateManager? = null,
    onShowUpdateDialog: (com.example.update.AppReleaseInfo) -> Unit = {},
    selectedRoomIdForNavigation: String,
    onNavigateToRoom: (String) -> Unit,
    onNavigateToIdeas: () -> Unit,
    onNavigateToFiles: () -> Unit,
    onNavigateToAdmin: () -> Unit = {},
    onSignOut: () -> Unit
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
                onNavigateToRoom = onNavigateToRoom,
                onNavigateToIdeas = onNavigateToIdeas,
                onNavigateToFiles = onNavigateToFiles,
                onNavigateToAdmin = onNavigateToAdmin
            )
            NavItem.ROOMS -> RoomsScreen(
                repository = repository,
                currentUserProfile = currentUserProfile,
                initialRoomId = selectedRoomIdForNavigation
            )
            NavItem.IDEAS -> IdeasScreen(
                repository = repository,
                currentUserProfile = currentUserProfile
            )
            NavItem.FILES -> FilesScreen(
                repository = repository,
                currentUserProfile = currentUserProfile
            )
            NavItem.ADMIN -> AdminScreen(
                repository = repository,
                currentUserProfile = currentUserProfile,
                updateManager = updateManager,
                onShowUpdateDialog = onShowUpdateDialog
            )
            NavItem.PROFILE -> ProfileScreen(
                currentUserProfile = currentUserProfile,
                repository = repository,
                updateManager = updateManager,
                onShowUpdateDialog = onShowUpdateDialog,
                onNavigateToAdmin = onNavigateToAdmin,
                onSignOut = onSignOut
            )
        }
    }
}

@Composable
fun TeamNotificationsDialog(
    notifications: List<TeamNotification>,
    currentUserId: String,
    locallyReadIds: Set<String>,
    onDismiss: () -> Unit,
    onMarkAsRead: (String) -> Unit,
    onMarkAllAsRead: () -> Unit
) {
    val context = LocalContext.current
    val areSystemNotificationsEnabled = remember {
        NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    val unreadCount = remember(notifications, locallyReadIds) {
        notifications.count { notif ->
            !notif.isReadBy(currentUserId) && !locallyReadIds.contains(notif.id)
        }
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
            tonalElevation = 6.dp,
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
                            color = ElectricBlue.copy(alpha = 0.15f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Notifications,
                                    contentDescription = null,
                                    tint = ElectricBlue,
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
                                color = if (unreadCount > 0) ElectricBlue else Color(0xFF94A3B8),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    if (unreadCount > 0) {
                        Button(
                            onClick = onMarkAllAsRead,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricBlue.copy(alpha = 0.15f),
                                contentColor = ElectricBlue
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
                        color = Color(0xFFEF4444).copy(alpha = 0.12f)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(20.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Sistem Bildirimleri Kapalı",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFEF4444)
                                )
                                Text(
                                    text = "Cihaz ayarlarından bildirimlere izin verin.",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                Text("Ayarlarda Aç", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ElectricBlue)
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
                    if (notifications.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.DoneAll,
                                    contentDescription = null,
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Henüz yeni bir bildirim yok.",
                                    fontSize = 13.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(
                                items = notifications,
                                key = { index, notif -> "${notif.id.ifBlank { "notif" }}_$index" }
                            ) { _, notif ->
                                val isRead = notif.isReadBy(currentUserId) || locallyReadIds.contains(notif.id)
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (!isRead && notif.id.isNotBlank()) {
                                                onMarkAsRead(notif.id)
                                            }
                                        },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isRead) {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        } else {
                                            ElectricBlue.copy(alpha = 0.12f)
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
                                            color = if (notif.type == "broadcast") Color(0xFFF59E0B).copy(alpha = 0.2f) else ElectricBlue.copy(alpha = 0.2f),
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = if (notif.type == "broadcast") Icons.Default.Campaign else Icons.Default.ChatBubble,
                                                    contentDescription = null,
                                                    tint = if (notif.type == "broadcast") Color(0xFFF59E0B) else ElectricBlue,
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
                                                        color = ElectricBlue,
                                                        modifier = Modifier.size(8.dp)
                                                    ) {}
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = notif.message,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            if (notif.senderName.isNotBlank()) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = "Gönderen: ${notif.senderName}",
                                                    fontSize = 10.sp,
                                                    color = Color(0xFF94A3B8)
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
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                    ) {
                        Text("Kapat", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
