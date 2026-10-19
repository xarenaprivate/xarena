alter table public.rewards_catalog drop constraint if exists rewards_catalog_cosmetic_only;
drop trigger if exists rewards_catalog_guard_t on public.rewards_catalog;
drop function if exists public.rewards_catalog_guard();
alter table public.rewards_catalog alter column kind set default 'reward';
CREATE OR REPLACE FUNCTION public.challenge_create(p_target uuid, p_stake integer)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare me uuid := auth.uid(); a profiles%rowtype; b profiles%rowtype; xa int; xb int; v_id uuid; v_name text;
begin
  if me is null then return jsonb_build_object('ok',false,'error','Log in first.'); end if;
  if p_target is null or p_target = me then return jsonb_build_object('ok',false,'error','Pick another player.'); end if;
  if p_stake is null or p_stake < 10 or p_stake > 100 then return jsonb_build_object('ok',false,'error','Stake must be 10 to 100 AP.'); end if;
  perform pg_advisory_xact_lock(hashtext('ap:'||me::text));
  select * into a from profiles where id=me; select * into b from profiles where id=p_target;
  if b.id is null or coalesce(b.banned,false) or coalesce(a.banned,false) then return jsonb_build_object('ok',false,'error','Player not available.'); end if;
  if a.geo_district is null or a.geo_district is distinct from b.geo_district or a.geo_state is distinct from b.geo_state then
    return jsonb_build_object('ok',false,'error','You can only challenge players in your own district.'); end if;
  select coalesce(xp,0) into xa from player_game_stats where user_id=me; select coalesce(xp,0) into xb from player_game_stats where user_id=p_target;
  if coalesce(xb,0) <= coalesce(xa,0) or xb > coalesce(xa,0) + 100 then return jsonb_build_object('ok',false,'error','Challenges are for rivals up to 100 EXP above you.'); end if;
  if (select count(*) from challenges where challenger=me and status in ('pending','active')) >= 3 then return jsonb_build_object('ok',false,'error','You already have 3 open challenges.'); end if;
  if exists (select 1 from challenges where status in ('pending','active') and ((challenger=me and challenged=p_target) or (challenger=p_target and challenged=me))) then
    return jsonb_build_object('ok',false,'error','There is already an open challenge between you two.'); end if;
  if _ap_bal(me) < p_stake then return jsonb_build_object('ok',false,'error','Not enough AP. You have '||_ap_bal(me)||'.'); end if;
  insert into challenges (challenger, challenged, stake, district, state) values (me, p_target, p_stake, a.geo_district, a.geo_state) returning id into v_id;
  insert into points_ledger (user_id, delta, kind, meta) values (me, -p_stake, 'challenge_escrow', jsonb_build_object('challenge', v_id));
  v_name := coalesce(nullif(a.ign,''), a.username, 'A player');
  perform notify(p_target, 'challenge', v_name || ' challenged you for ' || a.geo_district || ' King', 'Stake ' || p_stake || ' AP each. Open XARENA to accept or decline (expires in 24h).', 'leaderboard.html', 'ch:' || v_id);
  return jsonb_build_object('ok',true,'id',v_id,'balance',_ap_bal(me));
end $function$

;
notify pgrst, 'reload schema';
