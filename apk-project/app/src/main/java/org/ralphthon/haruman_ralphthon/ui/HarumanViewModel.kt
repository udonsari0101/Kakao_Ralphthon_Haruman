package org.ralphthon.haruman_ralphthon.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import org.ralphthon.haruman_ralphthon.data.*
import java.util.UUID

data class ProductDraft(
    val acquisitionId: String = UUID.randomUUID().toString(),
    val editingId: String? = null,
    val mode: String = "link",
    val inputSource: InputSource = InputSource.MANUAL,
    val url: String = "",
    val name: String = "",
    val price: String = "",
    val description: String = "",
    val userMemo: String = "",
    val hours: Int = 24,
    val imageUrl: String? = null,
    val imageHidden: Boolean = false,
    val desire: Desire? = null,
    val snapshot: ProductSnapshot? = null,
    val reviewMessage: String? = null,
    val dirty: Boolean = false,
    val editedFields: Set<String> = emptySet(),
) {
    val priceWon: Long? get() = price.toLongOrNull()
    val priceError: Boolean get() = price.isNotEmpty() &&
        (priceWon == null || priceWon!! < 0 || priceWon!! > 999_999_999_999L)
    val canSave: Boolean get() = name.isNotBlank() && !priceError

    fun encoded(): String = JSONObject().apply {
        put("acquisitionId", acquisitionId); put("editingId", editingId ?: JSONObject.NULL)
        put("mode", mode); put("inputSource", inputSource.name); put("url", url)
        put("name", name); put("price", price); put("description", description)
        put("userMemo", userMemo); put("hours", hours); put("imageUrl", imageUrl ?: JSONObject.NULL)
        put("imageHidden", imageHidden); put("reviewMessage", reviewMessage ?: JSONObject.NULL)
        put("desire", desire?.name ?: JSONObject.NULL); put("snapshot", snapshot?.toJson() ?: JSONObject.NULL)
        put("dirty", dirty)
        put("editedFields", JSONArray(editedFields.toList()))
    }.toString()

    companion object {
        fun decode(value: String?): ProductDraft? = value?.let { runCatching {
            val json = JSONObject(it)
            ProductDraft(
                acquisitionId = json.getString("acquisitionId"),
                editingId = json.optionalText("editingId"),
                mode = json.optString("mode", "manual"),
                inputSource = runCatching { InputSource.valueOf(json.getString("inputSource")) }.getOrDefault(InputSource.MANUAL),
                url = json.optString("url"), name = json.optString("name"), price = json.optString("price"),
                description = json.optString("description"), userMemo = json.optString("userMemo"),
                hours = json.optInt("hours", 24).coerceIn(0, 168), imageUrl = json.optionalText("imageUrl"),
                imageHidden = json.optBoolean("imageHidden"), reviewMessage = json.optionalText("reviewMessage"),
                dirty = json.optBoolean("dirty"),
                editedFields = json.optJSONArray("editedFields")?.let { fields ->
                    List(fields.length()) { index -> fields.getString(index) }.toSet()
                }.orEmpty(),
                desire = runCatching { Desire.valueOf(json.getString("desire")) }.getOrNull(),
                snapshot = json.optJSONObject("snapshot")?.toSnapshot(),
            )
        }.getOrNull() }
    }
}

private fun JSONObject.optionalText(key: String): String? = if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
private fun JSONObject?.stringList(key: String): List<String> = this?.optJSONArray(key)?.let { values ->
    List(values.length()) { values.getString(it) }
}.orEmpty()
private fun ProductDraft.worthKeeping(): Boolean = dirty || editingId != null || url.isNotBlank() ||
    name.isNotBlank() || price.isNotBlank() || description.isNotBlank() || userMemo.isNotBlank() || snapshot != null
private fun ProductDraft.hasManualEdits(): Boolean = editedFields.any { it in setOf("name", "price", "description", "userMemo", "imageHidden") } ||
    (dirty && editedFields.isEmpty() && (name.isNotBlank() || price.isNotBlank() || description.isNotBlank() || userMemo.isNotBlank()))

