package ph.tala.registry.data.local

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ph.tala.registry.domain.model.AnswerValue

/**
 * Day 5 storage spike checks. These run on the phone against a real app-private SQLite
 * file, so reopen, rollback and failed-write reporting are demonstrated on the device
 * rather than against an in-memory database.
 */
class RecordStoreDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: RegistryDatabase
    private lateinit var store: RoomRecordStore

    @Before fun startWithAnEmptyFile() {
        context.deleteDatabase(SampleRecords.STORE_DB_NAME)
        database = RegistryDatabase.open(context, SampleRecords.STORE_DB_NAME)
        store = RoomRecordStore(database)
    }

    @After fun closeAndRemove() {
        database.close()
        context.deleteDatabase(SampleRecords.STORE_DB_NAME)
    }

    /** Closes the connection so the next read must come from the file on disk. */
    private fun reopen() {
        database.close()
        database = RegistryDatabase.open(context, SampleRecords.STORE_DB_NAME)
        store = RoomRecordStore(database)
    }

    private fun load(): StoredRecord? = runBlocking { store.load(SampleRecords.HOUSEHOLD) }

    private fun scalar(row: MemberAnswerRow): String =
        (AnswerCodec.decode(row.kind, row.value) as AnswerValue.Scalar).text

    @Test fun savedSnapshotReopensAfterCloseWithScopedAnswersAndAttachmentsIntact() {
        val record = SampleRecords.record()
        assertEquals(WriteResult.Saved, runBlocking { store.save(record) })
        reopen()
        assertEquals(record, load())
        val loaded = requireNotNull(load())
        // Member answers stay attached to their own member identity.
        assertEquals(
            listOf(SampleRecords.MEMBER_ONE, SampleRecords.MEMBER_ONE, SampleRecords.MEMBER_TWO, SampleRecords.MEMBER_TWO),
            loaded.memberAnswers.map { it.memberId },
        )
        assertEquals("2000-02-29", scalar(loaded.memberAnswers[0]))
        assertEquals("Maria", scalar(loaded.memberAnswers[1]))
        assertEquals("Juan", scalar(loaded.memberAnswers[3]))
        assertEquals(listOf("Bank", "E-money"), (AnswerCodec.decode(loaded.memberAnswers[2].kind, loaded.memberAnswers[2].value) as AnswerValue.Multi).selected)
        assertEquals(14.5995, requireNotNull(loaded.coordinates).latitude, 0.0)
        assertEquals("attachments/${SampleRecords.ATTACHMENT}.jpg", loaded.attachments.single().relativePath)
    }

    @Test fun houseListObservesSavedRecordsInDisplayCodeOrder() {
        val record = SampleRecords.record()
        val other = SampleRecords.otherRecord()
        runBlocking {
            assertEquals(WriteResult.Saved, store.save(record))
            assertEquals(WriteResult.Saved, store.save(other))
            assertEquals(listOf("HH-00899", "HH-00901"), store.households.first().map { it.displayCode })
            // Saving a second household never overwrites the first.
            assertEquals(record, store.load(record.household.id))
            assertEquals(other, store.load(other.household.id))
        }
    }

    @Test fun snapshotReplacementClearsRemovedAnswersMembersAndCoordinates() {
        val first = SampleRecords.record()
        runBlocking { store.save(first) }
        // A hidden conditional answer is cleared, one member is removed and no coordinate is kept.
        val trimmed = first.copy(
            householdAnswers = first.householdAnswers.filterNot { it.field == "visitNotes" },
            members = first.members.filter { it.id == SampleRecords.MEMBER_ONE },
            memberAnswers = first.memberAnswers.filter { it.memberId == SampleRecords.MEMBER_ONE },
            coordinates = null,
            attachments = emptyList(),
        )
        assertEquals(WriteResult.Saved, runBlocking { store.save(trimmed) })
        reopen()
        assertEquals(trimmed, load())
        val loaded = requireNotNull(load())
        assertNull(loaded.coordinates)
        assertTrue(loaded.attachments.isEmpty())
        assertFalse(loaded.householdAnswers.any { it.field == "visitNotes" })
        assertFalse(loaded.memberAnswers.any { it.memberId == SampleRecords.MEMBER_TWO })
    }

    @Test fun failedWriteReportsFailureAndLeavesTheSavedRecordUnchanged() {
        val record = SampleRecords.record()
        runBlocking { store.save(record) }
        val rejected = record.copy(
            household = record.household.copy(headName = "Never Saved"),
            attachments = listOf(record.attachments.single().copy(relativePath = "../../databases/secrets.db")),
        )
        val result = runBlocking { store.save(rejected) }
        assertTrue("Expected a failed write, got $result", result is WriteResult.Failed)
        val message = (result as WriteResult.Failed).message
        // Failure reporting stays actionable without leaking stored paths or SQL details.
        assertFalse(message.contains("secrets"))
        assertFalse(message.contains("SQLite", ignoreCase = true))
        reopen()
        assertEquals(record, load())
        assertEquals("Maria Santos", requireNotNull(load()).household.headName)
    }

    @Test fun constraintFailureInsideTheTransactionRollsBackEveryChange() {
        val record = SampleRecords.record()
        runBlocking { store.save(record) }
        // A duplicated member primary key fails only after the household row was updated.
        val poisoned = record.copy(
            household = record.household.copy(headName = "Rolled Back Head"),
            members = record.members + record.members.single { it.id == SampleRecords.MEMBER_TWO },
        )
        val result = runBlocking { store.save(poisoned) }
        assertTrue("Expected a failed write, got $result", result is WriteResult.Failed)
        reopen()
        assertEquals("The transaction must roll back to the last saved snapshot.", record, load())
    }

    @Test fun answerCodecRoundTripsQuotesNewlinesAndUnicode() {
        val scalarValue = AnswerValue.Scalar("She said \"hello\"\nSecond line · bayanihan ☀")
        val multiValue = AnswerValue.Multi(listOf("Bank", "E-money, wallet", "None"))
        val (scalarKind, scalarEncoded) = AnswerCodec.encode(scalarValue)
        val (multiKind, multiEncoded) = AnswerCodec.encode(multiValue)
        assertEquals("scalar", scalarKind)
        assertEquals("multi", multiKind)
        assertEquals(scalarValue, AnswerCodec.decode(scalarKind, scalarEncoded))
        assertEquals(multiValue, AnswerCodec.decode(multiKind, multiEncoded))
    }
}
