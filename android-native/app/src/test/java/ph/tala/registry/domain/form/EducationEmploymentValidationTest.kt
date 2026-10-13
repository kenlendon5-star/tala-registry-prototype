package ph.tala.registry.domain.form

import org.junit.Assert.*
import org.junit.Test
import ph.tala.registry.domain.model.*
import java.time.LocalDate

/**
 * Tests for the Education section's field specifications and validation rules.
 */
class EducationEmploymentValidationTest {

    private val today = LocalDate.of(2026, 10, 5)
    private fun value(text: String) = AnswerValue.Scalar(text)
    private fun error(field: FieldSpec, text: String) = FormRules.error(field, mapOf(field.key to value(text)), today)

    @Test fun `education section field specifications and validation`() {
        val education = QuestionnaireSections.education

        // Test that all expected fields exist
        val expectedFields = listOf(
            "attending", "school", "reason", "grade",
            "educationAssistance", "track"
        )
        val actualFields = education.fields.map { it.key }
        assertTrue("Missing fields in education section", expectedFields.all { actualFields.contains(it) })

        // Test conditional visibility and validation
        val attendingField = education.fields.first { it.key == "attending" }
        val schoolField = education.fields.first { it.key == "school" }
        val reasonField = education.fields.first { it.key == "reason" }
        val trackField = education.fields.first { it.key == "track" }

        // When attending = Yes, school and track should be visible
        val stateAttendingYes = mapOf(
            "attending" to value("Yes"),
            "school" to value("Public"),
            "track" to value("STEM")
        )
        assertNull(error(attendingField, "Yes"))
        assertNull(error(schoolField, "Public"))
        assertNull(error(trackField, "STEM"))
        // reason should be hidden when attending = Yes
        assertNull(error(reasonField, ""))

        // When attending = No, reason should be visible
        val stateAttendingNo = mapOf(
            "attending" to value("No"),
            "reason" to value("Financial concerns")
        )
        assertNull(error(attendingField, "No"))
        assertNull(error(reasonField, "Financial concerns"))
        // school and track should be hidden when attending = No
        assertNull(error(schoolField, ""))
        assertNull(error(trackField, ""))

        // Test required vs optional fields
        assertNotNull(error(attendingField, "")) // Required
        val stateSchoolVisible = mapOf("attending" to value("Yes"))
        assertNotNull(error(schoolField, "")) // Required when visible
        assertNull(error(schoolField, "Public"))
        assertNull(error(trackField, "")) // Optional
        assertNull(error(trackField, "Custom Track"))

        // Test grade field (always visible, Select, required)
        val gradeField = education.fields.first { it.key == "grade" }
        assertNotNull(error(gradeField, ""))
        assertNull(error(gradeField, "Grade 10"))
        assertNull(error(gradeField, "College graduate"))

        // Test educationAssistance field (always visible, Choice Yes/No, required)
        val eduAssistField = education.fields.first { it.key == "educationAssistance" }
        assertNotNull(error(eduAssistField, ""))
        assertNull(error(eduAssistField, "Yes"))
        assertNull(error(eduAssistField, "No"))

        // Test invalid option rejection for Select/Choice fields
        assertNotNull(error(schoolField, "Invalid School"))
        assertNotNull(error(reasonField, "Invalid Reason"))
        assertNotNull(error(gradeField, "Invalid Grade"))
        assertNotNull(error(eduAssistField, "Maybe"))
    }