package ph.tala.registry.ui.location

import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ph.tala.registry.data.HouseholdRepository
import ph.tala.registry.data.local.WriteResult
import ph.tala.registry.domain.model.Coordinate
import ph.tala.registry.domain.model.CoordinateSource
import ph.tala.registry.domain.model.HouseholdId
import ph.tala.registry.domain.model.LocationRules
import ph.tala.registry.location.Disposable
import ph.tala.registry.location.FixOutcome
import ph.tala.registry.location.LocationClient
import ph.tala.registry.location.LocationPermissions

/** Explicit capture-machine states; nothing here implies a silent success. */
enum class LocationCapture {
    /** No attempt is running. */
    Idle,

    /** The enumerator has not answered the system permission dialog yet. */
    AwaitingPermission,

    /** First refusal; the enumerator may retry, which re-requests. */
    Denied,

    /** The system no longer shows the dialog; only device settings can recover. */
    PermanentlyDenied,

    /** The master location switch is off; enabling it recovers without reinstall. */
    LocationOff,

    /** A fresh fix was requested and has not returned yet. */
    Acquiring,

    /** No fix arrived within the timeout; the attempt was cancelled cleanly. */
    TimedOut,

    /** The provider could not produce a fix, or the fix failed validation. */
    Failed,
}

/** Explicit write outcomes for coordinate persistence; never merged into capture states. */
enum class LocationWritePhase { Idle, Saving, Saved, Failed }

/** Manual draft restored through SavedStateHandle; validated before repository writes. */
data class ManualLocationDraft(
    val latitude: String = "",
    val longitude: String = "",
    val latitudeError: String? = null,
    val longitudeError: String? = null,
    val attempt: Int = 0,
)

data class LocationUiState(
    val loading: Boolean = true,
    val coordinate: Coordinate? = null,
    val capture: LocationCapture = LocationCapture.Idle,
    val writePhase: LocationWritePhase = LocationWritePhase.Idle,
    val message: String = "",
    val manual: ManualLocationDraft? = null,
) {
    /** True once anything was stored for this household (device fix or manual entry). */
    val hasCoordinate: Boolean get() = coordinate != null
}

/**
 * Request-on-use household location capture, one ViewModel per household. Every
 * request and callback is bound to a monotonically increasing generation: a fix that
 * arrives after a newer request, an opened manual editor, a manual save or screen
 * disposal is rejected, and a result computed for this household can never reach
 * another (each ViewModel holds exactly one [HouseholdId], and the repository writes
 * under that id only).
 *
 * Location failures never block questionnaire entry or saving: this screen is reached
 * from the household record, and capture problems only change this card's state.
 */
