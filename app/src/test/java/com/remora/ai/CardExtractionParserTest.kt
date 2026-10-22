package com.remora.ai

import com.remora.core.model.entity.CardTags
import com.remora.core.model.entity.ContentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class CardExtractionParserTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    data class ParsedCard(
        val title: String,
        val summary: String,
        val tags: CardTags,
        val contentType: ContentType,
        val ocrText: String
    )

    private fun parseAndSanitize(rawJson: String): ParsedCard {
        val root = json.parseToJsonElement(rawJson).jsonObject

        val title = root["title"]?.jsonPrimitive?.content ?: ""
        val summary = root["summary"]?.jsonPrimitive?.content ?: ""
        val ocrText = root["ocr_text"]?.jsonPrimitive?.content ?: ""

        val rawContentType = root["content_type"]?.jsonPrimitive?.content ?: "UNKNOWN"
        val contentType = try {
            ContentType.valueOf(rawContentType)
        } catch (_: Exception) {
            ContentType.UNKNOWN
        }

        val tagsObj = root["tags"]?.jsonObject
        val subject = tagsObj?.get("subject")?.jsonPrimitive?.content ?: "chưa phân loại"
        val topics = mutableListOf<String>()
        tagsObj?.get("topics")?.let { elem ->
            try {
                if (elem is kotlinx.serialization.json.JsonArray) {
                    topics.addAll(elem.map { it.jsonPrimitive.content })
                }
            } catch (_: Exception) {}
        }

        return ParsedCard(
            title = title.take(80),
            summary = summary,
            tags = CardTags(subject = subject, topics = topics),
            contentType = contentType,
            ocrText = ocrText
        )
    }

    @Test
    fun testParseValidJson() {
        val validJson = """
            {
              "title": "Định lý Pythagoras",
              "summary": "Trong tam giác vuông, bình phương cạnh huyền bằng tổng bình phương hai cạnh góc vuông. a^2 + b^2 = c^2.",
              "tags": { "subject": "Hình học", "topics": ["TamGiác", "Pythagoras"] },
              "content_type": "THEORY",
              "ocr_text": "Chương 1: Tam giác vuông. Định lý Pytago..."
            }
        """.trimIndent()

        val card = parseAndSanitize(validJson)
        assertEquals("Định lý Pythagoras", card.title)
        assertEquals("Hình học", card.tags.subject)
        assertEquals(2, card.tags.topics.size)
        assertEquals(ContentType.THEORY, card.contentType)
        assertNotNull(card.ocrText)
    }

    @Test
    fun testSanitizationFallbackOnMissingFields() {
        val incompleteJson = """
            {
              "title": "Bài tập tích phân",
              "summary": "Tính tích phân bất định của hàm phân thức hữu tỉ.",
              "content_type": "INVALID_TYPE",
              "ocr_text": "I = int(x/(x^2+1)dx)"
            }
        """.trimIndent()

        val card = parseAndSanitize(incompleteJson)
        assertEquals("Bài tập tích phân", card.title)
        // Fallback rules: subject becomes "chưa phân loại", content_type becomes UNKNOWN
        assertEquals("chưa phân loại", card.tags.subject)
        assertEquals(ContentType.UNKNOWN, card.contentType)
        assertEquals("I = int(x/(x^2+1)dx)", card.ocrText)
    }
}
