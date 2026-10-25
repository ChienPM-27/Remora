package com.remora.work

import android.content.Context
import android.net.Uri
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.remora.RemoraApp
import com.remora.ai.AiGatewayClient
import com.remora.ai.ApiException
import com.remora.ai.model.ExtractCardResponseDto
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
import com.remora.util.SensitiveDataRedactor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import java.util.concurrent.TimeUnit

const val RESURFACING_TIMEOUT_MS = 30_000L

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

        // Consent gate (default-deny): without explicit consent, pending content is never
        // processed or uploaded. The card stays PENDING; no image leaves the device.
        if (card.status != ProcessingStatus.DONE &&
            db.appSettingDao().getSetting()?.consentGranted != true
        ) {
            Timber.i("Analysis deferred for MemoryCard #$cardId: capture consent not granted")
            return Result.success()
        }

        // Guard: if already DONE with valid embedding, return success immediately without re-calling AI
        if (shouldSkipAnalysis(card)) {
            Timber.i("MemoryCard #$cardId is already DONE with embedding; skipping analysis.")
            return Result.success()
        }

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

            // Redact before any field is stored, embedded or logged: secrets must not survive
            // into Room, the embedding payload, or resurfacing inputs.
            val extracted = redactSensitive(extractResult.getOrThrow())

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

            // Reject invalid vectors before persisting: wrong dimension, NaN/Infinity or zero norm.
            // The existing row (including any previously valid embedding) stays untouched.
            if (!VectorMath.isValidEmbedding(normalizedEmbedding)) {
                Timber.e("Invalid embedding rejected for card #$cardId (size=${normalizedEmbedding.size}, finite=${normalizedEmbedding.all { it.isFinite() }})")
                return handleFailure(
                    db, card,
                    IllegalStateException("INVALID_EMBEDDING: response failed dimension/finiteness/norm validation")
                )
            }

            // 5. Update Room database to DONE first so extraction result is never lost if resurfacing fails or cancels
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
            db.memoryCardDao().updateCard(MemoryCardEntity.fromDomainModel(updatedCard))
            Timber.i("Successfully analyzed and saved MemoryCard #$cardId as DONE")

            // 6. Trigger Resurfacing pipeline with isolated error handling and timeout.
            // A resurfacing service error or timeout must not revert the card's DONE analysis status.
            val resurfacingManager = remoraApp?.container?.resurfacingManager
            if (resurfacingManager != null && updatedCard.embedding != null) {
                executeResurfacingWithIsolation(updatedCard) { targetCard ->
                    resurfacingManager.runResurfacingPipeline(targetCard)
                        .onSuccess { resurfacedItems ->
                            if (resurfacedItems.isNotEmpty()) {
                                val setting = db.appSettingDao().getSetting()
                                if (CapturePolicy.shouldShowResurfacingAlert(setting)) {
                                    NotificationHelper.showResurfacingAlert(
                                        context = applicationContext,
                                        sourceCardTitle = targetCard.title,
                                        resurfacedItems = resurfacedItems,
                                        newCardId = targetCard.id
                                    )
                                }
                            }
                        }
                        .onFailure { cause ->
                            Timber.w(cause, "Resurfacing failed with a service error; card #${targetCard.id} stays DONE")
                        }
                }
            }

            return Result.success()

        } catch (e: CancellationException) {
            Timber.w("CardAnalyzeWorker cancelled for card #$cardId")
            throw e
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
        val errorMsg = (error?.message ?: "Unknown error").take(MAX_ERROR_CODE_LENGTH)
        val newAttemptCount = card.attemptCount + 1

        // Non-retryable client errors (400-404, 413, 422): retrying the same payload cannot succeed.
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

        // Retryable errors (408, 429, 5xx, network timeouts, invalid embedding responses)
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

            val retryAfterSeconds = (error as? ApiException)?.retryAfterSeconds
            if (retryAfterSeconds != null && retryAfterSeconds > 0) {
                // Honor server-provided Retry-After with an explicit delayed re-enqueue
                // instead of WorkManager's own backoff schedule.
                Timber.i("429 Retry-After=$retryAfterSeconds s honored for card #${card.id}")
                scheduleDelayedRetry(card.id, retryAfterSeconds)
                Result.failure()
            } else {
                Result.retry()
            }
        }
    }

    private fun scheduleDelayedRetry(cardId: Long, delaySeconds: Long) {
        val request = OneTimeWorkRequestBuilder<CardAnalyzeWorker>()
            .setInputData(workDataOf(KEY_CARD_ID to cardId))
            .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
            .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(applicationContext)
            .enqueueUniqueWork(uniqueWorkName(cardId), ExistingWorkPolicy.REPLACE, request)
    }

    companion object {
        const val KEY_CARD_ID = "key_card_id"
        const val MAX_RETRIES = 3
        const val MAX_ERROR_CODE_LENGTH = 200

        fun uniqueWorkName(cardId: Long): String = "card-analysis-$cardId"
    }
}

/**
 * Applies [SensitiveDataRedactor] to every text field of the AI extraction so secrets never
 * reach Room, the embedding payload, or resurfacing inputs through a side field.
 */
internal fun redactSensitive(dto: ExtractCardResponseDto): ExtractCardResponseDto = dto.copy(
    title = SensitiveDataRedactor.redact(dto.title),
    summary = SensitiveDataRedactor.redact(dto.summary),
    ocr_text = SensitiveDataRedactor.redact(dto.ocr_text),
    tags = dto.tags.copy(
        subject = SensitiveDataRedactor.redact(dto.tags.subject),
        topics = dto.tags.topics.map { SensitiveDataRedactor.redact(it) }
    )
)

/**
 * Fast-path predicate: returns true if the card has already reached [ProcessingStatus.DONE]
 * and possesses a non-empty embedding, indicating analysis is already complete.
 */
internal fun shouldSkipAnalysis(card: MemoryCard): Boolean {
    val emb = card.embedding
    return card.status == ProcessingStatus.DONE && emb != null && emb.isNotEmpty()
}

/**
 * Executes [extractCard] only if the card is not already [ProcessingStatus.DONE] with embedding.
 * Returns true if analysis was performed, false if skipped.
 */
internal suspend fun analyzeCardIfRequired(
    card: MemoryCard,
    extractCard: suspend () -> Unit
): Boolean {
    if (shouldSkipAnalysis(card)) {
        return false
    }
    extractCard()
    return true
}

/**
 * Isolated runner for resurfacing pipeline with strict timeout and exception isolation.
 * Catches [TimeoutCancellationException] separately without re-throwing, ensuring a timeout never fails the worker.
 * Re-throws standard [CancellationException] to honor coroutine cancellation.
 * Swallows other exceptions so a resurfacing failure never invalidates an already-completed [ProcessingStatus.DONE] card.
 */
internal suspend fun executeResurfacingWithIsolation(
    card: MemoryCard,
    timeoutMs: Long = RESURFACING_TIMEOUT_MS,
    runPipeline: suspend (MemoryCard) -> Unit
) {
    try {
        withTimeout(timeoutMs) {
            runPipeline(card)
        }
    } catch (e: TimeoutCancellationException) {
        Timber.w("Resurfacing pipeline timed out after ${timeoutMs}ms for card #${card.id}")
    } catch (e: CancellationException) {
        Timber.w("Resurfacing pipeline cancelled for card #${card.id}")
        throw e
    } catch (e: Exception) {
        Timber.w(e, "Resurfacing pipeline failed silently; card #${card.id} remains DONE")
    }
}
