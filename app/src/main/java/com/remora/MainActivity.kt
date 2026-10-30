package com.remora

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.remora.capture.CaptureForegroundService
import com.remora.capture.CapturePermissions
import com.remora.capture.NotificationHelper
import com.remora.search.SemanticSearchEngine
import com.remora.ui.RemoraMainScreen
import com.remora.ui.consent.ConsentScreen
import com.remora.ui.consent.ConsentViewModel
import com.remora.ui.gallery.GalleryViewModel
import com.remora.ui.search.SearchViewModel
import com.remora.ui.theme.RemoraTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var initialCaptureUri by mutableStateOf<Uri?>(null)

    private fun <T : ViewModel> ownedViewModel(type: Class<T>, create: () -> T): T =
        ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <VM : ViewModel> create(modelClass: Class<VM>): VM = create() as VM
        })[type]

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIncomingCaptureIntent(intent)
        val container = (application as RemoraApp).container
        val db = container.database
        val consent = ownedViewModel(ConsentViewModel::class.java) { ConsentViewModel(db.appSettingDao()) }
        val gallery = ownedViewModel(GalleryViewModel::class.java) {
            GalleryViewModel(container.cardRepository, db.resurfacingResultDao(), db.memoryCardDao())
        }
        val search = ownedViewModel(SearchViewModel::class.java) {
            SearchViewModel(SemanticSearchEngine(db.memoryCardDao(), container.aiGatewayClient))
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                db.appSettingDao().getSettingFlow().collect { settings ->
                    if (settings?.consentGranted == true && !settings.paused && CapturePermissions.canReadImages(this@MainActivity)) {
                        CaptureForegroundService.start(this@MainActivity)
                    } else CaptureForegroundService.stop(this@MainActivity)
                }
            }
        }

        setContent {
            RemoraTheme {
                Surface(Modifier.fillMaxSize()) {
                    val accepted by consent.hasConsented.collectAsState()
                    when (accepted) {
                        null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        false -> ConsentScreen(onConsentGranted = { consent.grantConsent {} })
                        true -> RemoraMainScreen(
                            galleryViewModel = gallery, searchViewModel = search,
                            initialCaptureUri = initialCaptureUri,
                            onCaptureConsumed = { initialCaptureUri = null }
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
        intent?.getStringExtra(NotificationHelper.EXTRA_IMAGE_URI)?.let { value ->
            // Notification intents are consumed once, including across activity recreation.
            initialCaptureUri = Uri.parse(value)
            intent.removeExtra(NotificationHelper.EXTRA_IMAGE_URI)
        }
    }
}
