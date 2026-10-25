package ph.tala.registry.data.cloud

import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import ph.tala.registry.data.backup.AttachmentSource
import ph.tala.registry.data.backup.BackupExporter
import ph.tala.registry.data.backup.BackupJson
import ph.tala.registry.data.backup.BackupMapper
import ph.tala.registry.data.backup.BackupRecord
import ph.tala.registry.data.local.RecordStore
import ph.tala.registry.data.local.StoredRecord
import ph.tala.registry.data.local.SyncOutboxStore

sealed interface HouseholdSyncResult {
    data class Synced(val count: Int, val attachmentCount: Int = 0) : HouseholdSyncResult
    data class Conflicted(val count: Int) : HouseholdSyncResult
    data class Failed(val message: String) : HouseholdSyncResult
}

interface HouseholdCloudSyncRepository {
    suspend fun upload(records: List<StoredRecord>): HouseholdSyncResult
    suspend fun replaceCloud(records: List<StoredRecord>): HouseholdSyncResult = HouseholdSyncResult.Failed("Cloud conflict replacement is unavailable.")
    suspend fun retryPending(): HouseholdSyncResult = HouseholdSyncResult.Synced(0)
}

@Serializable
private data class SnapshotSyncParameters(
    @SerialName("p_household_id") val householdId: String,
    @SerialName("p_payload") val payload: JsonElement,
    @SerialName("p_expected_revision") val expectedRevision: Long?,
    @SerialName("p_force") val force: Boolean,
)

@Serializable
private data class SnapshotRevision(@SerialName("new_revision") val revision: Long)

@Serializable
private data class AttachmentObjectUpsert(
    @SerialName("owner_id") val ownerId: String,
    @SerialName("household_id") val householdId: String,
    @SerialName("attachment_id") val attachmentId: String,
    @SerialName("member_id") val memberId: String?,
    val kind: String,
    @SerialName("mime_type") val mimeType: String,
    @SerialName("byte_size") val byteSize: Int,
    val sha256: String,
    @SerialName("object_path") val objectPath: String,
)

@Serializable
private data class ExistingAttachmentObject(
    @SerialName("attachment_id") val attachmentId: String,
    @SerialName("object_path") val objectPath: String,
)

/**
 * Queues selected household IDs before network I/O, then uploads the latest Room
 * snapshot and every referenced attachment. A failed or interrupted upload remains in
 * the Room outbox and can be retried idempotently after process restart.
 */
