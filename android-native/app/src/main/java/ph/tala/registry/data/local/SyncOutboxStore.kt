package ph.tala.registry.data.local

import androidx.room.withTransaction

interface SyncOutboxStore {
    suspend fun enqueue(householdIds: Collection<String>)
    suspend fun pending(): List<SyncOutboxRow>
    suspend fun completed(householdId: String)
    suspend fun failed(householdId: String, message: String)
    suspend fun revision(ownerId: String, householdId: String): Long?
    suspend fun updateRevision(ownerId: String, householdId: String, revision: Long)
}

class RoomSyncOutboxStore(private val database: RegistryDatabase) : SyncOutboxStore {
    private val dao = database.records()

    override suspend fun enqueue(householdIds: Collection<String>) {
        if (householdIds.isEmpty()) return
        val requestedAt = System.currentTimeMillis()
        database.withTransaction {
            val existing = dao.pendingSyncs().associateBy { it.householdId }
            dao.putSyncOutbox(householdIds.distinct().map { id ->
                existing[id]?.copy(requestedAt = requestedAt, lastError = "")
                    ?: SyncOutboxRow(id, requestedAt)
            })
        }
    }

    override suspend fun pending(): List<SyncOutboxRow> = dao.pendingSyncs()

    override suspend fun completed(householdId: String) = dao.clearSyncOutbox(householdId)

    override suspend fun failed(householdId: String, message: String) = dao.failSyncOutbox(householdId, message.take(240))

    override suspend fun revision(ownerId: String, householdId: String): Long? = dao.syncRevision(ownerId, householdId)

    override suspend fun updateRevision(ownerId: String, householdId: String, revision: Long) =
        dao.putSyncRevision(SyncRevisionRow(ownerId, householdId, revision))
}
