package app.friendly.assistant.ui.pages.setting

import me.rerere.hugeicons.HugeIcons
import android.content.Intent
import androidx.core.net.toUri
import me.rerere.hugeicons.stroke.Code
import me.rerere.hugeicons.stroke.Earth
import me.rerere.hugeicons.stroke.Mail01
import me.rerere.hugeicons.stroke.User
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import app.friendly.assistant.BuildConfig
import app.friendly.assistant.R
import app.friendly.assistant.Screen
import app.friendly.assistant.ui.components.nav.BackButton
import app.friendly.assistant.ui.components.easteregg.EmojiBurstHost
import app.friendly.assistant.ui.components.ui.CardGroup
import app.friendly.assistant.ui.context.LocalNavController
import app.friendly.assistant.ui.theme.CustomColors
import app.friendly.assistant.utils.SoundEffectPlayer
import app.friendly.assistant.utils.openUrl
import app.friendly.assistant.utils.plus

@Composable
fun SettingAboutPage() {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val context = LocalContext.current
    val navController = LocalNavController.current
    val soundOptions = remember { listOf(R.raw.bingbingbing, R.raw.gangguan) }
    val soundEffectPlayer = remember(context) { SoundEffectPlayer(context) }
    DisposableEffect(soundEffectPlayer) {
        soundEffectPlayer.preload(*soundOptions.toIntArray())
        onDispose {
            soundEffectPlayer.release()
        }
    }
    val emojiOptions = remember {
        listOf(
            "🎉", "✨", "🌟", "💫", "🎊", "🥳", "🎈", "🎆", "🎇", "🧨",
            "🌈", "🧧", "🎁", "🍬", "🍭", "🍉", "🍓", "🍒", "🍍", "🥭",
            "🐱", "🐶", "🦊", "🐼", "🦁", "🐯", "🐵", "🦄",
            "❤️", "🧡", "💛", "💚", "💙", "💜",
            "🇨🇳", "🌏", "🌍", "🌎",
            "🤗", "🤩", "😆", "😺", "😸", "🤡",
            "💡", "🔥", "💥", "🚀", "⭐", "🌙"
        )
    }
    var logoCenterPx by remember { mutableStateOf(Offset.Zero) }
    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(stringResource(R.string.about_page_title))
                },
                navigationIcon = {
                    BackButton()
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        EmojiBurstHost(
            modifier = Modifier.fillMaxSize(),
            emojiOptions = emojiOptions,
            burstCount = 12
        ) { onBurst ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = innerPadding + PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AsyncImage(
                            model = R.mipmap.ic_launcher,
                            contentDescription = "Logo",
                            modifier = Modifier
                                .clip(CircleShape)
                                .size(150.dp)
                                .onGloballyPositioned { coordinates ->
                                    val position = coordinates.positionInParent()
                                    val size = coordinates.size
                                    logoCenterPx = Offset(
                                        position.x + size.width / 2f,
                                        position.y + size.height / 2f
                                    )
                                }
                                .clickable {
                                    onBurst(logoCenterPx)
                                    soundEffectPlayer.play(soundOptions.random())
                                }
                        )

                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.displaySmall,
                        )
                    }
                }

                item {
                    CardGroup(
                        modifier = Modifier.padding(horizontal = 8.dp),
                    ) {
                        item(
                            leadingContent = { Icon(HugeIcons.User, null) },
                            supportingContent = { Text(stringResource(R.string.about_page_developer_name)) },
                            headlineContent = { Text(stringResource(R.string.about_page_developer)) },
                        )
                        item(
                            modifier = if (BuildConfig.DEBUG) {
                                Modifier.combinedClickable(
                                    onClick = {},
                                    onLongClick = { navController.navigate(Screen.Debug) },
                                )
                            } else {
                                Modifier
                            },
                            leadingContent = { Icon(HugeIcons.Code, null) },
                            supportingContent = {
                                val distribution = if (BuildConfig.IS_PLAY_BUILD) "Play" else "Nightly"
                                Text("${BuildConfig.VERSION_NAME} · $distribution")
                            },
                            headlineContent = { Text(stringResource(R.string.about_page_friendly_version)) },
                        )
                        item(
                            onClick = { context.openUrl("https://cryptgregresearch.org") },
                            leadingContent = { Icon(HugeIcons.Earth, null) },
                            supportingContent = { Text(stringResource(R.string.about_page_website_url)) },
                            headlineContent = { Text(stringResource(R.string.about_page_website)) },
                        )
                        item(
                            onClick = {
                                val address = context.getString(R.string.about_page_contact_email)
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_SENDTO, "mailto:$address".toUri())
                                    )
                                }
                            },
                            leadingContent = { Icon(HugeIcons.Mail01, null) },
                            supportingContent = { Text(stringResource(R.string.about_page_contact_email)) },
                            headlineContent = { Text(stringResource(R.string.about_page_contact)) },
                        )
                    }
                }
            }
        }
    }
}
