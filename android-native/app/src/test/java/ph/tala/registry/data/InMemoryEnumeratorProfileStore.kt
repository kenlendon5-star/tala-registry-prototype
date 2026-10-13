package ph.tala.registry.data

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
