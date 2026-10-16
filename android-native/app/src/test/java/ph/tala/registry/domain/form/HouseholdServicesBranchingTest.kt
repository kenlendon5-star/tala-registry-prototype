package ph.tala.registry.domain.form

import org.junit.Assert.*
import org.junit.Test
import ph.tala.registry.domain.model.*

/**
 * Conditional branch clearing and multi-select exclusivity for the six household-level
 * service sections (Health, Food security, Transport & finance, Internet & public safety,
 * Water & sanitation, Housing). Household sections are answered once per household.
 */
class HouseholdServicesBranchingTest {

    private fun value(text: String) = AnswerValue.Scalar(text)

    @Test fun `health illness branch clearing`() {
        val health = QuestionnaireSections.health

        val withCare = mapOf(
            "illness" to value("Yes"),
            "treatment" to value("Barangay health center"),
        )
        val cleared = FormRules.update(health, withCare, "illness", value("No"))
        assertFalse("treatment should be cleared when illness changes to No", cleared.containsKey("treatment"))
        assertEquals("No", cleared.scalar("illness"))
    }

    @Test fun `health treatment branch restores when care sought again`() {
        val health = QuestionnaireSections.health

        val withoutCare = mapOf("illness" to value("No"))
        val updated = FormRules.update(health, withoutCare, "treatment", value("Public hospital"))
        // A late answer for a still-hidden dependent must not appear.
        assertFalse("treatment stays hidden while illness is No", updated.containsKey("treatment"))

        val shown = FormRules.update(health, updated, "illness", value("Yes"))
        val withTreatment = FormRules.update(health, shown, "treatment", value("Private facility"))
        assertEquals("Private facility", withTreatment.scalar("treatment"))
    }

    @Test fun `health access answer survives illness branch flips`() {
        val health = QuestionnaireSections.health

        val state = mapOf(
            "illness" to value("Yes"),
            "treatment" to value("Public hospital"),
            "healthAccess" to value("Yes"),
        )
        val cleared = FormRules.update(health, state, "illness", value("No"))
        assertTrue("healthAccess is unconditional and must survive", cleared.containsKey("healthAccess"))
        assertEquals("Yes", cleared.scalar("healthAccess"))
    }

    @Test fun `internet connection branch clearing`() {
        val internet = QuestionnaireSections.internet

        val withAccess = mapOf(
            "internet" to value("Yes"),
            "connection" to value("Mobile data"),
        )
        val cleared = FormRules.update(internet, withAccess, "internet", value("No"))
        assertFalse("connection should be cleared when internet changes to No", cleared.containsKey("connection"))
        assertEquals("No", cleared.scalar("internet"))

        val restored = FormRules.update(internet, cleared, "internet", value("Yes"))
        val withConnection = FormRules.update(internet, restored, "connection", value("Fiber / cable"))
        assertEquals("Fiber / cable", withConnection.scalar("connection"))
    }

    @Test fun `internet safety answer survives access branch flips`() {
        val internet = QuestionnaireSections.internet

        val state = mapOf(
            "internet" to value("Yes"),
            "connection" to value("Public Wi-Fi"),
            "safety" to value("Somewhat safe"),
        )
        val cleared = FormRules.update(internet, state, "internet", value("No"))
        assertTrue("safety is unconditional and must survive", cleared.containsKey("safety"))
        assertEquals("Somewhat safe", cleared.scalar("safety"))
        assertFalse("connection should be cleared", cleared.containsKey("connection"))
    }

    @Test fun `transport financial accounts none is exclusive`() {
        val transport = QuestionnaireSections.transport
        val accounts = transport.fields.first { it.key == "accounts" }

        // Selecting None after a normal option drops the normal option.
        val withNone = FormRules.toggle(accounts, listOf("Bank", "E-money"), "None")
        assertEquals(listOf("None"), withNone.selected)

        // Selecting a normal option after None drops None.
        val backToNormal = FormRules.toggle(accounts, withNone.selected, "Bank")
        assertEquals(listOf("Bank"), backToNormal.selected)

        // The two exclusive choices also replace each other.
        val preferNot = FormRules.toggle(accounts, listOf("None"), "Prefer not to answer")
        assertEquals(listOf("Prefer not to answer"), preferNot.selected)

        // Toggling the exclusive choice off leaves the selection empty.
        val cleared = FormRules.toggle(accounts, preferNot.selected, "Prefer not to answer")
        assertTrue(cleared.selected.isEmpty())
    }

    @Test fun `transport financial accounts normal options accumulate`() {
        val transport = QuestionnaireSections.transport
        val accounts = transport.fields.first { it.key == "accounts" }

        val first = FormRules.toggle(accounts, emptyList(), "Bank")
        val second = FormRules.toggle(accounts, first.selected, "E-money")
        val again = FormRules.toggle(accounts, second.selected, "Bank")
        assertEquals(listOf("E-money"), again.selected)
    }

    @Test fun `water waste disposal practices accumulate without exclusives`() {
        val water = QuestionnaireSections.water
        val waste = water.fields.first { it.key == "waste" }

        assertTrue("waste has no exclusive options by spec", waste.exclusiveOptions.isEmpty())
        val first = FormRules.toggle(waste, emptyList(), "Collection service")
        val second = FormRules.toggle(waste, first.selected, "Composting")
        assertEquals(listOf("Collection service", "Composting"), second.selected)
        val removed = FormRules.toggle(waste, second.selected, "Collection service")
        assertEquals(listOf("Composting"), removed.selected)
    }

    @Test fun `fixed point clearing is order independent for household branches`() {
        // Dependency order in the schema must not affect cascading clearing.
        val internet = QuestionnaireSections.internet
        val withConnection = mapOf(
            "internet" to value("Yes"),
            "connection" to value("Other"),
        )
        val cleared = FormRules.update(internet, withConnection, "internet", value("No"))
        assertFalse(cleared.containsKey("connection"))
    }
}
