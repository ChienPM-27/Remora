package com.remora.ui

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.remora.capture.CaptureEventManager
import com.remora.capture.CapturePolicy
import com.remora.capture.NotificationHelper
import com.remora.core.model.entity.MemoryCard
import com.remora.core.model.entity.ProcessingStatus
import com.remora.core.model.resurface.ResurfacedItem
import com.remora.data.local.RemoraDatabase
import com.remora.data.local.entity.MemoryCardEntity
import com.remora.ui.capture.SavePromptSheet
import com.remora.ui.gallery.GalleryViewModel
import com.remora.ui.resurfacing.ResurfacingScreen
import com.remora.ui.search.SearchScreen
import com.remora.ui.search.SearchViewModel
import com.remora.ui.settings.SettingsScreen
import com.remora.ui.theme.RemoraBg
import com.remora.ui.theme.RemoraBlue
import com.remora.ui.theme.RemoraBorder
import com.remora.ui.theme.RemoraNavyDark
import com.remora.ui.theme.RemoraSubtleText
import com.remora.ui.timeline.TimelineScreen
import com.remora.util.ImageImporter
import com.remora.work.CardAnalyzeWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

enum class MainNavigationTab {
    TIMELINE,
    SEARCH,
    RESURFACE,
    SETTINGS
}

