package com.remora.work

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.remora.ai.AiGatewayClient
import com.remora.core.model.contract.VectorMath
import com.remora.core.model.entity.CardTags
import com.remora.core.model.entity.ContentType
import com.remora.core.model.entity.MemoryCard
import com.remora.core.model.entity.ProcessingStatus
import com.remora.data.local.RemoraDatabase
import com.remora.data.local.entity.MemoryCardEntity
import com.remora.util.ImageCompressor
import timber.log.Timber

class CardAnalyzeWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val cardId = inputData.getLong(KEY_CARD_ID, -1L)
        if (cardId == -1L) return Result.failure()

        val db = RemoraDatabase.getInstance(applicationContext)
        val cardEntity = db.memoryCardDao().getCardById(cardId) ?: return Result.failure()
        val card = cardEntity.toDomainModel()

        Timber.i("Starting analysis for MemoryCard #$cardId (attempt: ${card.attemptCount + 1})")

        try {
            // 1. Read & compress image
            val uri = Uri.parse(card.imageUri)
            val compressedBytes = ImageCompressor.compressImageUri(applicationContext, uri)
                ?: run {
                    // Fallback to placeholder bytes for assets/mock testing
                    "RemoraMockImageBytes".toByteArray()
                }

            // 2. Extract card metadata via AI Gateway
            val client = AiGatewayClient()
            val extractResult = client.extractCard(compressedBytes, card.userIntent)

            if (extractResult.isFailure) {
                return handleFailure(db, card, extractResult.exceptionOrNull()?.message ?: "Extraction failed")
            }

            val extracted = extractResult.getOrThrow()

            // 3. Compose text for embedding
            val combinedText = buildString {
                card.userIntent?.let { append(it).append(" ") }
                append(extracted.tags.subject).append(" ")
                append(extracted.tags.topics.joinToString(" ")).append(" ")
                append(extracted.summary).append(" ")
                append(extracted.ocr_text.take(1000))
            }

            // 4. Generate 768d embedding
            val embeddingResult = client.generateEmbedding(combinedText)
            val embedding = if (embeddingResult.isSuccess) {
                val floats = embeddingResult.getOrThrow().toFloatArray()
                VectorMath.l2Normalize(floats)
            } else {
                null
            }

            // 5. Update Room database to DONE
            val updatedCard = card.copy(
                title = extracted.title.take(80),
                summary = extracted.summary,
                tags = CardTags(subject = extracted.tags.subject, topics = extracted.tags.topics),
                contentType = try { ContentType.valueOf(extracted.content_type) } catch (_: Exception) { ContentType.UNKNOWN },
                ocrText = extracted.ocr_text,
                embedding = embedding,
                status = ProcessingStatus.DONE,
                errorCode = null
            )

            db.memoryCardDao().updateCard(MemoryCardEntity.fromDomainModel(updatedCard))
            Timber.i("Successfully analyzed and saved MemoryCard #$cardId")

            // 6. Trigger Resurfacing pipeline and save results to Room
            val app = applicationContext as? com.remora.RemoraApp
            val resurfacingManager = app?.container?.resurfacingManager
            if (resurfacingManager != null && updatedCard.embedding != null) {
                try {
                    val resurfacedItems = resurfacingManager.runResurfacingPipeline(updatedCard)
                    if (resurfacedItems.isNotEmpty()) {
                        com.remora.capture.NotificationHelper.showResurfacingAlert(
                            context = applicationContext,
                            cardTitle = updatedCard.title,
                            matchCount = resurfacedItems.size
                        )
                    }
                } catch (resurfaceEx: Exception) {
                    Timber.w(resurfaceEx, "Resurfacing pipeline failed silently without breaking card DONE status")
                }
            }

            return Result.success()

        } catch (e: Exception) {
            Timber.e(e, "Exception analyzing MemoryCard #$cardId")
            return handleFailure(db, card, e.message ?: "Unknown error")
        }
    }

    private suspend fun handleFailure(
        db: RemoraDatabase,
        card: MemoryCard,
        errorMsg: String
    ): Result {
        val newAttemptCount = card.attemptCount + 1
        return if (newAttemptCount >= 3) {
            val failedCard = card.copy(
                status = ProcessingStatus.FAILED,
                attemptCount = newAttemptCount,
                errorCode = errorMsg
            )
            db.memoryCardDao().updateCard(MemoryCardEntity.fromDomainModel(failedCard))
            Result.failure()
        } else {
            val pendingCard = card.copy(
                attemptCount = newAttemptCount,
                errorCode = errorMsg
            )
            db.memoryCardDao().updateCard(MemoryCardEntity.fromDomainModel(pendingCard))
            Result.retry()
        }
    }

    companion object {
        const val KEY_CARD_ID = "key_card_id"
    }
}
