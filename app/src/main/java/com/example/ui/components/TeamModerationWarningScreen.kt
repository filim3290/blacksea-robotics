package com.example.ui.components

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BlackseaRepository
import com.example.model.TeamAppealStatus
import com.example.model.TeamInfo
import com.example.model.TeamStatus
import com.example.model.UserProfile
import com.example.ui.theme.blackseaColors
import com.example.util.sanitizeUserErrorMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun TeamModerationWarningScreen(
    team: TeamInfo,
    repository: BlackseaRepository,
    currentUserProfile: UserProfile?,
    onBackToHub: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ext = MaterialTheme.blackseaColors

    BackHandler { onBackToHub() }

    val isSuspended = team.isSuspended()
    val statusColor = if (isSuspended) ext.dangerText else ext.amberText
    val statusContainerColor = if (isSuspended) {
        ext.dangerContainer
    } else {
        ext.amberText.copy(alpha = if (ext.isDark) 0.18f else 0.12f)
    }
    val statusIcon = if (isSuspended) Icons.Default.Block else Icons.Default.ReportProblem
    val statusTitle = if (isSuspended) {
        "Çalışma Alanı Durduruldu"
    } else {
        "Takım İncelemeye Alındı"
    }
    val statusSubtitle = if (isSuspended) {
        "${team.name} çalışma alanı BlackSea Robotics ana yöneticisi tarafından geçici olarak durduruldu."
    } else {
        "${team.name} çalışma alanı şu anda BlackSea Robotics ana yöneticisi tarafından inceleme sürecine alındı."
    }

    val isAppealPending = team.appealStatus.equals(TeamAppealStatus.PENDING, ignoreCase = true)
    val isAppealRejected = team.appealStatus.equals(TeamAppealStatus.REJECTED, ignoreCase = true)
    val appealBadgeLabel = TeamAppealStatus.displayName(team.appealStatus)

    var showAppealDialog by remember { mutableStateOf(false) }
    var appealText by remember(team.id, team.appealText) {
        mutableStateOf("")
    }
    var isSubmittingAppeal by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp)
            .testTag("team_moderation_warning_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 540.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.2.dp, statusColor.copy(alpha = 0.45f)),
                elevation = CardDefaults.cardElevation(defaultElevation = if (ext.isDark) 4.dp else 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = statusContainerColor,
                        modifier = Modifier.size(68.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = statusIcon,
                                contentDescription = statusTitle,
                                tint = statusColor,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TeamLogoBadge(team = team, size = 34)
                        Text(
                            text = team.name,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = statusContainerColor
                        ) {
                            Text(
                                text = TeamStatus.displayName(team.status),
                                color = statusColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Text(
                        text = statusTitle,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = statusColor,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = statusSubtitle,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = ext.textSecondary,
                        textAlign = TextAlign.Center
                    )

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, ext.cardBorder)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Yönetim Gerekçesi / Açıklama",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ext.textMuted
                            )
                            Text(
                                text = team.moderationReason.ifBlank {
                                    if (isSuspended) {
                                        "Takım çalışma alanı platform kuralları veya güvenlik gerekçesiyle geçici olarak durdurulmuştur. Yeniden açılması için itiraz gönderebilirsiniz."
                                    } else {
                                        "Takım çalışma alanı platform yönetimi tarafından incelenmektedir. İnceleme tamamlanana kadar sohbet, dosya ve görev alanları kilitlidir."
                                    }
                                },
                                fontSize = 13.sp,
                                lineHeight = 19.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    if (appealBadgeLabel.isNotBlank() || team.appealText.isNotBlank()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = if (isAppealRejected) {
                                ext.dangerContainer
                            } else {
                                ext.accentBlueContainer.copy(alpha = if (ext.isDark) 0.45f else 0.7f)
                            },
                            border = BorderStroke(
                                1.dp,
                                if (isAppealRejected) ext.dangerText.copy(alpha = 0.35f) else ext.accentBlue.copy(alpha = 0.35f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.HourglassTop,
                                        contentDescription = null,
                                        tint = if (isAppealRejected) ext.dangerText else ext.accentBlue,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = appealBadgeLabel.ifBlank { "Gönderilen İtiraz" },
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isAppealRejected) ext.dangerText else ext.accentBlue
                                    )
                                }
                                if (team.appealByName.isNotBlank()) {
                                    Text(
                                        text = "Gönderen: ${team.appealByName}",
                                        fontSize = 11.sp,
                                        color = ext.textMuted
                                    )
                                }
                                if (team.appealText.isNotBlank()) {
                                    Text(
                                        text = team.appealText,
                                        fontSize = 12.5.sp,
                                        lineHeight = 18.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = ext.cardBorder)

                    Button(
                        onClick = { showAppealDialog = true },
                        enabled = !isAppealPending && !isSubmittingAppeal,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("appeal_team_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ext.accentBlueSolid,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Gavel,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when {
                                isAppealPending -> "İtiraz İnceleniyor"
                                isAppealRejected -> "Yeniden İtiraz Et"
                                else -> "İtiraz Et"
                            },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedButton(
                        onClick = onBackToHub,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("return_to_hub_button"),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, ext.cardBorder)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Takımlar Merkezine Dön",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }

    if (showAppealDialog) {
        val trimmedAppeal = appealText.trim()
        val isAppealValid = trimmedAppeal.length >= 10
        AlertDialog(
            onDismissRequest = { if (!isSubmittingAppeal) showAppealDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Gavel,
                    contentDescription = null,
                    tint = ext.accentBlue,
                    modifier = Modifier.size(30.dp)
                )
            },
            title = {
                Text(
                    text = "${team.name} İçin İtiraz Gönder",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Çalışma alanının yeniden açılması için gerekçenizi veya aldığınız önlemleri BlackSea Robotics ana yöneticisine iletin.",
                        fontSize = 12.sp,
                        color = ext.textSecondary,
                        lineHeight = 18.sp
                    )
                    OutlinedTextField(
                        value = appealText,
                        onValueChange = { if (it.length <= 1000) appealText = it },
                        label = { Text("İtiraz Açıklaması (En az 10 karakter)") },
                        placeholder = { Text("Durumla ilgili açıklamanızı ve düzeltme adımlarını yazın...") },
                        minLines = 3,
                        maxLines = 6,
                        shape = RoundedCornerShape(12.dp),
                        isError = appealText.isNotBlank() && !isAppealValid,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("team_appeal_input")
                    )
                    Text(
                        text = "${trimmedAppeal.length}/1000",
                        fontSize = 11.sp,
                        color = ext.textMuted,
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isSubmittingAppeal = true
                        val appellantName = currentUserProfile?.getFullName()?.ifBlank { null } ?: "Takım Üyesi"
                        scope.launch {
                            try {
                                val res = repository.submitTeamAppeal(
                                    team = team,
                                    appellantName = appellantName,
                                    appealText = trimmedAppeal
                                )
                                if (res.isSuccess) {
                                    showAppealDialog = false
                                    appealText = ""
                                    Toast.makeText(
                                        context,
                                        "İtirazınız BlackSea Robotics ana yöneticisine gönderildi.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } else {
                                    Toast.makeText(
                                        context,
                                        sanitizeUserErrorMessage(res.exceptionOrNull(), "İtiraz gönderilemedi."),
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            } catch (ce: CancellationException) {
                                throw ce
                            } finally {
                                isSubmittingAppeal = false
                            }
                        }
                    },
                    enabled = isAppealValid && !isSubmittingAppeal,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ext.accentBlueSolid,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("submit_team_appeal_confirm")
                ) {
                    if (isSubmittingAppeal) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("İtirazı Gönder", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAppealDialog = false },
                    enabled = !isSubmittingAppeal
                ) {
                    Text("Vazgeç")
                }
            }
        )
    }
}
