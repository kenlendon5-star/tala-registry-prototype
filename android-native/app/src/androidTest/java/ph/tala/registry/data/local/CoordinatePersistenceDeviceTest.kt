package ph.tala.registry.data.local

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import ph.tala.registry.data.RoomHouseholdRepository
import ph.tala.registry.domain.model.*

class CoordinatePersistenceDeviceTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private val name="tala-day15-coordinate-test.db"
    @Test fun coordinateWritesPreserveGraphAndOtherHouseholdAcrossReopen() = runBlocking {
        context.deleteDatabase(name)
        var db=RegistryDatabase.open(context,name)
        try {
            var store=RoomRecordStore(db)
            val before=SampleRecords.record(); val other=SampleRecords.otherRecord()
            assertEquals(WriteResult.Saved,store.save(before)); assertEquals(WriteResult.Saved,store.save(other))
            val repo=RoomHouseholdRepository(store)
            val id=HouseholdId(before.household.id)
            val point=Coordinate(14.5,120.9,12.5,1_790_000_000_000,CoordinateSource.Gps)
            assertEquals(WriteResult.Saved,repo.saveCoordinate(id,point))
            assertEquals(before.copy(coordinates=CoordinateRow(id.value,14.5,120.9,12.5,point.capturedAt)),store.load(id.value))
            assertEquals(other,store.load(other.household.id))
            db.close(); db=RegistryDatabase.open(context,name); store=RoomRecordStore(db)
            val reopened=RoomHouseholdRepository(store)
            assertEquals(point,reopened.observeCoordinate(id).first())
            val manual=point.copy(latitude=14.6,accuracyMetres=null,capturedAt=point.capturedAt+1,source=CoordinateSource.Manual)
            assertEquals(WriteResult.Saved,reopened.saveCoordinate(id,manual))
            assertEquals(manual,reopened.observeCoordinate(id).first())
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun failedCoordinateTransactionRollsBackWholeGraph() = runBlocking {
        context.deleteDatabase(name); val db=RegistryDatabase.open(context,name)
        try {
            val store=RoomRecordStore(db); val before=SampleRecords.record()
            assertEquals(WriteResult.Saved,store.save(before))
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_location BEFORE INSERT ON coordinates BEGIN SELECT RAISE(ABORT, 'simulated write failure'); END")
            val result=RoomHouseholdRepository(store).saveCoordinate(HouseholdId(before.household.id),Coordinate(1.0,2.0,null,1_790_000_000_000,CoordinateSource.Manual))
            assertTrue(result is WriteResult.Failed); assertEquals(before,store.load(before.household.id))
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun sourceValidationAndArchivedGuardDoNotModifyStoredData() = runBlocking {
        context.deleteDatabase(name); val db=RegistryDatabase.open(context,name)
        try {
            val store=RoomRecordStore(db); val before=SampleRecords.record()
            assertEquals(WriteResult.Saved,store.save(before)); val repo=RoomHouseholdRepository(store); val id=HouseholdId(before.household.id)
            val bad=Coordinate(1.0,2.0,5.0,1_790_000_000_000,CoordinateSource.Manual)
            assertTrue(repo.saveCoordinate(id,bad) is WriteResult.Failed)
            assertTrue(repo.saveCoordinate(id,bad.copy(latitude=Double.NaN)) is WriteResult.Failed)
            assertEquals(before,store.load(id.value))
            assertEquals(WriteResult.Saved,repo.setArchived(id,true))
            assertTrue(repo.saveCoordinate(id,bad.copy(source=CoordinateSource.Gps)) is WriteResult.Failed)
            assertEquals(before.coordinates,store.load(id.value)?.coordinates)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
