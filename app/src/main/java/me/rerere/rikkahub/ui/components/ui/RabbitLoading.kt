package me.rerere.rikkahub.ui.components.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.context.LocalSettings

@Composable
fun RabbitLoadingIndicator(modifier: Modifier = Modifier) {
    val useAppIconStyleLoadingIndicator = LocalSettings.current.displaySetting.useAppIconStyleLoadingIndicator

    if (useAppIconStyleLoadingIndicator) {
        val pulse = rememberInfiniteTransition(label = "loadingCat")
        val alpha by pulse.animateFloat(
            initialValue = 1f,
            targetValue = 0.35f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "loadingCatAlpha",
        )
        Image(
            painter = painterResource(R.drawable.ic_loading_cat),
            contentDescription = null,
            modifier = modifier.graphicsLayer { this.alpha = alpha },
        )
    } else {
        ContainedLoadingIndicator(
            modifier = modifier,
        )
    }
}
