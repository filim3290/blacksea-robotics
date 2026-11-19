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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.BlackseaRepository
import com.example.model.COMPETITION_PRESETS
import com.example.model.PLATFORM_MAIN_TEAM_ID
import com.example.model.TEAM_ACCENT_COLORS
import com.example.model.TeamAppealStatus
import com.example.model.TeamInfo
import com.example.model.TeamLogoPreset
import com.example.model.TeamMembership
import com.example.model.TeamStatus
import com.example.model.TeamType
import com.example.ui.components.RoleBadge
import com.example.ui.components.TeamBadge
import com.example.ui.components.TeamLogoBadge
import com.example.ui.components.parseTeamColor
import com.example.ui.components.teamLogoPresetIcon
import com.example.ui.theme.blackseaColors
import com.example.util.sanitizeUserErrorMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.Locale

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

private enum class PlatformTeamModerationAction {
    SUSPEND,
    UNDER_REVIEW
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlatformTeamsOverviewTab(
    teams: List<TeamInfo>,
    repository: BlackseaRepository
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ext = MaterialTheme.blackseaColors

    var selectedTeamForMembers by remember { mutableStateOf<TeamInfo?>(null) }
    var moderationTarget by remember { mutableStateOf<Pair<TeamInfo, PlatformTeamModerationAction>?>(null) }
    var moderationReasonInput by remember { mutableStateOf("") }
    var teamToDeleteStep1 by remember { mutableStateOf<TeamInfo?>(null) }
    var teamToDeleteStep2 by remember { mutableStateOf<TeamInfo?>(null) }
    var processingTeamId by remember { mutableStateOf<String?>(null) }

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
                        text = "Platform Takımları Yönetimi (${teams.size})",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Üyelerini görmek istediğiniz takımın üzerine dokunun; takımların sohbet ve dosya içerikleri gizli kalırken üyeleri görüntüleyebilir, takımı durdurabilir, incelemeye alabilir, başlatabilir veya kaldırabilirsiniz.",
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
            val isMain = team.isMainTeam || team.id == PLATFORM_MAIN_TEAM_ID
            val isBusy = processingTeamId == team.id
            val isAppealPending = team.appealStatus.equals(TeamAppealStatus.PENDING, ignoreCase = true)

            val (statusColor, statusBg) = when {
                team.isApproved() -> Pair(ext.emeraldText, ext.emeraldSolid.copy(alpha = 0.14f))
                team.isSuspended() || team.isRejected() -> Pair(ext.dangerText, ext.dangerText.copy(alpha = 0.14f))
                else -> Pair(ext.amberText, ext.amberText.copy(alpha = 0.16f))
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { selectedTeamForMembers = team }
                    .testTag("platform_team_card_${team.id}"),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(
                    width = if (isAppealPending) 1.5.dp else 1.dp,
                    color = when {
                        isAppealPending -> ext.accentBlue.copy(alpha = 0.6f)
                        team.isSuspended() -> ext.dangerText.copy(alpha = 0.4f)
                        team.isUnderReview() -> ext.amberText.copy(alpha = 0.45f)
                        else -> ext.cardBorder
                    }
                )
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
                                if (isMain) {
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
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = ext.accentBlueContainer.copy(alpha = if (ext.isDark) 0.55f else 0.75f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.Groups, contentDescription = null, tint = ext.accentBlue, modifier = Modifier.size(14.dp))
                                Text(
                                    text = "${team.memberCount} Üye • Üyeleri Gör",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ext.accentBlue
                                )
                                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = ext.accentBlue, modifier = Modifier.size(14.dp))
                            }
                        }
                    }

                    if (team.managerName.isNotBlank() || team.managerContact.isNotBlank()) {
                        Text(
                            text = "Yetkili Öğretmen / Kaptan: ${team.managerName} (${team.managerContact})",
                            fontSize = 11.sp,
                            color = ext.textMuted
                        )
                    }

                    if (team.moderationReason.isNotBlank()) {
                        Text(
                            text = "Moderasyon Gerekçesi: ${team.moderationReason}",
                            fontSize = 11.sp,
                            color = statusColor,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    if (team.isRejected() && team.rejectionReason.isNotBlank()) {
                        Text(
                            text = "Red Gerekçesi: ${team.rejectionReason}",
                            fontSize = 11.sp,
                            color = ext.dangerText
                        )
                    }

                    if (team.appealText.isNotBlank() || isAppealPending) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = ext.accentBlueContainer.copy(alpha = if (ext.isDark) 0.45f else 0.7f),
                            border = BorderStroke(1.dp, ext.accentBlue.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.Gavel, contentDescription = null, tint = ext.accentBlue, modifier = Modifier.size(15.dp))
                                    Text(
                                        text = TeamAppealStatus.displayName(team.appealStatus).ifBlank { "Takım İtirazı" },
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ext.accentBlue
                                    )
                                }
                                if (team.appealByName.isNotBlank()) {
                                    Text(
                                        text = "Gönderen: ${team.appealByName}",
                                        fontSize = 10.sp,
                                        color = ext.textMuted
                                    )
                                }
                                if (team.appealText.isNotBlank()) {
                                    Text(
                                        text = team.appealText,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }
                    }

                    if (!isMain && !team.isPending()) {
                        HorizontalDivider(color = ext.cardBorder)
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (!team.isApproved()) {
                                Button(
                                    onClick = {
                                        processingTeamId = team.id
                                        scope.launch {
                                            try {
                                                val res = repository.resumeTeamWorkspace(team)
                                                if (res.isSuccess) {
                                                    Toast.makeText(context, "${team.name} çalışma alanı yeniden başlatıldı.", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(context, sanitizeUserErrorMessage(res.exceptionOrNull()), Toast.LENGTH_LONG).show()
                                                }
                                            } catch (ce: CancellationException) {
                                                throw ce
                                            } finally {
                                                processingTeamId = null
                                            }
                                        }
                                    },
                                    enabled = !isBusy,
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = ext.emeraldSolid,
                                        contentColor = Color.White
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .heightIn(min = 38.dp)
                                        .testTag("resume_team_${team.id}")
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Başlat", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            if (!team.isSuspended()) {
                                OutlinedButton(
                                    onClick = {
                                        moderationReasonInput = ""
                                        moderationTarget = team to PlatformTeamModerationAction.SUSPEND
                                    },
                                    enabled = !isBusy,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, ext.dangerText.copy(alpha = 0.5f)),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .heightIn(min = 38.dp)
                                        .testTag("suspend_team_${team.id}")
                                ) {
                                    Icon(Icons.Default.PauseCircle, contentDescription = null, tint = ext.dangerText, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Durdur", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = ext.dangerText)
                                }
                            }

                            if (!team.isUnderReview()) {
                                OutlinedButton(
                                    onClick = {
                                        moderationReasonInput = ""
                                        moderationTarget = team to PlatformTeamModerationAction.UNDER_REVIEW
                                    },
                                    enabled = !isBusy,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, ext.amberText.copy(alpha = 0.55f)),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .heightIn(min = 38.dp)
                                        .testTag("review_team_${team.id}")
                                ) {
                                    Icon(Icons.Default.ReportProblem, contentDescription = null, tint = ext.amberText, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("İncelemeye Al", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = ext.amberText)
                                }
                            }

                            if (isAppealPending) {
                                OutlinedButton(
                                    onClick = {
                                        processingTeamId = team.id
                                        scope.launch {
                                            try {
                                                val res = repository.dismissTeamAppeal(team)
                                                if (res.isSuccess) {
                                                    Toast.makeText(context, "${team.name} itirazı reddedildi.", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(context, sanitizeUserErrorMessage(res.exceptionOrNull()), Toast.LENGTH_LONG).show()
                                                }
                                            } catch (ce: CancellationException) {
                                                throw ce
                                            } finally {
                                                processingTeamId = null
                                            }
                                        }
                                    },
                                    enabled = !isBusy,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, ext.cardBorder),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.heightIn(min = 38.dp)
                                ) {
                                    Icon(Icons.Default.Block, contentDescription = null, tint = ext.textSecondary, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("İtirazı Reddet", fontSize = 12.sp, color = ext.textSecondary)
                                }
                            }

                            OutlinedButton(
                                onClick = { teamToDeleteStep1 = team },
                                enabled = !isBusy,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, ext.dangerText.copy(alpha = 0.5f)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .heightIn(min = 38.dp)
                                    .testTag("remove_platform_team_${team.id}")
                            ) {
                                Icon(Icons.Default.DeleteForever, contentDescription = null, tint = ext.dangerText, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Kaldır", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ext.dangerText)
                            }
                        }
                    }
                }
            }
        }
    }

    moderationTarget?.let { (targetTeam, action) ->
        val isSuspend = action == PlatformTeamModerationAction.SUSPEND
        val isBusy = processingTeamId == targetTeam.id
        AlertDialog(
            onDismissRequest = { if (!isBusy) moderationTarget = null },
            icon = {
                Icon(
                    imageVector = if (isSuspend) Icons.Default.PauseCircle else Icons.Default.ReportProblem,
                    contentDescription = null,
                    tint = if (isSuspend) ext.dangerText else ext.amberText,
                    modifier = Modifier.size(30.dp)
                )
            },
            title = {
                Text(
                    text = if (isSuspend) "${targetTeam.name} Çalışma Alanını Durdur" else "${targetTeam.name} Takımını İncelemeye Al",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (isSuspend) {
                            "Bu takımın sohbet, dosya, fikir ve görev alanı durdurulacaktır. Takım üyelerine uyarı ve itiraz ekranı gösterilir."
                        } else {
                            "Bu takım inceleme moduna alınacaktır. İnceleme tamamlanana kadar çalışma alanı kapatılır ve üyeler itiraz gönderebilir."
                        },
                        fontSize = 12.sp,
                        color = ext.textSecondary,
                        lineHeight = 18.sp
                    )
                    OutlinedTextField(
                        value = moderationReasonInput,
                        onValueChange = { if (it.length <= 500) moderationReasonInput = it },
                        label = { Text("Gerekçe / Açıklama (İsteğe Bağlı)") },
                        placeholder = { Text("Takıma gösterilecek açıklama...") },
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
                        processingTeamId = targetTeam.id
                        scope.launch {
                            try {
                                val res = if (isSuspend) {
                                    repository.suspendTeamWorkspace(targetTeam, moderationReasonInput)
                                } else {
                                    repository.markTeamUnderReview(targetTeam, moderationReasonInput)
                                }
                                if (res.isSuccess) {
                                    moderationTarget = null
                                    Toast.makeText(
                                        context,
                                        if (isSuspend) "${targetTeam.name} durduruldu." else "${targetTeam.name} incelemeye alındı.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    Toast.makeText(context, sanitizeUserErrorMessage(res.exceptionOrNull()), Toast.LENGTH_LONG).show()
                                }
                            } catch (ce: CancellationException) {
                                throw ce
                            } finally {
                                processingTeamId = null
                            }
                        }
                    },
                    enabled = !isBusy,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSuspend) ext.dangerSolid else ext.amberText,
                        contentColor = Color.White
                    )
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text(if (isSuspend) "Durdur" else "İncelemeye Al", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { moderationTarget = null }, enabled = !isBusy) {
                    Text("Vazgeç")
                }
            }
        )
    }

    teamToDeleteStep1?.let { targetTeam ->
        AlertDialog(
            onDismissRequest = { teamToDeleteStep1 = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.DeleteForever,
                    contentDescription = null,
                    tint = ext.dangerText,
                    modifier = Modifier.size(30.dp)
                )
            },
            title = { Text("${targetTeam.name} Takımını Kaldır", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "${targetTeam.name} platformdan kaldırılacak ve tüm takım üyelikleri silinecektir. Sonraki adımda kalıcı silme onayı istenecektir.",
                    fontSize = 13.sp,
                    color = ext.textSecondary,
                    lineHeight = 19.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        teamToDeleteStep1 = null
                        teamToDeleteStep2 = targetTeam
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ext.dangerSolid, contentColor = Color.White)
                ) {
                    Text("Devam Et", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { teamToDeleteStep1 = null }) {
                    Text("Vazgeç")
                }
            }
        )
    }

    teamToDeleteStep2?.let { targetTeam ->
        val isDeleting = processingTeamId == targetTeam.id
        AlertDialog(
            onDismissRequest = { if (!isDeleting) teamToDeleteStep2 = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.DeleteForever,
                    contentDescription = null,
                    tint = ext.dangerText,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Son Onay: Takımı Kaldır",
                    fontWeight = FontWeight.Bold,
                    color = ext.dangerText
                )
            },
            text = {
                Text(
                    text = "Bu işlem geri alınamaz! ${targetTeam.name} kalıcı olarak platformdan kaldırılacaktır. Emin misiniz?",
                    fontSize = 13.sp,
                    color = ext.textSecondary,
                    lineHeight = 19.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        processingTeamId = targetTeam.id
                        scope.launch {
                            try {
                                val res = repository.deleteTeamCompletely(targetTeam.id)
                                if (res.isSuccess) {
                                    teamToDeleteStep2 = null
                                    Toast.makeText(context, "${targetTeam.name} kalıcı olarak kaldırıldı.", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(
                                        context,
                                        sanitizeUserErrorMessage(res.exceptionOrNull(), "Takım kaldırılamadı."),
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
                    enabled = !isDeleting,
                    colors = ButtonDefaults.buttonColors(containerColor = ext.dangerSolid, contentColor = Color.White)
                ) {
                    if (isDeleting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Evet, Kaldır", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { teamToDeleteStep2 = null }, enabled = !isDeleting) {
                    Text("İptal")
                }
            }
        )
    }

    selectedTeamForMembers?.let { inspectedTeam ->
        TeamMembersInspectionDialog(
            team = inspectedTeam,
            repository = repository,
            onDismiss = { selectedTeamForMembers = null }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TeamMembersInspectionDialog(
    team: TeamInfo,
    repository: BlackseaRepository,
    onDismiss: () -> Unit
) {
    val ext = MaterialTheme.blackseaColors
    val members by remember(team.id) {
        repository.observeTeamMemberships(team.id)
    }.collectAsState(initial = emptyList())

    var searchQuery by remember(team.id) { mutableStateOf("") }
    var statusFilter by remember(team.id) { mutableStateOf("all") }

    val activeCount = remember(members) { members.count { it.isActive() } }
    val pendingCount = remember(members) { members.count { it.isPending() } }
    val restrictedCount = remember(members) { members.count { it.isRevoked() || it.isRejected() } }

    val filteredMembers = remember(members, searchQuery, statusFilter) {
        val q = searchQuery.trim().lowercase(Locale("tr", "TR"))
        members.filter { m ->
            val matchesStatus = when (statusFilter) {
                "active" -> m.isActive()
                "pending" -> m.isPending()
                "restricted" -> m.isRevoked() || m.isRejected()
                else -> true
            }
            val matchesQuery = q.isBlank() ||
                m.userName.lowercase(Locale("tr", "TR")).contains(q) ||
                m.userEmail.lowercase(Locale("tr", "TR")).contains(q) ||
                m.subTeam.lowercase(Locale("tr", "TR")).contains(q) ||
                m.role.lowercase(Locale("tr", "TR")).contains(q) ||
                m.roles.any { it.lowercase(Locale("tr", "TR")).contains(q) }
            matchesStatus && matchesQuery
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 600.dp)
                .heightIn(min = 320.dp, max = 680.dp)
                .testTag("team_members_inspection_dialog_${team.id}"),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.background,
            border = BorderStroke(1.dp, ext.cardBorder),
            tonalElevation = 6.dp,
            shadowElevation = 10.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TeamLogoBadge(team = team, size = 40)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${team.name} • Üyeler (${members.size})",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = listOf(team.schoolName, team.city, TeamStatus.displayName(team.status))
                                .filter { it.isNotBlank() }
                                .joinToString(" • "),
                            fontSize = 11.sp,
                            color = ext.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_team_members_inspection_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Kapat",
                            tint = ext.textMuted
                        )
                    }
                }

                HorizontalDivider(color = ext.cardBorder)

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Üye adı, e-posta, alt ekip veya rol ara...", fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Search, contentDescription = "Üye Ara", tint = ext.textMuted)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "Temizle")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .testTag("inspect_team_members_search_input")
                )

                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val chips = listOf(
                        "all" to "Tümü (${members.size})",
                        "active" to "Aktif ($activeCount)",
                        "pending" to "Bekleyen ($pendingCount)",
                        "restricted" to "Askıda/Red ($restrictedCount)"
                    )
                    items(chips) { (key, label) ->
                        FilterChip(
                            selected = statusFilter == key,
                            onClick = { statusFilter = key },
                            label = { Text(label, fontSize = 11.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                if (filteredMembers.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Groups,
                                contentDescription = null,
                                tint = ext.textMuted,
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = if (members.isEmpty()) {
                                    "Bu takımda henüz kayıtlı üye bulunmuyor."
                                } else {
                                    "Arama veya filtreyle eşleşen üye yok."
                                },
                                color = ext.textMuted,
                                fontSize = 13.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(
                            items = filteredMembers,
                            key = { index, member -> "inspect_${team.id}_${member.userId}_$index" }
                        ) { _, member ->
                            val displayName = member.userName.ifBlank {
                                member.userEmail.substringBefore("@").ifBlank { "Takım Üyesi" }
                            }
                            val memberRoles = member.roles
                                .ifEmpty { listOf(member.role) }
                                .filter { it.isNotBlank() }
                                .distinct()
                            val (badgeText, badgeFg, badgeBg) = when {
                                member.isActive() -> Triple("Aktif", ext.emeraldText, ext.emeraldSolid.copy(alpha = 0.14f))
                                member.isPending() -> Triple("Onay Bekliyor", ext.amberText, ext.amberText.copy(alpha = 0.16f))
                                member.isRevoked() -> Triple("Askıda", ext.dangerText, ext.dangerText.copy(alpha = 0.14f))
                                else -> Triple("Reddedildi", ext.dangerText, ext.dangerText.copy(alpha = 0.14f))
                            }
                            val isFounderOfTeam = team.founderId.isNotBlank() && member.userId == team.founderId

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, ext.cardBorder)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = ext.accentBlueContainer,
                                            modifier = Modifier.size(38.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = displayName.take(1).uppercase(),
                                                    fontWeight = FontWeight.Bold,
                                                    color = ext.accentBlue,
                                                    fontSize = 15.sp
                                                )
                                            }
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = displayName,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (member.userEmail.isNotBlank()) {
                                                Text(
                                                    text = member.userEmail,
                                                    fontSize = 11.sp,
                                                    color = ext.textMuted,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        Surface(shape = RoundedCornerShape(8.dp), color = badgeBg) {
                                            Text(
                                                text = badgeText,
                                                color = badgeFg,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }

                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        TeamBadge(teamName = member.subTeam.ifBlank { TeamType.GENERAL.displayName })
                                        if (isFounderOfTeam) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = ext.accentBlueContainer
                                            ) {
                                                Text(
                                                    text = "Kurucu",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = ext.accentBlue,
                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                        memberRoles.forEach { role ->
                                            RoleBadge(role = role)
                                        }
                                    }

                                    if (member.joinNote.isNotBlank() && member.isPending()) {
                                        Text(
                                            text = "Başvuru Notu: ${member.joinNote}",
                                            fontSize = 11.sp,
                                            color = ext.textSecondary
                                        )
                                    }
                                }
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
