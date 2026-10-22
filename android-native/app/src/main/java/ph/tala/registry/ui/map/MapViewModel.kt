package ph.tala.registry.ui.map

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import ph.tala.registry.data.HouseholdRepository
import ph.tala.registry.data.local.WriteResult
import ph.tala.registry.domain.model.Coordinate
import ph.tala.registry.domain.model.CoordinateSource
import ph.tala.registry.domain.model.HouseholdId
import ph.tala.registry.domain.model.LocationRules
import ph.tala.registry.domain.model.MapRules
import ph.tala.registry.ui.location.LocationWritePhase

/** The basemap the map surface is currently presenting. */
enum class BasemapMode {
    /** OpenStreetMap tiles are reachable; the tile map is shown. */
    Online,

    /** Airplane mode or no network: the offline coordinate plot replaces the tiles. */
    Offline,
}

/** One plotted household pin; [outOfArea] flags retained out-of-extent coordinates. */
data class HouseholdPin(
    val householdId: HouseholdId,
    val displayCode: String,
    val headName: String,
    val address: String,
    val coordinate: Coordinate,
    val outOfArea: Boolean,
)

/** A household without a saved coordinate: never plotted, still reachable from the list. */
data class UntaggedHousehold(
    val householdId: HouseholdId,
    val displayCode: String,
    val headName: String,
)

/**
 * The pin being adjusted on the map. Draft edits are held here (and in
 * [SavedStateHandle], mirroring the location card) until the enumerator saves or
 * cancels; saving records Manual provenance, which clears measured accuracy.
 */
data class PinAdjustDraft(
    val householdId: HouseholdId,
    val displayCode: String,
    val latitudeText: String,
    val longitudeText: String,
    val latitudeError: String? = null,
    val longitudeError: String? = null,
    val attempt: Int = 0,
)

data class MapUiState(
    val loading: Boolean = true,
    val basemap: BasemapMode = BasemapMode.Online,
    val pins: List<HouseholdPin> = emptyList(),
    val untagged: List<UntaggedHousehold> = emptyList(),
    val selectedId: HouseholdId? = null,
    val adjust: PinAdjustDraft? = null,
    val writePhase: LocationWritePhase = LocationWritePhase.Idle,
    val message: String = "",
) {
    val selectedPin: HouseholdPin? get() = pins.firstOrNull { it.householdId == selectedId }
    /** Pins that sit inside the approximate working extent and can be plotted. */
    val plottedCount: Int get() = pins.count { !it.outOfArea }
}

/**
 * Registry map state for D16. Reads stay fully offline (Room); only the basemap
 * depends on connectivity, and losing it never blocks selection, pin adjustment or
 * opening a record. One ViewModel serves the whole registry screen; writes always go
 * through the repository under the target household's id.
 */
