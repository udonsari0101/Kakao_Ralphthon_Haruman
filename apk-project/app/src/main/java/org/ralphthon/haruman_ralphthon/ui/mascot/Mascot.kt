package org.ralphthon.haruman_ralphthon.ui.mascot

import android.os.SystemClock
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.ralphthon.haruman_ralphthon.R
import org.ralphthon.haruman_ralphthon.data.AcornStage
import org.ralphthon.haruman_ralphthon.ui.AppColors
import org.ralphthon.haruman_ralphthon.ui.LocalAppPalette
import org.ralphthon.haruman_ralphthon.ui.rememberReducedMotion
import org.ralphthon.haruman_ralphthon.ui.theme.Haruman_RalphthonTheme
import androidx.compose.ui.res.painterResource

val LocalReduceMotion = staticCompositionLocalOf { false }
val LocalShowBubbles = staticCompositionLocalOf { true }
val LocalShowMascotBubbles = LocalShowBubbles

val MascotStageColor: Color
    @Composable @ReadOnlyComposable get() =
        if (LocalAppPalette.current.dark) Color(0xFF2A2420) else Color(0xFFFFF4E4)

enum class MascotPose(@DrawableRes val res: Int, val characterName: String, val description: String) {
    MOA_IDLE(R.drawable.mascot_moa_idle, "모아", "햄스터 모아"),
    MOA_RECEIVE(R.drawable.mascot_moa_receive, "모아", "햄스터 모아"),
    MOA_STUFFED(R.drawable.mascot_moa_stuffed, "모아", "햄스터 모아"),
    MOA_HAPPY(R.drawable.mascot_moa_happy, "모아", "햄스터 모아"),
    MOA_SLEEP(R.drawable.mascot_moa_sleep, "모아", "햄스터 모아"),
    TORI_IDLE(R.drawable.mascot_tori_idle, "토리", "다람쥐 토리"),
    TORI_PRESENT(R.drawable.mascot_tori_present, "토리", "다람쥐 토리"),
    TORI_BURY(R.drawable.mascot_tori_bury, "토리", "다람쥐 토리"),
    TORI_WAVE(R.drawable.mascot_tori_wave, "토리", "다람쥐 토리"),
    TORI_CHEER(R.drawable.mascot_tori_cheer, "토리", "다람쥐 토리"),
    TORI_DISAPPOINTED(R.drawable.mascot_tori_disappointed, "토리", "구덩이를 보며 아쉬워하는 다람쥐 토리")
}

enum class BubbleTail { LEFT, BOTTOM, NONE }

@Composable
fun mascotMotionReduced(): Boolean = LocalReduceMotion.current || rememberReducedMotion()

/** The two characters use the unmodified vector assets from the supplied handoff. */
@Composable
fun Mascot(
    pose: MascotPose,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    breathing: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    val reduced = mascotMotionReduced()
    val breath = if (breathing && !reduced) {
        val transition = rememberInfiniteTransition(label = "mascotBreathing")
        val value by transition.animateFloat(
            initialValue = 1f, targetValue = 1.03f,
            animationSpec = infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "breathScale"
        )
        value
    } else 1f
    val rotation = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var lastTap by remember { mutableStateOf(0L) }
    LaunchedEffect(reduced) { if (reduced) rotation.snapTo(0f) }
    // Keep the interactive target large even if a caller asks for a small decorative size.
    val targetModifier = if (onClick == null) modifier else
        Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).then(modifier)
    val interaction = if (onClick == null) Modifier else Modifier
        .clickable(role = Role.Button, onClickLabel = "${pose.characterName}와 이야기하기") {
            val now = SystemClock.elapsedRealtime()
            if (now - lastTap >= 300L) {
                lastTap = now
                onClick()
                if (!reduced) scope.launch {
                    rotation.animateTo(-4f, tween(80))
                    rotation.animateTo(4f, tween(100))
                    rotation.animateTo(0f, tween(120))
                }
            }
        }
    Box(
        targetModifier.then(interaction).size(size)
            .testTag("mascot_${pose.name.lowercase()}")
            .semantics { contentDescription = pose.description }
            .graphicsLayer {
                scaleY = breath
                rotationZ = rotation.value
                transformOrigin = TransformOrigin(0.5f, 0.92f)
            },
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = pose, label = "mascotPose",
            transitionSpec = {
                if (reduced) fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                else (fadeIn(tween(180)) + scaleIn(
                    animationSpec = spring(dampingRatio = 0.55f, stiffness = 500f), initialScale = 0.92f
                )) togetherWith fadeOut(tween(180))
            }
        ) { current ->
            Image(painterResource(current.res), contentDescription = null,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
    }
}

