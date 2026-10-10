package app.friendly.assistant.ui.components.openui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

private const val THUMB_MAX_PX = 240
private const val MAX_THUMBS = 48

/** Small JPEG data: URIs for local images, so the sandboxed page never needs file access. */
internal object OpenUiThumbnails {
    private val cache = LruCache<String, String>(96)

    private val sizes = java.util.concurrent.ConcurrentHashMap<String, Long>()

    fun sizeOf(url: String): Long? {
        sizes[url]?.let { return it.takeIf { size -> size > 0 } }
        val size = runCatching {
            val uri = Uri.parse(url)
            if (uri.scheme == "file") File(uri.path ?: return null).length() else 0L
        }.getOrDefault(0L)
        sizes[url] = size
        return size.takeIf { it > 0 }
    }

    fun load(context: Context, url: String): String? {
        cache.get(url)?.let { return it }
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return null
        if (uri.scheme != "file" && uri.scheme != "content") return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= THUMB_MAX_PX && bounds.outHeight / (sample * 2) >= THUMB_MAX_PX) sample *= 2
        val bitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val scale = THUMB_MAX_PX.toFloat() / maxOf(bitmap.width, bitmap.height)
        val scaled = if (scale < 1f) Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1), true) else bitmap
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 70, out)
        val dataUri = "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        cache.put(url, dataUri)
        return dataUri
    }
}

/** Thumbnails for the given image URLs, keyed by [openUiAssetKey]; loads off the main thread. */
@Composable
internal fun rememberOpenUiThumbnails(urls: List<String>): Map<String, String> {
    val context = LocalContext.current
    val wanted = urls.distinct().takeLast(MAX_THUMBS)
    val result by produceState(initialValue = emptyMap<String, String>(), wanted) {
        value = withContext(Dispatchers.IO) {
            wanted.mapNotNull { url ->
                runCatching { OpenUiThumbnails.load(context, url) }.getOrNull()?.let { openUiAssetKey(url) to it }
            }.toMap()
        }
    }
    return result
}
