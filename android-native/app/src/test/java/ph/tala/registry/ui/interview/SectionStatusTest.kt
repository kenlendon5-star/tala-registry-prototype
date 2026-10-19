package ph.tala.registry.ui.interview

import org.junit.Assert.assertEquals
import org.junit.Test
import ph.tala.registry.domain.model.AnswerState
import ph.tala.registry.domain.model.AnswerValue
import ph.tala.registry.domain.model.InterviewData

/** Chip/directory readiness must match the review-progress rule exactly. */
class SectionStatusTest {

    private class FakeRecord(
        val members: Map<String, String> = emptyMap(),
        val household: Map<String, AnswerState> = emptyMap(),
        val perMember: Map<String, Map<String, AnswerState>> = emptyMap(),
        val drawnSignature: Boolean = false,
    ) : InterviewData {
        override val memberNames: Map<String, String> = members
        override val hasDrawnSignature: Boolean = drawnSignature
        override fun sectionAnswers(route: String, memberId: String?): AnswerState =
            if (memberId != null) perMember[memberId].orEmpty()[route].orEmpty()
            else household[route].orEmpty()
    }

    private fun scalar(vararg pairs: Pair<String, String>): AnswerState =
        pairs.associate { (key, value) -> key to AnswerValue.Scalar(value) }

    @Test
    fun `empty record leaves every section not started`() {
        val statuses = sectionStatuses(FakeRecord())
        assertEquals(17, statuses.size)
        assertEquals(SectionStatus.NotStarted, statuses["setup"])
        assertEquals(SectionStatus.NotStarted, statuses["consent"])
    }

    @Test
    fun `null record has no statuses`() {
        assertEquals(emptyMap<String, SectionStatus>(), sectionStatuses(null))
    }

    @Test
    fun `fully answered household scope is ready`() {
        val record = FakeRecord(
            members = mapOf("m1" to "Ana Reyes"),
            household = mapOf(
                "setup" to scalar("enumerator" to "Kim", "barangay" to "Barangay Cogon", "purok" to "1", "interviewDate" to "2026-02-01"),
                "visit" to scalar("visitDate" to "2026-02-01", "visitResult" to "Completed visit"),
            ),
        )
        assertEquals(SectionStatus.Ready, sectionStatuses(record)["setup"])
        assertEquals(SectionStatus.Ready, sectionStatuses(record)["visit"])
    }

    @Test
    fun `invalid answers report needs attention even when something was answered`() {
        val record = FakeRecord(
            household = mapOf("setup" to scalar("enumerator" to "Kim")),
        )
        assertEquals(SectionStatus.NeedsAttention, sectionStatuses(record)["setup"])
    }

    @Test
    fun `member scope counts one complete member`() {
        val record = FakeRecord(
            members = mapOf("m1" to "Ana Reyes"),
            perMember = mapOf("m1" to mapOf("demographics" to scalar(
                "firstName" to "Ana", "lastName" to "Reyes", "registeredVoter" to "Yes", "relationship" to "Head",
                "sex" to "Female", "dob" to "1985-04-12", "birth" to "Yes", "civilStatus" to "Married",
            ))),
        )
        // Some other member section is still empty and has required fields; demographics is done.
        val statuses = sectionStatuses(record)
        assertEquals(SectionStatus.Ready, statuses["demographics"])
    }

    @Test
    fun `no members means every member section reports needs attention`() {
        val record = FakeRecord(
            perMember = mapOf("m1" to mapOf("demographics" to scalar("firstName" to "Ana"))),
        )
        val statuses = sectionStatuses(record)
        assertEquals(SectionStatus.NeedsAttention, statuses["demographics"])
    }

    @Test
    fun `consent refusal stays needs attention like the review gate`() {
        val record = FakeRecord(
            household = mapOf(
                "setup" to scalar("enumerator" to "Kim", "barangay" to "Barangay Cogon", "purok" to "1", "interviewDate" to "2026-02-01"),
                "consent" to scalar("consent" to "No"),
            ),
            members = mapOf("m1" to "Ana Reyes"),
        )
        assertEquals(SectionStatus.NeedsAttention, sectionStatuses(record)["consent"])
    }
}
