package com.remora.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.remora.search.SearchResultCard
import com.remora.search.SemanticSearchEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SearchUiState {
    object Idle : SearchUiState
    object Loading : SearchUiState
    data class Success(val results: List<SearchResultCard>) : SearchUiState
    object Empty : SearchUiState
    data class Error(val message: String) : SearchUiState
}

class SearchViewModel(
    private val searchEngine: SemanticSearchEngine
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    fun onQueryChanged(newQuery: String) {
        _query.value = newQuery
        searchJob?.cancel()

        if (newQuery.isBlank()) {
            _uiState.value = SearchUiState.Idle
            return
        }

        searchJob = viewModelScope.launch {
            delay(400) // debounce
            _uiState.value = SearchUiState.Loading
            try {
                val results = searchEngine.search(newQuery)
                _uiState.value = if (results.isEmpty()) SearchUiState.Empty else SearchUiState.Success(results)
            } catch (e: Exception) {
                timber.log.Timber.e(e, "Search failed for query: $newQuery")
                _uiState.value = SearchUiState.Error(e.message ?: "Tìm kiếm thất bại, vui lòng thử lại")
            }
        }
    }
}
