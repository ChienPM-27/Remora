package com.remora.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import timber.log.Timber
import com.remora.data.local.RemoraDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class CaptureForegroundService : Service() {

    private var observer: ScreenshotObserver? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
        if (!CapturePermissions.canReadImages(this)) { stopSelf(); return }
        registerScreenshotObserver()
        serviceScope.launch {
            RemoraDatabase.getInstance(applicationContext).appSettingDao().getSettingFlow().collect {
                if (it?.consentGranted != true || it.paused) stopSelf()
            }
        }
    }

    private fun startForegroundNotification() {
        val channelId = "remora_service_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Remora Background Monitor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors study screenshots in the background"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Remora đang hoạt động")
            .setContentText("Tự động phát hiện ảnh bài giảng khi chụp màn hình")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(1002, notification)
    }

    private fun registerScreenshotObserver() {
        observer = ScreenshotObserver(applicationContext, serviceScope) { imageUri, _ ->
            serviceScope.launch {
                val setting = RemoraDatabase.getInstance(applicationContext).appSettingDao().getSetting()
                if (setting?.consentGranted == true && !setting.paused && setting.notificationsEnabled && CapturePermissions.canNotify(applicationContext)) {
                    NotificationHelper.showCapturePrompt(applicationContext, imageUri)
                }
            }
        }
        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            observer!!
        )
        Timber.i("ScreenshotObserver registered in ForegroundService")
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        observer?.let {
            contentResolver.unregisterContentObserver(it)
        }
        Timber.i("CaptureForegroundService destroyed, observer unregistered")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTimeout(startId: Int, fgsType: Int) { stopSelf() }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, CaptureForegroundService::class.java)
            try { context.startForegroundService(intent) }
            catch (_: RuntimeException) { Timber.w("Capture service could not start; library import remains available") }
        }

        fun stop(context: Context) {
            val intent = Intent(context, CaptureForegroundService::class.java)
            context.stopService(intent)
        }
    }
}
