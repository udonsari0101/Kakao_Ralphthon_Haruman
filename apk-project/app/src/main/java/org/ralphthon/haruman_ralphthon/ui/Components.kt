package org.ralphthon.haruman_ralphthon.ui

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.findViewTreeLifecycleOwner

@Composable
fun PrimaryAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    weak: Boolean = false
) {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val reducedMotion = rememberReducedMotion()
    val scale by animateFloatAsState(
        if (pressed && enabled && !reducedMotion) 0.985f else 1f,
        animationSpec = tween(if (reducedMotion) 0 else 100), label = "buttonPress"
    )
    val container by animateColorAsState(
        targetValue = if (weak) AppColors.Soft else if (pressed) AppColors.Pressed else AppColors.Primary,
        animationSpec = tween(if (reducedMotion) 0 else 100), label = "buttonColor"
    )
    Button(
        onClick = onClick,
        modifier = Modifier.heightIn(min = 56.dp).then(modifier)
            .graphicsLayer { scaleX = scale; scaleY = scale },
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = if (weak) AppColors.Text else Color.White,
            disabledContainerColor = AppColors.Border,
            disabledContentColor = AppColors.Secondary
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 15.dp),
        interactionSource = interactions,
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp)
    ) {
        Text(text = text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

/** Android's remove-animation setting is observed while the screen is visible. */
@Composable
fun rememberReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    val lifecycle = LocalView.current.findViewTreeLifecycleOwner()?.lifecycle
    fun readSetting(): Boolean = runCatching {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) <= 0f
    }.getOrDefault(false)
    var reduced by remember(resolver) { mutableStateOf(readSetting()) }
    DisposableEffect(resolver, lifecycle) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { reduced = readSetting() }
        }
        runCatching {
            resolver.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer
            )
        }
        val resumeObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) reduced = readSetting()
        }
        lifecycle?.addObserver(resumeObserver)
        reduced = readSetting()
        onDispose {
            lifecycle?.removeObserver(resumeObserver)
            runCatching { resolver.unregisterContentObserver(observer) }
        }
    }
    return reduced
}

