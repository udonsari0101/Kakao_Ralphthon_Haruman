package org.ralphthon.haruman_ralphthon.ui

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.ralphthon.haruman_ralphthon.data.WishItem
import org.ralphthon.haruman_ralphthon.ui.mascot.*
import kotlin.math.abs

/** A visual session host. Only onAction/onUndo may change stored decisions. */
@Composable
fun ReviewScreen(
    session: ReviewSession,
    item: WishItem?,
    defaultHours: Int,
    now: Long,
    onBack: () -> Unit,
    onAction: (ReviewAction) -> Boolean,
    onUndo: () -> Unit,
    onCelebrationConsumed: () -> Unit,
    onHistory: () -> Unit,
    onShare: (String) -> Unit,
    budgetPreview: String? = null,
    homeLabel: String? = "보관함으로",
) {
    val reduced = mascotMotionReduced()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val currentItem by rememberUpdatedState(item)
    val currentSession by rememberUpdatedState(session)
    val actionCallback by rememberUpdatedState(onAction)
    var displayedItem by remember(session.id) { mutableStateOf(item) }
    var displayedIndex by remember(session.id) { mutableIntStateOf(session.index) }
    var locked by remember(session.id) { mutableStateOf(false) }
    var dragX by remember(session.id) { mutableFloatStateOf(0f) }
    var translateY by remember(session.id) { mutableFloatStateOf(0f) }
    var cardScale by remember(session.id) { mutableFloatStateOf(1f) }
    var cardAlpha by remember(session.id) { mutableFloatStateOf(1f) }
    var cardWidth by remember { mutableFloatStateOf(with(density) { 280.dp.toPx() }) }
    var cardHeight by remember { mutableFloatStateOf(with(density) { 320.dp.toPx() }) }
    var movement by remember { mutableStateOf<Job?>(null) }
    var feedback by remember(session.id) { mutableStateOf<String?>(null) }
    var feedbackAction by remember(session.id) { mutableStateOf<ReviewAction?>(null) }
    val feedbackProgress = remember(session.id) { Animatable(1f) }
    var feedbackJob by remember { mutableStateOf<Job?>(null) }
    var celebrationSeen by rememberSaveable(session.id) { mutableStateOf(session.celebrationConsumed) }
    val largeFont = density.fontScale > 1.3f
    val pageScroll = rememberScrollState()
    val bodyScroll = rememberScrollState()

    LaunchedEffect(session.celebrationConsumed) { if (session.celebrationConsumed) celebrationSeen = true }
    LaunchedEffect(item?.id, session.index) {
        if (!locked) {
            displayedItem = item
            displayedIndex = session.index
            dragX = 0f; translateY = 0f; cardScale = 1f; cardAlpha = 1f
            bodyScroll.scrollTo(0)
            pageScroll.scrollTo(0)
        }
    }
    LaunchedEffect(reduced) {
        if (reduced) {
            movement?.cancel(); feedbackJob?.cancel(); feedbackProgress.snapTo(1f)
            dragX = 0f; translateY = 0f; cardScale = 1f; cardAlpha = 1f
            displayedItem = currentItem; displayedIndex = currentSession.index
            locked = false
        }
    }

    fun returnToCenter() {
        if (locked) return
        movement?.cancel()
        val start = dragX
        movement = scope.launch {
            if (reduced) dragX = 0f
            else Animatable(start).animateTo(0f, spring(dampingRatio = .8f, stiffness = 450f)) { dragX = value }
        }
    }

    fun submit(action: ReviewAction) {
        val before = displayedItem ?: return
        if (locked || currentSession.complete || currentItem?.id != before.id) return
        movement?.cancel()
        locked = true
        // Persist first. The old card remains a visual snapshot while the model already advances.
        if (!actionCallback(action)) {
            locked = false
            returnToCenter()
            return
        }
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        feedbackAction = action
        feedback = when (action) {
            ReviewAction.SKIP -> if (!before.isSample && before.priceWon != null) "+${money(before.priceWon)}" else "마음을 한 번 더 확인했어요"
            ReviewAction.BUY -> "사기로 한 선택을 남겼어요"
            ReviewAction.LATER -> "${defaultHours}시간 더 생각해 봐요"
        }
        feedbackJob?.cancel()
        feedbackJob = scope.launch {
            if (reduced) feedbackProgress.snapTo(1f)
            else { feedbackProgress.snapTo(0f); feedbackProgress.animateTo(1f, tween(600)) }
        }
        val startX = dragX
        movement = scope.launch {
            if (reduced) {
                Animatable(1f).animateTo(0f, tween(150)) { cardAlpha = value }
            } else when (action) {
                ReviewAction.SKIP, ReviewAction.BUY -> {
                    val target = cardWidth * if (action == ReviewAction.SKIP) -1.3f else 1.3f
                    Animatable(startX).animateTo(target, tween(240)) { dragX = value }
                }
                ReviewAction.LATER -> {
                    Animatable(0f).animateTo(1f, tween(300)) {
                        cardScale = 1f - value * .7f
                        translateY = cardHeight * .55f * value
                        cardAlpha = 1f - value
                    }
                }
            }
            displayedItem = currentItem
            displayedIndex = currentSession.index
            dragX = 0f; translateY = 0f; cardAlpha = 1f
            bodyScroll.scrollTo(0)
            pageScroll.scrollTo(0)
            if (!currentSession.complete && !reduced) {
                cardScale = .95f
                Animatable(.95f).animateTo(1f, tween(220)) { cardScale = value }
            } else cardScale = 1f
            locked = false
        }
    }

    val canUndo = session.results.isNotEmpty() && now < session.undoUntil
    val showSummary = session.complete && !locked
    ContentFrame {
        BoxWithConstraints(Modifier.fillMaxSize().clipToBounds().background(AppColors.Surface)) {
            // Large type or a short window needs one reading order: product details
            // remain fully scrollable before the actions instead of sitting behind them.
            val scrollWholePage = largeFont || maxHeight < 480.dp
            if (showSummary) {
                ReviewSummary(
                    session = session,
                    canCelebrate = !celebrationSeen && !session.celebrationConsumed,
                    onCelebrate = { celebrationSeen = true; onCelebrationConsumed() },
                    canUndo = canUndo,
                    onUndo = onUndo,
                    onBack = onBack,
                    onHistory = onHistory,
                    onShare = onShare,
                    homeLabel = homeLabel,
                )
            } else Column(if (scrollWholePage) Modifier.fillMaxSize().verticalScroll(pageScroll) else Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp).semantics { contentDescription = "정리 시간 나가기" }) { AppIcon("back") }
                    Text("정리 시간", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = AppColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${(displayedIndex + 1).coerceAtMost(session.ids.size)} / ${session.ids.size}", Modifier.padding(horizontal = 12.dp).testTag("review_progress"), style = MaterialTheme.typography.labelLarge, color = AppColors.Secondary)
                }
                val bodyModifier = if (scrollWholePage) Modifier.fillMaxWidth() else Modifier.weight(1f).fillMaxWidth().verticalScroll(bodyScroll)
                Column(bodyModifier.padding(horizontal = 20.dp).padding(bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    val visible = displayedItem
                    if (visible == null) {
                        EmptyState("상품 상태를 다시 확인해 주세요", "이미 남긴 선택은 기록에 보관했어요", MascotPose.TORI_IDLE)
                        homeLabel?.let { PrimaryAction(it, onBack, Modifier.fillMaxWidth()) }
                    } else {
                        val activePose = if (!locked) MascotPose.TORI_PRESENT else when (feedbackAction) {
                            ReviewAction.SKIP -> MascotPose.TORI_WAVE
                            ReviewAction.BUY -> MascotPose.MOA_HAPPY
                            ReviewAction.LATER -> MascotPose.TORI_BURY
                            else -> MascotPose.TORI_PRESENT
                        }
                        MascotMessage(activePose, if (locked) when (feedbackAction) {
                            ReviewAction.SKIP -> "한 번 더 생각하고 결정했어요"
                            ReviewAction.BUY -> "나에게 필요한지 살펴보고 골랐어요"
                            ReviewAction.LATER -> MascotCopy.extended("${defaultHours}시간")
                            else -> MascotCopy.review(visible.desire, dateLabel(visible.createdAt))
                        } else MascotCopy.review(visible.desire, dateLabel(visible.createdAt)), Modifier.fillMaxWidth().padding(bottom = 14.dp), size = if (largeFont) 72.dp else 96.dp)
                        Box(Modifier.fillMaxWidth().padding(bottom = 16.dp), contentAlignment = Alignment.Center) {
                            val remaining = session.ids.size - displayedIndex
                            for (depth in minOf(2, remaining - 1) downTo 1) {
                                Box(Modifier.matchParentSize().graphicsLayer {
                                    scaleX = 1f - depth * .05f
                                    scaleY = 1f - depth * .05f
                                    translationY = depth * 8.dp.toPx()
                                }.clip(RoundedCornerShape(24.dp)).background(if (depth == 1) AppColors.Border else AppColors.Soft))
                            }
                            val swipeModifier = Modifier.pointerInput(visible.id, locked, reduced) {
                                val tracker = VelocityTracker()
                                var crossedThreshold = false
                                var dragAllowed = false
                                val velocityThreshold = 1200.dp.toPx()
                                detectHorizontalDragGestures(
                                    onDragStart = {
                                        dragAllowed = !locked
                                        if (dragAllowed) {
                                            movement?.cancel(); tracker.resetTracking(); crossedThreshold = false
                                            tracker.addPosition(SystemClock.uptimeMillis(), Offset(dragX, 0f))
                                        }
                                    },
                                    onHorizontalDrag = { change, amount ->
                                        if (dragAllowed && !locked) {
                                            change.consume()
                                            dragX += amount
                                            tracker.addPosition(change.uptimeMillis, Offset(dragX, 0f))
                                            if (!crossedThreshold && abs(dragX) >= cardWidth * .3f) {
                                                crossedThreshold = true
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            }
                                        }
                                    },
                                    onDragEnd = {
                                        if (dragAllowed && !locked) {
                                            val velocity = tracker.calculateVelocity().x
                                            if (abs(dragX) >= cardWidth * .3f || abs(velocity) >= velocityThreshold) {
                                                val direction = if (abs(dragX) >= cardWidth * .3f) dragX else velocity
                                                submit(if (direction < 0) ReviewAction.SKIP else ReviewAction.BUY)
                                            } else returnToCenter()
                                        }
                                        dragAllowed = false
                                    },
                                    onDragCancel = { dragAllowed = false; if (!locked) returnToCenter() },
                                )
                            }
                            ReviewProductCard(visible, Modifier.fillMaxWidth().onSizeChanged {
                                cardWidth = it.width.toFloat(); cardHeight = it.height.toFloat()
                            }.graphicsLayer {
                                translationX = dragX; translationY = translateY
                                rotationZ = if (reduced) 0f else (dragX / cardWidth.coerceAtLeast(1f)) * 12f
                                scaleX = cardScale; scaleY = cardScale; alpha = cardAlpha
                            }.then(swipeModifier).testTag("review_card"), dragX / cardWidth.coerceAtLeast(1f))
                            if (feedback != null && feedbackProgress.value < 1f) {
                                Text(feedback.orEmpty(), Modifier.align(Alignment.TopCenter).graphicsLayer {
                                    alpha = (1f - feedbackProgress.value).coerceIn(0f, 1f)
                                    translationY = -feedbackProgress.value * 28.dp.toPx()
                                }.clip(RoundedCornerShape(12.dp)).background(AppColors.Surface).padding(12.dp), color = if (feedbackAction == ReviewAction.SKIP) AppColors.Green else AppColors.Blue, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                            }
                        }
                        Text("옆으로 넘기거나 아래 버튼으로 골라 주세요", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted, textAlign = TextAlign.Center)
                    }
                }
                if (canUndo) ReviewUndo(locked, onUndo)
                HorizontalDivider(color = AppColors.Border)
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 8.dp)) {
                    budgetPreview?.let { Text(it, Modifier.padding(bottom = 10.dp).testTag("review_budget_preview"), style = MaterialTheme.typography.bodySmall, color = AppColors.Secondary) }
                    val enabled = !locked && displayedItem != null
                    if (largeFont) {
                        PrimaryAction("안 살래요", { submit(ReviewAction.SKIP) }, Modifier.fillMaxWidth().testTag("review_skip"), enabled = enabled, weak = true)
                        Spacer(Modifier.height(8.dp))
                        PrimaryAction("살래요", { submit(ReviewAction.BUY) }, Modifier.fillMaxWidth().testTag("review_buy"), enabled = enabled)
                    } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PrimaryAction("안 살래요", { submit(ReviewAction.SKIP) }, Modifier.weight(1f).testTag("review_skip"), enabled = enabled, weak = true)
                        PrimaryAction("살래요", { submit(ReviewAction.BUY) }, Modifier.weight(1f).testTag("review_buy"), enabled = enabled)
                    }
                    TextButton(onClick = { submit(ReviewAction.LATER) }, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("review_later")) {
                        Text("${defaultHours}시간 조금 더 생각할래요", textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewProductCard(item: WishItem, modifier: Modifier, progress: Float) {
    Box(modifier.clip(RoundedCornerShape(24.dp)).background(AppColors.Soft)) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            ProductImage(item, Modifier.size(112.dp).clip(RoundedCornerShape(18.dp)))
            Text(item.name, Modifier.padding(top = 20.dp), style = MaterialTheme.typography.titleLarge, color = AppColors.Text, textAlign = TextAlign.Center)
            Text(money(item.priceWon), Modifier.padding(top = 10.dp), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = AppColors.Text, textAlign = TextAlign.Center)
            Text("${item.platform.label} · ${dateLabel(item.createdAt)}에 담았어요", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = AppColors.Secondary, textAlign = TextAlign.Center)
            item.desire?.let { Spacer(Modifier.height(12.dp)); Pill("담을 때 ${it.label}", AppColors.BlueTint, AppColors.Blue) }
            if (item.isSample) { Spacer(Modifier.height(10.dp)); Pill("예시 · 내 실적에 포함하지 않아요", AppColors.Surface, AppColors.Muted) }
        }
        if (abs(progress) > .01f) {
            Box(Modifier.align(if (progress < 0) Alignment.TopEnd else Alignment.TopStart).padding(16.dp).graphicsLayer { alpha = (abs(progress) / .3f).coerceIn(0f, 1f) }) {
                Pill(if (progress < 0) "안 살래요" else "살래요", if (progress < 0) AppColors.GreenTint else AppColors.BlueTint, if (progress < 0) AppColors.Green else AppColors.Blue)
            }
        }
    }
}

@Composable
private fun ReviewUndo(disabled: Boolean, onUndo: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(AppColors.BlueTint).padding(horizontal = 20.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("방금 남긴 선택을 바꿀 수 있어요", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = AppColors.Text)
        TextButton(onClick = onUndo, enabled = !disabled, modifier = Modifier.heightIn(min = 48.dp).testTag("review_undo")) { Text("되돌리기", color = AppColors.Blue) }
    }
}

@Composable
private fun ReviewSummary(
    session: ReviewSession,
    canCelebrate: Boolean,
    onCelebrate: () -> Unit,
    canUndo: Boolean,
    onUndo: () -> Unit,
    onBack: () -> Unit,
    onHistory: () -> Unit,
    onShare: (String) -> Unit,
    homeLabel: String?,
) {
    val reduced = mascotMotionReduced()
    val confetti = remember(session.id) { Animatable(1f) }
    val countProgress = remember(session.id) { Animatable(1f) }
    val consume by rememberUpdatedState(onCelebrate)
    LaunchedEffect(session.id, reduced) {
        if (reduced) { confetti.snapTo(1f); countProgress.snapTo(1f) }
        if (canCelebrate) {
            consume()
            if (!reduced && session.realSkipCount > 0) {
                confetti.snapTo(0f); countProgress.snapTo(0f)
                coroutineScope {
                    launch { confetti.animateTo(1f, tween(1450, easing = LinearEasing)) }
                    launch { countProgress.animateTo(1f, tween(550)) }
                }
            }
        }
    }
    val realBuy = session.bought.count { !it.isSample }
    val realLater = session.later.count { !it.isSample }
    val unknown = session.skipped.count { !it.isSample && it.decisionPriceWon == null }
    Box(Modifier.fillMaxSize().clipToBounds()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp).semantics { contentDescription = "이전 화면으로 돌아가기" }) { AppIcon("back") }
                Text("정리 결과", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = AppColors.Text)
                Text("${session.ids.size} / ${session.ids.size}", Modifier.padding(horizontal = 12.dp).testTag("review_progress"), style = MaterialTheme.typography.labelLarge, color = AppColors.Secondary)
            }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Mascot(MascotPose.TORI_CHEER, size = 132.dp)
                if (LocalShowBubbles.current) SpeechBubble(if (session.realSkipCount > 0) MascotCopy.reviewSkipped(session.realSkipCount) else MascotCopy.REVIEW_NO_SKIP, tail = BubbleTail.BOTTOM, speaker = "토리")
                Text("오늘의 정리를 마쳤어요!", Modifier.padding(top = 20.dp).testTag("review_complete"), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = AppColors.Text, textAlign = TextAlign.Center)
                Text("${session.realSkipCount}개는 보내고, ${realBuy}개는 사기로 했어요\n${realLater}개는 조금 더 생각해 보기로 했어요", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodyMedium, color = AppColors.Secondary, textAlign = TextAlign.Center)
                Column(Modifier.padding(top = 24.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MascotStageColor).padding(20.dp)) {
                    Text("오늘 쓰지 않기로 한 금액", style = MaterialTheme.typography.bodySmall, color = AppColors.Secondary)
                    val total = if (countProgress.value >= 1f) session.total else (session.total.toDouble() * countProgress.value).toLong()
                    Text(money(total), Modifier.padding(top = 10.dp).testTag("review_total"), fontSize = 30.sp, fontWeight = FontWeight.Bold, color = AppColors.Text)
                    if (unknown > 0) Text("가격이 없는 ${unknown}개 상품은 금액 합계에 포함하지 않아요", Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodySmall, color = AppColors.Secondary)
                    if (session.hasExamples) Text("예시 상품 ${session.results.count { it.after.isSample }}개도 살펴봤어요. 위 개수와 금액에는 포함하지 않아요", Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodySmall, color = AppColors.Secondary)
                }
                OutlinedButton(onClick = {
                    onShare(buildString {
                        append("하루만에서 오늘 ${session.realSkipCount}개를 사지 않기로 하고 ${money(session.total)}을 쓰지 않기로 했어요 🌳")
                        append("\n${realBuy}개는 구매 결정, ${realLater}개는 조금 더 생각해 보기로 했어요.")
                        if (unknown > 0) append("\n가격이 없는 ${unknown}개는 금액 합계에서 제외했어요.")
                        if (session.hasExamples) append("\n예시 상품은 제외한 기록이에요.")
                    })
                }, modifier = Modifier.fillMaxWidth().padding(top = 20.dp).heightIn(min = 52.dp).testTag("review_share")) { Text("정리 결과 공유") }
                if (session.bought.isNotEmpty()) TextButton(onClick = onHistory, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("review_history")) { Text("사기로 한 상품 보기") }
            }
            if (canUndo) ReviewUndo(false, onUndo)
            if (homeLabel != null) Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 16.dp)) {
                PrimaryAction(homeLabel, onBack, Modifier.fillMaxWidth().testTag("review_home"))
            }
        }
        if (!reduced && confetti.value < 1f) CelebrationConfetti(confetti.value, Modifier.fillMaxSize())
    }
}
