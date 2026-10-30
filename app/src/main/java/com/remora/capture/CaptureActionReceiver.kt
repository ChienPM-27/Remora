package com.remora.capture

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.RemoteInput
import com.remora.RemoraApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import timber.log.Timber

class CaptureActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                withTimeout(8_000L) {
                    val container = (context.applicationContext as RemoraApp).container
                    when (action) {
                        NotificationHelper.ACTION_SAVE -> {
                            val image = intent.getStringExtra(NotificationHelper.EXTRA_IMAGE_URI)
                            if (image != null) {
                                val note = RemoteInput.getResultsFromIntent(intent)
                                    ?.getCharSequence(NotificationHelper.EXTRA_USER_INTENT)?.toString()
                                container.saveCoordinator.save(Uri.parse(image), note)
                                NotificationHelper.dismissNotification(context)
                            }
                        }
                        NotificationHelper.ACTION_SKIP -> NotificationHelper.dismissNotification(context)
                        NotificationHelper.ACTION_PAUSE -> {
                            val dao = container.database.appSettingDao()
                            dao.getSetting()?.let { dao.insertOrUpdate(it.copy(paused = true)) }
                            CaptureForegroundService.stop(context)
                            NotificationHelper.dismissNotification(context)
                        }
                    }
                }
            } catch (_: Exception) {
                // Retain the prompt so the user can retry by opening the app.
                Timber.w("Capture action could not complete")
            } finally {
                pending.finish()
            }
        }
    }
}