/** Small rounded line icons, kept local so the app works entirely offline. */
@Composable
fun AppIcon(name: String, modifier: Modifier = Modifier, tint: Color = AppColors.Text) {
    val surface = AppColors.Surface
    Canvas(modifier = modifier.size(22.dp)) {
        withTransform({ scale(size.width / 24f, size.height / 24f, Offset.Zero) }) {
            val stroke = Stroke(width = 1.85f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
                drawLine(tint, Offset(x1, y1), Offset(x2, y2), 1.85f, StrokeCap.Round)
            fun path(block: Path.() -> Unit) = drawPath(Path().apply(block), tint, style = stroke)
            when (name) {
                "back" -> path { moveTo(15f, 5f); lineTo(8f, 12f); lineTo(15f, 19f) }
                "chevron" -> path { moveTo(9f, 6f); lineTo(15f, 12f); lineTo(9f, 18f) }
                "plus" -> { line(12f, 5f, 12f, 19f); line(5f, 12f, 19f, 12f) }
                "close" -> { line(6f, 6f, 18f, 18f); line(18f, 6f, 6f, 18f) }
                "box" -> {
                    drawRoundRect(tint, Offset(3f, 5f), Size(18f, 15f), CornerRadius(2f), style = stroke)
                    line(3f, 10f, 21f, 10f); line(8f, 3f, 8f, 5f); line(16f, 3f, 16f, 5f)
                }
                "history" -> {
                    path { moveTo(3.4f, 9f); cubicTo(5f, 3f, 12.7f, 1.6f, 17.7f, 5.7f); cubicTo(23.5f, 10.5f, 20.3f, 21f, 12f, 21f); cubicTo(7.1f, 21f, 3.7f, 17.7f, 3f, 14f) }
                    path { moveTo(3f, 4f); lineTo(3f, 9f); lineTo(8f, 9f) }
                    path { moveTo(12f, 7f); lineTo(12f, 12f); lineTo(15.5f, 14f) }
                }
                "person" -> {
                    drawCircle(tint, 3.4f, Offset(12f, 7.5f), style = stroke)
                    path { moveTo(4.5f, 20f); cubicTo(5.3f, 12.9f, 18.7f, 12.9f, 19.5f, 20f) }
                }
                "clock" -> {
                    drawCircle(tint, 8.5f, Offset(12f, 12f), style = stroke)
                    path { moveTo(12f, 7f); lineTo(12f, 12f); lineTo(15.5f, 14f) }
                }
                "external" -> {
                    path { moveTo(14f, 4f); lineTo(20f, 4f); lineTo(20f, 10f) }
                    line(11f, 13f, 20f, 4f)
                    path { moveTo(17f, 13f); lineTo(17f, 18f); quadraticBezierTo(17f, 20f, 15f, 20f); lineTo(6f, 20f); quadraticBezierTo(4f, 20f, 4f, 18f); lineTo(4f, 9f); quadraticBezierTo(4f, 7f, 6f, 7f); lineTo(11f, 7f) }
                }
                "check" -> path { moveTo(5f, 12f); lineTo(9.5f, 16.5f); lineTo(19f, 7f) }
                "info" -> {
                    drawCircle(tint, 8.5f, Offset(12f, 12f), style = stroke)
                    drawCircle(tint, 1f, Offset(12f, 7.5f)); line(12f, 11f, 12f, 16.5f)
                }
                "edit" -> {
                    path { moveTo(4f, 16f); lineTo(15f, 5f); lineTo(19f, 9f); lineTo(8f, 20f); lineTo(4f, 20f); close() }
                    line(12.5f, 7.5f, 16.5f, 11.5f)
                }
                "sun" -> {
                    drawCircle(tint, 4f, Offset(12f, 12f), style = stroke)
                    line(12f, 2f, 12f, 4f); line(12f, 20f, 12f, 22f)
                    line(2f, 12f, 4f, 12f); line(20f, 12f, 22f, 12f)
                    line(5f, 5f, 6.5f, 6.5f); line(17.5f, 17.5f, 19f, 19f)
                    line(5f, 19f, 6.5f, 17.5f); line(17.5f, 6.5f, 19f, 5f)
                }
                "moon" -> path { moveTo(17f, 3.5f); cubicTo(9f, 1f, 2f, 9f, 5f, 16f); cubicTo(8f, 23f, 19f, 22f, 21f, 14f); cubicTo(13f, 18f, 9f, 9f, 17f, 3.5f); close() }
                "system" -> {
                    drawRoundRect(tint, Offset(3f, 4f), Size(18f, 13f), CornerRadius(2f), style = stroke)
                    line(12f, 17f, 12f, 21f); line(8f, 21f, 16f, 21f)
                }
                "link" -> {
                    path { moveTo(10f, 14f); lineTo(14f, 10f) }
                    path { moveTo(8.5f, 15.5f); cubicTo(6.5f, 17.5f, 3.4f, 14.5f, 5.5f, 12.5f); lineTo(9f, 9f); cubicTo(11f, 7f, 13f, 7.5f, 14f, 9f) }
                    path { moveTo(9.5f, 15f); cubicTo(11f, 16.5f, 13f, 17f, 15f, 15f); lineTo(18.5f, 11.5f); cubicTo(22f, 8f, 16f, 2f, 12.5f, 5.5f); lineTo(11.5f, 6.5f) }
                }
                "trash" -> {
                    line(4f, 6f, 20f, 6f)
                    path { moveTo(8f, 6f); lineTo(8f, 3f); lineTo(16f, 3f); lineTo(16f, 6f) }
                    path { moveTo(6f, 6f); lineTo(7f, 20f); lineTo(17f, 20f); lineTo(18f, 6f) }
                    line(10f, 10f, 10f, 16f); line(14f, 10f, 14f, 16f)
                }
                "download" -> {
                    path { moveTo(12f, 3f); lineTo(12f, 15f); moveTo(7f, 10f); lineTo(12f, 15f); lineTo(17f, 10f) }
                    path { moveTo(4f, 15f); lineTo(4f, 20f); lineTo(20f, 20f); lineTo(20f, 15f) }
                }
                "settings" -> {
                    line(4f, 6f, 20f, 6f); line(4f, 12f, 20f, 12f); line(4f, 18f, 20f, 18f)
                    drawCircle(surface, 2.4f, Offset(9f, 6f)); drawCircle(tint, 2.4f, Offset(9f, 6f), style = stroke)
                    drawCircle(surface, 2.4f, Offset(15f, 12f)); drawCircle(tint, 2.4f, Offset(15f, 12f), style = stroke)
                    drawCircle(surface, 2.4f, Offset(10f, 18f)); drawCircle(tint, 2.4f, Offset(10f, 18f), style = stroke)
                }
                else -> drawCircle(tint, 7f, Offset(12f, 12f), style = stroke)
            }
        }
    }
}

/** Original category artwork; these are illustrations, not fetched product photos. */
@Composable
fun ProductArtwork(kind: String, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(64.dp).clip(RoundedCornerShape(14.dp))) {
        withTransform({ scale(size.width / 100f, size.height / 100f, Offset.Zero) }) {
            drawRect(when (kind) {
                "lamp" -> Color(0xFFF7ECE2)
                "shoes" -> Color(0xFFF6E9E8)
                "headphones" -> Color(0xFFE8EDF3)
                "hairdryer" -> Color(0xFFEEEAF1)
                "stand" -> Color(0xFFEBEFEA)
                else -> Color(0xFFFFF4F3)
            }, size = Size(100f, 100f))
            drawOval(Color(0x10000000), Offset(19f, 78f), Size(64f, 9f))
            when (kind) {
                "headphones" -> drawHeadphones()
                "lamp" -> drawLamp()
                "shoes" -> drawShoes()
                "hairdryer" -> drawHairdryer()
                "stand" -> drawStand()
                else -> drawBag()
            }
        }
    }
}

