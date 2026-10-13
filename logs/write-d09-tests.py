exec(open('logs/repair-d09.py', encoding='utf-8').read().split('# Keep profile media')[0])
test = 'test/java/ph/tala/registry/'
device = 'androidTest/java/ph/tala/registry/'

write(test + 'domain/form/DemographicSectionsValidationTest.kt', '''package ph.tala.registry.domain.form

import org.junit.Assert.*
import org.junit.Test
import ph.tala.registry.domain.model.*
import java.time.LocalDate

class DemographicSectionsValidationTest {
    private val today = LocalDate.of(2026, 10, 13)
    private fun answers(vararg pairs: Pair<String, String>): AnswerState = pairs.associate { it.first to AnswerValue.Scalar(it.second) }
    private fun error(section: SectionSpec, key: String, text: String, vararg parents: Pair<String, String>) =
        FormRules.error(section.fields.single { it.key == key }, answers(*parents) + (key to AnswerValue.Scalar(text)), today)

    @Test fun identitySectionsAreMemberScoped() {
        listOf(QuestionnaireSections.demographics, QuestionnaireSections.other, QuestionnaireSections.migration).forEach {
            assertTrue(it.scopedToMember)
            assertEquals(it.fields.size, it.fields.map { field -> field.key }.distinct().size)
        }
    }
    @Test fun birthDateRejectsFutureImpossibleAndMissingDates() {
        val section = QuestionnaireSections.demographics
        listOf("", "2026-10-14", "2025-02-29", "2000-13-01", "not a date").forEach { assertNotNull(it, error(section, "dob", it)) }
        listOf("2026-10-13", "2000-02-29", "1990-05-15").forEach { assertNull(it, error(section, "dob", it)) }
    }
    @Test fun voterAndOverseasRequireAnExplicitValidChoice() {
        listOf(QuestionnaireSections.demographics to "registeredVoter", QuestionnaireSections.migration to "overseas").forEach { (section, key) ->
            listOf("Yes", "No").forEach { assertNull(error(section, key, it)) }
            listOf("", "Maybe").forEach { assertNotNull(error(section, key, it)) }
        }
    }
    @Test fun optionalPhoneAndNamesRespectTheirActualRequirements() {
        val section = QuestionnaireSections.demographics
        listOf("", "09171234567", "0917-123-4567").forEach { assertNull(error(section, "phone", it)) }
        listOf("0917123456", "091712345678", "abc09171234567").forEach { assertNotNull(error(section, "phone", it)) }
        listOf("firstName", "lastName").forEach { assertNotNull(error(section, it, "")); assertNull(error(section, it, "Reyes")) }
        listOf("middleName", "alias", "extension").forEach { assertNull(error(section, it, "")) }
    }
    @Test fun soloParentIdIsOptionalAndOnlyVisibleForYes() {
        val section = QuestionnaireSections.other
        val id = section.fields.single { it.key == "soloParentId" }
        assertFalse(id.isVisible(answers("soloParent" to "No")))
        assertTrue(id.isVisible(answers("soloParent" to "Yes")))
        assertNull(error(section, "soloParentId", "", "soloParent" to "Yes"))
        assertNull(error(section, "soloParentId", "SP-123", "soloParent" to "Yes"))
        assertNotNull(error(section, "soloParent", ""))
        assertNotNull(error(section, "soloParent", "Maybe"))
    }
    @Test fun disabilityRegistrationAndIdValidateAllParentCombinations() {
        val section = QuestionnaireSections.other
        assertNotNull(error(section, "disability", ""))
        assertNotNull(error(section, "disability", "Maybe"))
        for (disability in listOf("Yes", "No")) for (registered in listOf("Yes", "No")) {
            val parents = arrayOf("disability" to disability, "registeredPwd" to registered)
            val requiredId = disability == "Yes" && registered == "Yes"
            assertEquals(requiredId, error(section, "pwdId", "", *parents) != null)
            assertNull(error(section, "pwdId", "PWD-123", *parents))
            assertEquals(disability == "Yes", error(section, "disabilityDetail", "", *parents) != null)
            assertNull(error(section, "disabilityDetail", "Visual impairment", *parents))
        }
        assertNotNull(error(section, "registeredPwd", "", "disability" to "Yes"))
        assertNotNull(error(section, "registeredPwd", "Maybe", "disability" to "Yes"))
        assertNull(error(section, "registeredPwd", "", "disability" to "No"))
    }
    @Test fun migrationRequiresResidenceAndValidYearOnlyWhenMoved() {
        val section = QuestionnaireSections.migration
        listOf("", "1899", "2027", "not_a_number", "2020.5").forEach { assertNotNull(it, error(section, "moveYear", it, "moved" to "Yes")) }
        listOf("1900", "2020", "2026").forEach { assertNull(error(section, "moveYear", it, "moved" to "Yes")) }
        assertNotNull(error(section, "previousResidence", "", "moved" to "Yes"))
        assertNull(error(section, "previousResidence", "Cebu", "moved" to "Yes"))
        assertNull(error(section, "previousResidence", "", "moved" to "No"))
        assertNull(error(section, "moveYear", "invalid", "moved" to "No"))
        assertNotNull(error(section, "citizenship", ""))
        assertNotNull(error(section, "moved", "Maybe"))
    }
}
''')

