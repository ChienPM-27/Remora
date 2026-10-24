package com.remora.resurfacing

import com.remora.core.model.entity.MemoryCard
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
    suspend fun runResurfacingPipeline(newCard: MemoryCard): List<ResurfacedItem> {
        Timber.i("Running Resurfacing pipeline for card #${newCard.id}")

        // Stage 1: Top-15 on-device filtering
        val topCandidates = stage1Selector.selectTopCandidates(newCard, topN = 15)
        if (topCandidates.isEmpty()) {
            Timber.i("Zero candidates found for Resurfacing (empty database or single card)")
            return emptyList()
        }

        // Stage 2: Single batch LLM call
        val validResults = stage2Evaluator.evaluateBatch(newCard, topCandidates)
        if (validResults.isEmpty()) {
            Timber.i("Negative case: no genuine pedagogical links discovered")
            return emptyList()
        }

        // Persist to Room SQLite
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
        resurfacingResultDao.deleteResultsForCard(newCard.id)
        resurfacingResultDao.insertResults(entities)
        Timber.i("Persisted ${entities.size} resurfaced links for card #${newCard.id}")

        return validResults
    }
}
