package ph.tala.registry.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import ph.tala.registry.MainActivity
import ph.tala.registry.TalaApplication
import ph.tala.registry.data.HouseholdRepository
import ph.tala.registry.data.local.WriteResult
import ph.tala.registry.domain.edit.*
import ph.tala.registry.domain.form.*
import ph.tala.registry.domain.model.*
import ph.tala.registry.testing.CompleteInterviewFixture

/** Real UI + app-private PNG + Room. Write/restart methods run in separate invocations. */
class InterviewLifecycleDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app get() = context.applicationContext as TalaApplication
    private val complete = HouseholdId("d1300000-0000-4000-8000-000000000131")
    private val member = MemberId("d1300000-0000-4000-8000-000000000132")
    private val callback = HouseholdId(UUID.randomUUID().toString())
    private fun await(tag: String) = compose.waitUntil(30_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    private fun record(id: HouseholdId) = runBlocking { requireNotNull(app.repository.interviewRecord(id)) }
    private fun open(id: HouseholdId) {
        check(context.packageName.endsWith(".d08test"))
        await("nav-Households")
        compose.onNodeWithTag("nav-Households").performClick()
        await("household-search")
        compose.onNodeWithText("All", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithTag("household-search").performScrollTo().performTextReplacement(record(id).household.displayCode)
        val tag = "household-${record(id).household.displayCode}"
        await(tag)
        compose.onNodeWithTag(tag).performScrollTo().performClick()
        await("open-questionnaire")
        compose.onNodeWithTag("open-questionnaire").performScrollTo().performClick()
        await("section-setup")
    }
    private fun section(route: String) {
        compose.onNodeWithTag("section-$route").performScrollTo().performClick()
        compose.waitUntil(30_000) { compose.onAllNodes(hasTestTag("section-$route") and isSelected()).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun option(key: String, value: String) = compose.onNodeWithTag("option-$key-$value").performScrollTo().performClick()
    private fun select(key: String, value: String) {
        compose.onNodeWithTag("field-$key").performScrollTo().performClick()
        option(key, value)
    }
    private fun save() {
        compose.onNodeWithText("Save Draft").performClick()
        compose.waitUntil(30_000) { compose.onAllNodesWithText("Saved on this device.").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun recordVisit() {
        compose.onNodeWithText("Record Visit Result").performScrollTo().performClick()
        compose.waitUntil(30_000) { compose.onAllNodesWithText("Visit result recorded on this device.").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun review() {
        compose.onNodeWithText("Review Interview").performClick()
        await("review-progress")
    }
    private fun awaitSignature(present: Boolean) = compose.waitUntil(30_000) {
        record(complete).attachments.any { it.kind == HouseholdRepository.SIGNATURE } == present
    }
    private fun assertSignatureState(saved: Boolean) {
        val state = if (saved) "Signature saved" else "No signature saved"
        val matcher = hasTestTag("signature-pad") and
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, state)
        compose.waitUntil(30_000) { compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(matcher).assertExists()
    }
    private fun draw() {
        compose.onNodeWithTag("signature-pad").performScrollTo().performTouchInput {
            swipe(Offset(width * .15f, height * .6f), Offset(width * .8f, height * .25f), 400)
        }
        awaitSignature(true)
        assertSignatureState(true)
    }
    private fun assertPng() {
        val path = record(complete).attachments.single { it.kind == HouseholdRepository.SIGNATURE }.relativePath
        val image = requireNotNull(BitmapFactory.decodeFile(File(context.filesDir, path).path))
        try {
            assertEquals(600, image.width); assertEquals(240, image.height)
            val pixels = IntArray(image.width * image.height)
            image.getPixels(pixels, 0, image.width, 0, 0, image.width, image.height)
            assertTrue("the saved PNG contains a visible stroke", pixels.any { it != android.graphics.Color.WHITE })
        } finally { image.recycle() }
    }
    private fun capture(name: String) {
        val folder = File(context.getExternalFilesDir(null), "day13").apply { mkdirs() }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        try { File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
    }

    @Test fun completeThroughReviewWithDrawUndoRedoAndClear() {
        check(context.packageName.endsWith(".d08test"))
        runBlocking {
            app.photos.removeSignature(complete, app.repository)
            CompleteInterviewFixture.seed(app.repository, complete, member, includeConsent = false)
        }
        open(complete)
        section("visit"); recordVisit()
        review()
        compose.onNodeWithText("17 of 18 sections ready. This records local completion only.", useUnmergedTree = true).performScrollTo().assertExists()
        compose.onNodeWithText("Mark Interview Reviewed").performScrollTo().assertIsNotEnabled()
        // Review jumps to the missing consent form instead of losing the household context.
        compose.onNodeWithText("Consent & review: 1 missing or invalid answers").performScrollTo().performClick()
        await("option-consent-Yes")
        option("consent", "Yes"); save()
        compose.onNodeWithText("Accessible alternative: type a fictional name in the signature field instead of drawing.")
            .performScrollTo().assertIsDisplayed()
        assertSignatureState(false)
        draw()
        val initialPath = record(complete).attachments.single { it.kind == HouseholdRepository.SIGNATURE }.relativePath
        compose.onNodeWithTag("signature-undo").performScrollTo().performClick()
        awaitSignature(false)
        compose.waitUntil(30_000) { !File(context.filesDir, initialPath).exists() }
        assertFalse(File(context.filesDir, initialPath).exists())
        compose.onNodeWithTag("signature-redo").performScrollTo().performClick()
        awaitSignature(true); assertPng()
        compose.onNodeWithTag("signature-clear").performScrollTo().performClick()
        awaitSignature(false)
        assertSignatureState(false)
        draw(); assertPng()
        capture("drawn-signature")
        review()
        compose.onNodeWithText("18 of 18 sections ready. This records local completion only.", useUnmergedTree = true).performScrollTo().assertExists()
        compose.onNodeWithText("Mark Interview Reviewed").performScrollTo().performClick()
        compose.waitUntil(30_000) { record(complete).household.status == "Reviewed" }
        compose.waitUntil(30_000) { compose.onAllNodesWithText("Interview reviewed on this device.").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Interview reviewed on this device.").assertExists()
        capture("interview-reviewed")
    }

    @Test fun signatureAndReviewedStatusSurviveForceStop() {
        assertEquals("Reviewed", record(complete).household.status)
        assertTrue(InterviewRules.issues(record(complete)).isEmpty())
        assertPng()
        open(complete); section("consent")
        compose.waitUntil(30_000) { compose.onAllNodesWithContentDescription("Saved drawn signature").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Saved drawn signature").performScrollTo().assertIsDisplayed()
        capture("signature-after-force-stop")
        // A saved mark can be removed even when the new pad has no in-memory strokes.
        compose.onNodeWithTag("signature-clear").performScrollTo().performClick()
        awaitSignature(false)
        assertEquals("InProgress", record(complete).household.status)
        compose.onNodeWithTag("field-signedName").performScrollTo().performTextReplacement("Maria Sample")
        save()
        review()
        compose.onNodeWithText("Mark Interview Reviewed").performScrollTo().performClick()
        compose.waitUntil(30_000) { record(complete).household.status == "Reviewed" }
    }

    @Test fun callbackNoRespondentRefusalAndPartialResume() {
        check(context.packageName.endsWith(".d08test"))
        runBlocking { assertEquals(WriteResult.Saved, app.repository.createHousehold(callback, HouseholdDraft("Callback Acceptance", "Fictional Callback Street", HouseholdStatus.InProgress))) }
        open(callback); section("visit")
        compose.onNodeWithTag("field-visitDate").performScrollTo().performTextReplacement("2026-10-18")
        select("visitResult", "Callback")
        compose.onNodeWithTag("field-callbackDate").performScrollTo().performTextReplacement("2026-10-20T10:30")
        save(); recordVisit()
        assertEquals("Callback", record(callback).household.status)
        // The UX-03 compact reflow (single-scroll page while the IME is open) scrolls the
        // workspace header out of view, so close the keyboard first — as a user would tap away.
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithText("Save & Back").performClick()
        await("interview-screen")
        compose.onNodeWithContentDescription("Back").performClick()
        await("household-search")
        // Filter chips precede the household cards; the badge also says Callback.
        compose.onAllNodesWithText("Callback", useUnmergedTree = true).onFirst().performScrollTo().performClick()
        compose.onNodeWithTag("household-${record(callback).household.displayCode}").performScrollTo().assertExists()
        compose.onNodeWithTag("household-${record(callback).household.displayCode}").performClick()
        await("open-questionnaire")
        compose.onNodeWithTag("open-questionnaire").performScrollTo().performClick()
        await("section-setup"); section("visit")
        compose.onNodeWithTag("field-callbackDate").performScrollTo().assertTextContains("2026-10-20T10:30")
        select("visitResult", "No respondent"); save(); recordVisit()
        assertEquals("InProgress", record(callback).household.status)
        assertFalse(InterviewRules.answers(record(callback), QuestionnaireSections.visit).containsKey("callbackDate"))
        select("visitResult", "Refused"); save(); recordVisit()
        val history = record(callback).visits
        assertEquals(listOf("Callback", "No respondent", "Refused"), history.map { it.outcome })
        assertEquals("2026-10-20T10:30", history.first().callbackAt)
        assertTrue(history.all { it.visitedOn == "2026-10-18" })
        section("consent"); option("consent", "No"); save()
        review()
        compose.onNodeWithText("Mark Interview Reviewed").performScrollTo().assertIsNotEnabled()
        assertTrue(runBlocking { app.repository.reviewInterview(callback) } is WriteResult.Failed)
        assertEquals("InProgress", record(callback).household.status)
        capture("refused-interview")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithText("Save & Back").performClick()
        await("interview-screen")
        compose.onNodeWithContentDescription("Back").performClick()
        open(callback); section("visit")
        compose.onNodeWithTag("field-visitResult").performScrollTo().assertTextContains("Refused")
        section("consent")
        compose.onNodeWithTag("option-consent-No").performScrollTo().assertIsSelected()
    }
}
