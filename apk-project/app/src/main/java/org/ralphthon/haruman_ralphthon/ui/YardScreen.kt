package org.ralphthon.haruman_ralphthon.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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

/** createdAt owns a plot; decidedAt owns a spending period. These are deliberately separate. */
@Composable
fun YardScreen(
    items: List<WishItem>,
    now: Long,
    period: YardPeriod,
    goalWon: Long?,
    onItem: (WishItem) -> Unit,
    onDecide: (WishItem) -> Unit,
    onGoalEdit: () -> Unit,
    onPeriodChange: (YardPeriod) -> Unit
) {
    key(period) {
        YardContent(items, now, period, goalWon, onItem, onDecide, onGoalEdit, onPeriodChange)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun YardContent(
    items: List<WishItem>, now: Long, period: YardPeriod, goalWon: Long?,
    onItem: (WishItem) -> Unit, onDecide: (WishItem) -> Unit,
    onGoalEdit: () -> Unit, onPeriodChange: (YardPeriod) -> Unit
) {
    val currentStart = periodOf(now, period)
    val first = items.minOfOrNull { it.createdAt }?.coerceAtMost(now) ?: now
    val periods = remember(first, currentStart, period) { periodsBetween(first, now, period).ifEmpty { listOf(currentStart) } }
    val pager = rememberPagerState(initialPage = periods.lastIndex, pageCount = { periods.size })
    val reduced = mascotMotionReduced()
    val pageIndex = pager.currentPage.coerceIn(periods.indices)
    val at = periods[pageIndex]
    val summary = remember(items, at, period, now) { yardSummary(items, at, period, now) }
    val actual = summary.items.filterNot { it.isSample }.sortedBy { it.createdAt }
    val samples = summary.items.filter { it.isSample }.sortedBy { it.createdAt }
    var filter by rememberSaveable { mutableStateOf("all") }
    var selectedDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var showRoom by rememberSaveable { mutableStateOf(false) }
    val title = yardPeriodLabel(at, period)
    val earlierReady = items.any { !it.isSample && it.createdAt < at && it.ready(now) }
    val readyCount = actual.count { it.ready(now) }
    val filtered = actual.filter {
        when (filter) { "skip" -> it.decision == Decision.SKIP; "buy" -> it.decision == Decision.BUY; "pending" -> it.decision == Decision.PENDING; else -> true }
    }
    val jump: (Int) -> Unit = { target ->
        // The calendar lives below a lazy header and may not have been measured yet.
        // Suspending scrollToPage waits for that first layout, leaving the header unchanged.
        // A request updates the index immediately and is applied when the calendar is composed.
        pager.requestScrollToPage(target.coerceIn(periods.indices))
    }
    // Changing the week/month setting creates a new keyed pager on the current period.
    LaunchedEffect(currentStart) { if (at > currentStart) pager.requestScrollToPage(periods.lastIndex) }

    ContentFrame {
        LazyColumn(Modifier.fillMaxSize().testTag("yard_screen"), contentPadding = PaddingValues(bottom = 28.dp)) {
            item("yard_header") {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) {
                    Text("도토리 마당", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = AppColors.Text)
                    Text("담아 둔 마음이 어떻게 자랐는지 살펴봐요", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium, color = AppColors.Secondary)
                    FlowRow(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        YardPeriod.entries.forEach { value ->
                            FilterChip(selected = value == period, onClick = { onPeriodChange(value) },
                                modifier = Modifier.heightIn(min = 48.dp).testTag("yard_period_${value.name.lowercase(Locale.ROOT)}"),
                                label = { Text(if (value == YardPeriod.WEEK) "한 주" else "한 달") })
                        }
                    }
                    Text(title, Modifier.padding(top = 16.dp).testTag("yard_period_title"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = AppColors.Text)
                    FlowRow(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedButton(onClick = { jump(pageIndex - 1) }, enabled = pageIndex > 0,
                            modifier = Modifier.heightIn(min = 48.dp).testTag("yard_previous")) {
                            Text(if (earlierReady) "‹ 이전 · 새싹 있어요" else "‹ 이전")
                        }
                        OutlinedButton(onClick = { jump(pageIndex + 1) }, enabled = pageIndex < periods.lastIndex,
                            modifier = Modifier.heightIn(min = 48.dp).testTag("yard_next")) { Text("다음 ›") }
                        TextButton(onClick = { jump(periods.lastIndex) }, enabled = pageIndex != periods.lastIndex,
                            modifier = Modifier.heightIn(min = 48.dp).testTag("yard_current")) {
                            Text(if (period == YardPeriod.WEEK) "이번 주로" else "이번 달로")
                        }
                    }
                    if (readyCount > 0) Text("결정을 기다리는 새싹 ${readyCount}개가 있어요", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium, color = AppColors.Green)
                }
            }
            item("yard_summary") {
                YardSummaryCard(actual, summary, at, period, reduced)
            }
            item("yard_calendar") {
                HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth().testTag("yard_pager"), key = { periods[it] }) { index ->
                    val start = periods[index]
                    val bounds = periodBounds(start, period)
                    val pageItems = items.filter { !it.isSample && it.createdAt >= bounds.startInclusive && it.createdAt < bounds.endExclusive }
                    YardCalendar(start, period, pageItems, now) { selectedDay = it }
                }
                Text("추가한 날에 심었어요. 날짜를 누르면 상품을 볼 수 있어요", Modifier.padding(horizontal = 20.dp, vertical = 12.dp), style = MaterialTheme.typography.bodySmall, color = AppColors.Secondary)
            }
            item("yard_budget") {
                YardBudgetCard(summary, title, goalWon, { showRoom = true }, onGoalEdit)
            }
            item("yard_filters") {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Text("이 마당의 상품", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = AppColors.Text)
                    FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("all" to "전체", "skip" to "나무", "buy" to "구덩이", "pending" to "고민 중").forEach { (id, label) ->
                            FilterChip(selected = filter == id, onClick = { filter = id }, label = { Text(label) },
                                modifier = Modifier.heightIn(min = 48.dp).testTag("yard_filter_$id"))
                        }
                    }
                }
            }
            if (filtered.isEmpty()) item("yard_empty") {
                EmptyState(if (actual.isEmpty()) "아직 조용한 마당이에요" else "이런 상품은 아직 없어요",
                    if (actual.isEmpty()) "상품을 담으면 그날의 마당에\n작은 도토리가 심겨요" else "다른 모습을 골라 살펴봐요", MascotPose.TORI_IDLE, compact = true)
            }
            items(filtered, key = { "yard_item_${it.id}" }) { item -> YardProductRow(item, now, onItem, onDecide) }
            if (samples.isNotEmpty()) {
                item("yard_samples") { Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                    Text("예시 상품 ${samples.size}개", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = AppColors.Text)
                    Text("실제 마당과 개수·금액 합계에는 포함하지 않았어요", Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall, color = AppColors.Secondary)
                } }
                items(samples, key = { "yard_sample_${it.id}" }) { item -> YardProductRow(item, now, onItem, onDecide) }
            }
        }
    }
    selectedDay?.let { day ->
        val tomorrow = calendarAt(day).apply { add(Calendar.DAY_OF_MONTH, 1) }.timeInMillis
        YardDaySheet(items.filter { !it.isSample && it.createdAt >= day && it.createdAt < tomorrow }.sortedBy { it.createdAt }, day, now,
            onItem = { selectedDay = null; onItem(it) }, onDecide = { selectedDay = null; onDecide(it) }, onDismiss = { selectedDay = null })
    }
    if (showRoom) StorageRoomSheet(summary.budgetItems, title, onItem = { showRoom = false; onItem(it) }, onDismiss = { showRoom = false })
}

