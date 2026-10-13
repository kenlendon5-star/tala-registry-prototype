package ph.tala.registry.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ph.tala.registry.data.local.AnswerCodec
import ph.tala.registry.data.local.AttachmentRow
import ph.tala.registry.data.local.HouseholdAnswerRow
import ph.tala.registry.data.local.HouseholdRow
import ph.tala.registry.data.local.HouseholdWithMembers
import ph.tala.registry.data.local.InterviewRow
import ph.tala.registry.data.local.MemberRow
import ph.tala.registry.data.local.MemberAnswerRow
import ph.tala.registry.domain.form.InterviewRules
import ph.tala.registry.domain.form.QuestionnaireSections
import ph.tala.registry.domain.model.scalar
import ph.tala.registry.data.local.RecordStore
import ph.tala.registry.data.local.StoredRecord
import ph.tala.registry.data.local.WriteResult
import ph.tala.registry.domain.edit.EditRules
import ph.tala.registry.domain.edit.HouseholdDraft
import ph.tala.registry.domain.edit.MemberDraft
import ph.tala.registry.domain.model.AnswerState
import ph.tala.registry.domain.model.AnswerValue
import ph.tala.registry.domain.model.Household
import ph.tala.registry.domain.model.HouseholdId
import ph.tala.registry.domain.model.HouseholdStatus
import ph.tala.registry.domain.model.Member
import ph.tala.registry.domain.model.MemberId
import ph.tala.registry.domain.model.SectionSpec
import ph.tala.registry.domain.model.isAnswered
import java.util.UUID
import ph.tala.registry.data.HouseholdRepository.Companion.HOUSEHOLD_PHOTO

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

    suspend fun interviewRecord(id: HouseholdId): StoredRecord? = null
    suspend fun saveDraftSection(id: HouseholdId, route: String, memberId: MemberId?, answers: AnswerState, expected: AnswerState): WriteResult =
        WriteResult.Failed("Interview editing is unavailable.")
    suspend fun reviewInterview(id: HouseholdId): WriteResult = WriteResult.Failed("Interview review is unavailable.")

    /** Member id -> stored demographics sex answer ('' while unrecorded); feeds the home summary counts. */
    val memberSex: Flow<Map<String, String>>

    /** Saves the household picture as the record's photo attachment, replacing any previous one. */
    suspend fun saveHouseholdPhotoAttachment(householdId: HouseholdId, relativePath: String, mimeType: String): WriteResult

    /** Removes the household picture attachment row; the copied file is deleted by the PhotoStore caller. */
    suspend fun removeHouseholdPhotoAttachment(householdId: HouseholdId): WriteResult

    suspend fun saveMemberPhotoAttachment(householdId: HouseholdId, memberId: MemberId, relativePath: String, mimeType: String): WriteResult =
        WriteResult.Failed("Member pictures are unavailable.")
    suspend fun removeMemberPhotoAttachment(householdId: HouseholdId, memberId: MemberId): WriteResult =
        WriteResult.Failed("Member pictures are unavailable.")

    /** Adds a member when [memberId] is new, otherwise renames the existing member. */
    suspend fun saveMember(householdId: HouseholdId, memberId: MemberId, draft: MemberDraft): WriteResult

    /** Removes a member together with that member's answers; the rest of the record is untouched. */
    suspend fun removeMember(householdId: HouseholdId, memberId: MemberId): WriteResult

    /** Seeds explicit fictional fixtures, skipping records that already exist. Optional and idempotent. */
    suspend fun seedMissing(records: List<StoredRecord>): WriteResult

    companion object {
        /** Attachment kind used for the single household profile picture. */
        const val HOUSEHOLD_PHOTO = "household-photo"
        const val MEMBER_PHOTO = "member-photo"
    }
}

/**
 * [RecordStore]-backed repository: one ordered write stream, full-snapshot saves and
 * no destructive shortcuts. Reads map stored rows to immutable domain records.
 */
