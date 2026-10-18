package ph.tala.registry.domain.form

import org.junit.Assert.*
import org.junit.Test
import ph.tala.registry.domain.model.*
import java.time.LocalDate

/**
 * Field specifications and validation for Disaster preparedness and Social protection,
 * the last two household-level questionnaire sections. Conditional fields need their
 * parent answers in the state to be visible.
 */
class DisasterProtectionValidationTest {

    private val today = LocalDate.of(2026, 10, 16)
    private fun value(text: String) = AnswerValue.Scalar(text)
    private fun error(field: FieldSpec, text: String, vararg parents: Pair<String, String>) =
        FormRules.error(field, parents.associate { it.first to AnswerValue.Scalar(it.second) } + (field.key to value(text)), today)

    @Test fun `both sections are household scoped with unique field keys`() {
        for (section in listOf(QuestionnaireSections.disaster, QuestionnaireSections.protection)) {
            assertFalse("${section.route} must not be member-scoped", section.scopedToMember)
            val keys = section.fields.map { it.key }
            assertEquals("${section.route} field keys must be unique", keys.size, keys.distinct().size)
            assertTrue("${section.route} must declare fields", keys.isNotEmpty())
        }
    }

    @Test fun `disaster type and emergency kit validation`() {
        val disaster = QuestionnaireSections.disaster
        val disasterType = disaster.fields.first { it.key == "disasterType" }
        val emergencyKit = disaster.fields.first { it.key == "emergencyKit" }

        assertNotNull("disasterType is required", error(disasterType, ""))
        assertNull(error(disasterType, "Natural"))
        assertNull(error(disasterType, "Man-made"))
        assertNull(error(disasterType, "Biological Disaster"))
        assertNotNull("disasterType rejects off-list answers", error(disasterType, "Meteor strike"))

        assertNotNull("emergencyKit is required", error(emergencyKit, ""))
        assertNull(error(emergencyKit, "Yes"))
        assertNull(error(emergencyKit, "No"))
        assertNotNull("emergencyKit rejects off-list answers", error(emergencyKit, "Partly"))
    }

    @Test fun `hazards multi select validation and exclusivity`() {
        val hazards = QuestionnaireSections.disaster.fields.first { it.key == "hazards" }

        fun hazardsError(selected: List<String>) =
            FormRules.error(hazards, mapOf("hazards" to AnswerValue.Multi(selected)), today)

        assertNotNull("hazards is required", hazardsError(emptyList()))
        assertNull(hazardsError(listOf("Flood")))
        assertNull(hazardsError(listOf("Typhoon", "Earthquake", "Fire")))
        assertNull(hazardsError(listOf("None")))
        assertNotNull("None cannot coexist with other selections", hazardsError(listOf("Flood", "None")))
        assertNotNull("hazards rejects off-list options", hazardsError(listOf("Volcanic eruption")))
        assertNotNull("hazards rejects duplicate selections", hazardsError(listOf("Flood", "Flood")))
    }

    @Test fun `evacuation branch validation with nested other text`() {
        val disaster = QuestionnaireSections.disaster
        val evacuationKnown = disaster.fields.first { it.key == "evacuationKnown" }
        val evacuation = disaster.fields.first { it.key == "evacuation" }
        val evacuationOther = disaster.fields.first { it.key == "evacuationOther" }

        assertNotNull("evacuationKnown is required", error(evacuationKnown, ""))
        assertNull(error(evacuationKnown, "Yes"))
        assertNull(error(evacuationKnown, "No"))

        // evacuation visible only when evacuationKnown = Yes
        assertNotNull("evacuation is required when visible", error(evacuation, "", "evacuationKnown" to "Yes"))
        assertNull(error(evacuation, "Barangay Hall (Sample)", "evacuationKnown" to "Yes"))
        assertNull(error(evacuation, "School Evacuation Center (Sample)", "evacuationKnown" to "Yes"))
        assertNull(error(evacuation, "Covered Court (Sample)", "evacuationKnown" to "Yes"))
        assertNull("evacuation stays valid when hidden", error(evacuation, "", "evacuationKnown" to "No"))
        assertNotNull("evacuation rejects off-list answers", error(evacuation, "The mall", "evacuationKnown" to "Yes"))

        // evacuationOther visible only when evacuationKnown = Yes AND evacuation = Other
        assertNotNull("evacuationOther is required when visible",
            error(evacuationOther, "", "evacuationKnown" to "Yes", "evacuation" to "Other"))
        assertNull(error(evacuationOther, "Bgy. Multi-purpose building", "evacuationKnown" to "Yes", "evacuation" to "Other"))
        assertNull("evacuationOther stays valid when only one parent is met",
            error(evacuationOther, "", "evacuationKnown" to "Yes", "evacuation" to "Barangay Hall (Sample)"))
    }

