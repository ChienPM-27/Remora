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
import com.remora.data.local.entity.MemoryCardEntity
import com.remora.data.local.entity.ResurfacingResultEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

data class ResurfacedState(
    val sourceCard: MemoryCard? = null,
    val items: List<ResurfacedItem> = emptyList()
)

class GalleryViewModel(
    private val repository: MemoryCardRepository,
    private val resurfacingResultDao: ResurfacingResultDao,
    private val memoryCardDao: MemoryCardDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val selectedSourceCardId = kotlinx.coroutines.flow.MutableStateFlow<Long?>(null)

    /**
     * Explicitly focuses the Resurfacing tab on a specific source card ID (e.g. from notification intent).
     * Pass null to follow the latest source card automatically.
     */
    fun selectSourceCard(cardId: Long?) {
        selectedSourceCardId.value = cardId
    }

    val cards: StateFlow<List<MemoryCard>> = repository.getAllCards()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val resurfacedState: StateFlow<ResurfacedState> = combine(
        selectedSourceCardId,
        resurfacingResultDao.getLatestSourceCardIdFlow()
    ) { explicitId, latestSourceId ->
        explicitId to latestSourceId
    }
    .flatMapLatest { (explicitId, latestSourceId) ->
        val candidateId = if (explicitId != null && explicitId > 0L) {
            if (latestSourceId != null && latestSourceId > explicitId) {
                // A newer card with resurfacing results arrived after the pinned card, unpin!
                selectedSourceCardId.value = null
                latestSourceId
            } else {
                explicitId
            }
        } else {
            latestSourceId
        }

        if (candidateId == null) {
            flowOf(ResurfacedState())
        } else {
            resurfacingResultDao.getResultsForCardFlow(candidateId).flatMapLatest { entities ->
                val sourceCard = try {
                    memoryCardDao.getCardById(candidateId)?.toDomainModel()
                } catch (e: Exception) {
                    null
                }

                val isPinnedInvalid = explicitId != null && candidateId == explicitId &&
                    (sourceCard == null || entities.isEmpty()) &&
                    latestSourceId != null && latestSourceId != explicitId

                if (isPinnedInvalid) {
                    selectedSourceCardId.value = null
                    resurfacingResultDao.getResultsForCardFlow(latestSourceId).map { latestEntities ->
                        mapEntitiesToState(latestSourceId, latestEntities)
                    }
                } else {
                    flowOf(mapEntitiesToState(candidateId, entities))
                }
            }
        }
    }
    .flowOn(ioDispatcher)
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ResurfacedState()
    )

    private suspend fun mapEntitiesToState(
        sourceCardId: Long,
        entities: List<ResurfacingResultEntity>
    ): ResurfacedState {
        val sourceCard = try {
            memoryCardDao.getCardById(sourceCardId)?.toDomainModel()
        } catch (e: Exception) {
            Timber.w(e, "Failed to load source card #$sourceCardId")
            null
        }

        val items = entities.mapNotNull { entity ->
            try {
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
            } catch (e: Exception) {
                Timber.w(e, "Failed to map candidate card #${entity.candidateCardId}")
                null
            }
        }

        return ResurfacedState(
            sourceCard = sourceCard,
            items = items
        )
    }

    val resurfacedItems: StateFlow<List<ResurfacedItem>> = resurfacedState
        .map { it.items }
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

    /**
     * Creates a PENDING card for an imported image. When [imageHash] matches an existing card,
     * no card is created and [onDuplicateFound] receives the existing card's id instead —
     * unless [forceInsert] is set (the user explicitly chose "save as separate card").
     */
    fun importCard(
        imageUri: String,
        userIntent: String? = null,
        imageHash: String? = null,
        forceInsert: Boolean = false,
        onCardCreated: (Long) -> Unit,
        onDuplicateFound: (Long) -> Unit = {}
    ) {
        viewModelScope.launch(ioDispatcher) {
            val card = MemoryCard(
                imageUri = imageUri,
                createdAt = System.currentTimeMillis(),
                userIntent = userIntent?.trim()?.ifBlank { null },
                title = "Đang xử lý...",
                status = ProcessingStatus.PENDING,
                imageHash = imageHash
            )
            val entity = MemoryCardEntity.fromDomainModel(card)
            val result = if (forceInsert) memoryCardDao.insertCard(entity)
            else memoryCardDao.insertCardIfHashAbsent(entity)
            if (result < 0) onDuplicateFound(-result) else onCardCreated(result)
        }
    }

    fun deleteCard(cardId: Long) {
        viewModelScope.launch {
            repository.deleteCard(cardId)
            resurfacingResultDao.deleteResultsForCard(cardId)
        }
    }
}
