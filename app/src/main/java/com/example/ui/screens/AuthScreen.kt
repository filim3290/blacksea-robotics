package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.example.data.BlackseaRepository
import com.example.model.PredefinedRoles
import com.example.model.TeamType
import com.example.model.UserProfile
import com.example.model.UserStatus
import com.example.notifications.TeamNotificationDispatcher
import com.example.ui.components.BlackseaLogo
import com.example.ui.components.TeamBadge
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.PhoenixAmber
import com.example.ui.theme.blackseaColors
import com.example.util.AppMessages
import com.example.util.logDebug
import com.example.util.logDebugError
import com.example.util.logDebugWarn
import com.example.util.sanitizeUserErrorMessage
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope,
    onAuthCancelled: () -> Unit = {}
) {
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (e: Exception) {
        logDebugError("Auth", "Google Sign-In client config missing", e)
        onAuthError(AppMessages.GOOGLE_SIGN_IN_ERROR)
        return
    }

    val activity = context.findActivity()
    if (activity == null) {
        onAuthError(AppMessages.GOOGLE_SIGN_IN_ERROR)
        return
    }

    val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
    val request = GetCredentialRequest.Builder().addCredentialOption(signInOption).build()

    scope.launch {
        try {
            val result = credentialManager.getCredential(activity, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                Firebase.auth.signInWithCredential(authCredential).await()
                logDebug("Auth", "Google sign in successful")
                onAuthSuccess()
            } else {
                onAuthError(AppMessages.GOOGLE_SIGN_IN_ERROR)
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: GetCredentialCancellationException) {
            logDebugWarn("Auth", "Google Sign-In flow cancelled")
            onAuthCancelled()
        } catch (e: Exception) {
            logDebugError("Auth", "Google Sign-In failed", e)
            onAuthError(sanitizeUserErrorMessage(e, AppMessages.GOOGLE_SIGN_IN_ERROR))
        }
    }
}

fun attemptAutoSignIn(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onUnauthenticated: () -> Unit,
    scope: CoroutineScope
) {
    if (Firebase.auth.currentUser != null) {
        onAuthSuccess()
        return
    }
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (e: Exception) {
        onUnauthenticated()
        return
    }

    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(true)
        .setServerClientId(clientId)
        .setAutoSelectEnabled(true)
        .build()

    val request = GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build()

    scope.launch {
        try {
            val result = credentialManager.getCredential(context, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                Firebase.auth.signInWithCredential(authCredential).await()
                onAuthSuccess()
            } else {
                onUnauthenticated()
            }
        } catch (e: Exception) {
            onUnauthenticated()
        }
    }
}

fun appSignOut(
    credentialManager: CredentialManager,
    scope: CoroutineScope,
    onSignOutComplete: () -> Unit = {}
) {
    scope.launch {
        withContext(NonCancellable) {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                logDebugWarn("Auth", "Failed to clear credential state", e)
            } finally {
                Firebase.auth.signOut()
                onSignOutComplete()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    repository: BlackseaRepository,
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    val ext = MaterialTheme.blackseaColors

    var authUser by remember { mutableStateOf(Firebase.auth.currentUser) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var userProfile by remember { mutableStateOf<UserProfile?>(null) }
    var checkingProfile by remember { mutableStateOf(false) }

    // Real Identity Form Inputs
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var selectedTeam by remember { mutableStateOf(TeamType.SOFTWARE.displayName) }
    var teamMenuExpanded by remember { mutableStateOf(false) }

    // Keep authUser in sync with FirebaseAuth
    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            authUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose {
            Firebase.auth.removeAuthStateListener(listener)
        }
    }

    // Observe user profile in real-time whenever authUser changes
    LaunchedEffect(authUser?.uid) {
        val currentUid = authUser?.uid
        if (currentUid == null) {
            checkingProfile = false
            userProfile = null
        } else {
            checkingProfile = true
            repository.observeUserProfile(currentUid).collect { profile ->
                userProfile = profile
                checkingProfile = false

                if (profile == null || (profile.firstName.isBlank() && profile.lastName.isBlank())) {
                    // Pre-fill form from Google account if not yet set
                    val gName = authUser?.displayName ?: ""
                    val parts = gName.trim().split(" ")
                    if (parts.size >= 2) {
                        firstName = parts.dropLast(1).joinToString(" ")
                        lastName = parts.last()
                    } else if (parts.size == 1) {
                        firstName = parts[0]
                    }
                    username = (authUser?.email?.substringBefore("@") ?: "").lowercase()
                } else {
                    val isSuperAdmin = authUser?.email.equals("filim3290@gmail.com", ignoreCase = true)
                    // If approved or active, enter the main app!
                    if (UserStatus.isActive(profile.status) || isSuperAdmin) {
                        onAuthSuccess()
                    }
                }
            }
        }
    }

    // Handle back button on secondary steps
    if (authUser != null) {
        BackHandler {
            appSignOut(credentialManager, scope) {
                authUser = null
                userProfile = null
                checkingProfile = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(colors = ext.authGradientColors)
            )
            .padding(24.dp)
            .testTag("auth_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            BlackseaLogo(size = 96)

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "BLACKSEA ROBOTICS",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.2.sp,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Ünye Fen Lisesi Robotik Takımı",
                color = ext.textSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = PhoenixAmber.copy(alpha = if (ext.isDark) 0.15f else 0.22f),
                border = BorderStroke(1.dp, ext.amberText.copy(alpha = 0.25f))
            ) {
                Text(
                    text = "ONE TEAM. ONE VISION. ONE ROBOT.",
                    color = ext.amberText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.8.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            if (checkingProfile) {
                // Loading state while verifying identity with cloud database
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, ext.cardBorder),
                    elevation = CardDefaults.cardElevation(defaultElevation = if (ext.isDark) 0.dp else 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = ext.accentBlue, modifier = Modifier.size(36.dp), strokeWidth = 3.dp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Ekip Üyeliği Doğrulanıyor...",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Bilgileriniz kontrol ediliyor, lütfen bekleyin",
                            color = ext.textSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            } else if (authUser == null) {
                // STEP 1: Google Login Screen
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, ext.cardBorder),
                    elevation = CardDefaults.cardElevation(defaultElevation = if (ext.isDark) 0.dp else 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Ekip Platformu Girişi",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Ünye Fen Lisesi Robotik Takımı üyeleriyle aynı platformda buluşun ve anlık iletişim kurun.",
                            color = ext.textSecondary,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        if (errorMessage != null) {
                            Text(
                                text = errorMessage ?: "",
                                color = ext.dangerText,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                        }

                        Button(
                            onClick = {
                                isLoading = true
                                errorMessage = null
                                onGoogleSignInClicked(
                                    context = context,
                                    credentialManager = credentialManager,
                                    onAuthSuccess = {
                                        isLoading = false
                                        authUser = Firebase.auth.currentUser
                                    },
                                    onAuthError = { err ->
                                        isLoading = false
                                        errorMessage = err
                                    },
                                    scope = scope,
                                    onAuthCancelled = { isLoading = false }
                                )
                            },
                            enabled = !isLoading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("google_sign_in_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ext.accentBlueSolid,
                                contentColor = Color.White
                            )
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(imageVector = Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Google ile Giriş Yap",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }
                }
            } else if (userProfile == null || (userProfile?.firstName.isNullOrBlank() && userProfile?.lastName.isNullOrBlank())) {
                // STEP 2: New Member Real Identity Registration Form
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, ext.cardBorder),
                    elevation = CardDefaults.cardElevation(defaultElevation = if (ext.isDark) 0.dp else 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Header with Back button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    appSignOut(credentialManager, scope) {
                                        authUser = null
                                        userProfile = null
                                    }
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Geri Dön",
                                    tint = ext.textSecondary
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Gerçek Takım Kimliği Kaydı",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Signed in email pill
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, ext.cardBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Email, contentDescription = null, tint = ext.accentBlue, modifier = Modifier.size(14.dp))
                                    Text(
                                        text = authUser?.email ?: "",
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Text(
                                    text = "Değiştir",
                                    color = ext.accentBlue,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable {
                                        appSignOut(credentialManager, scope) {
                                            authUser = null
                                            userProfile = null
                                        }
                                    }
                                )
                            }
                        }

                        Text(
                            text = "Mesajlarda ve fikirlerde gerçek adınız ve soyadınız kullanılacaktır.",
                            color = ext.textSecondary,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Ad ve Soyad
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = firstName,
                                onValueChange = { firstName = it },
                                label = { Text("Ad") },
                                placeholder = { Text("Örn: Mehmet İlker") },
                                modifier = Modifier.weight(1f).testTag("first_name_input"),
                                shape = RoundedCornerShape(12.dp)
                            )

                            OutlinedTextField(
                                value = lastName,
                                onValueChange = { lastName = it },
                                label = { Text("Soyad") },
                                placeholder = { Text("Örn: Baş") },
                                modifier = Modifier.weight(1f).testTag("last_name_input"),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Kullanıcı Adı
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text("Kullanıcı Adı") },
                            placeholder = { Text("Örn: ilker_bas") },
                            modifier = Modifier.fillMaxWidth().testTag("username_input"),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Ekip Seçimi
                        ExposedDropdownMenuBox(
                            expanded = teamMenuExpanded,
                            onExpandedChange = { teamMenuExpanded = !teamMenuExpanded },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = selectedTeam,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Ekip Seçimi") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = teamMenuExpanded) },
                                modifier = Modifier.fillMaxWidth().menuAnchor().testTag("team_dropdown"),
                                shape = RoundedCornerShape(12.dp)
                            )

                            ExposedDropdownMenu(
                                expanded = teamMenuExpanded,
                                onDismissRequest = { teamMenuExpanded = false }
                            ) {
                                listOf(
                                    TeamType.MANAGEMENT,
                                    TeamType.SOFTWARE,
                                    TeamType.MECHANIC,
                                    TeamType.PR
                                ).forEach { team ->
                                    DropdownMenuItem(
                                        text = { TeamBadge(teamName = team.displayName) },
                                        onClick = {
                                            selectedTeam = team.displayName
                                            teamMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        if (errorMessage != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = errorMessage ?: "",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = {
                                val current = authUser ?: return@Button
                                val uid = current.uid
                                val email = current.email ?: ""
                                val photoUrl = current.photoUrl?.toString() ?: ""

                                val trimmedFirst = firstName.trim()
                                val trimmedLast = lastName.trim()

                                if (trimmedFirst.isBlank() || trimmedLast.isBlank()) {
                                    errorMessage = "Lütfen gerçek adınızı ve soyadınızı giriniz."
                                    return@Button
                                }

                                scope.launch {
                                    isLoading = true
                                    errorMessage = null
                                    try {
                                        val defaultRole = PredefinedRoles.getDefaultTeamRole(selectedTeam)
                                        val createdProfile = repository.createOrUpdateUserProfile(
                                            userId = uid,
                                            firstName = trimmedFirst,
                                            lastName = trimmedLast,
                                            username = username.trim().ifBlank { "${trimmedFirst.lowercase()}_${trimmedLast.lowercase()}" },
                                            email = email,
                                            team = selectedTeam,
                                            roles = listOf(defaultRole),
                                            photoUrl = photoUrl
                                        )
                                        TeamNotificationDispatcher.recordUserJoinTimestampIfMissing(context, uid)

                                        val isSuperAdmin = email.equals("filim3290@gmail.com", ignoreCase = true)
                                        if (isSuperAdmin || UserStatus.isActive(createdProfile.status)) {
                                            try {
                                                repository.ensureDefaultRoomsExist()
                                            } catch (_: Exception) {
                                            }
                                            onAuthSuccess()
                                        } else {
                                            // Regular new user: status is "pending".
                                            // Transition immediately to Pending Approval Screen!
                                            userProfile = createdProfile
                                            Toast.makeText(context, "Kaydınız alındı! Yönetici onayı bekleniyor.", Toast.LENGTH_LONG).show()
                                        }
                                    } catch (ce: CancellationException) {
                                        throw ce
                                    } catch (e: Exception) {
                                        logDebugError("Auth", "Save profile failed", e)
                                        errorMessage = sanitizeUserErrorMessage(e, "Profil kaydedilemedi. Lütfen tekrar deneyin.")
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            },
                            enabled = !isLoading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("save_profile_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ext.accentBlueSolid,
                                contentColor = Color.White
                            )
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Ekip Profilini Oluştur ve Başvur", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        TextButton(
                            onClick = {
                                appSignOut(credentialManager, scope) {
                                    authUser = null
                                    userProfile = null
                                }
                            }
                        ) {
                            Icon(imageVector = Icons.Default.ExitToApp, contentDescription = null, tint = ext.textSecondary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Farklı Bir Google Hesabı Kullan", color = ext.textSecondary, fontSize = 13.sp)
                        }
                    }
                }
            } else if (UserStatus.isPending(userProfile?.status)) {
                // STEP 3: Pending Approval Screen for New Users
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, ext.cardBorder),
                    elevation = CardDefaults.cardElevation(defaultElevation = if (ext.isDark) 0.dp else 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = PhoenixAmber.copy(alpha = 0.18f),
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.HourglassTop,
                                    contentDescription = null,
                                    tint = ext.amberText,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "Yönetici Onayı Bekleniyor",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Merhaba ${userProfile?.getFullName()}! Ekip başvurunuz başarıyla alındı. Takım yöneticisi veya danışman öğretmeniniz üyeliğinizi onayladığında tüm odalara ve çalışma alanlarına otomatik erişiminiz açılacaktır.",
                            color = ext.textSecondary,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 19.sp
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Member details pill
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, ext.cardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Başvuran:", fontSize = 12.sp, color = ext.textMuted)
                                    Text(userProfile?.getFullName() ?: "", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Ekip:", fontSize = 12.sp, color = ext.textMuted)
                                    Text(userProfile?.team ?: "", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ext.accentBlue)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("E-posta:", fontSize = 12.sp, color = ext.textMuted)
                                    Text(userProfile?.email ?: "", fontSize = 12.sp, color = ext.textSecondary)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Durum:", fontSize = 12.sp, color = ext.textMuted)
                                    Text("⏳ Onay Bekliyor", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ext.amberText)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = {
                                val currentUid = authUser?.uid ?: return@Button
                                scope.launch {
                                    isLoading = true
                                    val profile = repository.getUserProfile(currentUid)
                                    isLoading = false
                                    if (profile != null) {
                                        userProfile = profile
                                        if (UserStatus.isActive(profile.status)) {
                                            Toast.makeText(context, "Üyeliğiniz onaylandı! Hoş geldiniz.", Toast.LENGTH_SHORT).show()
                                            onAuthSuccess()
                                        } else {
                                            Toast.makeText(context, "Henüz onaylanmadı. Yönetici onayı bekleniyor.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            enabled = !isLoading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ext.accentBlueSolid,
                                contentColor = Color.White
                            )
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Onay Durumunu Kontrol Et", fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedButton(
                            onClick = {
                                appSignOut(credentialManager, scope) {
                                    authUser = null
                                    userProfile = null
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.ExitToApp, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Farklı Hesapla Giriş Yap / Çıkış")
                        }
                    }
                }
            } else if (UserStatus.isRevoked(userProfile?.status)) {
                // Revoked Access Screen
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, ext.cardBorder),
                    elevation = CardDefaults.cardElevation(defaultElevation = if (ext.isDark) 0.dp else 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = ext.dangerContainer,
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Block,
                                    contentDescription = null,
                                    tint = ext.dangerText,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "Uygulama Erişiminiz Kaldırıldı",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Sayın ${userProfile?.getFullName()}, takım yöneticisi uygulama erişiminizi kaldırdı. Tekrar erişim talebi gönderebilir veya farklı bir hesapla giriş yapabilirsiniz.",
                            color = ext.textSecondary,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 19.sp
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = {
                                val uid = authUser?.uid ?: return@Button
                                scope.launch {
                                    isLoading = true
                                    try {
                                        repository.reapplyForMembership(uid, userProfile?.team ?: "Genel Ekip")
                                        val updated = repository.getUserProfile(uid)
                                        userProfile = updated
                                        Toast.makeText(context, "Yeniden erişim başvurunuz yöneticiye iletildi!", Toast.LENGTH_LONG).show()
                                    } catch (ce: CancellationException) {
                                        throw ce
                                    } catch (e: Exception) {
                                        logDebugError("Auth", "Reapply from revoked failed", e)
                                        Toast.makeText(context, sanitizeUserErrorMessage(e, "Başvuru iletilemedi. Lütfen tekrar deneyin."), Toast.LENGTH_SHORT).show()
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            },
                            enabled = !isLoading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("revoked_reapply_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ext.accentBlueSolid,
                                contentColor = Color.White
                            )
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Tekrar Erişim Başvurusu Yap", fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedButton(
                            onClick = {
                                appSignOut(credentialManager, scope) {
                                    authUser = null
                                    userProfile = null
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.ExitToApp, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Farklı Bir Hesapla Giriş Yap")
                        }
                    }
                }
            } else if (UserStatus.isRejected(userProfile?.status)) {
                // Rejected Screen
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, ext.cardBorder),
                    elevation = CardDefaults.cardElevation(defaultElevation = if (ext.isDark) 0.dp else 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = ext.dangerContainer,
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = null,
                                    tint = ext.dangerText,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "Başvuru Onaylanmadı",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Sayın ${userProfile?.getFullName()}, ekip başvurunuz takım yönetimi tarafından onaylanmadı. Bilgilerinizi kontrol edip yeniden başvuru yapabilir veya farklı bir hesap seçebilirsiniz.",
                            color = ext.textSecondary,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = {
                                val uid = authUser?.uid ?: return@Button
                                scope.launch {
                                    isLoading = true
                                    try {
                                        repository.reapplyForMembership(uid, userProfile?.team ?: "Genel Ekip")
                                        val updated = repository.getUserProfile(uid)
                                        userProfile = updated
                                        Toast.makeText(context, "Yeniden başvurunuz yöneticiye iletildi!", Toast.LENGTH_LONG).show()
                                    } catch (ce: CancellationException) {
                                        throw ce
                                    } catch (e: Exception) {
                                        logDebugError("Auth", "Reapply failed", e)
                                        Toast.makeText(context, sanitizeUserErrorMessage(e, "Başvuru iletilemedi. Lütfen tekrar deneyin."), Toast.LENGTH_SHORT).show()
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            },
                            enabled = !isLoading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ext.accentBlueSolid,
                                contentColor = Color.White
                            )
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Tekrar Başvuru Yap", fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedButton(
                            onClick = {
                                appSignOut(credentialManager, scope) {
                                    authUser = null
                                    userProfile = null
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.ExitToApp, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Farklı Bir Hesapla Giriş Yap")
                        }
                    }
                }
            } else {
                // Active / Approved User -> Enter app immediately
                LaunchedEffect(Unit) {
                    onAuthSuccess()
                }
            }
        }
    }
}
