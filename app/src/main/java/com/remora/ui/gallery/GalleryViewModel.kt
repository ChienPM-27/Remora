package com.remora.ui.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.remora.core.model.contract.MemoryCardRepository
import com.remora.core.model.entity.MemoryCard
import com.remora.core.model.entity.ProcessingStatus
import com.remora.core.model.entity.ResurfaceGroup
import com.remora.core.model.entity.ResurfaceRelation
import com.remora.core.model.resurface.ResurfacedItem
import com.remora.data.local.dao.MemoryCardDao
import com.remora.data.local.dao.ResurfacingResultDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GalleryViewModel(
    private val repository: MemoryCardRepository,
    private val resurfacingResultDao: ResurfacingResultDao,
    private val memoryCardDao: MemoryCardDao
) : ViewModel() {

    val cards: StateFlow<List<MemoryCard>> = repository.getAllCards()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val resurfacedItems: StateFlow<List<ResurfacedItem>> = resurfacingResultDao.getRecentResultsFlow()
        .map { entities ->
            entities.mapNotNull { entity ->
                val cardEntity = memoryCardDao.getCardById(entity.candidateCardId)
                val card = cardEntity?.toDomainModel()
                if (card != null) {
                    ResurfacedItem(
                        candidateCard = card,
                        group = try { ResurfaceGroup.valueOf(entity.group) } catch (_: Exception) { ResurfaceGroup.SIMILAR },
                        relation = try { ResurfaceRelation.valueOf(entity.relation) } catch (_: Exception) { ResurfaceRelation.SUPPLEMENT },
                        newInfo = entity.newInfo,
                        benefit = entity.benefit,
                        reason = entity.reason,
                        confidence = entity.confidence
                    )
                } else null
            }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun retryCard(card: MemoryCard, onRetryEnqueued: (Long) -> Unit) {
        viewModelScope.launch {
            val pendingCard = card.copy(
                status = ProcessingStatus.PENDING,
                attemptCount = 0,
                errorCode = null
            )
            repository.updateCard(pendingCard)
            onRetryEnqueued(card.id)
        }
    }

    fun importCard(
        imageUri: String,
        userIntent: String? = null,
        onCardCreated: (Long) -> Unit
    ) {
        viewModelScope.launch {
            val card = MemoryCard(
                imageUri = imageUri,
                createdAt = System.currentTimeMillis(),
                userIntent = userIntent?.trim()?.ifBlank { null },
                title = "Đang xử lý...",
                status = ProcessingStatus.PENDING
            )
            val newId = repository.insertCard(card)
            onCardCreated(newId)
        }
    }

    fun deleteCard(cardId: Long) {
        viewModelScope.launch {
            repository.deleteCard(cardId)
        }
    }
}
