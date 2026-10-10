package app.friendly.assistant.ui.pages.setting.components

import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.MoneyBag02
import me.rerere.hugeicons.stroke.Tick01
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import app.friendly.assistant.ui.theme.LocalDarkMode
import app.friendly.assistant.ui.theme.PresetTheme
import app.friendly.assistant.ui.theme.PresetThemes
import app.friendly.assistant.ui.theme.presets.PaidThemes

@Composable
fun PresetThemeButton(
    theme: PresetTheme,
    selected: Boolean,
    modifier: Modifier = Modifier,
    locked: Boolean = false,
    onClick: () -> Unit
) {
    val darkMode = LocalDarkMode.current
    val scheme = theme.getColorScheme(darkMode)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = LocalIndication.current,
                onClick = {
                    onClick()
                }
            )
            .padding(8.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
        ) {
            Canvas(
                modifier = Modifier
                    .clip(CircleShape)
                    .size(48.dp)
            ) {
                drawRect(
                    color = scheme.primaryContainer,
                    size = size
                )
                drawRect(
                    color = scheme.secondaryContainer,
                    size = size,
                    topLeft = Offset(
                        x = size.width / 2,
                        y = 0f
                    ),
                )
                drawRect(
                    color = scheme.tertiaryContainer,
                    size = size,
                    topLeft = Offset(
                        x = size.width / 2,
                        y = size.height / 2
                    ),
                )
                drawCircle(
                    color = scheme.primary,
                    radius = if (selected) 12.dp.toPx() else 8.dp.toPx(),
                    center = Offset(
                        x = size.width / 2,
                        y = size.height / 2
                    )
                )
            }
            if (selected && !locked) {
                Icon(
                    HugeIcons.Tick01,
                    contentDescription = null,
                    tint = scheme.contentColorFor(scheme.onPrimary)
                )
            }
            if (locked) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        HugeIcons.MoneyBag02,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
        ProvideTextStyle(
            value = MaterialTheme.typography.labelMedium.copy(
                color = scheme.primary,
                textAlign = TextAlign.Center,
            )
        ) {
            theme.name()
        }
    }
}

private const val THEME_GRID_COLUMNS = 4

@Composable
fun PresetThemeButtonGroup(
    themeId: String,
    modifier: Modifier = Modifier,
    themes: List<PresetTheme> = PresetThemes,
    lockedThemeIds: Set<String> = emptySet(),
    onChangeTheme: (String) -> Unit,
    onLockedClick: ((String) -> Unit)? = null,
) {
    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = THEME_GRID_COLUMNS,
    ) {
        themes.fastForEach { theme ->
            key(theme.id) {
                val locked = theme.id in lockedThemeIds
                PresetThemeButton(
                    theme = theme,
                    selected = theme.id == themeId,
                    locked = locked,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (locked) {
                            onLockedClick?.invoke(theme.id)
                        } else {
                            onChangeTheme(theme.id)
                        }
                    },
                )
            }
        }

        repeat((THEME_GRID_COLUMNS - themes.size % THEME_GRID_COLUMNS) % THEME_GRID_COLUMNS) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PresetThemeButtonPreview() {
    var themeId by remember { mutableStateOf("ocean") }
    Column {
        PresetThemeButtonGroup(
            themeId = themeId,
            onChangeTheme = { themeId = it }
        )
        PresetThemeButtonGroup(
            themeId = themeId,
            themes = PaidThemes,
            lockedThemeIds = PaidThemes.map { it.id }.toSet(),
            onChangeTheme = { themeId = it },
            onLockedClick = {},
        )
    }
}
