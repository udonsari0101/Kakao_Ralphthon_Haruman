package org.ralphthon.haruman_ralphthon.ui

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.ralphthon.haruman_ralphthon.R
import org.ralphthon.haruman_ralphthon.data.*
import org.ralphthon.haruman_ralphthon.ui.mascot.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

fun money(value: Long?) = value?.let { String.format(Locale.KOREA, "%,d원", it) } ?: "가격 미정"
fun dateLabel(value: Long): String = SimpleDateFormat("M월 d일", Locale.KOREA).format(Date(value))
fun timeLabel(value: Long): String = SimpleDateFormat("M월 d일 a h:mm", Locale.KOREA).format(Date(value))
fun sameMonth(value: Long, now: Long): Boolean {
    val a = Calendar.getInstance().apply { timeInMillis = value }
    val b = Calendar.getInstance().apply { timeInMillis = now }
    return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.MONTH) == b.get(Calendar.MONTH)
}
fun remaining(item: WishItem, now: Long): String {
    val minutes = (item.remainingMillis(now) + 59_999) / 60_000
    return when {
        minutes <= 0 -> "지금"
        minutes >= 1440 -> "${minutes / 1440}일 ${minutes % 1440 / 60}시간"
        minutes >= 60 -> "${minutes / 60}시간 ${minutes % 60}분"
        else -> "${minutes}분"
    }
}
fun cooldownLabel(hours: Int) = when (hours) {
    0 -> "바로 결정하기"
    3 -> "3시간 뒤"
    24 -> "내일"
    72 -> "3일 뒤"
    168 -> "일주일 뒤"
    else -> "${hours}시간 뒤"
}

/** Keep reading and touch targets comfortable on both phones and wider windows. */
@Composable
internal fun ContentFrame(content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = 720.dp).fillMaxSize(), content = content)
    }
}

@Composable
fun BottomTabs(route: String, onChange: (String) -> Unit) {
    Surface(color = AppColors.Surface) {
        Column {
            HorizontalDivider(color = AppColors.Border, thickness = .5.dp)
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp)) {
                listOf(Triple("vault", "box", "보관함"), Triple("history", "history", "마당"), Triple("my", "person", "MY")).forEach { (id, icon, label) ->
                    val selected = route == id
                    val color = if (selected) AppColors.Blue else AppColors.Muted
                    Column(
                        Modifier.weight(1f).heightIn(min = 72.dp).testTag("tab_$id")
                            .selectable(selected, role = Role.Tab) { onChange(id) }.padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(Modifier.width(54.dp).height(28.dp).clip(CircleShape)
                            .background(if (selected) AppColors.BlueTint else Color.Transparent), contentAlignment = Alignment.Center) {
                            AppIcon(icon, Modifier.size(22.dp), color)
                        }
                        Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun Onboarding(onStart: () -> Unit) {
    ContentFrame {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val compact = maxHeight < 600.dp || LocalDensity.current.fontScale > 1.3f
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(top = 24.dp, bottom = 24.dp)) {
                BrandLogo(Modifier.width(132.dp).height(44.dp))
                Box(Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 24.dp), contentAlignment = Alignment.Center) {
                    OnboardingScene(Modifier.fillMaxWidth(.85f).widthIn(max = 300.dp).height(if (compact) 140.dp else 200.dp))
                }
                Text("사고 싶은 마음,\n잠시 넣어둘까요?", fontSize = 30.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.7).sp, color = AppColors.Text)
                Text("상품 링크를 담거나 직접 적어두세요.\n조금 뒤, 내 마음을 다시 살펴봐요.", Modifier.padding(top = 16.dp), color = AppColors.Secondary, fontSize = 15.sp, lineHeight = 25.sp)
                Column(Modifier.padding(vertical = 24.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MascotStageColor).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    MascotRole(MascotPose.MOA_IDLE, "모아가 마음을 모아둬요", "갖고 싶은 상품을 볼에 쏙 담아둘게요")
                    MascotRole(MascotPose.TORI_PRESENT, "토리와 다시 꺼내봐요", "때가 되면 함께 마음을 확인해요")
                }
                PrimaryAction("시작하기", onStart, Modifier.fillMaxWidth().testTag("onboarding_start"))
            }
        }
    }
}

