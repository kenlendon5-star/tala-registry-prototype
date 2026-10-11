package ph.tala.registry.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ph.tala.registry.data.local.HouseholdRow
import ph.tala.registry.data.local.InterviewRow
import ph.tala.registry.data.local.MemberAnswerRow
import ph.tala.registry.data.local.MemberRow
import ph.tala.registry.data.local.StoredRecord
import ph.tala.registry.data.local.WriteResult
import ph.tala.registry.domain.edit.HouseholdDraft
import ph.tala.registry.domain.edit.MemberDraft
import ph.tala.registry.domain.model.HouseholdId
import ph.tala.registry.domain.model.HouseholdStatus
import ph.tala.registry.domain.model.MemberId

/**
 * Day 6 repository checks. They run against [FakeRecordStore], which enforces the same
 * snapshot, uniqueness and referential rules as the Room store, so the mapping between
 * editing drafts and stored records is exercised end to end on the JVM.
 */
class HouseholdRepositoryTest {
    private val store = FakeRecordStore()
    private var interviews = 0
    private val repository = RoomHouseholdRepository(
        store,
        newId = { "b0000000-0000-4000-8000-0000000000${(interviews++).toString().padStart(2, '0')}" },
    )

    private fun id(tag: String) = HouseholdId("c0000000-0000-4000-8000-000000000$tag")
    private fun draft(head: String, address: String = "24 Mabini Street", status: HouseholdStatus = HouseholdStatus.InProgress) =
        HouseholdDraft(head, address, status)

    @Test
    fun creatingTwoHouseholdsKeepsBothRecordsAndTheirOwnValues() = runBlocking {
        assertEquals(WriteResult.Saved, repository.createHousehold(id("0a1"), draft("  Maria Santos  ")))
        assertEquals(WriteResult.Saved, repository.createHousehold(id("0a2"), draft("Ramon Reyes", "8 Rizal Street", HouseholdStatus.Callback)))

        val households = repository.households.first()
        assertEquals(listOf("HH-00001", "HH-00002"), households.map { it.displayCode })
        assertEquals(listOf("Maria Santos", "Ramon Reyes"), households.map { it.headName })
        assertNotEquals(households[0].id, households[1].id)

        assertEquals(WriteResult.Saved, repository.updateHousehold(id("0a1"), draft("Maria Santos-Reyes", status = HouseholdStatus.Reviewed)))
        val after = repository.households.first()
        val updated = after.first { it.id == id("0a1") }
        assertEquals("Maria Santos-Reyes", updated.headName)
        assertEquals(HouseholdStatus.Reviewed, updated.status)
        // The second household keeps its own head name, address and status.
        assertEquals(draft("Ramon Reyes", "8 Rizal Street", HouseholdStatus.Callback).headName, after.first { it.id == id("0a2") }.headName)
        assertEquals("8 Rizal Street", after.first { it.id == id("0a2") }.address)
        assertEquals(HouseholdStatus.Callback, after.first { it.id == id("0a2") }.status)
        assertEquals("HH-00002", after.first { it.id == id("0a2") }.displayCode)
    }

    @Test
    fun retriedCreateKeepsTheSameIdentityAndDisplayCode() = runBlocking {
        assertEquals(WriteResult.Saved, repository.createHousehold(id("0b1"), draft("Elena Cruz")))
        assertEquals(WriteResult.Saved, repository.createHousehold(id("0b1"), draft("Elena Dizon Cruz", "16 Bonifacio Street")))
        val households = repository.households.first()
        assertEquals(1, households.size)
        assertEquals("HH-00001", households.single().displayCode)
        assertEquals("Elena Dizon Cruz", households.single().headName)
        assertEquals(id("0b1"), households.single().id)
    }

    @Test
    fun aFailedWriteReportsFailureAndLeavesTheSavedRecordUnchanged() = runBlocking {
        assertEquals(WriteResult.Saved, repository.createHousehold(id("0c1"), draft("Maria Santos")))
        store.failWith = "Could not save on this device. The previous saved record is unchanged."
        val result = repository.updateHousehold(id("0c1"), draft("Never Saved"))
        assertTrue("Expected a failed write, got $result", result is WriteResult.Failed)
        assertEquals("Could not save on this device. The previous saved record is unchanged.", (result as WriteResult.Failed).message)
        store.failWith = null
        assertEquals("Maria Santos", repository.households.first().single().headName)
    }

