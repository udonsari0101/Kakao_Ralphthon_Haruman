package org.ralphthon.haruman_ralphthon.ui.mascot

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.ralphthon.haruman_ralphthon.R

private val YardOutline = Color(0xFF5B3A22)
private val YardSoil = Color(0xFFC29366)

/** A bought item's place stays in the yard: an empty pit with Moa's tiny tracks. */
@Composable
fun DugPit(modifier: Modifier = Modifier) {
    Canvas(modifier.size(48.dp).clearAndSetSemantics { contentDescription = "사기로 한 물건의 구덩이" }) {
        scale(size.width / 96f, size.height / 96f, pivot = Offset.Zero) {
            drawOval(Color(0x1A3A2418), Offset(13f, 73f), Size(73f, 13f))
            val soil = Path().apply {
                moveTo(9f, 68f); quadraticBezierTo(17f, 49f, 29f, 58f)
                quadraticBezierTo(44f, 50f, 55f, 59f)
                quadraticBezierTo(75f, 49f, 88f, 70f)
                quadraticBezierTo(87f, 82f, 47f, 83f)
                quadraticBezierTo(12f, 81f, 9f, 68f); close()
            }
            drawPath(soil, YardSoil)
            drawPath(soil, YardOutline, style = Stroke(2.8f))
            drawOval(Color(0xFF80532F), Offset(25f, 60f), Size(46f, 18f))
            drawOval(YardOutline, Offset(25f, 60f), Size(46f, 18f), style = Stroke(2.4f))
            drawOval(Color(0xFF624029), Offset(33f, 65f), Size(30f, 8f))
            drawCircle(Color(0xFFE4B987), 2.5f, Offset(17f, 68f))
            drawCircle(Color(0xFFE4B987), 2f, Offset(78f, 65f))
            drawLine(YardOutline, Offset(19f, 75f), Offset(22f, 76f), 2f, StrokeCap.Round)
            pawTrack(63f, 40f); pawTrack(79f, 26f)
        }
    }
}

private fun DrawScope.pawTrack(x: Float, y: Float) {
    drawOval(YardOutline.copy(alpha = 0.68f), Offset(x - 3f, y), Size(6f, 7f))
    listOf(Offset(-4f, -2f), Offset(0f, -4f), Offset(4f, -2f)).forEach {
        drawCircle(YardOutline.copy(alpha = 0.68f), 1.6f, Offset(x + it.x, y + it.y))
    }
}

/** Uses the supplied large tree and adds two matching acorns for the highest price band. */
@Composable
fun FruitingTree(modifier: Modifier = Modifier) {
    Box(modifier.size(48.dp).clearAndSetSemantics { contentDescription = "도토리가 열린 큰 나무" }) {
        Image(painterResource(R.drawable.prop_tree_big), null, Modifier.fillMaxSize())
        Canvas(Modifier.fillMaxSize()) {
            scale(size.width / 96f, size.height / 96f, pivot = Offset.Zero) {
                yardAcorn(35f, 45f); yardAcorn(52f, 28f)
            }
        }
    }
}

private fun DrawScope.yardAcorn(x: Float, y: Float) {
    drawOval(Color(0xFFDB9550), Offset(x - 5f, y - 4f), Size(10f, 12f))
    drawOval(YardOutline, Offset(x - 5f, y - 4f), Size(10f, 12f), style = Stroke(2.4f))
    val cap = Path().apply {
        moveTo(x - 6.5f, y - 3.5f)
        quadraticBezierTo(x, y - 10.5f, x + 6.5f, y - 3.5f)
        quadraticBezierTo(x, y - 1.5f, x - 6.5f, y - 3.5f); close()
    }
    drawPath(cap, Color(0xFF8A5A2B))
    drawPath(cap, YardOutline, style = Stroke(2.4f))
    drawLine(YardOutline, Offset(x, y - 7f), Offset(x + 2f, y - 11f), 2.4f, StrokeCap.Round)
    drawOval(Color(0xFFF4C58A), Offset(x - 2.5f, y), Size(2.5f, 4f))
}

