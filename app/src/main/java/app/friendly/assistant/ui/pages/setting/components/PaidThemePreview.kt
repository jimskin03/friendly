package app.friendly.assistant.ui.pages.setting.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.friendly.assistant.R
import app.friendly.assistant.ui.pages.chat.catPhotoScrim
import app.friendly.assistant.ui.theme.HomeChrome
import app.friendly.assistant.ui.theme.PresetTheme
import app.friendly.assistant.ui.theme.catHomeStyleId
import app.friendly.assistant.ui.theme.homeChromeFor
import coil3.compose.AsyncImage
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Clock02
import me.rerere.hugeicons.stroke.SquareLock02
import me.rerere.hugeicons.stroke.Tick01

private const val PREVIEW_COLUMNS = 2
private val LightCatCanvas = Color(0xFFF7F1E6)

/** Lock state of one paid theme card. */
sealed interface PaidCardLock {
    data object None : PaidCardLock

    /** Not owned. [price] is the localized Play price, or null when unknown. */
    data class Locked(val price: String?) : PaidCardLock

    /** Payment started but not confirmed yet. */
    data object Pending : PaidCardLock

    /** Play purchase sheet is open for this theme. */
    data class Purchasing(val price: String?) : PaidCardLock
}

/**
 * Simple visual picker for the paid cat themes: a 2-column grid of rounded thumbnails, each a
 * tiny mock of that theme's home screen (photo / cream canvas + header, cards and input bar in
 * the theme's own colors), with the name and, while locked, the Play price underneath.
 */
@Composable
fun PaidThemePreviewGrid(
    themeId: String,
    themes: List<PresetTheme>,
    modifier: Modifier = Modifier,
    lockFor: (String) -> PaidCardLock = { PaidCardLock.None },
    onChangeTheme: (String) -> Unit,
    onLockedClick: (String) -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        themes.chunked(PREVIEW_COLUMNS).forEach { rowThemes ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                rowThemes.forEach { theme ->
                    key(theme.id) {
                        val lock = lockFor(theme.id)
                        PaidThemePreviewCard(
                            theme = theme,
                            selected = theme.id == themeId,
                            lock = lock,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                if (lock == PaidCardLock.None) onChangeTheme(theme.id) else onLockedClick(theme.id)
                            },
                        )
                    }
                }
                repeat(PREVIEW_COLUMNS - rowThemes.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PaidThemePreviewCard(
    theme: PresetTheme,
    selected: Boolean,
    lock: PaidCardLock,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val chrome = remember(theme.id) { homeChromeFor(catHomeStyleId(theme.id)) }
    val primary = MaterialTheme.colorScheme.primary
    val ringShape = RoundedCornerShape(22.dp)
    val thumbShape = RoundedCornerShape(18.dp)
    val locked = lock != PaidCardLock.None
    val active = selected && !locked

    Column(
        modifier = modifier
            .clip(ringShape)
            .selectable(selected = active, role = Role.RadioButton, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 2.dp,
                    color = if (active) primary else Color.Transparent,
                    shape = ringShape,
                )
                .padding(4.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.6f)
                    .clip(thumbShape)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), thumbShape),
            ) {
                ThemeMiniHome(chrome = chrome)

                if (locked) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color(0x99000000))
                            .border(0.5.dp, Color(0x40FFFFFF), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        when (lock) {
                            is PaidCardLock.Purchasing -> CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 1.5.dp,
                                modifier = Modifier.size(14.dp),
                            )

                            PaidCardLock.Pending -> Icon(
                                imageVector = HugeIcons.Clock02,
                                contentDescription = stringResource(R.string.setting_theme_page_paid_pending),
                                tint = Color.White,
                                modifier = Modifier.size(14.dp),
                            )

                            else -> Icon(
                                imageVector = HugeIcons.SquareLock02,
                                contentDescription = stringResource(R.string.setting_theme_page_paid_locked),
                                tint = Color.White,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                } else if (selected) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = HugeIcons.Tick01,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier.padding(bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ProvideTextStyle(
                value = MaterialTheme.typography.labelLarge.copy(
                    color = if (active) primary else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )
            ) {
                theme.name()
            }
            when (lock) {
                is PaidCardLock.Locked -> lock.price?.let { PricePill(text = it, emphasized = true) }
                is PaidCardLock.Purchasing -> lock.price?.let { PricePill(text = it, emphasized = true) }
                PaidCardLock.Pending -> PricePill(
                    text = stringResource(R.string.setting_theme_page_paid_pending),
                    emphasized = false,
                )

                else -> Unit
            }
        }
    }
}

