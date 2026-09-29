package org.ralphthon.haruman_ralphthon.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.ralphthon.haruman_ralphthon.data.Decision
import org.ralphthon.haruman_ralphthon.data.Desire
import org.ralphthon.haruman_ralphthon.data.Platform
import org.ralphthon.haruman_ralphthon.data.WishItem
import org.ralphthon.haruman_ralphthon.data.decide
import org.ralphthon.haruman_ralphthon.data.extend
import org.ralphthon.haruman_ralphthon.ui.mascot.LocalReduceMotion
import org.ralphthon.haruman_ralphthon.ui.mascot.LocalShowBubbles
import org.ralphthon.haruman_ralphthon.ui.theme.Haruman_RalphthonTheme

/** In-memory screen callback harness; no LocalStore, application ViewModel, or device settings. */
@RunWith(AndroidJUnit4::class)
class ReviewUiTest {
    @get:Rule val compose = createComposeRule()

    private class Events {
        val attempts = mutableListOf<ReviewAction>()
        var currentSession: ReviewSession? = null
        var currentItems = emptyList<WishItem>()
        var backCount = 0
        var undoCount = 0
        var celebrations = 0
        var shareText: String? = null
    }

    private fun showReview(
        originalItems: List<WishItem>,
        originalSession: ReviewSession = ReviewSession(ids = originalItems.map { it.id }),
        rejectSave: Boolean = false,
        compactLargeFont: Boolean = false,
    ): Events {
        val events = Events()
        compose.setContent {
            var items by remember { mutableStateOf(originalItems) }
            var session by remember { mutableStateOf(originalSession) }
            val current = session.ids.getOrNull(session.index)?.let { id -> items.find { it.id == id } }
            val actualDensity = LocalDensity.current
            SideEffect { events.currentSession = session; events.currentItems = items }
            CompositionLocalProvider(
                LocalReduceMotion provides true,
                LocalShowBubbles provides false,
                LocalDensity provides if (compactLargeFont) Density(actualDensity.density, 2f) else actualDensity,
            ) {
                Haruman_RalphthonTheme {
                    Surface(if (compactLargeFont) Modifier.requiredSize(320.dp, 420.dp) else Modifier.fillMaxSize()) {
                        ReviewScreen(
                            session = session, item = current, defaultHours = 24, now = NOW,
                            onBack = { events.backCount++ },
                            onAction = { action ->
                                events.attempts += action
                                if (rejectSave || current == null || session.complete) false
                                else {
                                    val changed = when (action) {
                                        ReviewAction.SKIP -> decide(current, Decision.SKIP, NOW)
                                        ReviewAction.BUY -> decide(current, Decision.BUY, NOW)
                                        ReviewAction.LATER -> extend(current, 24, NOW)
                                    }
                                    items = items.map { if (it.id == current.id) changed else it }
                                    session = session.copy(index = session.index + 1,
                                        results = session.results + ReviewRecord(current, changed, action), undoUntil = NOW + 4_000)
                                    true
                                }
                            },
                            onUndo = {
                                events.undoCount++
                                session.results.lastOrNull()?.let { record ->
                                    items = items.map { if (it.id == record.before.id) record.before else it }
                                    session = session.copy(index = (session.index - 1).coerceAtLeast(0), results = session.results.dropLast(1), undoUntil = 0)
                                }
                            },
                            onCelebrationConsumed = { events.celebrations++; session = session.copy(celebrationConsumed = true) },
                            onHistory = {}, onShare = { events.shareText = it },
                        )
                    }
                }
            }
        }
        return events
    }

