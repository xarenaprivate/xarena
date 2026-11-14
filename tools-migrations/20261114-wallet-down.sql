BEGIN;
SET LOCAL lock_timeout='5s';
LOCK TABLE public.points_ledger IN SHARE ROW EXCLUSIVE MODE;
DROP TRIGGER IF EXISTS xr_wallet_insert_guard ON public.points_ledger;
DROP TRIGGER IF EXISTS xr_wallet_append_only ON public.points_ledger;
DROP FUNCTION IF EXISTS public.xr_wallet_balance();
DROP FUNCTION IF EXISTS public.xr_wallet_ledger_insert();
DROP FUNCTION IF EXISTS public.xr_wallet_immutable();
DROP FUNCTION IF EXISTS public.xr_wallet_credit_source(public.points_ledger);
DROP TABLE IF EXISTS public.xr_wallet_allocations;
DROP TABLE IF EXISTS public.xr_wallet_accounts;
CREATE OR REPLACE FUNCTION public.redeem_reward(p_item bigint)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare v_uid uuid := auth.uid(); v_item rewards_catalog%rowtype; v_balance int;
begin
  if v_uid is null then return jsonb_build_object('ok', false, 'error', 'Login required.'); end if;
  select * into v_item from rewards_catalog where id = p_item and active = true;
  if not found then return jsonb_build_object('ok', false, 'error', 'That reward is not available.'); end if;
  if exists (select 1 from reward_redemptions where user_id = v_uid and item_id = p_item and status = 'pending') then
    return jsonb_build_object('ok', false, 'error', 'You already have a pending request for this reward.');
  end if;
  select coalesce(sum(delta), 0) into v_balance from points_ledger where user_id = v_uid;
  if v_balance < v_item.cost then
    return jsonb_build_object('ok', false, 'error', 'Not enough points yet - keep playing to earn more.');
  end if;
  insert into points_ledger (user_id, delta, kind, meta) values (v_uid, -v_item.cost, 'redeem', jsonb_build_object('item', v_item.title));
  insert into reward_redemptions (user_id, item_id, title, cost) values (v_uid, v_item.id, v_item.title, v_item.cost);
  return jsonb_build_object('ok', true, 'balance', v_balance - v_item.cost);
end $function$
;

CREATE OR REPLACE FUNCTION public.buy_squad_logo(p_logo uuid)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare v_uid uuid := auth.uid(); v_logo public.squad_logos%rowtype; v_balance int;
begin
  if v_uid is null then return jsonb_build_object('ok', false, 'error', 'Log in first.'); end if;
  select * into v_logo from public.squad_logos where id = p_logo and active = true;
  if not found then return jsonb_build_object('ok', false, 'error', 'That logo is not available.'); end if;
  if exists (select 1 from public.squad_logo_owners where user_id = v_uid and logo_id = p_logo) then
    select coalesce(sum(delta), 0) into v_balance from public.points_ledger where user_id = v_uid;
    return jsonb_build_object('ok', true, 'already', true, 'balance', v_balance);
  end if;
  select coalesce(sum(delta), 0) into v_balance from public.points_ledger where user_id = v_uid;
  if v_balance < v_logo.cost_ap then
    return jsonb_build_object('ok', false, 'error', 'Not enough AP - this logo costs ' || v_logo.cost_ap || ' AP.', 'balance', v_balance);
  end if;
  insert into public.points_ledger (user_id, delta, kind, meta) values (v_uid, -v_logo.cost_ap, 'squad_logo', jsonb_build_object('logo', v_logo.title));
  insert into public.squad_logo_owners (user_id, logo_id) values (v_uid, p_logo);
  return jsonb_build_object('ok', true, 'balance', v_balance - v_logo.cost_ap);
end $function$
;

