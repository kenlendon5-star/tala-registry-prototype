package ph.tala.registry.ui.households

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ph.tala.registry.data.HouseholdRepository
import ph.tala.registry.data.local.WriteResult
import ph.tala.registry.domain.edit.EditRules
import ph.tala.registry.domain.edit.HouseholdDraft
import ph.tala.registry.domain.model.HouseholdId
import ph.tala.registry.domain.model.HouseholdStatus
import java.util.UUID

/** Progress of the one write the screen is waiting on. [SavePhase.Saved] appears only after a saved write. */
enum class SavePhase { Idle, Saving, Saved, Failed }

data class SaveStatus(
    val phase: SavePhase = SavePhase.Idle,
    val message: String? = null,
    val attempted: Boolean = false,
)

data class HouseholdEditUiState(
    val loading: Boolean = true,
    val creating: Boolean = false,
    val missing: Boolean = false,
    val displayCode: String? = null,
    val headName: String = "",
    val address: String = "",
    val status: HouseholdStatus = HouseholdStatus.InProgress,
    val headNameError: String? = null,
    val addressError: String? = null,
    val phase: SavePhase = SavePhase.Idle,
    val message: String? = null,
    val archived: Boolean = false,
) {
    val saving: Boolean get() = phase == SavePhase.Saving
}

/**
 * Add/edit household screen model. Unsaved edits live in [SavedStateHandle] so process
 * death resumes exactly what was typed, while the saved record is always re-read from
 * the repository. The record identity is generated once and restored with the screen,
 * so retrying a failed create updates the same household instead of making a second one.
 */
class HouseholdEditViewModel(
    householdId: HouseholdId?,
    private val repository: HouseholdRepository,
    private val savedState: SavedStateHandle,
    private val newId: () -> HouseholdId = { HouseholdId(UUID.randomUUID().toString()) },
) : ViewModel() {
    val id: HouseholdId = HouseholdId(
        (savedState[ID] as String?) ?: (householdId?.value ?: newId().value).also { savedState[ID] = it },
    )

    private val head = MutableStateFlow(savedState[HEAD] as String?)
    private val address = MutableStateFlow(savedState[ADDRESS] as String?)
    private val status = MutableStateFlow(savedState[STATUS] as String?)
    private val saveStatus = MutableStateFlow(SaveStatus())

    val uiState: StateFlow<HouseholdEditUiState> = combine(
        repository.observeHousehold(id),
        head,
        address,
        status,
        saveStatus,
    ) { record, headDraft, addressDraft, statusDraft, saving ->
        val headValue = headDraft ?: record?.headName.orEmpty()
        val addressValue = addressDraft ?: record?.address.orEmpty()
        val statusValue = statusDraft?.let { saved -> HouseholdStatus.entries.find { it.name == saved } }
            ?: record?.status ?: HouseholdStatus.InProgress
        val attempted = saving.attempted || saving.phase == SavePhase.Failed
        HouseholdEditUiState(
            loading = false,
            creating = record == null,
            missing = householdId != null && record == null,
            displayCode = record?.displayCode,
            headName = headValue,
            address = addressValue,
            status = statusValue,
            headNameError = EditRules.headName(headValue).takeIf { attempted },
            addressError = EditRules.address(addressValue).takeIf { attempted },
            phase = saving.phase,
            message = saving.message,
            archived = record?.archived == true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HouseholdEditUiState(creating = householdId == null))

    /** Editing after a save drops the confirmation, so the label never claims unsaved work. */
    private fun dropSavedAck() { if (saveStatus.value.phase == SavePhase.Saved) saveStatus.value = SaveStatus() }

    fun setHeadName(value: String) { if (saveStatus.value.phase == SavePhase.Saving) return; dropSavedAck(); savedState[HEAD] = value; head.value = value }
    fun setAddress(value: String) { if (saveStatus.value.phase == SavePhase.Saving) return; dropSavedAck(); savedState[ADDRESS] = value; address.value = value }
    fun setStatus(value: HouseholdStatus) { if (saveStatus.value.phase == SavePhase.Saving) return; dropSavedAck(); savedState[STATUS] = value.name; status.value = value.name }

    fun save() {
        if (saveStatus.value.phase == SavePhase.Saving) return
        viewModelScope.launch {
            val record = repository.observeHousehold(id).first()
            val draft = HouseholdDraft(
                headName = head.value ?: record?.headName.orEmpty(),
                address = address.value ?: record?.address.orEmpty(),
                status = status.value?.let { saved -> HouseholdStatus.entries.find { it.name == saved } }
                    ?: record?.status ?: HouseholdStatus.InProgress,
            )
            val problem = EditRules.householdProblem(draft)
            if (problem != null) {
                saveStatus.value = SaveStatus(SavePhase.Failed, problem, attempted = true)
                return@launch
            }
            saveStatus.value = SaveStatus(SavePhase.Saving)
            val result = if (record == null) repository.createHousehold(id, draft) else repository.updateHousehold(id, draft)
            saveStatus.value = result.toStatus()
        }
    }

    /** Sets or clears the archive marker; the record and its members are kept either way. */
    fun setArchived(archived: Boolean) {
        if (saveStatus.value.phase == SavePhase.Saving) return
        viewModelScope.launch {
            saveStatus.value = SaveStatus(SavePhase.Saving)
            val result = repository.setArchived(id, archived)
            saveStatus.value = result.toStatus(
                savedMessage = if (archived) "Archived. The record stays recoverable under Archived."
                else "Restored. The record is active again.",
            )
        }
    }

    private fun WriteResult.toStatus(savedMessage: String? = null) = when (this) {
        WriteResult.Saved -> SaveStatus(SavePhase.Saved, savedMessage ?: "Saved on this device.")
        is WriteResult.Failed -> SaveStatus(SavePhase.Failed, message, attempted = true)
    }

    companion object {
        const val ID = "householdEditId"
        const val HEAD = "householdEditHead"
        const val ADDRESS = "householdEditAddress"
        const val STATUS = "householdEditStatus"
    }
}
