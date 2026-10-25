create or replace function public.create_enumerator_profile()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  insert into public.enumerator_profiles (id, display_name, barangay)
  values (
    new.id,
    coalesce(new.raw_user_meta_data ->> 'display_name', ''),
    coalesce(new.raw_user_meta_data ->> 'barangay', '')
  )
  on conflict (id) do nothing;
  return new;
end;
$$;

revoke execute on function public.create_enumerator_profile() from public, anon, authenticated;
