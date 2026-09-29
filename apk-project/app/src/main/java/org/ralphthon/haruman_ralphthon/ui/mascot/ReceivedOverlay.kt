package org.ralphthon.haruman_ralphthon.ui.mascot

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.withTimeoutOrNull
import org.ralphthon.haruman_ralphthon.R

/** A bounded header illustration after durable save; it never captures input or owns saving. */
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
    var finished by remember(eventId) { mutableStateOf(played || reduced || eventId.isBlank()) }
    LaunchedEffect(eventId, reduced) {
        if (!played && !reduced && eventId.isNotBlank()) {
            played = true
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            // A slower system animator scale must not prolong the receipt state.
            withTimeoutOrNull(850L) {
                progress.animateTo(1f, tween(durationMillis = 850, easing = LinearEasing))
            }
        }
        played = true
        finished = true
        latestOnFinished()
    }
    val showFinal = finished || reduced
    val pose = when {
        showFinal -> MascotPose.MOA_HAPPY
        progress.value < 0.62f -> MascotPose.MOA_RECEIVE
        progress.value < 0.82f -> MascotPose.MOA_STUFFED
        else -> MascotPose.MOA_HAPPY
    }
    // No pointer handler or transient speech bubble: the parent reserves exactly
    // the same 100dp slot throughout, including rotation and reduced motion.
    Box(modifier.fillMaxSize().clipToBounds().testTag("received_overlay").clearAndSetSemantics {},
        contentAlignment = Alignment.Center) {
        Image(painterResource(pose.res), contentDescription = null, modifier = Modifier.fillMaxSize())
        if (!showFinal && progress.value < 0.58f) {
            Image(painterResource(R.drawable.prop_gift), contentDescription = null,
                modifier = Modifier.size(22.dp).graphicsLayer {
                    val travel = (progress.value / 0.58f).coerceIn(0f, 1f)
                    translationX = (32f - 38f * travel) * density
                    translationY = (-32f + 36f * travel) * density
                    scaleX = 1f - travel
                    scaleY = 1f - travel
                    alpha = 1f - travel
                })
        }
    }
}
