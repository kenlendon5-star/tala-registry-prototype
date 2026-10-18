package ph.tala.registry.domain.edit

import ph.tala.registry.domain.model.HouseholdStatus
import ph.tala.registry.domain.model.RecordTextLimits

/**
 * What the editing screens submit. Drafts carry trimmed user input only; record
 * identity, display codes, answers and timestamps stay with the repository.
 */
data class HouseholdDraft(
    val headName: String,
    val address: String,
    val status: HouseholdStatus,
)

data class MemberDraft(val displayName: String)

/**
 * Pure validation for the household and member edit screens. Limits match the
 * prototype forms (`newHouse`/`newMember`: head 80, address 150, member name 80), so a
 * record saved here stays exportable without truncation later.
 */
object EditRules {
    const val HEAD_MAX = RecordTextLimits.NAME
    const val ADDRESS_MAX = RecordTextLimits.ADDRESS
    const val NAME_MAX = RecordTextLimits.NAME

    fun headName(value: String): String? = when {
        value.isBlank() -> "Enter the household head's name."
        value.trim().length > HEAD_MAX -> "Use $HEAD_MAX characters or fewer."
        else -> null
    }

    fun address(value: String): String? = when {
        value.isBlank() -> "Enter the street, purok or landmark."
        value.trim().length > ADDRESS_MAX -> "Use $ADDRESS_MAX characters or fewer."
        else -> null
    }

    fun displayName(value: String): String? = when {
        value.isBlank() -> "Enter the member's full name."
        value.trim().length > NAME_MAX -> "Use $NAME_MAX characters or fewer."
        else -> null
    }

    /** The reason a household draft cannot be written, or null when it is ready. */
    fun householdProblem(draft: HouseholdDraft, allowReviewed: Boolean = false): String? =
        headName(draft.headName) ?: address(draft.address)
            ?: if (draft.status == HouseholdStatus.Reviewed && !allowReviewed) "Complete the interview review before marking this household reviewed." else null

    /** The reason a member draft cannot be written, or null when it is ready. */
    fun memberProblem(draft: MemberDraft): String? = displayName(draft.displayName)
}
