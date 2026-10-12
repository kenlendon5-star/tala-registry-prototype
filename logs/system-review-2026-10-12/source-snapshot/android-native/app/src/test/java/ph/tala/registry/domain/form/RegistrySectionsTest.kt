package ph.tala.registry.domain.form

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ph.tala.registry.domain.model.AnswerValue
import ph.tala.registry.domain.model.NO
import java.time.LocalDate

/** The registration section specs ported for plan card D08. */
class RegistrySectionsTest {
    private val today: LocalDate = LocalDate.of(2026, 10, 12)

    @Test fun registrationRoutesMatchThePrototypeInventory() {
        assertEquals(listOf("setup", "visit", "household"), RegistrySections.registration.map { it.route })
        val keys = RegistrySections.registration.flatMap { it.fields }.map { it.key }
        assertEquals("Registration field keys must stay unique for merged storage", keys.size, keys.distinct().size)
    }

    @Test fun callbackAppointmentAppearsOnlyForCallbacksAndClearsWithTheBranch() {
        val visit = RegistrySections.visit
        val answers = FormRules.update(visit, emptyMap(), "visitResult", AnswerValue.Scalar("Callback"))
        assertTrue(visit.visibleFields(answers).any { it.key == "callbackDate" })
        val rescheduled = FormRules.update(
            visit,
            answers + ("callbackDate" to AnswerValue.Scalar("2026-10-20T09:30")),
            "visitResult", AnswerValue.Scalar("No respondent"),
        )
        assertNull("Hidden dependent answers must clear", rescheduled["callbackDate"])
        assertTrue(visit.visibleFields(rescheduled).none { it.key == "callbackDate" })
    }

    @Test fun visitSectionRequiresDateResultAndConditionalAppointment() {
        val answers = FormRules.update(RegistrySections.visit, emptyMap(), "visitResult", AnswerValue.Scalar("Callback"))
        val errors = FormRules.validate(RegistrySections.visit, answers, today)
        assertEquals(setOf("visitDate", "callbackDate"), errors.keys)
    }

    @Test fun setupSectionRequiresEnumeratorBarangayPurokAndDate() {
        val errors = FormRules.validate(RegistrySections.setup, emptyMap(), today)
        assertEquals(setOf("enumerator", "barangay", "purok", "interviewDate"), errors.keys)
    }

    @Test fun householdSectionCoversStructuredNamesContactPreviousCityAndSafetyReason() {
        val household = RegistrySections.household
        listOf("firstName", "lastName", "extension", "alias", "phone", "previousCity", "houseSafe", "safetyReason", "respondent", "address").forEach { key ->
            assertTrue("Missing field $key", household.fields.any { it.key == key })
        }
        val answers = FormRules.update(household, emptyMap(), "houseSafe", AnswerValue.Scalar(NO))
        assertTrue(household.visibleFields(answers).any { it.key == "safetyReason" })
        val completed = answers
            + ("firstName" to AnswerValue.Scalar("Luna"))
            + ("lastName" to AnswerValue.Scalar("Reyes"))
            + ("address" to AnswerValue.Scalar("5 Sampaguita Street"))
            + ("respondent" to AnswerValue.Scalar("Luna Reyes"))
        assertTrue("Optional reason must not block a valid profile", FormRules.validate(household, completed, today).isEmpty())
    }
}