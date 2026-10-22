package com.remora.core.model.contract

import com.remora.core.model.entity.Screenshot
import com.remora.core.model.resurface.ResurfaceResult
import kotlinx.coroutines.flow.Flow

interface ResurfaceEngine {
    /**
     * So khớp và phân tích liên kết giữa ảnh mới và kho ảnh cũ
     */
    suspend fun resurface(
        target: Screenshot,
        candidates: List<Screenshot>
    ): Result<ResurfaceResult>
}

interface ScreenshotRepository {
    fun getAllScreenshots(): Flow<List<Screenshot>>
    suspend fun getScreenshotById(id: Long): Screenshot?
    suspend fun insertScreenshot(screenshot: Screenshot): Long
    suspend fun updateScreenshot(screenshot: Screenshot)
    suspend fun deleteScreenshot(id: Long)
    suspend fun getAllWithEmbeddings(): List<Screenshot>
    suspend fun searchHybrid(query: String, limit: Int = 20): List<Screenshot>
}
