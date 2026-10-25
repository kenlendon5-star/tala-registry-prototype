package ph.tala.registry.data.cloud

import android.content.Intent
import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.handleDeeplinks
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.storage.Storage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import java.net.URI
import kotlin.coroutines.resume

data class CloudAccount(val id: String, val email: String)

sealed interface AccountResult {
    data class SignedIn(val account: CloudAccount) : AccountResult
    data class ConfirmationRequired(val email: String) : AccountResult
    data class ConfirmationSent(val email: String) : AccountResult
    data class Failed(val message: String) : AccountResult
}

interface AccountRepository {
    suspend fun currentAccount(): CloudAccount?
    suspend fun profile(): CloudEnumeratorProfile?
    suspend fun signIn(email: String, password: String): AccountResult
    suspend fun signUp(email: String, password: String, displayName: String, barangay: String): AccountResult
    suspend fun resendConfirmation(email: String): AccountResult
    suspend fun handleAuthCallback(intent: Intent): AccountResult?
    suspend fun signOut()
}

data class CloudEnumeratorProfile(val name: String, val barangay: String)

@Serializable
private data class CloudEnumeratorProfileRow(
    @SerialName("display_name") val name: String,
    val barangay: String,
)

/** Auth client using only Supabase's publishable key; the service-role key never belongs in Android. */
class SupabaseAccountRepository(url: String, publishableKey: String) : AccountRepository {
    private val client: SupabaseClient? = if (!url.isSecureEndpoint() || publishableKey.isBlank()) null else createSupabaseClient(
        supabaseUrl = url,
        supabaseKey = publishableKey,
    ) {
        install(Auth) {
            scheme = AUTH_SCHEME
            host = AUTH_HOST
        }
        install(Postgrest)
        install(Storage)
    }

    internal fun clientOrNull(): SupabaseClient? = client

    override suspend fun currentAccount(): CloudAccount? = try {
        client?.auth?.currentSessionOrNull()?.toAccount()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }

    override suspend fun profile(): CloudEnumeratorProfile? = try {
        val configured = client ?: return null
        val account = configured.auth.currentSessionOrNull()?.toAccount() ?: return null
        configured.from("enumerator_profiles").select {
            filter { eq("id", account.id) }
        }.decodeSingleOrNull<CloudEnumeratorProfileRow>()?.let { CloudEnumeratorProfile(it.name, it.barangay) }
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }

    override suspend fun signIn(email: String, password: String): AccountResult {
        val normalizedEmail = email.trim()
        return request(email = normalizedEmail) {
            it.auth.signInWith(Email) {
                this.email = normalizedEmail
                this.password = password
            }
            val account = it.auth.currentSessionOrNull()?.toAccount()
                ?: return@request AccountResult.Failed("Sign-in completed without a session. Confirm the email, then sign in again.")
            AccountResult.SignedIn(account)
        }
    }

    override suspend fun signUp(email: String, password: String, displayName: String, barangay: String): AccountResult = request(AccountOperation.SignUp) {
        val normalizedEmail = email.trim()
        it.auth.signUpWith(Email) {
            this.email = normalizedEmail
            this.password = password
            data = buildJsonObject {
                put("display_name", displayName.trim())
                put("barangay", barangay.trim())
            }
        }
        val account = it.auth.currentSessionOrNull()?.toAccount()
        if (account == null) AccountResult.ConfirmationRequired(normalizedEmail) else AccountResult.SignedIn(account)
    }

    override suspend fun resendConfirmation(email: String): AccountResult = request(AccountOperation.Resend) {
        val normalizedEmail = email.trim()
        it.auth.resendEmail(OtpType.Email.SIGNUP, normalizedEmail)
        AccountResult.ConfirmationSent(normalizedEmail)
    }

