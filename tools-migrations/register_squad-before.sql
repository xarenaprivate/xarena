CREATE OR REPLACE FUNCTION public.register_squad(p_id text, p_tournament text, p_squad text, p_captain text, p_uid text, p_ign text, p_phone text, p_proof text, p_client text, p_ip text)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare v_slots int; v_hold boolean := false; v_flag text := null; v_burst int; v_token text; v_user uuid := auth.uid();
begin
  if v_user is null then return jsonb_build_object('ok', false, 'error', 'To register a squad, first create a free XARENA account or log in (Account page).'); end if;
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
  if v_slots is not null and (select count(*) from registrations where tournament_id = p_tournament and status not in ('rejected','cancelled','declined')) >= v_slots then
    return jsonb_build_object('ok', false, 'error', 'This tournament is full (' || v_slots || ' squads). Registrations are closed.');
  end if;
  if p_proof is null then v_hold := true; v_flag := 'no proof uploaded'; end if;
  select count(*) into v_burst from registrations
    where created_at > now() - interval '1 hour'
      and (client = p_client or (p_ip is not null and ip = p_ip::inet));
  if v_burst >= 3 then v_hold := true; v_flag := 'burst: same device/IP se 1 ghante me 3+ registrations'; end if;
  v_token := replace(gen_random_uuid()::text, '-', '');
  insert into registrations (id, tournament_id, squad, captain, uid, phone, status, ign, proof_path, client, ip, hold, flag, status_token, user_id)
  values (p_id, p_tournament, p_squad, p_captain, p_uid, p_phone, 'pending', p_ign, p_proof, p_client, p_ip::inet, v_hold, v_flag, v_token, v_user);
  return jsonb_build_object('ok', true, 'id', p_id, 'hold', v_hold, 'status_token', v_token);
end $function$

