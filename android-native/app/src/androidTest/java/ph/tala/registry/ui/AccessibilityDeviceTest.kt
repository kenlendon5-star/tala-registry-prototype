package ph.tala.registry.ui

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import ph.tala.registry.MainActivity

/** D20 checks for critical TalkBack names and minimum interactive bounds. */
class AccessibilityDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun await(tag: String) = compose.waitUntil(30_000) {
        compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }

    private fun assertMinimumTouchTarget(tag: String) {
        val bounds = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        val minimumPixels = 48f * compose.activity.resources.displayMetrics.density
        assertTrue("$tag width ${bounds.width} px is below 48dp", bounds.width + 1f >= minimumPixels)
        assertTrue("$tag height ${bounds.height} px is below 48dp", bounds.height + 1f >= minimumPixels)
    }

    @Test fun criticalNavigationHasTalkBackNamesAndMinimumTargets() {
        await("register-household")
        for (label in listOf("Home", "Households", "Map", "Reports", "Profile")) {
            compose.onNodeWithTag("nav-$label")
                .assert(hasContentDescription(label))
                .assertIsDisplayed()
            assertMinimumTouchTarget("nav-$label")
        }
        compose.onNodeWithTag("enumerator-row")
            .assert(hasContentDescription("Open Enumerator Profile"))
            .assertIsDisplayed()
        assertMinimumTouchTarget("enumerator-row")
        assertMinimumTouchTarget("register-household")
    }

    @Test fun questionnaireDirectoryRemainsLabelledAndReachableAtLargeText() {
        await("nav-Households")
        compose.onNodeWithTag("nav-Households").performClick()
        await("household-HH-00452")
        compose.onNodeWithTag("households-screen").performScrollToNode(hasTestTag("household-HH-00452"))
        compose.onNodeWithTag("household-HH-00452").performClick()
        await("interview-screen")
        compose.onNodeWithTag("interview-screen").performScrollToNode(hasTestTag("open-questionnaire"))
        compose.onNodeWithTag("open-questionnaire").performClick()
        await("section-directory")
        compose.onNodeWithTag("section-directory")
            .assert(hasContentDescription("Open section directory"))
            .assertIsDisplayed()
        assertMinimumTouchTarget("section-directory")
        compose.onNodeWithTag("section-directory").performClick()
        compose.onNodeWithTag("index-members").performScrollTo().assertIsDisplayed()
    }
}
