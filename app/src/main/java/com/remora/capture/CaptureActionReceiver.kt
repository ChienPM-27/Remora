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
        consentGranted: Boolean = true,
        imageHash: String? = null,
        onEnqueueWorker: (Long) -> Unit = {},
        onApplyCaptureAction: suspend (String) -> Unit = {},
        onSaveSuccess: () -> Unit = {},
        onResolve: (String) -> Unit
    ) {
        when (action) {
            NotificationHelper.ACTION_SAVE -> {
                if (imageUriStr == null) return
                if (!consentGranted) {
                    // Default-deny: without explicit consent no capture path may persist or upload content.
                    Timber.i("Save action ignored for uri (consent not granted); zero data leaves device")
                    onResolve(imageUriStr)
                    return
                }
                val existingCard = memoryCardDao.getCardByImageUri(imageUriStr)
                if (existingCard != null) {
                    Timber.i("MemoryCard for uri $imageUriStr already exists (#${existingCard.id}), skipping duplicate insert")
                } else {
                    val card = MemoryCard(
                        imageUri = imageUriStr,
                        createdAt = System.currentTimeMillis(),
                        userIntent = userIntent,
                        title = "Đang xử lý...",
                        status = ProcessingStatus.PENDING,
                        imageHash = imageHash
                    )
                    // Byte-identical images keep the FIRST card; nothing is merged or deleted.
                    val newId = memoryCardDao.insertCardIfHashAbsent(MemoryCardEntity.fromDomainModel(card))
                    if (newId < 0) {
                        Timber.i("Byte-identical image of card #${-newId}; skipping duplicate insert")
                    } else {
                        Timber.i("Inserted PENDING memory card #$newId into Room SQLite")
                        onEnqueueWorker(newId)
                    }
                }
                onSaveSuccess()
                onResolve(imageUriStr)
            }

            NotificationHelper.ACTION_SKIP -> {
                if (imageUriStr == null) return
                Timber.i("User skipped screenshot saving. Zero data leaves device.")
                onResolve(imageUriStr)
            }

            NotificationHelper.ACTION_MUTE_1H,
            NotificationHelper.ACTION_PAUSE -> {
                onApplyCaptureAction(action)
                Timber.i("Screenshot capture action applied: $action")
                if (imageUriStr != null) {
                    onResolve(imageUriStr)
                }
            }
        }
    }
}

class CaptureActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Timber.i("CaptureActionReceiver received action: $action")

        if (action != NotificationHelper.ACTION_SAVE) {
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
                    // Consent is checked at action time, not at notification-post time: a stale
                    // notification must not save anything after consent has been revoked.
                    val consentGranted = db.appSettingDao().getSetting()?.consentGranted == true
                    val imageHash = imageUriStr?.let {
                        try {
                            context.contentResolver.openInputStream(android.net.Uri.parse(it))
                                ?.use { stream -> com.remora.util.HashUtil.sha256(stream) }
                        } catch (e: Exception) {
                            Timber.w(e, "Failed to hash captured image")
                            null
                        }
                    }

                    CaptureActionHandler.handleAction(
                        action = action,
                        imageUriStr = imageUriStr,
                        userIntent = userIntent,
                        memoryCardDao = db.memoryCardDao(),
                        consentGranted = consentGranted,
                        imageHash = imageHash,
                        onEnqueueWorker = { newId ->
                            // Unique per card to prevent duplicate concurrent runs
                            val workRequest = androidx.work.OneTimeWorkRequestBuilder<com.remora.work.CardAnalyzeWorker>()
                                .setConstraints(
                                    androidx.work.Constraints.Builder()
                                        .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                                        .build()
                                )
                                .setBackoffCriteria(
                                    androidx.work.BackoffPolicy.EXPONENTIAL,
                                    30,
                                    java.util.concurrent.TimeUnit.SECONDS
                                )
                                .setInputData(
                                    androidx.work.workDataOf(com.remora.work.CardAnalyzeWorker.KEY_CARD_ID to newId)
                                )
                                .build()
                            androidx.work.WorkManager.getInstance(context).enqueueUniqueWork(
                                com.remora.work.CardAnalyzeWorker.uniqueWorkName(newId),
                                androidx.work.ExistingWorkPolicy.REPLACE,
                                workRequest
                            )
                        },
                        onApplyCaptureAction = { act ->
                            CapturePolicy.applyCaptureAction(act, db.appSettingDao(), System.currentTimeMillis())
                        },
                        onSaveSuccess = {
                            NotificationHelper.showSaveSuccess(context)
                        },
                        onResolve = { uri ->
                            PendingCaptureStore.getInstance(context).resolve(uri)
                        }
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Error or timeout in CaptureActionReceiver")
                NotificationHelper.dismissNotification(context)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
