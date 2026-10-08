package ph.tala.registry.data.local

import androidx.room.*

@Entity(tableName = "households", indices = [Index(value = ["displayCode"], unique = true)])
data class HouseholdRow(
    @PrimaryKey val id: String,
    val displayCode: String,
    val headName: String,
    val address: String,
    val status: String = "InProgress",
)

@Entity(tableName = "members", foreignKeys = [ForeignKey(entity = HouseholdRow::class, parentColumns = ["id"], childColumns = ["householdId"], onDelete = ForeignKey.CASCADE)], indices = [Index("householdId"), Index(value = ["id", "householdId"], unique = true)])
data class MemberRow(@PrimaryKey val id: String, val householdId: String, val displayName: String)

@Entity(tableName = "interviews", foreignKeys = [ForeignKey(entity = HouseholdRow::class, parentColumns = ["id"], childColumns = ["householdId"], onDelete = ForeignKey.CASCADE)], indices = [Index(value = ["householdId"], unique = true), Index(value = ["id", "householdId"], unique = true)])
data class InterviewRow(@PrimaryKey val id: String, val householdId: String, val questionnaireVersion: String = "practice-v1")

@Entity(tableName = "visits", foreignKeys = [ForeignKey(entity = InterviewRow::class, parentColumns = ["id"], childColumns = ["interviewId"], onDelete = ForeignKey.CASCADE)], indices = [Index("interviewId")])
data class VisitRow(@PrimaryKey val id: String, val interviewId: String, val outcome: String, val callbackAt: String? = null)

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
)
