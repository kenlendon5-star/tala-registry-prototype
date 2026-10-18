package ph.tala.registry.ui

import android.graphics.Bitmap
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
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
import java.io.File

/** Invoke each method separately, with an actual am force-stop between write and verify. */
class FoundationSectionsDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app get() = context.applicationContext as TalaApplication
    private val hh = HouseholdId("d1200000-0000-4000-8000-000000000121")
    private val maria = MemberId("d1200000-0000-4000-8000-000000000122")
    private val juan = MemberId("d1200000-0000-4000-8000-000000000123")

    private fun await(tag: String) = compose.waitUntil(30_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
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
        compose.onNodeWithTag("option-$key-$text").performScrollTo().performClick()
    }
    private fun save() {
        compose.onNodeWithText("Save draft").performClick()
        compose.waitUntil(30_000) { compose.onAllNodesWithText("Saved on this device.").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun open() {
        check(context.packageName.endsWith(".d08test"))
        await("nav-Households")
        compose.onNodeWithTag("nav-Households").performClick()
        await("household-search")
        compose.onNodeWithTag("household-search").performTextReplacement("Foundation Acceptance")
        val code = runBlocking { app.repository.interviewRecord(hh)!!.household.displayCode }
        await("household-$code")
        compose.onNodeWithTag("household-$code").performScrollTo().performClick()
        await("open-questionnaire")
        compose.onNodeWithTag("open-questionnaire").performScrollTo().performClick()
        await("section-setup") // the workspace root also exists while Room is loading
    }
    private fun capture(name: String) {
        val folder = File(context.getExternalFilesDir(null), "day12").apply { mkdirs() }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        try { File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
    }

    @Test fun writeBranchesThroughNativeForms() {
        check(context.packageName.endsWith(".d08test"))
        runBlocking {
            val repo = app.repository
            assertEquals(WriteResult.Saved, repo.createHousehold(hh, HouseholdDraft("Foundation Acceptance", "Fictional Acceptance Street", HouseholdStatus.InProgress)))
            for ((id, name) in listOf(maria to "Maria Sample", juan to "Juan Sample")) {
                assertEquals(WriteResult.Saved, repo.saveMember(hh, id, MemberDraft(name)))
                for (section in listOf(QuestionnaireSections.education, QuestionnaireSections.employment)) {
                    val before = InterviewRules.answers(repo.interviewRecord(hh)!!, section, id.value)
                    assertEquals(WriteResult.Saved, repo.saveDraftSection(hh, section.route, id, emptyMap(), before))
                }
            }
            for (section in listOf(QuestionnaireSections.disaster, QuestionnaireSections.protection)) {
                val before = InterviewRules.answers(repo.interviewRecord(hh)!!, section)
                assertEquals(WriteResult.Saved, repo.saveDraftSection(hh, section.route, null, emptyMap(), before))
            }
        }
        open()
        section("education", maria)
        option("attending", "Yes"); select("school", "Public"); input("track", "STEM")
        select("grade", "Grade 10"); option("educationAssistance", "Yes")
        compose.onNodeWithTag("option-educationAssistance-Yes").assertIsSelected()
        save()
        option("attending", "No")
        compose.onNodeWithTag("field-school").assertDoesNotExist()
        compose.onNodeWithTag("field-track").assertDoesNotExist()
        select("reason", "Working"); save()
        section("education", juan)
        option("attending", "Yes"); select("school", "Special Needs Education (SNED)")
        select("grade", "Kindergarten"); option("educationAssistance", "No"); save() // optional track stays blank
        section("education", maria)
        compose.onNodeWithTag("field-reason").performScrollTo().assertTextContains("Working")
        section("employment", maria)
        option("worked", "Yes"); input("occupation", "Teacher"); input("industry", "Education")
        select("employmentType", "Employee"); input("hours", "169")
        compose.onNodeWithText("Check answers").performClick()
        compose.onNodeWithTag("hint-hours", useUnmergedTree = true).performScrollTo().assertTextEquals("Enter no more than 168.")
        compose.onNodeWithTag("field-hours").assertIsFocused()
        input("hours", "40"); save()
        option("worked", "No")
        listOf("occupation", "industry", "employmentType", "hours").forEach { compose.onNodeWithTag("field-$it").assertDoesNotExist() }
        option("looking", "Yes"); select("notWorkingReason", "Student"); option("available", "Yes"); save()
        option("looking", "No")
        compose.onNodeWithTag("field-available").assertDoesNotExist()
        save()
        section("employment", juan)
        option("worked", "Yes"); input("occupation", "Vendor"); input("industry", "Retail")
        select("employmentType", "Self-employed"); input("hours", "30"); save()
        capture("education-employment-two-members")

        section("disaster")
        select("disasterType", "Natural"); option("hazards", "Flood"); option("hazards", "None")
        option("hazards", "Typhoon") // selecting a normal answer clears exclusive None
        option("evacuationKnown", "Yes"); select("evacuation", "Other"); input("evacuationOther", "Fictional Hall")
        option("evacuationKnown", "No")
        compose.onNodeWithTag("field-evacuation").assertDoesNotExist()
        compose.onNodeWithTag("field-evacuationOther").assertDoesNotExist()
        option("evacuationKnown", "Yes"); select("evacuation", "Barangay Hall (Sample)")
        option("emergencyKit", "Yes"); save()
        capture("disaster-sample-branch")
        section("protection")
        option("philHealth", "Yes"); option("philHealthRole", "Dependent")
        option("philHealth", "No")
        compose.onNodeWithTag("field-philHealthRole").assertDoesNotExist()
        option("philHealth", "Yes"); option("philHealthRole", "Member")
        option("dswd", "No"); option("fourPs", "No")
        option("programs", "None"); option("programs", "PhilHealth")
        option("assistance", "Yes"); input("assistanceDetail", "Fictional assistance")
        option("assistance", "No")
        compose.onNodeWithTag("field-assistanceDetail").assertDoesNotExist()
        save()
        capture("protection-cleared-dependents")
        assertStored()
    }

    private fun assertStored() = runBlocking {
        val record = app.repository.interviewRecord(hh)!!
        val educationMaria = InterviewRules.answers(record, QuestionnaireSections.education, maria.value)
        val educationJuan = InterviewRules.answers(record, QuestionnaireSections.education, juan.value)
        assertEquals("Working", educationMaria.scalar("reason"))
        assertFalse(educationMaria.containsKey("school")); assertFalse(educationMaria.containsKey("track"))
        assertEquals("Special Needs Education (SNED)", educationJuan.scalar("school"))
        val employmentMaria = InterviewRules.answers(record, QuestionnaireSections.employment, maria.value)
        assertEquals("No", employmentMaria.scalar("looking"))
        for (key in listOf("occupation", "industry", "employmentType", "hours", "available")) assertFalse(employmentMaria.containsKey(key))
        assertEquals("Vendor", InterviewRules.answers(record, QuestionnaireSections.employment, juan.value).scalar("occupation"))
        val disaster = InterviewRules.answers(record, QuestionnaireSections.disaster)
        assertEquals(listOf("Typhoon"), disaster.multi("hazards")); assertFalse(disaster.containsKey("evacuationOther"))
        val protection = InterviewRules.answers(record, QuestionnaireSections.protection)
        assertEquals("Member", protection.scalar("philHealthRole")); assertEquals(listOf("PhilHealth"), protection.multi("programs"))
        assertFalse(protection.containsKey("assistanceDetail"))
        for (section in listOf(QuestionnaireSections.education, QuestionnaireSections.employment)) {
            for (member in listOf(maria, juan)) {
                val errors = FormRules.validate(section, InterviewRules.answers(record, section, member.value))
                assertTrue("${section.route}/${member.value}: $errors", errors.isEmpty())
            }
        }
        for (section in listOf(QuestionnaireSections.disaster, QuestionnaireSections.protection)) {
            assertTrue(FormRules.validate(section, InterviewRules.answers(record, section)).isEmpty())
            assertFalse(record.memberAnswers.any { it.section == section.route })
        }
    }

    @Test fun verifyAfterForceStop() {
        assertStored()
        open()
        section("education", maria)
        compose.onNodeWithTag("field-reason").performScrollTo().assertTextContains("Working")
        section("employment", juan)
        compose.onNodeWithTag("field-occupation").performScrollTo().assertTextContains("Vendor")
        section("protection")
        compose.onNodeWithTag("option-philHealthRole-Member").performScrollTo().assertIsSelected()
        compose.onNodeWithTag("field-assistanceDetail").assertDoesNotExist()
        capture("questionnaire-after-force-stop")
    }
}