CREATE OR REPLACE FUNCTION public.admin_review_redemption(p_id bigint, p_action text, p_note text DEFAULT NULL::text)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare v_r reward_redemptions%rowtype;
begin
  if not (select is_admin()) then return jsonb_build_object('ok', false, 'error', 'admin only'); end if;
  if p_action not in ('fulfilled','rejected') then return jsonb_build_object('ok', false, 'error', 'Use fulfilled or rejected.'); end if;
  select * into v_r from reward_redemptions where id = p_id and status = 'pending';
  if not found then return jsonb_build_object('ok', false, 'error', 'No pending request with that id.'); end if;
  update reward_redemptions set status = p_action, note = nullif(trim(coalesce(p_note,'')),''), handled_at = now() where id = p_id;
  if p_action = 'rejected' then
    insert into points_ledger (user_id, delta, kind, meta) values (v_r.user_id, v_r.cost, 'refund', jsonb_build_object('item', v_r.title));
  end if;
  return jsonb_build_object('ok', true);
end $function$
;

CREATE OR REPLACE FUNCTION public.buy_avatar(p_id text)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare me uuid := auth.uid(); a avatar_catalog%rowtype; v_bal int;
begin
  if me is null then return jsonb_build_object('ok',false,'error','Log in first.'); end if;
  select * into a from avatar_catalog where id = p_id and active;
  if not found then return jsonb_build_object('ok',false,'error','That avatar is not available.'); end if;
  perform pg_advisory_xact_lock(hashtext('ap:'||me::text));
  if a.cost_ap = 0 or exists (select 1 from avatar_owners where user_id=me and avatar_id=p_id) then
    return jsonb_build_object('ok',true,'already',true,'balance',_ap_bal(me)); end if;
  v_bal := _ap_bal(me);
  if v_bal < a.cost_ap then return jsonb_build_object('ok',false,'error','Not enough AP - this avatar costs '||a.cost_ap||' AP. You have '||v_bal||'.','balance',v_bal); end if;
  insert into points_ledger(user_id,delta,kind,meta) values (me,-a.cost_ap,'avatar',jsonb_build_object('avatar',a.title));
  insert into avatar_owners(user_id,avatar_id) values (me,p_id);
  return jsonb_build_object('ok',true,'balance',v_bal - a.cost_ap);
end $function$
;

CREATE OR REPLACE FUNCTION public.admin_review_proof(p_id bigint, p_action text, p_points integer, p_note text)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare v_r public.result_proofs; v_pts int := least(greatest(coalesce(p_points,0),0),500);
begin
  if not (select is_admin()) then return jsonb_build_object('ok', false, 'error', 'Admin only.'); end if;
  select * into v_r from public.result_proofs where id = p_id and status = 'pending';
  if v_r.id is null then return jsonb_build_object('ok', false, 'error', 'Proof not found or already handled.'); end if;
  if p_action = 'approve' then
    update public.result_proofs set status = 'approved', points = v_pts, admin_note = p_note, handled_at = now() where id = p_id;
    if v_pts > 0 then perform public.award_xp(v_r.user_id, 'match', v_pts, jsonb_build_object('proof', p_id, 'note', coalesce(p_note,''))); end if;
    return jsonb_build_object('ok', true, 'awarded', v_pts);
  elsif p_action = 'reject' then
    update public.result_proofs set status = 'rejected', admin_note = p_note, handled_at = now() where id = p_id;
    return jsonb_build_object('ok', true, 'awarded', 0);
  end if;
  return jsonb_build_object('ok', false, 'error', 'Action must be approve or reject.');
end $function$
;

CREATE OR REPLACE FUNCTION public.spin_daily()
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare v_uid uuid := auth.uid();
  v_today date := (now() at time zone 'Asia/Kolkata')::date;
  v_roll int; v_seg int; v_prize_xp int; v_badge text;
