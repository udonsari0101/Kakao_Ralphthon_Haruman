package org.ralphthon.haruman_ralphthon.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import org.ralphthon.haruman_ralphthon.BuildConfig
import org.ralphthon.haruman_ralphthon.data.*
import org.ralphthon.haruman_ralphthon.ui.mascot.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HarumanApp(model: HarumanViewModel = viewModel()) {
    val reduced = rememberReducedMotion() || model.mascotReduceMotion
    CompositionLocalProvider(LocalReduceMotion provides reduced, LocalShowBubbles provides model.mascotBubbles) {
        HarumanContent(model)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun HarumanContent(model: HarumanViewModel) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var dialog by rememberSaveable { mutableStateOf("") }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val selected = model.selected
    val budget = remember(model.items, model.yardPeriod, periodOf(now, model.yardPeriod)) {
        yardSummary(model.items, now, model.yardPeriod, now)
    }
    var reportAt by rememberSaveable { mutableLongStateOf(periodOf(now, model.yardPeriod) - 1L) }
    var reportAuto by rememberSaveable { mutableStateOf(false) }
    var milestoneCopy by rememberSaveable { mutableStateOf("") }
    val screenStates = rememberSaveableStateHolder()
    fun openOriginal(item: WishItem) {
        if (item.url.isBlank()) {
            model.notifyUser(if (item.isSample) "둘러보기용 예시 상품이에요" else "직접 입력한 상품에는 링크가 없어요")
            return
        }
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.url))) }
            .onFailure { model.notifyUser("링크를 열 수 있는 앱을 찾지 못했어요") }
    }
    LaunchedEffect(model) { model.notices.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1_000) } }
    LaunchedEffect(model.sheetOpen, model.sharedChoices, model.shareConflict) {
        if (model.sheetOpen || model.sharedChoices.isNotEmpty() || model.shareConflict) dialog = ""
    }
    LaunchedEffect(model.route, model.sheetOpen, model.sharedChoices, model.shareConflict, dialog, model.items, model.spendingGoalWon, model.yardPeriod, budget.bounds.startInclusive) {
        if (model.route != "vault" || model.sheetOpen || model.sharedChoices.isNotEmpty() || model.shareConflict || dialog.isNotEmpty()) return@LaunchedEffect
        val previous = periodOf(now, model.yardPeriod) - 1L
        val previousSummary = yardSummary(model.items, previous, model.yardPeriod, now)
        if ((previousSummary.realItems.isNotEmpty() || previousSummary.budgetItems.isNotEmpty()) &&
            model.claimYardReport(previousSummary.bounds.startInclusive)) {
            reportAt = previous
            reportAuto = true
            dialog = "report"
        } else model.spendingGoalWon?.let { goal ->
            val tier = budgetTier(budget.budgetBoughtWon, goal)
            if (model.claimYardMilestone(budget.bounds.startInclusive, tier)) {
                milestoneCopy = when {
                    budget.budgetBoughtWon > goal -> "목표보다 ${money(budget.budgetBoughtWon - goal)} 많아졌어요. 괜찮아요. 남은 새싹을 천천히 살펴봐요."
                    budget.budgetBoughtWon == goal -> "이번 기간의 목표 금액을 모두 채웠어요. 다음 상품은 한 번 더 생각해 볼까요?"
                    tier == 2 -> "저장방이 거의 찼어요. 다음 상품은 조금 더 기다려 보면 어때요? 대기 시간은 직접 골라도 괜찮아요."
                    else -> "모아의 저장방이 꽤 찼어요. 지금까지의 선택을 함께 살펴봐요."
                }
                if (budget.budgetUnknownCount > 0) milestoneCopy += " 금액 미정 ${budget.budgetUnknownCount}개는 계산에서 제외했어요."
                dialog = "budget"
            }
        }
    }
    BackHandler(enabled = model.route !in listOf("vault", "onboarding") && !model.sheetOpen) { model.home() }
    Scaffold(
        containerColor = AppColors.Background,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { if (model.route in listOf("vault", "history", "my")) BottomTabs(model.route, model::navigate) },
    ) { insets ->
        Box(Modifier.fillMaxSize().padding(insets)) {
          screenStates.SaveableStateProvider(model.route) {
            when (model.route) {
                "onboarding" -> Onboarding(model::finishOnboarding)
                "vault" -> Column(Modifier.fillMaxSize()) {
                    if (model.pendingSharedLinks.isNotEmpty()) ResumeBanner("공유한 링크가 기다리고 있어요", "이어서 추가") { model.acceptPendingShare() }
                    if (model.hasHeldDraft()) ResumeBanner("작성 중이던 상품이 있어요", "이어서 작성", model::restoreHeldDraft)
                    Box(Modifier.weight(1f)) { VaultScreen(model.items, now, model::openAdd, { model.select(it, now) }, onReview = model::startReview,
                        yardPeriod = model.yardPeriod, goalWon = model.spendingGoalWon, onStorage = { dialog = "storage" }) }
                }
                "history" -> YardScreen(model.items, now, model.yardPeriod, model.spendingGoalWon,
                    onItem = { model.select(it, now, fromHistory = true) }, onDecide = model::openYardDecision,
                    onGoalEdit = { dialog = "goal" }, onPeriodChange = model::updateYardPeriod)
                "my" -> MyScreen(
                    hours = model.defaultHours, onDefault = { dialog = "cooldown" },
                    onExport = {
                        val text = buildString {
                            appendLine("하루만 · 나의 소비 기록")
                            model.items.filterNot { it.isSample }.forEach {
                                val price = if (it.decision == Decision.PENDING) it.priceWon else it.decisionPriceWon
                                appendLine("${dateLabel(it.createdAt)} | ${it.name} | ${money(price)} | ${it.platform.label} | ${when (it.decision) { Decision.BUY -> "구매 결정"; Decision.SKIP -> "구매 포기"; else -> "고민 중" }}")
                                if (it.userMemo.isNotBlank()) appendLine("메모: ${it.userMemo}")
                            }
                        }
                        runCatching {
                            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }, "내 기록 내보내기"))
                        }.onFailure { model.notifyUser("기록을 보낼 앱을 찾지 못했어요") }
                    },
                    onDelete = { dialog = "delete" }, themePreference = model.themePreference,
                    onThemeChange = model::setTheme, appVersion = BuildConfig.VERSION_NAME,
                    showMascotBubbles = model.mascotBubbles, onMascotBubblesChange = model::updateMascotBubbles,
                    reduceMascotMotion = model.mascotReduceMotion, onReduceMascotMotionChange = model::updateMascotReduceMotion,
                    yardPeriod = model.yardPeriod, onYardPeriodChange = model::updateYardPeriod,
                    spendingGoalWon = model.spendingGoalWon, onGoalEdit = { dialog = "goal" },
                    onReports = {
                        reportAt = periodOf(now, model.yardPeriod) - 1L
                        model.claimYardReport(periodOf(reportAt, model.yardPeriod))
                        reportAuto = false; dialog = "report"
                    },
                )
                "detail" -> selected?.let { item -> DetailScreen(item, now, model::home, { openOriginal(item) }, model::openDecision, onEdit = { model.edit(item) }) }
                "decide" -> selected?.let { item -> DecideScreen(item, model::home, model::extendSelected, model::decideSelected,
                    budgetPreview = buyPreview(item, budget.budgetBoughtWon, model.spendingGoalWon)) }
                "result" -> selected?.let { item -> ResultScreen(item, model.items, now, model::home, { openOriginal(item) }, celebrateEvent = model.celebrationId == item.id, onCelebrationConsumed = model::consumeCelebration) }
                "added" -> selected?.let { item -> AddedScreen(
                    item, model::home, { model.edit(item) }, { model.openDetail(item.id) },
                    receivedEventId = model.receivedId?.takeIf { it == item.id },
                    onReceivedFinished = model::consumeReceived,
                ) }
                "review" -> model.review?.let { session -> ReviewScreen(
                    session, model.currentReviewItem(), model.defaultHours, now,
                    onBack = model::home, onAction = model::reviewAction, onUndo = model::undoReview,
                    onCelebrationConsumed = model::consumeReviewCelebration,
                    budgetPreview = model.currentReviewItem()?.let { buyPreview(it, budget.budgetBoughtWon, model.spendingGoalWon) },
                    onHistory = { model.navigate("history") },
                    onShare = { text -> runCatching {
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text)
                        }, "오늘의 선택 나누기"))
                    }.onFailure { model.notifyUser("기록을 보낼 앱을 찾지 못했어요") } },
                ) }
            }
          }
          // Navigation stays available during the decorative receipt animation.
          // Leaving this screen consumes the event instead of replaying it later.
          LaunchedEffect(model.route, model.receivedId) {
              if (model.route != "added" && model.receivedId != null) model.consumeReceived()
          }
        }
    }
    val draft = model.draft
    if (model.sheetOpen && draft != null) {
        ModalBottomSheet(
            onDismissRequest = model::closeDraft,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = AppColors.Surface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            // Material3 1.3 installs a PRIORITY_OVERLAY back callback above the IME.
            // Disable it at creation; its layout does not update this flag dynamically.
            properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false),
        ) {
            // These locals belong to the dialog window, not the activity behind it.
            val imeVisible = WindowInsets.isImeVisible
            val sheetFocus = LocalFocusManager.current
            val sheetKeyboard = LocalSoftwareKeyboardController.current
            BackHandler {
                if (imeVisible) {
                    sheetFocus.clearFocus()
                    sheetKeyboard?.hide()
                } else {
                    model.closeDraft()
                }
            }
            AddSheet(draft, model.processing, model.requestError, model.retryAllowed,
                onChange = { value -> model.changeDraft { value } }, onModeChange = model::setMode,
                onRequest = model::requestProduct, onSave = { model.saveDraft() }, onCancelRequest = model::cancelRequest,
                budgetPreview = model.spendingGoalWon?.takeIf { draft.editingId == null || model.items.any { it.id == draft.editingId && it.decision == Decision.PENDING } }?.let { goal ->
                    draft.priceWon?.takeIf { !draft.priceError }?.let { price ->
                        val all = saturatedAdd(saturatedAdd(budget.budgetBoughtWon, pendingTotal(model.items, draft.editingId)), price)
                        "고민 중인 것까지 모두 사면 목표의 ${String.format(Locale.KOREA, "%.0f", all.toDouble() / goal * 100)}%예요. 금액 미정은 제외했어요."
                    }
                })
        }
    }
    if (dialog == "goal") GoalEditorDialog(model.spendingGoalWon, model.yardPeriod, model::setSpendingGoal) { dialog = "" }
    if (dialog == "storage") StorageRoomSheet(budget.budgetItems, periodBudgetLabel(model.yardPeriod),
        onItem = { dialog = ""; model.select(it, now, fromHistory = true) }, onDismiss = { dialog = "" })
    if (dialog == "report") YardReportSheet(model.items, model.yardPeriod, reportAt, model.spendingGoalWon, now,
        onItem = { dialog = ""; model.select(it, now, fromHistory = true) }, onDismiss = { dialog = "" }, celebrate = reportAuto)
    if (dialog == "budget") AlertDialog(onDismissRequest = { dialog = "" }, title = { Text("모아의 저장방") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            CheekMoa(model.items.count { !it.isSample && it.decision == Decision.PENDING }, overGoal = true)
            Text(milestoneCopy)
        } }, confirmButton = { TextButton(onClick = { dialog = "" }) { Text("확인했어요") } },
        dismissButton = { TextButton(onClick = { dialog = "goal" }) { Text("목표 다시 정하기") } })
    if (model.sharedChoices.isNotEmpty()) AlertDialog(
        onDismissRequest = model::dismissChoices,
        title = { Text("어떤 상품을 담을까요?") },
        text = { Column(Modifier.heightIn(max = 350.dp).verticalScroll(rememberScrollState())) {
            Text("공유한 링크가 여러 개예요. 하나를 골라 주세요", color = AppColors.Secondary)
            model.sharedChoices.forEachIndexed { index, url ->
                TextButton(onClick = { model.chooseSharedLink(url) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text("${index + 1}. $url", maxLines = 3)
                }
            }
        } },
        confirmButton = { TextButton(onClick = model::dismissChoices) { Text("나중에") } },
    )
    if (model.shareConflict) AlertDialog(
        onDismissRequest = model::keepCurrentDraft,
        title = { Text("작성 중인 내용이 있어요") },
        text = { Text("새로 공유한 링크를 먼저 볼까요? 지금 적은 내용도 보관해 둘게요.") },
        confirmButton = { TextButton(onClick = model::acceptPendingShare) { Text("공유한 링크 보기") } },
        dismissButton = { TextButton(onClick = model::keepCurrentDraft) { Text("작성 이어가기") } },
    )
    if (dialog == "delete") AlertDialog(
        onDismissRequest = { dialog = "" }, title = { Text("모든 기록을 지울까요?") },
        text = { Text("보관한 상품과 결정 기록이 모두 삭제돼요. 지운 기록은 되돌릴 수 없어요.") },
        confirmButton = { TextButton(onClick = { model.deleteAll(); dialog = "" }) { Text("모두 삭제", color = AppColors.Red) } },
        dismissButton = { TextButton(onClick = { dialog = "" }) { Text("취소") } },
    )
    if (dialog == "cooldown") AlertDialog(
        onDismissRequest = { dialog = "" }, title = { Text("언제 다시 생각해 볼까요?") },
        text = { Column { listOf(3, 24, 72, 168).forEach { hours ->
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { model.updateDefaultHours(hours); dialog = "" }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = hours == model.defaultHours, onClick = null)
                Text(cooldownLabel(hours), Modifier.padding(start = 12.dp))
            }
        } } }, confirmButton = { TextButton(onClick = { dialog = "" }) { Text("닫기") } },
    )
}

