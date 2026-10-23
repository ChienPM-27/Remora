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
import com.remora.capture.CaptureEventManager
import com.remora.capture.CaptureForegroundService
import com.remora.capture.NotificationHelper
import com.remora.capture.ScreenshotObserver
import com.remora.data.local.RemoraDatabase
import com.remora.search.SemanticSearchEngine
import com.remora.ui.RemoraMainScreen
import com.remora.ui.consent.ConsentScreen
import com.remora.ui.consent.ConsentViewModel
import com.remora.ui.gallery.GalleryViewModel
import com.remora.ui.search.SearchViewModel
import com.remora.ui.theme.RemoraTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class MainActivity : ComponentActivity() {

    private var initialCaptureUri by mutableStateOf<Uri?>(null)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            Timber.i("All requested permissions granted by user, starting CaptureForegroundService")
            CaptureForegroundService.start(this)
        } else {
            Timber.w("Some permissions were denied: $permissions")
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
        val permissions = getRequiredPermissions()
        val allGranted = permissions.all { perm ->
            ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) {
            Timber.i("Permissions already granted, starting CaptureForegroundService")
            CaptureForegroundService.start(this)
        } else {
            Timber.i("Requesting runtime permissions: ${permissions.joinToString()}")
            permissionLauncher.launch(permissions)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        handleIncomingCaptureIntent(intent)

        // Android 14+ Official Screen Capture Detection API
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            registerScreenCaptureCallback(mainExecutor) {
                Timber.i("ScreenCaptureCallback triggered by OS")
                lifecycleScope.launch(Dispatchers.IO) {
                    // Small delay to allow MediaStore write completion
                    delay(500L)
                    val uri = ScreenshotObserver.queryLatestScreenshotUri(applicationContext)
                    if (uri != null) {
                        Timber.i("Found screenshot uri from ScreenCaptureCallback: $uri")
                        withContext(Dispatchers.Main) {
                            CaptureEventManager.emitScreenshot(uri)
                        }
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

        setContent {
            RemoraTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val hasConsented by consentViewModel.hasConsented.collectAsState()

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
                            initialCaptureUri = initialCaptureUri
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
    }

    private fun handleIncomingCaptureIntent(intent: Intent?) {
        val uriStr = intent?.getStringExtra(NotificationHelper.EXTRA_IMAGE_URI)
        if (!uriStr.isNullOrBlank()) {
            val uri = Uri.parse(uriStr)
            Timber.i("MainActivity received intent with capture uri: $uri")
            initialCaptureUri = uri
            CaptureEventManager.emitScreenshot(uri)
        }
    }
}
