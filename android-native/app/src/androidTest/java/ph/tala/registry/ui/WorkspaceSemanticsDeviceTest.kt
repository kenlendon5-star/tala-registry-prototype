package ph.tala.registry.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import ph.tala.registry.MainActivity
import ph.tala.registry.TalaApplication
import ph.tala.registry.data.local.WriteResult
import ph.tala.registry.domain.edit.HouseholdDraft
import ph.tala.registry.domain.edit.MemberDraft
import ph.tala.registry.domain.model.*
import ph.tala.registry.testing.CompleteInterviewFixture
import java.util.UUID

/** Real persisted fixtures: correct readiness is independent of navigation selection. */
class WorkspaceSemanticsDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app get() = context.applicationContext as TalaApplication
    private val id = HouseholdId(UUID.randomUUID().toString())
    private val member = MemberId(UUID.randomUUID().toString())
    private val partialVisit = mapOf("visitDate" to AnswerValue.Scalar("2026-10-18"))

    private fun await(tag: String) = compose.waitUntil(30_000) {
        compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }
    private fun readiness(value: String) =
        SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, value)
    private fun awaitSelected(route: String) = compose.waitUntil(30_000) {
        compose.onAllNodes(hasTestTag("section-$route") and isSelected()).fetchSemanticsNodes().isNotEmpty()
    }

    @Before fun openWorkspace() {
        check(context.packageName.endsWith(".d08test"))
        val code = runBlocking {
            assertEquals(WriteResult.Saved, app.repository.createHousehold(id,
                HouseholdDraft("UX Semantics Fixture", "Fictional UX Street", HouseholdStatus.InProgress)))
            assertEquals(WriteResult.Saved, app.repository.saveMember(id, member, MemberDraft("UX Member")))
            val initialSetup = ph.tala.registry.domain.form.InterviewRules.answers(
                app.repository.interviewRecord(id)!!, ph.tala.registry.domain.form.QuestionnaireSections.setup)
            assertEquals(WriteResult.Saved, app.repository.saveDraftSection(id, "setup", null,
                CompleteInterviewFixture.sections.getValue("setup"), initialSetup))
            assertEquals(WriteResult.Saved, app.repository.saveDraftSection(id, "visit", null, partialVisit, emptyMap()))
            requireNotNull(app.repository.interviewRecord(id)).household.displayCode
        }
        await("nav-Households")
        compose.onNodeWithTag("nav-Households").performClick()
        await("household-search")
        compose.onNodeWithText("All", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithTag("household-search").performScrollTo().performTextReplacement(code)
        await("household-$code")
        compose.onNodeWithTag("household-$code").performScrollTo().performClick()
        await("open-questionnaire")
        compose.onNodeWithTag("open-questionnaire").performScrollTo().performClick()
        await("section-setup")
    }

    @Test fun sectionChipsExposeExactReadinessStates() {
        compose.onNodeWithTag("section-setup").performScrollTo().assert(readiness("Ready"))
        compose.onNodeWithTag("section-visit").performScrollTo().assert(readiness("Needs attention"))
        compose.onNodeWithTag("section-water").performScrollTo().assert(readiness("Not started"))
        compose.onNodeWithTag("section-directory").performScrollTo().performClick()
        compose.onNodeWithTag("directory-setup").performScrollTo().assert(readiness("Ready"))
        compose.onNodeWithTag("directory-visit").performScrollTo().assert(readiness("Needs attention"))
        compose.onNodeWithTag("directory-water").performScrollTo().assert(readiness("Not started"))
    }

    @Test fun selectionChangesNeitherReadinessNorStoredAnswers() {
        compose.onNodeWithTag("section-visit").performScrollTo().performClick()
        awaitSelected("visit")
        compose.onNodeWithTag("section-visit").assertIsSelected().assert(readiness("Needs attention"))
        compose.onNodeWithTag("section-setup").performScrollTo().assertIsNotSelected().assert(readiness("Ready"))
        compose.onNodeWithTag("section-water").performScrollTo().performClick()
        awaitSelected("water")
        compose.onNodeWithTag("section-water").assertIsSelected().assert(readiness("Not started"))
        compose.onNodeWithTag("section-visit").performScrollTo().assertIsNotSelected().assert(readiness("Needs attention"))
        runBlocking {
            val record = requireNotNull(app.repository.interviewRecord(id))
            assertEquals(partialVisit, ph.tala.registry.domain.form.InterviewRules.answers(record,
                ph.tala.registry.domain.form.QuestionnaireSections.visit))
        }
    }

    @Test fun directoryAndFooterUseCanonicalDemographicsNumber() {
        compose.onNodeWithTag("section-directory").performClick()
        compose.onNodeWithTag("directory-demographics").performScrollTo()
            .assert(hasText("05")).assert(hasText("Demographics"))
            .performClick()
        awaitSelected("demographics")
        compose.onNodeWithTag("section-position").assertTextEquals("Section 5 of 18")
        compose.onNodeWithTag("section-demographics").performScrollTo().assertIsSelected()
        compose.onNodeWithText("Save & back").performClick()
        await("interview-screen")
        compose.onNodeWithTag("interview-screen").performScrollToNode(hasTestTag("tab-Sections"))
        compose.onNodeWithTag("tab-Sections").performScrollTo().performClick()
        compose.onNodeWithTag("interview-screen").performScrollToNode(hasTestTag("overview-section-5"))
        compose.onNodeWithTag("overview-section-5").assert(hasAnyDescendant(hasText("05")))
            .assert(hasAnyDescendant(hasText("Demographics")))
    }

    @Test fun directoryListsMembersEditorAndRoutesThroughSaveBeforeExit() {
        compose.onNodeWithTag("section-directory").performClick()
        compose.onNodeWithTag("index-members").performScrollTo()
            .assert(hasText("04")).assert(hasText("Household members"))
            .performClick()
        await("interview-screen")
        compose.onNodeWithTag("interview-screen").performScrollToNode(hasTestTag("open-questionnaire"))
        compose.onNodeWithTag("open-questionnaire").performClick()
        await("section-setup")
        compose.onNodeWithTag("section-setup").performScrollTo().assertIsSelected()
    }

    @Test fun emptyMemberGuidanceAllowsAddAndReturnWithoutLosingDrafts() {
        runBlocking { assertEquals(WriteResult.Saved, app.repository.removeMember(id, member)) }
        compose.onNodeWithTag("section-demographics").performScrollTo().performClick()
        awaitSelected("demographics")
        await("leave-to-add-member")
        compose.onNodeWithTag("leave-to-add-member").performScrollTo().performClick()
        await("interview-screen")
        compose.onNodeWithTag("interview-screen").performScrollToNode(hasTestTag("tab-Members"))
        compose.onNodeWithTag("tab-Members").performScrollTo().performClick()
        compose.onNodeWithTag("interview-screen").performScrollToNode(hasTestTag("add-member"))
        compose.onNodeWithTag("add-member").performClick()
        await("field-displayName")
        compose.onNodeWithTag("field-displayName").performScrollTo().performTextInput("New UX Member")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithTag("save-member").performClick()
        compose.waitUntil(30_000) {
            runBlocking { app.repository.interviewRecord(id)!!.members.any { it.displayName == "New UX Member" } }
        }
        compose.onNodeWithTag("nav-back").performClick()
        await("interview-screen")
        compose.onNodeWithTag("interview-screen").performScrollToNode(hasTestTag("tab-Overview"))
        compose.onNodeWithTag("tab-Overview").performScrollTo().performClick()
        compose.onNodeWithTag("interview-screen").performScrollToNode(hasTestTag("open-questionnaire"))
        compose.onNodeWithTag("open-questionnaire").performClick()
        await("section-demographics")
        compose.onNodeWithTag("section-demographics").performScrollTo().performClick()
        compose.onNodeWithTag("field-firstName").performScrollTo().assertIsDisplayed()
        runBlocking {
            assertEquals(CompleteInterviewFixture.sections.getValue("setup"),
                ph.tala.registry.domain.form.InterviewRules.answers(app.repository.interviewRecord(id)!!,
                    ph.tala.registry.domain.form.QuestionnaireSections.setup))
        }
    }
}
