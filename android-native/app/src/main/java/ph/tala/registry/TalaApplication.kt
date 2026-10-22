package ph.tala.registry

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import ph.tala.registry.data.*
import ph.tala.registry.data.local.*
import java.io.File

/** Application-owned dependencies survive Activity recreation without holding an Activity. */
class TalaApplication : Application() {
    val profiles by lazy { DataStoreEnumeratorProfileStore(this) }
    val resumes by lazy { DataStoreInterviewResumeStore(this) }
    val photos by lazy { PhotoStore(this) }
    val repository by lazy { RoomHouseholdRepository(RoomRecordStore(RegistryDatabase.open(this)), profiles = profiles) }
    override fun onCreate() {
        super.onCreate()
        // D16 map: the tile provider must see an identifying User-Agent (OpenStreetMap
        // tile usage policy) and keep its on-demand tile cache inside the app-private
        // cache directory — never external storage, and never a bulk offline basemap.
        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidBasePath = File(cacheDir, "osmdroid").apply { mkdirs() }
            osmdroidTileCache = File(cacheDir, "osmdroid/tiles").apply { mkdirs() }
        }
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            photos.cleanupUnreferenced {
                repository.households.first().flatMap { repository.interviewRecord(it.id)?.attachments.orEmpty().map { row -> row.relativePath } }
                    .toSet() + listOfNotNull(profiles.profile.first().photoRelativePath)
            }
        }
    }
}
