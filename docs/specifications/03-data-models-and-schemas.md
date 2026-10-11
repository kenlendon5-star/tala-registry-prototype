# Data Models & Schemas — Tala

Version 1.0 · 11 October 2026. **Current** means checked-in Room version 1. **Target** means a design proposal requiring implementation/migration. No database is modified by this specification.

## 1. Sources of truth

- Current entities: [Entities.kt](../../android-native/app/src/main/java/ph/tala/registry/data/local/Entities.kt).
- Authoritative exported Room schema: [version 1 JSON](../../android-native/app/schemas/ph.tala.registry.data.local.RegistryDatabase/1.json).
- Readable generated DDL snapshot: [room-v1.sql](../schemas/room-v1.sql). Documentation only; do not apply as a Supabase migration.
- Current questionnaire: [field dictionary](../schemas/questionnaire-field-dictionary.md), [machine-readable inventory](../schemas/questionnaire-current.json), generated from browser/forms.js.
- Target portable format: [backup-v1.schema.json](../schemas/backup-v1.schema.json), [fictional example](../schemas/backup-v1.example.json). No current restore implementation consumes this format.

## 2. Current entity relationships

```mermaid
erDiagram
  HOUSEHOLDS ||--o{ MEMBERS : contains
  HOUSEHOLDS ||--o| INTERVIEWS : has
  INTERVIEWS ||--o{ VISITS : records
  INTERVIEWS ||--o{ HOUSEHOLD_ANSWERS : stores
  INTERVIEWS ||--o{ MEMBER_ANSWERS : stores
  MEMBERS ||--o{ MEMBER_ANSWERS : answers
  HOUSEHOLDS ||--o| COORDINATES : locates
  HOUSEHOLDS ||--o{ ATTACHMENTS : owns
  MEMBERS o|--o{ ATTACHMENTS : optionally_owns
```

One interview per household is enforced by a unique householdId index. The store expects an interview when loading a complete snapshot, although the household row can exist independently at the SQL level. Multiple visits belong to that interview. A future repeated census cycle needs a deliberate change to interview uniqueness; it is not supported by silently adding another interview now.

## 3. Current Room version-2 dictionary

All String columns use SQLite TEXT; Double uses REAL; Long uses INTEGER. Fields are non-null unless suffixed `?` below. UUID validation is performed by RoomRecordStore, not a SQL UUID column type.

| Table | Fields | Keys / relationships |
|---|---|---|
| households | id:String; displayCode:String; headName:String; address:String; status:String; archivedAt:Long? | PK id; unique displayCode. Store permits NotStarted, InProgress, Callback, Reviewed; archivedAt is null on active records and set when a record is archived. Migration 1 → 2 adds this column with `ALTER TABLE households ADD COLUMN archivedAt INTEGER` and keeps every existing row active |
| members | id:String; householdId:String; displayName:String | PK id; FK householdId→households.id CASCADE; household index; unique (id,householdId) |
| interviews | id:String; householdId:String; questionnaireVersion:String | PK id; unique householdId; FK→households CASCADE; unique (id,householdId). Kotlin default practice-v1 |
| visits | id:String; interviewId:String; outcome:String; callbackAt:String? | PK id; FK→interviews CASCADE; interview index. Current callback has no explicit zone column |
| household_answers | interviewId:String; section:String; field:String; kind:String; value:String | PK (interviewId,section,field); FK→interviews CASCADE |
| member_answers | interviewId:String; householdId:String; memberId:String; section:String; field:String; kind:String; value:String | PK (interviewId,memberId,section,field); composite FKs ensure interview and member belong to householdId; both FK pairs indexed |
| coordinates | householdId:String; latitude:Double; longitude:Double; accuracyMetres:Double?; capturedAt:Long | PK/FK householdId→households CASCADE; store validates latitude ±90, longitude ±180, finite nonnegative accuracy |
| attachments | id:String; householdId:String; memberId:String?; kind:String; relativePath:String; mimeType:String | PK id; household FK and optional composite member FK CASCADE; path validated as attachments/name.extension; referenced files not yet managed |

Kotlin constructor defaults are not necessarily SQL DEFAULT clauses. Use the exported schema when constructing SQL fixtures. Current save replaces the household's complete child graph transactionally; partial snapshots are unsafe unless merged before save.

## 4. Answer encoding and identity

Current AnswerCodec encodes `Scalar("Yes")` as kind=`scalar`, value=`"Yes"` (a JSON string), and Multi values as a JSON array string. Text, dates, numbers and choices are currently scalar strings; questionnaire validation supplies meaning. Absence means unanswered. Blank scalar is treated as unanswered; No is a real answered value.

Target in-memory key is `(interviewId, sectionKey, fieldKey, memberId?)`. Household answers omit memberId. Reject a supplied memberId that belongs to a different household. Field keys are stable machine identifiers; labels may be reformatted without changing keys. Do not use display names or roster indices as identity.

