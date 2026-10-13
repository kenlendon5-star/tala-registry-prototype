package ph.tala.registry

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import ph.tala.registry.data.DemoFixtures
import ph.tala.registry.ui.TalaApp
import ph.tala.registry.ui.theme.TalaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val dependencies = application as TalaApplication
        val repository = dependencies.repository
        // Fictional fixtures are explicit and optional; set to false for an empty registry.
        if (SEED_DEMO_RECORDS) lifecycleScope.launch { repository.seedMissing(DemoFixtures.records()) }
        val photos = dependencies.photos
        val profiles = dependencies.profiles
        setContent {
            TalaTheme {
                TalaApp(repository, photos, profiles)
            }
        }
    }

    companion object {
        const val SEED_DEMO_RECORDS = true
    }
}
