package org.ralphthon.haruman_ralphthon.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.ralphthon.haruman_ralphthon.data.Decision
import org.ralphthon.haruman_ralphthon.data.Platform
import org.ralphthon.haruman_ralphthon.data.WishItem
import org.ralphthon.haruman_ralphthon.ui.theme.Haruman_RalphthonTheme

/** Runs isolated screens in Compose's test host without touching app preferences. */
@RunWith(AndroidJUnit4::class)
class ScaffoldUiTest {
    @get:Rule
    val compose = createComposeRule()

    private data class SavedItem(
        val name: String,
        val price: Long?,
        val platform: Platform,
        val url: String,
        val hours: Int,
    )

    private fun showAdd(onSave: (SavedItem) -> Unit) {
        compose.setContent {
            Haruman_RalphthonTheme {
                Surface(Modifier.fillMaxSize()) {
                    AddSheet(initialUrl = "", defaultHours = 24) { name, price, platform, url, hours ->
                        onSave(SavedItem(name, price, platform, url, hours))
                    }
                }
            }
        }
    }

    @Test
    fun manualAddRequiresNameAndSavesInstagramUrlWithEnteredPrice() {
        var saved: SavedItem? = null
        val url = "https://www.instagram.com/p/ManualTest/?igsh=abc123"
        showAdd { saved = it }

        compose.onNodeWithTag("save_item").assertIsNotEnabled()
        compose.onNodeWithTag("input_name").performTextInput("테이블 조명")
        compose.onNodeWithTag("input_price").performTextInput("58000")
        compose.onNodeWithTag("input_url").performTextInput(url)
        compose.onNodeWithTag("save_item").performScrollTo().assertIsEnabled().performClick()

        compose.runOnIdle {
            assertEquals(SavedItem("테이블 조명", 58_000L, Platform.INSTAGRAM, url, 24), saved)
        }
    }

    @Test
    fun unsupportedYoutubeLinkCannotBeSaved() {
        var saved: SavedItem? = null
        showAdd { saved = it }

        compose.onNodeWithTag("input_name").performTextInput("지원하지 않는 영상")
        compose.onNodeWithTag("input_url").performTextInput("https://www.youtube.com/watch?v=test123")
        compose.onNodeWithTag("save_item").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("save_without_time").performScrollTo().assertIsNotEnabled()

        compose.runOnIdle { assertNull(saved) }
    }

    @Test
    fun unknownPriceStaysNullAndCanBeSavedWithoutCooldown() {
        var saved: SavedItem? = null
        showAdd { saved = it }

        compose.onNodeWithTag("input_name").performTextInput("가격 확인 전 상품")
        compose.onNodeWithTag("platform_instagram").performScrollTo().performClick()
        compose.onNodeWithTag("save_without_time").performScrollTo().assertIsEnabled().performClick()

        compose.runOnIdle {
            assertEquals(SavedItem("가격 확인 전 상품", null, Platform.INSTAGRAM, "", 0), saved)
        }
    }

    @Test
    fun decisionActionsReturnIntentWithoutClaimingPurchaseCompletion() {
        val decisions = mutableListOf<Decision>()
        var extensionCount = 0
        val item = readyItem()
        compose.setContent {
            Haruman_RalphthonTheme {
                DecideScreen(item, onBack = {}, onExtend = { extensionCount++ }) { decisions.add(it) }
            }
        }

        compose.onNodeWithTag("decide_skip").performClick()
        compose.onNodeWithTag("decide_buy").performClick()
        compose.onNodeWithText("구매 완료", substring = true).assertDoesNotExist()

        compose.runOnIdle {
            assertEquals(listOf(Decision.SKIP, Decision.BUY), decisions)
            assertEquals(0, extensionCount)
            assertEquals(Decision.PENDING, item.decision)
        }
    }

    @Test
    fun extendingDoesNotAlsoSubmitADecision() {
        val decisions = mutableListOf<Decision>()
        var extensionCount = 0
        compose.setContent {
            Haruman_RalphthonTheme {
                DecideScreen(readyItem(), onBack = {}, onExtend = { extensionCount++ }) { decisions.add(it) }
            }
        }

        compose.onNodeWithTag("extend_item").performScrollTo().performClick()

        compose.runOnIdle {
            assertEquals(1, extensionCount)
            assertEquals(emptyList<Decision>(), decisions)
        }
    }

    private fun readyItem(): WishItem {
        val now = System.currentTimeMillis()
        return WishItem(
            id = "ui-test-item",
            name = "생각해 둔 헤드폰",
            priceWon = 129_000L,
            platform = Platform.COUPANG,
            url = "",
            artwork = "headphones",
            createdAt = now - 25 * 3_600_000L,
            cooldownUntil = now - 3_600_000L,
        )
    }
}
