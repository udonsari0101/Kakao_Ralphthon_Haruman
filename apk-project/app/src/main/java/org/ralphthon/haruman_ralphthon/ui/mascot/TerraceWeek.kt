package org.ralphthon.haruman_ralphthon.ui.mascot

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import org.ralphthon.haruman_ralphthon.R
import org.ralphthon.haruman_ralphthon.data.*
import org.ralphthon.haruman_ralphthon.ui.LocalAppPalette
import org.ralphthon.haruman_ralphthon.ui.money
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private val TerraceBlue = Color(0xFF3182F6)

/** One terrace. The soil and date labels grow with text; no negative row margins are used. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TerraceWeek(
    items: List<WishItem>,
    weekStart: Long,
    weekLabel: String,
    now: Long,
    modifier: Modifier = Modifier,
    onWeek: () -> Unit,
    onItem: (WishItem) -> Unit,
    onDecide: (WishItem) -> Unit,
    animateStates: Set<String> = emptySet(),
    onAnimationConsumed: (String) -> Unit = {}
) {
    val density = LocalDensity.current
    val dark = LocalAppPalette.current.dark
    val fontScale = density.fontScale
    val signWidth = (52f * fontScale.coerceIn(1f, 1.6f)).dp
    val signHeight = (40f * fontScale.coerceAtLeast(1f)).dp
    val soilTop = maxOf(44.dp, signHeight + 4.dp)
    val plantLeft = signWidth + 10.dp
    val grassTop = soilTop - 14.dp
    val grassColors = if (dark) listOf(Color(0xFF668A4D), Color(0xFF426D37)) else listOf(Color(0xFF9AD37F), Color(0xFF76BC5E))
    val soilColors = if (dark) listOf(Color(0xFF725237), Color(0xFF513923)) else listOf(Color(0xFFC08A5D), Color(0xFFA8744A))
    val currentWeek = periodOf(now, YardPeriod.WEEK) == weekStart
    val today = terraceDayStart(now)

    BoxWithConstraints(modifier.fillMaxWidth().testTag("terrace_week_$weekStart")) {
        val plantWidth = (maxWidth - plantLeft - 4.dp).coerceAtLeast(1.dp)
        val widthPx = with(density) { plantWidth.toPx() }
        val basePx = with(density) { 44.dp.toPx() }
        val layout = remember(items, weekStart, widthPx, now, basePx) {
            layoutWeek(items, weekStart, widthPx, now, basePx)
        }
        val summary = layout.summary
        val weekEnd = Calendar.getInstance().apply { timeInMillis = weekStart; add(Calendar.DAY_OF_MONTH, 6) }.timeInMillis
        val format = remember { SimpleDateFormat("M.d", Locale.KOREA) }
        val range = "${format.format(Date(weekStart))} – ${format.format(Date(weekEnd))}"
        val weekDescription = "$weekLabel, $range, 나무 ${summary.realTreeCount}그루 ${terraceKnownAmount(summary.savedWon, summary.unknownSavedCount, summary.realTreeCount)} 아낌, " +
            "구덩이 ${summary.realPitCount}개 ${terraceKnownAmount(summary.boughtWon, summary.unknownBoughtCount, summary.realBuyCount)} 구매 결정, " +
            "결정할 새싹 ${summary.realSproutCount}개. 주의 상품 모두 보기"

        // This whole floor is a >=48dp alternative to crowded individual plants.
        Column(Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "이 주의 상품 모두 보기", onClick = onWeek)
            .testTag("terrace_week_open_$weekStart").semantics { contentDescription = weekDescription }) {
            Spacer(Modifier.height(soilTop))
            FlowRow(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                    .clip(RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp))
                    .background(Brush.verticalGradient(soilColors)).drawBehind {
                        var y = 17.dp.toPx()
                        while (y < size.height) {
                            drawRect(Color(0xFF502D14).copy(alpha = if (dark) .22f else .10f), Offset(0f, y), Size(size.width, 2.dp.toPx()))
                            y += 19.dp.toPx()
                        }
                    }.heightIn(min = 32.dp).padding(start = 6.dp, end = 6.dp, top = 7.dp, bottom = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (summary.realTreeCount > 0) TerraceChip("나무 ${summary.realTreeCount} · ${terraceKnownAmount(summary.savedWon, summary.unknownSavedCount, summary.realTreeCount)}", TerraceChipKind.TREE)
                if (summary.realPitCount > 0) TerraceChip("구덩이 ${summary.realPitCount} · ${terraceKnownAmount(summary.boughtWon, summary.unknownBoughtCount, summary.realBuyCount)}", TerraceChipKind.PIT)
                if (summary.realSproutCount > 0) TerraceChip("새싹 ${summary.realSproutCount}", TerraceChipKind.SPROUT)
                if (summary.realMoundCount > 0) TerraceChip("흙더미 ${summary.realMoundCount}", TerraceChipKind.MOUND)
                if (summary.realItems.isEmpty()) Text("아직 심은 상품이 없어요", fontSize = 10.sp, lineHeight = 14.sp,
                    color = if (dark) Color(0xFFF6E3CC) else Color(0xFFFFF4E4))
            }
            TerraceDayLabels(layout.days, today, plantLeft, plantWidth, fontScale, dark)
        }
        // Decorative foreground has no pointer handlers, so empty grass/sign taps reach the floor.
        Canvas(Modifier.fillMaxWidth().height(soilTop + 8.dp)) {
            val top = grassTop.toPx()
            val lip = Size(size.width, 18.dp.toPx())
            drawRoundRect(Brush.verticalGradient(grassColors, startY = top, endY = top + lip.height), Offset(0f, top), lip, CornerRadius(14.dp.toPx(), 10.dp.toPx()))
            drawLine(Color.White.copy(alpha = .35f), Offset(11.dp.toPx(), top + 1.dp.toPx()), Offset(size.width - 11.dp.toPx(), top + 1.dp.toPx()), 2.dp.toPx())
            if (currentWeek) drawRoundRect(TerraceBlue, Offset(1.5.dp.toPx(), top + 1.5.dp.toPx()),
                Size(size.width - 3.dp.toPx(), lip.height - 3.dp.toPx()), CornerRadius(14.dp.toPx(), 10.dp.toPx()), style = Stroke(2.5.dp.toPx()))
            val start = plantLeft.toPx()
            repeat(6) { i ->
                val x = start + (size.width - start - 8.dp.toPx()) * ((i * 37 % 97) / 97f)
                val bottom = top + 15.dp.toPx()
                val blades = Path().apply {
                    moveTo(x, bottom); lineTo(x + 2.dp.toPx(), bottom - 9.dp.toPx()); lineTo(x + 4.dp.toPx(), bottom)
                    lineTo(x + 7.dp.toPx(), bottom - 7.dp.toPx()); lineTo(x + 10.dp.toPx(), bottom); close()
                }
                drawPath(blades, if (dark) Color(0xFF6B9750) else Color(0xFF70B75C))
            }
            listOf(.32f, .83f).forEachIndexed { index, ratio ->
                val x = start + (size.width - start) * ratio
                drawLine(if (dark) Color(0xFF8DB575) else Color(0xFF61974D), Offset(x, top + 5.dp.toPx()), Offset(x, top + 13.dp.toPx()), 1.dp.toPx())
                drawCircle(if (index == 0) Color(0xFFBAA5F7) else Color(0xFFFFD865), 3.dp.toPx(), Offset(x, top + 4.dp.toPx()))
                drawCircle(Color(0xFFFFF3C0), 1.dp.toPx(), Offset(x, top + 4.dp.toPx()))
            }
        }
        Box(Modifier.padding(start = plantLeft).width(plantWidth).height(soilTop + 8.dp)) {
            val verticalShift = with(density) { (soilTop + 8.dp).toPx() } - layout.groundPx
            layout.plants.forEach { plant ->
                key(plant.item.id) {
                    TerracePlantImage(plant, now, animateStates, onAnimationConsumed,
                        Modifier.offset { IntOffset(plant.leftPx.roundToInt(), (plant.topPx + verticalShift).roundToInt()) }
                            .size(with(density) { plant.sizePx.toDp() }),
                        onClick = { if (plant.item.ready(now)) onDecide(plant.item) else onItem(plant.item) })
                }
            }
            layout.overflows.forEach { overflow ->
                val width = if (fontScale > 1.3f) 36.dp else 28.dp
                val x = with(density) { overflow.centerXPx.toDp() } - width / 2
                Text("+${overflow.count}", Modifier.offset(x.coerceIn(0.dp, (plantWidth - width).coerceAtLeast(0.dp)), 0.dp)
                    .width(width).clip(RoundedCornerShape(6.dp)).background(if (dark) Color(0xFF533B26) else Color(0xFFFFE5BB))
                    .clickable(role = Role.Button, onClickLabel = "이 날의 나머지 상품 보기", onClick = onWeek)
                    .padding(vertical = 2.dp).testTag("terrace_overflow_${overflow.dayStart}")
                    .semantics { contentDescription = "이 날의 상품 ${overflow.count}개 더 있어요. 주 상세 보기" },
                    fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                    color = if (dark) Color(0xFFFFE5BB) else Color(0xFF5B3A22))
            }
        }
        WoodWeekSign(weekLabel, range, summary.realSproutCount > 0, signWidth, signHeight, dark)
    }
}

@Composable
private fun WoodWeekSign(label: String, range: String, ready: Boolean, width: Dp, height: Dp, dark: Boolean) {
    val wood = if (dark) listOf(Color(0xFF96744B), Color(0xFF74502F)) else listOf(Color(0xFFF2D19C), Color(0xFFE3BA7E))
    val ink = if (dark) Color(0xFFFFF1D8) else Color(0xFF5B3A22)
    val shortLabel = Regex("\\d+\\s*주").findAll(label).lastOrNull()?.value ?: "한 주"
    Box(Modifier.padding(start = 6.dp).width(width).height(height + 8.dp)) {
        Box(Modifier.align(Alignment.BottomCenter).size(5.dp, 10.dp).background(Color(0xFF9A6A3F)))
        Column(Modifier.width(width).heightIn(min = height).clip(RoundedCornerShape(8.dp)).background(Brush.verticalGradient(wood))
            .border(2.dp, Color(0xFF9A6A3F), RoundedCornerShape(8.dp)).padding(horizontal = 3.dp, vertical = 3.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(shortLabel, fontSize = 13.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold, color = ink)
                if (ready) Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(if (dark) Color(0xFFB7F18D) else Color(0xFF2DB400)))
            }
            Text(range, fontSize = 8.sp, lineHeight = 10.sp, textAlign = TextAlign.Center, color = ink.copy(alpha = .88f))
        }
    }
}

private enum class TerraceChipKind { TREE, PIT, SPROUT, MOUND }

@Composable
private fun TerraceChip(text: String, kind: TerraceChipKind) {
    val dark = LocalAppPalette.current.dark
    val background = when (kind) {
        TerraceChipKind.TREE -> if (dark) Color(0xFF23482D) else Color(0xFFEAF7EC)
        TerraceChipKind.PIT -> if (dark) Color(0xFF4A3322) else Color(0xFFF4ECE4)
        TerraceChipKind.SPROUT -> if (dark) Color(0xFF4B4020) else Color(0xFFFFF5D6)
        TerraceChipKind.MOUND -> if (dark) Color(0xFF36383A) else Color(0xFFF1F3F5)
    }
    val foreground = when (kind) {
        TerraceChipKind.TREE -> if (dark) Color(0xFFB1E5AE) else Color(0xFF176831)
        TerraceChipKind.PIT -> if (dark) Color(0xFFF5C797) else Color(0xFF805024)
        TerraceChipKind.SPROUT -> if (dark) Color(0xFFFFE18C) else Color(0xFF805900)
        TerraceChipKind.MOUND -> if (dark) Color(0xFFD7DADD) else Color(0xFF555E68)
    }
    Text(text, Modifier.clip(RoundedCornerShape(12.dp)).background(background).padding(horizontal = 7.dp, vertical = 2.dp),
        fontSize = 10.5.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, color = foreground)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TerraceDayLabels(days: List<TerraceDayZone>, today: Long, left: Dp, width: Dp, fontScale: Float, dark: Boolean) {
    val density = LocalDensity.current
    val visible = days.filter { it.items.isNotEmpty() || it.dayStart == today }
    val dayNames = listOf("월", "화", "수", "목", "금", "토", "일")
    val labels = layoutTerraceDayLabels(visible.map { it.centerXPx }, with(density) { width.toPx() }, with(density) { 40.dp.toPx() })
    @Composable fun Label(day: TerraceDayZone, modifier: Modifier) {
        val number = Calendar.getInstance().apply { timeInMillis = day.dayStart }.get(Calendar.DAY_OF_MONTH)
        val isToday = day.dayStart == today
        val dayName = dayNames[days.indexOf(day)]
        Text("$number$dayName", modifier.testTag("terrace_day_label_${day.dayStart}").clip(RoundedCornerShape(5.dp))
            .background(if (isToday) TerraceBlue else Color.Transparent).padding(horizontal = 2.dp),
            color = if (isToday) Color.White else (if (dark) Color(0xFFBED1A6) else Color(0xFF536940)).copy(alpha = if (day.dayStart > today) .55f else 1f),
            fontSize = 9.sp, lineHeight = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
    if (fontScale > 1.3f || labels.requiresFlow) {
        FlowRow(Modifier.padding(start = left, top = 3.dp).width(width).heightIn(min = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            visible.forEach { Label(it, Modifier.widthIn(min = 40.dp)) }
        }
    } else Box(Modifier.padding(start = left, top = 3.dp).width(width).heightIn(min = (13f * fontScale.coerceAtLeast(1f)).dp)) {
        visible.forEachIndexed { index, day ->
            val x = with(density) { labels.leftPositionsPx[index].toDp() }
            Label(day, Modifier.offset(x).width(40.dp))
        }
    }
}

@Composable
private fun TerracePlantImage(
    plant: TerracePlant, now: Long, animateStates: Set<String>, onConsumed: (String) -> Unit,
    modifier: Modifier, onClick: () -> Unit
) {
    val resource = when (plant.kind) {
        TerracePlantKind.MOUND -> R.drawable.prop_acorn_buried
        TerracePlantKind.SPROUT -> R.drawable.prop_sprout_ready
        TerracePlantKind.TREE_SMALL -> R.drawable.prop_tree_small
        TerracePlantKind.TREE_BIG -> R.drawable.prop_tree_big
        TerracePlantKind.TREE_FRUIT -> R.drawable.prop_tree_fruit
        TerracePlantKind.PIT -> R.drawable.prop_hole
    }
    val state = yardItemState(plant.item, now)
    val event = "${plant.item.id}:${state.name}"
    val reduced = mascotMotionReduced()
    var played by rememberSaveable(event) { mutableStateOf(false) }
    val progress = remember(event) { Animatable(if (!reduced && !played && event in animateStates) 0f else 1f) }
    val eligible by rememberUpdatedState(event in animateStates)
    val consume by rememberUpdatedState(onConsumed)
    // An offscreen pager may compose this node before its event becomes eligible. Observe that
    // later transition without keying the running effect to the set removed by consumption.
    LaunchedEffect(event, reduced) {
        if (played) {
            progress.snapTo(1f)
            return@LaunchedEffect
        }
        if (!eligible) {
            progress.snapTo(1f)
            snapshotFlow { eligible }.first { it }
        }
        played = true
        consume(event)
        if (!reduced) {
            progress.snapTo(0f)
            withTimeoutOrNull(420L) { progress.animateTo(1f, tween(if (state == YardItemState.PIT) 300 else 360)) }
        }
        progress.snapTo(1f)
    }
    val stateLabel = when (state) { YardItemState.MOUND -> "고민하고 있어요"; YardItemState.SPROUT -> "결정할 때예요"; YardItemState.TREE -> "안 사기로 했어요"; YardItemState.PIT -> "사기로 했어요" }
    Box(modifier.testTag("terrace_plant_${plant.item.id}").clickable(role = Role.Button, onClick = onClick)
        .semantics { contentDescription = "${plant.item.name}, $stateLabel" }) {
        if (state == YardItemState.SPROUT) Canvas(Modifier.fillMaxSize()) {
            drawCircle(Brush.radialGradient(listOf(Color(0xB3FFCD3C), Color.Transparent)), size.minDimension * .48f, center)
        }
        Image(painterResource(resource), null, Modifier.fillMaxSize().graphicsLayer {
            transformOrigin = TransformOrigin(.5f, .9f)
            if (state == YardItemState.PIT) alpha = progress.value else scaleY = progress.value
        })
        if (!reduced && state == YardItemState.PIT && progress.value < 1f) Canvas(Modifier.fillMaxSize()) {
            repeat(8) { i ->
                val direction = if (i % 2 == 0) -1 else 1
                val t = progress.value
                val x = center.x + direction * size.width * (.15f + i * .025f) * t
                val y = size.height * (.73f - .55f * t + .4f * t * t) + i % 3 * 2.dp.toPx()
                drawCircle(Color(0xFFC29366).copy(alpha = 1f - t), (1.2f + i % 2).dp.toPx(), Offset(x, y))
            }
        }
    }
}

private fun terraceKnownAmount(amount: Long, unknown: Int, count: Int): String =
    if (unknown > 0 && unknown == count) "금액 미정" else money(amount)

private fun terraceDayStart(time: Long): Long = Calendar.getInstance().apply {
    timeInMillis = time; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis
