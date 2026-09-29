package org.ralphthon.haruman_ralphthon.data

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ProductApiCodecTest {
    private val url = "https://www.coupang.com/vp/products/6011227725?itemId=19848126302&vendorItemId=70778258892"
    private val image = "https://thumbnail.coupangcdn.com/fixture.jpg"

    @Test
    fun strictSuccessReturnsTypedProductAndKeepsOriginalSnapshot() {
        val response = fixture()
        val result = ProductApiCodec.decode(200, response.toString(), url) as ProductLinkResult.Complete
        assertEquals("테스트 상품", result.product.name)
        assertEquals(24_010L, result.product.priceWon)
        assertEquals(image, result.product.imageUrl)
        assertEquals(url, result.product.originalUrl)
        assertEquals("19848126302", result.product.itemId)
        assertEquals("KRW", result.product.snapshot?.currency)
        assertEquals("displayed", result.product.priceType)
    }

    @Test
    fun partialAlwaysRequiresReviewEvenWhenAllDisplayFieldsExist() {
        val response = fixture().put("status", "partial")
        assertTrue(ProductApiCodec.decode(200, response.toString(), url) is ProductLinkResult.NeedsReview)
        response.getJSONObject("product").put("price", JSONObject.NULL)
        val partial = ProductApiCodec.decode(200, response.toString(), url) as ProductLinkResult.NeedsReview
        assertNull(partial.product.priceWon)
        assertEquals("테스트 상품", partial.product.name)
        assertEquals(image, partial.product.imageUrl)
    }

    @Test
    fun unknownPriceIsNotZeroAndCannotAutoSave() {
        val response = fixture()
        response.getJSONObject("product").put("price", JSONObject.NULL)
        val result = ProductApiCodec.decode(200, response.toString(), url) as ProductLinkResult.NeedsReview
        assertNull(result.product.priceWon)
        response.getJSONObject("product").put("price", 0)
        assertEquals(0L, (ProductApiCodec.decode(200, response.toString(), url) as ProductLinkResult.Complete).product.priceWon)
    }

    @Test
    fun stringsDecimalNegativeOverflowAndForeignPriceRequireReview() {
        listOf<Any>("24010", "24,010원", 24_010.5, -1, InputLimits.PRICE_WON + 1).forEach { value ->
            val response = fixture()
            response.getJSONObject("product").put("price", value)
            val result = ProductApiCodec.decode(200, response.toString(), url) as ProductLinkResult.NeedsReview
            assertNull(result.product.priceWon)
        }
        val foreign = fixture()
        foreign.getJSONObject("product").put("currency", "USD")
        assertNull((ProductApiCodec.decode(200, foreign.toString(), url) as ProductLinkResult.NeedsReview).product.priceWon)
    }

    @Test
    fun imageUrlMustBeValidButNoDownloadIsRequiredForContractSuccess() {
        val response = fixture()
        response.getJSONObject("product").put("image_url", "http://127.0.0.1/private")
        val result = ProductApiCodec.decode(200, response.toString(), url) as ProductLinkResult.NeedsReview
        assertNull(result.product.imageUrl)
        assertEquals("http://127.0.0.1/private", result.product.snapshot?.imageUrl)
        // The syntactically valid fixture need not be downloadable: image loading is a later UI concern.
        assertTrue(ProductApiCodec.decode(200, fixture().toString(), url) is ProductLinkResult.Complete)
    }

    @Test
    fun missingDescriptionIsNormalAndUnspecifiedFieldsDoNotBecomeDescription() {
        val response = fixture()
        response.getJSONObject("product").put("price_note", "회원 조건").put("option_label", "검정 / 1개")
        val result = ProductApiCodec.decode(200, response.toString(), url) as ProductLinkResult.Complete
        assertEquals("회원 조건", result.product.priceNote)
        assertEquals("검정 / 1개", result.product.optionLabel)
        assertFalse(response.getJSONObject("product").has("description"))
    }

    @Test
    fun missingContractFieldsOrInvalidNameRequireReview() {
        val missing = fixture()
        missing.getJSONObject("product").remove("price_type")
        assertTrue(ProductApiCodec.decode(200, missing.toString(), url) is ProductLinkResult.NeedsReview)
        listOf<Any>(" ", "가".repeat(InputLimits.NAME + 1), 17).forEach { name ->
            val response = fixture()
            response.getJSONObject("product").put("name", name)
            assertTrue(ProductApiCodec.decode(200, response.toString(), url) is ProductLinkResult.NeedsReview)
        }
    }

    @Test
    fun wrongRequestIdentityOrSiteNeverProducesAnUnrelatedItem() {
        assertInvalid(fixture().put("original_url", "https://coupang.com/vp/products/999"))
        assertInvalid(fixture().put("source", "naver"))
        val changedOption = fixture()
        changedOption.getJSONObject("product").put("item_id", "wrong-option")
        assertTrue(ProductApiCodec.decode(200, changedOption.toString(), url) is ProductLinkResult.NeedsReview)
        val changedProduct = fixture()
        changedProduct.getJSONObject("product").put("id", "999")
        assertTrue(ProductApiCodec.decode(200, changedProduct.toString(), url) is ProductLinkResult.NeedsReview)
    }

    @Test
    fun shortLinkIsPreservedWithoutLocalExpansion() {
        val short = "https://link.coupang.com/a/hdpv0PnT9E"
        val response = fixture().put("original_url", short)
        val result = ProductApiCodec.decode(200, response.toString(), short) as ProductLinkResult.Complete
        assertEquals(short, result.product.originalUrl)
        assertEquals(url, result.product.productUrl)
    }

    @Test
    fun returnedUrlAndIdsMustAgreeEvenWithoutIdsInTheRequest() {
        val requests = listOf("https://link.coupang.com/a/hdpv0PnT9E", "https://www.coupang.com/vp/products/6011227725")
        requests.forEach { request ->
            listOf("id", "item_id", "vendor_item_id").forEach { field ->
                val response = fixture().put("original_url", request)
                response.getJSONObject("product").put(field, "999")
                val result = ProductApiCodec.decode(200, response.toString(), request)
                assertTrue("$field disagrees with returned URL", result is ProductLinkResult.NeedsReview)
                val snapshot = (result as ProductLinkResult.NeedsReview).product.snapshot!!
                assertEquals(request, snapshot.originalUrl)
                assertEquals(url, snapshot.productUrl)
                assertEquals("999", when (field) {
                    "id" -> snapshot.productId
                    "item_id" -> snapshot.itemId
                    else -> snapshot.vendorItemId
                })
                assertTrue(result.reasons.any { it.contains("옵션") })
            }
        }
    }

    @Test
    fun eachServerErrorUsesTheDocumentedHttpStatusAndCodeNotMessage() {
        val cases = listOf(
            Triple("invalid_url", 422, ProductFailureCode.INVALID_URL),
            Triple("unsupported_site", 422, ProductFailureCode.UNSUPPORTED_SITE),
            Triple("product_detail_required", 422, ProductFailureCode.PRODUCT_DETAIL_REQUIRED),
            Triple("blocked", 502, ProductFailureCode.BLOCKED),
            Triple("login_required", 502, ProductFailureCode.LOGIN_REQUIRED),
            Triple("busy", 429, ProductFailureCode.BUSY),
            Triple("timeout", 504, ProductFailureCode.TIMEOUT),
            Triple("model_unavailable", 503, ProductFailureCode.MODEL_UNAVAILABLE),
            Triple("extraction_failed", 502, ProductFailureCode.EXTRACTION_FAILED),
        )
        cases.forEach { (code, http, expected) ->
            val response = fixture().put("status", "error").put("code", code)
                .put("message", "문구가 바뀌어도 분기는 같아요").put("product", JSONObject.NULL)
            val result = ProductApiCodec.decode(http, response.toString(), url) as ProductLinkResult.Failure
            assertEquals(expected, result.code)
            assertEquals(ProductFailureCode.RESPONSE_INVALID, (ProductApiCodec.decode(200, response.toString(), url) as ProductLinkResult.Failure).code)
        }
    }

    @Test
    fun malformedJsonRedirectOrInconsistentStatusCannotAutoSave() {
        assertEquals(ProductFailureCode.RESPONSE_INVALID, (ProductApiCodec.decode(200, "<html>blocked</html>", url) as ProductLinkResult.Failure).code)
        assertEquals(ProductFailureCode.RESPONSE_INVALID, (ProductApiCodec.decode(302, fixture().toString(), url) as ProductLinkResult.Failure).code)
        assertInvalid(fixture().put("status", "unknown"))
        assertInvalid(fixture().put("code", "busy"))
        assertInvalid(fixture().apply { remove("fetched_at") })
        assertEquals(ProductFailureCode.AUTHENTICATION, (ProductApiCodec.decode(401, "", url) as ProductLinkResult.Failure).code)
    }

    @Test
    fun missingConfigurationNeverStartsANetworkRequest() = runBlocking {
        assertEquals(ProductLinkResult.NotConfigured, NotConfiguredProductLinkService.extract(url))
        assertEquals(ProductLinkResult.NotConfigured, HttpsProductLinkService().extract(url))
        val invalid = ProductApiConfig("http://example.com", null, 100, 100)
        assertEquals(ProductLinkResult.NotConfigured, HttpsProductLinkService(invalid).extract(url))
        assertNull(invalid.endpoint())
        assertNull(ProductApiConfig("https://example.com/old/path", null, 100, 100).endpoint())
        assertEquals("https://example.com/v1/products/extract", ProductApiConfig("https://example.com", null, 100, 100).endpoint().toString())
    }

    private fun assertInvalid(response: JSONObject) {
        assertEquals(ProductFailureCode.RESPONSE_INVALID, (ProductApiCodec.decode(200, response.toString(), url) as ProductLinkResult.Failure).code)
    }

    private fun fixture(): JSONObject = JSONObject().apply {
        put("status", "success"); put("code", JSONObject.NULL); put("message", JSONObject.NULL)
        put("source", "coupang"); put("original_url", url); put("product_url", url)
        put("fetched_at", "2026-09-29T11:40:00+09:00")
        put("product", JSONObject().apply {
            put("id", "6011227725"); put("item_id", "19848126302"); put("vendor_item_id", "70778258892")
            put("name", "테스트 상품"); put("price", 24_010); put("currency", "KRW")
            put("price_type", "displayed"); put("price_note", JSONObject.NULL)
            put("image_url", image); put("option_label", JSONObject.NULL)
        })
    }
}
