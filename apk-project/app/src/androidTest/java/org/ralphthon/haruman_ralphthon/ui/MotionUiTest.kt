package org.ralphthon.haruman_ralphthon.ui

import android.os.SystemClock
import android.os.Looper
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
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
import org.ralphthon.haruman_ralphthon.data.Decision
import org.ralphthon.haruman_ralphthon.data.Platform
import org.ralphthon.haruman_ralphthon.data.WishItem
import org.ralphthon.haruman_ralphthon.data.decide
import org.ralphthon.haruman_ralphthon.data.extend
import org.ralphthon.haruman_ralphthon.ui.mascot.LocalReduceMotion
import org.ralphthon.haruman_ralphthon.ui.mascot.LocalShowBubbles
import org.ralphthon.haruman_ralphthon.ui.theme.Haruman_RalphthonTheme
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A 21-second recording fixture with the real Android/Choreographer animation clock.
 * Deliberately has no ComposeTestRule, which would advance a synthetic test clock during idling.
 * Uses the existing ui-test-manifest ComponentActivity, in-memory state, and real pointer events
 * dispatched to the Compose root at positions discovered from its semantics tree.
 * The recorder is started/stopped by the operator; this test never runs ADB or changes OS settings.
 */
@RunWith(AndroidJUnit4::class)
class MotionUiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val trace = CopyOnWriteArrayList<String>()
    private var started = 0L
    private lateinit var activity: ComponentActivity
    private fun log(message: String) { trace += "${SystemClock.elapsedRealtime() - started}ms $message" }
    private fun main(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else instrumentation.runOnMainSync(block)
    }

    private class Fixture {
        val now = System.currentTimeMillis()
        val original = (1..2).map { n -> WishItem("motion-$n", "천천히 골라 둔 선물 $n", 12000L * n,
            Platform.MANUAL, "", "default", now - 90_000_000, now - 1000) }
        var items by mutableStateOf(original)
        var session by mutableStateOf(ReviewSession(ids = original.map { it.id }))
        var screen by mutableStateOf("added")
        var phase by mutableStateOf("실시간 모션 확인 · 준비해요")
        var event by mutableStateOf<String?>(null)
        var reduced by mutableStateOf(false)
        var renamed by mutableStateOf(false)
        var receipts = 0
        var homeTaps = 0
        var celebrations = 0
        var undoCalls = 0
        val attempts = mutableListOf<Pair<String?, ReviewAction>>()
    }

    @Test fun realTimeMotionReplayForPhoneRecording() {
        started = SystemClock.elapsedRealtime()
        val f = Fixture()
        val animatorScale = Settings.Global.getFloat(instrumentation.targetContext.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        log("OS animator scale=$animatorScale; no system setting was changed; clock=real Choreographer")
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        try {
            scenario.onActivity { host ->
                activity = host
                host.setContent {
                    CompositionLocalProvider(LocalReduceMotion provides f.reduced, LocalShowBubbles provides true) {
                        Haruman_RalphthonTheme(darkTheme = false) {
                            Surface(Modifier.fillMaxSize()) {
                                Column {
                                    Text(f.phase, Modifier.fillMaxWidth().padding(8.dp), fontSize = 12.sp)
                                    Box(Modifier.weight(1f)) {
                                        if (f.screen == "added") {
                                            AddedScreen(
                                                f.original.first().copy(name = if (f.renamed) "한 번 더 확인한 선물" else "천천히 골라 둔 선물"),
                                                onHome = { f.homeTaps++; log("added_home callback ${f.homeTaps}") },
                                                onEdit = {}, onDetail = {}, receivedEventId = f.event,
                                                onReceivedFinished = { f.receipts++; log("receipt finished ${f.receipts}") },
                                            )
                                        } else {
                                            val current = f.session.ids.getOrNull(f.session.index)?.let { id -> f.items.find { it.id == id } }
                                            ReviewScreen(f.session, current, 24, System.currentTimeMillis(), {},
                                                onAction = { action ->
                                                    f.attempts += current?.id to action
                                                    log("review action ${current?.id} $action; attempt=${f.attempts.size}")
                                                    if (current == null || !current.ready(System.currentTimeMillis())) false
                                                    else {
                                                        val after = when (action) {
                                                            ReviewAction.SKIP -> decide(current, Decision.SKIP, System.currentTimeMillis())
                                                            ReviewAction.BUY -> decide(current, Decision.BUY, System.currentTimeMillis())
                                                            ReviewAction.LATER -> extend(current, 24, System.currentTimeMillis())
                                                        }
                                                        f.items = f.items.map { if (it.id == after.id) after else it }
                                                        f.session = f.session.copy(index = f.session.index + 1,
                                                            results = f.session.results + ReviewRecord(current, after, action), undoUntil = System.currentTimeMillis() + 4000)
                                                        true
                                                    }
                                                },
                                                onUndo = {
                                                    f.session.results.lastOrNull()?.let { last ->
                                                        f.items = f.items.map { if (it.id == last.before.id) last.before else it }
                                                        f.session = f.session.copy(index = f.session.index - 1, results = f.session.results.dropLast(1), undoUntil = 0)
                                                        f.undoCalls++; log("review undo")
                                                    }
                                                },
                                                onCelebrationConsumed = { f.celebrations++; f.session = f.session.copy(celebrationConsumed = true); log("celebration consumed ${f.celebrations}") },
                                                onHistory = {}, onShare = {},
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            await("initial header") { find("added_character") != null }
            hold(450)
            val header = hit("added_character").bounds
            var eventStart = SystemClock.elapsedRealtime()
            main { f.phase = "수신 모션 · 기본 설정이에요"; f.event = "normal-receipt" }
            log("normal receipt event begins")
            hold(180)
            val during = hit("added_character").bounds
            assertEquals(header.top, during.top, 1f); assertEquals(header.height, during.height, 1f)
            await("normal receipt completion", 2500) { f.receipts == 1 }
            val receiptDuration = SystemClock.elapsedRealtime() - eventStart
            log("normal receipt observed completion after ${receiptDuration}ms")
            if (animatorScale > 0f) {
                val minimumDuration = (minOf(850f * animatorScale, 850f) * .6f).toLong()
                assertTrue("Normal receipt did not animate in real time", receiptDuration >= minimumDuration)
            }
            main { f.renamed = true }
            hold(600)
            main { assertEquals("Recomposition replayed the received event", 1, f.receipts) }

            // Exercise the real CTA during a second active event; do not wait for Compose idleness.
            scroll(10000f); hold(450)
            val home = hit("added_home")
            eventStart = SystemClock.elapsedRealtime()
            main { f.phase = "수신 중에도 버튼을 누를 수 있어요"; f.event = "interactive-receipt" }
            hold(35); tap(home)
            val tapDelay = SystemClock.elapsedRealtime() - eventStart
            main { assertEquals(1, f.homeTaps) }
            assertTrue("Receipt blocked a prompt CTA tap: ${tapDelay}ms", tapDelay < 850)
            log("real added_home tap delivered after ${tapDelay}ms during receipt")
            await("second receipt completion", 2500) { f.receipts == 2 }

            scroll(-10000f); hold(450)
            eventStart = SystemClock.elapsedRealtime()
            main { f.phase = "움직임 줄이기 · 바로 확인해요"; f.reduced = true; f.event = "reduced-receipt" }
            await("reduced receipt completion", 700) { f.receipts == 3 }
            log("reduced receipt completion after ${SystemClock.elapsedRealtime() - eventStart}ms")
            hold(1000)
            main { assertEquals(3, f.receipts); f.reduced = false; f.screen = "review"; f.phase = "왼쪽으로 밀어 정리해요" }
            await("review card") { find("review_card") != null }
            hold(800)
            swipeLeft(hit("review_card"))
            // A second pointer action while the previous card exits must not decide another item.
            if (animatorScale >= .25f) tap(hit("review_skip"))
            hold(750)
            main { assertEquals("Swipe or rapid second tap submitted twice", 1, f.attempts.size); assertEquals(1, f.session.index) }
            log("first real swipe committed once")
            hold(500)
            swipeLeft(hit("review_card"))
            await("review complete") { f.session.complete }
            hold(1800)
            main { assertEquals(2, f.attempts.size); assertEquals(1, f.celebrations); f.phase = "축하 효과는 한 번만 보여드려요" }
            // Undo and repeat the last choice within the same session; no second celebration.
            tap(hit("review_undo"))
            await("review undo") { f.undoCalls == 1 && !f.session.complete }
            hold(500)
            swipeLeft(hit("review_card"))
            await("review complete again") { f.session.complete }
            hold(1800)
            main { assertEquals(3, f.attempts.size); assertEquals(1, f.celebrations) }
            log("undo/repeat kept one celebration")
            while (SystemClock.elapsedRealtime() - started < 21_000) hold(100)
            log("recording fixture finished")
        } finally {
            scenario.close()
            val directory = File(instrumentation.targetContext.cacheDir, "motion-qa")
            check(directory.isDirectory || directory.mkdirs())
            File(directory, "realtime-motion.txt").writeText(trace.joinToString("\n"))
        }
    }

    private data class Hit(val root: ViewRootForTest, val bounds: Rect)
    private fun root(view: View): ViewRootForTest? = (view as? ViewRootForTest) ?:
        (view as? ViewGroup)?.let { group -> (0 until group.childCount).firstNotNullOfOrNull { root(group.getChildAt(it)) } }
    private fun descendants(node: SemanticsNode): Sequence<SemanticsNode> = sequence {
        yield(node); node.children.forEach { yieldAll(descendants(it)) }
    }
    private fun find(tag: String): Hit? {
        var result: Hit? = null
        main {
            val owner = root(activity.window.decorView)
            val node = owner?.let { descendants(it.semanticsOwner.unmergedRootSemanticsNode).firstOrNull { n -> n.config.getOrNull(SemanticsProperties.TestTag) == tag } }
            if (owner != null && node != null) result = Hit(owner, node.boundsInRoot)
        }
        return result
    }
    private fun hit(tag: String): Hit = checkNotNull(find(tag)) { "Missing fixture target $tag" }
    private fun scroll(delta: Float) {
        main {
            val owner = checkNotNull(root(activity.window.decorView))
            val action = descendants(owner.semanticsOwner.unmergedRootSemanticsNode)
                .mapNotNull { it.config.getOrNull(SemanticsActions.ScrollBy)?.action }.firstOrNull()
            checkNotNull(action) { "No scroll action" }.invoke(0f, delta)
        }
    }
    private fun send(hit: Hit, action: Int, downTime: Long, x: Float, y: Float) {
        main {
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
            try { hit.root.view.dispatchTouchEvent(event) } finally { event.recycle() }
        }
    }
    private fun tap(hit: Hit) {
        assertTrue("Tap target is outside its Compose view", hit.bounds.width > 0 && hit.bounds.height > 0 && hit.bounds.center.y in 0f..hit.root.view.height.toFloat())
        val down = SystemClock.uptimeMillis()
        send(hit, MotionEvent.ACTION_DOWN, down, hit.bounds.center.x, hit.bounds.center.y)
        hold(35)
        send(hit, MotionEvent.ACTION_UP, down, hit.bounds.center.x, hit.bounds.center.y)
    }
    private fun swipeLeft(hit: Hit) {
        val startX = hit.bounds.left + hit.bounds.width * .82f
        val endX = hit.bounds.left + hit.bounds.width * .12f
        val y = hit.bounds.center.y
        val down = SystemClock.uptimeMillis()
        send(hit, MotionEvent.ACTION_DOWN, down, startX, y)
        for (step in 1..12) { hold(20); send(hit, MotionEvent.ACTION_MOVE, down, startX + (endX - startX) * step / 12, y) }
        send(hit, MotionEvent.ACTION_UP, down, endX, y)
    }
    private fun hold(milliseconds: Long) {
        val end = SystemClock.elapsedRealtime() + milliseconds
        while (SystemClock.elapsedRealtime() < end) SystemClock.sleep(minOf(40, end - SystemClock.elapsedRealtime()).coerceAtLeast(1))
    }
    private fun await(label: String, timeout: Long = 2500, condition: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + timeout
        while (SystemClock.elapsedRealtime() < end) {
            var passed = false
            main { passed = condition() }
            if (passed) return
            hold(25)
        }
        error("Timed out waiting for $label")
    }
}
