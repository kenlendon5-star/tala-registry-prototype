package ph.tala.registry.data.local

import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ph.tala.registry.domain.form.QuestionnaireSections
import ph.tala.registry.domain.model.AnswerValue

/**
 * Day 11 device acceptance: the six household-level service sections save to
 * `household_answers` once per household, survive a real database close and reopen,
 * and never land in `member_answers`.
 */
class HouseholdServicesPersistenceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: RegistryDatabase
    private lateinit var store: RoomRecordStore

    private val HOUSEHOLD_ID = "c1d2e3f4-0000-4a00-8000-000000000011"
    private val MEMBER_ID = "c1d2e3f4-0000-4a00-8000-000000000012"
    private val INTERVIEW_ID = "c1d2e3f4-0000-4a00-8000-000000000013"

    @Before fun startWithAnEmptyFile() {
        context.deleteDatabase("tala-registry-household-services-test.db")
        database = RegistryDatabase.open(context, "tala-registry-household-services-test.db")
        store = RoomRecordStore(database)
    }

    @After fun closeAndRemove() {
        database.close()
        context.deleteDatabase("tala-registry-household-services-test.db")
    }

    private fun reopen() {
        database.close()
        database = RegistryDatabase.open(context, "tala-registry-household-services-test.db")
        store = RoomRecordStore(database)
    }

    private fun scalar(section: String, field: String, text: String) =
        HouseholdAnswerRow(INTERVIEW_ID, section, field, "scalar", "\"$text\"")

    /** Evidence artifact pulled by tools/verify-d11-device.ps1 into planning/03-evidence/day11/. */
    private fun record(name: String, content: String) {
        val folder = File(context.getExternalFilesDir(null), "day11").apply { mkdirs() }
        File(folder, name).writeText(content)
    }

    @Test fun householdServiceSectionsSaveReloadAtHouseholdScope() {
        val health = QuestionnaireSections.health.route
        val food = QuestionnaireSections.food.route
        val transport = QuestionnaireSections.transport.route
        val internet = QuestionnaireSections.internet.route
        val water = QuestionnaireSections.water.route
        val housing = QuestionnaireSections.housing.route

        val record = StoredRecord(
            household = HouseholdRow(HOUSEHOLD_ID, "HH-01101", "Maria Santos", "24 Mabini Street", "InProgress"),
            members = listOf(MemberRow(MEMBER_ID, HOUSEHOLD_ID, "Maria Santos")),
            interview = InterviewRow(INTERVIEW_ID, HOUSEHOLD_ID),
            householdAnswers = listOf(
                scalar(health, "illness", "Yes"),
                scalar(health, "treatment", "Barangay health center"),
                scalar(health, "healthAccess", "Yes"),
                scalar(food, "foodWorry", "Sometimes"),
                scalar(food, "skippedMeals", "No"),
                scalar(food, "foodAssistance", "Yes"),
                scalar(transport, "transport", "Tricycle"),
                HouseholdAnswerRow(INTERVIEW_ID, transport, "accounts", "multi", "[\"Bank\",\"E-money\"]"),
                scalar(internet, "internet", "Yes"),
                scalar(internet, "connection", "Mobile data"),
                scalar(internet, "safety", "Somewhat safe"),
                scalar(water, "water", "Piped into dwelling"),
                scalar(water, "toilet", "Flush / pour flush"),
                HouseholdAnswerRow(INTERVIEW_ID, water, "waste", "multi", "[\"Collection service\",\"Composting\"]"),
                scalar(water, "handwashing", "Yes"),
                scalar(housing, "roof", "Metal roofing sheets"),
                scalar(housing, "walls", "Concrete / brick"),
                scalar(housing, "tenure", "Owned"),
                scalar(housing, "electricity", "Yes"),
                scalar(housing, "rooms", "3"),
            ),
        )

        assertEquals(WriteResult.Saved, runBlocking { store.save(record) })
        reopen()
        val loaded = requireNotNull(runBlocking { store.load(HOUSEHOLD_ID) })

        assertEquals("household", record.household, loaded.household)
        assertEquals("members", record.members, loaded.members)
        assertEquals("interview", record.interview, loaded.interview)
        assertTrue("household fields must never become member-scoped", loaded.memberAnswers.isEmpty())

        // Decode every stored row back into answer values and compare against the
        // saved record, so the check covers the codec as well as the row identity.
        val saved = record.householdAnswers.associate { (it.section to it.field) to AnswerCodec.decode(it.kind, it.value) }
        assertEquals(saved.size, loaded.householdAnswers.size)
        for (row in loaded.householdAnswers) {
            val key = row.section to row.field
            assertTrue("Unexpected household answer: $key", saved.containsKey(key))
            assertEquals("Mismatch for $key", saved[key], AnswerCodec.decode(row.kind, row.value))
        }

        // Spot-check one value per section after the reopen.
        val answers = loaded.householdAnswers.associate { "${it.section}.${it.field}" to AnswerCodec.decode(it.kind, it.value) }
        assertEquals(AnswerValue.Scalar("Yes"), answers["$health.illness"])
        assertEquals(AnswerValue.Scalar("Sometimes"), answers["$food.foodWorry"])
        assertEquals(AnswerValue.Multi(listOf("Bank", "E-money")), answers["$transport.accounts"])
        assertEquals(AnswerValue.Scalar("Mobile data"), answers["$internet.connection"])
        assertEquals(AnswerValue.Multi(listOf("Collection service", "Composting")), answers["$water.waste"])
        assertEquals(AnswerValue.Scalar("3"), answers["$housing.rooms"])

        record("household-services-reopen.txt", buildString {
            appendLine("Database: tala-registry-household-services-test.db closed and reopened")
            appendLine("Household answers after reopen: ${loaded.householdAnswers.size} rows across the six household sections")
            appendLine("Member answers after reopen: ${loaded.memberAnswers.size} rows (expected 0 - household fields are not member-scoped)")
            for (row in loaded.householdAnswers.sortedWith(compareBy({ it.section }, { it.field }))) {
                appendLine("${row.section}.${row.field} = ${AnswerCodec.decode(row.kind, row.value)}")
            }
        })
    }

    @Test fun replacingAHouseholdSectionDropsOnlyItsOwnAnswers() {
        val health = QuestionnaireSections.health.route
        val housing = QuestionnaireSections.housing.route

        val first = StoredRecord(
            household = HouseholdRow(HOUSEHOLD_ID, "HH-01102", "Juan Santos", "8 Rizal Street", "InProgress"),
            members = emptyList(),
            interview = InterviewRow(INTERVIEW_ID, HOUSEHOLD_ID),
            householdAnswers = listOf(
                scalar(health, "illness", "No"),
                scalar(health, "healthAccess", "Yes"),
                scalar(housing, "roof", "Wood / bamboo"),
                scalar(housing, "rooms", "2"),
            ),
        )
        assertEquals(WriteResult.Saved, runBlocking { store.save(first) })

        // Re-save with revised housing answers: every save is a full snapshot, so the
        // replaced rows must be exactly the four in the new snapshot.
        val second = first.copy(householdAnswers = listOf(
            scalar(health, "illness", "No"),
            scalar(health, "healthAccess", "Yes"),
            scalar(housing, "roof", "Metal roofing sheets"),
            scalar(housing, "electricity", "No"),
        ))
        assertEquals(WriteResult.Saved, runBlocking { store.save(second) })
        reopen()
        val loaded = requireNotNull(runBlocking { store.load(HOUSEHOLD_ID) })

        val stored = loaded.householdAnswers.associate { "${it.section}.${it.field}" to AnswerCodec.decode(it.kind, it.value) }
        assertEquals(4, stored.size)
        assertEquals(AnswerValue.Scalar("No"), stored["$health.illness"])
        assertEquals(AnswerValue.Scalar("Yes"), stored["$health.healthAccess"])
        assertEquals(AnswerValue.Scalar("Metal roofing sheets"), stored["$housing.roof"])
        assertEquals(AnswerValue.Scalar("No"), stored["$housing.electricity"])

        record("household-section-replacement.txt", buildString {
            appendLine("Full-snapshot re-save replaced the stored rows exactly")
            appendLine("Household answers after re-save and reopen: ${loaded.householdAnswers.size} rows (expected 4)")
            for (row in loaded.householdAnswers.sortedWith(compareBy({ it.section }, { it.field }))) {
                appendLine("${row.section}.${row.field} = ${AnswerCodec.decode(row.kind, row.value)}")
            }
        })
    }
}
