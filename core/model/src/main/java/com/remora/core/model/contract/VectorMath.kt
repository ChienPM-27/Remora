package com.remora.core.model.contract

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

object VectorMath {

    /**
     * Tính Cosine Similarity giữa 2 vector FloatArray
     * Trả về giá trị trong khoảng [-1.0, 1.0] (thường từ 0.0 đến 1.0 với text embedding)
     */
    fun cosineSimilarity(v1: FloatArray, v2: FloatArray): Float {
        if (v1.size != v2.size || v1.isEmpty()) return 0f

        var dot = 0f
        var norm1 = 0f
        var norm2 = 0f

        for (i in v1.indices) {
            val a = v1[i]
            val b = v2[i]
            dot += a * b
            norm1 += a * a
            norm2 += b * b
        }

        val denominator = sqrt(norm1) * sqrt(norm2)
        return if (denominator > 1e-8) dot / denominator else 0f
    }

    /**
     * Chuyển FloatArray thành ByteArray (dùng để lưu vào SQLite Room dạng BLOB)
     */
    fun toByteArray(floats: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(floats.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (f in floats) {
            buffer.putFloat(f)
        }
        return buffer.array()
    }

    /**
     * Chuyển ByteArray từ SQLite Room BLOB ngược lại thành FloatArray
     */
    fun toFloatArray(bytes: ByteArray): FloatArray {
        if (bytes.size % 4 != 0) return FloatArray(0)
        val floats = FloatArray(bytes.size / 4)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        for (i in floats.indices) {
            floats[i] = buffer.float
        }
        return floats
    }

    /**
     * Chuẩn hóa L2 norm của vector (độ dài = 1)
     */
    fun l2Normalize(vector: FloatArray): FloatArray {
        var sumSquares = 0f
        for (v in vector) sumSquares += v * v
        val norm = sqrt(sumSquares)
        if (norm < 1e-8) return vector

        val result = FloatArray(vector.size)
        for (i in vector.indices) {
            result[i] = vector[i] / norm
        }
        return result
    }
}
