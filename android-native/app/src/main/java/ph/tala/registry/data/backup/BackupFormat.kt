package ph.tala.registry.data.backup

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * DTOs for the offline backup contract (`planning/docs/schemas/backup-v1.schema.json`).
 * The classes mirror the schema exactly — fields the record store does not persist are
 * absent — so a generated file validates against the documented contract and a D18
 * restore can rebuild a complete [ph.tala.registry.data.local.StoredRecord] graph.
 */
@Serializable
data class BackupFile(
    val format: String = "tala-backup",
    @SerialName("formatVersion") val formatVersion: Int = 1,
    val exportedAt: String,
    val prototype: Boolean = true,
    val records: List<BackupRecord>,
)

@Serializable
data class BackupRecord(
    val id: String,
    @SerialName("displayCode") val displayCode: String,
    val headName: String,
    val address: String,
    val status: String,
    val archivedAt: String?,
    val members: List<BackupMember>,
    val interview: BackupInterview,
    val visits: List<BackupVisit>,
    val location: BackupLocation?,
    val attachments: List<BackupAttachment>,
)

@Serializable
data class BackupMember(
    val id: String,
    @SerialName("displayName") val displayName: String,
    @SerialName("sortOrder") val sortOrder: Int,
)

@Serializable
data class BackupInterview(
    val id: String,
    @SerialName("questionnaireVersion") val questionnaireVersion: String,
    /** Unanswered/Yes/No; derived from the consent-section answer, not stored natively. */
    val consent: String,
    val signedName: String?,
    /** Only set when the signature file itself is included below. */
    @SerialName("signatureAttachmentId") val signatureAttachmentId: String?,
    val answers: List<BackupAnswer>,
)

@Serializable
data class BackupAnswer(
    val section: String,
    val field: String,
    /** null for household-scoped answers, the member UUID for member-scoped ones. */
    val memberId: String?,
    /** Portable scalar/multi objects, never the store's doubly encoded JSON strings. */
    val answer: BackupAnswerValue,
)

@Serializable
data class BackupAnswerValue(val kind: String, val value: List<String>) {
    companion object {
        fun scalar(text: String) = BackupAnswerValue("scalar", listOf(text))
        fun multi(selected: List<String>) = BackupAnswerValue("multi", selected)
    }
}

@Serializable
data class BackupVisit(
    val id: String,
    /** The form-entered visit date; legacy rows may be empty. */
    @SerialName("visitedOn") val visitedOn: String,
    val outcome: String,
    @SerialName("callbackLocalDateTime") val callbackLocalDateTime: String?,
    val callbackTimeZone: String?,
    val notes: String,
    /** When the entry was appended; null for legacy rows recorded before the column. */
    @SerialName("recordedAt") val recordedAt: String?,
)

@Serializable
data class BackupLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyMetres: Double?,
    /** device when accuracy is measured, manual for typed corrections. */
    val source: String,
    val capturedAt: String,
)

@Serializable
data class BackupAttachment(
    val id: String,
    val memberId: String?,
    val kind: String,
    @SerialName("mimeType") val mimeType: String,
    val byteSize: Int,
    val sha256: String,
    val contentBase64: String,
)

/** Shared encoder: pretty UTF-8 JSON with every contract field explicit (a backup file
 * must always carry format/formatVersion/prototype, even at their default values). */
val BackupJson: Json = Json { prettyPrint = true; encodeDefaults = true }
