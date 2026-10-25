package ph.tala.registry.data.backup

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ph.tala.registry.data.local.AttachmentRow
import ph.tala.registry.data.local.HouseholdRow
import ph.tala.registry.data.local.InterviewRow
import ph.tala.registry.data.local.MemberRow
import ph.tala.registry.data.local.StoredRecord
import java.io.ByteArrayOutputStream
import java.io.OutputStream

/**
 * Export behaviour on the JVM: selection containment, ordering, size limits,
 * skip-and-count honesty and explicit write-failure reporting.
 */
class BackupExporterTest {

    private class FakeAttachments(var files: Map<String, ByteArray> = emptyMap()) : AttachmentSource {
        override suspend fun read(relativePath: String): ByteArray? = files[relativePath]
    }

    private fun record(id: String, displayCode: String, vararg attachments: AttachmentRow) = StoredRecord(
        household = HouseholdRow(id, displayCode, "Maria Santos", "24 Mabini Street", "InProgress"),
        members = listOf(MemberRow("$id-m1", id, "Juan Santos")),
        interview = InterviewRow("$id-iv", id),
        attachments = attachments.toList(),
    )

    private fun attachment(id: String, householdId: String, path: String, kind: String = "household-photo") =
        AttachmentRow(id, householdId, null, kind, path, "image/png")

    @Test
    fun `exports only the selected records in display-code order`() = runTest {
        val all = listOf(
            record("hh-c", "HH-00454"),
            record("hh-a", "HH-00452"),
            record("hh-b", "HH-00453"),
        )
        val captured = mutableListOf<ByteArrayOutputStream>()
        val result = BackupExporter(clock = { 1_700_000_000_000 }).export(
            selected = listOf(all[0], all[2]), // HH-00454 + HH-00453 only
            attachments = FakeAttachments(),
            openSink = { ByteArrayOutputStream().also(captured::add) },
        )
        assertTrue(result is BackupExporter.ExportResult.Saved)
        val file = kotlinx.serialization.json.Json.decodeFromString(
            BackupFile.serializer(),
            captured.single().toByteArray().decodeToString(),
        )
        assertEquals(listOf("HH-00453", "HH-00454"), file.records.map { it.displayCode })
        assertTrue(file.records.none { it.id == "hh-a" })
        assertEquals("2023-11-14T22:13:20Z", file.exportedAt)
        assertEquals(1, file.formatVersion)
        assertEquals("tala-backup", file.format)
        assertEquals(2, (result as BackupExporter.ExportResult.Saved).recordCount)
    }

    @Test
    fun `empty selection fails without touching the sink`() = runTest {
        val captured = mutableListOf<ByteArrayOutputStream>()
        val result = BackupExporter().export(emptyList(), FakeAttachments(), openSink = { ByteArrayOutputStream().also(captured::add) })
        assertTrue(result is BackupExporter.ExportResult.Failed)
        assertEquals(BackupExporter.EMPTY_SELECTION_MESSAGE, (result as BackupExporter.ExportResult.Failed).message)
        assertTrue(captured.isEmpty())
    }

    @Test
    fun `attachment over the size limit is skipped and counted, export still saves`() = runTest {
        val big = attachment("att-big", "hh-1", "attachments/big.png")
        val small = attachment("att-small", "hh-1", "attachments/small.png")
        val bytes = ByteArray(BackupExporter.MAX_ATTACHMENT_BYTES + 1)
        val captured = mutableListOf<ByteArrayOutputStream>()
        val result = BackupExporter().export(
            selected = listOf(record("hh-1", "HH-00452", big, small)),
            attachments = FakeAttachments(mapOf("attachments/big.png" to bytes, "attachments/small.png" to "ok".toByteArray())),
            openSink = { ByteArrayOutputStream().also(captured::add) },
        )
        assertTrue(result is BackupExporter.ExportResult.Saved)
        assertEquals(1, (result as BackupExporter.ExportResult.Saved).skippedCount)
        assertEquals(1, result.attachmentCount)
        val file = kotlinx.serialization.json.Json.decodeFromString(BackupFile.serializer(), captured.single().toByteArray().decodeToString())
        assertEquals(listOf("att-small"), file.records.single().attachments.map { it.id })
    }

    @Test
    fun `attachment exactly at the limit is included`() = runTest {
        val exact = attachment("att-exact", "hh-1", "attachments/exact.png")
        val captured = mutableListOf<ByteArrayOutputStream>()
        val result = BackupExporter().export(
            selected = listOf(record("hh-1", "HH-00452", exact)),
            attachments = FakeAttachments(mapOf("attachments/exact.png" to ByteArray(BackupExporter.MAX_ATTACHMENT_BYTES))),
            openSink = { ByteArrayOutputStream().also(captured::add) },
        )
        assertTrue(result is BackupExporter.ExportResult.Saved)
        assertEquals(0, (result as BackupExporter.ExportResult.Saved).skippedCount)
        val file = kotlinx.serialization.json.Json.decodeFromString(BackupFile.serializer(), captured.single().toByteArray().decodeToString())
        assertEquals(listOf("att-exact"), file.records.single().attachments.map { it.id })
    }

    @Test
    fun `missing and empty attachment files are skipped`() = runTest {
        val missing = attachment("att-missing", "hh-1", "attachments/gone.png")
        val empty = attachment("att-empty", "hh-1", "attachments/empty.png")
        val captured = mutableListOf<ByteArrayOutputStream>()
        val result = BackupExporter().export(
            selected = listOf(record("hh-1", "HH-00452", missing, empty)),
            attachments = FakeAttachments(mapOf("attachments/empty.png" to ByteArray(0))),
            openSink = { ByteArrayOutputStream().also(captured::add) },
        )
        assertTrue(result is BackupExporter.ExportResult.Saved)
        assertEquals(2, (result as BackupExporter.ExportResult.Saved).skippedCount)
        val file = kotlinx.serialization.json.Json.decodeFromString(BackupFile.serializer(), captured.single().toByteArray().decodeToString())
        assertTrue(file.records.single().attachments.isEmpty())
        assertNull(file.records.single().interview.signatureAttachmentId)
    }

    @Test
    fun `sink write failure reports Failed and never a fake save`() = runTest {
        val result = BackupExporter().export(
            selected = listOf(record("hh-1", "HH-00452")),
            attachments = FakeAttachments(),
            openSink = { object : OutputStream() { override fun write(value: Int) = error("disk full") } },
        )
        assertTrue(result is BackupExporter.ExportResult.Failed)
        assertEquals(BackupExporter.WRITE_FAILED_MESSAGE, (result as BackupExporter.ExportResult.Failed).message)
    }

    @Test
    fun `total size over the cap aborts before writing`() = runTest {
        // Eleven 5 MiB attachments (each within the per-attachment limit) sum past the
        // 50 MiB total cap: the export must abort rather than emit an oversized file.
        val rows = (1..11).map { AttachmentRow("att-$it", "hh-1", null, "household-photo", "attachments/full-$it.png", "image/png") }
        val files = rows.associate { it.relativePath to ByteArray(BackupExporter.MAX_ATTACHMENT_BYTES) }
        val captured = mutableListOf<ByteArrayOutputStream>()
        val result = BackupExporter().export(
            selected = listOf(record("hh-1", "HH-00452", *rows.toTypedArray())),
            attachments = FakeAttachments(files),
            openSink = { ByteArrayOutputStream().also(captured::add) },
        )
        assertTrue(result is BackupExporter.ExportResult.Failed)
        assertEquals(BackupExporter.TOTAL_SIZE_MESSAGE, (result as BackupExporter.ExportResult.Failed).message)
        assertTrue(captured.isEmpty())
    }
}
