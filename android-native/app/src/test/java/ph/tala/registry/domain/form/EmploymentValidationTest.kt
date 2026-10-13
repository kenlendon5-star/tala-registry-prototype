package ph.tala.registry.domain.form

import org.junit.Assert.*
import org.junit.Test
import ph.tala.registry.domain.model.*
import java.time.LocalDate

/**
 * Tests for the Employment section's field specifications and validation rules.
 */
class EmploymentValidationTest {

    private val today = LocalDate.of(2026, 10, 5)
    private fun value(text: String) = AnswerValue.Scalar(text)
    private fun error(field: FieldSpec, text: String) = FormRules.error(field, mapOf(field.key to value(text)), today)

    @Test fun `employment section field specifications and validation`() {
        val employment = QuestionnaireSections.employment

        // Test that all expected fields exist
        val expectedFields = listOf(
            "worked", "occupation", "industry", "employmentType",
            "hours", "looking", "notWorkingReason", "available"
        )
        val actualFields = employment.fields.map { it.key }
        assertTrue("Missing fields in employment section", expectedFields.all { actualFields.contains(it) })

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
        val stateWorkedYes = mapOf(
            "worked" to value("Yes"),
            "occupation" to value("Software Engineer"),
            "industry" to value("IT"),
            "employmentType" to value("Employee"),
            "hours" to value("40")
        )
        assertNull(error(workedField, "Yes"))
        assertNull(error(occupationField, "Software Engineer"))
        assertNull(error(industryField, "IT"))
        assertNull(error(employmentTypeField, "Employee"))
        assertNull(error(hoursField, "40"))
        // looking, notWorkingReason, available should be hidden
        assertNull(error(lookingField, ""))
        assertNull(error(notWorkingReasonField, ""))
        assertNull(error(availableField, ""))

        // When worked = No, looking and notWorkingReason visible
        val stateWorkedNo = mapOf(
            "worked" to value("No"),
            "looking" to value("Yes"),
            "notWorkingReason" to value("Student"),
            "available" to value("Yes")
        )
        assertNull(error(workedField, "No"))
        assertNull(error(lookingField, "Yes"))
        assertNull(error(notWorkingReasonField, "Student"))
        // available visible only when worked=No AND looking=Yes
        assertNull(error(availableField, "Yes"))
        // occupation, industry, employmentType, hours should be hidden
        assertNull(error(occupationField, ""))
        assertNull(error(industryField, ""))
        assertNull(error(employmentTypeField, ""))
        assertNull(error(hoursField, "")

        // When worked = No, looking = No -> available hidden
        val stateWorkedNoLookingNo = mapOf(
            "worked" to value("No"),
            "looking" to value("No"),
            "available" to value("Yes")
        )
        assertNull(error(lookingField, "No"))
        assertNull(error(availableField, "Yes")) // Hidden, so valid even with value

        // Test hours validation (number range 0-168)
        assertNotNull(error(hoursField, "-1")) // Below minimum
        assertNotNull(error(hoursField, "169")) // Above maximum
        assertNull(error(hoursField, "0")) // At minimum
        assertNull(error(hoursField, "168")) // At maximum
        assertNotNull(error(hoursField, "abc")) // Not a number
        assertNotNull(error(hoursField, "40.5")) // Not whole number

        // When hours hidden (worked=No), invalid values should not error
        val stateHoursHidden = mapOf(
            "worked" to value("No"),
            "hours" to value("-1")
        )
        assertNull(error(hoursField, "-1")) // Hidden

        // Test required vs optional
        assertNotNull(error(workedField, "")) // Required Choice
        assertNotNull(error(lookingField, "")) // Required when visible (worked=No)
        assertNotNull(error(notWorkingReasonField, "")) // Required when visible
        assertNotNull(error(availableField, "")) // Required when visible (worked=No, looking=Yes)

        // Test invalid option rejection
        assertNotNull(error(employmentTypeField, "Invalid Type"))
        assertNotNull(error(notWorkingReasonField, "Invalid Reason"))
        assertNotNull(error(lookingField, "Maybe"))
    }
}