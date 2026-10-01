package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Tick01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.FolderLabel

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
    selected: FolderLabel,
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
