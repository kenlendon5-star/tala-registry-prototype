package ph.tala.registry.data.local

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ph.tala.registry.data.local.AnswerCodec
import ph.tala.registry.domain.model.AnswerValue
import ph.tala.registry.domain.model.HouseholdId
import ph.tala.registry.domain.model.MemberId
import ph.tala.registry.domain.form.QuestionnaireSections

class EducationEmploymentPersistenceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: ph.tala.registry.data.local.RegistryDatabase
    private lateinit var store: ph.tala.registry.data.local.RoomRecordStore

    private val HOUSEHOLD_ID = "b1b2c3d4-0000-4a00-8000-000000000001"
    private val MEMBER_ONE_ID = "b1b2c3d4-0000-4a00-8000-000000000002"
    private val MEMBER_TWO_ID = "b1b2c3d4-0000-4a00-8000-000000000003"
    private val INTERVIEW_ID = "b1b2c3d4-0000-4a00-8000-000000000004"

    @Before fun startWithAnEmptyFile() {
        context.deleteDatabase("tala-registry-education-employment-test.db")
        database = ph.tala.registry.data.local.RegistryDatabase.open(context, "tala-registry-education-employment-test.db")
        store = ph.tala.registry.data.local.RoomRecordStore(database)
    }

    @After fun closeAndRemove() {
        database.close()
        context.deleteDatabase("tala-registry-education-employment-test.db")
    }

    private fun reopen() {
        database.close()
        database = ph.tala.registry.data.local.RegistryDatabase.open(context, "tala-registry-education-employment-test.db")
        store = ph.tala.registry.data.local.RoomRecordStore(database)
    }

    private fun load(): ph.tala.registry.data.local.StoredRecord? = runBlocking { store.load(HOUSEHOLD_ID) }

    private fun scalar(row: ph.tala.registry.data.local.MemberAnswerRow): String =
        (AnswerCodec.decode(row.kind, row.value) as AnswerValue.Scalar).text

    @Test fun educationEmploymentSectionsMemberAnswersStayAttachedToCorrectMember() {
        val record = ph.tala.registry.data.local.StoredRecord(
            household = ph.tala.registry.data.local.HouseholdRow(
                HOUSEHOLD_ID, "HH-01001", "Maria Santos", "24 Mabini Street", "InProgress"
            ),
            members = listOf(
                ph.tala.registry.data.local.MemberRow(MEMBER_ONE_ID, HOUSEHOLD_ID, "Maria Santos", sortOrder = 0),
                ph.tala.registry.data.local.MemberRow(MEMBER_TWO_ID, HOUSEHOLD_ID, "Juan Santos", sortOrder = 1)
            ),
            interview = ph.tala.registry.data.local.InterviewRow(INTERVIEW_ID, HOUSEHOLD_ID),
            visits = emptyList(),
            householdAnswers = emptyList(),
            memberAnswers = listOf(
                ph.tala.registry.data.local.MemberAnswerRow(
                    INTERVIEW_ID, HOUSEHOLD_ID, MEMBER_ONE_ID,
                    QuestionnaireSections.education.route, "attending", "scalar", "\"Yes\""
                ),
                ph.tala.registry.data.local.MemberAnswerRow(
                    INTERVIEW_ID, HOUSEHOLD_ID, MEMBER_ONE_ID,
                    QuestionnaireSections.education.route, "school", "scalar", "\"Private\""
                ),
                ph.tala.registry.data.local.MemberAnswerRow(
                    INTERVIEW_ID, HOUSEHOLD_ID, MEMBER_ONE_ID,
                    QuestionnaireSections.education.route, "track", "scalar", "\"STEM\""
                ),
                ph.tala.registry.data.local.MemberAnswerRow(
                    INTERVIEW_ID, HOUSEHOLD_ID, MEMBER_ONE_ID,
                    QuestionnaireSections.education.route, "grade", "scalar", "\"Grade 10\""
                ),
                ph.tala.registry.data.local.MemberAnswerRow(
                    INTERVIEW_ID, HOUSEHOLD_ID, MEMBER_ONE_ID,
                    QuestionnaireSections.education.route, "educationAssistance", "scalar", "\"Yes\""
                ),
                ph.tala.registry.data.local.MemberAnswerRow(
                    INTERVIEW_ID, HOUSEHOLD_ID, MEMBER_TWO_ID,
                    QuestionnaireSections.education.route, "attending", "scalar", "\"No\""
                ),
                ph.tala.registry.data.local.MemberAnswerRow(
                    INTERVIEW_ID, HOUSEHOLD_ID, MEMBER_TWO_ID,
                    QuestionnaireSections.education.route, "reason", "scalar", "\"Working\""
                ),
                ph.tala.registry.data.local.MemberAnswerRow(
                    INTERVIEW_ID, HOUSEHOLD_ID, MEMBER_TWO_ID,
                    QuestionnaireSections.education.route, "grade", "scalar", "\"College graduate\""
                ),
                ph.tala.registry.data.local.MemberAnswerRow(
                    INTERVIEW_ID, HOUSEHOLD_ID, MEMBER_TWO_ID,
                    QuestionnaireSections.education.route, "educationAssistance", "scalar", "\"No\""
                ),
                ph.tala.registry.data.local.MemberAnswerRow(
                    INTERVIEW_ID, HOUSEHOLD_ID, MEMBER_ONE_ID,
                    QuestionnaireSections.employment.route, "worked", "scalar", "\"Yes\""
                ),
                ph.tala.registry.data.local.MemberAnswerRow(
                    INTERVIEW_ID, HOUSEHOLD_ID, MEMBER_ONE_ID,
                    QuestionnaireSections.employment.route, "occupation", "scalar", "\"Teacher\""
                ),
                ph.tala.registry.data.local.MemberAnswerRow(
                    INTERVIEW_ID, HOUSEHOLD_ID, MEMBER_ONE_ID,
                    QuestionnaireSections.employment.route, "industry", "scalar", "\"Education\""
                ),
                ph.tala.registry.data.local.MemberAnswerRow(
                    INTERVIEW_ID, HOUSEHOLD_ID, MEMBER_ONE_ID,
                    QuestionnaireSections.employment.route, "employmentType", "scalar", "\"Employee\""
                ),
                ph.tala.registry.data.local.MemberAnswerRow(
                    INTERVIEW_ID, HOUSEHOLD_ID, MEMBER_ONE_ID,
                    QuestionnaireSections.employment.route, "hours", "scalar", "\"40\""
                ),
                ph.tala.registry.data.local.MemberAnswerRow(
                    INTERVIEW_ID, HOUSEHOLD_ID, MEMBER_TWO_ID,
                    QuestionnaireSections.employment.route, "worked", "scalar", "\"No\""
                ),
                ph.tala.registry.data.local.MemberAnswerRow(
                    INTERVIEW_ID, HOUSEHOLD_ID, MEMBER_TWO_ID,
                    QuestionnaireSections.employment.route, "looking", "scalar", "\"Yes\""
                ),
                ph.tala.registry.data.local.MemberAnswerRow(
                    INTERVIEW_ID, HOUSEHOLD_ID, MEMBER_TWO_ID,
                    QuestionnaireSections.employment.route, "notWorkingReason", "scalar", "\"Student\""
                ),
                ph.tala.registry.data.local.MemberAnswerRow(
                    INTERVIEW_ID, HOUSEHOLD_ID, MEMBER_TWO_ID,
                    QuestionnaireSections.employment.route, "available", "scalar", "\"Yes\""
                )
            ),
            coordinates = null,
            attachments = emptyList()
        )

        assertEquals(ph.tala.registry.data.local.WriteResult.Saved, runBlocking { store.save(record) })
        reopen()
        val loaded = requireNotNull(load())

        // Verify non-answer fields match exactly
        assertEquals("household", record.household, loaded.household)
        assertEquals("members", record.members, loaded.members)
        assertEquals("interview", record.interview, loaded.interview)
        assertEquals("visits", record.visits, loaded.visits)
        assertEquals("householdAnswers", record.householdAnswers, loaded.householdAnswers)
        assertEquals("coordinates", record.coordinates, loaded.coordinates)
        assertEquals("attachments", record.attachments, loaded.attachments)

        // Build a map of expected answers keyed by (memberId, section, field) for order-agnostic verification
        val expected = mapOf(
            "$MEMBER_ONE_ID-education-attending" to "Yes",
            "$MEMBER_ONE_ID-education-school" to "Private",
            "$MEMBER_ONE_ID-education-track" to "STEM",
            "$MEMBER_ONE_ID-education-grade" to "Grade 10",
            "$MEMBER_ONE_ID-education-educationAssistance" to "Yes",
            "$MEMBER_TWO_ID-education-attending" to "No",
            "$MEMBER_TWO_ID-education-reason" to "Working",
            "$MEMBER_TWO_ID-education-grade" to "College graduate",
            "$MEMBER_TWO_ID-education-educationAssistance" to "No",
            "$MEMBER_ONE_ID-employment-worked" to "Yes",
            "$MEMBER_ONE_ID-employment-occupation" to "Teacher",
            "$MEMBER_ONE_ID-employment-industry" to "Education",
            "$MEMBER_ONE_ID-employment-employmentType" to "Employee",
            "$MEMBER_ONE_ID-employment-hours" to "40",
            "$MEMBER_TWO_ID-employment-worked" to "No",
            "$MEMBER_TWO_ID-employment-looking" to "Yes",
            "$MEMBER_TWO_ID-employment-notWorkingReason" to "Student",
            "$MEMBER_TWO_ID-employment-available" to "Yes"
        )

        // Verify count matches
        assertEquals(expected.size, loaded.memberAnswers.size)

        // Verify each loaded answer matches expected
        for (row in loaded.memberAnswers) {
            val key = "${row.memberId}-${row.section}-${row.field}"
            assertTrue("Unexpected answer key: $key", expected.containsKey(key))
            assertEquals("Mismatch for $key", expected[key]!!, scalar(row))
        }
    }
}
