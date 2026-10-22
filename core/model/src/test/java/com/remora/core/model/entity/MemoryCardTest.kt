package com.remora.core.model.entity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MemoryCardTest {

    @Test
    fun testDefaultValues() {
        val card = MemoryCard(
            id = 1L,
            imageUri = "content://media/external/images/media/1"
        )

        assertEquals(1L, card.id)
        assertEquals("content://media/external/images/media/1", card.imageUri)
        assertNull(card.userIntent)
        assertEquals("", card.title)
        assertEquals("", card.summary)
        assertEquals("chưa phân loại", card.tags.subject)
        assertEquals(0, card.tags.topics.size)
        assertEquals(ContentType.UNKNOWN, card.contentType)
        assertEquals("", card.ocrText)
        assertNull(card.embedding)
        assertEquals(ProcessingStatus.PENDING, card.status)
        assertEquals(0, card.attemptCount)
        assertNull(card.errorCode)
    }

    @Test
    fun testEqualityWithEmbedding() {
        val emb1 = floatArrayOf(0.1f, 0.2f, 0.3f)
        val emb2 = floatArrayOf(0.1f, 0.2f, 0.3f)
        val emb3 = floatArrayOf(0.1f, 0.2f, 0.4f)

        val card1 = MemoryCard(id = 1L, imageUri = "uri", embedding = emb1)
        val card2 = MemoryCard(id = 1L, imageUri = "uri", embedding = emb2)
        val card3 = MemoryCard(id = 1L, imageUri = "uri", embedding = emb3)

        assertEquals(card1, card2)
        assertEquals(card1.hashCode(), card2.hashCode())
        assertNotEquals(card1, card3)
    }

    @Test
    fun testProcessingStatusStreamlined() {
        // Must contain only PENDING, DONE, FAILED (exactly 3 values)
        val values = ProcessingStatus.values()
        assertEquals(3, values.size)
        assertEquals(ProcessingStatus.PENDING, ProcessingStatus.valueOf("PENDING"))
        assertEquals(ProcessingStatus.DONE, ProcessingStatus.valueOf("DONE"))
        assertEquals(ProcessingStatus.FAILED, ProcessingStatus.valueOf("FAILED"))
    }

    @Test
    fun testContentTypeValues() {
        val values = ContentType.values().map { it.name }
        val expected = listOf("THEORY", "PROBLEM", "SOLUTION", "CODE", "NOTES", "UNKNOWN")
        assertEquals(expected.sorted(), values.sorted())
    }
}
