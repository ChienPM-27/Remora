package com.remora.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlin.math.max

object ImageCompressor {

    const val MAX_DIMENSION = 1560
    const val DEFAULT_QUALITY = 85

    fun compressImageUri(
        context: Context,
        imageUri: Uri,
        maxDimension: Int = MAX_DIMENSION,
        quality: Int = DEFAULT_QUALITY
    ): ByteArray? {
        val inputStream: InputStream = context.contentResolver.openInputStream(imageUri) ?: return null
        return inputStream.use { stream ->
            compressStream(stream, maxDimension, quality)
        }
    }

    fun compressStream(
        inputStream: InputStream,
        maxDimension: Int = MAX_DIMENSION,
        quality: Int = DEFAULT_QUALITY
    ): ByteArray? {
        val originalBytes = inputStream.readBytes()
        if (originalBytes.isEmpty()) return null

        // Decode bounds first to check dimensions
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(originalBytes, 0, originalBytes.size, options)

        val width = options.outWidth
        val height = options.outHeight
        if (width <= 0 || height <= 0) return null

        val longestSide = max(width, height)
        var sampleSize = 1
        while (longestSide / (sampleSize * 2) >= maxDimension) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.RGB_565 // Lower memory footprint
        }

        val decodedBitmap = BitmapFactory.decodeByteArray(originalBytes, 0, originalBytes.size, decodeOptions) ?: return null

        // Scale if still exceeds maxDimension
        val currentMax = max(decodedBitmap.width, decodedBitmap.height)
        val finalBitmap = if (currentMax > maxDimension) {
            val scale = maxDimension.toFloat() / currentMax.toFloat()
            val matrix = Matrix().apply { postScale(scale, scale) }
            Bitmap.createBitmap(decodedBitmap, 0, 0, decodedBitmap.width, decodedBitmap.height, matrix, true)
        } else {
            decodedBitmap
        }

        val outputStream = ByteArrayOutputStream()
        finalBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        return outputStream.toByteArray()
    }
}
