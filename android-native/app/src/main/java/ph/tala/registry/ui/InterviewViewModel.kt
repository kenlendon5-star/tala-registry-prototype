package ph.tala.registry.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ph.tala.registry.data.HouseholdRepository
import ph.tala.registry.domain.form.InterviewRules
import ph.tala.registry.domain.form.QuestionnaireSections
import ph.tala.registry.domain.model.Household
import ph.tala.registry.domain.model.HouseholdId
import ph.tala.registry.domain.model.scalar

enum class InterviewTab(val label: String) { Overview("Overview"), Members("Members"), Sections("Sections") }

data class InterviewUiState(
    val loading: Boolean = true,
    val household: Household? = null,
    val tab: InterviewTab = InterviewTab.Overview,
    /** The enumerator name this record's saved setup section was stamped with; null until read or unanswered. */
    val enumeratorName: String? = null,
)

class InterviewViewModel(
    private val id: HouseholdId,
    private val repository: HouseholdRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    /** Record-level attribution: read from the stored setup answers, not the live profile. */
    private val enumerator = MutableStateFlow<String?>(null)

    init { refreshEnumerator() }

    val uiState: StateFlow<InterviewUiState> = combine(
        repository.observeHousehold(id),
        savedState.getStateFlow(TAB, InterviewTab.Overview.name),
        enumerator,
    ) { household, tab, who ->
        InterviewUiState(false, household, InterviewTab.entries.find { it.name == tab } ?: InterviewTab.Overview, who)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InterviewUiState())

    fun selectTab(tab: InterviewTab) {
        savedState[TAB] = tab.name
        if (tab == InterviewTab.Overview) refreshEnumerator()
    }

    private fun refreshEnumerator() {
        viewModelScope.launch {
            enumerator.value = repository.interviewRecord(id)
                ?.let { record -> InterviewRules.answers(record, QuestionnaireSections.setup).scalar("enumerator").ifBlank { null } }
        }
    }

    companion object { const val TAB = "interviewTab" }
}
