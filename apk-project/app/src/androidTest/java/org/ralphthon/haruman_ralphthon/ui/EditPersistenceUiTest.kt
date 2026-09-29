package org.ralphthon.haruman_ralphthon.ui

import android.app.Application
import android.content.Context
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.ralphthon.haruman_ralphthon.data.Decision
import org.ralphthon.haruman_ralphthon.data.LocalStore
import org.ralphthon.haruman_ralphthon.data.Platform
import org.ralphthon.haruman_ralphthon.data.WishItem
import org.ralphthon.haruman_ralphthon.ui.theme.Haruman_RalphthonTheme
import java.io.File
import java.util.UUID

/** Exercises real edit controls; every store and workspace belongs only to this UUID fixture. */
@RunWith(AndroidJUnit4::class)
class EditPersistenceUiTest {
    @get:Rule val compose = createComposeRule()
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val prefsName = "workflow-test-${UUID.randomUUID()}"
    private val store = LocalStore(app, prefsName)
    private val activeModel = mutableStateOf<HarumanViewModel?>(null)
    private val models = mutableListOf<HarumanViewModel>()

    @After fun clearOnlyOwnFixture() {
        instrumentation.runOnMainSync {
            activeModel.value = null
            models.forEach { it.viewModelScope.cancel() }
        }
        instrumentation.waitForIdleSync()
        app.getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit().clear().commit()
        val root = File(app.filesDir, "ui-workspace").canonicalFile
        val own = store.uiWorkspaceDirectory.canonicalFile
        check(own.parentFile == root && own != root)
        own.deleteRecursively()
    }

    @Test fun pendingEditUpdatesDetailAndVaultImmediatelyAndSurvivesFreshViewModel() {
        val original = originalItem("pending")
        launch(listOf(original))
        openVaultItem(original.id)

        val edited = original.copy(
            name = "수정한 대기 상품", priceWon = 24_680L,
            description = "저장 직후에도 보여야 하는 설명",
            userMemo = "다시 들어와도 유지할 메모",
        )
        editThroughUi(edited)
        assertDetail(edited)
        assertPersisted(edited)

        // Navigate using the real back button, then verify the row before reopening it.
        compose.onNodeWithContentDescription("뒤로가기").performClick()
        assertVaultRow(edited)
        compose.onNodeWithTag("item_${edited.id}").performClick()
        assertDetail(edited)

        // A new store/VM and a fresh composition cannot reuse the prior VM's in-memory list.
        replaceWithFreshModel()
        assertVaultRow(edited)
        compose.onNodeWithTag("item_${edited.id}").performClick()
        assertDetail(edited)
        assertPersisted(edited)
    }

    @Test fun decidedItemEditPersistsCurrentFieldsWithoutRewritingDecisionPriceOrRemovedPit() {
        val now = System.currentTimeMillis()
        val original = originalItem("bought").copy(
            decision = Decision.BUY, decidedAt = now - 1_000L,
            decisionPriceWon = 19_000L, pitRemovedAt = now - 500L,
        )
        launch(listOf(original), detailId = original.id)
        val edited = original.copy(
            name = "수정한 구매 결정 상품", priceWon = 42_600L,
            description = "현재 상품 설명을 새로 적었어요",
            userMemo = "결정 당시 금액과 구덩이 상태는 유지해요",
        )

        editThroughUi(edited)
        assertDetail(edited)
        compose.onNodeWithText(money(original.decisionPriceWon)).performScrollTo().assertIsDisplayed()
        assertPersisted(edited)

        replaceWithFreshModel(detailId = original.id)
        assertDetail(edited)
        compose.onNodeWithText(money(original.decisionPriceWon)).performScrollTo().assertIsDisplayed()
        assertPersisted(edited)
        compose.runOnIdle {
            assertEquals(original.decisionPriceWon, activeModel.value!!.selected!!.decisionPriceWon)
            assertEquals(original.pitRemovedAt, activeModel.value!!.selected!!.pitRemovedAt)
        }
    }

    private fun originalItem(id: String): WishItem {
        val now = System.currentTimeMillis()
        return WishItem(
            id = "edit-persistence-$id", name = "수정 전 상품 $id", priceWon = 1_230L,
            platform = Platform.MANUAL, url = "", artwork = "default",
            createdAt = now, cooldownUntil = now + 86_400_000L,
            description = "수정 전 설명 $id", userMemo = "수정 전 메모 $id",
        )
    }

    private fun launch(items: List<WishItem>, detailId: String? = null) {
        assertTrue(store.saveItems(items))
        instrumentation.runOnMainSync {
            store.onboardingDone = true
            store.mascotReduceMotion = true
            store.mascotBubbles = false
            activeModel.value = newModel(detailId)
        }
        compose.setContent {
            activeModel.value?.let { model ->
                key(model) {
                    Haruman_RalphthonTheme(darkTheme = false) { HarumanApp(model = model) }
                }
            }
        }
    }

    /** Called on the UI thread; the public constructor must never open the production namespace. */
    private fun newModel(detailId: String?): HarumanViewModel {
        val state = SavedStateHandle().apply {
            if (detailId != null) { set("route", "detail"); set("selectedId", detailId) }
        }
        return HarumanViewModel(app, state, LocalStore(app, prefsName)).also { models += it }
    }

    private fun replaceWithFreshModel(detailId: String? = null) {
        compose.runOnIdle {
            activeModel.value?.viewModelScope?.cancel()
            activeModel.value = null
        }
        compose.waitForIdle()
        compose.runOnIdle { activeModel.value = newModel(detailId) }
        compose.waitForIdle()
    }

    private fun editThroughUi(expected: WishItem) {
        compose.onNodeWithTag("edit_item").assertIsDisplayed().performClick()
        compose.onNodeWithText("상품 정보를 수정해요").assertIsDisplayed()
        listOf(
            "input_name" to expected.name,
            "input_price" to expected.priceWon!!.toString(),
            "input_description" to expected.description,
            "input_memo" to expected.userMemo,
        ).forEach { (tag, value) ->
            compose.onNodeWithTag(tag).performScrollTo().performTextReplacement(value)
        }
        compose.onNodeWithTag("save_item").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithTag("save_item").assertDoesNotExist()
        compose.onNodeWithTag("edit_item").assertIsDisplayed()
    }

    private fun assertDetail(expected: WishItem) {
        listOf(expected.name, money(expected.priceWon), expected.description, expected.userMemo).forEach { text ->
            compose.onNodeWithText(text).performScrollTo().assertIsDisplayed()
        }
    }

    private fun openVaultItem(id: String) {
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("item_$id"))
        compose.onNodeWithTag("item_$id").assertIsDisplayed().performClick()
    }

    private fun assertVaultRow(expected: WishItem) {
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("item_${expected.id}"))
        compose.onNodeWithTag("item_${expected.id}").assertIsDisplayed()
            .assertTextContains(expected.name).assertTextContains(money(expected.priceWon))
    }

    private fun assertPersisted(expected: WishItem) {
        compose.runOnIdle { assertEquals(listOf(expected), activeModel.value!!.items) }
        assertEquals(listOf(expected), LocalStore(app, prefsName).items())
    }
}
