package org.ralphthon.haruman_ralphthon.data

import java.net.URI
import java.util.Locale

object InputLimits {
    const val NAME = 200
    const val PRICE_WON = 999_999_999_999L
    const val URL = 8_192
    const val DESCRIPTION = 5_000
    const val MEMO = 2_000
}

data class ManualInputErrors(
    val name: String? = null,
    val price: String? = null,
    val description: String? = null,
    val memo: String? = null,
) {
    val isValid: Boolean get() = name == null && price == null && description == null && memo == null
}

fun validateManualInput(name: String, priceWon: Long?, description: String = "", userMemo: String = ""): ManualInputErrors =
    ManualInputErrors(
        name = when {
            name.isBlank() -> "상품 이름을 입력해 주세요."
            name.trim().length > InputLimits.NAME -> "상품 이름은 ${InputLimits.NAME}자까지 입력할 수 있어요."
            else -> null
        },
        price = if (priceWon != null && priceWon !in 0..InputLimits.PRICE_WON) "가격을 다시 확인해 주세요." else null,
        description = if (description.length > InputLimits.DESCRIPTION) "설명은 ${InputLimits.DESCRIPTION}자까지 입력할 수 있어요." else null,
        memo = if (userMemo.length > InputLimits.MEMO) "메모는 ${InputLimits.MEMO}자까지 입력할 수 있어요." else null,
    )

/** Strict single URL check. Does not open the product or expand a short link. */
fun isSupportedProductUrl(url: String): Boolean {
    if (url.isBlank() || url.length > InputLimits.URL || url != url.trim()) return false
    val uri = validHttpsUri(url) ?: return false
    val host = uri.host.lowercase(Locale.ROOT)
    return host == "coupang.com" || host.endsWith(".coupang.com")
}

/** Preserves the selected URL's query exactly and returns each distinct eligible link once. */
fun extractSupportedLinks(text: String): List<String> = sharedUrlCandidates(text)
    .filter(::isSupportedProductUrl).distinct().toList()

/** For viewing pre-v3 links only. New requests must use extractSupportedLinks. */
@Deprecated("Legacy Instagram/HTTP compatibility only. New input uses extractSupportedLinks.")
fun parseSupportedUrl(text: String): Pair<Platform, String>? {
    for (candidate in sharedUrlCandidates(text)) {
        val uri = runCatching { URI(candidate) }.getOrNull() ?: continue
        if (uri.scheme?.lowercase(Locale.ROOT) !in setOf("http", "https") || uri.rawUserInfo != null) continue
        val host = uri.host?.lowercase(Locale.ROOT) ?: continue
        val platform = when {
            host == "coupang.com" || host.endsWith(".coupang.com") -> Platform.COUPANG
            host == "instagram.com" || host.endsWith(".instagram.com") -> Platform.INSTAGRAM
            else -> continue
        }
        return platform to candidate
    }
    return null
}

/** Remote display URLs may use public HTTPS hosts, never local addresses or credentials. */
fun isValidImageUrl(url: String): Boolean {
    if (url.length > InputLimits.URL || url != url.trim()) return false
    val uri = validHttpsUri(url) ?: return false
    val host = uri.host.lowercase(Locale.ROOT).removeSuffix(".")
    if (!host.contains('.') || host.endsWith(".localhost") || host.endsWith(".local") ||
        host.endsWith(".internal") || host.endsWith(".invalid") || host.endsWith(".test")) return false
    // Do not resolve DNS here. Literal IPs are unnecessary for the supplied image contract.
    if (host.startsWith('[') || host.contains(':') || host.all { it.isDigit() || it == '.' }) return false
    return true
}

internal fun validHttpsUri(url: String): URI? {
    val uri = runCatching { URI(url) }.getOrNull() ?: return null
    if (!uri.scheme.equals("https", ignoreCase = true) || uri.host.isNullOrBlank() ||
        uri.rawUserInfo != null || uri.port !in listOf(-1, 443)) return null
    return uri
}

private fun sharedUrlCandidates(text: String): Sequence<String> = SHARED_URL.findAll(text).map {
    // Remove unmatched share wrappers, not balanced characters belonging to the original query.
    var candidate = it.value
    for ((open, close) in listOf('(' to ')', '[' to ']', '{' to '}')) {
        while (candidate.endsWith(close) && candidate.count { char -> char == close } > candidate.count { char -> char == open }) {
            candidate = candidate.dropLast(1)
        }
    }
    candidate.removeSuffix("。")
}

private val SHARED_URL = Regex("https?://[^\\s<>\"'\\p{Cntrl}]+", RegexOption.IGNORE_CASE)
