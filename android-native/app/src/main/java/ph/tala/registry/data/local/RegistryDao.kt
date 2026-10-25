package ph.tala.registry.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RegistryDao {
    @Query("SELECT * FROM households ORDER BY displayCode") fun observeHouseholds(): Flow<List<HouseholdRow>>
    @Transaction @Query("SELECT * FROM households ORDER BY displayCode") fun observeRecords(): Flow<List<HouseholdWithMembers>>
    @Query("SELECT * FROM members ORDER BY householdId, sortOrder, id") fun observeMembers(): Flow<List<MemberRow>>
    @Query("SELECT * FROM households WHERE id = :id") suspend fun household(id: String): HouseholdRow?
    @Query("SELECT * FROM households ORDER BY displayCode") suspend fun allHouseholds(): List<HouseholdRow>
    @Query("SELECT * FROM members WHERE householdId = :id ORDER BY sortOrder, id") suspend fun members(id: String): List<MemberRow>
    @Query("SELECT * FROM interviews WHERE householdId = :id") suspend fun interview(id: String): InterviewRow?
    @Query("SELECT * FROM visits WHERE interviewId = :id ORDER BY recordedAt, id") suspend fun visits(id: String): List<VisitRow>
    @Query("SELECT * FROM household_answers WHERE interviewId = :id ORDER BY section, field") suspend fun householdAnswers(id: String): List<HouseholdAnswerRow>
    @Query("SELECT * FROM member_answers WHERE interviewId = :id ORDER BY memberId, section, field") suspend fun memberAnswers(id: String): List<MemberAnswerRow>
    @Query("SELECT * FROM member_answers WHERE section = 'demographics' AND field = 'sex'") fun observeSexAnswers(): Flow<List<MemberAnswerRow>>
    @Query("SELECT i.householdId, a.kind, a.value FROM household_answers a INNER JOIN interviews i ON i.id = a.interviewId WHERE a.section = 'setup' AND a.field = 'purok'")
    fun observePurokAnswers(): Flow<List<HouseholdPurokAnswer>>
    @Query("SELECT * FROM coordinates WHERE householdId = :id") suspend fun coordinates(id: String): CoordinateRow?
    @Query("SELECT * FROM coordinates WHERE householdId = :id") fun observeCoordinate(id: String): Flow<CoordinateRow?>
    @Query("SELECT * FROM coordinates") fun observeAllCoordinates(): Flow<List<CoordinateRow>>
    @Query("SELECT * FROM attachments WHERE householdId = :id ORDER BY id") suspend fun attachments(id: String): List<AttachmentRow>
    @Query("SELECT * FROM sync_outbox ORDER BY requestedAt, householdId") suspend fun pendingSyncs(): List<SyncOutboxRow>
    @Query("SELECT revision FROM sync_revisions WHERE ownerId = :ownerId AND householdId = :householdId")
    suspend fun syncRevision(ownerId: String, householdId: String): Long?
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
    @Upsert suspend fun putSyncOutbox(rows: List<SyncOutboxRow>)
    @Query("DELETE FROM sync_outbox WHERE householdId = :householdId") suspend fun clearSyncOutbox(householdId: String)
    @Query("UPDATE sync_outbox SET attempts = attempts + 1, lastError = :message WHERE householdId = :householdId")
    suspend fun failSyncOutbox(householdId: String, message: String)
    @Upsert suspend fun putSyncRevision(row: SyncRevisionRow)
}
