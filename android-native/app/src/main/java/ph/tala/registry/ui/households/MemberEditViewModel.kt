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
import ph.tala.registry.domain.edit.MemberDraft
import ph.tala.registry.domain.model.HouseholdId
import ph.tala.registry.domain.model.MemberId
import java.util.UUID

data class MemberEditUiState(
    val loading: Boolean = true,
    val creating: Boolean = false,
    val missing: Boolean = false,
    val householdLabel: String = "",
    val displayName: String = "",
    val displayNameError: String? = null,
    val phase: SavePhase = SavePhase.Idle,
    val message: String? = null,
    val removed: Boolean = false,
) {
    val saving: Boolean get() = phase == SavePhase.Saving
}

/**
 * Add/edit member screen model. The member identity is generated once and restored
 * after process death, so a retried save updates the same member. Removal is confirmed
 * in the UI and then deletes the member together with its answers in one write.
 */
class MemberEditViewModel(
    private val householdId: HouseholdId,
    existingMemberId: MemberId?,
    private val repository: HouseholdRepository,
    private val savedState: SavedStateHandle,
    private val newId: () -> MemberId = { MemberId(UUID.randomUUID().toString()) },
) : ViewModel() {
    val id: MemberId = MemberId(
        (savedState[ID] as String?) ?: (existingMemberId?.value ?: newId().value).also { savedState[ID] = it },
    )

    /** Add screens show the form before the member row exists; only edits can go missing. */
    private val editing = existingMemberId != null

    private val name = MutableStateFlow(savedState[NAME] as String?)
    private val saveStatus = MutableStateFlow(SaveStatus())
    private val removedFlag = MutableStateFlow(false)

    val uiState: StateFlow<MemberEditUiState> = combine(
        repository.observeHousehold(householdId),
        name,
        saveStatus,
        removedFlag,
    ) { household, nameDraft, saving, removed ->
        val member = household?.members?.find { it.id == id }
        val nameValue = nameDraft ?: member?.displayName.orEmpty()
        val attempted = saving.attempted || saving.phase == SavePhase.Failed
        MemberEditUiState(
            loading = false,
            creating = member == null && !removed,
            missing = household == null || (editing && member == null && !removed),
            householdLabel = household?.let { "${it.displayCode} · ${it.headName}" }.orEmpty(),
            displayName = nameValue,
            displayNameError = EditRules.displayName(nameValue).takeIf { attempted },
            phase = saving.phase,
            message = saving.message,
            removed = removed,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MemberEditUiState(creating = existingMemberId == null))

    fun setDisplayName(value: String) {
        if (saveStatus.value.phase == SavePhase.Saving) return
        if (saveStatus.value.phase == SavePhase.Saved) saveStatus.value = SaveStatus()
        savedState[NAME] = value
        name.value = value
    }

    fun save() {
        if (saveStatus.value.phase == SavePhase.Saving) return
        viewModelScope.launch {
            val household = repository.observeHousehold(householdId).first()
            val draft = MemberDraft(name.value ?: household?.members?.find { it.id == id }?.displayName.orEmpty())
            val problem = EditRules.memberProblem(draft)
            if (problem != null) {
                saveStatus.value = SaveStatus(SavePhase.Failed, problem, attempted = true)
                return@launch
            }
            saveStatus.value = SaveStatus(SavePhase.Saving)
            saveStatus.value = repository.saveMember(householdId, id, draft).toStatus()
        }
    }

    fun remove() {
        if (saveStatus.value.phase == SavePhase.Saving) return
        viewModelScope.launch {
            saveStatus.value = SaveStatus(SavePhase.Saving)
            val result = repository.removeMember(householdId, id)
            saveStatus.value = when (result) {
                WriteResult.Saved -> {
                    removedFlag.value = true
                    SaveStatus(SavePhase.Saved, "Member removed from this household.")
                }
                is WriteResult.Failed -> SaveStatus(SavePhase.Failed, result.message, attempted = true)
            }
        }
    }

    private fun WriteResult.toStatus() = when (this) {
        WriteResult.Saved -> SaveStatus(SavePhase.Saved, "Saved on this device.")
        is WriteResult.Failed -> SaveStatus(SavePhase.Failed, message, attempted = true)
    }

    companion object {
        const val ID = "memberEditId"
        const val NAME = "memberEditName"
    }
}
