package ph.tala.registry.ui

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ph.tala.registry.data.FakeRecordStore
import ph.tala.registry.data.RoomHouseholdRepository
import ph.tala.registry.domain.edit.HouseholdDraft
import ph.tala.registry.domain.model.HouseholdId
import ph.tala.registry.domain.model.HouseholdStatus
import ph.tala.registry.domain.model.MemberId
import ph.tala.registry.ui.households.HouseholdEditViewModel
import ph.tala.registry.ui.households.MemberEditViewModel
import ph.tala.registry.ui.households.SavePhase

/**
 * Day 6 edit-screen checks: drafts survive process death, record identity is stable and
 * a rejected write is never reported as saved.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EditScreensViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun aFailedWriteIsNeverReportedAsSaved() = runTest(dispatcher) {
        val store = FakeRecordStore()
        val failure = "Could not save on this device. The previous saved record is unchanged."
        store.failWith = failure
        val repository = RoomHouseholdRepository(store)
        val model = HouseholdEditViewModel(null, repository, SavedStateHandle())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.uiState.collect() }

        model.setHeadName("Luna Reyes")
        model.setAddress("5 Sampaguita Street")
        model.save()
        advanceUntilIdle()

        assertEquals(SavePhase.Failed, model.uiState.value.phase)
        assertEquals(failure, model.uiState.value.message)
        assertNull(model.uiState.value.displayCode)
        assertTrue(repository.households.first().isEmpty())

        // Once storage accepts writes again, the same screen and identity save successfully.
        store.failWith = null
        model.save()
        advanceUntilIdle()
        assertEquals(SavePhase.Saved, model.uiState.value.phase)
        assertEquals("HH-00001", model.uiState.value.displayCode)
        assertEquals(1, repository.households.first().size)
    }

    @Test
    fun unsavedDraftsAndIdentitySurviveProcessDeath() = runTest(dispatcher) {
        val store = FakeRecordStore()
        val repository = RoomHouseholdRepository(store)
        val handle = SavedStateHandle()
        var issued = 0
        val newId = { HouseholdId("f0000000-0000-4000-8000-00000000000${issued++}") }
        val model = HouseholdEditViewModel(null, repository, handle, newId)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.uiState.collect() }
        model.setHeadName("Luna Reyes")
        model.setAddress("5 Sampaguita Street")
        advanceUntilIdle()

        // The draft and the generated identity are small values handed to SavedStateHandle,
        // which is exactly what a fresh process restores.
        assertEquals("Luna Reyes", handle[HouseholdEditViewModel.HEAD])
        assertEquals(model.id.value, handle[HouseholdEditViewModel.ID])
        val resumed = HouseholdEditViewModel(
            null,
            repository,
            SavedStateHandle(
                mapOf(
                    HouseholdEditViewModel.ID to requireNotNull(handle[HouseholdEditViewModel.ID]),
                    HouseholdEditViewModel.HEAD to "Luna Reyes",
                    HouseholdEditViewModel.ADDRESS to "5 Sampaguita Street",
                ),
            ),
            newId,
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { resumed.uiState.collect() }
        advanceUntilIdle()

        assertEquals(model.id, resumed.id)
        assertEquals("Luna Reyes", resumed.uiState.value.headName)
        assertEquals("5 Sampaguita Street", resumed.uiState.value.address)
        resumed.save()
        advanceUntilIdle()
        assertEquals(SavePhase.Saved, resumed.uiState.value.phase)
        assertEquals(1, repository.households.first().size)
        assertEquals("Luna Reyes", repository.households.first().single().headName)
    }

    @Test
    fun blankDraftReportsFailureAndWritesNothing() = runTest(dispatcher) {
        val repository = RoomHouseholdRepository(FakeRecordStore())
        val model = HouseholdEditViewModel(null, repository, SavedStateHandle())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.uiState.collect() }

        model.save()
        advanceUntilIdle()

        assertEquals(SavePhase.Failed, model.uiState.value.phase)
        assertEquals("Enter the household head's name.", model.uiState.value.message)
        assertNotNull(model.uiState.value.headNameError)
        assertTrue(repository.households.first().isEmpty())
    }

    @Test
    fun memberIdentityIsStableAcrossSavesAndRemovalIsReported() = runTest(dispatcher) {
        val repository = RoomHouseholdRepository(FakeRecordStore())
        val householdId = HouseholdId("a0000000-0000-4000-8000-000000000001")
        repository.createHousehold(householdId, HouseholdDraft("Maria Santos", "24 Mabini Street", HouseholdStatus.InProgress))
        var issued = 0
        val model = MemberEditViewModel(
            householdId, null, repository, SavedStateHandle(),
            newId = { MemberId("b0000000-0000-4000-8000-00000000000${issued++}") },
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.uiState.collect() }

        model.setDisplayName("Juan Santos")
        model.save()
        advanceUntilIdle()
        assertEquals(SavePhase.Saved, model.uiState.value.phase)
        assertEquals("b0000000-0000-4000-8000-000000000000", model.id.value)

        // Renaming goes to the same member identity instead of adding a second one.
        model.setDisplayName("Juan Dela Cruz")
        model.save()
        advanceUntilIdle()
        assertEquals(listOf("Juan Dela Cruz"), repository.households.first().single().members.map { it.displayName })

        model.remove()
        advanceUntilIdle()
        assertTrue(model.uiState.value.removed)
        assertEquals(SavePhase.Saved, model.uiState.value.phase)
        assertTrue(repository.households.first().single().members.isEmpty())
    }
}