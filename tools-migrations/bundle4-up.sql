-- Bundle 4 (additive/safety): Room ID only for active registrations (not rejected/cancelled/waitlist), reminder opt-out, team finder role.
create table if not exists public.match_reminder_optouts(user_id uuid not null, tournament_id text not null, created_at timestamptz default now(), primary key(user_id, tournament_id));
alter table public.match_reminder_optouts enable row level security;
drop policy if exists "optout own" on public.match_reminder_optouts;
create policy "optout own" on public.match_reminder_optouts for all to authenticated using (user_id = auth.uid()) with check (user_id = auth.uid());
create or replace function public.set_match_reminder(p_tournament text, p_on boolean) returns jsonb
language plpgsql security definer set search_path to 'public' as $f$
declare me uuid := auth.uid();
begin
  if me is null then return jsonb_build_object('ok',false,'error','Login required.'); end if;
  if p_on then delete from match_reminder_optouts where user_id=me and tournament_id=p_tournament;
  else insert into match_reminder_optouts(user_id,tournament_id) values (me,p_tournament) on conflict do nothing; end if;
  return jsonb_build_object('ok',true,'reminders_on',p_on);
end $f$;
grant execute on function public.set_match_reminder(text,boolean) to authenticated;
create or replace function public.my_match_reminders() returns jsonb
language sql stable security definer set search_path to 'public' as $f$
  select jsonb_build_object('ok', auth.uid() is not null, 'opted_out', coalesce((select jsonb_agg(tournament_id) from match_reminder_optouts where user_id=auth.uid()),'[]'::jsonb))
$f$;
grant execute on function public.my_match_reminders() to authenticated;
alter table public.lfg_lobbies add column if not exists role_wanted text check (role_wanted is null or role_wanted in ('IGL','Rusher','Sniper','Support','Grenadier','Any'));
create or replace function public.lfg_set_role(p_lobby bigint, p_role text) returns jsonb
language plpgsql security definer set search_path to 'public' as $f$
begin
  if auth.uid() is null then return jsonb_build_object('ok',false,'error','Login required.'); end if;
  if p_role not in ('IGL','Rusher','Sniper','Support','Grenadier','Any') then return jsonb_build_object('ok',false,'error','Pick a valid role.'); end if;
  update lfg_lobbies set role_wanted=p_role where id=p_lobby and host=auth.uid();
  return jsonb_build_object('ok', found);
end $f$;
grant execute on function public.lfg_set_role(bigint,text) to authenticated;
CREATE OR REPLACE FUNCTION public.lfg_list()
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare v_uid uuid := auth.uid();
begin
  -- stale lobbies die after 12 hours
  update public.lfg_lobbies set status = 'closed' where status in ('open','full') and created_at < now() - interval '12 hours';
  return jsonb_build_object('ok', true, 'lobbies', coalesce((
    select jsonb_agg(jsonb_build_object(
      'id', l.id, 'mode', l.mode, 'map', l.map, 'rank_min', l.rank_min, 'rank_max', l.rank_max,
      'mic', l.mic, 'note', l.note, 'role_wanted', l.role_wanted, 'slots', l.slots, 'status', l.status, 'created_at', l.created_at,
      'host', coalesce(hp.ign, hp.username),
      'mine', (v_uid is not null and exists (select 1 from public.lfg_members lm where lm.lobby_id = l.id and lm.user_id = v_uid)),
      'host_mine', (v_uid is not null and l.host = v_uid),
      'members', (select jsonb_agg(jsonb_build_object('name', coalesce(mp.ign, mp.username), 'me', (v_uid is not null and lm.user_id = v_uid)))
                  from public.lfg_members lm left join public.profiles mp on mp.id = lm.user_id where lm.lobby_id = l.id)
    ) order by l.created_at desc)
    from public.lfg_lobbies l left join public.profiles hp on hp.id = l.host
    where l.status in ('open','full')), '[]'::jsonb));
end $function$;
CREATE OR REPLACE FUNCTION public.match_room(p_match uuid)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare v_uid uuid := auth.uid(); v_m public.matches; v_reg boolean;
begin
  if v_uid is null then return jsonb_build_object('ok', false, 'error', 'Log in first.'); end if;
  select * into v_m from public.matches where id = p_match;
  if v_m.id is null then return jsonb_build_object('ok', false, 'error', 'Match not found.'); end if;
  if v_m.room_id is null then return jsonb_build_object('ok', false, 'error', 'Room details are not posted yet.'); end if;
  if v_m.starts_at is not null and now() < v_m.starts_at - make_interval(mins => v_m.reveal_minutes) then
    return jsonb_build_object('ok', false, 'error', 'Room ID unlocks ' || v_m.reveal_minutes || ' minutes before the match (' || to_char(v_m.starts_at at time zone 'Asia/Kolkata', 'DD Mon HH24:MI') || ' IST start).');
  end if;
  select exists (select 1 from public.registrations r where r.tournament_id = v_m.tournament_id and r.status not in ('rejected','cancelled','declined','waitlist') and r.user_id is not null and (r.user_id = v_uid or r.user_id in (select sm2.user_id from public.squad_members sm1 join public.squad_members sm2 on sm2.squad_id = sm1.squad_id where sm1.user_id = v_uid))) into v_reg;
  if not v_reg then return jsonb_build_object('ok', false, 'error', 'Room details are for registered players of this tournament only.'); end if;
  return jsonb_build_object('ok', true, 'room_id', v_m.room_id, 'room_pass', v_m.room_pass, 'title', v_m.title, 'starts_at', v_m.starts_at);
end $function$;
CREATE OR REPLACE FUNCTION public.match_reminder_tick()
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare v_m record; v_r record; v_n int := 0;
begin
  for v_m in select * from public.matches where status = 'scheduled' and starts_at is not null
      and starts_at between now() + interval '50 minutes' and now() + interval '65 minutes' loop
    for v_r in select rg.user_id from public.registrations rg where rg.tournament_id = v_m.tournament_id and rg.status not in ('rejected','cancelled','declined','waitlist') and rg.user_id is not null and not exists (select 1 from public.match_reminder_optouts o where o.user_id = rg.user_id and o.tournament_id = rg.tournament_id) loop
      perform public.notify(v_r.user_id, 'match_reminder', 'Match in ~1 hour: ' || v_m.title,
        'Room ID unlocks ' || v_m.reveal_minutes || ' minutes before start. Check in on the Tournaments page when it opens.',
        'tournaments.html', 'match:' || v_m.id::text);
      v_n := v_n + 1;
    end loop;
  end loop;
  return jsonb_build_object('ok', true, 'sent', v_n);
end $function$;
CREATE OR REPLACE FUNCTION public.match_reminder_15_tick()
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare m record; r record; n int := 0;
begin
  for m in select * from matches where status='scheduled' and starts_at is not null and starts_at between now() + interval '10 minutes' and now() + interval '20 minutes' loop
    for r in select distinct rg.user_id from registrations rg where rg.tournament_id=m.tournament_id and rg.status not in ('rejected','cancelled','declined','waitlist') and rg.user_id is not null and not exists (select 1 from match_reminder_optouts o where o.user_id=rg.user_id and o.tournament_id=rg.tournament_id) loop
      perform notify(r.user_id,'match_reminder','Match starts in ~15 minutes: '||m.title,'Get ready and open XARENA to check in.','tournaments.html','match15:'||m.id::text); n := n+1;
    end loop;
  end loop;
  return jsonb_build_object('ok',true,'sent',n);
end $function$;
notify pgrst, 'reload schema';
