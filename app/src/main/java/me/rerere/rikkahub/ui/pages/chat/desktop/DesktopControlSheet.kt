package me.rerere.rikkahub.ui.pages.chat.desktop

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.dokar.sonner.ToastType
import io.ktor.client.HttpClient
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Alert02
import me.rerere.hugeicons.stroke.Camera01
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.CheckmarkCircle02
import me.rerere.hugeicons.stroke.Collapse
import me.rerere.hugeicons.stroke.CommandLine
import me.rerere.hugeicons.stroke.Computer
import me.rerere.hugeicons.stroke.Expand
import me.rerere.hugeicons.stroke.Globe
import me.rerere.hugeicons.stroke.Keyboard
import me.rerere.hugeicons.stroke.Menu01
import me.rerere.hugeicons.stroke.Refresh01
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.hugeicons.stroke.Stop
import me.rerere.hugeicons.stroke.View
import me.rerere.hugeicons.stroke.ViewOff
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.tools.local.LocalToolOption
import me.rerere.rikkahub.data.datastore.NetworkSetting
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.remote.DesktopControlClient
import me.rerere.rikkahub.data.remote.DesktopControlDefaults
import me.rerere.rikkahub.ui.context.LocalToaster

enum class StreamDisplayMode {
    VIEW,
    INTERACTIVE
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DesktopControlSheet(
    assistant: Assistant,
    onUpdateAssistant: (Assistant) -> Unit,
    networkSetting: NetworkSetting,
    onUpdateNetworkSetting: (NetworkSetting) -> Unit,
    onDismissRequest: () -> Unit,
    onAppendPrompt: (String) -> Unit,
    onAttachScreenshot: (ByteArray) -> Unit,
    httpClient: HttpClient,
    isStreaming: Boolean,
    currentViewerUrl: String?,
    onStreamStarted: (String) -> Unit,
    onStreamStopped: () -> Unit,
) {
    val toaster = LocalToaster.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showConfigDialog by remember {
        mutableStateOf(networkSetting.desktopControlApiToken.isBlank())
    }
    var configBaseUrl by remember(networkSetting.desktopControlBaseUrl) {
        mutableStateOf(networkSetting.desktopControlBaseUrl.ifBlank { DesktopControlDefaults.BASE_URL })
    }
    var configApiToken by remember(networkSetting.desktopControlApiToken) {
        mutableStateOf(networkSetting.desktopControlApiToken)
    }
    var tokenVisible by remember { mutableStateOf(false) }
    var testingConnection by remember { mutableStateOf(false) }

    var isFullScreen by remember { mutableStateOf(false) }
    var displayMode by remember { mutableStateOf(StreamDisplayMode.VIEW) }
    var activeViewerUrl by remember(currentViewerUrl) { mutableStateOf(currentViewerUrl) }
    var isStartingStream by remember { mutableStateOf(false) }
    var isTakingSnapshot by remember { mutableStateOf(false) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var webViewLoading by remember { mutableStateOf(false) }

    // Quick action states
    var showBrowserDialog by remember { mutableStateOf(false) }
    var browserUrlInput by remember { mutableStateOf("https://github.com/jimskin03/friendly") }
    var showTypeDialog by remember { mutableStateOf(false) }
    var typeTextInput by remember { mutableStateOf("") }

    val hasDesktopTools = assistant.localTools.contains(LocalToolOption.DesktopControl)

    fun createClient(baseUrl: String = configBaseUrl, token: String = configApiToken): DesktopControlClient {
        return DesktopControlClient(
            http = httpClient,
            baseUrl = baseUrl.ifBlank { DesktopControlDefaults.BASE_URL },
            apiToken = token,
        )
    }

    // Auto-start stream if token is configured and not yet streaming
    LaunchedEffect(networkSetting.desktopControlApiToken) {
        if (networkSetting.desktopControlApiToken.isNotBlank() && activeViewerUrl == null && !isStartingStream) {
            isStartingStream = true
            try {
                val client = createClient(
                    baseUrl = networkSetting.desktopControlBaseUrl,
                    token = networkSetting.desktopControlApiToken,
                )
                val started = client.startStream(mode = "interactive")
                activeViewerUrl = started.viewer_url
                onStreamStarted(started.viewer_url)
            } catch (e: Exception) {
                // If stream is already active on host, fetch status
                try {
                    val client = createClient(
                        baseUrl = networkSetting.desktopControlBaseUrl,
                        token = networkSetting.desktopControlApiToken,
                    )
                    val status = client.status()
                    if (status.active && status.viewer_url != null) {
                        activeViewerUrl = status.viewer_url
                        onStreamStarted(status.viewer_url)
                    } else {
                        toaster.show(e.message ?: "Failed to start desktop stream", ToastType.Error)
                    }
                } catch (_: Exception) {
                    toaster.show(e.message ?: "Failed to start desktop stream", ToastType.Error)
                }
            } finally {
                isStartingStream = false
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        shape = if (isFullScreen) RoundedCornerShape(0.dp) else RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (isFullScreen) Modifier.fillMaxSize() else Modifier)
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = HugeIcons.Computer,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.desktop_control_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            isStartingStream -> Color(0xFFFFB300)
                                            activeViewerUrl != null -> Color(0xFF4CAF50)
                                            else -> Color(0xFF9E9E9E)
                                        }
                                    )
                            )
                            Text(
                                text = when {
                                    isStartingStream -> stringResource(R.string.desktop_status_connecting)
                                    activeViewerUrl != null -> stringResource(R.string.desktop_status_active)
                                    else -> stringResource(R.string.desktop_status_offline)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // Fullscreen toggle
                    IconButton(onClick = { isFullScreen = !isFullScreen }) {
                        Icon(
                            imageVector = if (isFullScreen) HugeIcons.Collapse else HugeIcons.Expand,
                            contentDescription = if (isFullScreen) stringResource(R.string.desktop_action_exit_fullscreen) else stringResource(R.string.desktop_action_fullscreen),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Settings toggle
                    IconButton(onClick = { showConfigDialog = !showConfigDialog }) {
                        Icon(
                            imageVector = HugeIcons.Settings03,
                            contentDescription = "Server Settings",
                            tint = if (showConfigDialog) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Stop stream
                    if (activeViewerUrl != null) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    try {
                                        val client = createClient(
                                            baseUrl = networkSetting.desktopControlBaseUrl,
                                            token = networkSetting.desktopControlApiToken,
                                        )
                                        client.stopStream()
                                        activeViewerUrl = null
                                        onStreamStopped()
                                        toaster.show("Desktop stream stopped", ToastType.Info)
                                    } catch (e: Exception) {
                                        toaster.show(e.message ?: "Failed to stop stream", ToastType.Error)
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = HugeIcons.Stop,
                                contentDescription = stringResource(R.string.desktop_action_stop),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    // Close sheet (stream remains alive in background)
                    IconButton(onClick = onDismissRequest) {
                        Icon(
                            imageVector = HugeIcons.Cancel01,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Connection Configuration Card (collapsible / shows if not configured)
            AnimatedVisibility(visible = showConfigDialog || networkSetting.desktopControlApiToken.isBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.desktop_config_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )

                        OutlinedTextField(
                            value = configBaseUrl,
                            onValueChange = { configBaseUrl = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Control API Base URL") },
                            placeholder = { Text(DesktopControlDefaults.BASE_URL) },
                            supportingText = {
                                Text("e.g. Tailscale IP: http://100.x.y.z:8787 or http://10.0.2.2:8787 (emulator)")
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        )

                        OutlinedTextField(
                            value = configApiToken,
                            onValueChange = { configApiToken = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("API Bearer Token") },
                            placeholder = { Text("Generated during setup") },
                            visualTransformation = if (tokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { tokenVisible = !tokenVisible }) {
                                    Icon(
                                        imageVector = if (tokenVisible) HugeIcons.ViewOff else HugeIcons.View,
                                        contentDescription = null
                                    )
                                }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    testingConnection = true
                                    scope.launch {
                                        try {
                                            val client = createClient(baseUrl = configBaseUrl, token = configApiToken)
                                            val ok = client.health()
                                            if (ok) {
                                                toaster.show(context.getString(R.string.desktop_config_success), ToastType.Success)
                                            } else {
                                                toaster.show(context.getString(R.string.desktop_config_failed), ToastType.Error)
                                            }
                                        } catch (e: Exception) {
                                            toaster.show(e.message ?: "Connection test failed", ToastType.Error)
                                        } finally {
                                            testingConnection = false
                                        }
                                    }
                                },
                                enabled = !testingConnection && configApiToken.isNotBlank(),
                                modifier = Modifier.weight(1f)
                            ) {
                                if (testingConnection) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text(stringResource(R.string.desktop_config_test))
                            }

                            Button(
                                onClick = {
                                    onUpdateNetworkSetting(
                                        networkSetting.copy(
                                            desktopControlBaseUrl = configBaseUrl.trim(),
                                            desktopControlApiToken = configApiToken.trim(),
                                        )
                                    )
                                    showConfigDialog = false
                                    // Trigger stream start
                                    scope.launch {
                                        isStartingStream = true
                                        try {
                                            val client = createClient(baseUrl = configBaseUrl, token = configApiToken)
                                            val started = client.startStream(mode = "interactive")
                                            activeViewerUrl = started.viewer_url
                                            onStreamStarted(started.viewer_url)
                                            toaster.show("Connected to desktop", ToastType.Success)
                                        } catch (e: Exception) {
                                            toaster.show(e.message ?: "Failed to connect", ToastType.Error)
                                        } finally {
                                            isStartingStream = false
                                        }
                                    }
                                },
                                enabled = configApiToken.isNotBlank(),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Save & Connect")
                            }
                        }
                    }
                }
            }

            // Stream Canvas / Embedded WebView
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (isFullScreen) Modifier.weight(1f)
                        else Modifier.aspectRatio(16f / 9.5f)
                    ),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Black)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (activeViewerUrl != null) {
                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    webViewInstance = this
                                    @SuppressLint("SetJavaScriptEnabled")
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.allowContentAccess = true
                                    settings.loadWithOverviewMode = true
                                    settings.useWideViewPort = true
                                    settings.setSupportZoom(true)
                                    settings.builtInZoomControls = false
                                    settings.displayZoomControls = false
                                    settings.cacheMode = WebSettings.LOAD_NO_CACHE

                                    webViewClient = object : WebViewClient() {
                                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                            webViewLoading = true
                                        }

                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            webViewLoading = false
                                        }
                                    }
                                    webChromeClient = WebChromeClient()

                                    loadUrl(activeViewerUrl!!)
                                }
                            },
                            update = { view ->
                                if (view.url != activeViewerUrl && activeViewerUrl != null) {
                                    view.loadUrl(activeViewerUrl!!)
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        // In View Mode: overlay intercepts touches so scrolling/monitoring doesn't accidentally click on desktop
                        if (displayMode == StreamDisplayMode.VIEW) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onTap = {
                                                toaster.show("Switch to Interactive Mode above to click & drag", ToastType.Info)
                                            }
                                        )
                                    }
                            )
                        }
                    } else {
                        // Offline placeholder
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            if (isStartingStream) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Starting Linux desktop stream...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White
                                )
                            } else {
                                Icon(
                                    imageVector = HugeIcons.Computer,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = Color.Gray
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Remote desktop is idle",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.LightGray
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = {
                                        scope.launch {
                                            isStartingStream = true
                                            try {
                                                val client = createClient()
                                                val started = client.startStream(mode = "interactive")
                                                activeViewerUrl = started.viewer_url
                                                onStreamStarted(started.viewer_url)
                                            } catch (e: Exception) {
                                                toaster.show(e.message ?: "Failed to start stream", ToastType.Error)
                                            } finally {
                                                isStartingStream = false
                                            }
                                        }
                                    },
                                    enabled = !isStartingStream && networkSetting.desktopControlApiToken.isNotBlank()
                                ) {
                                    Text("Start Live Stream")
                                }
                            }
                        }
                    }

                    // Floating stream controls overlay (on top of canvas)
                    if (activeViewerUrl != null) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp),
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            tonalElevation = 4.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                // Mode indicator & switcher
                                FilterChip(
                                    selected = displayMode == StreamDisplayMode.INTERACTIVE,
                                    onClick = {
                                        displayMode = if (displayMode == StreamDisplayMode.VIEW) {
                                            StreamDisplayMode.INTERACTIVE
                                        } else {
                                            StreamDisplayMode.VIEW
                                        }
                                        if (displayMode == StreamDisplayMode.INTERACTIVE) {
                                            toaster.show(context.getString(R.string.desktop_mode_interactive_toast), ToastType.Success)
                                        } else {
                                            toaster.show(context.getString(R.string.desktop_mode_view_toast), ToastType.Info)
                                        }
                                    },
                                    label = {
                                        Text(
                                            if (displayMode == StreamDisplayMode.INTERACTIVE) stringResource(R.string.desktop_mode_interactive)
                                            else stringResource(R.string.desktop_mode_view),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                )

                                // Reload button
                                IconButton(
                                    onClick = { webViewInstance?.reload() },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = HugeIcons.Refresh01,
                                        contentDescription = stringResource(R.string.desktop_action_reconnect),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Desktop Guidance Hint Banner
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.desktop_tip_banner),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            // Quick Actions Bar: Snap to Chat, Menu, Browser, Terminal, Type
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Snap to Chat
                Button(
                    onClick = {
                        isTakingSnapshot = true
                        scope.launch {
                            try {
                                val client = createClient()
                                val response = client.screenshot()
                                val bytes = Base64.decode(response.image_b64, Base64.DEFAULT)
                                onAttachScreenshot(bytes)
                                toaster.show("Desktop screenshot attached to chat", ToastType.Success)
                            } catch (e: Exception) {
                                toaster.show(e.message ?: "Failed to capture snapshot", ToastType.Error)
                            } finally {
                                isTakingSnapshot = false
                            }
                        }
                    },
                    enabled = !isTakingSnapshot && networkSetting.desktopControlApiToken.isNotBlank(),
                    modifier = Modifier.weight(1.3f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    if (isTakingSnapshot) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(4.dp))
                    } else {
                        Icon(HugeIcons.Camera01, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(stringResource(R.string.desktop_action_snap), fontWeight = FontWeight.SemiBold, maxLines = 1)
                }

                // Open Desktop Menu (Super / Right Click)
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            try {
                                val client = createClient()
                                client.launchApp("menu")
                                toaster.show("Opened desktop menu", ToastType.Info)
                            } catch (e: Exception) {
                                toaster.show(e.message ?: "Failed to open menu", ToastType.Error)
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(HugeIcons.Menu01, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.desktop_action_menu), maxLines = 1)
                }

                // Open Browser Drawer
                OutlinedButton(
                    onClick = { showBrowserDialog = !showBrowserDialog },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(HugeIcons.Globe, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Browser", maxLines = 1)
                }

                // Open Terminal
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            try {
                                val client = createClient()
                                client.launchApp("terminal")
                                toaster.show("Launched Terminal", ToastType.Success)
                            } catch (e: Exception) {
                                toaster.show(e.message ?: "Failed to launch terminal", ToastType.Error)
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(HugeIcons.CommandLine, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.desktop_action_terminal), maxLines = 1)
                }

                // Type Text
                OutlinedButton(
                    onClick = { showTypeDialog = !showTypeDialog },
                    modifier = Modifier.weight(0.9f)
                ) {
                    Icon(HugeIcons.Keyboard, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("Type", maxLines = 1)
                }
            }

            // Expandable Browser Launcher Drawer
            AnimatedVisibility(visible = showBrowserDialog) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Quick 1-tap Launch Chromium button + Presets
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        try {
                                            val client = createClient()
                                            client.openBrowser("https://www.google.com")
                                            toaster.show("Chromium launched", ToastType.Success)
                                            showBrowserDialog = false
                                        } catch (e: Exception) {
                                            toaster.show(e.message ?: "Failed to open browser", ToastType.Error)
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1.3f)
                            ) {
                                Icon(HugeIcons.Globe, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.desktop_action_launch_browser), maxLines = 1)
                            }

                            listOf(
                                "Google" to "https://www.google.com",
                                "GitHub" to "https://github.com",
                                "YouTube" to "https://www.youtube.com"
                            ).forEach { (label, url) ->
                                FilterChip(
                                    selected = browserUrlInput == url,
                                    onClick = {
                                        browserUrlInput = url
                                        scope.launch {
                                            try {
                                                val client = createClient()
                                                client.openBrowser(url)
                                                toaster.show("Launched $label", ToastType.Success)
                                                showBrowserDialog = false
                                            } catch (e: Exception) {
                                                toaster.show(e.message ?: "Failed to open $label", ToastType.Error)
                                            }
                                        }
                                    },
                                    label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }

                        // Custom URL row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = browserUrlInput,
                                onValueChange = { browserUrlInput = it },
                                modifier = Modifier.weight(1f),
                                label = { Text("Custom URL") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                            )
                            Button(
                                onClick = {
                                    scope.launch {
                                        try {
                                            val client = createClient()
                                            client.openBrowser(browserUrlInput)
                                            toaster.show("Launched $browserUrlInput", ToastType.Success)
                                            showBrowserDialog = false
                                        } catch (e: Exception) {
                                            toaster.show(e.message ?: "Failed to open browser", ToastType.Error)
                                        }
                                    }
                                },
                                enabled = browserUrlInput.isNotBlank()
                            ) {
                                Text("Go")
                            }
                        }
                    }
                }
            }

            // Expandable Keystroke / Type Input
            AnimatedVisibility(visible = showTypeDialog) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = typeTextInput,
                            onValueChange = { typeTextInput = it },
                            modifier = Modifier.weight(1f),
                            label = { Text("Type into active window") },
                            singleLine = true
                        )
                        Button(
                            onClick = {
                                scope.launch {
                                    try {
                                        val client = createClient()
                                        client.typeText(typeTextInput)
                                        toaster.show("Typed text sent", ToastType.Success)
                                        typeTextInput = ""
                                    } catch (e: Exception) {
                                        toaster.show(e.message ?: "Failed to type", ToastType.Error)
                                    }
                                }
                            },
                            enabled = typeTextInput.isNotBlank()
                        ) {
                            Text("Send")
                        }
                    }
                }
            }

            // Quick Hotkeys Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Keys:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                listOf("Ctrl+C" to listOf("ctrl", "c"),
                       "Ctrl+V" to listOf("ctrl", "v"),
                       "Super" to listOf("super"),
                       "Alt+Tab" to listOf("alt", "Tab"),
                       "Enter" to listOf("Return"),
                       "Esc" to listOf("Escape")).forEach { (label, keys) ->
                    FilterChip(
                        selected = false,
                        onClick = {
                            scope.launch {
                                try {
                                    val client = createClient()
                                    client.hotkey(keys)
                                    toaster.show("Sent $label", ToastType.Info)
                                } catch (e: Exception) {
                                    toaster.show(e.message ?: "Failed", ToastType.Error)
                                }
                            }
                        },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace) }
                    )
                }
            }

            // Assistant AI Desktop Tools Toggle Card
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.desktop_tools_toggle_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(R.string.desktop_tools_toggle_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = hasDesktopTools,
                        onCheckedChange = { enable ->
                            val currentTools = assistant.localTools.toMutableList()
                            if (enable) {
                                if (!currentTools.contains(LocalToolOption.DesktopControl)) {
                                    currentTools.add(LocalToolOption.DesktopControl)
                                }
                            } else {
                                currentTools.remove(LocalToolOption.DesktopControl)
                            }
                            onUpdateAssistant(assistant.copy(localTools = currentTools))
                        }
                    )
                }
            }
        }
    }
}