class RoomHouseholdRepository(
    private val store: RecordStore,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val clock: () -> Long = System::currentTimeMillis,
    private val profiles: EnumeratorProfileStore? = null,
) : HouseholdRepository {
    private val writes = Mutex()

    override suspend fun interviewRecord(id: HouseholdId): StoredRecord? = writes.withLock { store.load(id.value) }

    override suspend fun saveDraftSection(id: HouseholdId, route: String, memberId: MemberId?, answers: AnswerState, expected: AnswerState): WriteResult = writes.withLock {
        val record = store.load(id.value) ?: return@withLock WriteResult.Failed(MISSING)
        val section = QuestionnaireSections.all.find { it.route == route } ?: return@withLock WriteResult.Failed(INVALID)
        if (section.scopedToMember != (memberId != null) || (memberId != null && record.members.none { it.id == memberId.value }))
            return@withLock WriteResult.Failed("The member is no longer available in this household.")
        if (InterviewRules.answers(record, section, memberId?.value) != expected)
            return@withLock WriteResult.Failed("This section changed in another editor. Reload the saved section before applying your edits.")
        val cleaned = InterviewRules.clean(section, answers)
        val encoded = cleaned.mapValues { AnswerCodec.encode(it.value) }
        val hhAnswers = if (memberId != null) record.householdAnswers else
            record.householdAnswers.filterNot { it.section == route } + encoded.map { (field, value) ->
                HouseholdAnswerRow(record.interview.id, route, field, value.first, value.second)
            }
        val memberAnswers = if (memberId == null) record.memberAnswers else
            record.memberAnswers.filterNot { it.section == route && it.memberId == memberId.value } + encoded.map { (field, value) ->
                MemberAnswerRow(record.interview.id, id.value, memberId.value, route, field, value.first, value.second)
            }
        var household = record.household.copy(status = if (record.household.status == "Reviewed") "InProgress" else record.household.status)
        if (route == "visit") household = household.copy(status = statusForVisit(cleaned.scalar("visitResult")).name)
        val name = listOf("firstName", "middleName", "lastName", "extension").map { cleaned.scalar(it).trim() }.filter { it.isNotBlank() }.joinToString(" ")
        if (route == "household") household = household.copy(
            headName = name.takeIf { cleaned.scalar("firstName").isNotBlank() && cleaned.scalar("lastName").isNotBlank() } ?: household.headName,
            address = cleaned.scalar("address").takeIf { it.isNotBlank() } ?: household.address,
        )
        val members = if (route == "demographics" && memberId != null && cleaned.scalar("firstName").isNotBlank() && cleaned.scalar("lastName").isNotBlank())
            record.members.map { if (it.id == memberId.value) it.copy(displayName = name) else it } else record.members
        store.save(record.copy(household = household, members = members, householdAnswers = hhAnswers, memberAnswers = memberAnswers,
            interview = record.interview.copy(questionnaireVersion = QuestionnaireSections.VERSION)))
    }

    override suspend fun reviewInterview(id: HouseholdId): WriteResult = writes.withLock {
        val record = store.load(id.value) ?: return@withLock WriteResult.Failed(MISSING)
        if (record.household.archivedAt != null) return@withLock WriteResult.Failed("Restore this household before reviewing it.")
        if (InterviewRules.issues(record).isNotEmpty()) return@withLock WriteResult.Failed("Complete the missing answers and consent before review.")
        store.save(record.copy(household = record.household.copy(status = "Reviewed")))
    }

    override val households: Flow<List<Household>> =
        store.records.map { rows -> rows.map { it.toDomain() } }

    override suspend fun createHousehold(id: HouseholdId, draft: HouseholdDraft): WriteResult = writes.withLock {
        invalid(draft)?.let { return@withLock WriteResult.Failed(it) }
        // A retried create keeps the identity the screen already showed the user.
        val existing = store.load(id.value)
        if (existing != null) return@withLock replace(existing, draft)
        val interview = InterviewRow(newId(), id.value, QuestionnaireSections.VERSION)
        val profile = try { profiles?.profile?.first() } catch (_: java.io.IOException) {
            return@withLock WriteResult.Failed("Could not read the enumerator profile. Retry before registering this household.")
        }
        val setup = profile?.let { mapOf("enumerator" to it.name, "barangay" to it.barangay) }.orEmpty()
        store.save(
            StoredRecord(
                household = HouseholdRow(id.value, nextDisplayCode(), draft.headName.trim(), draft.address.trim(), draft.status.name),
                members = emptyList(),
                interview = interview,
                householdAnswers = (setup.map { (key, value) -> scalarRow(interview.id, "setup", key, value) }
                    + scalarRow(interview.id, "household", "address", draft.address.trim())),
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

    override val memberSex: Flow<Map<String, String>> = store.memberSex

    override suspend fun saveHouseholdPhotoAttachment(householdId: HouseholdId, relativePath: String, mimeType: String): WriteResult = writes.withLock {
        val record = store.load(householdId.value) ?: return@withLock WriteResult.Failed(MISSING)
        val kept = record.attachments.filterNot { it.kind == HOUSEHOLD_PHOTO }
        val row = AttachmentRow(UUID.randomUUID().toString(), householdId.value, null, HOUSEHOLD_PHOTO, relativePath, mimeType)
        store.save(record.copy(attachments = kept + row))
    }

    override suspend fun removeHouseholdPhotoAttachment(householdId: HouseholdId): WriteResult = writes.withLock {
        val record = store.load(householdId.value) ?: return@withLock WriteResult.Failed(MISSING)
        store.save(record.copy(attachments = record.attachments.filterNot { it.kind == HOUSEHOLD_PHOTO }))
    }

    override suspend fun saveMemberPhotoAttachment(householdId: HouseholdId, memberId: MemberId, relativePath: String, mimeType: String): WriteResult = writes.withLock {
        val record = store.load(householdId.value) ?: return@withLock WriteResult.Failed(MISSING)
        if (record.members.none { it.id == memberId.value }) return@withLock WriteResult.Failed(MISSING)
        val kept = record.attachments.filterNot { it.kind == HouseholdRepository.MEMBER_PHOTO && it.memberId == memberId.value }
        val row = AttachmentRow(UUID.randomUUID().toString(), householdId.value, memberId.value, HouseholdRepository.MEMBER_PHOTO, relativePath, mimeType)
        store.save(record.copy(attachments = kept + row))
    }

    override suspend fun removeMemberPhotoAttachment(householdId: HouseholdId, memberId: MemberId): WriteResult = writes.withLock {
        val record = store.load(householdId.value) ?: return@withLock WriteResult.Failed(MISSING)
        store.save(record.copy(attachments = record.attachments.filterNot { it.kind == HouseholdRepository.MEMBER_PHOTO && it.memberId == memberId.value }))
    }

    /** Only an appointment keeps the Callback badge; every other outcome leaves the record open. */
    private fun statusForVisit(result: String): HouseholdStatus = when (result) {
        "Callback" -> HouseholdStatus.Callback
        else -> HouseholdStatus.InProgress
    }

    override suspend fun saveMember(householdId: HouseholdId, memberId: MemberId, draft: MemberDraft): WriteResult = writes.withLock {
        invalid(draft)?.let { return@withLock WriteResult.Failed(it) }
        val record = store.load(householdId.value) ?: return@withLock WriteResult.Failed(MISSING)
        val name = draft.displayName.trim()
        val identity = InterviewRules.answers(record, QuestionnaireSections.demographics, memberId.value)
        if (hasStructuredName(identity) && name != record.members.find { it.id == memberId.value }?.displayName)
            return@withLock WriteResult.Failed("Edit First Name and Last Name in Name details to keep this member's identity consistent.")
        val members = if (record.members.any { it.id == memberId.value }) {
            record.members.map { if (it.id == memberId.value) it.copy(displayName = name) else it }
        } else {
            record.members + MemberRow(memberId.value, householdId.value, name)
        }
        store.save(record.copy(members = members, household = record.household.copy(status = if (record.household.status == "Reviewed") "InProgress" else record.household.status)))
    }

    override suspend fun removeMember(householdId: HouseholdId, memberId: MemberId): WriteResult = writes.withLock {
        val record = store.load(householdId.value) ?: return@withLock WriteResult.Failed(MISSING)
        store.save(
            record.copy(
                members = record.members.filterNot { it.id == memberId.value },
                household = record.household.copy(status = if (record.household.status == "Reviewed") "InProgress" else record.household.status),
                memberAnswers = record.memberAnswers.filterNot { it.memberId == memberId.value },
                attachments = record.attachments.filterNot { it.memberId == memberId.value },
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

    private suspend fun replace(record: StoredRecord, draft: HouseholdDraft): WriteResult {
        val identity = InterviewRules.answers(record, QuestionnaireSections.household)
        if (hasStructuredName(identity) && draft.headName.trim() != record.household.headName)
            return WriteResult.Failed("Edit First Name and Last Name in Name details to keep the household identity consistent.")
        return store.save(
            record.copy(
                household = record.household.copy(
                    headName = draft.headName.trim(),
                    address = draft.address.trim(),
                    status = draft.status.name,
                ),
                householdAnswers = record.householdAnswers.filterNot { it.section == "household" && it.field == "address" } +
                    scalarRow(record.interview.id, "household", "address", draft.address.trim()),
            ),
        )
    }

    private fun scalarRow(interview: String, section: String, key: String, text: String): HouseholdAnswerRow {
        val encoded = AnswerCodec.encode(AnswerValue.Scalar(text))
        return HouseholdAnswerRow(interview, section, key, encoded.first, encoded.second)
    }

    private fun hasStructuredName(answers: AnswerState) = answers.scalar("firstName").isNotBlank() && answers.scalar("lastName").isNotBlank()

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