    @Test fun `sample evacuation sites stay visibly marked for the demo`() {
        // Real-world evacuation areas are unresolved; the spec keeps sample labels and a hint.
        val evacuation = QuestionnaireSections.disaster.fields.first { it.key == "evacuation" }
        val sampleSites = evacuation.options.filter { it != "Other" }
        assertTrue("at least one sample site must exist", sampleSites.isNotEmpty())
        for (site in sampleSites) {
            assertTrue("sample site '$site' must keep its visible marker", site.contains("(Sample)"))
        }
        assertNotNull("evacuation field must carry the unresolved-choice hint", evacuation.hint)
        assertTrue(evacuation.hint!!.contains("Sample"))
    }

    @Test fun `phil health role branch validation`() {
        val protection = QuestionnaireSections.protection
        val philHealth = protection.fields.first { it.key == "philHealth" }
        val philHealthRole = protection.fields.first { it.key == "philHealthRole" }

        assertNotNull("philHealth is required", error(philHealth, ""))
        assertNull(error(philHealth, "Yes"))
        assertNull(error(philHealth, "No"))

        assertNotNull("philHealthRole is required when visible", error(philHealthRole, "", "philHealth" to "Yes"))
        assertNull(error(philHealthRole, "Member", "philHealth" to "Yes"))
        assertNull(error(philHealthRole, "Dependent", "philHealth" to "Yes"))
        assertNull("philHealthRole stays valid when hidden", error(philHealthRole, "", "philHealth" to "No"))
        assertNotNull("philHealthRole rejects off-list answers", error(philHealthRole, "Beneficiary", "philHealth" to "Yes"))
    }

    @Test fun `assistance branches without silently resolving the source note`() {
        val protection = QuestionnaireSections.protection
        val dswd = protection.fields.first { it.key == "dswd" }
        val fourPs = protection.fields.first { it.key == "fourPs" }
        val assistance = protection.fields.first { it.key == "assistance" }
        val assistanceDetail = protection.fields.first { it.key == "assistanceDetail" }

        for (field in listOf(dswd, fourPs, assistance)) {
            assertNotNull("${field.key} is required", error(field, ""))
            assertNull(error(field, "Yes"))
            assertNull(error(field, "No"))
            assertNotNull("${field.key} rejects off-list answers", error(field, "Sometimes"))
        }

        assertNotNull("assistanceDetail is required when visible", error(assistanceDetail, "", "assistance" to "Yes"))
        assertNull(error(assistanceDetail, "Ayuda", "assistance" to "Yes"))
        assertNull("assistanceDetail stays valid when hidden", error(assistanceDetail, "", "assistance" to "No"))
    }

    @Test fun `programs multi select validation and exclusivity`() {
        val programs = QuestionnaireSections.protection.fields.first { it.key == "programs" }

        fun programsError(selected: List<String>) =
            FormRules.error(programs, mapOf("programs" to AnswerValue.Multi(selected)), today)

        assertNotNull("programs is required", programsError(emptyList()))
        assertNull(programsError(listOf("PhilHealth")))
        assertNull(programsError(listOf("SSS", "GSIS", "4Ps")))
        assertNull(programsError(listOf("Senior citizen assistance", "PWD assistance")))
        assertNull(programsError(listOf("None")))
        assertNull(programsError(listOf("Prefer not to answer")))
        assertNotNull("None cannot coexist with other selections", programsError(listOf("SSS", "None")))
        assertNotNull("Prefer not to answer cannot coexist with other selections",
            programsError(listOf("GSIS", "Prefer not to answer")))
        assertNotNull("programs rejects off-list options", programsError(listOf("TUPAD")))
        assertNotNull("programs rejects duplicate selections", programsError(listOf("4Ps", "4Ps")))
    }

    @Test fun `validate reports only visible disaster fields`() {
        val disaster = QuestionnaireSections.disaster
        // evacuationKnown answered Yes, evacuation not yet: only evacuation is newly required.
        val errors = FormRules.validate(
            disaster,
            mapOf(
                "disasterType" to value("Natural"),
                "hazards" to AnswerValue.Multi(listOf("Flood")),
                "evacuationKnown" to value("Yes"),
                "emergencyKit" to value("No"),
            ),
            today,
        )
        assertEquals("only the newly visible evacuation field is flagged", setOf("evacuation"), errors.keys)

        val hidden = FormRules.validate(disaster, emptyMap(), today)
        assertEquals("only unconditional fields flagged while evacuation branch hidden",
            setOf("disasterType", "hazards", "evacuationKnown", "emergencyKit"), hidden.keys)
    }

    @Test fun `validate reports only visible protection fields`() {
        val protection = QuestionnaireSections.protection
        val errors = FormRules.validate(
            protection,
            mapOf(
                "philHealth" to value("Yes"),
                "philHealthRole" to value("Member"),
                "dswd" to value("No"),
                "fourPs" to value("No"),
                "programs" to AnswerValue.Multi(listOf("4Ps")),
                "assistance" to value("Yes"),
            ),
            today,
        )
        assertEquals("only the newly visible assistanceDetail field is flagged", setOf("assistanceDetail"), errors.keys)

        val hidden = FormRules.validate(protection, emptyMap(), today)
        assertEquals("only unconditional fields flagged while dependent fields hidden",
            setOf("philHealth", "dswd", "fourPs", "programs", "assistance"), hidden.keys)
    }
}
