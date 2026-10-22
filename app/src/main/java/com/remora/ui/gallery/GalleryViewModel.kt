package com.remora.ui.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.remora.core.model.contract.MemoryCardRepository
import com.remora.core.model.entity.MemoryCard
import com.remora.core.model.entity.ProcessingStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GalleryViewModel(
    private val repository: MemoryCardRepository
) : ViewModel() {

    val cards: StateFlow<List<MemoryCard>> = repository.getAllCards()
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

    fun deleteCard(cardId: Long) {
        viewModelScope.launch {
            repository.deleteCard(cardId)
        }
    }
}
