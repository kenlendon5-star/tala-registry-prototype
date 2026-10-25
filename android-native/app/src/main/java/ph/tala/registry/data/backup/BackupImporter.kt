package ph.tala.registry.data.backup

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.decodeFromStream
import ph.tala.registry.data.local.AnswerCodec
import ph.tala.registry.data.local.AttachmentRow
import ph.tala.registry.data.local.CoordinateRow
import ph.tala.registry.data.local.HouseholdAnswerRow
import ph.tala.registry.data.local.HouseholdRow
import ph.tala.registry.data.local.InterviewRow
import ph.tala.registry.data.local.MemberAnswerRow
import ph.tala.registry.data.local.MemberRow
import ph.tala.registry.data.local.RecordStore
import ph.tala.registry.data.local.StoredRecord
import ph.tala.registry.data.local.VisitRow
import ph.tala.registry.data.local.WriteResult
import ph.tala.registry.domain.form.InterviewRules
import ph.tala.registry.domain.form.QuestionnaireSections
import ph.tala.registry.domain.model.AnswerValue
import java.io.File
import java.io.InputStream
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Base64
import java.util.UUID

interface RestoredAttachmentStore {
    suspend fun stage(bytes: ByteArray, mimeType: String): String
    suspend fun discard(relativePaths: Collection<String>)
}

