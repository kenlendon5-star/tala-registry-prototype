package ph.tala.registry.data.local

import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import ph.tala.registry.domain.model.AnswerValue
import java.util.UUID

sealed interface WriteResult {
    data object Saved : WriteResult
    data class Failed(val message: String) : WriteResult
}

interface RecordStore {
    val households: Flow<List<HouseholdRow>>
    /** Households with their members, ordered by display code; used by the editing screens. */
    val records: Flow<List<HouseholdWithMembers>>
    /** Member id -> stored demographics sex answer ('' while unrecorded); feeds the home summary counts. */
    val memberSex: Flow<Map<String, String>>
    /** The stored coordinate of one household, or null while none was captured. */
    fun observeCoordinate(id: String): Flow<CoordinateRow?>
    suspend fun load(id: String): StoredRecord?
    suspend fun save(record: StoredRecord): WriteResult
}

/** Full-snapshot replacement is atomic, including deletions of hidden/cleared answers. */
class RoomRecordStore(private val database: RegistryDatabase) : RecordStore {
    private val dao = database.records()
    override val households = dao.observeHouseholds()
    override val records = dao.observeRecords()
    override val memberSex = dao.observeSexAnswers().map { rows ->
        rows.associate { row -> row.memberId to ((AnswerCodec.decode(row.kind, row.value) as? AnswerValue.Scalar)?.text.orEmpty()) }
    }
    override fun observeCoordinate(id: String) = dao.observeCoordinate(id)

    override suspend fun load(id: String): StoredRecord? = database.withTransaction {
        val household = dao.household(id) ?: return@withTransaction null
        val interview = checkNotNull(dao.interview(id)) { "Stored interview is missing" }
        StoredRecord(household, dao.members(id), interview, dao.visits(interview.id), dao.householdAnswers(interview.id), dao.memberAnswers(interview.id), dao.coordinates(id), dao.attachments(id))
    }

    override suspend fun save(record: StoredRecord): WriteResult = try {
        validate(record)
        database.withTransaction {
            val id = record.household.id
            dao.putHousehold(record.household)
            dao.clearAttachments(id)
            dao.clearInterviews(id) // Cascades visits and both scoped answer tables.
            dao.clearMembers(id)
            dao.clearCoordinates(id)
            dao.putMembers(record.members)
            dao.putInterview(record.interview)
            dao.putVisits(record.visits)
            dao.putHouseholdAnswers(record.householdAnswers)
            dao.putMemberAnswers(record.memberAnswers)
            record.coordinates?.let { dao.putCoordinate(it) }
            dao.putAttachments(record.attachments)
        }
        WriteResult.Saved
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        // Do not expose answers, paths or database diagnostics in UI/logs.
        WriteResult.Failed("Could not save on this device. The previous saved record is unchanged. Retry after checking available storage.")
    }

    private fun validate(r: StoredRecord) {
        fun uuid(value: String) { require(UUID.fromString(value).toString() == value) }
        uuid(r.household.id); uuid(r.interview.id)
        require(r.household.displayCode.isNotBlank())
        require(r.household.status in setOf("NotStarted", "InProgress", "Callback", "Reviewed"))
        require(r.interview.householdId == r.household.id && r.interview.questionnaireVersion.isNotBlank())
        r.members.forEach { uuid(it.id); require(it.householdId == r.household.id) }
        val members = r.members.map { it.id }.toSet()
        r.visits.forEach { uuid(it.id); require(it.interviewId == r.interview.id) }
        r.householdAnswers.forEach { require(it.interviewId == r.interview.id && it.section.isNotBlank() && it.field.isNotBlank()); AnswerCodec.decode(it.kind, it.value) }
        r.memberAnswers.forEach { require(it.interviewId == r.interview.id && it.householdId == r.household.id && it.memberId in members && it.section.isNotBlank() && it.field.isNotBlank()); AnswerCodec.decode(it.kind, it.value) }
        r.coordinates?.let { require(it.householdId == r.household.id && it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0 && (it.accuracyMetres == null || (it.accuracyMetres.isFinite() && it.accuracyMetres >= 0))) }
        r.attachments.forEach {
            uuid(it.id)
            require(it.householdId == r.household.id && (it.memberId == null || it.memberId in members))
            require(Regex("attachments/[a-zA-Z0-9_-]+\\.[a-zA-Z0-9]+").matches(it.relativePath))
        }
    }
}

object AnswerCodec {
    fun encode(value: AnswerValue): Pair<String, String> = when (value) {
        is AnswerValue.Scalar -> "scalar" to JsonPrimitive(value.text).toString()
        is AnswerValue.Multi -> "multi" to JsonArray(value.selected.distinct().map(::JsonPrimitive)).toString()
    }

    fun decode(kind: String, value: String): AnswerValue = when (kind) {
        "scalar" -> AnswerValue.Scalar(string(Json.parseToJsonElement(value)))
        "multi" -> AnswerValue.Multi((Json.parseToJsonElement(value) as JsonArray).map(::string))
        else -> error("Unknown answer kind")
    }

    private fun string(value: kotlinx.serialization.json.JsonElement): String =
        (value as JsonPrimitive).also { require(it.isString) }.content
}
