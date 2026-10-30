package com.remora.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.remora.RemoraApp
import com.remora.capture.CapturePermissions
import com.remora.capture.NotificationHelper
import com.remora.resurfacing.ResurfacingOutcome
import kotlinx.coroutines.CancellationException

class ResurfaceWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getLong(CardAnalyzeWorker.KEY_CARD_ID, -1)
        if (id < 0) return Result.failure()
        val container = (applicationContext as RemoraApp).container
        val result = container.resurfacingManager.runResurfacingPipeline(id)
        return when (result.outcome) {
            ResurfacingOutcome.COMPLETE -> {
                if (result.completedNow && result.items.isNotEmpty()) {
                    // An alert failure must not undo the durable AI checkpoint or request another batch.
                    try {
                        val preferences = container.database.appSettingDao().getSetting()
                        val source = container.database.memoryCardDao().getCardById(id)
                        if (source != null && preferences?.notificationsEnabled == true && CapturePermissions.canNotify(applicationContext)) {
                            NotificationHelper.showResurfacingAlert(applicationContext, source.title, result.items.size)
                        }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { }
                }
                Result.success()
            }
            ResurfacingOutcome.RETRY -> Result.retry()
            ResurfacingOutcome.FAILED -> Result.failure()
        }
    }
}
