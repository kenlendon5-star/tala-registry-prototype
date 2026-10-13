package ph.tala.registry.audit

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
import ph.tala.registry.ui.RegistryViewModel
import ph.tala.registry.ui.households.SavePhase
import ph.tala.registry.ui.interview.InterviewWorkspaceViewModel
import ph.tala.registry.ui.profile.EnumeratorProfileViewModel

@OptIn(ExperimentalCoroutinesApi::class)
class RegistrationRegressionTest {
    private val dispatcher = StandardTestDispatcher()
    private val household = HouseholdId("ad080000-0000-4000-8000-000000000001")
    private val a = MemberId("ad080000-0000-4000-8000-000000000011")
    private val b = MemberId("ad080000-0000-4000-8000-000000000012")
    private val c = MemberId("ad080000-0000-4000-8000-000000000013")
    private val store = FakeRecordStore()
    private val profiles = Profiles()
    private val repo = RoomHouseholdRepository(store, profiles = profiles)
    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { Dispatchers.resetMain() }
    private suspend fun create() { assertEquals(WriteResult.Saved, repo.createHousehold(household, HouseholdDraft("Legacy Name", "Address", HouseholdStatus.InProgress))) }
    private suspend fun answers(section: SectionSpec, member: MemberId? = null) = InterviewRules.answers(repo.interviewRecord(household)!!, section, member?.value)

    @Test fun attributionIsCapturedAtCreationAndNeverRestampedByOpeningOrEditing() = runTest(dispatcher) {
        create()
        profiles.profile.value = EnumeratorProfile("Enumerator B", "Barangay Cogon")
        val workspace = InterviewWorkspaceViewModel(household, repo, profiles = profiles)
        advanceUntilIdle()
        assertEquals("Enumerator A", workspace.state.value.answers.scalar("enumerator"))
        repo.updateHousehold(household, HouseholdDraft("Edited Legacy", "New address", HouseholdStatus.InProgress))
        assertEquals("Enumerator A", answers(QuestionnaireSections.setup).scalar("enumerator"))
    }

    @Test fun structuredIdentityCannotBeOverwrittenByTheLegacyEditorAndAddressStaysInSync() = runTest(dispatcher) {
        create()
        val before = answers(QuestionnaireSections.household)
        assertEquals(WriteResult.Saved, repo.saveDraftSection(household, "household", null,
            before + mapOf("firstName" to AnswerValue.Scalar("Ana"), "lastName" to AnswerValue.Scalar("Reyes")), before))
        assertTrue(repo.updateHousehold(household, HouseholdDraft("Ana Cruz", "Address", HouseholdStatus.InProgress)) is WriteResult.Failed)
        assertEquals("Ana Reyes", repo.interviewRecord(household)!!.household.headName)
        assertEquals(WriteResult.Saved, repo.updateHousehold(household, HouseholdDraft("Ana Reyes", "Moved address", HouseholdStatus.InProgress)))
        val current = answers(QuestionnaireSections.household)
        assertEquals("Moved address", current.scalar("address"))
        assertEquals(WriteResult.Saved, repo.saveDraftSection(household, "household", null, current + ("lastName" to AnswerValue.Scalar("Cruz")), current))
        assertEquals("Ana Cruz", repo.interviewRecord(household)!!.household.headName)
        assertTrue(repo.saveDraftSection(household, "household", null, before, before) is WriteResult.Failed)
    }

    @Test fun memberIdentityAndAnswersStayWithTheirOwnIds() = runTest(dispatcher) {
        create()
        repo.saveMember(household, a, MemberDraft("Legacy A")); repo.saveMember(household, b, MemberDraft("Legacy B"))
        val fields = mapOf("firstName" to AnswerValue.Scalar("Ana"), "lastName" to AnswerValue.Scalar("Reyes"), "sex" to AnswerValue.Scalar("Female"))
        assertEquals(WriteResult.Saved, repo.saveDraftSection(household, "demographics", a, fields, emptyMap()))
        assertTrue(repo.saveMember(household, a, MemberDraft("Different Name")) is WriteResult.Failed)
        assertEquals(WriteResult.Saved, repo.saveMember(household, b, MemberDraft("Edited B")))
        assertEquals("Ana Reyes", repo.interviewRecord(household)!!.members.single { it.id == a.value }.displayName)
        assertTrue(answers(QuestionnaireSections.demographics, b).isEmpty())
        assertEquals(WriteResult.Saved, repo.saveDraftSection(household, "demographics", a, fields + ("lastName" to AnswerValue.Scalar("Cruz")), fields))
        assertEquals("Ana Cruz", repo.interviewRecord(household)!!.members.single { it.id == a.value }.displayName)
    }

