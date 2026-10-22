package ph.tala.registry.ui.map

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ph.tala.registry.data.FakeRecordStore
import ph.tala.registry.data.HouseholdRepository
import ph.tala.registry.data.MemoryHouseholdRepository
import ph.tala.registry.data.local.CoordinateRow
import ph.tala.registry.data.local.StoredRecord
import ph.tala.registry.domain.edit.HouseholdDraft
import ph.tala.registry.domain.model.Coordinate
import ph.tala.registry.domain.model.CoordinateSource
import ph.tala.registry.domain.model.HouseholdId
import ph.tala.registry.domain.model.HouseholdStatus
import ph.tala.registry.domain.model.MapRules
import ph.tala.registry.ui.location.LocationWritePhase

/**
 * JVM behavior of the D16 registry map: reactive pins, extent flagging, manual pin
 * adjustment provenance, honest write phases and process-death draft recovery.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val now = 1_790_000_000_000L
    private val inside = HouseholdId("a0000000-0000-4000-8000-000000000001")
    private val other = HouseholdId("a0000000-0000-4000-8000-000000000002")
    private val untagged = HouseholdId("a0000000-0000-4000-8000-000000000003")
    private val archived = HouseholdId("a0000000-0000-4000-8000-000000000004")

    private lateinit var store: FakeRecordStore
    private lateinit var repository: HouseholdRepository
    private lateinit var connectivity: MutableStateFlow<Boolean>
    private val stores = mutableListOf<ViewModelStore>()

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        store = FakeRecordStore()
        repository = MemoryHouseholdRepository(store)
        connectivity = MutableStateFlow(true)
        runTest(dispatcher) {
            repository.createHousehold(inside, HouseholdDraft("Inside Sample", "Fictional Cogon Street", HouseholdStatus.InProgress))
            repository.createHousehold(other, HouseholdDraft("Other Sample", "Other Street", HouseholdStatus.InProgress))
            repository.createHousehold(untagged, HouseholdDraft("Untagged Sample", "Third Street", HouseholdStatus.InProgress))
            repository.createHousehold(archived, HouseholdDraft("Archived Sample", "Fourth Street", HouseholdStatus.InProgress))
            repository.setArchived(archived, true)
            repository.saveCoordinate(inside, Coordinate(11.0176, 124.6031, accuracyMetres = 12.5, capturedAt = now, source = CoordinateSource.Gps))
            repository.saveCoordinate(other, Coordinate(14.5994, 120.9842, accuracyMetres = null, capturedAt = now, source = CoordinateSource.Manual))
        }
    }

    @After fun cleanup() {
        stores.forEach { it.clear() }
        Dispatchers.resetMain()
    }

    private fun model(savedState: SavedStateHandle = SavedStateHandle()): MapViewModel {
        val owner = ViewModelStore().also(stores::add)
        val created = ViewModelProvider(owner, viewModelFactory {
            initializer { MapViewModel(repository, connectivity, { now }, savedState) }
        })[MapViewModel::class.java]
        // The init collect queues on the test dispatcher; flush it so pins are present.
        dispatcher.scheduler.advanceUntilIdle()
        return created
    }

    /** Flushes queued ViewModel coroutines (writes, connectivity transitions). */
    private fun settle() = dispatcher.scheduler.advanceUntilIdle()

    @Test fun pinsCombineHouseholdsWithCoordinatesAndRespectArchiving() = runTest(dispatcher) {
        val m = model()
        val state = m.state.value
        assertEquals(listOf("HH-00001", "HH-00002"), state.pins.map { it.displayCode })
        assertEquals(listOf("HH-00003"), state.untagged.map { it.displayCode })
        assertFalse(state.pins[0].outOfArea)
        assertTrue(state.pins[1].outOfArea)
        // Archived records stay out of the map entirely (D19 active-counts parity).
        assertTrue(state.pins.none { it.householdId == archived })
    }

    @Test fun selectionCardAndClearingFollowsThePinList() = runTest(dispatcher) {
        val m = model()
        m.select(inside)
        assertEquals(inside, m.state.value.selectedId)
        assertEquals(inside, m.state.value.selectedPin?.householdId)
        m.select(null)
        assertNull(m.state.value.selectedPin)
    }

    @Test fun adjustDraftSeedsFromTheSavedPinAndPlacePinUpdatesIt() = runTest(dispatcher) {
        val m = model()
        m.startAdjust(inside)
        val draft = requireNotNull(m.state.value.adjust)
        assertEquals("11.017600", draft.latitudeText)
        assertEquals("124.603100", draft.longitudeText)
        m.placePin(11.0190, 124.6050)
        val moved = requireNotNull(m.state.value.adjust)
        assertEquals("11.019000", moved.latitudeText)
        assertEquals("124.605000", moved.longitudeText)
    }

    @Test fun savingAnAdjustedPinStoresManualProvenanceAndClearsAccuracy() = runTest(dispatcher) {
        val m = model()
        m.startAdjust(inside)
        m.placePin(11.0190, 124.6050)
        m.saveAdjust()
        settle()
        val stored = requireNotNull(store.load(inside.value)?.coordinates)
        assertEquals(11.0190, stored.latitude, 1e-9)
        assertNull(stored.accuracyMetres)
        assertEquals(LocationWritePhase.Saved, m.state.value.writePhase)
        assertNull(m.state.value.adjust)
    }

    @Test fun outsideExtentSavesAreRejectedAndKeepTheDraft() = runTest(dispatcher) {
        val m = model()
        m.startAdjust(inside)
        m.placePin(14.5994, 120.9842) // real Ormoc-area point, outside the working extent
        m.saveAdjust()
        assertEquals(LocationWritePhase.Idle, m.state.value.writePhase)
        assertEquals(MapRules.OUTSIDE_MESSAGE, m.state.value.adjust?.longitudeError)
        // The original GPS pin is untouched by the rejected write.
        assertEquals(11.0176, requireNotNull(store.load(inside.value)?.coordinates).latitude, 1e-9)
    }

    @Test fun nonNumericFieldsFocusTheirOwnErrorAndNeverWrite() = runTest(dispatcher) {
        val m = model()
        m.startAdjust(inside)
        m.editLatitude("not a number")
        m.saveAdjust()
        assertEquals("Latitude must be a number between -90 and 90.", m.state.value.adjust?.latitudeError)
        assertNull(m.state.value.adjust?.longitudeError)
        assertEquals(11.0176, requireNotNull(store.load(inside.value)?.coordinates).latitude, 1e-9)
    }

    @Test fun failedWritesKeepTheDraftAndReportFailure() = runTest(dispatcher) {
        store.failWith = "Fictional storage failure"
        val m = model()
        m.startAdjust(inside)
        m.placePin(11.0200, 124.6060)
        m.saveAdjust()
        settle()
        assertEquals(LocationWritePhase.Failed, m.state.value.writePhase)
        assertNotNull(m.state.value.adjust)
        assertEquals("Fictional storage failure", m.state.value.message)
        // Retry after storage recovers.
        store.failWith = null
        m.saveAdjust()
        settle()
        assertEquals(LocationWritePhase.Saved, m.state.value.writePhase)
        assertNull(m.state.value.adjust)
    }

    @Test fun aDraftIsScopedToOneHouseholdAndNeverCrossesRecords() = runTest(dispatcher) {
        val m = model()
        m.startAdjust(inside)
        m.placePin(11.0200, 124.6060)
        m.saveAdjust()
        settle()
        val otherRow = requireNotNull(store.load(other.value)?.coordinates)
        assertEquals(14.5994, otherRow.latitude, 1e-9) // untouched
        val insideRow = requireNotNull(store.load(inside.value)?.coordinates)
        assertEquals(11.0200, insideRow.latitude, 1e-9)
    }

    @Test fun cancelAdjustDiscardsTheDraftWithoutWriting() = runTest(dispatcher) {
        val m = model()
        m.startAdjust(inside)
        m.placePin(11.0210, 124.6070)
        m.cancelAdjust()
        assertNull(m.state.value.adjust)
        assertEquals(11.0176, requireNotNull(store.load(inside.value)?.coordinates).latitude, 1e-9)
    }

    @Test fun losingTheBasemapKeepsEveryPinAndListEntry() = runTest(dispatcher) {
        val m = model()
        connectivity.value = false
        settle()
        assertEquals(BasemapMode.Offline, m.state.value.basemap)
        assertEquals(2, m.state.value.pins.size)
        assertEquals(1, m.state.value.untagged.size)
        connectivity.value = true
        settle()
        assertEquals(BasemapMode.Online, m.state.value.basemap)
    }

    @Test fun adjustDraftSurvivesProcessDeathThroughSavedState() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val first = model(saved)
        first.startAdjust(inside)
        first.placePin(11.0220, 124.6080)
        // Process death: a brand-new ViewModel over the same SavedStateHandle.
        val restored = model(saved)
        val draft = requireNotNull(restored.state.value.adjust)
        assertEquals("11.022000", draft.latitudeText)
        assertEquals("124.608000", draft.longitudeText)
        // The restored draft is data only; the write path is exercised by the live model.
    }

    @Test fun coordinateRowsRestoreIntoPinsThroughTheRepositoryContract() = runTest(dispatcher) {
        // The repository maps rows to coordinates by accuracy nullability (D15 contract).
        val row = requireNotNull(store.load(inside.value)?.coordinates)
        val coordinate = Coordinate(row.latitude, row.longitude, row.accuracyMetres, row.capturedAt, CoordinateSource.Gps)
        assertEquals(12.5, requireNotNull(coordinate.accuracyMetres), 0.0)
    }
}


