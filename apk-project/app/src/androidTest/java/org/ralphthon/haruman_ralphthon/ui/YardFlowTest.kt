package org.ralphthon.haruman_ralphthon.ui

import android.app.Application
import android.content.Context
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
import org.ralphthon.haruman_ralphthon.data.*
import org.ralphthon.haruman_ralphthon.ui.theme.Haruman_RalphthonTheme
import java.io.File
import java.util.UUID

/** Full app composition with a unique test store. No external share intent or production data. */
@RunWith(AndroidJUnit4::class)
class YardFlowTest {
    @get:Rule val compose = createComposeRule()
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val prefsName = "workflow-test-${UUID.randomUUID()}"
    private val store = LocalStore(app, prefsName)
    private val models = mutableListOf<HarumanViewModel>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val now = System.currentTimeMillis()
    private val current = periodOf(now, YardPeriod.MONTH)
    private val previous = periodOf(current - 1L, YardPeriod.MONTH)

    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private fun model(route: String = "my", fromStore: LocalStore = store): HarumanViewModel {
        lateinit var model: HarumanViewModel
        main {
            fromStore.onboardingDone = true
            fromStore.mascotReduceMotion = true
            model = HarumanViewModel(app, SavedStateHandle(mapOf("route" to route)), fromStore)
            models += model
        }
        return model
    }

    private fun show(model: HarumanViewModel) {
        compose.setContent { Haruman_RalphthonTheme { HarumanApp(model) } }
    }

    @After fun cleanOnlyOwnFixture() {
        main { models.forEach { it.viewModelScope.cancel() } }
        app.getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit().clear().commit()
        val root = File(app.filesDir, "ui-workspace").canonicalFile
        val own = store.uiWorkspaceDirectory.canonicalFile
        check(own.parentFile == root && own != root) { "Refusing to delete outside the fixture's workspace" }
        own.deleteRecursively()
    }

    @Test fun goalSavedThroughAppDialogSurvivesModelAndLocalStoreRecreation() {
        val original = model()
        show(original)
        compose.onNodeWithText("소비 목표").performScrollTo().performClick()
        compose.onNodeWithTag("goal_amount").performScrollTo().performClick().performTextInput("350000")
        compose.onNodeWithTag("goal_save").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(350_000L, original.spendingGoalWon) }
        compose.onNodeWithTag("goal_amount").assertDoesNotExist()
        val reopenedStore = LocalStore(app, prefsName)
        assertEquals(350_000L, reopenedStore.spendingGoalWon)
        val restored = model(fromStore = reopenedStore)
        main {
            assertEquals(350_000L, restored.spendingGoalWon)
            assertEquals(YardPeriod.MONTH, restored.yardPeriod)
        }
    }

    @Test fun pendingShareChoicesPreventAutomaticReportAndBudgetDialogsFromOverlapping() {
        assertTrue(store.saveItems(listOf(
            fixture("previous", previous + 3_600_000, Decision.SKIP, previous + 7_200_000, 2_000),
            fixture("current-buy", current + 3_600_000, Decision.BUY, now, 10_000),
        )))
        assertTrue(store.saveYardSettings(YardPeriod.MONTH, 10_000))
        val m = model("vault")
        main { m.receiveShare("https://www.coupang.com/vp/products/9047670772\nhttps://www.coupang.com/vp/products/9542831658") }
        show(m)
        compose.onNodeWithText("어떤 상품을 담을까요?").assertIsDisplayed()
        compose.onNodeWithTag("yard_report").assertDoesNotExist()
        compose.onNodeWithText("확인했어요").assertDoesNotExist()
        compose.onAllNodes(isDialog()).assertCountEquals(1)
        assertFalse(store.hasSeenYardReport(YardPeriod.MONTH, previous))
        assertFalse(store.hasSeenYardMilestone(YardPeriod.MONTH, current, "3"))
        compose.onNodeWithText("나중에").performClick()
        compose.onNodeWithTag("yard_report").assertExists()
        compose.onAllNodes(isDialog()).assertCountEquals(1)
    }

    @Test fun reportOpenedManuallyDoesNotOpenAgainWhenReturningToVault() {
        assertTrue(store.saveItems(listOf(fixture("previous", previous + 3_600_000, Decision.SKIP, previous + 7_200_000, 2_000))))
        val m = model("my")
        show(m)
        compose.onNodeWithText("마당 일기").performScrollTo().performClick()
        compose.onNodeWithTag("yard_report").assertExists()
        compose.onNodeWithTag("yard_report").performScrollToNode(hasTestTag("yard_report_close"))
        compose.onNodeWithTag("yard_report_close").performScrollTo().performClick()
        compose.onNodeWithTag("yard_report").assertDoesNotExist()
        compose.onNodeWithTag("tab_vault").performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertEquals("vault", m.route) }
        compose.onNodeWithTag("yard_report").assertDoesNotExist()
        assertTrue(LocalStore(app, prefsName).hasSeenYardReport(YardPeriod.MONTH, previous))
    }

    private fun fixture(id: String, createdAt: Long, decision: Decision, decidedAt: Long, price: Long) = WishItem(
        id = id, name = "검증 상품 $id", priceWon = price, platform = Platform.MANUAL, url = "", artwork = "bag",
        createdAt = createdAt, cooldownUntil = createdAt, decision = decision, decidedAt = decidedAt,
        decisionPriceWon = price, isSample = false,
    )
}
