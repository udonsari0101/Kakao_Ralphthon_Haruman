package org.ralphthon.haruman_ralphthon.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.ralphthon.haruman_ralphthon.data.*
import org.ralphthon.haruman_ralphthon.ui.mascot.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.sin

@Composable
fun DetailScreen(
    item: WishItem,
    now: Long,
    onBack: () -> Unit,
    onOriginal: () -> Unit,
    onDecide: () -> Unit,
    onEdit: (() -> Unit)? = null
) {
    val pending = item.decision == Decision.PENDING
    val hasLink = item.url.isNotBlank()
    ContentFrame {
        Column(Modifier.fillMaxSize().background(AppColors.Surface)) {
            DecisionBackButton(onBack, "상품 정보", onEdit)
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.Top) {
                    ProductImage(item, Modifier.size(80.dp).clip(RoundedCornerShape(16.dp)))
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.name, style = MaterialTheme.typography.titleLarge, color = AppColors.Text)
                        Text(money(item.priceWon), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleMedium, color = AppColors.Text)
                        Text(item.platform.label, Modifier.padding(top = 5.dp), style = MaterialTheme.typography.bodySmall, color = AppColors.Secondary)
                        if (item.isSample) { Spacer(Modifier.height(8.dp)); Pill("예시 상품", AppColors.Soft, AppColors.Muted) }
                    }
                }
                if (pending) {
                    val pose = if (item.ready(now)) MascotPose.TORI_PRESENT else MascotPose.TORI_BURY
                    val message = if (item.ready(now)) "새싹이 돋았어요. 이제 마음을 꺼내볼까요?" else "잘 묻어뒀어요. 조금 뒤에 다시 봐요"
                    MascotMessage(pose, message, Modifier.padding(top = 18.dp), size = 72.dp)
                }
                Column(Modifier.padding(top = 28.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(AppColors.Soft).padding(20.dp)) {
                    if (pending) {
                        Text(if (item.ready(now)) "다시 생각할 시간이 됐어요" else "다시 생각하기까지", style = MaterialTheme.typography.bodyMedium, color = AppColors.Secondary)
                        Text(if (item.ready(now)) "지금 결정할 수 있어요" else remaining(item, now), Modifier.padding(top = 8.dp), fontSize = 27.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, color = AppColors.Text)
                        AcornProgress(if (item.ready(now)) AcornStage.SPROUT else AcornStage.BURIED)
                        LinearProgressIndicator(
                            progress = { item.progress(now) },
                            modifier = Modifier.fillMaxWidth().padding(top = 18.dp).height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = AppColors.Primary, trackColor = AppColors.BlueTint
                        )
                        Text(if (item.ready(now)) "지금도 필요한지 마음을 살펴봐요" else "기다리는 시간의 ${(item.progress(now) * 100).toInt()}%가 지났어요", Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodySmall, color = AppColors.Secondary)
                    } else {
                        Pill(if (item.decision == Decision.SKIP) "안 샀어요" else "구매 결정", if (item.decision == Decision.SKIP) AppColors.GreenTint else AppColors.BlueTint, if (item.decision == Decision.SKIP) AppColors.Green else AppColors.Blue)
                        Text(if (item.decision == Decision.SKIP) "이번에는 사지 않기로 했어요" else "구매하기로 결정했어요", Modifier.padding(top = 14.dp), style = MaterialTheme.typography.titleMedium, color = AppColors.Text)
                        Text("${dateLabel(item.decidedAt ?: item.createdAt)}의 선택을 기록했어요", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = AppColors.Secondary)
                    }
                }
                item.productSnapshot?.let { snapshot ->
                    val option = snapshot.optionLabel?.take(240)?.trim().orEmpty()
                    val priceNote = snapshot.priceNote?.take(600)?.trim().orEmpty()
                    val priceType = when (snapshot.priceType) {
                        "displayed" -> "화면에 표시된 가격"
                        "coupon" -> "쿠폰 적용 가격"
                        "member" -> "회원 가격"
                        "unknown" -> "가격 조건 확인 필요"
                        else -> null
                    }
                    if (option.isNotBlank() || priceNote.isNotBlank() || priceType != null) {
                        Column(Modifier.fillMaxWidth().padding(top = 24.dp)) {
                            Text("가져온 상품 조건", style = MaterialTheme.typography.titleSmall, color = AppColors.Text)
                            if (option.isNotBlank()) DetailInfoRow("상품 옵션", option)
                            if (priceType != null) DetailInfoRow("가격 기준", priceType)
                            if (priceNote.isNotBlank()) Text(priceNote, style = MaterialTheme.typography.bodyMedium, color = AppColors.Secondary)
                            Text("옵션과 결제 금액은 원본에서 한 번 더 확인해 주세요", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
                        }
                    }
                }
                if (item.description.isNotBlank()) DetailTextBlock("상품 설명", item.description)
                if (item.userMemo.isNotBlank()) DetailTextBlock("내 메모", item.userMemo)
                Spacer(Modifier.height(24.dp))
                HorizontalDivider(color = AppColors.Border)
                DetailInfoRow("저장한 날", dateLabel(item.createdAt))
                DetailInfoRow("가져온 곳", item.platform.label)
                item.desire?.let { DetailInfoRow("담을 때의 마음", it.label) }
                if (pending) DetailInfoRow("다시 볼 시간", timeLabel(item.cooldownUntil))
                else {
                    DetailInfoRow("결정한 날", dateLabel(item.decidedAt ?: item.createdAt))
                    DetailInfoRow("결정할 때의 가격", money(item.decisionPriceWon))
                }
                HorizontalDivider(color = AppColors.Border)
                if (hasLink) {
                    Text("원본 링크", Modifier.padding(top = 22.dp, bottom = 8.dp), style = MaterialTheme.typography.labelLarge, color = AppColors.Secondary)
                    SelectionContainer { Text(item.url, style = MaterialTheme.typography.bodySmall, color = AppColors.Muted) }
                }
                if (item.isSample) {
                    Text("앱을 둘러볼 수 있도록 준비한 예시예요. 내 기록 합계에는 포함하지 않아요.", Modifier.padding(top = 18.dp), style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
                } else if (!pending) {
                    Text("정보를 수정해도 결정할 때 남긴 금액은 바뀌지 않아요.", Modifier.padding(top = 18.dp), style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
                } else if (!hasLink) {
                    Text("직접 적어둔 상품이에요. 정보 수정에서 내용을 바꿀 수 있어요.", Modifier.padding(top = 18.dp), style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
                }
                Spacer(Modifier.height(28.dp))
            }
            DecisionFooter {
                when {
                    pending && item.ready(now) -> {
                        PrimaryAction("결정하기", onDecide, Modifier.fillMaxWidth().testTag("detail_decide"))
                        if (hasLink) TextButton(onClick = onOriginal, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("원본에서 보기", color = AppColors.Secondary) }
                    }
                    hasLink -> PrimaryAction("원본에서 보기", onOriginal, Modifier.fillMaxWidth().testTag("detail_original"))
                    else -> PrimaryAction("보관함으로", onBack, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun AcornProgress(current: AcornStage) {
    Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(AcornStage.BURIED to "고민하고 있어요", AcornStage.SPROUT to "결정할 때예요").forEach { (stage, label) ->
            val reached = stage.ordinal <= current.ordinal
            Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                .background(if (stage == current) AppColors.BlueTint else Color.Transparent).padding(vertical = 8.dp)
                .alpha(if (reached) 1f else .4f), horizontalAlignment = Alignment.CenterHorizontally) {
                AcornIcon(stage, Modifier.size(40.dp), animate = false)
                Text(label, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelMedium,
                    color = if (stage == current) AppColors.Blue else AppColors.Secondary,
                    fontWeight = if (stage == current) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun DetailTextBlock(title: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(top = 24.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = AppColors.Text)
        SelectionContainer { Text(value, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium, color = AppColors.Secondary) }
    }
}

@Composable
fun DecideScreen(item: WishItem, onBack: () -> Unit, onExtend: () -> Unit, onDecision: (Decision) -> Unit) =
    DecideScreen(item, onBack, onExtend, onDecision, budgetPreview = null)

@Composable
fun DecideScreen(
    item: WishItem,
    onBack: () -> Unit,
    onExtend: () -> Unit,
    onDecision: (Decision) -> Unit,
    budgetPreview: String?,
) {
    val largeFont = LocalDensity.current.fontScale > 1.3f
    ContentFrame {
        Column(Modifier.fillMaxSize().background(AppColors.Surface)) {
            DecisionBackButton(onBack, "내 마음 다시 보기")
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.width(176.dp).height(if (largeFont) 100.dp else 124.dp)) {
                    ProductImage(item, Modifier.size(if (largeFont) 80.dp else 104.dp).align(Alignment.BottomCenter).clip(RoundedCornerShape(20.dp)))
                    Mascot(MascotPose.TORI_PRESENT, Modifier.align(Alignment.TopEnd), size = 64.dp)
                }
                Text("${dateLabel(item.createdAt)}에 담아뒀어요", Modifier.padding(top = 24.dp), style = MaterialTheme.typography.bodyMedium, color = AppColors.Secondary)
                item.desire?.let { Spacer(Modifier.height(10.dp)); Pill("담을 때 ${it.label}", AppColors.BlueTint, AppColors.Blue) }
                if (LocalShowBubbles.current) SpeechBubble(when (item.desire) {
                    Desire.FIRE -> "그땐 엄청 갖고 싶었어요. 지금은 어떤가요?"
                    Desire.MEH -> "그때도 고민했어요. 지금 마음은 어떤가요?"
                    else -> "이 상품, 아직도 마음에 남아 있나요?"
                }, Modifier.padding(top = 16.dp), tail = BubbleTail.BOTTOM, speaker = "토리")
                Text("잠시 생각해 봤어요.\n지금도 사고 싶나요?", Modifier.padding(top = 12.dp), fontSize = 28.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold, color = AppColors.Text, textAlign = TextAlign.Center)
                Text(item.name, Modifier.padding(top = 24.dp), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, color = AppColors.Text)
                Text(money(item.priceWon), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyLarge, color = AppColors.Secondary, textAlign = TextAlign.Center)
                budgetPreview?.let { Text(it, Modifier.padding(top = 16.dp).testTag("decision_budget_preview"), style = MaterialTheme.typography.bodyMedium, color = AppColors.Secondary, textAlign = TextAlign.Center) }
                if (item.userMemo.isNotBlank()) Column(Modifier.padding(top = 20.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(AppColors.Soft).padding(16.dp)) {
                    Text("담아둘 때 남긴 마음이에요", style = MaterialTheme.typography.labelMedium, color = AppColors.Secondary)
                    Text(item.userMemo, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium, color = AppColors.Text)
                }
                if (item.isSample) { Spacer(Modifier.height(12.dp)); Pill("예시 상품", AppColors.Soft, AppColors.Muted) }
                TextButton(onClick = onExtend, modifier = Modifier.padding(top = 20.dp).heightIn(min = 48.dp).testTag("extend_item")) { Text("조금 더 고민할래요", color = AppColors.Blue, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center) }
            }
            DecisionFooter {
                if (largeFont) {
                    PrimaryAction("안 살래요", { onDecision(Decision.SKIP) }, Modifier.fillMaxWidth().testTag("decide_skip"), weak = true)
                    Spacer(Modifier.height(8.dp))
                    PrimaryAction("살래요", { onDecision(Decision.BUY) }, Modifier.fillMaxWidth().testTag("decide_buy"))
                } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryAction("안 살래요", { onDecision(Decision.SKIP) }, Modifier.weight(1f).testTag("decide_skip"), weak = true)
                    PrimaryAction("살래요", { onDecision(Decision.BUY) }, Modifier.weight(1f).testTag("decide_buy"))
                }
            }
        }
    }
}

@Composable
fun ResultScreen(
    item: WishItem,
    items: List<WishItem>,
    now: Long,
    onHome: () -> Unit,
    onOriginal: () -> Unit
) = ResultScreen(item, items, now, onHome, onOriginal, celebrateEvent = false)

@Composable
fun ResultScreen(
    item: WishItem,
    items: List<WishItem>,
    now: Long,
    onHome: () -> Unit,
    onOriginal: () -> Unit,
    celebrateEvent: Boolean,
    onCelebrationConsumed: () -> Unit = {}
) {
    val skipped = item.decision == Decision.SKIP
    val monthlyTotal = skippedTotal(items.filter { sameMonth(it.decidedAt ?: it.createdAt, now) })
    val reducedMotion = rememberReducedMotion() || LocalReduceMotion.current
    val eventKey = "${item.id}:${item.decidedAt}:${item.decision}"
    var effectConsumed by rememberSaveable(eventKey) { mutableStateOf(false) }
    val confetti = remember(eventKey) { Animatable(1f) }
    val countProgress = remember(eventKey) { Animatable(1f) }
    val consumeEvent by rememberUpdatedState(onCelebrationConsumed)
    LaunchedEffect(eventKey, reducedMotion) {
        if (reducedMotion) { confetti.snapTo(1f); countProgress.snapTo(1f) }
        if (celebrateEvent && !effectConsumed) {
            effectConsumed = true
            consumeEvent()
            if (skipped && !item.isSample && !reducedMotion) {
                confetti.snapTo(0f)
                countProgress.snapTo(0f)
                coroutineScope {
                    launch { confetti.animateTo(1f, tween(1450, easing = LinearEasing)) }
                    launch { countProgress.animateTo(1f, tween(550, easing = LinearEasing)) }
                }
            }
        }
    }
    ContentFrame {
        Box(Modifier.fillMaxSize().clipToBounds().background(AppColors.Surface)) {
            Column(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Mascot(if (skipped) MascotPose.TORI_CHEER else MascotPose.MOA_HAPPY, size = 132.dp)
                    if (LocalShowBubbles.current) SpeechBubble(
                        when {
                            item.isSample -> "예시로 우리의 선택을 살펴봤어요"
                            !skipped -> "사기로 했군요! 원본에서 한 번 더 살펴봐요"
                            item.decisionPriceWon == null -> "마음을 한 번 더 확인한 것으로 충분해요"
                            else -> "우리의 선택으로 나무 한 그루가 자랐어요!"
                        }, Modifier.padding(top = 12.dp), tail = BubbleTail.BOTTOM, speaker = if (skipped) "토리" else "모아",
                    )
                    Text(if (item.isSample) "예시로 결정을\n해봤어요" else if (skipped) "한 번 더 생각하고\n결정했어요" else "구매하기로\n결정했어요", Modifier.padding(top = 20.dp), fontSize = 28.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold, color = AppColors.Text, textAlign = TextAlign.Center)
                    Text(if (skipped) "이번에는 사지 않기로 했어요" else "나에게 필요한지 살펴보고 선택했어요", Modifier.padding(top = 14.dp), style = MaterialTheme.typography.bodyLarge, color = AppColors.Secondary, textAlign = TextAlign.Center)
                    Text(item.name, Modifier.padding(top = 20.dp), style = MaterialTheme.typography.titleSmall, color = AppColors.Text, textAlign = TextAlign.Center)
                    if (!item.isSample && skipped) {
                        Column(Modifier.padding(top = 28.dp).fillMaxWidth().background(AppColors.Soft, RoundedCornerShape(18.dp)).padding(20.dp)) {
                            Text(if (item.decisionPriceWon == null) "가격 없이 선택을 기록했어요" else "이번에 쓰지 않기로 한 금액", style = MaterialTheme.typography.bodySmall, color = AppColors.Secondary)
                            if (item.decisionPriceWon != null) Text(money(item.decisionPriceWon), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = AppColors.Text)
                            HorizontalDivider(Modifier.padding(vertical = 18.dp), color = AppColors.Border)
                            Text("이번 달 쓰지 않기로 한 금액", style = MaterialTheme.typography.bodySmall, color = AppColors.Secondary)
                            val animatedTotal = if (countProgress.value >= 1f) monthlyTotal else (monthlyTotal.toDouble() * countProgress.value).toLong()
                            Text(money(animatedTotal), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = AppColors.Text)
                            if (item.decisionPriceWon == null) Text("가격이 없는 상품은 금액 합계에 포함하지 않아요", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
                        }
                    } else if (item.isSample) {
                        Text("예시 상품은 내 기록 합계에 포함하지 않아요", Modifier.padding(top = 20.dp), style = MaterialTheme.typography.bodySmall, color = AppColors.Muted, textAlign = TextAlign.Center)
                    } else {
                        Text(if (item.url.isNotBlank()) "원본에서 상품과 가격을 한 번 더 확인해 주세요" else "구매하기로 한 선택을 기록에 남겼어요", Modifier.padding(top = 20.dp), style = MaterialTheme.typography.bodyMedium, color = AppColors.Secondary, textAlign = TextAlign.Center)
                    }
                }
                DecisionFooter {
                    if (!skipped && item.url.isNotBlank()) {
                        PrimaryAction("원본에서 보기", onOriginal, Modifier.fillMaxWidth())
                        TextButton(onClick = onHome, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("result_home")) { Text("보관함으로", color = AppColors.Secondary) }
                    } else PrimaryAction("보관함으로", onHome, Modifier.fillMaxWidth().testTag("result_home"))
                }
            }
            if (!reducedMotion && confetti.value < 1f) CelebrationConfetti(confetti.value, Modifier.fillMaxSize())
        }
    }
}

/** Visual-only particles have no pointer input and never control saving or navigation. */
@Composable
internal fun CelebrationConfetti(progress: Float, modifier: Modifier = Modifier) {
    val palette = listOf(AppColors.Primary, Color(0xFFFFC83D), Color(0xFFF4B56E), Color(0xFFDB9550), Color(0xFFFF8F8F))
    Canvas(modifier) {
        val fade = ((1f - progress) / .3f).coerceIn(0f, 1f)
        repeat(32) { index ->
            val seed = (index * 73 % 97) / 97f
            val side = if (index % 2 == 0) -1f else 1f
            val startX = size.width * (.45f + seed * .1f)
            val spread = side * (.12f + seed * .62f) * size.width
            val x = startX + spread * progress
            val y = size.height * .2f - size.height * (.15f + seed * .3f) * progress + size.height * (.7f + seed * .35f) * progress * progress
            val drift = sin((progress * 12f + index).toDouble()).toFloat() * 8.dp.toPx() * progress
            val center = Offset(x + drift, y)
            val color = palette[index % palette.size].copy(alpha = fade)
            val particleWidth = (4 + index % 3).dp.toPx()
            rotate(index * 31f + progress * side * 360f, center) {
                if (index % 4 == 0) drawCircle(color, particleWidth / 2, center)
                else drawRoundRect(color, Offset(center.x - particleWidth / 2, center.y - particleWidth), Size(particleWidth, particleWidth * 1.8f), CornerRadius(1.dp.toPx()))
            }
        }
    }
}

@Composable
private fun DecisionBackButton(onBack: () -> Unit, title: String = "", onEdit: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp).semantics { contentDescription = "뒤로가기" }) { AppIcon("back") }
        Text(title, Modifier.weight(1f).padding(horizontal = 4.dp), style = MaterialTheme.typography.titleSmall, color = AppColors.Text)
        if (onEdit != null) TextButton(onClick = onEdit, modifier = Modifier.heightIn(min = 48.dp).testTag("edit_item")) { Text("정보 수정", color = AppColors.Blue) }
    }
}

@Composable
private fun DetailInfoRow(label: String, value: String) {
    val largeFont = LocalDensity.current.fontScale > 1.3f
    if (largeFont) Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = AppColors.Secondary)
        Text(value, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = AppColors.Text)
    } else Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = AppColors.Secondary)
        Text(value, Modifier.weight(1.3f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = AppColors.Text, textAlign = TextAlign.End)
    }
}

@Composable
private fun DecisionFooter(content: @Composable ColumnScope.() -> Unit) {
    HorizontalDivider(color = AppColors.Border)
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 16.dp), content = content)
}
