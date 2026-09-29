package org.ralphthon.haruman_ralphthon.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URLDecoder
import javax.net.ssl.HttpsURLConnection

interface ProductLinkService {
    suspend fun extract(url: String): ProductLinkResult
}

object NotConfiguredProductLinkService : ProductLinkService {
    override suspend fun extract(url: String): ProductLinkResult = ProductLinkResult.NotConfigured
}

data class ExtractedProduct(
    val originalUrl: String,
    val name: String = "",
    val priceWon: Long? = null,
    val imageUrl: String? = null,
    val productUrl: String? = null,
    val productId: String? = null,
    val itemId: String? = null,
    val vendorItemId: String? = null,
    val priceType: String? = null,
    val priceNote: String? = null,
    val optionLabel: String? = null,
    val snapshot: ProductSnapshot? = null,
)

sealed interface ProductLinkResult {
    data object NotConfigured : ProductLinkResult
    data class Complete(val product: ExtractedProduct) : ProductLinkResult
    data class NeedsReview(
        val product: ExtractedProduct,
        val reasons: List<String>,
        val message: String? = null,
    ) : ProductLinkResult
    data class Failure(val code: ProductFailureCode, val message: String, val retryable: Boolean) : ProductLinkResult
}

enum class ProductFailureCode {
    INVALID_URL, UNSUPPORTED_SITE, PRODUCT_DETAIL_REQUIRED, BLOCKED, LOGIN_REQUIRED,
    BUSY, TIMEOUT, MODEL_UNAVAILABLE, EXTRACTION_FAILED, AUTHENTICATION, NETWORK, RESPONSE_INVALID,
}

/** Configuration is explicit; no guessed endpoint, token or production timeout is embedded. */
class ProductApiConfig(
    val baseUrl: String,
    val authorizationHeader: String?,
    val connectTimeoutMillis: Int,
    val readTimeoutMillis: Int,
) {
    internal fun endpoint(): URI? {
        val uri = runCatching { URI(baseUrl) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", true) || uri.host.isNullOrBlank() || uri.rawUserInfo != null ||
            uri.rawQuery != null || uri.rawFragment != null || (uri.rawPath.isNotEmpty() && uri.rawPath != "/") ||
            connectTimeoutMillis <= 0 || readTimeoutMillis <= 0 ||
            authorizationHeader?.any { it == '\r' || it == '\n' } == true) return null
        return uri.resolve("/v1/products/extract")
    }
}

/** Dormant without explicit configuration. Only the API endpoint receives a selected product URL. */
class HttpsProductLinkService(private val config: ProductApiConfig? = null) : ProductLinkService {
    override suspend fun extract(url: String): ProductLinkResult {
        val configured = config ?: return ProductLinkResult.NotConfigured
        val endpoint = configured.endpoint() ?: return ProductLinkResult.NotConfigured
        if (!isSupportedProductUrl(url)) return failure(ProductFailureCode.INVALID_URL)
        return withContext(Dispatchers.IO) {
            var connection: HttpsURLConnection? = null
            try {
                currentCoroutineContext().ensureActive()
                connection = endpoint.toURL().openConnection() as HttpsURLConnection
                connection.apply {
                    requestMethod = "POST"
                    instanceFollowRedirects = false
                    connectTimeout = configured.connectTimeoutMillis
                    readTimeout = configured.readTimeoutMillis
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Accept", "application/json")
                    configured.authorizationHeader?.takeIf { it.isNotBlank() }?.let {
                        setRequestProperty("Authorization", it)
                    }
                }
                val requestBody = JSONObject().put("url", url).toString().toByteArray(Charsets.UTF_8)
                connection.setFixedLengthStreamingMode(requestBody.size)
                connection.outputStream.use { it.write(requestBody) }
                currentCoroutineContext().ensureActive()
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (output.size() + count > MAX_RESPONSE_BYTES) return@withContext failure(ProductFailureCode.RESPONSE_INVALID)
                        output.write(buffer, 0, count)
                    }
                    output.toString(Charsets.UTF_8.name())
                } ?: ""
                currentCoroutineContext().ensureActive()
                ProductApiCodec.decode(status, body, url)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: SocketTimeoutException) {
                failure(ProductFailureCode.TIMEOUT)
            } catch (_: IOException) {
                failure(ProductFailureCode.NETWORK)
            } catch (_: SecurityException) {
                failure(ProductFailureCode.NETWORK)
            } finally {
                connection?.disconnect()
            }
        }
    }
}

