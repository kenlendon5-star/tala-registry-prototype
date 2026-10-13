package ph.tala.registry.ui.profile

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ph.tala.registry.data.EnumeratorProfileStore
import ph.tala.registry.data.PhotoRepository
import ph.tala.registry.data.local.WriteResult
import ph.tala.registry.domain.model.EnumeratorProfile
import ph.tala.registry.ui.households.SavePhase

class EnumeratorProfileViewModel(private val profiles: EnumeratorProfileStore, private val photos: PhotoRepository) : ViewModel() {
    private val mutable = MutableStateFlow(EnumeratorProfileUiState())
    val uiState = mutable.asStateFlow()
    init { viewModelScope.launch {
        try { show(profiles.profile.first()) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { mutable.value = mutable.value.copy(loading = false, phase = SavePhase.Failed, message = "Could not read the saved profile. Reopen the app to retry.") }
    } }
    private fun show(profile: EnumeratorProfile) {
        mutable.value = mutable.value.copy(name = profile.name, barangay = profile.barangay,
            photoPath = profile.photoRelativePath, photoSaved = profile.photoRelativePath != null, loading = false)
    }
    fun setName(value: String) { if (!mutable.value.saving) mutable.value = mutable.value.copy(name = value, phase = SavePhase.Idle, message = "Unsaved changes") }
    fun setBarangay(value: String) { if (!mutable.value.saving) mutable.value = mutable.value.copy(barangay = value, phase = SavePhase.Idle, message = "Unsaved changes") }
    private fun write(operation: suspend (EnumeratorProfile) -> WriteResult) {
        val state = mutable.value
        if (state.loading || state.saving) return
        if (state.name.isBlank() || state.barangay.isBlank()) {
            mutable.value = state.copy(phase = SavePhase.Failed, message = "Enter the enumerator name and barangay.")
            return
        }
        val draft = EnumeratorProfile(state.name.trim(), state.barangay.trim(), state.photoPath)
        mutable.value = state.copy(saving = true, phase = SavePhase.Saving, message = "")
        viewModelScope.launch {
            val result = try {
                val saved = operation(draft)
                val durable = profiles.profile.first()
                if (saved is WriteResult.Saved) show(durable)
                else mutable.value = mutable.value.copy(photoPath = durable.photoRelativePath, photoSaved = durable.photoRelativePath != null)
                saved
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { WriteResult.Failed("Could not save the profile. Retry after checking storage.") }
            mutable.value = mutable.value.copy(saving = false,
                phase = if (result is WriteResult.Saved) SavePhase.Saved else SavePhase.Failed,
                message = when (result) {
                    WriteResult.Saved -> "Profile saved on this device."
                    is WriteResult.Failed -> result.message
                })
        }
    }
    fun save() = write { profiles.save(it) }
    fun savePhoto(uri: Uri) = write { photos.saveProfilePhoto(uri, it, profiles) }
    fun removePhoto() = write { photos.removeProfilePhoto(it, profiles) }
}
