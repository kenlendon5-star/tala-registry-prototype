package ph.tala.registry.domain.form

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import ph.tala.registry.data.*
import ph.tala.registry.data.local.WriteResult
import ph.tala.registry.domain.edit.*
import ph.tala.registry.domain.model.*
import ph.tala.registry.ui.households.SavePhase
import ph.tala.registry.ui.interview.InterviewWorkspaceViewModel

@OptIn(ExperimentalCoroutinesApi::class)
class DemographicSectionsMemberIsolationTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = FakeRecordStore()
    private val repository = RoomHouseholdRepository(store)
    private val household = HouseholdId("d0900000-0000-4000-8000-000000000001")
    private val maria = MemberId("d0900000-0000-4000-8000-000000000002")
    private val juan = MemberId("d0900000-0000-4000-8000-000000000003")
    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { Dispatchers.resetMain() }
    private suspend fun create() {
        assertEquals(WriteResult.Saved, repository.createHousehold(household, HouseholdDraft("Identity fixture", "Demo street", HouseholdStatus.InProgress)))
        repository.saveMember(household, maria, MemberDraft("Maria"))
        repository.saveMember(household, juan, MemberDraft("Juan"))
    }
    @Test fun realWorkspaceSavesBeforeSwitchingAndReloadsAllThreeMemberSections() = runTest(dispatcher) {
        create()
        val model = InterviewWorkspaceViewModel(household, repository)
        advanceUntilIdle()
        for ((section, key) in listOf(QuestionnaireSections.demographics to "registeredVoter", QuestionnaireSections.other to "soloParent", QuestionnaireSections.migration to "overseas")) {
            model.select(section.route, maria.value); advanceUntilIdle()
            model.answer(key, AnswerValue.Scalar("Yes"))
            model.select(section.route, juan.value); advanceUntilIdle() // cancels debounce, must flush Maria
            assertFalse(model.state.value.answers.containsKey(key))
            model.answer(key, AnswerValue.Scalar("No"))
            model.select(section.route, maria.value); advanceUntilIdle()
            assertEquals("Yes", model.state.value.answers.scalar(key))
            val recreated = InterviewWorkspaceViewModel(household, repository, initialRoute = section.route, initialMember = juan.value)
            advanceUntilIdle()
            assertEquals("No", recreated.state.value.answers.scalar(key))
        }
    }
    @Test fun failedSaveKeepsMemberAndDraftUntilSuccessfulRetry() = runTest(dispatcher) {
        create()
        val model = InterviewWorkspaceViewModel(household, repository, initialRoute = "migration", initialMember = maria.value)
        advanceUntilIdle()
        store.failWith = "Storage full"
        model.answer("overseas", AnswerValue.Scalar("Yes"))
        model.select("migration", juan.value); advanceUntilIdle()
        assertEquals(maria.value, model.state.value.memberId)
        assertEquals(SavePhase.Failed, model.state.value.phase)
        assertEquals("Yes", model.state.value.answers.scalar("overseas"))
        store.failWith = null
        model.select("migration", juan.value); advanceUntilIdle()
        assertEquals(juan.value, model.state.value.memberId)
        assertTrue(model.state.value.answers.isEmpty())
        assertEquals("Yes", InterviewRules.answers(repository.interviewRecord(household)!!, QuestionnaireSections.migration, maria.value).scalar("overseas"))
    }
    @Test fun clearingOneMembersBranchPreservesOtherMembersAndSections() = runTest(dispatcher) {
        create()
        val model = InterviewWorkspaceViewModel(household, repository)
        advanceUntilIdle()
        for (member in listOf(maria, juan)) {
            model.select("other", member.value); advanceUntilIdle()
            model.answer("disability", AnswerValue.Scalar("Yes"))
            model.answer("registeredPwd", AnswerValue.Scalar("Yes"))
            model.answer("pwdId", AnswerValue.Scalar(member.value))
            model.answer("disabilityDetail", AnswerValue.Scalar("Visual"))
            model.select("migration", member.value); advanceUntilIdle()
            model.answer("overseas", AnswerValue.Scalar("Yes")); advanceUntilIdle()
        }
        model.select("other", maria.value); advanceUntilIdle()
        model.answer("disability", AnswerValue.Scalar("No")); advanceUntilIdle()
        val record = repository.interviewRecord(household)!!
        assertFalse(InterviewRules.answers(record, QuestionnaireSections.other, maria.value).containsKey("pwdId"))
        assertEquals(juan.value, InterviewRules.answers(record, QuestionnaireSections.other, juan.value).scalar("pwdId"))
        assertEquals("Yes", InterviewRules.answers(record, QuestionnaireSections.migration, maria.value).scalar("overseas"))
    }
}
