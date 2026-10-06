package app.friendly.assistant.ui.components.webview

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import app.friendly.assistant.R

/**
 * Which site permissions a WebView may *ask* for. A `false` entry is blocked: the site's
 * request is denied without prompting. Defaults allow the site to ask for everything.
 */
data class WebViewSitePermissionPolicy(
    val camera: Boolean = true,
    val microphone: Boolean = true,
    val location: Boolean = true,
)

/** Session-only grant key for geolocation (WebKit media resources use their own strings). */
internal const val SITE_RESOURCE_GEOLOCATION = "friendly.GEOLOCATION"

/** A site permission request waiting for the user's answer. */
sealed interface PendingSitePermission {
    /** scheme://authority of the requesting site. */
    val origin: String

    /** WebKit resources (or [SITE_RESOURCE_GEOLOCATION]) the site may get if the user allows. */
    val resources: List<String>

    class Media(
        val request: PermissionRequest,
        override val resources: List<String>,
    ) : PendingSitePermission {
        override val origin: String = normalizeSiteOrigin(request.origin?.toString())
    }

    class Geolocation(
        rawOrigin: String,
        val callback: GeolocationPermissions.Callback,
    ) : PendingSitePermission {
        /** WebKit expects the exact origin string it passed in. */
        val webkitOrigin: String = rawOrigin
        override val origin: String = normalizeSiteOrigin(rawOrigin)
        override val resources: List<String> = listOf(SITE_RESOURCE_GEOLOCATION)
    }
}

internal fun normalizeSiteOrigin(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return raw
    val scheme = uri.scheme
    val authority = uri.encodedAuthority
    return if (scheme != null && !authority.isNullOrBlank()) "$scheme://$authority" else raw
}

private fun displayOrigin(origin: String): String =
    runCatching { Uri.parse(origin).host }.getOrNull()?.takeIf { it.isNotBlank() } ?: origin

/** Android runtime permissions that back a WebKit resource. Any one of them is enough. */
internal fun androidPermissionsFor(resource: String): List<String> = when (resource) {
    PermissionRequest.RESOURCE_VIDEO_CAPTURE -> listOf(Manifest.permission.CAMERA)
    PermissionRequest.RESOURCE_AUDIO_CAPTURE -> listOf(Manifest.permission.RECORD_AUDIO)
    SITE_RESOURCE_GEOLOCATION -> listOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )
    else -> emptyList()
}

internal fun Context.hasAndroidPermissionFor(resource: String): Boolean {
    val perms = androidPermissionsFor(resource)
    if (perms.isEmpty()) return false
    return perms.any { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
}

/** Filters the resources a site asked for down to those the current policy lets it ask for. */
internal fun WebViewSitePermissionPolicy.allowedResources(requested: Array<String>?): List<String> =
    requested.orEmpty().filter { resource ->
        when (resource) {
            PermissionRequest.RESOURCE_VIDEO_CAPTURE -> camera
            PermissionRequest.RESOURCE_AUDIO_CAPTURE -> microphone
            // Protected media ID / MIDI sysex are not exposed to web apps.
            else -> false
        }
    }.distinct()

/**
 * Grants [pending] for the resources the user allowed and Android currently permits.
 * Denies when nothing is left. Returns true when at least one resource was granted.
 */
internal fun WebViewState.completeSitePermission(
    context: Context,
    pending: PendingSitePermission,
): Boolean {
    if (!removeSitePermission(pending)) return false // Site cancelled meanwhile.
    val granted = pending.resources.filter { context.hasAndroidPermissionFor(it) }
    if (granted.isNotEmpty()) rememberSessionGrant(pending.origin, granted)
    when (pending) {
        is PendingSitePermission.Media -> runCatching {
            if (granted.isEmpty()) pending.request.deny() else pending.request.grant(granted.toTypedArray())
        }
        is PendingSitePermission.Geolocation -> runCatching {
            pending.callback.invoke(pending.webkitOrigin, granted.isNotEmpty(), false)
        }
    }
    return granted.isNotEmpty()
}

internal fun WebViewState.denySitePermission(pending: PendingSitePermission) {
    if (!removeSitePermission(pending)) return
    when (pending) {
        is PendingSitePermission.Media -> runCatching { pending.request.deny() }
        is PendingSitePermission.Geolocation -> runCatching {
            pending.callback.invoke(pending.webkitOrigin, false, false)
        }
    }
}

/**
 * Shows "Allow site access?" for the first queued site request, then requests the matching
 * Android runtime permissions when they are missing. Only composed while a request is queued.
 */
@Composable
internal fun WebViewSitePermissionHandler(state: WebViewState) {
    val context = LocalContext.current
    val pending = state.pendingSitePermissions.firstOrNull() ?: return
    var awaitingAndroid by remember { mutableStateOf<PendingSitePermission?>(null) }
    val deniedMessage = stringResource(R.string.webview_permission_android_denied)

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        val request = awaitingAndroid ?: return@rememberLauncherForActivityResult
        awaitingAndroid = null
        if (!state.completeSitePermission(context, request)) {
            Toast.makeText(context, deniedMessage, Toast.LENGTH_LONG).show()
        }
    }

    if (awaitingAndroid != null) return

    val labels = pending.resources.map { resource ->
        when (resource) {
            PermissionRequest.RESOURCE_VIDEO_CAPTURE -> stringResource(R.string.webview_permission_camera)
            PermissionRequest.RESOURCE_AUDIO_CAPTURE -> stringResource(R.string.webview_permission_microphone)
            else -> stringResource(R.string.webview_permission_location)
        }
    }

    AlertDialog(
        onDismissRequest = { state.denySitePermission(pending) },
        title = { Text(stringResource(R.string.webview_permission_title)) },
        text = {
            Text(
                stringResource(
                    R.string.webview_permission_message,
                    displayOrigin(pending.origin),
                    labels.joinToString(", "),
                )
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val missing = pending.resources
                    .filterNot { context.hasAndroidPermissionFor(it) }
                    .flatMap { androidPermissionsFor(it) }
                    .distinct()
                if (missing.isEmpty()) {
                    state.completeSitePermission(context, pending)
                } else {
                    awaitingAndroid = pending
                    launcher.launch(missing.toTypedArray())
                }
            }) {
                Text(stringResource(R.string.webview_permission_allow))
            }
        },
        dismissButton = {
            TextButton(onClick = { state.denySitePermission(pending) }) {
                Text(stringResource(R.string.webview_permission_block))
            }
        },
    )
}
