package ph.tala.registry.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Guards the exported Room schema. Version 1 has no migration yet, so this test rebuilds
 * the shipped database from the exported JSON, validates it through the migration harness,
 * and then opens it with Room to prove a pre-existing file is reopened non-destructively.
 * Destructive migration is not enabled, so a future version mismatch fails loudly instead
 * of wiping saved records.
 */
class SchemaMigrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val appContext = InstrumentationRegistry.getInstrumentation().targetContext

    @get:Rule val helper = MigrationTestHelper(instrumentation, RegistryDatabase::class.java)

    @Test fun exportedVersionOneSchemaRebuildsAndRoomReopensItWithoutDataLoss() {
        val created = helper.createDatabase(TEST_DB, 1)
        created.execSQL(
            "INSERT INTO households (id, displayCode, headName, address, status) VALUES (?, ?, ?, ?, ?)",
            arrayOf(SampleRecords.HOUSEHOLD, "HH-00777", "Luna Reyes", "2 Sampaguita Street", "NotStarted"),
        )
        created.close()

        // Validates the exported schema against a database at the same version with no migration.
        helper.runMigrationsAndValidate(TEST_DB, 1, true).close()

        // Room must accept the existing file and read the row; no rebuild, no data loss.
        val holder = if (appContext.getDatabasePath(TEST_DB).exists()) {
            appContext
        } else {
            instrumentation.context
        }
        val database = RegistryDatabase.open(holder, TEST_DB)
        val row = try {
            runBlocking { database.records().household(SampleRecords.HOUSEHOLD) }
        } finally {
            database.close()
            holder.deleteDatabase(TEST_DB)
        }
        assertEquals("HH-00777", requireNotNull(row).displayCode)
        assertEquals("Luna Reyes", row.headName)
        assertEquals("NotStarted", row.status)
    }

    private companion object {
        const val TEST_DB = "tala-registry-schema-test.db"
    }
}
