package ph.tala.registry.data.local

/**
 * Deterministic fictional records for the Day 5 storage evidence. Fixed UUIDs keep
 * assertions readable across separate process runs; they are not production identities
 * and the records are not shown in the app UI.
 */
object SampleRecords {
    /** Used by RecordStoreDeviceTest, which deletes it before and after every case. */
    const val STORE_DB_NAME = "tala-registry-store-test.db"
    /** Used by DurableSaveTest, which must keep the file across force-stop and reboot. */
    const val DB_NAME = "tala-registry-evidence.db"
    const val HOUSEHOLD = "a1b2c3d4-0000-4a00-8000-000000000001"
    const val MEMBER_ONE = "a1b2c3d4-0000-4a00-8000-000000000002"
    const val MEMBER_TWO = "a1b2c3d4-0000-4a00-8000-000000000003"
    const val INTERVIEW = "a1b2c3d4-0000-4a00-8000-000000000004"
    const val VISIT = "a1b2c3d4-0000-4a00-8000-000000000005"
    const val ATTACHMENT = "a1b2c3d4-0000-4a00-8000-000000000006"

    /** One complete household snapshot: two members, scoped answers, a visit, a coordinate and an attachment reference. */
    fun record(): StoredRecord = StoredRecord(
        household = HouseholdRow(HOUSEHOLD, "HH-00901", "Maria Santos", "24 Mabini Street · Purok 2", "InProgress"),
        members = listOf(
            MemberRow(MEMBER_ONE, HOUSEHOLD, "Maria Santos"),
            MemberRow(MEMBER_TWO, HOUSEHOLD, "Juan Santos"),
        ),
        interview = InterviewRow(INTERVIEW, HOUSEHOLD),
        visits = listOf(VisitRow(VISIT, INTERVIEW, "Callback requested", "2026-10-20T09:30")),
        householdAnswers = listOf(
            HouseholdAnswerRow(INTERVIEW, "practice-household", "phone", "scalar", "\"09171234567\""),
            HouseholdAnswerRow(INTERVIEW, "practice-household", "visitNotes", "scalar", "\"First line\\nSecond \\\"quoted\\\" line\""),
        ),
        memberAnswers = listOf(
            MemberAnswerRow(INTERVIEW, HOUSEHOLD, MEMBER_ONE, "practice-member", "dob", "scalar", "\"2000-02-29\""),
            MemberAnswerRow(INTERVIEW, HOUSEHOLD, MEMBER_ONE, "practice-member", "firstName", "scalar", "\"Maria\""),
            MemberAnswerRow(INTERVIEW, HOUSEHOLD, MEMBER_TWO, "practice-member", "accounts", "multi", "[\"Bank\",\"E-money\"]"),
            MemberAnswerRow(INTERVIEW, HOUSEHOLD, MEMBER_TWO, "practice-member", "firstName", "scalar", "\"Juan\""),
        ),
        coordinates = CoordinateRow(HOUSEHOLD, 14.5995, 120.9842, 8.5, 1_760_000_000_000),
        attachments = listOf(
            AttachmentRow(ATTACHMENT, HOUSEHOLD, null, "household-photo", "attachments/$ATTACHMENT.jpg", "image/jpeg"),
        ),
    )

    /** A second, smaller household used to check list ordering and record isolation. */
    fun otherRecord(): StoredRecord = StoredRecord(
        household = HouseholdRow("a1b2c3d4-0000-4a00-8000-000000000011", "HH-00899", "Ramon Reyes", "8 Rizal Street · Purok 2", "Callback"),
        members = listOf(MemberRow("a1b2c3d4-0000-4a00-8000-000000000012", "a1b2c3d4-0000-4a00-8000-000000000011", "Ramon Reyes")),
        interview = InterviewRow("a1b2c3d4-0000-4a00-8000-000000000013", "a1b2c3d4-0000-4a00-8000-000000000011"),
    )
}
