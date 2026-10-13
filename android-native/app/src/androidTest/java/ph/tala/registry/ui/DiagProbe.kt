package ph.tala.registry.ui

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.printToString
import androidx.compose.ui.semantics.getOrNull
import org.junit.Rule
import org.junit.Test
import ph.tala.registry.MainActivity

class DiagProbe {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun await(tag: String) = compose.waitUntil(10_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }

    @Test fun dumpAfterCreateSave() {
        await("household-HH-00452")
        compose.onNodeWithTag("nav-Households").performClick()
        compose.onNodeWithTag("add-household").performScrollTo().performClick()
        // Try the raw SetText semantics action directly, bypassing performTextReplacement.
        val headNode = compose.onNodeWithTag("field-headName")
        headNode.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetText) {
            it(androidx.compose.ui.text.AnnotatedString("Diag Family"))
        }
        compose.waitForIdle()
        Thread.sleep(500)
        val headVal = compose.onAllNodesWithTag("field-headName").fetchSemanticsNodes().map { it.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.EditableText)?.text }
        val headFocused = compose.onAllNodesWithTag("field-headName").fetchSemanticsNodes().map { it.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Focused) }
        println("DIAG after SetText: headEditable=$headVal focused=$headFocused")
    }
}
