package ph.tala.registry.ui.interview

import ph.tala.registry.domain.form.InterviewRules
import ph.tala.registry.domain.form.QuestionnaireSections
import ph.tala.registry.domain.model.InterviewData

/** One state per questionnaire section row (chips, directory and wide-layout index). */
enum class SectionStatus {
    /** Answered with no open issues — the review progress rule, not a completion claim. */
    Ready,
    /** At least one scope reports missing or invalid answers. */
    NeedsAttention,
    /** Nothing answered yet and nothing invalid. */
    NotStarted,
}

/**
 * Same readiness rule as [InterviewRules.progress], per section instead of as a count.
 * The `members` route is managed through the member editors, never as a form section.
 */
fun sectionStatuses(record: InterviewData?): Map<String, SectionStatus> {
    if (record == null) return emptyMap()
    val open = InterviewRules.issues(record).mapTo(mutableSetOf()) { it.route }
    return buildMap {
        for (section in QuestionnaireSections.all) {
            if (section.route == "members") continue
            val answered = if (section.scopedToMember) {
                record.memberNames.keys.any { record.sectionAnswers(section.route, it).isNotEmpty() }
            } else {
                record.sectionAnswers(section.route).isNotEmpty()
            }
            this[section.route] = when {
                // A member section with no members cannot be answered and never counts as ready.
                section.scopedToMember && record.memberNames.isEmpty() -> SectionStatus.NeedsAttention
                !answered -> SectionStatus.NotStarted
                section.route in open -> SectionStatus.NeedsAttention
                else -> SectionStatus.Ready
            }
        }
    }
}
