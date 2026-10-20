-- Flagged register_squad: identical to the previous version while flags.waitlist and flags.dup_check are false.
-- Previous definition saved in register_squad-before.sql (undo = run that file).
CREATE OR REPLACE FUNCTION public.register_squad(p_id text, p_tournament text, p_squad text, p_captain text, p_uid text, p_ign text, p_phone text, p_proof text, p_client text, p_ip text)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare v_flags jsonb := coalesce((select value from site_settings where key='flags'),'{}'::jsonb); v_wait boolean := false; v_slots int; v_hold boolean := false; v_flag text := null; v_burst int; v_token text; v_user uuid := auth.uid();
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
end $function$;

-- waitlist rows must not count toward slots
create or replace function public.tournament_reg_counts() returns jsonb
language sql stable security definer set search_path to 'public' as $$
  select coalesce(jsonb_agg(jsonb_build_object('tournament_id', t.id, 'registered',
      (select count(*) from registrations r where r.tournament_id=t.id and r.status not in ('rejected','cancelled','declined','waitlist')), 'slots', t.slots,
      'waitlist', (select count(*) from registrations r where r.tournament_id=t.id and r.status='waitlist')) order by t.sort, t.created_at), '[]'::jsonb)
  from tournaments t
$$;
create or replace function public.admin_promote_waitlist(p_tournament text) returns jsonb
language plpgsql security definer set search_path to 'public' as $$
declare v_id text; v_slots int; v_n int;
begin
  if not is_admin() then return jsonb_build_object('ok',false,'error','Admin only.'); end if;
  select slots into v_slots from tournaments where id=p_tournament for update;
  select count(*) into v_n from registrations where tournament_id=p_tournament and status not in ('rejected','cancelled','declined','waitlist');
  if v_slots is not null and v_n >= v_slots then return jsonb_build_object('ok',false,'error','No free slot.'); end if;
  select id into v_id from registrations where tournament_id=p_tournament and status='waitlist' order by created_at limit 1;
  if v_id is null then return jsonb_build_object('ok',false,'error','Waitlist is empty.'); end if;
  update registrations set status='pending' where id=v_id;
  return jsonb_build_object('ok',true,'id',v_id);
end $$;
grant execute on function public.admin_promote_waitlist(text) to authenticated;
notify pgrst, 'reload schema';
