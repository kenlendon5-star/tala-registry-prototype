package ph.tala.registry.data.backup

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.encodeToStream
import ph.tala.registry.data.local.StoredRecord
import java.io.File
import java.io.FilterOutputStream
import java.io.IOException
import java.io.OutputStream

/**
 * Builds the versioned backup file for exactly the selected records. Failure modes are
 * explicit: unreadable/oversized attachment files are skipped and counted, a sink or
 * size failure reports [ExportResult.Failed] with safe wording, and a canceled export
 * propagates cancellation instead of a fake success.
 */
class BackupExporter(
    private val clock: () -> Long = System::currentTimeMillis,
    private val stagingDirectory: File? = null,
) {
    sealed interface ExportResult {
        data class Saved(
            val recordCount: Int,
            val attachmentCount: Int,
            val skippedCount: Int,
        ) : ExportResult

        data class Failed(val message: String) : ExportResult
    }

    /**
     * @param selected the records to export; every other stored record stays out of the file.
     * @param openSink opens the final destination only after a complete bounded JSON
     * staging file has been written successfully.
     */
    @OptIn(ExperimentalSerializationApi::class)
    suspend fun export(
        selected: List<StoredRecord>,
        attachments: AttachmentSource,
        openSink: () -> OutputStream,
    ): ExportResult {
        if (selected.isEmpty()) return ExportResult.Failed(EMPTY_SELECTION_MESSAGE)
        val ordered = selected.sortedBy { it.household.displayCode }
        val included = mutableMapOf<String, BackupMapper.IncludedAttachment>()
        var skipped = 0
        var projectedAttachmentJsonBytes = 0L
        for (record in ordered) {
            for (row in record.attachments) {
                val bytes = attachments.read(row.relativePath)
                when {
                    bytes == null || bytes.isEmpty() -> skipped += 1
                    bytes.size > MAX_ATTACHMENT_BYTES -> skipped += 1
                    else -> {
                        projectedAttachmentJsonBytes += encodedBase64Size(bytes.size) + ATTACHMENT_JSON_OVERHEAD_BYTES
                        if (projectedAttachmentJsonBytes > MAX_TOTAL_BYTES) return ExportResult.Failed(TOTAL_SIZE_MESSAGE)
                        included[row.id] = BackupMapper.IncludedAttachment(bytes, row)
                    }
                }
            }
        }
        val file = BackupFile(exportedAt = BackupMapper.isoTimestamp(clock()), records = ordered.map { record ->
            BackupMapper.mapRecord(record, included.filterKeys { key -> record.attachments.any { it.id == key } })
        })
        val attachmentCount = included.size
        included.clear()
        val staging = try {
            File.createTempFile("tala-export-", ".json", stagingDirectory)
        } catch (_: Exception) {
            return ExportResult.Failed(WRITE_FAILED_MESSAGE)
        }
        return try {
            staging.outputStream().buffered().use { raw ->
                BackupJson.encodeToStream(BackupFile.serializer(), file, SizeLimitedOutputStream(raw, MAX_TOTAL_BYTES.toLong()))
            }
            openSink().use { destination -> staging.inputStream().buffered().use { it.copyTo(destination) } }
            ExportResult.Saved(
                recordCount = ordered.size,
                attachmentCount = attachmentCount,
                skippedCount = skipped,
            )
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: SizeLimitExceeded) {
            ExportResult.Failed(TOTAL_SIZE_MESSAGE)
        } catch (_: Exception) {
            ExportResult.Failed(WRITE_FAILED_MESSAGE)
        } finally {
            staging.delete()
        }
    }

    private fun encodedBase64Size(byteCount: Int): Long = 4L * ((byteCount.toLong() + 2L) / 3L)

    private class SizeLimitExceeded : IOException()

    private class SizeLimitedOutputStream(output: OutputStream, private val limit: Long) : FilterOutputStream(output) {
        private var written = 0L

        override fun write(value: Int) {
            ensureCapacity(1)
            out.write(value)
            written += 1
        }

        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            ensureCapacity(length)
            out.write(bytes, offset, length)
            written += length
        }

        private fun ensureCapacity(next: Int) {
            if (written + next > limit) throw SizeLimitExceeded()
        }
    }

    companion object {
        /** Contract maximum for one embedded attachment (backup-v1 byteSize maximum). */
        const val MAX_ATTACHMENT_BYTES = 5 * 1024 * 1024

        /** Export aborts above this so the file always fits the D18 restore-side 50 MiB cap. */
        const val MAX_TOTAL_BYTES = 50 * 1024 * 1024
        private const val ATTACHMENT_JSON_OVERHEAD_BYTES = 512L

        const val EMPTY_SELECTION_MESSAGE = "Select at least one household before exporting."
        const val TOTAL_SIZE_MESSAGE = "The export is too large for one file. Select fewer households and try again."
        const val WRITE_FAILED_MESSAGE = "Could not write the export to the chosen destination. The records are unchanged. Retry after checking available storage."
    }
}
