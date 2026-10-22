package ph.tala.registry.ui

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.ComponentActivity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import ph.tala.registry.data.*
import ph.tala.registry.data.local.*
import ph.tala.registry.domain.form.InterviewRules
import ph.tala.registry.domain.form.QuestionnaireSections
import ph.tala.registry.domain.model.*
import ph.tala.registry.ui.home.*
import ph.tala.registry.ui.profile.EnumeratorProfileUiState
import ph.tala.registry.ui.theme.TalaTheme
import java.io.File
import java.util.UUID

/** Fixture renders use the production Home composable; persistence checks use isolated files/Room. */
class HomeDashboardDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun fixture() = RegistryUiState(loading = false, households = listOf(
        Household(HouseholdId("fixture"), "HH-00452", "Maria Santos", "Purok 3", HouseholdStatus.InProgress,
            listOf(Member(MemberId("resident"), HouseholdId("fixture"), "Ana Santos"))),
    ), memberSex = mapOf("resident" to "Female"))

    @Test fun homeActionsCategoriesAndResponsiveLayout() {
        val landscape = InstrumentationRegistry.getArguments().getString("evidenceCase").orEmpty().startsWith("landscape")
        compose.activityRule.scenario.onActivity {
            it.requestedOrientation = if (landscape) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        compose.waitUntil(15_000) {
            runCatching { compose.activity.resources.configuration.orientation ==
                if (landscape) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT }.getOrDefault(false)
        }
        var browse = 0
        var callbacks = 0
        var register = 0
        var profile = 0
        var continued: InterviewResume? = null
        val state = fixture()
        val reference = InterviewResume(state.households.single().id, "demographics", "resident")
        compose.setContent {
            TalaTheme {
                HomeScreen(state, HomeInsights(false, listOf(PurokCount("Purok 3", 1)), reference),
                    EnumeratorProfileUiState(name = "Jamie Dela Cruz", barangay = "Barangay Cogon"),
                    { profile++ }, { continued = it }, { register++ }, { browse++ }, { callbacks++ })
            }
        }
        compose.onNodeWithText("Tala Registry").assertIsDisplayed()
        val viewport = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertEquals("Actual Window Orientation", landscape, viewport.width > viewport.height)
        println("HOME_VIEWPORT width=${viewport.width} height=${viewport.height} fontScale=${compose.activity.resources.configuration.fontScale}")
        capture("home-top")
        compose.onNodeWithText("LOCAL DEMO").assertDoesNotExist()
        compose.onNodeWithText("Saved on this device").assertDoesNotExist()
        compose.onNodeWithText("Quick Actions").assertDoesNotExist()
        compose.onNodeWithText("Small visits.", substring = true).assertDoesNotExist()
        compose.onNodeWithTag("enumerator-row").performClick()
        compose.onNodeWithTag("home-screen").performScrollToNode(hasTestTag("continue-interview"))
        compose.onNodeWithTag("continue-interview").performClick()
        compose.runOnIdle { assertEquals(reference, continued); assertEquals(1, profile) }
        compose.onNodeWithTag("home-screen").performScrollToNode(hasTestTag("register-household"))
        compose.onNodeWithTag("register-household").performClick()
        compose.onNodeWithTag("home-screen").performScrollToNode(hasTestTag("browse-all"))
        compose.onNodeWithTag("browse-all").performClick()
        compose.onNodeWithTag("home-screen").performScrollToNode(hasTestTag("summary-Callbacks"))
        compose.onNodeWithTag("summary-Callbacks").performClick()
        compose.runOnIdle {
            assertEquals("Register Callback", 1, register)
            assertEquals("Browse Callback", 1, browse)
            assertEquals("Callbacks Filter", 1, callbacks)
        }
        compose.onNodeWithTag("home-screen").performScrollToNode(hasTestTag("residents-dashboard"))
        compose.onNodeWithText("Recorded Residents").assertIsDisplayed()
        capture("home-residents")
        compose.onNodeWithTag("home-screen").performScrollToNode(hasTestTag("purok-dashboard"))
        compose.onNodeWithText("Households By Purok").assertIsDisplayed()
        compose.onNodeWithText("Purok 3").assertIsDisplayed()
        capture("home-puroks")
    }

    @Test fun recentInterviewSurvivesStoreRecreation() = runBlocking {
        val file = File(context.cacheDir, "home-resume-${UUID.randomUUID()}.preferences_pb")
        val reference = InterviewResume(HouseholdId(UUID.randomUUID().toString()), "demographics", UUID.randomUUID().toString())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val first = DataStoreInterviewResumeStore(PreferenceDataStoreFactory.create(scope = scope) { file })
        try {
            first.remember(reference)
            assertEquals(reference, first.recent.first())
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin() }
        val nextScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val reopened = DataStoreInterviewResumeStore(PreferenceDataStoreFactory.create(scope = nextScope) { file })
            assertEquals(reference, reopened.recent.first())
        } finally { nextScope.coroutineContext[Job]!!.cancelAndJoin(); file.delete() }
    }

    @Test fun roomPurokQueryUpdatesAfterRealSectionWrite() = runBlocking {
        val database = RegistryDatabase.openInMemoryForHomeTest()
        // See the companion extension below: no normal application database is opened.
        try {
            val repository = RoomHouseholdRepository(RoomRecordStore(database))
            val records = DemoFixtures.records()
            assertEquals(WriteResult.Saved, repository.seedMissing(records))
            val id = HouseholdId(records.first().household.id)
            val stored = repository.interviewRecord(id)!!
            val base = InterviewRules.answers(stored, QuestionnaireSections.setup)
            assertEquals(WriteResult.Saved, repository.saveDraftSection(id, "setup", null,
                base + ("purok" to AnswerValue.Scalar("Purok 7")), base))
            assertEquals("Purok 7", repository.householdPuroks.first()[id.value])
        } finally { database.close() }
    }

    private fun capture(name: String) {
        val scenario = InstrumentationRegistry.getArguments().getString("evidenceCase") ?: "default"
        val out = File(context.getExternalFilesDir(null), "home-redesign/$scenario").apply { mkdirs() }
        compose.waitForIdle()
        // Semantics can settle before the emulator's compositor presents that frame.
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        android.os.SystemClock.sleep(600)
        val bitmap = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        try {
            File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { bitmap.recycle() }
    }

    private fun RegistryDatabase.Companion.openInMemoryForHomeTest(): RegistryDatabase =
        androidx.room.Room.inMemoryDatabaseBuilder(context, RegistryDatabase::class.java).build()
}
