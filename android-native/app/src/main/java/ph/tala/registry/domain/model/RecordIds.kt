package ph.tala.registry.domain.model

/** Record identity is distinct from a list position or a display name. */
@JvmInline
value class HouseholdId(val value: String) {
    init { require(value.isNotBlank()) { "Household ID cannot be blank" } }
}

@JvmInline
value class MemberId(val value: String) {
    init { require(value.isNotBlank()) { "Member ID cannot be blank" } }
}
