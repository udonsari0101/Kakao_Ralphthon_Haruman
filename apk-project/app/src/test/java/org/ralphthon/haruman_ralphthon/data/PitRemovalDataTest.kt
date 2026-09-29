package org.ralphthon.haruman_ralphthon.data

import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class PitRemovalDataTest {
    private val zone = TimeZone.getTimeZone("UTC")
    private val now = Calendar.getInstance(zone).apply { clear(); set(2026, 8, 29, 12, 0, 0) }.timeInMillis
    private fun bought(id: String, amount: Long? = 19_000L) =
        WishItem(id, "상품 $id", 999_000L, Platform.MANUAL, "", "default", now, now,
            decision = Decision.BUY, decidedAt = now, decisionPriceWon = amount)

    @Test fun removedFlagRoundTripsAndOldJsonWithoutItKeepsTheExistingPit() {
        val original = bought("one")
        val old = JSONArray(ItemJsonCodec.encode(listOf(original))).apply { getJSONObject(0).remove("pitRemovedAt") }
        val decoded = ItemJsonCodec.decode(old.toString())
        assertFalse(decoded.hasInvalidRecords)
        assertNull(decoded.items.single().pitRemovedAt)
        assertTrue(decoded.items.single().hasVisiblePit())
        val removed = original.copy(pitRemovedAt = now + 100)
        val restored = ItemJsonCodec.decode(ItemJsonCodec.encode(listOf(removed))).items.single()
        assertEquals(removed, restored)
        assertFalse(restored.hasVisiblePit())
        assertEquals(original, restored.copy(pitRemovedAt = null))
    }

    @Test fun removingOneAndThenLastPitUpdatesBothPeriodsWithoutChangingPurchaseTotals() {
        val first = bought("one")
        val second = bought("two", 31_000L)
        val removedOne = listOf(first.copy(pitRemovedAt = now + 100), second)
        YardPeriod.entries.forEach { period ->
            val summary = yardSummary(removedOne, now, period, now, zone)
            assertEquals(1, summary.realPitCount)
            assertEquals(2, summary.realBuyCount)
            assertEquals(50_000L, summary.boughtWon)
            assertEquals(50_000L, summary.budgetBoughtWon)
            assertEquals(2, summary.budgetItems.size)
            assertEquals(2, summary.realItems.size)
        }
        val layout = layoutWeek(removedOne, now, 320f, now, timeZone = zone)
        assertEquals(listOf("two"), layout.plants.map { it.item.id })
        assertEquals(1, layout.days.sumOf { it.count })
        val allRemoved = removedOne.map { it.copy(pitRemovedAt = now + 200) }
        YardPeriod.entries.forEach { period ->
            val summary = yardSummary(allRemoved, now, period, now, zone)
            assertEquals(0, summary.realPitCount)
            assertEquals(50_000L, summary.boughtWon)
            assertEquals(50_000L, summary.budgetBoughtWon)
        }
        assertTrue(layoutWeek(allRemoved, now, 320f, now, timeZone = zone).plants.isEmpty())
    }

    @Test fun removedUnknownPurchasesStayUnknownAndDoNotMakeTheRemainingKnownPurchaseUnknown() {
        val unknown = bought("unknown", null).copy(pitRemovedAt = now)
        val known = bought("known", 22_000L)
        val sample = bought("sample-fixture", 99_000L).copy(isSample = true)
        val summary = yardSummary(listOf(unknown, known, sample), now, YardPeriod.MONTH, now, zone)
        assertEquals(1, summary.realPitCount)
        assertEquals(2, summary.realBuyCount)
        assertEquals(1, summary.unknownBoughtCount)
        assertEquals(1, summary.budgetUnknownCount)
        assertEquals(22_000L, summary.boughtWon)
        assertEquals(22_000L, summary.budgetBoughtWon)
        assertEquals(listOf("known"), layoutWeek(listOf(unknown, known, sample), now, 320f, now, timeZone = zone).plants.map { it.item.id })
    }

    @Test fun failedOrStaleDecorationWritesNeverErasePurchaseDataAndSuccessfulWriteSurvivesRepositoryReload() {
        val first = bought("first")
        val other = bought("other", 8_000L)
        val disk = MemoryDisk()
        val repo = ItemRepository(disk)
        assertTrue(repo.saveItems(listOf(first, other)))
        disk.fail = true
        assertEquals(UpdateItemResult.Failed, repo.updateItem(first, first.copy(pitRemovedAt = now)))
        assertEquals(listOf(first, other), ItemRepository(disk).items())
        disk.fail = false
        val latest = first.copy(userMemo = "다른 화면의 메모")
        assertTrue(repo.updateItem(first, latest) is UpdateItemResult.Updated)
        assertTrue(repo.updateItem(first, first.copy(pitRemovedAt = now)) is UpdateItemResult.Conflict)
        assertTrue(repo.updateItem(latest, latest.copy(pitRemovedAt = now)) is UpdateItemResult.Updated)
        assertEquals(listOf(latest.copy(pitRemovedAt = now), other), ItemRepository(disk).items())
    }

    private class MemoryDisk : ItemPersistence {
        var stored: String? = null
        var fail = false
        override fun read(): String? = stored
        override fun write(value: String): Boolean {
            if (fail) return false
            stored = value
            return true
        }
    }
}
