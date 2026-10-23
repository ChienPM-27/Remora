package com.remora.resurfacing

import com.remora.core.model.entity.CardTags
import com.remora.core.model.entity.ContentType
import com.remora.core.model.entity.MemoryCard
import com.remora.core.model.entity.ProcessingStatus
import com.remora.core.model.entity.ResurfaceGroup
import com.remora.core.model.entity.ResurfaceRelation
import com.remora.core.model.resurface.ResurfacedItem
import com.remora.data.local.dao.MemoryCardDao
import com.remora.data.local.dao.ResurfacingResultDao
import com.remora.data.local.entity.ResurfacingResultEntity
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class ResurfacingFlowTest {

    @Test
    fun testResurfaceResultEntityMapping() {
        val candidate = MemoryCard(
            id = 101,
            imageUri = "content://media/101",
            createdAt = 1000L,
            userIntent = "Bài tập Thales",
            title = "Tính độ dài đoạn thẳng bằng Thales",
            summary = "Áp dụng định lý Thales để tính cạnh tương ứng",
            tags = CardTags("Hình học", listOf("Thales")),
            contentType = ContentType.PROBLEM,
            ocrText = "Tính DE biết BC = 10",
            embedding = FloatArray(768) { 0.1f },
            status = ProcessingStatus.DONE
        )

        val item = ResurfacedItem(
            candidateCard = candidate,
            group = ResurfaceGroup.COMPLEMENTARY,
            relation = ResurfaceRelation.EXPLANATION,
            newInfo = "Lý thuyết bổ trợ cho bài tập",
            benefit = "Ôn lại phương pháp tính",
            reason = "Cùng chủ đề Thales",
            confidence = 0.92f
        )

        val entity = ResurfacingResultEntity(
            newCardId = 999L,
            candidateCardId = item.candidateCard.id,
            group = item.group.name,
            relation = item.relation.name,
            newInfo = item.newInfo,
            benefit = item.benefit,
            reason = item.reason,
            confidence = item.confidence
        )

        assertEquals(999L, entity.newCardId)
        assertEquals(101L, entity.candidateCardId)
        assertEquals("COMPLEMENTARY", entity.group)
        assertEquals("EXPLANATION", entity.relation)
        assertTrue(entity.confidence > 0.80f)
    }
}
