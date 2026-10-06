package me.rerere.rikkahub.ui.pages.apps

import android.webkit.WebView as AndroidWebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.rikkahub.data.model.InstalledWebApp
import me.rerere.rikkahub.data.model.WebAppLaunchMode
import me.rerere.rikkahub.data.model.WebAppPermissionPolicy
import me.rerere.rikkahub.data.model.WebAppZoomMode
import me.rerere.rikkahub.ui.components.webview.WebViewSitePermissionPolicy
import me.rerere.rikkahub.ui.components.webview.WebViewState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.webview.WebContent
import me.rerere.rikkahub.ui.components.webview.WebView
import me.rerere.rikkahub.ui.context.LocalNavController
import org.koin.androidx.compose.koinViewModel
import kotlin.uuid.Uuid

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebAppPage(
    appId: String,
    vm: AppsVM = koinViewModel(),
) {
    val settings = vm.settings.collectAsStateWithLifecycle().value
    val id = remember(appId) { runCatching { Uuid.parse(appId) }.getOrNull() }
    val app = settings.installedWebApps.firstOrNull { it.id == id }

    val startUrl = app?.startUrl?.takeIf { it.isNotBlank() } ?: "about:blank"
    val zoomMode = app?.zoomMode ?: WebAppZoomMode.AUTO
    // Key on zoom so changing Zoom in App options recreates WebSettings / initial scale.
    val state = remember(startUrl, zoomMode) {
        WebViewState(
            initialContent = WebContent.Url(startUrl),
            settings = { applyInstalledWebAppSettings(zoomMode) },
        )
    }
    // Per-app App options: Block denies the site's request without prompting.
    val sitePermissionPolicy = WebViewSitePermissionPolicy(
        camera = app?.cameraPermission != WebAppPermissionPolicy.BLOCK,
        microphone = app?.microphonePermission != WebAppPermissionPolicy.BLOCK,
        location = app?.locationPermission != WebAppPermissionPolicy.BLOCK,
    )
    SideEffect { state.sitePermissionPolicy = sitePermissionPolicy }
    val onWebViewCreated: (AndroidWebView) -> Unit = remember(zoomMode) {
        { webView -> webView.applyInstalledWebAppZoom(zoomMode) }
    }

    BackHandler(enabled = app != null && state.canGoBack) {
        state.goBack()
    }

    if (app == null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.apps_page_title)) },
                    navigationIcon = { BackButton() },
                )
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                if (!settings.init) {
                    Text(
                        text = stringResource(R.string.apps_page_missing),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    } else {
        // Recreate the WebView when zoom changes so setInitialScale / overview apply.
        key(app.zoomMode) {
            when (app.launchMode) {
                WebAppLaunchMode.COMPACT -> CompactWebApp(
                    app = app,
                    state = state,
                    onWebViewCreated = onWebViewCreated,
                )
                WebAppLaunchMode.FULLSIZE -> FullsizeWebApp(
                    state = state,
                    onWebViewCreated = onWebViewCreated,
                )
                WebAppLaunchMode.FULLSCREEN -> FullscreenWebApp(
                    state = state,
                    onWebViewCreated = onWebViewCreated,
                )
            }
        }
    }
}

@Composable
private fun CompactWebApp(
    app: InstalledWebApp,
    state: WebViewState,
    onWebViewCreated: (AndroidWebView) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = app.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall,
                    )
                },
                navigationIcon = { BackButton() },
            )
        },
    ) { innerPadding ->
        WebView(
            state = state,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            onCreated = onWebViewCreated,
        )
    }
}

@Composable
private fun FullsizeWebApp(
    state: WebViewState,
    onWebViewCreated: (AndroidWebView) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        BackButton(modifier = Modifier.padding(8.dp))
        WebView(
            state = state,
            modifier = Modifier
                .weight(1f)
                .fillMaxSize(),
            onCreated = onWebViewCreated,
        )
    }
}

@Composable
private fun FullscreenWebApp(
    state: WebViewState,
    onWebViewCreated: (AndroidWebView) -> Unit,
) {
    val navController = LocalNavController.current
    Box(modifier = Modifier.fillMaxSize()) {
        WebView(
            state = state,
            modifier = Modifier.fillMaxSize(),
            onCreated = onWebViewCreated,
        )
        IconButton(
            onClick = { navController.popBackStack() },
            modifier = Modifier
                .statusBarsPadding()
                .padding(8.dp)
                .align(Alignment.TopStart)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f)),
        ) {
            Icon(
                HugeIcons.ArrowLeft01,
                contentDescription = stringResource(R.string.back),
                tint = Color.White,
            )
        }
    }
}
