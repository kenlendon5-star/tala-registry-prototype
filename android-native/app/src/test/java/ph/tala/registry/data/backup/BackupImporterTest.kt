package ph.tala.registry.data.backup

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import ph.tala.registry.data.FakeRecordStore
import ph.tala.registry.data.local.AttachmentRow
import ph.tala.registry.data.local.HouseholdRow
import ph.tala.registry.data.local.InterviewRow
import ph.tala.registry.data.local.StoredRecord
import java.io.ByteArrayInputStream

class BackupImporterTest {
    @get:Rule val temporary = TemporaryFolder()

    private val householdId = "10000000-0000-4000-8000-000000000001"
    private val interviewId = "20000000-0000-4000-8000-000000000002"
    private val attachmentId = "30000000-0000-4000-8000-000000000003"
    private val bytes = "verified-image-bytes".toByteArray()

    @Test
    fun `preview and restore replace matching identity atomically with verified attachment`() = runTest {
        val store = FakeRecordStore(listOf(stored("Old name")))
        val media = FakeRestoreStore()
        val importer = BackupImporter(store, media, temporary.newFolder("imports"))

        val preview = importer.preview { ByteArrayInputStream(encodedBackup("New name")) }
        assertTrue(preview is BackupImporter.PreviewResult.Ready)
        val ready = (preview as BackupImporter.PreviewResult.Ready).preview
        assertEquals(1, ready.replacedCount)
        assertEquals(1, ready.attachmentCount)

        val result = importer.restore(ready)

        assertTrue(result is BackupImporter.RestoreResult.Restored)
        assertEquals("New name", store.load(householdId)?.household?.headName)
        assertTrue(store.load(householdId)?.attachments?.single()?.relativePath?.startsWith("attachments/") == true)
        assertEquals(1, media.staged.size)
    }

    @Test
    fun `hash mismatch is rejected before any record or attachment changes`() = runTest {
        val store = FakeRecordStore(listOf(stored("Unchanged")))
        val media = FakeRestoreStore()
        val importer = BackupImporter(store, media, temporary.newFolder("bad-hash"))
        val broken = backup("Changed").copy(
            records = backup("Changed").records.map { record ->
                record.copy(attachments = record.attachments.map { it.copy(sha256 = "0".repeat(64)) })
            },
        )

        val result = importer.preview { ByteArrayInputStream(BackupJson.encodeToString(broken).encodeToByteArray()) }

        assertTrue(result is BackupImporter.PreviewResult.Failed)
        assertEquals("Unchanged", store.load(householdId)?.household?.headName)
        assertTrue(media.staged.isEmpty())
    }

    @Test
    fun `display code owned by another identity is refused during preview`() = runTest {
        val otherId = "40000000-0000-4000-8000-000000000004"
        val store = FakeRecordStore(listOf(stored("Other", otherId)))
        val importer = BackupImporter(store, FakeRestoreStore(), temporary.newFolder("collision"))

        val result = importer.preview { ByteArrayInputStream(encodedBackup("Incoming")) }

        assertTrue(result is BackupImporter.PreviewResult.Failed)
        assertEquals(1, store.loadAll().size)
        assertEquals(otherId, store.loadAll().single().household.id)
    }

    @Test
    fun `database failure discards staged files and preserves prior record`() = runTest {
        val store = FakeRecordStore(listOf(stored("Unchanged"))).apply { failWith = "disk full" }
        val media = FakeRestoreStore()
        val importer = BackupImporter(store, media, temporary.newFolder("rollback"))
        val preview = (importer.preview { ByteArrayInputStream(encodedBackup("Changed")) } as BackupImporter.PreviewResult.Ready).preview

        val result = importer.restore(preview)

        assertTrue(result is BackupImporter.RestoreResult.Failed)
        assertEquals("Unchanged", store.load(householdId)?.household?.headName)
        assertEquals(media.staged, media.discarded)
    }

    @Test
    fun `old attachment cleanup failure cannot remove committed restored media`() = runTest {
        val oldAttachment = AttachmentRow(attachmentId, householdId, null, "household-photo", "attachments/old.png", "image/png")
        val store = FakeRecordStore(listOf(stored("Old name").copy(attachments = listOf(oldAttachment))))
        val media = FakeRestoreStore().apply { failDiscard = true }
        val importer = BackupImporter(store, media, temporary.newFolder("cleanup-failure"))
        val preview = (importer.preview { ByteArrayInputStream(encodedBackup("New name")) } as BackupImporter.PreviewResult.Ready).preview

        val result = importer.restore(preview)

        assertTrue(result is BackupImporter.RestoreResult.Restored)
        assertEquals("New name", store.load(householdId)?.household?.headName)
        assertEquals(media.staged.single(), store.load(householdId)?.attachments?.single()?.relativePath)
    }

