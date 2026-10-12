package ph.tala.registry.domain.form

import ph.tala.registry.data.local.AnswerCodec
import ph.tala.registry.data.local.StoredRecord
import ph.tala.registry.domain.model.*
import java.time.LocalDate

data class SectionIssue(val route: String, val memberId: String?, val label: String, val errors: Map<String, String>)

object InterviewRules {
    fun answers(record: StoredRecord, section: SectionSpec, memberId: String? = null): AnswerState =
        if (section.scopedToMember) record.memberAnswers.filter { it.section == section.route && it.memberId == memberId }
            .associate { it.field to AnswerCodec.decode(it.kind, it.value) }
        else record.householdAnswers.filter { it.section == section.route }
            .associate { it.field to AnswerCodec.decode(it.kind, it.value) }

    fun clean(section: SectionSpec, answers: AnswerState): AnswerState {
        val result = answers.filterKeys { key -> section.fields.any { it.key == key } }.toMutableMap()
        do {
            val hidden = section.fields.filter { it.key in result && !it.isVisible(result) }
            hidden.forEach { result.remove(it.key) }
        } while (hidden.isNotEmpty())
        return result.toMap()
    }

    fun issues(record: StoredRecord, today: LocalDate = LocalDate.now()): List<SectionIssue> = buildList {
        if (record.members.isEmpty()) add(SectionIssue("members", null, "Household members", mapOf("members" to "Add at least one member.")))
        QuestionnaireSections.forms.forEach { section ->
            val scopes: List<String?> = if (section.scopedToMember) record.members.map { it.id } else listOf(null)
            scopes.forEach { member ->
                val answers = answers(record, section, member)
                val errors = FormRules.validate(section, answers, today).toMutableMap()
                if (section.route == "consent" && answers.scalar("consent") != YES) errors["consent"] = "Consent is required for Reviewed. Refusal remains saved as a draft."
                if (section.route == "protection") {
                    val programs = answers.multi("programs")
                    if ((answers.scalar("philHealth") == NO && "PhilHealth" in programs) ||
                        (answers.scalar("fourPs") == NO && "4Ps" in programs)) errors["programs"] = "Program coverage contradicts the answers above."
                }
                if (errors.isNotEmpty()) add(SectionIssue(section.route, member,
                    section.title + (member?.let { id -> " · " + record.members.first { it.id == id }.displayName } ?: ""), errors))
            }
        }
    }
}
