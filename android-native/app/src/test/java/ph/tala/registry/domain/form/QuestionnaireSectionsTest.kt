package ph.tala.registry.domain.form

import org.junit.Assert.*
import org.junit.Test
import ph.tala.registry.domain.model.AnswerValue

class QuestionnaireSectionsTest {
    @Test fun registrationHasAllPlannedFieldsAndOptionalContact() {
        assertEquals(18, QuestionnaireSections.all.size)
        val fields = QuestionnaireSections.household.fields.associateBy { it.key }
        assertTrue(fields.keys.containsAll(listOf("firstName", "middleName", "lastName", "extension", "alias", "respondent", "phone", "previousCity", "houseSafe", "safetyReason", "photo")))
        assertTrue(fields.getValue("phone").optional)
        assertTrue(fields.getValue("previousCity").optional)
        assertFalse(fields.getValue("firstName").optional)
        assertFalse(fields.getValue("lastName").optional)
    }
    @Test fun leavingCallbackClearsTheAppointment() {
        val state = mapOf("visitResult" to AnswerValue.Scalar("Callback"), "callbackDate" to AnswerValue.Scalar("2026-10-12T14:00"))
        val next = FormRules.update(QuestionnaireSections.visit, state, "visitResult", AnswerValue.Scalar("Completed visit"))
        assertFalse(next.containsKey("callbackDate"))
    }
}