    @Test
    fun `incomplete reviewed backup is restored as a draft`() = runTest {
        val store = FakeRecordStore()
        val importer = BackupImporter(store, FakeRestoreStore(), temporary.newFolder("review-guard"))
        val reviewed = backup("Incomplete").copy(
            records = backup("Incomplete").records.map { it.copy(status = "Reviewed") },
        )
        val preview = importer.preview {
            ByteArrayInputStream(BackupJson.encodeToString(reviewed).encodeToByteArray())
        } as BackupImporter.PreviewResult.Ready

        val result = importer.restore(preview.preview)

        assertTrue(result is BackupImporter.RestoreResult.Restored)
        assertEquals("InProgress", store.load(householdId)?.household?.status)
    }

    @Test
    fun `duplicate attachment identity across households is rejected before staging`() = runTest {
        val store = FakeRecordStore()
        val media = FakeRestoreStore()
        val importer = BackupImporter(store, media, temporary.newFolder("duplicate-attachment"))
        val first = backup("First").records.single()
        val second = first.copy(
            id = "60000000-0000-4000-8000-000000000006",
            displayCode = "HH-00002",
            interview = first.interview.copy(id = "70000000-0000-4000-8000-000000000007"),
        )
        val duplicated = backup("First").copy(records = listOf(first, second))

        val result = importer.preview {
            ByteArrayInputStream(BackupJson.encodeToString(duplicated).encodeToByteArray())
        }

        assertTrue(result is BackupImporter.PreviewResult.Failed)
        assertTrue(media.staged.isEmpty())
        assertTrue(store.loadAll().isEmpty())
    }

    @Test
    fun `consent summary mismatch is rejected before staging`() = runTest {
        val media = FakeRestoreStore()
        val importer = BackupImporter(FakeRecordStore(), media, temporary.newFolder("consent-mismatch"))
        val source = backup("Mismatch")
        val mismatched = source.copy(
            records = source.records.map { record ->
                record.copy(interview = record.interview.copy(consent = "Yes"))
            },
        )

        val result = importer.preview {
            ByteArrayInputStream(BackupJson.encodeToString(mismatched).encodeToByteArray())
        }

        assertTrue(result is BackupImporter.PreviewResult.Failed)
        assertTrue(media.staged.isEmpty())
    }

    private fun encodedBackup(name: String) = BackupJson.encodeToString(backup(name)).encodeToByteArray()

    private fun backup(name: String) = BackupFile(
        exportedAt = "2026-10-24T00:00:00Z",
        records = listOf(
            BackupRecord(
                id = householdId,
                displayCode = "HH-00001",
                headName = name,
                address = "Purok 1",
                status = "InProgress",
                archivedAt = null,
                members = emptyList(),
                interview = BackupInterview(interviewId, "tala-demo-v1", "Unanswered", null, null, emptyList()),
                visits = emptyList(),
                location = null,
                attachments = listOf(
                    BackupAttachment(
                        id = attachmentId,
                        memberId = null,
                        kind = "household-photo",
                        mimeType = "image/png",
                        byteSize = bytes.size,
                        sha256 = BackupMapper.sha256Hex(bytes),
                        contentBase64 = BackupMapper.base64(bytes),
                    ),
                ),
            ),
        ),
    )

    private fun stored(name: String, id: String = householdId) = StoredRecord(
        household = HouseholdRow(id, "HH-00001", name, "Purok 1", "InProgress"),
        members = emptyList(),
        interview = InterviewRow(if (id == householdId) interviewId else "50000000-0000-4000-8000-000000000005", id, "tala-demo-v1"),
    )

    private class FakeRestoreStore : RestoredAttachmentStore {
        val staged = mutableListOf<String>()
        val discarded = mutableListOf<String>()
        var failDiscard = false

        override suspend fun stage(bytes: ByteArray, mimeType: String): String =
            "attachments/${staged.size + 1}.png".also(staged::add)

        override suspend fun discard(relativePaths: Collection<String>) {
            if (failDiscard) error("cleanup failed")
            discarded += relativePaths
        }
    }
}