class MapViewModel(
    private val repository: HouseholdRepository,
    connectivity: StateFlow<Boolean>,
    private val clock: () -> Long = System::currentTimeMillis,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val mutable = MutableStateFlow(
        MapUiState(
            basemap = if (connectivity.value) BasemapMode.Online else BasemapMode.Offline,
            adjust = restoreDraft(),
        ),
    )
    val state = mutable.asStateFlow()

    init {
        viewModelScope.launch {
            combine(repository.households, repository.householdCoordinates) { households, coordinates ->
                households to coordinates
            }.collect { (households, coordinates) ->
                val pins = households.filter { !it.archived }.mapNotNull { household ->
                    coordinates[household.id.value]?.let { coordinate ->
                        HouseholdPin(
                            householdId = household.id,
                            displayCode = household.displayCode,
                            headName = household.headName,
                            address = household.address,
                            coordinate = coordinate,
                            outOfArea = MapRules.outOfArea(coordinate),
                        )
                    }
                }.sortedBy { it.displayCode }
                val untagged = households.filter { !it.archived && it.id.value !in coordinates }.map {
                    UntaggedHousehold(it.id, it.displayCode, it.headName)
                }.sortedBy { it.displayCode }
                // A deleted or archived selection closes its card instead of pointing at nothing.
                val selected = mutable.value.selectedId?.takeIf { id -> households.any { it.id == id && !it.archived } }
                mutable.value = mutable.value.copy(loading = false, pins = pins, untagged = untagged, selectedId = selected)
            }
        }
        viewModelScope.launch {
            connectivity.collect { online ->
                mutable.value = mutable.value.copy(basemap = if (online) BasemapMode.Online else BasemapMode.Offline)
            }
        }
    }

    fun select(id: HouseholdId?) {
        if (mutable.value.writePhase == LocationWritePhase.Saving) return
        mutable.value = mutable.value.copy(selectedId = id, message = "")
    }

    /** Opens the pin editor seeded with the saved coordinate. */
    fun startAdjust(id: HouseholdId) {
        if (mutable.value.writePhase == LocationWritePhase.Saving) return
        val pin = mutable.value.pins.firstOrNull { it.householdId == id } ?: return
        mutable.value = mutable.value.copy(
            selectedId = id,
            writePhase = LocationWritePhase.Idle,
            message = "",
            adjust = PinAdjustDraft(
                householdId = id,
                displayCode = pin.displayCode,
                latitudeText = MapRules.formatCoordinate(pin.coordinate.latitude),
                longitudeText = MapRules.formatCoordinate(pin.coordinate.longitude),
            ),
        )
        rememberDraft()
    }

    /**
     * A tap or drag on the map placed the pin at [latitude]/[longitude]. The draft
     * fields update so the enumerator always sees the exact value a save would store.
     */
    fun placePin(latitude: Double, longitude: Double) {
        val draft = mutable.value.adjust ?: return
        if (mutable.value.writePhase == LocationWritePhase.Saving) return
        mutable.value = mutable.value.copy(adjust = draft.copy(
            latitudeText = MapRules.formatCoordinate(latitude),
            longitudeText = MapRules.formatCoordinate(longitude),
            latitudeError = null,
            longitudeError = null,
        ))
        rememberDraft()
    }

    fun editLatitude(text: String) {
        val draft = mutable.value.adjust ?: return
        if (mutable.value.writePhase == LocationWritePhase.Saving) return
        mutable.value = mutable.value.copy(adjust = draft.copy(latitudeText = text, latitudeError = null))
        rememberDraft()
    }

    fun editLongitude(text: String) {
        val draft = mutable.value.adjust ?: return
        if (mutable.value.writePhase == LocationWritePhase.Saving) return
        mutable.value = mutable.value.copy(adjust = draft.copy(longitudeText = text, longitudeError = null))
        rememberDraft()
    }

    /**
     * Saves the adjusted pin. Validation order matches the location card (parse, then
     * bounds) plus the working-extent rule for new map placements. A rejected save
     * keeps the draft so nothing the enumerator moved is lost.
     */
    fun saveAdjust() {
        val current = mutable.value
        val draft = current.adjust ?: return
        if (current.writePhase == LocationWritePhase.Saving) return
        val latitude = LocationRules.parse(draft.latitudeText)
        val longitude = LocationRules.parse(draft.longitudeText)
        if (latitude == null || longitude == null) {
            mutable.value = current.copy(adjust = draft.copy(
                latitudeError = if (latitude == null) "Latitude must be a number between -90 and 90." else null,
                longitudeError = if (longitude == null) "Longitude must be a number between -180 and 180." else null,
                attempt = draft.attempt + 1,
            ))
            return
        }
        val provenanceProblem = LocationRules.coordinateProblem(latitude, longitude, null)
        val outsideExtent = MapRules.extentProblem(latitude, longitude)
        if (provenanceProblem != null || outsideExtent != null) {
            mutable.value = current.copy(adjust = draft.copy(
                latitudeError = provenanceProblem?.takeIf { it.startsWith("Latitude") },
                longitudeError = provenanceProblem?.takeIf { it.startsWith("Longitude") } ?: outsideExtent,
                attempt = draft.attempt + 1,
            ))
            return
        }
        mutable.value = current.copy(writePhase = LocationWritePhase.Saving, message = "Saving pin position…")
        // Manual provenance: no measured accuracy; the capture time is the adjustment moment.
        val coordinate = Coordinate(latitude, longitude, accuracyMetres = null, capturedAt = clock(), source = CoordinateSource.Manual)
        viewModelScope.launch {
            val result = repository.saveCoordinate(draft.householdId, coordinate)
            // A rejected write keeps the draft on screen; an accepted one closes it.
            mutable.value = mutable.value.copy(
                writePhase = if (result is WriteResult.Saved) LocationWritePhase.Saved else LocationWritePhase.Failed,
                message = when (result) {
                    is WriteResult.Saved -> "Pin position saved on this device."
                    is WriteResult.Failed -> result.message
                },
                adjust = if (result is WriteResult.Saved) null else mutable.value.adjust,
            )
            rememberDraft()
        }
    }

    fun cancelAdjust() {
        if (mutable.value.writePhase == LocationWritePhase.Saving) return
        mutable.value = mutable.value.copy(adjust = null, writePhase = LocationWritePhase.Idle, message = "")
        rememberDraft()
    }

    /** The screen could not hand the coordinates to any external map application. */
    fun reportExternalMapUnavailable() {
        mutable.value = mutable.value.copy(
            message = "No map application is installed on this device. The coordinates stay saved here.",
        )
    }

    private fun restoreDraft(): PinAdjustDraft? {
        if (savedState.get<Boolean>(DRAFT_KEY) != true) return null
        val id = savedState.get<String>(DRAFT_ID) ?: return null
        return PinAdjustDraft(
            householdId = HouseholdId(id),
            displayCode = savedState[DRAFT_CODE] ?: "",
            latitudeText = savedState[DRAFT_LAT] ?: "",
            longitudeText = savedState[DRAFT_LON] ?: "",
        )
    }

    private fun rememberDraft() {
        val draft = mutable.value.adjust
        savedState[DRAFT_KEY] = draft != null
        savedState[DRAFT_ID] = draft?.householdId?.value
        savedState[DRAFT_CODE] = draft?.displayCode
        savedState[DRAFT_LAT] = draft?.latitudeText
        savedState[DRAFT_LON] = draft?.longitudeText
    }

    private companion object {
        const val DRAFT_KEY = "map.adjust"
        const val DRAFT_ID = "map.adjust.id"
        const val DRAFT_CODE = "map.adjust.code"
        const val DRAFT_LAT = "map.adjust.lat"
        const val DRAFT_LON = "map.adjust.lon"
    }
}
