BEGIN;
SET LOCAL lock_timeout='5s';
LOCK TABLE public.points_ledger IN SHARE ROW EXCLUSIVE MODE;
-- Wallet-wide, additive balances and provenance. Original ledger rows remain unchanged.
CREATE TABLE public.xr_wallet_accounts(
 user_id uuid PRIMARY KEY REFERENCES auth.users(id)ON DELETE CASCADE,
 earned bigint NOT NULL DEFAULT 0 CHECK(earned>=0),
 granted bigint NOT NULL DEFAULT 0 CHECK(granted>=0),
 unverified bigint NOT NULL DEFAULT 0 CHECK(unverified>=0),
 updated_at timestamptz NOT NULL DEFAULT clock_timestamp()
);
CREATE TABLE public.xr_wallet_allocations(
 ledger_id bigint PRIMARY KEY REFERENCES public.points_ledger(id)ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED,
 user_id uuid NOT NULL REFERENCES auth.users(id)ON DELETE CASCADE,
 earned_delta bigint NOT NULL,granted_delta bigint NOT NULL,unverified_delta bigint NOT NULL,
 reason text NOT NULL,refund_of bigint REFERENCES public.points_ledger(id),created_at timestamptz NOT NULL DEFAULT clock_timestamp()
);
CREATE UNIQUE INDEX xr_wallet_one_refund ON public.xr_wallet_allocations(refund_of)WHERE refund_of IS NOT NULL;
ALTER TABLE public.xr_wallet_accounts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.xr_wallet_allocations ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.xr_wallet_accounts,public.xr_wallet_allocations FROM PUBLIC,anon,authenticated;
GRANT ALL ON public.xr_wallet_accounts,public.xr_wallet_allocations TO service_role;
CREATE FUNCTION public.xr_wallet_credit_source(p public.points_ledger)RETURNS text LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path=public,pg_temp AS $$
BEGIN
 IF p.kind='admin_gift'AND EXISTS(SELECT 1 FROM public.gift_mailbox g WHERE g.id::text=p.meta->>'gift'AND g.recipient_id=p.user_id AND g.reward_kind='ap'AND g.amount=p.delta)THEN RETURN 'granted';END IF;
 IF p.kind IN('checkin','spin','quiz','mission','referral','referral_bonus')AND EXISTS(SELECT 1 FROM public.xp_events x WHERE x.user_id=p.user_id AND x.kind=p.kind AND x.amount=p.delta AND x.meta=p.meta)THEN RETURN 'earned';END IF;
 IF p.kind='match'AND EXISTS(SELECT 1 FROM public.xp_events x WHERE x.user_id=p.user_id AND x.kind='match'AND x.amount=p.delta AND x.meta=p.meta)AND NOT coalesce((p.meta->>'auto')::boolean,false)AND EXISTS(SELECT 1 FROM public.result_proofs r WHERE r.id::text=p.meta->>'proof'AND r.user_id=p.user_id AND r.status='approved'AND r.points=p.delta AND coalesce(r.admin_note,'')NOT LIKE 'auto-approved%')THEN RETURN 'earned';END IF;
 IF p.kind='season_pass'AND EXISTS(SELECT 1 FROM public.season_claims c JOIN public.season_rewards r ON r.level=c.level WHERE c.user_id=p.user_id AND c.level::text=p.meta->>'level'AND r.kind='ap'AND r.ap=p.delta)THEN RETURN 'earned';END IF;
 IF p.kind='voice_streak'AND EXISTS(SELECT 1 FROM public.xr_voice_streak_rewards r WHERE r.user_id=p.user_id AND r.milestone_day::text=p.meta->>'milestone_day'AND r.amount=p.delta)THEN RETURN 'earned';END IF;
 RETURN 'unverified';
