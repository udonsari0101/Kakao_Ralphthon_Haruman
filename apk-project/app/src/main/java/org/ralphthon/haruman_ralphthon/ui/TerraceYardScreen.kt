package org.ralphthon.haruman_ralphthon.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.ralphthon.haruman_ralphthon.R
import org.ralphthon.haruman_ralphthon.data.*
import org.ralphthon.haruman_ralphthon.ui.mascot.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Month totals belong to the month; each terrace includes the complete Monday–Sunday week. */
@Composable
fun YardScreen(
    items: List<WishItem>, now: Long, period: YardPeriod, goalWon: Long?,
    onItem: (WishItem) -> Unit, onDecide: (WishItem) -> Unit,
    onGoalEdit: () -> Unit, onPeriodChange: (YardPeriod) -> Unit,
    onReview: () -> Unit = {}, seenAnimations: Set<String> = emptySet(),
    onAnimationConsumed: (String) -> Unit = {},
) {
    var localSeen by remember { mutableStateOf(setOf<String>()) }
    val consumed = seenAnimations + localSeen
    val motionKeys = items.filterNot { it.isSample }.mapNotNull { item ->
        val state = yardItemState(item, now)
        if (state == YardItemState.SPROUT || (state != YardItemState.MOUND && "${item.id}:SPROUT" in consumed)) "${item.id}:${state.name}" else null
    }.toSet() - consumed
    val consume: (String) -> Unit = { key ->
        if (key !in localSeen) localSeen = localSeen + key
        onAnimationConsumed(key)
    }
    key(period) { TerracePages(items, now, period, goalWon, onItem, onDecide, onGoalEdit, onPeriodChange, onReview, motionKeys, consume) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TerracePages(
    records: List<WishItem>, now: Long, period: YardPeriod, goal: Long?,
    onItem: (WishItem) -> Unit, onDecide: (WishItem) -> Unit,
    onGoal: () -> Unit, onPeriod: (YardPeriod) -> Unit, onReview: () -> Unit,
    animationKeys: Set<String>, consume: (String) -> Unit,
) {
    val current = periodOf(now, period)
    val actual = records.filterNot { it.isSample }
    val first = actual.minOfOrNull { it.createdAt }?.coerceAtMost(now) ?: now
    val periods = remember(first, current, period) { periodsBetween(first, now, period) }
    val pager = rememberPagerState(initialPage = periods.lastIndex, pageCount = { periods.size })
    var selectedWeek by rememberSaveable { mutableStateOf<Long?>(null) }
    var roomAt by rememberSaveable { mutableStateOf<Long?>(null) }
    var pitItemId by rememberSaveable { mutableStateOf<String?>(null) }
    var pitPurchaseComplete by rememberSaveable { mutableStateOf(false) }
    val inspectPlant: (WishItem) -> Unit = { item ->
        if (item.decision == Decision.BUY) {
            pitPurchaseComplete = false
            pitItemId = item.id
        } else onItem(item)
    }
    val reduced = mascotMotionReduced()
    val dark = LocalAppPalette.current.dark
    val sky = if (dark) Color(0xFF142732) else Color(0xFFCFEBFF)
    val grass = if (dark) Color(0xFF263A26) else Color(0xFFDDEBC9)
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(sky, if (dark) Color(0xFF1D302B) else Color(0xFFEAF5DC), grass)))) {
        ContentFrame {
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize().testTag("yard_pager"), key = { periods[it] }) { index ->
                val at = periods[index]
                val summary = remember(records, at, period, now) { yardSummary(records, at, period, now) }
                val weeks = remember(at, period) { terraceWeeks(at, period) }
                val allReady = actual.count { it.ready(now) }
                val visiblePit = actual.firstOrNull { item -> item.decision == Decision.BUY && weeks.any { start -> item.createdAt in periodBounds(start, YardPeriod.WEEK) } }
                LazyColumn(Modifier.fillMaxSize().testTag(if (index == pager.currentPage) "yard_screen" else "yard_offscreen"),
                    contentPadding = PaddingValues(bottom = 24.dp)) {
                    item("heading") {
                        Column(Modifier.padding(horizontal = 18.dp).padding(top = 12.dp)) {
                            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("마당", Modifier.padding(end = 16.dp), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = AppColors.Text)
                                Row(Modifier.clip(RoundedCornerShape(14.dp)).background(AppColors.Text.copy(alpha = .055f)).padding(3.dp)) {
                                    YardPeriod.entries.forEach { value ->
                                        TextButton(onClick = { onPeriod(value) }, modifier = Modifier.heightIn(min = 48.dp)
                                            .testTag("yard_period_${value.name.lowercase(Locale.ROOT)}")
                                            .clip(RoundedCornerShape(11.dp)).background(if (value == period) AppColors.Surface else Color.Transparent),
                                            colors = ButtonDefaults.textButtonColors(contentColor = if (value == period) AppColors.Text else AppColors.Secondary)) {
                                            Text(value.label, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center) {
                                IconButton(onClick = { pager.requestScrollToPage(index - 1) }, enabled = index > 0,
                                    modifier = Modifier.size(48.dp).testTag("yard_previous").semantics { contentDescription = "이전 기간" }
                                        .clip(CircleShape).background(AppColors.Surface.copy(alpha = .75f))) { Text("‹", fontSize = 28.sp) }
                                Text(yardPeriodLabel(at, period), Modifier.weight(1f).padding(horizontal = 6.dp).testTag("yard_period_title"),
                                    fontSize = 19.sp, fontWeight = FontWeight.Bold, color = AppColors.Text, textAlign = TextAlign.Center)
                                IconButton(onClick = { pager.requestScrollToPage(index + 1) }, enabled = index < periods.lastIndex,
                                    modifier = Modifier.size(48.dp).testTag("yard_next").semantics { contentDescription = "다음 기간" }
                                        .clip(CircleShape).background(AppColors.Surface.copy(alpha = .75f))) { Text("›", fontSize = 28.sp) }
                            }
                            if (index != periods.lastIndex) TextButton(onClick = { pager.requestScrollToPage(periods.lastIndex) },
                                modifier = Modifier.align(Alignment.CenterHorizontally).heightIn(min = 48.dp).testTag("yard_current")) {
                                Text(if (period == YardPeriod.MONTH) "이번 달로" else "이번 주로")
                            }
                            TerraceSummary(summary, goal, period, at, reduced)
                        }
                    }
                    items(weeks, key = { "week_$it" }) { start ->
                        val weekNumber = weeks.indexOf(start) + 1
                        TerraceWeek(records, start, if (period == YardPeriod.MONTH) "${weekNumber}주" else "한 주", now,
                            Modifier.fillMaxWidth().padding(horizontal = 10.dp).padding(top = 10.dp), onWeek = { selectedWeek = start },
                            onItem = inspectPlant, onDecide = onDecide,
                            animateStates = if (index == pager.currentPage) animationKeys else emptySet(), onAnimationConsumed = consume)
                    }
                    item("yard_companion") {
                        Box(Modifier.padding(horizontal = 18.dp)) { YardCompanion(visiblePit, allReady, onReview) }
                    }
                    item("yard_explanation") {
                        Text(if (period == YardPeriod.MONTH) "위 요약은 이달 기록이에요. 각 밭은 월요일부터 일요일까지의 기록을 보여줘요." else "추가한 날에 심었어요. 식물이나 밭의 팻말을 눌러 살펴봐요.",
                            Modifier.padding(horizontal = 20.dp, vertical = 12.dp), style = MaterialTheme.typography.bodySmall, color = AppColors.Secondary)
                    }
                    if (period == YardPeriod.WEEK) {
                        val byDay = summary.realItems.groupBy { terraceDay(it.createdAt) }.toSortedMap()
                        if (byDay.isEmpty()) item("week_empty") { EmptyState("아직 조용한 마당이에요", "상품을 담으면 작은 도토리가 심겨요", MascotPose.TORI_IDLE, compact = true) }
                        byDay.forEach { (day, dayItems) ->
                            item("day_$day") { DayHeading(day, dayItems.size) }
                            items(dayItems, key = { "weekly_item_${it.id}" }) { item -> TerraceProductRow(item, now, onItem, onDecide) }
                        }
                    }
                    item("yard_actions") {
                        FlowRow(Modifier.padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { roomAt = at }, modifier = Modifier.heightIn(min = 48.dp).testTag("yard_storage_open")) { Text("저장방 보기") }
                            TextButton(onClick = onGoal, modifier = Modifier.heightIn(min = 48.dp).testTag("yard_goal_edit")) { Text(if (goal == null) "소비 목표 정하기" else "목표 다시 정하기") }
                        }
                        if (summary.sampleCount > 0) Text("예시 ${summary.sampleCount}개는 실제 마당과 합계에서 뺐어요", Modifier.padding(20.dp), style = MaterialTheme.typography.bodySmall, color = AppColors.Secondary)
                    }
                }
            }
        }
    }
    selectedWeek?.let { start ->
        TerraceWeekSheet(records, start, now, onItem = { selectedWeek = null; onItem(it) },
            onDecide = { selectedWeek = null; onDecide(it) }, onDismiss = { selectedWeek = null })
    }
    roomAt?.let { at -> StorageRoomSheet(yardSummary(records, at, period, now).budgetItems, yardPeriodLabel(at, period),
        onItem = { roomAt = null; onItem(it) }, onDismiss = { roomAt = null }) }
    pitItemId?.let { id ->
        val item = records.firstOrNull { it.id == id }
        val dismissPit = { pitItemId = null; pitPurchaseComplete = false }
        AlertDialog(
            onDismissRequest = dismissPit,
            icon = { Image(painterResource(R.drawable.prop_hole), null, Modifier.size(52.dp)) },
            title = { Text(if (pitPurchaseComplete) "구매 완료" else "구덩이 제거는 500원이에요") },
            text = { Text(if (pitPurchaseComplete) "목업 데모로 완료 화면을 보여드렸어요.\n실제로 결제하지 않았어요. 구덩이와 소비 기록도 그대로예요."
                else "토리가 구덩이를 보고 깜짝 놀랐어요.\n\n목업 데모예요. 실제 결제나 구덩이 제거, 소비 기록 변경은 없어요.",
                style = MaterialTheme.typography.bodySmall, color = AppColors.Secondary) },
            confirmButton = {
                if (pitPurchaseComplete) TextButton(onClick = dismissPit,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("yard_pit_complete_close")) { Text("확인") }
                else TextButton(onClick = {
                    // This presentation-only transition has no store, payment or product callback.
                    if (pitItemId == id && !pitPurchaseComplete && item?.decision == Decision.BUY) pitPurchaseComplete = true
                }, enabled = item?.decision == Decision.BUY,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("yard_pit_buy")) { Text("구매하기") }
            },
            dismissButton = { if (!pitPurchaseComplete) TextButton(onClick = dismissPit,
                modifier = Modifier.heightIn(min = 48.dp).testTag("yard_pit_close")) { Text("닫기") } },
            modifier = Modifier.testTag(if (pitPurchaseComplete) "yard_pit_complete" else "yard_pit_notice"),
        )
    }
}

