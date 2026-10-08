package ph.tala.registry.domain.form

import org.junit.Assert.*
import org.junit.Test
import ph.tala.registry.domain.model.*
import java.time.LocalDate

class FormRulesTest {
    private val today = LocalDate.of(2026, 10, 5)
    private fun value(text: String) = AnswerValue.Scalar(text)
    private fun error(field: FieldSpec, text: String) = FormRules.error(field, mapOf(field.key to value(text)), today)

    @Test fun requiredOptionalAndHiddenFields() {
        val required = FieldSpec("name", "Name", FieldType.Text)
        assertNotNull(error(required, "   "))
        assertNull(error(required.copy(optional = true), "   "))
        assertNull(error(required.copy(visibleWhen = Condition.yes("enabled")), ""))
        assertNotNull(FormRules.error(required.copy(optional = true), mapOf("name" to AnswerValue.Multi(listOf("wrong type"))), today))
    }

    @Test fun numericBoundsAreInclusiveAndRejectNonIntegers() {
        val field = FieldSpec("hours", "Hours", FieldType.Number, min = 0, max = 168)
        listOf("0", "168", "24").forEach { assertNull(it, error(field, it)) }
        listOf("-1", "169", "2.5", "NaN", "1e2", "99999999999999999999999").forEach { assertNotNull(it, error(field, it)) }
        assertNull(error(field.copy(min = null, max = null), "-100"))
    }

    @Test fun calendarValidationIsStrictAndAllowsToday() {
        val field = FieldSpec("dob", "Birth date", FieldType.Date, mustBePast = true)
        listOf("2000-02-29", "2026-10-05").forEach { assertNull(it, error(field, it)) }
        listOf("2025-02-29", "2026-02-30", "2026-13-01", "2026-1-01", "2026-10-06").forEach { assertNotNull(it, error(field, it)) }
    }

    @Test fun appointmentsRequireAnActualLocalDateAndMinute() {
        val field = FieldSpec("appointment", "Appointment", FieldType.DateTime)
        assertNull(error(field, "2028-02-29T23:59"))
        listOf("2026-02-29T09:00", "2026-10-05T24:00", "2026-10-05T09:60", "2026-10-05", "2026-10-05T09:00Z").forEach { assertNotNull(it, error(field, it)) }
    }

    @Test fun phoneNormalizationDoesNotTurnMalformedInputIntoValidData() {
        val field = FieldSpec("phone", "Phone", FieldType.Phone, optional = true)
        assertNull(error(field, ""))
        assertNull(error(field, "09171234567"))
        assertNull(error(field, "0917-123-4567"))
        assertEquals("0917-123-4567", FormRules.formatPhone("09171234567"))
        listOf("0917123456", "091712345678", "abc09171234567", "+639171234567", "0917--123-4567").forEach {
            assertNotNull(it, error(field, it))
            assertEquals(it, FormRules.formatPhone(it))
        }
    }

    @Test fun choiceAndSelectRejectInventedOptions() {
        listOf(FieldType.Choice, FieldType.Select).forEach { type ->
            val field = FieldSpec("option", "Option", type, listOf("Yes", "No", "Don't know"))
            assertNull(error(field, "Don't know"))
            assertNotNull(error(field, "Maybe"))
            assertNotNull(error(field, " Yes "))
            assertNull(error(field.copy(optional = true), ""))
        }
    }

    @Test fun multiSelectValidatesMembershipDuplicatesAndExclusivity() {
        val field = PracticeSection.member.fields.single { it.key == "accounts" }
        fun check(items: List<String>) = FormRules.error(field, mapOf(field.key to AnswerValue.Multi(items)), today)
        assertNull(check(listOf("Bank", "Cooperative")))
        assertNull(check(listOf("None")))
        listOf(emptyList(), listOf("Bank", "None"), listOf("Bank", "Bank"), listOf("Unknown")).forEach { assertNotNull(check(it)) }
        assertEquals(listOf("None"), FormRules.toggle(field, listOf("Bank", "E-money"), "None").selected)
        assertEquals(listOf("Bank"), FormRules.toggle(field, listOf("Prefer not to answer"), "Bank").selected)
        assertEquals(emptyList<String>(), FormRules.toggle(field, listOf("Bank"), "Bank").selected)
    }

    @Test fun hiddenAnswersClearToFixedPointEvenWhenDependenciesAreOutOfOrder() {
        val section = SectionSpec("identity", "Identity", listOf(
            FieldSpec("id", "PWD ID", FieldType.Text, visibleWhen = Condition.yes("registered")),
            FieldSpec("registered", "Registered", FieldType.Choice, listOf(YES, NO), visibleWhen = Condition.yes("disability")),
            FieldSpec("disability", "Disability", FieldType.Choice, listOf(YES, NO)),
        ))
        val before = mapOf("disability" to value(YES), "registered" to value(YES), "id" to value("PWD-123"), "unrelated" to value("Keep"))
        val after = FormRules.update(section, before, "disability", value(NO))
        assertFalse(after.containsKey("registered"))
        assertFalse(after.containsKey("id"))
        assertEquals("Keep", after.scalar("unrelated"))
        val reopened = FormRules.update(section, after, "disability", value(YES))
        assertFalse(reopened.containsKey("id"))
        assertTrue("registered" in FormRules.validate(section, reopened, today))
        assertFalse("id" in FormRules.validate(section, reopened, today))
    }

    @Test fun aLateHiddenFieldCallbackCannotResurrectAnAnswer() {
        val before = mapOf("worked" to value(NO))
        assertEquals(before, FormRules.update(PracticeSection.member, before, "occupation", value("Late result")))
    }

    @Test fun predicatesAndAllRepresentativeInputTypesAreCovered() {
        val state = mapOf("worked" to value(YES), "name" to value("Ana"))
        assertTrue(Condition.All(listOf(Condition.yes("worked"), Condition.Answered("name"))).matches(state))
        assertTrue(Condition.OneOf("worked", listOf(YES, "Unknown")).matches(state))
        assertFalse(Condition.Answered("missing").matches(state))
        assertEquals(FieldType.entries.toSet() - FieldType.Photo, PracticeSection.member.fields.map { it.type }.toSet())
    }

    @Test fun completedRepresentativeSectionPassesWhileOptionalAnswersStayBlank() {
        val answers = mapOf(
            "firstName" to value("Ana"), "dob" to value("2000-02-29"), "callbackDate" to value("2026-10-10T09:30"),
            "worked" to value(YES), "occupation" to value("Teacher"), "employmentType" to value("Employee"),
            "hours" to value("40"), "accounts" to AnswerValue.Multi(listOf("Bank")),
        )
        assertTrue(FormRules.validate(PracticeSection.member, answers, today).isEmpty())
        val noWork = FormRules.update(PracticeSection.member, answers, "worked", value(NO))
        assertTrue(FormRules.validate(PracticeSection.member, noWork, today).isEmpty())
        assertFalse(noWork.containsKey("hours"))
    }
}