write(test + 'domain/form/DemographicSectionsBranchingTest.kt', '''package ph.tala.registry.domain.form

import org.junit.Assert.*
import org.junit.Test
import ph.tala.registry.domain.model.*

class DemographicSectionsBranchingTest {
    private fun answers(vararg pairs: Pair<String, String>): AnswerState = pairs.associate { it.first to AnswerValue.Scalar(it.second) }
    @Test fun disabilityReversalClearsTheWholeCascadeAndDoesNotResurrectIt() {
        val section = QuestionnaireSections.other
        val start = answers("disability" to "Yes", "registeredPwd" to "Yes", "pwdId" to "PWD-123", "disabilityDetail" to "Visual", "soloParent" to "Yes", "soloParentId" to "SP-123")
        val no = FormRules.update(section, start, "disability", AnswerValue.Scalar("No"))
        listOf("registeredPwd", "pwdId", "disabilityDetail").forEach { assertFalse(no.containsKey(it)) }
        assertEquals("SP-123", no.scalar("soloParentId"))
        assertEquals(no, FormRules.update(section, no, "pwdId", AnswerValue.Scalar("late callback")))
        val yes = FormRules.update(section, no, "disability", AnswerValue.Scalar("Yes"))
        assertFalse(yes.containsKey("registeredPwd")); assertFalse(yes.containsKey("pwdId"))
        assertTrue(FormRules.validate(section, yes).containsKey("registeredPwd"))
    }
    @Test fun unregisteringPwdClearsOnlyId() {
        val section = QuestionnaireSections.other
        val start = answers("disability" to "Yes", "registeredPwd" to "Yes", "pwdId" to "PWD-123", "disabilityDetail" to "Visual")
        val no = FormRules.update(section, start, "registeredPwd", AnswerValue.Scalar("No"))
        assertFalse(no.containsKey("pwdId")); assertEquals("Visual", no.scalar("disabilityDetail"))
    }
    @Test fun soloParentReversalClearsOptionalId() {
        val section = QuestionnaireSections.other
        val no = FormRules.update(section, answers("soloParent" to "Yes", "soloParentId" to "SP-123"), "soloParent", AnswerValue.Scalar("No"))
        assertFalse(no.containsKey("soloParentId"))
        assertFalse(FormRules.update(section, no, "soloParent", AnswerValue.Scalar("Yes")).containsKey("soloParentId"))
    }
    @Test fun migrationReversalClearsResidenceAndYearButPreservesOverseas() {
        val section = QuestionnaireSections.migration
        val no = FormRules.update(section, answers("moved" to "Yes", "previousResidence" to "Cebu", "moveYear" to "2020", "overseas" to "Yes"), "moved", AnswerValue.Scalar("No"))
        assertFalse(no.containsKey("previousResidence")); assertFalse(no.containsKey("moveYear"))
        assertEquals("Yes", no.scalar("overseas"))
        val yes = FormRules.update(section, no, "moved", AnswerValue.Scalar("Yes"))
        assertFalse(yes.containsKey("moveYear"))
        assertTrue(FormRules.validate(section, yes).containsKey("previousResidence"))
    }
}
''')

