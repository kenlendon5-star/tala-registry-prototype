package ph.tala.registry.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import ph.tala.registry.domain.model.Household
import ph.tala.registry.domain.model.HouseholdId
import ph.tala.registry.domain.model.HouseholdStatus
import ph.tala.registry.domain.model.Member
import ph.tala.registry.domain.model.MemberId

/** Read contract for the Day 3 shell. Room and editing arrive in Days 5–6. */
interface HouseholdRepository {
    val households: Flow<List<Household>>
    fun observeHousehold(id: HouseholdId): Flow<Household?> =
        households.map { records -> records.find { it.id == id } }
}

/** Explicit fictional fixtures; this repository does not claim to save edits. */
class DemoHouseholdRepository : HouseholdRepository {
    override val households = MutableStateFlow(demoHouseholds()).asStateFlow()
}

private fun demoHouseholds(): List<Household> {
    val santos = HouseholdId("HH-00452")
    return listOf(
        Household(
            santos, "Maria Santos", "24 Mabini Street · Purok 2", HouseholdStatus.InProgress,
            listOf("Maria Santos", "Juan Santos", "Ana Santos", "Pedro Santos").mapIndexed { index, name ->
                Member(MemberId("DEMO-SANTOS-${index + 1}"), santos, name)
            },
        ),
        Household(HouseholdId("HH-00453"), "Ramon Reyes", "8 Rizal Street · Purok 2", HouseholdStatus.Callback),
        Household(HouseholdId("HH-00454"), "Elena Cruz", "16 Bonifacio Street · Purok 3", HouseholdStatus.NotStarted),
    )
}
