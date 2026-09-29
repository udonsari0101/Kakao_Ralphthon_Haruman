package org.ralphthon.haruman_ralphthon.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Uses only the runner-recognized, unique fixture namespace; never opens production preferences. */
@RunWith(AndroidJUnit4::class)
class YardSettingsTest {
    private val app = ApplicationProvider.getApplicationContext<Context>()
    private val prefsName = "workflow-test-${UUID.randomUUID()}"
    private val preferences = app.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    private fun recreated() = LocalStore(app, prefsName)

    @After fun cleanOwnFixture() {
        preferences.edit().clear().commit()
    }

    @Test fun defaultsAndOptionalGoalSurviveStoreRecreationWithoutChangingOtherSettings() {
        val initial = recreated()
        assertEquals(YardPeriod.MONTH, initial.yardPeriod)
        assertNull(initial.spendingGoalWon)
        // A synchronous fixture value makes unrelated-key preservation observable.
        assertTrue(preferences.edit().putString("theme_preference", ThemePreference.DARK.name)
            .putBoolean("onboarding_done", true).commit())
        assertTrue(initial.saveYardSettings(YardPeriod.WEEK, 150_000L))
        recreated().also {
            assertEquals(YardPeriod.WEEK, it.yardPeriod)
            assertEquals(150_000L, it.spendingGoalWon)
            assertEquals(ThemePreference.DARK, it.themePreference)
            assertTrue(it.onboardingDone)
        }
        assertTrue(recreated().saveYardSettings(YardPeriod.MONTH, null))
        recreated().also {
            assertEquals(YardPeriod.MONTH, it.yardPeriod)
            assertNull(it.spendingGoalWon)
            assertEquals(ThemePreference.DARK, it.themePreference)
        }
        assertFalse(preferences.contains("spending_goal_won"))
    }

    @Test fun invalidGoalRejectsPeriodAndGoalTogether() {
        assertTrue(recreated().saveYardSettings(YardPeriod.WEEK, 100_000L))
        for (invalid in listOf(0L, -1L, InputLimits.PRICE_WON + 1L, Long.MAX_VALUE)) {
            assertFalse(recreated().saveYardSettings(YardPeriod.MONTH, invalid))
            recreated().also {
                assertEquals(YardPeriod.WEEK, it.yardPeriod)
                assertEquals(100_000L, it.spendingGoalWon)
            }
        }
        assertTrue(recreated().saveYardSettings(YardPeriod.MONTH, InputLimits.PRICE_WON))
        assertEquals(InputLimits.PRICE_WON, recreated().spendingGoalWon)
    }

    @Test fun milestoneMarkersSurviveRecreationAndStayIsolatedByPeriodAndBand() {
        val start = 1_790_784_000_000L
        val nextStart = start + 7L * 24 * HOUR_MILLIS
        assertFalse(recreated().hasSeenYardMilestone(YardPeriod.WEEK, start, "goal_70"))
        assertTrue(recreated().markYardMilestoneSeen(YardPeriod.WEEK, start, "goal_70"))
        recreated().also {
            assertTrue(it.hasSeenYardMilestone(YardPeriod.WEEK, start, "goal_70"))
            assertFalse(it.hasSeenYardMilestone(YardPeriod.WEEK, start, "goal_90"))
            assertFalse(it.hasSeenYardMilestone(YardPeriod.WEEK, nextStart, "goal_70"))
            assertFalse(it.hasSeenYardMilestone(YardPeriod.MONTH, start, "goal_70"))
            assertFalse(it.hasSeenYardReport(YardPeriod.WEEK, start))
        }
        // Mark returns durable-save success, not whether this was the first display.
        assertTrue(recreated().markYardMilestoneSeen(YardPeriod.WEEK, start, "goal_70"))
        assertEquals(1, preferences.getStringSet("yard_seen_milestones", emptySet())!!.size)
        assertFalse(recreated().markYardMilestoneSeen(YardPeriod.WEEK, start, "bad:marker"))
        assertFalse(recreated().markYardMilestoneSeen(YardPeriod.WEEK, start, ""))
        assertEquals(1, preferences.getStringSet("yard_seen_milestones", emptySet())!!.size)
    }

    @Test fun reportMarkersStaySeparateAndIdempotentAcrossPeriodAndSettingsChanges() {
        val start = 1_790_784_000_000L
        val anotherStart = start + 7L * 24 * HOUR_MILLIS
        assertTrue(recreated().markYardReportSeen(YardPeriod.WEEK, start))
        assertTrue(recreated().markYardReportSeen(YardPeriod.WEEK, start))
        assertTrue(recreated().saveYardSettings(YardPeriod.MONTH, 200_000L))
        recreated().also {
            assertTrue(it.hasSeenYardReport(YardPeriod.WEEK, start))
            assertFalse(it.hasSeenYardReport(YardPeriod.MONTH, start))
            assertFalse(it.hasSeenYardReport(YardPeriod.WEEK, anotherStart))
            assertFalse(it.hasSeenYardMilestone(YardPeriod.WEEK, start, "goal_70"))
        }
        assertEquals(1, preferences.getStringSet("yard_seen_reports", emptySet())!!.size)
        assertTrue(recreated().markYardReportSeen(YardPeriod.MONTH, start))
        assertTrue(recreated().markYardMilestoneSeen(YardPeriod.WEEK, start, "goal_70"))
        assertEquals(2, preferences.getStringSet("yard_seen_reports", emptySet())!!.size)
        assertEquals(1, preferences.getStringSet("yard_seen_milestones", emptySet())!!.size)
        assertTrue(recreated().hasSeenYardReport(YardPeriod.WEEK, start))
    }
}
