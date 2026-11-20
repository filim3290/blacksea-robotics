package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BlackseaRepository
import com.example.model.ChatMessage
import com.example.model.SharedProjectFile
import com.example.model.UserProfile
import com.example.model.canBeDeletedBy
import com.example.model.canBeEditedBy
import com.example.model.filterChatDisplayRoles
import com.example.model.formatMessagesForClipboard
import com.example.notifications.TeamNotificationDispatcher
import com.example.ui.components.FileUploadProgressDialog
import com.example.ui.components.InAppFileViewerDialog
import com.example.ui.components.MediaThumbnailPreview
import com.example.ui.components.RoleBadge
import com.example.ui.components.TeamBadge
import com.example.ui.components.getFileIcon
import com.example.ui.components.getFileIconColor
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.PhoenixAmber
import com.example.ui.theme.blackseaColors
import com.example.util.AudioRecorderManager
import com.example.util.VoiceNotePlayer
import com.example.util.downloadFileToDevice
import com.example.util.logDebugError
import com.example.util.sanitizeUserErrorMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

import com.example.model.TeamInfo

@Composable
fun RoomsScreen(
    repository: BlackseaRepository,
    currentUserProfile: UserProfile?,
    activeTeam: TeamInfo = TeamInfo.mainBlackseaTeam(),
    initialRoomId: String = "general",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val ext = MaterialTheme.blackseaColors

    val activeTeamId = activeTeam.id.ifBlank { com.example.model.PLATFORM_MAIN_TEAM_ID }
    val activeTeamName = activeTeam.name.ifBlank { "BlackSea Robotics" }
    val storagePrefix = if (activeTeamId == com.example.model.PLATFORM_MAIN_TEAM_ID) "" else "teams/$activeTeamId/"

    val rooms = remember {
        listOf(
            Pair("general", "GENEL EKİP ODASI"),
            Pair("software", "YAZILIM EKİBİ"),
            Pair("mechanic", "MEKANİK EKİBİ"),
            Pair("pr", "PR EKİBİ")
        )
    }
    val initialRoomIdx = remember(rooms) {
        rooms.indexOfFirst { it.first == initialRoomId }.takeIf { it >= 0 } ?: 0
    }
    val pagerState = rememberPagerState(
        initialPage = initialRoomIdx,
        pageCount = { rooms.size }
    )
    val chipRowState = rememberLazyListState()
    val selectedRoomId = rooms.getOrElse(pagerState.currentPage) { rooms.first() }.first

    LaunchedEffect(initialRoomId) {
        val idx = rooms.indexOfFirst { it.first == initialRoomId }
        if (idx >= 0 && pagerState.currentPage != idx) {
            pagerState.scrollToPage(idx)
        }
    }
    LaunchedEffect(pagerState.currentPage) {
        chipRowState.animateScrollToItem(pagerState.currentPage)
    }
    DisposableEffect(selectedRoomId, activeTeamId, currentUserProfile?.userId, currentUserProfile?.id) {
        val activeUid = currentUserProfile?.userId?.ifBlank { currentUserProfile.id }.orEmpty()
        TeamNotificationDispatcher.setActiveChatRoom(context, activeUid, selectedRoomId, activeTeamId)
        onDispose {
            TeamNotificationDispatcher.setActiveChatRoom(context, activeUid, null, activeTeamId)
        }
    }
    val messages by remember(selectedRoomId, activeTeamId) {
        repository.observeMessages(selectedRoomId, teamId = activeTeamId)
    }.collectAsState(initial = emptyList())

    // Message Input State
    var messageText by remember { mutableStateOf("") }
    var replyingTo by remember { mutableStateOf<ChatMessage?>(null) }
    var isSending by remember { mutableStateOf(false) }

    // Quick Code / Paste Dialog State
    var showAttachmentDialog by remember { mutableStateOf(false) }
    var showAttachmentOptions by remember { mutableStateOf(false) }
    var attachedFileName by remember { mutableStateOf("") }
    var attachedFileType by remember { mutableStateOf("") }
    var attachedFileContent by remember { mutableStateOf("") }

    // Real File Upload State
    var pickedFileUri by remember { mutableStateOf<Uri?>(null) }
    var pickedFileName by remember { mutableStateOf("") }
    var pickedFileSizeFormatted by remember { mutableStateOf("") }
    var pickedFileType by remember { mutableStateOf("other") }
    var isUploadingFile by remember { mutableStateOf(false) }
    var uploadProgress by remember { mutableIntStateOf(0) }
    var uploadErrorMessage by remember { mutableStateOf<String?>(null) }
    var showUploadModal by remember { mutableStateOf(false) }

    // In-App File Preview Modal
    var activePreviewFile by remember { mutableStateOf<SharedProjectFile?>(null) }

    // Voice Recording State
    val audioRecorder = remember { AudioRecorderManager(context) }
    var isRecordingVoice by remember { mutableStateOf(false) }
    var recordDurationSeconds by remember { mutableIntStateOf(0) }
    var isVoiceUploading by remember { mutableStateOf(false) }

    // Message Edit (1-hour window) & WhatsApp-style Selection / Bulk Delete State
    var currentTimeMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var messageToEdit by remember { mutableStateOf<ChatMessage?>(null) }
    var editMessageText by remember { mutableStateOf("") }
    var isSavingMessageEdit by remember { mutableStateOf(false) }

    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedMessageIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showSelectionMoreMenu by remember { mutableStateOf(false) }
    var showBulkDeleteDialog by remember { mutableStateOf(false) }
    var isBulkDeleting by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            currentTimeMs = System.currentTimeMillis()
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val started = audioRecorder.startRecording()
            if (started) {
                isRecordingVoice = true
                recordDurationSeconds = 0
            } else {
                Toast.makeText(context, "Ses kaydı başlatılamadı", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Ses kaydı için mikrofon izni gereklidir", Toast.LENGTH_SHORT).show()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (isRecordingVoice) {
                audioRecorder.cancelRecording()
            }
        }
    }

    LaunchedEffect(isRecordingVoice) {
        if (isRecordingVoice) {
            recordDurationSeconds = 0
            while (isRecordingVoice) {
                delay(1000)
                recordDurationSeconds++
            }
        }
    }

    fun handlePickedUri(uri: Uri) {
        pickedFileUri = uri
        val (name, size) = repository.queryFileMetadata(context, uri)
        val mimeType = try { context.contentResolver.getType(uri) } catch (_: Exception) { null }
        val type = repository.detectFileType(name, mimeType)
        pickedFileName = name
        pickedFileType = type

        val kb = size / 1024.0
        val mb = kb / 1024.0
        pickedFileSizeFormatted = if (mb >= 1.0) {
            String.format(Locale.US, "%.1f MB", mb)
        } else if (kb >= 1.0) {
            String.format(Locale.US, "%.0f KB", kb)
        } else {
            "$size B"
        }

        uploadErrorMessage = if (size > 100L * 1024L * 1024L) {
            "Dosya boyutu 100 MB sınırını aşıyor."
        } else {
            null
        }
        uploadProgress = 0
        isUploadingFile = false
        showUploadModal = true
    }

    // Zero-permission Photo & Video Picker Launcher (Google Play Policy compliant)
    val visualMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) handlePickedUri(uri)
    }

    // General Device File Picker Launcher (PDF, Code, CAD, ZIP, etc.)
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) handlePickedUri(uri)
    }

    val isKeyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    val isAdmin = currentUserProfile?.isManagerAccount() == true
    val currentUid = currentUserProfile?.userId.orEmpty()
    val selectableMessages = remember(messages) {
        messages.filter { it.id.isNotBlank() }
    }
    val selectedMessages = remember(messages, selectedMessageIds) {
        messages.filter { it.id in selectedMessageIds }
    }
    val canDeleteSelection = remember(selectedMessages, currentUid, isAdmin) {
        selectedMessages.isNotEmpty() && selectedMessages.all { it.canBeDeletedBy(currentUid, isAdmin) }
    }
    val singleSelectedMessage = selectedMessages.singleOrNull()
    val canPinSingle = singleSelectedMessage != null && (singleSelectedMessage.senderId == currentUid || isAdmin)
    val canEditSingle = singleSelectedMessage != null && singleSelectedMessage.canBeEditedBy(currentUid, currentTimeMs)
    val allSelectableSelected = selectableMessages.isNotEmpty() &&
        selectableMessages.all { it.id in selectedMessageIds }

    fun exitSelectionMode() {
        isSelectionMode = false
        selectedMessageIds = emptySet()
        showSelectionMoreMenu = false
        showBulkDeleteDialog = false
    }

    fun copySelectedMessagesToClipboard() {
        val textToCopy = formatMessagesForClipboard(selectedMessages)
        if (textToCopy.isNotBlank()) {
            clipboardManager.setText(AnnotatedString(textToCopy))
            Toast.makeText(context, "Panoya kopyalandı.", Toast.LENGTH_SHORT).show()
        }
        exitSelectionMode()
    }

    LaunchedEffect(selectedRoomId) {
        exitSelectionMode()
    }

    LaunchedEffect(messages) {
        if (isSelectionMode) {
            val validIds = messages
                .mapNotNull { it.id.takeIf { id -> id.isNotBlank() } }
                .toSet()
            val pruned = selectedMessageIds.intersect(validIds)
            if (pruned != selectedMessageIds) {
                selectedMessageIds = pruned
            }
            if (pruned.isEmpty()) {
                exitSelectionMode()
            }
        }
    }

    BackHandler(enabled = isSelectionMode && !isBulkDeleting) {
        exitSelectionMode()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .testTag("rooms_screen")
    ) {
        // Room Filter Tabs OR WhatsApp-style Contextual Selection Bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = if (isSelectionMode) ElectricBlue.copy(alpha = if (ext.isDark) 0.20f else 0.12f) else MaterialTheme.colorScheme.surface,
            shadowElevation = 2.dp
        ) {
            if (isSelectionMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                        .testTag("bulk_selection_bar"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(
                        onClick = { exitSelectionMode() },
                        enabled = !isBulkDeleting,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("cancel_bulk_select_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Seçimi İptal Et",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Text(
                        text = "${selectedMessageIds.size} seçildi",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 4.dp)
                    )

                    if (canDeleteSelection) {
                        IconButton(
                            onClick = { showBulkDeleteDialog = true },
                            enabled = !isBulkDeleting,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("bulk_delete_trigger_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Seçilen Mesajları Sil",
                                tint = ext.dangerText
                            )
                        }
                    }

                    IconButton(
                        onClick = { copySelectedMessagesToClipboard() },
                        enabled = selectedMessages.isNotEmpty() && !isBulkDeleting,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("bulk_copy_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Seçilen Mesajları Kopyala",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Box {
                        IconButton(
                            onClick = { showSelectionMoreMenu = true },
                            enabled = !isBulkDeleting,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("selection_more_menu_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Diğer İşlemler",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        DropdownMenu(
                            expanded = showSelectionMoreMenu,
                            onDismissRequest = { showSelectionMoreMenu = false }
                        ) {
                            if (singleSelectedMessage != null) {
                                DropdownMenuItem(
                                    text = { Text("Yanıtla") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Reply,
                                            contentDescription = null,
                                            tint = ElectricBlue
                                        )
                                    },
                                    onClick = {
                                        val target = singleSelectedMessage
                                        exitSelectionMode()
                                        replyingTo = target
                                    },
                                    modifier = Modifier.testTag("menu_reply_message")
                                )
                            }

                            if (canPinSingle && singleSelectedMessage != null) {
                                DropdownMenuItem(
                                    text = { Text(if (singleSelectedMessage.isPinned) "Sabitlemeyi Kaldır" else "Sabitle") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.PushPin,
                                            contentDescription = null,
                                            tint = ext.amberText
                                        )
                                    },
                                    onClick = {
                                        val target = singleSelectedMessage
                                        exitSelectionMode()
                                        scope.launch {
                                            try {
                                                repository.togglePinMessage(selectedRoomId, target.id, !target.isPinned, teamId = activeTeamId)
                                            } catch (ce: CancellationException) {
                                                throw ce
                                            } catch (e: Exception) {
                                                logDebugError("RoomsScreen", "Toggle pin failed", e)
                                                Toast.makeText(
                                                    context,
                                                    sanitizeUserErrorMessage(e, "Sabitleme işlemi yapılamadı."),
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    },
                                    modifier = Modifier.testTag("menu_pin_message")
                                )
                            }

                            if (canEditSingle && singleSelectedMessage != null) {
                                DropdownMenuItem(
                                    text = { Text("Düzenle") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = null,
                                            tint = ext.accentBlue
                                        )
                                    },
                                    onClick = {
                                        val target = singleSelectedMessage
                                        exitSelectionMode()
                                        messageToEdit = target
                                        editMessageText = target.text
                                    },
                                    modifier = Modifier.testTag("menu_edit_message")
                                )
                            }

                            DropdownMenuItem(
                                text = { Text("Kopyala") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    copySelectedMessagesToClipboard()
                                },
                                modifier = Modifier.testTag("menu_copy_messages")
                            )

                            if (canDeleteSelection) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = if (selectedMessages.size > 1) "Sil (${selectedMessages.size})" else "Sil",
                                            color = ext.dangerText,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = null,
                                            tint = ext.dangerText
                                        )
                                    },
                                    onClick = {
                                        showSelectionMoreMenu = false
                                        showBulkDeleteDialog = true
                                    },
                                    modifier = Modifier.testTag("menu_delete_messages")
                                )
                            }

                            HorizontalDivider()

                            DropdownMenuItem(
                                text = { Text(if (allSelectableSelected) "Seçimi Temizle" else "Tümünü Seç") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.DoneAll,
                                        contentDescription = null,
                                        tint = ElectricBlue
                                    )
                                },
                                onClick = {
                                    showSelectionMoreMenu = false
                                    if (allSelectableSelected) {
                                        exitSelectionMode()
                                    } else {
                                        selectedMessageIds = selectableMessages.map { it.id }.toSet()
                                    }
                                },
                                modifier = Modifier.testTag("select_all_messages_button")
                            )
                        }
                    }
                }
            } else {
                LazyRow(
                    state = chipRowState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(rooms) { index, (_, title) ->
                        val isSelectedRoom = pagerState.currentPage == index
                        FilterChip(
                            selected = isSelectedRoom,
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            },
                            label = {
                                Text(
                                    text = title,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelectedRoom) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElectricBlue,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }
        }

        // Swipeable Room Pages (Pinned Messages Banner + Message Stream)
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !isSelectionMode && !isRecordingVoice,
            beyondViewportPageCount = 1,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) { pageIndex ->
            val pageRoomId = rooms[pageIndex].first
            val pageMessages by if (pageRoomId == selectedRoomId) {
                rememberUpdatedState(messages)
            } else {
                remember(pageRoomId, activeTeamId) {
                    repository.observeMessages(pageRoomId, teamId = activeTeamId)
                }.collectAsState(initial = emptyList())
            }
            val pageListState = rememberLazyListState()
            LaunchedEffect(pageMessages.size, isKeyboardVisible) {
                if (pageMessages.isNotEmpty()) {
                    pageListState.animateScrollToItem(pageMessages.size - 1)
                }
            }
            val pinnedMessages = remember(pageMessages) { pageMessages.filter { it.isPinned } }

            Column(modifier = Modifier.fillMaxSize()) {
                // Pinned Messages Banner
                if (pinnedMessages.isNotEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = PhoenixAmber.copy(alpha = if (ext.isDark) 0.12f else 0.16f)),
                        border = BorderStroke(1.dp, ext.amberText.copy(alpha = 0.25f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PushPin,
                                contentDescription = "Sabitli Mesaj",
                                tint = ext.amberText,
                                modifier = Modifier.size(16.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Sabitlenmiş Mesaj: ${pinnedMessages.last().text}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${pinnedMessages.last().senderName} • ${pinnedMessages.last().senderTeam}",
                                    fontSize = 10.sp,
                                    color = ext.textSecondary
                                )
                            }
                        }
                    }
                }

                // Message Stream
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    state = pageListState,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (pageMessages.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Bu odada henüz mesaj yok. İlk mesajı siz yazın!",
                                    fontSize = 13.sp,
                                    color = ext.textMuted
                                )
                            }
                        }
                    }

                    itemsIndexed(
                        items = pageMessages,
                        key = { index, msg -> "${msg.id.ifBlank { "msg_${msg.createdAt?.seconds ?: 0}" }}_$index" }
                    ) { _, message ->
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn() + slideInVertically(initialOffsetY = { 20 })
                        ) {
                            val isMyMessage = message.senderId == currentUserProfile?.userId
                            val canSelectMessage = message.id.isNotBlank()
                            val isMessageSelected = message.id in selectedMessageIds
                            MessageItemBubble(
                                message = message,
                                isMyMessage = isMyMessage,
                                isSelectionMode = isSelectionMode,
                                canSelect = canSelectMessage,
                                isSelected = isMessageSelected,
                                onToggleSelect = {
                                    if (canSelectMessage) {
                                        val updated = if (isMessageSelected) {
                                            selectedMessageIds - message.id
                                        } else {
                                            selectedMessageIds + message.id
                                        }
                                        selectedMessageIds = updated
                                        if (updated.isEmpty()) {
                                            exitSelectionMode()
                                        }
                                    }
                                },
                                onEnterSelectionMode = {
                                    if (canSelectMessage && !isRecordingVoice) {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        isSelectionMode = true
                                        selectedMessageIds = setOf(message.id)
                                    }
                                },
                                onReply = { replyingTo = message },
                                onOpenFile = { projectFile ->
                                    activePreviewFile = projectFile
                                },
                                onDownloadFile = { url ->
                                    downloadFileToDevice(context, message.fileName.ifBlank { "indirilen_dosya" }, url)
                                }
                            )
                        }
                    }
                }
            }
        }

        // Replying to Banner (hidden during bulk selection mode)
        if (!isSelectionMode && replyingTo != null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.Reply, contentDescription = null, tint = ElectricBlue, modifier = Modifier.size(16.dp))
                        Text(
                            text = "${replyingTo?.senderName}: ${replyingTo?.text}",
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { replyingTo = null },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Kapat", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // Attached file preview before sending (hidden during bulk selection mode)
        if (!isSelectionMode && attachedFileName.isNotBlank()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                shape = RoundedCornerShape(8.dp),
                color = ElectricBlue.copy(alpha = 0.15f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(imageVector = Icons.Default.AttachFile, contentDescription = null, tint = ElectricBlue, modifier = Modifier.size(16.dp))
                        Text(text = "Ek: $attachedFileName ($attachedFileType)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ElectricBlue)
                    }
                    IconButton(
                        onClick = {
                            attachedFileName = ""
                            attachedFileType = ""
                            attachedFileContent = ""
                        },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Kaldır", modifier = Modifier.size(14.dp))
                    }
                }
            }
        }

        // Bottom Input Bar (shows contextual selection hint while selecting messages)
        if (isSelectionMode) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 4.dp
            ) {
                val footerHint = when {
                    canDeleteSelection -> "${selectedMessages.size} mesaj seçildi · Kopyalamak, silmek veya diğer işlemler için üst menüyü (⋮) kullanın."
                    selectedMessages.any { !it.canBeDeletedBy(currentUid, isAdmin) } ->
                        "${selectedMessages.size} mesaj seçildi · Kopyalayabilirsiniz (başkalarının mesajları silinemez)."
                    else -> "${selectedMessages.size} mesaj seçildi."
                }
                Text(
                    text = footerHint,
                    fontSize = 12.sp,
                    color = ext.textSecondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        } else {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp
            ) {
            if (isRecordingVoice) {
                // Active Voice Recording View
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = {
                            audioRecorder.cancelRecording()
                            isRecordingVoice = false
                            recordDurationSeconds = 0
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Kaydı İptal Et",
                            tint = ext.dangerText
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(Color.Red, CircleShape)
                        )
                        val minutes = recordDurationSeconds / 60
                        val seconds = recordDurationSeconds % 60
                        Text(
                            text = String.format(Locale.getDefault(), "Ses Kaydediliyor... %02d:%02d", minutes, seconds),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    IconButton(
                        onClick = {
                            val file = audioRecorder.stopRecording()
                            isRecordingVoice = false
                            if (file != null && file.exists() && file.length() > 0) {
                                val duration = recordDurationSeconds
                                val realName = currentUserProfile?.getFullName() ?: "Takım Üyesi"
                                val roles = currentUserProfile?.roles ?: listOf("Takım Üyesi")
                                val team = currentUserProfile?.team ?: "Genel Ekip"
                                val durationText = String.format(Locale.getDefault(), "%02d:%02d", duration / 60, duration % 60)

                                scope.launch {
                                    isVoiceUploading = true
                                    try {
                                        val uploadResult = repository.uploadLocalFile(
                                            file = file,
                                            folder = "${storagePrefix}rooms_attachments/$selectedRoomId/audio",
                                            fileType = "audio"
                                        )

                                        repository.sendMessage(
                                            roomId = selectedRoomId,
                                            text = "🎙️ Ses Kaydı ($durationText)",
                                            senderName = realName,
                                            senderTeam = team,
                                            senderRoles = roles,
                                            senderPhotoUrl = currentUserProfile?.photoUrl ?: "",
                                            fileName = "Ses_Kaydı_${System.currentTimeMillis()}.m4a",
                                            fileType = "audio",
                                            fileUrl = uploadResult.downloadUrl,
                                            fileSize = uploadResult.fileSize,
                                            teamId = activeTeamId,
                                            teamName = activeTeamName
                                        )

                                        repository.addSharedFile(
                                            roomId = selectedRoomId,
                                            fileName = "Ses_Kaydı_${System.currentTimeMillis()}.m4a",
                                            fileType = "audio",
                                            fileSize = uploadResult.fileSize,
                                            description = "Ses kaydı ($durationText)",
                                            content = "",
                                            downloadUrl = uploadResult.downloadUrl,
                                            storagePath = "",
                                            uploadedByName = realName,
                                            uploadedByTeam = team,
                                            uploadedByRoles = roles,
                                            teamId = activeTeamId,
                                            teamName = activeTeamName
                                        )

                                        Toast.makeText(context, "Ses kaydı başarıyla gönderildi!", Toast.LENGTH_SHORT).show()
                                    } catch (ce: CancellationException) {
                                        throw ce
                                    } catch (e: Exception) {
                                        logDebugError("RoomsScreen", "Voice upload failed", e)
                                        Toast.makeText(context, sanitizeUserErrorMessage(e, "Ses kaydı gönderilemedi."), Toast.LENGTH_SHORT).show()
                                    } finally {
                                        file.delete()
                                        isVoiceUploading = false
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .background(ElectricBlue, CircleShape)
                    ) {
                        if (isVoiceUploading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = "Kaydı Gönder", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            } else {
                // Standard Chat Input Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = { showAttachmentOptions = true },
                        modifier = Modifier.size(42.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AttachFile,
                            contentDescription = "Dosya, Video veya Kod Ekle",
                            tint = ElectricBlue
                        )
                    }

                    OutlinedTextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        placeholder = { Text("Mesajınızı yazın...", fontSize = 14.sp) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_input_field"),
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 4
                    )

                    // Microphone button for quick voice notes
                    IconButton(
                        onClick = {
                            audioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .background(ElectricBlue.copy(alpha = 0.15f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Ses Kaydı Al",
                            tint = ElectricBlue,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Send text or attachment message
                    IconButton(
                        onClick = {
                            if ((messageText.isNotBlank() || attachedFileName.isNotBlank()) && !isSending) {
                                val reply = replyingTo
                                val currentText = messageText.trim()
                                val fileNameToSend = attachedFileName
                                val fileTypeToSend = attachedFileType
                                val fileContentToSend = attachedFileContent

                                // User real name & roles
                                val realName = currentUserProfile?.getFullName() ?: "Takım Üyesi"
                                val roles = currentUserProfile?.roles ?: listOf("Takım Üyesi")
                                val team = currentUserProfile?.team ?: "Genel Ekip"

                                // Reset fields
                                messageText = ""
                                replyingTo = null
                                attachedFileName = ""
                                attachedFileType = ""
                                attachedFileContent = ""
                                isSending = true

                                scope.launch {
                                    try {
                                        repository.sendMessage(
                                            roomId = selectedRoomId,
                                            text = currentText.ifBlank { "[$fileNameToSend paylaştı]" },
                                            senderName = realName,
                                            senderTeam = team,
                                            senderRoles = roles,
                                            senderPhotoUrl = currentUserProfile?.photoUrl ?: "",
                                            fileName = fileNameToSend,
                                            fileType = fileTypeToSend,
                                            fileUrl = fileContentToSend,
                                            replyToId = reply?.id ?: "",
                                            replyToSender = reply?.senderName ?: "",
                                            replyToText = reply?.text ?: "",
                                            teamId = activeTeamId,
                                            teamName = activeTeamName
                                        )
                                    } catch (ce: CancellationException) {
                                        throw ce
                                    } catch (e: Exception) {
                                        logDebugError("RoomsScreen", "Send message failed", e)
                                        Toast.makeText(context, sanitizeUserErrorMessage(e, "Mesaj gönderilemedi."), Toast.LENGTH_SHORT).show()
                                    } finally {
                                        isSending = false
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .background(ElectricBlue, CircleShape)
                            .testTag("send_message_button")
                    ) {
                        if (isSending) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = "Gönder", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
            }
        }
    }

    // Attachment Option Selector Dialog (Real Device File vs Quick Code Snippet)
    if (showAttachmentOptions) {
        AlertDialog(
            onDismissRequest = { showAttachmentOptions = false },
            title = {
                Text("Dosya veya İçerik Ekle", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Görsel/video galeriden veya tüm cihaz dosyalarından yükleyebilir ya da kod yapıştırabilirsiniz:", fontSize = 13.sp, color = ext.textSecondary)

                    // Option 1: Zero-permission Photo & Video Picker with Preview
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showAttachmentOptions = false
                                visualMediaLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                )
                            }
                            .testTag("pick_visual_media_option"),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        border = BorderStroke(1.dp, ext.cardBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = ext.emeraldSolid.copy(alpha = 0.15f),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = ext.emeraldText, modifier = Modifier.size(22.dp))
                                }
                            }
                            Column {
                                Text("Fotoğraf / Video Seç", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                                Text("Galeriden önizlemeli fotoğraf veya video paylaşın", fontSize = 11.sp, color = ext.textMuted)
                            }
                        }
                    }

                    // Option 2: Real Device File Picker
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showAttachmentOptions = false
                                filePickerLauncher.launch("*/*")
                            }
                            .testTag("pick_any_file_option"),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        border = BorderStroke(1.dp, ext.cardBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = ext.accentBlueContainer,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.UploadFile, contentDescription = null, tint = ext.accentBlue, modifier = Modifier.size(22.dp))
                                }
                            }
                            Column {
                                Text("Cihazdan Dosya Seç", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                                Text("PDF, Kod (.py, .cpp, .ino), CAD, ZIP, Ses (Maks 100 MB)", fontSize = 11.sp, color = ext.textMuted)
                            }
                        }
                    }

                    // Option 3: Quick Code Snippet / Link Paste
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showAttachmentOptions = false
                                showAttachmentDialog = true
                            },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        border = BorderStroke(1.dp, ext.cardBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = PhoenixAmber.copy(alpha = 0.15f),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Code, contentDescription = null, tint = ext.amberText, modifier = Modifier.size(22.dp))
                                }
                            }
                            Column {
                                Text("Kod Bloğu / Link Yapıştır", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                                Text("Hızlıca kod veya link metni ekleyin", fontSize = 11.sp, color = ext.textMuted)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAttachmentOptions = false }) {
                    Text("İptal")
                }
            }
        )
    }

    // Attachment Chooser Dialog (Snippet/Link)
    if (showAttachmentDialog) {
        AttachmentPickerDialog(
            onDismiss = { showAttachmentDialog = false },
            onFileSelected = { name, type, content ->
                attachedFileName = name
                attachedFileType = type
                attachedFileContent = content
                showAttachmentDialog = false
            }
        )
    }

    // Real Device File Upload Progress Modal
    if (showUploadModal && pickedFileUri != null) {
        FileUploadProgressDialog(
            fileName = pickedFileName,
            fileSizeFormatted = pickedFileSizeFormatted,
            fileType = pickedFileType,
            previewUri = pickedFileUri,
            isUploading = isUploadingFile,
            uploadProgress = uploadProgress,
            errorMessage = uploadErrorMessage,
            onCancel = {
                showUploadModal = false
                pickedFileUri = null
            },
            onConfirmUpload = {
                val uri = pickedFileUri ?: return@FileUploadProgressDialog
                scope.launch {
                    isUploadingFile = true
                    uploadErrorMessage = null
                    try {
                        val result = repository.uploadFile(
                            context = context,
                            uri = uri,
                            folder = "${storagePrefix}rooms_attachments/$selectedRoomId",
                            onProgress = { prog -> uploadProgress = prog }
                        )

                        // 1. Add file to workspace 'files' collection
                        repository.addSharedFile(
                            roomId = selectedRoomId,
                            fileName = result.fileName,
                            fileType = result.fileType,
                            fileSize = result.fileSize,
                            description = "Oda içi paylaşılan dosya ($selectedRoomId)",
                            content = result.textContent,
                            downloadUrl = result.downloadUrl,
                            storagePath = result.storagePath,
                            thumbnailUrl = result.thumbnailUrl,
                            mediaDurationMs = result.mediaDurationMs,
                            uploadedByName = currentUserProfile?.getFullName() ?: "Takım Üyesi",
                            uploadedByTeam = currentUserProfile?.team ?: "",
                            uploadedByRoles = currentUserProfile?.roles ?: emptyList(),
                            teamId = activeTeamId,
                            teamName = activeTeamName
                        )

                        // 2. Post chat message with file attachment
                        repository.sendMessage(
                            roomId = selectedRoomId,
                            text = "[${result.fileName} dosyasını paylaştı]",
                            senderName = currentUserProfile?.getFullName() ?: "Takım Üyesi",
                            senderTeam = currentUserProfile?.team ?: "Genel Ekip",
                            senderRoles = currentUserProfile?.roles ?: emptyList(),
                            senderPhotoUrl = currentUserProfile?.photoUrl ?: "",
                            fileName = result.fileName,
                            fileType = result.fileType,
                            fileUrl = result.downloadUrl.ifBlank { result.textContent },
                            fileSize = result.fileSize,
                            thumbnailUrl = result.thumbnailUrl,
                            storagePath = result.storagePath,
                            mediaDurationMs = result.mediaDurationMs,
                            teamId = activeTeamId,
                            teamName = activeTeamName
                        )

                        isUploadingFile = false
                        showUploadModal = false
                        pickedFileUri = null
                        Toast.makeText(context, "Dosya başarıyla gönderildi!", Toast.LENGTH_SHORT).show()
                    } catch (ce: CancellationException) {
                        throw ce
                    } catch (e: Exception) {
                        logDebugError("RoomsScreen", "Upload file failed", e)
                        isUploadingFile = false
                        uploadErrorMessage = sanitizeUserErrorMessage(e, "Dosya yüklenemedi. Lütfen tekrar deneyin.")
                    }
                }
            },
            onRetry = {
                uploadErrorMessage = null
            }
        )
    }

    // In-App File Viewer Modal
    if (activePreviewFile != null) {
        InAppFileViewerDialog(
            file = activePreviewFile!!,
            onDismiss = { activePreviewFile = null }
        )
    }

    // Edit Message Dialog (within 1 hour)
    if (messageToEdit != null) {
        val targetMsg = messageToEdit!!
        AlertDialog(
            onDismissRequest = { if (!isSavingMessageEdit) messageToEdit = null },
            title = { Text("Mesajı Düzenle", fontWeight = FontWeight.Bold, fontSize = 17.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Gönderdiğiniz mesajı 1 saat içinde düzenleyebilirsiniz.",
                        fontSize = 12.sp,
                        color = ext.textSecondary
                    )
                    OutlinedTextField(
                        value = editMessageText,
                        onValueChange = { editMessageText = it },
                        label = { Text("Mesajınız") },
                        minLines = 2,
                        maxLines = 5,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_message_input"),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = editMessageText.trim()
                        if (trimmed.isBlank()) return@Button
                        isSavingMessageEdit = true
                        scope.launch {
                            try {
                                repository.editMessage(selectedRoomId, targetMsg.id, trimmed, teamId = activeTeamId)
                                messageToEdit = null
                                Toast.makeText(context, "Mesaj güncellendi.", Toast.LENGTH_SHORT).show()
                            } catch (ce: CancellationException) {
                                throw ce
                            } catch (e: Exception) {
                                logDebugError("RoomsScreen", "Edit message failed", e)
                                val msg = e.message.orEmpty()
                                val friendly = if (
                                    e is IllegalStateException &&
                                    (msg.contains("1 saat") || msg.contains("kendi") || msg.contains("boş") || msg.contains("bulunamadı"))
                                ) {
                                    msg
                                } else {
                                    sanitizeUserErrorMessage(e, "Mesaj düzenlenemedi.")
                                }
                                Toast.makeText(context, friendly, Toast.LENGTH_LONG).show()
                            } finally {
                                isSavingMessageEdit = false
                            }
                        }
                    },
                    enabled = !isSavingMessageEdit && editMessageText.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricBlue,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("confirm_edit_message_button")
                ) {
                    if (isSavingMessageEdit) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Kaydet", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { messageToEdit = null },
                    enabled = !isSavingMessageEdit
                ) {
                    Text("Vazgeç")
                }
            }
        )
    }

    // Single / Bulk Delete Messages Confirmation Dialog
    if (showBulkDeleteDialog && selectedMessageIds.isNotEmpty() && canDeleteSelection) {
        val count = selectedMessageIds.size
        AlertDialog(
            onDismissRequest = { if (!isBulkDeleting) showBulkDeleteDialog = false },
            title = {
                Text(
                    text = if (count == 1) "Mesajı Sil" else "Seçilen Mesajları Sil",
                    fontWeight = FontWeight.Bold,
                    color = ext.dangerText
                )
            },
            text = {
                Text(
                    text = if (count == 1) {
                        "Bu mesaj kalıcı olarak silinsin mi? Bu işlem geri alınamaz."
                    } else {
                        "Seçilen $count mesaj kalıcı olarak silinsin mi? Bu işlem geri alınamaz."
                    },
                    color = ext.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val idsToDelete = selectedMessageIds.toList()
                        isBulkDeleting = true
                        scope.launch {
                            try {
                                repository.deleteMessages(selectedRoomId, idsToDelete, teamId = activeTeamId)
                                exitSelectionMode()
                                val toastText = if (idsToDelete.size == 1) "Mesaj silindi." else "${idsToDelete.size} mesaj silindi."
                                Toast.makeText(context, toastText, Toast.LENGTH_SHORT).show()
                            } catch (ce: CancellationException) {
                                throw ce
                            } catch (e: Exception) {
                                logDebugError("RoomsScreen", "Bulk delete messages failed", e)
                                Toast.makeText(
                                    context,
                                    sanitizeUserErrorMessage(e, "Mesajlar silinemedi."),
                                    Toast.LENGTH_SHORT
                                ).show()
                            } finally {
                                isBulkDeleting = false
                            }
                        }
                    },
                    enabled = !isBulkDeleting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ext.dangerSolid,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("confirm_bulk_delete_button")
                ) {
                    if (isBulkDeleting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(if (count == 1) "Evet, Sil" else "Evet, Tümünü Sil", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showBulkDeleteDialog = false },
                    enabled = !isBulkDeleting
                ) {
                    Text("Vazgeç")
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun MessageItemBubble(
    message: ChatMessage,
    isMyMessage: Boolean,
    isSelectionMode: Boolean = false,
    canSelect: Boolean = true,
    isSelected: Boolean = false,
    onToggleSelect: () -> Unit = {},
    onEnterSelectionMode: () -> Unit = {},
    onReply: () -> Unit,
    onOpenFile: (SharedProjectFile) -> Unit,
    onDownloadFile: (String) -> Unit
) {
    val context = LocalContext.current
    val ext = MaterialTheme.blackseaColors
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val formattedTime = message.createdAt?.toDate()?.let { timeFormatter.format(it) } ?: ""

    val swipeThresholdPx = with(density) { 52.dp.toPx() }
    val maxSwipePx = with(density) { 80.dp.toPx() }
    val swipeOffsetX = remember(message.id) { Animatable(0f) }
    var hapticFiredOnSwipe by remember(message.id) { mutableStateOf(false) }

    val rowBackgroundColor = if (isSelected) {
        ElectricBlue.copy(alpha = if (ext.isDark) 0.18f else 0.10f)
    } else {
        Color.Transparent
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBackgroundColor, RoundedCornerShape(12.dp))
            .pointerInput(message.id, isSelectionMode) {
                if (isSelectionMode) return@pointerInput
                val touchSlop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var dragStarted = false
                    var replyTriggered = false
                    var totalDx = 0f
                    var totalDy = 0f
                    val pointerId = down.id
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == pointerId }
                                ?: event.changes.firstOrNull()
                                ?: break
                            if (!change.pressed) {
                                if (dragStarted && swipeOffsetX.value >= swipeThresholdPx) {
                                    replyTriggered = true
                                }
                                break
                            }
                            val delta = change.position - change.previousPosition
                            if (!dragStarted) {
                                totalDx += delta.x
                                totalDy += delta.y
                                if (kotlin.math.abs(totalDx) > touchSlop || kotlin.math.abs(totalDy) > touchSlop) {
                                    if (totalDx > 0f && kotlin.math.abs(totalDx) > kotlin.math.abs(totalDy)) {
                                        dragStarted = true
                                        change.consume()
                                        val next = totalDx.coerceIn(0f, maxSwipePx)
                                        scope.launch { swipeOffsetX.snapTo(next) }
                                        if (!hapticFiredOnSwipe && next >= swipeThresholdPx) {
                                            hapticFiredOnSwipe = true
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        }
                                    } else {
                                        // Pass vertical scroll and leftward room-switch swipe to parent containers
                                        break
                                    }
                                }
                            } else {
                                change.consume()
                                val next = (swipeOffsetX.value + delta.x).coerceIn(0f, maxSwipePx)
                                scope.launch { swipeOffsetX.snapTo(next) }
                                if (!hapticFiredOnSwipe && next >= swipeThresholdPx) {
                                    hapticFiredOnSwipe = true
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                } else if (hapticFiredOnSwipe && next < swipeThresholdPx * 0.75f) {
                                    hapticFiredOnSwipe = false
                                }
                            }
                        }
                    } finally {
                        if (dragStarted) {
                            hapticFiredOnSwipe = false
                            scope.launch {
                                swipeOffsetX.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    )
                                )
                            }
                            if (replyTriggered) {
                                onReply()
                            }
                        }
                    }
                }
            }
            .combinedClickable(
                onClick = {
                    if (isSelectionMode && canSelect) {
                        onToggleSelect()
                    }
                },
                onLongClick = {
                    if (!isSelectionMode && canSelect) {
                        onEnterSelectionMode()
                    }
                }
            )
    ) {
        // WhatsApp-style Reply Indicator revealed when swiping right
        if (swipeOffsetX.value > 2f) {
            val swipeProgress = (swipeOffsetX.value / swipeThresholdPx).coerceIn(0f, 1f)
            Surface(
                shape = CircleShape,
                color = ElectricBlue.copy(alpha = 0.14f + 0.16f * swipeProgress),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 10.dp)
                    .size(34.dp)
                    .alpha(swipeProgress)
                    .scale(0.65f + 0.35f * swipeProgress)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Reply,
                        contentDescription = "Yanıtla",
                        tint = ElectricBlue,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(swipeOffsetX.value.toInt(), 0) }
                .padding(vertical = 2.dp, horizontal = 2.dp),
            horizontalAlignment = if (isMyMessage) Alignment.End else Alignment.Start
        ) {
            val displayRoles = remember(message.senderRoles) {
                filterChatDisplayRoles(message.senderRoles)
            }
            // Sender header with Real Name + Team + Kaptan/Öğretmen badge only (never show internal/other roles)
            if (!isMyMessage) {
                FlowRow(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = ElectricBlue.copy(alpha = 0.2f),
                        modifier = Modifier
                            .size(26.dp)
                            .align(Alignment.CenterVertically)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = message.senderName.take(1).uppercase(),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = ElectricBlue
                            )
                        }
                    }
                    Text(
                        text = message.senderName,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                    TeamBadge(teamName = message.senderTeam)
                    displayRoles.forEach { roleLabel ->
                        RoleBadge(role = roleLabel)
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isSelectionMode && canSelect) {
                    IconButton(
                        onClick = onToggleSelect,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("select_message_checkbox_${message.id}")
                    ) {
                        Icon(
                            imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            contentDescription = if (isSelected) "Seçili Mesaj" else "Mesajı Seç",
                            tint = if (isSelected) ElectricBlue else ext.textMuted,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Card(
                    shape = RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isMyMessage) 16.dp else 4.dp,
                        bottomEnd = if (isMyMessage) 4.dp else 16.dp
                    ),
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            isSelected && isMyMessage -> ElectricBlue.copy(alpha = 0.85f)
                            isSelected -> ElectricBlue.copy(alpha = if (ext.isDark) 0.24f else 0.14f)
                            isMyMessage -> ElectricBlue
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    ),
                    border = when {
                        isSelected -> BorderStroke(2.dp, if (isMyMessage) PhoenixAmber else ElectricBlue)
                        !isMyMessage -> BorderStroke(1.dp, ext.cardBorder)
                        else -> null
                    },
                    modifier = Modifier
                        .widthIn(max = 310.dp)
                        .combinedClickable(
                            onClick = {
                                if (isSelectionMode && canSelect) {
                                    onToggleSelect()
                                }
                            },
                            onLongClick = {
                                if (!isSelectionMode && canSelect) {
                                    onEnterSelectionMode()
                                }
                            }
                        )
                        .testTag("message_card_${message.id}")
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        // Reply Quote
                        if (message.replyToSender.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color.Black.copy(alpha = 0.15f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp)
                            ) {
                                Column(modifier = Modifier.padding(6.dp)) {
                                    Text(
                                        text = message.replyToSender,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = if (isMyMessage) Color.White else ElectricBlue
                                    )
                                    Text(
                                        text = message.replyToText,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = if (isMyMessage) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Main Text
                        Text(
                            text = message.text,
                            fontSize = 14.sp,
                            color = if (isMyMessage) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        val projectFile = SharedProjectFile(
                            id = message.id,
                            roomId = message.roomId,
                            fileName = message.fileName.ifBlank {
                                when (message.fileType) {
                                    "image" -> "Görsel.jpg"
                                    "video" -> "Video.mp4"
                                    "audio" -> "Ses_Kaydı.m4a"
                                    else -> "Dosya"
                                }
                            },
                            fileType = message.fileType,
                            fileSize = message.fileSize,
                            content = if (message.fileType == "code" || message.fileType == "text") message.fileUrl else "",
                            downloadUrl = message.fileUrl,
                            storagePath = message.storagePath,
                            thumbnailUrl = message.thumbnailUrl,
                            mediaDurationMs = message.mediaDurationMs,
                            uploadedByName = message.senderName,
                            uploadedByTeam = message.senderTeam
                        )

                        // Voice Note Audio Player
                        if (message.fileType == "audio" && message.fileUrl.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            VoiceNotePlayer(
                                audioUrl = message.fileUrl,
                                fileName = message.fileName.ifBlank { "Ses Kaydı.m4a" },
                                isMyMessage = isMyMessage,
                                onDownload = {
                                    downloadFileToDevice(context, message.fileName.ifBlank { "Ses_Kaydı.m4a" }, message.fileUrl)
                                }
                            )
                        } else if (message.fileType == "image" && (message.fileUrl.isNotBlank() || message.thumbnailUrl.isNotBlank() || message.storagePath.isNotBlank())) {
                            // INLINE IMAGE PREVIEW with Base64/local support and tap to zoom
                            Spacer(modifier = Modifier.height(8.dp))
                            MediaThumbnailPreview(
                                mediaType = "image",
                                mediaUrl = message.fileUrl,
                                thumbnailUrl = message.thumbnailUrl,
                                storagePath = message.storagePath,
                                fileName = message.fileName.ifBlank { "Görsel" },
                                fileSizeText = projectFile.getFormattedSize(),
                                height = 190.dp,
                                onClick = {
                                    if (isSelectionMode && canSelect) onToggleSelect() else if (!isSelectionMode) onOpenFile(projectFile)
                                },
                                onDownload = {
                                    if (isSelectionMode && canSelect) {
                                        onToggleSelect()
                                    } else if (!isSelectionMode) {
                                        downloadFileToDevice(
                                            context,
                                            message.fileName.ifBlank { "gorsel.jpg" },
                                            message.fileUrl.ifBlank { message.storagePath }
                                        )
                                    }
                                },
                                testTag = "chat_image_preview_${message.id}"
                            )
                        } else if (message.fileType == "video" && (message.fileUrl.isNotBlank() || message.thumbnailUrl.isNotBlank() || message.storagePath.isNotBlank())) {
                            // INLINE VIDEO THUMBNAIL PREVIEW with real frame, duration badge, and tap to play in ExoPlayer
                            Spacer(modifier = Modifier.height(8.dp))
                            MediaThumbnailPreview(
                                mediaType = "video",
                                mediaUrl = message.fileUrl,
                                thumbnailUrl = message.thumbnailUrl,
                                storagePath = message.storagePath,
                                fileName = message.fileName.ifBlank { "Video.mp4" },
                                fileSizeText = projectFile.getFormattedSize(),
                                durationMs = message.mediaDurationMs,
                                height = 190.dp,
                                onClick = {
                                    if (isSelectionMode && canSelect) onToggleSelect() else if (!isSelectionMode) onOpenFile(projectFile)
                                },
                                onDownload = {
                                    if (isSelectionMode && canSelect) {
                                        onToggleSelect()
                                    } else if (!isSelectionMode) {
                                        downloadFileToDevice(
                                            context,
                                            message.fileName.ifBlank { "video.mp4" },
                                            message.fileUrl.ifBlank { message.storagePath }
                                        )
                                    }
                                },
                                testTag = "chat_video_preview_${message.id}"
                            )
                        } else if (message.fileName.isNotBlank()) {
                            // DOCUMENT / CODE / CAD / ZIP PREVIEW CARD
                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isMyMessage) Color.White.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isSelectionMode && canSelect) onToggleSelect() else if (!isSelectionMode) onOpenFile(projectFile)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = getFileIcon(message.fileType),
                                            contentDescription = null,
                                            tint = if (isMyMessage) Color.White else getFileIconColor(message.fileType),
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Column {
                                            Text(
                                                text = message.fileName,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isMyMessage) Color.White else MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${message.fileType.uppercase()} · Önizlemek İçin Dokunun",
                                                fontSize = 10.sp,
                                                color = if (isMyMessage) Color.White.copy(alpha = 0.8f) else ext.textMuted
                                            )
                                        }
                                    }

                                    if (!isSelectionMode) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = (if (isMyMessage) Color.White else ElectricBlue).copy(alpha = 0.15f),
                                                modifier = Modifier.clickable { onOpenFile(projectFile) }
                                            ) {
                                                Text(
                                                    text = "Aç",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isMyMessage) Color.White else ext.accentBlue,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                            if (message.fileUrl.isNotBlank()) {
                                                IconButton(
                                                    onClick = {
                                                        downloadFileToDevice(context, message.fileName.ifBlank { "dosya" }, message.fileUrl)
                                                    },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Download,
                                                        contentDescription = "Cihaza İndir",
                                                        tint = if (isMyMessage) Color.White else ext.accentBlue,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Clean Footer: subtle pin status icon (when pinned) + timestamp only
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (message.isPinned) {
                                Icon(
                                    imageVector = Icons.Default.PushPin,
                                    contentDescription = "Sabitlendi",
                                    tint = if (isMyMessage) PhoenixAmber else ext.amberText,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }

                            val timeDisplay = buildString {
                                if (message.isEdited) {
                                    append("(düzenlendi) ")
                                }
                                append(formattedTime)
                            }.trim()
                            Text(
                                text = timeDisplay,
                                fontSize = 10.sp,
                                color = if (isMyMessage) Color.White.copy(alpha = 0.7f) else ext.textMuted
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AttachmentPickerDialog(
    onDismiss: () -> Unit,
    onFileSelected: (name: String, type: String, content: String) -> Unit
) {
    var fileName by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("code") }
    var content by remember { mutableStateOf("") }

    val fileTypes = listOf(
        "code" to "Kod Dosyası",
        "doc" to "Teknik Doküman",
        "cad" to "CAD / 3D Model",
        "image" to "Görsel Linki"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Dosya veya Kod Ekle", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    label = { Text("Dosya Adı") },
                    placeholder = { Text("Örn: autonomous_drive.cpp") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                Text("Dosya Türü:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(fileTypes) { (typeKey, typeLabel) ->
                        FilterChip(
                            selected = selectedType == typeKey,
                            onClick = { selectedType = typeKey },
                            label = { Text(typeLabel, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors()
                        )
                    }
                }

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text(if (selectedType == "code") "Kod İçeriği" else "Açıklama / Dosya Linki") },
                    placeholder = { Text(if (selectedType == "code") "// Kodunuzu buraya yapıştırın..." else "https://drive.google.com/... veya Onshape linki") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    shape = RoundedCornerShape(10.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (fileName.isNotBlank()) {
                        onFileSelected(fileName.trim(), selectedType, content.trim())
                    }
                },
                enabled = fileName.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricBlue,
                    contentColor = Color.White
                )
            ) {
                Text("Ekle")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("İptal")
            }
        }
    )
}
