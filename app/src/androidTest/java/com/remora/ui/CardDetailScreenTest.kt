package com.remora.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.remora.core.model.entity.MemoryCard
import com.remora.core.model.entity.ProcessingStatus
import com.remora.ui.gallery.CardDetailDialog
import com.remora.ui.settings.SettingsScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CardDetailScreenTest {
    @get:Rule val compose = createComposeRule()

    private fun card() = MemoryCard(
        id = 42, imageUri = "", createdAt = 1000, title = "Bài học đã lưu",
        userIntent = "Ôn thi ngày mai", status = ProcessingStatus.PENDING
    )

    @Test
    fun detailShowsLiveProcessingStateAndRetriesFailedCard() {
        val current = mutableStateOf(card())
        var retries = 0
        compose.setContent {
            MaterialTheme {
                CardDetailDialog(current.value, onDismiss = {}, onDelete = {}, onRetry = {
                    retries++
                    current.value = current.value.copy(status = ProcessingStatus.PENDING, errorCode = null)
                })
            }
        }
        compose.onNodeWithText("Thử lại").assertDoesNotExist()
        compose.runOnIdle { current.value = current.value.copy(status = ProcessingStatus.FAILED) }
        compose.onNodeWithText("Thử lại").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, retries) }
        compose.onNodeWithText("Thử lại").assertDoesNotExist()
        compose.onNodeWithText("Ôn thi ngày mai").performScrollTo().assertIsDisplayed()

        compose.runOnIdle {
            current.value = current.value.copy(status = ProcessingStatus.DONE,
                title = "Định lý mới nhất", summary = "Nội dung vừa xử lý xong")
        }
        compose.onNodeWithText("Định lý mới nhất").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Nội dung vừa xử lý xong").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun deleteRequiresConfirmationAndCancelKeepsTheCard() {
        val current = mutableStateOf<MemoryCard?>(card())
        var deletions = 0
        compose.setContent {
            MaterialTheme {
                current.value?.let { value ->
                    CardDetailDialog(value, onDismiss = {}, onRetry = {}, onDelete = {
                        deletions++
                        current.value = null
                    })
                } ?: Text("Thẻ đã được xóa")
            }
        }
        compose.onNodeWithText("Xóa thẻ").performScrollTo().performClick()
        compose.onNodeWithText("Xóa thẻ này?").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, deletions) }
        compose.onNodeWithText("Giữ lại").performClick()
        compose.onNodeWithText("Xóa thẻ này?").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, deletions) }

        compose.onNodeWithText("Xóa thẻ").performScrollTo().performClick()
        compose.onNodeWithText("Xóa").performClick()
        compose.onNodeWithText("Thẻ đã được xóa").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, deletions) }
    }

    @Test
    fun busyActionsAreDisabledAndFailureRemainsVisible() {
        val busy = mutableStateOf(true)
        val failure = mutableStateOf<String?>(null)
        compose.setContent {
            MaterialTheme {
                CardDetailDialog(card().copy(status = ProcessingStatus.FAILED), onDismiss = {},
                    onRetry = {}, onDelete = {}, actionInProgress = busy.value, actionError = failure.value)
            }
        }
        compose.onNodeWithText("Thử lại").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Xóa thẻ").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle {
            busy.value = false
            failure.value = "Chưa xóa được thẻ. Hãy thử lại."
        }
        compose.onNodeWithText("Chưa xóa được thẻ. Hãy thử lại.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun clearAllRequiresConfirmationAndDisablesRepeatedRequests() {
        val clearing = mutableStateOf(false)
        var clears = 0
        compose.setContent {
            MaterialTheme {
                SettingsScreen(onClearAllCards = { clears++; clearing.value = true }, isClearing = clearing.value)
            }
        }
        compose.onNodeWithText("Xóa toàn bộ thẻ").performScrollTo().performClick()
        compose.onNodeWithText("Xóa toàn bộ thẻ?").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, clears) }
        compose.onNodeWithText("Giữ lại").performClick()
        compose.runOnIdle { assertEquals(0, clears) }
        compose.onNodeWithText("Xóa toàn bộ thẻ").performScrollTo().performClick()
        compose.onNodeWithText("Xóa toàn bộ").performClick()
        compose.onNodeWithText("Đang xóa thẻ…").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { assertEquals(1, clears) }
    }
}
