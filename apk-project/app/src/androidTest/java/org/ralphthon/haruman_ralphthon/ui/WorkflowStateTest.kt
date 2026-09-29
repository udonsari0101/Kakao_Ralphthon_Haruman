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

    companion object { private const val URL = "https://www.coupang.com/vp/products/9047670772" }
}
