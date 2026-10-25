-- Cloud sync for Tala's offline-first Android registry. Room remains the device source
-- of truth; this stores an authenticated enumerator's latest complete household snapshot.

create table public.enumerator_profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  display_name text not null default '',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.household_snapshots (
  household_id uuid primary key,
  owner_id uuid not null references auth.users(id) on delete cascade,
  payload jsonb not null check (jsonb_typeof(payload) = 'object'),
  revision bigint not null default 0 check (revision >= 0),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index household_snapshots_owner_updated_idx
  on public.household_snapshots (owner_id, updated_at desc);

alter table public.enumerator_profiles enable row level security;
alter table public.household_snapshots enable row level security;

grant select, insert, update on public.enumerator_profiles to authenticated;
grant select, insert, update on public.household_snapshots to authenticated;

create policy "enumerators manage their own profile"
  on public.enumerator_profiles for all to authenticated
  using ((select auth.uid()) = id)
  with check ((select auth.uid()) = id);

create policy "enumerators manage their own household snapshots"
  on public.household_snapshots for all to authenticated
  using ((select auth.uid()) = owner_id)
  with check ((select auth.uid()) = owner_id);

create function public.create_enumerator_profile()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  insert into public.enumerator_profiles (id)
  values (new.id)
  on conflict (id) do nothing;
  return new;
end;
$$;

revoke all on function public.create_enumerator_profile() from public;

create trigger create_enumerator_profile_after_signup
  after insert on auth.users
  for each row execute procedure public.create_enumerator_profile();

create function public.set_household_snapshot_metadata()
returns trigger
language plpgsql
set search_path = public
as $$
begin
  if tg_op = 'UPDATE' then
    if new.owner_id is distinct from old.owner_id then
      raise exception 'A household snapshot cannot change owners';
    end if;
    new.revision = old.revision + 1;
    new.created_at = old.created_at;
  end if;
  new.updated_at = now();
  return new;
end;
$$;

create trigger set_household_snapshot_metadata_before_write
  before insert or update on public.household_snapshots
  for each row execute procedure public.set_household_snapshot_metadata();

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
  'registry-attachments',
  'registry-attachments',
  false,
  5242880,
  array['image/jpeg', 'image/png', 'image/webp']
)
on conflict (id) do update
set public = excluded.public,
    file_size_limit = excluded.file_size_limit,
    allowed_mime_types = excluded.allowed_mime_types;

create policy "enumerators select their attachment objects"
  on storage.objects for select to authenticated
  using (
    bucket_id = 'registry-attachments'
    and (storage.foldername(name))[1] = (select auth.uid()::text)
  );

create policy "enumerators insert their attachment objects"
  on storage.objects for insert to authenticated
  with check (
    bucket_id = 'registry-attachments'
    and (storage.foldername(name))[1] = (select auth.uid()::text)
  );

create policy "enumerators update their attachment objects"
  on storage.objects for update to authenticated
  using (
    bucket_id = 'registry-attachments'
    and (storage.foldername(name))[1] = (select auth.uid()::text)
  )
  with check (
    bucket_id = 'registry-attachments'
    and (storage.foldername(name))[1] = (select auth.uid()::text)
  );
