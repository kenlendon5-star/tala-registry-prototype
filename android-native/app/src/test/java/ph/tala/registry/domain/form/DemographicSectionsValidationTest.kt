package ph.tala.registry.domain.form

import org.junit.Assert.*
import org.junit.Test
import ph.tala.registry.domain.model.*
import java.time.LocalDate

class DemographicSectionsValidationTest {
    private val today = LocalDate.of(2026, 10, 13)
    private fun answers(vararg pairs: Pair<String, String>): AnswerState = pairs.associate { it.first to AnswerValue.Scalar(it.second) }
    private fun error(section: SectionSpec, key: String, text: String, vararg parents: Pair<String, String>) =
        FormRules.error(section.fields.single { it.key == key }, answers(*parents) + (key to AnswerValue.Scalar(text)), today)

    @Test fun identitySectionsAreMemberScoped() {
        listOf(QuestionnaireSections.demographics, QuestionnaireSections.other, QuestionnaireSections.migration).forEach {
            assertTrue(it.scopedToMember)
            assertEquals(it.fields.size, it.fields.map { field -> field.key }.distinct().size)
        }
    }
    @Test fun birthDateRejectsFutureImpossibleAndMissingDates() {
        val section = QuestionnaireSections.demographics
        listOf("", "2026-10-14", "2025-02-29", "2000-13-01", "not a date").forEach { assertNotNull(it, error(section, "dob", it)) }
        listOf("2026-10-13", "2000-02-29", "1990-05-15").forEach { assertNull(it, error(section, "dob", it)) }
    }
    @Test fun voterAndOverseasRequireAnExplicitValidChoice() {
        listOf(QuestionnaireSections.demographics to "registeredVoter", QuestionnaireSections.migration to "overseas").forEach { (section, key) ->
            listOf("Yes", "No").forEach { assertNull(error(section, key, it)) }
            listOf("", "Maybe").forEach { assertNotNull(error(section, key, it)) }
        }
    }
    @Test fun optionalPhoneAndNamesRespectTheirActualRequirements() {
        val section = QuestionnaireSections.demographics
        listOf("", "09171234567", "0917-123-4567").forEach { assertNull(error(section, "phone", it)) }
        listOf("0917123456", "091712345678", "abc09171234567").forEach { assertNotNull(error(section, "phone", it)) }
        listOf("firstName", "lastName").forEach { assertNotNull(error(section, it, "")); assertNull(error(section, it, "Reyes")) }
        listOf("middleName", "alias", "extension").forEach { assertNull(error(section, it, "")) }
    }
    @Test fun soloParentIdIsOptionalAndOnlyVisibleForYes() {
        val section = QuestionnaireSections.other
        val id = section.fields.single { it.key == "soloParentId" }
        assertFalse(id.isVisible(answers("soloParent" to "No")))
        assertTrue(id.isVisible(answers("soloParent" to "Yes")))
        assertNull(error(section, "soloParentId", "", "soloParent" to "Yes"))
        assertNull(error(section, "soloParentId", "SP-123", "soloParent" to "Yes"))
        assertNotNull(error(section, "soloParent", ""))
        assertNotNull(error(section, "soloParent", "Maybe"))
    }
    @Test fun disabilityRegistrationAndIdValidateAllParentCombinations() {
        val section = QuestionnaireSections.other
        assertNotNull(error(section, "disability", ""))
        assertNotNull(error(section, "disability", "Maybe"))
        for (disability in listOf("Yes", "No")) for (registered in listOf("Yes", "No")) {
            val parents = arrayOf("disability" to disability, "registeredPwd" to registered)
            val requiredId = disability == "Yes" && registered == "Yes"
            assertEquals(requiredId, error(section, "pwdId", "", *parents) != null)
            assertNull(error(section, "pwdId", "PWD-123", *parents))
            assertEquals(disability == "Yes", error(section, "disabilityDetail", "", *parents) != null)
            assertNull(error(section, "disabilityDetail", "Visual impairment", *parents))
        }
        assertNotNull(error(section, "registeredPwd", "", "disability" to "Yes"))
        assertNotNull(error(section, "registeredPwd", "Maybe", "disability" to "Yes"))
        assertNull(error(section, "registeredPwd", "", "disability" to "No"))
    }
    @Test fun migrationRequiresResidenceAndValidYearOnlyWhenMoved() {
        val section = QuestionnaireSections.migration
        listOf("", "1899", "2027", "not_a_number", "2020.5").forEach { assertNotNull(it, error(section, "moveYear", it, "moved" to "Yes")) }
        listOf("1900", "2020", "2026").forEach { assertNull(error(section, "moveYear", it, "moved" to "Yes")) }
        assertNotNull(error(section, "previousResidence", "", "moved" to "Yes"))
        assertNull(error(section, "previousResidence", "Cebu", "moved" to "Yes"))
        assertNull(error(section, "previousResidence", "", "moved" to "No"))
        assertNull(error(section, "moveYear", "invalid", "moved" to "No"))
        assertNotNull(error(section, "citizenship", ""))
        assertNotNull(error(section, "moved", "Maybe"))
    }
}
