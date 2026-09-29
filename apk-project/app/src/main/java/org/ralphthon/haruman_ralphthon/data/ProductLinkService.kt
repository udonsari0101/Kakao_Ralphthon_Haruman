package org.ralphthon.haruman_ralphthon.data

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.HttpsURLConnection
import kotlin.coroutines.resume

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
    data class Failure(
        val code: ProductFailureCode,
        val message: String,
        val retryable: Boolean,
        val retryAfterSeconds: Long? = null,
    ) : ProductLinkResult
}

enum class ProductFailureCode {
    INVALID_URL, UNSUPPORTED_SITE, PRODUCT_DETAIL_REQUIRED, BLOCKED, LOGIN_REQUIRED,
    BUSY, TIMEOUT, CLIENT_TIMEOUT, INVALID_REQUEST, SERVER_UNAVAILABLE, MODEL_UNAVAILABLE, EXTRACTION_FAILED, AUTHENTICATION, NETWORK, RESPONSE_INVALID,
}

/** Configuration is explicit; no guessed endpoint, token or production timeout is embedded. */
class ProductApiConfig(
    val baseUrl: String,
    val authorizationHeader: String?,
    val connectTimeoutMillis: Int,
    val readTimeoutMillis: Int,
    val overallTimeoutMillis: Long = 60_000,
) {
    internal fun endpoint(): URI? {
        val uri = runCatching { URI(baseUrl) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", true) || uri.host.isNullOrBlank() || !isPublicHttpsHost(uri.host) ||
            (uri.port != -1 && uri.port !in 1..65535) || uri.rawUserInfo != null ||
            uri.rawQuery != null || uri.rawFragment != null || (uri.rawPath.isNotEmpty() && uri.rawPath != "/") ||
            connectTimeoutMillis <= 0 || readTimeoutMillis <= 0 || overallTimeoutMillis !in 1..60_000 ||
            authorizationHeader?.any { it == '\r' || it == '\n' } == true) return null
        return uri.resolve("/v1/products/extract")
    }
}

