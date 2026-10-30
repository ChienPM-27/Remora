package com.remora.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.remora.core.model.entity.CardTags
import com.remora.core.model.entity.ContentType
import com.remora.core.model.entity.MemoryCard
import com.remora.core.model.entity.ProcessingStatus
import com.remora.core.model.contract.VectorMath
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Entity(
    tableName = "memory_card",
    indices = [
        Index(value = ["created_at"]),
        Index(value = ["status"]),
        Index(value = ["image_hash"], unique = true)
    ]
)
data class MemoryCardEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "image_uri")
    val imageUri: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "user_intent")
    val userIntent: String? = null,

    @ColumnInfo(name = "title")
    val title: String = "",

    @ColumnInfo(name = "summary")
    val summary: String = "",

    @ColumnInfo(name = "tags_json")
    val tagsJson: String = "{\"subject\":\"chưa phân loại\",\"topics\":[]}",

    @ColumnInfo(name = "content_type")
    val contentType: String = ContentType.UNKNOWN.name,

    @ColumnInfo(name = "ocr_text")
    val ocrText: String = "",

    @ColumnInfo(name = "embedding", typeAffinity = ColumnInfo.BLOB)
    val embedding: ByteArray? = null,

    @ColumnInfo(name = "status")
    val status: String = ProcessingStatus.PENDING.name,

    @ColumnInfo(name = "attempt_count")
    val attemptCount: Int = 0,

    @ColumnInfo(name = "error_code")
    val errorCode: String? = null,

    @ColumnInfo(name = "image_hash")
    val imageHash: String? = null,

    @ColumnInfo(name = "extraction_complete", defaultValue = "0")
    val extractionComplete: Boolean = false,

    @ColumnInfo(name = "resurfacing_status", defaultValue = "'PENDING'")
    val resurfacingStatus: String = "PENDING",

    @ColumnInfo(name = "resurfacing_attempt_count", defaultValue = "0")
    val resurfacingAttemptCount: Int = 0
) {
    fun toDomainModel(): MemoryCard {
        val parsedTags = try {
            Json.decodeFromString<CardTags>(tagsJson)
        } catch (_: Exception) {
            CardTags()
        }

        val floatEmbedding = StoredEmbedding.decode(embedding)

        return MemoryCard(
            id = id,
            imageUri = imageUri,
            createdAt = createdAt,
            userIntent = userIntent,
            title = title,
            summary = summary,
            tags = parsedTags,
            contentType = try { ContentType.valueOf(contentType) } catch (_: Exception) { ContentType.UNKNOWN },
            ocrText = ocrText,
            embedding = floatEmbedding,
            status = try { ProcessingStatus.valueOf(status) } catch (_: Exception) { ProcessingStatus.PENDING },
            attemptCount = attemptCount,
            errorCode = errorCode
        )
    }

    companion object {
        fun fromDomainModel(card: MemoryCard): MemoryCardEntity {
            val tagsJson = Json.encodeToString(card.tags)
            val byteEmbedding = StoredEmbedding.encode(card.embedding)

            return MemoryCardEntity(
                id = card.id,
                imageUri = card.imageUri,
                createdAt = card.createdAt,
                userIntent = card.userIntent,
                title = card.title,
                summary = card.summary,
                tagsJson = tagsJson,
                contentType = card.contentType.name,
                ocrText = card.ocrText,
                embedding = byteEmbedding,
                status = card.status.name,
                attemptCount = card.attemptCount,
                errorCode = card.errorCode
            )
        }
    }
}

/** Invalid vectors must not make a saved card unreadable. */
object StoredEmbedding {
    const val DIMENSION = 768

    fun isValid(vector: FloatArray): Boolean = vector.size == DIMENSION &&
        vector.all { it.isFinite() } && vector.any { it != 0f }

    fun decode(bytes: ByteArray?): FloatArray? {
        if (bytes == null || bytes.size != DIMENSION * 4) return null
        return VectorMath.toFloatArray(bytes).takeIf(::isValid)
    }

    fun encode(vector: FloatArray?): ByteArray? = vector?.takeIf(::isValid)?.let(VectorMath::toByteArray)
}