@Composable
fun RemoraMainScreen(
    galleryViewModel: GalleryViewModel,
    searchViewModel: SearchViewModel,
    initialCaptureUri: Uri? = null,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var currentTab by remember { mutableStateOf(MainNavigationTab.TIMELINE) }
    var activeCaptureUri by remember { mutableStateOf<Uri?>(initialCaptureUri) }
    val context = LocalContext.current

    // Observe incoming screenshot events emitted by ContentObserver or ScreenCaptureCallback
    LaunchedEffect(Unit) {
        CaptureEventManager.screenshotEvents.collect { uri ->
            Timber.i("Foreground received screenshot event: $uri -> opening SavePromptSheet")
            activeCaptureUri = uri
        }
    }

    // Keep activeCaptureUri updated if MainActivity passes a new one via Intent
    LaunchedEffect(initialCaptureUri) {
        if (initialCaptureUri != null) {
            activeCaptureUri = initialCaptureUri
        }
    }

    val cards by galleryViewModel.cards.collectAsState()
    val roomResurfacedItems by galleryViewModel.resurfacedItems.collectAsState()
    val searchQuery by searchViewModel.query.collectAsState()
    val searchUiState by searchViewModel.uiState.collectAsState()
    val searchIsSearching by searchViewModel.isSearching.collectAsState()
    val searchRecent by searchViewModel.recentQueries.collectAsState()
    val searchIndexedCount by searchViewModel.indexedCount.collectAsState()

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = Color.White,
                tonalElevation = 4.dp,
                modifier = Modifier.background(Color.White)
            ) {
                // 1. Tab: dòng thời gian (from 01-timeline.png)
                NavigationBarItem(
                    selected = currentTab == MainNavigationTab.TIMELINE,
                    onClick = { currentTab = MainNavigationTab.TIMELINE },
                    icon = {
                        Icon(
                            imageVector = if (currentTab == MainNavigationTab.TIMELINE) Icons.Filled.FormatListBulleted else Icons.Outlined.FormatListBulleted,
                            contentDescription = "dòng thời gian",
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    label = {
                        Text(
                            text = "dòng thời gian",
                            fontSize = 11.sp,
                            color = if (currentTab == MainNavigationTab.TIMELINE) RemoraBlue else RemoraSubtleText
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = RemoraBlue,
                        unselectedIconColor = RemoraSubtleText,
                        indicatorColor = Color.Transparent
                    )
                )

                // 2. Tab: tìm (from 02-search.png)
                NavigationBarItem(
                    selected = currentTab == MainNavigationTab.SEARCH,
                    onClick = { currentTab = MainNavigationTab.SEARCH },
                    icon = {
                        Icon(
                            imageVector = if (currentTab == MainNavigationTab.SEARCH) Icons.Filled.Search else Icons.Outlined.Search,
                            contentDescription = "tìm",
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    label = {
                        Text(
                            text = "tìm",
                            fontSize = 11.sp,
                            color = if (currentTab == MainNavigationTab.SEARCH) RemoraBlue else RemoraSubtleText
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = RemoraBlue,
                        unselectedIconColor = RemoraSubtleText,
                        indicatorColor = Color.Transparent
                    )
                )

                // 3. Tab: gợi nhớ (from 03-resurface.png)
                NavigationBarItem(
                    selected = currentTab == MainNavigationTab.RESURFACE,
                    onClick = { currentTab = MainNavigationTab.RESURFACE },
                    icon = {
                        Icon(
                            imageVector = if (currentTab == MainNavigationTab.RESURFACE) Icons.Filled.AutoAwesome else Icons.Outlined.AutoAwesome,
                            contentDescription = "gợi nhớ",
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    label = {
                        Text(
                            text = "gợi nhớ",
                            fontSize = 11.sp,
                            color = if (currentTab == MainNavigationTab.RESURFACE) RemoraBlue else RemoraSubtleText
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = RemoraBlue,
                        unselectedIconColor = RemoraSubtleText,
                        indicatorColor = Color.Transparent
                    )
                )

                // 4. Tab: cài đặt (from mockups bottom bar)
                NavigationBarItem(
                    selected = currentTab == MainNavigationTab.SETTINGS,
                    onClick = { currentTab = MainNavigationTab.SETTINGS },
                    icon = {
                        Icon(
                            imageVector = if (currentTab == MainNavigationTab.SETTINGS) Icons.Filled.Tune else Icons.Outlined.Tune,
                            contentDescription = "cài đặt",
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    label = {
                        Text(
                            text = "cài đặt",
                            fontSize = 11.sp,
                            color = if (currentTab == MainNavigationTab.SETTINGS) RemoraBlue else RemoraSubtleText
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = RemoraBlue,
                        unselectedIconColor = RemoraSubtleText,
                        indicatorColor = Color.Transparent
                    )
                )
            }
        },
        containerColor = RemoraBg,
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                MainNavigationTab.TIMELINE -> {
                    TimelineScreen(
                        cards = cards,
                        onRetryCard = { card ->
                            galleryViewModel.retryCard(card) { cardId ->
                                val request = OneTimeWorkRequestBuilder<CardAnalyzeWorker>()
                                    .setInputData(workDataOf(CardAnalyzeWorker.KEY_CARD_ID to cardId))
                                    .setConstraints(
                                        Constraints.Builder()
                                            .setRequiredNetworkType(NetworkType.CONNECTED)
                                            .build()
                                    )
                                    .build()
                                WorkManager.getInstance(context).enqueue(request)
                            }
                        },
                        onImportImage = { uri, userIntent ->
                            val persistentUri = try {
                                ImageImporter.copyUriToInternalStorage(context, uri)
                            } catch (e: Exception) {
                                Timber.e(e, "Failed to copy imported image")
                                null
                            }
                            if (persistentUri != null) {
                                galleryViewModel.importCard(persistentUri, userIntent) { cardId ->
                                    val request = OneTimeWorkRequestBuilder<CardAnalyzeWorker>()
                                        .setInputData(workDataOf(CardAnalyzeWorker.KEY_CARD_ID to cardId))
                                        .setConstraints(
                                            Constraints.Builder()
                                                .setRequiredNetworkType(NetworkType.CONNECTED)
                                                .build()
                                        )
                                        .build()
                                    WorkManager.getInstance(context).enqueue(request)
                                    Toast.makeText(context, "Đã thêm thẻ! AI đang xử lý...", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(context, "Không thể đọc ảnh đã chọn", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onNavigateToSearch = { currentTab = MainNavigationTab.SEARCH }
                    )
                }

                MainNavigationTab.SEARCH -> {
                    SearchScreen(
                        query = searchQuery,
                        uiState = searchUiState,
                        onQueryChanged = searchViewModel::onQueryChanged,
                        isSearching = searchIsSearching,
                        recentQueries = searchRecent,
                        indexedCount = searchIndexedCount,
                        onSubmit = searchViewModel::onSearchSubmit
                    )
                }

                MainNavigationTab.RESURFACE -> {
                    ResurfacingScreen(
                        items = roomResurfacedItems,
                        latestCard = cards.firstOrNull()
                    )
                }

                MainNavigationTab.SETTINGS -> {
                    SettingsScreen(
                        onClearAllCards = {
                            scope.launch(Dispatchers.IO) {
                                val db = RemoraDatabase.getInstance(context)
                                db.memoryCardDao().deleteAllCards()
                                db.resurfacingResultDao().deleteAllResults()
                            }
                        }
                    )
                }
            }
        }
    }

    // Modal Save Prompt Sheet (04-save.png) when a screenshot is detected
    activeCaptureUri?.let { uri ->
        SavePromptSheet(
            imageUri = uri,
            onSave = { intentText ->
                NotificationHelper.dismissNotification(context)
                val db = RemoraDatabase.getInstance(context)
                scope.launch(Dispatchers.IO) {
                    val card = MemoryCard(
                        imageUri = uri.toString(),
                        createdAt = System.currentTimeMillis(),
                        userIntent = intentText.ifBlank { "ôn thi giữa kỳ" },
                        title = "Đang xử lý...",
                        status = ProcessingStatus.PENDING
                    )
                    val newId = db.memoryCardDao().insertCard(MemoryCardEntity.fromDomainModel(card))
                    Timber.i("Saved card #$newId from SavePromptSheet into Room SQLite")

                    val workRequest = OneTimeWorkRequestBuilder<CardAnalyzeWorker>()
                        .setConstraints(
                            Constraints.Builder()
                                .setRequiredNetworkType(NetworkType.CONNECTED)
                                .build()
                        )
                        .setInputData(workDataOf(CardAnalyzeWorker.KEY_CARD_ID to newId))
                        .build()
                    WorkManager.getInstance(context).enqueue(workRequest)
                }
                activeCaptureUri = null
                currentTab = MainNavigationTab.TIMELINE
            },
            onDismiss = {
                NotificationHelper.dismissNotification(context)
                activeCaptureUri = null
            },
            onMute1Hour = {
                NotificationHelper.dismissNotification(context)
                scope.launch(Dispatchers.IO) {
                    val db = RemoraDatabase.getInstance(context)
                    CapturePolicy.applyCaptureAction(
                        NotificationHelper.ACTION_MUTE_1H,
                        db.appSettingDao(),
                        System.currentTimeMillis()
                    )
                }
                Toast.makeText(context, "Đã tắt nhận diện ảnh chụp trong 1 giờ", Toast.LENGTH_SHORT).show()
                activeCaptureUri = null
            },
            onPauseMonitoring = {
                NotificationHelper.dismissNotification(context)
                scope.launch(Dispatchers.IO) {
                    val db = RemoraDatabase.getInstance(context)
                    CapturePolicy.applyCaptureAction(
                        NotificationHelper.ACTION_PAUSE,
                        db.appSettingDao(),
                        System.currentTimeMillis()
                    )
                }
                Toast.makeText(context, "Đã tạm dừng nhận diện ảnh chụp", Toast.LENGTH_SHORT).show()
                activeCaptureUri = null
            }
        )
    }
}
