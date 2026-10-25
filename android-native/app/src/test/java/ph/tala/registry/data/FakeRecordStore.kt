package ph.tala.registry.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import ph.tala.registry.data.local.AnswerCodec
import ph.tala.registry.data.local.CoordinateRow
import ph.tala.registry.data.local.HouseholdRow
import ph.tala.registry.data.local.HouseholdWithMembers
import ph.tala.registry.data.local.RecordStore
import ph.tala.registry.data.local.StoredRecord
import ph.tala.registry.data.local.WriteResult
import ph.tala.registry.domain.model.AnswerValue

/**
 * In-memory [RecordStore] that mirrors the guarantees the Room store enforces —
 * full-snapshot replacement, unique display codes and no answers referencing removed
 * members — so Day 6 repository behaviour can be tested on the JVM without a device.
 */
class FakeRecordStore(seed: List<StoredRecord> = emptyList()) : RecordStore {
    private val state = MutableStateFlow(seed.associateBy { it.household.id })

    /** When set, every following save reports this failure and leaves stored data untouched. */
    var failWith: String? = null

    override val households: Flow<List<HouseholdRow>> =
        state.map { records -> records.values.map { it.household }.sortedBy { it.displayCode } }

    override val records: Flow<List<HouseholdWithMembers>> =
        state.map { records ->
            records.values.map { HouseholdWithMembers(it.household, it.members) }.sortedBy { it.household.displayCode }
        }

    override val memberSex: Flow<Map<String, String>> =
        state.map { records ->
            records.values.flatMap { it.memberAnswers }
                .filter { it.section == "demographics" && it.field == "sex" }
                .associate { row ->
                    row.memberId to ((AnswerCodec.decode(row.kind, row.value) as? AnswerValue.Scalar)?.text.orEmpty())
                }
        }

    override val householdPuroks: Flow<Map<String, String>> = state.map { records ->
        records.values.associate { record ->
            record.household.id to ((record.householdAnswers.firstOrNull { it.section == "setup" && it.field == "purok" }
                ?.let { AnswerCodec.decode(it.kind, it.value) } as? AnswerValue.Scalar)?.text.orEmpty())
        }
    }

    override fun observeCoordinate(id: String): Flow<CoordinateRow?> =
        state.map { records -> records[id]?.coordinates }

    override val allCoordinates: Flow<List<CoordinateRow>> =
        state.map { records -> records.values.mapNotNull { it.coordinates } }

    override suspend fun load(id: String): StoredRecord? = state.value[id]

    override suspend fun loadAll(): List<StoredRecord> = state.value.values.toList()

    override suspend fun save(record: StoredRecord): WriteResult {
        failWith?.let { return WriteResult.Failed(it) }
        val others = state.value.filterKeys { it != record.household.id }.values
        if (others.any { it.household.displayCode == record.household.displayCode }) {
            return WriteResult.Failed("That display code is already used by another household.")
        }
        val memberIds = record.members.map { it.id }.toSet()
        if (record.memberAnswers.any { it.memberId !in memberIds }) {
            return WriteResult.Failed("An answer still references a removed member.")
        }
        state.value = state.value + (record.household.id to record)
        return WriteResult.Saved
    }

    override suspend fun restore(records: List<StoredRecord>): WriteResult {
        failWith?.let { return WriteResult.Failed(it) }
        if (records.isEmpty() || records.map { it.household.id }.distinct().size != records.size ||
            records.map { it.household.displayCode }.distinct().size != records.size) {
            return WriteResult.Failed("Could not restore the backup. Existing records are unchanged.")
        }
        val incomingIds = records.map { it.household.id }.toSet()
        val untouched = state.value.filterKeys { it !in incomingIds }
        if (records.any { incoming -> untouched.values.any { it.household.displayCode == incoming.household.displayCode } }) {
            return WriteResult.Failed("Could not restore the backup. Existing records are unchanged.")
        }
        state.value = untouched + records.associateBy { it.household.id }
        return WriteResult.Saved
    }
}
