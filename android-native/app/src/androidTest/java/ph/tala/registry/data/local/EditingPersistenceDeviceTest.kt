package ph.tala.registry.data.local

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ph.tala.registry.data.RoomHouseholdRepository
import ph.tala.registry.domain.edit.HouseholdDraft
import ph.tala.registry.domain.edit.MemberDraft
import ph.tala.registry.domain.model.HouseholdId
import ph.tala.registry.domain.model.HouseholdStatus
import ph.tala.registry.domain.model.Member
import ph.tala.registry.domain.model.MemberId

/**
 * Day 6 acceptance on the phone: the editing repository writes through the real Room
 * stack into the app-private file, and household edits, member edits and archiving all
 * reload after the connection is closed and reopened.
 */
class EditingPersistenceDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: RegistryDatabase
    private lateinit var store: RoomRecordStore
    private lateinit var repository: RoomHouseholdRepository

    private val one = HouseholdId("9c1e0000-0000-4000-8000-000000000001")
    private val two = HouseholdId("9c1e0000-0000-4000-8000-000000000002")
    private val member = MemberId("9c1e0000-0000-4000-8000-000000000011")

    @Before fun startWithAnEmptyFile() {
        context.deleteDatabase(DB_NAME)
        open()
    }

    @After fun closeAndRemove() {
        database.close()
        context.deleteDatabase(DB_NAME)
    }

    private fun open() {
        database = RegistryDatabase.open(context, DB_NAME)
        store = RoomRecordStore(database)
        repository = RoomHouseholdRepository(store)
    }

    /** Closes the connection so the next read must come from the file on disk. */
    private fun reopen() {
        database.close()
        open()
    }

    @Test fun householdAndMemberEditsReloadAfterReopenAndNeverOverwriteEachOther() = runBlocking {
        assertEquals(WriteResult.Saved, repository.createHousehold(one, HouseholdDraft("Luna Reyes", "5 Sampaguita Street", HouseholdStatus.InProgress)))
        assertEquals(WriteResult.Saved, repository.createHousehold(two, HouseholdDraft("Ramon Reyes", "8 Rizal Street", HouseholdStatus.Callback)))
        assertEquals(WriteResult.Saved, repository.saveMember(one, member, MemberDraft("Luna Reyes Jr")))

        reopen()
        val households = repository.households.first()
        assertEquals(listOf("HH-00001", "HH-00002"), households.map { it.displayCode })
        assertEquals("Luna Reyes Jr", households.first { it.id == one }.members.single().displayName)
        assertEquals(emptyList<Member>(), households.first { it.id == two }.members)

        // The editor no longer grants Reviewed directly; the interview review path owns that status.
        assertTrue(repository.updateHousehold(one, HouseholdDraft("Luna Dizon Reyes", "5 Sampaguita Street", HouseholdStatus.Reviewed)) is WriteResult.Failed)
        assertEquals(WriteResult.Saved, repository.updateHousehold(one, HouseholdDraft("Luna Dizon Reyes", "5 Sampaguita Street", HouseholdStatus.InProgress)))
        assertEquals(WriteResult.Saved, repository.saveMember(one, member, MemberDraft("Luna Reyes Jr")))
        reopen()
        val after = repository.households.first()
        assertEquals("Luna Dizon Reyes", after.first { it.id == one }.headName)
        assertEquals("HH-00001", after.first { it.id == one }.displayCode)
        // The second household keeps its own head name, status and identity.
        assertEquals("Ramon Reyes", after.first { it.id == two }.headName)
        assertEquals(HouseholdStatus.Callback, after.first { it.id == two }.status)
    }

    @Test fun archivingKeepsTheRecordStoredAndRestoresTheSameIdentity() = runBlocking {
        assertEquals(WriteResult.Saved, repository.createHousehold(one, HouseholdDraft("Luna Reyes", "5 Sampaguita Street", HouseholdStatus.InProgress)))
        assertEquals(WriteResult.Saved, repository.setArchived(one, true))
        reopen()
        assertTrue(repository.households.first().single().archived)

        assertEquals(WriteResult.Saved, repository.setArchived(one, false))
        reopen()
        val restored = repository.households.first().single()
        assertFalse(restored.archived)
        assertEquals(one, restored.id)
        assertEquals("Luna Reyes", restored.headName)
    }

    private companion object {
        const val DB_NAME = "tala-registry-editing-test.db"
    }
}