package com.remora.resurfacing

import com.remora.core.model.entity.MemoryCard
import com.remora.core.model.entity.ResurfaceGroup
import com.remora.core.model.entity.ResurfaceRelation
import com.remora.core.model.resurface.ResurfacedItem
import com.remora.data.local.dao.MemoryCardDao
import com.remora.data.local.dao.ResurfacingResultDao
import com.remora.data.local.entity.ResurfacingResultEntity
import timber.log.Timber

class ResurfacingManager(
    private val memoryCardDao: MemoryCardDao,
    private val resurfacingResultDao: ResurfacingResultDao,
    private val stage1Selector: Stage1CandidateSelector,
    private val stage2Evaluator: Stage2BatchEvaluator
) {
    /**
     * Returns:
     * - Result.success(items): validated resurfaced items; empty list is a valid negative case.
     * - Result.failure(cause): recoverable service error. The caller keeps the card's DONE
     *   analysis status and may retry later; nothing is persisted in this case.
     *
     * Idempotency: results already stored for the card are returned without a new LLM call,
     * and persistence replaces (not appends) previous rows for the card.
     */
    suspend fun runResurfacingPipeline(newCard: MemoryCard): Result<List<ResurfacedItem>> {
        Timber.i("Running Resurfacing pipeline for card #${newCard.id}")

        // Cost rule: never call the LLM again when a result is already stored for this card.
        val stored = resurfacingResultDao.getResultsForCard(newCard.id)
        if (stored.isNotEmpty()) {
            Timber.i("Reusing ${stored.size} stored resurfacing results for card #${newCard.id} (no new LLM call)")
            return Result.success(stored.mapNotNull { it.toResurfacedItem(memoryCardDao) })
        }

        // Stage 1: Top-15 on-device filtering
        val topCandidates = stage1Selector.selectTopCandidates(newCard, topN = 15)
        if (topCandidates.isEmpty()) {
            Timber.i("Zero candidates found for Resurfacing (empty database or single card)")
            return Result.success(emptyList())
        }

        // Stage 2: Single batch LLM call (text metadata only, no images, no OCR)
        val evaluation = stage2Evaluator.evaluateBatch(newCard, topCandidates)
        val validResults = evaluation.getOrElse { cause ->
            Timber.w(cause, "Resurfacing service error for card #${newCard.id}; card stays DONE")
            return Result.failure(cause)
        }
        if (validResults.isEmpty()) {
            Timber.i("Negative case: no genuine pedagogical links discovered for card #${newCard.id}")
            return Result.success(emptyList())
        }

        // Persist idempotently: replace any prior rows for this card in one transaction.
        val entities = validResults.map { item ->
            ResurfacingResultEntity(
                newCardId = newCard.id,
                candidateCardId = item.candidateCard.id,
                group = item.group.name,
                relation = item.relation.name,
                newInfo = item.newInfo,
                benefit = item.benefit,
                reason = item.reason,
                confidence = item.confidence
            )
        }
        resurfacingResultDao.replaceResultsForCard(newCard.id, entities)
        Timber.i("Persisted ${entities.size} resurfaced links for card #${newCard.id}")

        return Result.success(validResults)
    }

    private suspend fun ResurfacingResultEntity.toResurfacedItem(
        memoryCardDao: MemoryCardDao
    ): ResurfacedItem? {
        val candidate = memoryCardDao.getCardById(candidateCardId)?.toDomainModel() ?: return null
        val group = try { ResurfaceGroup.valueOf(group) } catch (_: Exception) { return null }
        val relation = try { ResurfaceRelation.valueOf(relation) } catch (_: Exception) { return null }
        return ResurfacedItem(
            candidateCard = candidate,
            group = group,
            relation = relation,
            newInfo = newInfo,
            benefit = benefit,
            reason = reason,
            confidence = confidence
        )
    }
}
