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

/** Real workspace persistence under this test's UUID only; no app launch, network or user store. */
@RunWith(AndroidJUnit4::class)
class TerraceFlowTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val prefsName = "workflow-test-${UUID.randomUUID()}"
    private val store = LocalStore(app, prefsName)
    private val models = mutableListOf<HarumanViewModel>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val now = System.currentTimeMillis()
    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private fun model(state: SavedStateHandle = SavedStateHandle()): HarumanViewModel {
        lateinit var model: HarumanViewModel
        main {
            store.onboardingDone = true
            model = HarumanViewModel(app, state, LocalStore(app, prefsName))
            models += model
        }
        return model
    }

    @After fun cleanOwnFixture() {
        main { models.forEach { it.viewModelScope.cancel() } }
        app.getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit().clear().commit()
        val root = File(app.filesDir, "ui-workspace").canonicalFile
        val own = store.uiWorkspaceDirectory.canonicalFile
        check(own.parentFile == root && own != root)
        own.deleteRecursively()
    }

    @Test fun yardDetailAndDecisionReturnToYardButBackFromYardReturnsToVault() {
        assertTrue(store.saveItems(listOf(item("ready"))))
        val m = model()
        main {
            m.navigate("history")
            m.select(m.items.single(), now, fromHistory = true)
            assertEquals("detail", m.route)
            assertEquals("history", m.returnDestination)
            m.home()
            assertEquals("history", m.route)
            m.home()
            assertEquals("vault", m.route)
            m.openYardDecision(m.items.single())
            assertEquals("decide", m.route)
            m.decideSelected(Decision.SKIP)
            assertEquals("result", m.route)
            m.home()
            assertEquals("history", m.route)
            assertEquals(Decision.SKIP, m.items.single().decision)
        }
    }

    @Test fun originSurvivesSavedStateAndWorkspaceFallbackWithoutExpandingBundle() {
        assertTrue(store.saveItems(listOf(item("ready"))))
        val state = SavedStateHandle()
        val original = model(state)
        main { original.openYardDecision(original.items.single()) }
        assertEquals("history", state.get<String>("returnDestination"))
        val restored = model(state)
        main {
            assertEquals("decide", restored.route)
            assertEquals("history", restored.returnDestination)
            restored.home()
            assertEquals("history", restored.route)
        }
        // A restored navigation Bundle can omit the new key; the latest AtomicFile fills it in.
        val workspaceFallback = model(SavedStateHandle(mapOf("route" to "detail", "selectedId" to "ready")))
        main {
            assertEquals("history", workspaceFallback.returnDestination)
            workspaceFallback.home()
            assertEquals("history", workspaceFallback.route)
        }
        assertFalse(state.keys().contains("yardSeenAnimations"))
    }

    @Test fun reviewAndWaitMoreRememberYardWhileNewAddAndVaultSelectionsResetOrigin() {
        assertTrue(store.saveItems(listOf(item("ready"))))
        val m = model()
        main {
            m.navigate("history"); m.startReview()
            assertEquals("history", m.returnDestination)
            assertEquals("review", m.route)
            m.home(); assertEquals("history", m.route)
            m.openYardDecision(m.items.single()); m.extendSelected()
            assertEquals("history", m.route)
            assertEquals(Decision.PENDING, m.items.single().decision)
            m.openAdd(); assertEquals("vault", m.returnDestination)
            m.closeDraft()
            m.select(m.items.single(), now)
            assertEquals("vault", m.returnDestination)
            m.home(); assertEquals("vault", m.route)
            m.navigate("history"); m.openYardDecision(m.items.single())
            m.navigate("vault"); assertEquals("vault", m.returnDestination)
        }
    }

    @Test fun markerSurvivesRecreationAndPriceEditWithoutChangingProductOrSnapshot() {
        assertTrue(store.saveItems(listOf(item("tree").copy(decision = Decision.SKIP, decidedAt = now, decisionPriceWon = 12_000))))
        val m = model()
        val before = store.items()
        main {
            assertTrue(m.yardSeenAnimations.isEmpty())
            m.consumeYardAnimation("tree:SPROUT")
            m.consumeYardAnimation("tree:TREE")
            m.consumeYardAnimation("tree:TREE")
            assertEquals(setOf("tree:SPROUT", "tree:TREE"), m.yardSeenAnimations)
        }
        assertEquals(before, store.items())
        val restored = model()
        main {
            assertEquals(setOf("tree:SPROUT", "tree:TREE"), restored.yardSeenAnimations)
            restored.select(restored.items.single(), now, fromHistory = true)
            restored.edit(restored.items.single())
            restored.changeDraft { it.copy(price = "99999") }
            restored.saveDraft()
            assertEquals(12_000L, restored.items.single().decisionPriceWon)
            assertEquals(setOf("tree:SPROUT", "tree:TREE"), restored.yardSeenAnimations)
            restored.home(); assertEquals("history", restored.route)
        }
        val afterEdit = model()
        main { assertEquals(setOf("tree:SPROUT", "tree:TREE"), afterEdit.yardSeenAnimations) }
    }

    @Test fun markersRejectInvalidAndSampleKeysThenPruneRemovedProductsOnRecreation() {
        assertTrue(store.saveItems(listOf(item("one"), item("two"), item("sample-hidden"))))
        val m = model()
        main {
            listOf("one:SPROUT", "two:PIT", "missing:TREE", "one:MOUND", "sample-hidden:SPROUT", "malformed").forEach(m::consumeYardAnimation)
            assertEquals(setOf("one:SPROUT", "two:PIT"), m.yardSeenAnimations)
        }
        assertTrue(store.saveItems(store.items().filterNot { it.id == "one" }))
        val restored = model()
        main { assertEquals(setOf("two:PIT"), restored.yardSeenAnimations) }
        val again = model()
        main { assertEquals(setOf("two:PIT"), again.yardSeenAnimations) }
    }

    @Test fun deleteAllClearsAnimationMarkersAndOriginAlongWithExistingWorkspace() {
        assertTrue(store.saveItems(listOf(item("ready"))))
        val m = model()
        main {
            m.openYardDecision(m.items.single()); m.consumeYardAnimation("ready:SPROUT")
            m.deleteAll()
            assertTrue(m.yardSeenAnimations.isEmpty())
            assertEquals("vault", m.returnDestination)
            assertEquals("vault", m.route)
        }
        val restored = model()
        main {
            assertTrue(restored.items.isEmpty()); assertTrue(restored.yardSeenAnimations.isEmpty())
            assertEquals("vault", restored.returnDestination)
        }
    }

    private fun item(id: String) = WishItem(id, "검증 $id", 12_000, Platform.MANUAL, "", "bag", now - 60_000, now - 1)
}
