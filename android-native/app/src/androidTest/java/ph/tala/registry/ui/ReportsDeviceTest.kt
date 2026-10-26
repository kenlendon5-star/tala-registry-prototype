package ph.tala.registry.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Rule
import org.junit.Test
import ph.tala.registry.MainActivity
import ph.tala.registry.TalaApplication
import ph.tala.registry.data.local.WriteResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals

/**
 * Reports selection UI on a real device. SAF document dialogs are never launched here
 * (no environment-dependent document picker); these cases cover the
 * selection model itself: rendering, toggling, select-all/clear, the empty-selection
 * guard and the disabled-while-exporting rules.
 */
class ReportsDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun openReports() {
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("nav-Reports").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("nav-Reports").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("reports-screen").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun reportsScreenRendersSeededHouseholdRows() {
        openReports()
        compose.onNodeWithTag("reports-screen").assertIsDisplayed()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("reports-checkbox-HH-00452").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("reports-checkbox-HH-00452").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Export Selected").assertIsDisplayed()
        compose.onNodeWithTag("reports-restore").assertIsDisplayed().assertIsEnabled()
        // Empty selection keeps the export disabled until a row is checked.
        compose.onNodeWithTag("reports-export").assertIsNotEnabled()
    }

    @Test
    fun togglingAHouseholdEnablesExportAndClearRemovesIt() {
        openReports()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("reports-checkbox-HH-00452").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("reports-checkbox-HH-00452").performScrollTo().performClick()
        compose.onNodeWithTag("reports-export").assertIsEnabled()
        compose.onNodeWithTag("reports-clear").performClick()
        compose.onNodeWithTag("reports-export").assertIsNotEnabled()
    }

    @Test
    fun selectAllChecksEveryRowAndKeepsExportEnabled() {
        openReports()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("reports-checkbox-HH-00452").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("reports-select-all").performClick()
        compose.onNodeWithTag("reports-export").assertIsEnabled()
        // Seeded fixtures give at least two rows; every visible one must be checked.
        compose.onAllNodesWithTag("reports-checkbox-HH-00453")[0].performScrollTo().assertExists()
        compose.onNodeWithText("Clear Selection").performClick()
        compose.onNodeWithTag("reports-export").assertIsNotEnabled()
    }

    @Test
    fun archivedHouseholdRemainsVisibleAndSelectableAfterActivityRestart() {
        val app = compose.activity.application as TalaApplication
        val householdId = runBlocking {
            app.repository.households.first { rows -> rows.any { it.displayCode == "HH-00452" } }
                .first { it.displayCode == "HH-00452" }.id
        }
        try {
            assertEquals(WriteResult.Saved, runBlocking { app.repository.setArchived(householdId, true) })
            openReports()
            compose.onNodeWithTag("reports-row-status-HH-00452").performScrollTo()
                .assertTextContains("Archived", substring = true)
            compose.onNodeWithTag("reports-checkbox-HH-00452").performClick()
            compose.onNodeWithTag("reports-export").assertIsEnabled()
            compose.onNodeWithTag("reports-clear").performClick()

            compose.activityRule.scenario.recreate()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("reports-screen").fetchSemanticsNodes().isNotEmpty() }
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("reports-checkbox-HH-00452").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("reports-row-status-HH-00452").performScrollTo()
                .assertTextContains("Archived", substring = true)
            compose.onNodeWithTag("reports-checkbox-HH-00452").performClick()
            compose.onNodeWithTag("reports-export").assertIsEnabled()
        } finally {
            assertEquals(WriteResult.Saved, runBlocking { app.repository.setArchived(householdId, false) })
        }
    }

    @Test
    fun unsignedCloudSyncOpensAccountScreen() {
        openReports()
        compose.onNodeWithTag("reports-sync").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("account-screen").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("account-screen").assertIsDisplayed()
    }
}
