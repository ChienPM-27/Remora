package com.remora.resurfacing

import com.remora.data.local.dao.MemoryCardDao
import com.remora.data.local.dao.ResurfacingResultDao
import com.remora.data.local.entity.MemoryCardEntity
import com.remora.data.local.entity.ResurfacingResultEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

internal class ResurfacingTestStore : MemoryCardDao, ResurfacingResultDao {
    val cards = MutableStateFlow<List<MemoryCardEntity>>(emptyList())
    val results = MutableStateFlow<List<ResurfacingResultEntity>>(emptyList())

    fun put(card: MemoryCardEntity) {
        cards.value = (cards.value.filterNot { it.id == card.id } + card).sortedByDescending { it.createdAt }
    }

    override fun getAllCardsFlow() = cards
    override suspend fun getCardById(id: Long) = cards.value.firstOrNull { it.id == id }
    override suspend fun insertCard(card: MemoryCardEntity): Long {
        val id = card.id.takeIf { it != 0L } ?: ((cards.value.maxOfOrNull { it.id } ?: 0) + 1)
        put(card.copy(id = id))
        return id
    }
    override suspend fun updateCard(card: MemoryCardEntity) {
        if (getCardById(card.id) != null) put(card)
    }
    override suspend fun deleteCard(id: Long) {
        cards.value = cards.value.filterNot { it.id == id }
        results.value = results.value.filterNot { it.newCardId == id || it.candidateCardId == id }
    }
    override suspend fun getAllDoneCardsWithEmbedding() = cards.value.filter { it.status == "DONE" && it.embedding != null }
    override suspend fun getCandidatesExcluding(excludeId: Long) = getAllDoneCardsWithEmbedding().filterNot { it.id == excludeId }
    override suspend fun getDoneCardsInTimeRange(startTime: Long, endTime: Long) =
        getAllDoneCardsWithEmbedding().filter { it.createdAt in startTime..endTime }
    override suspend fun getCardCount() = cards.value.size
    override suspend fun getDoneCardCount() = getAllDoneCardsWithEmbedding().size
    override suspend fun deleteAllCards() { cards.value = emptyList(); results.value = emptyList() }
    override suspend fun getCardByHash(hash: String) = cards.value.firstOrNull { it.imageHash == hash }
    override suspend fun getPendingCards() = cards.value.filter { it.status == "PENDING" }
    override suspend fun getPendingResurfacing() = cards.value.filter { it.status == "DONE" && it.resurfacingStatus == "PENDING" }
    override suspend fun resetAuthenticationFailures() = Unit

    override fun getRecentResultsFlow() = results
    override fun getResultsForCardFlow(newCardId: Long) = results.map { it.filter { row -> row.newCardId == newCardId } }
    override suspend fun getResultsForCard(newCardId: Long) = results.value.filter { it.newCardId == newCardId }.sortedByDescending { it.confidence }
    override suspend fun insertResults(results: List<ResurfacingResultEntity>) { this.results.value += results }
    override suspend fun deleteResultsForCard(newCardId: Long) { results.value = results.value.filterNot { it.newCardId == newCardId } }
    override suspend fun deleteAllResults() { results.value = emptyList() }
    override suspend fun getExistingCardIds(ids: List<Long>) = cards.value.map { it.id }.filter { it in ids }

    override suspend fun beginAttempt(cardId: Long, maxAttempts: Int): Int {
        val card = getCardById(cardId) ?: return 0
        if (card.status != "DONE" || card.resurfacingStatus != "PENDING" || card.resurfacingAttemptCount >= maxAttempts) return 0
        put(card.copy(resurfacingAttemptCount = card.resurfacingAttemptCount + 1))
        return 1
    }
    override suspend fun markCompleted(cardId: Long): Int {
        val card = getCardById(cardId) ?: return 0
        if (card.status != "DONE" || card.resurfacingStatus != "PENDING") return 0
        put(card.copy(resurfacingStatus = "DONE"))
        return 1
    }
    override suspend fun markFailed(cardId: Long): Int {
        val card = getCardById(cardId) ?: return 0
        if (card.status != "DONE" || card.resurfacingStatus != "PENDING") return 0
        put(card.copy(resurfacingStatus = "FAILED"))
        return 1
    }
    override suspend fun resetFailed(cardId: Long): Int {
        val card = getCardById(cardId) ?: return 0
        if (card.status != "DONE" || card.resurfacingStatus != "FAILED") return 0
        put(card.copy(resurfacingStatus = "PENDING", resurfacingAttemptCount = 0))
        return 1
    }
}
