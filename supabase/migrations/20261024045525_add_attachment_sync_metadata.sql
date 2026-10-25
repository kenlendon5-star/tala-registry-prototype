-- Make household identities owner-scoped so importing the same fictional backup under
-- two enumerator accounts cannot create a cross-owner primary-key collision.
alter table public.household_snapshots
  drop constraint household_snapshots_pkey;

alter table public.household_snapshots
  add primary key (owner_id, household_id);

create table public.household_attachment_objects (
  owner_id uuid not null references auth.users(id) on delete cascade,
  household_id uuid not null,
  attachment_id uuid not null,
  member_id uuid,
  kind text not null check (kind in ('household-photo', 'member-photo', 'signature')),
  mime_type text not null check (mime_type in ('image/jpeg', 'image/png', 'image/webp')),
  byte_size integer not null check (byte_size > 0 and byte_size <= 5242880),
  sha256 text not null check (sha256 ~ '^[0-9a-f]{64}$'),
  object_path text not null unique,
  updated_at timestamptz not null default now(),
  primary key (owner_id, attachment_id)
);

create index household_attachment_objects_household_idx
  on public.household_attachment_objects (owner_id, household_id);

alter table public.household_attachment_objects enable row level security;

grant select, insert, update, delete on public.household_attachment_objects to authenticated;

create policy "enumerators manage their own attachment metadata"
  on public.household_attachment_objects for all to authenticated
  using ((select auth.uid()) = owner_id)
  with check (
    (select auth.uid()) = owner_id
    and (storage.foldername(object_path))[1] = owner_id::text
  );

create policy "enumerators delete their attachment objects"
  on storage.objects for delete to authenticated
  using (
    bucket_id = 'registry-attachments'
    and (storage.foldername(name))[1] = (select auth.uid()::text)
  );
