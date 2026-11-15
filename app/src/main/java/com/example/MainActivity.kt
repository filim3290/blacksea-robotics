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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
        val openUpdate = intent.getBooleanExtra(TeamNotificationDispatcher.EXTRA_OPEN_UPDATE_DIALOG, false)
        if (notifType.isNotBlank() || openUpdate) {
            pendingNavEvent.value = NotificationNavEvent(
                type = notifType,
                roomId = roomId,
                openUpdateDialog = openUpdate,
                nonce = System.nanoTime()
            )
            intent.removeExtra(TeamNotificationDispatcher.EXTRA_NOTIF_TYPE)
            intent.removeExtra(TeamNotificationDispatcher.EXTRA_NOTIF_ROOM_ID)
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

    val updateManager = remember { AppUpdateManager(context) }
    val updateCheckMutex = remember { Mutex() }
    var availableUpdate by remember { mutableStateOf<AppReleaseInfo?>(null) }
    var showNotificationRationale by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        logDebug("Notifications", "POST_NOTIFICATIONS granted: $isGranted")
        if (isGranted) {
            if (isAuthCompleted) {
                RealtimeNotificationService.startIfEligible(context)
            }
        } else {
            val enabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
            if (!enabled) {
                showNotificationRationale = true
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    suspend fun performUpdateCheckAndNotify(forceRefresh: Boolean = false) {
        updateCheckMutex.withLock {
            val result = updateManager.checkLatestReleaseDetailed(forceRefresh = forceRefresh)
            if (result is UpdateCheckResult.UpdateAvailable && result.isSignatureCompatible) {
                val update = result.release
                availableUpdate = update
                TeamNotificationDispatcher.dispatchUpdateNotificationIfNeeded(
                    context = context,
                    updateManager = updateManager,
                    release = update
                )
            }
        }
    }

    // Handle notification tap for update dialog immediately
    LaunchedEffect(notificationNavEvent?.nonce) {
        if (notificationNavEvent?.openUpdateDialog == true) {
            performUpdateCheckAndNotify(forceRefresh = true)
            onConsumeNotificationNav()
        }
    }

    // Check updates on app launch and listen to Firestore real-time updates
    LaunchedEffect(Unit) {
        val enabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        if (!enabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            } else {
                showNotificationRationale = true
            }
        }

        performUpdateCheckAndNotify()

        // Real-time listener for Firestore app_config/latest_update
        try {
            repository.db.collection("app_config").document("latest_update")
                .snapshots()
                .collect { doc ->
                    if (doc.exists()) {
                        val vCode = doc.getLong("versionCode")?.toInt() ?: 0
                        if (vCode > updateManager.getCurrentVersionCode()) {
                            val vName = doc.getString("versionName") ?: "1.0.5"
                            val resolvedUrl = updateManager.normalizeDownloadUrl(doc.getString("apkDownloadUrl"), vName)
                            if (resolvedUrl.startsWith("https://") && resolvedUrl.endsWith(".apk", ignoreCase = true)) {
                                val release = AppReleaseInfo(
                                    tagName = doc.getString("tagName") ?: "v$vName",
                                    versionName = vName,
                                    versionCode = vCode,
                                    title = doc.getString("title") ?: "Yeni Güncelleme Mevcut",
                                    changelog = doc.getString("changelog") ?: "• Yeni medya ve dosya paylaşım deneyimi\n• Otomatik güncelleme desteği\n• Hata düzeltmeleri ve iyileştirmeler",
                                    publishedAt = doc.getString("publishedAt") ?: "Bugün",
                                    apkDownloadUrl = resolvedUrl,
                                    apkFileName = doc.getString("apkFileName") ?: "BlackseaRobotics-v$vName.apk",
                                    apkSize = doc.getLong("apkSize") ?: (29 * 1024 * 1024L),
                                    expectedSha256 = doc.getString("expectedSha256") ?: "",
                                    expectedCertSha256 = doc.getString("expectedCertSha256") ?: "",
                                    isForceUpdate = doc.getBoolean("isForceUpdate") ?: false
                                )
                                if (updateManager.isReleaseSignatureCompatible(release)) {
                                    availableUpdate = release
                                    TeamNotificationDispatcher.dispatchUpdateNotificationIfNeeded(
                                        context = context,
                                        updateManager = updateManager,
                                        release = release
                                    )
                                }
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
            BackgroundNotificationWorker.schedulePeriodicSync(context)
            RealtimeNotificationService.startIfEligible(context)
            performUpdateCheckAndNotify()
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
    val activeIsManager = activeProfile?.isManagerAccount() ?: false

    LaunchedEffect(authenticatedUid, profileState) {
        if (authenticatedUid == null) return@LaunchedEffect
        when (val state = profileState) {
            is UserProfileLoadState.Loading -> Unit
            is UserProfileLoadState.Missing -> {
                val isFounderEmail = currentUser?.email.equals("filim3290@gmail.com", ignoreCase = true)
                if (!isFounderEmail) {
                    BackgroundNotificationWorker.cancelPeriodicSync(context)
                    RealtimeNotificationService.stop(context)
                    isAuthCompleted = false
                    Toast.makeText(
                        context,
                        "Hesabınız yönetici tarafından kaldırıldığı için oturum kapatıldı.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            is UserProfileLoadState.Found -> {
                val profile = state.profile
                val isFounder = profile.isSuperAdmin ||
                    profile.email.equals("filim3290@gmail.com", ignoreCase = true) ||
                    currentUser?.email.equals("filim3290@gmail.com", ignoreCase = true)
                val isApproved = UserStatus.isActive(profile.status) || isFounder
                if (!isApproved) {
                    BackgroundNotificationWorker.cancelPeriodicSync(context)
                    RealtimeNotificationService.stop(context)
                    isAuthCompleted = false
                    if (UserStatus.isRevoked(profile.status)) {
                        Toast.makeText(
                            context,
                            "Uygulama erişiminiz yönetici tarafından kaldırıldı.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } else {
                    RealtimeNotificationService.startIfEligible(context)
                    try {
                        repository.ensureDefaultRoomsExist()
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    LaunchedEffect(authenticatedUid, activeProfile?.status, activeIsManager, activeProfile?.team) {
        val uid = authenticatedUid ?: return@LaunchedEffect
        val profile = activeProfile ?: return@LaunchedEffect
        val isFounder = profile.isSuperAdmin ||
            profile.email.equals("filim3290@gmail.com", ignoreCase = true) ||
            currentUser?.email.equals("filim3290@gmail.com", ignoreCase = true)
        val isApproved = UserStatus.isActive(profile.status) || isFounder
        if (!isApproved) return@LaunchedEffect

        val profileJoinMs = profile.createdAt?.toDate()?.time ?: 0L
        if (profileJoinMs > 0L) {
            TeamNotificationDispatcher.recordUserJoinTimestampIfMissing(context, uid, profileJoinMs)
        }
        val effectiveJoinMs = if (profileJoinMs > 0L) {
            profileJoinMs
        } else {
            TeamNotificationDispatcher.getUserJoinTimestampMs(context, uid)
        }

        var hasHandledInitialSnapshot = false
        repository.observeNotifications(
            userId = uid,
            isManager = activeIsManager,
            userTeam = profile.team,
            userCreatedAtMs = effectiveJoinMs
        ).collect { filteredNotifications ->
            if (!hasHandledInitialSnapshot) {
                TeamNotificationDispatcher.syncBaselineSilently(
                    context = context,
                    userId = uid,
                    notifications = filteredNotifications
                )
                hasHandledInitialSnapshot = true
            } else {
                TeamNotificationDispatcher.processNotifications(
                    context = context,
                    userId = uid,
                    notifications = filteredNotifications,
                    userCreatedAtMs = effectiveJoinMs
                )
            }
        }
    }

    // Check updates when returning from background (uses 15-min cache)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch {
                    performUpdateCheckAndNotify()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (showNotificationRationale) {
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

    // Show App Update Dialog whenever a newer version is found
    availableUpdate?.let { release ->
        com.example.ui.components.AppUpdateDialog(
            releaseInfo = release,
            updateManager = updateManager,
            onDismiss = { availableUpdate = null }
        )
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
                notificationNavEvent = notificationNavEvent,
                onConsumeNotificationNav = onConsumeNotificationNav,
                onShowUpdateDialog = { release -> availableUpdate = release },
                onSignOut = {
                    RealtimeNotificationService.stop(context)
                    BackgroundNotificationWorker.cancelPeriodicSync(context)
                    Firebase.auth.signOut()
                    currentUser = null
                    isAuthCompleted = false
                }
            )
        }
    }
}