END $$;
REVOKE ALL ON FUNCTION public.xr_wallet_credit_source(public.points_ledger)FROM PUBLIC,anon,authenticated;
-- One common write lock covers *all* ledger insert paths, including future code.
CREATE FUNCTION public.xr_wallet_ledger_insert()RETURNS trigger LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=public,pg_temp AS $$
DECLARE a public.xr_wallet_accounts;source text;e bigint:=0;g bigint:=0;u bigint:=0;cost bigint;rest bigint;original public.xr_wallet_allocations;original_row public.points_ledger;ref bigint;
BEGIN
 PERFORM pg_advisory_xact_lock(hashtext('ap:'||NEW.user_id::text));
 INSERT INTO public.xr_wallet_accounts(user_id)VALUES(NEW.user_id)ON CONFLICT DO NOTHING;
 SELECT * INTO a FROM public.xr_wallet_accounts WHERE user_id=NEW.user_id FOR UPDATE;
 IF NEW.delta>0 THEN
  source:=public.xr_wallet_credit_source(NEW);
  IF NEW.meta->>'wallet_refund_ledger'IS NOT NULL THEN
   ref:=(NEW.meta->>'wallet_refund_ledger')::bigint;
   SELECT * INTO original FROM public.xr_wallet_allocations WHERE ledger_id=ref AND user_id=NEW.user_id;
   SELECT * INTO original_row FROM public.points_ledger WHERE id=ref AND user_id=NEW.user_id;
   IF original.ledger_id IS NULL OR original_row.delta<>-NEW.delta OR EXISTS(SELECT 1 FROM public.xr_wallet_allocations WHERE refund_of=ref)THEN RAISE EXCEPTION 'Invalid or duplicate wallet refund' USING ERRCODE='23514';END IF;
   e:=-original.earned_delta;g:=-original.granted_delta;u:=-original.unverified_delta;source:='linked_refund';
  ELSIF source='earned'THEN e:=NEW.delta;
  ELSIF source='granted'THEN g:=NEW.delta;
  ELSE u:=NEW.delta;END IF;
 ELSIF NEW.delta<0 THEN
  cost:=-NEW.delta::bigint;
  IF a.earned+a.granted+a.unverified<cost THEN RAISE EXCEPTION 'Not enough AP; wallet debit rejected' USING ERRCODE='23514';END IF;
  source:='debit_granted_then_unverified_then_earned';
  IF coalesce(NEW.meta->>'wallet_scope','')='earned_only'THEN
   IF a.earned<cost THEN RAISE EXCEPTION 'Not enough verified earned AP' USING ERRCODE='23514';END IF;e:=-cost;source:='earned_only_debit';
  ELSE
   g:=-least(a.granted,cost);rest:=cost+g;u:=-least(a.unverified,rest);rest:=rest+u;e:=-rest;
  END IF;
 ELSE source:='zero';END IF;
 UPDATE public.xr_wallet_accounts SET earned=earned+e,granted=granted+g,unverified=unverified+u,updated_at=clock_timestamp()WHERE user_id=NEW.user_id;
 INSERT INTO public.xr_wallet_allocations(ledger_id,user_id,earned_delta,granted_delta,unverified_delta,reason,refund_of)VALUES(NEW.id,NEW.user_id,e,g,u,source,ref);
 RETURN NEW;
END $$;
REVOKE ALL ON FUNCTION public.xr_wallet_ledger_insert()FROM PUBLIC,anon,authenticated;
CREATE FUNCTION public.xr_wallet_immutable()RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path=public,pg_temp AS $$BEGIN
 -- Preserve auth account deletion cascades; ordinary wallet history is append-only.
 IF TG_OP='DELETE'AND NOT EXISTS(SELECT 1 FROM auth.users WHERE id=OLD.user_id)THEN RETURN OLD;END IF;
 RAISE EXCEPTION 'Wallet history is append-only; use a linked correction' USING ERRCODE='23514';END $$;
REVOKE ALL ON FUNCTION public.xr_wallet_immutable()FROM PUBLIC,anon,authenticated;
-- Migrate every historical row under a write lock. Preserve all balances and ownership.
DO $$DECLARE r public.points_ledger;a public.xr_wallet_accounts;s text;e bigint;g bigint;u bigint;c bigint;rest bigint;BEGIN
 FOR r IN SELECT * FROM public.points_ledger ORDER BY created_at,id LOOP
 INSERT INTO public.xr_wallet_accounts(user_id)VALUES(r.user_id)ON CONFLICT DO NOTHING;
 SELECT * INTO a FROM public.xr_wallet_accounts WHERE user_id=r.user_id FOR UPDATE;
 e:=0;g:=0;u:=0;
 IF r.delta>=0 THEN s:=public.xr_wallet_credit_source(r);IF s='earned'THEN e:=r.delta;ELSIF s='granted'THEN g:=r.delta;ELSE u:=r.delta;END IF;
 ELSE
 c:=-r.delta::bigint;
 IF a.earned+a.granted+a.unverified<c THEN RAISE EXCEPTION 'Historical negative balance at ledger%, owner review required',r.id;END IF;
 g:=-least(a.granted,c);rest:=c+g;u:=-least(a.unverified,rest);e:=-(rest+u);s:='historical_debit_granted_first';END IF;
 UPDATE public.xr_wallet_accounts SET earned=earned+e,granted=granted+g,unverified=unverified+u WHERE user_id=r.user_id;
 INSERT INTO public.xr_wallet_allocations(ledger_id,user_id,earned_delta,granted_delta,unverified_delta,reason)VALUES(r.id,r.user_id,e,g,u,s);
 END LOOP;
 IF EXISTS(SELECT 1 FROM public.xr_wallet_accounts acct WHERE acct.earned+acct.granted+acct.unverified<>(SELECT coalesce(sum(delta),0)FROM public.points_ledger p WHERE p.user_id=acct.user_id))THEN RAISE EXCEPTION 'Wallet reconciliation failed';END IF;
