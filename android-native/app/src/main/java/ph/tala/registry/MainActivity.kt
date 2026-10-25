package ph.tala.registry

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.launch
import ph.tala.registry.data.DemoFixtures
import ph.tala.registry.ui.account.AccountViewModel
import ph.tala.registry.ui.TalaApp
import ph.tala.registry.ui.theme.TalaTheme

class MainActivity : ComponentActivity() {
    private var pendingAuthIntent by mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingAuthIntent = intent
        enableEdgeToEdge()
        val dependencies = application as TalaApplication
        val repository = dependencies.repository
        // Fictional fixtures are explicit and optional; set to false for an empty registry.
        if (SEED_DEMO_RECORDS) lifecycleScope.launch { repository.seedMissing(DemoFixtures.records()) }
        val photos = dependencies.photos
        val profiles = dependencies.profiles
        setContent {
            TalaTheme {
                val account: AccountViewModel = viewModel(factory = viewModelFactory {
                    initializer { AccountViewModel(dependencies.accounts, profiles) }
                })
                val accountState by account.state.collectAsStateWithLifecycle()
                LaunchedEffect(pendingAuthIntent) {
                    pendingAuthIntent?.let(account::handleAuthCallback)
                    pendingAuthIntent = null
                }
                TalaApp(
                    repository,
                    photos,
                    profiles,
                    dependencies.resumes,
                    dependencies.cloudSync,
                    dependencies.backupImporter,
                    accountState,
                    account::signIn,
                    account::signUp,
                    account::resendConfirmation,
                    account::signOut,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingAuthIntent = intent
    }

    companion object {
        const val SEED_DEMO_RECORDS = true
    }
}
