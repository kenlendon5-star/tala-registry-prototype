package ph.tala.registry.ui.reports

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ph.tala.registry.data.FakeRecordStore
import ph.tala.registry.data.RoomHouseholdRepository
import ph.tala.registry.data.backup.AttachmentSource
import ph.tala.registry.data.backup.BackupExporter
import ph.tala.registry.data.cloud.HouseholdCloudSyncRepository
import ph.tala.registry.data.cloud.HouseholdSyncResult
import ph.tala.registry.data.local.StoredRecord
import ph.tala.registry.domain.edit.HouseholdDraft
import ph.tala.registry.domain.model.HouseholdId
import ph.tala.registry.domain.model.HouseholdStatus

@OptIn(ExperimentalCoroutinesApi::class)
class ReportsSyncViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun syncUploadsOnlyTheSelectedLocalHousehold() = runTest(dispatcher) {
        val repository = RoomHouseholdRepository(FakeRecordStore())
        repository.createHousehold(HouseholdId("a0000000-0000-4000-8000-000000000001"), HouseholdDraft("Ari Santos", "Purok 1", HouseholdStatus.InProgress))
        repository.createHousehold(HouseholdId("a0000000-0000-4000-8000-000000000002"), HouseholdDraft("Bea Santos", "Purok 2", HouseholdStatus.InProgress))
        val sync = CapturingSync()
        val model = ReportsViewModel(repository, BackupExporter(), object : AttachmentSource { override suspend fun read(relativePath: String) = null }, sync, dispatcher)

        advanceUntilIdle()
        val selectedCode = model.state().value.households.first().displayCode
        model.toggle(selectedCode)
        model.syncSelected()
        advanceUntilIdle()

        assertEquals(listOf(selectedCode), sync.uploaded.flatMap { it }.map { it.household.displayCode })
        assertEquals(ReportsUiState.Phase.Synced, model.state().value.phase)
    }

    @Test
    fun conflictRequiresExplicitReplacementOfSelectedHousehold() = runTest(dispatcher) {
        val repository = RoomHouseholdRepository(FakeRecordStore())
        repository.createHousehold(HouseholdId("a0000000-0000-4000-8000-000000000001"), HouseholdDraft("Ari Santos", "Purok 1", HouseholdStatus.InProgress))
        val sync = CapturingSync(HouseholdSyncResult.Conflicted(1))
        val model = ReportsViewModel(repository, BackupExporter(), object : AttachmentSource { override suspend fun read(relativePath: String) = null }, sync, dispatcher)

        advanceUntilIdle()
        val selectedCode = model.state().value.households.single().displayCode
        model.toggle(selectedCode)
        model.syncSelected()
        advanceUntilIdle()

        assertEquals(ReportsUiState.Phase.Conflict, model.state().value.phase)
        assertTrue(sync.replaced.isEmpty())

        model.replaceCloudWithSelected()
        advanceUntilIdle()

        assertEquals(listOf(selectedCode), sync.replaced.flatten().map { it.household.displayCode })
        assertEquals(ReportsUiState.Phase.Synced, model.state().value.phase)
    }

    private class CapturingSync(
        private val uploadResult: HouseholdSyncResult? = null,
    ) : HouseholdCloudSyncRepository {
        val uploaded = mutableListOf<List<StoredRecord>>()
        val replaced = mutableListOf<List<StoredRecord>>()
        override suspend fun upload(records: List<StoredRecord>): HouseholdSyncResult {
            uploaded += records
            return uploadResult ?: HouseholdSyncResult.Synced(records.size)
        }

        override suspend fun replaceCloud(records: List<StoredRecord>): HouseholdSyncResult {
            replaced += records
            return HouseholdSyncResult.Synced(records.size)
        }
    }
}
