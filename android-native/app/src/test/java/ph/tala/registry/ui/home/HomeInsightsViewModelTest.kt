package ph.tala.registry.ui.home

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import ph.tala.registry.data.*
import ph.tala.registry.domain.edit.HouseholdDraft
import ph.tala.registry.domain.edit.MemberDraft
import ph.tala.registry.domain.model.*

@OptIn(ExperimentalCoroutinesApi::class)
class HomeInsightsViewModelTest {
    @Before fun setup() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun teardown() { Dispatchers.resetMain() }

    private class ResumeStore : InterviewResumeStore {
        override val recent = MutableStateFlow<InterviewResume?>(null)
        var fail = false
        override suspend fun remember(resume: InterviewResume) {
            if (fail) throw java.io.IOException("Storage unavailable")
            recent.value = resume
        }
    }

    @Test fun openingHouseholdsPreservesSameScopeAndFailureKeepsBrowsingFallback() = runTest {
        val repository = RoomHouseholdRepository(FakeRecordStore())
        val a = HouseholdId("a")
        val b = HouseholdId("b")
        repository.createHousehold(a, HouseholdDraft("Family A", "Address A", HouseholdStatus.InProgress))
        repository.createHousehold(b, HouseholdDraft("Family B", "Address B", HouseholdStatus.InProgress))
        repository.saveMember(a, MemberId("a-member"), MemberDraft("Resident A"))
        val store = ResumeStore()
        val model = HomeInsightsViewModel(repository, store)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect() }
        advanceUntilIdle()
        assertNull(model.state.value.resume)
        assertEquals(listOf(PurokCount("Not Recorded", 2, true)), model.state.value.puroks)
        val scope = InterviewResume(a, "demographics", "a-member")
        model.remember(scope)
        advanceUntilIdle()
        model.opened(a)
        advanceUntilIdle()
        assertEquals(scope, model.state.value.resume)
        model.opened(b)
        advanceUntilIdle()
        assertEquals(InterviewResume(b), model.state.value.resume)
        store.fail = true
        model.opened(a)
        advanceUntilIdle()
        assertNotNull(model.state.value.message)
        assertEquals(InterviewResume(b), model.state.value.resume)
        repository.setArchived(b, true)
        advanceUntilIdle()
        assertNull(model.state.value.resume)
        assertEquals(1, model.state.value.puroks.sumOf { it.count })
    }
}
