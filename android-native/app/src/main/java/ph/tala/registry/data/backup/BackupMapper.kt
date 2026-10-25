package ph.tala.registry.data.backup

import ph.tala.registry.data.local.AnswerCodec
import ph.tala.registry.data.local.AttachmentRow
import ph.tala.registry.data.local.HouseholdAnswerRow
import ph.tala.registry.data.local.MemberAnswerRow
import ph.tala.registry.data.local.StoredRecord
import ph.tala.registry.domain.model.AnswerValue
import java.security.MessageDigest
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Base64

/**
 * Pure mapping from a stored record snapshot to the backup contract. No Android types:
 * attachment bytes arrive through [IncludedAttachment], so the JVM can test every field
 * conversion without files or bitmaps.
 */
object BackupMapper {

    /** One attachment whose file bytes were read successfully and pass the size bounds. */
    data class IncludedAttachment(val bytes: ByteArray, val row: AttachmentRow)

    /** Epoch millis → ISO-8601 UTC timestamp (`Z` suffix, as the contract requires). */
    fun isoTimestamp(epochMillis: Long): String =
        DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(epochMillis))

    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    fun base64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    fun mapRecord(record: StoredRecord, included: Map<String, IncludedAttachment>): BackupRecord {
        // The signature link only points at an attachment that actually travels in this file.
        val signatureAttachmentId = record.attachments
            .find { it.kind == SIGNATURE_KIND }
            ?.let { included[it.id]?.row?.id }
        return BackupRecord(
            id = record.household.id,
            displayCode = record.household.displayCode,
            headName = record.household.headName,
            address = record.household.address,
            status = record.household.status,
            archivedAt = record.household.archivedAt?.let(::isoTimestamp),
            members = record.members.mapIndexed { order, member ->
                BackupMember(id = member.id, displayName = member.displayName, sortOrder = order)
            },
            interview = BackupInterview(
                id = record.interview.id,
                questionnaireVersion = record.interview.questionnaireVersion,
                consent = record.consentAnswer()?.let { it.ifBlank { null } } ?: CONSENT_UNANSWERED,
                signedName = record.consentAnswer(SIGNED_NAME_FIELD)?.takeIf { it.isNotBlank() },
                signatureAttachmentId = signatureAttachmentId,
                answers = record.householdAnswers.map { it.toBackup(memberId = null) } +
                    record.memberAnswers.map { it.toBackup(memberId = it.memberId) },
            ),
            visits = record.visits.map { visit ->
                BackupVisit(
                    id = visit.id,
                    visitedOn = visit.visitedOn,
                    outcome = visit.outcome,
                    callbackLocalDateTime = visit.callbackAt,
                    // No zone column exists in the store; the contract keeps the field nullable.
                    callbackTimeZone = null,
                    notes = visit.notes,
                    recordedAt = visit.recordedAt.takeIf { it > 0 }?.let(::isoTimestamp),
                )
            },
            location = record.coordinates?.let { coordinate ->
                BackupLocation(
                    latitude = coordinate.latitude,
                    longitude = coordinate.longitude,
                    accuracyMetres = coordinate.accuracyMetres,
                    // Provenance is recoverable without a column: null accuracy is manual.
                    source = if (coordinate.accuracyMetres == null) "manual" else "device",
                    capturedAt = isoTimestamp(coordinate.capturedAt),
                )
            },
            attachments = included.values
                .filter { it.row.householdId == record.household.id }
                .map { it.row.toBackup(it.bytes) },
        )
    }

    /** The consent-section scalar answer for [field], or null when the question was never answered. */
    private fun StoredRecord.consentAnswer(field: String = CONSENT_FIELD): String? =
        householdAnswers.find { it.section == CONSENT_SECTION && it.field == field }
            ?.let { (AnswerCodec.decode(it.kind, it.value) as? AnswerValue.Scalar)?.text }

    private fun HouseholdAnswerRow.toBackup(memberId: String?) = BackupAnswer(
        section = section,
        field = field,
        memberId = memberId,
        answer = decodeAnswer(kind, value),
    )

    private fun MemberAnswerRow.toBackup(memberId: String?) = BackupAnswer(
        section = section,
        field = field,
        memberId = memberId,
        answer = decodeAnswer(kind, value),
    )

    private fun decodeAnswer(kind: String, value: String): BackupAnswerValue = when (val decoded = AnswerCodec.decode(kind, value)) {
        is AnswerValue.Scalar -> BackupAnswerValue.scalar(decoded.text)
        is AnswerValue.Multi -> BackupAnswerValue.multi(decoded.selected)
    }

    private fun AttachmentRow.toBackup(bytes: ByteArray) = BackupAttachment(
        id = id,
        memberId = memberId,
        kind = kind,
        mimeType = mimeType,
        byteSize = bytes.size,
        sha256 = sha256Hex(bytes),
        contentBase64 = base64(bytes),
    )

    private const val CONSENT_SECTION = "consent"
    private const val CONSENT_FIELD = "consent"
    private const val SIGNED_NAME_FIELD = "signedName"
    private const val SIGNATURE_KIND = "signature"
    private const val CONSENT_UNANSWERED = "Unanswered"
}
