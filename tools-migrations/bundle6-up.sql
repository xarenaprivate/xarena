-- Bundle 6 (additive): helper-admin role, RPC-only. Existing is_admin() policies untouched.
create table if not exists public.helpers(user_id uuid primary key, note text, created_at timestamptz not null default now());
alter table public.helpers enable row level security;
drop policy if exists "helpers admin" on public.helpers;
create policy "helpers admin" on public.helpers for all to authenticated using (is_admin()) with check (is_admin());
create or replace function public.is_helper() returns boolean language sql stable security definer set search_path to 'public' as $$
  select exists (select 1 from helpers where user_id = auth.uid()) or is_admin() $$;
grant execute on function public.is_helper() to authenticated;

create or replace function public.admin_add_helper(p_username text) returns jsonb
language plpgsql security definer set search_path to 'public' as $$
declare v uuid;
begin
  if not is_admin() then return jsonb_build_object('ok',false,'error','Admin only.'); end if;
  select id into v from profiles where lower(username)=lower(trim(p_username));
  if v is null then return jsonb_build_object('ok',false,'error','Player not found.'); end if;
  insert into helpers(user_id) values (v) on conflict do nothing;
  insert into audit_log(actor,action,details) values ('admin','helper_add',jsonb_build_object('user',v));
  return jsonb_build_object('ok',true);
end $$;
grant execute on function public.admin_add_helper(text) to authenticated;
create or replace function public.admin_remove_helper(p_user uuid) returns jsonb
language plpgsql security definer set search_path to 'public' as $$
begin
  if not is_admin() then return jsonb_build_object('ok',false,'error','Admin only.'); end if;
  delete from helpers where user_id=p_user;
  insert into audit_log(actor,action,details) values ('admin','helper_remove',jsonb_build_object('user',p_user));
  return jsonb_build_object('ok',true);
end $$;
grant execute on function public.admin_remove_helper(uuid) to authenticated;
create or replace function public.admin_list_helpers() returns jsonb
language sql stable security definer set search_path to 'public' as $$
  select case when is_admin() then jsonb_build_object('ok',true,'helpers',coalesce((select jsonb_agg(jsonb_build_object('user_id',h.user_id,'username',p.username,'ign',p.ign)) from helpers h left join profiles p on p.id=h.user_id),'[]'::jsonb)) else jsonb_build_object('ok',false) end $$;
grant execute on function public.admin_list_helpers() to authenticated;

create or replace function public.helper_matches() returns jsonb
language plpgsql stable security definer set search_path to 'public' as $$
begin
  if not is_helper() then return jsonb_build_object('ok',false,'error','Helpers only.'); end if;
  return jsonb_build_object('ok',true,'matches',coalesce((select jsonb_agg(jsonb_build_object('id',id,'title',title,'tournament_id',tournament_id,'starts_at',starts_at,'room_set',(room_id is not null)) order by starts_at nulls last) from matches),'[]'::jsonb));
end $$;
grant execute on function public.helper_matches() to authenticated;
create or replace function public.helper_set_room(p_match uuid, p_room_id text, p_room_pass text) returns jsonb
language plpgsql security definer set search_path to 'public' as $$
begin
  if not is_helper() then return jsonb_build_object('ok',false,'error','Helpers only.'); end if;
  if char_length(coalesce(p_room_id,'')) not between 3 and 30 or char_length(coalesce(p_room_pass,'')) > 30 then return jsonb_build_object('ok',false,'error','Room ID 3-30 chars, password up to 30.'); end if;
  update matches set room_id=trim(p_room_id), room_pass=nullif(trim(p_room_pass),'') where id=p_match;
  insert into audit_log(actor,action,details) values (coalesce(auth.uid()::text,'helper'),'helper_set_room',jsonb_build_object('match',p_match));
  return jsonb_build_object('ok',found);
end $$;
grant execute on function public.helper_set_room(uuid,text,text) to authenticated;
create or replace function public.helper_add_result(p_match uuid, p_player text, p_squad text, p_kills int, p_placement int, p_points int) returns jsonb
language plpgsql security definer set search_path to 'public' as $$
begin
  if not is_helper() then return jsonb_build_object('ok',false,'error','Helpers only.'); end if;
  if char_length(trim(coalesce(p_player,''))) < 1 or p_kills < 0 or p_kills > 60 or p_placement not between 1 and 48 or p_points not between 0 and 500 then return jsonb_build_object('ok',false,'error','Check the values.'); end if;
  if not exists (select 1 from matches where id=p_match) then return jsonb_build_object('ok',false,'error','Match not found.'); end if;
  insert into lb_entries(match_id,player_name,squad,kills,placement,points) values (p_match,trim(p_player),nullif(trim(coalesce(p_squad,'')),''),p_kills,p_placement,p_points);
  insert into audit_log(actor,action,details) values (coalesce(auth.uid()::text,'helper'),'helper_add_result',jsonb_build_object('match',p_match,'player',p_player));
  return jsonb_build_object('ok',true);
end $$;
grant execute on function public.helper_add_result(uuid,text,text,int,int,int) to authenticated;
create or replace function public.helper_registrations(p_tournament text) returns jsonb
language plpgsql stable security definer set search_path to 'public' as $$
begin
  if not is_helper() then return jsonb_build_object('ok',false,'error','Helpers only.'); end if;
  return jsonb_build_object('ok',true,'registrations',coalesce((select jsonb_agg(jsonb_build_object('squad',squad,'ign',ign,'status',status,'verified',verified,'hold',hold,'created_at',created_at) order by created_at) from registrations where tournament_id=p_tournament),'[]'::jsonb));
end $$;
grant execute on function public.helper_registrations(text) to authenticated;
notify pgrst, 'reload schema';