@Composable
private fun TerraceSummary(summary: YardSummary, goal: Long?, period: YardPeriod, at: Long, reduced: Boolean) {
    val saved = terraceMoney(summary.savedWon, "$period:$at:saved", reduced)
    val bought = terraceMoney(summary.boughtWon, "$period:$at:bought", reduced)
    val trees = summary.realTreeCount
    val pits = summary.realPitCount
    val dark = LocalAppPalette.current.dark
    val tier = goal?.let { budgetTier(summary.budgetBoughtWon, it) } ?: 0
    val gauge = when (tier) { 0 -> if (dark) Color(0xFF83B8FF) else Color(0xFF3182F6); 1 -> Color(0xFFF2BC30); 2 -> Color(0xFFF28C28); else -> AppColors.Red }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(AppColors.Surface.copy(alpha = .95f)).padding(12.dp).testTag("yard_summary")) {
        BoxWithConstraints {
            val fontScale = LocalDensity.current.fontScale
            val stack = maxWidth < 290.dp || fontScale > 1.4f
            val twoColumns = fontScale >= 1.2f
            val stats = listOf(
                Triple(R.drawable.prop_tree_big, "나무 $trees · 아낌", terraceAmount(saved, summary.unknownSavedCount, trees)),
                Triple(R.drawable.prop_hole, "구덩이 $pits · 구매", terraceAmount(bought, summary.unknownBoughtCount, pits)),
                Triple(R.drawable.prop_sprout_ready, "고민 중", "${summary.realSproutCount + summary.realMoundCount}개"),
            )
            if (stack) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                stats.forEach { (icon, label, value) -> SummaryStat(icon, label, value, Modifier.fillMaxWidth()) }
            } else if (twoColumns) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    stats.take(2).forEach { (icon, label, value) -> SummaryStat(icon, label, value, Modifier.weight(1f)) }
                }
                val (icon, label, value) = stats.last()
                SummaryStat(icon, label, value, Modifier.fillMaxWidth())
            } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Top) {
                stats.forEach { (icon, label, value) -> SummaryStat(icon, label, value, Modifier.weight(1f)) }
            }
        }
        if (summary.unknownSavedCount + summary.unknownBoughtCount > 0) Text("금액 미정 ${summary.unknownSavedCount + summary.unknownBoughtCount}개는 금액 합계에서 뺐어요",
            Modifier.padding(top = 8.dp), fontSize = 11.sp, color = AppColors.Secondary)
        if (goal != null) {
            val ratio = (summary.budgetBoughtWon.toDouble() / goal).coerceIn(0.0, 1.0).toFloat()
            val percent = String.format(Locale.KOREA, "%.0f", summary.budgetBoughtWon.toDouble() / goal * 100)
            val allUnknown = summary.budgetItems.isNotEmpty() && summary.budgetUnknownCount == summary.budgetItems.size
            Text("${if (period == YardPeriod.MONTH) "이달" else "이번 주"} 구매 결정 · ${terraceAmount(summary.budgetBoughtWon, summary.budgetUnknownCount, summary.budgetItems.size)}", Modifier.padding(top = 10.dp), fontSize = 11.sp, color = AppColors.Secondary)
            Text("목표 ${money(goal)} · ${if (allUnknown) "금액 미정" else "$percent%"}", Modifier.padding(top = 3.dp).testTag("yard_goal_label"), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AppColors.Text)
            LinearProgressIndicator(progress = { ratio }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(7.dp).clip(CircleShape).testTag("yard_goal_gauge"), color = gauge, trackColor = AppColors.Soft)
            if (summary.budgetUnknownCount > 0) Text("구매 금액 미정 ${summary.budgetUnknownCount}개는 게이지에서 뺐어요", Modifier.padding(top = 6.dp), fontSize = 11.sp, color = AppColors.Secondary)
        }
    }
}

