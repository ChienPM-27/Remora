package com.remora.util

import android.content.Context
import android.net.Uri
import timber.log.Timber
import java.io.File
import java.util.UUID

object ImageImporter {

    /**
     * Copies an image from an external or temporary content URI (e.g. from PhotoPicker / Gallery)
     * into the app's persistent internal storage so it can be accessed reliably at any time
     * without permission expiration.
     *
     * @return Persistent "file://" URI string.
     */
    fun copyUriToInternalStorage(context: Context, sourceUri: Uri): String {
        val imagesDir = File(context.filesDir, "imported_images").apply {
            if (!exists()) mkdirs()
        }
        val filename = "import_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.jpg"
        val destFile = File(imagesDir, filename)

        val inputStream = context.contentResolver.openInputStream(sourceUri)
            ?: throw IllegalStateException("Cannot open input stream for URI: $sourceUri")

        inputStream.use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        Timber.i("Persisted gallery image to ${destFile.absolutePath} (${destFile.length()} bytes)")
        return Uri.fromFile(destFile).toString()
    }
}