/** Maps the received contract. Fixture success is not evidence that an API server exists. */
object ProductApiCodec {
    fun decode(httpStatus: Int, body: String, requestUrl: String): ProductLinkResult {
        if (!isSupportedProductUrl(requestUrl)) return failure(ProductFailureCode.INVALID_URL)
        if (httpStatus == 401 || httpStatus == 403) return failure(ProductFailureCode.AUTHENTICATION)
        if (body.length > MAX_RESPONSE_BYTES) return failure(ProductFailureCode.RESPONSE_INVALID)
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return failure(ProductFailureCode.RESPONSE_INVALID)
        if (TOP_KEYS.any { !root.has(it) } || TOP_STRING_KEYS.any { !root.isStringOrNull(it) }) {
            return failure(ProductFailureCode.RESPONSE_INVALID)
        }
        if (root.string("original_url") != requestUrl) return failure(ProductFailureCode.RESPONSE_INVALID)
        val status = root.string("status")
        val code = root.string("code")
        if (status == "error") {
            if (!root.isNull("product") || code == null) return failure(ProductFailureCode.RESPONSE_INVALID)
            val expected = ERROR_CODES[code] ?: return failure(ProductFailureCode.RESPONSE_INVALID)
            return if (httpStatus == expected.first) failure(expected.second) else failure(ProductFailureCode.RESPONSE_INVALID)
        }
        if (httpStatus != 200 || status !in setOf("success", "partial") || code != null || root.string("source") != "coupang") {
            return failure(ProductFailureCode.RESPONSE_INVALID)
        }
        val product = root.optJSONObject("product") ?: return failure(ProductFailureCode.RESPONSE_INVALID)
        val reasons = mutableListOf<String>()
        if (PRODUCT_KEYS.any { !product.has(it) }) reasons += "상품 정보 일부가 비어 있어요."
        if (PRODUCT_STRING_KEYS.any { !product.isStringOrNull(it) }) reasons += "상품 정보 형식을 확인해 주세요."
        val rawName = product.string("name")
        val rawPrice = product.strictLong("price")
        val currency = product.string("currency")
        val rawImage = product.string("image_url")
        val name = rawName?.trim().orEmpty()
        val price = rawPrice?.takeIf { it in 0..InputLimits.PRICE_WON && currency == "KRW" }
        val image = rawImage?.takeIf(::isValidImageUrl)
        if (name.isBlank() || name.length > InputLimits.NAME) reasons += "상품 이름을 확인해 주세요."
        if (price == null) reasons += "원화 가격을 확인해 주세요."
        if (image == null) reasons += "상품 사진을 확인하지 못했어요."
        val productUrl = root.string("product_url")
        if (productUrl != null && !isSupportedProductUrl(productUrl)) reasons += "상품 링크를 확인해 주세요."
        val priceType = product.string("price_type")
        if (priceType != null && priceType !in setOf("displayed", "coupon", "member", "unknown")) {
            reasons += "가격 조건을 확인해 주세요."
        }
        val snapshot = ProductSnapshot(
            name = rawName, priceWon = rawPrice, currency = currency, imageUrl = rawImage,
            priceType = priceType, priceNote = product.string("price_note"), optionLabel = product.string("option_label"),
            source = root.string("source"), originalUrl = requestUrl, productUrl = productUrl,
            productId = product.string("id"), itemId = product.string("item_id"),
            vendorItemId = product.string("vendor_item_id"), fetchedAt = root.string("fetched_at"),
        )
        if (!optionIdentityMatches(requestUrl, productUrl, snapshot)) reasons += "선택한 상품과 옵션을 확인해 주세요."
        val normalized = ExtractedProduct(
            originalUrl = requestUrl, name = name.take(InputLimits.NAME), priceWon = price, imageUrl = image,
            productUrl = productUrl?.takeIf(::isSupportedProductUrl), productId = snapshot.productId,
            itemId = snapshot.itemId, vendorItemId = snapshot.vendorItemId, priceType = priceType,
            priceNote = snapshot.priceNote, optionLabel = snapshot.optionLabel, snapshot = snapshot,
        )
        return if (status == "success" && reasons.isEmpty()) ProductLinkResult.Complete(normalized)
        else ProductLinkResult.NeedsReview(
            normalized,
            reasons.distinct().ifEmpty { listOf("불러온 정보를 확인하고 저장해 주세요.") },
            root.string("message")?.take(500),
        )
    }
}

