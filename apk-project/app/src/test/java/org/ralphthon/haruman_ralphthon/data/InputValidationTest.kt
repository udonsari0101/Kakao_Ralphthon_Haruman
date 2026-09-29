package org.ralphthon.haruman_ralphthon.data

import org.junit.Assert.*
import org.junit.Test

class InputValidationTest {
    @Test
    fun preservesFullProvidedLinksAndOffersDistinctChoices() {
        val original = "https://www.coupang.com/vp/products/9542831658?itemId=28466573033&searchId=2c3d56d48e9d421a82768884e662a9b8&sourceType=brandstore_display_ads&storeId=109671&subSourceType=brandstore_display_ads&vendorId=A00067881&vendorItemId=95416915229"
        val second = "https://www.coupang.com/vp/products/9047670772"
        val short = "https://link.coupang.com/a/hdpv0PnT9E"
        assertEquals(listOf(original, second, short), extractSupportedLinks("추천\n$original\n$second\n$short\n$original"))
    }

    @Test
    fun productInputAcceptsPublicHttpsProvidersWithoutGuessingServerSupport() {
        val urls = listOf("https://shop.example.com/products/1?itemId=abc", "https://shopping.naver.com/catalog/1", "https://www.coupang.com/vp/products/1")
        assertEquals(urls, extractSupportedLinks(urls.joinToString("\n")))
        // Host lookalikes are separate public sites, not a trusted provider identity.
        assertTrue(isSupportedProductUrl("https://coupang.com.evil.example/p/1"))
    }

    @Test
    fun newInputRejectsLocalAddressesUnsafeSchemesAndCredentials() {
        listOf(
            "http://www.coupang.com/vp/products/1", "https://localhost/p/1", "https://local/p/1",
            "https://127.0.0.1/p/1", "https://192.168.0.1/p/1", "https://[::1]/p/1",
            "https://host.local/p/1", "https://host.internal/p/1", "https://host.test/p/1",
            "https://user:secret@coupang.com/p/1", "https://coupang.com@evil.example/",
            "https://coupang.com:8080/p/1", "ftp://coupang.com/", "https://2130706433/p/1",
        ).forEach { assertTrue(it, extractSupportedLinks(it).isEmpty()) }
    }

    @Test
    fun shareWrappersDoNotDamageBalancedQueryCharacters() {
        val url = "https://www.coupang.com/vp/products/1?tracking=(a)&itemId=2"
        assertEquals(listOf(url), extractSupportedLinks("상품 ($url)"))
        val balancedEnd = "https://www.coupang.com/vp/products/1?tracking=(a)"
        assertEquals(listOf(balancedEnd), extractSupportedLinks(balancedEnd))
    }

    @Test
    fun invalidImagesNeverBecomeRemoteDisplayUrls() {
        listOf(
            "http://thumbnail.coupangcdn.com/a.jpg", "https://localhost/a.jpg",
            "https://127.0.0.1/a.jpg", "https://192.168.0.1/a.jpg", "https://[::1]/a.jpg",
            "https://host.local/a.jpg", "https://user:secret@example.com/a.jpg",
            "file:///sdcard/a.jpg", "data:image/png;base64,AA==", "https://example.com:8443/a.jpg",
        ).forEach { assertFalse(it, isValidImageUrl(it)) }
        assertTrue(isValidImageUrl("https://thumbnail.coupangcdn.com/a.jpg?size=640"))
    }

    @Test
    fun manualValidationDistinguishesUnknownZeroAndInvalidPriceAndBoundsText() {
        assertTrue(validateManualInput("상품", null).isValid)
        assertTrue(validateManualInput("상품", 0L).isValid)
        assertTrue(validateManualInput("가".repeat(InputLimits.NAME), InputLimits.PRICE_WON).isValid)
        assertFalse(validateManualInput(" ", null).isValid)
        assertFalse(validateManualInput("가".repeat(InputLimits.NAME + 1), null).isValid)
        assertFalse(validateManualInput("상품", -1L).isValid)
        assertFalse(validateManualInput("상품", InputLimits.PRICE_WON + 1).isValid)
        assertFalse(validateManualInput("상품", null, "가".repeat(InputLimits.DESCRIPTION + 1)).isValid)
        assertFalse(validateManualInput("상품", null, userMemo = "가".repeat(InputLimits.MEMO + 1)).isValid)
    }

    @Test
    fun overlongUrlIsNotSilentlyTruncated() {
        assertTrue(extractSupportedLinks("https://coupang.com/?q=" + "a".repeat(InputLimits.URL)).isEmpty())
    }
}
