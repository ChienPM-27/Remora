package com.remora.capture

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput

object NotificationHelper {

    const val CHANNEL_ID = "remora_capture_channel"
    const val NOTIFICATION_ID = 1001

    const val ACTION_SAVE = "com.remora.action.SAVE"
    const val ACTION_SKIP = "com.remora.action.SKIP"
    const val ACTION_MUTE_1H = "com.remora.action.MUTE_1H"
    const val ACTION_PAUSE = "com.remora.action.PAUSE"

    const val EXTRA_IMAGE_URI = "extra_image_uri"
    const val EXTRA_USER_INTENT = "extra_user_intent"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Remora Screenshot Actions"
            val descriptionText = "Interactive notification for saving lecture screenshots"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showCapturePrompt(context: Context, imageUri: Uri) {
        createNotificationChannel(context)

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        // Inline RemoteInput for user_intent
        val remoteInput = RemoteInput.Builder(EXTRA_USER_INTENT)
            .setLabel("Nhập ghi chú (VD: Ôn thi giữa kỳ)...")
            .build()

        val saveIntent = Intent(context, CaptureActionReceiver::class.java).apply {
            action = ACTION_SAVE
            putExtra(EXTRA_IMAGE_URI, imageUri.toString())
        }
        val savePendingIntent = PendingIntent.getBroadcast(context, 1, saveIntent, flags)

        val saveAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_save,
            "Lưu",
            savePendingIntent
        ).addRemoteInput(remoteInput).build()

        val skipIntent = Intent(context, CaptureActionReceiver::class.java).apply {
            action = ACTION_SKIP
            putExtra(EXTRA_IMAGE_URI, imageUri.toString())
        }
        val skipPendingIntent = PendingIntent.getBroadcast(context, 2, skipIntent, flags)

        val pauseIntent = Intent(context, CaptureActionReceiver::class.java).apply {
            action = ACTION_PAUSE
        }
        val pausePendingIntent = PendingIntent.getBroadcast(context, 3, pauseIntent, flags)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("Phát hiện ảnh chụp bài giảng")
            .setContentText("Bạn có muốn lưu vào Bộ não thứ hai Remora?")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .addAction(saveAction)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Bỏ qua", skipPendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "Tạm dừng", pausePendingIntent)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    fun dismissNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
    }
}
