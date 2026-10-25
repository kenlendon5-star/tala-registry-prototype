create function public.sync_household_snapshot(
  p_household_id uuid,
  p_payload jsonb,
  p_expected_revision bigint default null,
  p_force boolean default false
)
returns table (new_revision bigint)
language plpgsql
security invoker
set search_path = ''
as $$
declare
  current_revision bigint;
begin
  if (select auth.uid()) is null then
    raise exception 'Authentication is required';
  end if;

  if jsonb_typeof(p_payload) is distinct from 'object' then
    raise exception 'Snapshot payload must be an object';
  end if;

  select snapshot.revision
    into current_revision
    from public.household_snapshots as snapshot
   where snapshot.owner_id = (select auth.uid())
     and snapshot.household_id = p_household_id
   for update;

  if not found then
    if p_expected_revision is not null and not p_force then
      raise exception using errcode = '40001', message = 'TALA_SYNC_CONFLICT';
    end if;
    insert into public.household_snapshots (owner_id, household_id, payload)
    values ((select auth.uid()), p_household_id, p_payload)
    returning revision into current_revision;
  else
    if not p_force and (p_expected_revision is null or current_revision <> p_expected_revision) then
      raise exception using errcode = '40001', message = 'TALA_SYNC_CONFLICT';
    end if;
    update public.household_snapshots
       set payload = p_payload
     where owner_id = (select auth.uid())
       and household_id = p_household_id
    returning revision into current_revision;
  end if;

  return query select current_revision;
end;
$$;

revoke all on function public.sync_household_snapshot(uuid, jsonb, bigint, boolean) from public;
grant execute on function public.sync_household_snapshot(uuid, jsonb, bigint, boolean) to authenticated;
