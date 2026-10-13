from pathlib import Path

root = Path(__file__).resolve().parents[1]
base = root / 'android-native/app/src'
def read(p): return (base / p).read_text(encoding='utf-8')
def write(p, s):
    f = base / p
    f.parent.mkdir(parents=True, exist_ok=True)
    f.write_text(s, encoding='utf-8')
main = 'main/java/ph/tala/registry/'
test = 'test/java/ph/tala/registry/'

# Keep profile media in its existing DataStore workflow. Copying it through the
# repository while holding its write mutex would deadlock and mix photo owners.
p = main + 'data/HouseholdRepository.kt'
s = read(p).replace('    private val photos: PhotoStore? = null,\n', '')
a = s.index('        // Handle enumerator profile picture if available')
b = s.index('        store.save(', a)
s = s[:a] + s[b:]
s = s.replace('finalSetup.map', 'setup.map')
s = s.replace('    /** Adds a member when', '''    suspend fun saveMemberPhotoAttachment(householdId: HouseholdId, memberId: MemberId, relativePath: String, mimeType: String): WriteResult =
        WriteResult.Failed("Member pictures are unavailable.")
    suspend fun removeMemberPhotoAttachment(householdId: HouseholdId, memberId: MemberId): WriteResult =
        WriteResult.Failed("Member pictures are unavailable.")

    /** Adds a member when''')
s = s.replace('const val HOUSEHOLD_PHOTO = "household-photo"', 'const val HOUSEHOLD_PHOTO = "household-photo"\n        const val MEMBER_PHOTO = "member-photo"')
s = s.replace('it.kind == HOUSEHOLD_PHOTO && it.memberId', 'it.kind == HouseholdRepository.MEMBER_PHOTO && it.memberId')
s = s.replace('memberId.value, HOUSEHOLD_PHOTO, relativePath', 'memberId.value, HouseholdRepository.MEMBER_PHOTO, relativePath')
needle = 'override suspend fun saveMemberPhotoAttachment'
a = s.index(needle)
b = s.index('        val kept', a)
s = s[:b] + '        if (record.members.none { it.id == memberId.value }) return@withLock WriteResult.Failed(MISSING)\n' + s[b:]
write(p, s)
p = main + 'TalaApplication.kt'
write(p, read(p).replace(', photos = photos)', ')'))
p = main + 'data/PhotoStore.kt'
s = read(p).replace('import ph.tala.registry.domain.model.HouseholdId', 'import ph.tala.registry.domain.model.MemberId\nimport ph.tala.registry.domain.model.HouseholdId')
a = s.index('    /** Saves an existing enumerator profile photo')
b = s.index('    private suspend fun operation', a)
s = s[:a] + '''    override suspend fun saveMemberPhoto(source: Uri, householdId: HouseholdId, memberId: MemberId, repository: HouseholdRepository) = operation {
        val old = repository.interviewRecord(householdId)?.attachments?.find { it.kind == HouseholdRepository.MEMBER_PHOTO && it.memberId == memberId.value }?.relativePath
        replace(source, "attachments", old) { path -> repository.saveMemberPhotoAttachment(householdId, memberId, path, "image/jpeg") }
    }

    override suspend fun removeMemberPhoto(householdId: HouseholdId, memberId: MemberId, repository: HouseholdRepository) = operation {
        val old = repository.interviewRecord(householdId)?.attachments?.find { it.kind == HouseholdRepository.MEMBER_PHOTO && it.memberId == memberId.value }?.relativePath
        val result = repository.removeMemberPhotoAttachment(householdId, memberId)
        if (result is WriteResult.Saved) removeOld(old) else result
    }

''' + s[b:]
s = s.replace('interface PhotoRepository {', '''interface PhotoRepository {
    suspend fun saveMemberPhoto(source: Uri, householdId: HouseholdId, memberId: MemberId, repository: HouseholdRepository): WriteResult = WriteResult.Failed("Member pictures are unavailable.")
    suspend fun removeMemberPhoto(householdId: HouseholdId, memberId: MemberId, repository: HouseholdRepository): WriteResult = WriteResult.Failed("Member pictures are unavailable.")''')
