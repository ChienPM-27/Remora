package com.remora.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.remora.core.model.entity.CardTagsCodec
import com.remora.core.model.entity.ContentType
import com.remora.core.model.entity.MemoryCard
import com.remora.core.model.entity.ProcessingStatus

@Entity(
    tableName = "memory_card",
    indices = [
        Index(value = ["created_at"]),
        Index(value = ["status"]),
        Index(value = ["image_hash"])
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

    // SHA-256 hex of the original image bytes; NULL for cards saved before this column existed.
    @ColumnInfo(name = "image_hash")
    val imageHash: String? = null
) {
    fun toDomainModel(): MemoryCard {
        val parsedTags = CardTagsCodec.decode(tagsJson)

        val floatEmbedding = embedding?.let { bytes ->
            val floats = FloatArray(bytes.size / 4)
            val buffer = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            for (i in floats.indices) {
                floats[i] = buffer.float
            }
            floats
        }

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
            errorCode = errorCode,
            imageHash = imageHash
        )
    }

    companion object {
        fun fromDomainModel(card: MemoryCard): MemoryCardEntity {
            val tagsJson = CardTagsCodec.encode(card.tags)
            val byteEmbedding = card.embedding?.let { floats ->
                val buffer = java.nio.ByteBuffer.allocate(floats.size * 4).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                for (f in floats) {
                    buffer.putFloat(f)
                }
                buffer.array()
            }

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
                errorCode = card.errorCode,
                imageHash = card.imageHash
            )
        }
    }
}