/** An explicit option in the request must not silently turn into a different returned option. */
private fun optionIdentityMatches(requestUrl: String, productUrl: String?, snapshot: ProductSnapshot): Boolean {
    fun options(url: String?): Map<String, String> {
        val query = url?.let { runCatching { URI(it).rawQuery }.getOrNull() } ?: return emptyMap()
        return query.split('&').mapNotNull { part ->
            val pair = part.split('=', limit = 2)
            if (pair.size != 2) null else runCatching {
                URLDecoder.decode(pair[0], "UTF-8") to URLDecoder.decode(pair[1], "UTF-8")
            }.getOrNull()
        }.toMap()
    }
    fun productId(url: String?): String? = url?.let {
        runCatching { Regex("/vp/products/([0-9]+)(?:/|$)").find(URI(it).path.orEmpty())?.groupValues?.get(1) }.getOrNull()
    }
    val requested = options(requestUrl)
    val resolved = options(productUrl)
    val requestProductId = productId(requestUrl)
    if (requestProductId != null && listOfNotNull(snapshot.productId, productId(productUrl)).any { it != requestProductId }) return false
    return listOf("itemId" to snapshot.itemId, "vendorItemId" to snapshot.vendorItemId).all { (key, supplied) ->
        val expected = requested[key]
        expected == null || listOfNotNull(supplied, resolved[key]).let { observed -> observed.isNotEmpty() && observed.all { it == expected } }
    }
}

private fun JSONObject.string(key: String): String? = opt(key) as? String
private fun JSONObject.isStringOrNull(key: String): Boolean = isNull(key) || opt(key) is String
private fun JSONObject.strictLong(key: String): Long? = when (val value = opt(key)) {
    is Int -> value.toLong()
    is Long -> value
    else -> null
}

private fun failure(code: ProductFailureCode): ProductLinkResult.Failure {
    val message = when (code) {
        ProductFailureCode.INVALID_URL -> "쿠팡 상품의 HTTPS 링크를 확인해 주세요."
        ProductFailureCode.UNSUPPORTED_SITE -> "지금은 쿠팡 상품 링크를 추가할 수 있어요."
        ProductFailureCode.PRODUCT_DETAIL_REQUIRED -> "상품 상세 페이지 링크를 보내 주세요."
        ProductFailureCode.BLOCKED -> "상품 정보를 확인하기 어려워요. 직접 입력해 저장할 수 있어요."
        ProductFailureCode.LOGIN_REQUIRED -> "로그인이 필요한 상품이에요. 직접 입력해 저장해 주세요."
        ProductFailureCode.BUSY -> "다른 상품을 확인하고 있어요. 잠시 뒤 다시 시도해 주세요."
        ProductFailureCode.TIMEOUT -> "상품 확인 시간이 길어지고 있어요. 다시 시도하거나 직접 입력해 주세요."
        ProductFailureCode.MODEL_UNAVAILABLE -> "상품 확인 기능을 잠시 사용할 수 없어요."
        ProductFailureCode.EXTRACTION_FAILED -> "상품 정보를 가져오지 못했어요. 직접 입력할 수 있어요."
        ProductFailureCode.AUTHENTICATION -> "상품 연결 설정을 확인해야 해요. 직접 입력할 수 있어요."
        ProductFailureCode.NETWORK -> "연결을 확인하고 다시 시도해 주세요. 입력한 내용은 유지돼요."
        ProductFailureCode.RESPONSE_INVALID -> "받아온 상품 정보를 확인할 수 없어요. 직접 입력할 수 있어요."
    }
    return ProductLinkResult.Failure(code, message, code in setOf(
        ProductFailureCode.BUSY, ProductFailureCode.TIMEOUT, ProductFailureCode.MODEL_UNAVAILABLE,
        ProductFailureCode.EXTRACTION_FAILED, ProductFailureCode.NETWORK, ProductFailureCode.BLOCKED,
    ))
}

private const val MAX_RESPONSE_BYTES = 512 * 1024
private val TOP_KEYS = setOf("status", "code", "message", "source", "original_url", "product_url", "product", "fetched_at")
private val TOP_STRING_KEYS = TOP_KEYS - "product"
private val PRODUCT_STRING_KEYS = setOf("id", "item_id", "vendor_item_id", "name", "currency", "price_type", "price_note", "image_url", "option_label")
private val PRODUCT_KEYS = PRODUCT_STRING_KEYS + "price"
private val ERROR_CODES = mapOf(
    "invalid_url" to (422 to ProductFailureCode.INVALID_URL),
    "unsupported_site" to (422 to ProductFailureCode.UNSUPPORTED_SITE),
    "product_detail_required" to (422 to ProductFailureCode.PRODUCT_DETAIL_REQUIRED),
    "blocked" to (502 to ProductFailureCode.BLOCKED),
    "login_required" to (502 to ProductFailureCode.LOGIN_REQUIRED),
    "busy" to (429 to ProductFailureCode.BUSY),
    "timeout" to (504 to ProductFailureCode.TIMEOUT),
    "model_unavailable" to (503 to ProductFailureCode.MODEL_UNAVAILABLE),
    "extraction_failed" to (502 to ProductFailureCode.EXTRACTION_FAILED),
)
