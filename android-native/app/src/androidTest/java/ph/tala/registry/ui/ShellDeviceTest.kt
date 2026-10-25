package ph.tala.registry.ui

import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
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

    /** MainActivity seeds the fictional fixtures on first launch; list assertions wait for them. */
    private fun awaitSeededFixtures() {
        // The redesigned home (UX-07) shows summary metrics, not the roster; the
        // household rows live on the Households tab. Confirm seeding there, then
        // return Home so tests start from the launch screen.
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("register-household").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("nav-Households").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("household-HH-00452").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("nav-Home").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("home-screen").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun navigationKeyboardBackAndRecreation() {
        awaitSeededFixtures()
        compose.onNodeWithTag("home-screen").assertIsDisplayed()
        capture("01-home")
        // UX-07 home: the roster moved to the Households tab.
        compose.onNodeWithTag("nav-Households").performClick()
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
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.onNodeWithTag("record-id").assertTextEquals("HH-00453")
        compose.onNodeWithTag("tab-Members").assertIsSelected()
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
        awaitSeededFixtures()
        compose.onNodeWithTag("nav-Households").performClick()
        compose.onNodeWithTag("household-search").performTextReplacement("No Such Fictional Household D13")
        compose.waitUntil(10_000) { compose.onAllNodesWithText("No Matching Households").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Reviewed", useUnmergedTree = true).performClick()
        compose.onNodeWithText("No Matching Households").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("No Matching Households").assertIsDisplayed()
        compose.onNodeWithText("Clear Search and Filters").performClick()
        compose.onNodeWithTag("household-HH-00452").assertExists()
    }

    @Test fun launchRequestsOnlyDeclaredPermissionsAndContentRespectsSystemBars() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        val requested = info.requestedPermissions.orEmpty().toSet()
        // D16: INTERNET and ACCESS_NETWORK_STATE exist solely for the on-demand
        // OpenStreetMap basemap fetch plus explicit owner-authenticated Supabase actions.
        // There is no analytics SDK, and anything outside the declared set is a privacy
        // regression. DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION
        // is generated by the manifest merger for androidx runtime receivers (API 33+),
        // namespaced with the application id — tooling, not a permission request.
        val declared = setOf(
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.ACCESS_FINE_LOCATION",
        )
        val undeclared = requested.filterNot { it in declared || it.endsWith("DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION") }
        assertTrue("Undeclared permission requested: $undeclared", undeclared.isEmpty())
        assertTrue("The map basemap needs INTERNET", requested.contains("android.permission.INTERNET"))
        assertTrue("Offline detection needs ACCESS_NETWORK_STATE", requested.contains("android.permission.ACCESS_NETWORK_STATE"))
        assertEquals(false, compose.activity.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC != 0)
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