class LocationViewModel(
    private val id: HouseholdId,
    private val repository: HouseholdRepository,
    private val client: LocationClient,
    private val permissions: LocationPermissions,
    private val clock: () -> Long = System::currentTimeMillis,
    private val timeoutMs: Long = LocationRules.ACQUISITION_TIMEOUT_MS,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val mutable = MutableStateFlow(LocationUiState(manual = if (savedState.get<Boolean>("location.manual") == true)
        ManualLocationDraft(savedState["location.lat"] ?: "", savedState["location.lon"] ?: "") else null))
    val state = mutable.asStateFlow()

    /** Generations: ++ on every new request, editor action or disposal; callbacks carry theirs. */
    private var generation = 0
    private var acquisition: Disposable? = null
    private var timeout: Job? = null
    private var active = true

    fun activate() { active = true }
    fun deactivate() {
        active = false
        generation++
        cancelAcquisition()
        mutable.value = mutable.value.copy(capture = LocationCapture.Idle)
    }

    private fun rememberManual() {
        savedState["location.manual"] = mutable.value.manual != null
        savedState["location.lat"] = mutable.value.manual?.latitude.orEmpty()
        savedState["location.lon"] = mutable.value.manual?.longitude.orEmpty()
    }

    init {
        viewModelScope.launch {
            repository.observeCoordinate(id).collect { row ->
                // The repository maps stored rows; a null here means "not captured yet".
                mutable.value = mutable.value.copy(loading = false, coordinate = row)
            }
        }
    }

    /** Enumerator chose to capture: the only place a permission request is ever started. */
    fun capture(): Boolean {
        if (!active || mutable.value.writePhase == LocationWritePhase.Saving || mutable.value.manual != null ||
            mutable.value.capture == LocationCapture.AwaitingPermission) return false
        generation++
        cancelAcquisition()
        mutable.value = mutable.value.copy(writePhase = LocationWritePhase.Idle)
        if (!client.locationEnabled()) {
            mutable.value = mutable.value.copy(capture = LocationCapture.LocationOff,
                message = "Location is off on this device. Turn location on and capture again, or correct the coordinates manually.")
            return false
        }
        if (!permissions.fineGranted() && !permissions.coarseGranted()) {
            mutable.value = mutable.value.copy(capture = LocationCapture.AwaitingPermission,
                message = "Allow location access to capture this household's coordinates.")
            return true
        }
        startAcquisition()
        return false
    }

    /**
     * The system dialog answered. [permanent] comes from the UI's
     * shouldShowRequestPermissionRationale check: no rationale after a refusal means
     * the system will not ask again.
     */
    fun onPermissionResult(fine: Boolean, coarse: Boolean, permanent: Boolean) {
        if (!active || mutable.value.capture != LocationCapture.AwaitingPermission || mutable.value.manual != null) return
        generation++
        cancelAcquisition()
        when {
            (fine || coarse) && !client.locationEnabled() -> mutable.value = mutable.value.copy(capture = LocationCapture.LocationOff,
                message = "Location is off. Enable it and retry, or enter coordinates manually.")
            fine || coarse -> startAcquisition()
            permanent -> mutable.value = mutable.value.copy(capture = LocationCapture.PermanentlyDenied,
                message = "Location permission was denied for this app. Enable it in device settings, or correct the coordinates manually.")
            else -> mutable.value = mutable.value.copy(capture = LocationCapture.Denied,
                message = "Location was denied. You can retry, or correct the coordinates manually.")
        }
    }

    private fun startAcquisition() {
        val token = ++generation
        mutable.value = mutable.value.copy(capture = LocationCapture.Acquiring,
            message = if (permissions.fineGranted()) "Acquiring a fresh device fix…" else "Acquiring a fix with approximate access…")
        acquisition = client.requestFix { outcome -> onFix(outcome, token) }
        timeout = viewModelScope.launch {
            delay(timeoutMs)
            if (generation == token && mutable.value.capture == LocationCapture.Acquiring) {
                generation++
                cancelAcquisition()
                mutable.value = mutable.value.copy(capture = LocationCapture.TimedOut,
                    message = "No location fix within ${timeoutMs / 1000} seconds. Move to open sky and retry, or correct the coordinates manually.")
            }
        }
    }

    private fun onFix(outcome: FixOutcome, token: Int) {
        // Late callback: a newer request, manual editor action or disposal happened.
        if (!active || generation != token || mutable.value.capture != LocationCapture.Acquiring) return
        generation++
        cancelAcquisition()
        if (!permissions.fineGranted() && !permissions.coarseGranted()) {
            mutable.value = mutable.value.copy(capture = LocationCapture.Denied, message = "Location access changed. Retry permission or enter coordinates manually.")
            return
        }
        when (outcome) {
            is FixOutcome.Fix -> {
                // A malformed platform fix must never be stored as if it were measured.
                val problem = LocationRules.coordinateProblem(outcome.latitude, outcome.longitude, outcome.accuracyMetres)
                    ?: if (outcome.capturedAt <= 0 || clock() - outcome.capturedAt > 30_000 || outcome.capturedAt - clock() > 5_000)
                        "The fix is stale or has an invalid time. Capture again or enter coordinates manually." else null
                if (problem != null) {
                    mutable.value = mutable.value.copy(capture = LocationCapture.Failed, message = problem)
                    return
                }
                persist(Coordinate(outcome.latitude, outcome.longitude, outcome.accuracyMetres, outcome.capturedAt, CoordinateSource.Gps))
            }
            FixOutcome.Unavailable -> mutable.value = mutable.value.copy(capture = LocationCapture.Failed,
                message = "No location fix is available right now. Retry, or correct the coordinates manually.")
        }
    }

    private fun persist(coordinate: Coordinate) {
        mutable.value = mutable.value.copy(capture = LocationCapture.Idle, writePhase = LocationWritePhase.Saving,
            message = "Saving coordinates…")
        viewModelScope.launch {
            val result = repository.saveCoordinate(id, coordinate)
            // An accepted write finishes even when navigation cancels acquisition.
            mutable.value = mutable.value.copy(
                writePhase = if (result is WriteResult.Saved) LocationWritePhase.Saved else LocationWritePhase.Failed,
                message = when (result) {
                    is WriteResult.Saved -> "Coordinates saved on this device."
                    is WriteResult.Failed -> result.message
                },
            )
        }
    }

    /** Opens the manual editor and cancels any running acquisition (its fix is now stale). */
    fun openManual() {
        if (mutable.value.writePhase == LocationWritePhase.Saving) return
        generation++
        cancelAcquisition()
        val current = mutable.value.coordinate
        mutable.value = mutable.value.copy(capture = LocationCapture.Idle, writePhase = LocationWritePhase.Idle, message = "", manual = ManualLocationDraft(
            latitude = current?.let { format(it.latitude) }.orEmpty(),
            longitude = current?.let { format(it.longitude) }.orEmpty(),
        ))
        rememberManual()
    }

    private fun format(value: Double): String = if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

    fun editManualLatitude(text: String) {
        if (mutable.value.writePhase == LocationWritePhase.Saving) return
        val draft = mutable.value.manual ?: return
        mutable.value = mutable.value.copy(manual = draft.copy(latitude = text, latitudeError = null), writePhase = LocationWritePhase.Idle, message = "")
        rememberManual()
    }

    fun editManualLongitude(text: String) {
        if (mutable.value.writePhase == LocationWritePhase.Saving) return
        val draft = mutable.value.manual ?: return
        mutable.value = mutable.value.copy(manual = draft.copy(longitude = text, longitudeError = null), writePhase = LocationWritePhase.Idle, message = "")
        rememberManual()
    }

    fun saveManual() {
        if (!active || mutable.value.writePhase == LocationWritePhase.Saving) return
        val draft = mutable.value.manual ?: return
        val latitude = LocationRules.parse(draft.latitude)
        val longitude = LocationRules.parse(draft.longitude)
        if (latitude == null || longitude == null) {
            mutable.value = mutable.value.copy(manual = draft.copy(
                latitudeError = if (latitude == null) "Latitude must be a number between -90 and 90." else null,
                longitudeError = if (longitude == null) "Longitude must be a number between -180 and 180." else null,
                attempt = draft.attempt + 1,
            ))
            return
        }
        LocationRules.coordinateProblem(latitude, longitude, null)?.let { problem ->
            mutable.value = mutable.value.copy(manual = draft.copy(
                latitudeError = if (problem.startsWith("Latitude")) problem else null,
                longitudeError = if (problem.startsWith("Longitude")) problem else null,
                attempt = draft.attempt + 1,
            ))
            return
        }
        generation++
        cancelAcquisition()
        mutable.value = mutable.value.copy(writePhase = LocationWritePhase.Saving, message = "Saving coordinates…")
        // Manual provenance: no accuracy, and the capture time is the entry moment.
        val coordinate = Coordinate(latitude, longitude, accuracyMetres = null, capturedAt = clock(), source = CoordinateSource.Manual)
        viewModelScope.launch {
            val result = repository.saveCoordinate(id, coordinate)
            mutable.value = when (result) {
                is WriteResult.Saved -> mutable.value.copy(manual = null, writePhase = LocationWritePhase.Saved,
                    message = "Corrected coordinates saved on this device.")
                is WriteResult.Failed -> mutable.value.copy(writePhase = LocationWritePhase.Failed, message = result.message)
            }
            rememberManual()
        }
    }

    fun cancelManual() {
        if (mutable.value.writePhase == LocationWritePhase.Saving) return
        generation++
        mutable.value = mutable.value.copy(manual = null, capture = LocationCapture.Idle)
        rememberManual()
    }

    private fun cancelAcquisition() {
        timeout?.cancel()
        timeout = null
        acquisition?.dispose()
        acquisition = null
    }

    override fun onCleared() {
        // Screen disposal: any later fix callback is rejected by the generation bump
        // even if the client already captured the reference.
        deactivate()
        super.onCleared()
    }
}
