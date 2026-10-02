package ph.tala.registry.domain.model

/**
 * One stored answer. Text, number, date, choice and select answers are [Scalar];
 * multi-select answers are [Multi]. An absent key means "not answered yet" — the
 * prototype's distinction between an empty string and a missing key is preserved by
 * treating a blank scalar as unanswered (see [isAnswered]).
 */
sealed interface AnswerValue {
    data class Scalar(val text: String) : AnswerValue

    data class Multi(val selected: List<String>) : AnswerValue

    companion object {
        val Blank: AnswerValue = Scalar("")
    }
}

/** Answers keyed by [FieldSpec.key], held for exactly one [AnswerScope]. */
typealias AnswerState = Map<String, AnswerValue>

/**
 * Where a set of answers belongs. Household sections write to [Household]; the
 * `person: true` sections from the prototype's forms.js write to one [Member] each,
 * so two members never share or overwrite answers.
 */
sealed interface AnswerScope {
    val householdId: String

    data class Household(override val householdId: String) : AnswerScope

    data class Member(
        override val householdId: String,
        val memberId: String,
    ) : AnswerScope
}

fun AnswerState.scalar(key: String): String =
    (this[key] as? AnswerValue.Scalar)?.text.orEmpty()

fun AnswerState.multi(key: String): List<String> =
    (this[key] as? AnswerValue.Multi)?.selected.orEmpty()

fun AnswerState.isAnswered(key: String): Boolean = when (val value = this[key]) {
    null -> false
    is AnswerValue.Scalar -> value.text.isNotBlank()
    is AnswerValue.Multi -> value.selected.isNotEmpty()
}
