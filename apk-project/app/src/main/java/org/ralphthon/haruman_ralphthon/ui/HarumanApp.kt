package org.ralphthon.haruman_ralphthon.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.ralphthon.haruman_ralphthon.data.*
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HarumanApp(incomingShare: String? = null, onShareConsumed: () -> Unit = {}) {
    val context = LocalContext.current
    val store = remember { LocalStore(context.applicationContext) }
    var items by remember { mutableStateOf(store.items()) }
    var route by rememberSaveable { mutableStateOf(if (store.onboardingDone) "vault" else "onboarding") }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var addOpen by rememberSaveable { mutableStateOf(false) }
    var sharedUrl by rememberSaveable { mutableStateOf("") }
    var defaultHours by remember { mutableIntStateOf(store.defaultCooldownHours) }
    var dialog by remember { mutableStateOf("") }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val selected = items.find { it.id == selectedId }
    fun notify(text: String) { scope.launch { snackbar.showSnackbar(text) } }
    fun update(next: List<WishItem>) { items = next; store.saveItems(next) }
    fun home() { route = "vault"; selectedId = null }
    fun openOriginal(item: WishItem) {
        if (item.url.isBlank()) { notify("예시 상품이에요. 링크를 저장하면 원본을 열 수 있어요."); return }
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.url))) }
            .onFailure { notify("링크를 열 수 있는 앱을 찾지 못했어요.") }
    }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1_000) } }
    LaunchedEffect(incomingShare) {
        if (incomingShare != null) {
            val parsed = parseSupportedUrl(incomingShare)
            if (parsed == null) notify("쿠팡 또는 인스타그램 링크를 공유해 주세요.")
            else { sharedUrl = parsed.second; store.onboardingDone = true; route = "vault"; addOpen = true }
            onShareConsumed()
        }
    }
    BackHandler(enabled = route !in listOf("vault", "onboarding") && !addOpen) { home() }
    Scaffold(containerColor = AppColors.Background, contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { if (route in listOf("vault", "history", "my")) BottomTabs(route) { route = it } }
    ) { insets ->
        Box(Modifier.fillMaxSize().padding(insets)) {
            when (route) {
                "onboarding" -> Onboarding { store.onboardingDone = true; route = "vault" }
                "vault" -> VaultScreen(items, now, onAdd = { sharedUrl = ""; addOpen = true }) {
                    selectedId = it.id; route = if (it.ready(now)) "decide" else "detail"
                }
                "history" -> HistoryScreen(items, now) { selectedId = it.id; route = "detail" }
                "my" -> MyScreen(defaultHours, { dialog = "cooldown" }, {
                    val text = buildString {
                        appendLine("하루만 · 나의 소비 기록")
                        items.forEach { appendLine("${dateLabel(it.createdAt)} | ${it.name} | ${money(it.priceWon)} | ${it.platform.label} | ${when(it.decision) { Decision.BUY -> "구매 결정"; Decision.SKIP -> "구매 포기"; else -> "고민 중" }}") }
                    }
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }, "내 기록 내보내기"))
                }, { dialog = "delete" })
                "detail" -> selected?.let { DetailScreen(it, now, ::home, { openOriginal(it) }, { route = "decide" }) }
                "decide" -> selected?.let { item -> DecideScreen(item, ::home, {
                    update(items.map { if (it.id == item.id) extend(it, defaultHours, System.currentTimeMillis()) else it })
                    home(); notify("${defaultHours}시간 더 고민해 보기로 했어요.")
                }) { choice ->
                    val changed = decide(item, choice, System.currentTimeMillis())
                    if (changed.decision == Decision.PENDING) route = "detail"
                    else { update(items.map { if (it.id == item.id) changed else it }); route = "result" }
                } }
                "result" -> selected?.let { ResultScreen(it, items, now, ::home) { openOriginal(it) } }
            }
        }
    }
    if (addOpen) ModalBottomSheet(onDismissRequest = { addOpen = false },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AppColors.Surface, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        AddSheet(sharedUrl, defaultHours) { name, price, platform, url, hours ->
            if (url.isNotBlank() && items.any { it.url == url && it.decision == Decision.PENDING }) notify("이미 보관함에 있는 링크예요.")
            else {
                val time = System.currentTimeMillis()
                update(listOf(WishItem(UUID.randomUUID().toString(), name, price, platform, url, "default", time, time + hours * 3_600_000L)) + items)
                addOpen = false; sharedUrl = ""; route = "vault"
                notify(if (hours == 0) "보관함에 넣었어요." else "${hours}시간 뒤에 다시 생각해 봐요.")
            }
        }
    }
    if (dialog == "delete") AlertDialog(onDismissRequest = { dialog = "" },
        title = { Text("모든 기록을 지울까요?") }, text = { Text("보관한 상품과 결정 기록이 모두 삭제돼요. 삭제한 기록은 되돌릴 수 없어요.") },
        confirmButton = { TextButton(onClick = { update(emptyList()); dialog = ""; notify("모든 기록을 삭제했어요.") }) { Text("모두 삭제", color = AppColors.Red) } },
        dismissButton = { TextButton(onClick = { dialog = "" }) { Text("취소") } })
    if (dialog == "cooldown") AlertDialog(onDismissRequest = { dialog = "" }, title = { Text("기본 다시 보기 시간") },
        text = { Column { listOf(3, 24, 72, 168).forEach { hours ->
            Row(Modifier.fillMaxWidth().clickable { defaultHours = hours; store.defaultCooldownHours = hours; dialog = "" }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = hours == defaultHours, onClick = null)
                Text(cooldownLabel(hours), Modifier.padding(start = 12.dp))
            }
        } } }, confirmButton = { TextButton(onClick = { dialog = "" }) { Text("닫기") } })
}