@Composable
private fun MascotRole(pose: MascotPose, title: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Mascot(pose, size = 40.dp, breathing = false)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = AppColors.Text)
            Text(text, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = AppColors.Secondary)
        }
    }
}

@Composable
fun VaultScreen(items: List<WishItem>, now: Long, onAdd: () -> Unit, onItem: (WishItem) -> Unit) =
    VaultScreen(items, now, onAdd, onItem, onReview = null)

@Composable
fun VaultScreen(items: List<WishItem>, now: Long, onAdd: () -> Unit, onItem: (WishItem) -> Unit, onReview: (() -> Unit)?,
    yardPeriod: YardPeriod = YardPeriod.MONTH, goalWon: Long? = null, onStorage: () -> Unit = {}) {
    var filter by rememberSaveable { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val reduced = rememberReducedMotion() || LocalReduceMotion.current
    val active = items.filter { it.decision == Decision.PENDING }
    val ready = active.filter { it.ready(now) }
    val cooling = active.filterNot { it.ready(now) }
    val actualCount = active.count { !it.isSample }
    val monthly = skippedTotal(items.filter { it.decidedAt?.let { time -> sameMonth(time, now) } == true })
    ContentFrame {
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 24.dp)) {
            item("vault_header") {
                Column(Modifier.padding(20.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("보관함", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = AppColors.Text)
                            Text(if (actualCount > 0) "${actualCount}개의 마음을 담아뒀어요" else "사고 싶은 마음을 담아보세요", Modifier.padding(top = 8.dp), fontSize = 14.sp, color = AppColors.Secondary)
                        }
                        IconButton(onClick = onAdd, modifier = Modifier.size(48.dp).clip(CircleShape).background(AppColors.Primary).testTag("add_item").semantics { contentDescription = "상품 추가" }) {
                            AppIcon("plus", Modifier.size(24.dp), Color.White)
                        }
                    }
                    val spent = yardSummary(items, now, yardPeriod, now).budgetBoughtWon
                    VaultMascotStage(actualCount, active.count { it.isSample }, ready.size, monthly, onReview,
                        overGoal = goalWon != null && saturatedAdd(spent, pendingTotal(items)) > goalWon,
                        onCheek = { scope.launch { if (reduced) listState.scrollToItem(1) else listState.animateScrollToItem(1) } })
                    BudgetMiniCard(items, now, yardPeriod, goalWon, onStorage)
                    Row(Modifier.padding(top = 22.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(AppColors.Soft).padding(4.dp)) {
                        listOf("전체", "고민 중", "결정 가능").forEachIndexed { i, text ->
                            Box(Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(10.dp))
                                .background(if (filter == i) AppColors.Surface else Color.Transparent).testTag("filter_$i")
                                .selectable(filter == i, role = Role.Tab) { filter = i }.padding(horizontal = 5.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                                Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (filter == i) AppColors.Text else AppColors.Muted, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }
            if (filter != 1 && ready.isNotEmpty()) {
                item("ready_header") {
                    SectionHeading("다시 결정할 시간이에요", "잠시 미뤄둔 마음을 다시 살펴봐요")
                    if (ready.size >= 2 && onReview != null) TextButton(onClick = onReview, modifier = Modifier.padding(horizontal = 12.dp).heightIn(min = 48.dp)) { Text("한 번에 정리 ›", color = AppColors.Blue) }
                }
                items(ready, key = { "ready_${it.id}" }) { item -> ProductRow(item, now) { onItem(item) } }
                item("ready_gap") { Spacer(Modifier.height(24.dp)) }
            }
            if (filter != 2 && cooling.isNotEmpty()) {
                item("cooling_header") { SectionHeading("조금 더 고민해요", "정해둔 시간 뒤에 다시 생각해 봐요") }
                items(cooling, key = { "cooling_${it.id}" }) { item -> ProductRow(item, now) { onItem(item) } }
            }
            if ((filter == 1 && cooling.isEmpty()) || (filter == 2 && ready.isEmpty()) || active.isEmpty()) {
                item("vault_empty") {
                    EmptyState(if (filter == 2) "아직은 고민할 시간이에요" else "어떤 마음을 담아볼까요?", if (filter == 2) "시간이 지나면 여기서 다시 결정할 수 있어요." else "상품 링크를 가져오거나\n상품 이름을 직접 적어보세요.", if (filter == 2) MascotPose.TORI_BURY else MascotPose.MOA_SLEEP, compact = true)
                    if (active.isEmpty()) PrimaryAction("첫 상품 담기", onAdd, Modifier.fillMaxWidth().padding(horizontal = 20.dp))
                }
            }
            if (items.any { it.isSample }) item("sample_notice") {
                Text("예시 상품은 내 기록 합계에 포함하지 않아요", Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp), fontSize = 12.sp, color = AppColors.Muted, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun VaultMascotStage(total: Int, samples: Int, ready: Int, monthly: Long, onReview: (() -> Unit)?, overGoal: Boolean, onCheek: () -> Unit) {
    var tappedCopy by rememberSaveable { mutableStateOf<String?>(null) }
    var lastTap by remember { mutableLongStateOf(0L) }
    var tapCount by rememberSaveable { mutableIntStateOf(0) }
    val copy = tappedCopy ?: when {
        overGoal -> "모두 사면 목표를 넘어요. 함께 살펴볼까요?"
        total == 0 -> "쿨쿨… 갖고 싶은 게 생기면 깨워 주세요"
        total >= 10 -> "볼이 가득 찼어요. 함께 정리해 봐요!"
        total >= 6 -> "볼이 빵빵해요. 토리와 살펴볼까요?"
        total >= 3 -> "볼이 든든해요. ${total}개 모아뒀어요"
        else -> "지금 ${total}개 모아뒀어요!"
    }
    LaunchedEffect(total) { tappedCopy = null }
    Column(Modifier.padding(top = 22.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MascotStageColor).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CheekMoa(total, modifier = Modifier.testTag("vault_cheeks"), size = 88.dp, overGoal = overGoal, onClick = {
                val time = SystemClock.elapsedRealtime()
                if (time - lastTap >= 300) {
                    lastTap = time
                    tappedCopy = listOf("헤헤, 간지러워요!", "오늘은 어떤 마음을 찾았나요?", "마음을 잘 담아둘게요")[tapCount++ % 3]
                    onCheek()
                }
            })
            Column(Modifier.weight(1f)) {
                if (LocalShowBubbles.current) SpeechBubble(copy, speaker = "모아")
                else Text(if (total == 0) "모아가 기다리고 있어요" else "${total}개의 마음을 보관해요", style = MaterialTheme.typography.titleSmall, color = AppColors.Text)
                if (samples > 0) Text("예시 ${samples}개가 함께 있어요", Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall, color = AppColors.Secondary)
            }
        }
        Text("이번 달 쓰지 않기로 한 금액", Modifier.padding(top = 14.dp), fontSize = 12.sp, color = AppColors.Secondary)
        Text(money(monthly), Modifier.padding(top = 6.dp), fontSize = 25.sp, fontWeight = FontWeight.Bold, color = AppColors.Text)
        if (ready > 0 && onReview != null) {
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Mascot(MascotPose.TORI_PRESENT, size = 56.dp)
                Text("마당에 새싹 ${ready}개가 돋았어요.\n함께 다시 볼까요?", Modifier.weight(1f), fontSize = 13.sp, color = AppColors.Secondary)
            }
            PrimaryAction("정리 시간 시작 · ${ready}개", onReview, Modifier.fillMaxWidth().padding(top = 8.dp).testTag("start_review"))
        }
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String) {
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp)) {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AppColors.Text)
        Text(subtitle, Modifier.padding(top = 5.dp), fontSize = 12.sp, color = AppColors.Muted)
    }
}

@Composable
fun ItemSection(title: String, subtitle: String, items: List<WishItem>, now: Long, onItem: (WishItem) -> Unit) {
    Column(Modifier.padding(bottom = 24.dp)) {
        SectionHeading(title, subtitle)
        items.forEach { item -> ProductRow(item, now) { onItem(item) } }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProductRow(item: WishItem, now: Long, onClick: () -> Unit) {
    val displayPrice = if (item.decision == Decision.PENDING) item.priceWon else item.decisionPriceWon
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clip(RoundedCornerShape(16.dp))
        .background(AppColors.Surface).testTag("item_${item.id}").clickable(role = Role.Button, onClick = onClick)
        .padding(12.dp), verticalAlignment = Alignment.Top) {
        ProductImage(item, Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(item.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, color = AppColors.Text)
            Text(money(displayPrice), Modifier.padding(top = 5.dp), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = AppColors.Secondary)
            FlowRow(Modifier.padding(top = 7.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill(item.platform.label, AppColors.Soft, AppColors.Secondary)
                when (item.decision) {
                    Decision.BUY -> Pill("구매 결정", AppColors.BlueTint, AppColors.Blue)
                    Decision.SKIP -> Pill("안 샀어요", AppColors.GreenTint, AppColors.Green)
                    Decision.PENDING -> Pill(if (item.ready(now)) "결정할 수 있어요" else "${remaining(item, now)} 남았어요", AppColors.BlueTint, AppColors.Blue)
                }
                if (item.isSample) Pill("예시", AppColors.Soft, AppColors.Muted)
            }
        }
        val stage = if (item.decision == Decision.PENDING) { if (item.ready(now)) AcornStage.SPROUT else AcornStage.BURIED } else null
        if (stage != null) AcornIcon(stage, Modifier.padding(start = 6.dp, top = 4.dp).size(28.dp))
        else AppIcon("chevron", Modifier.padding(start = 4.dp, top = 4.dp).size(14.dp), AppColors.Muted)
    }
}

@Composable
fun Pill(text: String, background: Color, color: Color) {
    Text(text, Modifier.clip(RoundedCornerShape(6.dp)).background(background).padding(horizontal = 8.dp, vertical = 5.dp), color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
fun EmptyState(title: String, subtitle: String, pose: MascotPose? = null, compact: Boolean = false) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = if (compact) 20.dp else 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Mascot(pose ?: MascotPose.MOA_SLEEP, size = if (compact) 72.dp else 88.dp)
        Text(title, Modifier.padding(top = if (compact) 12.dp else 18.dp), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Text, textAlign = TextAlign.Center)
        Text(subtitle, Modifier.padding(top = 10.dp), fontSize = 14.sp, color = AppColors.Secondary, textAlign = TextAlign.Center, lineHeight = 23.sp)
    }
}

@Composable
fun HistoryScreen(items: List<WishItem>, now: Long, onItem: (WishItem) -> Unit) {
    val actual = items.filterNot { it.isSample }
    val decided = actual.filter { it.decision != Decision.PENDING }.sortedByDescending { it.decidedAt }
    val samples = items.filter { it.isSample && it.decision != Decision.PENDING }.sortedByDescending { it.decidedAt }
    val monthly = decided.filter { sameMonth(it.decidedAt ?: it.createdAt, now) }
    val unknownPrices = monthly.count { it.decision == Decision.SKIP && it.decisionPriceWon == null }
    ContentFrame {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item("history_header") {
                Column(Modifier.padding(20.dp)) {
                    Text("소비 기록", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = AppColors.Text)
                    Text("내가 한 선택을 돌아봐요", Modifier.padding(top = 8.dp), fontSize = 14.sp, color = AppColors.Secondary)
                    Column(Modifier.padding(top = 24.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(AppColors.Surface).padding(20.dp)) {
                        Text("이번 달 쓰지 않기로 한 금액", fontSize = 12.sp, color = AppColors.Secondary)
                        Text(money(skippedTotal(monthly)), Modifier.padding(top = 12.dp), fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.8).sp, color = AppColors.Text)
                        Text("결정할 때의 가격으로 기록했어요", Modifier.padding(top = 6.dp), fontSize = 12.sp, color = AppColors.Muted)
                        HorizontalDivider(Modifier.padding(vertical = 18.dp), color = AppColors.Border)
                        Text("${actual.count { sameMonth(it.createdAt, now) }}개 담았어요", fontSize = 14.sp, color = AppColors.Secondary)
                        Text("${monthly.count { it.decision == Decision.SKIP }}개는 안 샀어요 · ${monthly.count { it.decision == Decision.BUY }}개는 구매 결정", Modifier.padding(top = 8.dp), fontSize = 13.sp, color = AppColors.Secondary)
                        if (unknownPrices > 0) Text("가격이 없는 ${unknownPrices}개 상품은 금액 합계에서 뺐어요", Modifier.padding(top = 12.dp), fontSize = 12.sp, color = AppColors.Muted)
                        HorizontalDivider(Modifier.padding(vertical = 18.dp), color = AppColors.Border)
                        SavingsForest(monthly.filter { it.decision == Decision.SKIP }, now, onItem)
                    }
                }
            }
            if (decided.isEmpty()) item("history_empty") { EmptyState("기록이 차곡차곡 쌓일 거예요", "보관한 상품을 다시 생각하고\n내 마음에 맞게 결정해 보세요.", MascotPose.TORI_IDLE) }
            else {
                item("history_recent") { SectionHeading("최근 결정", "나의 선택을 한눈에 살펴봐요") }
                items(decided, key = { "history_${it.id}" }) { item -> ProductRow(item, now) { onItem(item) } }
            }
            if (samples.isNotEmpty()) {
                item("sample_history_header") { Column(Modifier.padding(top = 24.dp)) { SectionHeading("예시 기록", "내 결정과 금액 합계에는 포함하지 않아요") } }
                items(samples, key = { "sample_${it.id}" }) { item -> ProductRow(item, now) { onItem(item) } }
            }
        }
    }
}

@Composable
private fun SavingsForest(items: List<WishItem>, now: Long, onItem: (WishItem) -> Unit) {
    val trees = items.filterNot { it.isSample }.sortedBy { it.decidedAt ?: it.createdAt }
    val month = SimpleDateFormat("yyyy-MM", Locale.KOREA).format(Date(now))
    val treeIds = trees.map { "${it.id}:${it.decidedAt}" }
    var seenIds by rememberSaveable(month) { mutableStateOf(ArrayList(treeIds)) }
    var growingIds by remember(month) { mutableStateOf(emptySet<String>()) }
    var tappedCopy by rememberSaveable(month) { mutableStateOf(false) }
    var lastTap by remember { mutableLongStateOf(0L) }
    LaunchedEffect(treeIds) {
        growingIds = treeIds.filterNot { it in seenIds }.toSet()
        seenIds = ArrayList(treeIds)
    }
    Column(Modifier.fillMaxWidth().testTag("savings_forest")) {
        Text("이번 달 절약 숲", style = MaterialTheme.typography.titleSmall, color = AppColors.Text)
        Text("사지 않기로 한 선택마다 나무 한 그루를 남겨요", Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall, color = AppColors.Secondary)
        if (trees.isEmpty()) {
            Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Image(painterResource(R.drawable.prop_acorn_buried), null, Modifier.size(48.dp))
                Text("아직은 조용한 땅이에요.\n천천히 내 마음을 살펴봐요", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = AppColors.Secondary)
            }
        } else BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 14.dp)) {
            val columns = (maxWidth.value / 48f).toInt().coerceIn(1, 6)
            val visible = trees.take(columns * 2)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                visible.chunked(columns).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { item ->
                            val event = "${item.id}:${item.decidedAt}"
                            ForestTree(item, event in growingIds, Modifier.weight(1f)) { onItem(item) }
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                if (trees.size > visible.size) Text("+${trees.size - visible.size}그루 더 있어요", Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall, color = AppColors.Secondary)
            }
        }
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Mascot(MascotPose.TORI_IDLE, size = 48.dp, onClick = {
                val time = SystemClock.elapsedRealtime()
                if (time - lastTap >= 300) { lastTap = time; tappedCopy = !tappedCopy }
            })
            if (LocalShowBubbles.current) SpeechBubble(
                if (tappedCopy) "우리의 선택을 기억하고 있어요" else if (trees.isEmpty()) "첫 나무가 자라면 같이 볼까요?" else "이번 달 숲에 나무 ${trees.size}그루가 자랐어요!",
                Modifier.weight(1f), speaker = "토리",
            ) else Text("이번 달 나무 ${trees.size}그루", style = MaterialTheme.typography.bodySmall, color = AppColors.Secondary)
        }
    }
}