private fun DrawScope.drawHeadphones() {
    val band = Path().apply { moveTo(24f, 57f); lineTo(24f, 43f); cubicTo(24f, 10f, 76f, 10f, 76f, 43f); lineTo(76f, 58f) }
    drawPath(band, Color(0xFF344154), style = Stroke(9f, cap = StrokeCap.Round))
    val light = Path().apply { moveTo(26f, 42f); cubicTo(26f, 15f, 74f, 15f, 74f, 42f) }
    drawPath(light, Color(0xFF748293), style = Stroke(2f, cap = StrokeCap.Round))
    drawRoundRect(Color(0xFF253143), Offset(17f, 46f), Size(19f, 34f), CornerRadius(8f))
    drawRoundRect(Color(0xFF506075), Offset(19f, 47f), Size(10f, 29f), CornerRadius(5f))
    drawRoundRect(Color(0xFF253143), Offset(64f, 46f), Size(19f, 34f), CornerRadius(8f))
    drawRoundRect(Color(0xFF506075), Offset(70f, 47f), Size(10f, 29f), CornerRadius(5f))
}

private fun DrawScope.drawLamp() {
    drawRoundRect(Color(0xFFC76333), Offset(44f, 46f), Size(12f, 31f), CornerRadius(5f))
    drawOval(Color(0xFFB9552E), Offset(32f, 72f), Size(36f, 9f))
    val dome = Path().apply { moveTo(16f, 49f); cubicTo(17f, 10f, 83f, 10f, 84f, 49f); close() }
    drawPath(dome, Color(0xFFE8884A))
    drawOval(Color(0xFFC76333), Offset(16f, 44f), Size(68f, 12f))
    drawOval(Color(0xFFFFD8A9), Offset(25f, 47f), Size(50f, 5f))
    drawPath(Path().apply { moveTo(28f, 35f); cubicTo(34f, 25f, 42f, 22f, 49f, 22f) }, Color(0xFFF5B378), style = Stroke(3f, cap = StrokeCap.Round))
}

