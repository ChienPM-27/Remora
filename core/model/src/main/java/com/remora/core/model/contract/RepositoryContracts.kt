package com.remora.core.model.contract

import com.remora.core.model.entity.MemoryCard
import com.remora.core.model.resurface.ResurfaceResult
import kotlinx.coroutines.flow.Flow

interface ResurfaceEngine {
    /**
     * So khớp và phân tích liên kết giữa ảnh mới và kho ảnh cũ
     */
    suspend fun resurface(
        target: MemoryCard,
        candidates: List<MemoryCard>
    ): Result<ResurfaceResult>
}

interface MemoryCardRepository {
    fun getAllCards(): Flow<List<MemoryCard>>
    suspend fun getCardById(id: Long): MemoryCard?
    suspend fun insertCard(card: MemoryCard): Long
    suspend fun updateCard(card: MemoryCard)
    suspend fun deleteCard(id: Long)
    suspend fun getAllWithEmbeddings(): List<MemoryCard>
    suspend fun searchHybrid(query: String, limit: Int = 20): List<MemoryCard>
}
