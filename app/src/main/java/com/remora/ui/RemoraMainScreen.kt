package com.remora.ui

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.remora.capture.CapturePolicy
import com.remora.capture.NotificationHelper
import com.remora.capture.PendingCaptureStore
import com.remora.core.model.entity.MemoryCard
import com.remora.core.model.entity.ProcessingStatus
import com.remora.core.model.resurface.ResurfacedItem
import com.remora.data.local.RemoraDatabase
import com.remora.data.local.entity.MemoryCardEntity
import com.remora.ui.capture.SavePromptSheet
import com.remora.ui.gallery.BulkImportItem
import com.remora.ui.gallery.BulkImportState
import com.remora.ui.gallery.BulkImportStatus
import com.remora.ui.gallery.GalleryViewModel
import com.remora.ui.resurfacing.ResurfacingScreen
import com.remora.ui.search.SearchScreen
import com.remora.ui.search.SearchViewModel
import com.remora.ui.settings.SettingsScreen
import com.remora.ui.theme.RemoraBg
import com.remora.ui.theme.RemoraBlue
import com.remora.ui.theme.RemoraBlueSoft
import com.remora.ui.theme.RemoraBorder
import com.remora.ui.theme.RemoraNavyDark
import com.remora.ui.theme.RemoraSubtleText
import com.remora.ui.timeline.TimelineScreen
import com.remora.util.HashUtil
import com.remora.util.ImageImporter
import com.remora.work.CardAnalyzeWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

