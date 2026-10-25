package ph.tala.registry.data.local

import androidx.room.*
import ph.tala.registry.domain.model.InterviewData
import ph.tala.registry.domain.model.AnswerState
import ph.tala.registry.domain.model.AttachmentKinds

@Entity(tableName = "households", indices = [Index(value = ["displayCode"], unique = true)])
data class HouseholdRow(
    @PrimaryKey val id: String,
    val displayCode: String,
    val headName: String,
    val address: String,
    val status: String = "InProgress",
    /** Epoch millis while archived; null keeps the record active. Added by migration 1 → 2. */
    val archivedAt: Long? = null,
)

@Entity(tableName = "members", foreignKeys = [ForeignKey(entity = HouseholdRow::class, parentColumns = ["id"], childColumns = ["householdId"], onDelete = ForeignKey.CASCADE)], indices = [Index("householdId"), Index(value = ["id", "householdId"], unique = true)])
data class MemberRow(@PrimaryKey val id: String, val householdId: String, val displayName: String, val sortOrder: Int = 0)

@Entity(tableName = "interviews", foreignKeys = [ForeignKey(entity = HouseholdRow::class, parentColumns = ["id"], childColumns = ["householdId"], onDelete = ForeignKey.CASCADE)], indices = [Index(value = ["householdId"], unique = true), Index(value = ["id", "householdId"], unique = true)])
data class InterviewRow(@PrimaryKey val id: String, val householdId: String, val questionnaireVersion: String = "practice-v1")

@Entity(tableName = "visits", foreignKeys = [ForeignKey(entity = InterviewRow::class, parentColumns = ["id"], childColumns = ["interviewId"], onDelete = ForeignKey.CASCADE)], indices = [Index("interviewId")])
data class VisitRow(
    @PrimaryKey val id: String, val interviewId: String, val outcome: String, val callbackAt: String? = null,
    @ColumnInfo(defaultValue = "''") val visitedOn: String = "",
    @ColumnInfo(defaultValue = "''") val notes: String = "",
    @ColumnInfo(defaultValue = "0") val recordedAt: Long = 0,
)

@Entity(tableName = "household_answers", primaryKeys = ["interviewId", "section", "field"], foreignKeys = [ForeignKey(entity = InterviewRow::class, parentColumns = ["id"], childColumns = ["interviewId"], onDelete = ForeignKey.CASCADE)])
data class HouseholdAnswerRow(val interviewId: String, val section: String, val field: String, val kind: String, val value: String)

@Entity(tableName = "member_answers", primaryKeys = ["interviewId", "memberId", "section", "field"], foreignKeys = [
    ForeignKey(entity = InterviewRow::class, parentColumns = ["id", "householdId"], childColumns = ["interviewId", "householdId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = MemberRow::class, parentColumns = ["id", "householdId"], childColumns = ["memberId", "householdId"], onDelete = ForeignKey.CASCADE),
], indices = [Index(value = ["interviewId", "householdId"]), Index(value = ["memberId", "householdId"])])
data class MemberAnswerRow(val interviewId: String, val householdId: String, val memberId: String, val section: String, val field: String, val kind: String, val value: String)

@Entity(tableName = "coordinates", foreignKeys = [ForeignKey(entity = HouseholdRow::class, parentColumns = ["id"], childColumns = ["householdId"], onDelete = ForeignKey.CASCADE)])
data class CoordinateRow(@PrimaryKey val householdId: String, val latitude: Double, val longitude: Double, val accuracyMetres: Double?, val capturedAt: Long)

@Entity(tableName = "attachments", foreignKeys = [
    ForeignKey(entity = HouseholdRow::class, parentColumns = ["id"], childColumns = ["householdId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = MemberRow::class, parentColumns = ["id", "householdId"], childColumns = ["memberId", "householdId"], onDelete = ForeignKey.CASCADE),
], indices = [Index("householdId"), Index(value = ["memberId", "householdId"])])
data class AttachmentRow(@PrimaryKey val id: String, val householdId: String, val memberId: String?, val kind: String, val relativePath: String, val mimeType: String)

@Entity(
    tableName = "sync_outbox",
    foreignKeys = [ForeignKey(entity = HouseholdRow::class, parentColumns = ["id"], childColumns = ["householdId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("requestedAt")],
)
data class SyncOutboxRow(
    @PrimaryKey val householdId: String,
    val requestedAt: Long,
    val attempts: Int = 0,
    val lastError: String = "",
)

@Entity(
    tableName = "sync_revisions",
    primaryKeys = ["ownerId", "householdId"],
    foreignKeys = [ForeignKey(entity = HouseholdRow::class, parentColumns = ["id"], childColumns = ["householdId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("householdId")],
)
data class SyncRevisionRow(
    val ownerId: String,
    val householdId: String,
    val revision: Long,
)

/** One complete household snapshot. A section replacement must include all remaining answers. */
data class StoredRecord(
    val household: HouseholdRow,
    val members: List<MemberRow>,
    val interview: InterviewRow,
    val visits: List<VisitRow> = emptyList(),
    val householdAnswers: List<HouseholdAnswerRow> = emptyList(),
    val memberAnswers: List<MemberAnswerRow> = emptyList(),
    val coordinates: CoordinateRow? = null,
    val attachments: List<AttachmentRow> = emptyList(),
) : InterviewData {
    override val memberNames: Map<String, String> get() = members.associate { it.id to it.displayName }
    override val hasDrawnSignature: Boolean get() = attachments.any { it.kind == AttachmentKinds.SIGNATURE }
    override fun sectionAnswers(route: String, memberId: String?): AnswerState =
        if (memberId != null) memberAnswers.filter { it.section == route && it.memberId == memberId }
            .associate { it.field to AnswerCodec.decode(it.kind, it.value) }
        else householdAnswers.filter { it.section == route }
            .associate { it.field to AnswerCodec.decode(it.kind, it.value) }
}

/** Read model for lists and screens: one household row with its members, no answers. */
data class HouseholdPurokAnswer(val householdId: String, val kind: String, val value: String)

data class HouseholdWithMembers(
    @Embedded val household: HouseholdRow,
    @Relation(parentColumn = "id", entityColumn = "householdId")
    val members: List<MemberRow>,
)