/** Dormant without explicit configuration. Only the API endpoint receives a selected product URL. */
class HttpsProductLinkService(
    private val config: ProductApiConfig? = null,
    private val connectionFactory: (URI) -> HttpsURLConnection = { it.toURL().openConnection() as HttpsURLConnection },
) : ProductLinkService {
    private val inFlight = AtomicBoolean(false)
    private val starts = ArrayDeque<Long>()
    private var retryNotBeforeNanos = 0L

    override suspend fun extract(url: String): ProductLinkResult {
        val configured = config ?: return ProductLinkResult.NotConfigured
        val endpoint = configured.endpoint() ?: return ProductLinkResult.NotConfigured
        if (configured.authorizationHeader.isNullOrBlank()) return ProductLinkResult.NotConfigured
        if (!isSupportedProductUrl(url)) return failure(ProductFailureCode.INVALID_URL)
        val requestBody = JSONObject().put("url", url).toString().toByteArray(Charsets.UTF_8)
        if (requestBody.size > InputLimits.REQUEST_BYTES) return failure(ProductFailureCode.INVALID_REQUEST)
        if (!inFlight.compareAndSet(false, true)) return failure(ProductFailureCode.BUSY)
        try {
            val waitSeconds = synchronized(starts) {
                val now = System.nanoTime()
                while (starts.isNotEmpty() && now - starts.first() >= MINUTE_NANOS) starts.removeFirst()
                val cooldown = if (now < retryNotBeforeNanos) retryNotBeforeNanos - now else 0L
                val rateWait = if (starts.size >= 12) MINUTE_NANOS - (now - starts.first()) else 0L
                val wait = maxOf(cooldown, rateWait)
                if (wait <= 0) { starts.addLast(now); null } else (wait + 999_999_999L) / 1_000_000_000L
            }
            if (waitSeconds != null) return failure(ProductFailureCode.BUSY, waitSeconds)
            val result = withTimeoutOrNull(configured.overallTimeoutMillis) {
                execute(endpoint, configured, requestBody, url)
            } ?: failure(ProductFailureCode.CLIENT_TIMEOUT)
            if (result is ProductLinkResult.Failure && result.code == ProductFailureCode.BUSY) {
                result.retryAfterSeconds?.let { seconds ->
                    synchronized(starts) { retryNotBeforeNanos = System.nanoTime() + seconds * 1_000_000_000L }
                }
            }
            return result
        } finally {
            inFlight.set(false)
        }
    }

    /** Blocking HTTPS lives off the caller thread; cancellation actively closes its socket. */
    private suspend fun execute(
        endpoint: URI, configured: ProductApiConfig, requestBody: ByteArray, url: String,
    ): ProductLinkResult = suspendCancellableCoroutine { continuation ->
        val activeConnection = AtomicReference<HttpsURLConnection?>(null)
        val task = AtomicReference<Future<*>?>(null)
        continuation.invokeOnCancellation {
            task.get()?.cancel(true)
            // disconnect may wait inside a platform TLS implementation: never block the UI/cancellation caller.
            activeConnection.getAndSet(null)?.let { connection -> HTTP_WORKERS.execute { connection.disconnect() } }
        }
        val submitted = HTTP_WORKERS.submit {
            var connection: HttpsURLConnection? = null
            try {
                if (!continuation.isActive) return@submit
                connection = connectionFactory(endpoint)
                activeConnection.set(connection)
                if (!continuation.isActive) return@submit
                connection.apply {
                    requestMethod = "POST"
                    instanceFollowRedirects = false
                    connectTimeout = minOf(configured.connectTimeoutMillis.toLong(), configured.overallTimeoutMillis).toInt()
                    readTimeout = minOf(configured.readTimeoutMillis.toLong(), configured.overallTimeoutMillis).toInt()
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("Authorization", configured.authorizationHeader)
                }
                connection.setFixedLengthStreamingMode(requestBody.size)
                connection.outputStream.use { it.write(requestBody) }
                if (!continuation.isActive) return@submit
                val status = connection.responseCode
                val retryAfter = parseRetryAfterSeconds(connection.getHeaderField("Retry-After"))
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (continuation.isActive) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (output.size() + count > MAX_RESPONSE_BYTES) {
                            if (continuation.isActive) continuation.resume(failure(ProductFailureCode.RESPONSE_INVALID))
                            return@submit
                        }
                        output.write(buffer, 0, count)
                    }
                    output.toString(Charsets.UTF_8.name())
                }.orEmpty()
                if (continuation.isActive) continuation.resume(ProductApiCodec.decode(status, body, url, retryAfter))
            } catch (_: SocketTimeoutException) {
                if (continuation.isActive) continuation.resume(failure(ProductFailureCode.CLIENT_TIMEOUT))
            } catch (_: IOException) {
                if (continuation.isActive) continuation.resume(failure(ProductFailureCode.NETWORK))
            } catch (_: SecurityException) {
                if (continuation.isActive) continuation.resume(failure(ProductFailureCode.NETWORK))
            } catch (_: RuntimeException) {
                if (continuation.isActive) continuation.resume(failure(ProductFailureCode.RESPONSE_INVALID))
            } finally {
                activeConnection.compareAndSet(connection, null)
                connection?.disconnect()
            }
        }
        task.set(submitted)
        if (!continuation.isActive) submitted.cancel(true)
    }
}

/** Delta seconds or RFC1123 dates. Bound hostile/accidental waits to one day. */
internal fun parseRetryAfterSeconds(value: String?, nowMillis: Long = System.currentTimeMillis()): Long? {
    val raw = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    raw.toLongOrNull()?.let { return it.takeIf { number -> number >= 0 }?.coerceAtMost(86_400) }
    val date = runCatching {
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).apply {
            isLenient = false
            timeZone = TimeZone.getTimeZone("GMT")
        }.parse(raw)?.time
    }.getOrNull() ?: return null
    return ((date - nowMillis).coerceAtLeast(0) / 1_000 + if (date > nowMillis && (date - nowMillis) % 1_000 != 0L) 1 else 0)
        .coerceAtMost(86_400)
}

private const val MINUTE_NANOS = 60_000_000_000L
private val HTTP_WORKERS = Executors.newCachedThreadPool { runnable ->
    Thread(runnable, "product-api-io").apply { isDaemon = true }
}

