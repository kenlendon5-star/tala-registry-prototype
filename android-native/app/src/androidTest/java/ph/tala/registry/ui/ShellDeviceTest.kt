package ph.tala.registry.ui

import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import ph.tala.registry.MainActivity
import java.io.File

/** Runs against the installed app and a real Android window/keyboard. */
class ShellDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun navigationKeyboardBackAndRecreation() {
        compose.onNodeWithTag("home-screen").assertIsDisplayed()
        capture("01-home")
        compose.onNodeWithText("Browse households").performScrollTo().performClick()
        compose.onNodeWithTag("households-screen").assertIsDisplayed()
        capture("02-households")
        compose.onNodeWithTag("household-search").performClick().performTextInput("Reyes")
        compose.waitForIdle()
        compose.onNodeWithTag("household-HH-00453").assertExists()
        compose.onNodeWithTag("household-HH-00452").assertDoesNotExist()
        compose.waitUntil(5_000) {
            var shown = false
            compose.activityRule.scenario.onActivity { activity ->
                shown = ViewCompat.getRootWindowInsets(activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true
            }
            shown
        }
        assertSearchAboveKeyboard()
        capture("03-keyboard")
        Espresso.pressBack() // Android dismisses the keyboard before popping the destination.
        compose.onNodeWithTag("households-screen").assertIsDisplayed()
        compose.onNodeWithTag("household-HH-00453").performScrollTo().performClick()
        compose.onNodeWithTag("record-id").assertTextEquals("HH-00453")
        compose.onNodeWithTag("tab-Members").performClick()
        compose.onNodeWithText("No members in this sample household.").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("record-id").assertTextEquals("HH-00453")
        compose.onNodeWithTag("tab-Members").assertIsSelected()
        Espresso.pressBack()
        compose.onNodeWithTag("household-search").assertTextContains("Reyes")
        compose.onNodeWithText("Clear").performClick()
        compose.onNodeWithTag("household-HH-00452").performScrollTo().performClick()
        compose.onNodeWithTag("record-id").assertTextEquals("HH-00452")
        compose.onNodeWithTag("tab-Members").assertIsNotSelected()
        capture("04-interview")
        compose.onNodeWithTag("tab-Members").performClick()
        compose.onNodeWithText("Ana Santos").performScrollTo().assertIsDisplayed()
        capture("05-members")
        compose.onNodeWithTag("tab-Sections").performScrollTo().performClick()
        capture("06-sections")
        compose.onNodeWithTag("interview-screen").performScrollToNode(hasText("Consent & review"))
        compose.onNodeWithText("Consent & review").assertIsDisplayed()
        Espresso.pressBack()
        compose.onNodeWithTag("households-screen").assertIsDisplayed()
        Espresso.pressBack()
        compose.onNodeWithTag("home-screen").assertIsDisplayed()
    }

    @Test fun restoredFilterAndEmptySearchHaveRecovery() {
        compose.onNodeWithTag("nav-Households").performClick()
        compose.onNodeWithText("Reviewed", useUnmergedTree = true).performClick()
        compose.onNodeWithText("No matching households").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("No matching households").assertIsDisplayed()
        compose.onNodeWithText("Clear search and filters").performClick()
        compose.onNodeWithTag("household-HH-00452").assertExists()
    }

    @Test fun launchHasNoNetworkPermissionAndContentRespectsSystemBars() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        assertFalse("The offline shell must not require network access", info.requestedPermissions.orEmpty().contains("android.permission.INTERNET"))
        val content = compose.onNodeWithTag("home-screen").fetchSemanticsNode().boundsInWindow
        compose.activityRule.scenario.onActivity { activity ->
            val root = activity.window.decorView
            val bars = requireNotNull(ViewCompat.getRootWindowInsets(root)).getInsets(WindowInsetsCompat.Type.systemBars())
            assertTrue("Content overlaps status bar", content.top >= bars.top)
            assertTrue("Content overlaps navigation bar", content.bottom <= root.height - bars.bottom)
        }
    }

    private fun assertSearchAboveKeyboard() {
        val bounds = compose.onNodeWithTag("household-search").fetchSemanticsNode().boundsInWindow
        compose.activityRule.scenario.onActivity { activity ->
            val root = activity.window.decorView
            val ime = requireNotNull(ViewCompat.getRootWindowInsets(root)).getInsets(WindowInsetsCompat.Type.ime())
            assertTrue("Search is obscured by the keyboard", bounds.bottom <= root.height - ime.bottom)
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "day03").apply { mkdirs() }
        instrumentation.uiAutomation.takeScreenshot().useBitmap { bitmap ->
            File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun Bitmap.useBitmap(action: (Bitmap) -> Unit) { try { action(this) } finally { recycle() } }
}
