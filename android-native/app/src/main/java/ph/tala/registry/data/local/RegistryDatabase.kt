package ph.tala.registry.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Version 1 of the app-private registry database. Schemas are exported to
 * `app/schemas/` (and copied into androidTest assets) so migrations can be tested.
 * No destructive migration is configured: an unsupported version fails loudly instead
 * of deleting saved records. Small key/value settings do not belong here; they go to
 * DataStore when the app first needs them.
 */
@Database(entities = [HouseholdRow::class, MemberRow::class, InterviewRow::class, VisitRow::class, HouseholdAnswerRow::class, MemberAnswerRow::class, CoordinateRow::class, AttachmentRow::class], version = 1, exportSchema = true)
abstract class RegistryDatabase : RoomDatabase() {
    abstract fun records(): RegistryDao

    companion object {
        fun open(context: Context, name: String = "tala-registry.db"): RegistryDatabase =
            Room.databaseBuilder(context.applicationContext, RegistryDatabase::class.java, name).build()
    }
}
