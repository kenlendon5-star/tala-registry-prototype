package ph.tala.registry.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.content.ContentValues
import android.net.Uri
import android.os.SystemClock
import android.provider.MediaStore
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.After
import org.junit.Test
import org.junit.Assert.*
import ph.tala.registry.MainActivity
import ph.tala.registry.TalaApplication
import ph.tala.registry.data.local.WriteResult
import ph.tala.registry.domain.form.*
import ph.tala.registry.domain.model.*
import kotlinx.coroutines.flow.first
import java.io.File
import java.util.UUID
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RegistrationUiDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app get() = context.applicationContext as TalaApplication
    private val fixtures = mutableListOf<Uri>()
    @After fun removeOnlyGeneratedPickerImages() {
        fixtures.forEach { context.contentResolver.delete(it, null, null) }
    }
    private fun pickerImage(): Pair<Uri, String> {
        val taken = System.currentTimeMillis() + 300_000
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "tala-day08-${UUID.randomUUID()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/TalaDay08Tests")
            put(MediaStore.Images.Media.DATE_TAKEN, taken)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = checkNotNull(context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
        fixtures += uri
        val bitmap = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.GREEN) }
        try {
            context.contentResolver.openOutputStream(uri)!!.use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally { bitmap.recycle() }
        context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        val storedTaken = context.contentResolver.query(uri, arrayOf(MediaStore.Images.Media.DATE_TAKEN, MediaStore.Images.Media.DATE_MODIFIED), null, null, null)!!.use {
            check(it.moveToFirst())
            it.getLong(0).takeIf { value -> value > 0 } ?: it.getLong(1) * 1_000
        }
        println("DAY08 generated picker fixture: requested=$taken stored=$storedTaken locale=${Locale.getDefault()}")
        return uri to SimpleDateFormat("MMM d, yyyy h:mm a", Locale.US).format(Date(storedTaken))
    }
    private fun findPhoto(node: AccessibilityNodeInfo?, date: String): AccessibilityNodeInfo? {
        if (node == null) return null
        val description = node.contentDescription?.toString().orEmpty()
        if (description.startsWith("Photo taken on ") && description.contains(date)) return node
        for (index in 0 until node.childCount) findPhoto(node.getChild(index), date)?.let { return it }
        return null
    }
    private fun findConfirmation(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        val label = (node.text ?: node.contentDescription)?.toString().orEmpty()
        if (label == "Add" || label.startsWith("Add (") || label == "Done" || label == "Select" || label == "Use photo") return node
        for (index in 0 until node.childCount) findConfirmation(node.getChild(index))?.let { return it }
        return null
    }
    private fun tap(node: AccessibilityNodeInfo) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val bounds = Rect().also(node::getBoundsInScreen)
        val time = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(time, SystemClock.uptimeMillis(), action, bounds.exactCenterX(), bounds.exactCenterY(), 0)
            try { assertTrue(automation.injectInputEvent(event, true)) } finally { event.recycle() }
        }
    }
    private fun chooseFixture(button: String, date: String) {
        compose.onNodeWithTag(button).performScrollTo().performClick()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        var photo: AccessibilityNodeInfo? = null
        try {
            compose.waitUntil(15_000) {
                val root = automation.rootInActiveWindow
                if (root?.packageName?.toString() == "com.google.android.photopicker") photo = findPhoto(root, date)
                photo != null
            }
        } catch (failure: Exception) {
            println("DAY08 picker fixture not found: date=$date package=${automation.rootInActiveWindow?.packageName}")
            throw failure
        }
        tap(photo!!)
        var confirmed = false
        compose.waitUntil(15_000) {
            val root = automation.rootInActiveWindow
            if (root?.packageName?.toString() == context.packageName) true
            else {
                // This phone's current picker requires Add after selecting a thumbnail.
                if (!confirmed) findConfirmation(root)?.let { tap(it); confirmed = true }
                false
            }
        }
    }
    private fun assertFixtureCopy(path: String) {
        val image = checkNotNull(BitmapFactory.decodeFile(File(context.filesDir, path).path))
        try {
            val color = image.getPixel(image.width / 2, image.height / 2)
            assertTrue(android.graphics.Color.green(color) > 240)
            assertTrue(android.graphics.Color.red(color) < 15 && android.graphics.Color.blue(color) < 15)
        } finally { image.recycle() }
    }
    private fun await(tag: String) = compose.waitUntil(30_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    private fun awaitHomeFixtures() {
        await("register-household")
        compose.onNodeWithTag("nav-Households").performClick()
        await("household-HH-00452")
        compose.onNodeWithTag("nav-Home").performClick()
    }
    private fun saved() = compose.waitUntil(30_000) {
        compose.onAllNodesWithTag("save-status").fetchSemanticsNodes().any {
            it.config[androidx.compose.ui.semantics.SemanticsProperties.Text].any { text -> text.text.contains("saved", true) }
        }
    }

    @Test fun dashboardAfterRestartDisplaysPersistedCounts() {
        check(context.packageName.endsWith(".d08test"))
        awaitHomeFixtures()
        val expected = runBlocking {
            RegistryUiState(loading = false, households = app.repository.households.first().filterNot { it.archived }, memberSex = app.repository.memberSex.first())
        }
        for ((label, count) in listOf("Households" to expected.households.size, "Residents" to expected.memberCount,
            "Male" to expected.maleCount, "Female" to expected.femaleCount, "Sex Not Recorded" to expected.unrecordedSexCount)) {
            val tag = "metric-$label"
            compose.onNodeWithTag("home-screen", useUnmergedTree = true).performScrollToNode(hasTestTag(tag))
            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().any {
                    it.config[androidx.compose.ui.semantics.SemanticsProperties.Text].any { text -> text.text == count.toString() }
                }
            }
            compose.onNodeWithTag(tag, useUnmergedTree = true).assertTextEquals(count.toString()).assertIsDisplayed()
        }
    }

    @Test fun registrationNameDetailsPicturesAndProfileSurviveActivityRecreation() {
        check(context.packageName.endsWith(".d08test"))
        awaitHomeFixtures()
        compose.onNodeWithTag("nav-Households").performClick()
        compose.onNodeWithTag("add-household").performScrollTo().performClick()
        compose.onNodeWithTag("field-headName").performTextInput("UI Registration Family")
        val address = "UI Fictional Street ${UUID.randomUUID()}"
        compose.onNodeWithTag("field-address").performTextInput(address)
        compose.onNodeWithTag("save-household").performClick(); saved()
        compose.onNodeWithTag("edit-name-details").performScrollTo().performClick()
        await("interview-workspace")
        compose.onNodeWithTag("field-firstName").performScrollTo().performTextReplacement("Ana")
        compose.onNodeWithTag("field-lastName").performScrollTo().performTextReplacement("Reyes")
        compose.onNodeWithText("Save Draft").performClick(); saved()
        val household = runBlocking { app.repository.households.first().single { it.headName == "Ana Reyes" && it.address == address } }
        val (_, date) = pickerImage()
        chooseFixture("photo-choose", date)
        compose.waitUntil(15_000) { runBlocking { app.repository.interviewRecord(household.id)!!.attachments.isNotEmpty() } }
        val firstPhoto = runBlocking { app.repository.interviewRecord(household.id)!!.attachments.single().relativePath }
        assertFixtureCopy(firstPhoto)
        chooseFixture("photo-choose", date)
        compose.waitUntil(15_000) {
            runBlocking { app.repository.interviewRecord(household.id)!!.attachments.single().relativePath != firstPhoto } && !File(context.filesDir, firstPhoto).exists()
        }
        assertFalse(File(context.filesDir, firstPhoto).exists())
        compose.onNodeWithTag("photo-remove").performScrollTo().performClick()
        compose.waitUntil(15_000) { runBlocking { app.repository.interviewRecord(household.id)!!.attachments.isEmpty() } }
        chooseFixture("photo-choose", date)
        compose.waitUntil(15_000) { runBlocking { app.repository.interviewRecord(household.id)!!.attachments.isNotEmpty() } }
        compose.activityRule.scenario.recreate()
        await("interview-workspace")
        compose.onNodeWithTag("photo-choose").performScrollTo()
        await("photo-preview")
        compose.onNodeWithTag("photo-preview").performScrollTo().assertIsDisplayed()
        // Exercise the real platform Photo Picker; cancellation must retain the saved attachment.
        compose.onNodeWithTag("photo-choose").performScrollTo().performClick()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        compose.waitUntil(15_000) { automation.rootInActiveWindow?.packageName?.toString() == "com.google.android.photopicker" }
        automation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
        await("interview-workspace")
        compose.onNodeWithText("Save & Back").performClick()
        await("household-edit-screen")
        compose.onNodeWithTag("field-headName").assertTextContains("Ana Reyes")
        compose.onNodeWithTag("edit-name-details").performScrollTo().performClick()
        await("interview-workspace")
        compose.onNodeWithTag("field-lastName").performScrollTo().performTextReplacement("Cruz")
        compose.onNodeWithText("Save Draft").performClick(); saved()
        compose.onNodeWithText("Save & Back").performClick()
        compose.onNodeWithTag("field-headName").assertTextContains("Ana Cruz")
        runBlocking {
            val record = app.repository.interviewRecord(household.id)!!
            assertEquals("Ana Cruz", record.household.headName)
            assertEquals("Cruz", InterviewRules.answers(record, QuestionnaireSections.household).scalar("lastName"))
            assertEquals(1, record.attachments.size)
        }
        compose.onNodeWithContentDescription("Back", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("nav-Home").performClick()
        compose.onNodeWithTag("nav-Profile").performClick()
        await("profile-screen")
        compose.onNodeWithTag("field-profileName").performTextReplacement("UI Enumerator")
        compose.onNodeWithTag("save-profile").performScrollTo().performClick(); saved()
        val oldProfilePhoto = runBlocking { app.profiles.profile.first().photoRelativePath }
        chooseFixture("profile-photo-choose", date)
        compose.waitUntil(15_000) {
            val current = runBlocking { app.profiles.profile.first().photoRelativePath }
            current != null && current != oldProfilePhoto && (oldProfilePhoto == null || !File(context.filesDir, oldProfilePhoto).exists())
        }
        val profilePhoto = runBlocking { app.profiles.profile.first().photoRelativePath!! }
        assertFixtureCopy(profilePhoto)
        compose.onNodeWithTag("profile-photo-remove").performScrollTo().performClick()
        compose.waitUntil(15_000) { runBlocking { app.profiles.profile.first().photoRelativePath == null } }
        assertFalse(File(context.filesDir, profilePhoto).exists())
        chooseFixture("profile-photo-choose", date)
        compose.waitUntil(15_000) { runBlocking { app.profiles.profile.first().photoRelativePath != null } }
        compose.activityRule.scenario.recreate()
        await("profile-screen")
        compose.onNodeWithTag("field-profileName").assertTextContains("UI Enumerator")
        compose.onNodeWithTag("photo-preview").performScrollTo().assertIsDisplayed()
    }
}
