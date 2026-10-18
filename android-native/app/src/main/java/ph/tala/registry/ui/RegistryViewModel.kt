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
    Archived("Archived"),
}

data class RegistryUiState(
    val loading: Boolean = true,
    /** Active households only, so home counts never include archived records. */
    val households: List<Household> = emptyList(),
    val visibleHouseholds: List<Household> = emptyList(),
    val archivedCount: Int = 0,
    /** Member id -> stored demographics sex answer; missing keys are unrecorded. */
    val memberSex: Map<String, String> = emptyMap(),
    val query: String = "",
    val filter: HouseholdFilter = HouseholdFilter.All,
) {
    val memberCount: Int get() = households.sumOf { it.members.size }
    val callbackCount: Int get() = households.count { it.status == HouseholdStatus.Callback }
    val maleCount: Int get() = households.sumOf { household -> household.members.count { memberSex[it.id.value] == "Male" } }
    val femaleCount: Int get() = households.sumOf { household -> household.members.count { memberSex[it.id.value] == "Female" } }
    val unrecordedSexCount: Int get() = memberCount - maleCount - femaleCount
}

/** Only small UI inputs go into saved state; records belong to the repository. */
class RegistryViewModel(
    repository: HouseholdRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    val uiState: StateFlow<RegistryUiState> = combine(
        repository.households,
        repository.memberSex,
        savedState.getStateFlow(QUERY, ""),
        savedState.getStateFlow(FILTER, HouseholdFilter.All.name),
    ) { records, sex, query, filterName ->
        val filter = HouseholdFilter.entries.find { it.name == filterName } ?: HouseholdFilter.All
        val needle = query.trim()
        val active = records.filterNot { it.archived }
        val visible = when (filter) {
            // The Archived filter is the recovery path: same identity and answers return.
            HouseholdFilter.Archived -> records.filter { it.archived }
            else -> active.filter {
                filter.status == null || it.status == filter.status
            }
        }.filter { record ->
            listOf(record.headName, record.displayCode, record.id.value, record.address)
                .any { text -> text.contains(needle, ignoreCase = true) }
        }
        RegistryUiState(
            loading = false,
            households = active,
            visibleHouseholds = visible,
            archivedCount = records.size - active.size,
            memberSex = sex,
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
