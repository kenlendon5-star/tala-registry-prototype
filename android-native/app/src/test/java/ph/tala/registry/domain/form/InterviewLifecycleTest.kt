package ph.tala.registry.domain.form

import ph.tala.registry.data.local.HouseholdAnswerRow
import ph.tala.registry.data.local.MemberAnswerRow
import ph.tala.registry.data.local.HouseholdRow
import ph.tala.registry.data.local.MemberRow
import ph.tala.registry.data.local.InterviewRow

import org.junit.Assert.*
import org.junit.Test
import ph.tala.registry.data.HouseholdRepository
import ph.tala.registry.data.local.AttachmentRow
import ph.tala.registry.data.local.StoredRecord

/**
 * Day 13 interview lifecycle rules: local progress calculation and the consent gate that
 * accepts a drawn signature or a typed name, while a refusal stays a reviewable draft only.
 */
class InterviewLifecycleTest {

    private fun scalarRow(section: String, field: String, text: String) =
        HouseholdAnswerRow("interview", section, field, "scalar", "\"$text\"")

    private fun record(
        members: List<MemberRow> = listOf(MemberRow("member-1", "hh-1", "Maria Santos")),
        householdAnswers: List<HouseholdAnswerRow> = emptyList(),
        memberAnswers: List<MemberAnswerRow> = emptyList(),
        attachments: List<AttachmentRow> = emptyList(),
        status: String = "InProgress",
    ) = StoredRecord(
        household = HouseholdRow("hh-1", "HH-01301", "Maria Santos", "24 Mabini Street", status),
        members = members,
        interview = InterviewRow("interview", "hh-1", QuestionnaireSections.VERSION),
        householdAnswers = householdAnswers,
        memberAnswers = memberAnswers,
        attachments = attachments,
    )

    private fun signatureAttachment() =
        AttachmentRow("attachment-1", "hh-1", null, HouseholdRepository.SIGNATURE, "attachments/signed.png", "image/png")

    @Test fun `an empty interview reports no ready sections`() {
        val progress = InterviewRules.progress(record(members = emptyList()))
        assertEquals(QuestionnaireSections.all.size, progress.total)
        assertEquals("no answers means nothing is ready, including setup", 0, progress.ready)
        assertFalse(progress.allReady)
    }

    @Test fun `every filled household section counts ready once`() {
        // Disaster + protection fully answered; the remaining household sections stay empty.
        val disaster = listOf(
            scalarRow("disaster", "disasterType", "Natural"),
            HouseholdAnswerRow("interview", "disaster", "hazards", "multi", "[\"Flood\"]"),
            scalarRow("disaster", "evacuationKnown", "Yes"),
            scalarRow("disaster", "evacuation", "Barangay Hall (Sample)"),
            scalarRow("disaster", "emergencyKit", "Yes"),
        )
        val protection = listOf(
            scalarRow("protection", "philHealth", "Yes"),
            scalarRow("protection", "philHealthRole", "Member"),
            scalarRow("protection", "dswd", "Yes"),
            scalarRow("protection", "fourPs", "Yes"),
            HouseholdAnswerRow("interview", "protection", "programs", "multi", "[\"4Ps\"]"),
            scalarRow("protection", "assistance", "Yes"),
            scalarRow("protection", "assistanceDetail", "Ayuda"),
        )
        val progress = InterviewRules.progress(record(householdAnswers = disaster + protection))
        assertEquals("disaster, protection and the populated roster are ready", 3, progress.ready)
    }

    @Test fun `a member section is ready only when every member passes`() {
        val answers = listOf(
            scalarRow("education", "attending", "Yes"),
            scalarRow("education", "school", "Public"),
            scalarRow("education", "grade", "Grade 10"),
            scalarRow("education", "educationAssistance", "No"),
        )
        val one = record(memberAnswers = answers.map { MemberAnswerRow("interview", "hh-1", "member-1", it.section, it.field, it.kind, it.value) })
        assertEquals("roster and the complete education section", 2, InterviewRules.progress(one).ready)
        // A second member with no answers keeps the section not ready.
        val two = one.copy(members = one.members + MemberRow("member-2", "hh-1", "Juan Santos"))
        assertEquals("only the populated roster is ready until both members pass", 1, InterviewRules.progress(two).ready)
    }

    @Test fun `consent accepts a typed name without a drawn signature`() {
        val answers = listOf(scalarRow("consent", "consent", "Yes"), scalarRow("consent", "signedName", "Maria Santos"))
        val issues = InterviewRules.issues(record(householdAnswers = answers)).filter { it.route == "consent" }
        assertTrue("typed name completes consent", issues.isEmpty())
    }

    @Test fun `consent accepts a drawn signature without a typed name`() {
        val answers = listOf(scalarRow("consent", "consent", "Yes"))
        val issues = InterviewRules.issues(record(householdAnswers = answers, attachments = listOf(signatureAttachment())))
            .filter { it.route == "consent" }
        assertTrue("the drawn mark completes the step", issues.isEmpty())
    }

    @Test fun `consent with neither signature nor typed name is not ready`() {
        val answers = listOf(scalarRow("consent", "consent", "Yes"))
        val issues = InterviewRules.issues(record(householdAnswers = answers)).filter { it.route == "consent" }
        assertEquals(1, issues.size)
        assertTrue("names both modes", issues.single().errors.values.single().contains("Draw a signature"))
        assertEquals("consent stays open; only the roster is ready", 1, InterviewRules.progress(record(householdAnswers = answers)).ready)
    }

    @Test fun `refusal keeps the record a draft and names the rule`() {
        val answers = listOf(scalarRow("consent", "consent", "No"))
        val issues = InterviewRules.issues(record(householdAnswers = answers)).filter { it.route == "consent" }
        assertEquals(1, issues.size)
        assertTrue(issues.single().errors.values.single().contains("Refusal remains saved as a draft"))
    }
}
