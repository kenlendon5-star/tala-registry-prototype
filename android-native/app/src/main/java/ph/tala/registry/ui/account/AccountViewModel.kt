package ph.tala.registry.ui.account

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import ph.tala.registry.data.EnumeratorProfileStore
import ph.tala.registry.data.cloud.AccountRepository
import ph.tala.registry.data.cloud.AccountResult
import ph.tala.registry.domain.model.EnumeratorProfile

data class AccountUiState(
    val checking: Boolean = true,
    val busy: Boolean = false,
    val signedInEmail: String? = null,
    val message: String? = null,
    val messageIsError: Boolean = false,
    val pendingConfirmationEmail: String? = null,
)

class AccountViewModel(
    private val accounts: AccountRepository,
    private val profiles: EnumeratorProfileStore,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val mutableState = MutableStateFlow(AccountUiState())
    val state = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            val account = withContext(ioDispatcher) {
                withTimeoutOrNull(SESSION_LOOKUP_TIMEOUT_MILLIS) { accounts.currentAccount() }
            }
            if (account != null) hydrateCloudProfile()
            mutableState.value = AccountUiState(checking = false, signedInEmail = account?.email)
        }
    }

    fun signIn(email: String, password: String) = submit {
        val normalizedEmail = email.trim()
        AccountInputRules.signInProblem(normalizedEmail, password)?.let { return@submit AccountResult.Failed(it) }
        accounts.signIn(normalizedEmail, password).also { if (it is AccountResult.SignedIn) hydrateCloudProfile() }
    }

    fun signUp(email: String, password: String, confirmPassword: String, displayName: String, barangay: String) = submit {
        val normalizedEmail = email.trim()
        val normalizedName = displayName.trim()
        val normalizedBarangay = barangay.trim()
        AccountInputRules.signUpProblem(normalizedEmail, password, confirmPassword, normalizedName, normalizedBarangay)
            ?.let { return@submit AccountResult.Failed(it) }
        when (val result = accounts.signUp(normalizedEmail, password, normalizedName, normalizedBarangay)) {
            is AccountResult.SignedIn, is AccountResult.ConfirmationRequired -> {
                profiles.save(EnumeratorProfile(normalizedName, normalizedBarangay))
                result
            }
            else -> result
        }
    }

    fun resendConfirmation(email: String) = submit {
        if (email.isBlank()) AccountResult.Failed("Enter the email address used to create the account.")
        else accounts.resendConfirmation(email.trim())
    }

    fun handleAuthCallback(intent: Intent) {
        if (mutableState.value.busy) return
        val callback = intent.data
        if (callback?.scheme != AUTH_SCHEME || callback.host != AUTH_HOST) return
        mutableState.value = mutableState.value.copy(busy = true, message = null)
        viewModelScope.launch {
            val result = try {
                withContext(ioDispatcher) {
                    withTimeoutOrNull(REQUEST_TIMEOUT_MILLIS) { accounts.handleAuthCallback(intent) }
                } ?: AccountResult.Failed("The confirmation link timed out. Check the connection and retry.")
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                AccountResult.Failed("The confirmation link could not be opened. Request a new confirmation email.")
            }
            if (result is AccountResult.SignedIn) hydrateCloudProfile()
            mutableState.value = result.toUiState()
        }
    }

    fun signOut() {
        if (mutableState.value.busy) return
        mutableState.value = mutableState.value.copy(busy = true, message = null)
        viewModelScope.launch {
            mutableState.value = try {
                withContext(ioDispatcher) { accounts.signOut() }
                AccountUiState(checking = false)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                AccountUiState(checking = false, signedInEmail = mutableState.value.signedInEmail, message = "Could not sign out. Check the connection and retry.", messageIsError = true)
            }
        }
    }

    private fun submit(request: suspend () -> AccountResult) {
        if (mutableState.value.busy) return
        mutableState.value = mutableState.value.copy(busy = true, message = null)
        viewModelScope.launch {
            val result = try {
                withContext(ioDispatcher) {
                    withTimeoutOrNull(REQUEST_TIMEOUT_MILLIS) { request() }
                        ?: AccountResult.Failed("The account request timed out. Check the connection and retry.")
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                AccountResult.Failed("The account request failed unexpectedly. Check the connection and retry.")
            }
            mutableState.value = result.toUiState()
        }
    }

    private suspend fun hydrateCloudProfile() {
        val cloudProfile = withContext(ioDispatcher) {
            withTimeoutOrNull(SESSION_LOOKUP_TIMEOUT_MILLIS) { accounts.profile() }
        }
        if (cloudProfile != null && cloudProfile.name.isNotBlank() && cloudProfile.barangay.isNotBlank()) {
            profiles.save(EnumeratorProfile(cloudProfile.name, cloudProfile.barangay))
        }
    }

    private fun AccountResult.toUiState(): AccountUiState = when (this) {
        is AccountResult.SignedIn -> AccountUiState(checking = false, signedInEmail = account.email)
        is AccountResult.ConfirmationRequired -> AccountUiState(
            checking = false,
            message = "Check your email to confirm the account. The confirmation link returns to Tala.",
            pendingConfirmationEmail = email,
        )
        is AccountResult.ConfirmationSent -> AccountUiState(
            checking = false,
            message = "A new confirmation email was requested. Check your inbox and spam folder.",
            pendingConfirmationEmail = email,
        )
        is AccountResult.Failed -> AccountUiState(checking = false, message = message, messageIsError = true)
    }

    private companion object {
        const val SESSION_LOOKUP_TIMEOUT_MILLIS = 8_000L
        const val REQUEST_TIMEOUT_MILLIS = 20_000L
        const val AUTH_SCHEME = "tala"
        const val AUTH_HOST = "auth-callback"
    }
}
