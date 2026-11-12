package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BlackseaRepository
import com.example.model.SharedProjectFile
import com.example.model.UserProfile
import com.example.ui.components.FileUploadProgressDialog
import com.example.ui.components.InAppFileViewerDialog
import com.example.ui.components.SharedFileCard
import com.example.ui.components.openFileExternally
import com.example.util.downloadFileToDevice
import com.example.ui.theme.ElectricBlue
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(
    repository: BlackseaRepository,
    currentUserProfile: UserProfile?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedTypeFilter by remember { mutableStateOf("Hepsi") }
    val files by repository.observeSharedFiles().collectAsState(initial = emptyList())

    // File Preview Modal
    var activePreviewFile by remember { mutableStateOf<SharedProjectFile?>(null) }

    // Real File Upload State
    var pickedFileUri by remember { mutableStateOf<Uri?>(null) }
    var pickedFileName by remember { mutableStateOf("") }
    var pickedFileSizeFormatted by remember { mutableStateOf("") }
    var pickedFileType by remember { mutableStateOf("other") }
    var isUploading by remember { mutableStateOf(false) }
    var uploadProgress by remember { mutableIntStateOf(0) }
    var uploadErrorMessage by remember { mutableStateOf<String?>(null) }
    var showUploadModal by remember { mutableStateOf(false) }

    // Real Device File Picker Launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            pickedFileUri = uri
            val (name, size) = repository.queryFileMetadata(context, uri)
            val type = repository.detectFileType(name)
            pickedFileName = name
            pickedFileType = type

            val kb = size / 1024.0
            val mb = kb / 1024.0
            pickedFileSizeFormatted = if (mb >= 1.0) {
                String.format(java.util.Locale.US, "%.1f MB", mb)
            } else if (kb >= 1.0) {
                String.format(java.util.Locale.US, "%.0f KB", kb)
            } else {
                "$size B"
            }

            uploadErrorMessage = null
            uploadProgress = 0
            isUploading = false
            showUploadModal = true
        }
    }

    val fileTypes = listOf("Hepsi", "Kod", "Görsel", "Video", "Ses", "PDF", "CAD", "ZIP", "Belge")

    val filteredFiles = files.filter { file ->
        when (selectedTypeFilter) {
            "Hepsi" -> true
            "Kod" -> file.fileType == "code"
            "Görsel" -> file.fileType == "image"
            "Video" -> file.fileType == "video"
            "Ses" -> file.fileType == "audio"
            "PDF" -> file.fileType == "pdf"
            "CAD" -> file.fileType == "cad"
            "ZIP" -> file.fileType == "zip"
            "Belge" -> file.fileType == "doc" || file.fileType == "text"
            else -> true
        }
    }

    val isManager = currentUserProfile?.isSuperAdmin == true ||
            currentUserProfile?.email.equals("filim3290@gmail.com", ignoreCase = true) ||
            currentUserProfile?.roles?.any { it in listOf("Yönetici", "Takım Kaptanı", "Yetkili Öğretmen") } == true

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("files_screen")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header Tabs
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp
            ) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(fileTypes) { type ->
                            FilterChip(
                                selected = selectedTypeFilter == type,
                                onClick = { selectedTypeFilter = type },
                                label = { Text(type, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ElectricBlue,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }
            }

            if (filteredFiles.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(52.dp)
                        )
                        Text(
                            text = "Henüz paylaşılan bir dosya yok",
                            color = Color(0xFF94A3B8),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Aşağıdaki butona basarak cihazınızdan kod, CAD veya PDF yükleyin",
                            color = Color(0xFF64748B),
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredFiles) { file ->
                        val canDelete = isManager || file.uploadedById == currentUserProfile?.userId || file.uploadedById == currentUserProfile?.id
                        SharedFileCard(
                            file = file,
                            onOpenFile = { activePreviewFile = it },
                            onDownloadFile = {
                                downloadFileToDevice(context, it.fileName, it.downloadUrl, it.content)
                            },
                            canDelete = canDelete,
                            onDelete = {
                                scope.launch {
                                    repository.deleteSharedFile(file.id)
                                    Toast.makeText(context, "Dosya silindi", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            }
        }

        // Floating Action Button to pick & upload real device file
        FloatingActionButton(
            onClick = {
                filePickerLauncher.launch("*/*")
            },
            containerColor = ElectricBlue,
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("upload_file_fab")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(imageVector = Icons.Default.UploadFile, contentDescription = "Dosya Yükle")
                Text("Dosya Ekle", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        // In-App File Viewer Dialog
        if (activePreviewFile != null) {
            InAppFileViewerDialog(
                file = activePreviewFile!!,
                onDismiss = { activePreviewFile = null }
            )
        }

        // File Upload Progress Modal
        if (showUploadModal && pickedFileUri != null) {
            FileUploadProgressDialog(
                fileName = pickedFileName,
                fileSizeFormatted = pickedFileSizeFormatted,
                fileType = pickedFileType,
                previewUri = pickedFileUri,
                isUploading = isUploading,
                uploadProgress = uploadProgress,
                errorMessage = uploadErrorMessage,
                onCancel = {
                    showUploadModal = false
                    pickedFileUri = null
                },
                onConfirmUpload = {
                    val uri = pickedFileUri ?: return@FileUploadProgressDialog
                    scope.launch {
                        isUploading = true
                        uploadErrorMessage = null
                        try {
                            val uploadResult = repository.uploadFile(
                                context = context,
                                uri = uri,
                                folder = "workspace_files",
                                onProgress = { prog -> uploadProgress = prog }
                            )

                            repository.addSharedFile(
                                roomId = "general",
                                fileName = uploadResult.fileName,
                                fileType = uploadResult.fileType,
                                fileSize = uploadResult.fileSize,
                                description = "Çalışma dosyası",
                                content = uploadResult.textContent,
                                downloadUrl = uploadResult.downloadUrl,
                                storagePath = "",
                                uploadedByName = currentUserProfile?.getFullName() ?: "Takım Üyesi",
                                uploadedByTeam = currentUserProfile?.team ?: "",
                                uploadedByRoles = currentUserProfile?.roles ?: emptyList()
                            )

                            isUploading = false
                            showUploadModal = false
                            pickedFileUri = null
                            Toast.makeText(context, "Dosya başarıyla gönderildi!", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            isUploading = false
                            uploadErrorMessage = e.localizedMessage ?: "Dosya yüklenemedi"
                        }
                    }
                },
                onRetry = {
                    uploadErrorMessage = null
                }
            )
        }
    }
}
