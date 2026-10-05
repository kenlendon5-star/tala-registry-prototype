package ph.tala.registry.domain.model

/**
 * A registered household. [id] is the stable, human-readable record ID the prototype
 * already uses (for example `HH-00452`), so exported records stay recognisable.
 */
data class Household(
    val id: HouseholdId,
    val headName: String,
    val address: String,
    val status: HouseholdStatus,
    val members: List<Member> = emptyList(),
    val answers: AnswerState = emptyMap(),
)

/** One household member. Answers live here, not on the household, for member-scoped sections. */
data class Member(
    val id: MemberId,
    val householdId: HouseholdId,
    val displayName: String,
    val answers: AnswerState = emptyMap(),
)

enum class HouseholdStatus(val label: String) {
    NotStarted("Not started"),
    InProgress("In progress"),
    Callback("Callback"),
    Reviewed("Demo reviewed"),
    ;

    companion object {
        fun fromLabel(label: String): HouseholdStatus =
            entries.firstOrNull { it.label == label } ?: NotStarted
    }
}

/** The enumerator whose name and barangay are stamped on new records. */
data class EnumeratorProfile(
    val name: String,
    val barangay: String,
)
