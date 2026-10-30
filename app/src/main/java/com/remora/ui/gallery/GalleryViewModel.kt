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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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

    private val selectedCardId = MutableStateFlow<Long?>(null)
    val selectedCard: StateFlow<MemoryCard?> = combine(cards, selectedCardId) { currentCards, id ->
        currentCards.firstOrNull { it.id == id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _actionInProgress = MutableStateFlow(false)
    val actionInProgress = _actionInProgress.asStateFlow()
    private val _actionError = MutableStateFlow<String?>(null)
    val actionError = _actionError.asStateFlow()

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

    fun showCard(cardId: Long) {
        if (!_actionInProgress.value) {
            _actionError.value = null
            selectedCardId.value = cardId
        }
    }

    fun dismissCard() {
        selectedCardId.value = null
    }

    fun retryCard(cardId: Long, retry: suspend (Long) -> Unit) {
        runCardAction("Chưa thể thử lại. Hãy thử lại sau.") { retry(cardId) }
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

    fun deleteCard(cardId: Long, delete: suspend (Long) -> Unit) {
        runCardAction("Chưa xóa được thẻ. Hãy thử lại.") {
            delete(cardId)
            if (selectedCardId.value == cardId) selectedCardId.value = null
        }
    }

    fun clearCards(clear: suspend () -> Unit, onCleared: () -> Unit = {}) {
        runCardAction("Chưa xóa được toàn bộ thẻ. Hãy thử lại.") {
            clear()
            selectedCardId.value = null
            onCleared()
        }
    }

    private fun runCardAction(errorMessage: String, action: suspend () -> Unit) {
        if (_actionInProgress.value) return
        _actionInProgress.value = true
        _actionError.value = null
        viewModelScope.launch {
            try {
                action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _actionError.value = errorMessage
            } finally {
                _actionInProgress.value = false
            }
        }
    }
}
