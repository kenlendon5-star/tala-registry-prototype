package ph.tala.registry.domain.form

import org.junit.Assert.*
import org.junit.Test
import ph.tala.registry.domain.model.*
import java.time.LocalDate

/**
 * Tests for conditional branch clearing in the Education and Employment sections.
 * This validates that changing parent choices properly clears dependent hidden answers.
 */
class EducationEmploymentBranchingTest {

    private val today = LocalDate.of(2026, 10, 5)
    private fun value(text: String) = AnswerValue.Scalar(text)

    @Test fun `education attending branch clearing`() {
        val education = QuestionnaireSections.education

        val stateWithAttendingYes = mapOf(
            "attending" to value("Yes"),
            "school" to value("Public"),
            "track" to value("STEM")
        )

        val clearedState = FormRules.update(education, stateWithAttendingYes, "attending", value("No"))

        assertFalse("school should be cleared when attending changes to No", clearedState.containsKey("school"))
        assertFalse("track should be cleared when attending changes to No", clearedState.containsKey("track"))
        assertEquals("No", clearedState.scalar("attending"))
    }

    @Test fun `education attending No branch clearing`() {
        val education = QuestionnaireSections.education

        val stateWithAttendingNo = mapOf(
            "attending" to value("No"),
            "reason" to value("Financial concerns")
        )

        val clearedState = FormRules.update(education, stateWithAttendingNo, "attending", value("Yes"))

        assertFalse("reason should be cleared when attending changes to Yes", clearedState.containsKey("reason"))
        assertEquals("Yes", clearedState.scalar("attending"))
    }

    @Test fun `employment worked branch clearing`() {
        val employment = QuestionnaireSections.employment

        val stateWithWorkedYes = mapOf(
            "worked" to value("Yes"),
            "occupation" to value("Software Engineer"),
            "industry" to value("IT"),
            "employmentType" to value("Employee"),
            "hours" to value("40")
        )

        val clearedState = FormRules.update(employment, stateWithWorkedYes, "worked", value("No"))

        assertFalse("occupation should be cleared when worked changes to No", clearedState.containsKey("occupation"))
        assertFalse("industry should be cleared when worked changes to No", clearedState.containsKey("industry"))
        assertFalse("employmentType should be cleared when worked changes to No", clearedState.containsKey("employmentType"))
        assertFalse("hours should be cleared when worked changes to No", clearedState.containsKey("hours"))
        assertEquals("No", clearedState.scalar("worked"))
    }

    @Test fun `employment worked No branch clearing`() {
        val employment = QuestionnaireSections.employment

        val stateWithWorkedNo = mapOf(
            "worked" to value("No"),
            "looking" to value("Yes"),
            "notWorkingReason" to value("Student"),
            "available" to value("Yes")
        )

        val clearedState = FormRules.update(employment, stateWithWorkedNo, "worked", value("Yes"))

        assertFalse("looking should be cleared when worked changes to Yes", clearedState.containsKey("looking"))
        assertFalse("notWorkingReason should be cleared when worked changes to Yes", clearedState.containsKey("notWorkingReason"))
        assertFalse("available should be cleared when worked changes to Yes", clearedState.containsKey("available"))
        assertEquals("Yes", clearedState.scalar("worked"))
    }

    @Test fun `employment looking branch clearing`() {
        val employment = QuestionnaireSections.employment

        val stateWithLookingYes = mapOf(
            "worked" to value("No"),
            "looking" to value("Yes"),
            "notWorkingReason" to value("Student"),
            "available" to value("Yes")
        )

        val clearedState = FormRules.update(employment, stateWithLookingYes, "looking", value("No"))

        assertFalse("available should be cleared when looking changes to No", clearedState.containsKey("available"))
        assertTrue("notWorkingReason should remain when only looking changes", clearedState.containsKey("notWorkingReason"))
        assertEquals("No", clearedState.scalar("worked"))
        assertEquals("No", clearedState.scalar("looking"))
    }

    @Test fun `employment hours validation respects visibility`() {
        val employment = QuestionnaireSections.employment
        val hoursField = employment.fields.first { it.key == "hours" }

        // When worked = No, invalid hours values should not cause errors (field hidden)
        val stateWithInvalidHoursButWorkedNo = mapOf(
            "worked" to value("No"),
            "hours" to value("169")
        )
        assertNull(FormRules.error(hoursField, stateWithInvalidHoursButWorkedNo, today))

        // When worked = Yes, invalid hours values should cause errors (field visible)
        val stateWithInvalidHoursButWorkedYes = mapOf(
            "worked" to value("Yes"),
            "hours" to value("169")
        )
        assertNotNull("Should produce error for invalid hours when field is visible",
            FormRules.error(hoursField, stateWithInvalidHoursButWorkedYes, today))

        // Test negative hours
        val stateNegativeHours = mapOf("worked" to value("Yes"), "hours" to value("-1"))
        assertNotNull(FormRules.error(hoursField, stateNegativeHours, today))

        // When worked = No, negative hours should not error (hidden)
        val stateNegativeHoursHidden = mapOf("worked" to value("No"), "hours" to value("-1"))
        assertNull(FormRules.error(hoursField, stateNegativeHoursHidden, today))
    }
}