@Composable
private fun ForestTree(item: WishItem, grow: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val reduced = LocalReduceMotion.current || rememberReducedMotion()
    val scale = remember(item.id, item.decidedAt) { Animatable(1f) }
    LaunchedEffect(grow, reduced) {
        if (grow && !reduced) { scale.snapTo(0f); scale.animateTo(1f, tween(360)) }
        else scale.snapTo(1f)
    }
    val large = (item.decisionPriceWon ?: 0) >= 50_000
    Box(modifier.heightIn(min = 56.dp).clip(RoundedCornerShape(12.dp)).testTag("forest_${item.id}")
        .clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = "${item.name}, ${money(item.decisionPriceWon)}, 결정 기록 보기" }, contentAlignment = Alignment.BottomCenter) {
        Image(painterResource(if (large) R.drawable.prop_tree_big else R.drawable.prop_tree_small), null,
            Modifier.padding(bottom = 4.dp).size(if (large) 40.dp else 32.dp).graphicsLayer {
                scaleY = scale.value
                transformOrigin = TransformOrigin(.5f, 1f)
            })
    }
}

@Composable
fun MyScreen(
    hours: Int,
    onDefault: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    themePreference: ThemePreference = ThemePreference.SYSTEM,
    onThemeChange: (ThemePreference) -> Unit = {},
    appVersion: String = "1.0.0",
    showMascotBubbles: Boolean = true,
    onMascotBubblesChange: (Boolean) -> Unit = {},
    reduceMascotMotion: Boolean = false,
    onReduceMascotMotionChange: (Boolean) -> Unit = {},
    yardPeriod: YardPeriod = YardPeriod.MONTH,
    onYardPeriodChange: (YardPeriod) -> Unit = {},
    spendingGoalWon: Long? = null,
    onGoalEdit: () -> Unit = {},
    onReports: () -> Unit = {},
) {
    ContentFrame {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
            Text("MY", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = AppColors.Text)
            Row(Modifier.padding(top = 24.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(AppColors.Surface).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Mascot(MascotPose.MOA_IDLE, size = 56.dp, breathing = false)
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text("나만의 소비 보관함", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Text)
                    Text("내 속도대로, 하루만 더 생각해요", Modifier.padding(top = 6.dp), fontSize = 12.sp, color = AppColors.Secondary)
                }
            }
            SettingsHeading("다시 보는 시간")
            SettingRow("기본 기다리는 시간", cooldownLabel(hours), "clock", onDefault)
            Text("새로 담는 상품에 적용해요", Modifier.padding(horizontal = 8.dp, vertical = 10.dp), fontSize = 12.sp, color = AppColors.Muted)
            SettingsHeading("화면 모드")
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(AppColors.Surface)) {
                listOf(ThemePreference.SYSTEM, ThemePreference.LIGHT, ThemePreference.DARK).forEach { preference ->
                    val title = when (preference) { ThemePreference.SYSTEM -> "시스템 설정 따르기"; ThemePreference.LIGHT -> "라이트"; ThemePreference.DARK -> "다크" }
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("theme_${preference.name.lowercase(Locale.ROOT)}")
                        .selectable(preference == themePreference, role = Role.RadioButton) { onThemeChange(preference) }
                        .padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(title, fontSize = 14.sp, fontWeight = if (preference == themePreference) FontWeight.SemiBold else FontWeight.Normal, color = AppColors.Text)
                            if (preference == ThemePreference.SYSTEM) Text("휴대폰 설정에 맞춰 바뀌어요", Modifier.padding(top = 4.dp), fontSize = 12.sp, color = AppColors.Muted)
                        }
                        RadioButton(selected = preference == themePreference, onClick = null, modifier = Modifier.padding(start = 12.dp), colors = RadioButtonDefaults.colors(selectedColor = AppColors.Blue, unselectedColor = AppColors.Muted))
                    }
                }
            }
            SettingsHeading("모아와 토리")
            MascotSettingRow("캐릭터 말풍선 보기", "모아와 토리의 짧은 이야기를 보여드려요", showMascotBubbles, onMascotBubblesChange, "mascot_bubbles")
            Spacer(Modifier.height(8.dp))
            MascotSettingRow("캐릭터 움직임 줄이기", "호흡과 축하를 정적인 모습으로 보여드려요", reduceMascotMotion, onReduceMascotMotionChange, "mascot_reduce_motion")
            SettingsHeading("내 기록")
            SettingRow("마당 일기", "지난 기간의 선택을 돌아봐요", "history", onReports)
            Spacer(Modifier.height(8.dp))
            SettingRow("내 기록 내보내기", "", "download", onExport)
            Spacer(Modifier.height(8.dp))
            SettingRow("모든 기록 삭제", "", "trash", onDelete, destructive = true)
            SettingsHeading("마당과 소비 목표")
            Text("마당 주기", style = MaterialTheme.typography.titleSmall, color = AppColors.Text)
            YardPeriod.entries.forEach { period ->
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("setting_yard_${period.name.lowercase(Locale.ROOT)}")
                    .selectable(period == yardPeriod, role = Role.RadioButton) { onYardPeriodChange(period) }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(period == yardPeriod, onClick = null)
                    Text(period.label, Modifier.padding(start = 10.dp), color = AppColors.Text)
                }
            }
            SettingRow("소비 목표", spendingGoalWon?.let { "${yardPeriod.label} ${money(it)}" } ?: "목표 없이 사용하고 있어요", "box", onGoalEdit)
            Text("하루만에서 사기로 결정한 금액이에요. 실제 카드 지출과는 달라요.", Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall, color = AppColors.Secondary)
            Text("하루만 $appVersion", Modifier.fillMaxWidth().padding(top = 36.dp), fontSize = 12.sp, color = AppColors.Muted, textAlign = TextAlign.Center)
            Text("기록은 이 기기에 보관돼요", Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 24.dp), fontSize = 12.sp, color = AppColors.Muted, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun MascotSettingRow(label: String, supporting: String, selected: Boolean, onChange: (Boolean) -> Unit, tag: String) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(RoundedCornerShape(14.dp)).background(AppColors.Surface)
        .testTag(tag).toggleable(value = selected, role = Role.Switch, onValueChange = onChange)
        .padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 10.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = AppColors.Text)
            Text(supporting, Modifier.padding(top = 5.dp), style = MaterialTheme.typography.bodySmall, color = AppColors.Secondary)
        }
        Switch(checked = selected, onCheckedChange = null)
    }
}

@Composable
private fun SettingsHeading(text: String) {
    Text(text, Modifier.padding(top = 28.dp, bottom = 10.dp), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Secondary)
}

@Composable
fun SettingRow(label: String, value: String, icon: String, onClick: () -> Unit, destructive: Boolean = false) {
    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).clip(RoundedCornerShape(14.dp)).background(AppColors.Surface).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        AppIcon(icon, Modifier.size(20.dp), if (destructive) AppColors.Red else AppColors.Secondary)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(label, fontSize = 14.sp, color = if (destructive) AppColors.Red else AppColors.Text)
            if (value.isNotBlank()) Text(value, Modifier.padding(top = 5.dp), fontSize = 13.sp, color = AppColors.Secondary)
        }
        AppIcon("chevron", Modifier.size(16.dp), AppColors.Muted)
    }
}
