package com.remora

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.remora.capture.AppForegroundTracker
import com.remora.capture.CaptureForegroundService
import com.remora.capture.CapturePolicy
import com.remora.capture.NotificationHelper
import com.remora.capture.PendingCaptureStore
import com.remora.capture.ScreenshotObserver
import com.remora.data.local.RemoraDatabase
import com.remora.search.SemanticSearchEngine
import com.remora.ui.RemoraMainScreen
import com.remora.ui.consent.ConsentScreen
import com.remora.ui.consent.ConsentViewModel
import com.remora.ui.gallery.GalleryViewModel
import com.remora.ui.search.SearchViewModel
import com.remora.ui.settings.SettingsViewModel
import com.remora.ui.theme.RemoraTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.remora.ui.MainNavigationTab
import timber.log.Timber

class MainActivity : ComponentActivity() {

    private var initialTab by mutableStateOf(MainNavigationTab.TIMELINE)
    private var resurfacingLaunchTargetCardId by mutableStateOf<Long?>(null)

    override fun onStart() {
        super.onStart()
        AppForegroundTracker.isForeground = true
    }

    override fun onStop() {
        super.onStop()
        AppForegroundTracker.isForeground = false
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val mediaPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        val canReadImages = ContextCompat.checkSelfPermission(
            this, mediaPermission
        ) == PackageManager.PERMISSION_GRANTED

        // Notification permission is optional for monitoring. Denying it must not
        // prevent the foreground service and MediaStore observer from starting.
        if (canReadImages) {
            Timber.i("Image permission granted; starting CaptureForegroundService. Notification permission result: ${permissions[Manifest.permission.POST_NOTIFICATIONS]}")
            CaptureForegroundService.start(this)
        } else {
            Timber.w("Image/media permission denied; screenshot monitoring cannot start: $permissions")
        }
    }

