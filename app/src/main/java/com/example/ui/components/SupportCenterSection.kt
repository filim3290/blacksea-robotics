package com.example.ui.components

import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.BlackseaRepository
import com.example.model.SupportCategories
import com.example.model.SupportFaqData
import com.example.model.SupportTicket
import com.example.model.SupportTicketStatus
import com.example.model.UserProfile
import com.example.ui.theme.blackseaColors
import com.example.update.AppUpdateManager
import com.example.util.sanitizeUserErrorMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun SupportCenterButtonCard(
    currentUserProfile: UserProfile?,
    repository: BlackseaRepository?,
    updateManager: AppUpdateManager?,
    teamId: String = com.example.model.PLATFORM_MAIN_TEAM_ID,
    teamName: String = "BlackSea Robotics",
    modifier: Modifier = Modifier
) {
    val ext = MaterialTheme.blackseaColors
    var showSupportModal by remember { mutableStateOf(false) }

    val uid = currentUserProfile?.userId?.ifBlank { currentUserProfile.id }.orEmpty()
    val myTickets by remember(uid, repository, teamId) {
        if (repository == null || uid.isBlank()) flowOf(emptyList())
        else repository.observeMySupportTickets(uid, teamId = teamId)
    }.collectAsState(initial = emptyList())

    val openTicketsCount = remember(myTickets) {
        myTickets.count { SupportTicketStatus.normalize(it.status) != SupportTicketStatus.RESOLVED }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clickable { showSupportModal = true }
            .testTag("open_support_center_button"),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, ext.cardBorder),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = if (ext.isDark) 2.dp else 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = CircleShape,
                    color = ext.accentBlueContainer,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.SupportAgent,
                            contentDescription = "Yardım ve Destek",
                            tint = ext.accentBlue,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                }
                Column {
                    Text(
                        text = "Yardım & Destek Merkezi",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Hazır çözümler, SSS ve yönetici destek formu",
                        fontSize = 11.sp,
                        color = ext.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (openTicketsCount > 0) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = ext.amberText.copy(alpha = 0.16f)
                    ) {
                        Text(
                            text = "$openTicketsCount Açık",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = ext.amberText,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Destek Merkezini Aç",
                    tint = ext.textMuted,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }

    if (showSupportModal) {
        Dialog(
            onDismissRequest = { showSupportModal = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.94f)
                    .widthIn(max = 560.dp)
                    .heightIn(max = 680.dp)
                    .testTag("support_center_modal"),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.background,
                border = BorderStroke(1.dp, ext.cardBorder),
                tonalElevation = 6.dp,
                shadowElevation = 10.dp
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 18.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SupportAgent,
                                contentDescription = null,
                                tint = ext.accentBlue,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = "Yardım & Destek Merkezi",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(
                            onClick = { showSupportModal = false },
                            modifier = Modifier.testTag("close_support_modal_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Kapat",
                                tint = ext.textMuted
                            )
                        }
                    }

                    HorizontalDivider(color = ext.cardBorder)

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(14.dp)
                    ) {
                        HelpAndSupportCenterCard(
                            currentUserProfile = currentUserProfile,
                            repository = repository,
                            updateManager = updateManager,
                            teamId = teamId,
                            teamName = teamName
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HelpAndSupportCenterCard(
    currentUserProfile: UserProfile?,
    repository: BlackseaRepository?,
    updateManager: AppUpdateManager?,
    teamId: String = com.example.model.PLATFORM_MAIN_TEAM_ID,
    teamName: String = "BlackSea Robotics",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val ext = MaterialTheme.blackseaColors
    val scope = rememberCoroutineScope()

    val uid = currentUserProfile?.userId?.ifBlank { currentUserProfile.id }.orEmpty()
    val myTickets by remember(uid, repository, teamId) {
        if (repository == null || uid.isBlank()) flowOf(emptyList())
        else repository.observeMySupportTickets(uid, teamId = teamId)
    }.collectAsState(initial = emptyList())

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Tümü") }
    var expandedFaqId by remember { mutableStateOf<String?>(SupportFaqData.ITEMS.firstOrNull()?.id) }
    var showTicketFormDialog by remember { mutableStateOf(false) }

    val filteredFaqs = remember(searchQuery, selectedCategory) {
        val query = searchQuery.trim().lowercase(Locale("tr", "TR"))
        SupportFaqData.ITEMS.filter { item ->
            val matchesCat = selectedCategory == "Tümü" || item.category == selectedCategory
            val matchesQuery = query.isBlank() ||
                item.question.lowercase(Locale("tr", "TR")).contains(query) ||
                item.answer.lowercase(Locale("tr", "TR")).contains(query) ||
                item.category.lowercase(Locale("tr", "TR")).contains(query)
            matchesCat && matchesQuery
        }
    }

    val openTicketsCount = remember(myTickets) {
        myTickets.count { SupportTicketStatus.normalize(it.status) != SupportTicketStatus.RESOLVED }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("support_center_card"),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, ext.cardBorder),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = if (ext.isDark) 2.dp else 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Section Title
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = ext.accentBlueContainer,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.SupportAgent,
                                contentDescription = null,
                                tint = ext.accentBlue,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Yardım & Destek Merkezi",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Sık sorulan sorunlar ve yönetici destek formu",
                            fontSize = 11.sp,
                            color = ext.textMuted
                        )
                    }
                }

                if (openTicketsCount > 0) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = ext.amberText.copy(alpha = 0.16f)
                    ) {
                        Text(
                            text = "$openTicketsCount Açık Talep",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = ext.amberText,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // FAQ Search
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Sorun veya konu ara (ör. bildirim, güncelleme)...", fontSize = 12.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "SSS Ara",
                        tint = ext.textMuted
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Aramayı Temizle")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("support_faq_search_input")
            )

            // Category Filter Chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(SupportFaqData.CATEGORIES) { cat ->
                    val selected = selectedCategory == cat
                    FilterChip(
                        selected = selected,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ext.accentBlueContainer,
                            selectedLabelColor = ext.accentBlue
                        )
                    )
                }
            }

            // Expandable FAQ Accordion List
            if (filteredFaqs.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, ext.cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(imageVector = Icons.Default.HelpOutline, contentDescription = null, tint = ext.textMuted)
                        Text(
                            text = "Aramanızla eşleşen hazır çözüm bulunamadı.",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Aşağıdaki destek formunu doldurarak doğrudan yöneticiye iletebilirsiniz.",
                            fontSize = 11.sp,
                            color = ext.textSecondary
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    filteredFaqs.forEach { faq ->
                        val isExpanded = expandedFaqId == faq.id
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isExpanded) ext.accentBlueContainer.copy(alpha = if (ext.isDark) 0.25f else 0.45f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (ext.isDark) 0.45f else 0.65f),
                            border = BorderStroke(
                                1.dp,
                                if (isExpanded) ext.accentBlue.copy(alpha = 0.4f) else ext.cardBorder
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    expandedFaqId = if (isExpanded) null else faq.id
                                }
                                .testTag("faq_item_${faq.id}")
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = faq.category,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = ext.accentBlue
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = faq.question,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Icon(
                                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = if (isExpanded) "Daralt" else "Genişlet",
                                        tint = ext.textMuted
                                    )
                                }
                                AnimatedVisibility(visible = isExpanded) {
                                    Column {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        HorizontalDivider(color = ext.cardBorder)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = faq.answer,
                                            fontSize = 12.sp,
                                            lineHeight = 18.sp,
                                            color = ext.textSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Primary CTA to Submit Support Ticket to Managers
            if (repository != null && currentUserProfile != null) {
                Button(
                    onClick = { showTicketFormDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("open_support_ticket_form_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ext.accentBlueSolid,
                        contentColor = Color.White
                    )
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Yöneticiye Destek Talebi / Sorun Bildir", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }

            // User's Submitted Support Tickets History
            if (myTickets.isNotEmpty()) {
                HorizontalDivider(color = ext.cardBorder)
                Text(
                    text = "Gönderdiğim Destek Talepleri (${myTickets.size})",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    myTickets.take(5).forEach { ticket ->
                        SupportTicketSummaryCard(
                            ticket = ticket,
                            showSender = false
                        )
                    }
                }
            }
        }
    }

    if (showTicketFormDialog && repository != null && currentUserProfile != null) {
        val mgr = updateManager ?: remember { AppUpdateManager(context) }
        val appVer = remember { "${mgr.getCurrentVersionName()} (${mgr.getCurrentVersionCode()})" }
        val deviceInfo = remember { "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})" }

        SupportTicketFormDialog(
            userTeam = currentUserProfile.team,
            appVersion = appVer,
            deviceInfo = deviceInfo,
            onDismiss = { showTicketFormDialog = false },
            onSubmit = { category, subject, description, onComplete ->
                scope.launch {
                    try {
                        val res = repository.submitSupportTicket(
                            category = category,
                            subject = subject,
                            description = description,
                            userName = currentUserProfile.getFullName(),
                            userEmail = currentUserProfile.email,
                            userTeam = currentUserProfile.team,
                            appVersion = appVer,
                            deviceInfo = deviceInfo,
                            teamId = teamId,
                            teamName = teamName
                        )
                        onComplete()
                        if (res.isSuccess) {
                            showTicketFormDialog = false
                            Toast.makeText(
                                context,
                                "Destek talebiniz yöneticilere iletildi!",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            val err = res.exceptionOrNull()
                            Toast.makeText(
                                context,
                                sanitizeUserErrorMessage(err ?: IllegalStateException("Gönderilemedi"), "Destek talebi gönderilemedi."),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    } catch (ce: CancellationException) {
                        throw ce
                    } catch (e: Exception) {
                        onComplete()
                        Toast.makeText(context, sanitizeUserErrorMessage(e), Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportTicketFormDialog(
    userTeam: String,
    appVersion: String,
    deviceInfo: String,
    onDismiss: () -> Unit,
    onSubmit: (category: String, subject: String, description: String, onComplete: () -> Unit) -> Unit
) {
    val ext = MaterialTheme.blackseaColors
    var selectedCategory by remember { mutableStateOf(SupportCategories.ALL.first()) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var subject by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }

    val subjectValid = subject.trim().length in 3..120
    val descriptionValid = description.trim().length in 10..2000

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        icon = {
            Icon(
                imageVector = Icons.Default.SupportAgent,
                contentDescription = null,
                tint = ext.accentBlue,
                modifier = Modifier.size(30.dp)
            )
        },
        title = {
            Text(
                text = "Yöneticiye Destek Talebi Gönder",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Yaşadığınız sorunu veya önerinizi yazın. Talebiniz anında yöneticilere ve takım kaptanlarına bildirim olarak iletilir.",
                    fontSize = 12.sp,
                    color = ext.textSecondary,
                    lineHeight = 17.sp
                )

                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { if (!isSubmitting) categoryExpanded = !categoryExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedCategory,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Konu Kategorisi") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        shape = RoundedCornerShape(10.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false }
                    ) {
                        SupportCategories.ALL.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat) },
                                onClick = {
                                    selectedCategory = cat
                                    categoryExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = subject,
                    onValueChange = { if (it.length <= 120) subject = it },
                    label = { Text("Konu Başlığı") },
                    placeholder = { Text("Örn: Mesaj bildirimleri geç düşüyor") },
                    singleLine = true,
                    supportingText = {
                        Text(
                            text = if (subject.isNotBlank() && !subjectValid) "En az 3 karakter yazın" else "${subject.trim().length}/120",
                            fontSize = 10.sp
                        )
                    },
                    isError = subject.isNotBlank() && !subjectValid,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("support_ticket_subject_input"),
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { if (it.length <= 2000) description = it },
                    label = { Text("Sorun / Talep Detayı") },
                    placeholder = { Text("Sorunun ne zaman ve hangi ekranda oluştuğunu detaylıca yazın...") },
                    minLines = 4,
                    maxLines = 7,
                    supportingText = {
                        Text(
                            text = if (description.isNotBlank() && !descriptionValid) "En az 10 karakter yazın" else "${description.trim().length}/2000",
                            fontSize = 10.sp
                        )
                    },
                    isError = description.isNotBlank() && !descriptionValid,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("support_ticket_description_input"),
                    shape = RoundedCornerShape(10.dp)
                )

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, ext.cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = null,
                            tint = ext.textMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Otomatik eklenecek bilgi: v$appVersion • $userTeam • $deviceInfo",
                            fontSize = 10.sp,
                            color = ext.textSecondary,
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    isSubmitting = true
                    onSubmit(selectedCategory, subject.trim(), description.trim()) {
                        isSubmitting = false
                    }
                },
                enabled = !isSubmitting && subjectValid && descriptionValid,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ext.accentBlueSolid,
                    contentColor = Color.White
                ),
                modifier = Modifier.testTag("submit_support_ticket_button")
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text("Yöneticiye Gönder", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                Text("Vazgeç")
            }
        }
    )
}

@Composable
fun SupportTicketSummaryCard(
    ticket: SupportTicket,
    showSender: Boolean,
    modifier: Modifier = Modifier,
    onManageClick: (() -> Unit)? = null
) {
    val ext = MaterialTheme.blackseaColors
    val normalizedStatus = SupportTicketStatus.normalize(ticket.status)
    val (statusBg, statusFg) = when (normalizedStatus) {
        SupportTicketStatus.RESOLVED -> Pair(ext.emeraldSolid.copy(alpha = 0.16f), ext.emeraldText)
        SupportTicketStatus.IN_PROGRESS -> Pair(ext.accentBlueContainer, ext.accentBlue)
        else -> Pair(ext.amberText.copy(alpha = 0.16f), ext.amberText)
    }
    val dateFormat = remember { SimpleDateFormat("dd MMM HH:mm", Locale("tr", "TR")) }
    val dateLabel = remember(ticket.createdAt) {
        ticket.createdAt?.toDate()?.let { dateFormat.format(it) } ?: "Şimdi"
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (ext.isDark) 0.45f else 0.55f),
        border = BorderStroke(1.dp, ext.cardBorder)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = ext.accentBlueContainer
                ) {
                    Text(
                        text = ticket.category,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = ext.accentBlue,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = dateLabel,
                        fontSize = 10.sp,
                        color = ext.textMuted
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = statusBg
                    ) {
                        Text(
                            text = SupportTicketStatus.displayName(normalizedStatus),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = statusFg,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            if (showSender) {
                Text(
                    text = "${ticket.userName} • ${ticket.userTeam}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ext.accentBlue
                )
            }

            Text(
                text = ticket.subject,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = ticket.description,
                fontSize = 12.sp,
                color = ext.textSecondary,
                lineHeight = 17.sp
            )

            if (ticket.appVersion.isNotBlank() || ticket.deviceInfo.isNotBlank()) {
                Text(
                    text = listOf(ticket.appVersion, ticket.deviceInfo)
                        .filter { it.isNotBlank() }
                        .joinToString(" • "),
                    fontSize = 10.sp,
                    color = ext.textMuted
                )
            }

            if (ticket.adminReply.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = ext.emeraldSolid.copy(alpha = if (ext.isDark) 0.18f else 0.10f),
                    border = BorderStroke(1.dp, ext.emeraldText.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = ext.emeraldText,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Yönetici Yanıtı (${ticket.repliedByName.ifBlank { "Yönetim" }})",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ext.emeraldText
                            )
                        }
                        Text(
                            text = ticket.adminReply,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 17.sp
                        )
                    }
                }
            }

            if (onManageClick != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onManageClick,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ext.accentBlueSolid,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier.heightIn(min = 40.dp)
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.Reply, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (ticket.adminReply.isBlank()) "Yanıtla / Durumu Güncelle" else "Yanıtı Düzenle",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminSupportTab(
    tickets: List<SupportTicket>,
    repository: BlackseaRepository,
    currentUserProfile: UserProfile?,
    teamId: String = com.example.model.PLATFORM_MAIN_TEAM_ID,
    teamName: String = "BlackSea Robotics",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val ext = MaterialTheme.blackseaColors
    val scope = rememberCoroutineScope()

    var statusFilter by remember { mutableStateOf("all") }
    var ticketToReply by remember { mutableStateOf<SupportTicket?>(null) }

    val filteredTickets = remember(tickets, statusFilter) {
        if (statusFilter == "all") tickets
        else tickets.filter { SupportTicketStatus.normalize(it.status) == statusFilter }
    }

    Column(modifier = modifier.fillMaxSize()) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val filters = listOf(
                "all" to "Tümü (${tickets.size})",
                SupportTicketStatus.OPEN to "Açık (${tickets.count { SupportTicketStatus.normalize(it.status) == SupportTicketStatus.OPEN }})",
                SupportTicketStatus.IN_PROGRESS to "İnceleniyor (${tickets.count { SupportTicketStatus.normalize(it.status) == SupportTicketStatus.IN_PROGRESS }})",
                SupportTicketStatus.RESOLVED to "Çözüldü (${tickets.count { SupportTicketStatus.normalize(it.status) == SupportTicketStatus.RESOLVED }})"
            )
            items(filters) { (key, label) ->
                FilterChip(
                    selected = statusFilter == key,
                    onClick = { statusFilter = key },
                    label = { Text(label, fontSize = 12.sp) }
                )
            }
        }

        if (filteredTickets.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.SupportAgent,
                        contentDescription = null,
                        tint = ext.textMuted,
                        modifier = Modifier.size(42.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Bu kategoride destek talebi bulunmuyor.",
                        color = ext.textMuted,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(
                    items = filteredTickets,
                    key = { index, item -> "${item.id.ifBlank { "ticket" }}_$index" }
                ) { _, ticket ->
                    SupportTicketSummaryCard(
                        ticket = ticket,
                        showSender = true,
                        onManageClick = { ticketToReply = ticket }
                    )
                }
            }
        }
    }

    ticketToReply?.let { target ->
        var replyStatus by remember(target.id) { mutableStateOf(SupportTicketStatus.normalize(target.status)) }
        var replyText by remember(target.id) { mutableStateOf(target.adminReply) }
        var isSaving by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isSaving) ticketToReply = null },
            title = {
                Column {
                    Text(text = target.subject, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        text = "${target.userName} (${target.userTeam})",
                        fontSize = 11.sp,
                        color = ext.textMuted
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Talep Durumu:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SupportTicketStatus.ALL.forEach { st ->
                            FilterChip(
                                selected = replyStatus == st,
                                onClick = { if (!isSaving) replyStatus = st },
                                label = { Text(SupportTicketStatus.displayName(st), fontSize = 12.sp) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = replyText,
                        onValueChange = { if (it.length <= 2000) replyText = it },
                        label = { Text("Yönetici Yanıtı / Çözüm Notu") },
                        placeholder = { Text("Üyeye iletilecek açıklama veya çözüm adımlarını yazın...") },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isSaving = true
                        scope.launch {
                            val res = repository.replyAndUpdateSupportTicket(
                                ticketId = target.id,
                                status = replyStatus,
                                adminReply = replyText,
                                repliedByName = currentUserProfile?.getFullName() ?: "Yönetici",
                                targetUserId = target.userId,
                                teamId = teamId,
                                teamName = teamName
                            )
                            isSaving = false
                            if (res.isSuccess) {
                                ticketToReply = null
                                Toast.makeText(context, "Destek talebi güncellendi ve üyeye bildirildi.", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(
                                    context,
                                    sanitizeUserErrorMessage(res.exceptionOrNull() ?: IllegalStateException("Hata")),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    },
                    enabled = !isSaving,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ext.accentBlueSolid,
                        contentColor = Color.White
                    )
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Kaydet ve Bildir", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { ticketToReply = null }, enabled = !isSaving) {
                    Text("İptal")
                }
            }
        )
    }
}
