package com.remora.work

import com.remora.ai.model.CardTagsDto
import com.remora.ai.model.ExtractCardResponseDto
import com.remora.util.SensitiveDataRedactor
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardRedactionTest {

    @Test
    fun redactSensitive_masksSecretsInEveryTextField() {
        val dto = ExtractCardResponseDto(
            title = "Hoá đơn 4111111111111111",
            summary = "Thanh toán bằng thẻ 4111111111111111",
            tags = CardTagsDto(subject = "Mật khẩu: hunter2", topics = listOf("OTP: 482913")),
            content_type = "NOTES",
            ocr_text = "Mã xác thực: 998877\nPassword: superSecret1"
        )

        val redacted = redactSensitive(dto)

        val fields = listOf(
            redacted.title, redacted.summary, redacted.ocr_text, redacted.tags.subject
        ) + redacted.tags.topics
        listOf("4111111111111111", "hunter2", "482913", "998877", "superSecret1").forEach { secret ->
            fields.forEach { field ->
                assertFalse("'$secret' leaked in '$field'", field.contains(secret))
            }
        }
    }

    @Test
    fun redactSensitive_keepsNormalStudyContent() {
        val dto = ExtractCardResponseDto(
            title = "Định lý Pythagore",
            summary = "a²+b²=c², bài 123456.",
            tags = CardTagsDto(subject = "Toán", topics = listOf("hình học")),
            content_type = "THEORY",
            ocr_text = "Bài 15: tính tổng 123 + 456 = 579."
        )

        val redacted = redactSensitive(dto)

        assertTrue(redacted.title.contains("Pythagore"))
        assertTrue(redacted.summary.contains("a²+b²=c²"))
        assertTrue(redacted.ocr_text.contains("123 + 456"))
        assertFalse(redacted.title.contains(SensitiveDataRedactor.MASK))
        assertFalse(redacted.summary.contains(SensitiveDataRedactor.MASK))
        assertFalse(redacted.ocr_text.contains(SensitiveDataRedactor.MASK))
    }
}