write(test + 'domain/form/DemographicSectionsMemberIsolationTest.kt', '''package ph.tala.registry.domain.form

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import ph.tala.registry.data.*
import ph.tala.registry.data.local.WriteResult
import ph.tala.registry.domain.edit.*
import ph.tala.registry.domain.model.*
import ph.tala.registry.ui.households.SavePhase
import ph.tala.registry.ui.interview.InterviewWorkspaceViewModel

@OptIn(ExperimentalCoroutinesApi::class)
class DemographicSectionsMemberIsolationTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = FakeRecordStore()
    private val repository = RoomHouseholdRepository(store)
    private val household = HouseholdId("d0900000-0000-4000-8000-000000000001")
    private val maria = MemberId("d0900000-0000-4000-8000-000000000002")
    private val juan = MemberId("d0900000-0000-4000-8000-000000000003")
    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { Dispatchers.resetMain() }
    private suspend fun create() {
        assertEquals(WriteResult.Saved, repository.createHousehold(household, HouseholdDraft("Identity fixture", "Demo street", HouseholdStatus.InProgress)))
        repository.saveMember(household, maria, MemberDraft("Maria"))
        repository.saveMember(household, juan, MemberDraft("Juan"))
    }
    @Test fun realWorkspaceSavesBeforeSwitchingAndReloadsAllThreeMemberSections() = runTest(dispatcher) {
        create()
        val model = InterviewWorkspaceViewModel(household, repository)
        advanceUntilIdle()
        for ((section, key) in listOf(QuestionnaireSections.demographics to "registeredVoter", QuestionnaireSections.other to "soloParent", QuestionnaireSections.migration to "overseas")) {
            model.select(section.route, maria.value); advanceUntilIdle()
            model.answer(key, AnswerValue.Scalar("Yes"))
            model.select(section.route, juan.value); advanceUntilIdle() // cancels debounce, must flush Maria
            assertFalse(model.state.value.answers.containsKey(key))
            model.answer(key, AnswerValue.Scalar("No"))
            model.select(section.route, maria.value); advanceUntilIdle()
            assertEquals("Yes", model.state.value.answers.scalar(key))
            val recreated = InterviewWorkspaceViewModel(household, repository, initialRoute = section.route, initialMember = juan.value)
            advanceUntilIdle()
            assertEquals("No", recreated.state.value.answers.scalar(key))
        }
    }
    @Test fun failedSaveKeepsMemberAndDraftUntilSuccessfulRetry() = runTest(dispatcher) {
        create()
        val model = InterviewWorkspaceViewModel(household, repository, initialRoute = "migration", initialMember = maria.value)
        advanceUntilIdle()
        store.failWith = "Storage full"
        model.answer("overseas", AnswerValue.Scalar("Yes"))
        model.select("migration", juan.value); advanceUntilIdle()
        assertEquals(maria.value, model.state.value.memberId)
        assertEquals(SavePhase.Failed, model.state.value.phase)
        assertEquals("Yes", model.state.value.answers.scalar("overseas"))
        store.failWith = null
        model.select("migration", juan.value); advanceUntilIdle()
        assertEquals(juan.value, model.state.value.memberId)
        assertTrue(model.state.value.answers.isEmpty())
        assertEquals("Yes", InterviewRules.answers(repository.interviewRecord(household)!!, QuestionnaireSections.migration, maria.value).scalar("overseas"))
    }
    @Test fun clearingOneMembersBranchPreservesOtherMembersAndSections() = runTest(dispatcher) {
        create()
        val model = InterviewWorkspaceViewModel(household, repository)
        advanceUntilIdle()
        for (member in listOf(maria, juan)) {
            model.select("other", member.value); advanceUntilIdle()
            model.answer("disability", AnswerValue.Scalar("Yes"))
            model.answer("registeredPwd", AnswerValue.Scalar("Yes"))
            model.answer("pwdId", AnswerValue.Scalar(member.value))
            model.answer("disabilityDetail", AnswerValue.Scalar("Visual"))
            model.select("migration", member.value); advanceUntilIdle()
            model.answer("overseas", AnswerValue.Scalar("Yes")); advanceUntilIdle()
        }
        model.select("other", maria.value); advanceUntilIdle()
        model.answer("disability", AnswerValue.Scalar("No")); advanceUntilIdle()
        val record = repository.interviewRecord(household)!!
        assertFalse(InterviewRules.answers(record, QuestionnaireSections.other, maria.value).containsKey("pwdId"))
        assertEquals(juan.value, InterviewRules.answers(record, QuestionnaireSections.other, juan.value).scalar("pwdId"))
        assertEquals("Yes", InterviewRules.answers(record, QuestionnaireSections.migration, maria.value).scalar("overseas"))
    }
}
''')

