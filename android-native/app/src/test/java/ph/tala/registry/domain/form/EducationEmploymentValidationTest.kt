package ph.tala.registry.domain.form

import org.junit.Assert.*
import org.junit.Test
import ph.tala.registry.domain.model.*
import java.time.LocalDate

/**
 * Tests for the Education section's field specifications and validation rules.
 * Conditional fields need their parent answers in the state to be visible.
 */
class EducationEmploymentValidationTest {

    private val today = LocalDate.of(2026, 10, 5)
    private fun value(text: String) = AnswerValue.Scalar(text)
    private fun error(field: FieldSpec, text: String, vararg parents: Pair<String, String>) =
        FormRules.error(field, parents.associate { it.first to AnswerValue.Scalar(it.second) } + (field.key to value(text)), today)

    @Test fun `education section field specifications and validation`() {
        val education = QuestionnaireSections.education

        // Test that all expected fields exist
        val expectedFields = listOf(
            "attending", "school", "reason", "grade",
            "educationAssistance", "track"
        )
        val actualFields = education.fields.map { it.key }
        assertTrue("Missing fields in education section", expectedFields.all { actualFields.contains(it) })
        assertTrue("Education section must be member-scoped", education.scopedToMember)
        assertEquals("Field keys must be unique", education.fields.size, actualFields.distinct().size)

        // Test conditional visibility and validation
        val attendingField = education.fields.first { it.key == "attending" }
        val schoolField = education.fields.first { it.key == "school" }
        val reasonField = education.fields.first { it.key == "reason" }
        val trackField = education.fields.first { it.key == "track" }

        // When attending = Yes, school and track should be visible
        assertNull(error(attendingField, "Yes"))
        assertNull(error(schoolField, "Public", "attending" to "Yes"))
        assertNull(error(trackField, "STEM", "attending" to "Yes"))
        // reason should be hidden when attending = Yes
        assertNull(error(reasonField, "", "attending" to "Yes"))

        // When attending = No, reason should be visible
        assertNull(error(attendingField, "No"))
        assertNull(error(reasonField, "Financial concerns", "attending" to "No"))
        // school and track should be hidden when attending = No
        assertNull(error(schoolField, "", "attending" to "No"))
        assertNull(error(trackField, "", "attending" to "No"))

        // Test required vs optional fields
        assertNotNull(error(attendingField, "")) // Required
        assertNotNull(error(schoolField, "", "attending" to "Yes")) // Required when visible
        assertNull(error(schoolField, "Public", "attending" to "Yes"))
        assertNull(error(trackField, "", "attending" to "Yes")) // Optional when visible
        assertNull(error(trackField, "Custom Track", "attending" to "Yes"))

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
        assertNotNull(error(schoolField, "Invalid School", "attending" to "Yes"))
        assertNotNull(error(reasonField, "Invalid Reason", "attending" to "No"))
        assertNotNull(error(gradeField, "Invalid Grade"))
        assertNotNull(error(eduAssistField, "Maybe"))
    }
}
