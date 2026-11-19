package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.TeamType
import com.example.model.UserProfile
import com.example.ui.theme.blackseaColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileDialog(
    currentProfile: UserProfile,
    activeSubTeam: String = "",
    showSubTeamPicker: Boolean = false,
    isSaving: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (firstName: String, lastName: String, username: String, subTeam: String) -> Unit
) {
    val ext = MaterialTheme.blackseaColors

    var editFirstName by remember(currentProfile) {
        val fallbackFirst = currentProfile.firstName.ifBlank {
            currentProfile.displayName.trim().substringBefore(" ", "")
        }
        mutableStateOf(fallbackFirst)
    }
    var editLastName by remember(currentProfile) {
        val fallbackLast = currentProfile.lastName.ifBlank {
            val parts = currentProfile.displayName.trim().split(" ", limit = 2)
            parts.getOrElse(1) { "" }
        }
        mutableStateOf(fallbackLast)
    }
    var editUsername by remember(currentProfile) { mutableStateOf(currentProfile.username) }
    var editTeam by remember(currentProfile, activeSubTeam) {
        mutableStateOf(activeSubTeam.ifBlank { currentProfile.team.ifBlank { TeamType.GENERAL.displayName } })
    }
    var teamExpanded by remember { mutableStateOf(false) }

    val trimmedFirst = editFirstName.trim()
    val trimmedLast = editLastName.trim()
    val isFormValid = trimmedFirst.isNotBlank() && trimmedLast.isNotBlank()

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = {
            Text(
                text = "Profili Düzenle",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = editFirstName,
                    onValueChange = { if (it.length <= 50) editFirstName = it },
                    label = { Text("Ad") },
                    singleLine = true,
                    isError = editFirstName.isNotEmpty() && trimmedFirst.isBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_first_name_input"),
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = editLastName,
                    onValueChange = { if (it.length <= 50) editLastName = it },
                    label = { Text("Soyad") },
                    singleLine = true,
                    isError = editLastName.isNotEmpty() && trimmedLast.isBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_last_name_input"),
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = editUsername,
                    onValueChange = { if (it.length <= 40) editUsername = it },
                    label = { Text("Kullanıcı Adı") },
                    placeholder = { Text("Örn: ali.robotik") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_username_input"),
                    shape = RoundedCornerShape(10.dp)
                )

                if (showSubTeamPicker) {
                    ExposedDropdownMenuBox(
                        expanded = teamExpanded,
                        onExpandedChange = { teamExpanded = !teamExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = editTeam,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Takım İçi Alt Ekip") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = teamExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                            shape = RoundedCornerShape(10.dp)
                        )

                        ExposedDropdownMenu(
                            expanded = teamExpanded,
                            onDismissRequest = { teamExpanded = false }
                        ) {
                            listOf(
                                TeamType.GENERAL,
                                TeamType.MANAGEMENT,
                                TeamType.SOFTWARE,
                                TeamType.MECHANIC,
                                TeamType.PR
                            ).forEach { team ->
                                DropdownMenuItem(
                                    text = { Text(team.displayName) },
                                    onClick = {
                                        editTeam = team.displayName
                                        teamExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isFormValid && !isSaving) {
                        onSave(
                            trimmedFirst,
                            trimmedLast,
                            editUsername.trim(),
                            if (showSubTeamPicker) editTeam else ""
                        )
                    }
                },
                enabled = isFormValid && !isSaving,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ext.accentBlueSolid,
                    contentColor = Color.White
                ),
                modifier = Modifier.testTag("save_profile_button")
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Kaydet", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isSaving
            ) {
                Text("İptal")
            }
        },
        modifier = Modifier.testTag("edit_profile_dialog")
    )
}
