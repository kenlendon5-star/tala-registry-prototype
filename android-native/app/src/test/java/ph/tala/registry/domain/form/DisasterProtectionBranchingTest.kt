package ph.tala.registry.domain.form

import org.junit.Assert.*
import org.junit.Test
import ph.tala.registry.domain.model.*

/**
 * Conditional branch clearing and multi-select exclusivity for Disaster preparedness and
 * Social protection. The disaster section carries a nested branch: evacuationOther appears
 * only when evacuationKnown = Yes AND evacuation = Other, so flipping any parent must clear it.
 */
class DisasterProtectionBranchingTest {

    private fun value(text: String) = AnswerValue.Scalar(text)

    @Test fun `evacuation branch clearing`() {
        val disaster = QuestionnaireSections.disaster

        val withArea = mapOf(
            "evacuationKnown" to value("Yes"),
            "evacuation" to value("Barangay Hall (Sample)"),
        )
        val cleared = FormRules.update(disaster, withArea, "evacuationKnown", value("No"))
        assertFalse("evacuation should be cleared when evacuationKnown changes to No", cleared.containsKey("evacuation"))
        assertEquals("No", cleared.scalar("evacuationKnown"))
    }

    @Test fun `evacuation branch restores when known again`() {
        val disaster = QuestionnaireSections.disaster

        val withoutArea = mapOf("evacuationKnown" to value("No"))
        val updated = FormRules.update(disaster, withoutArea, "evacuation", value("School Evacuation Center (Sample)"))
        // A late answer for a still-hidden dependent must not appear.
        assertFalse("evacuation stays hidden while evacuationKnown is No", updated.containsKey("evacuation"))

        val shown = FormRules.update(disaster, updated, "evacuationKnown", value("Yes"))
        val withArea = FormRules.update(disaster, shown, "evacuation", value("Covered Court (Sample)"))
        assertEquals("Covered Court (Sample)", withArea.scalar("evacuation"))
    }

    @Test fun `nested evacuation other clears when parent choice changes`() {
        val disaster = QuestionnaireSections.disaster

        val withOther = mapOf(
            "evacuationKnown" to value("Yes"),
            "evacuation" to value("Other"),
            "evacuationOther" to value("Bgy. Multi-purpose building"),
        )

        // Choosing a named site instead of Other drops the free-text answer.
        val namedSite = FormRules.update(disaster, withOther, "evacuation", value("Barangay Hall (Sample)"))
        assertFalse("evacuationOther should be cleared when evacuation is no longer Other",
            namedSite.containsKey("evacuationOther"))
        assertEquals("Barangay Hall (Sample)", namedSite.scalar("evacuation"))

        // Flipping evacuationKnown off clears both branch fields.
        val bothCleared = FormRules.update(disaster, withOther, "evacuationKnown", value("No"))
        assertFalse("evacuation should be cleared", bothCleared.containsKey("evacuation"))
        assertFalse("evacuationOther should be cleared", bothCleared.containsKey("evacuationOther"))
    }

    @Test fun `late answer for hidden evacuation other must not appear`() {
        val disaster = QuestionnaireSections.disaster

        val namedSite = mapOf(
            "evacuationKnown" to value("Yes"),
            "evacuation" to value("Covered Court (Sample)"),
        )
        val updated = FormRules.update(disaster, namedSite, "evacuationOther", value("Rooftop"))
        assertFalse("evacuationOther stays hidden while a named site is chosen", updated.containsKey("evacuationOther"))
    }

    @Test fun `hazards none is exclusive`() {
        val hazards = QuestionnaireSections.disaster.fields.first { it.key == "hazards" }

        // Selecting None after normal options drops the normal options.
        val withNone = FormRules.toggle(hazards, listOf("Flood", "Typhoon"), "None")
        assertEquals(listOf("None"), withNone.selected)

        // Selecting a normal option after None drops None.
        val backToNormal = FormRules.toggle(hazards, withNone.selected, "Earthquake")
        assertEquals(listOf("Earthquake"), backToNormal.selected)

        // Toggling None off leaves the selection empty.
        val cleared = FormRules.toggle(hazards, listOf("None"), "None")
        assertTrue(cleared.selected.isEmpty())
    }

    @Test fun `hazards normal options accumulate`() {
        val hazards = QuestionnaireSections.disaster.fields.first { it.key == "hazards" }

        val first = FormRules.toggle(hazards, emptyList(), "Flood")
        val second = FormRules.toggle(hazards, first.selected, "Fire")
        assertEquals(listOf("Flood", "Fire"), second.selected)
        val removed = FormRules.toggle(hazards, second.selected, "Flood")
        assertEquals(listOf("Fire"), removed.selected)
    }

    @Test fun `protection programs none and prefer-not-to-answer are exclusive`() {
        val programs = QuestionnaireSections.protection.fields.first { it.key == "programs" }

        val withNone = FormRules.toggle(programs, listOf("SSS", "GSIS"), "None")
        assertEquals(listOf("None"), withNone.selected)

        val backToNormal = FormRules.toggle(programs, withNone.selected, "PhilHealth")
        assertEquals(listOf("PhilHealth"), backToNormal.selected)

        val preferNot = FormRules.toggle(programs, listOf("None"), "Prefer not to answer")
        assertEquals(listOf("Prefer not to answer"), preferNot.selected)

        val cleared = FormRules.toggle(programs, preferNot.selected, "Prefer not to answer")
        assertTrue(cleared.selected.isEmpty())
    }

    @Test fun `unconditional protection answers survive branch flips`() {
        val protection = QuestionnaireSections.protection

        val state = mapOf(
            "philHealth" to value("Yes"),
            "philHealthRole" to value("Dependent"),
            "dswd" to value("Yes"),
            "fourPs" to value("Yes"),
            "programs" to AnswerValue.Multi(listOf("4Ps")),
            "assistance" to value("Yes"),
            "assistanceDetail" to value("Ayuda"),
        )

        // philHealth flipping to No clears only philHealthRole.
        val cleared = FormRules.update(protection, state, "philHealth", value("No"))
        assertFalse("philHealthRole should be cleared", cleared.containsKey("philHealthRole"))
        assertEquals("Yes", cleared.scalar("dswd"))
        assertEquals("Yes", cleared.scalar("fourPs"))
        assertEquals(listOf("4Ps"), cleared.multi("programs"))
        assertEquals("Yes", cleared.scalar("assistance"))
        assertEquals("Ayuda", cleared.scalar("assistanceDetail"))

        // assistance flipping to No clears only assistanceDetail.
        val noAssistance = FormRules.update(protection, state, "assistance", value("No"))
        assertFalse("assistanceDetail should be cleared", noAssistance.containsKey("assistanceDetail"))
        assertEquals("Dependent", noAssistance.scalar("philHealthRole"))
    }

    @Test fun `fixed point clearing is order independent for disaster branches`() {
        val disaster = QuestionnaireSections.disaster
        // Dependency order in the schema must not affect cascading clearing.
        val withOther = mapOf(
            "evacuationKnown" to value("Yes"),
            "evacuation" to value("Other"),
            "evacuationOther" to value("Rooftop"),
        )
        val cleared = FormRules.update(disaster, withOther, "evacuationKnown", value("No"))
        assertFalse(cleared.containsKey("evacuation"))
        assertFalse(cleared.containsKey("evacuationOther"))
    }
}
