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
import android.database.Cursor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.LinkedHashSet

class ScreenshotObserver(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    private val onScreenshotDetected: (imageUri: Uri, imagePath: String?) -> Unit
) : ContentObserver(Handler(Looper.getMainLooper())) {

    private val handledMediaIds = LinkedHashSet<Long>()
    private val handledMediaIdsLock = Any()

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        Timber.d("MediaStore changed: uri=" + uri)
        handleMediaChange(uri)
    }

    private fun handleMediaChange(changedUri: Uri?) {
        scope.launch {
            try {
                val db = RemoraDatabase.getInstance(context)
                val setting = db.appSettingDao().getSetting()
                if (setting?.consentGranted != true || CapturePolicy.isCaptureSuppressed(setting, System.currentTimeMillis())) {
                    Timber.d("Screenshot capture ignored: monitoring is paused, temporarily suppressed, or consent not granted.")
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

                // Prefer the exact row reported by MediaStore. A notification can arrive
                // before the new row is queryable, so retry that URI instead of accidentally
                // consuming an older screenshot and then debouncing the real event.
                val changedId = changedUri?.lastPathSegment?.toLongOrNull()
                if (changedId != null) {
                    repeat(4) {
                        delay(250L)
                        if (queryAndProcessItem(changedUri, projection)) {
                            return@launch
                        }
                    }
                    Timber.w("MediaStore item was not ready after retries: " + changedUri)
                    return@launch
                }

                // Some providers only send the collection URI. Wait briefly for indexing,
                // then scan only genuinely fresh rows (not screenshots from several minutes ago).
                repeat(3) {
                    delay(250L)
                    if (processRecentImages(projection)) {
                        return@launch
                    }
                }
                Timber.d("No fresh screenshot row found for collection change: " + changedUri)
            } catch (e: Exception) {
                Timber.e(e, "Error processing MediaStore screenshot change")
            }
        }
    }

    /**
     * Returns true when this URI now points to a readable, completed media row.
     * A pending or not-yet-indexed row returns false so the caller can retry.
     */
    private fun queryAndProcessItem(uri: Uri, projection: Array<String>): Boolean {
        val cursor = context.contentResolver.query(uri, projection, null, null, null) ?: return false
        cursor.use {
            if (!it.moveToFirst()) return false
            val isPendingCol = it.getColumnIndex(MediaStore.Images.Media.IS_PENDING)
            if (isPendingCol != -1 && it.getInt(isPendingCol) == 1) {
                Timber.d("Changed image is still IS_PENDING: " + uri)
                return false
            }
            processCursorRow(it)
            return true
        }
    }

    /**
     * Fallback for providers that notify only the collection URI.
     * Returns true when at least one new screenshot was processed.
     */
    private fun processRecentImages(projection: Array<String>): Boolean {
        val sortOrder = MediaStore.Images.Media.DATE_ADDED.toString() + " DESC, " +
                MediaStore.Images.Media._ID + " DESC"
        val cursor = context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        ) ?: return false

        var processedScreenshot = false
        val nowSeconds = System.currentTimeMillis() / 1000L
        cursor.use {
            val dateAddedCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val isPendingCol = it.getColumnIndex(MediaStore.Images.Media.IS_PENDING)
            while (it.moveToNext()) {
                val ageSeconds = nowSeconds - it.getLong(dateAddedCol)
                if (ageSeconds > MAX_FALLBACK_AGE_SECONDS) break
                if (ageSeconds < -10L) continue
                if (isPendingCol != -1 && it.getInt(isPendingCol) == 1) continue
                if (processCursorRow(it)) processedScreenshot = true
            }
        }
        return processedScreenshot
    }

    /** Returns true only when a previously unseen screenshot is emitted. */
    private fun processCursorRow(cursor: Cursor): Boolean {
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
        val id = cursor.getLong(idCol)
        val displayName = cursor.getString(nameCol) ?: ""
        val pathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            cursor.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
        } else {
            cursor.getColumnIndex(MediaStore.Images.Media.DATA)
        }
        val pathOrData = if (pathCol != -1) cursor.getString(pathCol) ?: "" else ""

        if (!isScreenshot(pathOrData, displayName)) return false
        if (!claimMediaId(id)) {
            Timber.d("Duplicate screenshot row ignored: id=" + id)
            return false
        }

        val imageUri = ContentUris.withAppendedId(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
        )
        Timber.i("Fresh screenshot detected: id=" + id + ", uri=" + imageUri + ", name=" + displayName)
        CaptureEventManager.emitScreenshot(imageUri)
        onScreenshotDetected(imageUri, pathOrData)
        return true
    }

    private fun claimMediaId(id: Long): Boolean = synchronized(handledMediaIdsLock) {
        if (!handledMediaIds.add(id)) {
            false
        } else {
            // Bound memory use for a long-lived foreground service.
            if (handledMediaIds.size > 256) {
                val iterator = handledMediaIds.iterator()
                iterator.next()
                iterator.remove()
            }
            true
        }
    }

    companion object {
        private const val MAX_FALLBACK_AGE_SECONDS = 45L

        fun isScreenshot(path: String, name: String): Boolean {
            val lowerPath = path.lowercase()
            val lowerName = name.lowercase()
            return lowerPath.contains("screenshot") ||
                    lowerName.contains("screenshot") ||
                    lowerName.contains("screen_") ||
                    lowerName.startsWith("scr_") ||
                    lowerName.contains("chụp màn")
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
