package com.remora.ui.search

import androidx.lifecycle.ViewModelStore
import com.remora.search.MatchKind
import com.remora.search.SearchOutcome
import com.remora.search.SearchTestDao
import com.remora.search.SemanticSearchEngine
import com.remora.search.searchCard
import com.remora.search.searchVector
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import timber.log.Timber

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val dao = SearchTestDao()

    @Before
    fun setUp() { Dispatchers.setMain(dispatcher) }

    @After
    fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    private fun viewModel(embed: suspend (String) -> Result<List<Float>> = { Result.failure(IllegalStateException("offline")) }): SearchViewModel =
        SearchViewModel(SemanticSearchEngine(dao, embed)).also { store.put("search", it) }

    private fun outcome(vm: SearchViewModel): SearchOutcome = when (val state = vm.uiState.value) {
        is SearchUiState.Success -> state.outcome
        is SearchUiState.Empty -> state.outcome
        else -> error("Expected search outcome, got $state")
    }

    @Test
    fun indexedCountTracksCompletionInsertionDeletionAndClearWhileIdle() = runTest(dispatcher) {
        val pending = searchCard(1).copy(status = "PENDING", embedding = null)
        dao.cards.value = listOf(pending)
        val vm = viewModel()
        runCurrent()
        assertEquals(0, vm.indexedCount.value)
        dao.cards.value = listOf(searchCard(1), searchCard(2))
        runCurrent()
        assertEquals(2, vm.indexedCount.value)
        dao.cards.value = listOf(searchCard(2))
        runCurrent()
        assertEquals(1, vm.indexedCount.value)
        dao.cards.value = emptyList()
        runCurrent()
        assertEquals(0, vm.indexedCount.value)
        assertEquals(SearchUiState.Idle, vm.uiState.value)
    }

    @Test
    fun activeSearchRefreshesNotesContentStatusAndMembership() = runTest(dispatcher) {
        val pending = searchCard(1).copy(status = "PENDING", embedding = null, userIntent = "Ôn thi")
        dao.cards.value = listOf(pending)
        val vm = viewModel()
        runCurrent()
        vm.onQueryChanged("on thi")
        advanceUntilIdle()
        assertEquals("Ôn thi", outcome(vm).results.single().card.userIntent)
        assertFalse(outcome(vm).semanticAvailable)

        val completed = searchCard(1).copy(userIntent = "Ôn thi", title = "Latest title", summary = "Latest summary")
        dao.cards.value = listOf(completed, searchCard(2).copy(userIntent = "Ôn thi ngày mai"))
        runCurrent()
        assertEquals(setOf(1L, 2L), outcome(vm).results.map { it.card.id }.toSet())
        val updated = outcome(vm).results.single { it.card.id == 1L }.card
        assertEquals("Latest title", updated.title)
        assertEquals("Latest summary", updated.summary)
        assertEquals(completed.status, updated.status.name)
        assertEquals(2, vm.indexedCount.value)
        assertEquals(listOf("on thi"), vm.recentQueries.value)

        dao.cards.value = listOf(completed.copy(userIntent = "Different note"))
        runCurrent()
        assertTrue(vm.uiState.value is SearchUiState.Empty)
        assertEquals(1, outcome(vm).indexedCount)
        dao.cards.value = emptyList()
        runCurrent()
        assertEquals(0, outcome(vm).indexedCount)
        assertEquals(0, vm.indexedCount.value)
    }

    @Test
    fun emptySearchBecomesSuccessWhenRelevantCardArrivesUsingCachedVector() = runTest(dispatcher) {
        var calls = 0
        dao.cards.value = listOf(searchCard(1, 0.5f))
        val vm = viewModel { calls++; Result.success(searchVector(1f).toList()) }
        runCurrent()
        vm.onQueryChanged("quasar")
        advanceUntilIdle()
        assertTrue(vm.uiState.value is SearchUiState.Empty)
        assertTrue(vm.recentQueries.value.isEmpty())
        dao.cards.value = listOf(searchCard(1, 0.5f), searchCard(2, 0.8f))
        runCurrent()
        assertEquals(2L, outcome(vm).results.single().card.id)
        assertEquals(1, calls)
        dao.cards.value = listOf(searchCard(1, 0.5f))
        runCurrent()
        assertTrue(vm.uiState.value is SearchUiState.Empty)
        assertEquals(1, calls)
    }

    @Test
    fun rapidEditsDebounceAndKeyboardSubmitRunsImmediately() = runTest(dispatcher) {
        val queries = mutableListOf<String>()
        dao.cards.value = listOf(searchCard(1).copy(title = "quasar"))
        val vm = viewModel { queries += it; Result.failure(IllegalStateException("offline")) }
        runCurrent()
        vm.onQueryChanged("old topic")
        advanceTimeBy(200)
        vm.onQueryChanged("quasar")
        advanceTimeBy(449)
        runCurrent()
        assertTrue(queries.isEmpty())
        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf("quasar"), queries)
        vm.onQueryChanged("Physics")
        vm.onSearchSubmit()
        runCurrent()
        assertEquals(listOf("quasar", "Physics"), queries)
    }

    @Test
    fun lateCancelledQueryCannotReplaceNewResultsOrClearTheirLoadingState() = runTest(dispatcher) {
        val old = CompletableDeferred<Unit>()
        val newer = CompletableDeferred<Unit>()
        dao.cards.value = listOf(searchCard(1).copy(title = "oldtopic"), searchCard(2).copy(title = "newtopic"))
        val vm = viewModel { text ->
            withContext(NonCancellable) { if (text == "oldtopic") old.await() else newer.await() }
            Result.failure(IllegalStateException("offline"))
        }
        runCurrent()
        vm.onQueryChanged("oldtopic")
        vm.onSearchSubmit()
        runCurrent()
        vm.onQueryChanged("newtopic")
        vm.onSearchSubmit()
        runCurrent()
        assertTrue(vm.isSearching.value)
        old.complete(Unit)
        runCurrent()
        assertTrue(vm.isSearching.value)
        assertTrue(vm.recentQueries.value.isEmpty())
        newer.complete(Unit)
        runCurrent()
        assertEquals(2L, outcome(vm).results.single().card.id)
        assertEquals(listOf("newtopic"), vm.recentQueries.value)
        assertFalse(vm.isSearching.value)
    }

    @Test
    fun libraryChangeInvalidatesInFlightSnapshotAndRemovesDeletedResults() = runTest(dispatcher) {
        val release = CompletableDeferred<Unit>()
        var calls = 0
        dao.cards.value = listOf(searchCard(1).copy(title = "quasar"))
        val vm = viewModel {
            calls++
            if (calls == 2) withContext(NonCancellable) { release.await() }
            Result.failure(IllegalStateException("offline"))
        }
        runCurrent()
        vm.onQueryChanged("quasar")
        advanceUntilIdle()
        assertEquals(1L, outcome(vm).results.single().card.id)
        vm.onSearchSubmit()
        runCurrent()
        assertTrue(vm.isSearching.value)
        dao.cards.value = emptyList()
        runCurrent()
        assertTrue(vm.uiState.value is SearchUiState.Empty)
        assertEquals(0, vm.indexedCount.value)
        release.complete(Unit)
        runCurrent()
        assertTrue(vm.uiState.value is SearchUiState.Empty)
        assertEquals(0, outcome(vm).indexedCount)
        assertFalse(vm.isSearching.value)
    }

    @Test
    fun clearingQueryInvalidatesLateResponseAndKeepsIdle() = runTest(dispatcher) {
        val release = CompletableDeferred<Unit>()
        val vm = viewModel {
            withContext(NonCancellable) { release.await() }
            Result.success(searchVector(1f).toList())
        }
        runCurrent()
        vm.onQueryChanged("quasar")
        vm.onSearchSubmit()
        runCurrent()
        vm.onQueryChanged("")
        assertEquals(SearchUiState.Idle, vm.uiState.value)
        assertFalse(vm.isSearching.value)
        release.complete(Unit)
        runCurrent()
        assertEquals(SearchUiState.Idle, vm.uiState.value)
        assertTrue(vm.recentQueries.value.isEmpty())
    }

    @Test
    fun cancellationIsNotPresentedAsSearchError() = runTest(dispatcher) {
        val vm = viewModel { throw CancellationException("Cancelled") }
        runCurrent()
        vm.onQueryChanged("quasar")
        advanceUntilIdle()
        assertFalse(vm.uiState.value is SearchUiState.Error)
        assertFalse(vm.isSearching.value)
    }

    @Test
    fun searchErrorsAndLogsExcludeQueryAndExceptionDetails() = runTest(dispatcher) {
        val messages = mutableListOf<String>()
        val tree = object : Timber.Tree() {
            override fun log(priority: Int, tag: String?, message: String, t: Throwable?) { messages += message; assertNull(t) }
        }
        Timber.plant(tree)
        try {
            val vm = viewModel { throw IllegalStateException("private provider response") }
            runCurrent()
            vm.onQueryChanged("private study query")
            advanceUntilIdle()
            assertEquals(SearchUiState.Error("Tìm kiếm thất bại, vui lòng thử lại"), vm.uiState.value)
            assertFalse(vm.isSearching.value)
            assertTrue(messages.isNotEmpty())
            assertTrue(messages.none { it.contains("private") })
        } finally { Timber.uproot(tree) }
    }

    @Test
    fun abbreviationAndTimeMetadataArePreservedThroughViewModel() = runTest(dispatcher) {
        dao.cards.value = listOf(searchCard(1).copy(title = "Phân tích thiết kế hệ thống", createdAt = System.currentTimeMillis() - 1000))
        val vm = viewModel()
        runCurrent()
        vm.onQueryChanged("pttk hôm bữa")
        advanceUntilIdle()
        val result = outcome(vm)
        assertEquals(MatchKind.KEYWORD, result.results.single().matchKind)
        assertEquals(listOf("pttk" to "phân tích thiết kế hệ thống"), result.expansions)
        assertNotNull(result.timeLabel)
        assertTrue(result.highlightTerms.contains("phan"))
    }
}
