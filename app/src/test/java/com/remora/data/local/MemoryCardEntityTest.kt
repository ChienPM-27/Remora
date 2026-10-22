package com.remora.data.local

import com.remora.core.model.entity.CardTags
import com.remora.core.model.entity.ContentType
import com.remora.core.model.entity.MemoryCard
import com.remora.core.model.entity.ProcessingStatus
import com.remora.data.local.entity.MemoryCardEntity
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class MemoryCardEntityTest {

    @Test
    fun testDomainToEntityAndBackRoundTrip() {
        val originalCard = MemoryCard(
            id = 42L,
            imageUri = "content://media/external/images/media/42",
            createdAt = 1700000000000L,
            userIntent = "Ôn thi cuối kỳ môn Giải Tích",
            title = "Đạo hàm riêng cấp 1",
            summary = "Khái niệm đạo hàm riêng cho hàm hai biến f(x, y).",
            tags = CardTags(subject = "Giải Tích", topics = listOf("ĐạoHàm", "Toán")),
            contentType = ContentType.THEORY,
            ocrText = "df/dx = lim (f(x+h, y) - f(x, y)) / h",
            embedding = floatArrayOf(0.12f, -0.34f, 0.56f, 0.78f),
            status = ProcessingStatus.DONE,
            attemptCount = 1,
            errorCode = null
        )

        val entity = MemoryCardEntity.fromDomainModel(originalCard)
        assertEquals(42L, entity.id)
        assertEquals("content://media/external/images/media/42", entity.imageUri)
        assertEquals("THEORY", entity.contentType)
        assertEquals("DONE", entity.status)
        assertEquals(1, entity.attemptCount)

        val restoredCard = entity.toDomainModel()
        assertEquals(originalCard.id, restoredCard.id)
        assertEquals(originalCard.imageUri, restoredCard.imageUri)
        assertEquals(originalCard.userIntent, restoredCard.userIntent)
        assertEquals(originalCard.title, restoredCard.title)
        assertEquals(originalCard.summary, restoredCard.summary)
        assertEquals(originalCard.tags.subject, restoredCard.tags.subject)
        assertEquals(originalCard.tags.topics, restoredCard.tags.topics)
        assertEquals(originalCard.contentType, restoredCard.contentType)
        assertEquals(originalCard.ocrText, restoredCard.ocrText)
        assertEquals(originalCard.status, restoredCard.status)
        assertEquals(originalCard.attemptCount, restoredCard.attemptCount)
        assertArrayEquals(originalCard.embedding, restoredCard.embedding, 1e-5f)
    }
}
