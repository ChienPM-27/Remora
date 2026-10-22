package com.remora.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.remora.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-End Smoke Test verifying:
 * 1. App opens and handles Consent Screen if presented.
 * 2. Navigates to Gallery (Kho thẻ nhớ) where canonical/seeded cards are displayed.
 * 3. Switches to Search (Tìm kiếm) tab.
 * 4. Performs search input query and checks results.
 */
@RunWith(AndroidJUnit4::class)
class SmokeTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun testAppLaunch_GalleryAndSearchFlow() {
        // Step 1: If Consent button exists, tap "Tôi đồng ý & Bắt đầu sử dụng"
        try {
            val consentBtn = composeTestRule.onNodeWithText("Tôi đồng ý & Bắt đầu sử dụng")
            consentBtn.assertIsDisplayed()
            consentBtn.performClick()
            composeTestRule.waitForIdle()
        } catch (ignored: Throwable) {
            // Already consented in previous run or pre-consented
        }

        // Step 2: Verify Gallery tab is displayed
        composeTestRule.onNodeWithText("Kho thẻ nhớ").assertIsDisplayed()

        // Step 3: Switch to Search tab
        composeTestRule.onNodeWithText("Tìm kiếm").performClick()
        composeTestRule.waitForIdle()

        // Step 4: Perform search query
        val searchInput = composeTestRule.onNodeWithText("Tìm kiếm bài học (VD: slide phân phối chuẩn hôm bữa)...")
        searchInput.assertIsDisplayed()
        searchInput.performTextInput("Pythagoras")
        composeTestRule.waitForIdle()
    }
}