    private fun getRequiredPermissions(): Array<String> {
        val list = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.READ_MEDIA_IMAGES)
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            list.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        return list.toTypedArray()
    }

    private fun startCaptureServiceIfPermitted() {
        val mediaPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        val canReadImages = ContextCompat.checkSelfPermission(
            this, mediaPermission
        ) == PackageManager.PERMISSION_GRANTED

        if (canReadImages) {
            // Monitoring depends on media access, not notification permission.
            // The OS may suppress user-facing notifications if POST_NOTIFICATIONS is denied.
            Timber.i("Image permission granted, starting CaptureForegroundService")
            CaptureForegroundService.start(this)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Timber.w("POST_NOTIFICATIONS is denied; screenshot monitoring runs but capture prompts may be hidden")
            }
        } else {
            val permissions = getRequiredPermissions()
            Timber.i("Requesting runtime permissions: ${permissions.joinToString()}")
            permissionLauncher.launch(permissions)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        handleIncomingCaptureIntent(intent)
        handleIncomingResurfacingIntent(intent)

        // Android 14+ Official Screen Capture Detection API
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            registerScreenCaptureCallback(mainExecutor) {
                Timber.i("ScreenCaptureCallback triggered by OS")
                lifecycleScope.launch(Dispatchers.IO) {
                    val db = RemoraDatabase.getInstance(applicationContext)
                    val setting = db.appSettingDao().getSetting()
                    if (setting?.consentGranted != true || CapturePolicy.isCaptureSuppressed(setting, System.currentTimeMillis())) {
                        Timber.d("ScreenCaptureCallback ignored: screenshot monitoring is suppressed or consent not granted.")
                        return@launch
                    }

                    // Small delay to allow MediaStore write completion
                    delay(500L)
                    val uri = ScreenshotObserver.queryLatestScreenshotUri(applicationContext, maxAgeSeconds = 15L)
                    if (uri != null) {
                        Timber.i("Found fresh screenshot uri from ScreenCaptureCallback: $uri")
                        PendingCaptureStore.getInstance(applicationContext).markPending(uri, prioritize = false)
                    }
                }
            }
        }

        val app = application as RemoraApp
        val db = RemoraDatabase.getInstance(this)
        val cardRepo = app.container.cardRepository
        val aiClient = app.container.aiGatewayClient
        val searchEngine = SemanticSearchEngine(db.memoryCardDao(), aiClient)

        val consentViewModel = ConsentViewModel(db.appSettingDao())
        val galleryViewModel = GalleryViewModel(
            repository = cardRepo,
            resurfacingResultDao = db.resurfacingResultDao(),
            memoryCardDao = db.memoryCardDao()
        )
        val searchViewModel = SearchViewModel(searchEngine)
        val settingsViewModel = SettingsViewModel(db.appSettingDao())

        setContent {
            RemoraTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val hasConsented by consentViewModel.hasConsented.collectAsState()

                    // Side-effect: When notification opens a specific card, focus that card's resurfacing results
                    LaunchedEffect(resurfacingLaunchTargetCardId) {
                        val targetCardId = resurfacingLaunchTargetCardId
                        if (targetCardId != null) {
                            galleryViewModel.selectSourceCard(targetCardId)
                            resurfacingLaunchTargetCardId = null
                        }
                    }

                    // Side-effect: Only start service outside composition when consented
                    LaunchedEffect(hasConsented) {
                        if (hasConsented) {
                            startCaptureServiceIfPermitted()
                        }
                    }

                    if (!hasConsented) {
                        ConsentScreen(
                            onConsentGranted = {
                                consentViewModel.grantConsent {
                                    startCaptureServiceIfPermitted()
                                }
                            }
                        )
                    } else {
                        RemoraMainScreen(
                            galleryViewModel = galleryViewModel,
                            searchViewModel = searchViewModel,
                            settingsViewModel = settingsViewModel,
                            initialTab = initialTab,
                            onTabSelected = { tab ->
                                if (tab != MainNavigationTab.RESURFACE) {
                                    galleryViewModel.selectSourceCard(null)
                                }
                                initialTab = tab
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingCaptureIntent(intent)
        handleIncomingResurfacingIntent(intent)
    }

    private fun handleIncomingCaptureIntent(intent: Intent?) {
        val uriStr = intent?.getStringExtra(NotificationHelper.EXTRA_IMAGE_URI)
        if (!uriStr.isNullOrBlank()) {
            intent.removeExtra(NotificationHelper.EXTRA_IMAGE_URI)
            intent.removeExtra(NotificationHelper.EXTRA_OPEN_SAVE_SHEET)
            NotificationHelper.dismissNotification(this)

            lifecycleScope.launch(Dispatchers.IO) {
                val db = RemoraDatabase.getInstance(applicationContext)
                val existingCount = db.memoryCardDao().countCardsByImageUri(uriStr)
                if (existingCount > 0) {
                    Timber.i("MainActivity: card already exists for uri $uriStr, skipping SavePromptSheet")
                    PendingCaptureStore.getInstance(applicationContext).resolve(uriStr)
                    withContext(Dispatchers.Main) {
                        initialTab = MainNavigationTab.TIMELINE
                    }
                } else {
                    Timber.i("MainActivity received intent with fresh capture uri: $uriStr")
                    PendingCaptureStore.getInstance(applicationContext).markPending(uriStr, prioritize = true)
                }
            }
        }
    }

    private fun handleIncomingResurfacingIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(NotificationHelper.EXTRA_OPEN_RESURFACING, false) == true) {
            val newCardId = intent.getLongExtra(NotificationHelper.EXTRA_NEW_CARD_ID, -1L)
            Timber.i("MainActivity received intent to open resurfacing tab for card #$newCardId")
            initialTab = MainNavigationTab.RESURFACE
            if (newCardId > 0L) {
                resurfacingLaunchTargetCardId = newCardId
            }
            intent.removeExtra(NotificationHelper.EXTRA_OPEN_RESURFACING)
            intent.removeExtra(NotificationHelper.EXTRA_NEW_CARD_ID)
        }
    }
}