@Composable
private fun YardSummaryCard(actual: List<WishItem>, summary: YardSummary, at: Long, period: YardPeriod, reduced: Boolean) {
    val saved = yardAnimatedMoney(summary.savedWon, "$period:$at:saved", reduced)
    val bought = yardAnimatedMoney(summary.boughtWon, "$period:$at:bought", reduced)
    val trees = actual.count { it.decision == Decision.SKIP }
    val pits = actual.count { it.decision == Decision.BUY }
    val savingLabel = if (trees > 0 && summary.unknownSavedCount == trees) "금액 미정" else "${money(saved)} 아꼈어요"
    Column(Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(AppColors.Surface).padding(18.dp).testTag("yard_summary")) {
        Text("이 기간에 담은 ${actual.size}개 상품", style = MaterialTheme.typography.labelLarge, color = AppColors.Secondary)
        Text("나무 ${trees}그루 · $savingLabel", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = AppColors.Green)
        Text("구덩이 ${pits}개 · ${yardAmount(bought, summary.unknownBoughtCount, pits)} 구매 결정", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium, color = AppColors.Text)
        Text("새싹 ${summary.realSproutCount}개 · 흙더미 ${summary.realMoundCount}개", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium, color = AppColors.Secondary)
        if (summary.unknownSavedCount > 0 || summary.unknownBoughtCount > 0) Text("금액 미정: 나무 ${summary.unknownSavedCount}개 · 구덩이 ${summary.unknownBoughtCount}개\n금액을 아는 상품만 합산했어요", Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodySmall, color = AppColors.Secondary)
        Text("결정할 때의 가격으로 기록해요", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
    }
}

@Composable
private fun yardAnimatedMoney(value: Long, event: String, reduced: Boolean): Long {
    var played by rememberSaveable(event) { mutableStateOf(false) }
    val progress = remember(event) { Animatable(if (played || reduced) 1f else 0f) }
    LaunchedEffect(event, reduced) {
        if (played || reduced) progress.snapTo(1f) else {
            played = true
            progress.animateTo(1f, tween(420))
        }
    }
    return if (progress.value >= 1f) value else (value.toDouble() * progress.value).toLong()
}

@Composable
private fun YardCalendar(at: Long, period: YardPeriod, items: List<WishItem>, now: Long, onDay: (Long) -> Unit) {
    val first = calendarAt(at)
    val leading = if (period == YardPeriod.MONTH) (first.get(Calendar.DAY_OF_WEEK) + 5) % 7 else 0
    val count = if (period == YardPeriod.WEEK) 7 else first.getActualMaximum(Calendar.DAY_OF_MONTH)
    val cells = ((leading + count + 6) / 7) * 7
    val today = dayStart(now)
    val byDay = remember(items) { items.groupBy { dayStart(it.createdAt) } }
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
        // Seven individually usable dates retain 48 dp touch widths on a narrow 320 dp phone.
        val calendarWidth = maxOf(maxWidth, 336.dp)
        Column(Modifier.horizontalScroll(rememberScrollState())) {
            Column(Modifier.width(calendarWidth).clip(RoundedCornerShape(18.dp)).background(AppColors.GreenTint).padding(vertical = 8.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    listOf("월", "화", "수", "목", "금", "토", "일").forEach { day ->
                        Text(day, Modifier.weight(1f).padding(vertical = 8.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = AppColors.Secondary)
                    }
                }
                repeat(cells / 7) { row ->
                    Row(Modifier.fillMaxWidth()) {
                        repeat(7) { col ->
                            val index = row * 7 + col - leading
                            if (index !in 0 until count) Spacer(Modifier.weight(1f).height(112.dp))
                            else {
                                val day = calendarAt(at).apply { add(Calendar.DAY_OF_MONTH, index) }.timeInMillis
                                val daily = byDay[day].orEmpty().sortedBy { it.createdAt }
                                YardDayCell(day, daily, today == day, now, Modifier.weight(1f), onDay)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun YardDayCell(day: Long, items: List<WishItem>, today: Boolean, now: Long, modifier: Modifier, onDay: (Long) -> Unit) {
    val number = calendarAt(day).get(Calendar.DAY_OF_MONTH)
    val ready = items.count { it.ready(now) }
    val dayDescription = "${dateLabel(day)}, ${if (today) "오늘, " else ""}상품 ${items.size}개${if (ready > 0) ", 결정할 새싹 ${ready}개" else ""}, 날짜의 상품 보기"
    Column(modifier.heightIn(min = 112.dp).clip(RoundedCornerShape(10.dp))
        .background(if (today) AppColors.Surface else AppColors.GreenTint)
        .border(if (today) 1.5.dp else .5.dp, if (today) AppColors.Green else AppColors.Green.copy(alpha = .14f), RoundedCornerShape(10.dp))
        .testTag("yard_day_$day").clickable(role = Role.Button) { onDay(day) }
        .semantics(mergeDescendants = true) { contentDescription = dayDescription }
        .padding(horizontal = 2.dp, vertical = 5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(number.toString(), fontSize = 12.sp, fontWeight = if (today) FontWeight.Bold else FontWeight.Medium, color = AppColors.Text)
        Column(Modifier.heightIn(min = 66.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            if (items.isEmpty()) Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                Text("·", color = AppColors.Green.copy(alpha = .5f), fontSize = 20.sp)
            }
            items.take(3).forEach { item -> YardPlant(item, now, Modifier.size(22.dp)) }
        }
        if (items.size > 3) Text("+${items.size - 3}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AppColors.Green)
    }
}

@Composable
private fun YardPlant(item: WishItem, now: Long, modifier: Modifier = Modifier) {
    when (item.decision) {
        Decision.BUY -> DugPit(modifier)
        Decision.SKIP -> when {
            item.decisionPriceWon == null || item.decisionPriceWon < 0 -> UnpricedTree(modifier)
            item.decisionPriceWon >= 200_000 -> FruitingTree(modifier)
            else -> Image(painterResource(if (item.decisionPriceWon >= 50_000) R.drawable.prop_tree_big else R.drawable.prop_tree_small), null, modifier)
        }
        Decision.PENDING -> Image(painterResource(if (item.ready(now)) R.drawable.prop_acorn_sprout else R.drawable.prop_acorn_buried), null, modifier)
    }
}

@Composable
private fun YardBudgetCard(summary: YardSummary, periodLabel: String, goalWon: Long?, onRoom: () -> Unit, onGoalEdit: () -> Unit) {
    val goal = goalWon?.takeIf { it > 0 }
    val amount = summary.budgetBoughtWon
    val ratio = if (goal == null) 0f else (amount.toDouble() / goal.toDouble()).toFloat()
    val gaugeColor = when { ratio >= 1f -> AppColors.Red; ratio >= .9f -> if (LocalAppPalette.current.dark) androidx.compose.ui.graphics.Color(0xFFFFB36B) else androidx.compose.ui.graphics.Color(0xFFAD5100); ratio >= .7f -> if (LocalAppPalette.current.dark) androidx.compose.ui.graphics.Color(0xFFFFD563) else androidx.compose.ui.graphics.Color(0xFF896400); else -> AppColors.Green }
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(AppColors.Surface).padding(18.dp)) {
        Text("모아의 저장방", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = AppColors.Text)
        Text("$periodLabel · 이 기간에 사기로 결정한 금액", Modifier.padding(top = 7.dp), style = MaterialTheme.typography.bodySmall, color = AppColors.Secondary)
        Text(yardAmount(amount, summary.budgetUnknownCount, summary.budgetItems.size), Modifier.padding(top = 12.dp), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = AppColors.Text)
        if (goal != null) {
            LinearProgressIndicator(progress = { ratio.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).testTag("yard_goal_gauge"), color = gaugeColor, trackColor = AppColors.Soft)
            Text("목표 ${money(goal)} · ${String.format(Locale.KOREA, "%.0f", amount.toDouble() / goal * 100)}%", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = AppColors.Secondary)
            Text(if (amount > goal) "목표를 ${money(amount - goal)} 넘었어요" else "목표까지 ${money(goal - amount)} 남았어요", Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyMedium, color = gaugeColor)
        } else Text("소비 목표를 정하면 얼마나 남았는지 보여드려요", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = AppColors.Secondary)
        if (summary.budgetUnknownCount > 0) Text("금액 미정 ${summary.budgetUnknownCount}개는 금액과 게이지에서 뺐어요", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = AppColors.Secondary)
        Text("실제 결제 내역과는 달라요", Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
        PrimaryAction("저장방 보기", onRoom, Modifier.fillMaxWidth().padding(top = 14.dp).testTag("yard_storage_open"), weak = true)
        TextButton(onClick = onGoalEdit, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("yard_goal_edit")) { Text(if (goal == null) "소비 목표 정하기" else "목표 다시 정하기") }
    }
}

@Composable
private fun YardProductRow(item: WishItem, now: Long, onItem: (WishItem) -> Unit, onDecide: (WishItem) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        ProductRow(item, now) { onItem(item) }
        if (item.ready(now)) TextButton(onClick = { onDecide(item) }, modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth().heightIn(min = 48.dp).testTag("yard_decide_${item.id}")) { Text("이 새싹 결정하기") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun YardDaySheet(items: List<WishItem>, day: Long, now: Long, onItem: (WishItem) -> Unit, onDecide: (WishItem) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = AppColors.Background) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = (LocalConfiguration.current.screenHeightDp * .8f).dp).testTag("yard_day_sheet"), contentPadding = PaddingValues(bottom = 28.dp)) {
            item { Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text("${dateLabel(day)}에 심었어요", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = AppColors.Text)
                Text("상품 ${items.size}개", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium, color = AppColors.Secondary)
            } }
            if (items.isEmpty()) item { EmptyState("비어 있는 풀밭이에요", "이날은 상품을 담지 않았어요", MascotPose.TORI_IDLE, compact = true) }
            items(items, key = { it.id }) { item -> YardProductRow(item, now, onItem, onDecide) }
            item { TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("yard_day_close")) { Text("닫기") } }
        }
    }
}

/** Also used by the vault's budget row. Callers provide the chosen decidedAt period. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StorageRoomSheet(items: List<WishItem>, periodLabel: String, onItem: (WishItem) -> Unit, onDismiss: () -> Unit) {
    val actual = items.filter { !it.isSample && it.decision == Decision.BUY }.sortedByDescending { it.decidedAt }
    val total = actual.mapNotNull { it.decisionPriceWon?.takeIf { price -> price >= 0 } }.fold(0L) { acc, price -> if (Long.MAX_VALUE - acc < price) Long.MAX_VALUE else acc + price }
    val unknown = actual.count { it.decisionPriceWon == null || it.decisionPriceWon < 0 }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = AppColors.Background) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = (LocalConfiguration.current.screenHeightDp * .85f).dp).testTag("storage_room_sheet"), contentPadding = PaddingValues(bottom = 28.dp)) {
            item {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Mascot(MascotPose.MOA_HAPPY, size = 56.dp, breathing = false)
                        Text("모아의 저장방", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = AppColors.Text, modifier = Modifier.weight(1f))
                    }
                    Text(periodLabel, Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodyMedium, color = AppColors.Secondary)
                    Text("${actual.size}개 · ${yardAmount(total, unknown, actual.size)}", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = AppColors.Text)
                    Text("이 기간에 사기로 결정한 상품이에요. 실제 주문·결제 내역과는 달라요", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = AppColors.Secondary)
                    if (unknown > 0) Text("금액 미정 ${unknown}개는 합계에서 뺐어요", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = AppColors.Secondary)
                }
            }
            if (actual.isEmpty()) item { EmptyState("저장방에 자리가 넉넉해요", "사기로 결정한 상품이 여기에 모여요", MascotPose.MOA_SLEEP, compact = true) }
            items(actual, key = { it.id }) { item ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 10.dp).clip(RoundedCornerShape(14.dp)).background(AppColors.Surface)) {
                    ProductRow(item, item.decidedAt ?: item.createdAt) { onItem(item) }
                    Text("결정할 때 ${money(item.decisionPriceWon)} · ${item.decidedAt?.let(::dateLabel) ?: "결정 날짜 없음"}", Modifier.padding(horizontal = 20.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall, color = AppColors.Secondary)
                    HorizontalDivider(color = AppColors.Border, thickness = 3.dp)
                }
            }
            item { TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("storage_room_close")) { Text("닫기") } }
        }
    }
}

private fun calendarAt(time: Long) = Calendar.getInstance().apply { timeInMillis = time }
private fun yardAmount(amount: Long, unknownCount: Int, count: Int): String =
    if (count > 0 && unknownCount == count) "금액 미정" else money(amount)

private fun dayStart(time: Long): Long = calendarAt(time).apply {
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

internal fun yardPeriodLabel(at: Long, period: YardPeriod): String {
    if (period == YardPeriod.MONTH) return SimpleDateFormat("yyyy년 M월", Locale.KOREA).format(Date(at))
    val end = calendarAt(at).apply { add(Calendar.DAY_OF_MONTH, 6) }.timeInMillis
    val format = SimpleDateFormat("M.d", Locale.KOREA)
    val year = calendarAt(at).get(Calendar.YEAR)
    return "$year · ${format.format(Date(at))} – ${format.format(Date(end))}"
}
