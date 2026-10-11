package ph.tala.registry.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ph.tala.registry.data.local.HouseholdRow
import ph.tala.registry.data.local.HouseholdWithMembers
import ph.tala.registry.data.local.InterviewRow
import ph.tala.registry.data.local.MemberRow
import ph.tala.registry.data.local.RecordStore
import ph.tala.registry.data.local.StoredRecord
import ph.tala.registry.data.local.WriteResult
import ph.tala.registry.domain.edit.EditRules
import ph.tala.registry.domain.edit.HouseholdDraft
import ph.tala.registry.domain.edit.MemberDraft
import ph.tala.registry.domain.model.Household
import ph.tala.registry.domain.model.HouseholdId
import ph.tala.registry.domain.model.HouseholdStatus
import ph.tala.registry.domain.model.Member
import ph.tala.registry.domain.model.MemberId
import java.util.UUID

/**
 * Read and write contract for the editing slice. Reads are reactive; every write takes
 * the whole current snapshot of one household, changes only the requested parts, and
 * reports [WriteResult] instead of throwing so the UI can never mistake a rejected
 * write for a saved one.
 */
interface HouseholdRepository {
    val households: Flow<List<Household>>

    fun observeHousehold(id: HouseholdId): Flow<Household?> =
        households.map { records -> records.find { it.id == id } }

    /** Creates a household with a caller-chosen identity; a retry updates instead of duplicating. */
    suspend fun createHousehold(id: HouseholdId, draft: HouseholdDraft): WriteResult

    suspend fun updateHousehold(id: HouseholdId, draft: HouseholdDraft): WriteResult

    /** Archiving is reversible: it only sets or clears the archive marker. */
    suspend fun setArchived(id: HouseholdId, archived: Boolean): WriteResult

    /** Adds a member when [memberId] is new, otherwise renames the existing member. */
    suspend fun saveMember(householdId: HouseholdId, memberId: MemberId, draft: MemberDraft): WriteResult

    /** Removes a member together with that member's answers; the rest of the record is untouched. */
    suspend fun removeMember(householdId: HouseholdId, memberId: MemberId): WriteResult

    /** Seeds explicit fictional fixtures, skipping records that already exist. Optional and idempotent. */
    suspend fun seedMissing(records: List<StoredRecord>): WriteResult
}

/**
 * [RecordStore]-backed repository: one ordered write stream, full-snapshot saves and
 * no destructive shortcuts. Reads map stored rows to immutable domain records.
 */
class RoomHouseholdRepository(
    private val store: RecordStore,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val clock: () -> Long = System::currentTimeMillis,
) : HouseholdRepository {
    private val writes = Mutex()

    override val households: Flow<List<Household>> =
        store.records.map { rows -> rows.map { it.toDomain() } }

    override suspend fun createHousehold(id: HouseholdId, draft: HouseholdDraft): WriteResult = writes.withLock {
        invalid(draft)?.let { return@withLock WriteResult.Failed(it) }
        // A retried create keeps the identity the screen already showed the user.
        val existing = store.load(id.value)
        if (existing != null) return@withLock replace(existing, draft)
        store.save(
            StoredRecord(
                household = HouseholdRow(id.value, nextDisplayCode(), draft.headName.trim(), draft.address.trim(), draft.status.name),
                members = emptyList(),
                interview = InterviewRow(newId(), id.value),
            ),
        )
    }

    override suspend fun updateHousehold(id: HouseholdId, draft: HouseholdDraft): WriteResult = writes.withLock {
        invalid(draft)?.let { return@withLock WriteResult.Failed(it) }
        val record = store.load(id.value) ?: return@withLock WriteResult.Failed(MISSING)
        replace(record, draft)
    }

    override suspend fun setArchived(id: HouseholdId, archived: Boolean): WriteResult = writes.withLock {
        val record = store.load(id.value) ?: return@withLock WriteResult.Failed(MISSING)
        store.save(record.copy(household = record.household.copy(archivedAt = if (archived) clock() else null)))
    }

    override suspend fun saveMember(householdId: HouseholdId, memberId: MemberId, draft: MemberDraft): WriteResult = writes.withLock {
        invalid(draft)?.let { return@withLock WriteResult.Failed(it) }
        val record = store.load(householdId.value) ?: return@withLock WriteResult.Failed(MISSING)
        val name = draft.displayName.trim()
        val members = if (record.members.any { it.id == memberId.value }) {
            record.members.map { if (it.id == memberId.value) it.copy(displayName = name) else it }
        } else {
            record.members + MemberRow(memberId.value, householdId.value, name)
        }
        store.save(record.copy(members = members))
    }

    override suspend fun removeMember(householdId: HouseholdId, memberId: MemberId): WriteResult = writes.withLock {
        val record = store.load(householdId.value) ?: return@withLock WriteResult.Failed(MISSING)
        store.save(
            record.copy(
                members = record.members.filterNot { it.id == memberId.value },
                memberAnswers = record.memberAnswers.filterNot { it.memberId == memberId.value },
            ),
        )
    }

    override suspend fun seedMissing(records: List<StoredRecord>): WriteResult = writes.withLock {
        var outcome: WriteResult = WriteResult.Saved
        records.forEach { record ->
            if (outcome is WriteResult.Saved && store.load(record.household.id) == null) outcome = store.save(record)
        }
        outcome
    }

    private suspend fun replace(record: StoredRecord, draft: HouseholdDraft): WriteResult =
        store.save(
            record.copy(
                household = record.household.copy(
                    headName = draft.headName.trim(),
                    address = draft.address.trim(),
                    status = draft.status.name,
                ),
            ),
        )

    /** Next free `HH-00001`-style label, computed inside the write lock so codes never repeat. */
    private suspend fun nextDisplayCode(): String {
        val highest = store.records.first()
            .mapNotNull { DISPLAY_CODE.find(it.household.displayCode)?.groupValues?.get(1)?.toIntOrNull() }
            .maxOrNull() ?: 0
        return "HH-" + (highest + 1).toString().padStart(5, '0')
    }

    private fun invalid(draft: HouseholdDraft): String? =
        if (EditRules.householdProblem(draft) != null) INVALID else null

    private fun invalid(draft: MemberDraft): String? = EditRules.memberProblem(draft)?.let { INVALID }

    private fun HouseholdWithMembers.toDomain() = Household(
        id = HouseholdId(household.id),
        displayCode = household.displayCode,
        headName = household.headName,
        address = household.address,
        status = HouseholdStatus.entries.find { it.name == household.status } ?: HouseholdStatus.NotStarted,
        members = members.map { Member(MemberId(it.id), HouseholdId(it.householdId), it.displayName) },
        archivedAt = household.archivedAt,
    )

    private companion object {
        val DISPLAY_CODE = Regex("^HH-(\\d+)$")

        /** Safe, stable wording: never expose paths, SQL or answer content. */
        const val INVALID = "Some fields are not valid yet. Check the highlighted answers and save again."
        const val MISSING = "This record is no longer on this device. Go back to the household list and try again."
    }
}
