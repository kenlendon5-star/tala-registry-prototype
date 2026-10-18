package ph.tala.registry.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Version 3 of the app-private registry database. Schemas are exported to
 * `app/schemas/` (and copied into androidTest assets) so migrations can be tested.
 * Version 2 adds archiving; version 3 adds dated, ordered visit history.
 * Existing visit rows retain their IDs, outcomes and callbacks, with unknown dates left blank. No destructive
 * migration is configured: an unsupported version fails loudly instead of deleting
 * saved records. Small key/value settings do not belong here; they go to DataStore
 * when the app first needs them.
 */
@Database(entities = [HouseholdRow::class, MemberRow::class, InterviewRow::class, VisitRow::class, HouseholdAnswerRow::class, MemberAnswerRow::class, CoordinateRow::class, AttachmentRow::class], version = 3, exportSchema = true)
abstract class RegistryDatabase : RoomDatabase() {
    abstract fun records(): RegistryDao

    companion object {
        /** Adds the archive marker; every existing household keeps its data and stays active. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE households ADD COLUMN archivedAt INTEGER")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE visits ADD COLUMN visitedOn TEXT NOT NULL DEFAULT ''")
                connection.execSQL("ALTER TABLE visits ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
                connection.execSQL("ALTER TABLE visits ADD COLUMN recordedAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun open(context: Context, name: String = "tala-registry.db"): RegistryDatabase =
            Room.databaseBuilder(context.applicationContext, RegistryDatabase::class.java, name)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
