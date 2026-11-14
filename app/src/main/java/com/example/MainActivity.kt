package com.example

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.launch
import com.example.data.BlackseaRepository
import com.example.ui.MainScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.theme.BlackseaRoboticsTheme
import com.example.ui.theme.ElectricBlue
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        createNotificationChannels()

        val repository = BlackseaRepository(this)

        setContent {
            BlackseaRoboticsTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppContent(repository = repository)
                }
            }
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java) ?: return
            
            val generalChannel = NotificationChannel(
                "bsr_team_notifications",
                "BLACKSEA ROBOTICS Bildirimleri",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Takım mesajları, duyuruları ve onay bildirimleri"
                enableLights(true)
                enableVibration(true)
            }
            
            val announcementsChannel = NotificationChannel(
                "bsr_announcements",
                "Takım Duyuruları",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Yönetici ve takım kaptanı duyuruları"
                enableLights(true)
                enableVibration(true)
            }

            val updatesChannel = NotificationChannel(
                "bsr_updates",
                "Uygulama Güncellemeleri",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Yeni sürüm ve APK güncelleme bildirimleri"
                enableLights(true)
                enableVibration(true)
            }

            notificationManager.createNotificationChannel(generalChannel)
            notificationManager.createNotificationChannel(announcementsChannel)
            notificationManager.createNotificationChannel(updatesChannel)
        }
    }
}

@Composable
fun AppContent(repository: BlackseaRepository) {
    val context = LocalContext.current
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }
    var isAuthCompleted by remember { mutableStateOf(false) }

    val updateManager = remember { com.example.update.AppUpdateManager(context) }
    var availableUpdate by remember { mutableStateOf<com.example.update.AppReleaseInfo?>(null) }
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

    suspend fun performUpdateCheckAndNotify() {
        val result = updateManager.checkLatestReleaseDetailed(forceRefresh = false)
        if (result is com.example.update.UpdateCheckResult.UpdateAvailable && result.isSignatureCompatible) {
            val update = result.release
            availableUpdate = update

            // Dispatch a local system notification ONCE for this new version
            if (!updateManager.hasNotifiedForVersion(update.versionCode)) {
                try {
                    if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                        val tapIntent = Intent(context, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            putExtra("open_update_dialog", true)
                        }
                        val pendingIntent = PendingIntent.getActivity(
                            context,
                            1001,
                            tapIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        val notifManager = context.getSystemService(NotificationManager::class.java)
                        val builder = androidx.core.app.NotificationCompat.Builder(context, "bsr_updates")
                            .setSmallIcon(R.mipmap.ic_launcher)
                            .setContentTitle("🔔 Yeni Güncelleme Mevcut")
                            .setContentText("Blacksea Robotics için yeni bir sürüm (v${update.versionName}) yayınlandı.")
                            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                            .setContentIntent(pendingIntent)
                            .setAutoCancel(true)
                        notifManager?.notify(1001, builder.build())
                        updateManager.markVersionNotified(update.versionCode)
                    }
                } catch (e: Exception) {
                    Log.w("MainActivity", "Failed to trigger system notification: ${e.message}")
                }
            }
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
                                val release = com.example.update.AppReleaseInfo(
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
                                }
                            }
                        }
                    }
                }
        } catch (e: Exception) {
            Log.w("MainActivity", "Live update stream error: ${e.message}")
        }
    }

    // Check updates after user logs in
    LaunchedEffect(isAuthCompleted) {
        if (isAuthCompleted) {
            performUpdateCheckAndNotify()
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
                onShowUpdateDialog = { release -> availableUpdate = release },
                onSignOut = {
                    Firebase.auth.signOut()
                    currentUser = null
                    isAuthCompleted = false
                }
            )
        }
    }
}
