package ph.tala.registry.data.local

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import ph.tala.registry.data.RoomHouseholdRepository
import ph.tala.registry.domain.edit.*
import ph.tala.registry.domain.form.*
import ph.tala.registry.domain.model.*

class DemographicSectionsPersistenceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "d09-identity-persistence.db"
    private lateinit var database: RegistryDatabase
    private lateinit var repo: RoomHouseholdRepository
    private val hh = HouseholdId("d0900000-0000-4000-8000-000000000001")
    private val a = MemberId("d0900000-0000-4000-8000-000000000002")
    private val b = MemberId("d0900000-0000-4000-8000-000000000003")
    @Before fun before() { context.deleteDatabase(name); open() }
    private fun open() { database = RegistryDatabase.open(context, name); repo = RoomHouseholdRepository(RoomRecordStore(database)) }
    @After fun after() { database.close(); context.deleteDatabase(name) }
    private suspend fun answers(section: SectionSpec, member: MemberId) = InterviewRules.answers(repo.interviewRecord(hh)!!, section, member.value)
    private suspend fun save(section: SectionSpec, member: MemberId, vararg values: Pair<String, String>) {
        val before = answers(section, member)
        assertEquals(WriteResult.Saved, repo.saveDraftSection(hh, section.route, member, before + values.associate { it.first to AnswerValue.Scalar(it.second) }, before))
    }
    @Test fun allIdentitySectionsAndClearedBranchesSurviveDatabaseReopen() = runBlocking {
        assertEquals(WriteResult.Saved, repo.createHousehold(hh, HouseholdDraft("Identity fixture", "Demo street", HouseholdStatus.InProgress)))
        for ((member, first) in listOf(a to "Maria", b to "Juan")) {
            repo.saveMember(hh, member, MemberDraft(first))
            save(QuestionnaireSections.demographics, member, "firstName" to first, "lastName" to "Santos", "registeredVoter" to if (member == a) "Yes" else "No", "dob" to if (member == a) "1990-05-15" else "1985-08-22")
            save(QuestionnaireSections.other, member, "soloParent" to "Yes", "soloParentId" to "SP-$first", "disability" to "Yes", "registeredPwd" to "Yes", "pwdId" to "PWD-$first", "disabilityDetail" to "Visual")
            save(QuestionnaireSections.migration, member, "citizenship" to "Filipino", "moved" to "Yes", "previousResidence" to if (member == a) "Cebu" else "Davao", "moveYear" to "2020", "overseas" to if (member == a) "Yes" else "No")
        }
        val bBefore = repo.interviewRecord(hh)!!.memberAnswers.filter { it.memberId == b.value }.toSet()
        save(QuestionnaireSections.other, a, "disability" to "No", "soloParent" to "No")
        save(QuestionnaireSections.migration, a, "moved" to "No")
        database.close(); open()
        val record = repo.interviewRecord(hh)!!
        assertEquals(bBefore, record.memberAnswers.filter { it.memberId == b.value }.toSet())
        assertEquals("Maria", answers(QuestionnaireSections.demographics, a).scalar("firstName"))
        assertEquals("Juan", answers(QuestionnaireSections.demographics, b).scalar("firstName"))
        listOf("pwdId", "registeredPwd", "disabilityDetail", "soloParentId").forEach { assertFalse(answers(QuestionnaireSections.other, a).containsKey(it)) }
        listOf("previousResidence", "moveYear").forEach { assertFalse(answers(QuestionnaireSections.migration, a).containsKey(it)) }
        assertEquals("Yes", answers(QuestionnaireSections.migration, a).scalar("overseas"))
        assertEquals("No", answers(QuestionnaireSections.migration, b).scalar("overseas"))
        assertEquals(2, record.members.size)
    }
}
