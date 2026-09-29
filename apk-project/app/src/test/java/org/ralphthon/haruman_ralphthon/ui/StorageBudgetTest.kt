package org.ralphthon.haruman_ralphthon.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.ralphthon.haruman_ralphthon.data.Decision
import org.ralphthon.haruman_ralphthon.data.Platform
import org.ralphthon.haruman_ralphthon.data.WishItem

class StorageBudgetTest {
    private fun item(id: String = "item", price: Long? = 10L, decision: Decision = Decision.PENDING) =
        WishItem(id, "고민 중인 상품", price, Platform.MANUAL, "", "default", 1_000L, 2_000L,
            decision = decision)

    @Test fun budgetReactionsDistinguishExactGoalFromLaterOverspending() {
        val bought = listOf(0L, 69L, 70L, 89L, 90L, 99L, 100L, 101L)
        assertEquals(listOf(0, 0, 1, 1, 2, 2, 3, 4), bought.map { budgetTier(it, 100L) })
    }

    @Test fun thresholdCalculationDoesNotTruncatePartialPercentages() {
        assertEquals(0, budgetTier(6_999L, 10_000L))
        assertEquals(1, budgetTier(7_000L, 10_000L))
        assertEquals(1, budgetTier(8_999L, 10_000L))
        assertEquals(2, budgetTier(9_000L, 10_000L))
        assertEquals(2, budgetTier(9_999L, 10_000L))
        assertEquals(3, budgetTier(10_000L, 10_000L))
        assertEquals(4, budgetTier(10_001L, 10_000L))
    }

    @Test fun previewIsAbsentWithoutGoalOrForAnExampleProduct() {
        assertNull(buyPreview(item(price = 90_000L), 10_000L, null))
        assertNull(buyPreview(item(price = null), 10_000L, null))
        assertNull(buyPreview(item(price = 90_000L).copy(isSample = true), 10_000L, 100_000L))
    }

    @Test fun unknownPriceDoesNotPretendThatNothingWillBeSpent() {
        assertEquals("가격이 정해지면 남은 목표를 계산할 수 있어요", buyPreview(item(price = null), 30_000L, 100_000L))
    }

    @Test fun zeroPriceIsKnownAndLeavesTheRemainingGoalUnchanged() {
        assertEquals("사면 남은 목표 70,000원 → 70,000원", buyPreview(item(price = 0L), 30_000L, 100_000L))
    }

    @Test fun buyingShowsBothCurrentAndResultingRemainingAmounts() {
        assertEquals("사면 남은 목표 70,000원 → 45,000원", buyPreview(item(price = 25_000L), 30_000L, 100_000L))
        assertEquals("사면 남은 목표 70,000원 → 0원", buyPreview(item(price = 70_000L), 30_000L, 100_000L))
    }

    @Test fun exceedingGoalShowsTheExcessIncludingAnyExistingExcess() {
        assertEquals("사면 목표를 1원 넘어요", buyPreview(item(price = 70_001L), 30_000L, 100_000L))
        assertEquals("사면 목표를 10,000원 넘어요", buyPreview(item(price = 0L), 110_000L, 100_000L))
        assertEquals("사면 목표를 15,000원 넘어요", buyPreview(item(price = 5_000L), 110_000L, 100_000L))
    }

    @Test fun previewOverflowCannotTurnOverspendingIntoNegativeOrAvailableBudget() {
        assertEquals("사면 목표를 9,223,372,036,854,775,797원 넘어요",
            buyPreview(item(price = 20L), Long.MAX_VALUE - 1L, 10L))
    }

    @Test fun pendingTotalExcludesExamplesDecisionsAndUnknownOrInvalidAmounts() {
        val items = listOf(
            item("one", 10L), item("two", 15L), item("free", 0L), item("unknown", null),
            item("invalid", -999L), item("example", 50_000L).copy(isSample = true),
            item("bought", 5_000L, Decision.BUY), item("skipped", 8_000L, Decision.SKIP),
        )
        assertEquals(25L, pendingTotal(items))
        assertEquals(15L, pendingTotal(items, excludingId = "one"))
        assertEquals(25L, pendingTotal(items, excludingId = "missing-id"))
    }

    @Test fun excludedEditedItemIsNotCountedAgainWhenItsNewPriceIsPreviewed() {
        val items = listOf(item("editing", 30_000L), item("other", 20_000L))
        val otherPending = pendingTotal(items, excludingId = "editing")
        assertEquals(20_000L, otherPending)
        assertEquals(60_000L, saturatedAdd(otherPending, 40_000L))
    }

    @Test fun emptyUnknownAndFreeOnlyListsHaveNoKnownPendingCost() {
        assertEquals(0L, pendingTotal(emptyList()))
        assertEquals(0L, pendingTotal(listOf(item("unknown", null), item("free", 0L))))
        assertEquals(0L, pendingTotal(listOf(item("edited", 99L)), excludingId = "edited"))
    }

    @Test fun largePendingTotalsSaturateInsteadOfWrapping() {
        assertEquals(Long.MAX_VALUE,
            pendingTotal(listOf(item("huge", Long.MAX_VALUE - 2L), item("extra", 3L))))
    }

    @Test fun additionPreservesExactNonnegativeAmountsUpToTheLimit() {
        assertEquals(0L, saturatedAdd(0L, 0L))
        assertEquals(42_000L, saturatedAdd(20_000L, 22_000L))
        assertEquals(Long.MAX_VALUE, saturatedAdd(Long.MAX_VALUE - 1L, 1L))
        assertEquals(Long.MAX_VALUE, saturatedAdd(Long.MAX_VALUE, 0L))
        assertEquals(Long.MAX_VALUE, saturatedAdd(0L, Long.MAX_VALUE))
    }

    @Test fun additionSaturatesOnOverflowRegardlessOfOperandOrder() {
        assertEquals(Long.MAX_VALUE, saturatedAdd(Long.MAX_VALUE, 1L))
        assertEquals(Long.MAX_VALUE, saturatedAdd(1L, Long.MAX_VALUE))
        assertEquals(Long.MAX_VALUE, saturatedAdd(Long.MAX_VALUE - 1L, 2L))
    }
}
