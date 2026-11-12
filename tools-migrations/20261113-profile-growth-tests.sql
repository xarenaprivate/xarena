BEGIN;
DO $test$
DECLARE own uuid;a uuid;j jsonb;points_before bigint;badges_before bigint;
BEGIN
 SELECT user_id INTO own FROM public.admins LIMIT 1;SELECT id INTO a FROM public.profiles WHERE id<>own AND NOT coalesce(banned,false)ORDER BY id LIMIT 1;SELECT coalesce(sum(delta),0)INTO points_before FROM public.points_ledger;SELECT count(*)INTO badges_before FROM public.badges;
 PERFORM set_config('request.jwt.claim.sub','',true);BEGIN PERFORM public.xr_profile_growth();RAISE EXCEPTION 'Anonymous allowed';EXCEPTION WHEN insufficient_privilege THEN NULL;END;
 PERFORM set_config('request.jwt.claim.sub',a::text,true);j:=public.xr_profile_growth();ASSERT NOT(j->>'growth_enabled')::boolean,'Dark';ASSERT j->'avatar_xp_reward'='null'::jsonb AND j->'first_win_reward'='null'::jsonb AND j->'daily_tasks'='null'::jsonb,'No fake rewards';ASSERT j->'city_rank'='null'::jsonb AND j->'top_player_today'='null'::jsonb,'No ambiguous rank';ASSERT NOT(j->>'share_referral_attribution_available')::boolean,'No share attribution';
 BEGIN PERFORM public.xr_profile_growth_admin(true);RAISE EXCEPTION 'Player admin bypass';EXCEPTION WHEN insufficient_privilege THEN NULL;END;
 PERFORM set_config('request.jwt.claim.sub',own::text,true);PERFORM public.xr_profile_growth_admin(true);PERFORM set_config('request.jwt.claim.sub',a::text,true);
 j:=public.xr_profile_growth();ASSERT(j->>'growth_enabled')::boolean,'Fixture enabled';ASSERT(j->'profile_completeness'->>'percent')::integer BETWEEN 0 AND 100,'Completeness bounded';ASSERT(j->>'streak_reward_coins')::integer BETWEEN 6 AND 15,'Existing checkin formula';ASSERT jsonb_typeof(j->'badges'->'earned')='array','Actual badges';ASSERT NOT(j::text LIKE '%ff_uid%'OR j::text LIKE '%phone_code%'OR j::text LIKE '%geo_lat%'),'No private identifiers';
 PERFORM set_config('request.jwt.claim.sub',own::text,true);UPDATE public.xr_ops_settings SET clips_shed=true WHERE singleton;PERFORM set_config('request.jwt.claim.sub',a::text,true);j:=public.xr_profile_growth();ASSERT j->'my_clips_count'='null'::jsonb AND(j->>'clips_temporarily_unavailable')::boolean,'Clip shed honored';
 PERFORM set_config('request.jwt.claim.sub',own::text,true);UPDATE public.profiles SET banned=true WHERE id=a;PERFORM set_config('request.jwt.claim.sub',a::text,true);BEGIN PERFORM public.xr_profile_growth();RAISE EXCEPTION 'Banned allowed';EXCEPTION WHEN insufficient_privilege THEN NULL;END;
 ASSERT(SELECT coalesce(sum(delta),0)FROM public.points_ledger)=points_before,'No ledger mint';ASSERT(SELECT count(*)FROM public.badges)=badges_before,'No badge seed';
END $test$;

SELECT 'profile growth tests passed; fixtures reverted' result;ROLLBACK;