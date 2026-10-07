package app.friendly.assistant.ui.pages.chat

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.friendly.assistant.R
import app.friendly.assistant.Screen
import app.friendly.assistant.data.model.Avatar
import app.friendly.assistant.data.model.Folder
import app.friendly.assistant.data.model.FolderLabel
import app.friendly.assistant.data.model.HomeAction
import app.friendly.assistant.data.model.HomeActionKind
import app.friendly.assistant.data.model.normalizeHomeActions
import app.friendly.assistant.service.ChatService
import app.friendly.assistant.ui.components.ui.FolderBadge
import app.friendly.assistant.ui.components.ui.UIAvatar
import app.friendly.assistant.ui.context.LocalNavController
import app.friendly.assistant.ui.context.LocalSettings
import app.friendly.assistant.ui.theme.CatHomeStyleId
import app.friendly.assistant.ui.theme.HomeChrome
import app.friendly.assistant.ui.theme.rememberHomeChrome
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Favourite
import me.rerere.hugeicons.stroke.File02
import me.rerere.hugeicons.stroke.FolderAdd
import me.rerere.hugeicons.stroke.Grid
import me.rerere.hugeicons.stroke.Idea01
import me.rerere.hugeicons.stroke.Search01
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.hugeicons.stroke.Sparkles
import me.rerere.hugeicons.stroke.Zap
import org.koin.compose.koinInject
import java.util.Calendar

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

private fun Modifier.homeCardShadow(chrome: HomeChrome): Modifier {
    return if (chrome.useSoftShadow) {
        this.shadow(
            elevation = 8.dp,
            shape = RoundedCornerShape(20.dp),
            ambientColor = Color(0x22000000),
            spotColor = Color(0x18000000),
        )
    } else {
        this
    }
}

private fun homeCardShape(chrome: HomeChrome): RoundedCornerShape {
    return if (chrome.useDoodleBorder) {
        RoundedCornerShape(
            topStart = 22.dp,
            topEnd = 28.dp,
            bottomStart = 26.dp,
            bottomEnd = 18.dp,
        )
    } else {
        RoundedCornerShape(20.dp)
    }
}

@Composable
fun AssistantHome(
    userNickname: String,
    userAvatar: Avatar = Avatar.Dummy,
    assistantName: String = "",
    assistantAvatar: Avatar = Avatar.Dummy,
    folders: List<Folder>,
    onSelectFolder: (Folder) -> Unit,
    onSeeAllFolders: () -> Unit,
    onStarterClick: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenActivity: () -> Unit,
    onOpenAssistant: () -> Unit,
    onOpenSettings: () -> Unit,
    onNewFolder: () -> Unit,
    onOpenFavorite: () -> Unit,
    onQuickCreateFolder: (name: String, labelId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val chrome = rememberHomeChrome()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DashboardHeader(
            nickname = userNickname,
            userAvatar = userAvatar,
            assistantName = assistantName,
            onOpenAssistant = onOpenAssistant,
            onOpenSettings = onOpenSettings,
            chrome = chrome,
        )

        chrome.peekRes?.let { peek ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .offset(y = 8.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Image(
                    painter = painterResource(peek),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth(0.72f)
                        .height(96.dp),
                )
            }
        }

        ShortcutCards(
            onOpenSearch = onOpenSearch,
            onOpenActivity = onOpenActivity,
            chrome = chrome,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            FoldersSection(
                folders = folders,
                onSelectFolder = onSelectFolder,
                onSeeAllFolders = onSeeAllFolders,
                onNewFolder = onNewFolder,
                onOpenFavorite = onOpenFavorite,
                onQuickCreateFolder = onQuickCreateFolder,
                chrome = chrome,
                modifier = Modifier.weight(1f),
            )
            AppsHomeCard(
                chrome = chrome,
                modifier = Modifier.weight(1f),
            )
        }

        ActionStartersRow(
            onStarterClick = onStarterClick,
            chrome = chrome,
        )
    }
}

