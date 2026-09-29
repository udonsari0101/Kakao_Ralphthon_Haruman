package org.ralphthon.haruman_ralphthon.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
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
import org.ralphthon.haruman_ralphthon.data.Platform
import org.ralphthon.haruman_ralphthon.data.WishItem
import org.ralphthon.haruman_ralphthon.ui.mascot.LocalReduceMotion
import org.ralphthon.haruman_ralphthon.ui.mascot.TerraceWeek
import org.ralphthon.haruman_ralphthon.ui.theme.Haruman_RalphthonTheme
import java.io.File
import java.util.Calendar

/** Crowded weighted-day regression. No application storage, real time changes, or device settings. */
@RunWith(AndroidJUnit4::class)
class TerraceLabelUiTest {
    @get:Rule val compose = createComposeRule()
    private val monday = Calendar.getInstance().apply { clear(); set(2026, 8, 7, 0, 0, 0) }.timeInMillis

    @Test fun crowdedWeekLabelsDoNotOverlapAt130PercentFont() = verifyLabels(1.3f)
    @Test fun crowdedWeekLabelsDoNotOverlapAtNormalFont() = verifyLabels(1f)

    private fun verifyLabels(fontScale: Float) {
        val starts = (0..6).map { offset -> Calendar.getInstance().apply { timeInMillis = monday; add(Calendar.DAY_OF_MONTH, offset) }.timeInMillis }
        val records = starts.flatMapIndexed { day, start ->
            (0 until (if (day < 2) 1 else 3)).map { index ->
                WishItem("label-$day-$index", "검증 $day $index", null, Platform.MANUAL, "", "bag", start + index, starts.last() + 86_400_000L)
            }
        }
        var opened = 0
        compose.setContent {
            val original = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(original.density, fontScale), LocalReduceMotion provides true) {
                Haruman_RalphthonTheme {
                    Surface {
                        Column(Modifier.requiredSize(320.dp, 400.dp).clipToBounds().testTag("terrace_label_fixture")) {
                            TerraceWeek(records, monday, "2주", starts.last(), Modifier.fillMaxWidth().padding(10.dp), { opened++ }, {}, {})
                        }
                    }
                }
            }
        }
        val nodes = starts.map { compose.onNodeWithTag("terrace_day_label_$it", useUnmergedTree = true) }
        nodes.forEach { it.assertIsDisplayed() }
        val rectangles = nodes.map { it.fetchSemanticsNode().boundsInRoot }
        val host = compose.onNodeWithTag("terrace_label_fixture").fetchSemanticsNode().boundsInRoot
        capture(fontScale, rectangles.joinToString("\n"))
        rectangles.forEachIndexed { index, bounds ->
            assertTrue("Day $index leaves the horizontal viewport", bounds.left >= host.left - 1f && bounds.right <= host.right + 1f)
            assertTrue("Day $index leaves the vertical viewport", bounds.top >= host.top - 1f && bounds.bottom <= host.bottom + 1f)
            rectangles.drop(index + 1).forEach { other ->
                val separated = bounds.right <= other.left + 1f || other.right <= bounds.left + 1f || bounds.bottom <= other.top + 1f || other.bottom <= bounds.top + 1f
                assertTrue("Date labels overlap: $bounds and $other", separated)
            }
        }
        nodes.first().performTouchInput { click(center) }
        compose.runOnIdle { assertEquals("Date labels must keep the whole terrace clickable", 1, opened) }
    }

    private fun capture(scale: Float, coordinates: String) {
        val bitmap = compose.onNodeWithTag("terrace_label_fixture").captureToImage().asAndroidBitmap()
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "terrace-label-qa")
        assertTrue(dir.isDirectory || dir.mkdirs())
        val name = "crowded_320_font${scale.toString().replace('.', '_')}"
        File(dir, "$name.png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        File(dir, "$name.txt").writeText("320dp wide, fontScale=$scale; isolated 17-item week; reduced motion.\n$coordinates\n")
    }
}
