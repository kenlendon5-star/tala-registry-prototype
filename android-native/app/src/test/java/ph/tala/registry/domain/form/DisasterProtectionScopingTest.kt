package ph.tala.registry.domain.form

import org.junit.Assert.*
import org.junit.Test
import ph.tala.registry.domain.model.*
import ph.tala.registry.ui.form.FormSessionViewModel

/**
 * Disaster preparedness and Social protection answers must stay keyed to the household,
 * never to a member: answering once under the household scope is visible regardless of
 * member context, and a member scope is rejected for these sections outright.
 */
class DisasterProtectionScopingTest {

    private val household = HouseholdId("d1200000-0000-4000-8000-000000000001")
    private val home = AnswerScope.Household(household)
    private val maria = AnswerScope.Member(household, MemberId("d1200000-0000-4000-8000-000000000002"))
    private val juan = AnswerScope.Member(household, MemberId("d1200000-0000-4000-8000-000000000003"))

    private val householdSections = listOf(
        QuestionnaireSections.disaster,
        QuestionnaireSections.protection,
    )

    @Test fun `disaster and protection reject member scopes`() {
        val model = FormSessionViewModel()
        for (section in householdSections) {
            try {
                model.answer(section, maria, section.fields.first().key, AnswerValue.Scalar("Yes"))
                fail("${section.route} must refuse a member scope")
            } catch (expected: IllegalArgumentException) {
                assertEquals("Section and answer scope do not match", expected.message)
            }
        }
    }

    @Test fun `disaster and protection answers are keyed once not per member`() {
        val model = FormSessionViewModel()

        model.answer(QuestionnaireSections.disaster, home, "disasterType", AnswerValue.Scalar("Natural"))
        model.answer(QuestionnaireSections.disaster, home, "hazards", AnswerValue.Multi(listOf("Flood", "Typhoon")))
        model.answer(QuestionnaireSections.disaster, home, "evacuationKnown", AnswerValue.Scalar("Yes"))
        model.answer(QuestionnaireSections.disaster, home, "evacuation", AnswerValue.Scalar("Barangay Hall (Sample)"))
        model.answer(QuestionnaireSections.disaster, home, "emergencyKit", AnswerValue.Scalar("Yes"))

        model.answer(QuestionnaireSections.protection, home, "philHealth", AnswerValue.Scalar("Yes"))
        model.answer(QuestionnaireSections.protection, home, "philHealthRole", AnswerValue.Scalar("Member"))
        model.answer(QuestionnaireSections.protection, home, "programs", AnswerValue.Multi(listOf("4Ps")))

        // Exactly one draft bucket exists — the household bucket.
        assertEquals(setOf<AnswerScope>(home), model.state.value.drafts.keys)

        val disaster = model.state.value.form(QuestionnaireSections.disaster, home)
        assertEquals("Natural", disaster.answers.scalar("disasterType"))
        assertEquals(listOf("Flood", "Typhoon"), disaster.answers.multi("hazards"))
        assertEquals("Barangay Hall (Sample)", disaster.answers.scalar("evacuation"))
        assertEquals("Yes", disaster.answers.scalar("emergencyKit"))

        val protection = model.state.value.form(QuestionnaireSections.protection, home)
        assertEquals("Member", protection.answers.scalar("philHealthRole"))
        assertEquals(listOf("4Ps"), protection.answers.multi("programs"))
    }

    @Test fun `switching member context never splits household answers`() {
        val model = FormSessionViewModel()

        model.answer(QuestionnaireSections.protection, home, "philHealth", AnswerValue.Scalar("No"))
        model.answer(QuestionnaireSections.protection, home, "fourPs", AnswerValue.Scalar("Yes"))

        // Member-scoped sections continue to work alongside household ones.
        model.answer(QuestionnaireSections.education, maria, "attending", AnswerValue.Scalar("Yes"))
        model.answer(QuestionnaireSections.education, juan, "attending", AnswerValue.Scalar("No"))

        // The protection answers are one shared bucket; no member-scoped copies exist.
        assertEquals(setOf<AnswerScope>(home, maria, juan), model.state.value.drafts.keys)
        assertEquals("No", model.state.value.form(QuestionnaireSections.protection, home).answers.scalar("philHealth"))
        assertEquals("Yes", model.state.value.form(QuestionnaireSections.protection, home).answers.scalar("fourPs"))
        assertEquals("Yes", model.state.value.form(QuestionnaireSections.education, maria).answers.scalar("attending"))
        assertEquals("No", model.state.value.form(QuestionnaireSections.education, juan).answers.scalar("attending"))
    }

    @Test fun `disaster branch clearing applies to the single shared bucket`() {
        val model = FormSessionViewModel()

        model.answer(QuestionnaireSections.disaster, home, "evacuationKnown", AnswerValue.Scalar("Yes"))
        model.answer(QuestionnaireSections.disaster, home, "evacuation", AnswerValue.Scalar("Other"))
        model.answer(QuestionnaireSections.disaster, home, "evacuationOther", AnswerValue.Scalar("Rooftop"))
        model.answer(QuestionnaireSections.disaster, home, "evacuationKnown", AnswerValue.Scalar("No"))

        val disaster = model.state.value.form(QuestionnaireSections.disaster, home)
        assertEquals("No", disaster.answers.scalar("evacuationKnown"))
        assertFalse("cleared evacuation must not linger in the household bucket",
            disaster.answers.containsKey("evacuation"))
        assertFalse("cleared evacuationOther must not linger in the household bucket",
            disaster.answers.containsKey("evacuationOther"))
    }
}