@Composable
private fun ResumeBanner(message: String, action: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(AppColors.BlueTint).padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(message, Modifier.weight(1f), color = AppColors.Text, fontSize = 13.sp)
        TextButton(onClick = onClick) { Text(action) }
    }
}

@Composable
internal fun AddedScreen(
    item: WishItem,
    onHome: () -> Unit,
    onEdit: () -> Unit,
    onDetail: () -> Unit,
    receivedEventId: String?,
    onReceivedFinished: () -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 600.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            // Keep one fixed header slot before, during, and after receipt motion.
            // It never overlaps the product card or the navigation buttons.
            Box(Modifier.padding(top = 20.dp).size(100.dp).testTag("added_character")) {
                if (receivedEventId != null) {
                    ReceivedOverlay(receivedEventId, onReceivedFinished, Modifier.fillMaxSize())
                } else {
                    CharacterArt("success", Modifier.fillMaxSize())
                }
            }
            Text("보관함에 추가했어요", Modifier.padding(top = 20.dp).testTag("added_title"), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = AppColors.Text)
            Text("마음에 든 이유를 천천히 생각해 봐요", Modifier.padding(top = 8.dp), color = AppColors.Secondary, fontSize = 14.sp)
            Column(Modifier.padding(top = 24.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(AppColors.Surface).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                ProductImage(item, Modifier.size(112.dp))
                Text(item.name, Modifier.padding(top = 16.dp), color = AppColors.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(money(item.priceWon), Modifier.padding(top = 8.dp), color = AppColors.Secondary, fontSize = 16.sp)
                HorizontalDivider(Modifier.padding(vertical = 18.dp), color = AppColors.Border)
                Text("다시 볼 시간", color = AppColors.Secondary, fontSize = 12.sp)
                Text(if (item.cooldownUntil <= item.createdAt) "지금 바로 생각할 수 있어요" else SimpleDateFormat("M월 d일 a h:mm", Locale.KOREAN).format(Date(item.cooldownUntil)), Modifier.padding(top = 6.dp), color = AppColors.Text, fontWeight = FontWeight.SemiBold)
            }
            PrimaryAction("보관함으로", onHome, Modifier.fillMaxWidth().padding(top = 24.dp).testTag("added_home"))
            OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 52.dp).testTag("edit_added"), shape = RoundedCornerShape(12.dp)) { Text("정보 수정") }
            TextButton(onClick = onDetail, modifier = Modifier.heightIn(min = 48.dp)) { Text("상품 자세히 보기") }
        }
    }
}
