-- Multi-device DM keys (v2 envelope encryption). Additive only.
-- Applied 2026-10-19 via the Supabase management API. UNDO at the bottom.

create table if not exists public.dm_device_keys (
  user_id uuid not null references auth.users(id) on delete cascade,
  device_id text not null,
  public_key jsonb not null,
  key_id text not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (user_id, device_id)
);

alter table public.dm_device_keys enable row level security;

create policy dmdk_read on public.dm_device_keys for select using (true);
create policy dmdk_own on public.dm_device_keys for all
  using (user_id = auth.uid()) with check (user_id = auth.uid());

grant select, insert, update, delete on public.dm_device_keys to anon, authenticated;

-- UNDO:
-- drop table if exists public.dm_device_keys;
