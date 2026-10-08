package ph.tala.registry.ui

import org.junit.Assert.*
import org.junit.Test
import ph.tala.registry.domain.form.PracticeSection
import ph.tala.registry.domain.model.*
import ph.tala.registry.ui.form.FormSessionViewModel

class FormSessionViewModelTest {
    private val household = HouseholdId("A")
    private val ana = AnswerScope.Member(household, MemberId("ana"))
    private val juan = AnswerScope.Member(household, MemberId("juan"))

    @Test fun householdTwoMembersAndAnotherHouseholdNeverShareAnswers() {
        val model = FormSessionViewModel()
        val hh = AnswerScope.Household(household)
        val otherHouseholdAna = AnswerScope.Member(HouseholdId("B"), MemberId("ana"))
        model.answer(PracticeSection.household, hh, "firstName", AnswerValue.Scalar("Respondent"))
        listOf(ana to "Ana", juan to "Juan", otherHouseholdAna to "Other Ana").forEach { (scope, name) ->
            model.answer(PracticeSection.member, scope, "firstName", AnswerValue.Scalar(name))
        }
        assertEquals("Respondent", model.state.value.form(PracticeSection.household, hh).answers.scalar("firstName"))
        assertEquals("Ana", model.state.value.form(PracticeSection.member, ana).answers.scalar("firstName"))
        assertEquals("Juan", model.state.value.form(PracticeSection.member, juan).answers.scalar("firstName"))
        assertEquals("Other Ana", model.state.value.form(PracticeSection.member, otherHouseholdAna).answers.scalar("firstName"))
    }

    @Test fun validationAndBranchClearingStayOnTheOriginalMember() {
        val model = FormSessionViewModel()
        listOf(ana, juan).forEach {
            model.answer(PracticeSection.member, it, "worked", AnswerValue.Scalar(YES))
            model.answer(PracticeSection.member, it, "occupation", AnswerValue.Scalar("Teacher"))
        }
        model.check(PracticeSection.member, ana)
        model.answer(PracticeSection.member, ana, "worked", AnswerValue.Scalar(NO))
        val a = model.state.value.form(PracticeSection.member, ana)
        val j = model.state.value.form(PracticeSection.member, juan)
        assertEquals(1, a.validationAttempt)
        assertEquals(0, j.validationAttempt)
        assertFalse(a.answers.containsKey("occupation"))
        assertEquals("Teacher", j.answers.scalar("occupation"))
        assertTrue(j.errors.isEmpty())
        assertTrue("firstName" in a.errors)
    }

    @Test(expected = IllegalArgumentException::class)
    fun memberFormCannotWriteIntoHouseholdScope() {
        FormSessionViewModel().answer(PracticeSection.member, AnswerScope.Household(household), "firstName", AnswerValue.Scalar("Wrong scope"))
    }
}
