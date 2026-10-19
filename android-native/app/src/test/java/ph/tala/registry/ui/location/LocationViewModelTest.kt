package ph.tala.registry.ui.location

import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import ph.tala.registry.data.*
import ph.tala.registry.data.local.*
import ph.tala.registry.domain.edit.HouseholdDraft
import ph.tala.registry.domain.model.*
import ph.tala.registry.location.*

@OptIn(ExperimentalCoroutinesApi::class)
class LocationViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val stores = mutableListOf<ViewModelStore>()
    private val id = HouseholdId("a0000000-0000-4000-8000-000000000001")
    private val other = HouseholdId("a0000000-0000-4000-8000-000000000002")
    private val now = 1_790_000_000_000L
    private lateinit var store: FakeRecordStore
    private lateinit var repo: HouseholdRepository
    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        store = FakeRecordStore()
        repo = MemoryHouseholdRepository(store)
        runTest(dispatcher) {
            repo.createHousehold(id, HouseholdDraft("Sample A", "Sample Street", HouseholdStatus.InProgress))
            repo.createHousehold(other, HouseholdDraft("Sample B", "Other Street", HouseholdStatus.InProgress))
        }
    }
    @After fun cleanup() { stores.forEach { it.clear() }; Dispatchers.resetMain() }
    private fun model(client: FakeLocationClient, state: SavedStateHandle = SavedStateHandle(), household: HouseholdId = id, repository: HouseholdRepository = repo): LocationViewModel {
        val owner = ViewModelStore().also(stores::add)
        return ViewModelProvider(owner, viewModelFactory { initializer {
            LocationViewModel(household, repository, client, client, { now }, savedState = state)
        } })[LocationViewModel::class.java]
    }
    private fun fix(time: Long = now) = FixOutcome.Fix(14.5,120.9,12.5,time)
    @Test fun readFailurePreservesManualDraftAndCanRetry() = runTest(dispatcher) {
        var failRead = true
        val failingStore = object : RecordStore by store {
            override suspend fun load(id: String): StoredRecord? {
                if (failRead) throw IllegalStateException("Fictional storage read failure")
                return store.load(id)
            }
        }
        val m = model(FakeLocationClient(), repository = MemoryHouseholdRepository(failingStore))
        runCurrent(); m.openManual(); m.editManualLatitude("14.5"); m.editManualLongitude("120.9")
        m.saveManual(); runCurrent()
        assertEquals(LocationWritePhase.Failed, m.state.value.writePhase)
        assertEquals("14.5", m.state.value.manual?.latitude)
        assertNull(store.load(id.value)?.coordinates)
        failRead = false; m.saveManual(); runCurrent()
        assertEquals(LocationWritePhase.Saved, m.state.value.writePhase)
        assertNull(m.state.value.manual)
        assertEquals(14.5, requireNotNull(store.load(id.value)?.coordinates).latitude, 0.0)
    }
    @Test fun offNeverRequests() = runTest(dispatcher) {
        val c=FakeLocationClient(enabled=false); val m=model(c); runCurrent()
        assertFalse(m.capture()); assertEquals(LocationCapture.LocationOff,m.state.value.capture); assertTrue(c.requests.isEmpty())
    }
    @Test fun denialAndPermanentDenialRemainRetryable() = runTest(dispatcher) {
        val c=FakeLocationClient(fine=false,coarse=false); val m=model(c); runCurrent()
        assertTrue(m.capture()); assertFalse(m.capture())
        m.onPermissionResult(false,false,false); assertEquals(LocationCapture.Denied,m.state.value.capture)
        assertTrue(m.capture()); m.onPermissionResult(false,false,true)
        assertEquals(LocationCapture.PermanentlyDenied,m.state.value.capture); assertTrue(c.requests.isEmpty())
    }
    @Test fun coarseOnlySavesOriginalAccuracyAndTime() = runTest(dispatcher) {
        val c=FakeLocationClient(fine=false,coarse=true); val m=model(c); runCurrent(); m.capture()
        c.deliver(fix(now-500)); runCurrent()
        val saved=requireNotNull(m.state.value.coordinate)
        assertEquals(12.5,saved.accuracyMetres!!,0.0); assertEquals(now-500,saved.capturedAt)
        assertEquals(LocationWritePhase.Saved,m.state.value.writePhase)
    }
    @Test fun timeoutRejectsCallbackEvenFromIgnoringClient() = runTest(dispatcher) {
        val c=FakeLocationClient(); val m=model(c); runCurrent(); m.capture()
        advanceTimeBy(20_000); runCurrent(); assertEquals(LocationCapture.TimedOut,m.state.value.capture)
        c.deliver(fix()); runCurrent(); assertNull(store.load(id.value)?.coordinates)
    }
    @Test fun newerRequestRejectsOldFixAndDuplicateDelivery() = runTest(dispatcher) {
        val c=FakeLocationClient(); val m=model(c); runCurrent(); m.capture(); val old=c.requests.last()
        m.capture(); c.deliver(fix(),old); runCurrent(); assertNull(store.load(id.value)?.coordinates)
        c.deliver(fix()); runCurrent(); c.deliver(FixOutcome.Fix(1.0,2.0,3.0,now)); runCurrent()
        assertEquals(14.5,requireNotNull(store.load(id.value)?.coordinates).latitude,0.0)
    }
    @Test fun manualEditAndLatePermissionCannotRestartCapture() = runTest(dispatcher) {
        val c=FakeLocationClient(fine=false,coarse=false); val m=model(c); runCurrent(); m.capture(); m.openManual()
        c.setPermissions(true,true); m.onPermissionResult(true,true,false); assertTrue(c.requests.isEmpty())
        m.editManualLatitude("14.5"); m.editManualLongitude("120.9"); m.saveManual(); runCurrent()
        assertNull(requireNotNull(m.state.value.coordinate).accuracyMetres)
    }
    @Test fun navigationRejectsLateFixAndPermission() = runTest(dispatcher) {
        val c=FakeLocationClient(); val m=model(c); runCurrent(); m.capture(); m.deactivate()
        c.deliver(fix()); runCurrent(); assertNull(store.load(id.value)?.coordinates)
        m.onPermissionResult(true,true,false); assertEquals(1,c.requests.size)
        m.activate(); m.capture(); c.deliver(fix()); runCurrent(); assertNotNull(store.load(id.value)?.coordinates)
    }
    @Test fun aLateFixCannotOverwriteAnOpenManualDraft() = runTest(dispatcher) {
        val c=FakeLocationClient(); val m=model(c); runCurrent(); m.capture(); m.openManual()
        m.editManualLatitude("14.7"); m.editManualLongitude("120.8")
        c.deliver(fix()); runCurrent()
        assertNull(store.load(id.value)?.coordinates); assertEquals("14.7",m.state.value.manual?.latitude)
        m.saveManual(); runCurrent(); assertEquals(14.7,requireNotNull(store.load(id.value)?.coordinates).latitude,0.0)
        assertNull(store.load(id.value)?.coordinates?.accuracyMetres)
    }
    @Test fun revokedPermissionAndStaleFixAreNotSaved() = runTest(dispatcher) {
        val c=FakeLocationClient(); val m=model(c); runCurrent(); m.capture(); c.setPermissions(false,false)
        c.deliver(fix()); runCurrent(); assertEquals(LocationCapture.Denied,m.state.value.capture)
        c.setPermissions(true,true); m.capture(); c.deliver(fix(now-31_000)); runCurrent()
        assertEquals(LocationCapture.Failed,m.state.value.capture); assertNull(store.load(id.value)?.coordinates)
    }
    @Test fun invalidAndFutureFixesAreRejected() = runTest(dispatcher) {
        val c=FakeLocationClient(); val m=model(c); runCurrent(); m.capture(); c.deliver(FixOutcome.Fix(Double.NaN,1.0,1.0,now)); runCurrent()
        assertEquals(LocationCapture.Failed,m.state.value.capture)
        m.capture(); c.deliver(fix(now+6_000)); runCurrent(); assertNull(store.load(id.value)?.coordinates)
    }
    @Test fun manualDraftSurvivesRestorationAndFailureRetry() = runTest(dispatcher) {
        val state=SavedStateHandle(); val c=FakeLocationClient(); val m=model(c,state); runCurrent()
        m.openManual(); m.editManualLatitude("14.5"); m.editManualLongitude("120.9")
        val copy=SavedStateHandle(state.keys().associateWith { state.get<Any?>(it) })
        val restored=model(c,copy); runCurrent(); assertEquals("14.5",restored.state.value.manual?.latitude)
        store.failWith="Disk failure"; restored.saveManual(); runCurrent()
        assertEquals(LocationWritePhase.Failed,restored.state.value.writePhase); assertNotNull(restored.state.value.manual)
        store.failWith=null; restored.saveManual(); runCurrent()
        assertEquals(LocationWritePhase.Saved,restored.state.value.writePhase); assertNull(restored.state.value.manual)
    }
    @Test fun manualBoundsAndSourceReplaceMeasuredFix() = runTest(dispatcher) {
        val c=FakeLocationClient(); val m=model(c); runCurrent(); m.capture(); c.deliver(fix(now-500)); runCurrent()
        m.openManual(); m.editManualLatitude("91"); m.saveManual(); assertNotNull(m.state.value.manual?.latitudeError)
        m.editManualLatitude("14.6"); m.saveManual(); runCurrent()
        val value=requireNotNull(m.state.value.coordinate)
        assertEquals(CoordinateSource.Manual,value.source); assertNull(value.accuracyMetres); assertEquals(now,value.capturedAt)
    }
    @Test fun twoHouseholdsStayIsolated() = runTest(dispatcher) {
        val c=FakeLocationClient(); val a=model(c); val b=model(FakeLocationClient(),household=other); runCurrent()
        a.capture(); c.deliver(fix()); runCurrent(); assertNotNull(a.state.value.coordinate); assertNull(b.state.value.coordinate)
        assertNull(store.load(other.value)?.coordinates)
    }
    @Test fun acceptedWriteCompletesAcrossNavigationWithoutStuckSaving() = runTest(dispatcher) {
        val gate=CompletableDeferred<Unit>()
        val slow=object: HouseholdRepository by repo {
            override suspend fun saveCoordinate(householdId: HouseholdId,coordinate: Coordinate): WriteResult { gate.await(); return repo.saveCoordinate(householdId,coordinate) }
        }
        val c=FakeLocationClient(); val m=model(c,repository=slow); runCurrent(); m.capture(); c.deliver(fix()); runCurrent()
        assertEquals(LocationWritePhase.Saving,m.state.value.writePhase); m.deactivate(); m.activate(); gate.complete(Unit); runCurrent()
        assertEquals(LocationWritePhase.Saved,m.state.value.writePhase)
    }
    @Test fun missingProviderAndModelDisposalNeverSave() = runTest(dispatcher) {
        val c=FakeLocationClient(); val m=model(c); runCurrent(); m.capture(); c.deliver(FixOutcome.Unavailable); runCurrent()
        assertEquals(LocationCapture.Failed,m.state.value.capture)
        m.capture(); stores.last().clear(); c.deliver(fix()); runCurrent(); assertNull(store.load(id.value)?.coordinates)
    }
}
