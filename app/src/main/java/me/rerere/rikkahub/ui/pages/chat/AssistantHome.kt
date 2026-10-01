package me.rerere.rikkahub.ui.pages.chat

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.File02
import me.rerere.hugeicons.stroke.Idea01
import me.rerere.hugeicons.stroke.Search01
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.hugeicons.stroke.Sparkles
import me.rerere.hugeicons.stroke.Zap
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.Folder
import me.rerere.rikkahub.data.model.FolderLabel
import me.rerere.rikkahub.ui.components.ui.FolderBadge
import java.util.Calendar
import kotlin.uuid.Uuid

data class AssistantStarter(
    val label: String,
    val prompt: String,
    val icon: ImageVector = HugeIcons.Sparkles,
)

private data class TemplateFolder(
    val name: String,
    val subtitle: String,
    val labelId: String,
    val time: String,
)

private val DefaultTemplateFolders = listOf(
    TemplateFolder("Project planning", "Strategy and next steps", "planning", "2h"),
    TemplateFolder("Research summary", "Key findings and insights", "research", "5h"),
    TemplateFolder("Code review", "Fixed issues and suggestions", "code", "1d"),
    TemplateFolder("Travel itinerary", "Tokyo, 5 days plan", "travel", "2d"),
    TemplateFolder("Fitness & health", "Routine and nutrition tips", "health", "3d"),
    TemplateFolder("Side project ideas", "Web app concepts", "project", "4d"),
)

@Composable
fun AssistantHome(
    userNickname: String,
    folders: List<Folder>,
    onSelectFolder: (Folder) -> Unit,
    onSeeAllFolders: () -> Unit,
    onStarterClick: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenActivity: () -> Unit,
    onOpenSettings: () -> Unit,
    onQuickCreateFolder: (name: String, labelId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Top Header
        DashboardHeader(
            nickname = userNickname,
            onOpenSettings = onOpenSettings,
        )

        // Shortcut Cards: Search & Activity
        ShortcutCards(
            onOpenSearch = onOpenSearch,
            onOpenActivity = onOpenActivity,
        )

        // Folders Section
        FoldersSection(
            folders = folders,
            onSelectFolder = onSelectFolder,
            onSeeAllFolders = onSeeAllFolders,
            onQuickCreateFolder = onQuickCreateFolder,
        )

        // Quick Action Starters
        ActionStartersRow(
            onStarterClick = onStarterClick,
        )
    }
}

