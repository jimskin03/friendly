package app.friendly.assistant.ui.pages.setting.components

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.friendly.assistant.R
import app.friendly.assistant.data.billing.PaidThemeProducts
import app.friendly.assistant.data.billing.PaidThemeStoreState
import app.friendly.assistant.data.billing.StoreStatus
import app.friendly.assistant.ui.theme.PresetTheme
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.PlayStore
import me.rerere.hugeicons.stroke.Refresh01
import me.rerere.hugeicons.stroke.Sparkles

/** Lock state for one paid theme under the current store state. */
fun PaidThemeStoreState.lockFor(themeId: String): PaidCardLock = when {
    isUnlocked(themeId) -> PaidCardLock.None
    purchasing == PaidThemeProducts.forTheme(themeId) || purchasing == PaidThemeProducts.BUNDLE ->
        PaidCardLock.Purchasing(price = priceFor(themeId))
    isPending(themeId) -> PaidCardLock.Pending
    else -> PaidCardLock.Locked(price = if (status == StoreStatus.Ready) priceFor(themeId) else null)
}

/**
 * Paid Themes card body: an optional store note, the preview grid with lock state and Play
 * prices, the bundle offer, and Restore purchases. Stateless so previews can render it.
 */
@Composable
fun PaidThemeStoreSection(
    themeId: String,
    themes: List<PresetTheme>,
    store: PaidThemeStoreState,
    onSelectTheme: (String) -> Unit,
    onLockedClick: (String) -> Unit,
    onBuyBundle: () -> Unit,
    onRetry: () -> Unit,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val anyLocked = themes.any { !store.isUnlocked(it.id) }
    Column(modifier = modifier.fillMaxWidth()) {
        if (anyLocked) {
            when (store.status) {
                StoreStatus.Ready -> Text(
                    text = stringResource(R.string.setting_theme_page_paid_store_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp),
                )

                StoreStatus.Unavailable -> StoreNote(
                    icon = HugeIcons.PlayStore,
                    text = stringResource(R.string.setting_theme_page_paid_unavailable),
                )

                StoreStatus.Offline -> StoreNote(
                    icon = HugeIcons.Refresh01,
                    text = stringResource(R.string.setting_theme_page_paid_offline),
                    action = stringResource(R.string.setting_theme_page_paid_retry),
                    onAction = onRetry,
                )

                StoreStatus.Connecting, StoreStatus.Unlocked -> Unit
            }
        }

        PaidThemePreviewGrid(
            themeId = themeId,
            themes = themes,
            lockFor = store::lockFor,
            onChangeTheme = onSelectTheme,
            onLockedClick = onLockedClick,
        )

        store.bundleOffer?.let { bundle ->
            BundleOffer(
                price = bundle.formattedPrice,
                savingsPercent = store.bundleSavingsPercent,
                busy = store.purchasing != null,
                onClick = onBuyBundle,
            )
        }

        if (store.status == StoreStatus.Ready) {
            TextButton(
                onClick = onRestore,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 6.dp),
            ) {
                Text(stringResource(R.string.setting_theme_page_paid_restore))
            }
        }
    }
}

@Composable
private fun StoreNote(
    icon: ImageVector,
    text: String,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 12.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(start = 14.dp, end = if (action != null) 4.dp else 14.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (action != null) {
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
private fun BundleOffer(
    price: String,
    savingsPercent: Int?,
    busy: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), shape)
            .clickable(enabled = !busy, onClick = onClick)
            .padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = HugeIcons.Sparkles,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(18.dp),
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(R.string.setting_theme_page_paid_bundle_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = if (savingsPercent != null) {
                    stringResource(R.string.setting_theme_page_paid_bundle_save, savingsPercent)
                } else {
                    stringResource(R.string.setting_theme_page_paid_bundle_subtitle)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Button(
            onClick = onClick,
            enabled = !busy,
            contentPadding = ButtonDefaults.ContentPadding,
        ) {
            Text(text = price, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}
