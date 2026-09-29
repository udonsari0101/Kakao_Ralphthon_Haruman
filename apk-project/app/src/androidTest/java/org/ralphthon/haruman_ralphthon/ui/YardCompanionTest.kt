package org.ralphthon.haruman_ralphthon.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.ralphthon.haruman_ralphthon.data.*
import org.ralphthon.haruman_ralphthon.ui.mascot.LocalReduceMotion
import org.ralphthon.haruman_ralphthon.ui.mascot.LocalShowBubbles
import org.ralphthon.haruman_ralphthon.ui.theme.Haruman_RalphthonTheme
import java.io.File
import java.util.Calendar

/** UI-only records and removal callbacks; production persistence is covered in PitRemovalFlowTest. */
@RunWith(AndroidJUnit4::class)
class YardCompanionTest {
    @get:Rule val compose = createComposeRule()
    private val now = date(2026, 9, 29, 12)

    private class Events(initial: List<WishItem>, font: Float, width: Int) {
        var records by mutableStateOf(initial)
        var dark by mutableStateOf(false)
        var bubbles by mutableStateOf(true)
        var fontScale by mutableFloatStateOf(font)
        var widthDp by mutableIntStateOf(width)
        var period by mutableStateOf(YardPeriod.MONTH)
        var removalFails = false
        val removals = mutableListOf<String>()
        val details = mutableListOf<WishItem>()
        val decisions = mutableListOf<String>()
        var reviews = 0
        var goalTaps = 0
    }

