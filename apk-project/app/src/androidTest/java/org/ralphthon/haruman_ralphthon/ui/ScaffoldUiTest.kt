package org.ralphthon.haruman_ralphthon.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.ralphthon.haruman_ralphthon.data.Decision
import org.ralphthon.haruman_ralphthon.data.Platform
import org.ralphthon.haruman_ralphthon.data.ThemePreference
import org.ralphthon.haruman_ralphthon.data.WishItem
import org.ralphthon.haruman_ralphthon.ui.theme.Haruman_RalphthonTheme

/** Isolated Compose hosts: these tests never read, seed or clear the user's app preferences. */
@RunWith(AndroidJUnit4::class)
class ScaffoldUiTest {
    @get:Rule
    val compose = createComposeRule()

    private class EntryEvents {
        var saved: ProductDraft? = null
        var requests = 0
        var cancellations = 0
    }

    private fun showAdd(
        initial: ProductDraft = ProductDraft(mode = "manual"),
        processing: Boolean = false,
        errorMessage: String? = null,
        retryAllowed: Boolean = false,
    ): EntryEvents {
        val events = EntryEvents()
        compose.setContent {
            var draft by remember { mutableStateOf(initial) }
            Haruman_RalphthonTheme {
                Surface(Modifier.fillMaxSize()) {
                    AddSheet(
                        draft = draft,
                        processing = processing,
                        errorMessage = errorMessage,
                        retryAllowed = retryAllowed,
                        onChange = { draft = it },
                        onModeChange = { draft = draft.copy(mode = it) },
                        onRequest = { events.requests++ },
                        onSave = { events.saved = draft },
                        onCancelRequest = { events.cancellations++ },
                    )
                }
            }
        }
        return events
    }