@Composable
private fun SummaryStat(icon: Int, label: String, value: String, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(icon), null, Modifier.size(25.dp))
        Column(Modifier.padding(start = 4.dp)) {
            Text(label, fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold, color = AppColors.Text)
            Text(value, fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.Bold, color = AppColors.Text)
        }
    }
}

@Composable
private fun YardCompanion(pit: WishItem?, count: Int, onReview: () -> Unit) {
    val bubbles = LocalShowBubbles.current
    val reduced = mascotMotionReduced()
    val disappointed = pit != null
    var phraseIndex by rememberSaveable(disappointed) { mutableIntStateOf(0) }
    val phrases = if (disappointed) listOf("아앗… 구덩이가 생겼어요…", "조금 아쉽지만 괜찮아요", "다음에도 같이 살펴봐요")
        else listOf("구덩이가 없어요! 토리가 신났어요", "포근한 마당이라 기분이 좋아요", "오늘도 만나서 반가워요")
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val pressScale by animateFloatAsState(if (pressed && !reduced) .97f else 1f,
        animationSpec = tween(if (reduced) 0 else 100), label = "yardToriPress")
    Column(Modifier.fillMaxWidth().padding(top = 10.dp).testTag("yard_companion")) {
        // The tail overlaps the image's transparent edge by 8dp, without covering its tap target.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
            if (bubbles) YardToriSpeech(phrases[phraseIndex % phrases.size], Modifier.weight(1f))
            else Spacer(Modifier.weight(1f))
            Box(Modifier.size(88.dp).clip(CircleShape).testTag("yard_tori_talk")
                .clickable(interactionSource = interactions, indication = LocalIndication.current,
                    role = Role.Button, onClickLabel = if (bubbles) "토리의 다른 이야기 듣기" else "토리와 인사하기") {
                    if (bubbles) phraseIndex = (phraseIndex + 1) % phrases.size
                }.semantics(mergeDescendants = true) { stateDescription = if (disappointed) "조금 아쉬운 토리" else "행복한 토리" }) {
                Mascot(if (disappointed) MascotPose.TORI_DISAPPOINTED else MascotPose.TORI_CHEER,
                    size = 88.dp, breathing = false,
                    modifier = Modifier.testTag(if (disappointed) "yard_tori_disappointed" else "yard_tori_happy")
                        .graphicsLayer { scaleX = pressScale; scaleY = pressScale })
            }
        }
        if (count > 0) TextButton(onClick = onReview, modifier = Modifier.heightIn(min = 48.dp).testTag("yard_tori_review")) {
            Text("정리 시간 시작 · ${count}개")
        }
    }
}

