package ph.tala.registry.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * Guards the exported Room schema. Version 2 adds the nullable `households.archivedAt`
 * column, so this test rebuilds a version-1 file from the exported JSON, runs the real
 * 1 → 2 migration through the migration harness, and then opens the file with Room to
 * prove an existing install is migrated non-destructively. Destructive migration is not
 * enabled, so an unsupported version fails loudly instead of wiping saved records.
 */
class SchemaMigrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val appContext = InstrumentationRegistry.getInstrumentation().targetContext

    @get:Rule val helper = MigrationTestHelper(instrumentation, RegistryDatabase::class.java)

    @Test fun versionOneFixtureMigratesToVersionTwoWithoutDataLoss() {
        val created = helper.createDatabase(TEST_DB, 1)
        insertSampleHousehold(created)
        created.close()

        // Validates the exported version-2 schema against a database migrated by the real 1 → 2 step.
        helper.runMigrationsAndValidate(TEST_DB, 2, true, RegistryDatabase.MIGRATION_1_2).close()

        val holder = holderFor()
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
        // Every migrated household stays active until it is explicitly archived.
        assertNull(row.archivedAt)
    }

    @Test fun roomReopensAVersionOneFileByRunningTheMigrationItself() {
        val created = helper.createDatabase(TEST_DB, 1)
        insertSampleHousehold(created)
        created.close()

        val holder = holderFor()
        val database = RegistryDatabase.open(holder, TEST_DB)
        val row = try {
            runBlocking { database.records().household(SampleRecords.HOUSEHOLD) }
        } finally {
            database.close()
            holder.deleteDatabase(TEST_DB)
        }
        assertEquals("HH-00777", requireNotNull(row).displayCode)
        assertNull(row.archivedAt)
    }

    private fun insertSampleHousehold(created: androidx.sqlite.db.SupportSQLiteDatabase) {
        created.execSQL(
            "INSERT INTO households (id, displayCode, headName, address, status) VALUES (?, ?, ?, ?, ?)",
            arrayOf(SampleRecords.HOUSEHOLD, "HH-00777", "Luna Reyes", "2 Sampaguita Street", "NotStarted"),
        )
    }

    private fun holderFor() = if (appContext.getDatabasePath(TEST_DB).exists()) appContext else instrumentation.context

    private companion object {
        const val TEST_DB = "tala-registry-schema-test.db"
    }
}
