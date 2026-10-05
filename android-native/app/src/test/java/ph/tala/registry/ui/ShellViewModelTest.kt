package ph.tala.registry.ui

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import ph.tala.registry.data.HouseholdRepository
import ph.tala.registry.domain.model.Household
import ph.tala.registry.domain.model.HouseholdId
import ph.tala.registry.domain.model.HouseholdStatus

@OptIn(ExperimentalCoroutinesApi::class)
class ShellViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private class Repository : HouseholdRepository {
        override val households = MutableStateFlow(listOf(
            Household(HouseholdId("A"), "Maria Santos", "Purok 2", HouseholdStatus.InProgress),
            Household(HouseholdId("B"), "Ramon Reyes", "Purok 3", HouseholdStatus.Callback),
        ))
    }

    @Test fun searchAndStatusCombineWithoutChangingHomeTotals() = runTest(dispatcher) {
        val model = RegistryViewModel(Repository(), SavedStateHandle())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.uiState.collect() }
        model.setQuery("  REYES  ")
        model.setFilter(HouseholdFilter.Callback)
        advanceUntilIdle()
        assertEquals(listOf(HouseholdId("B")), model.uiState.value.visibleHouseholds.map { it.id })
        assertEquals(2, model.uiState.value.households.size)
        model.setFilter(HouseholdFilter.InProgress)
        advanceUntilIdle()
        assertTrue(model.uiState.value.visibleHouseholds.isEmpty())
        model.clearSearch()
        advanceUntilIdle()
        assertEquals(2, model.uiState.value.visibleHouseholds.size)
    }

    @Test fun savedInputsRestoreAndNewRepositoryEmissionsRefreshResults() = runTest(dispatcher) {
        val repository = Repository()
        val saved = SavedStateHandle(mapOf(RegistryViewModel.QUERY to "Purok 3", RegistryViewModel.FILTER to "Callback"))
        val model = RegistryViewModel(repository, saved)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.uiState.collect() }
        advanceUntilIdle()
        assertEquals("Purok 3", model.uiState.value.query)
        assertEquals(HouseholdFilter.Callback, model.uiState.value.filter)
        assertEquals("Ramon Reyes", model.uiState.value.visibleHouseholds.single().headName)
        repository.households.value = emptyList()
        advanceUntilIdle()
        assertTrue(model.uiState.value.visibleHouseholds.isEmpty())
        assertFalse(model.uiState.value.loading)
    }

    @Test fun interviewTracksIdentityAcrossReorderingAndRemoval() = runTest(dispatcher) {
        val repository = Repository()
        val model = InterviewViewModel(HouseholdId("B"), repository, SavedStateHandle(mapOf(InterviewViewModel.TAB to "Members")))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.uiState.collect() }
        repository.households.value = repository.households.value.reversed()
        advanceUntilIdle()
        assertEquals("Ramon Reyes", model.uiState.value.household?.headName)
        assertEquals(InterviewTab.Members, model.uiState.value.tab)
        repository.households.value = repository.households.value.filterNot { it.id == HouseholdId("B") }
        advanceUntilIdle()
        assertNull(model.uiState.value.household)
        assertFalse(model.uiState.value.loading)
    }

    @Test(expected = IllegalArgumentException::class)
    fun blankIdentityIsRejected() { HouseholdId(" ") }
}
