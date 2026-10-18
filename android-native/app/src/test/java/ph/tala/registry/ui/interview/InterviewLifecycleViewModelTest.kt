package ph.tala.registry.ui.interview

import ph.tala.registry.domain.edit.HouseholdDraft
import ph.tala.registry.domain.edit.MemberDraft

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import ph.tala.registry.data.*
import ph.tala.registry.domain.form.*
import ph.tala.registry.domain.model.*
import ph.tala.registry.ui.households.SavePhase

/**
 * Day 13 workspace behavior: a drawn signature satisfies the typed-name requirement, and
 * refusing consent removes the saved drawn mark exactly once, as the reference prototype does.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InterviewLifecycleViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repo: RoomHouseholdRepository
    private lateinit var photos: InMemoryPhotoStore
    private val hh = HouseholdId("hh")

    @Before fun before() {
        Dispatchers.setMain(dispatcher)
        repo = RoomHouseholdRepository(FakeRecordStore())
        photos = InMemoryPhotoStore()
        runTest(dispatcher) {
            repo.createHousehold(hh, HouseholdDraft("Fixture", "Street", HouseholdStatus.InProgress))
            repo.saveMember(hh, MemberId("member"), MemberDraft("Maria"))
        }
    }

    @After fun after() { Dispatchers.resetMain() }

    private fun workspace() = InterviewWorkspaceViewModel(hh, repo, photos, initialRoute = "consent")

    @Test fun `check accepts a drawn signature without a typed name`() = runTest(dispatcher) {
        val model = workspace()
        advanceUntilIdle()
        model.answer("consent", AnswerValue.Scalar("Yes")); advanceUntilIdle()
        model.saveSignature(byteArrayOf(1, 2, 3)); advanceUntilIdle()
        model.check()
        assertTrue("the drawn mark replaces the typed name requirement", model.state.value.errors["signedName"] == null)
        assertTrue(model.hasSignature)
        assertEquals(listOf("save"), photos.signatureEvents)
    }

    @Test fun `refusing consent removes the drawn signature once`() = runTest(dispatcher) {
        val model = workspace()
        advanceUntilIdle()
        model.answer("consent", AnswerValue.Scalar("Yes")); advanceUntilIdle()
        model.saveSignature(byteArrayOf(1, 2, 3)); advanceUntilIdle()
        assertTrue(model.hasSignature)
        model.answer("consent", AnswerValue.Scalar("No")); advanceUntilIdle()
        assertFalse("the refusal clears the drawn mark", model.hasSignature)
        assertEquals(listOf("save", "remove"), photos.signatureEvents)
        assertTrue(repo.interviewRecord(hh)!!.attachments.none { it.kind == HouseholdRepository.SIGNATURE })
        assertTrue(model.state.value.message.contains("Refusal recorded"))
    }

    @Test fun `clear signature removes the attachment and keeps the answer editable`() = runTest(dispatcher) {
        val model = workspace()
        advanceUntilIdle()
        model.answer("consent", AnswerValue.Scalar("Yes")); advanceUntilIdle()
        model.saveSignature(byteArrayOf(9)); advanceUntilIdle()
        model.clearSignature(); advanceUntilIdle()
        assertFalse(model.hasSignature)
        assertEquals(listOf("save", "remove"), photos.signatureEvents)
        assertEquals("Yes", model.state.value.answers.scalar("consent"))
        assertEquals(SavePhase.Saved, model.state.value.phase)
    }

    @Test fun `review reports local progress`() = runTest(dispatcher) {
        val model = workspace()
        advanceUntilIdle()
        model.review(); advanceUntilIdle()
        assertTrue(model.state.value.showingReview)
        val progress = model.state.value.progress
        assertEquals(QuestionnaireSections.all.size, progress.total)
        assertTrue("an interview with only consent typed is not complete", progress.ready < progress.total)
        assertTrue(model.state.value.issues.isNotEmpty())
    }

    @Test fun `clear queues behind an unacknowledged signature save`() = runTest(dispatcher) {
        val model = workspace()
        advanceUntilIdle()
        model.answer("consent", AnswerValue.Scalar("Yes")); advanceUntilIdle()
        model.saveSignature(byteArrayOf(1))
        model.clearSignature() // save coroutine has not run, so hasSignature is still false
        advanceUntilIdle()
        assertFalse(model.hasSignature)
        assertEquals(listOf("save", "remove"), photos.signatureEvents)
    }

    @Test fun `refusal cleanup failure blocks leaving and retries with save`() = runTest(dispatcher) {
        var fail = true
        val failing = object : PhotoRepository by photos {
            override suspend fun removeSignature(householdId: HouseholdId, repository: HouseholdRepository): ph.tala.registry.data.local.WriteResult =
                if (fail) ph.tala.registry.data.local.WriteResult.Failed("Storage unavailable") else photos.removeSignature(householdId, repository)
        }
        val model = InterviewWorkspaceViewModel(hh, repo, failing, initialRoute = "consent")
        advanceUntilIdle()
        model.answer("consent", AnswerValue.Scalar("Yes")); advanceUntilIdle()
        model.saveSignature(byteArrayOf(1)); advanceUntilIdle()
        model.answer("consent", AnswerValue.Scalar("No")); advanceUntilIdle()
        assertEquals(SavePhase.Failed, model.state.value.phase)
        assertTrue(model.hasSignature)
        var left = false
        model.leave { left = true }; advanceUntilIdle()
        assertFalse(left)
        fail = false
        model.save(); advanceUntilIdle()
        assertFalse(model.hasSignature)
        assertEquals(SavePhase.Saved, model.state.value.phase)
        model.leave { left = true }; advanceUntilIdle()
        assertTrue(left)
    }

    @Test fun `queued signature cannot restore a mark after refusal`() = runTest(dispatcher) {
        val model = workspace()
        advanceUntilIdle()
        model.answer("consent", AnswerValue.Scalar("Yes")); advanceUntilIdle()
        model.saveSignature(byteArrayOf(1))
        model.answer("consent", AnswerValue.Scalar("No"))
        advanceUntilIdle()
        assertFalse(model.hasSignature)
        assertTrue(repo.reviewInterview(hh) is ph.tala.registry.data.local.WriteResult.Failed)
    }
}
