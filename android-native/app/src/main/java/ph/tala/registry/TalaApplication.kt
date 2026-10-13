package ph.tala.registry

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ph.tala.registry.data.*
import ph.tala.registry.data.local.*

/** Application-owned dependencies survive Activity recreation without holding an Activity. */
class TalaApplication : Application() {
    val profiles by lazy { DataStoreEnumeratorProfileStore(this) }
    val photos by lazy { PhotoStore(this) }
    val repository by lazy { RoomHouseholdRepository(RoomRecordStore(RegistryDatabase.open(this)), profiles = profiles) }
    override fun onCreate() {
        super.onCreate()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            photos.cleanupUnreferenced {
                repository.households.first().flatMap { repository.interviewRecord(it.id)?.attachments.orEmpty().map { row -> row.relativePath } }
                    .toSet() + listOfNotNull(profiles.profile.first().photoRelativePath)
            }
        }
    }
}
