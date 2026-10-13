package ph.tala.registry.ui.interview

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import ph.tala.registry.data.*
import ph.tala.registry.data.local.WriteResult
import ph.tala.registry.domain.edit.*
import ph.tala.registry.domain.form.*
import ph.tala.registry.domain.model.*
import ph.tala.registry.ui.households.SavePhase

@OptIn(ExperimentalCoroutinesApi::class)
class InterviewSectionsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { Dispatchers.resetMain() }
    @Test fun staleWorkspaceCannotOverwriteAChangedMemberSection() = runTest(dispatcher) {
        val repo = RoomHouseholdRepository(FakeRecordStore())
        val hh = HouseholdId("hh"); val member = MemberId("member")
        repo.createHousehold(hh, HouseholdDraft("Fixture", "Street", HouseholdStatus.InProgress))
        repo.saveMember(hh, member, MemberDraft("Maria"))
        val first = InterviewWorkspaceViewModel(hh, repo, initialRoute = "demographics", initialMember = member.value)
        val stale = InterviewWorkspaceViewModel(hh, repo, initialRoute = "demographics", initialMember = member.value)
        advanceUntilIdle()
        first.answer("registeredVoter", AnswerValue.Scalar("Yes")); advanceUntilIdle()
        stale.answer("registeredVoter", AnswerValue.Scalar("No")); advanceUntilIdle()
        assertEquals(SavePhase.Failed, stale.state.value.phase)
        assertEquals("Yes", InterviewRules.answers(repo.interviewRecord(hh)!!, QuestionnaireSections.demographics, member.value).scalar("registeredVoter"))
        stale.reload(); advanceUntilIdle()
        assertEquals("Yes", stale.state.value.answers.scalar("registeredVoter"))
    }
}
