package com.remora.capture

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import timber.log.Timber

class ScreenshotObserver(
    private val context: Context,
    private val onScreenshotDetected: (imageUri: Uri, imagePath: String?) -> Unit
) : ContentObserver(Handler(Looper.getMainLooper())) {

    private var lastHandledTimestamp: Long = 0L

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        handleMediaChange(uri)
    }

    private fun handleMediaChange(targetUri: Uri?) {
        val now = System.currentTimeMillis()
        if (now - lastHandledTimestamp < 800L) {
            // Debounce rapid multiple OS notifications
            return
        }

        try {
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_ADDED,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Images.Media.RELATIVE_PATH
                } else {
                    MediaStore.Images.Media.DATA
                }
            )

            val queryUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

            context.contentResolver.query(queryUri, projection, null, null, sortOrder)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                    val pathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)
                    } else {
                        cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                    }

                    val id = cursor.getLong(idCol)
                    val displayName = cursor.getString(nameCol) ?: ""
                    val pathOrData = cursor.getString(pathCol) ?: ""

                    if (isScreenshot(pathOrData, displayName)) {
                        lastHandledTimestamp = now
                        val imageUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                        Timber.i("Screenshot detected: uri=$imageUri, name=$displayName")
                        onScreenshotDetected(imageUri, pathOrData)
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error processing MediaStore change")
        }
    }

    private fun isScreenshot(path: String, name: String): Boolean {
        val lowerPath = path.lowercase()
        val lowerName = name.lowercase()
        return lowerPath.contains("screenshot") ||
                lowerName.contains("screenshot") ||
                lowerName.startsWith("scr_")
    }
}
