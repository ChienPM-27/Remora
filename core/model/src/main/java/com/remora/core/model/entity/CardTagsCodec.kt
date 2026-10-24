package com.remora.core.model.entity

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * Versioned codec for the `tags_json` column.
 *
 * v1 (legacy, written before 2026-10-24): {"subject": "...", "topics": ["..."]}
 * v2 (current): adds optional "course", "chapter", "concepts" keys.
 *
 * Decoding rules (never fabricate, never crash, keep what is valid):
 * - Missing/blank/null subject -> "chưa phân loại".
 * - Non-string entries inside topics/concepts are dropped, valid ones kept.
 * - Blank course/chapter/concepts are normalized to absent (null / empty list).
 * - Unknown versions >= 3 are still parsed best-effort by shape.
 * - Malformed JSON -> CardTags() defaults; the caller decides whether to log ids.
 */
object CardTagsCodec {

    const val CURRENT_VERSION: Int = 2
    const val UNCLASSIFIED_SUBJECT: String = "chưa phân loại"

    private val decoder = Json { ignoreUnknownKeys = true }
    private val encoder = Json { explicitNulls = false }

    fun decode(raw: String?): CardTags {
        if (raw.isNullOrBlank()) return CardTags()
        return try {
            val obj = decoder.parseToJsonElement(raw).jsonObject
            CardTags(
                subject = obj.stringOr("subject")?.takeIf { it.isNotBlank() } ?: UNCLASSIFIED_SUBJECT,
                topics = obj.stringListOr("topics"),
                course = obj.stringOr("course")?.takeIf { it.isNotBlank() },
                chapter = obj.stringOr("chapter")?.takeIf { it.isNotBlank() },
                concepts = obj.stringListOr("concepts")
            )
        } catch (_: Exception) {
            CardTags()
        }
    }

    fun encode(tags: CardTags): String {
        val carriesV2Fields = tags.course != null || tags.chapter != null || tags.concepts.isNotEmpty()
        return if (carriesV2Fields) {
            encoder.encodeToString(TagsJson(subject = tags.subject, topics = tags.topics, course = tags.course, chapter = tags.chapter, concepts = tags.concepts, version = CURRENT_VERSION))
        } else {
            // v1-compatible output so older readers stay supported.
            encoder.encodeToString(TagsJson(subject = tags.subject, topics = tags.topics))
        }
    }

    @kotlinx.serialization.Serializable
    private data class TagsJson(
        val subject: String,
        val topics: List<String>,
        val course: String? = null,
        val chapter: String? = null,
        val concepts: List<String> = emptyList(),
        val version: Int? = null
    )

    private fun kotlinx.serialization.json.JsonObject.stringOr(key: String): String? =
        when (val el: JsonElement = this[key] ?: JsonNull) {
            is JsonPrimitive -> if (el.isString) el.content else null
            else -> null
        }

    private fun kotlinx.serialization.json.JsonObject.stringListOr(key: String): List<String> {
        val arr = (this[key] as? JsonArray) ?: return emptyList()
        return arr.mapNotNull { el ->
            (el as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
        }
    }
}
