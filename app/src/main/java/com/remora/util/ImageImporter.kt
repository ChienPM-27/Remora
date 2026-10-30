package com.remora.util

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID

data class ImportedImage(val uri: String, val hash: String)

object ImageImporter {
    const val MAX_BYTES = 20 * 1024 * 1024

    suspend fun importImage(context: Context, source: Uri): ImportedImage {
        val directory = File(context.filesDir, "imported_images").apply {
            if (!exists() && !mkdirs()) throw IOException("STORAGE_UNAVAILABLE")
        }
        val destination = File(directory, "memory-${UUID.randomUUID()}.img")
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val input = context.contentResolver.openInputStream(source) ?: throw IOException("UNREADABLE_IMAGE")
            var total = 0L
            input.use { stream ->
                destination.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = stream.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > MAX_BYTES) throw IOException("IMAGE_TOO_LARGE")
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                    output.fd.sync()
                }
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(destination.absolutePath, bounds)
            if (total == 0L || bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("UNREADABLE_IMAGE")
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            return ImportedImage(Uri.fromFile(destination).toString(), hash)
        } catch (error: Throwable) {
            destination.delete()
            throw error
        }
    }

    fun deleteOwnedImage(context: Context, uriString: String) {
        val uri = Uri.parse(uriString)
        if (uri.scheme != "file") return
        val path = uri.path ?: return
        val directory = File(context.filesDir, "imported_images").canonicalFile
        val file = File(path).canonicalFile
        if (file.parentFile == directory && file.isFile) file.delete()
    }
}
