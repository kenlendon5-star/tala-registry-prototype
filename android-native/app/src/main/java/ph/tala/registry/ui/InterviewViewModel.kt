package ph.tala.registry.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import ph.tala.registry.data.HouseholdRepository
import ph.tala.registry.domain.model.Household
import ph.tala.registry.domain.model.HouseholdId

enum class InterviewTab(val label: String) { Overview("Overview"), Members("Members"), Sections("Sections") }

data class InterviewUiState(
    val loading: Boolean = true,
    val household: Household? = null,
    val tab: InterviewTab = InterviewTab.Overview,
)

class InterviewViewModel(
    id: HouseholdId,
    repository: HouseholdRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    val uiState = combine(repository.observeHousehold(id), savedState.getStateFlow(TAB, InterviewTab.Overview.name)) { household, tab ->
        InterviewUiState(false, household, InterviewTab.entries.find { it.name == tab } ?: InterviewTab.Overview)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InterviewUiState())

    fun selectTab(tab: InterviewTab) { savedState[TAB] = tab.name }

    companion object { const val TAB = "interviewTab" }
}
