package ph.tala.registry.domain.form

import ph.tala.registry.domain.model.*
import java.time.LocalDate

data class SectionIssue(val route: String, val memberId: String?, val label: String, val errors: Map<String, String>)

/** Interview progress for review screens: which of the 18 sections are ready. A drawn or
 * typed signature completes the consent section; a refusal never blocks progress, only review. */
data class InterviewProgress(val ready: Int, val total: Int) {
    val allReady: Boolean get() = ready == total
}

object InterviewRules {
    fun answers(record: InterviewData, section: SectionSpec, memberId: String? = null): AnswerState =
        if (section.scopedToMember && memberId == null) emptyMap()
        else record.sectionAnswers(section.route, memberId.takeIf { section.scopedToMember })

    fun clean(section: SectionSpec, answers: AnswerState): AnswerState {
        val result = answers.filterKeys { key -> section.fields.any { it.key == key } }.toMutableMap()
        do {
            val hidden = section.fields.filter { it.key in result && !it.isVisible(result) }
            hidden.forEach { result.remove(it.key) }
        } while (hidden.isNotEmpty())
        return result.toMap()
    }

    /** Local review progress: a section is ready when no scope of it reports an issue. */
    fun progress(record: InterviewData, today: LocalDate = LocalDate.now()): InterviewProgress {
        val open = issues(record, today).map { it.route }.toSet()
        return InterviewProgress(
            ready = QuestionnaireSections.all.count { section ->
                section.route !in open &&
                    (!(section.scopedToMember || section.route == "members") || record.memberNames.isNotEmpty())
            },
            total = QuestionnaireSections.all.size,
        )
    }

    fun issues(record: InterviewData, today: LocalDate = LocalDate.now()): List<SectionIssue> = buildList {
        if (record.memberNames.isEmpty()) add(SectionIssue("members", null, "Household members", mapOf("members" to "Add at least one member.")))
        QuestionnaireSections.forms.forEach { section ->
            val scopes: List<String?> = if (section.scopedToMember) record.memberNames.keys.toList() else listOf(null)
            scopes.forEach { member ->
                val answers = answers(record, section, member)
                val errors = FormRules.validate(section, answers, today).toMutableMap()
                if (section.route == "consent" && answers.scalar("consent") != YES) errors["consent"] = "Consent is required for Reviewed. Refusal remains saved as a draft."
                if (section.route == "consent" && answers.scalar("consent") == YES && answers.scalar("signedName").isBlank()) {
                    if (record.hasDrawnSignature)
                        errors.remove("signedName") // a drawn mark completes the step without a typed name
                    else
                        errors["signedName"] = "Draw a signature in the pad or type a fictional name."
                }
                if (section.route == "protection") {
                    val programs = answers.multi("programs")
                    if ((answers.scalar("philHealth") == NO && "PhilHealth" in programs) ||
                        (answers.scalar("fourPs") == NO && "4Ps" in programs)) errors["programs"] = "Program coverage contradicts the answers above."
                }
                if (errors.isNotEmpty()) add(SectionIssue(section.route, member,
                    section.title + (member?.let { id -> " · " + record.memberNames.getValue(id) } ?: ""), errors))
            }
        }
    }
}
