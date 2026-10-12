package ph.tala.registry.domain.form

import ph.tala.registry.domain.model.*
import java.time.LocalDate
import java.time.LocalDateTime

/** Pure Kotlin rules: no Compose, Android context, repository or wall-clock dependency in tests. */
object FormRules {
    fun validate(section: SectionSpec, answers: AnswerState, today: LocalDate = LocalDate.now()): Map<String, String> =
        section.fields.mapNotNull { field -> error(field, answers, today)?.let { field.key to it } }.toMap()

    fun error(field: FieldSpec, answers: AnswerState, today: LocalDate): String? {
        if (!field.isVisible(answers)) return null
        val value = answers[field.key]
        if (!answers.isAnswered(field.key)) return if (field.optional) null else "Choose or enter an answer."
        if (field.type == FieldType.MultiSelect) {
            val selected = (value as? AnswerValue.Multi)?.selected ?: return "Choose from the listed answers."
            if (selected.any { it !in field.options } || selected.distinct().size != selected.size) return "Choose from the listed answers."
            if (selected.size > 1 && selected.any { it in field.exclusiveOptions }) return "Choose that answer on its own."
            return null
        }
        val text = (value as? AnswerValue.Scalar)?.text?.trim() ?: return "Enter a single answer."
        return when (field.type) {
            FieldType.Text, FieldType.TextArea -> null
            FieldType.Phone -> if (phonePattern.matches(text)) null else "Use 11 digits, for example 0917-123-4567."
            FieldType.Number -> {
                val number = text.takeIf { wholeNumber.matches(it) }?.toLongOrNull()
                when {
                    number == null -> "Enter a whole number."
                    field.min != null && number < field.min -> "Enter at least ${field.min}."
                    field.max != null && number > field.max -> "Enter no more than ${field.max}."
                    else -> null
                }
            }
            FieldType.Date -> {
                val date = parseDate(text)
                when {
                    date == null -> "Use a valid date: YYYY-MM-DD."
                    field.mustBePast && date > today -> "Date cannot be in the future."
                    else -> null
                }
            }
            FieldType.DateTime -> if (parseDateTime(text) != null) null else "Use a valid date and time: YYYY-MM-DDTHH:MM."
            FieldType.Choice, FieldType.Select -> if ((value as AnswerValue.Scalar).text in field.options) null else "Choose an available answer."
            FieldType.Photo -> "Photo collection is not available in this form yet."
            FieldType.MultiSelect -> error("Handled above")
        }
    }

    fun update(section: SectionSpec, answers: AnswerState, key: String, value: AnswerValue): AnswerState {
        val field = section.fields.single { it.key == key }
        // A late picker callback must not restore an answer whose parent branch was hidden.
        if (!field.isVisible(answers)) return answers
        val updated = (answers + (key to value)).toMutableMap()
        // Fixed point: dependency order in the schema must not affect cascading clearing.
        do {
            val hidden = section.fields.filter { it.key in updated && !it.isVisible(updated) }.map { it.key }
            hidden.forEach(updated::remove)
        } while (hidden.isNotEmpty())
        return updated.toMap()
    }

    fun toggle(field: FieldSpec, selected: List<String>, option: String): AnswerValue.Multi {
        require(field.type == FieldType.MultiSelect && option in field.options)
        return AnswerValue.Multi(when {
            option in selected -> selected - option
            option in field.exclusiveOptions -> listOf(option)
            else -> (selected.filterNot { it in field.exclusiveOptions } + option).distinct()
        })
    }

    /** Do not strip arbitrary characters into an apparently valid phone number. */
    fun formatPhone(text: String): String {
        val trimmed = text.trim()
        if (!phonePattern.matches(trimmed)) return text
        val digits = trimmed.replace("-", "")
        return "${digits.take(4)}-${digits.substring(4, 7)}-${digits.takeLast(4)}"
    }

    fun parseDate(text: String): LocalDate? =
        if (!Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}").matches(text)) null else runCatching { LocalDate.parse(text) }.getOrNull()

    fun parseDateTime(text: String): LocalDateTime? =
        if (!Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}").matches(text)) null else runCatching { LocalDateTime.parse(text) }.getOrNull()

    private val phonePattern = Regex("(?:[0-9]{11}|[0-9]{4}-[0-9]{3}-[0-9]{4})")
    private val wholeNumber = Regex("-?[0-9]+")
}