    @Test fun removingPhotoFlushesEditsDuringDebounce() = runTest(dispatcher) {
        create()
        val photos = Photos()
        val workspace = InterviewWorkspaceViewModel(household, repo, photos, initialRoute = "household")
        advanceUntilIdle()
        workspace.answer("respondent", AnswerValue.Scalar("New respondent"))
        workspace.removePhoto()
        advanceUntilIdle()
        assertEquals(1, photos.removals)
        assertEquals("New respondent", answers(QuestionnaireSections.household).scalar("respondent"))
        assertEquals("New respondent", workspace.state.value.answers.scalar("respondent"))
    }

    @Test fun failedDraftSavePreventsPhotoMutationAndKeepsTheDraftForRetry() = runTest(dispatcher) {
        create()
        val photos = Photos()
        val workspace = InterviewWorkspaceViewModel(household, repo, photos, initialRoute = "household")
        advanceUntilIdle()
        store.failWith = "Storage full"
        workspace.answer("respondent", AnswerValue.Scalar("Keep this draft"))
        workspace.removePhoto()
        advanceUntilIdle()
        assertEquals(0, photos.removals)
        assertEquals(SavePhase.Failed, workspace.state.value.phase)
        assertEquals("Keep this draft", workspace.state.value.answers.scalar("respondent"))
        store.failWith = null
        workspace.removePhoto()
        advanceUntilIdle()
        assertEquals(1, photos.removals)
        assertEquals("Keep this draft", answers(QuestionnaireSections.household).scalar("respondent"))
    }

    @Test fun photoFailureDoesNotLoseTheAlreadyFlushedDraft() = runTest(dispatcher) {
        create()
        val photos = Photos().apply { result = WriteResult.Failed("Picture unavailable") }
        val workspace = InterviewWorkspaceViewModel(household, repo, photos, initialRoute = "household")
        advanceUntilIdle()
        workspace.answer("phone", AnswerValue.Scalar("09123456789"))
        workspace.removePhoto()
        advanceUntilIdle()
        assertEquals(SavePhase.Failed, workspace.state.value.phase)
        assertEquals("09123456789", workspace.state.value.answers.scalar("phone"))
        assertEquals("09123456789", answers(QuestionnaireSections.household).scalar("phone"))
    }

    @Test fun profileRemovalCommitsBeforeSuccessAndEditsClearSavedAcknowledgement() = runTest(dispatcher) {
        profiles.profile.value = profiles.profile.value.copy(photoRelativePath = "profile/old.jpg")
        val model = EnumeratorProfileViewModel(profiles, Photos())
        advanceUntilIdle()
        profiles.failure = "Storage full"
        model.removePhoto()
        advanceUntilIdle()
        assertEquals(SavePhase.Failed, model.uiState.value.phase)
        assertEquals("profile/old.jpg", model.uiState.value.photoPath)
        profiles.failure = null
        model.removePhoto()
        advanceUntilIdle()
        assertNull(profiles.profile.value.photoRelativePath)
        assertNull(model.uiState.value.photoPath)
        assertEquals(SavePhase.Saved, model.uiState.value.phase)
        model.setName("Unsaved name")
        assertEquals(SavePhase.Idle, model.uiState.value.phase)
        assertEquals("Enumerator A", profiles.profile.value.name)
    }

    @Test fun aFailedPhotoOperationCanBeRetriedWithoutChangingTheSavedDraft() = runTest(dispatcher) {
        create()
        val photos = Photos().apply { result = WriteResult.Failed("Picture unavailable") }
        val workspace = InterviewWorkspaceViewModel(household, repo, photos, initialRoute = "household")
        advanceUntilIdle()
        workspace.removePhoto()
        advanceUntilIdle()
        assertEquals(SavePhase.Failed, workspace.state.value.phase)
        photos.result = WriteResult.Saved
        workspace.removePhoto()
        advanceUntilIdle()
        assertEquals(2, photos.removals)
        assertEquals(SavePhase.Saved, workspace.state.value.phase)
    }

