package org.ralphthon.haruman_ralphthon.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.ralphthon.haruman_ralphthon.data.*
import org.ralphthon.haruman_ralphthon.ui.mascot.CheekMoa
import org.ralphthon.haruman_ralphthon.ui.mascot.LocalReduceMotion
import org.ralphthon.haruman_ralphthon.ui.mascot.LocalShowBubbles
import org.ralphthon.haruman_ralphthon.ui.theme.Haruman_RalphthonTheme
import java.io.File
import java.util.Calendar

/** In-memory fixtures only. No ViewModel, application preferences, network, or device settings. */
@RunWith(AndroidJUnit4::class)
class YardUiTest {
    @get:Rule val compose = createComposeRule()
    private val now = date(2026, 9, 29, 12)
    private class Events { val decisions = mutableListOf<String>(); val details = mutableListOf<String>(); val periods = mutableListOf<YardPeriod>(); var goals = 0 }

    @Composable
    private fun Host(large: Boolean = true, content: @Composable () -> Unit) {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, if (large) 2f else 1f),
            LocalReduceMotion provides true, LocalShowBubbles provides false) {
            Haruman_RalphthonTheme {
                Surface(Modifier.requiredSize(if (large) 320.dp else 360.dp, 560.dp).clipToBounds().testTag(HOST)) { content() }
            }
        }
    }

    private fun show(items: List<WishItem>, period: YardPeriod = YardPeriod.MONTH, goal: Long? = 20_000L, large: Boolean = true): Events {
        val events = Events()
        compose.setContent {
            var currentPeriod by remember { mutableStateOf(period) }
            Host(large) {
                YardScreen(items, now, currentPeriod, goal, { events.details += it.id }, { events.decisions += it.id },
                    { events.goals++ }, { events.periods += it; currentPeriod = it })
            }
        }
        return events
    }

    private fun findYard(tag: String): SemanticsNodeInteraction {
        compose.onNodeWithTag("yard_screen").performScrollToNode(hasTestTag(tag))
        return compose.onNodeWithTag(tag).performScrollTo()
    }

    private fun tap(node: SemanticsNodeInteraction) {
        node.assertIsDisplayed().assertIsEnabled().assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        node.performTouchInput { click(center) }
    }

    @Test fun weekMonthNavigationAtDoubleFontHasReachableButtonsAndCannotGoIntoFuture() {
        val events = show(listOf(item("old", date(2026, 8, 20)), item("current", date(2026, 9, 29))))
        findYard("yard_next").assertIsNotEnabled()
        tap(findYard("yard_previous"))
        compose.onNodeWithTag("yard_period_title").assertTextEquals("2026년 8월")
        findYard("yard_previous").assertIsNotEnabled()
        tap(findYard("yard_current"))
        compose.onNodeWithTag("yard_period_title").assertTextEquals("2026년 9월")
        findYard("yard_next").assertIsNotEnabled()
        tap(findYard("yard_period_week"))
        compose.onNodeWithTag("yard_period_title").assertTextEquals(yardPeriodLabel(periodOf(now, YardPeriod.WEEK), YardPeriod.WEEK))
        findYard("yard_next").assertIsNotEnabled()
        screenshot("yard_week_navigation_320_font2")
        tap(findYard("yard_period_month"))
        compose.runOnIdle { assertEquals(listOf(YardPeriod.WEEK, YardPeriod.MONTH), events.periods) }
    }

    @Test fun crowdedDateShowsOverflowAndDaySheetKeepsReadyDecisionActionUsable() {
        val day = date(2026, 9, 29)
        val events = show((1..4).map { item("ready$it", day + it * 60_000L).copy(cooldownUntil = now - 1) }, YardPeriod.WEEK)
        val cell = findYard("yard_day_$day")
        cell.assertContentDescriptionEquals("9월 29일, 오늘, 상품 4개, 결정할 새싹 4개, 날짜의 상품 보기")
        compose.onNodeWithText("+1", useUnmergedTree = true).assertExists()
        tap(cell)
        compose.onNodeWithTag("yard_day_sheet").assertIsDisplayed()
        compose.onNodeWithTag("yard_decide_ready1").performScrollTo()
        screenshot("yard_day_decision_320_font2", "yard_day_sheet")
        tap(compose.onNodeWithTag("yard_decide_ready1"))
        compose.runOnIdle { assertEquals(listOf("ready1"), events.decisions); assertTrue(events.details.isEmpty()) }
        compose.onNodeWithTag("yard_day_sheet").assertDoesNotExist()
    }

    @Test fun gardenAndBudgetUseDifferentDatesAndExcludeSampleAndUnknownAmounts() {
        val records = listOf(
            item("old-buy", date(2026, 8, 31)).copy(name = "지난달 심은 상품", decision = Decision.BUY, decidedAt = date(2026, 9, 2), decisionPriceWon = 10_000),
            item("unknown-tree", date(2026, 9, 2)).copy(decision = Decision.SKIP, decidedAt = now, decisionPriceWon = null),
            item("free-buy", date(2026, 9, 3)).copy(decision = Decision.BUY, decidedAt = now, decisionPriceWon = 0),
            item("unknown-buy", date(2026, 9, 3)).copy(decision = Decision.BUY, decidedAt = now, decisionPriceWon = null),
            item("sample-tree", date(2026, 9, 2)).copy(decision = Decision.SKIP, decidedAt = now, decisionPriceWon = 900_000, isSample = true),
        )
        val events = show(records)
        findYard("yard_summary")
        compose.onNodeWithText("이 기간에 담은 3개 상품").assertExists()
        compose.onNodeWithText("나무 1그루 · 금액 미정").assertExists()
        compose.onNodeWithText("구덩이 2개 · 0원 구매 결정").assertExists()
        compose.onNodeWithText("금액 미정: 나무 1개 · 구덩이 1개\n금액을 아는 상품만 합산했어요").assertExists()
        tap(findYard("yard_storage_open"))
        compose.onNodeWithTag("storage_room_sheet").assertIsDisplayed()
        compose.onNodeWithText("3개 · 10,000원").assertExists()
        compose.onNodeWithText("금액 미정 1개는 합계에서 뺐어요").assertExists()
        compose.onNodeWithText("지난달 심은 상품").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(listOf("old-buy"), events.details) }
    }

    @Test fun filtersKeepPendingSeparateAndExplicitGoalEditIsReachable() {
        val records = listOf(
            item("waiting", date(2026, 9, 3)).copy(name = "조금 더 기다리는 상품", cooldownUntil = now + 86_400_000),
            item("bought", date(2026, 9, 4)).copy(name = "이미 결정한 상품", decision = Decision.BUY, decidedAt = now, decisionPriceWon = 1_000),
        )
        val events = show(records, goal = null)
        tap(findYard("yard_goal_edit"))
        compose.runOnIdle { assertEquals(1, events.goals) }
        tap(findYard("yard_filter_pending"))
        compose.onNodeWithTag("yard_screen").performScrollToNode(hasText("조금 더 기다리는 상품"))
        compose.onNodeWithText("조금 더 기다리는 상품").assertExists()
        compose.onNodeWithText("이미 결정한 상품").assertDoesNotExist()
        compose.onNodeWithTag("yard_decide_waiting").assertDoesNotExist()
        tap(findYard("yard_filter_buy"))
        compose.onNodeWithTag("yard_screen").performScrollToNode(hasText("이미 결정한 상품"))
        compose.onNodeWithText("이미 결정한 상품").assertExists()
        compose.onNodeWithText("조금 더 기다리는 상품").assertDoesNotExist()
    }

    @Test fun storageRoomUsesDecisionSnapshotAndLeavesUnknownAndExamplesOutOfMoney() {
        var clicked: String? = null
        var dismissed = 0
        var records by mutableStateOf(listOf(
            item("known", now).copy(name = "가격을 나중에 고친 상품", priceWon = 999_000, decision = Decision.BUY, decidedAt = now, decisionPriceWon = 5_000),
            item("unknown", now).copy(decision = Decision.BUY, decidedAt = now),
            item("sample-room", now).copy(name = "합계에서 빠질 예시", isSample = true, decision = Decision.BUY, decidedAt = now, decisionPriceWon = 100_000),
        ))
        compose.setContent {
            Host {
                StorageRoomSheet(records, "2026년 9월", { clicked = it.id }, { dismissed++ })
            }
        }
        compose.onNodeWithText("2개 · 5,000원").assertExists()
        compose.onNodeWithText("금액 미정 1개는 합계에서 뺐어요").assertExists()
        compose.onNodeWithText("합계에서 빠질 예시").assertDoesNotExist()
        compose.onNodeWithText("가격을 나중에 고친 상품").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("known", clicked) }
        compose.runOnIdle { records = records.filterNot { it.id == "known" } }
        compose.onNodeWithText("1개 · 금액 미정").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("1개 · 0원").assertDoesNotExist()
        val close = compose.onNodeWithTag("storage_room_close").performScrollTo()
        screenshot("storage_close_320_font2", "storage_room_sheet")
        tap(close)
        compose.runOnIdle { assertEquals(1, dismissed) }
    }

    @Test fun onlyUnknownPurchaseShowsUnknownInGardenBudgetAndRoomInsteadOfZero() {
        show(listOf(item("unknown-only", date(2026, 9, 2)).copy(decision = Decision.BUY, decidedAt = now, decisionPriceWon = null)))
        findYard("yard_summary")
        compose.onNodeWithText("구덩이 1개 · 금액 미정 구매 결정").assertExists()
        findYard("yard_storage_open")
        compose.onNodeWithText("금액 미정").assertExists()
        tap(compose.onNodeWithTag("yard_storage_open"))
        compose.onNodeWithText("1개 · 금액 미정").assertExists()
        compose.onNodeWithText("1개 · 0원").assertDoesNotExist()
    }

    @Test fun fiveCheekStagesRenderDistinctArtAndExposeExactCountWithGoalWarning() {
        var count by mutableStateOf(0)
        var clicks = 0
        compose.setContent { Host(large = false) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CheekMoa(count, Modifier.testTag("cheek"), onClick = { clicks++ }, overGoal = count == 10)
            }
        } }
        val images = mutableListOf<Bitmap>()
        listOf(0, 2, 5, 9, 10).forEach { next ->
            compose.runOnIdle { count = next }
            val node = compose.onNodeWithTag("cheek")
            node.assertContentDescriptionEquals("모아의 볼주머니, 고민 중인 물건 ${next}개" + if (next == 10) ", 모두 사기로 하면 소비 목표를 넘어요" else "")
            val bitmap = node.captureToImage().asAndroidBitmap()
            assertTrue("Cheek stage $next reused another stage's rendered art", images.none { it.sameAs(bitmap) })
            images += bitmap
            screenshot("cheek_$next")
            tap(node)
        }
        compose.runOnIdle { assertEquals(5, clicks) }
    }

    private fun screenshot(name: String, tag: String = HOST) {
        val bitmap = compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "yard-layout-qa")
        assertTrue(dir.isDirectory || dir.mkdirs())
        File(dir, "$name.png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        File(dir, "$name.txt").writeText("Isolated Compose fixture, reduced motion, local data only. The calendar/day fixture is 320dp/font2; cheek fixture is360dp/font1. A modal sheet uses the device window and inherits font2. No application preferences changed.\n")
    }

    private fun item(id: String, created: Long) = WishItem(id, "상품 $id", 3_000, Platform.MANUAL, "", "bag", created, now + 60_000)
    private fun date(year: Int, month: Int, day: Int, hour: Int = 0): Long = Calendar.getInstance().apply {
        clear(); set(year, month - 1, day, hour, 0, 0)
    }.timeInMillis
    companion object { private const val HOST = "yard_test_host" }
}