    override suspend fun handleAuthCallback(intent: Intent): AccountResult? {
        val data = intent.data ?: return null
        if (data.scheme != AUTH_SCHEME || data.host != AUTH_HOST) return null
        val configured = client ?: return AccountResult.Failed("Cloud sign-in is not configured. Add the Supabase publishable key to supabase.properties.")
        return suspendCancellableCoroutine { continuation ->
            configured.handleDeeplinks(
                intent,
                onSessionSuccess = { session ->
                    val result = session.toAccount()?.let(AccountResult::SignedIn)
                        ?: AccountResult.Failed("The confirmation link did not contain a valid account session.")
                    if (continuation.isActive) continuation.resume(result)
                },
                onError = {
                    if (continuation.isActive) continuation.resume(AccountResult.Failed("The confirmation link is invalid or expired. Request a new confirmation email."))
                },
            )
        }
    }

    override suspend fun signOut() {
        client?.auth?.signOut()
    }

    private suspend fun request(
        operation: AccountOperation = AccountOperation.SignIn,
        email: String? = null,
        block: suspend (SupabaseClient) -> AccountResult,
    ): AccountResult {
        val configured = client ?: return AccountResult.Failed("Cloud sign-in is not configured. Add the Supabase publishable key to supabase.properties.")
        return try {
            block(configured)
        } catch (error: AuthRestException) {
            val errorCode = error.errorCode
            Log.w(TAG, "Supabase account request failed: ${errorCode?.value ?: "unknown"}")
            if (errorCode == AuthErrorCode.EmailNotConfirmed && email != null) {
                AccountResult.ConfirmationRequired(email)
            } else {
                AccountResult.Failed(errorCode.toUserMessage(operation))
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w(TAG, "Supabase account request failed: ${error::class.simpleName}")
            AccountResult.Failed("Could not reach Supabase. Check the connection and account details, then retry.")
        }
    }

    private fun io.github.jan.supabase.auth.user.UserSession.toAccount(): CloudAccount? {
        val user = user ?: return null
        val email = user.email ?: return null
        return CloudAccount(user.id, email)
    }

    private fun AuthErrorCode?.toUserMessage(operation: AccountOperation): String = when (this) {
        AuthErrorCode.InvalidCredentials -> "Incorrect email or password."
        AuthErrorCode.EmailNotConfirmed -> "Confirm your email before signing in. You can resend the confirmation below."
        AuthErrorCode.EmailAddressInvalid, AuthErrorCode.ValidationFailed -> "Enter a valid email address."
        AuthErrorCode.EmailAddressNotAuthorized -> "This project cannot send confirmation email to that address. Configure custom SMTP or use an authorized test address."
        AuthErrorCode.UserAlreadyExists, AuthErrorCode.EmailExists -> "An account with this email already exists. Sign in instead."
        AuthErrorCode.WeakPassword -> "Use a stronger password with at least 8 characters."
        AuthErrorCode.OverEmailSendRateLimit, AuthErrorCode.OverRequestRateLimit -> "Too many requests were made. Wait a few minutes, then try again."
        AuthErrorCode.SignupDisabled, AuthErrorCode.EmailProviderDisabled -> "Email account creation is currently disabled for this project."
        AuthErrorCode.RequestTimeout -> "The account request timed out. Check the connection and retry."
        else -> when (operation) {
            AccountOperation.SignIn -> "Supabase could not sign in. Check the account details and retry."
            AccountOperation.SignUp -> "Supabase could not create the account. Review the details and retry."
            AccountOperation.Resend -> "Supabase could not resend the confirmation email. Try again later."
        }
    }

    private companion object {
        const val TAG = "TalaAuth"
        const val AUTH_SCHEME = "tala"
        const val AUTH_HOST = "auth-callback"

        fun String.isSecureEndpoint(): Boolean = runCatching {
            val endpoint = URI(this)
            endpoint.scheme.equals("https", ignoreCase = true) && !endpoint.host.isNullOrBlank() && endpoint.userInfo == null
        }.getOrDefault(false)
    }

    private enum class AccountOperation { SignIn, SignUp, Resend }
}
