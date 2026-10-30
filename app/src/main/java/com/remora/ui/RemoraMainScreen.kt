package com.remora.ui

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.remora.RemoraApp
import com.remora.capture.CaptureEventManager
import com.remora.capture.CaptureForegroundService
import com.remora.capture.NotificationHelper
import com.remora.ui.capture.SavePromptSheet
import com.remora.ui.gallery.GalleryViewModel
import com.remora.ui.gallery.CardDetailDialog
import com.remora.ui.resurfacing.ResurfacingScreen
import com.remora.ui.search.SearchScreen
import com.remora.ui.search.SearchViewModel
import com.remora.ui.settings.SettingsScreen
import com.remora.ui.theme.RemoraBg
import com.remora.ui.theme.RemoraBlue
import com.remora.ui.timeline.TimelineScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

// Destinations map to the learner's jobs: keep, find, connect, control.
enum class MainNavigationTab { TIMELINE, SEARCH, RESURFACE, SETTINGS }

@Composable
fun RemoraMainScreen(
    galleryViewModel: GalleryViewModel,
    searchViewModel: SearchViewModel,
    initialCaptureUri: Uri? = null,
    modifier: Modifier = Modifier,
    onCaptureConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    val container = (context.applicationContext as RemoraApp).container
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var currentTab by rememberSaveable { mutableStateOf(MainNavigationTab.TIMELINE) }
    var activeCapture by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    val cards by galleryViewModel.cards.collectAsState()
    val selectedCard by galleryViewModel.selectedCard.collectAsState()
    val actionInProgress by galleryViewModel.actionInProgress.collectAsState()
    val actionError by galleryViewModel.actionError.collectAsState()
    val connections by galleryViewModel.connectionBatches.collectAsState()
    val query by searchViewModel.query.collectAsState()
    val searchState by searchViewModel.uiState.collectAsState()
    val searching by searchViewModel.isSearching.collectAsState()
    val recent by searchViewModel.recentQueries.collectAsState()
    val indexed by searchViewModel.indexedCount.collectAsState()

    LaunchedEffect(actionError) {
        actionError?.let { snackbar.showSnackbar(it) }
    }

    LaunchedEffect(Unit) {
        CaptureEventManager.screenshotEvents.collect { uri ->
            if (activeCapture == null) { activeCapture = uri.toString(); saveError = null }
        }
    }
    LaunchedEffect(initialCaptureUri) {
        initialCaptureUri?.let {
            activeCapture = it.toString()
            saveError = null
            onCaptureConsumed()
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = RemoraBg,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                val labels = listOf("Thư viện", "Tìm kiếm", "Gợi nhớ", "Cài đặt")
                val icons = listOf(Icons.AutoMirrored.Filled.List, Icons.Default.Search, Icons.Default.AutoAwesome, Icons.Default.Tune)
                MainNavigationTab.entries.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = currentTab == tab,
                        onClick = { currentTab = tab },
                        icon = { Icon(icons[index], null, Modifier.size(22.dp)) },
                        label = { Text(labels[index]) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = RemoraBlue)
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (currentTab) {
                MainNavigationTab.TIMELINE -> TimelineScreen(
                    cards = cards,
                    onRetryCard = { card -> galleryViewModel.retryCard(card.id, container.saveCoordinator::retry) },
                    onOpenCard = galleryViewModel::showCard,
                    onImportImage = { uri, _ -> activeCapture = uri.toString(); saveError = null },
                    onNavigateToSearch = { currentTab = MainNavigationTab.SEARCH }
                )
                MainNavigationTab.SEARCH -> SearchScreen(
                    query = query, uiState = searchState,
                    onQueryChanged = searchViewModel::onQueryChanged,
                    onOpenCard = galleryViewModel::showCard,
                    isSearching = searching, recentQueries = recent, indexedCount = indexed,
                    onSubmit = searchViewModel::onSearchSubmit
                )
                MainNavigationTab.RESURFACE -> ResurfacingScreen(
                    batches = connections, onOpenCard = galleryViewModel::showCard,
                    onRetryBatch = { id -> galleryViewModel.retryConnections(id, container.saveCoordinator::retryResurfacing) },
                    isRetrying = actionInProgress)
                MainNavigationTab.SETTINGS -> SettingsScreen(onClearAllCards = {
                    galleryViewModel.clearCards(clear = container.saveCoordinator::clear) {
                        scope.launch { snackbar.showSnackbar("Đã xóa thẻ và bản sao ảnh trong Remora") }
                    }
                }, isClearing = actionInProgress)
            }
        }
    }

    selectedCard?.let { card ->
        CardDetailDialog(
            card = card,
            onDismiss = galleryViewModel::dismissCard,
            onRetry = { galleryViewModel.retryCard(card.id, container.saveCoordinator::retry) },
            onDelete = { galleryViewModel.deleteCard(card.id, container.saveCoordinator::delete) },
            actionInProgress = actionInProgress,
            actionError = actionError
        )
    }

    activeCapture?.let { image ->
        SavePromptSheet(
            imageUri = Uri.parse(image), isSaving = saving, errorMessage = saveError,
            onSave = { note ->
                if (!saving) {
                    saving = true
                    saveError = null
                    scope.launch {
                        try {
                            val result = container.saveCoordinator.save(Uri.parse(image), note)
                            activeCapture = null
                            currentTab = MainNavigationTab.TIMELINE
                            NotificationHelper.dismissNotification(context)
                            snackbar.showSnackbar(if (result.duplicate) "Ảnh này đã có trong thư viện. Ghi chú cũ được giữ nguyên."
                                else "Đã lưu ảnh. Remora sẽ phân tích khi có kết nối AI.")
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (error: Exception) {
                            saveError = if (error.message == "IMAGE_TOO_LARGE") "Ảnh quá lớn. Hãy chọn ảnh nhỏ hơn 20 MB."
                                else "Chưa lưu được ảnh. Kiểm tra dung lượng hoặc chọn lại ảnh rồi thử lại."
                        } finally { saving = false }
                    }
                }
            },
            onDismiss = { if (!saving) { activeCapture = null; NotificationHelper.dismissNotification(context) } },
            onToggleNotifications = {
                scope.launch {
                    val dao = container.database.appSettingDao()
                    dao.getSetting()?.let { dao.insertOrUpdate(it.copy(notificationsEnabled = false)) }
                    activeCapture = null
                }
            },
            onPauseMonitoring = {
                scope.launch {
                    val dao = container.database.appSettingDao()
                    dao.getSetting()?.let { dao.insertOrUpdate(it.copy(paused = true)) }
                    CaptureForegroundService.stop(context)
                    activeCapture = null
                }
            }
        )
    }
}
