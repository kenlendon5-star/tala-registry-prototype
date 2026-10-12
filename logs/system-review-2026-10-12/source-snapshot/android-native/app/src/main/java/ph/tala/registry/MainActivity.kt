package ph.tala.registry

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import ph.tala.registry.data.DemoFixtures
import ph.tala.registry.data.RoomHouseholdRepository
import ph.tala.registry.data.local.RecordStore
import ph.tala.registry.data.local.RegistryDatabase
import ph.tala.registry.data.local.RoomRecordStore
import ph.tala.registry.ui.TalaApp
import ph.tala.registry.ui.theme.TalaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        enableEdgeToEdge()
        val store: RecordStore = RoomRecordStore(RegistryDatabase.open(this))
        val repository = RoomHouseholdRepository(store)
        // Fictional fixtures are explicit and optional; set to false for an empty registry.
        if (SEED_DEMO_RECORDS) lifecycleScope.launch { repository.seedMissing(DemoFixtures.records()) }
        setContent {
            TalaTheme {
                TalaApp(repository)
            }
        }
    }

    companion object {
        const val SEED_DEMO_RECORDS = true
    }
}