/** Maps the received contract. Fixture success is not evidence that an API server exists. */
object ProductApiCodec {
    fun decode(httpStatus: Int, body: String, requestUrl: String, retryAfterSeconds: Long? = null): ProductLinkResult {
        if (!isSupportedProductUrl(requestUrl)) return failure(ProductFailureCode.INVALID_URL)
        if (httpStatus == 401 || httpStatus == 403) return failure(ProductFailureCode.AUTHENTICATION)
        if (httpStatus == 413) return failure(ProductFailureCode.INVALID_REQUEST)
        if (body.length > MAX_RESPONSE_BYTES) return failure(ProductFailureCode.RESPONSE_INVALID)
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return when {
            httpStatus == 429 -> failure(ProductFailureCode.BUSY, retryAfterSeconds)
            httpStatus == 504 -> failure(ProductFailureCode.TIMEOUT)
            httpStatus in 500..599 -> failure(ProductFailureCode.SERVER_UNAVAILABLE)
            else -> failure(ProductFailureCode.RESPONSE_INVALID)
        }
        if (TOP_KEYS.any { !root.has(it) } || TOP_STRING_KEYS.any { !root.isStringOrNull(it) }) {
            return failure(ProductFailureCode.RESPONSE_INVALID)
        }
        if (root.string("original_url") != requestUrl) return failure(ProductFailureCode.RESPONSE_INVALID)
        val status = root.string("status")
        val code = root.string("code")
        if (status == "error") {
            if (!root.isNull("product") || code == null) return failure(ProductFailureCode.RESPONSE_INVALID)
            val expected = ERROR_CODES[code] ?: return failure(ProductFailureCode.RESPONSE_INVALID)
            return if (httpStatus == expected.first) failure(expected.second, retryAfterSeconds.takeIf { expected.second == ProductFailureCode.BUSY }) else failure(ProductFailureCode.RESPONSE_INVALID)
        }
        if (httpStatus != 200 || status !in setOf("success", "partial") || code != null || root.string("source").isNullOrBlank()) {
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
    fun isCoupang(url: String?): Boolean {
        val host = url?.let { runCatching { URI(it).host?.lowercase(Locale.ROOT) }.getOrNull() } ?: return false
        return host == "coupang.com" || host.endsWith(".coupang.com")
    }
    // Other providers may use the same parameter names for unrelated purposes.
    if (!isCoupang(requestUrl) && !isCoupang(productUrl)) return true
    if (isCoupang(requestUrl) && productUrl != null && !isCoupang(productUrl)) return false
    fun options(url: String?): Map<String, String> {
        if (!isCoupang(url)) return emptyMap()
        val query = url?.let { runCatching { URI(it).rawQuery }.getOrNull() } ?: return emptyMap()
        return query.split('&').mapNotNull { part ->
            val pair = part.split('=', limit = 2)
            if (pair.size != 2) null else runCatching {
                URLDecoder.decode(pair[0], "UTF-8") to URLDecoder.decode(pair[1], "UTF-8")
            }.getOrNull()
        }.toMap()
    }
    fun productId(url: String?): String? = url?.takeIf(::isCoupang)?.let {
        runCatching { Regex("/vp/products/([0-9]+)(?:/|$)").find(URI(it).path.orEmpty())?.groupValues?.get(1) }.getOrNull()
    }
    val requested = options(requestUrl)
    val resolved = options(productUrl)
    val requestProductId = productId(requestUrl)
    val resolvedProductId = productId(productUrl)
    // Short links do not expose IDs in the request. The returned URL and fields
    // must still agree with each other before any result can be auto-saved.
    if (snapshot.productId != null && resolvedProductId != null && snapshot.productId != resolvedProductId) return false
    if (requestProductId != null && listOfNotNull(snapshot.productId, resolvedProductId).any { it != requestProductId }) return false
    return listOf("itemId" to snapshot.itemId, "vendorItemId" to snapshot.vendorItemId).all { (key, supplied) ->
        val expected = requested[key]
        val resolvedId = resolved[key]
        (supplied == null || resolvedId == null || supplied == resolvedId) &&
            (expected == null || listOfNotNull(supplied, resolvedId).let { observed -> observed.isNotEmpty() && observed.all { it == expected } })
    }
}

private fun JSONObject.string(key: String): String? = opt(key) as? String
private fun JSONObject.isStringOrNull(key: String): Boolean = isNull(key) || opt(key) is String
private fun JSONObject.strictLong(key: String): Long? = when (val value = opt(key)) {
    is Int -> value.toLong()
    is Long -> value
    else -> null
}

private fun failure(code: ProductFailureCode, retryAfterSeconds: Long? = null): ProductLinkResult.Failure {
    val message = when (code) {
        ProductFailureCode.INVALID_URL -> "상품의 HTTPS 링크를 확인해 주세요."
        ProductFailureCode.UNSUPPORTED_SITE -> "이 상품 링크는 아직 지원하지 않아요. 직접 입력해 저장할 수 있어요."
        ProductFailureCode.PRODUCT_DETAIL_REQUIRED -> "상품 상세 페이지 링크를 보내 주세요."
        ProductFailureCode.BLOCKED -> "상품 정보를 확인하기 어려워요. 직접 입력해 저장할 수 있어요."
        ProductFailureCode.LOGIN_REQUIRED -> "로그인이 필요한 상품이에요. 직접 입력해 저장해 주세요."
        ProductFailureCode.BUSY -> "다른 상품을 확인하고 있어요. 잠시 뒤 다시 시도해 주세요."
        ProductFailureCode.TIMEOUT -> "서버에서 상품을 확인하는 시간이 길어졌어요. 다시 시도하거나 직접 입력해 주세요."
        ProductFailureCode.CLIENT_TIMEOUT -> "연결 대기 시간이 지났어요. 다시 시도하거나 직접 입력해 주세요."
        ProductFailureCode.INVALID_REQUEST -> "링크가 너무 길거나 요청 조건에 맞지 않아요. 상품 링크를 확인해 주세요."
        ProductFailureCode.SERVER_UNAVAILABLE -> "상품 연결 서버가 잠시 응답하지 않아요. 잠시 뒤 다시 시도해 주세요."
        ProductFailureCode.MODEL_UNAVAILABLE -> "상품 확인 기능을 잠시 사용할 수 없어요."
        ProductFailureCode.EXTRACTION_FAILED -> "상품 정보를 가져오지 못했어요. 직접 입력할 수 있어요."
        ProductFailureCode.AUTHENTICATION -> "상품 연결 설정을 확인해야 해요. 직접 입력할 수 있어요."
        ProductFailureCode.NETWORK -> "연결을 확인하고 다시 시도해 주세요. 입력한 내용은 유지돼요."
        ProductFailureCode.RESPONSE_INVALID -> "받아온 상품 정보를 확인할 수 없어요. 직접 입력할 수 있어요."
    }
    return ProductLinkResult.Failure(code, message, code in setOf(
        ProductFailureCode.BUSY, ProductFailureCode.TIMEOUT, ProductFailureCode.CLIENT_TIMEOUT,
        ProductFailureCode.SERVER_UNAVAILABLE, ProductFailureCode.MODEL_UNAVAILABLE,
        ProductFailureCode.EXTRACTION_FAILED, ProductFailureCode.NETWORK, ProductFailureCode.BLOCKED,
    ), retryAfterSeconds?.coerceIn(0, 86_400))
}

private const val MAX_RESPONSE_BYTES = 512 * 1024
private val TOP_KEYS = setOf("status", "code", "message", "source", "original_url", "product_url", "product", "fetched_at")
private val TOP_STRING_KEYS = TOP_KEYS - "product"
private val PRODUCT_STRING_KEYS = setOf("id", "item_id", "vendor_item_id", "name", "currency", "price_type", "price_note", "image_url", "option_label")
private val PRODUCT_KEYS = PRODUCT_STRING_KEYS + "price"
private val ERROR_CODES = mapOf(
    "unauthorized" to (401 to ProductFailureCode.AUTHENTICATION),
    "invalid_request" to (413 to ProductFailureCode.INVALID_REQUEST),
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
