package com.remora.data.seed

import android.content.Context
import android.net.Uri
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.remora.BuildConfig
import com.remora.core.model.entity.CardTags
import com.remora.core.model.entity.ContentType
import com.remora.core.model.entity.MemoryCard
import com.remora.core.model.entity.ProcessingStatus
import com.remora.data.local.RemoraDatabase
import com.remora.data.local.entity.MemoryCardEntity
import com.remora.work.CardAnalyzeWorker
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream

object DemoDataSeeder {

    data class DemoSeedItem(
        val assetFileName: String,
        val userIntent: String
    )

    private val DEMO_ITEMS = listOf(
        DemoSeedItem("01_math_pythagoras.png", "Công thức cần nhớ thi giữa kỳ"),
        DemoSeedItem("02_math_thales.png", "Slide định lý Thales thầy giảng trên lớp"),
        DemoSeedItem("03_physics_newton2.png", "Định luật 2 Newton vật lý đại cương"),
        DemoSeedItem("04_calculus_derivative.png", "Bảng đạo hàm các hàm số cơ bản"),
        DemoSeedItem("05_prob_normal_dist.png", "Slide ôn tập thi xác suất thống kê"),
        DemoSeedItem("06_cs_binary_search.png", "Code thuật toán tìm kiếm nhị phân"),
        DemoSeedItem("07_chem_periodic_law.png", "Bảng tuần hoàn Men-đê-lê-ép ôn thi Hóa"),
        DemoSeedItem("08_cs_quicksort.png", "Giải thuật sắp xếp nhanh QuickSort"),
        DemoSeedItem("09_algebra_matrix.png", "Công thức tính định thức ma trận"),
        DemoSeedItem("10_biology_dna.png", "Cấu trúc xoắn kép phân tử ADN")
    )

    suspend fun seedDemoCardsIfEmpty(context: Context, db: RemoraDatabase) {
        if (!BuildConfig.DEBUG) {
            Timber.i("DemoDataSeeder skipped: not a debug build")
            return
        }

        val existingCount = db.memoryCardDao().getCardCount()
        if (existingCount > 0) {
            Timber.i("Database already has $existingCount cards, skipping demo import.")
            return
        }

        Timber.i("Importing ${DEMO_ITEMS.size} demo screenshots via the real processing pipeline...")

        val demoDir = File(context.filesDir, "demo_images").apply {
            if (!exists()) mkdirs()
        }

        for (item in DEMO_ITEMS) {
            try {
                // 1. Copy real asset image to local storage to produce a valid URI
                val destFile = File(demoDir, item.assetFileName)
                if (!destFile.exists() || destFile.length() == 0L) {
                    context.assets.open(item.assetFileName).use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }

                val imageUri = Uri.fromFile(destFile).toString()

                // 2. Insert card in PENDING status - NO mock vectors or static done status
                val initialCard = MemoryCard(
                    id = 0L,
                    imageUri = imageUri,
                    createdAt = System.currentTimeMillis(),
                    userIntent = item.userIntent,
                    title = "Đang xử lý...",
                    summary = "",
                    tags = CardTags(),
                    contentType = ContentType.UNKNOWN,
                    ocrText = "",
                    embedding = null,
                    status = ProcessingStatus.PENDING,
                    attemptCount = 0
                )

                val newCardId = db.memoryCardDao().insertCard(MemoryCardEntity.fromDomainModel(initialCard))
                Timber.i("Inserted PENDING demo card #$newCardId for ${item.assetFileName}")

                // 3. Enqueue CardAnalyzeWorker to run through the genuine AI extraction & resurfacing pipeline
                val workRequest = OneTimeWorkRequestBuilder<CardAnalyzeWorker>()
                    .setInputData(workDataOf(CardAnalyzeWorker.KEY_CARD_ID to newCardId))
                    .build()

                WorkManager.getInstance(context).enqueue(workRequest)

            } catch (e: Exception) {
                Timber.e(e, "Failed to import demo image ${item.assetFileName} through pipeline")
            }
        }

        Timber.i("All demo images have been enqueued into real WorkManager pipeline.")
    }
}
