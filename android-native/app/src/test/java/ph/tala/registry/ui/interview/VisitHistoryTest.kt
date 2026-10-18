package ph.tala.registry.ui.interview

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import ph.tala.registry.data.*
import ph.tala.registry.data.local.WriteResult
import ph.tala.registry.domain.edit.HouseholdDraft
import ph.tala.registry.domain.model.*

class VisitHistoryTest {
    private val id = HouseholdId("household")
    private val callback: AnswerState = mapOf("visitDate" to AnswerValue.Scalar("2026-10-18"), "visitResult" to AnswerValue.Scalar("Callback"), "callbackDate" to AnswerValue.Scalar("2026-10-20T10:30"))

    @Test fun `recorded visits preserve earlier outcomes and ignore repeated acknowledgements`() = runTest {
        val repository = RoomHouseholdRepository(FakeRecordStore())
        repository.createHousehold(id, HouseholdDraft("Fictional Household", "Sample Street", HouseholdStatus.InProgress))
        repository.saveDraftSection(id, "visit", null, callback, emptyMap())
        assertEquals(WriteResult.Saved, repository.recordVisit(id, callback))
        assertEquals(WriteResult.Saved, repository.recordVisit(id, callback))
        val next = callback - "callbackDate" + ("visitResult" to AnswerValue.Scalar("No respondent"))
        repository.saveDraftSection(id, "visit", null, next, callback)
        assertEquals(WriteResult.Saved, repository.recordVisit(id, next))
        val visits = repository.interviewRecord(id)!!.visits
        assertEquals(listOf("Callback", "No respondent"), visits.map { it.outcome })
        assertEquals("2026-10-20T10:30", visits.first().callbackAt)
        assertNull(visits.last().callbackAt)
        assertTrue(visits.last().recordedAt > visits.first().recordedAt)
    }

    @Test fun `failed visit write is retryable and stale or invalid actions are refused`() = runTest {
        val store = FakeRecordStore()
        val repository = RoomHouseholdRepository(store)
        repository.createHousehold(id, HouseholdDraft("Fictional Household", "Sample Street", HouseholdStatus.InProgress))
        assertTrue(repository.recordVisit(id, emptyMap()) is WriteResult.Failed)
        repository.saveDraftSection(id, "visit", null, callback, emptyMap())
        assertTrue(repository.recordVisit(id, emptyMap()) is WriteResult.Failed)
        store.failWith = "Disk full"
        assertTrue(repository.recordVisit(id, callback) is WriteResult.Failed)
        assertTrue(repository.interviewRecord(id)!!.visits.isEmpty())
        store.failWith = null
        assertEquals(WriteResult.Saved, repository.recordVisit(id, callback))
        assertEquals(1, repository.interviewRecord(id)!!.visits.size)
    }
}
