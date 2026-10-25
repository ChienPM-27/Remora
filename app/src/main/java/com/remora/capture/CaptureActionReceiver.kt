package com.remora.capture

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.remora.core.model.entity.MemoryCard
import com.remora.core.model.entity.ProcessingStatus
import com.remora.data.local.RemoraDatabase
import com.remora.data.local.entity.MemoryCardEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import timber.log.Timber

object CaptureActionHandler {
    suspend fun handleAction(
        action: String,
        imageUriStr: String?,
        userIntent: String?,
        memoryCardDao: com.remora.data.local.dao.MemoryCardDao,
        onEnqueueWorker: (Long) -> Unit = {},
        onApplyCaptureAction: suspend (String) -> Unit = {},
        onResolve: (String) -> Unit
    ) {
        if (imageUriStr == null) return

        when (action) {
            NotificationHelper.ACTION_SAVE -> {
                val existingCard = memoryCardDao.getCardByImageUri(imageUriStr)
                if (existingCard != null) {
                    Timber.i("MemoryCard for uri $imageUriStr already exists (#${existingCard.id}), skipping duplicate insert")
                } else {
                    val card = MemoryCard(
                        imageUri = imageUriStr,
                        createdAt = System.currentTimeMillis(),
                        userIntent = userIntent,
                        title = "Đang xử lý...",
                        status = ProcessingStatus.PENDING
                    )
                    val newId = memoryCardDao.insertCard(MemoryCardEntity.fromDomainModel(card))
                    Timber.i("Inserted PENDING memory card #$newId into Room SQLite")
                    onEnqueueWorker(newId)
                }
                onResolve(imageUriStr)
            }

            NotificationHelper.ACTION_SKIP -> {
                Timber.i("User skipped screenshot saving. Zero data leaves device.")
                onResolve(imageUriStr)
            }

            NotificationHelper.ACTION_MUTE_1H,
            NotificationHelper.ACTION_PAUSE -> {
                onApplyCaptureAction(action)
                Timber.i("Screenshot capture action applied: $action")
                onResolve(imageUriStr)
            }
        }
    }
}

class CaptureActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Timber.i("CaptureActionReceiver received action: $action")

        if (action == NotificationHelper.ACTION_SAVE) {
            NotificationHelper.showSaveSuccess(context)
        } else {
            NotificationHelper.dismissNotification(context)
        }

        // Hold process execution with goAsync() during asynchronous background database insertion
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                withTimeout(8_000L) {
                    val imageUriStr = intent.getStringExtra(NotificationHelper.EXTRA_IMAGE_URI)
                    val remoteInputResults = RemoteInput.getResultsFromIntent(intent)
                    val userIntent = remoteInputResults?.getCharSequence(NotificationHelper.EXTRA_USER_INTENT)?.toString()
                    val db = RemoraDatabase.getInstance(context)

                    CaptureActionHandler.handleAction(
                        action = action,
                        imageUriStr = imageUriStr,
                        userIntent = userIntent,
                        memoryCardDao = db.memoryCardDao(),
                        onEnqueueWorker = { newId ->
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
                        },
                        onApplyCaptureAction = { act ->
                            CapturePolicy.applyCaptureAction(act, db.appSettingDao(), System.currentTimeMillis())
                        },
                        onResolve = { uri ->
                            PendingCaptureStore.getInstance(context).resolve(uri)
                        }
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Error or timeout in CaptureActionReceiver")
            } finally {
                pendingResult.finish()
            }
        }
    }
}
