package ph.tala.registry.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import ph.tala.registry.data.local.AnswerCodec
import ph.tala.registry.data.local.AttachmentRow
import ph.tala.registry.data.local.CoordinateRow
import ph.tala.registry.data.local.HouseholdAnswerRow
import ph.tala.registry.data.local.HouseholdRow
import ph.tala.registry.data.local.InterviewRow
import ph.tala.registry.data.local.MemberAnswerRow
import ph.tala.registry.data.local.MemberRow
import ph.tala.registry.data.local.StoredRecord
import ph.tala.registry.data.local.VisitRow
import ph.tala.registry.domain.form.QuestionnaireSections
import ph.tala.registry.domain.model.AnswerValue

/**
 * Pure-JVM verification of the stored-record → backup-contract mapping: every field
 * conversion the D18 restore will depend on, with no files, Android types or clocks.
 */
class BackupMapperTest {

    private val householdId = "hh-1"
    private val interviewId = "iv-1"

    private fun householdAnswers(vararg pairs: Pair<String, AnswerValue>) =
        pairs.map { (field, value) ->
            val (kind, encoded) = AnswerCodec.encode(value)
            HouseholdAnswerRow(interviewId, "consent", field, kind, encoded)
        }

    private fun record(
        answers: List<HouseholdAnswerRow> = emptyList(),
        archivedAt: Long? = null,
        status: String = "InProgress",
    ) = StoredRecord(
        household = HouseholdRow(householdId, "HH-00452", "Maria Santos", "24 Mabini Street", status, archivedAt),
        members = listOf(MemberRow("m-1", householdId, "Juan Santos"), MemberRow("m-2", householdId, "Pedro Santos")),
        interview = InterviewRow(interviewId, householdId, QuestionnaireSections.VERSION),
        householdAnswers = answers,
    )

    @Test
    fun `full snapshot maps every field the restore rebuilds`() {
        val (consentKind, consentValue) = AnswerCodec.encode(AnswerValue.Scalar("Yes"))
        val (nameKind, nameValue) = AnswerCodec.encode(AnswerValue.Scalar("Maria Santos"))
        val (sexKind, sexValue) = AnswerCodec.encode(AnswerValue.Multi(listOf("Male", "Senior")))
        val bytes = "signature-png-bytes".toByteArray()
        val mapped = BackupMapper.mapRecord(
            record(
                answers = listOf(
                    HouseholdAnswerRow(interviewId, "consent", "consent", consentKind, consentValue),
                    HouseholdAnswerRow(interviewId, "consent", "signedName", nameKind, nameValue),
                ),
                status = "Reviewed",
            ).let { base ->
                base.copy(
                    memberAnswers = listOf(
                        MemberAnswerRow(interviewId, householdId, "m-1", "demographics", "sex", sexKind, sexValue),
                    ),
                    visits = listOf(
                        VisitRow("v-1", interviewId, "Callback", "2026-10-24T09:00", visitedOn = "2026-10-23", notes = "Bring ID", recordedAt = 1_700_000_000_000),
                        VisitRow("v-2", interviewId, "Interview started", visitedOn = "", recordedAt = 0),
                    ),
                    coordinates = CoordinateRow(householdId, 14.5995, 120.9842, 8.5, 1_700_000_001_000),
                    attachments = listOf(AttachmentRow("sig-1", householdId, null, "signature", "attachments/abc.png", "image/png")),
                )
            },
            included = mapOf("sig-1" to BackupMapper.IncludedAttachment(bytes, AttachmentRow("sig-1", householdId, null, "signature", "attachments/abc.png", "image/png"))),
        )

        assertEquals("HH-00452", mapped.displayCode)
        assertEquals("Reviewed", mapped.status)
        assertNull(mapped.archivedAt)
        assertEquals(listOf("Juan Santos", "Pedro Santos"), mapped.members.map { it.displayName })
        assertEquals(listOf(0, 1), mapped.members.map { it.sortOrder })
        assertEquals(QuestionnaireSections.VERSION, mapped.interview.questionnaireVersion)
        assertEquals("Yes", mapped.interview.consent)
        assertEquals("Maria Santos", mapped.interview.signedName)
        assertEquals("sig-1", mapped.interview.signatureAttachmentId)
        // Answers decode to portable scalars/multi — never the store's doubly encoded strings.
        assertEquals(BackupAnswerValue.scalar("Yes"), mapped.interview.answers.first { it.field == "consent" }.answer)
        assertEquals(BackupAnswerValue.scalar("Maria Santos"), mapped.interview.answers.first { it.field == "signedName" }.answer)
        val sex = mapped.interview.answers.first { it.field == "sex" }
        assertEquals(BackupAnswerValue.multi(listOf("Male", "Senior")), sex.answer)
        assertEquals("m-1", sex.memberId)
        assertTrue(mapped.interview.answers.filter { it.section == "consent" }.all { it.memberId == null })
        val firstVisit = mapped.visits[0]
        assertEquals("2026-10-23", firstVisit.visitedOn)
        assertEquals("Callback", firstVisit.outcome)
        assertEquals("2026-10-24T09:00", firstVisit.callbackLocalDateTime)
        assertNull(firstVisit.callbackTimeZone)
        assertEquals("Bring ID", firstVisit.notes)
        assertEquals("2023-11-14T22:13:20Z", firstVisit.recordedAt)
        assertNull(mapped.visits[1].recordedAt)
        assertEquals("", mapped.visits[1].visitedOn)
        assertEquals(14.5995, mapped.location?.latitude!!, 1e-9)
        assertEquals(120.9842, mapped.location?.longitude!!, 1e-9)
        assertEquals(8.5, mapped.location?.accuracyMetres!!, 1e-9)
        assertEquals("device", mapped.location?.source)
        val attachment = mapped.attachments.single()
        assertEquals("sig-1", attachment.id)
        assertEquals(bytes.size, attachment.byteSize)
        assertEquals(BackupMapper.sha256Hex(bytes), attachment.sha256)
        assertEquals(BackupMapper.base64(bytes), attachment.contentBase64)
    }

