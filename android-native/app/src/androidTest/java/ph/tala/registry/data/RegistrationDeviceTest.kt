package ph.tala.registry.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import ph.tala.registry.data.local.*
import ph.tala.registry.domain.edit.*
import ph.tala.registry.domain.form.*
import ph.tala.registry.domain.model.*
import ph.tala.registry.ui.RegistryUiState
import java.io.File
import java.util.UUID

/** Every test owns one temporary root, one profile store and one uniquely named database. */
class RegistrationDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val token = UUID.randomUUID().toString()
    private val dbName = "registration-$token.db"
    private val root = File(context.cacheDir, "registration-$token")
    private lateinit var database: RegistryDatabase
    private lateinit var repo: RoomHouseholdRepository
    private lateinit var store: FailingStore
    private lateinit var profiles: DataStoreEnumeratorProfileStore
    private lateinit var profileJob: Job
    private lateinit var photos: PhotoStore
    private val household = HouseholdId("ad080000-0000-4000-8000-000000000001")
    private val a = MemberId("ad080000-0000-4000-8000-000000000011")
    private val b = MemberId("ad080000-0000-4000-8000-000000000012")
    private val c = MemberId("ad080000-0000-4000-8000-000000000013")

    @Before fun open() {
        root.mkdirs()
        openProfile()
        openDatabase()
        photos = PhotoStore(context, root)
    }
    private fun openProfile() {
        profileJob = SupervisorJob()
        profiles = DataStoreEnumeratorProfileStore(PreferenceDataStoreFactory.create(
            scope = CoroutineScope(profileJob + Dispatchers.IO), produceFile = { File(root, "test.preferences_pb") }))
    }
    private fun openDatabase() {
        database = RegistryDatabase.open(context, dbName)
        store = FailingStore(RoomRecordStore(database))
        repo = RoomHouseholdRepository(store, profiles = profiles)
    }
    @After fun cleanup() = runBlocking {
        database.close()
        profileJob.cancelAndJoin()
        context.deleteDatabase(dbName)
        check(root.canonicalFile.parentFile == context.cacheDir.canonicalFile && root.name == "registration-$token")
        root.deleteRecursively()
        Unit
    }
    private fun image(width: Int = 2400, height: Int = 1800): Uri {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.rgb(20, 100, 160))
        val file = File(root, "source-${UUID.randomUUID()}.png")
        file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
        return Uri.fromFile(file)
    }
    private suspend fun create() { assertEquals(WriteResult.Saved, repo.createHousehold(household, HouseholdDraft("Luna Reyes", "5 Sampaguita Street", HouseholdStatus.InProgress))) }
    private suspend fun path() = repo.interviewRecord(household)!!.attachments.single().relativePath
    private fun assertNormalized(path: String) {
        val file = File(root, path)
        assertTrue(file.length() in 1..1024 * 1024L)
        val image = BitmapFactory.decodeFile(file.path)
        assertNotNull(image)
        assertTrue(maxOf(image.width, image.height) <= 1600)
        image.recycle()
    }

    @Test fun memberPicturesAreIsolatedAndFailedReplacementRetainsTheSavedCopy() = runBlocking {
        create()
        repo.saveMember(household, a, MemberDraft("Maria"))
        repo.saveMember(household, b, MemberDraft("Juan"))
        val source = image()
        assertEquals(WriteResult.Saved, photos.saveHouseholdPhoto(source, household, repo))
        assertEquals(WriteResult.Saved, photos.saveMemberPhoto(source, household, a, repo))
        assertEquals(WriteResult.Saved, photos.saveMemberPhoto(source, household, b, repo))
        val before = repo.interviewRecord(household)!!.attachments
        val original = before.single { it.memberId == a.value }.relativePath
        assertNormalized(original)
        store.fail = true
        assertTrue(photos.saveMemberPhoto(source, household, a, repo) is WriteResult.Failed)
        assertEquals(before, repo.interviewRecord(household)!!.attachments)
        assertTrue(File(root, original).exists())
        store.fail = false
        assertEquals(WriteResult.Saved, photos.saveMemberPhoto(source, household, a, repo))
        assertFalse(File(root, original).exists())
        val current = repo.interviewRecord(household)!!.attachments
        assertEquals(before.filter { it.memberId != a.value }.toSet(), current.filter { it.memberId != a.value }.toSet())
        database.close(); openDatabase()
        assertEquals(current.toSet(), repo.interviewRecord(household)!!.attachments.toSet())
        assertEquals(WriteResult.Saved, photos.removeMemberPhoto(household, a, repo))
        assertEquals(setOf(null, b.value), repo.interviewRecord(household)!!.attachments.map { it.memberId }.toSet())
    }

    @Test fun picturesProfileAttributionAndAllCountsSurviveReopen() = runBlocking {
        profiles.save(EnumeratorProfile("Enumerator A", "Barangay Cogon"))
        create()
        for ((id, name) in listOf(a to "Member A", b to "Member B", c to "Member C")) repo.saveMember(household, id, MemberDraft(name))
        repo.saveDraftSection(household, "demographics", a, mapOf("sex" to AnswerValue.Scalar("Male")), emptyMap())
        repo.saveDraftSection(household, "demographics", b, mapOf("sex" to AnswerValue.Scalar("Female")), emptyMap())
        assertEquals(WriteResult.Saved, photos.saveHouseholdPhoto(image(), household, repo))
        assertEquals(WriteResult.Saved, photos.saveProfilePhoto(image(), EnumeratorProfile("Enumerator B", "Barangay Cogon"), profiles))
        val householdPath = path()
        val profilePath = profiles.profile.first().photoRelativePath!!
        database.close(); profileJob.cancelAndJoin()
        openProfile(); openDatabase()
        assertEquals("Enumerator B", profiles.profile.first().name)
        assertEquals(profilePath, profiles.profile.first().photoRelativePath)
        assertEquals(householdPath, path())
        assertNormalized(householdPath); assertNormalized(profilePath)
        assertEquals("Enumerator A", InterviewRules.answers(repo.interviewRecord(household)!!, QuestionnaireSections.setup).scalar("enumerator"))
        suspend fun counts() = RegistryUiState(loading = false, households = repo.households.first().filterNot { it.archived }, memberSex = repo.memberSex.first())
        counts().let { assertEquals(3, it.memberCount); assertEquals(1, it.maleCount); assertEquals(1, it.femaleCount); assertEquals(1, it.unrecordedSexCount) }
        repo.setArchived(household, true)
        assertEquals(0, counts().memberCount)
        repo.setArchived(household, false)
        assertEquals(3, counts().memberCount)
    }

    @Test fun householdReplaceRemoveFailuresAndCleanupPreserveDurablePhotos() = runBlocking {
        create()
        assertEquals(WriteResult.Saved, photos.saveHouseholdPhoto(image(), household, repo))
        val first = path()
        assertNormalized(first)
        store.fail = true
        assertTrue(photos.saveHouseholdPhoto(image(), household, repo) is WriteResult.Failed)
        assertEquals(first, path()); assertTrue(File(root, first).exists())
        assertEquals(1, File(root, "attachments").list()!!.size)
        assertTrue(photos.removeHouseholdPhoto(household, repo) is WriteResult.Failed)
        assertEquals(first, path()); assertTrue(File(root, first).exists())
        store.fail = false
        assertEquals(WriteResult.Saved, photos.saveHouseholdPhoto(image(320, 180), household, repo))
        val second = path()
        assertNotEquals(first, second); assertFalse(File(root, first).exists()); assertNormalized(second)
        val orphan = File(root, "attachments/${UUID.randomUUID()}.jpg").apply { writeBytes(byteArrayOf(1)) }
        assertEquals(WriteResult.Saved, photos.cleanupUnreferenced { setOf(second) })
        assertFalse(orphan.exists()); assertTrue(File(root, second).exists())
        assertEquals(WriteResult.Saved, photos.removeHouseholdPhoto(household, repo))
        assertTrue(repo.interviewRecord(household)!!.attachments.isEmpty())
        assertFalse(File(root, second).exists())
        assertTrue(photos.saveHouseholdPhoto(image(), HouseholdId(UUID.randomUUID().toString()), repo) is WriteResult.Failed)
        assertEquals(0, File(root, "attachments").list()!!.size)
    }

    @Test fun profileReplaceAndRemovalAreDurableAndFailedCommitKeepsOldFile() = runBlocking {
        val profile = EnumeratorProfile("Test Enumerator", "Barangay Cogon")
        assertEquals(WriteResult.Saved, photos.saveProfilePhoto(image(), profile, profiles))
        val first = profiles.profile.first().photoRelativePath!!
        val rejected = object : EnumeratorProfileStore by profiles {
            override suspend fun save(profile: EnumeratorProfile) = WriteResult.Failed("Injected write failure")
        }
        assertTrue(photos.saveProfilePhoto(image(), profile, rejected) is WriteResult.Failed)
        assertTrue(photos.removeProfilePhoto(profile, rejected) is WriteResult.Failed)
        assertEquals(first, profiles.profile.first().photoRelativePath); assertTrue(File(root, first).exists())
        assertEquals(1, File(root, "profile").list()!!.size)
        assertEquals(WriteResult.Saved, photos.saveProfilePhoto(image(), profile, profiles))
        val second = profiles.profile.first().photoRelativePath!!
        assertNotEquals(first, second); assertFalse(File(root, first).exists())
        assertEquals(WriteResult.Saved, photos.removeProfilePhoto(profile, profiles))
        assertFalse(File(root, second).exists())
        profileJob.cancelAndJoin(); openProfile()
        assertNull(profiles.profile.first().photoRelativePath)
    }

    @Test fun invalidOversizedUnavailableAndUnwritableImagesFailWithoutChangingReferences() = runBlocking {
        create()
        assertEquals(WriteResult.Saved, photos.saveHouseholdPhoto(image(), household, repo))
        val saved = path()
        val invalid = File(root, "invalid.png").apply { writeBytes(ByteArray(32) { 7 }) }
        val oversized = File(root, "oversized.png").apply { writeBytes(ByteArray(photos.maxBytes + 1)) }
        for (source in listOf(Uri.fromFile(invalid), Uri.fromFile(oversized), Uri.fromFile(File(root, "missing.png")))) {
            assertTrue(photos.saveHouseholdPhoto(source, household, repo) is WriteResult.Failed)
            assertTrue(photos.saveProfilePhoto(source, profiles.profile.first(), profiles) is WriteResult.Failed)
            assertEquals(saved, path()); assertTrue(File(root, saved).exists())
        }
        val blocked = File(root, "blocked").apply { writeText("file instead of directory") }
        assertTrue(PhotoStore(context, blocked).saveHouseholdPhoto(image(), household, repo) is WriteResult.Failed)
        assertEquals(saved, path())
        assertEquals(1, File(root, "attachments").list()!!.size)
    }

    private class FailingStore(private val actual: RecordStore) : RecordStore by actual {
        var fail = false
        override suspend fun save(record: StoredRecord) = if (fail) WriteResult.Failed("Injected write failure") else actual.save(record)
    }
}
