package com.remora.work

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.remora.RemoraApp
import com.remora.ai.AiGatewayClient
import com.remora.ai.ApiException
import com.remora.capture.CapturePolicy
import com.remora.capture.NotificationHelper
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

        Timber.i("Starting analysis for MemoryCard #$cardId (current attempts: ${card.attemptCount})")

        try {
            // 1. Read & compress image (No mock bytes permitted: if unreadable -> mark FAILED)
            val uri = Uri.parse(card.imageUri)
            val compressedBytes = ImageCompressor.compressImageUri(applicationContext, uri)
            if (compressedBytes == null) {
                Timber.e("Failed to read or compress image for card #$cardId: ${card.imageUri}")
                val failedCard = card.copy(
                    status = ProcessingStatus.FAILED,
                    attemptCount = card.attemptCount + 1,
                    errorCode = "UNREADABLE_IMAGE: File cannot be read or compressed from device"
                )
                db.memoryCardDao().updateCard(MemoryCardEntity.fromDomainModel(failedCard))
                return Result.failure()
            }

            // 2. Extract card metadata via AI Gateway (singleton from AppContainer)
            val remoraApp = applicationContext as? RemoraApp
                ?: applicationContext.applicationContext as? RemoraApp
            val client = remoraApp?.container?.aiGatewayClient ?: AiGatewayClient()
            val extractResult = client.extractCard(compressedBytes, card.userIntent)

            if (extractResult.isFailure) {
                val error = extractResult.exceptionOrNull()
                return handleFailure(db, card, error)
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
            if (embeddingResult.isFailure) {
                val error = embeddingResult.exceptionOrNull()
                Timber.w("Embedding generation failed for card #$cardId, triggering retry policy")
                return handleFailure(db, card, error)
            }

            val rawEmbedding = embeddingResult.getOrThrow().toFloatArray()
            val normalizedEmbedding = VectorMath.l2Normalize(rawEmbedding)

            // 5. Update Room database to DONE
            val updatedCard = card.copy(
                title = extracted.title.take(80),
                summary = extracted.summary,
                tags = CardTags(subject = extracted.tags.subject, topics = extracted.tags.topics),
                contentType = try { ContentType.valueOf(extracted.content_type) } catch (_: Exception) { ContentType.UNKNOWN },
                ocrText = extracted.ocr_text,
                embedding = normalizedEmbedding,
                status = ProcessingStatus.DONE,
                errorCode = null
            )

            // 6. Trigger Resurfacing pipeline and save results to Room before marking card as DONE
            // This guarantees resurfacing results are already persisted when observers see card transition to DONE, eliminating UI flicker.
            val resurfacingManager = remoraApp?.container?.resurfacingManager
            if (resurfacingManager != null && updatedCard.embedding != null) {
                try {
                    val resurfacedItems = resurfacingManager.runResurfacingPipeline(updatedCard)
                    if (resurfacedItems.isNotEmpty()) {
                        val setting = db.appSettingDao().getSetting()
                        if (CapturePolicy.shouldShowResurfacingAlert(setting)) {
                            NotificationHelper.showResurfacingAlert(
                                context = applicationContext,
                                sourceCardTitle = updatedCard.title,
                                resurfacedItems = resurfacedItems,
                                newCardId = updatedCard.id
                            )
                        }
                    }
                } catch (resurfaceEx: Exception) {
                    Timber.w(resurfaceEx, "Resurfacing pipeline failed silently without breaking card DONE status")
                }
            }

            db.memoryCardDao().updateCard(MemoryCardEntity.fromDomainModel(updatedCard))
            Timber.i("Successfully analyzed and saved MemoryCard #$cardId as DONE")

            return Result.success()

        } catch (e: Exception) {
            Timber.e(e, "Unexpected exception analyzing MemoryCard #$cardId")
            return handleFailure(db, card, e)
        }
    }

    private suspend fun handleFailure(
        db: RemoraDatabase,
        card: MemoryCard,
        error: Throwable?
    ): Result {
        val errorMsg = error?.message ?: "Unknown error"
        val newAttemptCount = card.attemptCount + 1

        // Distinguish non-retryable client errors (400, 401, 403, 404)
        if (error is ApiException && error.isNonRetryableClientError) {
            Timber.e("Non-retryable client error (HTTP ${error.statusCode}) for card #${card.id}. Marking FAILED.")
            val failedCard = card.copy(
                status = ProcessingStatus.FAILED,
                attemptCount = newAttemptCount,
                errorCode = "CLIENT_ERROR_${error.statusCode}: $errorMsg"
            )
            db.memoryCardDao().updateCard(MemoryCardEntity.fromDomainModel(failedCard))
            return Result.failure()
        }

        // Retryable errors (429, 5xx, or network timeouts)
        return if (newAttemptCount >= MAX_RETRIES) {
            Timber.e("Max retries ($MAX_RETRIES) exceeded for card #${card.id}. Marking FAILED.")
            val failedCard = card.copy(
                status = ProcessingStatus.FAILED,
                attemptCount = newAttemptCount,
                errorCode = errorMsg
            )
            db.memoryCardDao().updateCard(MemoryCardEntity.fromDomainModel(failedCard))
            Result.failure()
        } else {
            Timber.w("Retryable error for card #${card.id} (attempt $newAttemptCount/$MAX_RETRIES). Requeueing with backoff.")
            val pendingCard = card.copy(
                status = ProcessingStatus.PENDING,
                attemptCount = newAttemptCount,
                errorCode = errorMsg
            )
            db.memoryCardDao().updateCard(MemoryCardEntity.fromDomainModel(pendingCard))
            Result.retry()
        }
    }

    companion object {
        const val KEY_CARD_ID = "key_card_id"
        const val MAX_RETRIES = 3
    }
}
