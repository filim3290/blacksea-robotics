package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Send
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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.data.FileUploadResult
import com.example.model.IdeaItem
import com.example.model.SharedProjectFile
import com.example.model.UserProfile
import com.example.ui.components.AnimatedHeartLikeButton
import com.example.ui.components.ExoVideoPlayerView
import com.example.ui.components.IdeaCategoryBadge
import com.example.ui.components.InAppFileViewerDialog
import com.example.ui.components.MediaThumbnailPreview
import com.example.ui.components.TeamBadge
import com.example.ui.components.UserRolesRow
import com.example.ui.components.getFileIcon
import com.example.ui.components.getFileIconColor
import com.example.ui.theme.ElectricBlue
import com.example.util.downloadFileToDevice
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdeasScreen(
    repository: BlackseaRepository,
    currentUserProfile: UserProfile?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedCategoryFilter by remember { mutableStateOf("Hepsi") }
    var selectedRoomFilter by remember { mutableStateOf("all") }

    val ideas by repository.observeIdeas(if (selectedRoomFilter == "all") null else selectedRoomFilter).collectAsState(initial = emptyList())

    // Dialog state
    var showCreateDialog by remember { mutableStateOf(false) }

    // Status change dialog
    var ideaToChangeStatus by remember { mutableStateOf<IdeaItem?>(null) }

    // Comments Sheet State
    var activeIdeaForComments by remember { mutableStateOf<IdeaItem?>(null) }

    // File Preview Dialog State
    var activePreviewFile by remember { mutableStateOf<SharedProjectFile?>(null) }

    val categories = listOf(
        "Hepsi",
        "Yeni",
        "Değerlendiriliyor",
        "Geliştiriliyor",
        "Kabul Edildi",
        "Tamamlandı",
        "Reddedildi"
    )

    val roomOptions = listOf(
        Pair("all", "Tüm Odalar"),
        Pair("general", "Genel Ekip"),
        Pair("software", "Yazılım"),
        Pair("mechanic", "Mekanik"),
        Pair("pr", "PR")
    )

    val filteredIdeas = ideas.filter { idea ->
        if (selectedCategoryFilter == "Hepsi") true else (idea.category == selectedCategoryFilter || (selectedCategoryFilter == "Yeni" && idea.category == "Yeni fikir"))
    }

    val isAdminOrLeader = currentUserProfile?.isSuperAdmin == true ||
            currentUserProfile?.email.equals("filim3290@gmail.com", ignoreCase = true) ||
            currentUserProfile?.roles?.any { it in listOf("Yönetici", "Takım Kaptanı", "Yetkili Öğretmen") } == true

    val timeFormatter = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }

    Box(modifier = modifier.fillMaxSize().testTag("ideas_screen")) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Room & Category Filter Chips
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp
            ) {
                Column {
                    // Room Filter
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(roomOptions) { (id, label) ->
                            FilterChip(
                                selected = selectedRoomFilter == id,
                                onClick = { selectedRoomFilter = id },
                                label = { Text(text = label, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ElectricBlue,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }

                    // Category Filter
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(categories) { cat ->
                            FilterChip(
                                selected = selectedCategoryFilter == cat,
                                onClick = { selectedCategoryFilter = cat },
                                label = { Text(text = cat, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }
                }
            }

            // Ideas List
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (filteredIdeas.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 60.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Bu filtrede henüz fikir bulunmuyor. Yeni bir fikir ekleyin!",
                                color = Color(0xFF94A3B8),
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                itemsIndexed(
                    items = filteredIdeas,
                    key = { index, idea -> "${idea.id.ifBlank { idea.title }}_$index" }
                ) { _, idea ->
                    val isLiked = idea.likes.contains(currentUserProfile?.userId)
                    val canDelete = isAdminOrLeader || idea.authorId == currentUserProfile?.userId
                    val dateFormatted = idea.createdAt?.toDate()?.let { timeFormatter.format(it) } ?: ""

                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn() + slideInVertically(initialOffsetY = { 20 })
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                // Header: Room tag & Status Badge
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        TeamBadge(teamName = idea.authorTeam)
                                        Text(
                                            text = "• ${idea.roomId.uppercase()} ODASI",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF94A3B8)
                                        )
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IdeaCategoryBadge(category = idea.category)
                                        if (isAdminOrLeader) {
                                            IconButton(
                                                onClick = { ideaToChangeStatus = idea },
                                                modifier = Modifier.size(24.dp).padding(start = 4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Edit,
                                                    contentDescription = "Durumu Değiştir",
                                                    tint = Color(0xFF94A3B8),
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Title
                                Text(
                                    text = idea.title,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                // Author Real Name & Roles
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = idea.authorName,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ElectricBlue
                                    )
                                    if (idea.authorRoles.isNotEmpty()) {
                                        UserRolesRow(roles = idea.authorRoles.take(2))
                                    }
                                    if (dateFormatted.isNotBlank()) {
                                        Text(
                                            text = "· $dateFormatted",
                                            fontSize = 11.sp,
                                            color = Color(0xFF94A3B8)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Description
                                Text(
                                    text = idea.description,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 19.sp
                                )

                                if (idea.fileName.isNotBlank() || idea.fileUrl.isNotBlank() || idea.thumbnailUrl.isNotBlank()) {
                                    val resolvedFileType = idea.fileType.ifBlank {
                                        repository.detectFileType(idea.fileName)
                                    }
                                    val projectFile = SharedProjectFile(
                                        id = idea.id,
                                        roomId = idea.roomId,
                                        fileName = idea.fileName.ifBlank {
                                            when (resolvedFileType) {
                                                "image" -> "Görsel.jpg"
                                                "video" -> "Video.mp4"
                                                else -> "Ek_Dosya"
                                            }
                                        },
                                        fileType = resolvedFileType,
                                        fileSize = idea.fileSize,
                                        content = if (resolvedFileType == "code" || resolvedFileType == "text") idea.fileUrl else "",
                                        downloadUrl = idea.fileUrl,
                                        storagePath = idea.storagePath,
                                        thumbnailUrl = idea.thumbnailUrl,
                                        mediaDurationMs = idea.mediaDurationMs,
                                        uploadedByName = idea.authorName,
                                        uploadedByTeam = idea.authorTeam
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    if (resolvedFileType == "image" || resolvedFileType == "video") {
                                        MediaThumbnailPreview(
                                            mediaType = resolvedFileType,
                                            mediaUrl = idea.fileUrl,
                                            thumbnailUrl = idea.thumbnailUrl,
                                            storagePath = idea.storagePath,
                                            fileName = projectFile.fileName,
                                            fileSizeText = projectFile.getFormattedSize(),
                                            durationMs = idea.mediaDurationMs,
                                            height = 175.dp,
                                            onClick = { activePreviewFile = projectFile },
                                            onDownload = {
                                                downloadFileToDevice(
                                                    context,
                                                    projectFile.fileName,
                                                    idea.fileUrl.ifBlank { idea.storagePath }
                                                )
                                            },
                                            testTag = "idea_media_preview_${idea.id}"
                                        )
                                    } else {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { activePreviewFile = projectFile }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = getFileIcon(resolvedFileType),
                                                    contentDescription = null,
                                                    tint = getFileIconColor(resolvedFileType),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Text(
                                                    text = "Ek: ${projectFile.fileName} · Önizle / İndir",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = ElectricBlue,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                // Footer: Animated Heart Like Button + Comments + Delete
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        // Animated Heart Like Button
                                        AnimatedHeartLikeButton(
                                            isLiked = isLiked,
                                            likeCount = idea.likeCount,
                                            onToggleLike = {
                                                scope.launch {
                                                    repository.toggleLikeIdea(idea.id, idea.likes)
                                                }
                                            }
                                        )

                                        // Comments Button
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable { activeIdeaForComments = idea }
                                                .padding(horizontal = 6.dp, vertical = 4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ChatBubbleOutline,
                                                contentDescription = "Yorumlar",
                                                tint = Color(0xFF64748B),
                                                modifier = Modifier.size(19.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Yorumlar",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = Color(0xFF64748B)
                                            )
                                        }
                                    }

                                    if (canDelete) {
                                        IconButton(
                                            onClick = {
                                                scope.launch {
                                                    repository.deleteIdea(idea.id)
                                                    Toast.makeText(context, "Fikir silindi", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Sil",
                                                tint = Color(0xFF94A3B8),
                                                modifier = Modifier.size(18.dp)
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

        // Floating Action Button to Add Idea
        FloatingActionButton(
            onClick = { showCreateDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("create_idea_fab"),
            containerColor = ElectricBlue,
            contentColor = Color.White
        ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = "Yeni Fikir Ekle")
        }
    }

    // File Preview Dialog
    if (activePreviewFile != null) {
        InAppFileViewerDialog(
            file = activePreviewFile!!,
            onDismiss = { activePreviewFile = null }
        )
    }

    // Create Idea Dialog
    if (showCreateDialog) {
        CreateIdeaDialog(
            repository = repository,
            onDismiss = { showCreateDialog = false },
            onSubmit = { title, desc, cat, room, uploadResult ->
                scope.launch {
                    val realName = currentUserProfile?.getFullName() ?: "Takım Üyesi"
                    val roles = currentUserProfile?.roles ?: listOf("Takım Üyesi")
                    val team = currentUserProfile?.team ?: "Genel Ekip"

                    repository.addIdea(
                        roomId = room,
                        title = title,
                        description = desc,
                        category = cat,
                        authorName = realName,
                        authorTeam = team,
                        authorRoles = roles,
                        fileName = uploadResult?.fileName ?: "",
                        fileType = uploadResult?.fileType ?: "",
                        fileUrl = uploadResult?.downloadUrl?.ifBlank { uploadResult.textContent } ?: "",
                        fileSize = uploadResult?.fileSize ?: 0L,
                        thumbnailUrl = uploadResult?.thumbnailUrl ?: "",
                        storagePath = uploadResult?.storagePath ?: "",
                        mediaDurationMs = uploadResult?.mediaDurationMs ?: 0L
                    )
                    showCreateDialog = false
                    Toast.makeText(context, "Fikriniz takımla paylaşıldı!", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // Change Status Dialog
    if (ideaToChangeStatus != null) {
        val idea = ideaToChangeStatus!!
        ChangeStatusDialog(
            currentStatus = idea.category,
            onDismiss = { ideaToChangeStatus = null },
            onSave = { newStatus ->
                scope.launch {
                    repository.updateIdeaCategory(idea.id, newStatus)
                    ideaToChangeStatus = null
                    Toast.makeText(context, "Fikir durumu güncellendi: $newStatus", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // Comments Sheet
    if (activeIdeaForComments != null) {
        val idea = activeIdeaForComments!!
        IdeaCommentsBottomSheet(
            idea = idea,
            repository = repository,
            currentUserProfile = currentUserProfile,
            isAdmin = isAdminOrLeader,
            onDismiss = { activeIdeaForComments = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateIdeaDialog(
    repository: BlackseaRepository,
    onDismiss: () -> Unit,
    onSubmit: (title: String, desc: String, category: String, room: String, attachment: FileUploadResult?) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Yeni") }
    var selectedRoom by remember { mutableStateOf("general") }

    var categoryExpanded by remember { mutableStateOf(false) }
    var roomExpanded by remember { mutableStateOf(false) }

    var pickedUri by remember { mutableStateOf<Uri?>(null) }
    var pickedName by remember { mutableStateOf("") }
    var pickedType by remember { mutableStateOf("other") }
    var pickedSizeFormatted by remember { mutableStateOf("") }
    var isUploading by remember { mutableStateOf(false) }
    var uploadProgress by remember { mutableIntStateOf(0) }
    var uploadError by remember { mutableStateOf<String?>(null) }

    fun handlePickedUri(uri: Uri) {
        pickedUri = uri
        val (name, size) = repository.queryFileMetadata(context, uri)
        val mime = try { context.contentResolver.getType(uri) } catch (_: Exception) { null }
        pickedName = name
        pickedType = repository.detectFileType(name, mime)
        val kb = size / 1024.0
        val mb = kb / 1024.0
        pickedSizeFormatted = if (mb >= 1.0) {
            String.format(Locale.US, "%.1f MB", mb)
        } else if (kb >= 1.0) {
            String.format(Locale.US, "%.0f KB", kb)
        } else {
            "$size B"
        }
        uploadError = if (size > 100L * 1024L * 1024L) "Dosya boyutu 100 MB sınırını aşıyor." else null
    }

    val visualMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) handlePickedUri(uri)
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) handlePickedUri(uri)
    }

    val categories = listOf(
        "Yeni",
        "Değerlendiriliyor",
        "Geliştiriliyor",
        "Kabul Edildi",
        "Tamamlandı",
        "Reddedildi"
    )

    val rooms = listOf(
        Pair("general", "Genel Ekip Odası"),
        Pair("software", "Yazılım Ekibi Odası"),
        Pair("mechanic", "Mekanik Ekibi Odası"),
        Pair("pr", "PR Ekibi Odası")
    )

    AlertDialog(
        onDismissRequest = { if (!isUploading) onDismiss() },
        title = { Text("Yeni Fikir Paylaş", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Fikir Başlığı") },
                    placeholder = { Text("Örn: Robot Kolu İçin Yeni Mekanizma") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                // Room Selector
                ExposedDropdownMenuBox(
                    expanded = roomExpanded,
                    onExpandedChange = { roomExpanded = !roomExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = rooms.firstOrNull { it.first == selectedRoom }?.second ?: "Genel Ekip Odası",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("İlgili Oda") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = roomExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    ExposedDropdownMenu(
                        expanded = roomExpanded,
                        onDismissRequest = { roomExpanded = false }
                    ) {
                        rooms.forEach { (id, name) ->
                            DropdownMenuItem(
                                text = { Text(name) },
                                onClick = {
                                    selectedRoom = id
                                    roomExpanded = false
                                }
                            )
                        }
                    }
                }

                // Category Selector
                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = !categoryExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedCategory,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Durum / Kategori") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    ExposedDropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false }
                    ) {
                        categories.forEach { cat ->
                            DropdownMenuItem(
                                text = { IdeaCategoryBadge(category = cat) },
                                onClick = {
                                    selectedCategory = cat
                                    categoryExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Açıklama & Öneri") },
                    placeholder = { Text("Tasarım ve uygulama detayları...") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    minLines = 3,
                    maxLines = 5
                )

                // Media / File Attachment Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            visualMediaLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                            )
                        },
                        enabled = !isUploading,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("idea_pick_media_button"),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp), tint = ElectricBlue)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Görsel / Video", fontSize = 11.sp, maxLines = 1)
                    }

                    OutlinedButton(
                        onClick = { filePickerLauncher.launch("*/*") },
                        enabled = !isUploading,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("idea_pick_file_button"),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                    ) {
                        Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp), tint = ElectricBlue)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Dosya Ekle", fontSize = 11.sp, maxLines = 1)
                    }
                }

                // Inline Attachment Preview before submitting
                if (pickedUri != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = pickedName,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${pickedType.uppercase()} · $pickedSizeFormatted",
                                        fontSize = 10.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                }
                                if (!isUploading) {
                                    IconButton(
                                        onClick = {
                                            pickedUri = null
                                            pickedName = ""
                                            uploadError = null
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Eki Kaldır", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }

                            if (pickedType == "image") {
                                MediaThumbnailPreview(
                                    mediaType = "image",
                                    mediaUrl = pickedUri.toString(),
                                    localPreviewUri = pickedUri,
                                    height = 135.dp,
                                    testTag = "idea_create_image_preview"
                                )
                            } else if (pickedType == "video") {
                                ExoVideoPlayerView(
                                    videoUri = pickedUri!!,
                                    autoPlay = false,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(170.dp)
                                        .testTag("idea_create_video_preview")
                                )
                            }
                        }
                    }
                }

                if (isUploading) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(text = "Ek yükleniyor... %$uploadProgress", fontSize = 11.sp, color = ElectricBlue)
                        LinearProgressIndicator(
                            progress = { uploadProgress / 100f },
                            modifier = Modifier.fillMaxWidth().height(5.dp),
                            color = ElectricBlue
                        )
                    }
                }

                if (uploadError != null) {
                    Text(text = uploadError ?: "", color = Color(0xFFEF4444), fontSize = 11.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmedTitle = title.trim()
                    if (trimmedTitle.isBlank()) return@Button
                    val uri = pickedUri
                    if (uri == null) {
                        onSubmit(trimmedTitle, description.trim(), selectedCategory, selectedRoom, null)
                    } else {
                        scope.launch {
                            isUploading = true
                            uploadError = null
                            try {
                                val res = repository.uploadFile(
                                    context = context,
                                    uri = uri,
                                    folder = "ideas_attachments/$selectedRoom",
                                    onProgress = { uploadProgress = it }
                                )
                                isUploading = false
                                onSubmit(trimmedTitle, description.trim(), selectedCategory, selectedRoom, res)
                            } catch (e: Exception) {
                                isUploading = false
                                uploadError = e.localizedMessage ?: "Ek yüklenemedi"
                            }
                        }
                    }
                },
                enabled = title.isNotBlank() && !isUploading,
                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
            ) {
                if (isUploading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Yükleniyor...")
                } else {
                    Text("Paylaş")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isUploading) {
                Text("İptal")
            }
        }
    )
}

@Composable
fun ChangeStatusDialog(
    currentStatus: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    val statuses = listOf(
        "Yeni",
        "Değerlendiriliyor",
        "Geliştiriliyor",
        "Kabul Edildi",
        "Tamamlandı",
        "Reddedildi"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Fikir Durumunu Güncelle", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                statuses.forEach { status ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSave(status) },
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (status == currentStatus) ElectricBlue.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IdeaCategoryBadge(category = status)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Vazgeç")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdeaCommentsBottomSheet(
    idea: IdeaItem,
    repository: BlackseaRepository,
    currentUserProfile: UserProfile?,
    isAdmin: Boolean,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val comments by repository.observeIdeaComments(idea.id).collectAsState(initial = emptyList())
    var commentText by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState()
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Yorumlar: ${idea.title}",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .heightIn(max = 320.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (comments.isEmpty()) {
                    item {
                        Text(
                            text = "Henüz yorum yok. İlk yorumu siz yapın!",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8),
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                }

                itemsIndexed(
                    items = comments,
                    key = { index, comment -> "${comment.id.ifBlank { "comment" }}_$index" }
                ) { _, comment ->
                    val canDelete = isAdmin || comment.authorId == currentUserProfile?.userId

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = comment.authorName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = ElectricBlue
                                    )
                                    if (comment.authorRoles.isNotEmpty()) {
                                        UserRolesRow(roles = comment.authorRoles.take(1))
                                    }
                                }

                                if (canDelete) {
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                repository.deleteIdeaComment(idea.id, comment.id)
                                                Toast.makeText(context, "Yorum silindi", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Yorumu Sil", tint = Color(0xFFEF4444), modifier = Modifier.size(14.dp))
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = comment.text,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = commentText,
                    onValueChange = { commentText = it },
                    placeholder = { Text("Yorumunuzu yazın...", fontSize = 13.sp) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(20.dp)
                )

                IconButton(
                    onClick = {
                        if (commentText.isNotBlank()) {
                            val textToSend = commentText.trim()
                            val realName = currentUserProfile?.getFullName() ?: "Takım Üyesi"
                            val roles = currentUserProfile?.roles ?: emptyList()
                            commentText = ""
                            scope.launch {
                                repository.addIdeaComment(
                                    ideaId = idea.id,
                                    text = textToSend,
                                    authorName = realName,
                                    authorRoles = roles
                                )
                            }
                        }
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .background(ElectricBlue, CircleShape)
                ) {
                    Icon(imageVector = Icons.Default.Send, contentDescription = "Gönder", tint = Color.White)
                }
            }
        }
    }
}
