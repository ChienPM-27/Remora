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

class CaptureForegroundService : Service() {

    private var observer: ScreenshotObserver? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
        registerScreenshotObserver()
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
        observer = ScreenshotObserver(applicationContext) { imageUri, _ ->
            NotificationHelper.showCapturePrompt(applicationContext, imageUri)
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
        observer?.let {
            contentResolver.unregisterContentObserver(it)
        }
        Timber.i("CaptureForegroundService destroyed, observer unregistered")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, CaptureForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, CaptureForegroundService::class.java)
            context.stopService(intent)
        }
    }
}
