package com.remora.work

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.remora.RemoraApp
import com.remora.util.ImageCompressor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CardAnalyzeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getLong(KEY_CARD_ID, -1L)
        if (id < 0) return Result.failure()
        val container = (applicationContext as RemoraApp).container
        val dao = container.database.memoryCardDao()
        val client = container.aiGatewayClient
        val pipeline = CardAnalysisPipeline(
            load = dao::getCardById,
            persist = dao::updateCard,
            extract = { card ->
                val bytes = withContext(Dispatchers.IO) {
                    ImageCompressor.compressImageUri(applicationContext, Uri.parse(card.imageUri))
                } ?: throw UnreadableImageException()
                client.extractCard(bytes, card.userIntent).getOrThrow()
            },
            embed = { text -> client.generateEmbedding(text).getOrThrow().toFloatArray() }
        )
        return when (pipeline.run(id)) {
            AnalysisOutcome.COMPLETE -> {
                val card = dao.getCardById(id)
                if (card?.status == "DONE" && card.resurfacingStatus == "PENDING") WorkScheduler(applicationContext).resurface(id)
                Result.success()
            }
            AnalysisOutcome.RETRY -> Result.retry()
            AnalysisOutcome.FAILED -> Result.failure()
        }
    }

    companion object {
        const val KEY_CARD_ID = "key_card_id"
        const val MAX_RETRIES = CardAnalysisPipeline.MAX_ATTEMPTS
    }
}