/** Decorative speech stays short; essential controls and instructions remain outside it. */
@Composable
fun SpeechBubble(
    text: String,
    modifier: Modifier = Modifier,
    tail: BubbleTail = BubbleTail.LEFT,
    speaker: String? = null
) {
    if (!LocalShowBubbles.current || text.isBlank()) return
    val reduced = mascotMotionReduced()
    val reveal = remember(text) { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(text, reduced) {
        if (reduced) reveal.snapTo(1f) else reveal.animateTo(1f, tween(180))
    }
    val surface = AppColors.Surface
    val border = AppColors.Border
    val left = if (tail == BubbleTail.LEFT) 8.dp else 0.dp
    val bottom = if (tail == BubbleTail.BOTTOM) 8.dp else 0.dp
    Box(
        modifier.graphicsLayer {
            alpha = reveal.value
            translationY = if (reduced) 0f else (1f - reveal.value) * 6.dp.toPx()
        }.clearAndSetSemantics {
            contentDescription = if (speaker.isNullOrBlank()) text else "$speaker: $text"
        }.drawBehind {
            val start = left.toPx()
            val bottomSpace = bottom.toPx()
            val width = size.width - start
            val height = size.height - bottomSpace
            val stroke = 1.dp.toPx()
            val topLeft = Offset(start + stroke / 2, stroke / 2)
            val body = Size((width - stroke).coerceAtLeast(0f), (height - stroke).coerceAtLeast(0f))
            drawRoundRect(surface, topLeft, body, CornerRadius(16.dp.toPx()))
            drawRoundRect(border, topLeft, body, CornerRadius(16.dp.toPx()), style = Stroke(stroke))
            if (tail != BubbleTail.NONE) {
                val triangle = Path()
                val edge = Path()
                if (tail == BubbleTail.LEFT) {
                    val center = height / 2
                    triangle.moveTo(start + stroke, center - 6.dp.toPx())
                    triangle.lineTo(stroke, center)
                    triangle.lineTo(start + stroke, center + 6.dp.toPx())
                    edge.moveTo(start + stroke / 2, center - 6.dp.toPx())
                    edge.lineTo(stroke, center)
                    edge.lineTo(start + stroke / 2, center + 6.dp.toPx())
                } else {
                    val center = size.width * 0.28f
                    triangle.moveTo(center - 6.dp.toPx(), height - stroke)
                    triangle.lineTo(center, size.height - stroke)
                    triangle.lineTo(center + 6.dp.toPx(), height - stroke)
                    edge.moveTo(center - 6.dp.toPx(), height - stroke / 2)
                    edge.lineTo(center, size.height - stroke)
                    edge.lineTo(center + 6.dp.toPx(), height - stroke / 2)
                }
                triangle.close()
                drawPath(triangle, surface)
                drawPath(edge, border, style = Stroke(stroke))
            }
        }.padding(start = 14.dp + left, end = 14.dp, top = 10.dp, bottom = 10.dp + bottom)
    ) {
        Text(text, color = AppColors.Text, fontSize = 14.sp, lineHeight = 20.sp,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** A single accessible utterance when character and speech are displayed together. */
@Composable
fun MascotMessage(
    pose: MascotPose,
    text: String,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    breathing: Boolean = true
) {
    val show = LocalShowBubbles.current && text.isNotBlank()
    Row(
        modifier.clearAndSetSemantics {
            contentDescription = if (show) "${pose.characterName}: $text" else pose.description
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Mascot(pose, size = size, breathing = breathing)
        if (show) SpeechBubble(text, Modifier.weight(1f), speaker = pose.characterName)
    }
}

@Composable
fun OnboardingScene(modifier: Modifier = Modifier) {
    Image(
        painterResource(R.drawable.scene_duo_onboarding),
        contentDescription = "담아두는 햄스터 모아와 다시 살펴보는 다람쥐 토리",
        modifier = modifier.widthIn(max = 300.dp).fillMaxWidth().aspectRatio(1.5f),
        contentScale = ContentScale.Fit
    )
}

@Composable
fun AcornIcon(stage: AcornStage, modifier: Modifier = Modifier, animate: Boolean = true) {
    val reduced = mascotMotionReduced()
    val scale = remember { Animatable(1f) }
    var previous by remember { mutableStateOf(stage) }
    LaunchedEffect(stage, reduced, animate) {
        if (reduced || !animate) scale.snapTo(1f)
        else if (stage != previous) {
            scale.snapTo(0.8f)
            scale.animateTo(1f, tween(220))
        }
        previous = stage
    }
    val (resource, label) = when (stage) {
        AcornStage.BURIED -> R.drawable.prop_acorn_buried to "묻어두었어요"
        AcornStage.SPROUT -> R.drawable.prop_acorn_sprout to "새싹이 났어요"
        AcornStage.READY -> R.drawable.prop_acorn_ready to "다시 볼 시간이에요"
    }
    Image(painterResource(resource), contentDescription = label,
        modifier = modifier.size(28.dp).graphicsLayer { scaleX = scale.value; scaleY = scale.value },
        contentScale = ContentScale.Fit)
}

@Preview(name = "모아와 토리 전체 포즈", showBackground = true, widthDp = 400)
@Composable
private fun MascotPosePreview() {
    Haruman_RalphthonTheme {
        Column {
            MascotPose.entries.chunked(5).forEach { poses ->
                Row { poses.forEach { Mascot(it, size = 76.dp, breathing = false) } }
            }
        }
    }
}
