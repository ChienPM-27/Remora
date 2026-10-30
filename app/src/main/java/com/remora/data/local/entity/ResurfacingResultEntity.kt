package com.remora.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.remora.core.model.entity.MemoryCard
import com.remora.core.model.entity.ResurfaceGroup
import com.remora.core.model.entity.ResurfaceRelation
import com.remora.core.model.resurface.ResurfacedItem

@Entity(
    tableName = "resurfacing_result",
    foreignKeys = [
        ForeignKey(
            entity = MemoryCardEntity::class,
            parentColumns = ["id"],
            childColumns = ["new_card_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = MemoryCardEntity::class,
            parentColumns = ["id"],
            childColumns = ["candidate_card_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["new_card_id"]),
        Index(value = ["candidate_card_id"])
    ]
)
data class ResurfacingResultEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "new_card_id")
    val newCardId: Long,

    @ColumnInfo(name = "candidate_card_id")
    val candidateCardId: Long,

    @ColumnInfo(name = "group")
    val group: String,

    @ColumnInfo(name = "relation")
    val relation: String,

    @ColumnInfo(name = "new_info")
    val newInfo: String = "",

    @ColumnInfo(name = "benefit")
    val benefit: String = "",

    @ColumnInfo(name = "reason")
    val reason: String = "",

    @ColumnInfo(name = "confidence")
    val confidence: Float,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toDomainModel(candidate: MemoryCard): ResurfacedItem? {
        if (candidate.id != candidateCardId) return null
        val parsedGroup = runCatching { ResurfaceGroup.valueOf(group) }.getOrNull() ?: return null
        val parsedRelation = runCatching { ResurfaceRelation.valueOf(relation) }.getOrNull() ?: return null
        return ResurfacedItem(candidate, parsedGroup, parsedRelation, newInfo, benefit, reason, confidence)
    }
}
