package ph.tala.registry.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import ph.tala.registry.MainActivity

class AccountDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun openAccount() {
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("nav-Reports").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("nav-Reports").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("reports-sync").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("reports-sync").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("account-screen").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun createAccountRequiresMatchingPasswords() {
        openAccount()
        compose.onNodeWithTag("account-mode-toggle").performScrollTo().performClick()
        compose.onNodeWithTag("account-name").performTextInput("Test Enumerator")
        compose.onNodeWithTag("account-barangay").performTextInput("Barangay Test")
        compose.onNodeWithTag("account-email").performTextInput("enumerator@example.test")
        compose.onNodeWithTag("account-password").performTextInput("password123")
        compose.onNodeWithTag("account-confirm-password").performTextInput("password456")
        compose.onNodeWithTag("account-submit").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("account-confirm-password").performTextClearance()
        compose.onNodeWithTag("account-confirm-password").performTextInput("password123")
        compose.onNodeWithTag("account-submit").assertIsEnabled().assertIsDisplayed()
    }

    @Test
    fun confirmationCallbackResolvesToMainActivity() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val callback = Intent(Intent.ACTION_VIEW, Uri.parse("tala://auth-callback#access_token=fake"))
            .setPackage(context.packageName)
        assertNotNull(context.packageManager.resolveActivity(callback, 0))
    }
}
