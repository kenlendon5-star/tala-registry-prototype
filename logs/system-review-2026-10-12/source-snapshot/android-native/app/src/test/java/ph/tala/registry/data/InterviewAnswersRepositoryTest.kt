package ph.tala.registry.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ph.tala.registry.data.local.WriteResult
import ph.tala.registry.domain.edit.HouseholdDraft
import ph.tala.registry.domain.form.RegistrySections
import ph.tala.registry.domain.model.AnswerState
import ph.tala.registry.domain.model.AnswerValue
import ph.tala.registry.domain.model.HouseholdId
import ph.tala.registry.domain.model.HouseholdStatus

/** Durable registration answers through the repository (plan card D08). */
class InterviewAnswersRepositoryTest {
    private val household = HouseholdId("5a000000-0000-4000-8000-000000000001")

    private suspend fun created(repo: RoomHouseholdRepository) {
        assertEquals(WriteResult.Saved, repo.createHousehold(household, HouseholdDraft("Luna Reyes", "5 Sampaguita Street", HouseholdStatus.NotStarted)))
    }

    @Test fun savedSectionAnswersReloadAndOtherSectionsAreUntouched() = runTest {
        val repo = RoomHouseholdRepository(FakeRecordStore())
        created(repo)
        val visit: AnswerState = mapOf(
            "visitDate" to AnswerValue.Scalar("2026-10-12"),
            "visitResult" to AnswerValue.Scalar("Callback"),
            "callbackDate" to AnswerValue.Scalar("2026-10-20T09:30"),
        )
        assertEquals(WriteResult.Saved, repo.saveSectionAnswers(household, RegistrySections.visit, visit))
        assertEquals(WriteResult.Saved, repo.saveSectionAnswers(household, RegistrySections.setup, mapOf("enumerator" to AnswerValue.Scalar("Jamie Dela Cruz"))))

        val saved = repo.householdSectionAnswers(household)
        assertEquals("Callback", (saved["visitResult"] as AnswerValue.Scalar).text)
        assertEquals("Jamie Dela Cruz", (saved["enumerator"] as AnswerValue.Scalar).text)

        // Re-saving the visit section replaces only its own rows.
        assertEquals(WriteResult.Saved, repo.saveSectionAnswers(
            household, RegistrySections.visit,
            mapOf("visitDate" to AnswerValue.Scalar("2026-10-13"), "visitResult" to AnswerValue.Scalar("Completed visit")),
        ))
        val reloaded = repo.householdSectionAnswers(household)
        assertEquals("Completed visit", (reloaded["visitResult"] as AnswerValue.Scalar).text)
        assertNull("Replaced section answers must not survive", reloaded["callbackDate"])
        assertEquals("Jamie Dela Cruz", (reloaded["enumerator"] as AnswerValue.Scalar).text)
    }

    @Test fun visitResultDrivesTheHouseholdStatusSoCountsStayTruthful() = runTest {
        val repo = RoomHouseholdRepository(FakeRecordStore())
        created(repo)
        repo.saveSectionAnswers(household, RegistrySections.visit, mapOf("visitResult" to AnswerValue.Scalar("Callback")))
        assertEquals(HouseholdStatus.Callback, repo.households.first().single().status)
        repo.saveSectionAnswers(household, RegistrySections.visit, mapOf("visitResult" to AnswerValue.Scalar("Completed visit")))
        assertEquals(HouseholdStatus.InProgress, repo.households.first().single().status)
    }

    @Test fun archivedMarkerSurvivesASectionSave() = runTest {
        val repo = RoomHouseholdRepository(FakeRecordStore())
        created(repo)
        assertEquals(WriteResult.Saved, repo.setArchived(household, true))
        assertEquals(WriteResult.Saved, repo.saveSectionAnswers(household, RegistrySections.visit, mapOf("visitResult" to AnswerValue.Scalar("Completed visit"))))
        assertTrue(repo.households.first().single().archived)
    }

    @Test fun unknownHouseholdFailsWithoutWriting() = runTest {
        val repo = RoomHouseholdRepository(FakeRecordStore())
        created(repo)
        val other = HouseholdId("5a000000-0000-4000-8000-000000000002")
        assertTrue(repo.saveSectionAnswers(other, RegistrySections.visit, emptyMap()) is WriteResult.Failed)
        assertTrue(repo.householdSectionAnswers(other).isEmpty())
    }
}