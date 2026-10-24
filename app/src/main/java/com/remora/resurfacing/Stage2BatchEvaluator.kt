package com.remora.resurfacing

import com.remora.ai.AiGatewayClient
import com.remora.ai.model.CandidateItemDto
import com.remora.ai.model.CardTagsDto
import com.remora.ai.model.ResurfaceBatchRequestDto
import com.remora.core.model.entity.MemoryCard
import com.remora.core.model.entity.ResurfaceGroup
import com.remora.core.model.entity.ResurfaceRelation
import com.remora.core.model.resurface.ResurfaceValidator
import com.remora.core.model.resurface.ResurfacedItem
import timber.log.Timber

open class Stage2BatchEvaluator(
    private val client: AiGatewayClient
) {
    open suspend fun evaluateBatch(
        newCard: MemoryCard,
        candidates: List<MemoryCard>
    ): List<ResurfacedItem> {
        if (candidates.isEmpty()) return emptyList()

        val candidateMap = candidates.associateBy { it.id }

        val newCardDto = CandidateItemDto(
            candidate_id = newCard.id,
            title = newCard.title,
            summary = newCard.summary,
            tags = CardTagsDto(subject = newCard.tags.subject, topics = newCard.tags.topics),
            content_type = newCard.contentType.name,
            user_intent = newCard.userIntent
        )

        val candidateDtos = candidates.map { c ->
            CandidateItemDto(
                candidate_id = c.id,
                title = c.title,
                summary = c.summary,
                tags = CardTagsDto(subject = c.tags.subject, topics = c.tags.topics),
                content_type = c.contentType.name,
                user_intent = c.userIntent
            )
        }

        val request = ResurfaceBatchRequestDto(
            new_card = newCardDto,
            candidates = candidateDtos
        )

        val responseResult = client.evaluateResurfacing(request)
        if (responseResult.isFailure) {
            Timber.w("Resurfacing batch request failed")
            return emptyList()
        }

        val batchResponse = responseResult.getOrThrow()
        val rawItems = mutableListOf<ResurfacedItem>()

        for (itemDto in batchResponse.results) {
            val candidateCard = candidateMap[itemDto.candidate_id] ?: continue
            val group = try { ResurfaceGroup.valueOf(itemDto.group) } catch (_: Exception) { continue }
            val relation = try { ResurfaceRelation.valueOf(itemDto.relation) } catch (_: Exception) { continue }

            rawItems.add(
                ResurfacedItem(
                    candidateCard = candidateCard,
                    group = group,
                    relation = relation,
                    newInfo = itemDto.new_info,
                    benefit = itemDto.benefit,
                    reason = itemDto.reason,
                    confidence = itemDto.confidence
                )
            )
        }

        // Apply strict validation rules: confidence > 0.80, allowed IDs, group-relation compatibility, max 2 items per group
        return ResurfaceValidator.filterValidItems(rawItems, allowedCandidateIds = candidateMap.keys)
    }
}