@Composable
private fun PricePill(text: String, emphasized: Boolean) {
    val container = if (emphasized) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest
    val content = if (emphasized) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = content,
        maxLines = 1,
        modifier = Modifier
            .clip(CircleShape)
            .background(container)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

/** Tiny static mock of the theme's home screen. */
@Composable
private fun ThemeMiniHome(chrome: HomeChrome) {
    val miniCard = RoundedCornerShape(7.dp)
    val cardBorderWidth = if (chrome.useDoodleBorder) 1.dp else 0.5.dp

    Box(modifier = Modifier.fillMaxSize().background(LightCatCanvas)) {
        val bg = chrome.backgroundRes
        if (bg != null) {
            AsyncImage(
                model = bg,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            catPhotoScrim(chrome.styleId)?.let { scrim ->
                Box(modifier = Modifier.fillMaxSize().background(scrim))
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            // Header: avatar, greeting lines, settings button
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(chrome.avatarFill)
                        .border(1.dp, chrome.avatarRing, CircleShape),
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 5.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(chrome.title.copy(alpha = 0.9f)),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.5f)
                            .height(3.dp)
                            .clip(CircleShape)
                            .background(chrome.subtitle.copy(alpha = 0.8f)),
                    )
                }
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(chrome.settingsBg)
                        .border(0.5.dp, chrome.settingsBorder, CircleShape),
                )
            }

            chrome.peekRes?.let { peek ->
                Image(
                    painter = painterResource(peek),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp)
                        .padding(horizontal = 14.dp)
                        .offset(y = 5.dp),
                )
            }

            // Search / Activity cards
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(2) {
                    MiniCard(
                        chrome = chrome,
                        shape = miniCard,
                        borderWidth = cardBorderWidth,
                        modifier = Modifier
                            .weight(1f)
                            .height(20.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .border(1.dp, chrome.accent, CircleShape),
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.7f)
                                    .height(3.dp)
                                    .clip(CircleShape)
                                    .background(chrome.title.copy(alpha = 0.75f)),
                            )
                        }
                    }
                }
            }

            // Folders / Apps
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                MiniCard(
                    chrome = chrome,
                    shape = miniCard,
                    borderWidth = cardBorderWidth,
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp),
                )
                MiniCard(
                    chrome = chrome,
                    shape = miniCard,
                    borderWidth = cardBorderWidth,
                    modifier = Modifier
                        .weight(1f)
                        .height(16.dp),
                )
            }

            // Quick actions
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(4) { index ->
                    val pastel = chrome.actionPastels.getOrNull(index)
                    MiniCard(
                        chrome = chrome,
                        shape = RoundedCornerShape(5.dp),
                        borderWidth = cardBorderWidth,
                        fill = if (chrome.usePastelActions && pastel != null) pastel else chrome.card,
                        modifier = Modifier
                            .weight(1f)
                            .height(12.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Input bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp)
                    .clip(CircleShape)
                    .background(if (chrome.darkChrome) Color(0xB3D8CFC8) else Color.White)
                    .border(0.5.dp, chrome.cardBorder, CircleShape),
            )
        }
    }
}

@Composable
private fun MiniCard(
    chrome: HomeChrome,
    shape: RoundedCornerShape,
    borderWidth: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    fill: Color = chrome.card,
    content: @Composable () -> Unit = {},
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(fill)
            .border(borderWidth, chrome.cardBorder, shape),
    ) {
        content()
    }
}
