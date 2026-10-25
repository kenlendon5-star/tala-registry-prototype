package ph.tala.registry.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ph.tala.registry.data.HouseholdRepository
import ph.tala.registry.data.backup.AttachmentSource
import ph.tala.registry.data.backup.BackupExporter
import ph.tala.registry.data.backup.BackupImporter
import ph.tala.registry.data.cloud.HouseholdCloudSyncRepository
import ph.tala.registry.data.cloud.HouseholdSyncResult
import ph.tala.registry.domain.model.HouseholdId
import ph.tala.registry.ui.reports.ReportsUiState.Phase

/** Which households are checked for export. Selection survives navigation; payload = selection only. */
data class ReportsUiState(
    /** Household rows in display-code order: label, head name and status for the checklist. */
    val households: List<HouseholdRow> = emptyList(),
    val selectedCodes: Set<String> = emptySet(),
    val phase: Phase = Phase.Idle,
    val message: String = "",
    val importPreview: BackupImporter.ImportPreview? = null,
) {
    data class HouseholdRow(val id: String, val displayCode: String, val headName: String, val status: String)

    enum class Phase { Idle, Exporting, ReadingBackup, Restoring, Syncing, Saved, Restored, Synced, Conflict, Failed, Canceled }

    val canExport: Boolean get() = selectedCodes.isNotEmpty() && phase != Phase.Exporting && phase != Phase.Syncing
    val canSync: Boolean get() = canExport
}

/**
 * Owns record selection and the export run. The SAF dialog stays in the UI layer; the
 * ViewModel receives the chosen destination only as a write sink, so a canceled dialog
 * never touches data. Export runs off the main thread with an explicit outcome phase —
 * a failed write is never reported as saved.
 */
