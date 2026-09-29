package org.ralphthon.haruman_ralphthon.ui

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.ralphthon.haruman_ralphthon.data.ProductApiSettings
import org.ralphthon.haruman_ralphthon.data.ProductApiSettingsResult
import org.ralphthon.haruman_ralphthon.ui.theme.Haruman_RalphthonTheme
import java.util.UUID

/** Only dummy credentials in UUID-scoped encrypted files/keys. Secure dialog is not screenshotted. */
@RunWith(AndroidJUnit4::class)
class ProductApiSettingsUiTest {
    @get:Rule val compose = createComposeRule()
    private val settings = ProductApiSettings(ApplicationProvider.getApplicationContext<Context>(), "product-api-ui-test-${UUID.randomUUID()}")
    @After fun clearOwnNamespace() { settings.clear() }

    private fun waitLoaded() {
        compose.waitUntil(5_000) {
            val node = compose.onAllNodesWithTag("api_base_url").fetchSemanticsNodes().firstOrNull()
            node != null && !node.config.contains(SemanticsProperties.Disabled)
        }
    }
    private fun replace(tag: String, text: String) {
        compose.onNodeWithTag(tag).performScrollTo().performSemanticsAction(SemanticsActions.SetText) { it(AnnotatedString(text)) }
    }

    @Test fun savedTokenIsNeverShownAndChangingAuthorityRequiresExplicitNewToken() {
        assertEquals(ProductApiSettingsResult.Success, settings.save(BASE, "stored-dummy-token"))
        var changed = 0
        compose.setContent { Haruman_RalphthonTheme { ProductApiSettingsDialog(settings, { changed++ }, {}) } }
        waitLoaded()
        compose.onNodeWithTag("api_token").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        compose.onNodeWithText("stored-dummy-token", substring = true).assertDoesNotExist()
        replace("api_base_url", "https://other.example.test:8443")
        compose.onNodeWithTag("api_settings_save").performScrollTo().assertIsNotEnabled()
        replace("api_token", "new-explicit-dummy-token")
        compose.onNodeWithTag("api_settings_save").performScrollTo().assertIsEnabled().performClick()
        compose.waitUntil(5_000) { changed == 1 }
        assertEquals("https://other.example.test:8443", settings.baseUrl)
        assertEquals("Bearer new-explicit-dummy-token", settings.read()!!.authorizationHeader)
    }

    @Test fun unsavedTokenDoesNotSurviveSavedStateRestoration() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { Haruman_RalphthonTheme { ProductApiSettingsDialog(settings, {}, {}) } }
        waitLoaded()
        replace("api_base_url", BASE)
        replace("api_token", "unsaved-dummy-token")
        compose.onNodeWithTag("api_settings_save").performScrollTo().assertIsEnabled()
        restoration.emulateSavedInstanceStateRestore()
        waitLoaded()
        compose.onNodeWithTag("api_settings_save").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("api_token").performScrollTo().assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        assertNull(settings.read())
    }

    @Test fun doubleFontDialogScrollsToValidationSaveAndClearActions() {
        assertEquals(ProductApiSettingsResult.Success, settings.save(BASE, "fixture-token"))
        var changed = 0
        compose.setContent {
            val original = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(original.density, 2f)) {
                Haruman_RalphthonTheme { ProductApiSettingsDialog(settings, { changed++ }, {}) }
            }
        }
        waitLoaded()
        replace("api_base_url", "http://invalid.example.test/path")
        compose.onNodeWithTag("api_settings_save").performScrollTo().assertIsNotEnabled()
        replace("api_base_url", BASE)
        compose.onNodeWithTag("api_settings_save").performScrollTo().assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithTag("api_settings_clear").performScrollTo().assertIsDisplayed().performClick()
        compose.waitUntil(5_000) { changed == 1 }
        assertNull(settings.read())
    }

    @Test fun builtInConnectionExplainsOptionalOverrideAndClearReturnsControlToDefault() {
        assertEquals(ProductApiSettingsResult.Success, settings.save(BASE, "override-dummy-token"))
        var changed = 0
        var dismissed = 0
        compose.setContent {
            Haruman_RalphthonTheme {
                ProductApiSettingsDialog(settings, { changed++ }, { dismissed++ }, defaultConfigured = true)
            }
        }
        waitLoaded()
        compose.onNodeWithTag("api_default_connection_info").performScrollTo()
            .assertIsDisplayed().assertTextEquals("앱에 기본 상품 연결이 준비되어 있어요")
        compose.onNodeWithText("다른 연결 사용 · 선택").assertExists()
        compose.onNodeWithTag("api_token").performScrollTo()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        compose.onNodeWithTag("api_default_restore_info").performScrollTo()
            .assertIsDisplayed().assertTextEquals("저장한 연결 정보를 지우면 앱의 기본 연결을 다시 사용해요")
        compose.onNodeWithTag("api_settings_clear").performScrollTo()
            .assertIsDisplayed().assertTextEquals("기본 연결로 돌아가기").performClick()
        compose.waitUntil(5_000) { changed == 1 && dismissed == 1 }
        assertNull(settings.read())
        assertFalse(settings.status().configured)
    }

    companion object { private const val BASE = "https://api.example.test:8443" }
}
