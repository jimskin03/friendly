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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ChartColumn
import me.rerere.hugeicons.stroke.Favourite
import me.rerere.hugeicons.stroke.Folder01
import me.rerere.hugeicons.stroke.MoreHorizontal
import me.rerere.hugeicons.stroke.Smile

@Composable
fun HomeBottomBar(
    onNewFolder: () -> Unit,
    onAssistant: () -> Unit,
    onAnalyze: () -> Unit,
    onFavorite: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HomeBottomNavItem(
                icon = HugeIcons.Folder01,
                label = "New folder",
                onClick = onNewFolder,
            )
            HomeBottomNavItem(
                icon = HugeIcons.Smile,
                label = "Assistant",
                onClick = onAssistant,
            )
            HomeBottomNavItem(
                icon = HugeIcons.ChartColumn,
                label = "Analyze",
                onClick = onAnalyze,
            )
            HomeBottomNavItem(
                icon = HugeIcons.Favourite,
                label = "Favorite",
                onClick = onFavorite,
            )
            HomeBottomNavItem(
                icon = HugeIcons.MoreHorizontal,
                label = "More",
                onClick = onMore,
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
