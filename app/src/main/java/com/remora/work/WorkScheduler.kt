package com.remora.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

interface CardWorkScheduler {
    fun analyze(cardId: Long)
    fun resurface(cardId: Long)
    fun retryResurface(cardId: Long) { resurface(cardId) }
    fun cancel(cardId: Long)
    fun cancelAll()
}

class WorkScheduler(context: Context) : CardWorkScheduler {
    private val workManager = WorkManager.getInstance(context)

    override fun analyze(cardId: Long) {
        val request = OneTimeWorkRequestBuilder<CardAnalyzeWorker>()
            .setInputData(workDataOf(CardAnalyzeWorker.KEY_CARD_ID to cardId))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(TAG)
            .addTag(cardTag(cardId))
            .build()
        workManager.enqueueUniqueWork("analyze-$cardId", ExistingWorkPolicy.KEEP, request)
    }

    override fun cancel(cardId: Long) { workManager.cancelAllWorkByTag(cardTag(cardId)) }

    override fun resurface(cardId: Long) { enqueueResurfacing(cardId, ExistingWorkPolicy.KEEP) }

    override fun retryResurface(cardId: Long) { enqueueResurfacing(cardId, ExistingWorkPolicy.REPLACE) }

    private fun enqueueResurfacing(cardId: Long, policy: ExistingWorkPolicy) {
        val request = OneTimeWorkRequestBuilder<ResurfaceWorker>()
            .setInputData(workDataOf(CardAnalyzeWorker.KEY_CARD_ID to cardId))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(TAG).addTag(cardTag(cardId)).build()
        workManager.enqueueUniqueWork("resurface-$cardId", policy, request)
    }
    override fun cancelAll() { workManager.cancelAllWorkByTag(TAG) }

    companion object {
        const val TAG = "remora-card-work"
        fun cardTag(cardId: Long) = "remora-card-$cardId"
    }
}
