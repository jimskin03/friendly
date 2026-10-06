package app.friendly.assistant.ui.components.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Folder01
import me.rerere.hugeicons.stroke.Tick01
import app.friendly.assistant.R
import app.friendly.assistant.data.model.Folder
import app.friendly.assistant.data.model.FolderLabel
import kotlin.uuid.Uuid

@Composable
fun FolderBadge(
    label: FolderLabel,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
    iconSize: Dp = 20.dp,
    shapeRadius: Dp = 12.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(shapeRadius))
            .background(label.color),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = label.icon,
            contentDescription = label.title,
            tint = Color.White,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
fun FolderLabelPicker(
    selected: FolderLabel?,
    onSelect: (FolderLabel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val labels = remember { FolderLabel.entries }
    // Split into 2 rows of 5
    val row1 = labels.take(5)
    val row2 = labels.drop(5)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            row1.forEach { item ->
                LabelPickerItem(
                    label = item,
                    selected = item == selected,
                    onClick = { onSelect(item) },
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            row2.forEach { item ->
                LabelPickerItem(
                    label = item,
                    selected = item == selected,
                    onClick = { onSelect(item) },
                )
            }
        }
    }
}

@Composable
private fun LabelPickerItem(
    label: FolderLabel,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(shape)
            .background(
                if (selected) label.color else label.color.copy(alpha = 0.18f)
            )
            .then(
                if (selected) {
                    Modifier.border(2.dp, Color.White, shape)
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = label.icon,
            contentDescription = label.title,
            tint = if (selected) Color.White else label.color,
            modifier = Modifier.size(22.dp),
        )
        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(2.dp)
                    .size(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = HugeIcons.Tick01,
                    contentDescription = null,
                    tint = label.color,
                    modifier = Modifier.size(10.dp),
                )
            }
        }
    }
}

@Composable
fun CreateFolderDialog(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    onConfirm: (name: String, label: FolderLabel) -> Unit,
    initialName: String = "",
    initialLabel: FolderLabel = FolderLabel.PLANNING,
) {
    if (!visible) return

    var name by remember(visible) { mutableStateOf(initialName) }
    var selectedLabel by remember(visible) { mutableStateOf(initialLabel) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(stringResource(R.string.chat_page_create_folder))
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.chat_page_folder_name)) },
                    placeholder = { Text(stringResource(R.string.chat_page_folder_name)) },
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Assign label",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = selectedLabel.color.copy(alpha = 0.15f),
                    ) {
                        Text(
                            text = selectedLabel.title,
                            style = MaterialTheme.typography.labelMedium,
                            color = selectedLabel.color,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }

                FolderLabelPicker(
                    selected = selectedLabel,
                    onSelect = { selectedLabel = it },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(name, selectedLabel)
                    onDismissRequest()
                },
                enabled = name.isNotBlank(),
            ) {
                Text(stringResource(R.string.chat_page_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.chat_page_cancel))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoveToFolderSheet(
    folders: List<Folder>,
    currentFolderId: Uuid?,
    onDismissRequest: () -> Unit,
    onSelectFolder: (Uuid?) -> Unit,
    onCreateNewFolder: (name: String, labelId: String) -> Unit,
) {
    var showCreateDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Save to folder",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                )
                TextButton(onClick = { showCreateDialog = true }) {
                    Icon(HugeIcons.Add01, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.size(4.dp))
                    Text("New folder")
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
            ) {
                // Unfiled / Remove from folder option
                item {
                    Surface(
                        onClick = {
                            onSelectFolder(null)
                            onDismissRequest()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        color = Color.Transparent,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    HugeIcons.Folder01,
                                    null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            Text(
                                text = "None (Unfiled)",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            if (currentFolderId == null) {
                                Icon(
                                    HugeIcons.Tick01,
                                    null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }

                // Existing folders
                items(folders, key = { it.id.toString() }) { folder ->
                    val label = FolderLabel.fromId(folder.label)
                    val isSelected = folder.id == currentFolderId
                    Surface(
                        onClick = {
                            onSelectFolder(folder.id)
                            onDismissRequest()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        color = Color.Transparent,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            FolderBadge(
                                label = label,
                                size = 36.dp,
                                iconSize = 18.dp,
                                shapeRadius = 10.dp,
                            )
                            Text(
                                text = folder.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier.weight(1f),
                            )
                            if (isSelected) {
                                Icon(
                                    HugeIcons.Tick01,
                                    null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateFolderDialog(
            visible = true,
            onDismissRequest = { showCreateDialog = false },
            onConfirm = { name, label ->
                onCreateNewFolder(name, label.id)
                showCreateDialog = false
            }
        )
    }
}

@Composable
fun FolderEmptyChatView(
    folderName: String,
    folderLabel: FolderLabel,
    onStarterClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(modifier = Modifier.height(28.dp))
        FolderBadge(
            label = folderLabel,
            size = 68.dp,
            iconSize = 34.dp,
            shapeRadius = 22.dp,
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "New chat in $folderName",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Messages in this session are saved to \"$folderName\".",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        val suggestions = remember(folderLabel) {
            when (folderLabel.id) {
                "travel" -> listOf(
                    "Help me plan a 5-day itinerary",
                    "Packing list for an upcoming trip",
                    "Top hidden gems to visit",
                )
                "finance" -> listOf(
                    "Help me budget my monthly expenses",
                    "Analyze investment strategies",
                    "Track my savings goal progress",
                )
                "study" -> listOf(
                    "Explain this topic in simple terms",
                    "Create flashcards for revision",
                    "Summarize key study notes",
                )
                "work" -> listOf(
                    "Draft an executive summary",
                    "Help me organize project milestones",
                    "Write a professional follow-up email",
                )
                "health" -> listOf(
                    "Create a balanced meal plan",
                    "Design a 3-day workout routine",
                    "Tips for healthy daily habits",
                )
                "creative" -> listOf(
                    "Brainstorm creative story ideas",
                    "Help me write dialogue",
                    "Generate fresh concept outlines",
                )
                "code" -> listOf(
                    "Review my architecture design",
                    "Explain this algorithm",
                    "Help me debug an issue",
                )
                else -> listOf(
                    "Brainstorm ideas for this project",
                    "Draft an outline or plan",
                    "Ask any question to get started",
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            suggestions.forEach { prompt ->
                Surface(
                    onClick = { onStarterClick(prompt) },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = prompt,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            imageVector = HugeIcons.ArrowRight01,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}
