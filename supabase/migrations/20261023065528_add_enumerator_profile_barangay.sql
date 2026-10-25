alter table public.enumerator_profiles
  add column if not exists barangay text not null default '';
