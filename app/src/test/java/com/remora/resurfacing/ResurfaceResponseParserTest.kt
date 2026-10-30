package com.remora.resurfacing

import com.remora.ai.ResurfaceResponseParser
import org.junit.Assert.*
import org.junit.Test

class ResurfaceResponseParserTest {
    private fun row(id: String = "101", confidence: String = "0.92") =
        """{"candidate_id":$id,"group":"COMPLEMENTARY","relation":"EXPLANATION","new_info":"A proof","reason":"Explains the exercise","benefit":"Use the proof","confidence":$confidence}"""

    @Test
    fun parsesValidRowsAndAnExplicitEmptyArray() {
        val item = ResurfaceResponseParser.parse("""{"results":[${row()}]}""").results.single()
        assertEquals(101L, item.candidate_id)
        assertEquals(0.92f, item.confidence)
        assertEquals("Use the proof", item.benefit)
        assertTrue(ResurfaceResponseParser.parse("""{"results":[]}""").results.isEmpty())
    }

    @Test
    fun candidateIdentityMustBeAnExactIntegerLiteral() {
        val invalidIds = listOf("\"101\"", "101.0", "101.5", "1.01e2", "9223372036854775808", "null", "true")
        val response = ResurfaceResponseParser.parse("""{"results":[${(invalidIds.map { row(it) } + row()).joinToString(",") }]}""")
        assertEquals(listOf(101L), response.results.map { it.candidate_id })
    }

    @Test
    fun invalidRowShapesDoNotDiscardOtherValidRows() {
        val invalid = listOf("null", "[]", "5", "{}", row().replace("\"Use the proof\"", "null"), row(confidence = "\"0.92\""))
        val response = ResurfaceResponseParser.parse("""{"results":[${(invalid + row()).joinToString(",")}]}""")
        assertEquals(1, response.results.size)
    }

    @Test
    fun aMissingOrMalformedResultsArrayIsAFailureRatherThanANegativeCheckpoint() {
        listOf("{}", "[]", "{", "null", """{"results":null}""", """{"results":{}}""").forEach {
            assertTrue("Invalid batch shape must fail", runCatching { ResurfaceResponseParser.parse(it) }.isFailure)
        }
    }
}
