package org.ralphthon.haruman_ralphthon.ui

import android.graphics.Bitmap
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.ralphthon.haruman_ralphthon.data.*
import org.ralphthon.haruman_ralphthon.ui.mascot.LocalReduceMotion
import org.ralphthon.haruman_ralphthon.ui.mascot.TerraceWeek
import org.ralphthon.haruman_ralphthon.ui.theme.Haruman_RalphthonTheme
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.ceil
import kotlin.math.floor

/** Real Choreographer/pointer fixture. No ComposeTestRule, app model, preferences or OS writes. */
@RunWith(AndroidJUnit4::class)
class TerraceMotionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val trace = CopyOnWriteArrayList<String>()
    private lateinit var activity: ComponentActivity
    private var started = 0L
    private val directory get() = File(instrumentation.targetContext.cacheDir, "terrace-motion-qa")

    private class Fixture {
        val now = System.currentTimeMillis()
        val week = periodOf(now, YardPeriod.WEEK)
        val original = listOf(
            WishItem("terrace-motion-a", "천천히 살펴본 나무", 60_000L, Platform.MANUAL, "", "default", week + 10_000L, now - 1_000L),
            WishItem("terrace-motion-b", "마음을 정한 선물", 19_000L, Platform.MANUAL, "", "default", week + 20_000L, now - 1_000L),
        )
        var items by mutableStateOf(original)
        var pending by mutableStateOf(emptySet<String>())
        var show by mutableStateOf(false)
        var reduced by mutableStateOf(false)
        var phase by mutableStateOf("실시간 테라스 모션을 준비해요")
        val consumed = mutableMapOf<String, Int>()
        val consumedAt = mutableMapOf<String, Long>()
        val decideTaps = mutableListOf<String>()
        val detailTaps = mutableListOf<String>()
        var weekTaps = 0
    }

    @Test fun realTimeTerraceTransitionsConsumeOnceAndNeverBlockThePlants() {
        started = SystemClock.elapsedRealtime()
        val fixture = Fixture()
        val scale = Settings.Global.getFloat(instrumentation.targetContext.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        log("clock=real Android Choreographer; OS animator scale=$scale; no app data or system settings changed")
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        try {
            scenario.onActivity { host ->
                activity = host
                host.setContent {
                    CompositionLocalProvider(LocalReduceMotion provides fixture.reduced) {
                        Haruman_RalphthonTheme(darkTheme = false) {
                            Surface(Modifier.fillMaxSize()) {
                                Column(Modifier.fillMaxSize().background(Color(0xFFEAF5DC)).padding(horizontal = 10.dp)) {
                                    Text(fixture.phase, Modifier.fillMaxWidth().padding(vertical = 20.dp), fontSize = 13.sp)
                                    Spacer(Modifier.height(32.dp))
                                    if (fixture.show) TerraceWeek(
                                        fixture.items, fixture.week, "1주", fixture.now,
                                        onWeek = { fixture.weekTaps++; log("week callback") },
                                        onItem = { fixture.detailTaps += it.id; log("detail callback ${it.id}") },
                                        onDecide = { fixture.decideTaps += it.id; log("decision callback ${it.id}") },
                                        animateStates = fixture.pending,
                                        onAnimationConsumed = { event ->
                                            fixture.consumed[event] = (fixture.consumed[event] ?: 0) + 1
                                            fixture.consumedAt[event] = SystemClock.elapsedRealtime()
                                            fixture.pending = fixture.pending - event
                                            log("event consumed $event count=${fixture.consumed[event]} (start marker, not completion)")
                                        }
                                    )
                                    Spacer(Modifier.height(32.dp))
                                    Text("실제 상품과 설정을 바꾸지 않는 검수 화면이에요", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
            hold(450)
            main { fixture.phase = "미리 그린 페이지는 기다리고 있어요"; fixture.show = true }
            await("precomposed sprout nodes") {
                findOnMain("terrace_plant_terrace-motion-a")?.bounds?.let { it.width > 0 && it.height > 0 } == true
            }
            hold(200)
            main { assertTrue("Offscreen fixture consumed an ineligible event", fixture.consumed.isEmpty()) }
            log("both sprouts were precomposed with empty animation eligibility and consumed no events")
            val sproutStart = SystemClock.elapsedRealtime()
            main {
                fixture.phase = "이 페이지로 오면 새싹이 자라요"
                fixture.pending = fixture.original.map { "${it.id}:SPROUT" }.toSet()
            }
            await("sprout nodes") {
                findOnMain("terrace_plant_terrace-motion-a")?.bounds?.let { it.width > 0 && it.height > 0 } == true &&
                    fixture.consumedAt["terrace-motion-a:SPROUT"] != null
            }
            tap(hit("terrace_plant_terrace-motion-a"))
            val tapDelay = SystemClock.elapsedRealtime() - sproutStart
            var tapAfterMarker = 0L
            main {
                assertEquals(listOf("terrace-motion-a"), fixture.decideTaps)
                tapAfterMarker = SystemClock.elapsedRealtime() - checkNotNull(fixture.consumedAt["terrace-motion-a:SPROUT"])
            }
            log("sprout pointer callback delivered ${tapDelay}ms after eligibility changed on an existing node")
            log("sprout pointer callback observed ${tapAfterMarker}ms after its animation start marker")
            if (scale >= 1f) assertTrue("Sprout animation delayed the tap: ${tapAfterMarker}ms", tapAfterMarker < 360L)
            saveScreen("01-sprouts-during")
            hold(600)
            main {
                assertEquals(1, fixture.consumed["terrace-motion-a:SPROUT"])
                assertEquals(1, fixture.consumed["terrace-motion-b:SPROUT"])
                fixture.phase = "같은 상태를 다시 그려도 반복하지 않아요"
                fixture.items = fixture.items.map { it.copy(name = it.name + " · 확인했어요") }
                // Even an accidental same-key rearm must not replay an already mounted plant.
                fixture.pending = fixture.pending + "terrace-motion-a:SPROUT" + "terrace-motion-b:SPROUT"
            }
            hold(600)
            main {
                assertEquals(1, fixture.consumed["terrace-motion-a:SPROUT"])
                assertEquals(1, fixture.consumed["terrace-motion-b:SPROUT"])
                fixture.pending = setOf("terrace-motion-a:TREE", "terrace-motion-b:PIT")
                fixture.items = listOf(
                    fixture.items[0].copy(decision = Decision.SKIP, decidedAt = fixture.now, decisionPriceWon = 60_000L),
                    fixture.items[1].copy(decision = Decision.BUY, decidedAt = fixture.now, decisionPriceWon = 19_000L),
                )
                fixture.phase = "나무와 구덩이로 마음을 기록했어요"
            }
            val decisionStart = SystemClock.elapsedRealtime()
            await("tree and pit event start") {
                fixture.consumed["terrace-motion-a:TREE"] == 1 && fixture.consumed["terrace-motion-b:PIT"] == 1
            }
            tap(hit("terrace_plant_terrace-motion-b"))
            main { assertEquals(listOf("terrace-motion-b"), fixture.detailTaps) }
            log("pit pointer callback delivered ${SystemClock.elapsedRealtime() - decisionStart}ms after state update")
            hold(700)
            saveScreen("02-decisions-after")
            val normalTree = nodeBitmap(hit("terrace_plant_terrace-motion-a"))
            val normalPit = nodeBitmap(hit("terrace_plant_terrace-motion-b"))
            log("normal final-pose snapshots observed ${SystemClock.elapsedRealtime() - decisionStart}ms after state update; this is an observation time, not measured animation duration")
            main {
                fixture.phase = "움직임을 줄이면 바로 같은 모습이에요"
                fixture.reduced = true
                fixture.items = fixture.items.mapIndexed { index, item -> item.copy(id = "terrace-reduced-${index}") }
                fixture.pending = setOf("terrace-reduced-0:TREE", "terrace-reduced-1:PIT")
            }
            val reducedStart = SystemClock.elapsedRealtime()
            await("reduced markers", 700L) {
                fixture.consumed["terrace-reduced-0:TREE"] == 1 && fixture.consumed["terrace-reduced-1:PIT"] == 1
            }
            hold(100)
            val reducedTree = nodeBitmap(hit("terrace_plant_terrace-reduced-0"))
            val reducedPit = nodeBitmap(hit("terrace_plant_terrace-reduced-1"))
            assertTrue("Reduced tree did not render the normal final pose", normalTree.sameAs(reducedTree))
            assertTrue("Reduced pit did not render the normal final pose", normalPit.sameAs(reducedPit))
            log("reduced final pixels matched both normal final poses after ${SystemClock.elapsedRealtime() - reducedStart}ms")
            saveScreen("03-reduced-final")
            tap(hit("terrace_plant_terrace-reduced-0"))
            main {
                assertEquals(listOf("terrace-motion-b", "terrace-reduced-0"), fixture.detailTaps)
                assertTrue("An event was consumed more than once", fixture.consumed.values.all { it == 1 })
                assertEquals(6, fixture.consumed.size)
                fixture.phase = "한 번만 재생하고, 버튼은 계속 눌러져요"
            }
            while (SystemClock.elapsedRealtime() - started < 7_000L) hold(100)
            log("fixture passed; 6 unique state markers consumed once; 3 real pointer callbacks")
        } finally {
            scenario.close()
            check(directory.isDirectory || directory.mkdirs())
            File(directory, "realtime-terrace-motion.txt").writeText(trace.joinToString("\n"))
        }
    }

    private data class Hit(val owner: ViewRootForTest, val bounds: Rect)
    private fun log(message: String) { trace += "${SystemClock.elapsedRealtime() - started}ms $message" }
    private fun main(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else instrumentation.runOnMainSync(block)
    }
    private fun root(view: View): ViewRootForTest? = (view as? ViewRootForTest) ?: (view as? ViewGroup)?.let { group ->
        (0 until group.childCount).firstNotNullOfOrNull { root(group.getChildAt(it)) }
    }
    private fun descendants(node: SemanticsNode): Sequence<SemanticsNode> = sequence {
        yield(node); node.children.forEach { yieldAll(descendants(it)) }
    }
    private fun findOnMain(tag: String): Hit? {
        val owner = root(activity.window.decorView) ?: return null
        val node = descendants(owner.semanticsOwner.unmergedRootSemanticsNode)
            .firstOrNull { it.config.getOrNull(SemanticsProperties.TestTag) == tag } ?: return null
        return Hit(owner, node.boundsInRoot)
    }
    private fun hit(tag: String): Hit {
        var result: Hit? = null
        main { result = findOnMain(tag) }
        return checkNotNull(result) { "Missing real-time fixture node $tag" }
    }
    private fun tap(hit: Hit) {
        assertTrue("Plant has no usable bounds", hit.bounds.width > 0 && hit.bounds.height > 0)
        val down = SystemClock.uptimeMillis()
        fun send(action: Int) = main {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, hit.bounds.center.x, hit.bounds.center.y, 0)
            try { hit.owner.view.dispatchTouchEvent(event) } finally { event.recycle() }
        }
        send(MotionEvent.ACTION_DOWN); hold(30); send(MotionEvent.ACTION_UP)
    }
    private fun screen(): Bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot()) { "Fixture screenshot unavailable" }
    private fun saveScreen(name: String) {
        check(directory.isDirectory || directory.mkdirs())
        File(directory, "$name.png").outputStream().use { check(screen().compress(Bitmap.CompressFormat.PNG, 100, it)) }
        log("captured $name.png")
    }
    private fun nodeBitmap(hit: Hit): Bitmap {
        val location = IntArray(2)
        main { hit.owner.view.getLocationOnScreen(location) }
        val bitmap = screen()
        val left = floor(hit.bounds.left + location[0]).toInt().coerceIn(0, bitmap.width - 1)
        val top = floor(hit.bounds.top + location[1]).toInt().coerceIn(0, bitmap.height - 1)
        val right = ceil(hit.bounds.right + location[0]).toInt().coerceIn(left + 1, bitmap.width)
        val bottom = ceil(hit.bounds.bottom + location[1]).toInt().coerceIn(top + 1, bitmap.height)
        return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
    }
    private fun hold(milliseconds: Long) {
        val end = SystemClock.elapsedRealtime() + milliseconds
        while (SystemClock.elapsedRealtime() < end) SystemClock.sleep(minOf(40L, end - SystemClock.elapsedRealtime()).coerceAtLeast(1L))
    }
    private fun await(label: String, timeout: Long = 2_500L, condition: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + timeout
        while (SystemClock.elapsedRealtime() < end) {
            var passed = false
            main { passed = condition() }
            if (passed) return
            hold(20)
        }
        error("Timed out waiting for $label")
    }
}
