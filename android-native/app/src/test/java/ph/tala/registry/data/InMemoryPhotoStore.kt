package ph.tala.registry.data

import android.net.Uri
import ph.tala.registry.data.local.WriteResult
import ph.tala.registry.domain.model.*

class InMemoryPhotoStore : PhotoRepository {
    /** Records signature calls so tests can assert the cleanup ran exactly once. */
    val signatureEvents = mutableListOf<String>()

    override suspend fun saveHouseholdPhoto(source: Uri, householdId: HouseholdId, repository: HouseholdRepository) = WriteResult.Failed("No image decoder in JVM tests.")
    override suspend fun removeHouseholdPhoto(householdId: HouseholdId, repository: HouseholdRepository) = repository.removeHouseholdPhotoAttachment(householdId)
    override suspend fun saveProfilePhoto(source: Uri, profile: EnumeratorProfile, profiles: EnumeratorProfileStore) = WriteResult.Failed("No image decoder in JVM tests.")
    override suspend fun removeProfilePhoto(profile: EnumeratorProfile, profiles: EnumeratorProfileStore) = profiles.save(profile.copy(photoRelativePath = null))
    override suspend fun removeMemberPhoto(householdId: HouseholdId, memberId: MemberId, repository: HouseholdRepository) = repository.removeMemberPhotoAttachment(householdId, memberId)
    override suspend fun saveSignature(bytes: ByteArray, householdId: HouseholdId, repository: HouseholdRepository): WriteResult {
        signatureEvents += "save"
        return repository.saveSignatureAttachment(householdId, "attachments/signature.png", "image/png")
    }
    override suspend fun removeSignature(householdId: HouseholdId, repository: HouseholdRepository): WriteResult {
        signatureEvents += "remove"
        return repository.removeSignatureAttachment(householdId)
    }
}
