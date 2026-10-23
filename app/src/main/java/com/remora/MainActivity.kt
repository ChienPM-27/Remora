package com.remora

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.remora.ai.AiGatewayClient
import com.remora.capture.CaptureForegroundService
import com.remora.data.local.RemoraDatabase
import com.remora.data.seed.DemoDataSeeder
import com.remora.search.SemanticSearchEngine
import com.remora.ui.RemoraMainScreen
import com.remora.ui.consent.ConsentScreen
import com.remora.ui.consent.ConsentViewModel
import com.remora.ui.gallery.GalleryViewModel
import com.remora.ui.search.SearchViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

class MainActivity : ComponentActivity() {

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

        // Seed demo cards in background
        lifecycleScope.launch(Dispatchers.IO) {
            DemoDataSeeder.seedDemoCardsIfEmpty(db)
        }

        setContent {
            MaterialTheme {
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
                            searchViewModel = searchViewModel
                        )
                    }
                }
            }
        }
    }
}
