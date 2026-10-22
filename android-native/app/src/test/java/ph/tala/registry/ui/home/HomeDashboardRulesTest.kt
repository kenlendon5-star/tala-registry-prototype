package ph.tala.registry.ui.home

import org.junit.Assert.*
import org.junit.Test
import ph.tala.registry.data.InterviewResume
import ph.tala.registry.domain.model.*

class HomeDashboardRulesTest {
    private fun household(id: String, archived: Boolean = false) = Household(
        HouseholdId(id), id, "Fictional Household", "Purok 99 (address only)", HouseholdStatus.InProgress,
        listOf(Member(MemberId("$id-member"), HouseholdId(id), "Fictional Resident")),
        archivedAt = if (archived) 1 else null,
    )

    @Test fun puroksUseExplicitAnswersNormalizeFormattingAndKeepMissingLast() {
        val records = listOf("A", "B", "C", "D", "E", "F").map { household(it) } + household("archived", true)
        val rows = purokCounts(records, mapOf("A" to " purok 02 ", "B" to "2", "C" to "Purok 10",
            "D" to " riverside  north ", "E" to "   ", "archived" to "1"))
        assertEquals(listOf("Purok 2", "Purok 10", "Riverside North", "Not Recorded"), rows.map { it.label })
        assertEquals(listOf(2, 1, 1, 2), rows.map { it.count })
        assertEquals(6, rows.sumOf { it.count })
        assertTrue(rows.last().unrecorded)
        assertFalse(rows.any { it.label == "Purok 99" })
    }

    @Test fun zeroHouseholdsHaveNoFabricatedPuroks() {
        assertTrue(purokCounts(emptyList(), mapOf("stale" to "1")).isEmpty())
        assertNull(householdPurokLabel(null))
        assertNull(householdPurokLabel("   "))
    }

    @Test fun recentInterviewKeepsItsHouseholdAcrossReordering() {
        val a = household("A")
        val b = household("B")
        val ref = InterviewResume(b.id, "demographics", "B-member")
        assertEquals(ref, validResume(ref, listOf(a, b)))
        assertEquals(ref, validResume(ref, listOf(b, a)))
        assertNull(validResume(null, listOf(a, b)))
    }

    @Test fun removedOrArchivedHouseholdNeverBecomesAnotherInterview() {
        val ref = InterviewResume(HouseholdId("A"))
        assertNull(validResume(ref, listOf(household("B"))))
        assertNull(validResume(ref, listOf(household("A", true))))
    }

    @Test fun removedMemberAndInvalidSectionFallBackToHouseholdSetup() {
        val household = household("A")
        assertEquals(InterviewResume(household.id), validResume(InterviewResume(household.id, "demographics", "removed"), listOf(household)))
        assertEquals(InterviewResume(household.id), validResume(InterviewResume(household.id, "unknown", "A-member"), listOf(household)))
        assertEquals(InterviewResume(household.id), validResume(InterviewResume(household.id, "members"), listOf(household)))
        assertEquals(InterviewResume(household.id), validResume(InterviewResume(household.id, "setup", "A-member"), listOf(household)))
    }
}
