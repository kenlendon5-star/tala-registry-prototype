package ph.tala.registry.testing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import ph.tala.registry.data.HouseholdRepository
import ph.tala.registry.data.local.WriteResult
import ph.tala.registry.domain.edit.*
import ph.tala.registry.domain.form.*
import ph.tala.registry.domain.model.*

/** Explicit fictional answers: validation changes must be reviewed instead of auto-filled. */
object CompleteInterviewFixture {
    private fun answers(vararg pairs: Pair<String, String>): AnswerState = pairs.associate { it.first to AnswerValue.Scalar(it.second) }
    val sections = mapOf(
        "setup" to answers("enumerator" to "Demo Enumerator", "barangay" to "Barangay Cogon", "purok" to "Zone Sample", "interviewDate" to "2026-10-18"),
        "visit" to answers("visitDate" to "2026-10-18", "visitResult" to "Completed visit"),
        "household" to answers("firstName" to "Lifecycle", "lastName" to "Acceptance", "address" to "Fictional Lifecycle Street", "respondent" to "Maria Sample", "houseSafe" to "Yes"),
        "demographics" to answers("firstName" to "Maria", "lastName" to "Sample", "registeredVoter" to "Yes", "relationship" to "Head", "sex" to "Female", "dob" to "2000-01-01", "birth" to "Yes", "civilStatus" to "Single"),
        "other" to answers("nationalId" to "No", "soloParent" to "No", "disability" to "No", "seniorId" to "Not applicable"),
        "migration" to answers("citizenship" to "Filipino", "moved" to "No", "overseas" to "No"),
        "education" to answers("attending" to "No", "reason" to "Completed schooling", "grade" to "College graduate", "educationAssistance" to "No"),
        "employment" to answers("worked" to "Yes", "occupation" to "Teacher", "industry" to "Education", "employmentType" to "Employee", "hours" to "40"),
        "health" to answers("illness" to "No", "healthAccess" to "Yes"),
        "food" to answers("foodWorry" to "Never", "skippedMeals" to "No", "foodAssistance" to "No"),
        "transport" to (answers("transport" to "Walking") + ("accounts" to AnswerValue.Multi(listOf("None")))),
        "disaster" to (answers("disasterType" to "Natural", "evacuationKnown" to "No", "emergencyKit" to "Yes") + ("hazards" to AnswerValue.Multi(listOf("None")))),
        "internet" to answers("internet" to "No", "safety" to "Very safe"),
        "protection" to (answers("philHealth" to "No", "dswd" to "No", "fourPs" to "No", "assistance" to "No") + ("programs" to AnswerValue.Multi(listOf("None")))),
        "water" to (answers("water" to "Piped into dwelling", "toilet" to "Flush / pour flush", "handwashing" to "Yes") + ("waste" to AnswerValue.Multi(listOf("Collection service")))),
        "housing" to answers("roof" to "Metal roofing sheets", "walls" to "Concrete / brick", "tenure" to "Owned", "electricity" to "Yes", "rooms" to "2"),
        "consent" to answers("consent" to "Yes", "signedName" to "Maria Sample"),
    )

    suspend fun seed(repository: HouseholdRepository, id: HouseholdId, member: MemberId, includeConsent: Boolean = true) {
        assertEquals(WriteResult.Saved, repository.createHousehold(id, HouseholdDraft("Lifecycle Acceptance", "Fictional Lifecycle Street", HouseholdStatus.InProgress)))
        assertEquals(WriteResult.Saved, repository.saveMember(id, member, MemberDraft("Maria Sample")))
        for (section in QuestionnaireSections.forms) {
            val values = if (section.route == "consent" && !includeConsent) emptyMap() else sections.getValue(section.route)
            if (values.isNotEmpty()) assertTrue("${section.route}: ${FormRules.validate(section, values)}", FormRules.validate(section, values).isEmpty())
            val scope = member.takeIf { section.scopedToMember }
            val before = InterviewRules.answers(repository.interviewRecord(id)!!, section, scope?.value)
            assertEquals(WriteResult.Saved, repository.saveDraftSection(id, section.route, scope, values, before))
        }
    }
}
