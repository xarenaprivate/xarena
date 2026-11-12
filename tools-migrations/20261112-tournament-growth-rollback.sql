BEGIN;
UPDATE public.xr_tournament_settings SET enabled=false;
UPDATE public.xr_match_access_settings SET enabled=false;
DROP TRIGGER xr_tournament_registration_meta ON public.registrations;
DROP TRIGGER xr_tournament_free ON public.tournaments;
CREATE OR REPLACE FUNCTION public.register_squad(p_id text, p_tournament text, p_squad text, p_captain text, p_uid text, p_ign text, p_phone text, p_proof text, p_client text, p_ip text)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare v_flags jsonb := coalesce((select value from site_settings where key='flags'),'{}'::jsonb); v_wait boolean := false; v_slots int; v_hold boolean := false; v_flag text := null; v_burst int; v_token text; v_user uuid := auth.uid();
begin
  if v_user is null then return jsonb_build_object('ok', false, 'error', 'To register a squad, first create a free XARENA account or log in (Account page).'); end if;
  -- Additive closed-event guard, serialized with existing capacity check.
  perform 1 from public.tournaments where id=p_tournament and reg_open=true and status in ('upcoming','live') for update;
  if not found then return jsonb_build_object('ok',false,'error','Registration is closed for this tournament.'); end if;
  if not exists (select 1 from profiles where id = v_user and phone_verified) then
    return jsonb_build_object('ok', false, 'error', 'Phone verification is pending — send your code from the Account page to the owner on Instagram @ud_speed. Registration opens as soon as you are verified.');
  end if;
  if p_uid !~ '^[0-9]{5,15}$' then return jsonb_build_object('ok', false, 'error', 'Free Fire UID must be 5-15 digits (numbers only).'); end if;
  if p_phone !~ '^[+0-9][0-9 -]{7,16}$' then return jsonb_build_object('ok', false, 'error', 'Enter a valid phone number.'); end if;
  if char_length(trim(p_squad)) < 2 or char_length(trim(p_captain)) < 2 or char_length(trim(coalesce(p_ign,''))) < 2 then
    return jsonb_build_object('ok', false, 'error', 'Squad name, captain and in-game name are all required.');
  end if;
  if exists (select 1 from registrations where tournament_id = p_tournament and user_id = v_user) then
    return jsonb_build_object('ok', false, 'error', 'Your squad is already registered in this tournament.');
  end if;
  if exists (select 1 from registrations where tournament_id = p_tournament and uid = p_uid) then
    return jsonb_build_object('ok', false, 'error', 'This UID is already registered. One registration per UID.');
  end if;
  if exists (select 1 from registrations where tournament_id = p_tournament and phone = p_phone) then
    return jsonb_build_object('ok', false, 'error', 'This phone number is already registered. One registration per number.');
  end if;
  select slots into v_slots from tournaments where id = p_tournament for update;
  if v_slots is not null and (select count(*) from registrations where tournament_id = p_tournament and status not in ('rejected','cancelled','declined','waitlist')) >= v_slots then
    if coalesce((v_flags->>'waitlist')::boolean,false) then v_wait := true;
    else return jsonb_build_object('ok', false, 'error', 'This tournament is full (' || v_slots || ' squads). Registrations are closed.'); end if;
  end if;
  if coalesce((v_flags->>'dup_check')::boolean,false) and exists (select 1 from registrations where tournament_id = p_tournament and lower(trim(ign)) = lower(trim(p_ign)) and status not in ('rejected','cancelled','declined')) then
    v_hold := true; v_flag := 'duplicate IGN in this tournament';
  end if;
  if p_proof is null then v_hold := true; v_flag := coalesce(v_flag || '; ', '') || 'no proof uploaded'; end if;
  select count(*) into v_burst from registrations
    where created_at > now() - interval '1 hour'
      and (client = p_client or (p_ip is not null and ip = p_ip::inet));
  if v_burst >= 3 then v_hold := true; v_flag := 'burst: same device/IP se 1 ghante me 3+ registrations'; end if;
  v_token := replace(gen_random_uuid()::text, '-', '');
  insert into registrations (id, tournament_id, squad, captain, uid, phone, status, ign, proof_path, client, ip, hold, flag, status_token, user_id)
  values (p_id, p_tournament, p_squad, p_captain, p_uid, p_phone, case when v_wait then 'waitlist' else 'pending' end, p_ign, p_proof, p_client, p_ip::inet, v_hold, v_flag, v_token, v_user);
  return jsonb_build_object('ok', true, 'id', p_id, 'hold', v_hold, 'status_token', v_token, 'waitlist', v_wait);
end $function$
;
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
end $function$
;
CREATE OR REPLACE FUNCTION public.match_checkin(p_match uuid)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare v_uid uuid := auth.uid(); v_m public.matches; v_count int;
begin
  if v_uid is null then return jsonb_build_object('ok', false, 'error', 'Log in first.'); end if;
  select * into v_m from public.matches where id = p_match;
  if v_m.id is null then return jsonb_build_object('ok', false, 'error', 'Match not found.'); end if;
  if v_m.starts_at is not null and (now() < v_m.starts_at - interval '60 minutes' or now() > v_m.starts_at + interval '15 minutes') then
    return jsonb_build_object('ok', false, 'error', 'Check-in opens 1 hour before the match and closes 15 minutes after start.');
  end if;
  if not exists (select 1 from public.registrations where tournament_id = v_m.tournament_id and user_id = v_uid) then
    return jsonb_build_object('ok', false, 'error', 'Check-in is for registered players of this tournament only.');
  end if;
  insert into public.match_checkins (match_id, user_id) values (p_match, v_uid) on conflict do nothing;
  select count(*) into v_count from public.match_checkins where match_id = p_match;
  return jsonb_build_object('ok', true, 'count', v_count, 'already', false);
end $function$
;
DROP FUNCTION public.xr_tournament_overview(text,text,integer);
DROP FUNCTION public.xr_match_checkin_guarded(uuid);
DROP FUNCTION public.xr_match_room_checked(uuid);
DROP FUNCTION public.xr_home_chat_preview(text);
DROP FUNCTION public.xr_chat_pin_admin(text,text,boolean);
DROP FUNCTION public.xr_tournament_season_summary(text);
DROP FUNCTION public.xr_prize_reveal_subscribe(text,boolean);
DROP FUNCTION public.xr_tournament_registration_sharing(text,boolean,boolean,boolean);
DROP FUNCTION public.xr_tournament_waitlist_join(text,text);
DROP FUNCTION public.xr_tournament_free_guard();
DROP FUNCTION public.xr_tournament_registration_guard();
DROP FUNCTION public.xr_match_access_admin(uuid,jsonb);
DROP FUNCTION public.xr_tournament_settings_admin(text,jsonb);
DROP FUNCTION public.xr_tournament_admin();
NOTIFY pgrst,'reload schema';
COMMIT;