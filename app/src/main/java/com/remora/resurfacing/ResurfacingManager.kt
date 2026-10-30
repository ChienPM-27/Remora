package com.remora.resurfacing

import com.remora.ai.ApiException
import com.remora.core.model.entity.MemoryCard
import com.remora.core.model.resurface.ResurfaceValidator
import com.remora.core.model.resurface.ResurfacedItem
import com.remora.data.local.dao.MemoryCardDao
import com.remora.data.local.dao.ResurfacingResultDao
import com.remora.data.local.entity.ResurfacingResultEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

enum class ResurfacingOutcome { COMPLETE, RETRY, FAILED }

data class ResurfacingRunResult(
    val outcome: ResurfacingOutcome,
    val items: List<ResurfacedItem> = emptyList(),
    val completedNow: Boolean = false
)

class ResurfacingManager(
    private val memoryCardDao: MemoryCardDao,
    private val resurfacingResultDao: ResurfacingResultDao,
    private val selectCandidates: suspend (MemoryCard) -> List<MemoryCard>,
    private val evaluateBatch: suspend (MemoryCard, List<MemoryCard>) -> List<ResurfacedItem>
) {
    constructor(
        memoryCardDao: MemoryCardDao,
        resurfacingResultDao: ResurfacingResultDao,
        stage1Selector: Stage1CandidateSelector,
        stage2Evaluator: Stage2BatchEvaluator
    ) : this(memoryCardDao, resurfacingResultDao,
        { card -> stage1Selector.selectTopCandidates(card, topN = 15) }, stage2Evaluator::evaluateBatch)

    private val cardLocks = ConcurrentHashMap<Long, Mutex>()

    suspend fun runResurfacingPipeline(cardId: Long): ResurfacingRunResult =
        cardLocks.getOrPut(cardId) { Mutex() }.withLock {
            val card = memoryCardDao.getCardById(cardId) ?: return@withLock complete()
            if (card.status != "DONE") return@withLock complete()
            if (card.resurfacingStatus != "PENDING" || card.resurfacingAttemptCount >= MAX_ATTEMPTS) {
                return@withLock currentResult(cardId)
            }
            if (resurfacingResultDao.beginAttempt(cardId, MAX_ATTEMPTS) == 0) return@withLock currentResult(cardId)

            try {
                val source = card.toDomainModel()
                val candidates = selectCandidates(source).filterNot { it.id == cardId }.distinctBy { it.id }
                currentCoroutineContext().ensureActive()
                val current = memoryCardDao.getCardById(cardId)
                if (current?.status != "DONE" || current.resurfacingStatus != "PENDING") return@withLock currentResult(cardId)
                val evaluated = if (candidates.isEmpty()) emptyList() else evaluateBatch(source, candidates)
                currentCoroutineContext().ensureActive()
                val valid = ResurfaceValidator.filterValidItems(evaluated, candidates.map { it.id }.toSet())
                val createdAt = System.currentTimeMillis()
                val rows = valid.map { item ->
                    ResurfacingResultEntity(
                        newCardId = cardId, candidateCardId = item.candidateCard.id,
                        group = item.group.name, relation = item.relation.name,
                        newInfo = item.newInfo, benefit = item.benefit, reason = item.reason,
                        confidence = item.confidence, createdAt = createdAt
                    )
                }
                val committed = resurfacingResultDao.completeBatch(cardId, rows)
                val result = currentResult(cardId).copy(completedNow = committed)
                Timber.i("Resurfacing card_id=%d status=%s count=%d", cardId, result.outcome.name, result.items.size)
                result
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val latest = memoryCardDao.getCardById(cardId)
                if (latest?.status == "DONE" && latest.resurfacingStatus == "PENDING" &&
                    (latest.resurfacingAttemptCount >= MAX_ATTEMPTS || (error is ApiException && error.isNonRetryableClientError))) {
                    resurfacingResultDao.markFailed(cardId)
                }
                currentResult(cardId)
            }
        }

    private suspend fun currentResult(cardId: Long): ResurfacingRunResult {
        val card = memoryCardDao.getCardById(cardId) ?: return complete()
        if (card.status != "DONE") return complete()
        return when (card.resurfacingStatus) {
            "DONE" -> complete(cachedItems(cardId))
            "PENDING" -> {
                if (card.resurfacingAttemptCount >= MAX_ATTEMPTS) {
                    resurfacingResultDao.markFailed(cardId)
                    ResurfacingRunResult(ResurfacingOutcome.FAILED)
                } else ResurfacingRunResult(ResurfacingOutcome.RETRY)
            }
            else -> ResurfacingRunResult(ResurfacingOutcome.FAILED)
        }
    }

    private suspend fun cachedItems(cardId: Long): List<ResurfacedItem> {
        val items = resurfacingResultDao.getResultsForCard(cardId).mapNotNull { row ->
            if (row.candidateCardId == cardId) return@mapNotNull null
            memoryCardDao.getCardById(row.candidateCardId)?.toDomainModel()?.let(row::toDomainModel)
        }
        return ResurfaceValidator.filterValidItems(items, items.map { it.candidateCard.id }.toSet())
    }

    private fun complete(items: List<ResurfacedItem> = emptyList()) =
        ResurfacingRunResult(ResurfacingOutcome.COMPLETE, items)

    companion object { const val MAX_ATTEMPTS = 3 }
}
