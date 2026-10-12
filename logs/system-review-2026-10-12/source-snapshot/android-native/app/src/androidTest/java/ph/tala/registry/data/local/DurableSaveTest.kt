package ph.tala.registry.data.local

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * Deliberately split into two steps so the evidence run can seed a record, force-stop or
 * reboot the phone, and then verify it from a fresh process. NAME_ASCENDING keeps a plain
 * full-suite run in seed-then-verify order.
 *
 * Run sequence for Day 5 evidence:
 *   am instrument -e class ph.tala.registry.data.local.DurableSaveTest#test1_seedSavedRecord ...
 *   adb shell am force-stop ph.tala.registry   (and, separately, adb reboot)
 *   am instrument -e class ph.tala.registry.data.local.DurableSaveTest#test2_savedRecordSurvivesProcessRestartAndReboot ...
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class DurableSaveTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun test1_seedSavedRecord() {
        context.deleteDatabase(SampleRecords.DB_NAME)
        val database = RegistryDatabase.open(context, SampleRecords.DB_NAME)
        val result = try {
            runBlocking { RoomRecordStore(database).save(SampleRecords.record()) }
        } finally {
            database.close()
        }
        assertEquals(WriteResult.Saved, result)
        assertTrue(
            "The seeded database file must be on disk before restart checks.",
            context.getDatabasePath(SampleRecords.DB_NAME).exists(),
        )
    }

    @Test fun test2_savedRecordSurvivesProcessRestartAndReboot() {
        assertTrue(
            "Run DurableSaveTest#test1_seedSavedRecord first; the evidence database is missing.",
            context.getDatabasePath(SampleRecords.DB_NAME).exists(),
        )
        val database = RegistryDatabase.open(context, SampleRecords.DB_NAME)
        val loaded = try {
            runBlocking { RoomRecordStore(database).load(SampleRecords.HOUSEHOLD) }
        } finally {
            database.close()
        }
        // Recomputed from the same fixed inputs, so this compares content, not object identity.
        assertEquals(SampleRecords.record(), loaded)
        // Keep the database so a second verification run after a reboot can read it again.
    }
}
