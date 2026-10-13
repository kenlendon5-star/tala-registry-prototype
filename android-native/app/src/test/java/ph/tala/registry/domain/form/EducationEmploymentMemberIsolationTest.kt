package ph.tala.registry.domain.form

import org.junit.Assert.*
import org.junit.Test
import ph.tala.registry.domain.model.*
import ph.tala.registry.ui.form.FormSessionViewModel
import ph.tala.registry.domain.model.AnswerScope

/**
 * Tests for member switching and answer isolation in the Education and Employment sections.
 * This validates that answers for different members are kept isolated when using member-scoped sections.
 */
class EducationEmploymentMemberIsolationTest {

    private val household = HouseholdId("d1000000-0000-4000-8000-000000000001")
    private val maria = AnswerScope.Member(household, MemberId("d1000000-0000-4000-8000-000000000002"))
    private val juan = AnswerScope.Member(household, MemberId("d1000000-0000-4000-8000-000000000003"))

    @Test fun `education section member switching keeps answers isolated`() {
        val model = FormSessionViewModel()

        model.answer(QuestionnaireSections.education, maria, "attending", AnswerValue.Scalar("Yes"))
        model.answer(QuestionnaireSections.education, maria, "school", AnswerValue.Scalar("Private"))
        model.answer(QuestionnaireSections.education, maria, "track", AnswerValue.Scalar("STEM"))
        model.answer(QuestionnaireSections.education, maria, "grade", AnswerValue.Scalar("Grade 10"))
        model.answer(QuestionnaireSections.education, maria, "educationAssistance", AnswerValue.Scalar("Yes"))

        model.answer(QuestionnaireSections.education, juan, "attending", AnswerValue.Scalar("No"))
        model.answer(QuestionnaireSections.education, juan, "reason", AnswerValue.Scalar("Working"))
        model.answer(QuestionnaireSections.education, juan, "grade", AnswerValue.Scalar("College graduate"))
        model.answer(QuestionnaireSections.education, juan, "educationAssistance", AnswerValue.Scalar("No"))

        assertEquals("Yes", model.state.value.form(QuestionnaireSections.education, maria).answers.scalar("attending"))
        assertEquals("Private", model.state.value.form(QuestionnaireSections.education, maria).answers.scalar("school"))
        assertEquals("STEM", model.state.value.form(QuestionnaireSections.education, maria).answers.scalar("track"))
        assertEquals("Grade 10", model.state.value.form(QuestionnaireSections.education, maria).answers.scalar("grade"))
        assertEquals("Yes", model.state.value.form(QuestionnaireSections.education, maria).answers.scalar("educationAssistance"))
        assertFalse("Maria's reason should not be set",
            model.state.value.form(QuestionnaireSections.education, maria).answers.containsKey("reason"))

        assertEquals("No", model.state.value.form(QuestionnaireSections.education, juan).answers.scalar("attending"))
        assertEquals("Working", model.state.value.form(QuestionnaireSections.education, juan).answers.scalar("reason"))
        assertEquals("College graduate", model.state.value.form(QuestionnaireSections.education, juan).answers.scalar("grade"))
        assertEquals("No", model.state.value.form(QuestionnaireSections.education, juan).answers.scalar("educationAssistance"))
        assertFalse("Juan's school should not be set",
            model.state.value.form(QuestionnaireSections.education, juan).answers.containsKey("school"))
        assertFalse("Juan's track should not be set",
            model.state.value.form(QuestionnaireSections.education, juan).answers.containsKey("track"))

        model.answer(QuestionnaireSections.education, maria, "attending", AnswerValue.Scalar("No"))
        model.answer(QuestionnaireSections.education, maria, "reason", AnswerValue.Scalar("Not yet school age"))
        assertEquals("No", model.state.value.form(QuestionnaireSections.education, maria).answers.scalar("attending"))
        assertEquals("Not yet school age", model.state.value.form(QuestionnaireSections.education, maria).answers.scalar("reason"))
        assertEquals("No", model.state.value.form(QuestionnaireSections.education, juan).answers.scalar("attending"))
        assertEquals("Working", model.state.value.form(QuestionnaireSections.education, juan).answers.scalar("reason"))
    }

    @Test fun `employment section member switching keeps answers isolated`() {
        val model = FormSessionViewModel()

        model.answer(QuestionnaireSections.employment, maria, "worked", AnswerValue.Scalar("Yes"))
        model.answer(QuestionnaireSections.employment, maria, "occupation", AnswerValue.Scalar("Teacher"))
        model.answer(QuestionnaireSections.employment, maria, "industry", AnswerValue.Scalar("Education"))
        model.answer(QuestionnaireSections.employment, maria, "employmentType", AnswerValue.Scalar("Employee"))
        model.answer(QuestionnaireSections.employment, maria, "hours", AnswerValue.Scalar("40"))

        model.answer(QuestionnaireSections.employment, juan, "worked", AnswerValue.Scalar("No"))
        model.answer(QuestionnaireSections.employment, juan, "looking", AnswerValue.Scalar("Yes"))
        model.answer(QuestionnaireSections.employment, juan, "notWorkingReason", AnswerValue.Scalar("Student"))
        model.answer(QuestionnaireSections.employment, juan, "available", AnswerValue.Scalar("Yes"))

        assertEquals("Yes", model.state.value.form(QuestionnaireSections.employment, maria).answers.scalar("worked"))
        assertEquals("Teacher", model.state.value.form(QuestionnaireSections.employment, maria).answers.scalar("occupation"))
        assertEquals("Education", model.state.value.form(QuestionnaireSections.employment, maria).answers.scalar("industry"))
        assertEquals("Employee", model.state.value.form(QuestionnaireSections.employment, maria).answers.scalar("employmentType"))
        assertEquals("40", model.state.value.form(QuestionnaireSections.employment, maria).answers.scalar("hours"))
        assertFalse("Maria's looking should not be set",
            model.state.value.form(QuestionnaireSections.employment, maria).answers.containsKey("looking"))

        assertEquals("No", model.state.value.form(QuestionnaireSections.employment, juan).answers.scalar("worked"))
        assertEquals("Yes", model.state.value.form(QuestionnaireSections.employment, juan).answers.scalar("looking"))
        assertEquals("Student", model.state.value.form(QuestionnaireSections.employment, juan).answers.scalar("notWorkingReason"))
        assertEquals("Yes", model.state.value.form(QuestionnaireSections.employment, juan).answers.scalar("available"))
        assertFalse("Juan's occupation should not be set",
            model.state.value.form(QuestionnaireSections.employment, juan).answers.containsKey("occupation"))
        assertFalse("Juan's hours should not be set",
            model.state.value.form(QuestionnaireSections.employment, juan).answers.containsKey("hours"))

        model.answer(QuestionnaireSections.employment, maria, "worked", AnswerValue.Scalar("No"))
        model.answer(QuestionnaireSections.employment, maria, "looking", AnswerValue.Scalar("Yes"))
        model.answer(QuestionnaireSections.employment, maria, "notWorkingReason", AnswerValue.Scalar("Homemaking / caregiving"))
        model.answer(QuestionnaireSections.employment, maria, "available", AnswerValue.Scalar("Yes"))
        assertEquals("No", model.state.value.form(QuestionnaireSections.employment, maria).answers.scalar("worked"))
        assertEquals("No", model.state.value.form(QuestionnaireSections.employment, juan).answers.scalar("worked"))
    }
}