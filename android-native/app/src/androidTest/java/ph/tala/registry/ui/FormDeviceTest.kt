package ph.tala.registry.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import ph.tala.registry.MainActivity
import java.io.File
import java.time.LocalDate

class FormDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before fun openPractice() {
        compose.onNodeWithTag("home-screen").performScrollToNode(hasTestTag("household-HH-00452"))
        compose.onNodeWithTag("household-HH-00452").performClick()
        compose.onNodeWithTag("interview-screen").performScrollToNode(hasTestTag("open-practice"))
        compose.onNodeWithTag("open-practice").performClick()
        compose.onNodeWithTag("practice-screen").assertIsDisplayed()
    }

    private fun input(key: String, value: String) {
        compose.onNodeWithTag("field-$key").performScrollTo().performClick().performTextReplacement(value)
    }

    private fun option(key: String, value: String) {
        compose.onNodeWithTag("option-$key-$value").performScrollTo().performClick()
    }

    private fun scope(id: String) {
        compose.onNodeWithTag("answer-scope").performClick()
        compose.onNodeWithTag("scope-$id").performClick()
    }

    private fun fillRequired() {
        input("firstName", "Ana")
        input("dob", "2000-02-29")
        input("callbackDate", "2026-10-20T09:30")
        option("worked", "Yes")
        input("occupation", "Teacher")
        compose.onNodeWithTag("field-employmentType").performScrollTo().performClick()
        compose.onNodeWithTag("option-employmentType-Employee").performClick()
        input("hours", "40")
        option("accounts", "Bank")
    }

    @Test fun allControlsValidateAndHiddenRequiredAnswersAreCleared() {
        fillRequired()
        input("phone", "09171234567")
        // Moving focus formats a valid local number; typing remains uninterrupted.
        compose.onNodeWithTag("field-dob").performScrollTo().performClick()
        compose.onNodeWithTag("field-phone").assertTextContains("0917-123-4567")
        input("visitNotes", "First line\nSecond line")
        compose.onNodeWithTag("check-answers").performClick()
        compose.onNodeWithTag("form-result").assertTextContains("Answers are valid", substring = true)
        capture("01-valid-form")
        option("worked", "No")
        compose.onNodeWithTag("field-hours").assertDoesNotExist()
        compose.onNodeWithTag("field-occupation").assertDoesNotExist()
        compose.onNodeWithTag("field-employmentType").assertDoesNotExist()
        compose.onNodeWithTag("check-answers").performClick()
        compose.onNodeWithTag("form-result").assertTextContains("Answers are valid", substring = true)
        option("worked", "Yes")
        compose.onNodeWithTag("check-answers").performClick()
        compose.onNodeWithTag("field-occupation").assertIsFocused()
        compose.onNodeWithTag("hint-occupation", useUnmergedTree = true).assertTextEquals("Choose or enter an answer.")
        capture("02-branch-cleared")
    }

    @Test fun errorsScrollAndFocusFirstInvalidFieldIncludingNumericLimits() {
        compose.onNodeWithTag("field-visitNotes").performScrollTo()
        compose.onNodeWithTag("check-answers").performClick()
        compose.onNodeWithTag("field-firstName").assertIsFocused().assertIsDisplayed()
        capture("03-first-error")
        fillRequired()
        input("hours", "169")
        compose.onNodeWithTag("field-visitNotes").performScrollTo()
        compose.onNodeWithTag("check-answers").performClick()
        compose.onNodeWithTag("field-hours").assertIsFocused().assertIsDisplayed()
        compose.onNodeWithTag("hint-hours", useUnmergedTree = true).assertTextEquals("Enter no more than 168.")
        input("hours", "168")
        input("dob", "2025-02-29")
        compose.onNodeWithTag("check-answers").performClick()
        compose.onNodeWithTag("field-dob").assertIsFocused()
        compose.onNodeWithTag("hint-dob", useUnmergedTree = true).assertTextEquals("Use a valid date: YYYY-MM-DD.")
    }

    @Test fun exclusiveMultiSelectAndOptionalFieldsWork() {
        fillRequired()
        option("accounts", "E-money")
        option("accounts", "None")
        compose.onNodeWithTag("option-accounts-Bank").assertIsOff()
        compose.onNodeWithTag("option-accounts-E-money").assertIsOff()
        compose.onNodeWithTag("option-accounts-None").assertIsOn()
        option("accounts", "Bank")
        compose.onNodeWithTag("option-accounts-None").assertIsOff()
        compose.onNodeWithTag("check-answers").performClick()
        compose.onNodeWithTag("form-result").assertTextContains("Answers are valid", substring = true)
    }

    @Test fun householdAndTwoMembersKeepDistinctDraftsAcrossNavigationAndRecreation() {
        input("firstName", "Household respondent")
        scope("DEMO-SANTOS-1")
        input("firstName", "Maria")
        scope("DEMO-SANTOS-2")
        input("firstName", "Juan")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("answer-scope").assertTextContains("Answering for: Juan Santos")
        compose.onNodeWithTag("field-firstName").assertTextContains("Juan")
        scope("DEMO-SANTOS-1")
        compose.onNodeWithTag("field-firstName").assertTextContains("Maria")
        capture("04-member-scope")
        scope("household")
        compose.onNodeWithTag("field-firstName").assertTextContains("Household respondent")
        // Toolbar Back avoids ambiguity about whether the IME is open.
        compose.onNodeWithText("Back", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("interview-screen").performScrollToNode(hasTestTag("open-practice"))
        compose.onNodeWithTag("open-practice").performClick()
        compose.onNodeWithTag("field-firstName").assertTextContains("Household respondent")
    }

    @Test fun calendarAndTimePickersCommitOnlyOnConfirmation() {
        input("dob", "2000-01-02")
        compose.onNodeWithTag("pick-dob").performClick()
        compose.onNodeWithText("Cancel", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("field-dob").assertTextContains("2000-01-02")
        compose.onNodeWithTag("pick-dob").performClick()
        compose.onNodeWithText("Use date", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("field-dob").assertTextContains("2000-01-02")
        compose.onNodeWithTag("pick-callbackDate").performScrollTo().performClick()
        compose.onNodeWithText("Choose time", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Use appointment", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("field-callbackDate").assertTextContains("${LocalDate.now()}T09:00")
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "day04").apply { mkdirs() }
        // Capture the app window only, excluding unrelated phone overlays and notifications.
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
