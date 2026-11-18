package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.example.model.COMPETITION_PRESETS
import com.example.model.TEAM_ACCENT_COLORS
import com.example.model.TeamInfo
import com.example.model.TeamLogoPreset
import com.example.ui.components.BlackseaLogo
import com.example.ui.components.TeamLogoBadge
import com.example.ui.components.parseTeamColor
import com.example.ui.components.teamLogoPresetIcon
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

    // Dedicated "Kendi Ekibini Oluştur" onboarding flow state
    var isCreateTeamFlow by rememberSaveable { mutableStateOf(false) }
    var isPreparingAccount by remember { mutableStateOf(false) }
    var teamSubmissionSuccess by rememberSaveable { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            authUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose {
            Firebase.auth.removeAuthStateListener(listener)
        }
    }

    // Standard Google Sign-In auto-onboards global /users/{uid} and opens Team Hub immediately
    LaunchedEffect(authUser?.uid, isCreateTeamFlow, teamSubmissionSuccess) {
        val user = authUser ?: return@LaunchedEffect
        if (isCreateTeamFlow || teamSubmissionSuccess) return@LaunchedEffect

        isPreparingAccount = true
        errorMessage = null
        val res = repository.ensureBaseUserProfileOnGoogleSignIn(
            userId = user.uid,
            email = user.email.orEmpty(),
            displayName = user.displayName.orEmpty(),
            photoUrl = user.photoUrl?.toString().orEmpty()
        )
        isPreparingAccount = false
        if (res.isSuccess) {
            onAuthSuccess()
        } else {
            errorMessage = sanitizeUserErrorMessage(
                res.exceptionOrNull() ?: IllegalStateException("Hesap oluşturulamadı."),
                "Hesap hazırlanırken bir sorun oluştu."
            )
        }
    }

    if (authUser != null && (isCreateTeamFlow || teamSubmissionSuccess)) {
        BackHandler {
            teamSubmissionSuccess = false
            isCreateTeamFlow = false
            appSignOut(credentialManager, scope) {
                authUser = null
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors = ext.authGradientColors))
            .systemBarsPadding()
            .imePadding()
            .padding(20.dp)
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
            BlackseaLogo(size = 88)

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "BLACKSEA ROBOTICS",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 23.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.2.sp,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Çoklu Takım & Proje Platformu",
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
                    text = "TEKNOFEST • ROBOTİK • YAZILIM • MÜHENDİSLİK",
                    color = ext.amberText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.6.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            when {
                isPreparingAccount -> {
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
                                text = "Hesabınız Hazırlanıyor...",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Aktif takımlar yükleniyor, lütfen bekleyin",
                                color = ext.textSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                authUser == null -> {
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
                                text = "Platforma Giriş Yap",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Google hesabınızla giriş yaparak takımları inceleyip katılabilir veya okulunuz/projeniz için kendi ekibinizi oluşturabilirsiniz.",
                                color = ext.textSecondary,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 18.sp
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            if (errorMessage != null) {
                                Text(
                                    text = errorMessage.orEmpty(),
                                    color = ext.dangerText,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )
                            }

                            Button(
                                onClick = {
                                    isCreateTeamFlow = false
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
                                if (isLoading && !isCreateTeamFlow) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
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

                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                HorizontalDivider(modifier = Modifier.weight(1f), color = ext.cardBorder)
                                Text(
                                    text = "  veya  ",
                                    fontSize = 12.sp,
                                    color = ext.textMuted
                                )
                                HorizontalDivider(modifier = Modifier.weight(1f), color = ext.cardBorder)
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            OutlinedButton(
                                onClick = {
                                    isCreateTeamFlow = true
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
                                            isCreateTeamFlow = false
                                            errorMessage = err
                                        },
                                        scope = scope,
                                        onAuthCancelled = {
                                            isLoading = false
                                            isCreateTeamFlow = false
                                        }
                                    )
                                },
                                enabled = !isLoading,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("create_team_entry_button"),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.5.dp, ext.amberText.copy(alpha = 0.65f))
                            ) {
                                if (isLoading && isCreateTeamFlow) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        color = ext.amberText,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.AddCircleOutline,
                                        contentDescription = null,
                                        tint = ext.amberText,
                                        modifier = Modifier.size(19.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Kendi Ekibini Oluştur",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = ext.amberText
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Yeni takım başvuruları e-posta hesabınıza bağlanır ve BlackSea Robotics yöneticilerinin onayından sonra aktif olur.",
                                color = ext.textMuted,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                teamSubmissionSuccess -> {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("team_application_submitted_card"),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, ext.cardBorder),
                        elevation = CardDefaults.cardElevation(defaultElevation = if (ext.isDark) 0.dp else 4.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = ext.emeraldSolid.copy(alpha = 0.16f),
                                modifier = Modifier.size(64.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = ext.emeraldText,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }

                            Text(
                                text = "Başvurunuz Alındı!",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )

                            Text(
                                text = "Takım oluşturma başvurunuz BlackSea Robotics yöneticilerine gönderildi.",
                                color = ext.textSecondary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                                lineHeight = 20.sp
                            )

                            Text(
                                text = "Başvurunuz onaylandığında bildirim alacaksınız ve ekibinizin bağımsız çalışma alanı otomatik olarak açılacaktır. Bu arada diğer takımlara da göz atabilirsiniz.",
                                color = ext.textMuted,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 18.sp
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Button(
                                onClick = {
                                    teamSubmissionSuccess = false
                                    isCreateTeamFlow = false
                                    onAuthSuccess()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("go_to_team_hub_after_apply_button"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ext.accentBlueSolid,
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(imageVector = Icons.Default.Groups, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Takımlar Ekranına Geç", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                isCreateTeamFlow -> {
                    TeamCreationApplicationCard(
                        repository = repository,
                        connectedEmail = authUser?.email.orEmpty(),
                        defaultManagerName = authUser?.displayName.orEmpty(),
                        founderPhotoUrl = authUser?.photoUrl?.toString().orEmpty(),
                        onChangeAccountClick = {
                            appSignOut(credentialManager, scope) {
                                authUser = null
                                isCreateTeamFlow = false
                            }
                        },
                        onBackClick = {
                            isCreateTeamFlow = false
                            appSignOut(credentialManager, scope) {
                                authUser = null
                            }
                        },
                        onSubmitted = {
                            teamSubmissionSuccess = true
                        }
                    )
                }

                else -> {
                    // Fallback error state if account initialization failed after sign-in
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, ext.cardBorder)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = errorMessage ?: "Bağlantı doğrulanamadı.",
                                color = ext.dangerText,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                            Button(
                                onClick = {
                                    val user = authUser ?: return@Button
                                    scope.launch {
                                        isPreparingAccount = true
                                        val res = repository.ensureBaseUserProfileOnGoogleSignIn(
                                            userId = user.uid,
                                            email = user.email.orEmpty(),
                                            displayName = user.displayName.orEmpty(),
                                            photoUrl = user.photoUrl?.toString().orEmpty()
                                        )
                                        isPreparingAccount = false
                                        if (res.isSuccess) {
                                            onAuthSuccess()
                                        } else {
                                            errorMessage = sanitizeUserErrorMessage(
                                                res.exceptionOrNull() ?: IllegalStateException("Tekrar deneyin.")
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Tekrar Dene", fontWeight = FontWeight.Bold)
                            }
                            TextButton(
                                onClick = {
                                    appSignOut(credentialManager, scope) { authUser = null }
                                }
                            ) {
                                Text("Oturumu Kapat")
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TeamCreationApplicationCard(
    repository: BlackseaRepository,
    connectedEmail: String,
    defaultManagerName: String,
    founderPhotoUrl: String,
    onChangeAccountClick: (() -> Unit)? = null,
    onBackClick: () -> Unit,
    onSubmitted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ext = MaterialTheme.blackseaColors

    var teamName by rememberSaveable { mutableStateOf("") }
    var shortCode by rememberSaveable { mutableStateOf("") }
    var shortCodeEdited by rememberSaveable { mutableStateOf(false) }
    var competition by rememberSaveable { mutableStateOf("TEKNOFEST") }
    var schoolName by rememberSaveable { mutableStateOf("") }
    var city by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var motto by rememberSaveable { mutableStateOf("") }
    var selectedLogoKey by rememberSaveable { mutableStateOf(TeamLogoPreset.ROCKET.key) }
    var customLogoUrl by rememberSaveable { mutableStateOf("") }
    var isUploadingLogo by remember { mutableStateOf(false) }
    var selectedColorHex by rememberSaveable { mutableStateOf(TEAM_ACCENT_COLORS.first()) }
    var managerName by rememberSaveable(defaultManagerName) { mutableStateOf(defaultManagerName) }
    var managerContact by rememberSaveable(connectedEmail) { mutableStateOf(connectedEmail) }
    var isSubmitting by remember { mutableStateOf(false) }
    var formError by remember { mutableStateOf<String?>(null) }

    val logoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            isUploadingLogo = true
            scope.launch {
                val res = repository.uploadTeamLogo(
                    context = context,
                    uri = uri,
                    teamHint = shortCode.ifBlank { teamName.ifBlank { "team" } }
                )
                isUploadingLogo = false
                if (res.isSuccess) {
                    customLogoUrl = res.getOrNull().orEmpty()
                    Toast.makeText(context, "Takım logosu eklendi!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(
                        context,
                        sanitizeUserErrorMessage(res.exceptionOrNull(), "Logo yüklenemedi."),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("team_creation_form_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, ext.cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = if (ext.isDark) 0.dp else 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick, enabled = !isSubmitting) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Geri",
                        tint = ext.textSecondary
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Kendi Ekibini Oluştur",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "BlackSea Robotics yönetici onayından sonra ekibiniz aktif olur",
                        fontSize = 11.sp,
                        color = ext.textMuted
                    )
                }
            }

            // Verified Google account pill
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, ext.cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(imageVector = Icons.Default.Email, contentDescription = null, tint = ext.accentBlue, modifier = Modifier.size(16.dp))
                        Text(
                            text = connectedEmail.ifBlank { "Bağlı Google Hesabı" },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (onChangeAccountClick != null) {
                        Text(
                            text = "Değiştir",
                            color = ext.accentBlue,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable(enabled = !isSubmitting) { onChangeAccountClick() }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = teamName,
                    onValueChange = { value ->
                        teamName = value
                        if (!shortCodeEdited) {
                            val normalized = value.trim()
                                .replace('ç', 'C').replace('Ç', 'C')
                                .replace('ğ', 'G').replace('Ğ', 'G')
                                .replace('ı', 'I').replace('İ', 'I')
                                .replace('ö', 'O').replace('Ö', 'O')
                                .replace('ş', 'S').replace('Ş', 'S')
                                .replace('ü', 'U').replace('Ü', 'U')
                            val initials = normalized.split(" ")
                                .filter { it.isNotBlank() }
                                .map { it.first().uppercaseChar() }
                                .joinToString("")
                            shortCode = if (initials.length >= 2) {
                                initials.take(5)
                            } else {
                                normalized.replace(" ", "").take(4).uppercase()
                            }
                        }
                    },
                    label = { Text("Takım Adı *") },
                    placeholder = { Text("Örn: Fatsa Robotik") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1.7f)
                        .testTag("create_team_name_input")
                )
                OutlinedTextField(
                    value = shortCode,
                    onValueChange = {
                        shortCodeEdited = it.isNotBlank()
                        if (it.length <= 8) shortCode = it.uppercase()
                    },
                    label = { Text("Kısa Kod *") },
                    placeholder = { Text("Örn: FRT") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("create_team_code_input")
                )
            }

            Text(
                text = "Yarışma / Proje Alanı *",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                COMPETITION_PRESETS.forEach { preset ->
                    FilterChip(
                        selected = competition == preset,
                        onClick = { competition = preset },
                        label = { Text(preset, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ext.accentBlueContainer,
                            selectedLabelColor = ext.accentBlue
                        )
                    )
                }
            }
            OutlinedTextField(
                value = competition,
                onValueChange = { competition = it },
                label = { Text("Yarışma / Proje / Amaç *") },
                placeholder = { Text("Örn: TEKNOFEST İnsansız Hava Aracı") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("create_team_competition_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = schoolName,
                    onValueChange = { schoolName = it },
                    label = { Text("Okul / Kurum Adı *") },
                    placeholder = { Text("Örn: Ünye Fen Lisesi") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1.2f)
                        .testTag("create_team_school_input")
                )
                OutlinedTextField(
                    value = city,
                    onValueChange = { city = it },
                    label = { Text("Şehir *") },
                    placeholder = { Text("Örn: Ordu") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(0.9f)
                        .testTag("create_team_city_input")
                )
            }

            OutlinedTextField(
                value = description,
                onValueChange = { if (it.length <= 450) description = it },
                label = { Text("Takım Hakkında Kısa Açıklama *") },
                placeholder = { Text("Takımınızın hedefini, odaklandığı teknoloji ve projeleri kısaca anlatın...") },
                minLines = 3,
                maxLines = 5,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("create_team_description_input")
            )

            OutlinedTextField(
                value = motto,
                onValueChange = { if (it.length <= 100) motto = it },
                label = { Text("Takım Sloganı / Motto (Opsiyonel)") },
                placeholder = { Text("Örn: Geleceği Kodlayan Ekip") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = managerName,
                    onValueChange = { managerName = it },
                    label = { Text("Takım Yöneticisi Adı *") },
                    placeholder = { Text("Ad Soyad") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("create_team_manager_name_input")
                )
                OutlinedTextField(
                    value = managerContact,
                    onValueChange = { managerContact = it },
                    label = { Text("İletişim Bilgisi *") },
                    placeholder = { Text("Tel veya E-posta") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("create_team_manager_contact_input")
                )
            }

            // Logo & Accent Color Customization
            Text(
                text = "Takım Logosu, Sembolü ve Rengi",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                border = BorderStroke(1.dp, ext.cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TeamLogoBadge(
                        team = TeamInfo(
                            id = "preview",
                            name = teamName.ifBlank { "Yeni Takım" },
                            logoKey = selectedLogoKey,
                            logoUrl = customLogoUrl,
                            accentColorHex = selectedColorHex
                        ),
                        size = 52
                    )
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = if (customLogoUrl.isNotBlank()) "Özel Logo Yüklendi" else "Özel Takım Logosu (Opsiyonel)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (customLogoUrl.isNotBlank()) "Kartlarda özel logonuz gösterilecek" else "Galeriden takım logonuzu yükleyebilirsiniz",
                            fontSize = 11.sp,
                            color = ext.textMuted
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    logoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                enabled = !isUploadingLogo && !isSubmitting,
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("create_team_pick_logo_button")
                            ) {
                                if (isUploadingLogo) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(imageVector = Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(15.dp))
                                }
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = if (customLogoUrl.isNotBlank()) "Değiştir" else "Logo Seç",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (customLogoUrl.isNotBlank()) {
                                TextButton(
                                    onClick = { customLogoUrl = "" },
                                    enabled = !isUploadingLogo && !isSubmitting,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Logoyu Kaldır", tint = ext.dangerText, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("Kaldır", fontSize = 11.sp, color = ext.dangerText)
                                }
                            }
                        }
                    }
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TeamLogoPreset.entries
                    .filter { it != TeamLogoPreset.BSR_PHOENIX }
                    .forEach { preset ->
                        val isSelected = selectedLogoKey == preset.key
                        val accent = parseTeamColor(selectedColorHex)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) accent.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, if (isSelected) accent else ext.cardBorder),
                            modifier = Modifier.clickable { selectedLogoKey = preset.key }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = teamLogoPresetIcon(preset.key),
                                    contentDescription = preset.label,
                                    tint = if (isSelected) accent else ext.textSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = preset.label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) accent else ext.textSecondary
                                )
                            }
                        }
                    }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TEAM_ACCENT_COLORS.forEach { hex ->
                    val color = parseTeamColor(hex)
                    val selected = selectedColorHex.equals(hex, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                width = if (selected) 2.5.dp else 1.dp,
                                color = if (selected) MaterialTheme.colorScheme.onSurface else Color.White.copy(alpha = 0.4f),
                                shape = CircleShape
                            )
                            .clickable { selectedColorHex = hex },
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Seçili Renk",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            if (formError != null) {
                Text(
                    text = formError.orEmpty(),
                    color = ext.dangerText,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Button(
                onClick = {
                    val code = shortCode.trim().ifBlank {
                        teamName.trim().split(" ").filter { it.isNotBlank() }
                            .map { it.first().uppercaseChar() }
                            .joinToString("")
                            .take(4)
                    }
                    isSubmitting = true
                    formError = null
                    scope.launch {
                        val res = repository.submitTeamCreationRequest(
                            name = teamName,
                            shortCode = code,
                            competition = competition,
                            schoolName = schoolName,
                            city = city,
                            description = description,
                            motto = motto,
                            logoKey = selectedLogoKey,
                            logoUrl = customLogoUrl,
                            accentColorHex = selectedColorHex,
                            managerName = managerName,
                            managerContact = managerContact,
                            founderEmail = connectedEmail,
                            founderPhotoUrl = founderPhotoUrl
                        )
                        isSubmitting = false
                        if (res.isSuccess) {
                            Toast.makeText(
                                context,
                                "Takım oluşturma başvurunuz BlackSea Robotics yöneticilerine gönderildi.",
                                Toast.LENGTH_LONG
                            ).show()
                            onSubmitted()
                        } else {
                            formError = sanitizeUserErrorMessage(
                                res.exceptionOrNull() ?: IllegalStateException("Başvuru gönderilemedi.")
                            )
                        }
                    }
                },
                enabled = !isSubmitting && !isUploadingLogo,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp)
                    .testTag("submit_team_creation_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ext.accentBlueSolid,
                    contentColor = Color.White
                )
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(imageVector = Icons.Default.RocketLaunch, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Takım Başvurusunu Gönder", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
}
