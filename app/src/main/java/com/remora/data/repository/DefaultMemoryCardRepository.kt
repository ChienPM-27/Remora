package com.remora.data.repository

import com.remora.core.model.contract.MemoryCardRepository
import com.remora.core.model.entity.MemoryCard
import com.remora.data.local.dao.MemoryCardDao
import com.remora.data.local.entity.MemoryCardEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DefaultMemoryCardRepository(
    private val dao: MemoryCardDao
) : MemoryCardRepository {

    override fun getAllCards(): Flow<List<MemoryCard>> {
        return dao.getAllCardsFlow().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override suspend fun getCardById(id: Long): MemoryCard? {
        return dao.getCardById(id)?.toDomainModel()
    }

    override suspend fun insertCard(card: MemoryCard): Long {
        val entity = MemoryCardEntity.fromDomainModel(card)
        return dao.insertCard(entity)
    }

    override suspend fun updateCard(card: MemoryCard) {
        val previous = dao.getCardById(card.id) ?: return
        val entity = MemoryCardEntity.fromDomainModel(card).copy(
            imageHash = previous.imageHash,
            extractionComplete = previous.extractionComplete,
            resurfacingStatus = previous.resurfacingStatus,
            resurfacingAttemptCount = previous.resurfacingAttemptCount
        )
        dao.updateCard(entity)
    }

    override suspend fun deleteCard(id: Long) {
        dao.deleteCard(id)
    }

    override suspend fun getAllWithEmbeddings(): List<MemoryCard> {
        return dao.getAllDoneCardsWithEmbedding().map { it.toDomainModel() }
    }

    override suspend fun searchHybrid(query: String, limit: Int): List<MemoryCard> {
        // Simple text pre-filter; semantic search handled by SemanticSearchEngine
        return getAllWithEmbeddings().filter { card ->
            card.title.contains(query, ignoreCase = true) ||
                    card.summary.contains(query, ignoreCase = true) ||
                    card.tags.subject.contains(query, ignoreCase = true) ||
                    (card.userIntent?.contains(query, ignoreCase = true) == true)
        }.take(limit)
    }
}
