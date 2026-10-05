package ph.tala.registry

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ph.tala.registry.data.DemoHouseholdRepository
import ph.tala.registry.ui.TalaApp
import ph.tala.registry.ui.theme.TalaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TalaTheme {
                TalaApp(repository)
            }
        }
    }

    companion object {
        private val repository = DemoHouseholdRepository()
    }
}