END $$;
CREATE TRIGGER xr_wallet_insert_guard BEFORE INSERT ON public.points_ledger FOR EACH ROW EXECUTE FUNCTION public.xr_wallet_ledger_insert();
CREATE TRIGGER xr_wallet_append_only BEFORE UPDATE OR DELETE ON public.points_ledger FOR EACH ROW EXECUTE FUNCTION public.xr_wallet_immutable();
CREATE FUNCTION public.xr_wallet_balance()RETURNS jsonb LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path=public,pg_temp AS $$DECLARE a public.xr_wallet_accounts;BEGIN
IF auth.uid()IS NULL THEN RETURN jsonb_build_object('ok',false,'error','Login required.');END IF;
SELECT * INTO a FROM public.xr_wallet_accounts WHERE user_id=auth.uid();
RETURN jsonb_build_object('ok',true,'balance',coalesce(a.earned,0)+coalesce(a.granted,0)+coalesce(a.unverified,0),'earned_spendable',coalesce(a.earned,0),'granted',coalesce(a.granted,0),'unverified',coalesce(a.unverified,0),'cash_value',false,'earned_only_certified',true);END $$;
REVOKE ALL ON FUNCTION public.xr_wallet_balance()FROM PUBLIC,anon;GRANT EXECUTE ON FUNCTION public.xr_wallet_balance()TO authenticated,service_role;

CREATE OR REPLACE FUNCTION public.redeem_reward(p_item bigint)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare v_uid uuid := auth.uid(); v_item rewards_catalog%rowtype; v_balance int; v_redemption bigint;
begin
  if v_uid is null then return jsonb_build_object('ok', false, 'error', 'Login required.'); end if;
  perform pg_advisory_xact_lock(hashtext('ap:'||v_uid::text));
  select * into v_item from rewards_catalog where id = p_item and active = true;
  if not found then return jsonb_build_object('ok', false, 'error', 'That reward is not available.'); end if;
  if v_item.cost < 0 then return jsonb_build_object('ok',false,'error','Invalid reward cost.');end if;
  if exists (select 1 from reward_redemptions where user_id = v_uid and item_id = p_item and status = 'pending') then
    return jsonb_build_object('ok', false, 'error', 'You already have a pending request for this reward.');
  end if;
  select coalesce(sum(delta), 0) into v_balance from points_ledger where user_id = v_uid;
  if v_balance < v_item.cost then
    return jsonb_build_object('ok', false, 'error', 'Not enough points yet - keep playing to earn more.');
  end if;
  insert into reward_redemptions (user_id,item_id,title,cost)values(v_uid,v_item.id,v_item.title,v_item.cost)returning id into v_redemption;
  if v_item.cost>0 then insert into points_ledger(user_id,delta,kind,meta)values(v_uid,-v_item.cost,'redeem',jsonb_build_object('item',v_item.title,'redemption',v_redemption));end if;
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
  perform pg_advisory_xact_lock(hashtext('ap:'||v_uid::text));
  select * into v_logo from public.squad_logos where id = p_logo and active = true;
  if not found then return jsonb_build_object('ok', false, 'error', 'That logo is not available.'); end if;
  if v_logo.cost_ap<0 then return jsonb_build_object('ok',false,'error','Invalid logo cost.');end if;
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
  select * into v_r from reward_redemptions where id = p_id and status = 'pending' for update;
  if not found then return jsonb_build_object('ok', false, 'error', 'No pending request with that id.'); end if;
  update reward_redemptions set status = p_action, note = nullif(trim(coalesce(p_note,'')),''), handled_at = now() where id = p_id;
  if p_action = 'rejected' then
    insert into points_ledger (user_id, delta, kind, meta) values (v_r.user_id, v_r.cost, 'refund', jsonb_build_object('item',v_r.title,'redemption',v_r.id,'wallet_refund_ledger',(select id from public.points_ledger where user_id=v_r.user_id and kind='redeem' and meta->>'redemption'=v_r.id::text order by id limit 1)));
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
  if a.cost_ap<0 then return jsonb_build_object('ok',false,'error','Invalid avatar cost.');end if;
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
  select * into v_r from public.result_proofs where id = p_id and status = 'pending' for update;
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
  perform 1 from public.player_game_stats where user_id=v_uid for update;
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
  perform pg_advisory_xact_lock(hashtext('ap:'||v_uid::text));
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
  perform pg_advisory_xact_lock(hashtext('ap:'||u::text));
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

CREATE OR REPLACE FUNCTION public._ap_bal(u uuid) RETURNS integer LANGUAGE sql STABLE SET search_path=public,pg_temp AS $$select coalesce(sum(delta),0)::int from public.points_ledger where user_id=u$$;
REVOKE ALL ON FUNCTION public._ap_bal(uuid)FROM PUBLIC,anon,authenticated;GRANT EXECUTE ON FUNCTION public._ap_bal(uuid)TO service_role;;
COMMIT;
