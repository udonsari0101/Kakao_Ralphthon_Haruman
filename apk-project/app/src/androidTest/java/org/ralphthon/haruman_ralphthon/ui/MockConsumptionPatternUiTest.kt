package org.ralphthon.haruman_ralphthon.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
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
import org.ralphthon.haruman_ralphthon.data.Decision
import org.ralphthon.haruman_ralphthon.data.MockConsumptionPatterns
import org.ralphthon.haruman_ralphthon.data.Platform
import org.ralphthon.haruman_ralphthon.data.WishItem
import org.ralphthon.haruman_ralphthon.ui.mascot.LocalReduceMotion
import org.ralphthon.haruman_ralphthon.ui.mascot.LocalShowBubbles
import org.ralphthon.haruman_ralphthon.ui.theme.Haruman_RalphthonTheme
import java.io.File

/** Isolated Compose fixtures only: no ViewModel, preferences, API or real product data. */
@RunWith(AndroidJUnit4::class)
class MockConsumptionPatternUiTest {
    @get:Rule val compose = createComposeRule()

    @Composable
    private fun Host(fontScale: Float, dark: Boolean, content: @Composable () -> Unit) {
        val original = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(original.density, fontScale),
            LocalReduceMotion provides true, LocalShowBubbles provides false) {
            Haruman_RalphthonTheme(darkTheme = dark) {
                Surface(Modifier.requiredSize(320.dp, 560.dp).clipToBounds().testTag("pattern_host"), content = content)
            }
        }
    }

    private fun checkChart(fontScale: Float, dark: Boolean, name: String) {
        compose.setContent {
            Host(fontScale, dark) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { MockConsumptionPattern() }
            }
        }
        compose.onNodeWithTag("mock_pattern_example").assertIsDisplayed().assertTextEquals("예시 데이터")
        compose.onNodeWithTag("mock_pattern_disclaimer").performScrollTo()
            .assertTextContains("실제 이용자 통계가 아니에요", substring = true)
        screenshot("${name}_intro")
        val host = compose.onNodeWithTag("pattern_host").fetchSemanticsNode().boundsInRoot
        MockConsumptionPatterns.points.forEach { point ->
            compose.onNodeWithTag("mock_pattern_point_${point.elapsedHours}").performScrollTo().assertIsDisplayed()
                .assertTextContains(point.label)
                .assertTextContains("구매 결정 ${point.buyPercent}%")
                .assertTextContains("보류 중 ${point.pendingPercent}%")
                .assertTextContains("안 사기로 ${point.skipPercent}%")
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "예시 데이터"))
            val bar = compose.onNodeWithTag("mock_pattern_bar_${point.elapsedHours}", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            assertTrue("Chart bar must have usable width", bar.width > host.width / 2)
            assertTrue("Chart bar escaped its viewport", bar.left >= host.left - 1 && bar.right <= host.right + 1)
        }
        compose.onNodeWithTag("mock_pattern_note").performScrollTo().assertIsDisplayed()
            .assertTextContains("구매 결정은 실제 주문을 뜻하지 않아요", substring = true)
        screenshot("${name}_end")
    }

    @Test fun lightChartExposesAllTimePointsAndExactSharesWithoutColorOnlyMeaning() = checkChart(1f, false, "light_320")

    @Test fun darkChartAtDoubleFontKeepsEveryTimePointAndDisclaimerReachable() = checkChart(2f, true, "dark_font2_320")

    @Test fun detailIncludesExampleChartAndKeepsDecisionAndBackWhenYardHomeButtonIsHidden() {
        var decisions = 0
        var backs = 0
        compose.setContent {
            Host(1f, false) {
                DetailScreen(item(), 10_000, { backs++ }, {}, { decisions++ }, homeLabel = null)
            }
        }
        compose.onNodeWithTag("mock_pattern_example").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("마당으로").assertDoesNotExist()
        compose.onNodeWithText("보관함으로").assertDoesNotExist()
        compose.onNodeWithTag("detail_decide").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("뒤로가기").performClick()
        compose.runOnIdle { assertEquals(1, decisions); assertEquals(1, backs) }
    }

    @Test fun completedYardResultHidesHomeFooterAndKeepsAccessibleBackAction() {
        val skipped = item().copy(decision = Decision.SKIP, decidedAt = 10_000, decisionPriceWon = 20_000)
        var backs = 0
        compose.setContent {
            Host(1f, false) {
                ResultScreen(skipped, listOf(skipped), 10_000, { backs++ }, {}, celebrateEvent = false, homeLabel = null)
            }
        }
        compose.onNodeWithTag("result_home").assertDoesNotExist()
        compose.onNodeWithTag("decision_footer").assertDoesNotExist()
        compose.onNodeWithContentDescription("뒤로가기").assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp).performClick()
        compose.runOnIdle { assertEquals(1, backs) }
    }

    private fun screenshot(name: String) {
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "mock-consumption-qa")
        assertTrue(directory.isDirectory || directory.mkdirs())
        val image = compose.onNodeWithTag("pattern_host").captureToImage().asAndroidBitmap()
        File(directory, "$name.png").outputStream().use { assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }

    private fun item() = WishItem("mock-pattern-ui", "예시로 확인할 상품", 20_000,
        Platform.MANUAL, "", "bag", 0, 0)
}
