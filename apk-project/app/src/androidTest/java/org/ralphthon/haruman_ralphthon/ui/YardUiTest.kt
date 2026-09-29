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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
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
    private class Events(initialLarge: Boolean, initialFontScale: Float) {
        val decisions = mutableListOf<String>(); val details = mutableListOf<String>(); val periods = mutableListOf<YardPeriod>()
        var goals = 0; var reviews = 0
        var large by mutableStateOf(initialLarge)
        var fontScale by mutableStateOf(initialFontScale)
    }

    @Composable
    private fun Host(large: Boolean = true, width: Dp = if (large) 320.dp else 360.dp,
                     fontScale: Float = if (large) 2f else 1f, content: @Composable () -> Unit) {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale),
            LocalReduceMotion provides true, LocalShowBubbles provides false) {
            Haruman_RalphthonTheme {
                Surface(Modifier.requiredSize(width, 560.dp).clipToBounds().testTag(HOST)) { content() }
            }
        }
    }

    private fun show(items: List<WishItem>, period: YardPeriod = YardPeriod.MONTH, goal: Long? = 20_000L,
                     large: Boolean = true, fontScale: Float = if (large) 2f else 1f): Events {
        val events = Events(large, fontScale)
        compose.setContent {
            var currentPeriod by remember { mutableStateOf(period) }
            Host(events.large, fontScale = events.fontScale) {
                YardScreen(items, now, currentPeriod, goal, { events.details += it.id }, { events.decisions += it.id },
                    { events.goals++ }, { events.periods += it; currentPeriod = it }, onReview = { events.reviews++ })
            }
        }
        return events
    }

    private fun findYard(tag: String): SemanticsNodeInteraction {
        compose.onNodeWithTag("yard_screen").performScrollToNode(hasTestTag(tag))
        return yardNode(tag).performScrollTo()
    }

    // HorizontalPager may keep neighbors composed. Only the current page is a user target.
    private fun yardNode(tag: String) = compose.onNode(hasTestTag(tag) and hasAnyAncestor(hasTestTag("yard_screen")))
    private fun yardText(text: String) = compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag("yard_screen")))
    private fun findYardText(text: String): SemanticsNodeInteraction {
        compose.onNodeWithTag("yard_screen").performScrollToNode(hasText(text))
        return yardText(text).performScrollTo()
    }
    private fun summaryText(text: String) = compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag("yard_summary"))
        and hasAnyAncestor(hasTestTag("yard_screen")))
    private fun findWeekSheet(tag: String): SemanticsNodeInteraction {
        compose.onNodeWithTag("yard_week_sheet").performScrollToNode(hasTestTag(tag))
        return compose.onNode(hasTestTag(tag) and hasAnyAncestor(hasTestTag("yard_week_sheet"))).performScrollTo()
    }
    private fun findWeekText(text: String): SemanticsNodeInteraction {
        compose.onNodeWithTag("yard_week_sheet").performScrollToNode(hasText(text))
        return compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag("yard_week_sheet"))).performScrollTo()
    }
    private fun openWeek(start: Long) {
        val floor = findYard("terrace_week_open_$start")
        floor.assertIsDisplayed().assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        // Touch the date/soil area, away from overlapping foreground plant hit targets.
        floor.performTouchInput { click(Offset(center.x, height.toFloat() - 2f)) }
        compose.onNodeWithTag("yard_week_sheet").assertIsDisplayed()
    }

    private fun tap(node: SemanticsNodeInteraction) {
        node.assertIsDisplayed().assertIsEnabled().assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        node.performTouchInput { click(center) }
    }

    @Test fun weekMonthNavigationAtDoubleFontHasReachableButtonsAndCannotGoIntoFuture() {
        val events = show(listOf(item("old", date(2026, 8, 20)), item("current", date(2026, 9, 29))))
        findYard("yard_next").assertIsNotEnabled()
        tap(findYard("yard_previous"))
        yardNode("yard_period_title").assertTextEquals("2026년 8월")
        findYard("yard_previous").assertIsNotEnabled()
        tap(findYard("yard_current"))
        yardNode("yard_period_title").assertTextEquals("2026년 9월")
        findYard("yard_next").assertIsNotEnabled()
        tap(findYard("yard_period_week"))
        yardNode("yard_period_title").assertTextEquals(yardPeriodLabel(periodOf(now, YardPeriod.WEEK), YardPeriod.WEEK))
        findYard("yard_next").assertIsNotEnabled()
        screenshot("yard_week_navigation_320_font2")
        tap(findYard("yard_period_month"))
        compose.runOnIdle { assertEquals(listOf(YardPeriod.WEEK, YardPeriod.MONTH), events.periods) }
    }

    @Test fun thirtySevenItemTerracesKeepWeekOrderAndSixPlusOverflowAccessibleAtFont13() {
        val records = terraceFixture()
        assertEquals(37, records.size)
        val events = show(records, large = false, fontScale = 1.3f)
        val starts = listOf(date(2026, 8, 31), date(2026, 9, 7), date(2026, 9, 14), date(2026, 9, 21), date(2026, 9, 28))
        var previousScroll = -1f
        starts.forEachIndexed { index, start ->
            findYard("terrace_week_$start").assertIsDisplayed()
            val scroll = compose.onNodeWithTag("yard_screen").fetchSemanticsNode()
                .config[SemanticsProperties.VerticalScrollAxisRange].value()
            assertTrue("Week ${index + 1} appeared before the previous terrace", scroll >= previousScroll)
            previousScroll = scroll
            yardNode("terrace_week_open_$start").assertContentDescriptionContains("${index + 1}주", substring = true)
        }
        val sixDay = date(2026, 9, 26)
        val six = records.filter { it.createdAt >= sixDay && it.createdAt < date(2026, 9, 27) }
        assertEquals(6, six.size)
        findYard("terrace_week_${date(2026, 9, 21)}").assertIsDisplayed()
        six.forEach { yardNode("terrace_plant_${it.id}").assertExists() }
        yardNode("terrace_overflow_$sixDay").assertDoesNotExist()
        val crowdedDay = date(2026, 9, 13)
        val week = date(2026, 9, 7)
        val crowded = records.filter { it.createdAt >= crowdedDay && it.createdAt < date(2026, 9, 14) }
        assertEquals(7, crowded.size)
        val floor = findYard("terrace_week_$week").fetchSemanticsNode().boundsInRoot
        crowded.take(6).forEach { record ->
            val plant = yardNode("terrace_plant_${record.id}").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue("Plant ${record.id} escaped its terrace", plant.left >= floor.left - 1f && plant.right <= floor.right + 1f && plant.top >= floor.top - 1f && plant.bottom <= floor.bottom + 1f)
        }
        yardNode("terrace_plant_${crowded.last().id}").assertDoesNotExist()
        yardNode("terrace_overflow_$crowdedDay").assertTextEquals("+1")
            .assertContentDescriptionEquals("이 날의 상품 1개 더 있어요. 주 상세 보기")
        // Week totals include all eight records (one on the 9th, seven on the 13th), not just six plants.
        yardNode("terrace_week_open_$week")
            .assertContentDescriptionContains("나무 6그루 63,000원 아낌", substring = true)
            .assertContentDescriptionContains("구덩이 2개 21,000원 구매 결정", substring = true)
        screenshot("terrace_crowded_360_font1_3")
        openWeek(week)
        compose.onNodeWithText("상품 8개 · 이웃한 달의 날짜도 함께 보여요").assertExists()
        val seventh = findWeekText(crowded.last().name).assertIsDisplayed()
        screenshot("terrace_week_all_items_font1_3", "yard_week_sheet")
        seventh.performClick()
        compose.runOnIdle { assertEquals(listOf(crowded.last().id), events.details); assertTrue(events.decisions.isEmpty()) }
        compose.onNodeWithTag("yard_week_sheet").assertDoesNotExist()
        // Reuse the same 37 records at the narrow accessibility size; no new setContent or device settings.
        compose.runOnIdle { events.large = true; events.fontScale = 2f }
        findYard("terrace_week_$week").assertIsDisplayed()
        yardNode("terrace_overflow_$crowdedDay").assertTextEquals("+1")
        screenshot("terrace_crowded_320_font2")
        openWeek(week)
        findWeekText(crowded.last().name).assertIsDisplayed()
        val close = findWeekSheet("yard_week_close")
        screenshot("terrace_crowded_week_close_320_font2", "yard_week_sheet")
        tap(close)
        compose.onNodeWithTag("yard_week_sheet").assertDoesNotExist()
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
        summaryText("나무 1 · 아낌").assertExists()
        summaryText("금액 미정").assertExists()
        summaryText("구덩이 2 · 구매").assertExists()
        summaryText("0원").assertExists()
        summaryText("금액 미정 2개는 금액 합계에서 뺐어요").assertExists()
        // The first September terrace also contains August 31; the monthly summary above does not.
        findYard("terrace_week_open_${date(2026, 8, 31)}")
            .assertContentDescriptionContains("구덩이 3개 10,000원 구매 결정", substring = true)
        findYardText("예시 1개는 실제 마당과 합계에서 뺐어요").assertIsDisplayed()
        tap(findYard("yard_storage_open"))
        compose.onNodeWithTag("storage_room_sheet").assertIsDisplayed()
        compose.onNodeWithText("3개 · 10,000원").assertExists()
        compose.onNodeWithText("금액 미정 1개는 합계에서 뺐어요").assertExists()
        compose.onNodeWithText("지난달 심은 상품").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(listOf("old-buy"), events.details) }
    }

    @Test fun doubleFontWeekDetailsAndToriReviewStayReachableWithoutSpeechBubbles() {
        val records = listOf(
            item("waiting", date(2026, 9, 29)).copy(name = "조금 더 기다리는 상품", cooldownUntil = now + 86_400_000),
            item("ready", date(2026, 9, 29, 1)).copy(name = "다시 생각할 상품", cooldownUntil = now - 1),
            item("bought", date(2026, 9, 28)).copy(name = "이미 결정한 상품", decision = Decision.BUY, decidedAt = now, decisionPriceWon = 1_000),
        )
        val events = show(records, period = YardPeriod.WEEK, goal = null)
        tap(findYard("yard_goal_edit"))
        compose.runOnIdle { assertEquals(1, events.goals) }
        tap(findYard("yard_tori_review"))
        compose.runOnIdle { assertEquals(1, events.reviews); assertTrue(events.decisions.isEmpty()) }
        val start = date(2026, 9, 28)
        openWeek(start)
        findWeekText("조금 더 기다리는 상품").assertIsDisplayed()
        compose.onNode(hasTestTag("yard_decide_waiting") and hasAnyAncestor(hasTestTag("yard_week_sheet"))).assertDoesNotExist()
        val decide = findWeekSheet("yard_decide_ready")
        screenshot("terrace_week_decision_320_font2", "yard_week_sheet")
        tap(decide)
        compose.runOnIdle { assertEquals(listOf("ready"), events.decisions) }
        compose.onNodeWithTag("yard_week_sheet").assertDoesNotExist()
        openWeek(start)
        val close = findWeekSheet("yard_week_close")
        screenshot("terrace_week_close_320_font2", "yard_week_sheet")
        tap(close)
        compose.onNodeWithTag("yard_week_sheet").assertDoesNotExist()
        findYardText("이미 결정한 상품").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(listOf("bought"), events.details) }
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
        summaryText("구덩이 1 · 구매").assertExists()
        summaryText("금액 미정").assertExists()
        summaryText("이달 구매 결정 · 금액 미정").assertExists()
        summaryText("이달 구매 결정 · 0원").assertDoesNotExist()
        findYard("yard_storage_open")
        tap(yardNode("yard_storage_open"))
        compose.onNodeWithText("1개 · 금액 미정").assertExists()
        compose.onNodeWithText("1개 · 0원").assertDoesNotExist()
    }

    @Test fun budgetPercentageDoesNotTruncate176PercentTo175() {
        show(listOf(item("percentage", date(2026, 9, 2)).copy(
            decision = Decision.BUY, decidedAt = now, priceWon = 88_000, decisionPriceWon = 88_000,
        )), goal = 50_000)
        findYard("yard_goal_gauge")
        findYard("yard_goal_label").assertTextEquals("목표 50,000원 · 176%").assertIsDisplayed()
        yardText("목표 50,000원 · 175%").assertDoesNotExist()
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
        File(dir, "$name.txt").writeText("Isolated Compose fixture, reduced motion, local data only. Terrace crowded fixture: 360dp/font1.3; navigation and week actions: 320dp/font2; cheeks: 360dp/font1. A modal sheet uses the device window and inherits the host font scale. No application preferences or device settings changed.\n")
    }

    /** Same 37-record day plan as the handoff, with deterministic states; never seeds LocalStore. */
    private fun terraceFixture(): List<WishItem> {
        val plan = listOf(2 to 2, 4 to 1, 6 to 3, 9 to 1, 13 to 7, 14 to 3, 16 to 2,
            19 to 2, 23 to 2, 26 to 6, 27 to 5, 29 to 2, 30 to 1)
        var index = 0
        return plan.flatMap { (day, count) -> (1..count).map { withinDay ->
            val number = ++index
            val choice = when {
                day >= 23 -> Decision.PENDING
                number % 3 == 0 -> Decision.BUY
                else -> Decision.SKIP
            }
            val price = number * 1_000L
            item("terrace-$number", date(2026, 9, day) + withinDay * 60_000L).copy(
                name = "테라스 상품 $number", priceWon = price + 1_000_000L,
                decision = choice, decidedAt = if (choice == Decision.PENDING) null else now - 1,
                decisionPriceWon = if (choice == Decision.PENDING) null else price,
                cooldownUntil = if (day in 23..27) now - 1 else now + HOUR_MILLIS,
            )
        } }
    }

    private fun item(id: String, created: Long) = WishItem(id, "상품 $id", 3_000, Platform.MANUAL, "", "bag", created, now + 60_000)
    private fun date(year: Int, month: Int, day: Int, hour: Int = 0): Long = Calendar.getInstance().apply {
        clear(); set(year, month - 1, day, hour, 0, 0)
    }.timeInMillis
    companion object { private const val HOST = "yard_test_host" }
}
