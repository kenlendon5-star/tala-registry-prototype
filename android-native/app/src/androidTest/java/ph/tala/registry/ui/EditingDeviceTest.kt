package ph.tala.registry.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import ph.tala.registry.MainActivity

/**
 * Day 6 editing flows on the target phone: add a household, see the saved state, reload
 * after recreation, then exercise member add and the archive/restore round trip.
 */
class EditingDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    /** MainActivity seeds the fictional fixtures on first launch; list assertions wait for them. */
    private fun awaitSeededFixtures() {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("household-HH-00452").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun recordCode(): String =
        compose.onNodeWithTag("edit-record-id").fetchSemanticsNode().config[SemanticsProperties.Text].first().text

    /** Reads a node's text or editable text directly; avoids the text-assertion helpers missing from this BOM. */
    private fun assertNodeContainsText(tag: String, substring: String) {
        val config = compose.onNodeWithTag(tag).fetchSemanticsNode().config
        val texts = config.getOrNull(SemanticsProperties.Text)?.map { it.text }.orEmpty()
        val editable = config.getOrNull(SemanticsProperties.EditableText)?.text
        val combined = (texts + listOfNotNull(editable)).joinToString(" ")
        assertTrue(
            "Expected [$tag] to contain \"$substring\", got \"$combined\"",
            combined.contains(substring),
        )
    }

    @Test fun newHouseholdReportsSavedAndReloadsAfterRecreation() {
        awaitSeededFixtures()
        compose.onNodeWithTag("nav-Households").performClick()
        compose.onNodeWithTag("add-household").performScrollTo().performClick()
        compose.onNodeWithTag("field-headName").performTextInput("Luna Reyes")
        compose.onNodeWithTag("field-address").performTextInput("5 Sampaguita Street")
        compose.onNodeWithTag("save-household").performClick()
        assertNodeContainsText("save-status", "Saved")
        // The new record keeps its generated identity and receives the next display code.
        val code = recordCode()
        assertTrue("Expected a generated HH-##### display code, got $code", Regex("^HH-\\d{5}$").matches(code))

        compose.activityRule.scenario.recreate()
        assertNodeContainsText("field-headName", "Luna Reyes")
        assertEquals(code, recordCode())

        // Editing updates the same record instead of creating a second household.
        compose.onNodeWithTag("field-headName").performScrollTo().performTextReplacement("Luna Dizon Reyes")
        compose.onNodeWithTag("save-household").performClick()
        assertNodeContainsText("save-status", "Saved")
        assertEquals(code, recordCode())

        compose.onNodeWithText("Back", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("households-screen").assertIsDisplayed()
        compose.onNodeWithTag("households-screen").performScrollToNode(hasTestTag("household-$code"))
        compose.onNodeWithTag("household-$code").assertIsDisplayed()
    }

    @Test fun memberAddEditAndArchiveRestoreRoundTrip() {
        awaitSeededFixtures()
        compose.onNodeWithTag("home-screen").performScrollToNode(hasTestTag("household-HH-00452"))
        compose.onNodeWithTag("household-HH-00452").performClick()
        compose.onNodeWithTag("tab-Members").performClick()
        compose.onNodeWithTag("interview-screen").performScrollToNode(hasTestTag("add-member"))
        compose.onNodeWithTag("add-member").performClick()
        compose.onNodeWithTag("field-displayName").performTextInput("Pedrito Santos")
        compose.onNodeWithTag("save-member").performClick()
        assertNodeContainsText("save-status", "Saved")

        compose.onNodeWithText("Back", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("interview-screen").performScrollToNode(hasText("Pedrito Santos"))
        compose.onNodeWithText("Pedrito Santos").assertIsDisplayed()

        compose.onNodeWithTag("tab-Overview").performClick()
        compose.onNodeWithTag("interview-screen").performScrollToNode(hasTestTag("edit-household"))
        compose.onNodeWithTag("edit-household").performClick()
        compose.onNodeWithTag("archive-household").performScrollTo().performClick()
        compose.onNodeWithText("Confirm archive", useUnmergedTree = true).performClick()
        assertNodeContainsText("save-status", "Archived")

        // Archiving is reversible and keeps the same record identity.
        compose.onNodeWithTag("archive-household").performScrollTo().performClick()
        compose.onNodeWithText("Confirm restore", useUnmergedTree = true).performClick()
        assertNodeContainsText("save-status", "Restored")
        assertEquals("HH-00452", recordCode())
    }
}