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
 * Day 12 device acceptance: Disaster preparedness and Social protection save to
 * `household_answers` once per household, survive a real database close and reopen,
 * and never land in `member_answers`.
 */
class DisasterProtectionPersistenceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: RegistryDatabase
    private lateinit var store: RoomRecordStore

    private val HOUSEHOLD_ID = "c1d2e3f4-0000-4a00-8000-000000000021"
    private val MEMBER_ID = "c1d2e3f4-0000-4a00-8000-000000000022"
    private val INTERVIEW_ID = "c1d2e3f4-0000-4a00-8000-000000000023"

    @Before fun startWithAnEmptyFile() {
        context.deleteDatabase("tala-registry-disaster-protection-test.db")
        database = RegistryDatabase.open(context, "tala-registry-disaster-protection-test.db")
        store = RoomRecordStore(database)
    }

    @After fun closeAndRemove() {
        database.close()
        context.deleteDatabase("tala-registry-disaster-protection-test.db")
    }

    private fun reopen() {
        database.close()
        database = RegistryDatabase.open(context, "tala-registry-disaster-protection-test.db")
        store = RoomRecordStore(database)
    }

    private fun scalar(section: String, field: String, text: String) =
        HouseholdAnswerRow(INTERVIEW_ID, section, field, "scalar", "\"$text\"")

    /** Evidence artifact pulled by tools/verify-d12-device.ps1 into planning/03-evidence/day12/. */
    private fun record(name: String, content: String) {
        val folder = File(context.getExternalFilesDir(null), "day12").apply { mkdirs() }
        File(folder, name).writeText(content)
    }

    @Test fun disasterProtectionSectionsSaveReloadAtHouseholdScope() {
        val disaster = QuestionnaireSections.disaster.route
        val protection = QuestionnaireSections.protection.route

        val record = StoredRecord(
            household = HouseholdRow(HOUSEHOLD_ID, "HH-01201", "Maria Santos", "24 Mabini Street", "InProgress"),
            members = listOf(MemberRow(MEMBER_ID, HOUSEHOLD_ID, "Maria Santos")),
            interview = InterviewRow(INTERVIEW_ID, HOUSEHOLD_ID),
            householdAnswers = listOf(
                scalar(disaster, "disasterType", "Natural"),
                HouseholdAnswerRow(INTERVIEW_ID, disaster, "hazards", "multi", "[\"Flood\",\"Typhoon\"]"),
                scalar(disaster, "evacuationKnown", "Yes"),
                scalar(disaster, "evacuation", "Other"),
                scalar(disaster, "evacuationOther", "Bgy. Multi-purpose building"),
                scalar(disaster, "emergencyKit", "Yes"),
                scalar(protection, "philHealth", "Yes"),
                scalar(protection, "philHealthRole", "Dependent"),
                scalar(protection, "dswd", "Yes"),
                scalar(protection, "fourPs", "No"),
                HouseholdAnswerRow(INTERVIEW_ID, protection, "programs", "multi", "[\"4Ps\",\"Senior citizen assistance\"]"),
                scalar(protection, "assistance", "Yes"),
                scalar(protection, "assistanceDetail", "Ayuda"),
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

        // Spot-check one value per branch shape after the reopen.
        val answers = loaded.householdAnswers.associate { "${it.section}.${it.field}" to AnswerCodec.decode(it.kind, it.value) }
        assertEquals(AnswerValue.Scalar("Natural"), answers["$disaster.disasterType"])
        assertEquals(AnswerValue.Multi(listOf("Flood", "Typhoon")), answers["$disaster.hazards"])
        assertEquals(AnswerValue.Scalar("Bgy. Multi-purpose building"), answers["$disaster.evacuationOther"])
        assertEquals(AnswerValue.Scalar("Dependent"), answers["$protection.philHealthRole"])
        assertEquals(AnswerValue.Multi(listOf("4Ps", "Senior citizen assistance")), answers["$protection.programs"])

        record("disaster-protection-reopen.txt", buildString {
            appendLine("Database: tala-registry-disaster-protection-test.db closed and reopened")
            appendLine("Household answers after reopen: ${loaded.householdAnswers.size} rows across the two household sections")
            appendLine("Member answers after reopen: ${loaded.memberAnswers.size} rows (expected 0 - household fields are not member-scoped)")
            for (row in loaded.householdAnswers.sortedWith(compareBy({ it.section }, { it.field }))) {
                appendLine("${row.section}.${row.field} = ${AnswerCodec.decode(row.kind, row.value)}")
            }
        })
    }

    @Test fun replacingAProtectionSectionDropsOnlyItsOwnAnswers() {
        val disaster = QuestionnaireSections.disaster.route
        val protection = QuestionnaireSections.protection.route

        val first = StoredRecord(
            household = HouseholdRow(HOUSEHOLD_ID, "HH-01202", "Juan Santos", "8 Rizal Street", "InProgress"),
            members = emptyList(),
            interview = InterviewRow(INTERVIEW_ID, HOUSEHOLD_ID),
            householdAnswers = listOf(
                scalar(disaster, "disasterType", "Man-made"),
                scalar(disaster, "evacuationKnown", "No"),
                scalar(protection, "philHealth", "No"),
                scalar(protection, "dswd", "No"),
                scalar(protection, "fourPs", "No"),
            ),
        )
        assertEquals(WriteResult.Saved, runBlocking { store.save(first) })

        // Re-save with revised protection answers: every save is a full snapshot, so the
        // replaced rows must be exactly the five in the new snapshot.
        val second = first.copy(householdAnswers = listOf(
            scalar(disaster, "disasterType", "Man-made"),
            scalar(disaster, "evacuationKnown", "No"),
            scalar(protection, "philHealth", "Yes"),
            scalar(protection, "philHealthRole", "Member"),
            HouseholdAnswerRow(INTERVIEW_ID, protection, "programs", "multi", "[\"PhilHealth\",\"SSS\"]"),
        ))
        assertEquals(WriteResult.Saved, runBlocking { store.save(second) })
        reopen()
        val loaded = requireNotNull(runBlocking { store.load(HOUSEHOLD_ID) })

        val stored = loaded.householdAnswers.associate { "${it.section}.${it.field}" to AnswerCodec.decode(it.kind, it.value) }
        assertEquals(5, stored.size)
        assertEquals(AnswerValue.Scalar("Man-made"), stored["$disaster.disasterType"])
        assertEquals(AnswerValue.Scalar("No"), stored["$disaster.evacuationKnown"])
        assertEquals(AnswerValue.Scalar("Yes"), stored["$protection.philHealth"])
        assertEquals(AnswerValue.Scalar("Member"), stored["$protection.philHealthRole"])
        assertEquals(AnswerValue.Multi(listOf("PhilHealth", "SSS")), stored["$protection.programs"])

        record("protection-section-replacement.txt", buildString {
            appendLine("Full-snapshot re-save replaced the stored rows exactly")
            appendLine("Household answers after re-save and reopen: ${loaded.householdAnswers.size} rows (expected 5)")
            for (row in loaded.householdAnswers.sortedWith(compareBy({ it.section }, { it.field }))) {
                appendLine("${row.section}.${row.field} = ${AnswerCodec.decode(row.kind, row.value)}")
            }
        })
    }
}
