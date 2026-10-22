package com.remora.search

import com.remora.core.model.contract.VectorMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SemanticSearchEngineTest {

    @Test
    fun testRelevanceThresholdFilter() {
        val queryVector = VectorMath.l2Normalize(floatArrayOf(1.0f, 0.0f, 0.0f))

        val highlyRelevant = VectorMath.l2Normalize(floatArrayOf(0.95f, 0.1f, 0.0f))
        val marginallyRelevant = VectorMath.l2Normalize(floatArrayOf(0.68f, 0.70f, 0.0f))
        val irrelevant = VectorMath.l2Normalize(floatArrayOf(0.1f, 0.99f, 0.0f))

        val score1 = VectorMath.dotProduct(queryVector, highlyRelevant)
        val score2 = VectorMath.dotProduct(queryVector, marginallyRelevant)
        val score3 = VectorMath.dotProduct(queryVector, irrelevant)

        val threshold = 0.65f

        assertTrue(score1 >= threshold)
        assertTrue(score2 >= threshold)
        assertFalse(score3 >= threshold) // Irrelevant, must be filtered out
    }
}
