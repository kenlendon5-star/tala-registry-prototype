package ph.tala.registry.ui

import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ph.tala.registry.data.RoomHouseholdRepository
import ph.tala.registry.data.local.CoordinateRow
import ph.tala.registry.data.local.HouseholdRow
import ph.tala.registry.data.local.InterviewRow
import ph.tala.registry.data.local.MemberRow
import ph.tala.registry.data.local.RegistryDatabase
import ph.tala.registry.data.local.RoomRecordStore
import ph.tala.registry.data.local.StoredRecord
import ph.tala.registry.data.local.WriteResult
import ph.tala.registry.ui.map.MapViewModel
import java.io.File
import java.util.UUID

/** D20 representative local-data timing harness. All generated identities and names are fictional. */
class PerformanceDatasetDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val databaseName = "d20-performance.db"

    @Test fun twoHundredHouseholdsAndOneThousandMembersMeetProvisionalTargets() {
        runBlocking {
        context.deleteDatabase(databaseName)
        val records = dataset()
        assertEquals(200, records.size)
        assertEquals(1_000, records.sumOf { it.members.size })

        var database = RegistryDatabase.open(context, databaseName)
        val seedStart = SystemClock.elapsedRealtimeNanos()
        assertEquals(WriteResult.Saved, RoomRecordStore(database).restore(records))
        val seedMs = elapsedMilliseconds(seedStart)
        database.close()

        lateinit var repository: RoomHouseholdRepository
        val startupStart = SystemClock.elapsedRealtimeNanos()
        database = RegistryDatabase.open(context, databaseName)
        repository = RoomHouseholdRepository(RoomRecordStore(database))
        assertEquals(200, repository.households.first { it.size == 200 }.size)
        val startupMs = elapsedMilliseconds(startupStart)

        val owner = ViewModelStore()
        var listMs = 0L
        var searchTimes = emptyList<Long>()
        var mapMs = 0L
        var pinCount = 0
        var untaggedCount = 0
        try {
            withContext(Dispatchers.Main) {
                val listStart = SystemClock.elapsedRealtimeNanos()
                val registry = RegistryViewModel(repository, SavedStateHandle())
                owner.put("registry", registry)
                registry.uiState.first { !it.loading && it.households.size == 200 }
                listMs = elapsedMilliseconds(listStart)

                searchTimes = (0 until 50).map { index ->
                    val query = "Fictional Household ${index.toString().padStart(3, '0')}"
                    val started = SystemClock.elapsedRealtimeNanos()
                    registry.setQuery(query)
                    registry.uiState.first { it.query == query }
                    elapsedMilliseconds(started)
                }

                val mapStart = SystemClock.elapsedRealtimeNanos()
                val map = MapViewModel(repository, MutableStateFlow(false))
                owner.put("map", map)
                val mapState = map.state.first { !it.loading && it.pins.size + it.untagged.size == 200 }
                mapMs = elapsedMilliseconds(mapStart)
                pinCount = mapState.pins.size
                untaggedCount = mapState.untagged.size
            }
        } finally {
            withContext(Dispatchers.Main) { owner.clear() }
            database.close()
        }

        val searchP95 = percentile95(searchTimes)
        val evidence = File(context.getExternalFilesDir(null), "day20").apply { mkdirs() }
        val report = """{
              "device": "${Build.MANUFACTURER} ${Build.MODEL}",
              "api": ${Build.VERSION.SDK_INT},
              "households": 200,
              "members": 1000,
              "pins": $pinCount,
              "untagged": $untaggedCount,
              "seedMs": $seedMs,
              "startupDataMs": $startupMs,
              "listStateMs": $listMs,
              "searchP95Ms": $searchP95,
              "mapStateMs": $mapMs,
              "targetsMs": { "startupData": 3000, "searchP95": 300, "mapState": 3000 }
            }
            """.trimIndent()
        File(evidence, "performance.json").writeText(report + "\n")
        Log.i("D20_PERFORMANCE", report.replace('\n', ' '))

        assertTrue("Startup data load took ${startupMs}ms", startupMs <= 3_000)
        assertTrue("List state took ${listMs}ms", listMs <= 3_000)
        assertTrue("Search p95 took ${searchP95}ms", searchP95 <= 300)
        assertTrue("Map state took ${mapMs}ms", mapMs <= 3_000)
            context.deleteDatabase(databaseName)
        }
    }

    private fun dataset(): List<StoredRecord> = (0 until 200).map { householdIndex ->
        val householdId = uuid("d20-household-$householdIndex")
        val interviewId = uuid("d20-interview-$householdIndex")
        StoredRecord(
            household = HouseholdRow(
                id = householdId,
                displayCode = "HH-${(10_000 + householdIndex).toString().padStart(5, '0')}",
                headName = "Fictional Household ${householdIndex.toString().padStart(3, '0')}",
                address = "Purok ${(householdIndex % 12) + 1}, Fictional Barangay",
                status = if (householdIndex % 7 == 0) "Callback" else "InProgress",
            ),
            members = (0 until 5).map { memberIndex ->
                MemberRow(
                    id = uuid("d20-member-$householdIndex-$memberIndex"),
                    householdId = householdId,
                    displayName = "Fictional Resident $householdIndex-$memberIndex",
                    sortOrder = memberIndex,
                )
            },
            interview = InterviewRow(interviewId, householdId),
            coordinates = if (householdIndex % 5 == 0) null else CoordinateRow(
                householdId = householdId,
                latitude = 11.0 + (householdIndex % 20) * 0.001,
                longitude = 124.59 + (householdIndex % 25) * 0.001,
                accuracyMetres = 12.0,
                capturedAt = 1_790_000_000_000L + householdIndex,
            ),
        )
    }

    private fun uuid(seed: String): String = UUID.nameUUIDFromBytes(seed.toByteArray()).toString()

    private fun elapsedMilliseconds(started: Long): Long =
        (SystemClock.elapsedRealtimeNanos() - started) / 1_000_000

    private fun percentile95(values: List<Long>): Long =
        values.sorted()[(values.size * 95 + 99) / 100 - 1]
}
