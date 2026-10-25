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
import ph.tala.registry.data.cloud.SupabaseAccountRepository
import ph.tala.registry.data.cloud.SupabaseHouseholdCloudSyncRepository
import ph.tala.registry.data.backup.BackupImporter
import java.io.File

/** Application-owned dependencies survive Activity recreation without holding an Activity. */
class TalaApplication : Application() {
    val profiles by lazy { DataStoreEnumeratorProfileStore(this) }
    val resumes by lazy { DataStoreInterviewResumeStore(this) }
    val photos by lazy { PhotoStore(this) }
    val database by lazy { RegistryDatabase.open(this) }
    val recordStore by lazy { RoomRecordStore(database) }
    val syncOutbox by lazy { RoomSyncOutboxStore(database) }
    val repository by lazy { RoomHouseholdRepository(recordStore, profiles = profiles) }
    val accounts by lazy { SupabaseAccountRepository(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_PUBLISHABLE_KEY) }
    val cloudSync by lazy { SupabaseHouseholdCloudSyncRepository(accounts, recordStore, syncOutbox, photos) }
    val backupImporter by lazy { BackupImporter(recordStore, photos, File(cacheDir, "backup-imports")) }
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
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { cloudSync.retryPending() }
    }
}
