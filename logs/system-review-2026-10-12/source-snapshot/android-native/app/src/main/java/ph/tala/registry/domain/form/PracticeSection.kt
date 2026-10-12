package ph.tala.registry.domain.form

import ph.tala.registry.domain.model.*

/** Representative fields from forms.js, combined for Day 4 acceptance, not an official survey section. */
object PracticeSection {
    private val fields = listOf(
        FieldSpec("firstName", "First name", FieldType.Text),
        FieldSpec("phone", "Contact number (optional)", FieldType.Phone, optional = true, hint = "0917-123-4567"),
        FieldSpec("dob", "Date of birth", FieldType.Date, mustBePast = true),
        FieldSpec("callbackDate", "Callback appointment", FieldType.DateTime),
        FieldSpec("worked", "Worked for pay or profit in the past 7 days?", FieldType.Choice, listOf(YES, NO)),
        FieldSpec("occupation", "Occupation / job title", FieldType.Text, visibleWhen = Condition.yes("worked")),
        FieldSpec("employmentType", "Employment type", FieldType.Select,
            listOf("Employee", "Self-employed", "Employer", "Paid family worker"), visibleWhen = Condition.yes("worked")),
        FieldSpec("hours", "Hours worked in the past 7 days", FieldType.Number,
            min = 0, max = 168, visibleWhen = Condition.yes("worked")),
        FieldSpec("accounts", "Financial accounts held", FieldType.MultiSelect,
            listOf("Bank", "Digital bank", "E-money", "Cooperative", "None", "Prefer not to answer"),
            exclusiveOptions = setOf("None", "Prefer not to answer")),
        FieldSpec("visitNotes", "Visit notes (optional)", FieldType.TextArea, optional = true),
    )

    val household = SectionSpec("practice-household", "Practice household interview", fields)
    val member = SectionSpec("practice-member", "Practice member interview", fields, scopedToMember = true)
}
