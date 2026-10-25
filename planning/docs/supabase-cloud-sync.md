# Supabase Cloud Sync

## Status

The hosted Supabase project `lylsjfmxhrynwgpdttim` has the tracked cloud migrations applied. Tala remains usable offline without an account. Reports and Profile expose optional email/password authentication and explicit household sync.

Room remains the application source of truth. Before network I/O, Reports writes every selected household to the Room `sync_outbox`; startup and later manual sync attempts retry queued work idempotently. Snapshots include household, member, interview, visit, and location data. Every referenced picture or signature must upload successfully before the outbox entry is cleared. Owner-scoped revision checks refuse blind overwrites; the UI reports a conflict and requires separate confirmation before replacing a newer cloud copy.

## Schema

- `public.enumerator_profiles`: one owner-scoped profile for every Auth account, created by an `auth.users` trigger.
- `public.household_snapshots`: latest full JSON snapshot keyed by owner plus stable household UUID, with revision and server timestamps.
- `public.household_attachment_objects`: owner-scoped attachment metadata containing object path, household/member link, MIME type, byte size and SHA-256.
- `registry-attachments`: private Storage bucket; objects use `<auth-user-id>/<household-id>/<attachment-id>-<hash>.<extension>`.

All exposed application tables have RLS enabled. Policies require `auth.uid()` to equal the row owner. Storage policies enforce the same owner from the first path segment for select, insert, update and delete. Never put a service-role or secret key in Android.

## Android Contract

1. Save to Room before attempting cloud work; a network failure never rolls back local data.
2. Manual sync stores an outbox operation before network I/O and retries it at startup or the next sync action. Automatic enqueue after every local edit remains a later enhancement.
3. Use only the project URL and publishable key from ignored `android-native/supabase.properties`.
   Android accepts only an HTTPS endpoint and disables cleartext application traffic.
4. A missing local attachment fails the sync instead of silently producing an incomplete cloud backup.
5. Attachment metadata stores private object paths, SHA-256, MIME type and size, never public URLs.
6. The `sync_household_snapshot` function updates only the server revision last acknowledged by that device. Ordinary retry never overwrites a newer remote revision.
7. Reports exposes a separately confirmed **Replace Cloud Copy** action for deliberate local-wins conflict resolution.

## Account Flow

1. A stored Supabase session restores automatically; otherwise the local registry remains available while the account screen presents sign-in and account creation.
2. New enumerators provide name, barangay, email, an eight-character minimum password, and matching confirmation. Passwords are never stored in Room or DataStore.
3. Email confirmation returns through the allow-listed `tala://auth-callback` deep link and supports resend.
4. Sign-up preserves the local enumerator profile while confirmation is pending. Sign-in immediately hydrates name and barangay from the owner profile.
5. Profile edits remain local; household outbox sync never silently overwrites the cloud profile row.
6. Configure custom SMTP before production/UAT with arbitrary enumerator addresses.

## Operations

```powershell
rtk proxy supabase migration list --linked
rtk proxy supabase db advisors --linked
rtk proxy supabase db query --linked "select tablename, rowsecurity from pg_tables where schemaname = 'public';"
```

Create schema changes with `rtk proxy supabase migration new <name>`, review them, apply them, then rerun migration status and advisors.
