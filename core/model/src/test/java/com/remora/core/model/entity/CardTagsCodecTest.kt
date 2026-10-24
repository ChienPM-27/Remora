package com.remora.core.model.entity

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CardTagsCodecTest {

    @Test
    fun `decodes legacy v1 json with subject and topics`() {
        val tags = CardTagsCodec.decode("""{"subject":"Toán rời rạc","topics":["Đồ thị","Tree"]}""")
        assertEquals("Toán rời rạc", tags.subject)
        assertEquals(listOf("Đồ thị", "Tree"), tags.topics)
        assertNull(tags.course)
        assertNull(tags.chapter)
        assertTrue(tags.concepts.isEmpty())
    }

    @Test
    fun `decodes v2 json with optional hierarchy without fabricating`() {
        val tags = CardTagsCodec.decode(
            """{"subject":"Giải tích","topics":["Tích phân"],"course":"GT1","chapter":"Chương 2","concepts":["Fubini"],"version":2}"""
        )
        assertEquals("Giải tích", tags.subject)
        assertEquals("GT1", tags.course)
        assertEquals("Chương 2", tags.chapter)
        assertEquals(listOf("Fubini"), tags.concepts)
    }

    @Test
    fun `missing or blank subject falls back to unclassified`() {
        assertEquals("chưa phân loại", CardTagsCodec.decode("""{"topics":["x"]}""").subject)
        assertEquals("chưa phân loại", CardTagsCodec.decode("""{"subject":"   "}""").subject)
        assertEquals("chưa phân loại", CardTagsCodec.decode(null).subject)
        assertEquals("chưa phân loại", CardTagsCodec.decode("").subject)
    }

    @Test
    fun `malformed json falls back to defaults without crashing`() {
        val tags = CardTagsCodec.decode("""{"subject":"Giải tích","topics":[""")
        assertEquals("chưa phân loại", tags.subject)
        assertTrue(tags.topics.isEmpty())
        assertEquals(CardTags(), CardTagsCodec.decode("not json at all"))
    }

    @Test
    fun `wrong field types keep valid entries and drop invalid ones`() {
        val tags = CardTagsCodec.decode(
            """{"subject":123,"topics":["Đồ thị",42,null,"Orgraph"],"course":true}"""
        )
        // subject not a string -> fallback; topics keep only valid strings
        assertEquals("chưa phân loại", tags.subject)
        assertEquals(listOf("Đồ thị", "Orgraph"), tags.topics)
        assertNull(tags.course)
    }

    @Test
    fun `blank optional fields are normalized to absent`() {
        val tags = CardTagsCodec.decode(
            """{"subject":"Lý","topics":[],"course":"","chapter":"   ","concepts":["","  "]}"""
        )
        assertNull(tags.course)
        assertNull(tags.chapter)
        assertTrue(tags.concepts.isEmpty())
    }

    @Test
    fun `unknown future version is still parsed best-effort by shape`() {
        val tags = CardTagsCodec.decode(
            """{"version":9,"subject":"Hóa","topics":["Liên kết"],"futureField":{"a":1}}"""
        )
        assertEquals("Hóa", tags.subject)
        assertEquals(listOf("Liên kết"), tags.topics)
    }

    @Test
    fun `encode without v2 fields produces v1-compatible json`() {
        val json = CardTagsCodec.encode(CardTags(subject = "Toán", topics = listOf("Đồ thị")))
        val obj = Json.parseToJsonElement(json).jsonObject
        assertTrue(obj.keys.containsAll(setOf("subject", "topics")))
        assertTrue(!obj.keys.contains("course"))
        assertTrue(!obj.keys.contains("version"))
    }

    @Test
    fun `encode with v2 fields writes version and round-trips`() {
        val original = CardTags(
            subject = "Giải tích", topics = listOf("Tích phân"),
            course = "GT1", chapter = "Chương 2", concepts = listOf("Fubini")
        )
        val roundTrip = CardTagsCodec.decode(CardTagsCodec.encode(original))
        assertEquals(original, roundTrip)
        val obj = Json.parseToJsonElement(CardTagsCodec.encode(original)).jsonObject
        assertEquals(2, (obj["version"] as JsonPrimitive).content.toInt())
    }
}
