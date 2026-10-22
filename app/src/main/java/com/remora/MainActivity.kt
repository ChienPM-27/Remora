package com.remora

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as RemoraApp
        val db = RemoraDatabase.getInstance(this)
        val cardRepo = app.container.cardRepository
        val aiClient = AiGatewayClient()
        val searchEngine = SemanticSearchEngine(db.memoryCardDao(), aiClient)

        val consentViewModel = ConsentViewModel(db.appSettingDao())
        val galleryViewModel = GalleryViewModel(cardRepo)
        val searchViewModel = SearchViewModel(searchEngine)

        // Seed demo cards in background
        lifecycleScope.launch(Dispatchers.IO) {
            DemoDataSeeder.seedDemoCardsIfEmpty(db)
        }

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val hasConsented by consentViewModel.hasConsented.collectAsState()

                    if (!hasConsented) {
                        ConsentScreen(
                            onConsentGranted = {
                                consentViewModel.grantConsent {
                                    CaptureForegroundService.start(this@MainActivity)
                                }
                            }
                        )
                    } else {
                        // Start screenshot observer background service
                        CaptureForegroundService.start(this@MainActivity)

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