    @Test
    fun `manual coordinate maps to manual provenance via null accuracy`() {
        val mapped = BackupMapper.mapRecord(
            record().copy(coordinates = CoordinateRow(householdId, 14.6, 120.98, null, 1_700_000_000_000)),
            included = emptyMap(),
        )
        assertEquals("manual", mapped.location?.source)
        assertNull(mapped.location?.accuracyMetres)
    }

    @Test
    fun `archived household keeps its archived timestamp as ISO`() {
        val mapped = BackupMapper.mapRecord(
            record(archivedAt = 1_700_000_000_000),
            included = emptyMap(),
        )
        assertEquals("2023-11-14T22:13:20Z", mapped.archivedAt)
    }

    @Test
    fun `missing consent answers stay unanswered without a fabricated name`() {
        val mapped = BackupMapper.mapRecord(record(), included = emptyMap())
        assertEquals("Unanswered", mapped.interview.consent)
        assertNull(mapped.interview.signedName)
        assertNull(mapped.interview.signatureAttachmentId)
        assertTrue(mapped.attachments.isEmpty())
        assertNull(mapped.location)
    }

    @Test
    fun `blank consent scalar reads as unanswered and blank signed name is null`() {
        val (kind, value) = AnswerCodec.encode(AnswerValue.Scalar(""))
        val mapped = BackupMapper.mapRecord(
            record(answers = listOf(
                HouseholdAnswerRow(interviewId, "consent", "consent", kind, value),
                HouseholdAnswerRow(interviewId, "consent", "signedName", kind, value),
            )),
            included = emptyMap(),
        )
        assertEquals("Unanswered", mapped.interview.consent)
        assertNull(mapped.interview.signedName)
    }

    @Test
    fun `signature without included bytes leaves no dangling id`() {
        val mapped = BackupMapper.mapRecord(
            record().copy(attachments = listOf(AttachmentRow("sig-1", householdId, null, "signature", "attachments/abc.png", "image/png"))),
            included = emptyMap(),
        )
        assertNull(mapped.interview.signatureAttachmentId)
        assertTrue(mapped.attachments.isEmpty())
    }

    @Test
    fun `encoded backup json round-trips to equal values`() {
        val bytes = "png".toByteArray()
        val mapped = BackupMapper.mapRecord(
            record(answers = listOf(
                HouseholdAnswerRow(interviewId, "consent", "consent", "scalar", "\"Yes\""),
            )).copy(attachments = listOf(AttachmentRow("sig-1", householdId, null, "signature", "attachments/a.png", "image/png"))),
            included = mapOf("sig-1" to BackupMapper.IncludedAttachment(bytes, AttachmentRow("sig-1", householdId, null, "signature", "attachments/a.png", "image/png"))),
        )
        val file = BackupFile(exportedAt = "2026-10-23T00:00:00Z", records = listOf(mapped))
        val json = BackupJson.encodeToString(BackupFile.serializer(), file)
        val reparsed = kotlinx.serialization.json.Json.decodeFromString(BackupFile.serializer(), json)
        assertEquals(file, reparsed)
        // Version keys are present in the emitted document, and pretty print keeps it multiline.
        val root = kotlinx.serialization.json.Json.parseToJsonElement(json).jsonObject
        assertEquals(1, root["formatVersion"]!!.jsonPrimitive.content.toInt())
        assertEquals("tala-backup", root["format"]!!.jsonPrimitive.content)
        val member = root["records"]!!.jsonArray.single().jsonObject["members"]!!.jsonArray.first().jsonObject
        assertEquals("Juan Santos", member["displayName"]!!.jsonPrimitive.content)
        assertNull(member["displayCode"])
        assertTrue(json.lines().size > 1)
    }
}
