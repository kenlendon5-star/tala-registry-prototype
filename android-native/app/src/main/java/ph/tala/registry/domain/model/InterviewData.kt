package ph.tala.registry.domain.model

/** Read-only domain contract; validation does not know Room rows or JSON encoding. */
interface InterviewData {
    val memberNames: Map<String, String>
    val hasDrawnSignature: Boolean
    fun sectionAnswers(route: String, memberId: String? = null): AnswerState
}

object AttachmentKinds {
    const val SIGNATURE = "signature"
}