/** Unlimited text wrapping keeps this adjacent bubble readable at large accessibility font sizes. */
@Composable
private fun YardToriSpeech(text: String, modifier: Modifier = Modifier) {
    val surface = AppColors.Surface
    val border = AppColors.Border
    Box(modifier.testTag("yard_tori_bubble").semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
        .drawBehind {
            val tail = 8.dp.toPx()
            val stroke = 1.dp.toPx()
            val bodyWidth = (size.width - tail - stroke).coerceAtLeast(0f)
            val bodyHeight = (size.height - stroke).coerceAtLeast(0f)
            val origin = Offset(stroke / 2, stroke / 2)
            val body = Size(bodyWidth, bodyHeight)
            drawRoundRect(surface, origin, body, CornerRadius(16.dp.toPx()))
            drawRoundRect(border, origin, body, CornerRadius(16.dp.toPx()), style = Stroke(stroke))
            val center = size.height / 2
            val bodyRight = size.width - tail
            val triangle = Path().apply {
                moveTo(bodyRight - stroke, center - 6.dp.toPx())
                lineTo(size.width - stroke, center)
                lineTo(bodyRight - stroke, center + 6.dp.toPx())
                close()
            }
            val edge = Path().apply {
                moveTo(bodyRight - stroke / 2, center - 6.dp.toPx())
                lineTo(size.width - stroke, center)
                lineTo(bodyRight - stroke / 2, center + 6.dp.toPx())
            }
            drawPath(triangle, surface)
            drawPath(edge, border, style = Stroke(stroke))
        }.padding(start = 12.dp, end = 20.dp, top = 12.dp, bottom = 12.dp)) {
        Text(text, Modifier.testTag("yard_tori_phrase"), color = AppColors.Text,
            fontSize = 14.sp, lineHeight = 20.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TerraceWeekSheet(records: List<WishItem>, start: Long, now: Long, onItem: (WishItem) -> Unit, onDecide: (WishItem) -> Unit, onDismiss: () -> Unit) {
    val summary = yardSummary(records, start, YardPeriod.WEEK, now)
    val byDay = summary.realItems.groupBy { terraceDay(it.createdAt) }.toSortedMap()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = AppColors.Background) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = (LocalConfiguration.current.screenHeightDp * .85f).dp).testTag("yard_week_sheet"), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { Column(Modifier.padding(20.dp)) {
                Text("한 주의 마당", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = AppColors.Text)
                Text(yardPeriodLabel(start, YardPeriod.WEEK), Modifier.padding(top = 6.dp), color = AppColors.Secondary)
                Text("상품 ${summary.realItems.size}개 · 이웃한 달의 날짜도 함께 보여요", Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall, color = AppColors.Secondary)
            } }
            if (byDay.isEmpty()) item { EmptyState("비어 있는 밭이에요", "이 주에는 상품을 담지 않았어요", MascotPose.TORI_IDLE, compact = true) }
            byDay.forEach { (day, daily) ->
                item("heading_$day") { DayHeading(day, daily.size) }
                items(daily, key = { it.id }) { item -> TerraceProductRow(item, now, onItem, onDecide) }
            }
            item { TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("yard_week_close")) { Text("닫기") } }
        }
    }
}