private fun DrawScope.drawShoes() {
    val upper = Path().apply { moveTo(15f, 61f); lineTo(22f, 36f); quadraticBezierTo(26f, 33f, 30f, 43f); lineTo(41f, 49f); lineTo(49f, 38f); lineTo(57f, 44f); quadraticBezierTo(65f, 58f, 79f, 61f); quadraticBezierTo(90f, 63f, 86f, 73f); lineTo(17f, 73f); close() }
    drawPath(upper, Color(0xFFCD5D59))
    drawPath(Path().apply { moveTo(17f, 66f); quadraticBezierTo(50f, 72f, 87f, 68f); lineTo(86f, 77f); lineTo(16f, 77f); close() }, Color(0xFFFAF7F3))
    drawPath(Path().apply { moveTo(38f, 58f); lineTo(48f, 45f); lineTo(56f, 49f); lineTo(48f, 62f); close() }, Color(0xFFF1C2B6))
    drawLine(Color(0xFFF8E9E1), Offset(50f, 47f), Offset(60f, 52f), 2.4f, StrokeCap.Round)
    drawLine(Color(0xFFF8E9E1), Offset(54f, 53f), Offset(64f, 57f), 2.4f, StrokeCap.Round)
    drawLine(Color(0xFFD5CEC8), Offset(20f, 78f), Offset(82f, 78f), 2f, StrokeCap.Round)
}

private fun DrawScope.drawHairdryer() {
    drawRoundRect(Color(0xFF3D3A46), Offset(43f, 45f), Size(15f, 34f), CornerRadius(6f))
    drawRoundRect(Color(0xFFB98977), Offset(43f, 72f), Size(15f, 7f), CornerRadius(2f))
    drawPath(Path().apply { moveTo(33f, 27f); lineTo(75f, 34f); lineTo(75f, 49f); lineTo(33f, 56f); close() }, Color(0xFF514A59))
    drawRoundRect(Color(0xFFB98977), Offset(70f, 33f), Size(9f, 17f), CornerRadius(3f))
    drawOval(Color(0xFFC99783), Offset(19f, 25f), Size(32f, 33f))
    drawOval(Color(0xFF3D3A46), Offset(25f, 31f), Size(20f, 21f))
    drawOval(Color(0xFF6A6170), Offset(29f, 35f), Size(12f, 13f))
    drawCircle(Color(0xFFC99783), 2f, Offset(50.5f, 58f))
}

private fun DrawScope.drawStand() {
    drawPath(Path().apply { moveTo(47f, 60f); lineTo(56f, 60f); lineTo(61f, 77f); lineTo(39f, 77f); close() }, Color(0xFFADB9B7))
    drawRoundRect(Color(0xFF889A98), Offset(31f, 75f), Size(40f, 5f), CornerRadius(2.5f))
    drawRoundRect(Color(0xFF859894), Offset(15f, 21f), Size(70f, 44f), CornerRadius(5f))
    drawRoundRect(Color(0xFFD4E3DE), Offset(19f, 25f), Size(62f, 35f), CornerRadius(3f))
    drawPath(Path().apply { moveTo(20f, 53f); lineTo(39f, 38f); lineTo(53f, 48f); lineTo(66f, 33f); lineTo(81f, 46f); lineTo(81f, 59f); lineTo(20f, 59f); close() }, Color(0xFFACC7BA))
    drawCircle(Color(0xFFF5F4DC), 5f, Offset(66f, 34f))
}

private fun DrawScope.drawBag() {
    drawPath(Path().apply { moveTo(37f, 38f); lineTo(37f, 30f); cubicTo(37f, 13f, 63f, 13f, 63f, 30f); lineTo(63f, 38f) }, Color(0xFFB80010), style = Stroke(4f, cap = StrokeCap.Round))
    drawRoundRect(Color(0xFFE60012), Offset(24f, 34f), Size(52f, 47f), CornerRadius(9f))
    drawPath(Path().apply { moveTo(41f, 54f); lineTo(47f, 60f); lineTo(60f, 47f) }, Color.White, style = Stroke(4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawRoundRect(Color(0xFFFF6B73), Offset(27f, 38f), Size(5f, 36f), CornerRadius(2f))
}
