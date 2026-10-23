package com.remora.capture

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.remora.data.local.RemoraDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.math.abs

class ScreenshotObserver(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    private val onScreenshotDetected: (imageUri: Uri, imagePath: String?) -> Unit
) : ContentObserver(Handler(Looper.getMainLooper())) {

    private var lastHandledMediaId: Long = -1L
    private var lastHandledTimestamp: Long = 0L

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        handleMediaChange()
    }

    private fun handleMediaChange() {
        val now = System.currentTimeMillis()
        if (now - lastHandledTimestamp < 800L) {
            // Debounce rapid multiple OS content notifications
            return
        }

        scope.launch {
            try {
                // 1. Check if monitoring is paused by user in AppSetting
                val db = RemoraDatabase.getInstance(context)
                val setting = db.appSettingDao().getSetting()
                if (setting?.paused == true) {
                    Timber.d("Screenshot capture ignored: user has paused monitoring.")
                    return@launch
                }

                val projectionList = mutableListOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.DATE_ADDED
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    projectionList.add(MediaStore.Images.Media.RELATIVE_PATH)
                    projectionList.add(MediaStore.Images.Media.IS_PENDING)
                } else {
                    projectionList.add(MediaStore.Images.Media.DATA)
                }

                val projection = projectionList.toTypedArray()
                val queryUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC, ${MediaStore.Images.Media._ID} DESC"

                context.contentResolver.query(queryUri, projection, null, null, sortOrder)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                        val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                        val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)

                        val id = cursor.getLong(idCol)

                        // 2. Anti-duplication by Media ID
                        if (id == lastHandledMediaId) {
                            return@use
                        }

                        // 3. Check IS_PENDING on Android 10+ (file still being written by OS)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            val isPendingCol = cursor.getColumnIndex(MediaStore.Images.Media.IS_PENDING)
                            if (isPendingCol != -1 && cursor.getInt(isPendingCol) == 1) {
                                Timber.d("Image #$id is still IS_PENDING, waiting for next event...")
                                return@use
                            }
                        }

                        // 4. Time sanity check: allow up to 10 minutes tolerance to avoid clock skew on emulators
                        val dateAddedSeconds = cursor.getLong(dateAddedCol)
                        val nowSeconds = System.currentTimeMillis() / 1000L
                        val isRecent = abs(nowSeconds - dateAddedSeconds) < 600L || (lastHandledMediaId != -1L && id > lastHandledMediaId)
                        if (!isRecent) {
                            return@use
                        }

                        val displayName = cursor.getString(nameCol) ?: ""
                        val pathOrData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            val pathCol = cursor.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
                            if (pathCol != -1) cursor.getString(pathCol) ?: "" else ""
                        } else {
                            val dataCol = cursor.getColumnIndex(MediaStore.Images.Media.DATA)
                            if (dataCol != -1) cursor.getString(dataCol) ?: "" else ""
                        }

                        if (isScreenshot(pathOrData, displayName)) {
                            lastHandledMediaId = id
                            lastHandledTimestamp = now
                            val imageUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                            Timber.i("Fresh screenshot detected: id=$id, uri=$imageUri, name=$displayName")

                            // Emit directly to foreground UI bus
                            CaptureEventManager.emitScreenshot(imageUri)

                            // Also notify listener (NotificationHelper)
                            onScreenshotDetected(imageUri, pathOrData)
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error processing MediaStore screenshot change")
            }
        }
    }

    companion object {
        fun isScreenshot(path: String, name: String): Boolean {
            val lowerPath = path.lowercase()
            val lowerName = name.lowercase()
            return lowerPath.contains("screenshot") ||
                    lowerName.contains("screenshot") ||
                    lowerName.contains("screen") ||
                    lowerName.startsWith("scr_") ||
                    lowerName.contains("chụp") ||
                    lowerPath.contains("pictures") ||
                    lowerPath.contains("dcim")
        }

        fun queryLatestScreenshotUri(context: Context): Uri? {
            try {
                val projection = arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Images.Media.RELATIVE_PATH else MediaStore.Images.Media.DATA
                )
                val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC, ${MediaStore.Images.Media._ID} DESC"
                context.contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    null,
                    null,
                    sortOrder
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                        val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                        val id = cursor.getLong(idCol)
                        val name = cursor.getString(nameCol) ?: ""
                        val pathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            cursor.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
                        } else {
                            cursor.getColumnIndex(MediaStore.Images.Media.DATA)
                        }
                        val path = if (pathCol != -1) cursor.getString(pathCol) ?: "" else ""

                        if (isScreenshot(path, name)) {
                            return ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error querying latest screenshot")
            }
            return null
        }
    }
}
