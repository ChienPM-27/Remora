package com.remora.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.remora.data.local.RemoraDatabase
import com.remora.data.local.entity.MemoryCardEntity
import com.remora.util.ImageImporter
import com.remora.work.CardWorkScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class SaveResult(val cardId: Long, val duplicate: Boolean)

/** The only entry point for a user-authorized image save. No network work before insertion. */
class SaveCoordinator(
    private val context: Context,
    private val database: RemoraDatabase,
    private val scheduler: CardWorkScheduler
) {
    private val mutationLock = Mutex()

    suspend fun save(source: Uri, intent: String?): SaveResult = withContext(Dispatchers.IO) {
        mutationLock.withLock {
            check(database.appSettingDao().getSetting()?.consentGranted == true) { "CONSENT_REQUIRED" }
            val image = ImageImporter.importImage(context, source)
            var retained = false
            try {
                val result = database.withTransaction {
                    val existing = database.memoryCardDao().getCardByHash(image.hash)
                    if (existing != null) {
                        SaveResult(existing.id, true)
                    } else {
                        val id = database.memoryCardDao().insertCard(MemoryCardEntity(
                            imageUri = image.uri,
                            imageHash = image.hash,
                            createdAt = System.currentTimeMillis(),
                            userIntent = intent?.trim()?.take(2000)?.ifBlank { null },
                            title = "Ảnh đã lưu"
                        ))
                        SaveResult(id, false)
                    }
                }
                retained = !result.duplicate
                if (!result.duplicate) scheduler.analyze(result.cardId)
                result
            } finally {
                if (!retained) ImageImporter.deleteOwnedImage(context, image.uri)
            }
        }
    }

    suspend fun reconcilePending() = withContext(Dispatchers.IO) {
        database.memoryCardDao().getPendingCards().forEach { scheduler.analyze(it.id) }
    }

    suspend fun retry(cardId: Long) = mutationLock.withLock {
        val dao = database.memoryCardDao()
        database.withTransaction {
            val card = dao.getCardById(cardId) ?: return@withTransaction
            if (card.status == "FAILED") dao.updateCard(card.copy(status = "PENDING", attemptCount = 0, errorCode = null))
        }
        if (dao.getCardById(cardId)?.status == "PENDING") scheduler.analyze(cardId)
    }

    suspend fun delete(cardId: Long) = withContext(Dispatchers.IO) {
        mutationLock.withLock {
            val card = database.memoryCardDao().getCardById(cardId) ?: return@withLock
            scheduler.cancel(cardId)
            database.memoryCardDao().deleteCard(cardId)
            ImageImporter.deleteOwnedImage(context, card.imageUri)
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        mutationLock.withLock {
            val cards = database.openHelper.readableDatabase.query("SELECT image_uri FROM memory_card").use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
            }
            scheduler.cancelAll()
            database.withTransaction {
                database.resurfacingResultDao().deleteAllResults()
                database.memoryCardDao().deleteAllCards()
            }
            cards.forEach { ImageImporter.deleteOwnedImage(context, it) }
        }
    }
}
