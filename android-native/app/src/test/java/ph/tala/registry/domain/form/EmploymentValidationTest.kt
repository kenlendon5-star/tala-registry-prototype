package ph.tala.registry.domain.form

import org.junit.Assert.*
import org.junit.Test
import ph.tala.registry.domain.model.*
import java.time.LocalDate

/**
 * Tests for the Employment section's field specifications and validation rules.
 * Conditional fields need their parent answers in the state to be visible.
 */
class EmploymentValidationTest {

    private val today = LocalDate.of(2026, 10, 5)
    private fun value(text: String) = AnswerValue.Scalar(text)
    private fun error(field: FieldSpec, text: String, vararg parents: Pair<String, String>) =
        FormRules.error(field, parents.associate { it.first to AnswerValue.Scalar(it.second) } + (field.key to value(text)), today)

    @Test fun `employment section field specifications and validation`() {
        val employment = QuestionnaireSections.employment

        // Test that all expected fields exist
        val expectedFields = listOf(
            "worked", "occupation", "industry", "employmentType",
            "hours", "looking", "notWorkingReason", "available"
        )
        val actualFields = employment.fields.map { it.key }
        assertTrue("Missing fields in employment section", expectedFields.all { actualFields.contains(it) })
        assertTrue("Employment section must be member-scoped", employment.scopedToMember)
        assertEquals("Field keys must be unique", employment.fields.size, actualFields.distinct().size)

        // Test conditional visibility and validation
        val workedField = employment.fields.first { it.key == "worked" }
        val occupationField = employment.fields.first { it.key == "occupation" }
        val industryField = employment.fields.first { it.key == "industry" }
        val employmentTypeField = employment.fields.first { it.key == "employmentType" }
        val hoursField = employment.fields.first { it.key == "hours" }
        val lookingField = employment.fields.first { it.key == "looking" }
        val notWorkingReasonField = employment.fields.first { it.key == "notWorkingReason" }
        val availableField = employment.fields.first { it.key == "available" }

        // When worked = Yes, occupation/industry/employmentType/hours visible
        assertNull(error(workedField, "Yes"))
        assertNull(error(occupationField, "Software Engineer", "worked" to "Yes"))
        assertNull(error(industryField, "IT", "worked" to "Yes"))
        assertNull(error(employmentTypeField, "Employee", "worked" to "Yes"))
        assertNull(error(hoursField, "40", "worked" to "Yes"))
        // looking, notWorkingReason, available should be hidden
        assertNull(error(lookingField, "", "worked" to "Yes"))
        assertNull(error(notWorkingReasonField, "", "worked" to "Yes"))
        assertNull(error(availableField, "", "worked" to "Yes"))

        // When worked = No, looking and notWorkingReason visible
        assertNull(error(workedField, "No"))
        assertNull(error(lookingField, "Yes", "worked" to "No"))
        assertNull(error(notWorkingReasonField, "Student", "worked" to "No"))
        // occupation, industry, employmentType, hours should be hidden
        assertNull(error(occupationField, "", "worked" to "No"))
        assertNull(error(industryField, "", "worked" to "No"))
        assertNull(error(employmentTypeField, "", "worked" to "No"))
        assertNull(error(hoursField, "", "worked" to "No"))

        // When worked = No, looking = No -> available hidden
        assertNull(error(lookingField, "No", "worked" to "No"))
        assertNull(error(availableField, "Yes", "worked" to "No", "looking" to "No")) // Hidden, so valid even with a value

        // When worked = No, looking = Yes -> available visible
        assertNull(error(availableField, "Yes", "worked" to "No", "looking" to "Yes"))
        assertNotNull(error(availableField, "", "worked" to "No", "looking" to "Yes"))

        // Test hours validation (number range 0-168)
        assertNotNull(error(hoursField, "-1", "worked" to "Yes")) // Below minimum
        assertNotNull(error(hoursField, "169", "worked" to "Yes")) // Above maximum
        assertNull(error(hoursField, "0", "worked" to "Yes")) // At minimum
        assertNull(error(hoursField, "168", "worked" to "Yes")) // At maximum
        assertNotNull(error(hoursField, "abc", "worked" to "Yes")) // Not a number
        assertNotNull(error(hoursField, "40.5", "worked" to "Yes")) // Not whole number

        // When hours hidden (worked=No), invalid values should not error
        assertNull(error(hoursField, "-1", "worked" to "No")) // Hidden

        // Test required vs optional
        assertNotNull(error(workedField, "")) // Required Choice
        assertNotNull(error(lookingField, "", "worked" to "No")) // Required when visible
        assertNotNull(error(notWorkingReasonField, "", "worked" to "No")) // Required when visible

        // Test invalid option rejection
        assertNotNull(error(employmentTypeField, "Invalid Type", "worked" to "Yes"))
        assertNotNull(error(notWorkingReasonField, "Invalid Reason", "worked" to "No"))
        assertNotNull(error(lookingField, "Maybe", "worked" to "No"))
    }
}