/** Owns requests across configuration changes; process death restores an editable draft, never replays a POST. */
class HarumanViewModel internal constructor(
    application: Application,
    private val state: SavedStateHandle,
    private val store: LocalStore,
) : AndroidViewModel(application) {
    constructor(application: Application, state: SavedStateHandle) : this(application, state, LocalStore(application))
    private val messages = Channel<String>(Channel.BUFFERED)
    val notices = messages.receiveAsFlow()
    private val workspaceStore = store.uiWorkspaceStore()
    private val workspaceToken = state.get<String>("workspaceToken") ?: "active-workspace"
    private val reviewToken = state.get<String>("reviewToken") ?: "active-review"
    private val restoredWorkspaceText = workspaceStore.read(workspaceToken)
    private val restoredWorkspace = restoredWorkspaceText?.let { runCatching { JSONObject(it) }.getOrNull() }
    private val restoredReviewText = workspaceStore.read(reviewToken)
    private var lastWorkspaceText = restoredWorkspaceText
    private var workspaceWriteFailed = false

    var items by mutableStateOf(store.items()); private set
    var route by mutableStateOf(state.get<String>("route") ?: if (store.onboardingDone) "vault" else "onboarding"); private set
    var selectedId by mutableStateOf(state.get<String>("selectedId")); private set
    var draft by mutableStateOf(ProductDraft.decode(if (restoredWorkspace != null) restoredWorkspace.optionalText("draft") else state["draft"])); private set
    var sheetOpen by mutableStateOf(state.get<Boolean>("sheetOpen") ?: restoredWorkspace?.optBoolean("sheetOpen") ?: false); private set
    var defaultHours by mutableStateOf(store.defaultCooldownHours); private set
    var themePreference by mutableStateOf(store.themePreference); private set
    var mascotBubbles by mutableStateOf(store.mascotBubbles); private set
    var mascotReduceMotion by mutableStateOf(store.mascotReduceMotion); private set
    var processing by mutableStateOf(false); private set
    var requestError by mutableStateOf<String?>(null); private set
    var retryAllowed by mutableStateOf(false); private set
    var sharedChoices by mutableStateOf<List<String>>(state.get<ArrayList<String>>("sharedChoices")?.toList() ?: restoredWorkspace.stringList("sharedChoices")); private set
    var pendingSharedLinks by mutableStateOf<List<String>>(state.get<ArrayList<String>>("pendingSharedLinks")?.toList() ?: restoredWorkspace.stringList("pendingSharedLinks")); private set
    var shareConflict by mutableStateOf(restoredWorkspace?.optBoolean("shareConflict") ?: false); private set
    private var heldDrafts by mutableStateOf(
        (restoredWorkspace.stringList("heldDrafts").mapNotNull { ProductDraft.decode(it) } +
            if (restoredWorkspace == null) listOfNotNull(ProductDraft.decode(state["heldDraft"])) else emptyList())
            .distinctBy { it.acquisitionId },
    )
    var celebrationId by mutableStateOf(state.get<String>("celebrationId")); private set
    var receivedId by mutableStateOf(state.get<String>("receivedId")); private set
    var review by mutableStateOf(ReviewSession.decode(restoredReviewText ?: state["review"])); private set
    private var lastPersistedReview = review
    private var reviewFileDirty = restoredReviewText == null

    var linkService: ProductLinkService = NotConfiguredProductLinkService
    private var activeRequest: Job? = null
    private var requestGeneration = 0L
    private var extracted: ExtractedProduct? = null
    val selected: WishItem? get() = items.find { it.id == selectedId }

    init {
        if (state.get<Boolean>("requestActive") == true) {
            requestError = "불러오기가 중단됐어요. 다시 시도하거나 직접 입력할 수 있어요"
            retryAllowed = true
            sheetOpen = true
            state["requestActive"] = false
        }
        if (route !in setOf("onboarding", "vault", "history", "my", "detail", "decide", "result", "added", "review")) route = "vault"
        if (route == "review" && review == null) route = "vault"
        if (route in setOf("detail", "decide", "result", "added") && selected == null) route = "vault"
        // Migrate old Bundle payloads on first load and remove them only after the file is safe.
        persist()
    }

    private fun workspaceJson(): String = JSONObject().apply {
        put("draft", draft?.encoded() ?: JSONObject.NULL)
        put("heldDrafts", JSONArray(heldDrafts.map { it.encoded() }))
        put("sheetOpen", sheetOpen); put("shareConflict", shareConflict)
        put("sharedChoices", JSONArray(sharedChoices)); put("pendingSharedLinks", JSONArray(pendingSharedLinks))
    }.toString()

    private fun persist(): Boolean {
        // A keystroke must not serialize or write every completed review card again.
        if (reviewFileDirty || review !== lastPersistedReview) {
            if (!workspaceStore.write(reviewToken, review?.encoded() ?: "null")) return workspaceFailure()
            lastPersistedReview = review; reviewFileDirty = false
        }
        val workspace = workspaceJson()
        if (workspace != lastWorkspaceText) {
            if (!workspaceStore.write(workspaceToken, workspace)) return workspaceFailure()
            lastWorkspaceText = workspace
        }
        workspaceWriteFailed = false
        state["route"] = route; state["selectedId"] = selectedId
        state["workspaceToken"] = workspaceToken; state["reviewToken"] = reviewToken
        state["sheetOpen"] = sheetOpen
        state["celebrationId"] = celebrationId
        state["receivedId"] = receivedId
        listOf("draft", "heldDraft", "review", "sharedChoices", "pendingSharedLinks").forEach { state.remove<Any>(it) }
        return true
    }

    private fun workspaceFailure(): Boolean {
        requestError = "작성 중인 내용을 기기에 보관하지 못했어요. 입력은 열어 두었어요. 다시 저장해 주세요"
        if (!workspaceWriteFailed) notifyUser("임시 저장을 마치지 못했어요. 이전에 보관한 내용은 그대로 있어요")
        workspaceWriteFailed = true
        return false
    }

    fun notifyUser(message: String) { messages.trySend(message) }
    fun navigate(destination: String) { route = destination; persist() }
    fun home() { route = "vault"; selectedId = null; persist() }
    fun finishOnboarding() { store.onboardingDone = true; home() }
    fun select(item: WishItem, now: Long, fromHistory: Boolean = false) {
        selectedId = item.id
        route = "detail"
        persist()
    }
    fun openDetail(itemId: String) { selectedId = itemId; route = "detail"; persist() }
    fun openDecision() { route = "decide"; persist() }

    fun setTheme(value: ThemePreference) {
        themePreference = value; store.themePreference = value
    }

    fun updateMascotBubbles(value: Boolean) { mascotBubbles = value; store.mascotBubbles = value }
    fun updateMascotReduceMotion(value: Boolean) { mascotReduceMotion = value; store.mascotReduceMotion = value }
    fun consumeReceived() { receivedId = null; persist() }

    fun updateDefaultHours(value: Int) {
        if (value !in setOf(3, 24, 72, 168)) return
        defaultHours = value; store.defaultCooldownHours = value
        notifyUser("다시 볼 시간을 바꿨어요")
    }

    fun openAdd() {
        if (draft == null) draft = ProductDraft(hours = defaultHours)
        sheetOpen = true; persist()
    }

    /** Closing preserves fields. Any late response loses its generation and cannot write storage. */
    fun closeDraft() {
        cancelRequest()
        sheetOpen = false; persist()
    }

    fun discardDraft() {
        cancelRequest(); draft = null; extracted = null; requestError = null
        sheetOpen = false; persist()
    }

    fun changeDraft(transform: (ProductDraft) -> ProductDraft) {
        val current = draft ?: return
        if (processing) cancelRequest()
        val transformed = transform(current)
        val editedFields = current.editedFields + buildSet {
            if (transformed.name != current.name) add("name")
            if (transformed.price != current.price) add("price")
            if (transformed.description != current.description) add("description")
            if (transformed.userMemo != current.userMemo) add("userMemo")
            if (transformed.imageHidden != current.imageHidden) add("imageHidden")
        }
        val changed = transformed.copy(dirty = true, editedFields = editedFields)
        draft = if (changed.url != current.url) changed.copy(snapshot = null, imageUrl = null, reviewMessage = null) else changed
        requestError = null
        persist()
    }

    fun setMode(mode: String) {
        cancelRequest(); requestError = null
        val current = draft ?: ProductDraft(hours = defaultHours)
        draft = current.copy(mode = mode)
        sheetOpen = true; persist()
    }

    fun edit(item: WishItem) {
        cancelRequest(); extracted = null; requestError = null
        if (draft?.editingId == item.id) {
            sheetOpen = true; persist(); return
        }
        val previousEdit = heldDrafts.firstOrNull { it.editingId == item.id }
        holdCurrentDraft()
        heldDrafts = heldDrafts.filterNot { it.acquisitionId == previousEdit?.acquisitionId }
        draft = previousEdit ?: ProductDraft(
            editingId = item.id, mode = "manual", inputSource = item.inputSource,
            url = item.url, name = item.name, price = item.priceWon?.toString().orEmpty(),
            description = item.description, userMemo = item.userMemo, hours = defaultHours,
            imageUrl = item.imageUrl, imageHidden = item.imageHidden,
            desire = item.desire, snapshot = item.productSnapshot,
        )
        sheetOpen = true; persist()
    }

    fun receiveShare(text: String) {
        val links = extractSupportedLinks(text).distinct()
        if (links.isEmpty()) {
            notifyUser("쿠팡 상품 링크를 공유해 주세요")
            openAdd()
            return
        }
        store.onboardingDone = true
        if (processing && links.size == 1 && draft?.url == links.first()) return
        val current = draft
        if (current != null && (current.worthKeeping() || processing)) {
            pendingSharedLinks = links; shareConflict = true; persist()
        } else acceptLinks(links)
    }

    /** Keep a complete backup of the previous draft when switching to a newly shared link. */
    fun acceptPendingShare() {
        if (pendingSharedLinks.isEmpty()) return
        holdCurrentDraft()
        shareConflict = false
        val links = pendingSharedLinks
        pendingSharedLinks = emptyList()
        acceptLinks(links)
    }

    fun keepCurrentDraft() {
        shareConflict = false; sheetOpen = true
        notifyUser("작성 중인 내용을 이어서 입력해 주세요. 공유한 링크도 보관했어요")
        persist()
    }

    fun restoreHeldDraft() {
        val held = heldDrafts.firstOrNull() ?: return
        heldDrafts = heldDrafts.drop(1)
        holdCurrentDraft()
        cancelRequest(); draft = held; sheetOpen = true; extracted = null; requestError = null; persist()
    }

    fun hasHeldDraft(): Boolean = heldDrafts.isNotEmpty()

    private fun holdCurrentDraft() {
        draft?.takeIf { it.worthKeeping() }?.let { current ->
            heldDrafts = heldDrafts.filterNot { it.acquisitionId == current.acquisitionId } + current
        }
    }

    private fun acceptLinks(links: List<String>) {
        if (links.isEmpty()) return
        cancelRequest(); requestError = null; route = "vault"
        if (links.size > 1) {
            sharedChoices = links; sheetOpen = false; persist()
        } else startSharedLink(links.single())
    }

    fun chooseSharedLink(url: String) {
        if (url !in sharedChoices) return
        sharedChoices = emptyList(); startSharedLink(url)
    }

    fun dismissChoices() { sharedChoices = emptyList(); persist() }

    private fun startSharedLink(url: String) {
        draft = ProductDraft(mode = "link", inputSource = InputSource.LINK_SHARE, url = url, hours = defaultHours)
        sheetOpen = true; extracted = null; persist(); requestProduct()
    }

    fun requestProduct() {
        val current = draft ?: return
        if (processing || current.editingId != null) return
        val links = extractSupportedLinks(current.url).distinct()
        if (links.size != 1 || current.url.length > 8192) {
            requestError = if (links.size > 1) "상품 링크를 한 개씩 넣어 주세요" else "쿠팡 상품의 HTTPS 링크를 확인해 주세요"
            retryAllowed = false; return
        }
        val url = links.single()
        val duplicate = items.find { it.decision == Decision.PENDING && it.url == url }
        if (duplicate != null) {
            holdCurrentDraft()
            selectedId = duplicate.id; route = "detail"; sheetOpen = false; draft = null
            notifyUser("이미 보관함에 있어요"); persist(); return
        }
        cancelRequest()
        val generation = requestGeneration
        val captured = current.copy(url = url, inputSource = if (current.inputSource == InputSource.LINK_SHARE) InputSource.LINK_SHARE else InputSource.LINK_INPUT)
        draft = captured; requestError = null; retryAllowed = false
        if (linkService === NotConfiguredProductLinkService) {
            requestError = "링크로 불러오는 기능은 준비 중이에요. 지금은 직접 입력해 저장할 수 있어요"
            persist(); return
        }
        processing = true; state["requestActive"] = true; persist()
        activeRequest = viewModelScope.launch {
            try {
                val result = linkService.extract(url)
                if (generation != requestGeneration || draft?.acquisitionId != captured.acquisitionId || !sheetOpen) return@launch
                processing = false; state["requestActive"] = false
                when (result) {
                    ProductLinkResult.NotConfigured -> {
                        requestError = "링크로 불러오는 기능은 준비 중이에요. 직접 입력해 저장할 수 있어요"
                        retryAllowed = false
                    }
                    is ProductLinkResult.Complete -> {
                        extracted = result.product
                        draft = captured.fromExtracted(result.product)
                        if (captured.hasManualEdits()) {
                            draft = draft?.copy(reviewMessage = "직접 고친 내용은 그대로 두었어요. 새 상품 정보를 확인하고 저장해 주세요. " + result.product.proposal())
                        } else saveDraft(automatic = true)
                    }
                    is ProductLinkResult.NeedsReview -> {
                        extracted = result.product
                        draft = captured.fromExtracted(result.product).copy(
                            mode = "manual", reviewMessage = (result.reasons.distinct().joinToString(" ") +
                                if (captured.hasManualEdits()) " 직접 고친 내용은 그대로 두었어요. ${result.product.proposal()}" else "").take(500),
                        )
                    }
                    is ProductLinkResult.Failure -> { requestError = result.message; retryAllowed = result.retryable }
                }
                persist()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (generation == requestGeneration) {
                    processing = false; state["requestActive"] = false
                    requestError = "상품 정보를 가져오지 못했어요. 다시 시도하거나 직접 입력할 수 있어요"
                    retryAllowed = true
                }
            }
        }
    }

    fun cancelRequest() {
        requestGeneration++
        activeRequest?.cancel(); activeRequest = null
        processing = false; state["requestActive"] = false
    }

    fun saveDraft(automatic: Boolean = false) {
        val current = draft ?: return
        if (automatic && current.hasManualEdits()) return
        if (!current.canSave) return
        if (current.url.isNotBlank() && extractSupportedLinks(current.url).none { it == current.url } && current.editingId == null) {
            requestError = "상품 링크를 확인하거나 링크 없이 저장해 주세요"; return
        }
        cancelRequest()
        if (current.editingId != null) {
            val existing = items.find { it.id == current.editingId } ?: run {
                requestError = "이 상품은 삭제됐어요. 작성한 내용은 아직 열어 두었어요"; return
            }
            val updated = existing.copy(name = current.name.trim(), priceWon = current.priceWon,
                description = current.description.trim(), userMemo = current.userMemo.trim(), imageHidden = current.imageHidden, desire = current.desire)
            val next = items.map { if (it.id == existing.id) updated else it }
            if (!store.saveItems(next)) { requestError = "수정한 정보를 아직 저장하지 못했어요. 다시 시도해 주세요"; return }
            items = next; selectedId = existing.id; route = "detail"
            notifyUser("수정한 정보를 저장했어요")
        } else {
            val duplicate = items.find { it.decision == Decision.PENDING && current.url.isNotBlank() && it.url == current.url }
            if (duplicate != null) {
                holdCurrentDraft()
                selectedId = duplicate.id; route = "detail"; sheetOpen = false; draft = null
                extracted = null; requestError = null
                notifyUser("이미 보관함에 있어요. 작성한 내용도 따로 보관했어요"); persist(); return
            } else {
                val now = System.currentTimeMillis()
                val snapshot = current.snapshot
                val item = WishItem(
                    id = UUID.randomUUID().toString(), name = current.name.trim(), priceWon = current.priceWon,
                    platform = if (current.url.isBlank()) Platform.MANUAL else Platform.COUPANG,
                    url = current.url, artwork = "default", createdAt = now,
                    cooldownUntil = now + current.hours * 3_600_000L,
                    description = current.description.trim(), userMemo = current.userMemo.trim(),
                    imageUrl = current.imageUrl, imageHidden = current.imageHidden,
                    inputSource = if (current.url.isBlank()) InputSource.MANUAL else current.inputSource,
                    acquisitionId = current.acquisitionId, productSnapshot = snapshot, desire = current.desire,
                    productId = snapshot?.productId, itemId = snapshot?.itemId, vendorItemId = snapshot?.vendorItemId,
                )
                when (val saved = store.saveNewItemOnce(item)) {
                    is SaveItemResult.Saved -> { items = store.items(); selectedId = saved.item.id; route = "added"; receivedId = saved.item.id }
                    is SaveItemResult.AlreadySaved -> { items = store.items(); selectedId = saved.item.id; route = "detail"; notifyUser("이미 보관함에 있어요") }
                    SaveItemResult.Failed -> { requestError = "아직 보관함에 저장하지 못했어요. 입력한 정보는 그대로 있어요"; persist(); return }
                }
            }
        }
        heldDrafts = heldDrafts.filterNot { it.acquisitionId == current.acquisitionId ||
            (current.editingId != null && it.editingId == current.editingId) }
        sheetOpen = false; draft = null; extracted = null; requestError = null
        persist()
    }

    fun decideSelected(choice: Decision) {
        val current = selected ?: return
        val changed = decide(current, choice, System.currentTimeMillis())
        if (changed == current) { route = "detail"; persist(); return }
        if (!store.saveItems(items.map { if (it.id == current.id) changed else it })) {
            notifyUser("아직 결정을 저장하지 못했어요. 다시 시도해 주세요"); return
        }
        items = store.items()
        celebrationId = if (changed.decision == Decision.SKIP && !changed.isSample) changed.id else null
        route = "result"; persist()
    }

    fun consumeCelebration() { celebrationId = null; persist() }

    fun startReview() {
        val now = System.currentTimeMillis()
        val ready = items.filter { it.ready(now) }.sortedBy { it.createdAt }
        if (ready.isEmpty()) { notifyUser("아직은 조금 더 기다릴 시간이에요"); return }
        cancelRequest(); sheetOpen = false
        review = ReviewSession(ids = ready.map { it.id })
        route = "review"; persist()
    }

    fun currentReviewItem(): WishItem? = review?.let { session -> session.ids.getOrNull(session.index)?.let { id -> items.find { it.id == id } } }

    fun reviewAction(action: ReviewAction): Boolean {
        val session = review ?: return false
        val item = currentReviewItem() ?: return false
        val now = System.currentTimeMillis()
        if (!item.ready(now)) {
            notifyUser("이 상품의 상태가 바뀌었어요")
            review = session.copy(index = session.index + 1); persist(); return false
        }
        val changed = when (action) {
            ReviewAction.SKIP -> decide(item, Decision.SKIP, now)
            ReviewAction.BUY -> decide(item, Decision.BUY, now)
            ReviewAction.LATER -> extend(item, defaultHours, now)
        }
        if (!store.saveItems(items.map { if (it.id == item.id) changed else it })) {
            notifyUser("아직 저장하지 못했어요. 같은 상품에서 다시 시도해 주세요"); return false
        }
        items = store.items()
        review = session.copy(index = session.index + 1,
            results = session.results + ReviewRecord(item, changed, action), undoUntil = now + 4_000)
        persist(); return true
    }

    fun undoReview() {
        val session = review ?: return
        val record = session.results.lastOrNull() ?: return
        if (System.currentTimeMillis() > session.undoUntil) return
        if (items.find { it.id == record.after.id } != record.after) {
            notifyUser("상품 정보가 바뀌어 되돌릴 수 없어요"); return
        }
        if (store.saveItems(items.map { if (it.id == record.before.id) record.before else it })) {
            items = store.items()
            review = session.copy(index = (session.index - 1).coerceAtLeast(0), results = session.results.dropLast(1), undoUntil = 0)
            persist(); notifyUser("다시 꺼냈어요. 천천히 골라 주세요")
        } else notifyUser("아직 되돌리지 못했어요. 다시 시도해 주세요")
    }

    fun consumeReviewCelebration() { review = review?.copy(celebrationConsumed = true); persist() }

    fun extendSelected() {
        val current = selected ?: return
        val changed = extend(current, defaultHours, System.currentTimeMillis())
        if (store.saveItems(items.map { if (it.id == current.id) changed else it })) {
            items = store.items(); home(); notifyUser("${defaultHours}시간 더 생각해 보기로 했어요")
        } else notifyUser("다시 볼 시간을 저장하지 못했어요")
    }

    fun deleteAll() {
        cancelRequest()
        val previousWorkspace = workspaceJson()
        val previousReview = review?.encoded() ?: "null"
        val emptyWorkspace = JSONObject().apply {
            put("draft", JSONObject.NULL); put("heldDrafts", JSONArray())
            put("sheetOpen", false); put("shareConflict", false)
            put("sharedChoices", JSONArray()); put("pendingSharedLinks", JSONArray())
        }.toString()
        // Clear persisted drafts before reporting success; a restart cannot resurrect them.
        if (!workspaceStore.write(reviewToken, "null")) { workspaceFailure(); return }
        if (!workspaceStore.write(workspaceToken, emptyWorkspace)) {
            workspaceStore.write(reviewToken, previousReview)
            workspaceFailure(); return
        }
        if (!store.saveItems(emptyList())) {
            val restoredReview = workspaceStore.write(reviewToken, previousReview)
            val restoredDrafts = workspaceStore.write(workspaceToken, previousWorkspace)
            if (!restoredReview || !restoredDrafts) workspaceFailure()
            notifyUser("기록을 지우지 못했어요. 작성한 내용은 열어 두었어요. 다시 시도해 주세요")
            return
        }
        items = emptyList(); draft = null; heldDrafts = emptyList(); sheetOpen = false
        review = null; lastPersistedReview = null; reviewFileDirty = false
        sharedChoices = emptyList(); pendingSharedLinks = emptyList(); shareConflict = false
        celebrationId = null; receivedId = null; selectedId = null
        extracted = null; requestError = null; retryAllowed = false
        lastWorkspaceText = emptyWorkspace
        route = "vault"; persist(); notifyUser("모든 기록을 삭제했어요")
    }
}

private fun ProductDraft.fromExtracted(product: ExtractedProduct): ProductDraft {
    // Old drafts did not record individual fields; preserve their manual content conservatively.
    val preserveLegacy = hasManualEdits() && editedFields.isEmpty()
    return copy(
        name = if ("name" in editedFields || preserveLegacy) name else product.name,
        price = if ("price" in editedFields || preserveLegacy) price else product.priceWon?.toString().orEmpty(),
        imageUrl = product.imageUrl, mode = "manual", snapshot = product.snapshot,
    )
}

private fun ExtractedProduct.proposal(): String = "새로 받은 정보: ${name.take(160)} · ${priceWon?.let { "${it}원" } ?: "가격 미정"}"
