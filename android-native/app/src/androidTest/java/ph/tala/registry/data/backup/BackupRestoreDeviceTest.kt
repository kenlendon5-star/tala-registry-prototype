package ph.tala.registry.data.backup

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ph.tala.registry.data.PhotoStore
import ph.tala.registry.data.local.AttachmentRow
import ph.tala.registry.data.local.CoordinateRow
import ph.tala.registry.data.local.HouseholdAnswerRow
import ph.tala.registry.data.local.HouseholdRow
import ph.tala.registry.data.local.InterviewRow
import ph.tala.registry.data.local.MemberRow
import ph.tala.registry.data.local.RegistryDatabase
import ph.tala.registry.data.local.RoomRecordStore
import ph.tala.registry.data.local.StoredRecord
import ph.tala.registry.data.local.WriteResult
import java.io.File
import java.util.Base64

class BackupRestoreDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var sourceDatabase: RegistryDatabase
    private lateinit var targetDatabase: RegistryDatabase
    private lateinit var sourceRoot: File
    private lateinit var targetRoot: File
    private lateinit var backup: File

    @Before
    fun setUp() {
        context.deleteDatabase(SOURCE_DB)
        context.deleteDatabase(TARGET_DB)
        sourceDatabase = RegistryDatabase.open(context, SOURCE_DB)
        targetDatabase = RegistryDatabase.open(context, TARGET_DB)
        sourceRoot = File(context.cacheDir, "restore-source").apply { deleteRecursively(); mkdirs() }
        targetRoot = File(context.cacheDir, "restore-target").apply { deleteRecursively(); mkdirs() }
        backup = File(context.cacheDir, "restore-roundtrip.json")
    }

    @After
    fun tearDown() {
        sourceDatabase.close()
        targetDatabase.close()
        context.deleteDatabase(SOURCE_DB)
        context.deleteDatabase(TARGET_DB)
        sourceRoot.deleteRecursively()
        targetRoot.deleteRecursively()
        backup.delete()
    }

    @Test
    fun exportedRoomGraphAndAttachmentRestoreIntoCleanDatabase() = runBlocking {
        val source = RoomRecordStore(sourceDatabase)
        val target = RoomRecordStore(targetDatabase)
        val png = Base64.getDecoder().decode(ONE_PIXEL_PNG)
        val relative = "attachments/$ATTACHMENT_ID.png"
        File(sourceRoot, relative).apply { parentFile?.mkdirs(); writeBytes(png) }
        val original = StoredRecord(
            household = HouseholdRow(HOUSEHOLD_ID, "HH-00991", "Lina Cruz", "Purok 7", "InProgress"),
            members = listOf(MemberRow(MEMBER_ID, HOUSEHOLD_ID, "Mina Cruz", 0)),
            interview = InterviewRow(INTERVIEW_ID, HOUSEHOLD_ID, "tala-demo-v1"),
            householdAnswers = listOf(HouseholdAnswerRow(INTERVIEW_ID, "consent", "consent", "scalar", "\"Yes\"")),
            coordinates = CoordinateRow(HOUSEHOLD_ID, 14.5995, 120.9842, null, 1_798_000_000_000),
            attachments = listOf(AttachmentRow(ATTACHMENT_ID, HOUSEHOLD_ID, null, "household-photo", relative, "image/png")),
        )
        assertEquals(WriteResult.Saved, source.save(original))
        val exported = BackupExporter().export(listOf(original), object : AttachmentSource {
            override suspend fun read(relativePath: String): ByteArray? = File(sourceRoot, relativePath).takeIf(File::isFile)?.readBytes()
        }) { backup.outputStream() }
        assertTrue(exported is BackupExporter.ExportResult.Saved)

        val importer = BackupImporter(target, PhotoStore(context, targetRoot), File(context.cacheDir, "restore-previews"))
        val preview = importer.preview { backup.inputStream() }
        assertTrue(preview is BackupImporter.PreviewResult.Ready)
        val restored = importer.restore((preview as BackupImporter.PreviewResult.Ready).preview)

        assertTrue(restored is BackupImporter.RestoreResult.Restored)
        val loaded = requireNotNull(target.load(HOUSEHOLD_ID))
        assertEquals("Lina Cruz", loaded.household.headName)
        assertEquals(listOf("Mina Cruz"), loaded.members.map { it.displayName })
        assertEquals(14.5995, loaded.coordinates?.latitude ?: 0.0, 0.0)
        assertEquals("Yes", ph.tala.registry.data.local.AnswerCodec.decode(loaded.householdAnswers.single().kind, loaded.householdAnswers.single().value).let { (it as ph.tala.registry.domain.model.AnswerValue.Scalar).text })
        assertTrue(File(targetRoot, loaded.attachments.single().relativePath).readBytes().contentEquals(png))
    }

    private companion object {
        const val SOURCE_DB = "tala-backup-source.db"
        const val TARGET_DB = "tala-backup-target.db"
        const val HOUSEHOLD_ID = "81000000-0000-4000-8000-000000000001"
        const val MEMBER_ID = "82000000-0000-4000-8000-000000000002"
        const val INTERVIEW_ID = "83000000-0000-4000-8000-000000000003"
        const val ATTACHMENT_ID = "84000000-0000-4000-8000-000000000004"
        const val ONE_PIXEL_PNG = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
    }
}
