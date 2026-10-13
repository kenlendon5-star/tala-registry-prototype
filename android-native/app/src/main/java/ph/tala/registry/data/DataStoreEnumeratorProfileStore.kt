package ph.tala.registry.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.map
import ph.tala.registry.data.local.WriteResult
import ph.tala.registry.domain.model.EnumeratorProfile

private val Context.enumeratorProfileDataStore by preferencesDataStore(name = "enumerator_profile")

/** Injectable storage keeps device tests independent from the real profile file. */
class DataStoreEnumeratorProfileStore(private val dataStore: DataStore<Preferences>) : EnumeratorProfileStore {
    constructor(context: Context) : this(context.applicationContext.enumeratorProfileDataStore)
    private val nameKey = stringPreferencesKey("name")
    private val barangayKey = stringPreferencesKey("barangay")
    private val photoPathKey = stringPreferencesKey("photoPath")
    override val profile = dataStore.data.map { prefs ->
        EnumeratorProfile(prefs[nameKey] ?: "Jamie Dela Cruz", prefs[barangayKey] ?: "Barangay Cogon", prefs[photoPathKey])
    }
    override suspend fun save(profile: EnumeratorProfile): WriteResult = try {
        if (profile.name.isBlank() || profile.barangay.isBlank()) {
            WriteResult.Failed("Enter the enumerator name and barangay.")
        } else {
            dataStore.edit { prefs ->
                prefs[nameKey] = profile.name.trim()
                prefs[barangayKey] = profile.barangay.trim()
                profile.photoRelativePath?.let { prefs[photoPathKey] = it } ?: prefs.remove(photoPathKey)
            }
            WriteResult.Saved
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        WriteResult.Failed("Could not save the profile on this device. Your previous profile is unchanged.")
    }
}