write(test + 'ui/interview/InterviewSectionsViewModelTest.kt', '''package ph.tala.registry.ui.interview

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import ph.tala.registry.data.*
import ph.tala.registry.data.local.WriteResult
import ph.tala.registry.domain.edit.*
import ph.tala.registry.domain.form.*
import ph.tala.registry.domain.model.*
import ph.tala.registry.ui.households.SavePhase

@OptIn(ExperimentalCoroutinesApi::class)
class InterviewSectionsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { Dispatchers.resetMain() }
    @Test fun staleWorkspaceCannotOverwriteAChangedMemberSection() = runTest(dispatcher) {
        val repo = RoomHouseholdRepository(FakeRecordStore())
        val hh = HouseholdId("hh"); val member = MemberId("member")
        repo.createHousehold(hh, HouseholdDraft("Fixture", "Street", HouseholdStatus.InProgress))
        repo.saveMember(hh, member, MemberDraft("Maria"))
        val first = InterviewWorkspaceViewModel(hh, repo, initialRoute = "demographics", initialMember = member.value)
        val stale = InterviewWorkspaceViewModel(hh, repo, initialRoute = "demographics", initialMember = member.value)
        advanceUntilIdle()
        first.answer("registeredVoter", AnswerValue.Scalar("Yes")); advanceUntilIdle()
        stale.answer("registeredVoter", AnswerValue.Scalar("No")); advanceUntilIdle()
        assertEquals(SavePhase.Failed, stale.state.value.phase)
        assertEquals("Yes", InterviewRules.answers(repo.interviewRecord(hh)!!, QuestionnaireSections.demographics, member.value).scalar("registeredVoter"))
        stale.reload(); advanceUntilIdle()
        assertEquals("Yes", stale.state.value.answers.scalar("registeredVoter"))
    }
}
''')

write(test + 'ui/interview/InterviewAnswersRepositoryTest.kt', '''package ph.tala.registry.ui.interview

import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.*
import ph.tala.registry.data.*
import ph.tala.registry.data.local.WriteResult
import ph.tala.registry.domain.edit.*
import ph.tala.registry.domain.model.*

class InterviewAnswersRepositoryTest {
    @Test fun memberPicturesNeverReplaceHouseholdOrOtherMemberPictures() = runTest {
        val store = FakeRecordStore()
        val repo = RoomHouseholdRepository(store)
        val hh = HouseholdId("hh"); val a = MemberId("a"); val b = MemberId("b")
        repo.createHousehold(hh, HouseholdDraft("Fixture", "Street", HouseholdStatus.InProgress))
        repo.saveMember(hh, a, MemberDraft("Maria")); repo.saveMember(hh, b, MemberDraft("Juan"))
        repo.saveHouseholdPhotoAttachment(hh, "attachments/house.jpg", "image/jpeg")
        repo.saveMemberPhotoAttachment(hh, a, "attachments/a.jpg", "image/jpeg")
        repo.saveMemberPhotoAttachment(hh, b, "attachments/b.jpg", "image/jpeg")
        repo.saveMemberPhotoAttachment(hh, a, "attachments/a-new.jpg", "image/jpeg")
        assertEquals(setOf("attachments/house.jpg", "attachments/a-new.jpg", "attachments/b.jpg"), repo.interviewRecord(hh)!!.attachments.map { it.relativePath }.toSet())
        store.failWith = "Full"
        assertTrue(repo.removeMemberPhotoAttachment(hh, a) is WriteResult.Failed)
        assertEquals(3, repo.interviewRecord(hh)!!.attachments.size)
        store.failWith = null
        repo.removeHouseholdPhotoAttachment(hh)
        assertEquals(setOf(a.value, b.value), repo.interviewRecord(hh)!!.attachments.map { it.memberId }.toSet())
        repo.removeMemberPhotoAttachment(hh, a)
        assertEquals(b.value, repo.interviewRecord(hh)!!.attachments.single().memberId)
        assertTrue(repo.saveMemberPhotoAttachment(hh, MemberId("missing"), "attachments/x.jpg", "image/jpeg") is WriteResult.Failed)
    }
}
''')

# Device-only persistence test lives in androidTest, uses production repository saves,
# and compares answers by their keys instead of assuming insertion order.
old = base / (test + 'data/local/DemographicSectionsPersistenceTest.kt')
new = base / (device + 'data/local/DemographicSectionsPersistenceTest.kt')
new.parent.mkdir(parents=True, exist_ok=True)
old.rename(new)
write(device + 'data/local/DemographicSectionsPersistenceTest.kt', '''package ph.tala.registry.data.local

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
''')
