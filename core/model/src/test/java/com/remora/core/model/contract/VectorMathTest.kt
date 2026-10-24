package com.remora.core.model.contract

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VectorMathTest {

    @Test
    fun testCosineSimilarityIdenticalVectors() {
        val v1 = floatArrayOf(1.0f, 2.0f, 3.0f)
        val v2 = floatArrayOf(1.0f, 2.0f, 3.0f)
        val similarity = VectorMath.cosineSimilarity(v1, v2)
        assertEquals(1.0f, similarity, 1e-5f)
    }

    @Test
    fun testCosineSimilarityOrthogonalVectors() {
        val v1 = floatArrayOf(1.0f, 0.0f)
        val v2 = floatArrayOf(0.0f, 1.0f)
        val similarity = VectorMath.cosineSimilarity(v1, v2)
        assertEquals(0.0f, similarity, 1e-5f)
    }

    @Test
    fun testBlobEncodeDecodeRoundTrip() {
        val original = floatArrayOf(0.123f, -4.56f, 78.9f, 0.0f)
        val bytes = VectorMath.toByteArray(original)
        assertEquals(original.size * 4, bytes.size)

        val decoded = VectorMath.toFloatArray(bytes)
        assertArrayEquals(original, decoded, 1e-6f)
    }

    @Test
    fun testL2Normalize() {
        val v = floatArrayOf(3.0f, 4.0f)
        val normalized = VectorMath.l2Normalize(v)
        assertEquals(0.6f, normalized[0], 1e-5f)
        assertEquals(0.8f, normalized[1], 1e-5f)

        // Dot product of normalized vector with itself should equal 1.0
        val dot = normalized[0] * normalized[0] + normalized[1] * normalized[1]
        assertEquals(1.0f, dot, 1e-5f)
    }

    @Test
    fun testIsValidEmbeddingAcceptsContractVector() {
        val vector = VectorMath.l2Normalize(FloatArray(VectorMath.EMBEDDING_DIMENSION) { (it % 13) + 1f })
        assertTrue(VectorMath.isValidEmbedding(vector))
    }

    @Test
    fun testIsValidEmbeddingRejectsNullWrongDimensionAndEmpty() {
        assertTrue(!VectorMath.isValidEmbedding(null))
        assertTrue(!VectorMath.isValidEmbedding(FloatArray(0)))
        assertTrue(!VectorMath.isValidEmbedding(FloatArray(767)))
        assertTrue(!VectorMath.isValidEmbedding(FloatArray(769)))
    }

    @Test
    fun testIsValidEmbeddingRejectsNonFiniteValues() {
        val nan = FloatArray(VectorMath.EMBEDDING_DIMENSION) { 0.1f }
        nan[10] = Float.NaN
        assertTrue(!VectorMath.isValidEmbedding(nan))

        val inf = FloatArray(VectorMath.EMBEDDING_DIMENSION) { 0.1f }
        inf[10] = Float.POSITIVE_INFINITY
        assertTrue(!VectorMath.isValidEmbedding(inf))
    }

    @Test
    fun testIsValidEmbeddingRejectsZeroNormVector() {
        assertTrue(!VectorMath.isValidEmbedding(FloatArray(VectorMath.EMBEDDING_DIMENSION)))
    }

    @Test
    fun testL2NormalizeLeavesNonFiniteVectorUnchanged() {
        val v = floatArrayOf(Float.NaN, 1f)
        val result = VectorMath.l2Normalize(v)
        assertArrayEquals(v, result, 0f)
    }

    @Test
    fun testByteLengthMatchesContract() {
        val vector = FloatArray(VectorMath.EMBEDDING_DIMENSION) { 0.5f }
        assertEquals(VectorMath.EMBEDDING_DIMENSION * 4, VectorMath.toByteArray(vector).size)
    }
}
