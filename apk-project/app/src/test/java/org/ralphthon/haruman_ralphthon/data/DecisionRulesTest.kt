package org.ralphthon.haruman_ralphthon.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DecisionRulesTest {
    private val item = WishItem(
        id = "test-item",
        name = "테스트 상품",
        priceWon = 25_000L,
        platform = Platform.COUPANG,
        url = "https://www.coupang.com/vp/products/123",
        artwork = "default",
        createdAt = 1_000L,
        cooldownUntil = 11_000L,
    )

    @Test
    fun deadlineIsInclusiveAndProgressIsClamped() {
        assertFalse(item.ready(10_999L))
        assertTrue(item.ready(11_000L))
        assertEquals(1L, item.remainingMillis(10_999L))
        assertEquals(0L, item.remainingMillis(11_000L))
        assertEquals(0L, item.remainingMillis(15_000L))
        assertEquals(0f, item.progress(0L), 0.001f)
        assertEquals(0.5f, item.progress(6_000L), 0.001f)
        assertEquals(1f, item.progress(15_000L), 0.001f)
    }

    @Test
    fun zeroLengthCooldownNeverProducesNaN() {
        val immediate = item.copy(cooldownUntil = item.createdAt)
        assertEquals(0f, immediate.progress(999L), 0f)
        assertEquals(1f, immediate.progress(1_000L), 0f)
    }

    @Test
    fun decisionRequiresDeadlineAndCannotBeChangedOrCountedTwice() {
        assertSame(item, decide(item, Decision.SKIP, 10_999L))
        assertSame(item, decide(item, Decision.PENDING, 11_000L))
        val skipped = decide(item, Decision.SKIP, 11_000L)
        assertEquals(Decision.SKIP, skipped.decision)
        assertEquals(11_000L, skipped.decidedAt)
        assertFalse(skipped.ready(12_000L))
        assertSame(skipped, decide(skipped, Decision.BUY, 12_000L))
        assertSame(skipped, decide(skipped, Decision.SKIP, 13_000L))
        assertEquals(25_000L, skippedTotal(listOf(decide(skipped, Decision.SKIP, 14_000L))))
    }

    @Test
    fun skippedTotalOnlyIncludesKnownNonnegativeSkippedAmounts() {
        assertEquals(25_000L, skippedTotal(listOf(
            item,
            item.copy(id = "bought", priceWon = 100_000L, decision = Decision.BUY, decisionPriceWon = 100_000L),
            item.copy(id = "unknown", priceWon = null, decision = Decision.SKIP),
            item.copy(id = "negative", priceWon = -1L, decision = Decision.SKIP, decisionPriceWon = -1L),
            item.copy(id = "skipped", decision = Decision.SKIP, decisionPriceWon = 25_000L),
        )))
        assertEquals(0L, skippedTotal(emptyList()))
    }

    @Test
    fun decisionAmountSurvivesPriceEditsAndSamplesNeverCount() {
        val skipped = decide(item, Decision.SKIP, 11_000L)
        val edited = skipped.copy(priceWon = 990_000L)
        assertEquals(25_000L, edited.decisionPriceWon)
        assertEquals(25_000L, skippedTotal(listOf(edited, edited.copy(id = "sample-record", isSample = true))))
        val unknown = decide(item.copy(priceWon = null), Decision.SKIP, 11_000L).copy(priceWon = 50_000L)
        assertEquals(0L, skippedTotal(listOf(unknown)))
    }

    @Test
    fun zeroCooldownAndOverflowRemainSafe() {
        assertEquals(123L, cooldownDeadline(123L, 0))
        assertEquals(Long.MAX_VALUE, cooldownDeadline(Long.MAX_VALUE - 10L, 24))
        assertEquals(Long.MAX_VALUE, skippedTotal(listOf(
            item.copy(decision = Decision.SKIP, decisionPriceWon = Long.MAX_VALUE),
            item.copy(decision = Decision.SKIP, decisionPriceWon = 1L),
        )))
    }

    @Test
    fun acornStagesRespectHalfwayDeadlineAndFinalDecisions() {
        assertEquals(AcornStage.BURIED, acornStage(item, 5_900L))
        assertEquals(AcornStage.SPROUT, acornStage(item, 6_000L))
        assertEquals(AcornStage.SPROUT, acornStage(item, 10_999L))
        assertEquals(AcornStage.READY, acornStage(item, 11_000L))
        assertNull(acornStage(decide(item, Decision.SKIP, 11_000L), 11_000L))
        assertNull(acornStage(decide(item, Decision.BUY, 11_000L), 11_000L))
        assertEquals(AcornStage.READY, acornStage(item.copy(cooldownUntil = item.createdAt), item.createdAt))
    }

    @Test
    fun extendingWaitsAnotherPeriodAndPreservesHistory() {
        val extended = extend(item, 24, 12_000L)
        assertEquals(12_000L + 24 * 3_600_000L, extended.cooldownUntil)
        assertFalse(extended.ready(12_000L))
        assertEquals(item.createdAt, extended.createdAt)
        val bought = decide(item, Decision.BUY, 12_000L)
        assertSame(bought, extend(bought, 24, 13_000L))
    }

    @Test(expected = IllegalArgumentException::class)
    fun extendingRequiresPositiveHours() {
        extend(item, 0, 12_000L)
    }

    @Test
    fun supportedDomainsAndSharedTextPreserveTheUrl() {
        val coupang = "https://link.coupang.com/a/AbC123?itemId=123&vendorItemId=456"
        assertEquals(Platform.COUPANG to coupang, parseSupportedUrl("쿠팡을 추천합니다!\n$coupang\n추천 상품"))
        val instagram = "https://www.instagram.com/reel/AbCdEf/?igsh=XYZ_123"
        assertEquals(Platform.INSTAGRAM to instagram, parseSupportedUrl(instagram))
        assertEquals(Platform.COUPANG to "http://coupang.com/", parseSupportedUrl("http://coupang.com/"))
        assertEquals(Platform.INSTAGRAM to "HTTPS://INSTAGRAM.COM/p/ABC/", parseSupportedUrl("HTTPS://INSTAGRAM.COM/p/ABC/"))
        assertEquals(Platform.INSTAGRAM to instagram, parseSupportedUrl("게시물 ($instagram)"))
    }

    @Test
    fun unsupportedSchemesHostsAndLookalikesAreRejected() {
        listOf(
            "https://coupang.com.evil.example/products/1",
            "https://notcoupang.com/products/1",
            "https://instagram.com.evil.example/p/1",
            "https://evilinstagram.com/p/1",
            "https://coupang.com@evil.example/products/1",
            "https://user:password@instagram.com/p/1",
            "https://evil.example/?url=https://coupang.com",
            "https://www.youtube.com/watch?v=abc",
            "ftp://instagram.com/p/1",
            "instagram://p/1",
            "www.coupang.com/products/1",
            "일반 텍스트",
            "",
        ).forEach { assertNull(it, parseSupportedUrl(it)) }
    }
}
