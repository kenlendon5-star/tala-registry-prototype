package ph.tala.registry.ui.interview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ph.tala.registry.data.HouseholdRepository
import ph.tala.registry.data.local.WriteResult
import ph.tala.registry.domain.form.FormRules
import ph.tala.registry.domain.form.RegistrySections
import ph.tala.registry.domain.model.AnswerState
import ph.tala.registry.domain.model.AnswerValue
import ph.tala.registry.domain.model.HouseholdId
import ph.tala.registry.domain.model.SectionSpec
import ph.tala.registry.domain.model.isAnswered
import ph.tala.registry.ui.form.SectionFormState
import ph.tala.registry.ui.households.SavePhase
import ph.tala.registry.ui.households.SaveStatus
import java.time.LocalDate

data class InterviewSectionsUiState(
    val loading: Boolean = true,
    val sections: Map<String, SectionFormState> = emptyMap(),
    val statuses: Map<String, SaveStatus> = emptyMap(),
)

/**
 * Durable interview answers for the registration sections (plan card D08). Drafts
 * start from the saved record, edits stay in memory, and an explicit save replaces
 * exactly one section's stored answers through the repository. A Saved label is
 * dropped as soon as the draft changes again, so the screen never confirms work
 * that is not on the device.
 */
class InterviewSectionsViewModel(
    private val householdId: HouseholdId,
    private val repository: HouseholdRepository,
    private val today: () -> LocalDate = LocalDate::now,
) : ViewModel() {
    private val loaded = MutableStateFlow(false)
    private val drafts = MutableStateFlow<Map<String, AnswerState>>(emptyMap())
    private val attempts = MutableStateFlow<Map<String, Int>>(emptyMap())
    private val statuses = MutableStateFlow<Map<String, SaveStatus>>(emptyMap())

    init {
        viewModelScope.launch {
            val saved = repository.householdSectionAnswers(householdId)
            drafts.value = RegistrySections.registration.associate { section ->
                section.route to section.fields.mapNotNull { field ->
                    saved[field.key]?.takeIf { saved.isAnswered(field.key) }?.let { field.key to it }
                }.toMap()
            }
            loaded.value = true
        }
    }

    val uiState: StateFlow<InterviewSectionsUiState> = combine(loaded, drafts, attempts, statuses) { ready, draftMap, attemptMap, statusMap ->
        InterviewSectionsUiState(
            loading = !ready,
            sections = RegistrySections.registration.associate { section ->
                val answers = draftMap[section.route].orEmpty()
                val attempt = attemptMap[section.route] ?: 0
                section.route to SectionFormState(
                    answers,
                    if (attempt > 0) FormRules.validate(section, answers, today()) else emptyMap(),
                    attempt,
                )
            },
            statuses = statusMap,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InterviewSectionsUiState())

    fun answer(section: SectionSpec, key: String, value: AnswerValue) {
        if (statuses.value[section.route]?.phase == SavePhase.Saving) return
        drafts.update { current ->
            current + (section.route to FormRules.update(section, current[section.route].orEmpty(), key, value))
        }
        statuses.update { current ->
            if (current[section.route]?.phase == SavePhase.Saved) current + (section.route to SaveStatus()) else current
        }
    }

    fun check(section: SectionSpec) {
        attempts.update { current -> current + (section.route to ((current[section.route] ?: 0) + 1)) }
    }

    fun save(section: SectionSpec) {
        if (statuses.value[section.route]?.phase == SavePhase.Saving) return
        viewModelScope.launch {
            val answers = drafts.value[section.route].orEmpty()
            // Draft durability is independent of completion validation.
            statuses.update { it + (section.route to SaveStatus(SavePhase.Saving)) }
            val result = repository.saveSectionAnswers(householdId, section, drafts.value[section.route].orEmpty())
            statuses.update {
                it + (section.route to when (result) {
                    WriteResult.Saved -> SaveStatus(SavePhase.Saved, "Answers saved on this device.")
                    is WriteResult.Failed -> SaveStatus(SavePhase.Failed, result.message, attempted = true)
                })
            }
        }
    }
}
