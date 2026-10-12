package ph.tala.registry.domain.model

/** The input types used by the 18 questionnaire sections. */
enum class FieldType {
    Text,
    TextArea,
    Phone,
    Number,
    Date,
    DateTime,
    Choice,
    Select,
    MultiSelect,
    Photo,
}

/**
 * Declarative replacement for the `when` lambdas in the prototype's forms.js.
 * Keeping visibility as data (not a lambda) makes it testable and comparable,
 * which a closure over mutable answer state would not be.
 */
sealed interface Condition {
    data class Equals(val key: String, val value: String) : Condition

    data class OneOf(val key: String, val values: List<String>) : Condition

    data class Answered(val key: String) : Condition

    data class All(val conditions: List<Condition>) : Condition

    fun matches(answers: AnswerState): Boolean = when (this) {
        is Equals -> answers.scalar(key) == value
        is OneOf -> answers.scalar(key) in values
        is Answered -> answers.isAnswered(key)
        is All -> conditions.all { it.matches(answers) }
    }

    companion object {
        fun yes(key: String): Condition = Equals(key, YES)

        fun no(key: String): Condition = Equals(key, NO)
    }
}

const val YES = "Yes"
const val NO = "No"

/**
 * One questionnaire field. Mirrors the `field(...)` helper in forms.js, including the
 * optional/min/max/past constraints that drive inline validation.
 */
data class FieldSpec(
    val key: String,
    val label: String,
    val type: FieldType,
    val options: List<String> = emptyList(),
    val optional: Boolean = false,
    val hint: String? = null,
    val min: Int? = null,
    val max: Int? = null,
    val mustBePast: Boolean = false,
    val visibleWhen: Condition? = null,
    /** A choice such as None cannot coexist with another selection. */
    val exclusiveOptions: Set<String> = emptySet(),
) {
    init {
        require(key.isNotBlank())
        require(min == null || max == null || min <= max)
        require(options.distinct().size == options.size)
        require(exclusiveOptions.all { it in options })
        require(exclusiveOptions.isEmpty() || type == FieldType.MultiSelect)
    }
    val isChoiceLike: Boolean
        get() = type == FieldType.Choice || type == FieldType.Select || type == FieldType.MultiSelect

    fun isVisible(answers: AnswerState): Boolean = visibleWhen?.matches(answers) ?: true
}

/**
 * One questionnaire section. [scopedToMember] is the prototype's `person: true`:
 * the section is answered once per household member rather than once per household.
 */
data class SectionSpec(
    val route: String,
    val title: String,
    val fields: List<FieldSpec>,
    val scopedToMember: Boolean = false,
) {
    init { require(fields.map { it.key }.distinct().size == fields.size) { "Section field keys must be unique" } }
    /** Fields the respondent can currently see, given what has been answered so far. */
    fun visibleFields(answers: AnswerState): List<FieldSpec> = fields.filter { it.isVisible(answers) }
}
