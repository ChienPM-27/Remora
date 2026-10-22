package com.remora.search

import com.remora.ai.AiGatewayClient
import com.remora.core.model.contract.VectorMath
import com.remora.core.model.entity.MemoryCard
import com.remora.data.local.dao.MemoryCardDao
import timber.log.Timber

data class SearchResultCard(
    val card: MemoryCard,
    val similarityScore: Float
)

class SemanticSearchEngine(
    private val dao: MemoryCardDao,
    private val aiClient: AiGatewayClient
) {
    val RELEVANCE_THRESHOLD = 0.65f

    suspend fun search(rawQuery: String): List<SearchResultCard> {
        val resolved = TimeResolver.resolve(rawQuery)
        val cleanQuery = resolved.cleanQuery
        if (cleanQuery.isBlank()) return emptyList()

        Timber.i("Executing semantic search for: '$cleanQuery' (timeRange: ${resolved.startTimeMs}..${resolved.endTimeMs})")

        // 1. Fetch query vector
        val embeddingResult = aiClient.generateEmbedding(cleanQuery, taskType = "RETRIEVAL_QUERY")
        val queryVector = if (embeddingResult.isSuccess) {
            VectorMath.l2Normalize(embeddingResult.getOrThrow().toFloatArray())
        } else {
            Timber.w("Failed to embed search query, fallback to empty results")
            return emptyList()
        }

        // 2. Fetch candidates from Room SQLite
        val entities = if (resolved.startTimeMs != null && resolved.endTimeMs != null) {
            dao.getDoneCardsInTimeRange(resolved.startTimeMs, resolved.endTimeMs)
        } else {
            dao.getAllDoneCardsWithEmbedding()
        }

        // 3. Compute dot product similarity on-device
        val results = mutableListOf<SearchResultCard>()
        for (entity in entities) {
            val card = entity.toDomainModel()
            val cardVector = card.embedding ?: continue

            val similarity = VectorMath.dotProduct(queryVector, cardVector)
            if (similarity >= RELEVANCE_THRESHOLD) {
                results.add(SearchResultCard(card = card, similarityScore = similarity))
            }
        }

        // 4. Sort descending by similarity
        return results.sortedByDescending { it.similarityScore }
    }
}
