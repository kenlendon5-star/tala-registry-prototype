package ph.tala.registry.data

import ph.tala.registry.data.local.HouseholdRow
import ph.tala.registry.data.local.InterviewRow
import ph.tala.registry.data.local.MemberRow
import ph.tala.registry.data.local.StoredRecord

/**
 * Explicit, optional fictional fixtures. Identities are fixed UUIDs because the record
 * store requires canonical UUID keys; the readable `HH-00452`-style labels live in
 * [HouseholdRow.displayCode], exactly like user-created records. MainActivity seeds
 * them once per installed database, and seeding is skipped for any record that already
 * exists, so a seeded household can be edited or archived without being recreated.
 */
object DemoFixtures {
    const val SANTOS = "1a5d0000-0000-4000-8000-000000000452"
    const val REYES = "1a5d0000-0000-4000-8000-000000000453"
    const val CRUZ = "1a5d0000-0000-4000-8000-000000000454"

    const val MARIA = "1a5d0000-0000-4000-8000-000000001001"
    const val JUAN = "1a5d0000-0000-4000-8000-000000001002"
    const val ANA = "1a5d0000-0000-4000-8000-000000001003"
    const val PEDRO = "1a5d0000-0000-4000-8000-000000001004"

    fun records(): List<StoredRecord> = listOf(
        StoredRecord(
            household = HouseholdRow(SANTOS, "HH-00452", "Maria Santos", "24 Mabini Street · Purok 2", "InProgress"),
            members = listOf(
                MemberRow(MARIA, SANTOS, "Maria Santos"),
                MemberRow(JUAN, SANTOS, "Juan Santos"),
                MemberRow(ANA, SANTOS, "Ana Santos"),
                MemberRow(PEDRO, SANTOS, "Pedro Santos"),
            ),
            interview = InterviewRow("1a5d0000-0000-4000-8000-000000009452", SANTOS),
        ),
        StoredRecord(
            household = HouseholdRow(REYES, "HH-00453", "Ramon Reyes", "8 Rizal Street · Purok 2", "Callback"),
            members = emptyList(),
            interview = InterviewRow("1a5d0000-0000-4000-8000-000000009453", REYES),
        ),
        StoredRecord(
            household = HouseholdRow(CRUZ, "HH-00454", "Elena Cruz", "16 Bonifacio Street · Purok 3", "NotStarted"),
            members = emptyList(),
            interview = InterviewRow("1a5d0000-0000-4000-8000-000000009454", CRUZ),
        ),
    )
}
