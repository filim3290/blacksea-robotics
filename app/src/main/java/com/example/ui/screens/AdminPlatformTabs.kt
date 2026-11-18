package com.example.ui.screens

import android.widget.Toast
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BlackseaRepository
import com.example.model.COMPETITION_PRESETS
import com.example.model.PLATFORM_MAIN_TEAM_ID
import com.example.model.TEAM_ACCENT_COLORS
import com.example.model.TeamInfo
import com.example.model.TeamLogoPreset
import com.example.model.TeamStatus
import com.example.ui.components.TeamLogoBadge
import com.example.ui.components.parseTeamColor
import com.example.ui.components.teamLogoPresetIcon
import com.example.ui.theme.blackseaColors
import com.example.util.sanitizeUserErrorMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TeamApplicationsTab(
    pendingApplications: List<TeamInfo>,
    repository: BlackseaRepository
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ext = MaterialTheme.blackseaColors

    var appToReject by remember { mutableStateOf<TeamInfo?>(null) }
    var rejectionReason by remember { mutableStateOf("") }
    var processingTeamId by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("team_applications_tab"),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = ext.accentBlueContainer.copy(alpha = 0.45f)),
                border = BorderStroke(1.dp, ext.accentBlue.copy(alpha = 0.25f))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Takım Kurma Başvuruları (BSR Platform Yönetimi)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Onaylanan her yeni takım otomatik olarak kurucu yöneticisiyle ve 4 bağımsız çalışma odasıyla platformda aktif edilir.",
                        fontSize = 12.sp,
                        color = ext.textSecondary,
                        lineHeight = 17.sp
                    )
                }
            }
        }

        if (pendingApplications.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, ext.cardBorder)
                ) {
                    Text(
                        text = "Şu anda onay bekleyen yeni takım başvurusu bulunmuyor.",
                        fontSize = 13.sp,
                        color = ext.textMuted,
                        modifier = Modifier.padding(20.dp)
                    )
                }
            }
        } else {
            itemsIndexed(
                items = pendingApplications,
                key = { index, app -> "pending_team_${app.id}_$index" }
            ) { _, app ->
                val isProcessing = processingTeamId == app.id
                val accent = parseTeamColor(app.accentColorHex, ext.accentBlue)

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, ext.amberText.copy(alpha = 0.35f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = if (ext.isDark) 2.dp else 1.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            TeamLogoBadge(team = app, size = 50)
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = app.name,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f, fill = false),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (app.shortCode.isNotBlank()) {
                                        Surface(shape = RoundedCornerShape(6.dp), color = accent.copy(alpha = 0.14f)) {
                                            Text(
                                                text = app.shortCode,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = accent,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = app.competition,
                                    fontSize = 12.sp,
                                    color = ext.accentBlue,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = ext.amberText.copy(alpha = 0.16f)
                            ) {
                                Text(
                                    text = "Beklemede",
                                    color = ext.amberText,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.School, contentDescription = null, tint = ext.textSecondary, modifier = Modifier.size(15.dp))
                                    Text(
                                        text = "${app.schoolName} • ${app.city}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.Person, contentDescription = null, tint = ext.textSecondary, modifier = Modifier.size(15.dp))
                                    Text(
                                        text = "Yönetici: ${app.managerName} (${app.managerContact})",
                                        fontSize = 12.sp,
                                        color = ext.textSecondary
                                    )
                                }
                                if (app.founderEmail.isNotBlank()) {
                                    Text(
                                        text = "Kurucu Hesap: ${app.founderEmail}",
                                        fontSize = 11.sp,
                                        color = ext.textMuted
                                    )
                                }
                            }
                        }

                        if (app.description.isNotBlank()) {
                            Text(
                                text = app.description,
                                fontSize = 13.sp,
                                color = ext.textSecondary,
                                lineHeight = 18.sp
                            )
                        }

                        if (app.motto.isNotBlank()) {
                            Text(
                                text = "Motto: \"${app.motto}\"",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = ext.amberText
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    appToReject = app
                                    rejectionReason = ""
                                },
                                enabled = !isProcessing,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, ext.dangerText.copy(alpha = 0.45f))
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = ext.dangerText, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Reddet", color = ext.dangerText, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    processingTeamId = app.id
                                    scope.launch {
                                        try {
                                            val res = repository.approveTeamCreationRequest(app)
                                            if (res.isSuccess) {
                                                Toast.makeText(
                                                    context,
                                                    "${app.name} onaylandı ve çalışma alanı oluşturuldu!",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            } else {
                                                Toast.makeText(
                                                    context,
                                                    sanitizeUserErrorMessage(res.exceptionOrNull()),
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        } catch (ce: CancellationException) {
                                            throw ce
                                        } finally {
                                            processingTeamId = null
                                        }
                                    }
                                },
                                enabled = !isProcessing,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ext.emeraldSolid,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier.testTag("approve_team_${app.id}")
                            ) {
                                if (isProcessing) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Onayla", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (appToReject != null) {
        val targetApp = appToReject!!
        AlertDialog(
            onDismissRequest = { if (processingTeamId == null) appToReject = null },
            title = { Text("${targetApp.name} Başvurusunu Reddet", fontWeight = FontWeight.Bold, color = ext.dangerText) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Başvuru sahibine bildirilecek kısa bir reddetme gerekçesi yazabilirsiniz (isteğe bağlı).",
                        fontSize = 12.sp,
                        color = ext.textSecondary
                    )
                    OutlinedTextField(
                        value = rejectionReason,
                        onValueChange = { rejectionReason = it },
                        label = { Text("Gerekçe (İsteğe Bağlı)") },
                        placeholder = { Text("Örn: Okul doğrulama bilgisi eksik") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        processingTeamId = targetApp.id
                        scope.launch {
                            try {
                                val res = repository.rejectTeamCreationRequest(targetApp, rejectionReason)
                                if (res.isSuccess) {
                                    Toast.makeText(context, "${targetApp.name} başvurusu reddedildi.", Toast.LENGTH_SHORT).show()
                                    appToReject = null
                                } else {
                                    Toast.makeText(
                                        context,
                                        sanitizeUserErrorMessage(res.exceptionOrNull()),
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            } catch (ce: CancellationException) {
                                throw ce
                            } finally {
                                processingTeamId = null
                            }
                        }
                    },
                    enabled = processingTeamId == null,
                    colors = ButtonDefaults.buttonColors(containerColor = ext.dangerSolid, contentColor = Color.White)
                ) {
                    if (processingTeamId != null) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Başvuruyu Reddet", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { appToReject = null }, enabled = processingTeamId == null) {
                    Text("Vazgeç")
                }
            }
        )
    }
}

@Composable
fun PlatformTeamsOverviewTab(
    teams: List<TeamInfo>
) {
    val ext = MaterialTheme.blackseaColors

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("platform_teams_overview_tab"),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, ext.cardBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Platform Takımları Genel Durumu (${teams.size})",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Her takımın iç sohbetleri, dosyaları, fikirleri ve görevleri kendi çalışma alanında gizli tutulur. Burada yalnızca temel takım durumu görüntülenir.",
                        fontSize = 11.sp,
                        color = ext.textSecondary,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        itemsIndexed(
            items = teams,
            key = { index, team -> "overview_${team.id}_$index" }
        ) { _, team ->
            val (statusColor, statusBg) = when {
                team.isApproved() -> Pair(ext.emeraldText, ext.emeraldSolid.copy(alpha = 0.14f))
                team.isRejected() -> Pair(ext.dangerText, ext.dangerText.copy(alpha = 0.14f))
                else -> Pair(ext.amberText, ext.amberText.copy(alpha = 0.16f))
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, ext.cardBorder)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TeamLogoBadge(team = team, size = 44)
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = team.name,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (team.isMainTeam || team.id == PLATFORM_MAIN_TEAM_ID) {
                                    Icon(Icons.Default.Verified, contentDescription = "Ana Takım", tint = ext.accentBlue, modifier = Modifier.size(15.dp))
                                }
                            }
                            Text(
                                text = listOf(team.schoolName, team.city).filter { it.isNotBlank() }.joinToString(" • "),
                                fontSize = 11.sp,
                                color = ext.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Surface(shape = RoundedCornerShape(8.dp), color = statusBg) {
                            Text(
                                text = TeamStatus.displayName(team.status),
                                color = statusColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.LocationOn, contentDescription = null, tint = ext.accentBlue, modifier = Modifier.size(14.dp))
                            Text(text = team.competition.ifBlank { "Robotik" }, fontSize = 11.sp, color = ext.textSecondary)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Groups, contentDescription = null, tint = ext.textMuted, modifier = Modifier.size(14.dp))
                            Text(text = "${team.memberCount} Üye", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = ext.textSecondary)
                        }
                    }

                    if (team.managerName.isNotBlank() || team.managerContact.isNotBlank()) {
                        Text(
                            text = "Yönetici: ${team.managerName} (${team.managerContact})",
                            fontSize = 11.sp,
                            color = ext.textMuted
                        )
                    }

                    if (team.isRejected() && team.rejectionReason.isNotBlank()) {
                        Text(
                            text = "Red Gerekçesi: ${team.rejectionReason}",
                            fontSize = 11.sp,
                            color = ext.dangerText
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TeamInfoEditorTab(
    activeTeam: TeamInfo,
    repository: BlackseaRepository,
    onTeamDeleted: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ext = MaterialTheme.blackseaColors

    var name by remember(activeTeam.id) { mutableStateOf(activeTeam.name) }
    var shortCode by remember(activeTeam.id) { mutableStateOf(activeTeam.shortCode) }
    var competition by remember(activeTeam.id) { mutableStateOf(activeTeam.competition) }
    var schoolName by remember(activeTeam.id) { mutableStateOf(activeTeam.schoolName) }
    var city by remember(activeTeam.id) { mutableStateOf(activeTeam.city) }
    var description by remember(activeTeam.id) { mutableStateOf(activeTeam.description) }
    var motto by remember(activeTeam.id) { mutableStateOf(activeTeam.motto) }
    var logoKey by remember(activeTeam.id) { mutableStateOf(activeTeam.logoKey) }
    var logoUrl by remember(activeTeam.id) { mutableStateOf(activeTeam.logoUrl) }
    var accentColorHex by remember(activeTeam.id) { mutableStateOf(activeTeam.accentColorHex) }
    var managerName by remember(activeTeam.id) { mutableStateOf(activeTeam.managerName) }
    var managerContact by remember(activeTeam.id) { mutableStateOf(activeTeam.managerContact) }
    var isSaving by remember { mutableStateOf(false) }
    var isUploadingLogo by remember { mutableStateOf(false) }
    var showDeleteTeamStep1 by remember { mutableStateOf(false) }
    var showDeleteTeamStep2 by remember { mutableStateOf(false) }
    var isDeletingTeam by remember { mutableStateOf(false) }

    LaunchedEffect(activeTeam) {
        name = activeTeam.name
        shortCode = activeTeam.shortCode
        competition = activeTeam.competition
        schoolName = activeTeam.schoolName
        city = activeTeam.city
        description = activeTeam.description
        motto = activeTeam.motto
        logoKey = activeTeam.logoKey
        logoUrl = activeTeam.logoUrl
        accentColorHex = activeTeam.accentColorHex
        managerName = activeTeam.managerName
        managerContact = activeTeam.managerContact
    }

    val logoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            isUploadingLogo = true
            scope.launch {
                val res = repository.uploadTeamLogo(
                    context = context,
                    uri = uri,
                    teamHint = shortCode.ifBlank { activeTeam.id }
                )
                isUploadingLogo = false
                if (res.isSuccess) {
                    logoUrl = res.getOrNull().orEmpty()
                    Toast.makeText(context, "Logo yüklendi! Kaydetmek için aşağıdaki butona dokunun.", Toast.LENGTH_SHORT).show()
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

    val isMainTeam = activeTeam.isMainTeam || activeTeam.id == PLATFORM_MAIN_TEAM_ID
    val logoChoices = if (isMainTeam) TeamLogoPreset.entries else TeamLogoPreset.entries.filter { it != TeamLogoPreset.BSR_PHOENIX }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, ext.cardBorder)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TeamLogoBadge(
                        team = activeTeam.copy(
                            name = name,
                            logoKey = logoKey,
                            logoUrl = logoUrl,
                            accentColorHex = accentColorHex
                        ),
                        size = 52
                    )
                    Column {
                        Text(
                            text = "Takım Kimliği & Profili",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Burada yapılan değişiklikler takım merkezinde ve ana sayfada görünür.",
                            fontSize = 11.sp,
                            color = ext.textSecondary
                        )
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { if (!isMainTeam) name = it },
                    enabled = !isMainTeam,
                    label = { Text("Takım Adı") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = shortCode,
                        onValueChange = { if (!isMainTeam) shortCode = it.uppercase().take(10) },
                        enabled = !isMainTeam,
                        label = { Text("Kısa Kod") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = city,
                        onValueChange = { city = it },
                        label = { Text("Şehir") },
                        modifier = Modifier.weight(1.3f),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )
                }

                OutlinedTextField(
                    value = schoolName,
                    onValueChange = { schoolName = it },
                    label = { Text("Okul / Kurum") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )

                OutlinedTextField(
                    value = competition,
                    onValueChange = { competition = it },
                    label = { Text("Yarışma / Amaç") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    COMPETITION_PRESETS.forEach { preset ->
                        FilterChip(
                            selected = competition == preset,
                            onClick = { competition = preset },
                            label = { Text(preset, fontSize = 11.sp) }
                        )
                    }
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Takım Açıklaması") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = motto,
                    onValueChange = { motto = it },
                    label = { Text("Takım Sloganı / Motto") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )

                if (!isMainTeam) {
                    Text("Özel Takım Logosu", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ext.textSecondary)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                logoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            enabled = !isUploadingLogo && !isSaving,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("pick_team_logo_button")
                        ) {
                            if (isUploadingLogo) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(imageVector = Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (logoUrl.isNotBlank()) "Logoyu Değiştir" else "Özel Logo Yükle",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (logoUrl.isNotBlank()) {
                            TextButton(
                                onClick = { logoUrl = "" },
                                enabled = !isUploadingLogo && !isSaving
                            ) {
                                Icon(imageVector = Icons.Default.Delete, contentDescription = "Logoyu Kaldır", tint = ext.dangerText, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Logoyu Kaldır", color = ext.dangerText, fontSize = 12.sp)
                            }
                        }
                    }

                    Text("Takım Simgesi (Yedek İkon)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ext.textSecondary)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        logoChoices.forEach { preset ->
                            FilterChip(
                                selected = logoKey == preset.key,
                                onClick = { logoKey = preset.key },
                                leadingIcon = {
                                    Icon(
                                        imageVector = teamLogoPresetIcon(preset.key),
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp)
                                    )
                                },
                                label = { Text(preset.label, fontSize = 11.sp) }
                            )
                        }
                    }

                    Text("Vurgu Rengi", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ext.textSecondary)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TEAM_ACCENT_COLORS.forEach { hex ->
                            val color = parseTeamColor(hex)
                            val isSelected = accentColorHex.equals(hex, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.White.copy(alpha = 0.5f),
                                        shape = CircleShape
                                    )
                                    .clickable { accentColorHex = hex }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = managerName,
                    onValueChange = { managerName = it },
                    label = { Text("Takım Yöneticisi Adı") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )

                OutlinedTextField(
                    value = managerContact,
                    onValueChange = { managerContact = it },
                    label = { Text("Yönetici İletişim Bilgisi") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )

                Button(
                    onClick = {
                        isSaving = true
                        scope.launch {
                            try {
                                val res = repository.updateTeamDetails(
                                    teamId = activeTeam.id,
                                    name = name,
                                    shortCode = shortCode,
                                    competition = competition,
                                    schoolName = schoolName,
                                    city = city,
                                    description = description,
                                    motto = motto,
                                    logoKey = logoKey,
                                    logoUrl = logoUrl,
                                    accentColorHex = accentColorHex,
                                    managerName = managerName,
                                    managerContact = managerContact
                                )
                                if (res.isSuccess) {
                                    Toast.makeText(context, "Takım bilgileri güncellendi!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, sanitizeUserErrorMessage(res.exceptionOrNull()), Toast.LENGTH_LONG).show()
                                }
                            } catch (ce: CancellationException) {
                                throw ce
                            } finally {
                                isSaving = false
                            }
                        }
                    },
                    enabled = !isSaving && !isUploadingLogo && name.trim().length >= 2,
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ext.accentBlueSolid,
                        contentColor = Color.White
                    )
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Takım Bilgilerini Kaydet", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (!isMainTeam) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, ext.dangerText.copy(alpha = 0.35f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Takımı Kalıcı Olarak Sil / Kaldır",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = ext.dangerText
                    )
                    Text(
                        text = "${activeTeam.name} takımı ve tüm mesajlar, dosyalar, fikirler, görevler, duyurular ve üye kayıtları geri alınamaz şekilde silinir.",
                        fontSize = 12.sp,
                        color = ext.textSecondary,
                        lineHeight = 17.sp
                    )
                    OutlinedButton(
                        onClick = { showDeleteTeamStep1 = true },
                        enabled = !isDeletingTeam && !isSaving,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 46.dp)
                            .testTag("delete_team_button"),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.2.dp, ext.dangerText.copy(alpha = 0.6f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ext.dangerText)
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Takımı Kalıcı Olarak Sil", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showDeleteTeamStep1) {
        AlertDialog(
            onDismissRequest = { showDeleteTeamStep1 = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.DeleteForever,
                    contentDescription = null,
                    tint = ext.dangerText,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text("${activeTeam.name} Takımını Silmek İstiyor musunuz?", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "Bu işlem takımı, sohbet odalarını, mesajları, dosyaları, fikirleri, görevleri ve tüm üyelik kayıtlarını siler. Yanlışlıkla silmeyi önlemek için bir sonraki adımda son onay istenecektir.",
                    fontSize = 13.sp,
                    color = ext.textSecondary,
                    lineHeight = 19.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteTeamStep1 = false
                        showDeleteTeamStep2 = true
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ext.dangerSolid,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("delete_team_step1_confirm")
                ) {
                    Text("Devam Et", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteTeamStep1 = false }) {
                    Text("Vazgeç")
                }
            }
        )
    }

    if (showDeleteTeamStep2) {
        AlertDialog(
            onDismissRequest = { if (!isDeletingTeam) showDeleteTeamStep2 = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.DeleteForever,
                    contentDescription = null,
                    tint = ext.dangerText,
                    modifier = Modifier.size(34.dp)
                )
            },
            title = {
                Text(
                    "Son Onay: Takım Kalıcı Olarak Silinecek",
                    fontWeight = FontWeight.Bold,
                    color = ext.dangerText
                )
            },
            text = {
                Text(
                    "Bu işlem geri alınamaz! ${activeTeam.name} çalışma alanı tamamen kapatılacak ve tüm veriler kalıcı olarak silinecektir. Emin misiniz?",
                    fontSize = 13.sp,
                    color = ext.textSecondary,
                    lineHeight = 19.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        isDeletingTeam = true
                        scope.launch {
                            try {
                                val res = repository.deleteTeamCompletely(activeTeam.id)
                                if (res.isSuccess) {
                                    showDeleteTeamStep2 = false
                                    Toast.makeText(
                                        context,
                                        "${activeTeam.name} takımı ve tüm verileri kalıcı olarak silindi.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    onTeamDeleted()
                                } else {
                                    Toast.makeText(
                                        context,
                                        sanitizeUserErrorMessage(res.exceptionOrNull(), "Takım silinemedi."),
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            } catch (ce: CancellationException) {
                                throw ce
                            } finally {
                                isDeletingTeam = false
                            }
                        }
                    },
                    enabled = !isDeletingTeam,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ext.dangerSolid,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("delete_team_step2_confirm")
                ) {
                    if (isDeletingTeam) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Evet, Takımı Sil", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteTeamStep2 = false },
                    enabled = !isDeletingTeam
                ) {
                    Text("İptal")
                }
            }
        )
    }
}
