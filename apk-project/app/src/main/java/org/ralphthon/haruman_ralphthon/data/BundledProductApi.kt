package org.ralphthon.haruman_ralphthon.data

import org.ralphthon.haruman_ralphthon.BuildConfig

/** The user authorized this hackathon credential inside the APK; it is not a durable secret. */
fun bundledProductApiConfig(): ProductApiConfig? {
    val base = BuildConfig.PRODUCT_API_BASE_URL
    val token = BuildConfig.PRODUCT_API_TOKEN
    if (base.isBlank() || token.isBlank()) return null
    return ProductApiConfig(base, "Bearer $token", 5_000, 60_000).takeIf { it.endpoint() != null }
}