begin
  if v_uid is null then return jsonb_build_object('ok', false, 'error', 'Login required.'); end if;
  insert into player_game_stats (user_id) values (v_uid) on conflict (user_id) do nothing;
  if exists (select 1 from player_game_stats where user_id = v_uid and last_spin = v_today) then
    return jsonb_build_object('ok', false, 'error', 'Already spun today. Come back tomorrow!');
  end if;
  v_roll := floor(random()*100)::int;
  if      v_roll < 30 then v_seg := 0; v_prize_xp := 5;
  elsif v_roll < 55 then v_seg := 1; v_prize_xp := 10;
  elsif v_roll < 75 then v_seg := 2; v_prize_xp := 15;
  elsif v_roll < 87 then v_seg := 3; v_prize_xp := 20;
  elsif v_roll < 95 then v_seg := 4; v_prize_xp := 30;
  elsif v_roll < 99 then v_seg := 5; v_prize_xp := 50;
  else  v_seg := 6; v_badge := 'lucky_spinner'; v_prize_xp := 25;
  end if;
  update player_game_stats set last_spin = v_today, spins_total = spins_total + 1, updated_at = now()
    where user_id = v_uid;
  perform award_xp(v_uid, 'spin', v_prize_xp, jsonb_build_object('segment', v_seg));
  if v_badge is not null then perform award_badge_internal(v_uid, v_badge); end if;
  return jsonb_build_object('ok', true, 'segment', v_seg, 'xp', v_prize_xp, 'badge', v_badge);
end $function$
;

CREATE OR REPLACE FUNCTION public.daily_mission_claim()
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare v_uid uuid := auth.uid(); v_m jsonb; v_streak int; v_bonus int := 0;
begin
  if v_uid is null then return jsonb_build_object('ok', false, 'error', 'Log in first.'); end if;
  v_m := public.daily_mission();
  if not (v_m->>'done')::boolean then return jsonb_build_object('ok', false, 'error', 'Mission not complete yet - check in, spin and answer a quiz today.'); end if;
  if (v_m->>'claimed')::boolean then return jsonb_build_object('ok', false, 'error', 'Already claimed today. New mission tomorrow.'); end if;
  insert into public.mission_claims (user_id, day) values (v_uid, (now() at time zone 'Asia/Kolkata')::date);
  perform public.award_xp(v_uid, 'mission', 15, jsonb_build_object('day', (now() at time zone 'Asia/Kolkata')::date));
  -- streak milestone bonuses, once each
  select streak into v_streak from public.player_game_stats where user_id = v_uid;
  if v_streak >= 7 and not exists (select 1 from public.xp_events where user_id = v_uid and kind = 'mission' and meta->>'milestone' = '7') then
    perform public.award_xp(v_uid, 'mission', 25, jsonb_build_object('milestone', '7')); v_bonus := v_bonus + 25;
  end if;
  if v_streak >= 30 and not exists (select 1 from public.xp_events where user_id = v_uid and kind = 'mission' and meta->>'milestone' = '30') then
    perform public.award_xp(v_uid, 'mission', 100, jsonb_build_object('milestone', '30')); v_bonus := v_bonus + 100;
  end if;
  return jsonb_build_object('ok', true, 'reward', 15, 'streak_bonus', v_bonus);
end $function$
;

CREATE OR REPLACE FUNCTION public.claim_mission_v2(p_kind text)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare u uuid := auth.uid(); d date := (now() at time zone 'Asia/Kolkata')::date; s jsonb; it jsonb;
begin
  if u is null then return jsonb_build_object('ok',false,'error','Log in first.'); end if;
  s := daily_missions_v2();
  select x into it from jsonb_array_elements(s->'missions') x where x->>'kind'=p_kind;
  if it is null then return jsonb_build_object('ok',false,'error','Unknown mission.'); end if;
  if not (it->>'done')::bool then return jsonb_build_object('ok',false,'error','Mission not complete yet.'); end if;
  if (it->>'claimed')::bool then return jsonb_build_object('ok',false,'error','Already claimed today.'); end if;
  insert into mission_claims_v2(user_id,day,kind) values (u,d,p_kind);
  perform award_xp(u,'mission',(it->>'reward')::int,jsonb_build_object('day',d,'mission',p_kind));
  return jsonb_build_object('ok',true,'reward',(it->>'reward')::int);
end $function$
;
CREATE OR REPLACE FUNCTION public._ap_bal(u uuid)
 RETURNS integer
 LANGUAGE sql
 STABLE
AS $function$ select coalesce(sum(delta),0)::int from points_ledger where user_id=u $function$
;
GRANT EXECUTE ON FUNCTION public._ap_bal(uuid)TO PUBLIC,anon,authenticated;
COMMIT;
