package org.ralphthon.haruman_ralphthon.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.ralphthon.haruman_ralphthon.data.Platform
import org.ralphthon.haruman_ralphthon.data.ThemePreference
import org.ralphthon.haruman_ralphthon.data.WishItem
import org.ralphthon.haruman_ralphthon.ui.mascot.LocalReduceMotion
import org.ralphthon.haruman_ralphthon.ui.mascot.LocalShowBubbles
import org.ralphthon.haruman_ralphthon.ui.theme.Haruman_RalphthonTheme
import java.io.File

/**
 * Isolated layout hosts: no production ViewModel, preferences, network, or device setting writes.
 * The short host models space left above an IME; it does not open or replace the real keyboard.
 * Artifacts: PNG files in targetContext.cacheDir/visual-layout-qa plus viewport notes.
 */
@RunWith(AndroidJUnit4::class)
class VisualLayoutTest {
    @get:Rule val compose = createComposeRule()
    private var viewport = ""

    @Composable
    private fun Host(width: Dp, height: Dp, fontScale: Float = 1f, dark: Boolean = false, content: @Composable () -> Unit) {
        val density = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(density.density, fontScale),
            LocalReduceMotion provides true,
            LocalShowBubbles provides true,
        ) {
            Haruman_RalphthonTheme(darkTheme = dark) {
                Surface(Modifier.requiredSize(width, height).clipToBounds().testTag(HOST)) { content() }
            }
        }
    }

    private fun screenshot(name: String, diagnostics: String = "") {
        compose.waitForIdle()
        val bitmap = compose.onNodeWithTag(HOST).captureToImage().asAndroidBitmap()
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "visual-layout-qa")
        assertTrue("Could not create the private QA directory", directory.isDirectory || directory.mkdirs())
        File(directory, "$name.png").outputStream().use { stream ->
            assertTrue("Could not encode screenshot", bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
        }
        File(directory, "$name.txt").writeText("$viewport\npixels=${bitmap.width}x${bitmap.height}\n" +
            "Isolated Compose fixture; reduced motion; simulated viewport; no live API or real IME.\n" + diagnostics)
    }

    /** A real pointer event also detects another component consuming taps over a button. */
    private fun touch(target: SemanticsNodeInteraction) {
        target.assertIsDisplayed().assertIsEnabled().assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        val bounds = target.fetchSemanticsNode().boundsInRoot
        val host = compose.onNodeWithTag(HOST).fetchSemanticsNode().boundsInRoot
        assertTrue("Target is outside the viewport horizontally", bounds.left >= host.left - 1f && bounds.right <= host.right + 1f)
        assertTrue("Target center is outside the viewport vertically", bounds.center.y in host.top..host.bottom)
        target.performTouchInput { click(center) }
    }

    private fun assertTextFits(text: String, artifact: String): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        val node = compose.onNodeWithText(text, useUnmergedTree = true)
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        if (layouts.isEmpty()) screenshot(artifact, "text=$text\nNo TextLayoutResult was returned.\n")
        assertTrue("Missing text layout: $text", layouts.isNotEmpty())
        val layout = layouts.first()
        val bounds = node.fetchSemanticsNode().boundsInRoot
        val host = compose.onNodeWithTag(HOST).fetchSemanticsNode().boundsInRoot
        val widthDelta = layout.multiParagraph.width - layout.size.width
        val heightDelta = layout.multiParagraph.height - layout.size.height
        val diagnostic = buildString {
            appendLine("text=$text")
            appendLine("textLength=${layout.layoutInput.text.length}; resultCount=${layouts.size}")
            appendLine("nodeBoundsInRoot=$bounds; hostBoundsInRoot=$host")
            appendLine("layoutSizePx=${layout.size}; paragraphWidthPx=${layout.multiParagraph.width}; paragraphHeightPx=${layout.multiParagraph.height}")
            appendLine("paragraphMinusLayoutWidthPx=$widthDelta; paragraphMinusLayoutHeightPx=$heightDelta")
            appendLine("didOverflowWidth=${layout.didOverflowWidth}; didOverflowHeight=${layout.didOverflowHeight}; hasVisualOverflow=${layout.hasVisualOverflow}")
            appendLine("didExceedMaxLines=${layout.multiParagraph.didExceedMaxLines}; lineCount=${layout.lineCount}")
            appendLine("constraints=${layout.layoutInput.constraints}; maxLines=${layout.layoutInput.maxLines}; softWrap=${layout.layoutInput.softWrap}; overflow=${layout.layoutInput.overflow}")
            appendLine("density=${layout.layoutInput.density.density}; fontScale=${layout.layoutInput.density.fontScale}")
            appendLine("fontSize=${layout.layoutInput.style.fontSize}; lineHeight=${layout.layoutInput.style.lineHeight}; fontWeight=${layout.layoutInput.style.fontWeight}; platformStyle=${layout.layoutInput.style.platformStyle}")
            for (line in 0 until layout.lineCount) {
                appendLine("line[$line]: start=${layout.getLineStart(line)}; end=${layout.getLineEnd(line)}; visibleEnd=${layout.getLineEnd(line, true)}; ellipsized=${layout.isLineEllipsized(line)}; " +
                    "left=${layout.getLineLeft(line)}; right=${layout.getLineRight(line)}; top=${layout.getLineTop(line)}; bottom=${layout.getLineBottom(line)}; baseline=${layout.getLineBaseline(line)}")
            }
            for (offset in 0 until minOf(layout.layoutInput.text.length, 256)) {
                appendLine("glyph[$offset]=${layout.layoutInput.text.text[offset]}; bounds=${runCatching { layout.getBoundingBox(offset) }.getOrNull()}")
            }
            appendLine("Assertions use actual per-line text/glyph spans, complete character coverage, no ellipsis, and height/viewport containment with a 1px rounding allowance.")
        }
        // Always save evidence before asserting, including the state that previously failed.
        screenshot(artifact, diagnostic)
        node.assertIsDisplayed()
        val label = "$text (see $artifact.txt)"
        val allowance = 1f
        assertTrue("Text leaves the viewport horizontally: $label", bounds.left >= host.left - allowance && bounds.right <= host.right + allowance)
        assertTrue("Text leaves the viewport vertically: $label", bounds.top >= host.top - allowance && bounds.bottom <= host.bottom + allowance)
        assertTrue("An ancestor clips the text width: $label", bounds.width + allowance >= layout.size.width)
        assertTrue("An ancestor clips the text height: $label", bounds.height + allowance >= layout.size.height)
        assertFalse("Text exceeds max lines: $label", layout.multiParagraph.didExceedMaxLines)
        assertTrue("No line contains the text: $label", layout.lineCount > 0)
        assertEquals("The first characters are missing: $label", 0, layout.getLineStart(0))
        assertEquals("The last characters are missing: $label", layout.layoutInput.text.length, layout.getLineEnd(layout.lineCount - 1))
        assertTrue("Paragraph height exceeds the measured text box: $label", layout.multiParagraph.height <= layout.size.height + allowance)
        // Foundation 1.9.2's ParagraphLayoutCache.slowCreateTextLayoutResultOrNull rebuilds
        // the semantics paragraph with max constraints while retaining the smaller layoutSize.
        // A centered label can therefore have a large paragraph x origin without clipped ink.
        // Compare each line's actual span rather than that reconstructed paragraph's full width.
        for (line in 0 until layout.lineCount) {
            assertFalse("Line $line is ellipsized: $label", layout.isLineEllipsized(line))
            if (line > 0) assertEquals("Characters are missing before line $line: $label", layout.getLineEnd(line - 1), layout.getLineStart(line))
            val lineSpan = layout.getLineRight(line) - layout.getLineLeft(line)
            assertTrue("Line $line spans $lineSpan px in ${layout.size.width}px: $label", lineSpan <= layout.size.width + allowance)
            assertTrue("Line $line is above the measured text box: $label", layout.getLineTop(line) >= -allowance)
            assertTrue("Line $line is below the measured text box: $label", layout.getLineBottom(line) <= layout.size.height + allowance)
            val boxes = (layout.getLineStart(line) until layout.getLineEnd(line, true))
                .filterNot { layout.layoutInput.text.text[it] == '\n' || layout.layoutInput.text.text[it] == '\r' }
                .map { layout.getBoundingBox(it) }
            if (boxes.isNotEmpty()) {
                val inkSpan = boxes.maxOf { it.right } - boxes.minOf { it.left }
                assertTrue("Line $line glyphs span $inkSpan px in ${layout.size.width}px: $label", inkSpan <= layout.size.width + allowance)
                assertTrue("Line $line glyphs extend above the text box: $label", boxes.minOf { it.top } >= -allowance)
                assertTrue("Line $line glyphs extend below the text box: $label", boxes.maxOf { it.bottom } <= layout.size.height + allowance)
            }
        }
        return layout
    }

    @Test fun receivedHeaderKeepsItsSlotAndDoesNotInterceptNavigationButtons() {
        var receiptVisible by mutableStateOf(true)
        var completed = 0; var home = 0; var edit = 0; var detail = 0
        viewport = "360dp x 640dp; fontScale=1; light; received header"
        compose.setContent {
            Host(360.dp, 640.dp) {
                AddedScreen(item(), { home++ }, { edit++ }, { detail++ },
                    receivedEventId = if (receiptVisible) "visual-receipt" else null,
                    onReceivedFinished = { completed++ })
            }
        }
        val characterBefore = compose.onNodeWithTag("added_character").fetchSemanticsNode().boundsInRoot
        val titleBefore = compose.onNodeWithTag("added_title").fetchSemanticsNode().boundsInRoot
        assertTrue("The received character covers the title", characterBefore.bottom <= titleBefore.top)
        screenshot("added_received_slot_360")
        // Keep the event visible after its callback so controls are exercised with that slot present.
        touch(compose.onNodeWithTag("added_home").performScrollTo())
        touch(compose.onNodeWithTag("edit_added").performScrollTo())
        touch(compose.onNodeWithText("상품 자세히 보기").performScrollTo())
        screenshot("added_received_cta_360")
        compose.onNodeWithTag("added_character").performScrollTo()
        compose.runOnIdle { receiptVisible = false }
        val characterAfter = compose.onNodeWithTag("added_character").fetchSemanticsNode().boundsInRoot
        val titleAfter = compose.onNodeWithTag("added_title").fetchSemanticsNode().boundsInRoot
        assertEquals("The receipt slot changes height when it finishes", characterBefore.height, characterAfter.height, 1f)
        assertEquals("The title jumps relative to its character", titleBefore.top - characterBefore.bottom, titleAfter.top - characterAfter.bottom, 1f)
        screenshot("added_settled_slot_360")
        compose.runOnIdle { assertEquals(1, completed); assertEquals(1, home); assertEquals(1, edit); assertEquals(1, detail) }
    }

    @Test fun addedLongNameAtDoubleFontKeepsEveryActionReachable() {
        var home = 0; var edit = 0; var detail = 0
        viewport = "320dp x 420dp; fontScale=2; light; long product name"
        val longName = "오랫동안 고민해 본 따뜻한 겨울 이불과 베개 세트"
        compose.setContent {
            Host(320.dp, 420.dp, 2f) {
                AddedScreen(item().copy(name = longName), { home++ }, { edit++ }, { detail++ }, null, {})
            }
        }
        compose.onNodeWithText(longName).performScrollTo()
        assertTrue("Long product name should wrap", assertTextFits(longName, "added_long_name_320_font2").lineCount > 1)
        touch(compose.onNodeWithTag("added_home").performScrollTo())
        touch(compose.onNodeWithTag("edit_added").performScrollTo())
        touch(compose.onNodeWithText("상품 자세히 보기").performScrollTo())
        screenshot("added_cta_320_font2")
        compose.runOnIdle { assertEquals(1, home); assertEquals(1, edit); assertEquals(1, detail) }
    }

    @Test fun manualSheetFitsDoubleFontAndCanSaveWithinImeSizedViewport() {
        var saved: ProductDraft? = null
        viewport = "320dp x 360dp; fontScale=2; light; simulated keyboard-height viewport"
        compose.setContent {
            var draft by remember { mutableStateOf(ProductDraft(mode = "manual", name = "직접 입력한 상품", price = "0")) }
            Host(320.dp, 360.dp, 2f) {
                AddSheet(draft, false, null, false, { draft = it }, { draft = draft.copy(mode = it) }, {}, { saved = draft }, {})
            }
        }
        compose.onNodeWithTag("mode_manual").performScrollTo().assertIsDisplayed()
        assertTextFits("직접 입력", "manual_modes_320_font2")
        compose.onNodeWithTag("input_memo").performScrollTo()
            .performSemanticsAction(SemanticsActions.SetText) { it(AnnotatedString("다음 달에도 필요한지 천천히 생각해요")) }
        touch(compose.onNodeWithTag("cooldown_0").performScrollTo())
        compose.onNodeWithTag("cooldown_0").assertIsSelected()
        compose.onNodeWithTag("save_item").performScrollTo()
        assertTextFits("보관함에 저장", "manual_save_320_font2_short")
        touch(compose.onNodeWithTag("save_item"))
        compose.runOnIdle {
            assertEquals("다음 달에도 필요한지 천천히 생각해요", saved?.userMemo)
            assertEquals(0L, saved?.priceWon)
            assertEquals(0, saved?.hours)
        }
    }

    @Test fun linkFailureAtDoubleFontKeepsManualFallbackUsableInShortViewport() {
        var modeChanges = 0; var requests = 0
        viewport = "320dp x 360dp; fontScale=2; light; unconfigured link notice"
        val notice = "링크로 불러오는 기능은 준비 중이에요. 지금은 직접 입력해 저장할 수 있어요"
        compose.setContent {
            var draft by remember { mutableStateOf(ProductDraft(mode = "link", url = "https://www.coupang.com/vp/products/9047670772")) }
            Host(320.dp, 360.dp, 2f) {
                AddSheet(draft, false, notice, false, { draft = it }, { draft = draft.copy(mode = it); modeChanges++ }, { requests++ }, {}, {})
            }
        }
        compose.onNodeWithText(notice).performScrollTo()
        assertTextFits(notice, "link_notice_320_font2_short")
        compose.onNodeWithTag("manual_fallback").performScrollTo()
        assertTextFits("직접 입력해서 저장", "link_fallback_320_font2_short")
        touch(compose.onNodeWithTag("manual_fallback"))
        compose.onNodeWithTag("input_name").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, modeChanges); assertEquals(0, requests) }
    }

    @Test fun myDoubleFontControlsKeepTheirRolesAndCanChangeThemeAndMotion() {
        val changes = mutableListOf<ThemePreference>()
        var bubbleChanges = 0; var motionChanges = 0
        viewport = "320dp x 520dp; fontScale=2; theme changes; radio and switch controls"
        compose.setContent {
            var theme by remember { mutableStateOf(ThemePreference.SYSTEM) }
            var bubbles by remember { mutableStateOf(true) }
            var reduced by remember { mutableStateOf(false) }
            Host(320.dp, 520.dp, 2f, dark = theme == ThemePreference.DARK) {
                MyScreen(24, {}, {}, {}, themePreference = theme,
                    onThemeChange = { theme = it; changes += it }, appVersion = "visual-test",
                    showMascotBubbles = bubbles, onMascotBubblesChange = { bubbles = it; bubbleChanges++ },
                    reduceMascotMotion = reduced, onReduceMascotMotionChange = { reduced = it; motionChanges++ })
            }
        }
        val radioRole = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
        compose.onNodeWithTag("theme_system").performScrollTo().assert(radioRole).assertIsSelected()
        assertTextFits("시스템 설정 따르기", "my_system_320_font2")
        touch(compose.onNodeWithTag("theme_light").performScrollTo().assert(radioRole))
        compose.onNodeWithTag("theme_light").assertIsSelected()
        touch(compose.onNodeWithTag("theme_dark").performScrollTo().assert(radioRole))
        compose.onNodeWithTag("theme_dark").assertIsSelected()
        screenshot("my_dark_320_font2")
        val switchRole = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)
        touch(compose.onNodeWithTag("mascot_bubbles").performScrollTo().assert(switchRole).assertIsOn())
        compose.onNodeWithTag("mascot_bubbles").assertIsOff()
        touch(compose.onNodeWithTag("mascot_reduce_motion").performScrollTo().assert(switchRole).assertIsOff())
        compose.onNodeWithTag("mascot_reduce_motion").assertIsOn()
        assertTextFits("캐릭터 움직임 줄이기", "my_motion_320_font2_dark")
        compose.runOnIdle { assertEquals(listOf(ThemePreference.LIGHT, ThemePreference.DARK), changes); assertEquals(1, bubbleChanges); assertEquals(1, motionChanges) }
    }

    @Test fun emptyVaultAt360ExposesAccessibleAddActionsAndNoReviewAction() {
        var additions = 0; var reviewCalls = 0
        viewport = "360dp x 640dp; fontScale=1; light; empty vault"
        compose.setContent {
            Host(360.dp, 640.dp) { VaultScreen(emptyList(), NOW, { additions++ }, {}, onReview = { reviewCalls++ }) }
        }
        compose.onNodeWithTag("start_review").assertDoesNotExist()
        touch(compose.onNodeWithTag("add_item"))
        screenshot("vault_empty_top_360")
        val primary = compose.onNodeWithText("첫 상품 담기").performScrollTo()
        assertTextFits("첫 상품 담기", "vault_empty_primary_360")
        touch(primary)
        compose.runOnIdle { assertEquals(2, additions); assertEquals(0, reviewCalls) }
    }

    private fun item() = WishItem("visual-fixture", "생각해 둔 작은 선물", 12900L, Platform.MANUAL, "", "default", NOW, NOW + 86_400_000)

    companion object {
        private const val HOST = "visual_host"
        private const val NOW = 1_800_000_000_000L
    }
}
