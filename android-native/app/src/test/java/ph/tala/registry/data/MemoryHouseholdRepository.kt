package ph.tala.registry.data

import ph.tala.registry.data.local.RecordStore

/** Uses production repository behavior over the existing in-memory record store. */
class MemoryHouseholdRepository(store: RecordStore = FakeRecordStore(), profiles: EnumeratorProfileStore? = null) :
    HouseholdRepository by RoomHouseholdRepository(store, profiles = profiles)