class BackupImporter(
    private val records: RecordStore,
    private val attachments: RestoredAttachmentStore,
    private val stagingDirectory: File,
) {
    sealed interface PreviewResult {
        data class Ready(val preview: ImportPreview) : PreviewResult
        data class Failed(val message: String) : PreviewResult
    }

    sealed interface RestoreResult {
        data class Restored(val recordCount: Int, val attachmentCount: Int, val replacedCount: Int) : RestoreResult
        data class Failed(val message: String) : RestoreResult
    }

    data class ImportPreview(
        val token: String,
        val recordCount: Int,
        val attachmentCount: Int,
        val replacedCount: Int,
    )

    suspend fun preview(openSource: () -> InputStream): PreviewResult {
        val token = UUID.randomUUID().toString()
        val staging = previewFile(token)
        return try {
            staging.parentFile?.mkdirs()
            copyBounded(openSource(), staging)
            val backup = decodeAndValidate(staging)
            val existing = records.loadAll()
            val existingById = existing.associateBy { it.household.id }
            val existingCodeOwners = existing.associate { it.household.displayCode to it.household.id }
            val collision = backup.records.firstOrNull { record ->
                existingCodeOwners[record.displayCode]?.let { it != record.id } == true
            }
            require(collision == null) { "A different household already uses ${collision?.displayCode}. Remove the conflict or choose another backup." }
            PreviewResult.Ready(
                ImportPreview(
                    token = token,
                    recordCount = backup.records.size,
                    attachmentCount = backup.records.sumOf { it.attachments.size },
                    replacedCount = backup.records.count { it.id in existingById },
                ),
            )
        } catch (cancelled: CancellationException) {
            staging.delete()
            throw cancelled
        } catch (error: Exception) {
            staging.delete()
            PreviewResult.Failed(error.safeImportMessage())
        }
    }

    suspend fun restore(preview: ImportPreview): RestoreResult {
        val staging = previewFile(preview.token)
        val stagedPaths = mutableListOf<String>()
        var committed = false
        return try {
            require(staging.isFile) { "The selected backup preview expired. Choose the file again." }
            val backup = decodeAndValidate(staging)
            val previousPaths = backup.records.mapNotNull { records.load(it.id) }.flatMap { record -> record.attachments.map { it.relativePath } }
            val restored = backup.records.map { record ->
                val paths = record.attachments.associate { attachment ->
                    val bytes = decodeAttachment(attachment)
                    val path = attachments.stage(bytes, attachment.mimeType)
                    stagedPaths += path
                    attachment.id to path
                }
                record.toStoredRecord(paths)
            }
            when (val result = records.restore(restored)) {
                WriteResult.Saved -> {
                    committed = true
                    val livePaths = restored.flatMap { record -> record.attachments.map { it.relativePath } }.toSet()
                    runCatching { attachments.discard(previousPaths.filterNot(livePaths::contains)) }
                    RestoreResult.Restored(restored.size, stagedPaths.size, preview.replacedCount)
                }
                is WriteResult.Failed -> {
                    attachments.discard(stagedPaths)
                    RestoreResult.Failed(result.message)
                }
            }
        } catch (cancelled: CancellationException) {
            if (!committed) attachments.discard(stagedPaths)
            throw cancelled
        } catch (error: Exception) {
            if (!committed) attachments.discard(stagedPaths)
            RestoreResult.Failed(error.safeImportMessage())
        } finally {
            staging.delete()
        }
    }

    fun discard(preview: ImportPreview) {
        previewFile(preview.token).delete()
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun decodeAndValidate(file: File): BackupFile {
        val backup = file.inputStream().buffered().use { BackupJson.decodeFromStream<BackupFile>(it) }
        require(backup.format == FORMAT && backup.formatVersion == FORMAT_VERSION) { "This backup format is not supported." }
        require(backup.prototype) { "This backup is not marked for Tala's fictional-data environment." }
        Instant.parse(backup.exportedAt)
        require(backup.records.isNotEmpty()) { "The backup contains no household records." }
        require(backup.records.size <= MAX_RECORDS) { "The backup contains too many household records." }
        require(backup.records.map { it.id }.distinct().size == backup.records.size) { "The backup contains duplicate household IDs." }
        require(backup.records.map { it.displayCode }.distinct().size == backup.records.size) { "The backup contains duplicate household codes." }
        requireUnique(backup.records.map { it.interview.id }, "interview")
        requireUnique(backup.records.flatMap { record -> record.members.map { it.id } }, "member")
        requireUnique(backup.records.flatMap { record -> record.visits.map { it.id } }, "visit")
        requireUnique(backup.records.flatMap { record -> record.attachments.map { it.id } }, "attachment")
        backup.records.forEach(::validateRecord)
        return backup
    }

    private fun validateRecord(record: BackupRecord) {
        uuid(record.id)
        require(DISPLAY_CODE.matches(record.displayCode)) { "A household code is invalid." }
        require(record.headName.length <= MAX_TEXT && record.address.length <= MAX_TEXT) { "A household text field is too long." }
        require(record.status in STATUSES) { "A household status is invalid." }
        record.archivedAt?.let { Instant.parse(it) }
        uuid(record.interview.id)
        require(record.interview.questionnaireVersion == QuestionnaireSections.VERSION) { "This backup uses an unsupported questionnaire version." }

        val memberIds = record.members.map { member ->
            uuid(member.id)
            require(member.displayName.length <= MAX_TEXT)
            member.id
        }
        require(memberIds.distinct().size == memberIds.size) { "A household contains duplicate member IDs." }
        require(record.members.map { it.sortOrder }.sorted() == record.members.indices.toList()) { "Member ordering is invalid." }
        require(record.interview.answers.size <= MAX_ANSWERS_PER_RECORD) { "A household contains too many answers." }
        val answerKeys = record.interview.answers.map { answer ->
            val section = QuestionnaireSections.forms.find { it.route == answer.section }
            require(section != null && section.fields.any { it.key == answer.field }) { "An answer does not belong to the current questionnaire." }
            require(if (section.scopedToMember) answer.memberId in memberIds else answer.memberId == null) { "An answer has an invalid household or member scope." }
            require(answer.answer.kind in setOf("scalar", "multi")) { "An answer kind is invalid." }
            require(answer.answer.value.size <= MAX_ANSWER_VALUES && answer.answer.value.all { it.length <= MAX_TEXT }) { "An answer value is too large." }
            if (answer.answer.kind == "scalar") require(answer.answer.value.size == 1)
            else require(answer.answer.value.distinct().size == answer.answer.value.size)
            listOf(answer.memberId.orEmpty(), answer.section, answer.field).joinToString("|")
        }
        require(answerKeys.distinct().size == answerKeys.size) { "The backup contains duplicate answers." }
        val consent = record.interview.answers.find { it.section == "consent" && it.field == "consent" }
            ?.answer?.toAnswerValue() as? AnswerValue.Scalar
        val signedName = record.interview.answers.find { it.section == "consent" && it.field == "signedName" }
            ?.answer?.toAnswerValue() as? AnswerValue.Scalar
        require(record.interview.consent in setOf("Unanswered", "Yes", "No") && record.interview.consent == (consent?.text?.takeIf(String::isNotBlank) ?: "Unanswered")) {
            "The backup consent summary does not match its answers."
        }
        require(record.interview.signedName == signedName?.text?.takeIf(String::isNotBlank)) {
            "The backup signature summary does not match its answers."
        }

        val visitIds = record.visits.map { visit ->
            uuid(visit.id)
            if (visit.visitedOn.isNotBlank()) LocalDate.parse(visit.visitedOn)
            visit.callbackLocalDateTime?.let(LocalDateTime::parse)
            visit.recordedAt?.let(Instant::parse)
            require(visit.outcome.length <= 100 && visit.notes.length <= MAX_NOTES)
            visit.id
        }
        require(visitIds.distinct().size == visitIds.size) { "The backup contains duplicate visits." }

        record.location?.let { location ->
            require(location.latitude.isFinite() && location.latitude in -90.0..90.0)
            require(location.longitude.isFinite() && location.longitude in -180.0..180.0)
            require(location.source in setOf("device", "manual"))
            require(location.accuracyMetres == null || (location.accuracyMetres.isFinite() && location.accuracyMetres >= 0.0))
            require((location.source == "manual") == (location.accuracyMetres == null))
            Instant.parse(location.capturedAt)
        }

        require(record.attachments.size <= MAX_ATTACHMENTS_PER_RECORD) { "A household contains too many attachments." }
        val attachmentIds = record.attachments.map { attachment ->
            uuid(attachment.id)
            require(attachment.memberId == null || attachment.memberId in memberIds) { "An attachment refers to a missing member." }
            require(attachment.kind in ATTACHMENT_KINDS) { "An attachment kind is invalid." }
            require(attachment.mimeType in MIME_TYPES) { "An attachment type is invalid." }
            decodeAttachment(attachment)
            attachment.id
        }
        require(attachmentIds.distinct().size == attachmentIds.size) { "The backup contains duplicate attachment IDs." }
        record.interview.signatureAttachmentId?.let { signatureId ->
            require(record.attachments.any { it.id == signatureId && it.kind == "signature" }) { "The signature link is invalid." }
        }
    }

    private fun decodeAttachment(attachment: BackupAttachment): ByteArray {
        require(attachment.byteSize in 1..BackupExporter.MAX_ATTACHMENT_BYTES) { "An attachment size is invalid." }
        require(attachment.contentBase64.length <= encodedBase64Size(attachment.byteSize) + 2) { "An attachment is larger than declared." }
        val bytes = Base64.getDecoder().decode(attachment.contentBase64)
        require(bytes.size == attachment.byteSize) { "An attachment size does not match its content." }
        require(BackupMapper.sha256Hex(bytes).equals(attachment.sha256, ignoreCase = true)) { "An attachment failed its integrity check." }
        return bytes
    }

    private fun BackupRecord.toStoredRecord(paths: Map<String, String>): StoredRecord {
        val memberRows = members.sortedBy { it.sortOrder }.map { member -> MemberRow(member.id, id, member.displayName, member.sortOrder) }
        val householdAnswers = mutableListOf<HouseholdAnswerRow>()
        val memberAnswers = mutableListOf<MemberAnswerRow>()
        interview.answers.groupBy { it.section to it.memberId }.forEach { (scope, answers) ->
            val section = QuestionnaireSections.forms.first { it.route == scope.first }
            val memberId = scope.second
            val cleaned = InterviewRules.clean(section, answers.associate { it.field to it.answer.toAnswerValue() })
            cleaned.forEach { (field, value) ->
                val (kind, encoded) = AnswerCodec.encode(value)
                if (memberId == null) householdAnswers += HouseholdAnswerRow(interview.id, scope.first, field, kind, encoded)
                else memberAnswers += MemberAnswerRow(interview.id, id, memberId, scope.first, field, kind, encoded)
            }
        }
        val restored = StoredRecord(
            household = HouseholdRow(id, displayCode, headName, address, status, archivedAt?.let { Instant.parse(it).toEpochMilli() }),
            members = memberRows,
            interview = InterviewRow(interview.id, id, interview.questionnaireVersion),
            visits = visits.map { visit -> VisitRow(visit.id, interview.id, visit.outcome, visit.callbackLocalDateTime, visit.visitedOn, visit.notes, visit.recordedAt?.let { Instant.parse(it).toEpochMilli() } ?: 0L) },
            householdAnswers = householdAnswers,
            memberAnswers = memberAnswers,
            coordinates = location?.let { CoordinateRow(id, it.latitude, it.longitude, it.accuracyMetres, Instant.parse(it.capturedAt).toEpochMilli()) },
            attachments = attachments.map { attachment -> AttachmentRow(attachment.id, id, attachment.memberId, attachment.kind, checkNotNull(paths[attachment.id]), attachment.mimeType) },
        )
        return if (restored.household.status == "Reviewed" && InterviewRules.issues(restored).isNotEmpty()) {
            restored.copy(household = restored.household.copy(status = "InProgress"))
        } else restored
    }

    private fun BackupAnswerValue.toAnswerValue(): AnswerValue = when (kind) {
        "scalar" -> AnswerValue.Scalar(value.single())
        "multi" -> AnswerValue.Multi(value)
        else -> error("Unknown answer kind")
    }

    private fun copyBounded(source: InputStream, target: File) {
        source.use { input ->
            target.outputStream().buffered().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    require(total <= BackupExporter.MAX_TOTAL_BYTES) { "The backup is larger than 50 MiB." }
                    output.write(buffer, 0, read)
                }
            }
        }
    }

    private fun previewFile(token: String): File {
        require(UUID.fromString(token).toString() == token)
        val root = stagingDirectory.canonicalFile
        val result = File(root, "tala-import-$token.json").canonicalFile
        require(result.toPath().startsWith(root.toPath()))
        return result
    }

    private fun uuid(value: String) {
        require(UUID.fromString(value).toString() == value) { "The backup contains an invalid identity." }
    }

    private fun requireUnique(values: List<String>, label: String) {
        require(values.distinct().size == values.size) { "The backup contains duplicate $label IDs across households." }
    }

    private fun encodedBase64Size(byteCount: Int): Int = 4 * ((byteCount + 2) / 3)

    private fun Exception.safeImportMessage(): String = message?.takeIf {
        it.startsWith("This backup") || it.startsWith("The backup") || it.startsWith("A household") ||
            it.startsWith("A different household") || it.startsWith("An attachment") || it.startsWith("An answer") ||
            it.startsWith("Member ordering") || it.startsWith("The signature") || it.startsWith("The selected")
    } ?: "The selected file is not a valid Tala backup. Existing records are unchanged."

    private companion object {
        const val FORMAT = "tala-backup"
        const val FORMAT_VERSION = 1
        const val MAX_RECORDS = 5_000
        const val MAX_ATTACHMENTS_PER_RECORD = 500
        const val MAX_ANSWERS_PER_RECORD = 10_000
        const val MAX_ANSWER_VALUES = 100
        const val MAX_TEXT = 2_000
        const val MAX_NOTES = 10_000
        val DISPLAY_CODE = Regex("HH-[0-9]{5,}")
        val STATUSES = setOf("NotStarted", "InProgress", "Callback", "Reviewed")
        val ATTACHMENT_KINDS = setOf("household-photo", "member-photo", "signature")
        val MIME_TYPES = setOf("image/jpeg", "image/png", "image/webp")
    }
}
