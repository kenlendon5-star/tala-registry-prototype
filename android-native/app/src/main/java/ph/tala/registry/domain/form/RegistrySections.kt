package ph.tala.registry.domain.form

import ph.tala.registry.domain.model.*

/**
 * The registration slice of the questionnaire (plan card D08): survey setup, visit
 * information and the household profile, ported from `forms.js` through
 * `docs/schemas/questionnaire-field-dictionary.md`. Field keys match the prototype so
 * stored answers and a future export stay compatible. The household photo renders as
 * an explicit "later build" placeholder until the photo card lands; member-scoped
 * sections (5–13) and the bespoke roster/consent workflows arrive with their own cards.
 */
object RegistrySections {
    val setup = SectionSpec(
        "setup", "Survey setup",
        listOf(
            FieldSpec("enumerator", "Enumerator name", FieldType.Text),
            FieldSpec("barangay", "Barangay", FieldType.Select, listOf("Barangay Cogon")),
            FieldSpec("purok", "Purok / zone", FieldType.Text),
            FieldSpec("interviewDate", "Interview date", FieldType.Date),
        ),
    )

    val visit = SectionSpec(
        "visit", "Visit information",
        listOf(
            FieldSpec("visitDate", "Visit date", FieldType.Date),
            FieldSpec("visitResult", "Visit result", FieldType.Select,
                listOf("Interview started", "Completed visit", "Callback", "No respondent", "Refused")),
            FieldSpec("callbackDate", "Callback appointment", FieldType.DateTime,
                visibleWhen = Condition.Equals("visitResult", "Callback")),
            FieldSpec("visitNotes", "Visit notes (optional)", FieldType.TextArea, optional = true),
        ),
    )

    val household = SectionSpec(
        "household", "Household profile",
        listOf(
            FieldSpec("head", "Household head (optional)", FieldType.Text, optional = true),
            FieldSpec("firstName", "First name", FieldType.Text),
            FieldSpec("middleName", "Middle name (optional)", FieldType.Text, optional = true),
            FieldSpec("lastName", "Last name", FieldType.Text),
            FieldSpec("extension", "Extension (optional)", FieldType.Select,
                listOf("Jr.", "Sr.", "II", "III", "IV"), optional = true),
            FieldSpec("alias", "Nickname / alias (optional)", FieldType.Text, optional = true),
            FieldSpec("address", "Address / purok", FieldType.Text),
            FieldSpec("respondent", "Respondent name", FieldType.Text),
            FieldSpec("phone", "Contact number (optional)", FieldType.Phone, optional = true, hint = "0917-123-4567"),
            FieldSpec("previousCity", "Previous city (optional)", FieldType.Text, optional = true),
            FieldSpec("photo", "Household profile picture (optional)", FieldType.Photo, optional = true),
            FieldSpec("houseSafe", "Is your house safe?", FieldType.Choice, listOf(YES, NO)),
            FieldSpec("safetyReason", "Reason (optional)", FieldType.TextArea, optional = true,
                visibleWhen = Condition.Answered("houseSafe")),
        ),
    )

    /** Sections implemented natively so far, in interview order. */
    val registration = listOf(setup, visit, household)
}