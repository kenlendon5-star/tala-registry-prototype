package ph.tala.registry.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import ph.tala.registry.MainActivity
import ph.tala.registry.TalaApplication
import ph.tala.registry.data.local.WriteResult
import ph.tala.registry.domain.edit.*
import ph.tala.registry.domain.form.*
import ph.tala.registry.domain.model.*
import ph.tala.registry.testing.CompleteInterviewFixture
import java.io.File
import java.util.UUID

/**
 * D14 week-two gate: one scripted interview walked through the real workspace UI,
 * covering every generated questionnaire section with two members and distinct
 * answers, then reviewed to explicit local completion. The walker is driven by the
 * generated [QuestionnaireSections] specs, so a regenerated questionnaire must stay
 * UI-walkable or this test fails.
 */
class FullCoverageDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app get() = context.applicationContext as TalaApplication
    // Each writer run starts with fresh identities. Keep them in app-private storage
    // so the separate force-stop verifier reopens exactly that run's interview.
    private val fixtureFile get() = File(context.filesDir, "day14-fixture-ids.txt")
    private val fixtureIds by lazy {
        require(fixtureFile.isFile) { "Run the D14 interview writer before the restart verifier." }
        fixtureFile.readLines().also { ids ->
            require(ids.size == 3 && ids.distinct().size == 3)
            ids.forEach { UUID.fromString(it) }
        }
    }
    private val hh get() = HouseholdId(fixtureIds[0])
    private val maria get() = MemberId(fixtureIds[1])
    private val juan get() = MemberId(fixtureIds[2])

    /** Second member: distinct answers and the opposite migration/education branches. */
    private val juanAnswers: Map<String, AnswerState> = mapOf(
        "demographics" to mapOf(
            "firstName" to AnswerValue.Scalar("Juan"),
            "lastName" to AnswerValue.Scalar("Sample"),
            "registeredVoter" to AnswerValue.Scalar("No"),
            "relationship" to AnswerValue.Scalar("Spouse"),
            "sex" to AnswerValue.Scalar("Male"),
            "dob" to AnswerValue.Scalar("1985-08-22"),
            "birth" to AnswerValue.Scalar("No"),
            "civilStatus" to AnswerValue.Scalar("Married"),
        ),
        "other" to mapOf(
            "nationalId" to AnswerValue.Scalar("Yes"),
            "soloParent" to AnswerValue.Scalar("No"),
            "disability" to AnswerValue.Scalar("No"),
            "seniorId" to AnswerValue.Scalar("Not applicable"),
        ),
        "migration" to mapOf(
            "citizenship" to AnswerValue.Scalar("Filipino"),
            "moved" to AnswerValue.Scalar("Yes"),
            "previousResidence" to AnswerValue.Scalar("Cebu City"),
            "moveYear" to AnswerValue.Scalar("2015"),
            "overseas" to AnswerValue.Scalar("No"),
        ),
        "education" to mapOf(
            "attending" to AnswerValue.Scalar("Yes"),
            "school" to AnswerValue.Scalar("Private"),
            "track" to AnswerValue.Scalar("TVL"),
            "grade" to AnswerValue.Scalar("Grade 6"),
            "educationAssistance" to AnswerValue.Scalar("No"),
        ),
        "employment" to mapOf(
            "worked" to AnswerValue.Scalar("Yes"),
            "occupation" to AnswerValue.Scalar("Vendor"),
            "industry" to AnswerValue.Scalar("Retail"),
            "employmentType" to AnswerValue.Scalar("Self-employed"),
            "hours" to AnswerValue.Scalar("30"),
        ),
    )

    private fun await(tag: String) = compose.waitUntil(30_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    private fun record(id: HouseholdId) = runBlocking { requireNotNull(app.repository.interviewRecord(id)) }
    private fun section(route: String, member: MemberId? = null) {
        compose.onNodeWithTag("section-$route").performScrollTo().performClick()
        compose.waitUntil(30_000) { compose.onAllNodes(hasTestTag("section-$route") and isSelected()).fetchSemanticsNodes().isNotEmpty() }
        if (member != null) {
            await("member-${member.value}")
            compose.onNodeWithTag("member-${member.value}").performScrollTo().performClick()
            compose.waitUntil(30_000) { compose.onAllNodes(hasTestTag("member-${member.value}") and isSelected()).fetchSemanticsNodes().isNotEmpty() }
        }
    }
    private fun input(key: String, text: String) {
        compose.onNodeWithTag("field-$key").performScrollTo().performTextReplacement(text)
        // Wait for the platform keyboard to finish moving the form before tapping
        // another control. Compose idle alone does not await OEM IME animations.
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.waitUntil(5_000) {
            compose.runOnIdle {
                ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
                    ?.isVisible(WindowInsetsCompat.Type.ime()) != true
            }
        }
    }
    private fun option(key: String, text: String) = compose.onNodeWithTag("option-$key-$text").performScrollTo().performClick()
    private fun select(key: String, text: String) {
        compose.onNodeWithTag("field-$key").performScrollTo().performClick()
        option(key, text)
    }
    private fun save() {
        compose.onNodeWithText("Save draft").performClick()
        compose.waitUntil(30_000) { compose.onAllNodesWithText("Saved on this device.").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun recordVisit() {
        compose.onNodeWithText("Record visit result").performScrollTo().performClick()
        compose.waitUntil(30_000) { compose.onAllNodesWithText("Visit result recorded on this device.").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun review() {
        compose.onNodeWithText("Review interview").performClick()
        await("review-progress")
    }
    private fun open() {
        check(context.packageName.endsWith(".d08test"))
        await("nav-Households")
        compose.onNodeWithTag("nav-Households").performClick()
        await("household-search")
        val code = record(hh).household.displayCode
        compose.onNodeWithTag("household-search").performTextReplacement(code)
        await("household-$code")
        compose.onNodeWithTag("household-$code").performScrollTo().performClick()
        await("open-questionnaire")
        compose.onNodeWithTag("open-questionnaire").performScrollTo().performClick()
        await("section-setup") // the workspace root also exists while Room is loading
    }
    private fun capture(name: String) {
        val folder = File(context.getExternalFilesDir(null), "day14").apply { mkdirs() }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        try { File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
    }

    /** Type one section's fixture into the live form; hidden conditional fields are skipped. */
    private fun fillAndSave(section: SectionSpec, answers: AnswerState) {
        for (field in section.fields) {
            val value = answers[field.key] ?: continue
            when (field.type) {
                FieldType.Photo -> Unit // optional slot; D08 picture acceptance covers the picker flow
                FieldType.Choice, FieldType.MultiSelect -> {
                    val values = when (value) {
                        is AnswerValue.Multi -> value.selected
                        is AnswerValue.Scalar -> listOf(value.text)
                    }
                    values.forEach { option(field.key, it) }
                }
                FieldType.Select -> select(field.key, (value as AnswerValue.Scalar).text)
                else -> {
                    val text = (value as AnswerValue.Scalar).text
                    if (text.isNotBlank()) input(field.key, text)
                }
            }
        }
        save()
    }

    @Test fun fullInterviewThroughAllSectionsWithTwoMembers() {
        check(context.packageName.endsWith(".d08test"))
        fixtureFile.writeText(List(3) { UUID.randomUUID().toString() }.joinToString("\n"))
        runBlocking {
            assertEquals(WriteResult.Saved, app.repository.createHousehold(hh, HouseholdDraft("Lifecycle Acceptance", "Fictional Lifecycle Street", HouseholdStatus.InProgress)))
            assertEquals(WriteResult.Saved, app.repository.saveMember(hh, maria, MemberDraft("Maria Sample")))
            assertEquals(WriteResult.Saved, app.repository.saveMember(hh, juan, MemberDraft("Juan Sample")))
        }
        open()
        val fixture = CompleteInterviewFixture.sections
        for (section in QuestionnaireSections.forms) {
            section(section.route)
            if (section.scopedToMember) {
                // Both members answer in the same UI session to prove scope switching.
                section(section.route, maria)
                fillAndSave(section, fixture.getValue(section.route))
                section(section.route, juan)
                fillAndSave(section, juanAnswers.getValue(section.route))
            } else {
                fillAndSave(section, fixture.getValue(section.route))
                if (section.route == "visit") recordVisit()
            }
        }
        capture("all-sections-consent")
        review()
        compose.onNodeWithText("18 of 18 sections ready. This records local completion only.", useUnmergedTree = true).performScrollTo().assertExists()
        assertTrue(InterviewRules.issues(record(hh)).isEmpty())
        assertTrue(InterviewRules.progress(record(hh)).allReady)
        capture("review-18-of-18")
        compose.onNodeWithText("Mark interview reviewed").performScrollTo().performClick()
        compose.waitUntil(30_000) { record(hh).household.status == "Reviewed" }
        compose.onNodeWithText("Interview reviewed on this device.").assertExists()
        capture("interview-reviewed")
    }

    @Test fun verifyFullInterviewAfterForceStop() {
        check(context.packageName.endsWith(".d08test"))
        val record = record(hh)
        assertEquals("Reviewed", record.household.status)
        assertTrue("review issues after force-stop: ${InterviewRules.issues(record)}", InterviewRules.issues(record).isEmpty())
        assertTrue(InterviewRules.progress(record).allReady)
        // Dated visit history recorded during the interview survives the force-stop.
        assertEquals(listOf("Completed visit"), record.visits.map { it.outcome })
        // Both members kept their own distinct answers on the member-scoped sections.
        assertEquals("Maria", InterviewRules.answers(record, QuestionnaireSections.demographics, maria.value).scalar("firstName"))
        assertEquals("Juan", InterviewRules.answers(record, QuestionnaireSections.demographics, juan.value).scalar("firstName"))
        assertEquals("Female", InterviewRules.answers(record, QuestionnaireSections.demographics, maria.value).scalar("sex"))
        assertEquals("Male", InterviewRules.answers(record, QuestionnaireSections.demographics, juan.value).scalar("sex"))
        assertEquals("Completed schooling", InterviewRules.answers(record, QuestionnaireSections.education, maria.value).scalar("reason"))
        assertEquals("Private", InterviewRules.answers(record, QuestionnaireSections.education, juan.value).scalar("school"))
        assertEquals("Teacher", InterviewRules.answers(record, QuestionnaireSections.employment, maria.value).scalar("occupation"))
        assertEquals("Vendor", InterviewRules.answers(record, QuestionnaireSections.employment, juan.value).scalar("occupation"))
        assertEquals("No", InterviewRules.answers(record, QuestionnaireSections.migration, maria.value).scalar("moved"))
        assertEquals("Cebu City", InterviewRules.answers(record, QuestionnaireSections.migration, juan.value).scalar("previousResidence"))
        assertEquals("2015", InterviewRules.answers(record, QuestionnaireSections.migration, juan.value).scalar("moveYear"))
        assertEquals("Not applicable", InterviewRules.answers(record, QuestionnaireSections.other, juan.value).scalar("seniorId"))
        // Every household-scoped section kept its fixture answer; member_answers stays clean.
        val fixture = CompleteInterviewFixture.sections
        for (section in QuestionnaireSections.forms.filter { !it.scopedToMember }) {
            val stored = InterviewRules.answers(record, section)
            val expected = fixture.getValue(section.route)
            for ((key, value) in expected) {
                val text = when (value) {
                    is AnswerValue.Scalar -> value.text
                    is AnswerValue.Multi -> value.selected.joinToString()
                }
                assertEquals("${section.route}/$key after force-stop", text, when (value) {
                    is AnswerValue.Multi -> stored.multi(key).joinToString()
                    else -> stored.scalar(key)
                })
            }
            assertFalse("${section.route} must stay household-scoped", record.memberAnswers.any { it.section == section.route })
        }
        // And each member section actually stored rows for both members.
        for (section in QuestionnaireSections.forms.filter { it.scopedToMember }) {
            for (member in listOf(maria, juan)) {
                assertTrue(
                    "${section.route} stored nothing for ${member.value}",
                    record.memberAnswers.any { it.section == section.route && it.memberId == member.value },
                )
            }
        }
        // The reviewed state and both member scopes reload into the UI.
        open()
        section("education", maria)
        compose.onNodeWithTag("field-reason").performScrollTo().assertTextContains("Completed schooling")
        section("education", juan)
        compose.onNodeWithTag("field-school").performScrollTo().assertTextContains("Private")
        section("employment", juan)
        compose.onNodeWithTag("field-occupation").performScrollTo().assertTextContains("Vendor")
        section("consent")
        compose.onNodeWithTag("field-signedName").performScrollTo().assertTextContains("Maria Sample")
        review()
        compose.onNodeWithText("18 of 18 sections ready. This records local completion only.", useUnmergedTree = true).performScrollTo().assertExists()
        capture("coverage-after-force-stop")
    }
}
