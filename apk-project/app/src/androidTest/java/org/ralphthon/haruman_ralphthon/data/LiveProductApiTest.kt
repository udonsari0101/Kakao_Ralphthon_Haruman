package org.ralphthon.haruman_ralphthon.data

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Explicitly opt in with -e liveApi true. One request, no model, preferences, retries or UI actions. */
@RunWith(AndroidJUnit4::class)
class LiveProductApiTest {
    @Test
    fun bundledConfigurationMakesOneLiveExtractionRequest() {
        assumeTrue("Live API probe requires explicit opt-in", InstrumentationRegistry.getArguments().getString("liveApi") == "true")

        val started = SystemClock.elapsedRealtime()
        // Do not retain or print exception messages: platform failures can include a server address.
        val outcome = try {
            val config = bundledProductApiConfig()
            if (config == null) {
                Outcome("not_configured", "NotConfigured", "CONFIG_MISSING", false, false)
            } else {
                when (val result = runBlocking { HttpsProductLinkService(config).extract(PRODUCT_URL) }) {
                    ProductLinkResult.NotConfigured -> Outcome("not_configured", "NotConfigured", "CONFIG_REJECTED", false, false)
                    is ProductLinkResult.Complete -> Outcome("complete", "Complete", null, true, true)
                    is ProductLinkResult.NeedsReview -> Outcome("needs_review", "NeedsReview", null, true, true)
                    is ProductLinkResult.Failure -> when (result.code) {
                        // A parsed, documented BLOCKED response proves the API answered; it is not transport failure.
                        ProductFailureCode.BLOCKED -> Outcome("blocked", "Failure", result.code.name, false, true)
                        ProductFailureCode.NETWORK, ProductFailureCode.CLIENT_TIMEOUT ->
                            Outcome("transport_failure", "Failure", result.code.name, false, false)
                        else -> Outcome("api_failure", "Failure", result.code.name, false, false)
                    }
                }
            }
        } catch (_: Exception) {
            Outcome("unexpected_failure", "Exception", "UNEXPECTED", false, false)
        }

        val report = JSONObject()
            .put("status", outcome.status)
            .put("resultClass", outcome.resultClass)
            .put("code", outcome.code ?: JSONObject.NULL)
            .put("productPresent", outcome.productPresent)
            .put("duration", SystemClock.elapsedRealtime() - started)
        // Only this fixed allowlist is written. Never serialize a result/product/config or server message.
        val saved = runCatching {
            File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "live-api-qa.json")
                .writeText(report.toString(2), Charsets.UTF_8)
        }.isSuccess
        assertTrue("Could not save sanitized live API probe report", saved)
        assertTrue("Live API probe did not return Complete, NeedsReview or BLOCKED; see sanitized report", outcome.accepted)
    }

    private class Outcome(
        val status: String,
        val resultClass: String,
        val code: String?,
        val productPresent: Boolean,
        val accepted: Boolean,
    )

    private companion object {
        const val PRODUCT_URL = "https://www.coupang.com/vp/products/9047670772"
    }
}
