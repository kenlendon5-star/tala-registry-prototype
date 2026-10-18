package ph.tala.registry.domain.model

/** Shared by generated questionnaire constraints and the standalone record editors. */
object RecordTextLimits {
    const val NAME = 80
    const val ADDRESS = 150

    fun structuredName(answers: AnswerState): String =
        listOf("firstName", "middleName", "lastName", "extension")
            .map { answers.scalar(it).trim() }.filter { it.isNotBlank() }.joinToString(" ")
}
