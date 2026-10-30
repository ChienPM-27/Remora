package com.remora.ai

import com.remora.ai.model.ResurfaceBatchResponseDto
import com.remora.ai.model.ResurfaceResultItemDto
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.longOrNull

object ResurfaceResponseParser {
    fun parse(body: String): ResurfaceBatchResponseDto {
        val root = Json.parseToJsonElement(body) as? JsonObject
        val rows = root?.get("results") as? JsonArray ?: throw SerializationException("INVALID_BATCH_RESPONSE")
        return ResurfaceBatchResponseDto(rows.mapNotNull { element ->
            val row = element as? JsonObject ?: return@mapNotNull null
            val id = (row["candidate_id"] as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull
                ?: return@mapNotNull null
            val confidence = (row["confidence"] as? JsonPrimitive)?.takeUnless { it.isString }?.floatOrNull
                ?: return@mapNotNull null
            ResurfaceResultItemDto(
                candidate_id = id,
                group = row.string("group") ?: return@mapNotNull null,
                relation = row.string("relation") ?: return@mapNotNull null,
                new_info = row.string("new_info") ?: return@mapNotNull null,
                benefit = row.string("benefit") ?: return@mapNotNull null,
                reason = row.string("reason") ?: return@mapNotNull null,
                confidence = confidence
            )
        })
    }

    private fun JsonObject.string(key: String): String? =
        (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content
}
