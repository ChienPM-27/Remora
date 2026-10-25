package com.remora.resurfacing

import com.remora.core.model.contract.VectorMath
import com.remora.core.model.entity.MemoryCard
import com.remora.data.local.dao.MemoryCardDao

open class Stage1CandidateSelector(
    private val dao: MemoryCardDao
) {
    open suspend fun selectTopCandidates(
        newCard: MemoryCard,
        topN: Int = 15
    ): List<MemoryCard> {
        val newVector = newCard.embedding ?: return emptyList()

        val candidateEntities = dao.getCandidatesExcluding(newCard.id)
        if (candidateEntities.isEmpty()) return emptyList()

        val scored = mutableListOf<Pair<MemoryCard, Float>>()

        for (entity in candidateEntities) {
            val candidate = entity.toDomainModel()
            val candidateVector = candidate.embedding ?: continue
            val score = VectorMath.dotProduct(newVector, candidateVector)
            scored.add(candidate to score)
        }

        // Sort descending and take topN
        return scored.sortedByDescending { it.second }
            .take(topN)
            .map { it.first }
    }
}
