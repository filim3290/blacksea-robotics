package com.example

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.BlackseaRepository
import com.example.data.UserProfileLoadState
import com.example.model.UserStatus
import com.example.notifications.BackgroundNotificationWorker
import com.example.notifications.RealtimeNotificationService
import com.example.notifications.TeamNotificationDispatcher
import com.example.ui.MainScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.BlackseaSplashScreen
import com.example.ui.theme.BlackseaRoboticsTheme
import com.example.ui.theme.ElectricBlue
import com.example.update.AppReleaseInfo
import com.example.update.AppUpdateManager
import com.example.update.UpdateCheckResult
import com.example.util.logDebug
import com.example.util.logDebugWarn
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class NotificationNavEvent(
    val type: String = "",
    val roomId: String = "",
    val teamId: String = "",
    val openUpdateDialog: Boolean = false,
    val nonce: Long = System.currentTimeMillis()
)

class MainActivity : ComponentActivity() {
    private val pendingNavEvent = mutableStateOf<NotificationNavEvent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )
        TeamNotificationDispatcher.ensureNotificationChannels(this)
        handleNotificationIntent(intent)

        val repository = BlackseaRepository(this)

        setContent {
            BlackseaRoboticsTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppContent(
                        repository = repository,
                        notificationNavEvent = pendingNavEvent.value,
                        onConsumeNotificationNav = { pendingNavEvent.value = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        if (intent == null) return
        val notifType = intent.getStringExtra(TeamNotificationDispatcher.EXTRA_NOTIF_TYPE).orEmpty()
        val roomId = intent.getStringExtra(TeamNotificationDispatcher.EXTRA_NOTIF_ROOM_ID).orEmpty()
        val teamId = intent.getStringExtra(TeamNotificationDispatcher.EXTRA_NOTIF_TEAM_ID).orEmpty()
        val openUpdate = intent.getBooleanExtra(TeamNotificationDispatcher.EXTRA_OPEN_UPDATE_DIALOG, false)
        if (notifType.isNotBlank() || openUpdate) {
            pendingNavEvent.value = NotificationNavEvent(
                type = notifType,
                roomId = roomId,
                teamId = teamId,
                openUpdateDialog = openUpdate,
                nonce = System.nanoTime()
            )
            intent.removeExtra(TeamNotificationDispatcher.EXTRA_NOTIF_TYPE)
            intent.removeExtra(TeamNotificationDispatcher.EXTRA_NOTIF_ROOM_ID)
            intent.removeExtra(TeamNotificationDispatcher.EXTRA_NOTIF_TEAM_ID)
            intent.removeExtra(TeamNotificationDispatcher.EXTRA_OPEN_UPDATE_DIALOG)
        }
    }
}

@Composable
fun AppContent(
    repository: BlackseaRepository,
    notificationNavEvent: NotificationNavEvent? = null,
    onConsumeNotificationNav: () -> Unit = {}
) {
    val context = LocalContext.current
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }
    var isAuthCompleted by remember { mutableStateOf(false) }
    var showSplash by rememberSaveable { mutableStateOf(true) }
    var hasPromptedNotificationPermission by rememberSaveable { mutableStateOf(false) }

    val updateManager = remember { AppUpdateManager(context) }
    val updateCheckMutex = remember { Mutex() }
    var availableUpdate by remember { mutableStateOf<AppReleaseInfo?>(null) }
    var inAppLatestUpdate by remember { mutableStateOf<AppReleaseInfo?>(null) }
    var showNotificationRationale by remember { mutableStateOf(false) }
    var isSelfDeleting by remember { mutableStateOf(false) }
    var permissionGrantCount by remember { mutableIntStateOf(0) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        logDebug("Notifications", "POST_NOTIFICATIONS granted: $isGranted")
        if (isGranted) {
            permissionGrantCount++
            BackgroundNotificationWorker.schedulePeriodicSync(context)
            BackgroundNotificationWorker.triggerImmediateSync(context)
            RealtimeNotificationService.startIfEnabled(context)
        } else {
            val enabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
            if (!enabled) {
                showNotificationRationale = true
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    suspend fun performUpdateCheckAndNotify(
        forceRefresh: Boolean = false,
        notifyOs: Boolean = true,
        openDialogRegardlessOfSignature: Boolean = false
    ) {
        updateCheckMutex.withLock {
            val result = updateManager.checkLatestReleaseDetailed(forceRefresh = forceRefresh)
            if (result is UpdateCheckResult.UpdateAvailable) {
                val update = result.release
                inAppLatestUpdate = update
                if (openDialogRegardlessOfSignature || (update.isForceUpdate && result.isSignatureCompatible)) {
                    availableUpdate = update
                }
                if (notifyOs) {
                    TeamNotificationDispatcher.dispatchUpdateNotificationIfNeeded(
                        context = context,
                        updateManager = updateManager,
                        release = update
                    )
                }
            } else if (result is UpdateCheckResult.UpToDate || result is UpdateCheckResult.NoReleaseFound) {
                inAppLatestUpdate = null
                availableUpdate = null
                TeamNotificationDispatcher.clearPendingUpdateNotification(context)
            }
        }
    }

    // Splash completion is driven directly by BlackseaSplashScreen once all 5 scenes finish (~3.22s + 0.28s fade)

    // Request notification permission only after splash exits so OS prompt never covers splash
    LaunchedEffect(showSplash) {
        if (!showSplash && !hasPromptedNotificationPermission) {
            hasPromptedNotificationPermission = true
            val enabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
            if (!enabled) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    showNotificationRationale = true
                }
            }
        }
    }

    // Handle notification tap for update dialog immediately
    LaunchedEffect(notificationNavEvent?.nonce) {
        if (notificationNavEvent?.openUpdateDialog == true) {
            performUpdateCheckAndNotify(
                forceRefresh = true,
                notifyOs = false,
                openDialogRegardlessOfSignature = true
            )
            onConsumeNotificationNav()
        }
    }

    // Check updates on app launch and listen to Firestore real-time updates
    LaunchedEffect(Unit) {
        TeamNotificationDispatcher.cleanupLegacyRealtimeSyncArtifacts(context)
        BackgroundNotificationWorker.schedulePeriodicSync(context)
        performUpdateCheckAndNotify(forceRefresh = true, notifyOs = true)

        // Real-time listener for Firestore app_config/latest_update
        try {
            var isInitialConfigEmission = true
            repository.db.collection("app_config").document("latest_update")
                .snapshots()
                .collect { doc ->
                    val shouldNotifyOs = !isInitialConfigEmission
                    isInitialConfigEmission = false
                    if (doc.exists()) {
                        val vCode = doc.getLong("versionCode")?.toInt() ?: 0
                        val vName = doc.getString("versionName") ?: ""
                        if (!updateManager.isUpdateAlreadyInstalled(vCode, vName)) {
                            val effectiveVName = vName.ifBlank { "1.0.5" }
                            val resolvedUrl = updateManager.normalizeDownloadUrl(doc.getString("apkDownloadUrl"), effectiveVName)
                            if (resolvedUrl.startsWith("https://") && resolvedUrl.endsWith(".apk", ignoreCase = true)) {
                                val release = AppReleaseInfo(
                                    tagName = doc.getString("tagName") ?: "v$effectiveVName",
                                    versionName = effectiveVName,
                                    versionCode = vCode,
                                    title = doc.getString("title") ?: "Yeni Güncelleme Mevcut",
                                    changelog = doc.getString("changelog") ?: "• Yeni medya ve dosya paylaşım deneyimi\n• Otomatik güncelleme desteği\n• Hata düzeltmeleri ve iyileştirmeler",
                                    publishedAt = doc.getString("publishedAt") ?: "Bugün",
                                    apkDownloadUrl = resolvedUrl,
                                    apkFileName = doc.getString("apkFileName") ?: "BlackseaRobotics-v$effectiveVName.apk",
                                    apkSize = doc.getLong("apkSize") ?: (29 * 1024 * 1024L),
                                    expectedSha256 = doc.getString("expectedSha256") ?: "",
                                    expectedCertSha256 = doc.getString("expectedCertSha256") ?: "",
                                    isForceUpdate = doc.getBoolean("isForceUpdate") ?: false
                                )
                                inAppLatestUpdate = release
                                if (release.isForceUpdate && updateManager.isReleaseSignatureCompatible(release)) {
                                    availableUpdate = release
                                }
                                if (shouldNotifyOs) {
                                    TeamNotificationDispatcher.dispatchUpdateNotificationIfNeeded(
                                        context = context,
                                        updateManager = updateManager,
                                        release = release
                                    )
                                }
                            }
                        } else {
                            inAppLatestUpdate?.let { currentBanner ->
                                if (updateManager.isUpdateAlreadyInstalled(currentBanner.versionCode, currentBanner.versionName)) {
                                    inAppLatestUpdate = null
                                }
                            }
                            availableUpdate?.let { currentDialog ->
                                if (updateManager.isUpdateAlreadyInstalled(currentDialog.versionCode, currentDialog.versionName)) {
                                    availableUpdate = null
                                }
                            }
                            if (inAppLatestUpdate == null) {
                                TeamNotificationDispatcher.clearPendingUpdateNotification(context)
                            }
                        }
                    }
                }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            logDebugWarn("MainActivity", "Live update stream error", e)
        }
    }

    // Check updates and schedule background notification sync after user logs in
    LaunchedEffect(currentUser?.uid, isAuthCompleted) {
        val activeUid = currentUser?.uid
        if (activeUid != null && isAuthCompleted) {
            isSelfDeleting = false
            BackgroundNotificationWorker.schedulePeriodicSync(context)
            performUpdateCheckAndNotify(forceRefresh = false, notifyOs = true)
        }
    }

    // Live profile observation for instant kick-out when revoked/deleted + event-driven notifications
    val authenticatedUid = if (isAuthCompleted) currentUser?.uid else null
    val profileState by remember(authenticatedUid) {
        if (authenticatedUid.isNullOrBlank()) {
            flowOf(UserProfileLoadState.Loading)
        } else {
            repository.observeUserProfileState(authenticatedUid)
        }
    }.collectAsState(initial = UserProfileLoadState.Loading)

    val activeProfile = (profileState as? UserProfileLoadState.Found)?.profile
    val isPlatformAdmin = remember(activeProfile, currentUser?.email) {
        activeProfile?.isPlatformManagerAccount() == true ||
            currentUser?.email.equals(com.example.model.PLATFORM_FOUNDER_EMAIL, ignoreCase = true)
    }

    val myMemberships by remember(authenticatedUid) {
        if (authenticatedUid.isNullOrBlank()) {
            flowOf(emptyList())
        } else {
            repository.observeMyMemberships(authenticatedUid)
        }
    }.collectAsState(initial = emptyList())

    val membershipsByTeam = remember(myMemberships, activeProfile, isPlatformAdmin) {
        val map = myMemberships.associateBy { it.teamId }.toMutableMap()
        val profile = activeProfile
        if (profile != null && !map.containsKey(com.example.model.PLATFORM_MAIN_TEAM_ID)) {
            val uid = profile.userId.ifBlank { profile.id }
            if (isPlatformAdmin && uid.isNotBlank()) {
                map[com.example.model.PLATFORM_MAIN_TEAM_ID] = com.example.model.TeamMembership(
                    id = "${com.example.model.PLATFORM_MAIN_TEAM_ID}_$uid",
                    teamId = com.example.model.PLATFORM_MAIN_TEAM_ID,
                    userId = uid,
                    userName = profile.getFullName(),
                    userEmail = profile.email,
                    subTeam = profile.team,
                    role = profile.role,
                    roles = profile.roles,
                    status = UserStatus.ACTIVE.value,
                    isTeamAdmin = true
                )
            }
        }
        map
    }

    LaunchedEffect(authenticatedUid, profileState, isSelfDeleting) {
        if (authenticatedUid == null) return@LaunchedEffect
        when (val state = profileState) {
            is UserProfileLoadState.Loading -> Unit
            is UserProfileLoadState.Missing -> {
                val isFounderEmail = currentUser?.email.equals(com.example.model.PLATFORM_FOUNDER_EMAIL, ignoreCase = true)
                if (!isFounderEmail && !isSelfDeleting) {
                    BackgroundNotificationWorker.cancelPeriodicSync(context)
                    RealtimeNotificationService.stop(context)
                    isAuthCompleted = false
                    Toast.makeText(
                        context,
                        "Hesabınız kaldırıldığı için oturum kapatıldı.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            is UserProfileLoadState.Found -> {
                val profile = state.profile
                BackgroundNotificationWorker.schedulePeriodicSync(context)
                RealtimeNotificationService.startIfEnabled(context)
                try {
                    repository.migrateLegacyUserToMainTeamMembershipIfNeeded(profile)
                    if (isPlatformAdmin) {
                        repository.ensureMainTeamExists()
                        repository.ensureDefaultRoomsExist()
                    }
                } catch (_: Exception) {
                }
            }
        }
    }

    LaunchedEffect(authenticatedUid, activeProfile?.createdAt, membershipsByTeam, isPlatformAdmin, permissionGrantCount) {
        val uid = authenticatedUid ?: return@LaunchedEffect
        val profile = activeProfile ?: return@LaunchedEffect

        val profileJoinMs = profile.createdAt?.toDate()?.time ?: 0L
        if (profileJoinMs > 0L) {
            TeamNotificationDispatcher.recordUserJoinTimestampIfMissing(context, uid, profileJoinMs)
        }
        val effectiveJoinMs = if (profileJoinMs > 0L) {
            profileJoinMs
        } else {
            TeamNotificationDispatcher.getUserJoinTimestampMs(context, uid)
        }

        if (!TeamNotificationDispatcher.hasSeededBaseline(context, uid)) {
            val initialSnapshot = repository.fetchNotificationsOnceForUser(
                userId = uid,
                membershipsByTeam = membershipsByTeam,
                isPlatformAdmin = isPlatformAdmin,
                userCreatedAtMs = effectiveJoinMs
            )
            if (initialSnapshot != null) {
                TeamNotificationDispatcher.syncBaselineSilently(
                    context = context,
                    userId = uid,
                    notifications = initialSnapshot
                )
            }
        }

        repository.observeNotificationsForUser(
            userId = uid,
            membershipsByTeam = membershipsByTeam,
            isPlatformAdmin = isPlatformAdmin,
            userCreatedAtMs = effectiveJoinMs
        ).collect { filteredNotifications ->
            TeamNotificationDispatcher.processNotifications(
                context = context,
                userId = uid,
                notifications = filteredNotifications,
                userCreatedAtMs = effectiveJoinMs
            )
        }
    }

    // Check updates when returning from background and schedule prompt background check on stop
    DisposableEffect(lifecycleOwner, currentUser?.uid) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                TeamNotificationDispatcher.setAppForeground(context, currentUser?.uid, true)
                RealtimeNotificationService.startIfEnabled(context)
                scope.launch {
                    performUpdateCheckAndNotify(forceRefresh = true, notifyOs = true)
                }
            } else if (event == Lifecycle.Event.ON_STOP) {
                TeamNotificationDispatcher.setAppForeground(context, currentUser?.uid, false)
                RealtimeNotificationService.startIfEnabled(context)
                BackgroundNotificationWorker.schedulePeriodicSync(context)
                BackgroundNotificationWorker.triggerOneTimeUpdateCheck(context, delaySeconds = 5L)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (!showSplash && showNotificationRationale) {
        AlertDialog(
            onDismissRequest = { showNotificationRationale = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = null,
                    tint = ElectricBlue,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Bildirimlere İzin Verin",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Takım mesajları, önemli duyurular ve başvuru onay durumunuzdan anında haberdar olmak için lütfen bildirimlere izin verin. Ayarlarda engellenmiş görünüyorsa 'Ayarlarda Aç' butonuna basarak bildirimleri etkinleştirebilirsiniz.",
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showNotificationRationale = false
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
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricBlue,
                        contentColor = Color.White
                    )
                ) {
                    Text("Ayarlarda Aç")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNotificationRationale = false }) {
                    Text("Daha Sonra")
                }
            }
        )
    }

    // Show App Update Dialog whenever a newer version is found and splash has exited
    if (!showSplash) {
        availableUpdate?.let { release ->
            com.example.ui.components.AppUpdateDialog(
                releaseInfo = release,
                updateManager = updateManager,
                onDismiss = { availableUpdate = null }
            )
        }
    }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
            if (auth.currentUser == null) {
                isAuthCompleted = false
            }
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose {
            Firebase.auth.removeAuthStateListener(listener)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (currentUser == null || !isAuthCompleted) {
            AuthScreen(
                repository = repository,
                onAuthSuccess = {
                    currentUser = Firebase.auth.currentUser
                    isAuthCompleted = true
                }
            )
        } else {
            val uid = currentUser!!.uid
            key(uid) {
                MainScreen(
                    currentUserId = uid,
                    repository = repository,
                    updateManager = updateManager,
                    availableUpdate = inAppLatestUpdate,
                    notificationNavEvent = notificationNavEvent,
                    onConsumeNotificationNav = onConsumeNotificationNav,
                    onShowUpdateDialog = { release -> availableUpdate = release },
                    onSignOut = {
                        BackgroundNotificationWorker.cancelPeriodicSync(context)
                        RealtimeNotificationService.stop(context)
                        Firebase.auth.signOut()
                        currentUser = null
                        isAuthCompleted = false
                    },
                    onAccountDeleted = {
                        isSelfDeleting = true
                        BackgroundNotificationWorker.cancelPeriodicSync(context)
                        RealtimeNotificationService.stop(context)
                        TeamNotificationDispatcher.clearUserSyncState(context, uid)
                        Firebase.auth.signOut()
                        currentUser = null
                        isAuthCompleted = false
                    }
                )
            }
        }

        AnimatedVisibility(
            visible = showSplash,
            exit = fadeOut(animationSpec = tween(280))
        ) {
            BlackseaSplashScreen(
                onAnimationFinished = { showSplash = false }
            )
        }
    }
}