    @Test
    fun failedSaveKeepsTheCardAndAllowsRetryWithoutAdvancing() {
        val first = ready("first")
        val events = showReview(listOf(first, ready("second")), rejectSave = true)
        compose.onNodeWithTag("review_skip").performClick()
        compose.onNodeWithTag("review_progress").assertTextEquals("1 / 2")
        compose.onNodeWithTag("review_skip").assertIsEnabled().performClick()
        compose.onNodeWithTag("review_complete").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(listOf(ReviewAction.SKIP, ReviewAction.SKIP), events.attempts)
            assertEquals(0, events.currentSession?.index)
            assertTrue(events.currentSession?.results?.isEmpty() == true)
            assertEquals(first, events.currentItems.first())
        }
    }

    @Test
    fun duplicateActionInTheSameFrameCommitsOnlyOneCard() {
        val events = showReview(listOf(ready("first"), ready("second")))
        compose.onNodeWithTag("review_skip").performSemanticsAction(SemanticsActions.OnClick) { click ->
            click()
            click()
        }
        compose.waitForIdle()
        compose.onNodeWithTag("review_progress").assertTextEquals("2 / 2")
        compose.runOnIdle {
            assertEquals(listOf(ReviewAction.SKIP), events.attempts)
            assertEquals(1, events.currentSession?.results?.size)
            assertEquals(Decision.SKIP, events.currentItems.first().decision)
            assertEquals(Decision.PENDING, events.currentItems.last().decision)
        }
    }

    @Test
    fun skipBuyAndLaterButtonsProcessTheirOwnCardsAndBackKeepsTheResults() {
        val events = showReview(listOf(ready("skip"), ready("buy"), ready("later")))
        compose.onNodeWithTag("review_skip").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("review_buy").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("review_later").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("review_complete").assertExists()
        compose.onNodeWithTag("review_home").performClick()
        compose.runOnIdle {
            assertEquals(listOf(ReviewAction.SKIP, ReviewAction.BUY, ReviewAction.LATER), events.attempts)
            assertEquals(3, events.currentSession?.index)
            assertEquals(Decision.SKIP, events.currentItems[0].decision)
            assertEquals(Decision.BUY, events.currentItems[1].decision)
            assertEquals(Decision.PENDING, events.currentItems[2].decision)
            assertEquals(NOW + 24 * 3_600_000L, events.currentItems[2].cooldownUntil)
            assertEquals(1, events.backCount)
            assertEquals(3, events.currentSession?.results?.size)
        }
    }

    @Test
    fun undoRestoresTheOriginalCardAndFinishingAgainDoesNotRepeatCelebration() {
        val before = ready("original").copy(desire = Desire.FIRE, userMemo = "다시 확인해요")
        val events = showReview(listOf(before))
        compose.onNodeWithTag("review_skip").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("review_undo").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("review_complete").assertDoesNotExist()
        compose.onNodeWithTag("review_progress").assertTextEquals("1 / 1")
        compose.runOnIdle {
            assertEquals(before, events.currentItems.single())
            assertEquals(0, events.currentSession?.index)
            assertTrue(events.currentSession?.results?.isEmpty() == true)
            assertEquals(1, events.undoCount)
            assertEquals(1, events.celebrations)
        }
        compose.onNodeWithTag("review_skip").performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(1, events.celebrations) }
    }

    @Test
    fun summaryAndExplicitShareExcludeSamplesAndUnknownAmounts() {
        val known = ready("known").copy(priceWon = 12_000)
        val unknown = ready("unknown").copy(priceWon = null)
        val sample = ready("sample-example").copy(priceWon = 900_000, isSample = true)
        val records = listOf(known, unknown, sample).map { before -> ReviewRecord(before, decide(before, Decision.SKIP, NOW), ReviewAction.SKIP) }
        val session = ReviewSession(ids = records.map { it.before.id }, index = 3, results = records, celebrationConsumed = true)
        val events = showReview(records.map { it.after }, session)
        compose.onNodeWithTag("review_total").performScrollTo().assertTextEquals("12,000원")
        compose.onNodeWithText("가격이 없는 1개 상품은 금액 합계에 포함하지 않아요").assertExists()
        compose.runOnIdle { assertEquals(null, events.shareText) }
        compose.onNodeWithTag("review_share").performScrollTo().performClick()
        compose.runOnIdle {
            val shared = requireNotNull(events.shareText)
            assertTrue(shared.contains("2개를 사지 않기로"))
            assertTrue(shared.contains("12,000원"))
            assertTrue(shared.contains("가격이 없는 1개"))
            assertTrue(shared.contains("예시 상품은 제외"))
            assertFalse(shared.contains("900,000"))
            assertTrue(events.attempts.isEmpty())
        }
    }

    @Test
    fun narrowWindowWithLargeFontCanScrollToEveryDecisionAction() {
        val events = showReview(listOf(ready("narrow").copy(name = "작은 화면에서도 충분히 살펴보고 결정할 긴 상품 이름")), compactLargeFont = true)
        compose.onNodeWithTag("review_skip").performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag("review_buy").performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag("review_later").performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("review_complete").assertExists()
        compose.runOnIdle { assertEquals(listOf(ReviewAction.LATER), events.attempts) }
    }

    private fun ready(id: String) = WishItem(
        id = id, name = "살펴볼 상품 $id", priceWon = 10_000, platform = Platform.MANUAL,
        url = "", artwork = "default", createdAt = NOW - 25 * 3_600_000L,
        cooldownUntil = NOW - 3_600_000L,
    )

    companion object { private const val NOW = 1_800_000_000_000L }
}
