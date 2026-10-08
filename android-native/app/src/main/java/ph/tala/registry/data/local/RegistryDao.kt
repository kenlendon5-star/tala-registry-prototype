package ph.tala.registry.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RegistryDao {
    @Query("SELECT * FROM households ORDER BY displayCode") fun observeHouseholds(): Flow<List<HouseholdRow>>
    @Query("SELECT * FROM households WHERE id = :id") suspend fun household(id: String): HouseholdRow?
    @Query("SELECT * FROM members WHERE householdId = :id ORDER BY id") suspend fun members(id: String): List<MemberRow>
    @Query("SELECT * FROM interviews WHERE householdId = :id") suspend fun interview(id: String): InterviewRow?
    @Query("SELECT * FROM visits WHERE interviewId = :id ORDER BY id") suspend fun visits(id: String): List<VisitRow>
    @Query("SELECT * FROM household_answers WHERE interviewId = :id ORDER BY section, field") suspend fun householdAnswers(id: String): List<HouseholdAnswerRow>
    @Query("SELECT * FROM member_answers WHERE interviewId = :id ORDER BY memberId, section, field") suspend fun memberAnswers(id: String): List<MemberAnswerRow>
    @Query("SELECT * FROM coordinates WHERE householdId = :id") suspend fun coordinates(id: String): CoordinateRow?
    @Query("SELECT * FROM attachments WHERE householdId = :id ORDER BY id") suspend fun attachments(id: String): List<AttachmentRow>
    @Query("DELETE FROM attachments WHERE householdId = :id") suspend fun clearAttachments(id: String)
    @Query("DELETE FROM interviews WHERE householdId = :id") suspend fun clearInterviews(id: String)
    @Query("DELETE FROM members WHERE householdId = :id") suspend fun clearMembers(id: String)
    @Query("DELETE FROM coordinates WHERE householdId = :id") suspend fun clearCoordinates(id: String)
    @Upsert suspend fun putHousehold(row: HouseholdRow)
    @Insert suspend fun putMembers(rows: List<MemberRow>)
    @Insert suspend fun putInterview(row: InterviewRow)
    @Insert suspend fun putVisits(rows: List<VisitRow>)
    @Insert suspend fun putHouseholdAnswers(rows: List<HouseholdAnswerRow>)
    @Insert suspend fun putMemberAnswers(rows: List<MemberAnswerRow>)
    @Insert suspend fun putCoordinate(row: CoordinateRow)
    @Insert suspend fun putAttachments(rows: List<AttachmentRow>)
}
