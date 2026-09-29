package org.ralphthon.haruman_ralphthon.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.ralphthon.haruman_ralphthon.data.*
import java.io.File
import java.util.UUID

/** UUID-only preferences and workspace. Never loads API credentials, production records or a payment. */
@RunWith(AndroidJUnit4::class)
class PitRemovalFlowTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val name = "workflow-test-${UUID.randomUUID()}"
    private val store = LocalStore(app, name)
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val models = mutableListOf<HarumanViewModel>()
    private val now = System.currentTimeMillis()
    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private fun model(): HarumanViewModel {
        lateinit var result: HarumanViewModel
        main { result = HarumanViewModel(app, SavedStateHandle(), LocalStore(app, name)); models += result }
        return result
    }
    @After fun clearOnlyThisFixture() {
        main { models.forEach { it.viewModelScope.cancel() } }
        app.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        val root = File(app.filesDir, "ui-workspace").canonicalFile
        val own = store.uiWorkspaceDirectory.canonicalFile
        check(own.parentFile == root && own != root)
        own.deleteRecursively()
    }
    private fun bought(id: String, amount: Long? = 19_000L) = WishItem(id, "검수 상품 $id", 999_000L,
        Platform.MANUAL, "", "default", now, now, decision = Decision.BUY, decidedAt = now, decisionPriceWon = amount)

    @Test fun removalSurvivesNewStoreAndViewModelPreservesAnotherPitAndNeverAddsTheDisplayedFee() {
        val first = bought("first")
        val second = bought("second", 31_000L)
        assertTrue(store.saveItems(listOf(first, second)))
        val initial = model()
        var removedAt: Long? = null
        main {
            initial.navigate("history")
            assertTrue(initial.removeYardPit(first))
            removedAt = initial.items.first().pitRemovedAt
            assertNotNull(removedAt)
            assertTrue(initial.removeYardPit(first))
            assertEquals(removedAt, initial.items.first().pitRemovedAt)
            assertEquals("history", initial.route)
            assertEquals(first, initial.items.first().copy(pitRemovedAt = null))
            assertEquals(second, initial.items.last())
        }
        val persisted = LocalStore(app, name).items()
        assertEquals(removedAt, persisted.first().pitRemovedAt)
        val restarted = model()
        main {
            assertEquals(persisted, restarted.items)
            YardPeriod.entries.forEach { period ->
                val summary = yardSummary(restarted.items, now, period, now)
                assertEquals(1, summary.realPitCount)
                assertEquals(50_000L, summary.boughtWon)
                assertEquals(50_000L, summary.budgetBoughtWon)
            }
            assertTrue(restarted.removeYardPit(second))
        }
        val reopened = model()
        main {
            assertEquals(2, reopened.items.size)
            assertTrue(reopened.items.all { it.decision == Decision.BUY && it.pitRemovedAt != null })
            assertTrue(layoutWeek(reopened.items, now, 320f, now).plants.isEmpty())
            YardPeriod.entries.forEach { period ->
                assertEquals(0, yardSummary(reopened.items, now, period, now).realPitCount)
                assertEquals(50_000L, yardSummary(reopened.items, now, period, now).budgetBoughtWon)
            }
        }
    }

    @Test fun removalFromAStaleScreenPreservesNewerEditsAndLaterEditingKeepsThePitRemoved() {
        val original = bought("edited", null)
        assertTrue(store.saveItems(listOf(original)))
        val oldScreen = model()
        val latest = original.copy(name = "다른 화면에서 바꾼 이름", priceWon = 77_000L, userMemo = "보존할 메모")
        assertTrue(LocalStore(app, name).updateItem(original, latest) is UpdateItemResult.Updated)
        main {
            assertTrue(oldScreen.removeYardPit(original))
            val saved = oldScreen.items.single()
            assertEquals(latest, saved.copy(pitRemovedAt = null))
            assertNull(saved.decisionPriceWon)
            oldScreen.edit(saved)
            oldScreen.changeDraft { it.copy(name = "제거 후에도 수정해요") }
            oldScreen.saveDraft()
            assertEquals("제거 후에도 수정해요", oldScreen.items.single().name)
            assertEquals(saved.pitRemovedAt, oldScreen.items.single().pitRemovedAt)
        }
        assertNotNull(LocalStore(app, name).items().single().pitRemovedAt)
        assertNull(LocalStore(app, name).items().single().decisionPriceWon)
    }

    @Test fun deletedNonPurchaseAndExampleItemsCannotBeRemovedOrResurrected() {
        val deleted = bought("deleted")
        val pending = bought("pending").copy(decision = Decision.PENDING, decidedAt = null, decisionPriceWon = null)
        val sample = bought("sample-pit").copy(isSample = true)
        assertTrue(store.saveItems(listOf(deleted, pending, sample)))
        val stale = model()
        assertTrue(LocalStore(app, name).saveItems(listOf(pending, sample)))
        main {
            assertFalse(stale.removeYardPit(deleted))
            assertFalse(stale.removeYardPit(pending))
            assertFalse(stale.removeYardPit(sample))
        }
        assertEquals(listOf(pending, sample), LocalStore(app, name).items())
    }
}
