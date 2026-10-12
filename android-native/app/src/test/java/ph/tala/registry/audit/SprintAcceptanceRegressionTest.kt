package ph.tala.registry.audit

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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ph.tala.registry.data.FakeRecordStore
import ph.tala.registry.data.RoomHouseholdRepository
import ph.tala.registry.domain.edit.HouseholdDraft
import ph.tala.registry.domain.edit.MemberDraft
import ph.tala.registry.domain.model.HouseholdId
import ph.tala.registry.domain.model.HouseholdStatus
import ph.tala.registry.domain.model.MemberId
import ph.tala.registry.ui.RegistryViewModel
import ph.tala.registry.ui.households.HouseholdEditViewModel
import ph.tala.registry.ui.households.MemberEditViewModel
import ph.tala.registry.ui.households.SavePhase

/**
 * Regression guards from the 12 October 2026 sprint audit
 * (docs/reviews/SPRINT-07-20-CRITERIA.json). Each test encodes one acceptance line the
 * audit found missing or broken in the claimed D07–D20 work:
 *  - D07/D08: the new-member form must be available before the member row exists.
 *  - D19: archived households must leave the active home counts.
 *  - D20: unsaved content must not keep a Saved label.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SprintAcceptanceRegressionTest {
    private val dispatcher = StandardTestDispatcher()
    private val household = HouseholdId("7a000000-0000-4000-8000-000000000001")
    private val member = MemberId("7a000000-0000-4000-8000-000000000011")
    @Before fun start() { Dispatchers.setMain(dispatcher) }
    @After fun finish() { Dispatchers.resetMain() }

    @Test fun aNewMemberMustNotBeShownAsMissingBeforeItsFirstSave() = runTest(dispatcher) {
        val repository = RoomHouseholdRepository(FakeRecordStore())
        repository.createHousehold(household, HouseholdDraft("Audit Family", "Fictional Address", HouseholdStatus.InProgress))
        val model = MemberEditViewModel(household, null, repository, SavedStateHandle(), newId = { member })
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.uiState.collect() }
        advanceUntilIdle()
        assertTrue(model.uiState.value.creating)
        assertFalse("D07/D08: the new-member form must be available before a row exists", model.uiState.value.missing)
    }

    @Test fun archivedHouseholdsAndMembersMustLeaveActiveDashboardCounts() = runTest(dispatcher) {
        val repository = RoomHouseholdRepository(FakeRecordStore())
        repository.createHousehold(household, HouseholdDraft("Audit Family", "Fictional Address", HouseholdStatus.InProgress))
        repository.saveMember(household, member, MemberDraft("Audit Member"))
        repository.setArchived(household, true)
        assertTrue(repository.households.first().single().archived)
        val model = RegistryViewModel(repository, SavedStateHandle())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.uiState.collect() }
        advanceUntilIdle()
        assertEquals("D19: active home counts must exclude archived households", 0, model.uiState.value.households.size)
        assertEquals("D19: active member count must exclude archived households", 0, model.uiState.value.memberCount)
    }

    @Test fun changingASavedDraftMustClearItsSavedAcknowledgement() = runTest(dispatcher) {
        val repository = RoomHouseholdRepository(FakeRecordStore())
        val model = HouseholdEditViewModel(null, repository, SavedStateHandle(), newId = { household })
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.uiState.collect() }
        model.setHeadName("Before Save")
        model.setAddress("Fictional Address")
        model.save()
        advanceUntilIdle()
        assertEquals(SavePhase.Saved, model.uiState.value.phase)
        model.setHeadName("Not Yet Saved")
        advanceUntilIdle()
        assertEquals("Before Save", repository.households.first().single().headName)
        assertNotEquals("D20: unsaved content must not retain a Saved label", SavePhase.Saved, model.uiState.value.phase)
    }
}