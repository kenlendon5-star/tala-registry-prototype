package ph.tala.registry.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import ph.tala.registry.TalaApplication
import ph.tala.registry.data.local.WriteResult
import ph.tala.registry.domain.edit.*
import ph.tala.registry.domain.form.*
import ph.tala.registry.domain.model.*
import ph.tala.registry.ui.RegistryUiState
import java.io.File
import java.util.Properties

/** Invoke the two methods in separate instrumentation runs with am force-stop between them.
 * Deliberately restricted to the disposable application ID, never a user's normal app. */
class RegistrationRestartProbe {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app get() = context.applicationContext as TalaApplication
    private val id = HouseholdId("ad080000-0000-4000-8000-000000000081")
    private val members = (1..3).map { MemberId("ad080000-0000-4000-8000-00000000008$it") }

    @Test fun writeFixture() = runBlocking {
        check(context.packageName.endsWith(".d08test"))
        app.profiles.save(EnumeratorProfile("Restart Enumerator A", "Barangay Cogon"))
        assertEquals(WriteResult.Saved, app.repository.createHousehold(id, HouseholdDraft("Restart Family", "Fictional restart address", HouseholdStatus.InProgress)))
        members.forEachIndexed { index, member -> app.repository.saveMember(id, member, MemberDraft("Restart Member $index")) }
        for ((member, sex) in listOf(members[0] to "Male", members[1] to "Female")) {
            val before = InterviewRules.answers(app.repository.interviewRecord(id)!!, QuestionnaireSections.demographics, member.value)
            assertEquals(WriteResult.Saved, app.repository.saveDraftSection(id, "demographics", member, before + ("sex" to AnswerValue.Scalar(sex)), before))
        }
        val image = Bitmap.createBitmap(2400, 1800, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.CYAN) }
        val source = File(context.cacheDir, "restart-source.png")
        source.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }; image.recycle()
        assertEquals(WriteResult.Saved, app.photos.saveHouseholdPhoto(Uri.fromFile(source), id, app.repository))
        assertEquals(WriteResult.Saved, app.photos.saveProfilePhoto(Uri.fromFile(source), EnumeratorProfile("Restart Enumerator B", "Barangay Cogon"), app.profiles))
        source.delete()
        val expected = Properties().apply {
            setProperty("householdPhoto", app.repository.interviewRecord(id)!!.attachments.single().relativePath)
            setProperty("profilePhoto", app.profiles.profile.first().photoRelativePath!!)
        }
        File(context.filesDir, "day08-restart.properties").outputStream().use { expected.store(it, "Fictional restart fixture") }
    }

    @Test fun verifyAfterForceStop() = runBlocking {
        check(context.packageName.endsWith(".d08test"))
        val expected = Properties().apply { File(context.filesDir, "day08-restart.properties").inputStream().use { load(it) } }
        val record = app.repository.interviewRecord(id)!!
        assertEquals(3, record.members.size)
        assertEquals("Restart Enumerator A", InterviewRules.answers(record, QuestionnaireSections.setup).scalar("enumerator"))
        assertEquals("Restart Enumerator B", app.profiles.profile.first().name)
        assertEquals(expected.getProperty("householdPhoto"), record.attachments.single().relativePath)
        assertEquals(expected.getProperty("profilePhoto"), app.profiles.profile.first().photoRelativePath)
        for (key in listOf("householdPhoto", "profilePhoto")) {
            val file = File(context.filesDir, expected.getProperty(key))
            val bitmap = BitmapFactory.decodeFile(file.path)
            assertNotNull(bitmap); assertTrue(maxOf(bitmap.width, bitmap.height) <= 1600); bitmap.recycle()
            assertTrue(file.length() in 1..1024 * 1024L)
        }
        val counts = RegistryUiState(loading = false, households = app.repository.households.first().filter { it.id == id }, memberSex = app.repository.memberSex.first())
        assertEquals(3, counts.memberCount); assertEquals(1, counts.maleCount); assertEquals(1, counts.femaleCount); assertEquals(1, counts.unrecordedSexCount)
    }
}
