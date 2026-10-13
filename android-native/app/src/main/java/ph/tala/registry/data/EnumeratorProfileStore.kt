package ph.tala.registry.data

import kotlinx.coroutines.flow.Flow
import ph.tala.registry.data.local.WriteResult
import ph.tala.registry.domain.model.EnumeratorProfile

interface EnumeratorProfileStore {
    val profile: Flow<EnumeratorProfile>
    suspend fun save(profile: EnumeratorProfile): WriteResult
}
