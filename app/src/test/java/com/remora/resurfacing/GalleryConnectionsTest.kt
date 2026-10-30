package com.remora.resurfacing

import app.cash.turbine.test
import com.remora.core.model.entity.ProcessingStatus
import com.remora.data.local.entity.MemoryCardEntity
import com.remora.data.local.entity.ResurfacingResultEntity
import com.remora.data.repository.DefaultMemoryCardRepository
import com.remora.ui.gallery.GalleryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GalleryConnectionsTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ResurfacingTestStore()
    private fun viewModel() = GalleryViewModel(DefaultMemoryCardRepository(store), store, store)
    private fun card(id: Long, state: String = "DONE") = MemoryCardEntity(
        id = id, imageUri = "", createdAt = id, title = "Original title $id",
        userIntent = "Original note $id", status = "DONE", extractionComplete = true, resurfacingStatus = state)
    private fun link(source: Long, candidate: Long, group: String = "SIMILAR", relation: String = "SUPPLEMENT") =
        ResurfacingResultEntity(newCardId = source, candidateCardId = candidate, group = group, relation = relation,
            reason = "Reason for source $source", benefit = "Learning benefit for source $source", confidence = 0.92f)

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun twoSourcesRetainTheirOwnGroupsCandidateAndExplanations() = runTest {
        listOf(card(10), card(20), card(30)).forEach(store::put)
        store.results.value = listOf(link(20, 10), link(30, 10, "COMPLEMENTARY", "EXPLANATION"))
        viewModel().connectionBatches.test {
            assertTrue(awaitItem().isEmpty())
            val batches = awaitItem()
            assertEquals(listOf(30L, 20L, 10L), batches.map { it.sourceCard.id })
            val sources = batches.filter { it.sourceCard.id != 10L }
            assertEquals(listOf("COMPLEMENTARY", "SIMILAR"), sources.map { it.items.single().group.name })
            sources.forEach {
                assertEquals(10L, it.items.single().candidateCard.id)
                assertEquals("Reason for source ${it.sourceCard.id}", it.items.single().reason)
                assertEquals("Learning benefit for source ${it.sourceCard.id}", it.items.single().benefit)
                assertEquals("Original note ${it.sourceCard.id}", it.sourceCard.userIntent)
            }
        }
    }

    @Test
    fun cardUpdatesRefreshBothSidesWithoutAConnectionTableChange() = runTest {
        store.put(card(10)); store.put(card(20))
        store.results.value = listOf(link(20, 10))
        viewModel().connectionBatches.test {
            awaitItem(); awaitItem()
            store.put(card(10).copy(title = "E = mc²", userIntent = "Updated personal note"))
            val updated = awaitItem().first { it.sourceCard.id == 20L }.items.single().candidateCard
            assertEquals("E = mc²", updated.title)
            assertEquals("Updated personal note", updated.userIntent)

            store.put(card(20).copy(title = "New source title"))
            assertEquals("New source title", awaitItem().first { it.sourceCard.id == 20L }.sourceCard.title)
        }
    }

    @Test
    fun sourceAndCandidateDeletionImmediatelyRemoveObsoleteConnections() = runTest {
        listOf(card(10), card(20), card(30)).forEach(store::put)
        store.results.value = listOf(link(20, 10), link(30, 10))
        viewModel().connectionBatches.test {
            awaitItem(); awaitItem()
            store.deleteCard(30)
            assertFalse(awaitItem().any { it.sourceCard.id == 30L })
            store.deleteCard(10)
            val remaining = awaitItem()
            assertEquals(listOf(20L), remaining.map { it.sourceCard.id })
            assertTrue(remaining.single().items.isEmpty())
        }
    }

    @Test
    fun completedEmptyPendingAndFailedBatchesRemainDistinct() = runTest {
        store.put(card(10)); store.put(card(20, "PENDING")); store.put(card(30, "FAILED"))
        viewModel().connectionBatches.test {
            awaitItem()
            val batches = awaitItem()
            assertEquals(listOf(ProcessingStatus.FAILED, ProcessingStatus.PENDING, ProcessingStatus.DONE), batches.map { it.status })
            assertTrue(batches.all { it.items.isEmpty() })
            store.resetFailed(30)
            assertEquals(ProcessingStatus.PENDING, awaitItem().first { it.sourceCard.id == 30L }.status)
        }
    }

    @Test
    fun invalidLegacyRowsCannotBeRenderedAsInventedConnections() = runTest {
        store.put(card(10)); store.put(card(20))
        val valid = link(20, 10)
        store.results.value = listOf(valid.copy(group = "INVALID"), valid.copy(relation = "INVALID"),
            valid.copy(confidence = Float.NaN), valid.copy(candidateCardId = 20), valid.copy(candidateCardId = 999),
            valid, valid.copy(group = "COMPLEMENTARY", relation = "EXPLANATION"))
        viewModel().connectionBatches.test {
            awaitItem()
            assertEquals(listOf(10L), awaitItem().first { it.sourceCard.id == 20L }.items.map { it.candidateCard.id })
        }
    }
}
