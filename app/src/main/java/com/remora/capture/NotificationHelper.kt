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
import com.remora.MainActivity

object NotificationHelper {

    const val CHANNEL_ID = "remora_capture_channel"
    const val RESURFACING_CHANNEL_ID = "remora_resurfacing_channel"
    const val NOTIFICATION_ID = 1001
    const val RESURFACING_NOTIFICATION_ID = 2001

    const val ACTION_SAVE = "com.remora.action.SAVE"
    const val ACTION_SKIP = "com.remora.action.SKIP"
    const val ACTION_MUTE_1H = "com.remora.action.MUTE_1H"
    const val ACTION_PAUSE = "com.remora.action.PAUSE"

    const val EXTRA_IMAGE_URI = "extra_image_uri"
    const val EXTRA_USER_INTENT = "extra_user_intent"
    const val EXTRA_OPEN_SAVE_SHEET = "extra_open_save_sheet"

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

            val resurfaceChannel = NotificationChannel(
                RESURFACING_CHANNEL_ID,
                "Remora Resurfacing Insights",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Proactive alerts when new card links to previous study knowledge"
            }
            notificationManager.createNotificationChannel(resurfaceChannel)
        }
    }

    fun showCapturePrompt(context: Context, imageUri: Uri) {
        createNotificationChannel(context)

        val piFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        // Tap on notification body -> Open Remora with Save layout directly
        val openSaveIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra(EXTRA_IMAGE_URI, imageUri.toString())
            putExtra(EXTRA_OPEN_SAVE_SHEET, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val contentPendingIntent = PendingIntent.getActivity(context, 100, openSaveIntent, piFlags)

        // Inline RemoteInput for user_intent
        val remoteInput = RemoteInput.Builder(EXTRA_USER_INTENT)
            .setLabel("Nhập ghi chú (VD: Ôn thi giữa kỳ)...")
            .build()

        val saveIntent = Intent(context, CaptureActionReceiver::class.java).apply {
            action = ACTION_SAVE
            putExtra(EXTRA_IMAGE_URI, imageUri.toString())
        }
        val savePendingIntent = PendingIntent.getBroadcast(context, 1, saveIntent, piFlags)

        val saveAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_save,
            "Lưu",
            savePendingIntent
        ).addRemoteInput(remoteInput).build()

        val skipIntent = Intent(context, CaptureActionReceiver::class.java).apply {
            action = ACTION_SKIP
            putExtra(EXTRA_IMAGE_URI, imageUri.toString())
        }
        val skipPendingIntent = PendingIntent.getBroadcast(context, 2, skipIntent, piFlags)

        val mute1hIntent = Intent(context, CaptureActionReceiver::class.java).apply {
            action = ACTION_MUTE_1H
        }
        val mute1hPendingIntent = PendingIntent.getBroadcast(context, 3, mute1hIntent, piFlags)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("Phát hiện ảnh chụp bài giảng")
            .setContentText("Chạm để mở bảng lưu Remora hoặc nhập ghi chú")
            .setContentIntent(contentPendingIntent)
            .setFullScreenIntent(contentPendingIntent, false)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .addAction(saveAction)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Bỏ qua", skipPendingIntent)
            .addAction(android.R.drawable.ic_lock_idle_alarm, "Tắt 1 giờ", mute1hPendingIntent)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    fun showResurfacingAlert(context: Context, cardTitle: String, matchCount: Int) {
        createNotificationChannel(context)

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val openAppIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val contentPendingIntent = PendingIntent.getActivity(context, 4, openAppIntent, flags)

        val notification = NotificationCompat.Builder(context, RESURFACING_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Remora: Tìm thấy tri thức liên quan!")
            .setContentText("Bài học \"${cardTitle.take(40)}\" có $matchCount liên kết hữu ích với kiến thức bạn từng học.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(RESURFACING_NOTIFICATION_ID, notification)
    }

    fun dismissNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
    }
}
