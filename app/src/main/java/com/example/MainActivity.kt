package com.example

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.BlackseaRepository
import com.example.model.UserRole
import com.example.model.UserStatus
import com.example.notifications.BackgroundNotificationWorker
import com.example.notifications.TeamNotificationDispatcher
import com.example.ui.MainScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.theme.BlackseaRoboticsTheme
import com.example.ui.theme.ElectricBlue
import com.example.update.AppReleaseInfo
import com.example.update.AppUpdateManager
import com.example.update.UpdateCheckResult
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
        enableEdgeToEdge()
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
        Log.d("Notifications", "POST_NOTIFICATIONS granted: $isGranted")
        if (!isGranted) {
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
                                    changelog = doc.getString("changelog") ?: "• Yeni dosya paylaşım sistemi (Resim, Video, Ses, CAD)\n• Otomatik APK güncelleme sistemi\n• Hata düzeltmeleri",
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
            Log.w("MainActivity", "Live update stream error: ${e.message}")
        }
    }

    // Check updates and schedule background notification sync after user logs in
    LaunchedEffect(currentUser?.uid, isAuthCompleted) {
        val activeUid = currentUser?.uid
        if (activeUid != null && isAuthCompleted) {
            BackgroundNotificationWorker.schedulePeriodicSync(context)
            performUpdateCheckAndNotify()
        }
    }

    // Real-time event-driven OS notifications while app is alive (never floods backlog on startup)
    val authenticatedUid = if (isAuthCompleted) currentUser?.uid else null
    val activeProfile by remember(authenticatedUid) {
        if (authenticatedUid.isNullOrBlank()) flowOf(null) else repository.observeUserProfile(authenticatedUid)
    }.collectAsState(initial = null)

    val activeIsManager = activeProfile?.let { profile ->
        profile.isSuperAdmin ||
            profile.role == UserRole.ADMIN.displayName ||
            profile.roles.contains("Yönetici") ||
            profile.roles.contains("Takım Kaptanı") ||
            profile.roles.contains("Yetkili Öğretmen") ||
            profile.email.equals("filim3290@gmail.com", ignoreCase = true)
    } ?: false

    LaunchedEffect(authenticatedUid, activeProfile?.status, activeIsManager, activeProfile?.team) {
        val uid = authenticatedUid ?: return@LaunchedEffect
        val profile = activeProfile ?: return@LaunchedEffect
        val isApproved = UserStatus.isActive(profile.status) ||
            profile.isSuperAdmin ||
            profile.email.equals("filim3290@gmail.com", ignoreCase = true)
        if (!isApproved) return@LaunchedEffect

        repository.observeNotifications(
            userId = uid,
            isManager = activeIsManager,
            userTeam = profile.team
        ).collect { filteredNotifications ->
            TeamNotificationDispatcher.processNotifications(
                context = context,
                userId = uid,
                notifications = filteredNotifications
            )
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
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
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
                    BackgroundNotificationWorker.cancelPeriodicSync(context)
                    Firebase.auth.signOut()
                    currentUser = null
                    isAuthCompleted = false
                }
            )
        }
    }
}
