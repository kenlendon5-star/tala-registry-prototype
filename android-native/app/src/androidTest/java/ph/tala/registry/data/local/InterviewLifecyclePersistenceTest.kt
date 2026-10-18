package ph.tala.registry.data.local

import ph.tala.registry.domain.form.QuestionnaireSections
import ph.tala.registry.domain.model.scalar

import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.ByteArrayOutputStream
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import ph.tala.registry.data.PhotoStore
import ph.tala.registry.testing.CompleteInterviewFixture
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ph.tala.registry.data.HouseholdRepository
import ph.tala.registry.data.RoomHouseholdRepository
import ph.tala.registry.domain.edit.HouseholdDraft
import ph.tala.registry.domain.edit.MemberDraft
import ph.tala.registry.domain.form.InterviewRules
import ph.tala.registry.domain.model.AnswerState
import ph.tala.registry.domain.model.AnswerValue
import ph.tala.registry.domain.model.HouseholdId
import ph.tala.registry.domain.model.HouseholdStatus
import ph.tala.registry.domain.model.MemberId

/**
 * Day 13 device acceptance: a refused visit stays a reviewable draft, a callback visit
 * keeps its appointment and badge, and the drawn consent signature survives a real
 * database close and reopen.
 */
class InterviewLifecyclePersistenceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: RegistryDatabase
    private lateinit var store: RoomRecordStore
    private lateinit var repository: RoomHouseholdRepository
    private val mediaRoot = File(context.cacheDir, "day13-signature-test")

    private val HOUSEHOLD_A = "c1d2e3f4-0000-4a00-8000-000000000031"
    private val HOUSEHOLD_B = "c1d2e3f4-0000-4a00-8000-000000000032"
    private val MEMBER_A = "c1d2e3f4-0000-4a00-8000-000000000033"
    private val MEMBER_B = "c1d2e3f4-0000-4a00-8000-000000000034"

    @Before fun startWithAnEmptyFile() {
        context.deleteDatabase("tala-registry-interview-lifecycle-test.db")
        mediaRoot.deleteRecursively()
        openStore()
    }

    @After fun closeAndRemove() {
        database.close()
        mediaRoot.deleteRecursively()
        context.deleteDatabase("tala-registry-interview-lifecycle-test.db")
    }

    private fun openStore() {
        database = RegistryDatabase.open(context, "tala-registry-interview-lifecycle-test.db")
        store = RoomRecordStore(database)
        repository = RoomHouseholdRepository(store)
    }

    /** Evidence artifact pulled by tools/verify-d13-device.ps1 into planning/03-evidence/day13/. */
    private fun record(name: String, content: String) {
        val folder = File(context.getExternalFilesDir(null), "day13").apply { mkdirs() }
        File(folder, name).writeText(content)
    }

    private suspend fun seed(id: String, memberId: String, name: String) {
        assertEquals(WriteResult.Saved, repository.createHousehold(HouseholdId(id), HouseholdDraft(name, "24 Mabini Street", HouseholdStatus.InProgress)))
        assertEquals(WriteResult.Saved, repository.saveMember(HouseholdId(id), MemberId(memberId), MemberDraft(name)))
    }

    @Test fun savedSignatureSurvivesReopenAndCompletesConsent() = runBlocking {
        val id = HouseholdId(HOUSEHOLD_A)
        seed(HOUSEHOLD_A, MEMBER_A, "Maria Santos")
        assertEquals(WriteResult.Saved, repository.saveDraftSection(
            id, "consent", null,
            answers = mapOf<String, AnswerValue>("consent" to AnswerValue.Scalar("Yes"), "signedName" to AnswerValue.Scalar("")),
            expected = emptyMap(),
        ))
        val bitmap = Bitmap.createBitmap(600, 240, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.WHITE)
        for (x in 50..300) bitmap.setPixel(x, 100, android.graphics.Color.BLACK)
        val png = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        bitmap.recycle()
        assertEquals(WriteResult.Saved, PhotoStore(context, mediaRoot).saveSignature(png, id, repository))
        val savedPath = repository.interviewRecord(id)!!.attachments.single().relativePath
        assertTrue("consent holds a drawn mark before the reopen", repository.interviewRecord(id)!!.attachments.any { it.kind == HouseholdRepository.SIGNATURE })

        database.close()
        openStore()

        val reopened = requireNotNull(repository.interviewRecord(id))
        val signature = reopened.attachments.filter { it.kind == HouseholdRepository.SIGNATURE }
        assertEquals("exactly one signature row survives the reopen", 1, signature.size)
        assertEquals(savedPath, signature.single().relativePath)
        assertEquals("image/png", signature.single().mimeType)
        val reopenedFile = File(mediaRoot, savedPath)
        assertTrue("actual signature bytes survive reopen", png.contentEquals(reopenedFile.readBytes()))
        val decoded = requireNotNull(BitmapFactory.decodeFile(reopenedFile.path))
        assertEquals(600, decoded.width)
        assertEquals(240, decoded.height)
        assertEquals(android.graphics.Color.BLACK, decoded.getPixel(100, 100))
        decoded.recycle()

        val consentIssues = InterviewRules.issues(reopened).filter { it.route == "consent" }
        assertTrue("the drawn mark satisfies the typed-name requirement after reopen", consentIssues.isEmpty())
        assertEquals("consent and the populated roster are ready", 2, InterviewRules.progress(reopened).ready)

        record("signature-reopen.txt", buildString {
            appendLine("Database: tala-registry-interview-lifecycle-test.db closed and reopened")
            appendLine("Signature attachments after reopen: ${signature.size} (kind=${signature.single().kind}, mime=${signature.single().mimeType})")
            appendLine("Consent issues after reopen: ${consentIssues.size} (drawn signature accepted without a typed name)")
            appendLine("PNG bytes match; decoded 600x240 image retains the drawn black mark")
            appendLine("Ready sections after reopen: ${InterviewRules.progress(reopened).ready} of ${InterviewRules.progress(reopened).total}")
        })
    }

    @Test fun callbackAndRefusedVisitsFollowTheAgreedRules() = runBlocking {
        val callbackHousehold = HouseholdId(HOUSEHOLD_A)
        val refusedHousehold = HouseholdId(HOUSEHOLD_B)
        seed(HOUSEHOLD_A, MEMBER_A, "Maria Santos")
        seed(HOUSEHOLD_B, MEMBER_B, "Juan Santos")

        // A callback appointment keeps the record open with the appointment stored.
        val visitAnswers: AnswerState = mapOf(
            "visitDate" to AnswerValue.Scalar("2026-10-18"),
            "visitResult" to AnswerValue.Scalar("Callback"),
            "callbackDate" to AnswerValue.Scalar("2026-10-20T10:30"),
        )
        assertEquals(WriteResult.Saved, repository.saveDraftSection(callbackHousehold, "visit", null, visitAnswers, emptyMap()))
        assertEquals(WriteResult.Saved, repository.recordVisit(callbackHousehold, visitAnswers))
        assertEquals(WriteResult.Saved, repository.recordVisit(callbackHousehold, visitAnswers))
        assertEquals(HouseholdStatus.Callback, HouseholdStatus.entries.first { it.name == repository.interviewRecord(callbackHousehold)!!.household.status })

        // A refusal stays saved as a draft: the record keeps its in-progress status and
        // the review rules keep naming the refusal instead of granting completion.
        assertEquals(WriteResult.Saved, repository.saveDraftSection(refusedHousehold, "consent", null,
            mapOf("consent" to AnswerValue.Scalar("No")), emptyMap()))
        val refusedBefore = requireNotNull(repository.interviewRecord(refusedHousehold))
        assertEquals(HouseholdStatus.InProgress, HouseholdStatus.entries.first { it.name == refusedBefore.household.status })
        val refusalIssues = InterviewRules.issues(refusedBefore).filter { it.route == "consent" }
        assertTrue("the refusal names its rule", refusalIssues.single().errors.values.single().contains("Refusal remains saved as a draft"))

        database.close()
        openStore()

        val callbackAfter = requireNotNull(repository.interviewRecord(callbackHousehold))
        val refusedAfter = requireNotNull(repository.interviewRecord(refusedHousehold))
        assertEquals("the callback badge survives the reopen", HouseholdStatus.Callback,
            HouseholdStatus.entries.first { it.name == callbackAfter.household.status })
        assertEquals("2026-10-20T10:30", InterviewRules.answers(callbackAfter, QuestionnaireSections.get("visit")).scalar("callbackDate"))
        assertEquals("retry does not duplicate visit history", 1, callbackAfter.visits.size)
        assertEquals("2026-10-18", callbackAfter.visits.single().visitedOn)
        assertEquals("the refusal stays a draft after the reopen", HouseholdStatus.InProgress,
            HouseholdStatus.entries.first { it.name == refusedAfter.household.status })
        assertEquals("the populated roster remains ready during refusal", 1, InterviewRules.progress(refusedAfter).ready)
        assertTrue(repository.reviewInterview(refusedHousehold) is WriteResult.Failed)
        assertNotEquals("Reviewed", refusedAfter.household.status)

        record("visit-outcomes-reopen.txt", buildString {
            appendLine("Database: tala-registry-interview-lifecycle-test.db closed and reopened")
            appendLine("Callback household: status=${callbackAfter.household.status}, callbackDate=${InterviewRules.answers(callbackAfter, QuestionnaireSections.get("visit")).scalar("callbackDate")}")
            appendLine("Dated visit history survives reopen; repeated Record visit action retains one entry")
            appendLine("Refused household: status=${refusedAfter.household.status}, ready=${InterviewRules.progress(refusedAfter).ready} of ${InterviewRules.progress(refusedAfter).total}")
            appendLine("Refusal issue: ${refusalIssues.single().errors.values.single()}")
        })
    }

    @Test fun completedInterviewIsReviewedLocallyAndReopens() = runBlocking {
        val id = HouseholdId(HOUSEHOLD_A)
        CompleteInterviewFixture.seed(repository, id, MemberId(MEMBER_A))
        assertTrue(InterviewRules.issues(repository.interviewRecord(id)!!).isEmpty())
        assertEquals(18, InterviewRules.progress(repository.interviewRecord(id)!!).ready)
        assertEquals(WriteResult.Saved, repository.reviewInterview(id))
        database.close()
        openStore()
        assertEquals("Reviewed", repository.interviewRecord(id)!!.household.status)
        record("completed-interview-reopen.txt", "All 18 sections validate; explicit local review saved Reviewed; status survives Room reopen.\n")
    }

    @Test fun signatureOrphansAreRecoveredWithoutDeletingLiveMarks() = runBlocking {
        val id = HouseholdId(HOUSEHOLD_A)
        seed(HOUSEHOLD_A, MEMBER_A, "Maria Santos")
        val folder = File(mediaRoot, "attachments").apply { mkdirs() }
        val orphan = File(folder, "00000000-0000-4000-8000-000000000001.png").apply { writeBytes(byteArrayOf(1)) }
        val live = File(folder, "00000000-0000-4000-8000-000000000002.png").apply { writeBytes(byteArrayOf(2)) }
        assertEquals(WriteResult.Saved, PhotoStore(context, mediaRoot).cleanupUnreferenced { setOf("attachments/${live.name}") })
        assertTrue(!orphan.exists())
        assertTrue(live.exists())
    }
}
