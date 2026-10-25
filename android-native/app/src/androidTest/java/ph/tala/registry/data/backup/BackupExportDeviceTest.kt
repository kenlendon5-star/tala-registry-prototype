package ph.tala.registry.data.backup

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ph.tala.registry.data.RoomHouseholdRepository
import ph.tala.registry.data.local.WriteResult
import ph.tala.registry.data.local.AttachmentRow
import ph.tala.registry.data.local.HouseholdRow
import ph.tala.registry.data.local.InterviewRow
import ph.tala.registry.data.local.MemberRow
import ph.tala.registry.data.local.RegistryDatabase
import ph.tala.registry.data.local.RoomRecordStore
import ph.tala.registry.data.local.StoredRecord
import ph.tala.registry.domain.model.HouseholdId
import java.io.File
import java.util.UUID

/**
 * D17 export against a real Room database and real files on the device: the saved
 * JSON re-parses with an independent Json instance (the "opens on another machine"
 * proxy), attachment bytes round-trip byte-for-byte, and an orphaned attachment row
 * (file missing on disk) is skipped and counted — never silently dropped or fatal.
 */
class BackupExportDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: RegistryDatabase
    private lateinit var store: RoomRecordStore
    private lateinit var repository: RoomHouseholdRepository
    private lateinit var photoStore: ph.tala.registry.data.PhotoStore
    private lateinit var backupFile: File

    @Before
    fun startWithCleanState() {
        context.deleteDatabase(BACKUP_DB_NAME)
        database = RegistryDatabase.open(context, BACKUP_DB_NAME)
        store = RoomRecordStore(database)
        repository = RoomHouseholdRepository(store)
        photoStore = ph.tala.registry.data.PhotoStore(context, root = context.filesDir)
        backupFile = File(context.cacheDir, "backup-export-test-${UUID.randomUUID()}.json")
    }

    @After
    fun closeAndCleanUp() {
        database.close()
        backupFile.delete()
        context.deleteDatabase(BACKUP_DB_NAME)
    }

    private suspend fun seed(householdId: String, displayCode: String, attachment: Pair<String, ByteArray>? = null): AttachmentRow? {
        val interviewId = UUID.randomUUID().toString()
        val attachmentRow = attachment?.let { (path, _) ->
            AttachmentRow(UUID.randomUUID().toString(), householdId, null, "household-photo", path, "image/jpeg")
        }
        val record = StoredRecord(
            household = HouseholdRow(householdId, displayCode, "Maria Santos", "24 Mabini Street", "InProgress"),
            members = listOf(MemberRow(UUID.randomUUID().toString(), householdId, "Juan Santos")),
            interview = InterviewRow(interviewId, householdId),
            householdAnswers = listOf(
                ph.tala.registry.data.local.HouseholdAnswerRow(interviewId, "consent", "consent", "scalar", "\"Yes\""),
            ),
            attachments = listOfNotNull(attachmentRow),
        )
        assertEquals(WriteResult.Saved::class, store.save(record)::class)
        attachment?.let { (path, bytes) ->
            File(context.filesDir, path).parentFile?.mkdirs()
            File(context.filesDir, path).writeBytes(bytes)
        }
        return attachmentRow
    }

    @Test
    fun exportReparsesAndRoundTripsAttachmentBytes() = runBlocking {
        val householdId = UUID.randomUUID().toString()
        val photoBytes = "attachment-bytes-for-roundtrip-0123456789".toByteArray()
        val path = "attachments/${UUID.randomUUID()}.jpg"
        val row = seed(householdId, "HH-00701", path to photoBytes)
        assertTrue(photoStore.read(path)?.contentEquals(photoBytes) == true)
        val record = requireNotNull(repository.interviewRecord(HouseholdId(householdId)))
        val exported = BackupExporter().export(
            selected = listOf(record),
            attachments = photoStore,
        ) { backupFile.outputStream() }
        assertTrue(exported is BackupExporter.ExportResult.Saved)
        assertEquals(0, (exported as BackupExporter.ExportResult.Saved).skippedCount)
        assertEquals(1, exported.attachmentCount)

        // Independent Json instance (no prettyPrint config) — the "another machine" proxy.
        val root = Json.parseToJsonElement(backupFile.readText()).jsonObject
        assertEquals("tala-backup", root["format"]!!.jsonPrimitive.content)
        assertEquals(1, root["formatVersion"]!!.jsonPrimitive.content.toInt())
        val recordJson = root["records"]!!.jsonArray.single().jsonObject
        assertEquals("HH-00701", recordJson["displayCode"]!!.jsonPrimitive.content)
        assertEquals("Yes", recordJson["interview"]!!.jsonObject["consent"]!!.jsonPrimitive.content)
        val attachmentJson = recordJson["attachments"]!!.jsonArray.single().jsonObject
        assertEquals(row?.id, attachmentJson["id"]!!.jsonPrimitive.content)
        assertEquals(photoBytes.size, attachmentJson["byteSize"]!!.jsonPrimitive.content.toInt())
        assertEquals(BackupMapper.sha256Hex(photoBytes), attachmentJson["sha256"]!!.jsonPrimitive.content)
        val decoded = java.util.Base64.getDecoder().decode(attachmentJson["contentBase64"]!!.jsonPrimitive.content)
        assertTrue(photoBytes.contentEquals(decoded))
        assertTrue(backupFile.length() > 0)
    }

    @Test
    fun orphanedAttachmentRowIsSkippedAndCounted() = runBlocking {
        val householdId = UUID.randomUUID().toString()
        // Reference exists in the database; file was never written (crash recovery gap).
        seed(householdId, "HH-00702", null).let { }
        val record = requireNotNull(repository.interviewRecord(HouseholdId(householdId)))
        // Add a second household whose attachment row has no file.
        val recordWithOrphan = record.copy(
            attachments = listOf(AttachmentRow(UUID.randomUUID().toString(), record.household.id, null, "household-photo", "attachments/missing.jpg", "image/jpeg")),
        )
        assertEquals(WriteResult.Saved::class, store.save(recordWithOrphan)::class)
        val exported = BackupExporter().export(
            selected = listOf(repository.interviewRecord(HouseholdId(householdId))!!),
            attachments = photoStore,
        ) { backupFile.outputStream() }
        assertTrue(exported is BackupExporter.ExportResult.Saved)
        assertEquals(1, (exported as BackupExporter.ExportResult.Saved).skippedCount)
        val root = Json.parseToJsonElement(backupFile.readText()).jsonObject
        val attachmentJson = root["records"]!!.jsonArray.single().jsonObject["attachments"]!!.jsonArray
        assertTrue(attachmentJson.isEmpty())
        // No dangling signature id either: the file never travelled.
        assertTrue(root["records"]!!.jsonArray.single().jsonObject["interview"]!!.jsonObject["signatureAttachmentId"].toString() == "null")
    }

    private companion object {
        const val BACKUP_DB_NAME = "tala-backup-export-test.db"
    }
}