    @Test fun answersTypedDuringADraftWriteArePreservedAndSavedNext() = runTest(dispatcher) {
        create()
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val delayed = object : HouseholdRepository by repo {
            override suspend fun saveDraftSection(id: HouseholdId, route: String, memberId: MemberId?, answers: AnswerState, expected: AnswerState): WriteResult {
                started.complete(Unit)
                finish.await()
                return repo.saveDraftSection(id, route, memberId, answers, expected)
            }
        }
        val workspace = InterviewWorkspaceViewModel(household, delayed, initialRoute = "household")
        advanceUntilIdle()
        workspace.answer("respondent", AnswerValue.Scalar("First saved answer"))
        workspace.save()
        runCurrent()
        assertTrue(started.isCompleted)
        assertEquals(SavePhase.Saving, workspace.state.value.phase)
        workspace.answer("phone", AnswerValue.Scalar("09123456789"))
        finish.complete(Unit)
        advanceUntilIdle()
        assertEquals("First saved answer", answers(QuestionnaireSections.household).scalar("respondent"))
        assertEquals("09123456789", answers(QuestionnaireSections.household).scalar("phone"))
        assertEquals("09123456789", workspace.state.value.answers.scalar("phone"))
        assertEquals(SavePhase.Saved, workspace.state.value.phase)
    }

    @Test fun answersTypedDuringAPhotoWriteRemainInTheDraftAndReachStorage() = runTest(dispatcher) {
        create()
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val photos = object : PhotoRepository by Photos() {
            override suspend fun removeHouseholdPhoto(householdId: HouseholdId, repository: HouseholdRepository): WriteResult {
                started.complete(Unit)
                finish.await()
                return WriteResult.Saved
            }
        }
        val workspace = InterviewWorkspaceViewModel(household, repo, photos, initialRoute = "household")
        advanceUntilIdle()
        workspace.answer("respondent", AnswerValue.Scalar("Before picture"))
        workspace.removePhoto()
        runCurrent()
        assertTrue(started.isCompleted)
        workspace.answer("phone", AnswerValue.Scalar("09123456789"))
        finish.complete(Unit)
        advanceUntilIdle()
        assertEquals("Before picture", answers(QuestionnaireSections.household).scalar("respondent"))
        assertEquals("09123456789", answers(QuestionnaireSections.household).scalar("phone"))
        assertEquals("09123456789", workspace.state.value.answers.scalar("phone"))
        assertEquals(SavePhase.Saved, workspace.state.value.phase)
    }

    @Test fun liveCountsFollowSexEditsMemberRemovalArchiveAndRestore() = runTest(dispatcher) {
        create()
        for ((id, name) in listOf(a to "Member A", b to "Member B", c to "Member C")) repo.saveMember(household, id, MemberDraft(name))
        repo.saveDraftSection(household, "demographics", a, mapOf("sex" to AnswerValue.Scalar("Male")), emptyMap())
        repo.saveDraftSection(household, "demographics", b, mapOf("sex" to AnswerValue.Scalar("Female")), emptyMap())
        val model = RegistryViewModel(repo, SavedStateHandle())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.uiState.collect {} }
        advanceUntilIdle()
        fun counts() = model.uiState.value.let { listOf(it.households.size, it.memberCount, it.maleCount, it.femaleCount, it.unrecordedSexCount) }
        assertEquals(listOf(1, 3, 1, 1, 1), counts())
        repo.saveDraftSection(household, "demographics", a, mapOf("sex" to AnswerValue.Scalar("Female")), answers(QuestionnaireSections.demographics, a))
        advanceUntilIdle(); assertEquals(listOf(1, 3, 0, 2, 1), counts())
        repo.removeMember(household, b)
        advanceUntilIdle(); assertEquals(listOf(1, 2, 0, 1, 1), counts())
        repo.setArchived(household, true)
        advanceUntilIdle(); assertEquals(listOf(0, 0, 0, 0, 0), counts())
        repo.setArchived(household, false)
        advanceUntilIdle(); assertEquals(listOf(1, 2, 0, 1, 1), counts())
    }

    private class Profiles : EnumeratorProfileStore {
        override val profile = MutableStateFlow(EnumeratorProfile("Enumerator A", "Barangay Cogon"))
        var failure: String? = null
        override suspend fun save(profile: EnumeratorProfile): WriteResult {
            failure?.let { return WriteResult.Failed(it) }
            this.profile.value = profile
            return WriteResult.Saved
        }
    }
    private class Photos : PhotoRepository {
        var removals = 0
        var result: WriteResult = WriteResult.Saved
        override suspend fun saveHouseholdPhoto(source: Uri, householdId: HouseholdId, repository: HouseholdRepository) = result
        override suspend fun removeHouseholdPhoto(householdId: HouseholdId, repository: HouseholdRepository): WriteResult { removals++; return result }
        override suspend fun saveProfilePhoto(source: Uri, profile: EnumeratorProfile, profiles: EnumeratorProfileStore) = profiles.save(profile.copy(photoRelativePath = "profile/new.jpg"))
        override suspend fun removeProfilePhoto(profile: EnumeratorProfile, profiles: EnumeratorProfileStore) = profiles.save(profile.copy(photoRelativePath = null))
    }
}
