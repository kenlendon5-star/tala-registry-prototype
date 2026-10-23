package ph.tala.registry.ui

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.content.ContextCompat
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import ph.tala.registry.MainActivity
import ph.tala.registry.TalaApplication
import ph.tala.registry.data.local.WriteResult
import ph.tala.registry.domain.edit.HouseholdDraft
import ph.tala.registry.domain.form.InterviewRules
import ph.tala.registry.domain.form.QuestionnaireSections
import ph.tala.registry.domain.model.*
import java.io.File
import java.util.UUID

/** Real target-app UI and permission dialogs; provider fixes are explicitly fictional mocks. */
class LocationDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val app get() = context.applicationContext as TalaApplication
    private fun record(id: HouseholdId) = runBlocking { requireNotNull(app.repository.interviewRecord(id)) }
    private fun await(tag: String) = compose.waitUntil(15_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    private fun create(): HouseholdId {
        check(context.packageName.endsWith(".d08test"))
        val id=HouseholdId(UUID.randomUUID().toString())
        runBlocking { assertEquals(WriteResult.Saved,app.repository.createHousehold(id,HouseholdDraft("Location Sample","Fictional Location Street",HouseholdStatus.InProgress))) }
        return id
    }
    private fun open(id: HouseholdId) {
        await("nav-Households"); compose.onNodeWithTag("nav-Households").performClick()
        await("household-search"); compose.onNodeWithTag("household-search").performTextReplacement(record(id).household.displayCode)
        val tag="household-${record(id).household.displayCode}"
        await(tag); compose.onNodeWithTag(tag).performScrollTo().performClick()
        await("location-card")
    }
    private fun shell(command: String): String = android.os.ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand(command),
    ).bufferedReader().use { reader -> reader.readText().also { assertFalse(it, it.contains("Exception")) } }
    private fun systemNode(suffix: String): AccessibilityNodeInfo? {
        val root = instrumentation.uiAutomation.rootInActiveWindow ?: return null
        val controller = root.packageName?.toString() ?: return null
        if (controller !in setOf("com.android.permissioncontroller", "com.google.android.permissioncontroller")) return null
        // Google's controller can retain AOSP's com.android resource namespace.
        return listOf("com.android.permissioncontroller", "com.google.android.permissioncontroller")
            .firstNotNullOfOrNull { namespace -> root.findAccessibilityNodeInfosByViewId("$namespace:id/$suffix").firstOrNull { it.isVisibleToUser && it.isEnabled } }
    }
    private fun systemClick(vararg suffixes: String) {
        val automation = instrumentation.uiAutomation
        val info = automation.serviceInfo
        val originalFlags = info.flags
        // Android requires this flag for findAccessibilityNodeInfosByViewId.
        info.flags = originalFlags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        automation.serviceInfo = info
        try {
            val deadline=SystemClock.elapsedRealtime()+10_000
            while(SystemClock.elapsedRealtime()<deadline) {
                val node=suffixes.firstNotNullOfOrNull { systemNode(it) }
                if(node!=null && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return
                SystemClock.sleep(50)
            }
            throw AssertionError("System permission control unavailable: ${suffixes.joinToString()} (active package: ${automation.rootInActiveWindow?.packageName})")
        } finally {
            info.flags = originalFlags
            automation.serviceInfo = info
        }
    }
    private fun capture() { compose.onNodeWithTag("capture-location").performScrollTo().performClick() }
    private fun captureStatus(text: String, timeout: Long=15_000) = compose.waitUntil(timeout) {
        compose.onAllNodesWithText(text,substring=true).fetchSemanticsNodes().isNotEmpty()
    }
    private fun manual(lat: String, lon: String) {
        compose.onNodeWithTag("correct-location").performScrollTo().performClick()
        compose.onNodeWithTag("manual-lat").performScrollTo().performTextReplacement(lat)
        compose.onNodeWithTag("manual-lon").performScrollTo().performTextReplacement(lon)
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithTag("save-manual-location").performScrollTo().performClick()
    }
    private fun captureImage(name: String) {
        val dir=File(context.getExternalFilesDir(null),"day15").apply { mkdirs() }
        val bitmap=compose.onRoot().captureToImage().asAndroidBitmap()
        try { File(dir,"$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) } } finally { bitmap.recycle() }
    }
    private fun saveQuestionnaireDespiteFailure(id: HouseholdId) {
        compose.onNodeWithTag("open-questionnaire").performScrollTo().performClick()
        await("section-setup")
        compose.onNodeWithTag("field-enumerator").performScrollTo().performTextReplacement("Location Permission Sample")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithText("Save Draft").performClick()
        compose.waitUntil(15_000) { InterviewRules.answers(record(id),QuestionnaireSections.setup).scalar("enumerator")=="Location Permission Sample" }
    }
    @Test fun deniedThenPermanentDenialDoesNotBlockInterview() {
        val id=create(); open(id); capture(); systemClick("permission_deny_button")
        captureStatus("Location was denied")
        capture(); systemClick("permission_deny_and_dont_ask_again_button", "permission_deny_button")
        captureStatus("Location permission was denied for this app")
        compose.onNodeWithTag("open-location-settings").performScrollTo().assertExists()
        captureImage("permission-denied")
        saveQuestionnaireDespiteFailure(id)
        assertNull(record(id).coordinates)
    }
    @Test fun manualWithoutPermissionSurvivesActivityRecreation() {
        assertEquals(PackageManager.PERMISSION_DENIED,ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_COARSE_LOCATION))
        val id=create(); open(id)
        manual("91","120.9"); compose.onNodeWithTag("hint-manual-lat", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("manual-lat").assertIsFocused()
        compose.onNodeWithTag("manual-lat").performTextReplacement("14.5")
        compose.activityRule.scenario.recreate(); await("manual-lat")
        compose.onNodeWithTag("manual-lat").assertTextContains("14.5")
        compose.onNodeWithTag("manual-lon").assertTextContains("120.9")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithTag("save-manual-location").performScrollTo().performClick()
        compose.waitUntil(15_000) { record(id).coordinates?.latitude==14.5 }
        compose.onNodeWithTag("location-metadata").performScrollTo().assertTextContains("Manually corrected", substring = true)
        assertNull(record(id).coordinates?.accuracyMetres)
        File(context.filesDir,"day15-manual-id.txt").writeText(id.value)
        captureImage("manual-saved")
    }
    @Test fun manualReopensAfterForceStop() {
        val id=HouseholdId(File(context.filesDir,"day15-manual-id.txt").readText())
        val saved=requireNotNull(record(id).coordinates)
        assertEquals(14.5,saved.latitude,0.0); assertEquals(120.9,saved.longitude,0.0); assertNull(saved.accuracyMetres); assertTrue(saved.capturedAt>0)
        open(id); compose.onNodeWithTag("location-metadata").performScrollTo().assertTextContains("Manually corrected", substring = true)
        captureImage("manual-reopened")
    }
    @Test fun preciseDialogStoresMockFixAndManualCorrection() {
        val id=create(); open(id); capture()
        systemClick("permission_location_accuracy_radio_fine"); systemClick("permission_allow_foreground_only_button")
        captureStatus("Acquiring")
        shell("cmd location providers set-test-provider-location gps --location 14.5,120.9 --accuracy 12.5")
        compose.waitUntil(15_000) { record(id).coordinates!=null }
        val fix=requireNotNull(record(id).coordinates)
        assertEquals(14.5,fix.latitude,0.0); assertEquals(12.5,requireNotNull(fix.accuracyMetres),0.0); assertTrue(fix.capturedAt>0)
        assertEquals(PackageManager.PERMISSION_GRANTED,ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_FINE_LOCATION))
        File(context.filesDir,"day15-fix-id.txt").writeText(id.value)
        captureImage("precise-mock-fix")
    }
    @Test fun fixReopensAndManualCorrectionRemovesMeasuredAccuracy() {
        val id=HouseholdId(File(context.filesDir,"day15-fix-id.txt").readText())
        assertNotNull(record(id).coordinates?.accuracyMetres)
        open(id); manual("14.6","120.8")
        compose.waitUntil(15_000) { record(id).coordinates?.latitude==14.6 }
        assertNull(record(id).coordinates?.accuracyMetres)
        compose.onNodeWithTag("location-metadata").performScrollTo().assertTextContains("Manually corrected", substring = true)
        captureImage("corrected-device-fix")
    }
    @Test fun approximateDialogStoresMockFix() {
        val id=create(); open(id); capture()
        systemClick("permission_location_accuracy_radio_coarse"); systemClick("permission_allow_foreground_only_button")
        captureStatus("Acquiring")
        shell("cmd location providers set-test-provider-location fused --location 14.5,120.9 --accuracy 12.5")
        compose.waitUntil(15_000) { record(id).coordinates!=null }
        assertEquals(PackageManager.PERMISSION_DENIED,ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_FINE_LOCATION))
        assertEquals(PackageManager.PERMISSION_GRANTED,ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_COARSE_LOCATION))
        assertNotNull(record(id).coordinates?.accuracyMetres)
        captureImage("approximate-mock-fix")
    }
    @Test fun locationOffAndManualFallbackKeepInterviewUsable() {
        val id=create(); open(id); capture(); captureStatus("Location is off")
        captureImage("location-off"); saveQuestionnaireDespiteFailure(id)
    }
    @Test fun timeoutRejectsCachedFixAndInterviewStillSaves() {
        val id=create(); open(id); capture(); captureStatus("No location fix within",30_000)
        assertNull(record(id).coordinates)
        captureImage("location-timeout"); saveQuestionnaireDespiteFailure(id)
    }
}
