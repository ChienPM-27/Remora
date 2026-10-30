package com.remora.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.remora.RemoraApp
import com.remora.capture.CapturePermissions
import com.remora.capture.NotificationHelper
import kotlinx.coroutines.CancellationException

class ResurfaceWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getLong(CardAnalyzeWorker.KEY_CARD_ID, -1)
        val container = (applicationContext as RemoraApp).container
        val dao = container.database.memoryCardDao()
        val card = dao.getCardById(id) ?: return Result.success()
        if (card.status != "DONE" || card.resurfacingStatus == "DONE") return Result.success()
        if (card.resurfacingStatus == "FAILED") return Result.failure()
        if (card.resurfacingAttemptCount >= CardAnalysisPipeline.MAX_ATTEMPTS) {
            dao.updateCard(card.copy(resurfacingStatus = "FAILED"))
            return Result.failure()
        }
        dao.updateCard(card.copy(resurfacingAttemptCount = card.resurfacingAttemptCount + 1))
        return try {
            val items = container.resurfacingManager.runResurfacingPipeline(card.toDomainModel())
            val latest = dao.getCardById(id) ?: return Result.success()
            dao.updateCard(latest.copy(resurfacingStatus = "DONE"))
            val preferences = container.database.appSettingDao().getSetting()
            if (items.isNotEmpty() && preferences?.notificationsEnabled == true && CapturePermissions.canNotify(applicationContext)) {
                NotificationHelper.showResurfacingAlert(applicationContext, latest.title, items.size)
            }
            Result.success()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            val latest = dao.getCardById(id) ?: return Result.success()
            val failed = latest.resurfacingAttemptCount >= CardAnalysisPipeline.MAX_ATTEMPTS
            dao.updateCard(latest.copy(resurfacingStatus = if (failed) "FAILED" else "PENDING"))
            if (failed) Result.failure() else Result.retry()
        }
    }
}