write(p, s)
p = main + 'domain/form/QuestionnaireSections.kt'
s = read(p).replace('\\"', '"')
# Enumerator media is managed by the existing profile screen, not a questionnaire answer.
s = s.replace('        FieldSpec("photo", "Enumerator Profile Picture (Optional)", FieldType.Photo, optional = true),\n', '')
write(p, s)
p = main + 'ui/interview/InterviewWorkspaceScreen.kt'
s = read(p).replace('\\"', '"').replace('HouseholdRepository.HOUSEHOLD_PHOTO && it.memberId', 'HouseholdRepository.MEMBER_PHOTO && it.memberId')
s = s.replace('FilterChip(selected = !state.showingReview', 'FilterChip(modifier = Modifier.testTag("section-${section.route}"), selected = !state.showingReview')
s = s.replace('FilterChip(selected = state.memberId', 'FilterChip(modifier = Modifier.testTag("member-${member.id}"), selected = state.memberId')
write(p, s)
p = main + 'ui/interview/InterviewWorkspaceViewModel.kt'
s = read(p).replace('HouseholdRepository.HOUSEHOLD_PHOTO && it.memberId', 'HouseholdRepository.MEMBER_PHOTO && it.memberId')
s = s.replace('    val hasEnumeratorPhoto: Boolean\n        get() = profiles?.profile.firstOrNull()?.photoRelativePath != null\n\n', '')
s = s.replace('mutable.value.memberId ?: throw IllegalStateException("Member ID required for member photo")', 'MemberId(mutable.value.memberId ?: return@withLock)')
write(p, s)

# Replace non-compiling no-op fakes with the already used repository boundary.
write(test + 'data/MemoryHouseholdRepository.kt', '''package ph.tala.registry.data

import ph.tala.registry.data.local.RecordStore

/** Uses production repository behavior over the existing in-memory record store. */
class MemoryHouseholdRepository(store: RecordStore = FakeRecordStore(), profiles: EnumeratorProfileStore? = null) :
    HouseholdRepository by RoomHouseholdRepository(store, profiles = profiles)
''')
write(test + 'data/InMemoryEnumeratorProfileStore.kt', '''package ph.tala.registry.data

import kotlinx.coroutines.flow.MutableStateFlow
import ph.tala.registry.domain.model.EnumeratorProfile
import ph.tala.registry.data.local.WriteResult

class InMemoryEnumeratorProfileStore : EnumeratorProfileStore {
    override val profile = MutableStateFlow(EnumeratorProfile("Test Enumerator", "Barangay Cogon"))
    override suspend fun save(profile: EnumeratorProfile): WriteResult {
        this.profile.value = profile
        return WriteResult.Saved
    }
}
''')
write(test + 'data/InMemoryPhotoStore.kt', '''package ph.tala.registry.data

import android.net.Uri
import ph.tala.registry.data.local.WriteResult
import ph.tala.registry.domain.model.*

class InMemoryPhotoStore : PhotoRepository {
    override suspend fun saveHouseholdPhoto(source: Uri, householdId: HouseholdId, repository: HouseholdRepository) = WriteResult.Failed("No image decoder in JVM tests.")
    override suspend fun removeHouseholdPhoto(householdId: HouseholdId, repository: HouseholdRepository) = repository.removeHouseholdPhotoAttachment(householdId)
    override suspend fun saveProfilePhoto(source: Uri, profile: EnumeratorProfile, profiles: EnumeratorProfileStore) = WriteResult.Failed("No image decoder in JVM tests.")
    override suspend fun removeProfilePhoto(profile: EnumeratorProfile, profiles: EnumeratorProfileStore) = profiles.save(profile.copy(photoRelativePath = null))
    override suspend fun removeMemberPhoto(householdId: HouseholdId, memberId: MemberId, repository: HouseholdRepository) = repository.removeMemberPhotoAttachment(householdId, memberId)
}
''')
p = root / 'android-native/app/build.gradle.kts'
s = p.read_text().replace('versionCode = 4', 'versionCode = 5').replace('versionName = "0.4.0"', 'versionName = "0.5.0"')
p.write_text(s, encoding='utf-8')