    private fun show(items: List<WishItem> = emptyList(), font: Float = 2f, width: Int = 320,
        restoration: StateRestorationTester? = null): Events {
        val events = Events(items, font, width)
        val content: @Composable () -> Unit = {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, events.fontScale),
                LocalReduceMotion provides true, LocalShowBubbles provides events.bubbles) {
                Haruman_RalphthonTheme(darkTheme = events.dark) {
                    Surface(Modifier.requiredSize(events.widthDp.dp, 560.dp).clipToBounds().testTag(HOST)) {
                        YardScreen(events.records, now, events.period, null,
                            onItem = { events.details += it }, onDecide = { events.decisions += it.id },
                            onGoalEdit = { events.goalTaps++ }, onPeriodChange = { events.period = it }, onReview = { events.reviews++ },
                            onRemovePit = { target ->
                                events.removals += target.id
                                if (events.removalFails) false else {
                                    events.records = events.records.map { if (it.id == target.id) it.copy(pitRemovedAt = now) else it }
                                    true
                                }
                            })
                    }
                }
            }
        }
        if (restoration == null) compose.setContent(content) else restoration.setContent(content)
        return events
    }

    @Test fun emptyYardKeepsHappyToriWithoutReadyProductsOrSpeechBubblesInBothThemes() {
        val events = show()
        findYard("yard_companion").assertIsDisplayed()
        yardNode("yard_tori_happy").assertIsDisplayed()
        yardNode("yard_tori_disappointed").assertDoesNotExist()
        yardNode("yard_tori_review").assertDoesNotExist()
        capture("happy_light_320_font2")
        compose.runOnIdle { events.bubbles = false; events.dark = true }
        findYard("yard_companion").assertIsDisplayed()
        yardNode("yard_tori_happy").assertIsDisplayed()
        yardNode("yard_companion_pit").assertDoesNotExist()
        yardText("구덩이가 없어요! 토리가 신났어요").assertDoesNotExist()
        capture("happy_dark_no_bubbles_320_font2")
        compose.runOnIdle {
            assertTrue(events.records.isEmpty()); assertTrue(events.details.isEmpty())
            assertTrue(events.decisions.isEmpty()); assertEquals(0, events.reviews)
        }
    }

    @Test fun examplePurchaseDoesNotCreateARealPitOrDisappointedCompanion() {
        val sample = bought("sample-pit", date(2026, 9, 10)).copy(isSample = true)
        val events = show(listOf(sample))
        compose.runOnIdle { events.bubbles = false }
        findYard("yard_companion").assertIsDisplayed()
        yardNode("yard_tori_happy").assertIsDisplayed()
        yardNode("yard_tori_disappointed").assertDoesNotExist()
        yardNode("yard_companion_pit").assertDoesNotExist()
        compose.onNodeWithTag("yard_pit_notice").assertDoesNotExist()
        compose.runOnIdle { assertEquals(listOf(sample), events.records); assertTrue(events.details.isEmpty()) }
    }

    @Test fun pitRemovalCommitsOnceRestoresCompletionAndPreservesThePurchaseRecord() {
        val record = bought("pit", date(2026, 9, 29, 1)).copy(priceWon = 999_000L, decisionPriceWon = 19_000L)
        val restoration = StateRestorationTester(compose)
        val events = show(listOf(record), restoration = restoration)
        findYard("yard_companion").assertIsDisplayed()
        yardNode("yard_tori_disappointed").assertIsDisplayed()
        yardNode("yard_tori_happy").assertDoesNotExist()
        yardNode("yard_tori_review").assertDoesNotExist()
        yardNode("yard_companion_pit").assertDoesNotExist()
        capture("disappointed_light_320_font2")
        findYard("terrace_plant_pit").performTouchInput { click(center) }
        capture("pit_notice_light_320_font2", "yard_pit_notice")
        checkNoticeAndActions("pit_notice_light_320_font2")
        tap(dialogAction("yard_pit_close"))
        compose.onNodeWithTag("yard_pit_notice").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(listOf(record), events.records)
            assertTrue(events.details.isEmpty()); assertTrue(events.decisions.isEmpty())
            assertEquals(0, events.reviews); assertEquals(0, events.goalTaps)
            events.dark = true; events.bubbles = false
        }
        findYard("yard_companion").assertIsDisplayed()
        yardNode("yard_tori_disappointed").assertIsDisplayed()
        yardNode("yard_companion_pit").assertDoesNotExist()
        yardText("아앗… 구덩이가 생겼어요…").assertDoesNotExist()
        capture("disappointed_dark_no_bubbles_320_font2")
        // An individual BUY plant opens the same notice rather than navigating immediately.
        findYard("terrace_plant_pit").performTouchInput { click(center) }
        capture("pit_notice_dark_320_font2", "yard_pit_notice")
        checkNoticeAndActions("pit_notice_dark_320_font2")
        compose.runOnIdle { assertTrue(events.details.isEmpty()); assertEquals(listOf(record), events.records) }
        dialogAction("yard_pit_buy").assertTextEquals("구매하기")
        // Invoke the same in-flight click twice before recomposition: completion remains idempotent.
        val purchase = dialogAction("yard_pit_buy").fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
        compose.runOnIdle { purchase(); purchase() }
        compose.onNodeWithTag("yard_pit_notice").assertDoesNotExist()
        compose.onNodeWithTag("yard_pit_complete").assertIsDisplayed()
        compose.onNodeWithText("구매 완료").assertIsDisplayed()
        compose.onNode(hasText("구덩이를 깔끔하게 정리했어요.", substring = true)).assertIsDisplayed()
        capture("pit_removed_complete_dark_320_font2", "yard_pit_complete")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("yard_pit_complete").assertIsDisplayed()
        compose.onNodeWithTag("yard_pit_buy").assertDoesNotExist()
        tap(dialogAction("yard_pit_complete_close"))
        compose.onNodeWithTag("yard_pit_complete").assertDoesNotExist()
        findYard("yard_companion").assertIsDisplayed()
        yardNode("yard_tori_happy").assertIsDisplayed()
        yardNode("terrace_plant_pit").assertDoesNotExist()
        tap(findYard("yard_period_week"))
        findYard("yard_companion").assertIsDisplayed()
        yardNode("yard_tori_happy").assertIsDisplayed()
        yardNode("terrace_plant_pit").assertDoesNotExist()
        findYard("yard_summary").assertIsDisplayed()
        yardText("구덩이 0 · 구매").assertExists()
        tap(findYard("yard_period_month"))
        findYard("yard_companion").assertIsDisplayed()
        yardNode("yard_tori_happy").assertIsDisplayed()
        compose.runOnIdle {
            assertTrue(events.details.isEmpty())
            assertEquals(listOf("pit"), events.removals)
            assertEquals(record, events.records.single().copy(pitRemovedAt = null))
            assertEquals(now, events.records.single().pitRemovedAt)
            assertEquals(999_000L, events.records.single().priceWon)
            assertEquals(19_000L, events.records.single().decisionPriceWon)
            assertEquals(Decision.BUY, events.records.single().decision)
            assertTrue(events.decisions.isEmpty()); assertEquals(0, events.reviews)
        }
    }

    @Test fun companionTracksTheSelectedMonthsFullTerraceWeeksRatherThanAllHistory() {
        val oldPit = bought("old-pit", date(2026, 8, 20))
        val currentMound = item("current", date(2026, 9, 29))
        val events = show(listOf(oldPit, currentMound), font = 1f, width = 360)
        findYard("yard_companion").assertIsDisplayed()
        yardNode("yard_tori_happy").assertIsDisplayed()
        tap(findYard("yard_previous"))
        yardNode("yard_period_title").assertTextEquals("2026년 8월")
        findYard("yard_companion").assertIsDisplayed()
        yardNode("yard_tori_disappointed").assertIsDisplayed()
        tap(findYard("yard_next"))
        yardNode("yard_period_title").assertTextEquals("2026년 9월")
        findYard("yard_companion").assertIsDisplayed()
        yardNode("yard_tori_happy").assertIsDisplayed()
        // September's first visible terrace starts on August 31, so that neighboring date counts.
        compose.runOnIdle { events.records = listOf(oldPit.copy(createdAt = date(2026, 8, 31)), currentMound) }
        findYard("yard_companion").assertIsDisplayed()
        yardNode("yard_tori_disappointed").assertIsDisplayed()
        yardNode("yard_companion_pit").assertDoesNotExist()
        findYard("terrace_plant_old-pit").performTouchInput { click(center) }
        tap(dialogAction("yard_pit_buy"))
        compose.onNodeWithText("구매 완료").assertIsDisplayed()
        tap(dialogAction("yard_pit_complete_close"))
        compose.runOnIdle {
            assertTrue(events.details.isEmpty())
            assertEquals(date(2026, 8, 31), events.records.first { it.id == "old-pit" }.createdAt)
            assertEquals(19_000L, events.records.first { it.id == "old-pit" }.decisionPriceWon)
            assertNotNull(events.records.first { it.id == "old-pit" }.pitRemovedAt)
            assertTrue(events.decisions.isEmpty())
        }
    }

    @Test fun failedRemovalKeepsThePitAndAllowsRetryWithoutShowingCompletion() {
        val record = bought("retry-pit", date(2026, 9, 29, 1))
        val events = show(listOf(record), font = 1f, width = 360)
        events.removalFails = true
        findYard("terrace_plant_retry-pit").performTouchInput { click(center) }
        tap(dialogAction("yard_pit_buy"))
        compose.onNodeWithTag("yard_pit_error").assertIsDisplayed()
        compose.onNodeWithTag("yard_pit_complete").assertDoesNotExist()
        compose.runOnIdle { assertEquals(listOf(record), events.records); events.removalFails = false }
        tap(dialogAction("yard_pit_buy"))
        compose.onNodeWithTag("yard_pit_complete").assertIsDisplayed()
        tap(dialogAction("yard_pit_complete_close"))
        findYard("yard_companion").assertIsDisplayed()
        yardNode("yard_tori_happy").assertIsDisplayed()
        compose.runOnIdle { assertTrue(events.details.isEmpty()); assertTrue(events.decisions.isEmpty()) }
    }

    @Test fun toriCyclesContextualSpeechBesideItsTailAtDoubleFontAndRespectsBubblesOff() {
        val events = show()
        fun checkPhrases(expected: List<String>, context: String) {
            expected.forEachIndexed { index, phrase ->
                findYard("yard_companion").assertIsDisplayed()
                val text = compose.onNodeWithTag("yard_tori_phrase", useUnmergedTree = true)
                text.assertTextEquals(phrase)
                assertTextFits(text, "${context}_phrase_$index", HOST)
                val bubble = yardNode("yard_tori_bubble").fetchSemanticsNode().boundsInRoot
                val tori = yardNode("yard_tori_talk").fetchSemanticsNode().boundsInRoot
                val host = compose.onNodeWithTag(HOST).fetchSemanticsNode().boundsInRoot
                assertTrue("Bubble must stay on Tori's left", bubble.left < tori.left)
                assertTrue("Tail must reach the transparent edge of Tori", bubble.right >= tori.left && bubble.right < tori.right)
                assertEquals("Bubble tail must point at Tori's center", bubble.center.y, tori.center.y, 1f)
                assertTrue("Companion must stay inside the narrow host", bubble.left >= host.left - 1 && tori.right <= host.right + 1)
                val talk = yardNode("yard_tori_talk")
                assertEquals("토리의 다른 이야기 듣기", talk.fetchSemanticsNode().config[SemanticsActions.OnClick].label)
                tap(talk)
            }
            compose.onNodeWithTag("yard_tori_phrase", useUnmergedTree = true).assertTextEquals(expected.first())
        }
        checkPhrases(listOf("구덩이가 없어요! 토리가 신났어요", "포근한 마당이라 기분이 좋아요", "오늘도 만나서 반가워요"), "happy")
        val record = bought("talk-pit", date(2026, 9, 29, 1))
        compose.runOnIdle { events.records = listOf(record); events.dark = true }
        checkPhrases(listOf("아앗… 구덩이가 생겼어요…", "조금 아쉽지만 괜찮아요", "다음에도 같이 살펴봐요"), "disappointed_dark")
        compose.runOnIdle { events.bubbles = false }
        findYard("yard_companion").assertIsDisplayed()
        val hiddenTalk = yardNode("yard_tori_talk")
        assertEquals("토리와 인사하기", hiddenTalk.fetchSemanticsNode().config[SemanticsActions.OnClick].label)
        tap(hiddenTalk)
        yardNode("yard_tori_bubble").assertDoesNotExist()
        compose.onNodeWithTag("yard_pit_notice").assertDoesNotExist()
        compose.runOnIdle {
            assertFalse(events.bubbles)
            assertEquals(listOf(record), events.records)
            assertTrue(events.details.isEmpty()); assertTrue(events.decisions.isEmpty())
            events.bubbles = true
        }
        compose.onNodeWithTag("yard_tori_phrase", useUnmergedTree = true).assertTextEquals("아앗… 구덩이가 생겼어요…")
    }

    private fun checkNoticeAndActions(artifact: String) {
        compose.onNodeWithTag("yard_pit_notice").assertIsDisplayed()
        val title = compose.onNodeWithText("구덩이 제거는 500원이에요")
        val explanation = compose.onNode(hasText("구매 기록과 금액은 그대로 남겨요.", substring = true))
        assertTextFits(title, "${artifact}_title"); assertTextFits(explanation, "${artifact}_body")
        dialogAction("yard_pit_close").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        dialogAction("yard_pit_buy").assertIsDisplayed().assertHeightIsAtLeast(48.dp).assertTextEquals("구매하기")
    }

    private fun assertTextFits(node: SemanticsNodeInteraction, artifact: String, containerTag: String = "yard_pit_notice") {
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val bounds = node.fetchSemanticsNode().boundsInRoot
        val dialogBounds = compose.onNodeWithTag(containerTag).fetchSemanticsNode().boundsInRoot
        val diagnostic = buildString {
            appendLine("nodeBoundsInRoot=$bounds; dialogBoundsInRoot=$dialogBounds; resultCount=${layouts.size}")
            layouts.forEach { layout ->
                appendLine("text=${layout.layoutInput.text.text}")
                appendLine("layoutSizePx=${layout.size}; paragraphWidthPx=${layout.multiParagraph.width}; paragraphHeightPx=${layout.multiParagraph.height}")
                appendLine("didOverflowWidth=${layout.didOverflowWidth}; didOverflowHeight=${layout.didOverflowHeight}; hasVisualOverflow=${layout.hasVisualOverflow}; didExceedMaxLines=${layout.multiParagraph.didExceedMaxLines}")
                appendLine("constraints=${layout.layoutInput.constraints}; maxLines=${layout.layoutInput.maxLines}; softWrap=${layout.layoutInput.softWrap}; overflow=${layout.layoutInput.overflow}")
                appendLine("density=${layout.layoutInput.density.density}; fontScale=${layout.layoutInput.density.fontScale}")
                for (line in 0 until layout.lineCount) {
                    appendLine("line[$line]: start=${layout.getLineStart(line)}; end=${layout.getLineEnd(line)}; visibleEnd=${layout.getLineEnd(line, true)}; ellipsized=${layout.isLineEllipsized(line)}; left=${layout.getLineLeft(line)}; right=${layout.getLineRight(line)}; top=${layout.getLineTop(line)}; bottom=${layout.getLineBottom(line)}; baseline=${layout.getLineBaseline(line)}")
                }
                for (offset in 0 until minOf(layout.layoutInput.text.length, 256)) {
                    appendLine("glyph[$offset]=${layout.layoutInput.text.text[offset]}; bounds=${runCatching { layout.getBoundingBox(offset) }.getOrNull()}")
                }
            }
            appendLine("Assertions check actual line/glyph spans, all characters, ellipsis, text height and dialog containment with 1px rounding allowance. Semantics may reconstruct a wider centered paragraph than the measured text box.")
        }
        // Persist the failing view and measured evidence before any fit assertion.
        capture(artifact, containerTag, diagnostic)
        node.assertIsDisplayed()
        assertTrue("Dialog text has no layout evidence", layouts.isNotEmpty())
        val allowance = 1f
        assertTrue("Text leaves dialog horizontally: $artifact", bounds.left >= dialogBounds.left - allowance && bounds.right <= dialogBounds.right + allowance)
        assertTrue("Text leaves dialog vertically: $artifact", bounds.top >= dialogBounds.top - allowance && bounds.bottom <= dialogBounds.bottom + allowance)
        layouts.forEach { layout ->
            val label = "${layout.layoutInput.text.text} (see $artifact.txt)"
            assertTrue("Ancestor clips text width: $label", bounds.width + allowance >= layout.size.width)
            assertTrue("Ancestor clips text height: $label", bounds.height + allowance >= layout.size.height)
            assertFalse("Text exceeds max lines: $label", layout.multiParagraph.didExceedMaxLines)
            assertTrue("No text lines: $label", layout.lineCount > 0)
            assertEquals("First characters are missing: $label", 0, layout.getLineStart(0))
            assertEquals("Last characters are missing: $label", layout.layoutInput.text.length, layout.getLineEnd(layout.lineCount - 1))
            assertTrue("Paragraph exceeds measured height: $label", layout.multiParagraph.height <= layout.size.height + allowance)
            // Match VisualLayoutTest: inspect ink span rather than reconstructed paragraph width.
            for (line in 0 until layout.lineCount) {
                assertFalse("Dialog text is ellipsized: $label", layout.isLineEllipsized(line))
                if (line > 0) assertEquals("Characters missing before line $line: $label", layout.getLineEnd(line - 1), layout.getLineStart(line))
                val span = layout.getLineRight(line) - layout.getLineLeft(line)
                assertTrue("Line $line width $span exceeds ${layout.size.width}: $label", span <= layout.size.width + allowance)
                assertTrue("Line $line is above text box: $label", layout.getLineTop(line) >= -allowance)
                assertTrue("Line $line is below text box: $label", layout.getLineBottom(line) <= layout.size.height + allowance)
                val boxes = (layout.getLineStart(line) until layout.getLineEnd(line, true))
                    .filterNot { layout.layoutInput.text.text[it] == '\n' || layout.layoutInput.text.text[it] == '\r' }
                    .map { layout.getBoundingBox(it) }
                if (boxes.isNotEmpty()) {
                    val inkSpan = boxes.maxOf { it.right } - boxes.minOf { it.left }
                    assertTrue("Line $line glyph width $inkSpan exceeds ${layout.size.width}: $label", inkSpan <= layout.size.width + allowance)
                    assertTrue("Line $line glyphs exceed top: $label", boxes.minOf { it.top } >= -allowance)
                    assertTrue("Line $line glyphs exceed bottom: $label", boxes.maxOf { it.bottom } <= layout.size.height + allowance)
                }
            }
        }
    }

    private fun dialogAction(tag: String): SemanticsNodeInteraction {
        val node = compose.onNodeWithTag(tag)
        // Some OS dialog windows are shorter. A hidden action must have a usable scroll path.
        if (runCatching { node.assertIsDisplayed() }.isFailure) node.performScrollTo()
        return node
    }

    private fun findYard(tag: String): SemanticsNodeInteraction {
        compose.onNodeWithTag("yard_screen").performScrollToNode(hasTestTag(tag))
        return yardNode(tag).performScrollTo()
    }
    private fun yardNode(tag: String) = compose.onNode(hasTestTag(tag) and hasAnyAncestor(hasTestTag("yard_screen")), useUnmergedTree = true)
    private fun yardText(text: String) = compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag("yard_screen")))
    private fun tap(node: SemanticsNodeInteraction) {
        node.assertIsDisplayed().assertIsEnabled().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        node.performTouchInput { click(center) }
    }
    private fun capture(name: String, tag: String = HOST, diagnostic: String = "") {
        val bitmap = compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "yard-companion-qa")
        assertTrue(directory.isDirectory || directory.mkdirs())
        File(directory, "$name.png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        File(directory, "$name.txt").writeText("In-memory Compose host: 320dp/font2 unless the test says 360dp/font1; reduced motion. Dialogs use the actual device window and OS font scale; the text evidence records the observed scale separately. No production store, network, payment or device settings are accessed.\n" + diagnostic)
    }
    private fun item(id: String, created: Long) = WishItem(id, "검수 상품 $id", 19_000L, Platform.MANUAL, "", "default", created, now + HOUR_MILLIS)
    private fun bought(id: String, created: Long) = item(id, created).copy(decision = Decision.BUY, decidedAt = now, decisionPriceWon = 19_000L)
    private fun date(year: Int, month: Int, day: Int, hour: Int = 0): Long = Calendar.getInstance().apply {
        clear(); set(year, month - 1, day, hour, 0, 0)
    }.timeInMillis
    companion object { private const val HOST = "yard_companion_test_host" }
}
