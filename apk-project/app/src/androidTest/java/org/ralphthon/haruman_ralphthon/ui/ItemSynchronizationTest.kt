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

/** Two independent owners of UUID-only storage. No production constructor, API or manual refresh. */
@RunWith(AndroidJUnit4::class)
class ItemSynchronizationTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val prefsName = "workflow-test-${UUID.randomUUID()}"
    private val store = LocalStore(app, prefsName)
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val models = mutableListOf<HarumanViewModel>()
    private val now = System.currentTimeMillis()

    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)

    private fun model(): HarumanViewModel {
        lateinit var result: HarumanViewModel
        main {
            result = HarumanViewModel(app, SavedStateHandle(), LocalStore(app, prefsName))
            models += result
        }
        return result
    }

    private fun awaitAutomaticUpdate(message: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + 3_000_000_000L
        while (System.nanoTime() < deadline) {
            var satisfied = false
            main { satisfied = condition() }
            if (satisfied) return
            Thread.sleep(20)
        }
        fail(message)
    }

    @After fun clearOnlyOwnStorage() {
        main { models.forEach { it.viewModelScope.cancel() } }
        app.getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit().clear().commit()
        val root = File(app.filesDir, "ui-workspace").canonicalFile
        val own = store.uiWorkspaceDirectory.canonicalFile
        check(own.parentFile == root && own != root)
        own.deleteRecursively()
    }

    @Test fun anotherOwnerEditAutomaticallyUpdatesSelectedItemAndSurvivesColdModelCreation() {
        val original = fixture("edited")
        val untouched = fixture("untouched").copy(decision = Decision.SKIP, pitRemovedAt = null)
        assertTrue(store.saveItems(listOf(original, untouched)))
        val reader = model()
        val writer = model()
        val expected = original.copy(name = "다른 화면에서 수정한 이름", priceWon = 27_500L,
            description = "수정한 상품 설명", userMemo = "수정한 나의 메모")
        main {
            reader.select(original, now, fromHistory = true)
            writer.edit(writer.items.first { it.id == original.id })
            writer.changeDraft { it.copy(name = expected.name, price = "27500", description = expected.description, userMemo = expected.userMemo) }
            writer.saveDraft()
            assertNull(writer.requestError)
            assertEquals(expected, writer.selected)
        }
        // Prove persistence separately from notification, so a failure isolates stale live state.
        assertEquals(listOf(expected, untouched), LocalStore(app, prefsName).items())
        awaitAutomaticUpdate("VM A kept old items/selected after VM B durably saved an edit") {
            reader.items == listOf(expected, untouched) && reader.selected == expected
        }
        main {
            assertEquals("detail", reader.route)
            assertEquals(original.id, reader.selectedId)
            assertEquals(original.decisionPriceWon, reader.selected!!.decisionPriceWon)
            assertEquals(original.productSnapshot, reader.selected!!.productSnapshot)
            assertEquals(original.pitRemovedAt, reader.selected!!.pitRemovedAt)
        }
        val cold = model()
        main { assertEquals(listOf(expected, untouched), cold.items) }
    }

    @Test fun anotherOwnerCreationAppearsAutomaticallyWithoutChangingTheCurrentSelection() {
        val original = fixture("selected")
        assertTrue(store.saveItems(listOf(original)))
        val reader = model()
        val writer = model()
        lateinit var added: WishItem
        main {
            reader.select(original, now)
            writer.openAdd()
            writer.setMode("manual")
            writer.changeDraft { it.copy(name = "다른 화면에서 담은 상품", price = "8300", description = "새 상품 설명", hours = 0) }
            writer.saveDraft()
            assertNull(writer.requestError)
            added = writer.items.single { it.id != original.id }
        }
        awaitAutomaticUpdate("VM A did not observe a product created by VM B") {
            reader.items == listOf(original, added)
        }
        main {
            assertEquals(original, reader.selected)
            assertEquals("detail", reader.route)
            assertNull(reader.draft)
            assertFalse(reader.sheetOpen)
        }
        assertEquals(listOf(original, added), LocalStore(app, prefsName).items())
    }

    @Test fun automaticRefreshKeepsAClosedUnsavedEditAndItsOriginalConflictBaseline() {
        val original = fixture("draft-target")
        assertTrue(store.saveItems(listOf(original)))
        val reader = model()
        val writer = model()
        lateinit var unsaved: ProductDraft
        val external = original.copy(name = "다른 화면에서 저장한 이름", priceWon = 46_000L,
            description = "새로 저장된 설명", userMemo = "다른 화면의 메모")
        main {
            reader.select(original, now, fromHistory = true)
            reader.edit(original)
            reader.changeDraft { it.copy(name = "아직 저장하지 않은 이름", userMemo = "계속 작성할 메모") }
            reader.closeDraft()
            unsaved = reader.draft!!
            writer.edit(writer.items.single())
            writer.changeDraft { it.copy(name = external.name, price = "46000", description = external.description, userMemo = external.userMemo) }
            writer.saveDraft()
            assertEquals(external, writer.selected)
        }
        assertEquals(listOf(external), LocalStore(app, prefsName).items())
        awaitAutomaticUpdate("VM A's saved item did not refresh while an unsaved edit was held") {
            reader.items.singleOrNull() == external && reader.selected == external
        }
        main {
            assertEquals(unsaved, reader.draft)
            assertEquals(original, reader.draft!!.editBaseline)
            assertFalse(reader.sheetOpen)
            reader.edit(reader.selected!!)
            assertTrue(reader.sheetOpen)
            assertEquals(unsaved, reader.draft)
            // A notification must not silently bless the old edit as based on the newer record.
            reader.saveDraft()
            assertNotNull(reader.requestError)
            assertEquals("아직 저장하지 않은 이름", reader.draft!!.name)
            assertEquals("계속 작성할 메모", reader.draft!!.userMemo)
        }
        assertEquals(listOf(external), LocalStore(app, prefsName).items())
    }

    private fun fixture(id: String) = WishItem(id, "원래 상품 $id", 19_000L, Platform.MANUAL,
        "", "default", now, now, decision = Decision.BUY, decidedAt = now,
        description = "원래 설명", userMemo = "원래 메모", decisionPriceWon = 12_000L,
        productSnapshot = ProductSnapshot(name = "원본 상품 이름", priceWon = 12_000L, currency = "KRW"),
        pitRemovedAt = now - 1000)
}
