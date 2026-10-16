package ph.tala.registry.domain.form

import org.junit.Assert.*
import org.junit.Test
import ph.tala.registry.domain.model.*
import ph.tala.registry.ui.form.FormSessionViewModel

/**
 * Household service answers must stay keyed to the household, never to a member:
 * answering once under the household scope is visible regardless of member context,
 * and a member scope is rejected for these sections outright.
 */
class HouseholdServicesScopingTest {

    private val household = HouseholdId("d1100000-0000-4000-8000-000000000001")
    private val home = AnswerScope.Household(household)
    private val maria = AnswerScope.Member(household, MemberId("d1100000-0000-4000-8000-000000000002"))
    private val juan = AnswerScope.Member(household, MemberId("d1100000-0000-4000-8000-000000000003"))

    private val householdSections = listOf(
        QuestionnaireSections.health,
        QuestionnaireSections.food,
        QuestionnaireSections.transport,
        QuestionnaireSections.internet,
        QuestionnaireSections.water,
        QuestionnaireSections.housing,
    )

    @Test fun `household sections reject member scopes`() {
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

    @Test fun `household answers are keyed once not per member`() {
        val model = FormSessionViewModel()

        model.answer(QuestionnaireSections.health, home, "illness", AnswerValue.Scalar("Yes"))
        model.answer(QuestionnaireSections.health, home, "treatment", AnswerValue.Scalar("Public hospital"))
        model.answer(QuestionnaireSections.transport, home, "accounts", AnswerValue.Multi(listOf("Bank", "E-money")))
        model.answer(QuestionnaireSections.housing, home, "rooms", AnswerValue.Scalar("3"))

        // Exactly one draft bucket exists — the household bucket.
        assertEquals(setOf<AnswerScope>(home), model.state.value.drafts.keys)

        val health = model.state.value.form(QuestionnaireSections.health, home)
        assertEquals("Yes", health.answers.scalar("illness"))
        assertEquals("Public hospital", health.answers.scalar("treatment"))
        assertEquals(listOf("Bank", "E-money"),
            model.state.value.form(QuestionnaireSections.transport, home).answers.multi("accounts"))
        assertEquals("3", model.state.value.form(QuestionnaireSections.housing, home).answers.scalar("rooms"))
    }

    @Test fun `switching member context never splits household answers`() {
        val model = FormSessionViewModel()

        model.answer(QuestionnaireSections.water, home, "water", AnswerValue.Scalar("Piped into dwelling"))
        model.answer(QuestionnaireSections.water, home, "toilet", AnswerValue.Scalar("Flush / pour flush"))

        // Member-scoped sections continue to work alongside household ones.
        model.answer(QuestionnaireSections.education, maria, "attending", AnswerValue.Scalar("Yes"))
        model.answer(QuestionnaireSections.education, juan, "attending", AnswerValue.Scalar("No"))

        // The household water answers are one shared bucket; no member-scoped copies exist.
        assertEquals(setOf<AnswerScope>(home, maria, juan), model.state.value.drafts.keys)
        assertEquals("Piped into dwelling", model.state.value.form(QuestionnaireSections.water, home).answers.scalar("water"))
        assertEquals("Flush / pour flush", model.state.value.form(QuestionnaireSections.water, home).answers.scalar("toilet"))
        assertEquals("Yes", model.state.value.form(QuestionnaireSections.education, maria).answers.scalar("attending"))
        assertEquals("No", model.state.value.form(QuestionnaireSections.education, juan).answers.scalar("attending"))
    }

    @Test fun `household branch clearing applies to the single shared bucket`() {
        val model = FormSessionViewModel()

        model.answer(QuestionnaireSections.internet, home, "internet", AnswerValue.Scalar("Yes"))
        model.answer(QuestionnaireSections.internet, home, "connection", AnswerValue.Scalar("Mobile data"))
        model.answer(QuestionnaireSections.internet, home, "internet", AnswerValue.Scalar("No"))

        val internet = model.state.value.form(QuestionnaireSections.internet, home)
        assertEquals("No", internet.answers.scalar("internet"))
        assertFalse("cleared connection must not linger in the household bucket", internet.answers.containsKey("connection"))
    }
}
