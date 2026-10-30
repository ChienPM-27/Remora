package com.remora.ui.resurfacing

import com.remora.core.model.entity.MemoryCard
import com.remora.core.model.entity.ProcessingStatus
import com.remora.core.model.resurface.ResurfacedItem

data class ResurfacingBatch(
    val sourceCard: MemoryCard,
    val status: ProcessingStatus,
    val items: List<ResurfacedItem> = emptyList()
)