/** Unknown is represented explicitly; a missing price must never look like zero savings. */
@Composable
fun UnpricedTree(modifier: Modifier = Modifier) {
    Box(modifier.size(48.dp).clearAndSetSemantics { contentDescription = "가격이 정해지지 않은 물건의 나무" }) {
        Image(painterResource(R.drawable.prop_tree_small), null, Modifier.fillMaxSize())
        Canvas(Modifier.fillMaxSize()) {
            scale(size.width / 96f, size.height / 96f, pivot = Offset.Zero) {
                drawLine(YardOutline, Offset(75f, 74f), Offset(75f, 88f), 3f, StrokeCap.Round)
                drawRoundRect(Color(0xFFFFF4E4), Offset(63f, 52f), Size(24f, 24f), CornerRadius(4f))
                drawRoundRect(YardOutline, Offset(63f, 52f), Size(24f, 24f), CornerRadius(4f), style = Stroke(2.4f))
                val question = Path().apply {
                    moveTo(70f, 60f); cubicTo(70f, 54f, 81f, 55f, 80f, 61f)
                    cubicTo(80f, 64f, 75f, 63f, 75f, 67f)
                }
                drawPath(question, YardOutline, style = Stroke(2.5f, cap = StrokeCap.Round))
                drawCircle(YardOutline, 1.6f, Offset(75f, 71f))
            }
        }
    }
}

/** Five cheek stages share the original face, proportions and palette. Negative counts are empty. */
@Composable
fun CheekMoa(
    count: Int,
    modifier: Modifier = Modifier,
    size: Dp = 88.dp,
    onClick: (() -> Unit)? = null,
    overGoal: Boolean = false
) {
    val stage = when { count <= 0 -> 0; count <= 2 -> 1; count <= 5 -> 2; count <= 9 -> 3; else -> 4 }
    val resource = when (stage) {
        0 -> R.drawable.mascot_moa_sleep
        1 -> R.drawable.mascot_moa_cheek_1
        2 -> R.drawable.mascot_moa_cheek_2
        3 -> R.drawable.mascot_moa_cheek_3
        else -> R.drawable.mascot_moa_cheek_4
    }
    val reduced = mascotMotionReduced()
    val breath = if (reduced) 1f else {
        val transition = rememberInfiniteTransition(label = "cheekBreathing")
        val value by transition.animateFloat(1f, 1.03f,
            infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "cheekBreath")
        value
    }
    val wiggle = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(reduced) { if (reduced) wiggle.snapTo(0f) }
    val click = if (onClick == null) Modifier else Modifier.clickable(role = Role.Button,
        onClickLabel = "고민 중인 물건 펼쳐 보기") {
        onClick()
        if (!reduced && !wiggle.isRunning) scope.launch {
            wiggle.animateTo(-4f, tween(80)); wiggle.animateTo(4f, tween(100)); wiggle.animateTo(0f, tween(120))
        }
    }
    val target = if (onClick == null) modifier else Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).then(modifier)
    Box(target.then(click).size(size).semantics {
        contentDescription = "모아의 볼주머니, 고민 중인 물건 ${count.coerceAtLeast(0)}개" +
            if (overGoal) ", 모두 사기로 하면 소비 목표를 넘어요" else ""
    }.graphicsLayer {
        scaleY = breath; rotationZ = wiggle.value; transformOrigin = TransformOrigin(0.5f, 0.92f)
    }) {
        Image(painterResource(resource), null, Modifier.fillMaxSize())
        if (overGoal && stage < 3) Canvas(Modifier.fillMaxSize()) {
            scale(this.size.width / 240f, this.size.height / 240f, pivot = Offset.Zero) {
                val sweat = Path().apply {
                    moveTo(196f, 86f); quadraticBezierTo(204f, 100f, 196f, 106f)
                    quadraticBezierTo(188f, 100f, 196f, 86f); close()
                }
                drawPath(sweat, Color(0xFF9FD3F5)); drawPath(sweat, YardOutline, style = Stroke(2.4f))
            }
        }
    }
}
