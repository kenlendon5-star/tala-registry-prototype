package ph.tala.registry.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import ph.tala.registry.data.HouseholdRepository
import ph.tala.registry.domain.model.Household
import ph.tala.registry.domain.model.HouseholdStatus

enum class HouseholdFilter(val label: String, val status: HouseholdStatus? = null) {
    All("All"), InProgress("In progress", HouseholdStatus.InProgress),
    Callback("Callback", HouseholdStatus.Callback), Reviewed("Reviewed", HouseholdStatus.Reviewed),
}

data class RegistryUiState(
    val loading: Boolean = true,
    val households: List<Household> = emptyList(),
    val visibleHouseholds: List<Household> = emptyList(),
    val query: String = "",
    val filter: HouseholdFilter = HouseholdFilter.All,
) {
    val memberCount: Int get() = households.sumOf { it.members.size }
    val callbackCount: Int get() = households.count { it.status == HouseholdStatus.Callback }
}

/** Only small UI inputs go into saved state; records belong to the repository. */
class RegistryViewModel(
    repository: HouseholdRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    val uiState: StateFlow<RegistryUiState> = combine(
        repository.households,
        savedState.getStateFlow(QUERY, ""),
        savedState.getStateFlow(FILTER, HouseholdFilter.All.name),
    ) { records, query, filterName ->
        val filter = HouseholdFilter.entries.find { it.name == filterName } ?: HouseholdFilter.All
        val needle = query.trim()
        RegistryUiState(
            loading = false,
            households = records,
            visibleHouseholds = records.filter {
                (filter.status == null || it.status == filter.status) &&
                    listOf(it.headName, it.id.value, it.address).any { text -> text.contains(needle, ignoreCase = true) }
            },
            query = query,
            filter = filter,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RegistryUiState())

    fun setQuery(value: String) { savedState[QUERY] = value }
    fun setFilter(value: HouseholdFilter) { savedState[FILTER] = value.name }
    fun clearSearch() { setQuery(""); setFilter(HouseholdFilter.All) }

    companion object {
        const val QUERY = "householdQuery"
        const val FILTER = "householdFilter"
    }
}
