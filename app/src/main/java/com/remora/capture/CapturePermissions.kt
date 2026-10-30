package com.remora.capture

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object CapturePermissions {
    val imagePermission: String get() = if (Build.VERSION.SDK_INT >= 33)
        Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE

    fun canReadImages(context: Context) =
        ContextCompat.checkSelfPermission(context, imagePermission) == PackageManager.PERMISSION_GRANTED

    fun canNotify(context: Context) = androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
}
