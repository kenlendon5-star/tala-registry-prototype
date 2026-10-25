package ph.tala.registry.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test fun versionTwoVisitAndAttachmentsSurviveDatedHistoryMigration() {
        val created = helper.createDatabase(TEST_DB, 2)
        created.execSQL("INSERT INTO households (id, displayCode, headName, address, status, archivedAt) VALUES (?, 'HH-00777', 'Luna Reyes', 'Sample Street', 'Callback', NULL)", arrayOf(SampleRecords.HOUSEHOLD))
        val interview = "d1300000-0000-4000-8000-000000000191"
        val visit = "d1300000-0000-4000-8000-000000000192"
        created.execSQL("INSERT INTO interviews (id, householdId, questionnaireVersion) VALUES (?, ?, 'tala-demo-v1')", arrayOf(interview, SampleRecords.HOUSEHOLD))
        created.execSQL("INSERT INTO visits (id, interviewId, outcome, callbackAt) VALUES (?, ?, 'Callback', '2026-10-20T10:30')", arrayOf(visit, interview))
        created.execSQL("INSERT INTO attachments (id, householdId, memberId, kind, relativePath, mimeType) VALUES (?, ?, NULL, 'signature', 'attachments/legacy.png', 'image/png')", arrayOf("d1300000-0000-4000-8000-000000000193", SampleRecords.HOUSEHOLD))
        created.close()
        helper.runMigrationsAndValidate(TEST_DB, 3, true, RegistryDatabase.MIGRATION_2_3).close()
        val holder = holderFor()
        val database = RegistryDatabase.open(holder, TEST_DB)
        try {
            runBlocking {
                val migrated = database.records().visits(interview).single()
                assertEquals(visit, migrated.id)
                assertEquals("Callback", migrated.outcome)
                assertEquals("2026-10-20T10:30", migrated.callbackAt)
                assertEquals("", migrated.visitedOn)
                assertEquals(0L, migrated.recordedAt)
                assertTrue(database.records().attachments(SampleRecords.HOUSEHOLD).any { it.relativePath == "attachments/legacy.png" })
            }
        } finally { database.close(); holder.deleteDatabase(TEST_DB) }
    }

    @Test fun versionThreeMembersReceiveStableRosterOrder() {
        val created = helper.createDatabase(TEST_DB, 3)
        created.execSQL("INSERT INTO households (id, displayCode, headName, address, status, archivedAt) VALUES (?, 'HH-00777', 'Luna Reyes', 'Sample Street', 'InProgress', NULL)", arrayOf(SampleRecords.HOUSEHOLD))
        val first = "10000000-0000-4000-8000-000000000001"
        val second = "20000000-0000-4000-8000-000000000002"
        created.execSQL("INSERT INTO members (id, householdId, displayName) VALUES (?, ?, 'First Member')", arrayOf(first, SampleRecords.HOUSEHOLD))
        created.execSQL("INSERT INTO members (id, householdId, displayName) VALUES (?, ?, 'Second Member')", arrayOf(second, SampleRecords.HOUSEHOLD))
        created.close()

        helper.runMigrationsAndValidate(TEST_DB, 4, true, RegistryDatabase.MIGRATION_3_4).close()
        val holder = holderFor()
        val database = RegistryDatabase.open(holder, TEST_DB)
        try {
            runBlocking {
                val members = database.records().members(SampleRecords.HOUSEHOLD)
                assertEquals(listOf(first, second), members.map { it.id })
                assertEquals(listOf(0, 1), members.map { it.sortOrder })
            }
        } finally { database.close(); holder.deleteDatabase(TEST_DB) }
    }

    @Test fun versionFourAddsDurableSyncOutboxWithoutChangingHouseholds() {
        val created = helper.createDatabase(TEST_DB, 4)
        created.execSQL("INSERT INTO households (id, displayCode, headName, address, status, archivedAt) VALUES (?, 'HH-00777', 'Luna Reyes', 'Sample Street', 'InProgress', NULL)", arrayOf(SampleRecords.HOUSEHOLD))
        created.close()

        helper.runMigrationsAndValidate(TEST_DB, 5, true, RegistryDatabase.MIGRATION_4_5).close()
        val holder = holderFor()
        val database = RegistryDatabase.open(holder, TEST_DB)
        try {
            runBlocking {
                database.records().putSyncOutbox(listOf(SyncOutboxRow(SampleRecords.HOUSEHOLD, 123L)))
                database.records().putSyncRevision(SyncRevisionRow("91000000-0000-4000-8000-000000000001", SampleRecords.HOUSEHOLD, 2L))
                assertEquals(SampleRecords.HOUSEHOLD, database.records().pendingSyncs().single().householdId)
                assertEquals(2L, database.records().syncRevision("91000000-0000-4000-8000-000000000001", SampleRecords.HOUSEHOLD))
                assertEquals("Luna Reyes", database.records().household(SampleRecords.HOUSEHOLD)?.headName)
            }
        } finally { database.close(); holder.deleteDatabase(TEST_DB) }
    }

    private fun holderFor() = if (appContext.getDatabasePath(TEST_DB).exists()) appContext else instrumentation.context

    private companion object {
        const val TEST_DB = "tala-registry-schema-test.db"
    }
}
