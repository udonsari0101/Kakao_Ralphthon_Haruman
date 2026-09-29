package org.ralphthon.haruman_ralphthon.ui.mascot

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import org.ralphthon.haruman_ralphthon.R

/** Place above the app in a Box, only after durable save. It never owns saving or navigation. */
@Composable
fun ReceivedOverlay(
    eventId: String,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val reduced = mascotMotionReduced()
    val latestOnFinished by rememberUpdatedState(onFinished)
    val haptics = LocalHapticFeedback.current
    val progress = remember(eventId) { Animatable(0f) }
    var played by rememberSaveable(eventId) { mutableStateOf(false) }
    var finished by remember(eventId) { mutableStateOf(false) }
    fun finish() {
        if (!finished) {
            finished = true
            latestOnFinished()
        }
    }
    LaunchedEffect(eventId, reduced) {
        if (played || reduced || eventId.isBlank()) {
            played = true
            finish()
        } else {
            played = true
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            progress.animateTo(1f, tween(durationMillis = 850, easing = LinearEasing))
            finish()
        }
    }
    if (finished || reduced) return
    val pose = when {
        progress.value < 0.62f -> MascotPose.MOA_RECEIVE
        progress.value < 0.82f -> MascotPose.MOA_STUFFED
        else -> MascotPose.MOA_IDLE
    }
    // The full-screen Box has no pointer handler. Only tapping the character dismisses it.
    Box(modifier.fillMaxSize().testTag("received_overlay").clearAndSetSemantics {},
        contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier.padding(bottom = 88.dp, start = 20.dp, end = 20.dp).graphicsLayer {
                val entry = (progress.value / 0.2f).coerceIn(0f, 1f)
                alpha = entry
                translationY = 40.dp.toPx() * (1f - entry)
            },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SpeechBubble("받았어요! 잘 보관해 둘게요.", tail = BubbleTail.BOTTOM, speaker = "모아")
            Box(Modifier.size(140.dp), contentAlignment = Alignment.Center) {
                Mascot(pose, size = 140.dp, breathing = false, onClick = ::finish)
                if (progress.value < 0.58f) {
                    Image(painterResource(R.drawable.prop_gift), contentDescription = null,
                        modifier = Modifier.size(34.dp).graphicsLayer {
                            val travel = (progress.value / 0.58f).coerceIn(0f, 1f)
                            translationX = (52f - 62f * travel) * density
                            translationY = (-60f + 64f * travel) * density
                            scaleX = 1f - travel
                            scaleY = 1f - travel
                            alpha = 1f - travel
                        })
                }
            }
        }
    }
}