    @Test
    fun manualNameIsRequiredAndWhitespaceCannotBeSaved() {
        val events = showAdd()
        compose.onNodeWithTag("save_item").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("input_name").performScrollTo().performTextInput("   ")
        compose.onNodeWithTag("save_item").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("input_name").performScrollTo().performTextReplacement("직접 적은 상품")
        compose.onNodeWithTag("save_item").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals("직접 적은 상품", events.saved?.name)
            assertEquals("", events.saved?.url)
            assertEquals(0, events.requests)
        }
    }

    @Test
    fun unknownPriceStaysNullAndImmediateDecisionCanBeSelected() {
        val events = showAdd()
        compose.onNodeWithTag("input_name").performTextInput("가격 확인 전 상품")
        compose.onNodeWithTag("cooldown_0").performScrollTo().performClick()
        compose.onNodeWithTag("save_item").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals("가격 확인 전 상품", events.saved?.name)
            assertNull(events.saved?.priceWon)
            assertEquals(0, events.saved?.hours)
            assertEquals(0, events.requests)
        }
    }

    @Test
    fun explicitZeroPriceIsNotChangedToUnknown() {
        val events = showAdd(initial = ProductDraft(mode = "manual", name = "무료 상품"))
        compose.onNodeWithTag("input_price").performScrollTo().performTextInput("0")
        compose.onNodeWithTag("save_item").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(0L, events.saved?.priceWon) }
    }

    @Test
    fun invalidNegativeAndExcessivePricesDoNotEnableSave() {
        val events = showAdd(initial = ProductDraft(mode = "manual", name = "가격을 확인할 상품"))
        listOf("-1", "12.5", "1000000000000").forEach { invalid ->
            compose.onNodeWithTag("input_price").performScrollTo().performTextReplacement(invalid)
            compose.onNodeWithText("0 이상의 원 단위 숫자를 입력해 주세요").assertExists()
            compose.onNodeWithTag("save_item").performScrollTo().assertIsNotEnabled()
        }
        compose.runOnIdle { assertNull(events.saved) }
    }

    @Test
    fun editingDescriptionMemoAndHiddenImageKeepsOriginalIdentityAndLink() {
        val originalUrl = "https://www.coupang.com/vp/products/9047670772"
        val events = showAdd(initial = ProductDraft(
            editingId = "existing-product", mode = "manual", name = "바꾸기 전 이름", price = "58000",
            description = "기존 설명", userMemo = "기존 메모", url = originalUrl,
            imageUrl = "https://image.example.test/product.png",
        ))
        compose.onNodeWithTag("input_name").performTextReplacement("수정한 상품")
        compose.onNodeWithTag("input_description").performScrollTo().performTextReplacement("직접 확인한 상품 설명")
        compose.onNodeWithTag("input_memo").performScrollTo().performTextReplacement("다음 달에도 필요한지 생각해요")
        compose.onNodeWithTag("hide_product_image").performScrollTo().performClick()
        compose.onNodeWithTag("input_url").assertDoesNotExist()
        compose.onNodeWithTag("mode_link").assertDoesNotExist()
        compose.onNodeWithTag("save_item").performScrollTo().performClick()
        compose.runOnIdle {
            val saved = requireNotNull(events.saved)
            assertEquals("existing-product", saved.editingId)
            assertEquals(originalUrl, saved.url)
            assertEquals("수정한 상품", saved.name)
            assertEquals("직접 확인한 상품 설명", saved.description)
            assertEquals("다음 달에도 필요한지 생각해요", saved.userMemo)
            assertTrue(saved.imageHidden)
            assertEquals(0, events.requests)
        }
    }

    @Test
    fun unconfiguredLinkOffersManualEntryAndPreservesReceivedUrl() {
        val originalUrl = "https://link.coupang.com/a/hdpv0PnT9E"
        val notice = "링크로 불러오는 기능은 준비 중이에요. 지금은 직접 입력해 저장할 수 있어요"
        val events = showAdd(initial = ProductDraft(mode = "link", url = originalUrl), errorMessage = notice)
        compose.onNodeWithText(notice).performScrollTo().assertExists()
        compose.onNodeWithTag("link_loading").assertDoesNotExist()
        compose.onNodeWithTag("manual_fallback").performScrollTo().performClick()
        compose.onNodeWithTag("input_name").performScrollTo().performTextInput("직접 확인한 상품")
        compose.onNodeWithTag("save_item").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals("manual", events.saved?.mode)
            assertEquals(originalUrl, events.saved?.url)
            assertEquals(0, events.requests)
        }
    }

    @Test
    fun linkTypingDoesNotSubmitUntilTheRequestActionIsPressed() {
        val events = showAdd(initial = ProductDraft(mode = "link"))
        compose.onNodeWithTag("request_link").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("input_url").performScrollTo().performTextInput("https://www.coupang.com/vp/products/9047670772")
        compose.runOnIdle { assertEquals(0, events.requests) }
        compose.onNodeWithTag("request_link").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(1, events.requests)
            assertNull(events.saved)
        }
    }

    @Test
    fun inFlightLinkDisablesEditingAndCancelDoesNotSaveOrSubmit() {
        val events = showAdd(initial = ProductDraft(mode = "link", url = "https://www.coupang.com/vp/products/9047670772"), processing = true)
        compose.onNodeWithTag("input_url").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("paste_link").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("request_link").assertDoesNotExist()
        compose.onNodeWithText("불러오기 취소").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(1, events.cancellations)
            assertEquals(0, events.requests)
            assertNull(events.saved)
        }
    }

    @Test
    fun eachThemeOptionHasAnAccessibleTouchTargetAndReturnsItsSelection() {
        val selections = mutableListOf<ThemePreference>()
        compose.setContent {
            var preference by remember { mutableStateOf(ThemePreference.SYSTEM) }
            Haruman_RalphthonTheme {
                Surface(Modifier.fillMaxSize()) {
                    MyScreen(
                        hours = 24, onDefault = {}, onExport = {}, onDelete = {},
                        themePreference = preference,
                        onThemeChange = { preference = it; selections.add(it) },
                        appVersion = "test",
                    )
                }
            }
        }
        compose.onNodeWithTag("theme_system").performScrollTo().assertIsSelected().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag("theme_light").performScrollTo().assertHeightIsAtLeast(48.dp).performClick().assertIsSelected()
        compose.onNodeWithTag("theme_dark").performScrollTo().assertHeightIsAtLeast(48.dp).performClick().assertIsSelected()
        compose.onNodeWithTag("theme_system").performScrollTo().performClick().assertIsSelected()
        compose.runOnIdle { assertEquals(listOf(ThemePreference.LIGHT, ThemePreference.DARK, ThemePreference.SYSTEM), selections) }
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
            id = "ui-test-item", name = "생각해 둔 헤드폰", priceWon = 129_000L,
            platform = Platform.COUPANG, url = "", artwork = "headphones",
            createdAt = now - 25 * 3_600_000L, cooldownUntil = now - 3_600_000L,
        )
    }
}