import com.remora.ui.settings.SettingsViewModel

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
    settingsViewModel: SettingsViewModel = run {
        val context = LocalContext.current
        remember(context) { SettingsViewModel(RemoraDatabase.getInstance(context).appSettingDao()) }
    },
    initialTab: MainNavigationTab = MainNavigationTab.TIMELINE,
    onTabSelected: (MainNavigationTab) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val store = remember(context) { PendingCaptureStore.getInstance(context) }
    var currentTab by remember { mutableStateOf(initialTab) }
    var activePromptUri by remember { mutableStateOf<Uri?>(null) }
    var duplicateImport by remember { mutableStateOf<DuplicateImportPrompt?>(null) }
    var bulkPick by remember { mutableStateOf<List<Uri>?>(null) }
    val bulkState by galleryViewModel.bulkImportState.collectAsState()

    fun enqueueAnalysis(cardId: Long) {
        val request = OneTimeWorkRequestBuilder<CardAnalyzeWorker>()
            .setInputData(workDataOf(CardAnalyzeWorker.KEY_CARD_ID to cardId))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(
                androidx.work.BackoffPolicy.EXPONENTIAL,
                30,
                java.util.concurrent.TimeUnit.SECONDS
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            CardAnalyzeWorker.uniqueWorkName(cardId),
            androidx.work.ExistingWorkPolicy.REPLACE,
            request
        )
    }

    /** Copies the image, creates a PENDING card (hash-dedup aware) and enqueues analysis. */
    fun importImageNow(uri: Uri, userIntent: String?, imageHash: String?, force: Boolean) {
        scope.launch {
            val persistentUri = withContext(Dispatchers.IO) {
                try {
                    ImageImporter.copyUriToInternalStorage(context, uri)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to copy imported image")
                    null
                }
            }
            if (persistentUri == null) {
                Toast.makeText(context, "Không thể đọc ảnh đã chọn", Toast.LENGTH_SHORT).show()
                return@launch
            }
            galleryViewModel.importCard(
                imageUri = persistentUri,
                userIntent = userIntent,
                imageHash = imageHash,
                forceInsert = force,
                onCardCreated = { cardId ->
                    enqueueAnalysis(cardId)
                    scope.launch {
                        Toast.makeText(context, "Đã thêm thẻ! AI đang xử lý...", Toast.LENGTH_SHORT).show()
                    }
                },
                onDuplicateFound = {
                    scope.launch {
                        Toast.makeText(context, "Ảnh này đã có trong Remora", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }

    /** Imports one batch image: hash -> dedup -> copy -> PENDING card -> enqueue. Runs on IO. */
    suspend fun bulkImportOne(uriString: String): BulkImportStatus = withContext(Dispatchers.IO) {
        val uri = Uri.parse(uriString)
        val db = RemoraDatabase.getInstance(context)
        val imageHash = try {
            context.contentResolver.openInputStream(uri)?.use { HashUtil.sha256(it) }
        } catch (e: Exception) {
            Timber.w(e, "Bulk import: failed to hash image")
            null
        }
        if (imageHash != null && db.memoryCardDao().getCardByImageHash(imageHash) != null) {
            return@withContext BulkImportStatus.DUPLICATE
        }
        val persistentUri = try {
            ImageImporter.copyUriToInternalStorage(context, uri)
        } catch (e: Exception) {
            Timber.w(e, "Bulk import: failed to copy image")
            null
        } ?: return@withContext BulkImportStatus.FAILED
        val card = MemoryCard(
            imageUri = persistentUri,
            createdAt = System.currentTimeMillis(),
            title = "Đang xử lý...",
            status = ProcessingStatus.PENDING,
            imageHash = imageHash
        )
        val newId = db.memoryCardDao().insertCardIfHashAbsent(MemoryCardEntity.fromDomainModel(card))
        if (newId < 0) {
            BulkImportStatus.DUPLICATE
        } else {
            enqueueAnalysis(newId)
            BulkImportStatus.IMPORTED
        }
    }

    // Observe pending captures reactively only when RESUMED
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            store.prune()
            store.pending.collect { pendingList ->
                // If active prompt is no longer in pending list (e.g. resolved externally), clear it
                if (activePromptUri != null && pendingList.none { it.uriString == activePromptUri.toString() }) {
                    activePromptUri = null
                }
                // If no sheet currently active, inspect pending queue for the next valid candidate
                if (activePromptUri == null && pendingList.isNotEmpty()) {
                    val db = RemoraDatabase.getInstance(context)
                    for (item in pendingList) {
                        val count = withContext(Dispatchers.IO) {
                            db.memoryCardDao().countCardsByImageUri(item.uriString)
                        }
                        val uri = Uri.parse(item.uriString)
                        val readable = withContext(Dispatchers.IO) {
                            try {
                                context.contentResolver.openInputStream(uri)?.use { true } ?: false
                            } catch (e: Exception) {
                                false
                            }
                        }
                        if (count > 0 || !readable) {
                            Timber.i("Pruning invalid/existing pending capture: ${item.uriString} (count=$count, readable=$readable)")
                            store.resolve(item.uriString)
                        } else {
                            activePromptUri = uri
                            break
                        }
                    }
                }
            }
        }
    }

    // Keep currentTab updated if MainActivity passes a new initialTab via Intent (e.g. from notification)
    LaunchedEffect(initialTab) {
        currentTab = initialTab
    }

    val cards by galleryViewModel.cards.collectAsState()
    val resurfacedState by galleryViewModel.resurfacedState.collectAsState()
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
                    onClick = {
                        currentTab = MainNavigationTab.TIMELINE
                        onTabSelected(MainNavigationTab.TIMELINE)
                    },
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
                    onClick = {
                        currentTab = MainNavigationTab.SEARCH
                        onTabSelected(MainNavigationTab.SEARCH)
                    },
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
                    onClick = {
                        currentTab = MainNavigationTab.RESURFACE
                        onTabSelected(MainNavigationTab.RESURFACE)
                    },
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
                    onClick = {
                        currentTab = MainNavigationTab.SETTINGS
                        onTabSelected(MainNavigationTab.SETTINGS)
                    },
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
                                enqueueAnalysis(cardId)
                            }
                        },
                        onImportImage = { uri, userIntent ->
                            scope.launch {
                                // Hash the ORIGINAL bytes before copying: an identical image
                                // must be detected no matter which URI it arrives from.
                                val imageHash = withContext(Dispatchers.IO) {
                                    try {
                                        context.contentResolver.openInputStream(uri)
                                            ?.use { HashUtil.sha256(it) }
                                    } catch (e: Exception) {
                                        Timber.w(e, "Failed to hash import image")
                                        null
                                    }
                                }
                                val existing = imageHash?.let {
                                    withContext(Dispatchers.IO) {
                                        RemoraDatabase.getInstance(context).memoryCardDao().getCardByImageHash(it)
                                    }
                                }
                                if (existing != null) {
                                    duplicateImport = DuplicateImportPrompt(uri, userIntent, imageHash, existing.toDomainModel())
                                } else {
                                    importImageNow(uri, userIntent, imageHash, force = false)
                                }
                            }
                        },
                        onBulkImport = { uris -> bulkPick = uris },
                        onNavigateToSearch = {
                            currentTab = MainNavigationTab.SEARCH
                            onTabSelected(MainNavigationTab.SEARCH)
                        }
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
                        items = resurfacedState.items,
                        sourceCard = resurfacedState.sourceCard,
                        latestCard = cards.firstOrNull()
                    )
                }

                MainNavigationTab.SETTINGS -> {
                    SettingsScreen(
                        viewModel = settingsViewModel,
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

    // Modal Save Prompt Sheet (04-save.png) when a pending capture is ready
    activePromptUri?.let { uri ->
        SavePromptSheet(
            imageUri = uri,
            onSave = { intentText ->
                NotificationHelper.dismissNotification(context)
                store.resolve(uri)
                activePromptUri = null

                val db = RemoraDatabase.getInstance(context)
                scope.launch(Dispatchers.IO) {
                    val count = db.memoryCardDao().countCardsByImageUri(uri.toString())
                    if (count > 0) {
                        Timber.i("SavePromptSheet: card already exists for $uri, skipping duplicate insert")
                    } else {
                        val imageHash = try {
                            context.contentResolver.openInputStream(uri)?.use { HashUtil.sha256(it) }
                        } catch (e: Exception) {
                            Timber.w(e, "Failed to hash captured image")
                            null
                        }
                        val card = MemoryCard(
                            imageUri = uri.toString(),
                            createdAt = System.currentTimeMillis(),
                            userIntent = intentText.trim().ifBlank { null },
                            title = "Đang xử lý...",
                            status = ProcessingStatus.PENDING,
                            imageHash = imageHash
                        )
                        // Byte-identical images keep the FIRST card; nothing is merged or deleted.
                        val newId = db.memoryCardDao().insertCardIfHashAbsent(MemoryCardEntity.fromDomainModel(card))
                        if (newId < 0) {
                            Timber.i("SavePromptSheet: byte-identical image of card #${-newId}; skipping duplicate insert")
                        } else {
                            Timber.i("Saved card #$newId from SavePromptSheet into Room SQLite")
                            enqueueAnalysis(newId)
                        }
                    }
                }
                currentTab = MainNavigationTab.TIMELINE
                onTabSelected(MainNavigationTab.TIMELINE)
            },
            onDismiss = {
                NotificationHelper.dismissNotification(context)
                store.resolve(uri)
                activePromptUri = null
            },
            onMute1Hour = {
                NotificationHelper.dismissNotification(context)
                store.resolve(uri)
                activePromptUri = null
                scope.launch(Dispatchers.IO) {
                    val db = RemoraDatabase.getInstance(context)
                    CapturePolicy.applyCaptureAction(
                        NotificationHelper.ACTION_MUTE_1H,
                        db.appSettingDao(),
                        System.currentTimeMillis()
                    )
                }
                Toast.makeText(context, "Đã tắt nhận diện ảnh chụp trong 1 giờ", Toast.LENGTH_SHORT).show()
            },
            onPauseMonitoring = {
                NotificationHelper.dismissNotification(context)
                store.resolve(uri)
                activePromptUri = null
                scope.launch(Dispatchers.IO) {
                    val db = RemoraDatabase.getInstance(context)
                    CapturePolicy.applyCaptureAction(
                        NotificationHelper.ACTION_PAUSE,
                        db.appSettingDao(),
                        System.currentTimeMillis()
                    )
                }
                Toast.makeText(context, "Đã tạm dừng nhận diện ảnh chụp", Toast.LENGTH_SHORT).show()
            }
        )
    }

    duplicateImport?.let { prompt ->
        DuplicateCardDialog(
            existingTitle = prompt.existing.title.ifBlank { "Đang xử lý..." },
            onUseExisting = {
                duplicateImport = null
                Toast.makeText(context, "Đã dùng thẻ có sẵn trong thư viện", Toast.LENGTH_SHORT).show()
            },
            onSaveSeparate = {
                duplicateImport = null
                importImageNow(prompt.uri, prompt.userIntent, prompt.imageHash, force = true)
            },
            onDismiss = { duplicateImport = null }
        )
    }

    // Bulk import: explicit confirmation gate, then a per-image progress dialog.
    bulkPick?.let { uris ->
        BulkImportConfirmDialog(
            count = uris.size,
            onStart = {
                galleryViewModel.bulkImport(uris.map { it.toString() }) { bulkImportOne(it) }
                bulkPick = null
            },
            onDismiss = { bulkPick = null }
        )
    }

    if (bulkState.active) {
        BulkImportProgressDialog(
            state = bulkState,
            onRetryItem = { position ->
                galleryViewModel.retryBulkItem(position) { bulkImportOne(it) }
            },
            onRetryAllFailed = {
                galleryViewModel.retryAllFailed { bulkImportOne(it) }
            },
            onClose = { galleryViewModel.clearBulkImport() }
        )
    }
}

/** A gallery import whose SHA-256 matched an existing card; the user must choose (FR-026). */
private data class DuplicateImportPrompt(
    val uri: Uri,
    val userIntent: String?,
    val imageHash: String?,
    val existing: MemoryCard
)

@Composable
private fun DuplicateCardDialog(
    existingTitle: String,
    onUseExisting: () -> Unit,
    onSaveSeparate: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Ảnh này đã có trong Remora",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = RemoraNavyDark
            )
        },
        text = {
            Text(
                text = "Ảnh bạn chọn trùng nội dung với thẻ \"${existingTitle.take(60)}\". Bạn muốn xử lý thế nào?",
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = RemoraNavyDark.copy(alpha = 0.8f)
            )
        },
        confirmButton = {
            Button(
                onClick = onUseExisting,
                shape = RoundedCornerShape(percent = 50),
                colors = ButtonDefaults.buttonColors(containerColor = RemoraNavyDark)
            ) {
                Text("Dùng thẻ đã có", fontSize = 13.sp, color = Color.White)
            }
        },
        dismissButton = {
            androidx.compose.foundation.layout.Row {
                TextButton(onClick = onSaveSeparate) {
                    Text("Lưu thẻ riêng", fontSize = 13.sp, color = RemoraBlue, fontWeight = FontWeight.SemiBold)
                }
                TextButton(onClick = onDismiss) {
                    Text("Hủy", fontSize = 13.sp, color = RemoraSubtleText)
                }
            }
        },
        containerColor = Color.White,
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
private fun BulkImportConfirmDialog(
    count: Int,
    onStart: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Nhập $count ảnh vào Remora?",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = RemoraNavyDark
            )
        },
        text = {
            Text(
                text = "Mỗi ảnh sẽ thành một thẻ và được gửi AI phân tích, theo quyền riêng tư bạn đã cấp. " +
                    "Ảnh trùng nội dung với thẻ đã có sẽ được bỏ qua.",
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = RemoraNavyDark.copy(alpha = 0.8f)
            )
        },
        confirmButton = {
            Button(
                onClick = onStart,
                shape = RoundedCornerShape(percent = 50),
                colors = ButtonDefaults.buttonColors(containerColor = RemoraNavyDark)
            ) {
                Text("Bắt đầu nhập", fontSize = 13.sp, color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy", fontSize = 13.sp, color = RemoraSubtleText)
            }
        },
        containerColor = Color.White,
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
private fun BulkImportProgressDialog(
    state: BulkImportState,
    onRetryItem: (Int) -> Unit,
    onRetryAllFailed: () -> Unit,
    onClose: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!state.running) onClose() },
        title = {
            Text(
                text = if (state.running) "Đang nhập ảnh ${minOf(state.done + 1, state.total)}/${state.total}"
                else "Đã xử lý ${state.done}/${state.total} ảnh",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = RemoraNavyDark
            )
        },
        text = {
            androidx.compose.foundation.layout.Column {
                LinearProgressIndicator(
                    progress = { if (state.total == 0) 0f else state.done.toFloat() / state.total },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp),
                    color = RemoraBlue,
                    trackColor = RemoraBlueSoft
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(state.items) { position, item ->
                        BulkImportRow(
                            item = item,
                            retryEnabled = !state.running,
                            onRetry = { onRetryItem(position) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (!state.running && state.failures > 0) {
                Button(
                    onClick = onRetryAllFailed,
                    shape = RoundedCornerShape(percent = 50),
                    colors = ButtonDefaults.buttonColors(containerColor = RemoraNavyDark)
                ) {
                    Text("Thử lại ${state.failures} ảnh lỗi", fontSize = 13.sp, color = Color.White)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onClose) {
                Text("Đóng", fontSize = 13.sp, color = RemoraBlue, fontWeight = FontWeight.SemiBold)
            }
        },
        containerColor = Color.White,
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
private fun BulkImportRow(
    item: BulkImportItem,
    retryEnabled: Boolean,
    onRetry: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        val labelAndTint = when (item.status) {
            BulkImportStatus.QUEUED -> "đang chờ" to RemoraSubtleText
            BulkImportStatus.RUNNING -> "đang xử lý" to RemoraBlue
            BulkImportStatus.IMPORTED -> "đã nhập" to RemoraBlue
            BulkImportStatus.DUPLICATE -> "trùng — đã bỏ qua" to RemoraSubtleText
            BulkImportStatus.FAILED -> "không đọc được ảnh" to Color(0xFFEF4444)
        }
        when (item.status) {
            BulkImportStatus.RUNNING -> CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = RemoraBlue
            )
            BulkImportStatus.IMPORTED -> Icon(
                Icons.Outlined.CheckCircle, contentDescription = "đã nhập",
                tint = RemoraBlue, modifier = Modifier.size(18.dp)
            )
            BulkImportStatus.DUPLICATE -> Icon(
                Icons.Outlined.ContentCopy, contentDescription = "trùng, đã bỏ qua",
                tint = RemoraSubtleText, modifier = Modifier.size(18.dp)
            )
            BulkImportStatus.FAILED -> Icon(
                Icons.Outlined.ErrorOutline, contentDescription = "lỗi",
                tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp)
            )
            BulkImportStatus.QUEUED -> Icon(
                Icons.Outlined.Schedule, contentDescription = "đang chờ",
                tint = RemoraSubtleText, modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "Ảnh ${item.number}",
            fontSize = 13.sp,
            color = RemoraNavyDark,
            modifier = Modifier.weight(1f)
        )
        Text(text = labelAndTint.first, fontSize = 12.sp, color = labelAndTint.second)
        if (item.status == BulkImportStatus.FAILED && retryEnabled) {
            Spacer(modifier = Modifier.width(6.dp))
            TextButton(onClick = onRetry, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)) {
                Text("Thử lại", fontSize = 12.sp, color = RemoraBlue, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
