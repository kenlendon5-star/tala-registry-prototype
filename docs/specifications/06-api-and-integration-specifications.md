# API & Integration Specifications — Tala

Version 1.0 · 11 October 2026. **Current:** offline app architecture and browser integrations. **Proposed:** native adapter/backup contracts. **Future:** cloud sync. There is no deployed application REST API or payment gateway in this repository.

## 1. Integration inventory

| Integration | Data exchanged | Authentication / network | State |
|---|---|---|---|
| Room repository | Typed local record snapshots and change streams | App process; no network | Store and editing adapter implemented (Days 5–6) |
| Device location | Latitude, longitude, optional accuracy, capture time | Foreground user permission | Browser implemented; native pending |
| OSM raster tiles | Visible viewport z/x/y requests | Online public service, policy applies | Browser implemented |
| Google Maps URLs | Saved coordinates or user-selected address | Explicit external action; api=1 URL needs no API key | Browser implemented; native launcher pending |
| Photo/file picker | Selected image content or document URI | User-mediated OS access | Browser file input implemented; native pending |
| Backup destination/source | Versioned JSON file including media | User-selected local/document provider | Browser legacy export only; proposed native contract |
| Supabase | Future authenticated record changes and private media | HTTPS, authenticated client, server authorization | No application integration |
| Payments, SMS, email | None | None | Not required; do not add credentials/services |

## 2. Current local interfaces

HouseholdRepository exposes `households: Flow<List<Household>>` and `observeHousehold(HouseholdId)`, plus the Day 6 writes `createHousehold(id, draft)`, `updateHousehold(id, draft)`, `setArchived(id, archived)`, `saveMember(householdId, memberId, draft)`, `removeMember(householdId, memberId)` and `seedMissing(records)`. The current implementation is RoomHouseholdRepository: one mutex-serialised write stream over RecordStore full-snapshot saves, UUID identities with unique `HH-#####` display labels, and defensive validation before every write. RecordStore exposes `households: Flow<List<HouseholdRow>>`, `records: Flow<List<HouseholdWithMembers>>`, `suspend load(id: String): StoredRecord?`, and `suspend save(record: StoredRecord): WriteResult`. Current WriteResult is Saved or Failed(message); cancellation is rethrown. Conflict detection and revision checks (TR-06) are not implemented yet; a stale editor currently overwrites the latest snapshot.

The current snapshot save validates identity/ownership, then updates the household and replaces members, interview/visits/answers, coordinates and attachment references in one Room transaction. The caller must supply the entire graph. Do not implement Save Section by discarding other sections and passing only the current answers.

## 3. Proposed editing repository contract

The following is an interface specification, not existing Kotlin source:

```kotlin
suspend fun loadHousehold(id: HouseholdId): LoadResult
suspend fun createHousehold(input: NewHousehold): SaveResult
suspend fun saveSnapshot(snapshot: HouseholdSnapshot, expectedRevision: Long): SaveResult
suspend fun archive(id: HouseholdId, expectedRevision: Long): SaveResult
suspend fun restoreArchive(id: HouseholdId, expectedRevision: Long): SaveResult
suspend fun previewImport(source: BackupSource): ImportPreviewResult
suspend fun applyImport(previewToken: String, decisions: List<ConflictDecision>): ImportResult
```

| Result | Required payload | UI behavior |
|---|---|---|
| Saved | householdId, newRevision, savedAt | Display Saved; navigation/review may proceed |
| Invalid | ordered field/scope errors | Focus first visible error; preserve input |
| Conflict | householdId, expectedRevision, actualRevision | Ask reload/resolve; retain unsaved state |
| Failed | stable code and safe message | Retry possible; prior durable state unchanged |
| NotFound | requested ID | Explain unavailable record and return to list |

Stable proposed failure codes: STORAGE_UNAVAILABLE, STORAGE_FULL, INVALID_RECORD, STALE_REVISION, NOT_FOUND, MEDIA_INVALID, MEDIA_WRITE_FAILED, UNSUPPORTED_VERSION, BACKUP_TOO_LARGE, BACKUP_CORRUPT, PERMISSION_DENIED and CANCELLED. Cancellation is a neutral user outcome, not a successful write. Do not expose SQL, absolute file paths, answers or credentials in messages.

Each save runs once against its expected revision. A duplicate retry may return its already committed result only if the repository can prove it is the same operation; it must not silently increment revisions for an unrelated stale write. Keep import previews invalidatable when destination revisions change.

## 4. Location adapter

Request only when the user selects Use Device Location. Bind the request to household ID and screen/request generation. Target timeout 15 seconds, foreground-only; cancel when leaving the screen. Accept optional accuracy and represent unavailable accuracy as null, not zero. Reject nonfinite values and latitude outside [-90,90] or longitude outside [-180,180].

Result payload:

