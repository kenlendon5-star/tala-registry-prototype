package ph.tala.registry.ui.interview

import org.junit.Assert.assertEquals
import org.junit.Test
import ph.tala.registry.domain.form.QuestionnaireSections

/**
 * UX-04: the workspace directory, the wide index and the overview Sections tab all derive their
 * section numbers from this single canonical order. Pin it so a regenerated questionnaire that
 * reorders sections is caught and the numbering agreement is re-checked rather than silently
 * drifting (the old workspace renumbered a 17-item filtered list, mislabelling Demographics 04
 * while the Sections tab correctly showed 05).
 */
class SectionNumberingTest {

    @Test
    fun `canonical order keeps members at 04 and demographics at 05`() {
        val routes = QuestionnaireSections.all.map { it.route }
        assertEquals(18, routes.size)
        assertEquals("members", routes[3])
        assertEquals("demographics", routes[4])
    }

    @Test
    fun `canonical numbers are stable 1-based indices into the full questionnaire`() {
        assertEquals(1, canonicalSectionNumber("setup"))
        assertEquals(4, canonicalSectionNumber("members"))
        assertEquals(5, canonicalSectionNumber("demographics"))
        assertEquals(18, canonicalSectionNumber("consent"))
    }
}