@Composable
private fun DashboardHeader(
    nickname: String,
    onOpenSettings: () -> Unit,
) {
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greeting = when (hour) {
        in 5..11 -> stringResource(R.string.menu_page_morning_greeting)
        in 12..17 -> stringResource(R.string.menu_page_afternoon_greeting)
        in 18..22 -> stringResource(R.string.menu_page_evening_greeting)
        else -> stringResource(R.string.menu_page_night_greeting)
    }
    val displayName = nickname.ifBlank { "Khai" }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f),
            ) {
                // Gradient orb avatar with online indicator
                Box(modifier = Modifier.size(54.dp)) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFFF472B6), // Pink
                                        Color(0xFFA855F7), // Purple
                                        Color(0xFFFBBF24), // Peach/Amber
                                        Color(0xFF38BDF8), // Cyan
                                    )
                                )
                            )
                    )

                    // Green online dot badge
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981))
                            .border(2.dp, Color(0xFF0F172A), CircleShape)
                    )
                }

                // Greeting & Status
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "$greeting, $displayName",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 19.sp,
                            ),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "👋",
                            fontSize = 18.sp,
                        )
                    }

                    Text(
                        text = "Your AI assistant",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF94A3B8),
                        maxLines = 1,
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.padding(top = 1.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF10B981))
                        )
                        Text(
                            text = "Online",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                            color = Color(0xFF10B981),
                        )
                    }
                }
            }

            // Top Right Settings Button (Pro button removed)
            Surface(
                onClick = onOpenSettings,
                shape = CircleShape,
                color = Color(0x281E293B),
                border = BorderStroke(1.dp, Color(0x2294A3B8)),
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = HugeIcons.Settings03,
                        contentDescription = stringResource(R.string.settings),
                        tint = Color(0xFFCBD5E1),
                        modifier = Modifier.size(19.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ShortcutCards(
    onOpenSearch: () -> Unit,
    onOpenActivity: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Search Card
        Surface(
            onClick = onOpenSearch,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(18.dp),
            color = Color(0x221E293B),
            border = BorderStroke(1.dp, Color(0x3338BDF8)),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .border(1.dp, Color(0x6638BDF8), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = HugeIcons.Search01,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(18.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Search",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White,
                        maxLines = 1,
                    )
                    Text(
                        text = "Find anything",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF94A3B8),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    imageVector = HugeIcons.ArrowRight01,
                    contentDescription = null,
                    tint = Color(0xFF64748B),
                    modifier = Modifier.size(14.dp),
                )
            }
        }

        // Activity Card
        Surface(
            onClick = onOpenActivity,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(18.dp),
            color = Color(0x221E293B),
            border = BorderStroke(1.dp, Color(0x33818CF8)),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .border(1.dp, Color(0x66818CF8), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = HugeIcons.Zap,
                        contentDescription = null,
                        tint = Color(0xFF818CF8),
                        modifier = Modifier.size(18.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Activity",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White,
                        maxLines = 1,
                    )
                    Text(
                        text = "View recent work",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF94A3B8),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    imageVector = HugeIcons.ArrowRight01,
                    contentDescription = null,
                    tint = Color(0xFF64748B),
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
private fun FoldersSection(
    folders: List<Folder>,
    onSelectFolder: (Folder) -> Unit,
    onSeeAllFolders: () -> Unit,
    onQuickCreateFolder: (name: String, labelId: String) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        color = Color(0x221E293B),
        border = BorderStroke(1.dp, Color(0x1FFFFFFF)),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Folders Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Folders",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.clickable { onSeeAllFolders() },
                ) {
                    Text(
                        text = "See all",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF94A3B8),
                    )
                    Icon(
                        imageVector = HugeIcons.ArrowRight01,
                        contentDescription = null,
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(12.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            if (folders.isNotEmpty()) {
                folders.take(6).forEachIndexed { index, folder ->
                    val label = FolderLabel.fromId(folder.label)
                    val timeString = remember(folder.createAt) {
                        formatRelativeTime(index)
                    }
                    FolderRowItem(
                        title = folder.name,
                        subtitle = "Saved chat sessions",
                        label = label,
                        time = timeString,
                        onClick = { onSelectFolder(folder) },
                    )
                }
            } else {
                // If user has not created custom folders, show the default template items
                DefaultTemplateFolders.forEach { template ->
                    val label = FolderLabel.fromId(template.labelId)
                    FolderRowItem(
                        title = template.name,
                        subtitle = "Saved chat sessions",
                        label = label,
                        time = template.time,
                        onClick = {
                            onQuickCreateFolder(template.name, template.labelId)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun FolderRowItem(
    title: String,
    subtitle: String,
    label: FolderLabel,
    time: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        FolderBadge(
            label = label,
            size = 40.dp,
            iconSize = 20.dp,
            shapeRadius = 12.dp,
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF94A3B8),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = time,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF64748B),
            )
            Icon(
                imageVector = HugeIcons.ArrowRight01,
                contentDescription = null,
                tint = Color(0xFF475569),
                modifier = Modifier.size(13.dp),
            )
        }
    }
}

@Composable
private fun ActionStartersRow(
    onStarterClick: (String) -> Unit,
) {
    val starters = listOf(
        AssistantStarter(
            label = "Plan my day",
            prompt = "Help me plan my day with a clear schedule and priorities.",
            icon = HugeIcons.Sparkles,
        ),
        AssistantStarter(
            label = "Summarize",
            prompt = "Please summarize the following text or documents:",
            icon = HugeIcons.File02,
        ),
        AssistantStarter(
            label = "Look up",
            prompt = "Look up detailed information about ",
            icon = HugeIcons.Search01,
        ),
        AssistantStarter(
            label = "Brainstorm",
            prompt = "Brainstorm creative and effective ideas for ",
            icon = HugeIcons.Idea01,
        ),
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        starters.forEach { starter ->
            Surface(
                onClick = { onStarterClick(starter.prompt) },
                shape = RoundedCornerShape(16.dp),
                color = Color(0x221E293B),
                border = BorderStroke(1.dp, Color(0x1FFFFFFF)),
                modifier = Modifier.weight(1f),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = starter.icon,
                        contentDescription = null,
                        tint = Color(0xFFCBD5E1),
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = starter.label,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = Color(0xFFE2E8F0),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

private fun formatRelativeTime(index: Int): String {
    return when (index) {
        0 -> "2h"
        1 -> "5h"
        2 -> "1d"
        3 -> "2d"
        4 -> "3d"
        else -> "${index}d"
    }
}
