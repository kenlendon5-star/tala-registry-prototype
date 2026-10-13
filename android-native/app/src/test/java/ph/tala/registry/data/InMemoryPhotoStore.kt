package ph.tala.registry.data

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
