package ph.tala.registry.audit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import ph.tala.registry.data.*
import ph.tala.registry.data.local.WriteResult
import ph.tala.registry.domain.edit.*
import ph.tala.registry.domain.form.*
import ph.tala.registry.domain.model.*
import ph.tala.registry.ui.*
import ph.tala.registry.ui.households.*
import ph.tala.registry.ui.interview.InterviewWorkspaceViewModel

/** Reproductions of the D01–D12 audit's lifecycle, navigation and editor failures. */
@OptIn(ExperimentalCoroutinesApi::class)
class FoundationFixesTest {
    private val dispatcher = StandardTestDispatcher()
    private val hh = HouseholdId("fixture-household")
    private val first = MemberId("first-member")
    private val second = MemberId("second-member")
    private val store = FakeRecordStore()
    private val repo = RoomHouseholdRepository(store)

    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { Dispatchers.resetMain() }

    private suspend fun seed() {
        repo.createHousehold(hh, HouseholdDraft("Fixture Family", "Fictional Street", HouseholdStatus.InProgress))
        repo.saveMember(hh, first, MemberDraft("First Person"))
        repo.saveMember(hh, second, MemberDraft("Second Person"))
    }

    private fun restored(handle: SavedStateHandle) = SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })
    private fun own(model: InterviewWorkspaceViewModel) = ViewModelStore().also { it.put("workspace", model) }
    private fun value(text: String) = AnswerValue.Scalar(text)

    @Test fun killedDebounceRestoresExactMemberSectionAndDraft() = runTest(dispatcher) {
        seed()
        val handle = SavedStateHandle()
        val model = InterviewWorkspaceViewModel(hh, repo, savedState = handle)
        val owner = own(model)
        advanceUntilIdle()
        model.select("education", second.value)
        advanceUntilIdle()
        model.answer("attending", value("No"))
        model.answer("reason", value("Working"))
        val saved = restored(handle)
        owner.clear() // pending debounce must not write in the old process
        advanceUntilIdle()
        assertTrue(InterviewRules.answers(repo.interviewRecord(hh)!!, QuestionnaireSections.education, second.value).isEmpty())

        val resumed = InterviewWorkspaceViewModel(hh, repo, savedState = saved)
        val resumedOwner = own(resumed)
        advanceUntilIdle()
        assertEquals("education", resumed.state.value.route)
        assertEquals(second.value, resumed.state.value.memberId)
        assertEquals("Working", resumed.state.value.answers.scalar("reason"))
        resumed.save()
        advanceUntilIdle()
        assertEquals(SavePhase.Saved, resumed.state.value.phase)
        assertEquals("Working", InterviewRules.answers(repo.interviewRecord(hh)!!, QuestionnaireSections.education, second.value).scalar("reason"))
        assertTrue(InterviewRules.answers(repo.interviewRecord(hh)!!, QuestionnaireSections.education, first.value).isEmpty())
        resumedOwner.clear()
    }

    @Test fun failedDraftRestorationRetainsOriginalConflictBase() = runTest(dispatcher) {
        seed()
        val handle = SavedStateHandle()
        val model = InterviewWorkspaceViewModel(hh, repo, initialRoute = "demographics", initialMember = first.value, savedState = handle)
        val owner = own(model)
        advanceUntilIdle()
        store.failWith = "Storage full"
        model.answer("registeredVoter", value("No"))
        advanceUntilIdle()
        assertEquals(SavePhase.Failed, model.state.value.phase)
        val saved = restored(handle)
        owner.clear()
        store.failWith = null
        repo.saveDraftSection(hh, "demographics", first, mapOf("registeredVoter" to value("Yes")), emptyMap())

        val resumed = InterviewWorkspaceViewModel(hh, repo, savedState = saved)
        val resumedOwner = own(resumed)
        advanceUntilIdle()
        assertEquals("No", resumed.state.value.answers.scalar("registeredVoter"))
        resumed.save()
        advanceUntilIdle()
        assertEquals(SavePhase.Failed, resumed.state.value.phase)
        assertTrue(resumed.state.value.message.contains("another editor"))
        assertEquals("Yes", InterviewRules.answers(repo.interviewRecord(hh)!!, QuestionnaireSections.demographics, first.value).scalar("registeredVoter"))
        resumedOwner.clear()
    }

    @Test fun commitBeforeProcessDeathDoesNotCreateFalseConflictOnRestore() = runTest(dispatcher) {
        seed()
        val handle = SavedStateHandle()
        val model = InterviewWorkspaceViewModel(hh, repo, initialRoute = "education", initialMember = first.value, savedState = handle)
        val owner = own(model)
        advanceUntilIdle()
        model.answer("attending", value("No"))
        val savedBeforeAck = restored(handle)
        owner.clear()
        repo.saveDraftSection(hh, "education", first, mapOf("attending" to value("No")), emptyMap())
        val resumed = InterviewWorkspaceViewModel(hh, repo, savedState = savedBeforeAck)
        val resumedOwner = own(resumed)
        advanceUntilIdle()
        resumed.answer("reason", value("Working"))
        advanceUntilIdle()
        assertEquals(SavePhase.Saved, resumed.state.value.phase)
        resumedOwner.clear()
    }

    @Test fun switchingMembersDrainsKeystrokesArrivingDuringSave() = runTest(dispatcher) {
        seed()
        val slow = object : HouseholdRepository by repo {
            override suspend fun saveDraftSection(id: HouseholdId, route: String, memberId: MemberId?, answers: AnswerState, expected: AnswerState): WriteResult {
                delay(1_000)
                return repo.saveDraftSection(id, route, memberId, answers, expected)
            }
        }
        val model = InterviewWorkspaceViewModel(hh, slow, initialRoute = "education", initialMember = first.value)
        val owner = own(model)
        advanceUntilIdle()
        model.answer("attending", value("No"))
        model.select("education", second.value)
        runCurrent() // first snapshot is now awaiting its write
        model.answer("reason", value("Working"))
        advanceUntilIdle()
        assertEquals(second.value, model.state.value.memberId)
        assertTrue(model.state.value.answers.isEmpty())
        assertEquals("Working", InterviewRules.answers(repo.interviewRecord(hh)!!, QuestionnaireSections.education, first.value).scalar("reason"))
        owner.clear()
    }

    @Test fun newerDraftCanRetryAfterDeathBetweenCommitAndAcknowledgement() = runTest(dispatcher) {
        seed()
        val committed = CompletableDeferred<Unit>()
        val holdAcknowledgement = CompletableDeferred<Unit>()
        val slow = object : HouseholdRepository by repo {
            override suspend fun saveDraftSection(id: HouseholdId, route: String, memberId: MemberId?, answers: AnswerState, expected: AnswerState): WriteResult {
                val result = repo.saveDraftSection(id, route, memberId, answers, expected)
                committed.complete(Unit)
                holdAcknowledgement.await()
                return result
            }
        }
        val handle = SavedStateHandle()
        val model = InterviewWorkspaceViewModel(hh, slow, initialRoute = "education", initialMember = first.value, savedState = handle)
        val owner = own(model)
        advanceUntilIdle()
        model.answer("attending", value("No"))
        model.save()
        runCurrent()
        assertTrue(committed.isCompleted)
        model.answer("reason", value("Working"))
        val saved = restored(handle)
        owner.clear()
        val resumed = InterviewWorkspaceViewModel(hh, repo, savedState = saved)
        val resumedOwner = own(resumed)
        advanceUntilIdle()
        resumed.save()
        advanceUntilIdle()
        assertEquals(SavePhase.Saved, resumed.state.value.phase)
        assertEquals("Working", InterviewRules.answers(repo.interviewRecord(hh)!!, QuestionnaireSections.education, first.value).scalar("reason"))
        resumedOwner.clear()
    }

    @Test fun leavingWaitsForLatestDraftAndStaysOnFailure() = runTest(dispatcher) {
        seed()
        var writes = 0
        val slow = object : HouseholdRepository by repo {
            override suspend fun saveDraftSection(id: HouseholdId, route: String, memberId: MemberId?, answers: AnswerState, expected: AnswerState): WriteResult {
                delay(1_000)
                return if (++writes > 1) WriteResult.Failed("Storage full") else repo.saveDraftSection(id, route, memberId, answers, expected)
            }
        }
        val model = InterviewWorkspaceViewModel(hh, slow, initialRoute = "education", initialMember = first.value)
        val owner = own(model)
        advanceUntilIdle()
        model.answer("attending", value("No"))
        var left = false
        model.leave { left = true }
        runCurrent()
        model.answer("reason", value("Working"))
        advanceUntilIdle()
        assertFalse(left)
        assertEquals(SavePhase.Failed, model.state.value.phase)
        assertEquals("Working", model.state.value.answers.scalar("reason"))
        owner.clear()
    }

    @Test fun reviewedRecordCanOpenNameDetailsAndChangedAddressReopensReview() = runTest(dispatcher) {
        seed()
        val record = repo.interviewRecord(hh)!!
        store.save(record.copy(household = record.household.copy(status = "Reviewed")))
        val model = HouseholdEditViewModel(hh, repo, SavedStateHandle())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.uiState.collect() }
        advanceUntilIdle()
        var opened = false
        model.openNameDetails { opened = true }
        advanceUntilIdle()
        assertTrue(opened)
        assertEquals("Reviewed", repo.interviewRecord(hh)!!.household.status)
        model.setAddress("Corrected fictional address")
        model.save()
        advanceUntilIdle()
        assertEquals(SavePhase.Saved, model.uiState.value.phase)
        assertEquals("InProgress", repo.interviewRecord(hh)!!.household.status)
        assertEquals("Corrected fictional address", repo.interviewRecord(hh)!!.household.address)
        assertTrue(repo.updateHousehold(hh, HouseholdDraft("Fixture Family", "Street", HouseholdStatus.Reviewed)) is WriteResult.Failed)
        assertTrue(repo.createHousehold(HouseholdId("another"), HouseholdDraft("Fixture", "Street", HouseholdStatus.Reviewed)) is WriteResult.Failed)
    }

    @Test fun longQuestionnaireDraftsRemainRepairableWithoutBreakingEditors() = runTest(dispatcher) {
        seed()
        val answers = mapOf("firstName" to value("A".repeat(40)), "lastName" to value("B".repeat(40)), "address" to value("X".repeat(151)))
        val errors = FormRules.validate(QuestionnaireSections.household, answers)
        assertTrue(errors.containsKey("lastName")) // total is 81 including the space
        assertTrue(errors.containsKey("address"))
        assertTrue(repo.saveDraftSection(hh, "household", null, answers, InterviewRules.answers(repo.interviewRecord(hh)!!, QuestionnaireSections.household)) is WriteResult.Saved)
        val record = repo.interviewRecord(hh)!!
        assertEquals("Fixture Family", record.household.headName)
        assertEquals("Fictional Street", record.household.address)
        assertEquals(151, InterviewRules.answers(record, QuestionnaireSections.household).scalar("address").length)
        val corrected = answers + ("lastName" to value("B".repeat(39))) + ("address" to value("X".repeat(150)))
        repo.saveDraftSection(hh, "household", null, corrected, answers)
        val valid = repo.interviewRecord(hh)!!
        assertEquals(80, valid.household.headName.length)
        assertNull(EditRules.headName(valid.household.headName))
        assertNull(EditRules.address(valid.household.address))
        assertTrue(repo.updateHousehold(hh, HouseholdDraft(valid.household.headName, valid.household.address, HouseholdStatus.InProgress)) is WriteResult.Saved)
    }

    @Test fun archivedSearchUsesNameCodeAndAddressLikeActiveSearch() = runTest(dispatcher) {
        seed()
        val other = HouseholdId("another-household")
        repo.createHousehold(other, HouseholdDraft("Other Family", "Other Street", HouseholdStatus.InProgress))
        repo.setArchived(hh, true)
        repo.setArchived(other, true)
        val model = RegistryViewModel(repo, SavedStateHandle())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.uiState.collect() }
        model.setFilter(HouseholdFilter.Archived)
        model.setQuery("Fictional Street")
        advanceUntilIdle()
        assertEquals(listOf(hh), model.uiState.value.visibleHouseholds.map { it.id })
        model.setQuery("no matches")
        advanceUntilIdle()
        assertTrue(model.uiState.value.visibleHouseholds.isEmpty())
    }

    @Test fun legacyOverlongReadOnlyNamesStillOfferNameDetailsRepair() = runTest(dispatcher) {
        seed()
        val record = repo.interviewRecord(hh)!!
        val longName = "A".repeat(80) + " Sample"
        fun encoded(key: String, text: String): ph.tala.registry.data.local.HouseholdAnswerRow {
            val (kind, json) = ph.tala.registry.data.local.AnswerCodec.encode(value(text))
            return ph.tala.registry.data.local.HouseholdAnswerRow(record.interview.id, "household", key, kind, json)
        }
        val nameAnswers = listOf(encoded("firstName", "A".repeat(80)), encoded("lastName", "Sample"))
        store.save(record.copy(
            household = record.household.copy(headName = longName),
            members = record.members.map { if (it.id == first.value) it.copy(displayName = longName) else it },
            householdAnswers = record.householdAnswers + nameAnswers,
            memberAnswers = nameAnswers.map { ph.tala.registry.data.local.MemberAnswerRow(it.interviewId, hh.value, first.value, "demographics", it.field, it.kind, it.value) },
        ))
        val household = HouseholdEditViewModel(hh, repo, SavedStateHandle())
        val member = MemberEditViewModel(hh, first, repo, SavedStateHandle())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { household.uiState.collect() }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { member.uiState.collect() }
        advanceUntilIdle()
        household.setAddress("Pending address edit")
        var householdOpened = false
        var memberOpened = false
        household.openNameDetails { householdOpened = true }
        member.openNameDetails { memberOpened = true }
        advanceUntilIdle()
        assertTrue(householdOpened)
        assertTrue(memberOpened)
        assertEquals("Pending address edit", household.uiState.value.address)
        assertEquals("Fictional Street", repo.interviewRecord(hh)!!.household.address)
    }
}