class ReportsViewModel(
    private val repository: HouseholdRepository,
    private val exporter: BackupExporter,
    private val attachments: AttachmentSource,
    private val cloudSync: HouseholdCloudSyncRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val importer: BackupImporter? = null,
) : ViewModel() {

    private val state = MutableStateFlow(ReportsUiState())

    fun state(): StateFlow<ReportsUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.households.collect { rows ->
                val uiRows = rows
                    .sortedBy { it.displayCode }
                    .map { ReportsUiState.HouseholdRow(it.id.value, it.displayCode, it.headName, if (it.archived) "Archived" else it.status.label) }
                state.update { current ->
                    val codes = uiRows.map { it.displayCode }.toSet()
                    current.copy(households = uiRows, selectedCodes = current.selectedCodes intersect codes)
                }
            }
        }
    }

    fun toggle(displayCode: String) {
        state.update { current ->
            val next = if (displayCode in current.selectedCodes) current.selectedCodes - displayCode else current.selectedCodes + displayCode
            current.copy(selectedCodes = next, phase = Phase.Idle, message = "")
        }
    }

    fun selectAll() {
        state.update { current -> current.copy(selectedCodes = current.households.map { it.displayCode }.toSet(), phase = Phase.Idle, message = "") }
    }

    fun clearSelection() {
        state.update { current -> current.copy(selectedCodes = emptySet(), phase = Phase.Idle, message = "") }
    }

    /**
     * Runs the export for the currently selected households into a stream [openStream]
     * provides (the SAF destination; may throw when the location is unusable). The
     * ViewModel owns open/close so the stream lives exactly as long as the write —
     * a canceled dialog never reaches here, and the outcome lands in an explicit phase:
     * a failed write is never reported as saved.
     */
    fun exportTo(openStream: () -> java.io.OutputStream) {
        val selected = state.value.selectedCodes.toList()
        if (selected.isEmpty()) return
        state.update { it.copy(phase = Phase.Exporting, message = "") }
        viewModelScope.launch {
            try {
                val records = withContext(ioDispatcher) {
                    val byCode = state.value.households.associate { it.displayCode to it.id }
                    repository.interviewRecords(selected.mapNotNull { byCode[it] }.map(::HouseholdId))
                }
                val result = withContext(ioDispatcher) { exporter.export(records, attachments, openStream) }
                state.update {
                    when (result) {
                        is BackupExporter.ExportResult.Saved -> it.copy(
                            phase = Phase.Saved,
                            message = "Exported ${result.recordCount} ${if (result.recordCount == 1) "household" else "households"}" +
                                " with ${result.attachmentCount} ${if (result.attachmentCount == 1) "attachment" else "attachments"}" +
                                (if (result.skippedCount > 0) "; ${result.skippedCount} skipped." else "."),
                        )
                        is BackupExporter.ExportResult.Failed -> it.copy(phase = Phase.Failed, message = result.message)
                    }
                }
            } catch (cancelled: CancellationException) {
                state.update { it.copy(phase = Phase.Canceled, message = CANCELED_MESSAGE) }
                throw cancelled
            } catch (_: Exception) {
                state.update { it.copy(phase = Phase.Failed, message = WRITE_FAILED_MESSAGE) }
            }
        }
    }

    fun syncSelected() {
        syncSelected(replaceCloud = false)
    }

    fun replaceCloudWithSelected() {
        syncSelected(replaceCloud = true)
    }

    private fun syncSelected(replaceCloud: Boolean) {
        val selectedIds = state.value.households
            .filter { it.displayCode in state.value.selectedCodes }
            .map { HouseholdId(it.id) }
        if (selectedIds.isEmpty()) return
        state.update { it.copy(phase = Phase.Syncing, message = "") }
        viewModelScope.launch {
            val result = try {
                val records = withContext(ioDispatcher) {
                    repository.interviewRecords(selectedIds)
                }
                withContext(ioDispatcher) { if (replaceCloud) cloudSync.replaceCloud(records) else cloudSync.upload(records) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                HouseholdSyncResult.Failed("Could not sync to Supabase. Local household data is unchanged; retry when online.")
            }
            state.update {
                when (result) {
                    is HouseholdSyncResult.Synced -> it.copy(
                        phase = Phase.Synced,
                        message = "Synced ${result.count} ${if (result.count == 1) "household" else "households"} and ${result.attachmentCount} ${if (result.attachmentCount == 1) "attachment" else "attachments"} to Supabase.",
                    )
                    is HouseholdSyncResult.Conflicted -> it.copy(
                        phase = Phase.Conflict,
                        message = "${result.count} ${if (result.count == 1) "household has" else "households have"} a newer cloud snapshot. Local data was not overwritten; conflict review is required.",
                    )
                    is HouseholdSyncResult.Failed -> it.copy(phase = Phase.Failed, message = result.message)
                }
            }
        }
    }

    fun previewImport(openStream: () -> java.io.InputStream) {
        val available = importer ?: return
        state.update { it.copy(phase = Phase.ReadingBackup, message = "", importPreview = null) }
        viewModelScope.launch {
            val result = withContext(ioDispatcher) { available.preview(openStream) }
            state.update { current ->
                when (result) {
                    is BackupImporter.PreviewResult.Ready -> current.copy(phase = Phase.Idle, importPreview = result.preview)
                    is BackupImporter.PreviewResult.Failed -> current.copy(phase = Phase.Failed, message = result.message)
                }
            }
        }
    }

    fun confirmImport() {
        val available = importer ?: return
        val preview = state.value.importPreview ?: return
        state.update { it.copy(phase = Phase.Restoring, message = "", importPreview = null) }
        viewModelScope.launch {
            val result = withContext(ioDispatcher) { available.restore(preview) }
            state.update { current ->
                when (result) {
                    is BackupImporter.RestoreResult.Restored -> current.copy(
                        phase = Phase.Restored,
                        message = "Restored ${result.recordCount} ${if (result.recordCount == 1) "household" else "households"} and ${result.attachmentCount} ${if (result.attachmentCount == 1) "attachment" else "attachments"}" +
                            (if (result.replacedCount > 0) "; replaced ${result.replacedCount} existing ${if (result.replacedCount == 1) "record" else "records"}." else "."),
                    )
                    is BackupImporter.RestoreResult.Failed -> current.copy(phase = Phase.Failed, message = result.message)
                }
            }
        }
    }

    fun cancelImport() {
        val preview = state.value.importPreview ?: return
        importer?.discard(preview)
        state.update { it.copy(importPreview = null, phase = Phase.Canceled, message = "Restore canceled. Existing records are unchanged.") }
    }

    fun reportCanceled() {
        state.update { it.copy(phase = Phase.Canceled, message = CANCELED_MESSAGE) }
    }

    private companion object {
        const val CANCELED_MESSAGE = "Export canceled. No file was written."
        const val WRITE_FAILED_MESSAGE = "Could not write the backup file. Check available storage and retry."
    }
}
