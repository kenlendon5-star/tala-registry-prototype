package ph.tala.registry.ui.account

import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import ph.tala.registry.data.EnumeratorProfileStore
import ph.tala.registry.data.cloud.AccountRepository
import ph.tala.registry.data.cloud.AccountResult
import ph.tala.registry.data.cloud.CloudAccount
import ph.tala.registry.data.cloud.CloudEnumeratorProfile
import ph.tala.registry.data.local.WriteResult
import ph.tala.registry.domain.model.EnumeratorProfile

@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun existingSessionHydratesTheLocalEnumeratorProfile() = runTest(dispatcher) {
        val profiles = FakeProfiles()
        val accounts = FakeAccounts(
            account = CloudAccount("a0000000-0000-4000-8000-000000000001", "enumerator@example.test"),
            cloudProfile = CloudEnumeratorProfile("Ari Santos", "Barangay Cogon"),
        )

        val model = AccountViewModel(accounts, profiles, dispatcher)
        advanceUntilIdle()

        assertEquals("enumerator@example.test", model.state.value.signedInEmail)
        assertEquals(EnumeratorProfile("Ari Santos", "Barangay Cogon"), profiles.profile.value)
    }

    @Test
    fun signOutReturnsToTheAccountGate() = runTest(dispatcher) {
        val accounts = FakeAccounts(account = CloudAccount("a0000000-0000-4000-8000-000000000001", "enumerator@example.test"))
        val model = AccountViewModel(accounts, FakeProfiles(), dispatcher)
        advanceUntilIdle()

        model.signOut()
        advanceUntilIdle()

        assertEquals(1, accounts.signOutCalls)
        assertNull(model.state.value.signedInEmail)
    }

    @Test
    fun signInHydratesCloudProfileImmediately() = runTest(dispatcher) {
        val profiles = FakeProfiles()
        val accounts = FakeAccounts(
            cloudProfile = CloudEnumeratorProfile("Lina Cruz", "Barangay 12"),
            signInResult = AccountResult.SignedIn(CloudAccount("a0000000-0000-4000-8000-000000000002", "lina@example.test")),
        )
        val model = AccountViewModel(accounts, profiles, dispatcher)
        advanceUntilIdle()

        model.signIn(" lina@example.test ", "password123")
        advanceUntilIdle()

        assertEquals("lina@example.test", model.state.value.signedInEmail)
        assertEquals(EnumeratorProfile("Lina Cruz", "Barangay 12"), profiles.profile.value)
    }

    @Test
    fun confirmationRequiredSignupKeepsLocalProfileAndCanResend() = runTest(dispatcher) {
        val profiles = FakeProfiles()
        val accounts = FakeAccounts(
            signUpResult = AccountResult.ConfirmationRequired("new@example.test"),
            resendResult = AccountResult.ConfirmationSent("new@example.test"),
        )
        val model = AccountViewModel(accounts, profiles, dispatcher)
        advanceUntilIdle()

        model.signUp("new@example.test", "password123", "password123", " New Enumerator ", " Barangay 9 ")
        advanceUntilIdle()

        assertEquals(EnumeratorProfile("New Enumerator", "Barangay 9"), profiles.profile.value)
        assertEquals("new@example.test", model.state.value.pendingConfirmationEmail)
        assertEquals(false, model.state.value.messageIsError)

        model.resendConfirmation("new@example.test")
        advanceUntilIdle()

        assertEquals(1, accounts.resendCalls)
        assertEquals("new@example.test", model.state.value.pendingConfirmationEmail)
    }

    @Test
    fun programmaticSignupRejectsInvalidEmailAndPasswordMismatchBeforeRepositoryCall() = runTest(dispatcher) {
        val accounts = FakeAccounts(signUpResult = AccountResult.ConfirmationRequired("unused@example.test"))
        val model = AccountViewModel(accounts, FakeProfiles(), dispatcher)
        advanceUntilIdle()

        model.signUp("not-an-email", "password123", "different123", "Enumerator", "Barangay 9")
        advanceUntilIdle()

        assertEquals(0, accounts.signUpCalls)
        assertEquals(true, model.state.value.messageIsError)
        assertEquals("Enter a valid email address.", model.state.value.message)
    }

    private class FakeProfiles(initial: EnumeratorProfile = EnumeratorProfile("Local", "Local")) : EnumeratorProfileStore {
        override val profile = MutableStateFlow(initial)
        override suspend fun save(profile: EnumeratorProfile): WriteResult {
            this.profile.value = profile
            return WriteResult.Saved
        }
    }

    private class FakeAccounts(
        private var account: CloudAccount? = null,
        private val cloudProfile: CloudEnumeratorProfile? = null,
        private val signInResult: AccountResult = AccountResult.Failed("Not used"),
        private val signUpResult: AccountResult = AccountResult.Failed("Not used"),
        private val resendResult: AccountResult = AccountResult.Failed("Not used"),
    ) : AccountRepository {
        var signOutCalls = 0
        var resendCalls = 0
        var signUpCalls = 0
        override suspend fun currentAccount(): CloudAccount? = account
        override suspend fun profile(): CloudEnumeratorProfile? = cloudProfile
        override suspend fun signIn(email: String, password: String): AccountResult = signInResult
        override suspend fun signUp(email: String, password: String, displayName: String, barangay: String): AccountResult {
            signUpCalls += 1
            return signUpResult
        }
        override suspend fun resendConfirmation(email: String): AccountResult { resendCalls += 1; return resendResult }
        override suspend fun handleAuthCallback(intent: Intent): AccountResult? = null
        override suspend fun signOut() { signOutCalls += 1; account = null }
    }
}
