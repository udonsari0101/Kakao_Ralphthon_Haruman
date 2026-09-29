package org.ralphthon.haruman_ralphthon.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.ralphthon.haruman_ralphthon.data.*
import java.io.File
import java.util.UUID

/** Real Android preferences under a unique test-only name. Never opens the user's data store. */
@RunWith(AndroidJUnit4::class)
class WorkflowStateTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val prefsName = "workflow-test-${UUID.randomUUID()}"
    private val store = LocalStore(app, prefsName)
    private val models = mutableListOf<HarumanViewModel>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private fun model(state: SavedStateHandle = SavedStateHandle()): HarumanViewModel {
        lateinit var result: HarumanViewModel
        main { result = HarumanViewModel(app, state, store); models += result }
        return result
    }
    private fun awaitCondition(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 3_000_000_000L
        while (System.nanoTime() < deadline) {
            var okay = false
            main { okay = condition() }
            if (okay) return
            Thread.sleep(20)
        }
        fail("Workflow condition did not complete")
    }
    @After fun cleanOwnFixture() {
        main { models.forEach { it.viewModelScope.cancel() } }
        app.getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit().clear().commit()
        // LocalStore derives this directory from this test's unique preferences name.
        store.uiWorkspaceDirectory.deleteRecursively()
    }

    private class DeferredService(private val ignoreCancellation: Boolean = false) : ProductLinkService {
        val reply = CompletableDeferred<ProductLinkResult>()
        var calls = 0
        override suspend fun extract(url: String): ProductLinkResult {
            calls++
            return if (ignoreCancellation) withContext(NonCancellable) { reply.await() } else reply.await()
        }
    }
    private fun product(price: Long? = 12000L) = ExtractedProduct(
        originalUrl = URL, name = "Fixture product", priceWon = price,
        imageUrl = "https://images.example.test/item.png",
        snapshot = ProductSnapshot(name = "Original API name", priceWon = price, currency = "KRW",
            priceType = "coupon", priceNote = "Coupon condition", originalUrl = URL, productId = "9047670772"),
    )

    @Test fun addAfterClosingEditCreatesNewItemAndKeepsUnsavedCorrectionSeparate() {
        val now = System.currentTimeMillis()
        assertTrue(store.saveItems(listOf(WishItem("original", "원래 상품", 1000L, Platform.MANUAL, "", "default", now, now))))
        val state = SavedStateHandle()
        val original = model(state)
        main {
            original.edit(original.items.single())
            original.changeDraft { it.copy(name = "아직 저장하지 않은 수정") }
            original.closeDraft()
        }
        val m = model(state)
        main {
            m.navigate("vault"); m.openAdd()
            assertNull(m.draft?.editingId)
            assertEquals("", m.draft?.name)
            assertTrue(m.hasHeldDraft())
            m.setMode("manual")
            m.changeDraft { it.copy(name = "새로 추가한 상품", price = "2000") }
            m.saveDraft()
            assertEquals(2, m.items.size)
            assertEquals("원래 상품", m.items.single { it.id == "original" }.name)
            m.restoreHeldDraft()
            assertEquals("original", m.draft?.editingId)
            assertEquals("아직 저장하지 않은 수정", m.draft?.name)
        }
    }

    @Test fun staleEditorPreservesAProductAddedByAnotherModel() {
        val now = System.currentTimeMillis()
        val first = WishItem("first", "첫 상품", 1000L, Platform.MANUAL, "", "default", now, now)
        assertTrue(store.saveItems(listOf(first)))
        val editor = model(); val creator = model()
        main {
            editor.edit(first); editor.changeDraft { it.copy(name = "직접 고친 첫 상품") }
            creator.openAdd(); creator.setMode("manual")
            creator.changeDraft { it.copy(name = "다른 화면에서 추가한 상품", price = "2000") }
            creator.saveDraft()
            val added = creator.items.single { it.id != first.id }
            editor.saveDraft()
            assertNull(editor.draft)
            assertEquals(2, store.items().size)
            assertEquals(added, store.items().single { it.id == added.id })
            assertEquals("직접 고친 첫 상품", store.items().single { it.id == first.id }.name)
            assertEquals(store.items(), editor.items)
        }
    }

    @Test fun staleEditRequiresReviewAndRetryPreservesLatestDecisionAndUntouchedFields() {
        val now = System.currentTimeMillis()
        val first = WishItem("first", "첫 상품", 1000L, Platform.MANUAL, "", "default", now, now)
        assertTrue(store.saveItems(listOf(first)))
        val editor = model(); val writer = model()
        main {
            editor.edit(first); editor.changeDraft { it.copy(name = "내가 고친 이름", desire = Desire.LIKE) }
            writer.edit(first)
            writer.changeDraft { it.copy(price = "3000", userMemo = "다른 화면에서 적은 메모") }
            writer.saveDraft(); writer.decideSelected(Decision.BUY)
            val decided = store.items().single()
            assertEquals(Decision.BUY, decided.decision)
            editor.saveDraft()
            assertEquals(decided, store.items().single())
            assertTrue(editor.sheetOpen)
            assertTrue(editor.requestError.orEmpty().contains("다른 화면"))
            assertEquals("내가 고친 이름", editor.draft?.name)
            assertEquals(Desire.LIKE, editor.draft?.desire)
            assertEquals("3000", editor.draft?.price)
            assertEquals("다른 화면에서 적은 메모", editor.draft?.userMemo)
            editor.saveDraft() // An explicit retry applies only the reviewed draft onto fresh state.
            val saved = store.items().single()
            assertEquals("내가 고친 이름", saved.name)
            assertEquals(Desire.LIKE, saved.desire)
            assertEquals(3000L, saved.priceWon)
            assertEquals("다른 화면에서 적은 메모", saved.userMemo)
            assertEquals(Decision.BUY, saved.decision)
            assertEquals(decided.decidedAt, saved.decidedAt)
            assertEquals(3000L, saved.decisionPriceWon)
        }
    }

    @Test fun staleEditNeverRecreatesAProductDeletedByAnotherModel() {
        val now = System.currentTimeMillis()
        val first = WishItem("first", "첫 상품", 1000L, Platform.MANUAL, "", "default", now, now)
        assertTrue(store.saveItems(listOf(first)))
        val editor = model(); val deleter = model()
        main {
            editor.edit(first); editor.changeDraft { it.copy(name = "남아 있는 입력") }
            deleter.deleteAll()
            editor.saveDraft()
            assertTrue(store.items().isEmpty())
            assertTrue(editor.items.isEmpty())
            assertEquals("남아 있는 입력", editor.draft?.name)
            assertTrue(editor.requestError.orEmpty().contains("삭제"))
            editor.saveDraft()
            assertTrue(store.items().isEmpty())
        }
    }

    @Test fun heldEditKeepsItsOriginalRevisionAcrossRecreationAndExplicitConflictRetry() {
        val now = System.currentTimeMillis()
        val first = WishItem("first", "원래 이름", 1000L, Platform.MANUAL, "", "default", now, now)
        assertTrue(store.saveItems(listOf(first)))
        val editorState = SavedStateHandle(mapOf("workspaceToken" to "held-editor", "reviewToken" to "held-editor-review"))
        val editor = model(editorState)
        val writer = model(SavedStateHandle(mapOf("workspaceToken" to "held-writer", "reviewToken" to "held-writer-review")))
        main {
            editor.edit(first); editor.changeDraft { it.copy(name = "내가 작성한 이름") }
            editor.closeDraft(); editor.openAdd(); editor.closeDraft()
            writer.edit(first); writer.changeDraft { it.copy(price = "7000", userMemo = "새로 저장된 메모") }
            writer.saveDraft()
        }
        val latest = store.items().single()
        val restored = model(editorState)
        main {
            assertEquals(latest, restored.items.single())
            restored.restoreHeldDraft()
            assertEquals(first, restored.draft?.editBaseline)
            restored.saveDraft()
            assertEquals(latest, store.items().single())
            assertTrue(restored.sheetOpen)
            assertTrue(restored.requestError.orEmpty().contains("다른 화면"))
            assertEquals("내가 작성한 이름", restored.draft?.name)
            assertEquals("7000", restored.draft?.price)
            assertEquals("새로 저장된 메모", restored.draft?.userMemo)
            assertEquals(latest, restored.draft?.editBaseline)
        }
        // The reviewed baseline also survives another recreation before the explicit retry.
        val reviewed = model(editorState)
        main {
            assertEquals(latest, reviewed.draft?.editBaseline)
            reviewed.saveDraft()
            assertNull(reviewed.draft)
            assertEquals(latest.copy(name = "내가 작성한 이름"), store.items().single())
        }
    }

    @Test fun savingAnotherProductCannotMakeAHeldSameFieldConflictDisappear() {
        val now = System.currentTimeMillis()
        val first = WishItem("first", "원래 이름", 1000L, Platform.MANUAL, "", "default", now, now)
        assertTrue(store.saveItems(listOf(first)))
        val editor = model(); val writer = model()
        main {
            editor.edit(first); editor.changeDraft { it.copy(name = "내가 고친 이름") }; editor.closeDraft()
            editor.openAdd(); editor.setMode("manual"); editor.changeDraft { it.copy(name = "두 번째 상품") }
            writer.edit(first)
            writer.changeDraft { it.copy(name = "다른 화면에서 고친 이름", price = "9000", userMemo = "최신 메모") }
            writer.saveDraft(); writer.decideSelected(Decision.BUY)
            val latest = store.items().single()
            editor.saveDraft() // New item refreshes editor.items, while the held draft is still old.
            val second = editor.items.single { it.id != first.id }
            assertEquals(latest, editor.items.single { it.id == first.id })
            editor.restoreHeldDraft()
            assertEquals(first, editor.draft?.editBaseline)
            editor.saveDraft()
            assertEquals(latest, store.items().single { it.id == first.id })
            assertEquals(second, store.items().single { it.id == second.id })
            assertTrue(editor.requestError.orEmpty().contains("다른 화면"))
            assertEquals("내가 고친 이름", editor.draft?.name)
            assertEquals("9000", editor.draft?.price)
            assertEquals("최신 메모", editor.draft?.userMemo)
            editor.saveDraft()
            assertEquals(latest.copy(name = "내가 고친 이름"), store.items().single { it.id == first.id })
            assertEquals(second, store.items().single { it.id == second.id })
        }
    }

    @Test fun legacyEditWithoutBaselineRequiresReviewAndRetainsManualInput() {
        val now = System.currentTimeMillis()
        val latest = WishItem("first", "최신 이름", 7000L, Platform.MANUAL, "", "default", now, now,
            decision = Decision.BUY, decidedAt = now, decisionPriceWon = 7000L, userMemo = "최신 메모")
        assertTrue(store.saveItems(listOf(latest)))
        val oldDraft = ProductDraft(editingId = latest.id, mode = "manual", name = "미저장 이름", price = "1000",
            userMemo = "옛 메모", dirty = true, editedFields = setOf("name"))
        val legacyJson = org.json.JSONObject(oldDraft.encoded()).apply { remove("editBaseline") }.toString()
        assertEquals(oldDraft, ProductDraft.decode(legacyJson))
        val editor = model(SavedStateHandle(mapOf("draft" to legacyJson, "sheetOpen" to true)))
        main {
            editor.saveDraft()
            assertEquals(latest, store.items().single())
            assertTrue(editor.sheetOpen)
            assertTrue(editor.requestError.orEmpty().contains("이전에 작성한"))
            assertEquals("미저장 이름", editor.draft?.name)
            assertEquals("7000", editor.draft?.price)
            assertEquals("최신 메모", editor.draft?.userMemo)
            assertEquals(latest, editor.draft?.editBaseline)
            editor.saveDraft()
            assertEquals(latest.copy(name = "미저장 이름"), store.items().single())
        }
    }

    @Test fun staleDecisionAndExtensionCannotReplaceAnotherModelsPurchase() {
        val now = System.currentTimeMillis()
        val first = WishItem("first", "첫 상품", 1000L, Platform.MANUAL, "", "default", now, now)
        assertTrue(store.saveItems(listOf(first)))
        val decision = model(); val extension = model(); val writer = model()
        main {
            decision.select(first, now); extension.select(first, now); writer.select(first, now)
            writer.decideSelected(Decision.BUY)
            val purchased = store.items().single()
            decision.decideSelected(Decision.SKIP)
            assertEquals(purchased, store.items().single())
            assertEquals("detail", decision.route)
            assertNull(decision.celebrationId)
            extension.extendSelected()
            assertEquals(purchased, store.items().single())
            assertEquals("detail", extension.route)
            assertEquals(purchased, extension.selected)
        }
    }

    @Test fun reviewAndUndoPreserveProductsAddedAfterTheSessionStarted() {
        val now = System.currentTimeMillis()
        val first = WishItem("first", "첫 상품", 1000L, Platform.MANUAL, "", "default", now, now)
        assertTrue(store.saveItems(listOf(first)))
        val reviewer = model(); val creator = model()
        main {
            reviewer.startReview()
            creator.openAdd(); creator.setMode("manual")
            creator.changeDraft { it.copy(name = "나중에 추가한 상품") }; creator.saveDraft()
            val added = creator.items.single { it.id != first.id }
            assertTrue(reviewer.reviewAction(ReviewAction.SKIP))
            assertEquals(added, store.items().single { it.id == added.id })
            reviewer.undoReview()
            assertEquals(2, store.items().size)
            assertEquals(first, store.items().single { it.id == first.id })
            assertEquals(added, store.items().single { it.id == added.id })
            assertEquals(0, reviewer.review?.index)
        }
    }

    @Test fun staleReviewSkipsChangedCardWithoutReplacingItsDecision() {
        val now = System.currentTimeMillis()
        val first = WishItem("first", "첫 상품", 1000L, Platform.MANUAL, "", "default", now, now)
        assertTrue(store.saveItems(listOf(first)))
        val reviewer = model(); val writer = model()
        main {
            reviewer.startReview(); writer.select(first, now); writer.decideSelected(Decision.BUY)
            val purchased = store.items().single()
            assertFalse(reviewer.reviewAction(ReviewAction.SKIP))
            assertEquals(purchased, store.items().single())
            assertTrue(reviewer.review!!.complete)
            assertTrue(reviewer.review!!.results.isEmpty())
        }
    }

    @Test fun undoChecksLatestStoredProductInsteadOfItsModelsOldSnapshot() {
        val now = System.currentTimeMillis()
        val first = WishItem("first", "첫 상품", 1000L, Platform.MANUAL, "", "default", now, now)
        assertTrue(store.saveItems(listOf(first)))
        val reviewer = model()
        main { reviewer.startReview(); assertTrue(reviewer.reviewAction(ReviewAction.SKIP)) }
        val editor = model()
        main {
            editor.edit(editor.items.single()); editor.changeDraft { it.copy(userMemo = "결정 뒤 새로 적은 메모") }
            editor.saveDraft()
            val corrected = store.items().single()
            reviewer.undoReview()
            assertEquals(corrected, store.items().single())
            assertEquals(Decision.SKIP, corrected.decision)
            assertEquals(1, reviewer.review!!.index)
            assertEquals(0L, reviewer.review!!.undoUntil)
            assertEquals(corrected, reviewer.items.single())
        }
    }

    @Test fun serverRetryWindowBlocksImmediateRepeatAndKeepsManualDraft() {
        val m = model()
        var calls = 0
        main {
            m.linkService = object : ProductLinkService {
                override suspend fun extract(url: String): ProductLinkResult {
                    calls++
                    return ProductLinkResult.Failure(ProductFailureCode.BUSY, "잠시 쉬고 있어요", true, 30L)
                }
            }
            m.receiveShare(URL)
        }
        awaitCondition { !m.processing && calls == 1 }
        main {
            repeat(3) { m.requestProduct() }
            assertEquals(1, calls)
            assertEquals(URL, m.draft?.url)
            assertTrue(m.items.isEmpty())
            assertTrue(m.requestError.orEmpty().contains("초 뒤"))
            m.changeDraft { it.copy(name = "내가 확인한 상품", price = "12500", mode = "manual") }
            m.saveDraft()
            assertEquals(12500L, m.items.single().priceWon)
        }
    }

    @Test fun generalProductLinkKeepsTheReturnedSourceWithoutUsingLegacyPlatform() {
        val m = model()
        val url = "https://shop.example.com/products/42?color=red"
        main {
            m.linkService = object : ProductLinkService {
                override suspend fun extract(url: String) = ProductLinkResult.Complete(
                    product().copy(originalUrl = url, snapshot = ProductSnapshot(source = "example-shop", originalUrl = url)),
                )
            }
            m.receiveShare(url)
        }
        awaitCondition { m.items.size == 1 }
        main {
            assertEquals(Platform.PRODUCT_LINK, m.items.single().platform)
            assertEquals("example-shop", m.items.single().productSnapshot?.source)
            assertEquals(url, m.items.single().url)
        }
    }

    @Test fun unconfiguredShareKeepsUrlAndNeverCreatesAnInventedProduct() {
        val m = model()
        main { m.receiveShare("A product $URL") }
        main {
            assertEquals(URL, m.draft?.url)
            assertEquals(InputSource.LINK_SHARE, m.draft?.inputSource)
            assertFalse(m.processing)
            assertTrue(m.sheetOpen)
            assertNotNull(m.requestError)
            assertTrue(m.items.isEmpty())
        }
    }

    @Test fun successfulResponseStoresExactlyOnceEvenWhenRequestedTwice() {
        val m = model(); val service = DeferredService()
        main { m.linkService = service; m.receiveShare(URL); m.requestProduct() }
        awaitCondition { service.calls == 1 }
        service.reply.complete(ProductLinkResult.Complete(product()))
        awaitCondition { m.items.size == 1 }
        main {
            m.saveDraft(); m.requestProduct()
            assertEquals(1, m.items.size)
            assertEquals("added", m.route)
            assertFalse(m.sheetOpen)
            assertEquals(InputSource.LINK_SHARE, m.items.single().inputSource)
            assertEquals("coupon", m.items.single().productSnapshot?.priceType)
        }
        assertEquals(1, store.items().size)
    }

    @Test fun partialResponseRemainsEditableAndOriginalMetadataSurvivesRecreation() {
        val saved = SavedStateHandle(); val m = model(saved); val service = DeferredService()
        main { m.linkService = service; m.receiveShare(URL) }
        awaitCondition { service.calls == 1 }
        service.reply.complete(ProductLinkResult.NeedsReview(product(null), listOf("가격을 확인해 주세요")))
        awaitCondition { m.draft?.reviewMessage != null }
        main { m.changeDraft { it.copy(name = "Edited name", price = "0", description = "My description") } }
        val restored = model(saved)
        main {
            assertTrue(restored.items.isEmpty())
            assertEquals("Edited name", restored.draft?.name)
            assertEquals("Original API name", restored.draft?.snapshot?.name)
            restored.saveDraft()
            assertEquals(0L, restored.items.single().priceWon)
            assertEquals("My description", restored.items.single().description)
            assertEquals("Original API name", restored.items.single().productSnapshot?.name)
        }
    }

    @Test fun lateResponseAfterCloseCannotStoreOrReplaceTheDraft() {
        val m = model(); val service = DeferredService(ignoreCancellation = true)
        main { m.linkService = service; m.receiveShare(URL) }
        awaitCondition { service.calls == 1 }
        main { m.closeDraft() }
        service.reply.complete(ProductLinkResult.Complete(product()))
        instrumentation.waitForIdleSync()
        main {
            assertTrue(m.items.isEmpty())
            assertFalse(m.sheetOpen)
            assertEquals(URL, m.draft?.url)
            assertEquals("", m.draft?.name)
        }
    }

    @Test fun manualSaveWinsOverAnOlderInFlightResponse() {
        val m = model(); val service = DeferredService(ignoreCancellation = true)
        main { m.linkService = service; m.receiveShare(URL) }
        awaitCondition { service.calls == 1 }
        main {
            m.setMode("manual")
            m.changeDraft { it.copy(name = "Manually verified", price = "") }
            m.saveDraft()
        }
        service.reply.complete(ProductLinkResult.Complete(product()))
        instrumentation.waitForIdleSync()
        main { assertEquals("Manually verified", m.items.single().name); assertNull(m.items.single().priceWon) }
    }

    @Test fun replacementSharePreservesBothOriginalDraftAndNewLink() {
        val m = model()
        main {
            m.openAdd(); m.setMode("manual")
            m.changeDraft { it.copy(name = "My draft", userMemo = "Keep my memo") }
            m.receiveShare(URL)
            assertTrue(m.shareConflict)
            assertEquals("My draft", m.draft?.name)
            m.acceptPendingShare()
            assertEquals(URL, m.draft?.url)
            assertTrue(m.hasHeldDraft())
            m.restoreHeldDraft()
            assertEquals("My draft", m.draft?.name)
            assertEquals("Keep my memo", m.draft?.userMemo)
            assertTrue(m.hasHeldDraft())
        }
    }

    @Test fun processDeathRecoversDraftAndDoesNotReplayRequest() {
        val draft = ProductDraft(url = URL, name = "Still here", inputSource = InputSource.LINK_SHARE)
        val m = model(SavedStateHandle(mapOf("requestActive" to true, "draft" to draft.encoded(), "sheetOpen" to true)))
        main {
            assertFalse(m.processing); assertTrue(m.retryAllowed)
            assertEquals("Still here", m.draft?.name)
            assertTrue(m.items.isEmpty()); assertNotNull(m.requestError)
        }
    }

    @Test fun editingAnOldDecisionDoesNotChangeRecordedSavings() {
        val now = System.currentTimeMillis()
        val original = WishItem("old", "Old item", 1000L, Platform.MANUAL, "", "default", now - 1000, now - 500)
        assertTrue(store.saveItems(listOf(decide(original, Decision.SKIP, now))))
        val m = model()
        main {
            m.edit(m.items.single()); m.changeDraft { it.copy(name = "Changed", price = "7000", userMemo = "Edited later") }; m.saveDraft()
            assertEquals(7000L, m.items.single().priceWon)
            assertEquals(1000L, m.items.single().decisionPriceWon)
            assertEquals(1000L, skippedTotal(m.items))
        }
    }

    @Test fun reviewPersistsBeforeAdvancingAndUndoPreservesCelebrationConsumption() {
        val now = System.currentTimeMillis()
        assertTrue(store.saveItems(listOf(WishItem("ready", "Ready", 5000L, Platform.MANUAL, "", "default", now - 1000, now - 500))))
        val state = SavedStateHandle(); val m = model(state)
        main {
            m.startReview(); assertTrue(m.reviewAction(ReviewAction.SKIP))
            assertEquals(Decision.SKIP, store.items().single().decision)
            assertTrue(m.review!!.complete)
            m.consumeReviewCelebration(); m.undoReview()
            assertEquals(Decision.PENDING, store.items().single().decision)
            assertTrue(m.review!!.celebrationConsumed)
        }
        val restored = model(state)
        main { assertEquals("review", restored.route); assertEquals("ready", restored.currentReviewItem()?.id) }
    }

    @Test fun preferencesAndManualNullPricePersistWithoutChangingDraft() {
        val m = model()
        main {
            m.openAdd(); m.setMode("manual"); m.changeDraft { it.copy(name = "No price", desire = Desire.LIKE) }
            m.setTheme(ThemePreference.DARK); m.updateMascotBubbles(false); m.updateMascotReduceMotion(true)
            assertEquals("No price", m.draft?.name)
            m.saveDraft()
        }
        val restored = model()
        main {
            assertEquals(ThemePreference.DARK, restored.themePreference)
            assertFalse(restored.mascotBubbles); assertTrue(restored.mascotReduceMotion)
            assertNull(restored.items.single().priceWon)
            assertEquals(Desire.LIKE, restored.items.single().desire)
        }
    }

    @Test fun openingAnotherItemForEditPreservesDraftAndResumesSameItemEdits() {
        val now = System.currentTimeMillis()
        assertTrue(store.saveItems(listOf(WishItem("existing", "Original", 1000L, Platform.MANUAL, "", "default", now, now))))
        val state = SavedStateHandle(); val m = model(state)
        main {
            m.openAdd(); m.setMode("manual"); m.changeDraft { it.copy(name = "Unsaved new item", userMemo = "Keep this") }
            m.closeDraft(); m.edit(m.items.single())
            assertTrue(m.hasHeldDraft())
            m.changeDraft { it.copy(name = "Unsaved edit") }; m.closeDraft(); m.edit(m.items.single())
            assertEquals("Unsaved edit", m.draft?.name)
            m.restoreHeldDraft()
            assertEquals("Unsaved new item", m.draft?.name)
            assertEquals("Keep this", m.draft?.userMemo)
            m.edit(m.items.single())
            assertEquals("Unsaved edit", m.draft?.name)
        }
        val restored = model(state)
        main { restored.restoreHeldDraft(); assertEquals("Unsaved new item", restored.draft?.name) }
    }

    @Test fun repeatedSharesKeepEveryDraftOnceAndSurviveRecreation() {
        val state = SavedStateHandle(); val m = model(state)
        main {
            m.openAdd(); m.setMode("manual"); m.changeDraft { it.copy(name = "Draft A") }
            m.receiveShare(URL); m.acceptPendingShare()
            m.setMode("manual"); m.changeDraft { it.copy(name = "Draft B") }
            m.receiveShare("https://www.coupang.com/vp/products/9542831658"); m.acceptPendingShare()
            m.setMode("manual"); m.changeDraft { it.copy(name = "Draft C") }
        }
        val restored = model(state)
        main {
            val names = mutableListOf(restored.draft!!.name)
            repeat(2) { restored.restoreHeldDraft(); names += restored.draft!!.name }
            assertEquals(setOf("Draft A", "Draft B", "Draft C"), names.toSet())
            restored.restoreHeldDraft()
            assertEquals("Draft C", restored.draft?.name)
            assertFalse(state.contains("heldDraft"))
            assertFalse(state.contains("draft"))
        }
    }

    @Test fun correctedFieldsSurviveAnotherApiResponseAndRequireExplicitSave() {
        val m = model(); val first = DeferredService()
        main { m.linkService = first; m.receiveShare(URL) }
        first.reply.complete(ProductLinkResult.NeedsReview(product(null), listOf("가격을 확인해 주세요")))
        awaitCondition { m.draft?.reviewMessage != null }
        val second = DeferredService()
        main {
            m.changeDraft { it.copy(price = "0") }
            m.changeDraft { it.copy(name = "My corrected name", price = "", description = "My description", userMemo = "My memo") }
            m.setMode("link"); m.linkService = second; m.requestProduct()
        }
        second.reply.complete(ProductLinkResult.Complete(product(99999)))
        awaitCondition { !m.processing && m.draft?.snapshot?.priceWon == 99999L }
        main {
            assertTrue(m.items.isEmpty())
            assertEquals("My corrected name", m.draft?.name)
            assertEquals("", m.draft?.price)
            assertEquals("My description", m.draft?.description)
            assertEquals("My memo", m.draft?.userMemo)
            assertTrue(m.draft!!.reviewMessage!!.contains("99999"))
            m.saveDraft()
            assertEquals("My corrected name", m.items.single().name)
            assertNull(m.items.single().priceWon)
            assertEquals(99999L, m.items.single().productSnapshot?.priceWon)
        }
    }

    @Test fun untouchedPartialResponseIsPreservedWhenAnotherLinkArrives() {
        val m = model(); val service = DeferredService()
        main { m.linkService = service; m.receiveShare(URL) }
        service.reply.complete(ProductLinkResult.NeedsReview(product(null), listOf("가격을 확인해 주세요")))
        awaitCondition { m.draft?.reviewMessage != null }
        main {
            m.receiveShare("https://www.coupang.com/vp/products/9542831658")
            assertTrue(m.shareConflict)
            m.linkService = NotConfiguredProductLinkService
            m.acceptPendingShare(); m.restoreHeldDraft()
            assertEquals("Fixture product", m.draft?.name)
            assertNotNull(m.draft?.reviewMessage)
        }
    }

    @Test fun largeReviewMigratesOutOfSavedStateAndRestoresFromPrivateFile() {
        val now = System.currentTimeMillis()
        val records = (1..40).map { index ->
            val before = WishItem("large-$index", "Long note $index", 1000L, Platform.MANUAL, "", "default", now - 1000, now - 500,
                description = "가".repeat(5000), userMemo = "나".repeat(2000))
            ReviewRecord(before, decide(before, Decision.SKIP, now), ReviewAction.SKIP)
        }
        assertTrue(store.saveItems(records.map { it.after }))
        val original = ReviewSession(ids = records.map { it.before.id }, index = records.size, results = records, celebrationConsumed = true)
        assertTrue(original.encoded().length * 2 > 1_048_576)
        val state = SavedStateHandle(mapOf("route" to "review", "review" to original.encoded()))
        val m = model(state)
        main {
            assertEquals(40, m.review?.results?.size)
            assertFalse(state.contains("review"))
            assertNotNull(state.get<String>("reviewToken"))
            val stateStringCharacters = state.keys().sumOf { (state.get<Any>(it) as? String)?.length ?: 0 }
            assertTrue("Only small keys belong in saved state", stateStringCharacters < 1024)
            m.openAdd(); m.setMode("manual"); m.changeDraft { it.copy(name = "Independent draft") }
        }
        val restored = model(state)
        main {
            assertEquals(original, restored.review)
            assertEquals("Independent draft", restored.draft?.name)
            assertEquals("가".repeat(5000), restored.review!!.results.last().before.description)
        }
    }

    @Test fun deleteAllClearsDraftQueueSharedLinksReviewAndSavedWorkspace() {
        val now = System.currentTimeMillis()
        assertTrue(store.saveItems(listOf(WishItem("ready", "Ready", 1000L, Platform.MANUAL, "", "default", now - 1000, now - 500))))
        val state = SavedStateHandle(); val m = model(state)
        main {
            m.startReview(); assertTrue(m.reviewAction(ReviewAction.SKIP)); m.home()
            m.openAdd(); m.setMode("manual"); m.changeDraft { it.copy(name = "Draft A") }
            m.receiveShare(URL); m.acceptPendingShare()
            m.receiveShare("https://www.coupang.com/vp/products/9542831658")
            assertTrue(m.hasHeldDraft()); assertTrue(m.pendingSharedLinks.isNotEmpty())
            m.deleteAll()
            assertTrue(m.items.isEmpty()); assertNull(m.draft); assertNull(m.review)
            assertFalse(m.hasHeldDraft()); assertFalse(m.shareConflict)
            assertTrue(m.pendingSharedLinks.isEmpty()); assertTrue(m.sharedChoices.isEmpty())
            assertNull(m.receivedId); assertNull(m.celebrationId); assertFalse(m.sheetOpen)
        }
        val restored = model(state)
        main {
            assertNull(restored.draft); assertNull(restored.review); assertFalse(restored.hasHeldDraft())
            assertTrue(restored.pendingSharedLinks.isEmpty()); assertTrue(restored.items.isEmpty())
            restored.restoreHeldDraft(); assertNull(restored.draft)
        }
    }

    @Test fun failedWorkspaceWriteKeepsLastFileAndCurrentInput() {
        val state = SavedStateHandle(); val m = model(state)
        main { m.openAdd(); m.setMode("manual"); m.changeDraft { it.copy(name = "Durable draft") } }
        val token = state.get<String>("workspaceToken")!!
        val blocker = File(store.uiWorkspaceDirectory, "$token.json.new")
        assertTrue(blocker.mkdir())
        File(blocker, "block-write").writeText("fixture")
        main {
            m.changeDraft { it.copy(name = "Newest input") }
            assertEquals("Newest input", m.draft?.name)
            assertNotNull(m.requestError)
        }
        val restored = model(state)
        main { assertEquals("Durable draft", restored.draft?.name) }
        assertTrue(blocker.deleteRecursively())
        main { m.closeDraft(); m.openAdd() }
        val recovered = model(state)
        main { assertEquals("Newest input", recovered.draft?.name) }
    }

    @Test fun duplicateProductKeepsUnsavedInputWithoutCreatingAnotherItem() {
        val now = System.currentTimeMillis()
        assertTrue(store.saveItems(listOf(WishItem("existing", "Existing item", 1000L, Platform.COUPANG, URL, "default", now, now))))
        val m = model()
        main {
            m.openAdd(); m.setMode("manual")
            m.changeDraft { it.copy(url = URL, name = "Unsaved correction", userMemo = "Keep this note") }
            m.saveDraft()
            assertEquals(1, m.items.size); assertTrue(m.hasHeldDraft()); assertNull(m.draft)
            m.restoreHeldDraft()
            assertEquals("Unsaved correction", m.draft?.name)
            m.setMode("link"); m.requestProduct(); m.restoreHeldDraft()
            assertEquals("Keep this note", m.draft?.userMemo)
            assertFalse(m.hasHeldDraft())
            assertEquals(1, m.items.size)
        }
    }

    @Test fun committedAcquisitionRestoresAsExistingItemInsteadOfAnUnsavedCreation() {
        val state = SavedStateHandle(); val m = model(state)
        lateinit var original: ProductDraft
        main {
            m.openAdd(); m.setMode("manual")
            m.changeDraft { it.copy(name = "Already committed", price = "12000", userMemo = "Keep note") }
            original = m.draft!!
        }
        // Simulate process death between the item commit and clearing the workspace draft.
        val saved = WishItem("committed", original.name, 12000L, Platform.MANUAL, "", "default", 1L, 2L,
            userMemo = original.userMemo, acquisitionId = original.acquisitionId)
        assertTrue(store.saveNewItemOnce(saved) is SaveItemResult.Saved)
        val restored = model(state)
        main {
            assertNull(restored.draft); assertFalse(restored.sheetOpen)
            assertEquals("detail", restored.route); assertEquals(saved.id, restored.selected?.id)
            assertEquals(1, restored.items.size)
            restored.edit(restored.selected!!)
            restored.changeDraft { it.copy(name = "Explicit edit") }
            restored.saveDraft()
            assertEquals("Explicit edit", restored.items.single().name)
        }
    }

    @Test fun replayedAcquisitionPreservesNewManualCorrectionsUntilExplicitEditSave() {
        val m = model()
        lateinit var original: ProductDraft
        main {
            m.openAdd(); m.setMode("manual")
            m.changeDraft { it.copy(name = "Committed name", price = "1000") }
            original = m.draft!!
        }
        val saved = WishItem("saved-once", original.name, 1000L, Platform.MANUAL, "", "default", 1L, 2L,
            acquisitionId = original.acquisitionId)
        assertTrue(store.saveNewItemOnce(saved) is SaveItemResult.Saved)
        main {
            m.changeDraft { it.copy(name = "New correction", price = "", userMemo = "Do not discard") }
            m.saveDraft()
            assertEquals(1, m.items.size)
            assertEquals("Committed name", m.items.single().name)
            assertTrue(m.sheetOpen); assertEquals(saved.id, m.draft?.editingId)
            assertEquals("New correction", m.draft?.name)
            assertEquals("", m.draft?.price)
            assertEquals("Do not discard", m.draft?.userMemo)
        }
        val restored = model()
        main {
            assertEquals(saved.id, restored.draft?.editingId)
            assertEquals("New correction", restored.draft?.name)
            restored.saveDraft()
            assertEquals(1, restored.items.size)
            assertEquals("New correction", restored.items.single().name)
            assertNull(restored.items.single().priceWon)
            assertEquals("Do not discard", restored.items.single().userMemo)
        }
    }

    @Test fun unacceptedSharesRemainDistinctAndAreConsumedInArrivalOrderAfterRestore() {
        val m = model()
        val second = "https://www.coupang.com/vp/products/9542831658"
        main {
            m.openAdd(); m.setMode("manual"); m.changeDraft { it.copy(name = "Original draft") }
            m.receiveShare(URL); m.receiveShare(second); m.receiveShare(URL)
            assertEquals(listOf(URL, second), m.pendingSharedLinks)
        }
        val restored = model()
        main {
            assertEquals(listOf(URL, second), restored.pendingSharedLinks)
            restored.acceptPendingShare()
            assertEquals(URL, restored.draft?.url)
            assertEquals(listOf(second), restored.pendingSharedLinks)
            restored.setMode("manual"); restored.changeDraft { it.copy(name = "First shared item") }
            restored.saveDraft()
            restored.acceptPendingShare()
            assertEquals(second, restored.draft?.url)
            assertTrue(restored.pendingSharedLinks.isEmpty())
            restored.restoreHeldDraft()
            assertEquals("Original draft", restored.draft?.name)
        }
    }

    @Test fun overlongOriginalLinkIsPreservedAndNeverSentToTheService() {
        val m = model(); val service = DeferredService()
        val longUrl = "$URL?padding=" + "x".repeat(InputLimits.URL)
        main {
            m.linkService = service; m.openAdd(); m.changeDraft { it.copy(url = longUrl) }
            m.requestProduct()
            assertEquals(longUrl, m.draft?.url)
            assertEquals(0, service.calls); assertFalse(m.processing)
            assertNotNull(m.requestError); assertFalse(m.retryAllowed)
            assertTrue(m.items.isEmpty())
        }
    }

    companion object { private const val URL = "https://www.coupang.com/vp/products/9047670772" }
}
