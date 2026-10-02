package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ChartColumn
import me.rerere.hugeicons.stroke.MoreHorizontal
import me.rerere.hugeicons.stroke.Settings03

/**
 * Minimal home bottom bar. Former New folder / Assistant / Favorite / Analyze
 * entry points were relocated; only More remains here, which opens a sheet
 * that includes Analyze (same destination as before) plus Settings.
 */
@Composable
fun HomeBottomBar(
    onAnalyze: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showMoreSheet by remember { mutableStateOf(false) }

    Surface(
        color = Color.Transparent,
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HomeBottomNavItem(
                icon = HugeIcons.MoreHorizontal,
                label = "More",
                onClick = { showMoreSheet = true },
            )
        }
    }

    if (showMoreSheet) {
        HomeMoreSheet(
            onDismissRequest = { showMoreSheet = false },
            onAnalyze = {
                showMoreSheet = false
                onAnalyze()
            },
            onSettings = {
                showMoreSheet = false
                onSettings()
            },
        )
    }
}

@Composable
private fun HomeMoreSheet(
    onDismissRequest: () -> Unit,
    onAnalyze: () -> Unit,
    onSettings: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = "More",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            ListItem(
                headlineContent = { Text("Analyze") },
                supportingContent = { Text("Usage and activity stats") },
                leadingContent = {
                    Icon(
                        imageVector = HugeIcons.ChartColumn,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onAnalyze)
                    .padding(horizontal = 8.dp),
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            ListItem(
                headlineContent = { Text("Settings") },
                supportingContent = { Text("App preferences and account") },
                leadingContent = {
                    Icon(
                        imageVector = HugeIcons.Settings03,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onSettings)
                    .padding(horizontal = 8.dp),
            )
        }
    }
}

@Composable
private fun HomeBottomNavItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color(0xFF94A3B8),
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = Color(0xFF94A3B8),
            maxLines = 1,
        )
    }
}
