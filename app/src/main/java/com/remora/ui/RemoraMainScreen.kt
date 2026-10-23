package com.remora.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.remora.core.model.resurface.ResurfacedItem
import com.remora.ui.gallery.GalleryScreen
import com.remora.ui.gallery.GalleryViewModel
import com.remora.ui.resurfacing.ResurfacingBanner
import com.remora.ui.resurfacing.ResurfacingDetailDialog
import com.remora.ui.search.SearchScreen
import com.remora.ui.search.SearchViewModel

enum class NavigationTab {
    GALLERY,
    SEARCH
}

@Composable
fun RemoraMainScreen(
    galleryViewModel: GalleryViewModel,
    searchViewModel: SearchViewModel,
    resurfacedItems: List<ResurfacedItem> = emptyList(),
    modifier: Modifier = Modifier
) {
    var currentTab by remember { mutableStateOf(NavigationTab.GALLERY) }
    var showResurfacingDetail by remember { mutableStateOf(false) }

    val cards by galleryViewModel.cards.collectAsState()
    val roomResurfacedItems by galleryViewModel.resurfacedItems.collectAsState()
    val activeResurfacedItems = if (resurfacedItems.isNotEmpty()) resurfacedItems else roomResurfacedItems

    val searchQuery by searchViewModel.query.collectAsState()
    val searchUiState by searchViewModel.uiState.collectAsState()

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentTab == NavigationTab.GALLERY,
                    onClick = { currentTab = NavigationTab.GALLERY },
                    icon = { Icon(Icons.Default.Collections, contentDescription = "Kho thẻ nhớ") },
                    label = { Text("Kho thẻ nhớ") }
                )
                NavigationBarItem(
                    selected = currentTab == NavigationTab.SEARCH,
                    onClick = { currentTab = NavigationTab.SEARCH },
                    icon = { Icon(Icons.Default.Search, contentDescription = "Tìm kiếm") },
                    label = { Text("Tìm kiếm") }
                )
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (currentTab) {
                NavigationTab.GALLERY -> {
                    androidx.compose.foundation.layout.Column {
                        if (activeResurfacedItems.isNotEmpty()) {
                            ResurfacingBanner(
                                items = activeResurfacedItems,
                                onClick = { showResurfacingDetail = true }
                            )
                        }
                        GalleryScreen(
                            cards = cards,
                            onRetryCard = { card ->
                                galleryViewModel.retryCard(card) {
                                    // Trigger WorkManager retry
                                }
                            }
                        )
                    }
                }
                NavigationTab.SEARCH -> {
                    SearchScreen(
                        query = searchQuery,
                        uiState = searchUiState,
                        onQueryChanged = searchViewModel::onQueryChanged
                    )
                }
            }
        }
    }

    if (showResurfacingDetail && activeResurfacedItems.isNotEmpty()) {
        ResurfacingDetailDialog(
            items = activeResurfacedItems,
            onDismiss = { showResurfacingDetail = false }
        )
    }
}
