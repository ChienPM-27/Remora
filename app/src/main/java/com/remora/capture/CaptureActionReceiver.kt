package com.remora.capture

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.remora.RemoraApp
import com.remora.core.model.entity.CardTags
import com.remora.core.model.entity.ContentType
import com.remora.core.model.entity.MemoryCard
import com.remora.core.model.entity.ProcessingStatus
import com.remora.data.local.RemoraDatabase
import com.remora.data.local.entity.AppSettingEntity
import com.remora.data.local.entity.MemoryCardEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

class CaptureActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Timber.i("CaptureActionReceiver received action: $action")

        NotificationHelper.dismissNotification(context)

        when (action) {
            NotificationHelper.ACTION_SAVE -> {
                val imageUriStr = intent.getStringExtra(NotificationHelper.EXTRA_IMAGE_URI) ?: return
                val remoteInputResults = RemoteInput.getResultsFromIntent(intent)
                val userIntent = remoteInputResults?.getCharSequence(NotificationHelper.EXTRA_USER_INTENT)?.toString()

                CoroutineScope(Dispatchers.IO).launch {
                    val db = RemoraDatabase.getInstance(context)
                    val card = MemoryCard(
                        imageUri = imageUriStr,
                        createdAt = System.currentTimeMillis(),
                        userIntent = userIntent,
                        title = "Đang xử lý...",
                        status = ProcessingStatus.PENDING
                    )
                    val newId = db.memoryCardDao().insertCard(MemoryCardEntity.fromDomainModel(card))
                    Timber.i("Inserted PENDING memory card #$newId into Room SQLite")

                    // Enqueue background analysis worker
                    val workRequest = androidx.work.OneTimeWorkRequestBuilder<com.remora.work.CardAnalyzeWorker>()
                        .setConstraints(
                            androidx.work.Constraints.Builder()
                                .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                                .build()
                        )
                        .setInputData(
                            androidx.work.workDataOf(com.remora.work.CardAnalyzeWorker.KEY_CARD_ID to newId)
                        )
                        .build()

                    androidx.work.WorkManager.getInstance(context).enqueue(workRequest)
                }
            }

            NotificationHelper.ACTION_SKIP -> {
                Timber.i("User skipped screenshot saving. Zero data leaves device.")
            }

            NotificationHelper.ACTION_PAUSE -> {
                CoroutineScope(Dispatchers.IO).launch {
                    val db = RemoraDatabase.getInstance(context)
                    db.appSettingDao().insertOrUpdate(
                        AppSettingEntity(id = 1L, notificationsEnabled = true, paused = true)
                    )
                    Timber.i("Screenshot monitoring paused by user.")
                }
            }
        }
    }
}
