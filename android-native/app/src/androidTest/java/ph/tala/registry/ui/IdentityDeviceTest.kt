package ph.tala.registry.ui

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import ph.tala.registry.MainActivity
import ph.tala.registry.TalaApplication
import ph.tala.registry.data.local.WriteResult
import ph.tala.registry.domain.edit.*
import ph.tala.registry.domain.form.*
import ph.tala.registry.domain.model.*
import java.io.File

/** Run writeThroughQuestionnaire and verifyAfterForceStop separately, with am force-stop between. */
class IdentityDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app get() = context.applicationContext as TalaApplication
    private val hh = HouseholdId("d0900000-0000-4000-8000-000000000091")
    private val maria = MemberId("d0900000-0000-4000-8000-000000000092")
    private val juan = MemberId("d0900000-0000-4000-8000-000000000093")
    private fun await(tag: String) = compose.waitUntil(30_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    private fun section(route: String, member: MemberId) {
        compose.onNodeWithTag("section-$route").performScrollTo().performClick()
        compose.waitUntil(30_000) { compose.onAllNodes(hasTestTag("section-$route") and isSelected()).fetchSemanticsNodes().isNotEmpty() }
        await("member-${member.value}")
        compose.onNodeWithTag("member-${member.value}").performScrollTo().performClick()
        compose.waitUntil(30_000) { compose.onAllNodes(hasTestTag("member-${member.value}") and isSelected()).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun input(key: String, text: String) = compose.onNodeWithTag("field-$key").performScrollTo().performTextReplacement(text)
    private fun option(key: String, text: String) = compose.onNodeWithTag("option-$key-$text").performScrollTo().performClick()
    private fun select(key: String, text: String) {
        compose.onNodeWithTag("field-$key").performScrollTo().performClick()
        compose.onNodeWithTag("option-$key-$text").performScrollTo().performClick()
    }
    private fun save() {
        compose.onNodeWithText("Save draft").performClick()
        compose.waitUntil(30_000) {
            compose.onAllNodesWithText("Saved on this device.").fetchSemanticsNodes().isNotEmpty()
        }
    }
    private fun open() {
        check(context.packageName.endsWith(".d08test"))
        await("nav-Households")
        compose.onNodeWithTag("nav-Households").performClick()
        await("household-search")
        compose.onNodeWithTag("household-search").performTextReplacement("Day Nine Identity")
        val code = runBlocking { app.repository.interviewRecord(hh)!!.household.displayCode }
        await("household-$code")
        compose.onNodeWithTag("household-$code").performScrollTo().performClick()
        await("open-questionnaire")
        compose.onNodeWithTag("open-questionnaire").performScrollTo().performClick()
        await("interview-workspace")
    }
    private fun capture(name: String) {
        val folder = File(context.getExternalFilesDir(null), "day09").apply { mkdirs() }
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        try { File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
    }

    @Test fun writeThroughQuestionnaire() {
        check(context.packageName.endsWith(".d08test"))
        runBlocking {
            val repo = app.repository
            assertEquals(WriteResult.Saved, repo.createHousehold(hh, HouseholdDraft("Day Nine Identity", "Fictional Day 9 Street", HouseholdStatus.InProgress)))
            for ((member, name) in listOf(maria to "Maria Santos", juan to "Juan Santos")) {
                assertEquals(WriteResult.Saved, repo.saveMember(hh, member, MemberDraft(name)))
                for (section in listOf(QuestionnaireSections.demographics, QuestionnaireSections.other, QuestionnaireSections.migration)) {
                    val before = InterviewRules.answers(repo.interviewRecord(hh)!!, section, member.value)
                    assertEquals(WriteResult.Saved, repo.saveDraftSection(hh, section.route, member, emptyMap(), before))
                }
            }
        }
        open()
        for ((member, first) in listOf(maria to "Maria", juan to "Juan")) {
            section("demographics", member)
            input("firstName", first); input("lastName", "Santos")
            option("registeredVoter", if (member == maria) "Yes" else "No")
            select("relationship", if (member == maria) "Head" else "Spouse")
            option("sex", if (member == maria) "Female" else "Male")
            input("dob", "2099-01-01")
            compose.onNodeWithText("Check answers").performClick()
            compose.onNodeWithTag("hint-dob", useUnmergedTree = true).performScrollTo().assertTextEquals("Date cannot be in the future.")
            input("dob", if (member == maria) "1990-05-15" else "1985-08-22")
            option("birth", "Yes"); select("civilStatus", "Married")
            save()
            section("other", member)
            option("nationalId", "Yes"); option("soloParent", "Yes"); input("soloParentId", "SP-$first")
            option("disability", "Yes"); option("registeredPwd", "Yes")
            compose.onNodeWithText("Check answers").performClick()
            compose.onNodeWithTag("hint-pwdId", useUnmergedTree = true).performScrollTo().assertTextEquals("Choose or enter an answer.")
            input("pwdId", "PWD-$first"); input("disabilityDetail", "Visual impairment")
            option("seniorId", "Not applicable"); save()
            if (member == maria) {
                option("registeredPwd", "No")
                compose.onNodeWithTag("field-pwdId").assertDoesNotExist()
                compose.onNodeWithTag("field-disabilityDetail").performScrollTo().assertTextContains("Visual impairment")
                option("registeredPwd", "Yes")
                compose.onNodeWithTag("field-pwdId").performScrollTo().assert(SemanticsMatcher.expectValue(
                    androidx.compose.ui.semantics.SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString("")))
                input("pwdId", "PWD-Maria")
            }
            section("migration", member)
            input("citizenship", "Filipino"); option("moved", "Yes")
            input("previousResidence", if (member == maria) "Cebu" else "Davao")
            input("moveYear", "1899")
            compose.onNodeWithText("Check answers").performClick()
            compose.onNodeWithTag("hint-moveYear", useUnmergedTree = true).performScrollTo().assertTextEquals("Enter at least 1900.")
            input("moveYear", if (member == maria) "2020" else "2021")
            option("overseas", if (member == maria) "Yes" else "No"); save()
        }
        section("other", maria)
        option("disability", "No"); option("soloParent", "No")
        listOf("registeredPwd", "pwdId", "disabilityDetail", "soloParentId").forEach { compose.onNodeWithTag("field-$it").assertDoesNotExist() }
        save(); capture("01-cleared-maria")
        section("other", juan)
        compose.onNodeWithTag("field-pwdId").performScrollTo().assertTextContains("PWD-Juan")
        capture("02-preserved-juan")
        section("migration", maria); option("moved", "No"); save()
        compose.onNodeWithTag("field-previousResidence").assertDoesNotExist()
        compose.onNodeWithTag("option-overseas-Yes").performScrollTo().assertIsSelected()
        capture("03-migration")
        compose.activityRule.scenario.recreate()
        await("interview-workspace")
        section("demographics", juan)
        compose.onNodeWithTag("field-firstName").performScrollTo().assertTextContains("Juan")
        assertStored()
    }

    private fun assertStored() = runBlocking {
        val record = app.repository.interviewRecord(hh)!!
        fun answers(section: SectionSpec, member: MemberId) = InterviewRules.answers(record, section, member.value)
        for (section in listOf(QuestionnaireSections.demographics, QuestionnaireSections.other, QuestionnaireSections.migration)) {
            for (member in listOf(maria, juan)) assertEquals("${section.route}/${member.value}", emptyMap<String, String>(), FormRules.validate(section, answers(section, member)))
        }
        assertEquals("1990-05-15", answers(QuestionnaireSections.demographics, maria).scalar("dob"))
        assertEquals("1985-08-22", answers(QuestionnaireSections.demographics, juan).scalar("dob"))
        assertEquals("Yes", answers(QuestionnaireSections.demographics, maria).scalar("registeredVoter"))
        assertEquals("No", answers(QuestionnaireSections.demographics, juan).scalar("registeredVoter"))
        assertEquals("PWD-Juan", answers(QuestionnaireSections.other, juan).scalar("pwdId"))
        assertEquals("SP-Juan", answers(QuestionnaireSections.other, juan).scalar("soloParentId"))
        listOf("registeredPwd", "pwdId", "disabilityDetail", "soloParentId").forEach { assertFalse(answers(QuestionnaireSections.other, maria).containsKey(it)) }
        listOf("previousResidence", "moveYear").forEach { assertFalse(answers(QuestionnaireSections.migration, maria).containsKey(it)) }
        assertEquals("Davao", answers(QuestionnaireSections.migration, juan).scalar("previousResidence"))
        assertEquals("Yes", answers(QuestionnaireSections.migration, maria).scalar("overseas"))
        assertEquals("No", answers(QuestionnaireSections.migration, juan).scalar("overseas"))
    }
    @Test fun verifyAfterForceStop() {
        check(context.packageName.endsWith(".d08test"))
        assertStored()
        open()
        section("other", maria)
        compose.onNodeWithTag("option-disability-No").performScrollTo().assertIsSelected()
        compose.onNodeWithTag("field-pwdId").assertDoesNotExist()
        section("other", juan)
        compose.onNodeWithTag("field-pwdId").performScrollTo().assertTextContains("PWD-Juan")
        section("migration", juan)
        compose.onNodeWithTag("field-previousResidence").performScrollTo().assertTextContains("Davao")
        capture("04-after-force-stop")
    }
}