In the proposed portable model, member name components live on the member object, and displayName is a derived convenience value that must match those components. Do not duplicate name/extension/alias fields in its generic answers array. A form renderer binds those structural fields to the member object; remaining demographics, such as registeredVoter, remain scoped answers. The current browser inventory has not yet made this normalization.

Current native demo identifiers such as HH-00452 are readable placeholders. Target persistent identity is UUID with displayCode kept separately. A migration from browser data must generate and retain an explicit old-ID→UUID mapping, including member identities that currently only have array positions. Never infer member identity from a name match.

## 5. Target schema extensions — not yet migrated

| Entity | Proposed fields / change | Purpose |
|---|---|---|
| Household | revision:Long; createdAt/updatedAt:UTC timestamp; archivedAt:nullable timestamp | Optimistic concurrency, history boundary, persistent archive |
| Member | firstName/middleName/lastName/extension/alias; sortOrder:Int | One authoritative name and stable user-visible roster order; displayName derived |
| Household head | headMemberId:nullable member FK in same household, plus provisional head name before roster | Avoid two independent identities; confirm respondent versus head semantics |
| Interview | consent:Unanswered/Yes/No; signedName?; signatureAttachmentId?; reviewedAt?; reviewedRevision? | Review tied to acknowledged record content; no duplicate signature source |
| Visit | visitedAt:timestamp; callbackLocalDateTime?; callbackTimeZone?; notes?; enumerator attribution | Ordered visit history and unambiguous appointments |
| Coordinate | source:device/manual; confirmedAt; addressAtCapture; notes? | Preserve capture provenance and detect address changes |
| Attachment | byteSize; sha256; createdAt; source; lifecycle state | Integrity, backup, orphan cleanup and file/database reconciliation |
| Enumerator profile | local profile ID; name; barangay code/label; photo reference | Local workspace configuration; separate from authenticated cloud identity |
| Reference catalogs | catalog ID/version plus barangay/evacuation items | Replace samples without changing existing historical labels silently |

These extensions require a Room v2+ migration and preservation tests. DEC-02 (social-protection scope) and DEC-03 (school classification) must be resolved before changing answer meanings. Keep older questionnaire versions readable.

## 6. Portable backup contract

The proposed offline format uses UTF-8 JSON, format=`tala-backup`, formatVersion=`1`, exportedAt UTC, prototype=true, and a records array. Each household is a full graph with members, interview, visits, scoped answers, optional location and embedded attachments. IDs remain UUIDs and pictures/signatures travel with the record as base64 content plus MIME type, byte size and SHA-256. The portable answer objects carry scalar/multi values directly, not the database's doubly encoded JSON strings.

Required restore sequence:

1. Enforce 50 MiB file cap before parsing; reject unsupported format/version and wrong primitive types.
2. Validate JSON Schema and semantic invariants below. Decode bounded attachments to a staging directory and verify size/hash and format.
3. Show counts, duplicate IDs, conflicts and the source export time. Default to importing new records only; existing IDs require a visible keep/replace decision. Do not invent new IDs for the same record silently.
4. Stage changes; transactionally write the complete chosen graphs with appropriate revision checks; finalize media safely. If a write or file operation fails, restore the prior state and retain a recoverable error.
5. Recompute completeness/counts and verify references. A schema-valid file can still contain invalid questionnaire answers; preserve incomplete drafts but do not label them Reviewed.

Semantic invariants that JSON Schema alone cannot establish: unique IDs across records; unique displayCode; unique section/field/member answer key; member ownership; attachment reference existence; head/signature/profile reference scope; field membership in questionnaire version; hidden-answer cleanup; strict calendar dates; actual base64 decode size; content hash; reviewedRevision matching content revision. The example uses an incomplete draft intentionally.

Browser `exportData` currently emits `{exportedAt, prototype, households}` without this versioned format and without stable member IDs. Treat it as a separate legacy import source requiring a converter, never as backup-v1 automatically.

## 7. Future cloud model — design boundary only

If synchronization is approved, mirror persistent UUID relationships in PostgreSQL and add barangay/workspace ownership, enumerator assignments, server revisions, change cursors/tombstones and an audit-event model. Keep database privileges and RLS policies in versioned migrations. Index foreign keys and ownership/filter columns used by real queries; assess plans before adding speculative indexes. Use private object storage with household-scoped authorization for media.

No cloud DDL is prescribed as ready to apply: assignment authority, role matrix, retention and conflict rules need confirmation first. The existing Supabase migration is a placeholder. Exposed relations require both grants and row-level policies. [Supabase RLS](https://supabase.com/docs/guides/database/postgres/row-level-security), [PostgreSQL constraints](https://www.postgresql.org/docs/current/ddl-constraints.html).

## 8. Migration acceptance

Start with the exported v1 fixture, migrate to the chosen next version, and verify every household/member/answer/visit/coordinate/attachment survives. Test foreign-key failures, rollback, archive/restore, duplicate IDs, altered display names, existing reviewed records, and interrupted media migration. Keep schema, questionnaire and portable format versions independent.
