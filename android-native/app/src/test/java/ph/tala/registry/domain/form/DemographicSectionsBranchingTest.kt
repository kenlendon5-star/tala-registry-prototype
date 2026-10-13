package ph.tala.registry.domain.form

import org.junit.Assert.*
import org.junit.Test
import ph.tala.registry.domain.model.*

class DemographicSectionsBranchingTest {
    private fun answers(vararg pairs: Pair<String, String>): AnswerState = pairs.associate { it.first to AnswerValue.Scalar(it.second) }
    @Test fun disabilityReversalClearsTheWholeCascadeAndDoesNotResurrectIt() {
        val section = QuestionnaireSections.other
        val start = answers("disability" to "Yes", "registeredPwd" to "Yes", "pwdId" to "PWD-123", "disabilityDetail" to "Visual", "soloParent" to "Yes", "soloParentId" to "SP-123")
        val no = FormRules.update(section, start, "disability", AnswerValue.Scalar("No"))
        listOf("registeredPwd", "pwdId", "disabilityDetail").forEach { assertFalse(no.containsKey(it)) }
        assertEquals("SP-123", no.scalar("soloParentId"))
        assertEquals(no, FormRules.update(section, no, "pwdId", AnswerValue.Scalar("late callback")))
        val yes = FormRules.update(section, no, "disability", AnswerValue.Scalar("Yes"))
        assertFalse(yes.containsKey("registeredPwd")); assertFalse(yes.containsKey("pwdId"))
        assertTrue(FormRules.validate(section, yes).containsKey("registeredPwd"))
    }
    @Test fun unregisteringPwdClearsOnlyId() {
        val section = QuestionnaireSections.other
        val start = answers("disability" to "Yes", "registeredPwd" to "Yes", "pwdId" to "PWD-123", "disabilityDetail" to "Visual")
        val no = FormRules.update(section, start, "registeredPwd", AnswerValue.Scalar("No"))
        assertFalse(no.containsKey("pwdId")); assertEquals("Visual", no.scalar("disabilityDetail"))
    }
    @Test fun soloParentReversalClearsOptionalId() {
        val section = QuestionnaireSections.other
        val no = FormRules.update(section, answers("soloParent" to "Yes", "soloParentId" to "SP-123"), "soloParent", AnswerValue.Scalar("No"))
        assertFalse(no.containsKey("soloParentId"))
        assertFalse(FormRules.update(section, no, "soloParent", AnswerValue.Scalar("Yes")).containsKey("soloParentId"))
    }
    @Test fun migrationReversalClearsResidenceAndYearButPreservesOverseas() {
        val section = QuestionnaireSections.migration
        val no = FormRules.update(section, answers("moved" to "Yes", "previousResidence" to "Cebu", "moveYear" to "2020", "overseas" to "Yes"), "moved", AnswerValue.Scalar("No"))
        assertFalse(no.containsKey("previousResidence")); assertFalse(no.containsKey("moveYear"))
        assertEquals("Yes", no.scalar("overseas"))
        val yes = FormRules.update(section, no, "moved", AnswerValue.Scalar("Yes"))
        assertFalse(yes.containsKey("moveYear"))
        assertTrue(FormRules.validate(section, yes).containsKey("previousResidence"))
    }
}