class SupabaseHouseholdCloudSyncRepository(
    private val accounts: SupabaseAccountRepository,
    private val records: RecordStore,
    private val outbox: SyncOutboxStore,
    private val attachments: AttachmentSource,
) : HouseholdCloudSyncRepository {
    override suspend fun upload(records: List<StoredRecord>): HouseholdSyncResult {
        if (records.isEmpty()) return HouseholdSyncResult.Failed("Select at least one household to sync.")
        outbox.enqueue(records.map { it.household.id })
        return drain()
    }

    override suspend fun replaceCloud(records: List<StoredRecord>): HouseholdSyncResult {
        if (records.isEmpty()) return HouseholdSyncResult.Failed("Select the conflicted households first.")
        val householdIds = records.map { it.household.id }.toSet()
        outbox.enqueue(householdIds)
        return drain(force = true, onlyIds = householdIds)
    }

    override suspend fun retryPending(): HouseholdSyncResult = drain()

    private suspend fun drain(force: Boolean = false, onlyIds: Set<String>? = null): HouseholdSyncResult {
        val pending = outbox.pending().filter { onlyIds == null || it.householdId in onlyIds }
        if (pending.isEmpty()) return HouseholdSyncResult.Synced(0)
        val client = accounts.clientOrNull()
            ?: return HouseholdSyncResult.Failed("Cloud sync is queued, but Supabase is not configured on this device.")
        val account = accounts.currentAccount()
            ?: return HouseholdSyncResult.Failed("Cloud sync is queued. Sign in to retry it.")
        var synced = 0
        var attachmentCount = 0
        var failures = 0
        var conflicts = 0
        for (operation in pending) {
            val record = records.load(operation.householdId)
            if (record == null) {
                outbox.completed(operation.householdId)
                continue
            }
            try {
                attachmentCount += syncRecord(client, account.id, record, force)
                outbox.completed(operation.householdId)
                synced += 1
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (error.message.orEmpty().contains(CONFLICT_CODE)) {
                    conflicts += 1
                    outbox.failed(operation.householdId, "Remote snapshot changed; review before replacing it.")
                } else {
                    failures += 1
                    outbox.failed(operation.householdId, "Upload failed; retry when online.")
                }
            }
        }
        return if (conflicts > 0) HouseholdSyncResult.Conflicted(conflicts)
        else if (failures == 0) HouseholdSyncResult.Synced(synced, attachmentCount)
        else HouseholdSyncResult.Failed(
            if (synced == 0) "Cloud sync is queued and will retry when signed in and online."
            else "Synced $synced households; $failures remain queued for retry.",
        )
    }

    private suspend fun syncRecord(
        client: io.github.jan.supabase.SupabaseClient,
        ownerId: String,
        record: StoredRecord,
        force: Boolean,
    ): Int {
        val existing = client.from(ATTACHMENT_TABLE).select {
            filter {
                eq("owner_id", ownerId)
                eq("household_id", record.household.id)
            }
        }.decodeList<ExistingAttachmentObject>()

        val uploaded = record.attachments.map { attachment ->
            val bytes = attachments.read(attachment.relativePath)
                ?: error("A referenced attachment is missing")
            require(bytes.isNotEmpty() && bytes.size <= BackupExporter.MAX_ATTACHMENT_BYTES)
            val extension = when (attachment.mimeType) {
                "image/jpeg" -> "jpg"
                "image/png" -> "png"
                "image/webp" -> "webp"
                else -> error("Unsupported attachment type")
            }
            val sha256 = BackupMapper.sha256Hex(bytes)
            val objectPath = "$ownerId/${record.household.id}/${attachment.id}-${sha256.take(16)}.$extension"
            client.storage.from(ATTACHMENT_BUCKET).upload(objectPath, bytes) { upsert = true }
            AttachmentObjectUpsert(
                ownerId = ownerId,
                householdId = record.household.id,
                attachmentId = attachment.id,
                memberId = attachment.memberId,
                kind = attachment.kind,
                mimeType = attachment.mimeType,
                byteSize = bytes.size,
                sha256 = sha256,
                objectPath = objectPath,
            )
        }

        val payload = BackupJson.encodeToJsonElement(BackupRecord.serializer(), BackupMapper.mapRecord(record, emptyMap()))
        val revision = client.postgrest.rpc(
            "sync_household_snapshot",
            BackupJson.encodeToJsonElement(
                SnapshotSyncParameters.serializer(),
                SnapshotSyncParameters(record.household.id, payload, outbox.revision(ownerId, record.household.id), force),
            ).jsonObject,
        ).decodeSingle<SnapshotRevision>().revision
        outbox.updateRevision(ownerId, record.household.id, revision)

        if (uploaded.isNotEmpty()) {
            client.from(ATTACHMENT_TABLE).upsert(
                JsonArray(uploaded.map { BackupJson.encodeToJsonElement(AttachmentObjectUpsert.serializer(), it) }),
            ) { onConflict = "owner_id,attachment_id" }
        }

        val currentIds = uploaded.map { it.attachmentId }.toSet()
        val stale = existing.filterNot { it.attachmentId in currentIds }
        if (stale.isNotEmpty()) client.storage.from(ATTACHMENT_BUCKET).delete(*stale.map { it.objectPath }.toTypedArray())
        stale.forEach { old ->
            client.from(ATTACHMENT_TABLE).delete {
                filter {
                    eq("owner_id", ownerId)
                    eq("attachment_id", old.attachmentId)
                }
            }
        }
        return uploaded.size
    }

    private companion object {
        const val ATTACHMENT_TABLE = "household_attachment_objects"
        const val ATTACHMENT_BUCKET = "registry-attachments"
        const val CONFLICT_CODE = "TALA_SYNC_CONFLICT"
    }
}
