package org.ralphthon.haruman_ralphthon.ui

import android.app.Application
import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
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
import org.ralphthon.haruman_ralphthon.data.LocalStore
import org.ralphthon.haruman_ralphthon.data.Platform
import org.ralphthon.haruman_ralphthon.data.WishItem
import org.ralphthon.haruman_ralphthon.ui.theme.Haruman_RalphthonTheme
import java.io.File
import java.util.UUID

/** Real app navigation and persistence, isolated to this test's UUID preferences/workspace. */
@RunWith(AndroidJUnit4::class)
class AddAfterEditUiTest {
    @get:Rule val compose = createComposeRule()
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val prefsName = "workflow-test-${UUID.randomUUID()}"
    private val store = LocalStore(app, prefsName)
    private val showApp = mutableStateOf(true)
    private var model: HarumanViewModel? = null

    @After fun cleanOwnFixture() {
        instrumentation.runOnMainSync {
            showApp.value = false
            model?.viewModelScope?.cancel()
        }
        instrumentation.waitForIdleSync()
        app.getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit().clear().commit()
        val root = File(app.filesDir, "ui-workspace").canonicalFile
        val own = store.uiWorkspaceDirectory.canonicalFile
        check(own.parentFile == root && own != root)
        own.deleteRecursively()
    }

    @Test fun vaultAddAfterClosingEditOpensBlankNewProductAndPreservesUnsavedEdit() {
        val now = System.currentTimeMillis()
        val original = WishItem(
            id = "add-after-edit-original", name = "원래 보관한 상품", priceWon = 1_000L,
            platform = Platform.MANUAL, url = "", artwork = "default",
            createdAt = now, cooldownUntil = now + 86_400_000L,
            description = "원래 상품 설명", userMemo = "원래 상품 메모",
        )
        assertTrue(store.saveItems(listOf(original)))
        lateinit var fixture: HarumanViewModel
        instrumentation.runOnMainSync {
            store.onboardingDone = true
            store.mascotReduceMotion = true
            store.mascotBubbles = false
            // The public two-argument constructor opens the user's store: never use it here.
            fixture = HarumanViewModel(app, SavedStateHandle(), store)
            model = fixture
        }
        compose.setContent {
            if (showApp.value) Haruman_RalphthonTheme(darkTheme = false) {
                HarumanApp(model = fixture)
            }
        }

        scrollVaultTo("item_${original.id}")
        compose.onNodeWithTag("item_${original.id}").performClick()
        compose.onNodeWithTag("edit_item").assertIsDisplayed().performClick()
        compose.onNodeWithText("상품 정보를 수정해요").assertIsDisplayed()
        assertField("input_name", original.name)
        compose.onNodeWithTag("input_name").performScrollTo()
            .performTextReplacement("아직 저장하지 않은 수정")

        // Use the sheet's actual accessibility dismiss action, without invoking model.closeDraft.
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.Dismiss))
            .performSemanticsAction(SemanticsActions.Dismiss) { dismiss -> assertTrue(dismiss()) }
        compose.waitUntil(5_000) { !fixture.sheetOpen }
        compose.onNodeWithContentDescription("뒤로가기").assertIsDisplayed().performClick()
        scrollVaultTo("add_item")
        compose.onNodeWithTag("add_item").assertIsDisplayed().performClick()

        compose.onNodeWithText("마음에 든 상품을 담아 봐요").assertIsDisplayed()
        compose.onNodeWithText("상품 정보를 수정해요").assertDoesNotExist()
        assertField("input_url", "")
        compose.runOnIdle {
            assertNull(fixture.draft?.editingId)
            assertTrue(fixture.hasHeldDraft())
            assertEquals(listOf(original), fixture.items)
        }
        compose.onNodeWithTag("mode_manual").performScrollTo().performClick()
        listOf("input_name", "input_price", "input_description", "input_memo").forEach {
            assertField(it, "")
        }
        compose.onNodeWithTag("save_item").assertIsNotEnabled()
        compose.onNodeWithTag("input_name").performScrollTo().performTextReplacement("새로 추가한 상품")
        compose.onNodeWithTag("input_price").performScrollTo().performTextReplacement("2000")
        compose.onNodeWithTag("save_item").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithTag("added_title").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(2, fixture.items.size)
            assertEquals(original, fixture.items.single { it.id == original.id })
            val added = fixture.items.single { it.id != original.id }
            assertEquals("새로 추가한 상품", added.name)
            assertEquals(2_000L, added.priceWon)
            assertEquals("", added.description)
            assertEquals("", added.userMemo)
            assertEquals(fixture.items, store.items())
        }

        compose.onNodeWithTag("added_home").performScrollTo().performClick()
        compose.onNodeWithText("이어서 작성").assertIsDisplayed().performClick()
        compose.onNodeWithText("상품 정보를 수정해요").assertIsDisplayed()
        assertField("input_name", "아직 저장하지 않은 수정")
        assertField("input_price", "1000")
        compose.runOnIdle {
            assertEquals(original.id, fixture.draft?.editingId)
            assertEquals(original, store.items().single { it.id == original.id })
            assertEquals(2, store.items().size)
        }
    }

    private fun scrollVaultTo(tag: String) {
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag(tag))
    }

    private fun assertField(tag: String, expected: String) {
        val value = compose.onNodeWithTag(tag).fetchSemanticsNode()
            .config[SemanticsProperties.EditableText].text
        assertEquals("$tag should contain only this draft's value", expected, value)
    }
}