    @Test
    fun editingAMissingRecordFailsWithAPlainMessage() = runBlocking {
        val result = repository.updateHousehold(id("0d1"), draft("Nobody"))
        assertTrue("Expected a failed write, got $result", result is WriteResult.Failed)
        val message = (result as WriteResult.Failed).message
        assertFalse(message.contains("SQLite", ignoreCase = true))
        assertFalse(message.contains(".db"))
        assertTrue(repository.households.first().isEmpty())
    }

    @Test
    fun blankDraftsAreRejectedBeforeAnyWrite() = runBlocking {
        assertEquals(WriteResult.Failed("Some fields are not valid yet. Check the highlighted answers and save again."), repository.createHousehold(id("0e1"), draft("   ")))
        assertTrue(repository.households.first().isEmpty())
    }

    @Test
    fun archivingAndRestoringOnlyTouchTheTargetHousehold() = runBlocking {
        repository.createHousehold(id("0f1"), draft("Maria Santos"))
        repository.createHousehold(id("0f2"), draft("Ramon Reyes"))
        assertEquals(WriteResult.Saved, repository.setArchived(id("0f1"), true))
        val archived = repository.households.first()
        assertTrue(archived.first { it.id == id("0f1") }.archived)
        assertFalse(archived.first { it.id == id("0f2") }.archived)
        assertEquals(2, archived.size)
        assertEquals(WriteResult.Saved, repository.setArchived(id("0f1"), false))
        assertNull(repository.households.first().first { it.id == id("0f1") }.archivedAt)
    }

    @Test
    fun addingRenamingAndRemovingMembersKeepsTheRestOfTheRecord() = runBlocking {
        val household = id("1a1")
        val interview = "d0000000-0000-4000-8000-000000000001"
        val kept = "e0000000-0000-4000-8000-000000000001"
        val removed = "e0000000-0000-4000-8000-000000000002"
        val added = MemberId("e0000000-0000-4000-8000-000000000003")
        val seeded = FakeRecordStore(
            listOf(
                StoredRecord(
                    household = HouseholdRow(household.value, "HH-00901", "Maria Santos", "24 Mabini Street", "InProgress"),
                    members = listOf(MemberRow(kept, household.value, "Juan Santos"), MemberRow(removed, household.value, "Pedro Santos")),
                    interview = InterviewRow(interview, household.value),
                    memberAnswers = listOf(
                        MemberAnswerRow(interview, household.value, kept, "demographics", "firstName", "scalar", "\"Juan\""),
                        MemberAnswerRow(interview, household.value, removed, "demographics", "firstName", "scalar", "\"Pedro\""),
                    ),
                ),
            ),
        )
        val repo = RoomHouseholdRepository(seeded)
        assertEquals(WriteResult.Saved, repo.saveMember(household, MemberId(kept), MemberDraft("  Juan Dela Cruz  ")))
        assertEquals(WriteResult.Saved, repo.saveMember(household, added, MemberDraft("Ana Santos")))
        assertEquals(WriteResult.Saved, repo.removeMember(household, MemberId(removed)))

        val record = requireNotNull(seeded.load(household.value))
        assertEquals(listOf(kept, added.value), record.members.map { it.id })
        assertEquals("Juan Dela Cruz", record.members.first { it.id == kept }.displayName)
        // The removed member's answers go with the member; the other member keeps his.
        assertEquals(listOf(kept), record.memberAnswers.map { it.memberId })
        assertEquals("Maria Santos", record.household.headName)
        assertEquals("HH-00901", record.household.displayCode)
    }

    @Test
    fun seedingFixturesIsIdempotentAndKeepsUserEdits() = runBlocking {
        val repo = RoomHouseholdRepository(store)
        assertEquals(WriteResult.Saved, repo.seedMissing(DemoFixtures.records()))
        assertEquals(listOf("HH-00452", "HH-00453", "HH-00454"), repo.households.first().map { it.displayCode })
        assertEquals(4, repo.households.first().first { it.displayCode == "HH-00452" }.members.size)

        assertEquals(WriteResult.Saved, repo.updateHousehold(HouseholdId(DemoFixtures.SANTOS), draft("Maria Santos edited")))
        assertEquals(WriteResult.Saved, repo.seedMissing(DemoFixtures.records()))
        val households = repo.households.first()
        assertEquals(3, households.size)
        assertEquals("Maria Santos edited", households.first { it.displayCode == "HH-00452" }.headName)

        // User-created records continue after the seeded labels.
        assertEquals(WriteResult.Saved, repo.createHousehold(id("2b1"), draft("Luna Reyes")))
        assertEquals("HH-00455", repo.households.first().last().displayCode)
    }
}