```json
{"householdId":"11111111-1111-4111-8111-111111111111","latitude":11.0176,"longitude":124.6031,"accuracyMetres":12.5,"capturedAt":"2026-10-11T09:00:00Z","source":"device"}
```

The result is an unconfirmed draft. Show the pin/coordinates and require confirmation before persistence. DENIED, TIMEOUT and UNAVAILABLE lead to manual input. A callback for a previously selected household must never write to the newly selected one. Browser working-area bounds are illustrative; official locality validation awaits DEC-04.

## 5. Maps and directions

Current tile template is `https://tile.openstreetmap.org/{z}/{x}/{y}.png`. Keep visible attribution, request only normal visible-map content, follow caching/client identification requirements, and show fallback when unavailable. Do not bulk-download or prefetch offline regions from this endpoint. [OSM tile usage policy](https://operations.osmfoundation.org/policies/tiles/).

External map contracts (encode query parameters using a URL builder):

```text
https://www.google.com/maps/search/?api=1&query=11.0176%2C124.6031
https://www.google.com/maps/dir/?api=1&destination=11.0176%2C124.6031
```

Use saved coordinates for a saved pin. If the user explicitly searches an address without a pin, encode the address plus locality. Do not put names, IDs, phone numbers, health information or answer JSON in map URLs. Launch through a user action; handle an absent application by offering a browser or displaying the coordinates. Maps URLs are cross-platform and do not require an API key; this does not imply native embedded map SDKs have the same requirements. [Google Maps URLs](https://developers.google.com/maps/documentation/urls/get-started).

## 6. Media and backup adapters

Media input accepts JPEG, PNG and WebP up to 5 MiB. Decode and verify the actual image rather than trusting a filename. Re-encode to remove unnecessary metadata and honor the technical size limits. Save file references and checksums, not transient picker URIs or browser data URLs, in the target database. Permission denial and picker cancel leave the previous attachment intact.

Backup uses the [versioned schema](../schemas/backup-v1.schema.json) and [example](../schemas/backup-v1.example.json). Export completes only when all bytes reach the chosen destination. On cancellation, do not mark the record exported or erase local data. Restore validates before mutation and includes an explicit conflict preview; see [data requirements](03-data-models-and-schemas.md).

Current browser downloads (`{exportedAt, prototype, households}`) are legacy exports and lack the proposed format marker/version. They need a deliberate conversion path, including UUID assignment and signature/photo conversion.

## 7. Future Supabase integration contract — not implemented

Do not build a client that assumes the tooling setup created application tables. Before connecting, settle ownership, assignment and supervisor authority, then create versioned migrations, explicit grants, RLS policies and allow/deny tests. Use only client-appropriate publishable credentials plus authenticated sessions; service-role/secret credentials stay outside clients. [Supabase RLS](https://supabase.com/docs/guides/database/postgres/row-level-security).

The following semantic operations are a proposed sync contract, **not existing URLs**. Choose PostgREST/RPC/Edge Function routing when the cloud phase is approved; do not fabricate deployed `/api/v1` endpoints.

| Operation | Request | Response / behavior |
|---|---|---|
| Pull changes | authorized workspace, opaque cursor, limit ≤100 | authorized upserts/tombstones, next cursor, hasMore; stable ordering |
| Push household | UUID, base server revision, operation UUID, questionnaire version, full validated snapshot | new revision/ack; duplicate operation returns same result; stale revision returns Conflict |
| Upload attachment | authorized household/member, content hash, MIME, size | private object identifier; bounded upload; file access checked independently |
| Download attachment | authorized object reference | expiring authorized access or authenticated stream; no public bucket |
| Resolve conflict | latest revision and explicit chosen merged snapshot | atomic new revision or another conflict; no automatic data loss |

Error semantics: invalid input→400, unauthenticated→401, forbidden→403 (or intentionally indistinguishable NotFound), absent record→404, revision conflict→409, too large→413, rate limit→429, retryable server failure→5xx. These are proposed HTTP adapter semantics, not guaranteed raw Supabase responses.

Retry only idempotent operations with jittered delays 1s, 2s, 4s, 8s, 16s; respect Retry-After. Do not retry authorization/validation failures blindly. Persist pending work and operation IDs before network submission. Never move local data to Reviewed because it synchronized; completion and synchronization are separate states. Cloud timestamps/cursors are server-owned and cannot rely on device-clock ordering.

## 8. Contract acceptance

Tests must cover normal responses, denial, timeout, cancellation, malformed values, offline operation, stale callbacks, duplicate retries, conflicting revisions, unsupported questionnaire/backup versions, corrupt media and partial import rollback. Mock adapters test UI behavior; real device/provider integration verifies permissions and lifecycle. Future cloud tests must exercise both authorized and unauthorized roles against actual policies before pilot use.
