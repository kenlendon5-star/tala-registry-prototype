package ph.tala.registry.ui.interview

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ph.tala.registry.data.FakeRecordStore
import ph.tala.registry.data.RoomHouseholdRepository
import ph.tala.registry.domain.edit.HouseholdDraft
import ph.tala.registry.domain.form.RegistrySections
import ph.tala.registry.domain.model.AnswerValue
import ph.tala.registry.domain.model.HouseholdId
import ph.tala.registry.domain.model.HouseholdStatus
import ph.tala.registry.ui.households.SavePhase

/** Durable section drafts and explicit saves on the interview Sections tab. */
@OptIn(ExperimentalCoroutinesApi::class)
class InterviewSectionsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val household = HouseholdId("6a000000-0000-4000-8000-000000000001")

    @Before fun start() { Dispatchers.setMain(dispatcher) }
    @After fun finish() { Dispatchers.resetMain() }

    private fun collect(model: InterviewSectionsViewModel) = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.uiState.collect() }

    @Test fun draftsStartFromTheSavedRecordAndSavePersistsNewAnswers() = runTest(dispatcher) {
        val repository = RoomHouseholdRepository(FakeRecordStore())
        repository.createHousehold(household, HouseholdDraft("Luna Reyes", "5 Sampaguita Street", HouseholdStatus.NotStarted))
        repository.saveSectionAnswers(household, RegistrySections.visit, mapOf("visitResult" to AnswerValue.Scalar("Callback")))
        val model = InterviewSectionsViewModel(household, repository)
        collect(model)
        advanceUntilIdle()

        val visit = model.uiState.value.sections.getValue("visit")
        assertEquals("Callback", (visit.answers["visitResult"] as AnswerValue.Scalar).text)

        model.answer(RegistrySections.visit, "visitDate", AnswerValue.Scalar("2026-10-12"))
        model.save(RegistrySections.visit)
        advanceUntilIdle()
        assertEquals(SavePhase.Saved, model.uiState.value.statuses.getValue("visit").phase)

        val stored = repository.householdSectionAnswers(household)
        assertEquals("2026-10-12", (stored["visitDate"] as AnswerValue.Scalar).text)
        assertEquals("Callback", (stored["visitResult"] as AnswerValue.Scalar).text)
    }

    @Test fun incompleteSectionsRefuseToSaveAndReportTheErrors() = runTest(dispatcher) {
        val repository = RoomHouseholdRepository(FakeRecordStore())
        repository.createHousehold(household, HouseholdDraft("Luna Reyes", "5 Sampaguita Street", HouseholdStatus.NotStarted))
        val model = InterviewSectionsViewModel(household, repository)
        collect(model)
        advanceUntilIdle()

        model.answer(RegistrySections.visit, "visitResult", AnswerValue.Scalar("Callback"))
        model.save(RegistrySections.visit)
        advanceUntilIdle()

        val status = model.uiState.value.statuses.getValue("visit")
        assertEquals(SavePhase.Failed, status.phase)
        val form = model.uiState.value.sections.getValue("visit")
        assertTrue(form.errors.containsKey("visitDate"))
        assertTrue(form.errors.containsKey("callbackDate"))
    }

    @Test fun editingAfterASaveDropsTheSavedAcknowledgement() = runTest(dispatcher) {
        val repository = RoomHouseholdRepository(FakeRecordStore())
        repository.createHousehold(household, HouseholdDraft("Luna Reyes", "5 Sampaguita Street", HouseholdStatus.NotStarted))
        val model = InterviewSectionsViewModel(household, repository)
        collect(model)
        advanceUntilIdle()

        model.answer(RegistrySections.setup, "enumerator", AnswerValue.Scalar("Jamie Dela Cruz"))
        model.answer(RegistrySections.setup, "barangay", AnswerValue.Scalar("Barangay Cogon"))
        model.answer(RegistrySections.setup, "purok", AnswerValue.Scalar("Purok 2"))
        model.answer(RegistrySections.setup, "interviewDate", AnswerValue.Scalar("2026-10-12"))
        model.save(RegistrySections.setup)
        advanceUntilIdle()
        assertEquals(SavePhase.Saved, model.uiState.value.statuses.getValue("setup").phase)

        model.answer(RegistrySections.setup, "purok", AnswerValue.Scalar("Purok 3"))
        advanceUntilIdle()
        assertNotEquals("Unsaved content must not keep a Saved label", SavePhase.Saved, model.uiState.value.statuses.getValue("setup").phase)
    }
}