@Composable
private fun DashboardHeader(
    nickname: String,
    userAvatar: Avatar = Avatar.Dummy,
    assistantName: String = "",
    onOpenAssistant: () -> Unit,
    onOpenSettings: () -> Unit,
    chrome: HomeChrome,
) {
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greeting = when (hour) {
        in 5..11 -> stringResource(R.string.menu_page_morning_greeting)
        in 12..17 -> stringResource(R.string.menu_page_afternoon_greeting)
        in 18..22 -> stringResource(R.string.menu_page_evening_greeting)
        else -> stringResource(R.string.menu_page_night_greeting)
    }
    val cleanGreeting = greeting.replace("👋", "").trim()
    val displayName = nickname.ifBlank { stringResource(R.string.user_default_name) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (chrome.headerImageRes != null) {
                    Modifier.height(168.dp)
                } else {
                    Modifier
                }
            ),
    ) {
        chrome.headerImageRes?.let { headerRes ->
            Image(
                painter = painterResource(headerRes),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0x66000000),
                                Color(0x99000000),
                            )
                        )
                    )
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .align(Alignment.TopCenter),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f),
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clickable(onClick = onOpenAssistant)
                ) {
                    if (userAvatar != Avatar.Dummy) {
                        UIAvatar(
                            name = displayName,
                            value = userAvatar,
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape),
                        )
                    } else if (chrome.styleId != CatHomeStyleId.NONE) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(chrome.avatarFill)
                                .border(2.dp, chrome.avatarRing, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(
                                painter = painterResource(R.drawable.theme_cat_paw_white),
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            Color(0xFFF472B6),
                                            Color(0xFFA855F7),
                                            Color(0xFFFBBF24),
                                            Color(0xFF38BDF8),
                                        )
                                    )
                                )
                        )
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(chrome.online)
                            .border(
                                2.dp,
                                if (chrome.darkChrome) Color(0xFF0F172A) else Color(0xFFFFFFF8),
                                CircleShape,
                            )
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "$cleanGreeting, $displayName 👋",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp,
                        ),
                        color = chrome.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = assistantName.ifBlank { "Your AI assistant" },
                        style = MaterialTheme.typography.bodySmall,
                        color = chrome.subtitle,
                        maxLines = 1,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.padding(top = 1.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(chrome.online)
                        )
                        Text(
                            text = "Online",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                            color = chrome.online,
                        )
                    }
                }
            }

            Surface(
                onClick = onOpenSettings,
                shape = CircleShape,
                color = chrome.settingsBg,
                border = BorderStroke(1.dp, chrome.settingsBorder),
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = HugeIcons.Settings03,
                        contentDescription = stringResource(R.string.settings),
                        tint = chrome.settingsIcon,
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
    chrome: HomeChrome,
) {
    val chatService = koinInject<ChatService>()
    val runningIds by chatService.runningConversationIds().collectAsStateWithLifecycle(emptySet())
    val queryRunning = runningIds.isNotEmpty()
    val flash = rememberInfiniteTransition(label = "activityFlash")
    val flashAlpha by flash.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 4000
                1f at 0
                1f at 2600
                0.15f at 3200
                1f at 4000
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "activityFlashAlpha",
    )

    val searchTint = when (chrome.styleId) {
        CatHomeStyleId.NONE -> Color(0xFF38BDF8)
        CatHomeStyleId.PLAYFUL_DOODLE -> Color(0xFF5C4030)
        CatHomeStyleId.CUTE_MINIMAL -> Color(0xFF6B5344)
        else -> chrome.accent
    }
    val activityTint = when (chrome.styleId) {
        CatHomeStyleId.NONE -> Color(0xFF818CF8)
        CatHomeStyleId.PLAYFUL_DOODLE -> Color(0xFF7B6BB0)
        CatHomeStyleId.CUTE_MINIMAL -> Color(0xFF8B6BB0)
        else -> chrome.accent
    }
    val searchCard = when (chrome.styleId) {
        CatHomeStyleId.PLAYFUL_DOODLE -> Color(0xFFFFF3D6)
        CatHomeStyleId.CUTE_MINIMAL -> chrome.card
        else -> chrome.card
    }
    val activityCard = when (chrome.styleId) {
        CatHomeStyleId.PLAYFUL_DOODLE -> Color(0xFFEDE6FF)
        CatHomeStyleId.CUTE_MINIMAL -> chrome.card
        else -> chrome.card
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            onClick = onOpenSearch,
            modifier = Modifier
                .weight(1f)
                .homeCardShadow(chrome),
            shape = RoundedCornerShape(18.dp),
            color = searchCard,
            border = BorderStroke(1.dp, if (chrome.styleId == CatHomeStyleId.NONE) Color(0x3338BDF8) else chrome.cardBorder),
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
                        .border(1.dp, searchTint.copy(alpha = 0.45f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = HugeIcons.Search01,
                        contentDescription = null,
                        tint = searchTint,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Search",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = chrome.title,
                        maxLines = 1,
                    )
                    Text(
                        text = "Find anything",
                        style = MaterialTheme.typography.bodySmall,
                        color = chrome.subtitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    imageVector = HugeIcons.ArrowRight01,
                    contentDescription = null,
                    tint = chrome.chevron,
                    modifier = Modifier.size(14.dp),
                )
            }
        }

        Surface(
            onClick = onOpenActivity,
            modifier = Modifier
                .weight(1f)
                .homeCardShadow(chrome),
            shape = RoundedCornerShape(18.dp),
            color = activityCard,
            border = BorderStroke(1.dp, if (chrome.styleId == CatHomeStyleId.NONE) Color(0x33818CF8) else chrome.cardBorder),
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
                        .border(1.dp, activityTint.copy(alpha = 0.45f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = HugeIcons.Zap,
                        contentDescription = null,
                        tint = activityTint,
                        modifier = Modifier
                            .size(18.dp)
                            .alpha(if (queryRunning) flashAlpha else 1f),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Activity",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = chrome.title,
                        maxLines = 1,
                    )
                    Text(
                        text = "View recent work",
                        style = MaterialTheme.typography.bodySmall,
                        color = chrome.subtitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    imageVector = HugeIcons.ArrowRight01,
                    contentDescription = null,
                    tint = chrome.chevron,
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
    onNewFolder: () -> Unit,
    onOpenFavorite: () -> Unit,
    onQuickCreateFolder: (name: String, labelId: String) -> Unit,
    chrome: HomeChrome,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.homeCardShadow(chrome),
        shape = homeCardShape(chrome),
        color = chrome.card,
        border = BorderStroke(
            width = if (chrome.useDoodleBorder) 1.5.dp else 1.dp,
            color = chrome.cardBorder,
        ),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "Folders",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = chrome.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = HugeIcons.FolderAdd,
                        contentDescription = "New folder",
                        tint = chrome.muted,
                        modifier = Modifier
                            .size(22.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onNewFolder)
                            .padding(2.dp),
                    )
                    Icon(
                        imageVector = HugeIcons.Favourite,
                        contentDescription = "Favorite",
                        tint = chrome.muted,
                        modifier = Modifier
                            .size(22.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onOpenFavorite)
                            .padding(2.dp),
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onSeeAllFolders() }
                            .padding(start = 4.dp),
                    ) {
                        Text(
                            text = "See all",
                            style = MaterialTheme.typography.labelMedium,
                            color = chrome.muted,
                        )
                        Icon(
                            imageVector = HugeIcons.ArrowRight01,
                            contentDescription = null,
                            tint = chrome.muted,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            if (folders.isNotEmpty()) {
                folders.take(6).forEachIndexed { index, folder ->
                    val label = FolderLabel.fromId(folder.label)
                    val timeString = remember(folder.createAt) { formatRelativeTime(index) }
                    FolderRowItem(
                        title = folder.name,
                        subtitle = "Saved chat sessions",
                        label = label,
                        time = timeString,
                        onClick = { onSelectFolder(folder) },
                        chrome = chrome,
                    )
                }
            } else {
                DefaultTemplateFolders.forEach { template ->
                    val label = FolderLabel.fromId(template.labelId)
                    FolderRowItem(
                        title = template.name,
                        subtitle = "Saved chat sessions",
                        label = label,
                        time = template.time,
                        onClick = { onQuickCreateFolder(template.name, template.labelId) },
                        chrome = chrome,
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
    chrome: HomeChrome,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                color = chrome.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = chrome.subtitle,
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
                color = chrome.time,
            )
            Icon(
                imageVector = HugeIcons.ArrowRight01,
                contentDescription = null,
                tint = chrome.chevron,
                modifier = Modifier.size(13.dp),
            )
        }
    }
}

@Composable
private fun AppsHomeCard(chrome: HomeChrome, modifier: Modifier = Modifier) {
    val navController = LocalNavController.current
    val settings = LocalSettings.current
    val favorites = if (settings.init) emptyList() else settings.installedWebApps.filter { it.favorite }.take(4)
    if (favorites.isEmpty()) {
        Surface(
            onClick = { navController.navigate(Screen.Apps) },
            modifier = modifier.homeCardShadow(chrome),
            shape = homeCardShape(chrome),
            color = chrome.card,
            border = BorderStroke(if (chrome.useDoodleBorder) 1.5.dp else 1.dp, chrome.cardBorder),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppsHomeTitle(chrome)
                Text(
                    text = stringResource(R.string.apps_home_card_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = chrome.subtitle,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    } else {
        Surface(
            modifier = modifier.homeCardShadow(chrome),
            shape = homeCardShape(chrome),
            color = chrome.card,
            border = BorderStroke(if (chrome.useDoodleBorder) 1.5.dp else 1.dp, chrome.cardBorder),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { navController.navigate(Screen.Apps) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppsHomeTitle(chrome)
                }
                favorites.forEach { app ->
                    val label = FolderLabel.entries
                        .firstOrNull { it.id.equals(app.iconId, ignoreCase = true) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { navController.navigate(Screen.WebApp(app.id.toString())) }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (label != null) {
                            FolderBadge(
                                label = label,
                                size = 28.dp,
                                iconSize = 14.dp,
                                shapeRadius = 8.dp,
                            )
                        }
                        Text(
                            text = app.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = chrome.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppsHomeTitle(chrome: HomeChrome) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = HugeIcons.Grid,
            contentDescription = null,
            tint = chrome.icon,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = stringResource(R.string.apps_page_title),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = chrome.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ActionStartersRow(
    onStarterClick: (String) -> Unit,
    chrome: HomeChrome,
) {
    val navController = LocalNavController.current
    val settings = LocalSettings.current
    val actions = if (settings.init) {
        normalizeHomeActions(emptyList())
    } else {
        normalizeHomeActions(settings.homeActions)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val promptFallback = stringResource(R.string.home_action_prompt)
        val launchFallback = stringResource(R.string.home_action_launch_app)
        actions.forEachIndexed { index, action ->
            val pastel = chrome.actionPastels.getOrNull(index)
            val bg = when {
                chrome.usePastelActions && pastel != null -> pastel
                else -> chrome.card
            }
            Surface(
                onClick = {
                    when (action.kind) {
                        HomeActionKind.PROMPT -> {
                            if (action.prompt.isNotBlank()) onStarterClick(action.prompt)
                        }
                        HomeActionKind.LAUNCH_APP -> {
                            if (action.appId.isNotBlank()) {
                                navController.navigate(Screen.WebApp(action.appId))
                            }
                        }
                    }
                },
                shape = RoundedCornerShape(16.dp),
                color = bg,
                border = BorderStroke(1.dp, chrome.cardBorder),
                modifier = Modifier
                    .weight(1f)
                    .homeCardShadow(chrome),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = homeActionIcon(action),
                        contentDescription = null,
                        tint = chrome.icon,
                        modifier = Modifier.size(18.dp),
                    )
                    val chipLabel = action.label.ifBlank {
                        if (action.kind == HomeActionKind.LAUNCH_APP) launchFallback else promptFallback
                    }
                    Text(
                        text = chipLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = chrome.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

private fun homeActionIcon(action: HomeAction): ImageVector {
    if (action.kind == HomeActionKind.LAUNCH_APP) return HugeIcons.Grid
    return when (action.label) {
        "Summarize" -> HugeIcons.File02
        "Look up" -> HugeIcons.Search01
        "Brainstorm" -> HugeIcons.Idea01
        else -> HugeIcons.Sparkles
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