@Composable
private fun DayHeading(day: Long, count: Int) {
    Text("${SimpleDateFormat("M월 d일 E요일", Locale.KOREA).format(Date(day))} · ${count}개", Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = AppColors.Text)
}

@Composable
private fun TerraceProductRow(item: WishItem, now: Long, onItem: (WishItem) -> Unit, onDecide: (WishItem) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        ProductRow(item, now) { onItem(item) }
        if (item.decision != Decision.PENDING && item.decisionPriceWon == null) Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.prop_sign_unknown), "가격 미정", Modifier.size(24.dp))
            Text("결정할 때 가격이 정해지지 않았어요", fontSize = 12.sp, color = AppColors.Secondary)
        }
        if (item.ready(now)) TextButton(onClick = { onDecide(item) }, modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth().heightIn(min = 48.dp).testTag("yard_decide_${item.id}")) { Text("이 새싹 결정하기") }
    }
}

@Composable
private fun terraceMoney(value: Long, event: String, reduced: Boolean): Long {
    var played by rememberSaveable(event) { mutableStateOf(false) }
    val progress = remember(event) { Animatable(if (played || reduced) 1f else 0f) }
    LaunchedEffect(event, reduced) {
        if (played || reduced) progress.snapTo(1f) else { played = true; progress.animateTo(1f, tween(450)) }
    }
    return if (progress.value >= 1f) value else (value.toDouble() * progress.value).toLong()
}

private fun terraceAmount(value: Long, unknown: Int, count: Int): String = if (count > 0 && unknown == count) "금액 미정" else money(value)
private fun terraceDay(time: Long): Long = Calendar.getInstance().apply {
    timeInMillis = time; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis
