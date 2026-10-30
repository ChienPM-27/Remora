package com.remora.work

import com.remora.ai.ApiException
import com.remora.ai.model.ExtractCardResponseDto
import com.remora.core.model.entity.CardTags
import com.remora.data.local.entity.MemoryCardEntity
import com.remora.data.local.entity.StoredEmbedding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException

enum class AnalysisOutcome { COMPLETE, RETRY, FAILED }

/** Resumable stage logic; callbacks let tests interrupt the real pipeline at persistence boundaries. */
class CardAnalysisPipeline(
    private val load: suspend (Long) -> MemoryCardEntity?,
    private val persist: suspend (MemoryCardEntity) -> Unit,
    private val extract: suspend (MemoryCardEntity) -> ExtractCardResponseDto,
    private val embed: suspend (String) -> FloatArray
) {
    suspend fun run(cardId: Long): AnalysisOutcome {
        var card = load(cardId) ?: return AnalysisOutcome.COMPLETE
        if (card.status == "DONE") return AnalysisOutcome.COMPLETE
        if (card.status == "FAILED") return AnalysisOutcome.FAILED
        if (card.attemptCount >= MAX_ATTEMPTS) {
            persist(card.copy(status = "FAILED", errorCode = card.errorCode ?: "ATTEMPTS_EXHAUSTED"))
            return AnalysisOutcome.FAILED
        }
        card = card.copy(attemptCount = card.attemptCount + 1)
        persist(card)
        try {
            if (!card.extractionComplete) {
                val result = extract(card)
                currentCoroutineContext().ensureActive()
                if (load(cardId) == null) return AnalysisOutcome.COMPLETE
                card = card.copy(title = result.title, summary = result.summary,
                    tagsJson = Json.encodeToString(CardTags(result.tags.subject, result.tags.topics)),
                    contentType = result.content_type, ocrText = result.ocr_text, extractionComplete = true)
                persist(card)
            }
            if (load(cardId) == null) return AnalysisOutcome.COMPLETE
            val model = card.toDomainModel()
            val text = listOfNotNull(model.userIntent, model.tags.subject,
                model.tags.topics.joinToString(" "), model.summary, model.ocrText.take(1000)).joinToString("\n")
            val vector = embed(text)
            require(StoredEmbedding.isValid(vector)) { "INVALID_VECTOR" }
            currentCoroutineContext().ensureActive()
            if (load(cardId) == null) return AnalysisOutcome.COMPLETE
            persist(card.copy(status = "DONE", embedding = StoredEmbedding.encode(vector), errorCode = null))
            return AnalysisOutcome.COMPLETE
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            val latest = load(cardId) ?: return AnalysisOutcome.COMPLETE
            val permanent = (error is ApiException && error.isNonRetryableClientError) || error is UnreadableImageException
            val failed = permanent || latest.attemptCount >= MAX_ATTEMPTS
            persist(latest.copy(status = if (failed) "FAILED" else "PENDING", errorCode = errorCode(error)))
            return if (failed) AnalysisOutcome.FAILED else AnalysisOutcome.RETRY
        }
    }

    companion object {
        const val MAX_ATTEMPTS = 3
        fun errorCode(error: Exception): String = when (error) {
            is ApiException -> if (error.statusCode == 401) "SIGN_IN_REQUIRED" else "HTTP_${error.statusCode}"
            is UnreadableImageException -> "UNREADABLE_IMAGE"
            is IOException -> "NETWORK_UNAVAILABLE"
            else -> "INVALID_RESPONSE"
        }
    }
}

class UnreadableImageException : IOException("UNREADABLE_IMAGE")
