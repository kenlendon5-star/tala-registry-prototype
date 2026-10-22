package ph.tala.registry.ui.home

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import ph.tala.registry.data.*
import ph.tala.registry.data.local.WriteResult
import ph.tala.registry.domain.edit.HouseholdDraft
import ph.tala.registry.domain.form.InterviewRules
import ph.tala.registry.domain.form.QuestionnaireSections
import ph.tala.registry.domain.model.*

class HomePurokRepositoryTest {
    @Test fun summaryChangesAfterSaveAndClearAndDoesNotReadAddress() = runTest {
        val repo = RoomHouseholdRepository(FakeRecordStore())
        val id = HouseholdId("home-purok")
        assertEquals(WriteResult.Saved, repo.createHousehold(id, HouseholdDraft("Fictional Family", "Purok 99", HouseholdStatus.InProgress)))
        val original = repo.interviewRecord(id)!!
        val base = InterviewRules.answers(original, QuestionnaireSections.setup)
        val answers = base + ("purok" to AnswerValue.Scalar("Purok 2"))
        assertEquals(WriteResult.Saved, repo.saveDraftSection(id, "setup", null, answers, base))
        assertEquals("Purok 2", repo.householdPuroks.first()[id.value])
        assertEquals(listOf(PurokCount("Purok 2", 1)), purokCounts(repo.households.first(), repo.householdPuroks.first()))
        assertEquals(WriteResult.Saved, repo.saveDraftSection(id, "setup", null, answers + ("purok" to AnswerValue.Scalar("")), answers))
        assertEquals(listOf(PurokCount("Not Recorded", 1, true)), purokCounts(repo.households.first(), repo.householdPuroks.first()))
        repo.setArchived(id, true)
        assertTrue(purokCounts(repo.households.first(), repo.householdPuroks.first()).isEmpty())
    }
}
