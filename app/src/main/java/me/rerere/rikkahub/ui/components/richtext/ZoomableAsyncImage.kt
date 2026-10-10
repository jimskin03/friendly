package me.rerere.rikkahub.ui.components.richtext

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.request.placeholder
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.ui.ImagePreviewDialog
import me.rerere.rikkahub.ui.components.ui.LocalExportContext
import me.rerere.rikkahub.ui.modifier.shimmer
import me.rerere.rikkahub.ui.theme.LocalDarkMode

@Composable
fun MarkdownImage(
    src: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    minHeight: Dp = 120.dp,
    maxHeight: Dp = Dp.Unspecified,
) {
    var failed by remember(src) { mutableStateOf(src.isBlank()) }
    if (failed) {
        BrokenMarkdownImage(
            src = src,
            contentDescription = contentDescription,
            modifier = modifier,
        )
        return
    }
    var imageModifier = modifier
    if (minHeight > 0.dp) imageModifier = imageModifier.heightIn(min = minHeight)
    if (maxHeight != Dp.Unspecified) imageModifier = imageModifier.heightIn(max = maxHeight)
    ZoomableAsyncImage(
        model = src,
        contentDescription = contentDescription,
        modifier = imageModifier,
        contentScale = contentScale,
        onError = { failed = true },
    )
}

@Composable
private fun BrokenMarkdownImage(
    src: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val alt = contentDescription?.trim().orEmpty()
    val url = src.trim()
    if (alt.isEmpty() && url.isEmpty()) return
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = modifier.padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (alt.isNotEmpty()) {
            Text(
                text = alt,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (url.isNotEmpty() && url != alt) {
            Text(
                text = url,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable {
                    runCatching { uriHandler.openUri(url) }
                },
            )
        }
    }
}

@Composable
fun ZoomableAsyncImage(
    model: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Fit,
    alpha: Float = DefaultAlpha,
    onError: () -> Unit = {},
) {
    var showImageViewer by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val placeholder = if(LocalDarkMode.current) R.drawable.placeholder_dark else R.drawable.placeholder
    val export = LocalExportContext.current
    val coilModel = ImageRequest.Builder(context)
        .data(model)
        .placeholder(placeholder)
        .crossfade(false)
        .allowHardware(!export)
        .build()
    var loading by remember { mutableStateOf(false) }
    AsyncImage(
        model = coilModel,
        contentDescription = contentDescription,
        modifier = modifier
            .shimmer(isLoading = loading)
            .clickable {
                showImageViewer = true
            },
        contentScale = contentScale,
        alpha = alpha,
        alignment = alignment,
        onLoading = {
            loading = true
        },
        onSuccess = {
            loading = false
        },
        onError = {
            loading = false
            onError()
        },
    )
    if (showImageViewer) {
        ImagePreviewDialog(images = listOf(model ?: "")) {
            showImageViewer = false
        }
    }
}
