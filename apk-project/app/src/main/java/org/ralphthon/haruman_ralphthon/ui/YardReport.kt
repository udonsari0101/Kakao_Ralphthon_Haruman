package org.ralphthon.haruman_ralphthon.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.withTimeoutOrNull
import org.ralphthon.haruman_ralphthon.R
import org.ralphthon.haruman_ralphthon.data.*
import org.ralphthon.haruman_ralphthon.ui.mascot.*

/** Completed periods only. Both the preview and exported text use the same real-item summary. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun YardReportSheet(
    items: List<WishItem>,
    period: YardPeriod,
    at: Long,
    goalWon: Long?,
    now: Long,
    onItem: (WishItem) -> Unit,
    onDismiss: () -> Unit,
    celebrate: Boolean = false
) {
    val context = LocalContext.current
    val last = periodOf(periodOf(now, period) - 1L, period)
    val realItems = remember(items) { items.filterNot { it.isSample } }
    // A purchase may belong to a later period than its original garden plot.
    val first = minOf(realItems.minOfOrNull { it.createdAt } ?: last, last)
    val periods = remember(first, last, period) { periodsBetween(first, last, period) }
    val initial = periodOf(at, period).coerceIn(periods.first(), periods.last())
    var selected by rememberSaveable(period, at) { mutableLongStateOf(initial) }
    val index = periods.indexOf(selected).takeIf { it >= 0 } ?: periods.lastIndex
    val displayed = periods[index]
    val summary = remember(realItems, displayed, period, now) { yardSummary(realItems, displayed, period, now) }
    val label = yardPeriodLabel(displayed, period)
    val goal = goalWon?.takeIf { it > 0L }
    val goalText = reportGoalText(summary, goal)
    var shareError by rememberSaveable { mutableStateOf<String?>(null) }
    val maximumHeight = (LocalConfiguration.current.screenHeightDp * 0.9f).dp
    val showExamplesNote = items.any { it.isSample }
    val reduced = mascotMotionReduced()
    // Root persists the automatic-report event. This local marker prevents a second animation
    // while browsing periods, recomposing or restoring this already displayed sheet.
    var celebrationPlayed by rememberSaveable(period, at) { mutableStateOf(false) }
    val celebration = remember(period, at) { Animatable(1f) }
    val showCelebration = celebrate && displayed == initial && goal != null &&
        summary.budgetUnknownCount == 0 && summary.budgetBoughtWon <= goal &&
        (summary.realItems.isNotEmpty() || summary.budgetItems.isNotEmpty())
    LaunchedEffect(showCelebration, reduced) {
        if (!showCelebration || celebrationPlayed || reduced) {
            if (showCelebration) celebrationPlayed = true
            celebration.snapTo(1f)
        } else {
            celebrationPlayed = true
            celebration.snapTo(0f)
            // Also cap wall time when Android's animation duration scale is greater than one.
            withTimeoutOrNull(1_000L) { celebration.animateTo(1f, tween(1_000)) }
            celebration.snapTo(1f)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AppColors.Background
    ) {
        LazyColumn(
            Modifier.fillMaxWidth().heightIn(max = maximumHeight).testTag("yard_report"),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (showCelebration) item("goal_celebration") {
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MascotStageColor)
                    .testTag("yard_report_celebration")) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            Mascot(MascotPose.MOA_HAPPY, size = 84.dp, breathing = false)
                            Mascot(MascotPose.TORI_CHEER, size = 84.dp, breathing = false)
                        }
                        Text(if (period == YardPeriod.WEEK) "지난주도 차근차근 고민했어요!" else "지난달도 차근차근 고민했어요!",
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = AppColors.Text)
                        Text("설정한 목표 안에서 마음을 정했어요. 모아와 토리가 함께 축하해요",
                            style = MaterialTheme.typography.bodyMedium, color = AppColors.Secondary)
                    }
                    if (!reduced && celebration.value < 1f) CelebrationConfetti(celebration.value, Modifier.matchParentSize())
                }
            }
            item("heading") {
                Column {
                    Text("마당 일기", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = AppColors.Text)
                    Text(label, Modifier.padding(top = 8.dp).testTag("yard_report_period"), style = MaterialTheme.typography.titleLarge, color = AppColors.Text)
                    Text("지나간 마음을 함께 돌아봐요", Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyMedium, color = AppColors.Secondary)
                    FlowRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedButton(onClick = { selected = periods[index - 1]; shareError = null }, enabled = index > 0,
                            modifier = Modifier.heightIn(min = 48.dp).testTag("yard_report_previous")) { Text("‹ 이전") }
                        OutlinedButton(onClick = { selected = periods[index + 1]; shareError = null }, enabled = index < periods.lastIndex,
                            modifier = Modifier.heightIn(min = 48.dp).testTag("yard_report_next")) { Text("다음 ›") }
                        TextButton(onClick = { selected = periods.last(); shareError = null }, enabled = index != periods.lastIndex,
                            modifier = Modifier.heightIn(min = 48.dp).testTag("yard_report_latest")) { Text("최근 일기로") }
                    }
                }
            }
            item("garden") {
                ReportCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Mascot(MascotPose.TORI_IDLE, size = 60.dp, breathing = false)
                        Text("이 기간에 담은 상품", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = AppColors.Text)
                    }
                    if (summary.realItems.isEmpty()) {
                        Text("이때는 새로 담은 상품이 없었어요", style = MaterialTheme.typography.bodyMedium, color = AppColors.Secondary)
                    } else {
                        ReportGarden(summary, now)
                        Text(reportTreeLine(summary), style = MaterialTheme.typography.titleMedium, color = AppColors.Green, fontWeight = FontWeight.Bold)
                        Text(reportPitLine(summary), style = MaterialTheme.typography.bodyMedium, color = AppColors.Text)
                        Text("아직 고민 중 ${summary.realSproutCount + summary.realMoundCount}개", style = MaterialTheme.typography.bodyMedium, color = AppColors.Secondary)
                        if (summary.unknownSavedCount > 0 || summary.unknownBoughtCount > 0) Text(
                            "금액 미정: 안 사기로 한 상품 ${summary.unknownSavedCount}개 · 구매 결정 상품 ${summary.unknownBoughtCount}개\n금액을 아는 상품만 더했어요",
                            style = MaterialTheme.typography.bodySmall, color = AppColors.Secondary)
                    }
                    Text("추가한 날을 기준으로 모았어요. 이후의 결정도 지금 모습에 반영돼요", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
                }
            }
            item("budget") {
                ReportCard {
                    Text("이 기간의 구매 결정", style = MaterialTheme.typography.titleMedium, color = AppColors.Text, fontWeight = FontWeight.Bold)
                    Text(reportAmount(summary.budgetBoughtWon, summary.budgetUnknownCount, summary.budgetItems.size), style = MaterialTheme.typography.headlineSmall, color = AppColors.Text, fontWeight = FontWeight.Bold)
                    Text("사기로 결정한 날이 이 기간인 상품 ${summary.budgetItems.size}개를 모았어요", style = MaterialTheme.typography.bodyMedium, color = AppColors.Secondary)
                    if (summary.budgetUnknownCount > 0) Text("금액 미정 ${summary.budgetUnknownCount}개는 합계에 넣지 않았어요", style = MaterialTheme.typography.bodySmall, color = AppColors.Secondary)
                    Text(goalText, style = MaterialTheme.typography.bodyLarge, color = AppColors.Text, fontWeight = FontWeight.SemiBold)
                    if (goal != null) Text("현재 설정한 목표 ${money(goal)}로 비교했어요", style = MaterialTheme.typography.bodySmall, color = AppColors.Secondary)
                    Text("하루만에서 정한 마음의 기록이에요. 실제 주문·결제 내역과는 달라요", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
                }
            }
            summary.largestTree?.let { largest ->
                item("largest_tree") {
                    ReportCard {
                        Text("가장 큰 나무", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = AppColors.Text)
                        Text(largest.name, style = MaterialTheme.typography.bodyLarge, color = AppColors.Text)
                        Text("결정할 때 ${money(largest.decisionPriceWon)}", style = MaterialTheme.typography.bodyMedium, color = AppColors.Green)
                        TextButton(onClick = { onDismiss(); onItem(largest) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("yard_report_largest")) { Text("상품 살펴보기") }
                    }
                }
            }
            item("actions") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (showExamplesNote) Text("예시 상품은 그림과 개수·금액에서 뺐어요", style = MaterialTheme.typography.bodySmall, color = AppColors.Secondary)
                    shareError?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = AppColors.Red) }
                    PrimaryAction("공유하기", {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, reportShareText(label, summary, goal, goalText))
                            putExtra(Intent.EXTRA_SUBJECT, "하루만 · $label 마당 일기")
                        }
                        try {
                            context.startActivity(Intent.createChooser(send, "마당 일기 공유하기"))
                            shareError = null
                        } catch (_: ActivityNotFoundException) {
                            shareError = "공유할 수 있는 앱이 없어요"
                        } catch (_: SecurityException) {
                            shareError = "지금은 공유 창을 열 수 없어요. 잠시 뒤 다시 해주세요"
                        }
                    }, Modifier.fillMaxWidth().testTag("yard_report_share"))
                    TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("yard_report_close")) { Text("닫기") }
                }
            }
        }
    }
}

@Composable
private fun ReportCard(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(AppColors.Surface).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReportGarden(summary: YardSummary, now: Long) {
    val visibleItems = summary.realItems.filterNot { it.decision == Decision.BUY && !it.hasVisiblePit() }
    FlowRow(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MascotStageColor).padding(8.dp)
        .clearAndSetSemantics { contentDescription = "마당 모습, 나무 ${summary.realTreeCount}그루, 구덩이 ${summary.realPitCount}개, 고민 중 ${summary.realSproutCount + summary.realMoundCount}개" },
        horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        visibleItems.take(21).forEach { item ->
            val art = Modifier.size(38.dp)
            when (yardItemState(item, now)) {
                YardItemState.PIT -> DugPit(art)
                YardItemState.MOUND -> Image(painterResource(R.drawable.prop_acorn_buried), null, art)
                YardItemState.SPROUT -> Image(painterResource(R.drawable.prop_acorn_sprout), null, art)
                YardItemState.TREE -> when {
                    item.decisionPriceWon == null -> UnpricedTree(art)
                    item.decisionPriceWon >= 200_000L -> FruitingTree(art)
                    else -> Image(painterResource(if (item.decisionPriceWon >= 50_000L) R.drawable.prop_tree_big else R.drawable.prop_tree_small), null, art)
                }
            }
        }
        if (visibleItems.size > 21) Text("+${visibleItems.size - 21}", Modifier.padding(8.dp), style = MaterialTheme.typography.labelLarge, color = AppColors.Text)
    }
}

private fun reportGoalText(summary: YardSummary, goalWon: Long?): String = when {
    goalWon == null -> "이 기간은 금액 목표 없이 돌아봐요"
    summary.budgetBoughtWon > goalWon -> "목표보다 ${money(summary.budgetBoughtWon - goalWon)} 많아요. 다음 마음도 천천히 살펴봐요"
    summary.budgetUnknownCount > 0 -> "금액 미정 상품이 있어 목표 달성 여부는 아직 알 수 없어요"
    summary.budgetItems.isEmpty() -> "이 기간에는 구매 결정을 하지 않았어요"
    else -> "목표 안에서 충분히 고민했어요!"
}

private fun reportAmount(amount: Long, unknown: Int, count: Int): String =
    if (unknown > 0 && unknown == count) "금액 미정" else money(amount)

private fun reportTreeLine(summary: YardSummary): String =
    "나무 ${summary.realTreeCount}그루 · " + if (summary.unknownSavedCount > 0 && summary.unknownSavedCount == summary.realTreeCount)
        "금액 미정" else "${money(summary.savedWon)} 아꼈어요"

private fun reportPitLine(summary: YardSummary): String =
    "구덩이 ${summary.realPitCount}개 · ${reportAmount(summary.boughtWon, summary.unknownBoughtCount, summary.realBuyCount)} 구매 결정"

private fun reportShareText(label: String, summary: YardSummary, goalWon: Long?, goalText: String): String = buildString {
    appendLine("하루만 · $label 마당 일기")
    appendLine()
    appendLine("[이 기간에 담은 상품 · 추가한 날 기준]")
    appendLine(reportTreeLine(summary))
    appendLine(reportPitLine(summary))
    appendLine("아직 고민 중 ${summary.realSproutCount + summary.realMoundCount}개")
    if (summary.unknownSavedCount > 0 || summary.unknownBoughtCount > 0) appendLine("금액 미정: 안 사기로 한 상품 ${summary.unknownSavedCount}개 · 구매 결정 상품 ${summary.unknownBoughtCount}개")
    appendLine("이후의 결정도 현재 모습에 반영했어요")
    appendLine()
    appendLine("[이 기간의 구매 결정 · 결정한 날 기준]")
    appendLine("${summary.budgetItems.size}개 · ${reportAmount(summary.budgetBoughtWon, summary.budgetUnknownCount, summary.budgetItems.size)}")
    if (summary.budgetUnknownCount > 0) appendLine("금액 미정 ${summary.budgetUnknownCount}개는 합계에서 뺐어요")
    if (goalWon != null) appendLine("현재 설정한 목표 ${money(goalWon)}로 비교했어요")
    appendLine(goalText)
    summary.largestTree?.let { appendLine(); appendLine("가장 큰 나무: ${it.name} · ${money(it.decisionPriceWon)}") }
    appendLine()
    append("예시 상품은 제외했어요. 실제 주문·결제 내역과는 달라요")
}
