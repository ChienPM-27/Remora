package com.remora.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.remora.core.model.entity.MemoryCard
import com.remora.search.SearchOutcome
import com.remora.search.SemanticSearchEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import timber.log.Timber

sealed interface SearchUiState {
    object Idle : SearchUiState
    object Loading : SearchUiState
    data class Success(val outcome: SearchOutcome) : SearchUiState
    data class Empty(val outcome: SearchOutcome) : SearchUiState
    data class Error(val message: String) : SearchUiState
}

class SearchViewModel(
    private val searchEngine: SemanticSearchEngine
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    /** True while a request is in flight; lets the UI keep old results on screen instead of flashing a spinner. */
    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _recentQueries = MutableStateFlow<List<String>>(emptyList())
    val recentQueries: StateFlow<List<String>> = _recentQueries.asStateFlow()

    private val _indexedCount = MutableStateFlow(0)
    val indexedCount: StateFlow<Int> = _indexedCount.asStateFlow()

    private var searchJob: Job? = null
    private var library: List<MemoryCard>? = null

    // Query edits and library changes both invalidate in-flight results, even if work returns late.
    private var requestId = 0

    init {
        viewModelScope.launch {
            try {
                searchEngine.observeLibrary().distinctUntilChanged().collect { cards ->
                    library = cards
                    _indexedCount.value = searchEngine.indexedCount(cards)
                    refreshVisibleResults(cards)
                    launchSearch(debounceMs = 0)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                Timber.e("Library refresh failed")
                _uiState.value = SearchUiState.Error("Chưa thể cập nhật thư viện, vui lòng thử lại")
            }
        }
    }

    fun onQueryChanged(newQuery: String) {
        _query.value = newQuery
        launchSearch(debounceMs = DEBOUNCE_MS)
    }

    /** Keyboard "search" action or tapping a suggestion: no debounce. */
    fun onSearchSubmit() {
        launchSearch(debounceMs = 0)
    }

    private fun launchSearch(debounceMs: Long) {
        searchJob?.cancel()
        val myId = ++requestId
        val q = _query.value.trim()

        if (q.length < MIN_QUERY_LENGTH) {
            _isSearching.value = false
            _uiState.value = SearchUiState.Idle
            return
        }

        searchJob = viewModelScope.launch {
            if (debounceMs > 0) delay(debounceMs)
            _isSearching.value = true
            val current = _uiState.value
            if (current !is SearchUiState.Success && current !is SearchUiState.Empty) {
                _uiState.value = SearchUiState.Loading
            }
            try {
                val outcome = searchEngine.search(q, cards = library)
                currentCoroutineContext().ensureActive()
                if (myId != requestId) return@launch // a newer query superseded this one
                if (outcome.results.isEmpty()) {
                    _uiState.value = SearchUiState.Empty(outcome)
                } else {
                    _uiState.value = SearchUiState.Success(outcome)
                    rememberQuery(q)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                Timber.e("Search failed")
                if (myId == requestId) {
                    _uiState.value = SearchUiState.Error("Tìm kiếm thất bại, vui lòng thử lại")
                }
            } finally {
                if (myId == requestId) _isSearching.value = false
            }
        }
    }

    /** Remove deleted results immediately and keep displayed notes/status current during reranking. */
    private fun refreshVisibleResults(cards: List<MemoryCard>) {
        val previous = when (val state = _uiState.value) {
            is SearchUiState.Success -> state.outcome
            is SearchUiState.Empty -> state.outcome
            else -> return
        }
        val byId = cards.associateBy { it.id }
        val outcome = previous.copy(
            results = previous.results.mapNotNull { result ->
                byId[result.card.id]?.let { result.copy(card = it) }
            },
            indexedCount = _indexedCount.value
        )
        _uiState.value = if (outcome.results.isEmpty()) SearchUiState.Empty(outcome)
        else SearchUiState.Success(outcome)
    }

    private fun rememberQuery(q: String) {
        _recentQueries.value = (listOf(q) + _recentQueries.value.filterNot { it.equals(q, ignoreCase = true) })
            .take(MAX_RECENT)
    }

    private companion object {
        const val DEBOUNCE_MS = 450L
        const val MIN_QUERY_LENGTH = 2
        const val MAX_RECENT = 5
    }
}
