BEGIN;
CREATE TABLE public.xr_profile_growth_settings(singleton boolean PRIMARY KEY DEFAULT true CHECK(singleton),enabled boolean NOT NULL DEFAULT false);
INSERT INTO public.xr_profile_growth_settings(singleton)VALUES(true);
ALTER TABLE public.xr_profile_growth_settings ENABLE ROW LEVEL SECURITY;REVOKE ALL ON public.xr_profile_growth_settings FROM PUBLIC,anon,authenticated;
CREATE OR REPLACE FUNCTION public.xr_profile_growth_admin(p_enabled boolean)RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path TO pg_catalog,public AS $fn$
BEGIN IF auth.uid()IS NULL OR NOT public.is_admin()THEN RAISE EXCEPTION 'Admin only.'USING ERRCODE='42501';END IF;IF p_enabled IS NULL THEN RETURN jsonb_build_object('ok',false,'error','Enabled state required.');END IF;UPDATE public.xr_profile_growth_settings SET enabled=p_enabled WHERE singleton;RETURN jsonb_build_object('ok',true,'growth_enabled',p_enabled);END $fn$;
REVOKE ALL ON FUNCTION public.xr_profile_growth_admin(boolean)FROM PUBLIC,anon,authenticated;GRANT EXECUTE ON FUNCTION public.xr_profile_growth_admin(boolean)TO authenticated;
CREATE OR REPLACE FUNCTION public.xr_profile_growth()RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path TO pg_catalog,public AS $fn$
DECLARE u uuid:=public.xr_sf_actor();pr public.profiles;st public.player_game_stats;on_now boolean;earned jsonb;nextbadge jsonb;complete jsonb;missing jsonb;claimed boolean;today date:=(statement_timestamp()AT TIME ZONE 'Asia/Kolkata')::date;nextstreak integer;reward integer;vfriends bigint;clips bigint;clip_shed boolean;
BEGIN
 SELECT enabled INTO on_now FROM public.xr_profile_growth_settings WHERE singleton;SELECT * INTO pr FROM public.profiles WHERE id=u;SELECT * INTO st FROM public.player_game_stats WHERE user_id=u;
 SELECT coalesce(jsonb_agg(jsonb_build_object('key',badge,'name',replace(badge,'_',' '))ORDER BY earned_at,badge),'[]'::jsonb)INTO earned FROM(SELECT badge,earned_at FROM public.badges WHERE user_id=u ORDER BY earned_at,badge LIMIT 100)b;
 claimed:=coalesce(st.last_checkin=today,false);nextstreak:=CASE WHEN claimed THEN coalesce(st.streak,1)WHEN st.last_checkin=today-1 THEN coalesce(st.streak,0)+1 ELSE 1 END;reward:=5+least(nextstreak,10);
 IF on_now THEN
 SELECT count(*)INTO vfriends FROM public.profiles f WHERE f.id<>u AND f.verified AND NOT coalesce(f.banned,false)AND NOT public.is_blocked(u,f.id)AND coalesce(f.show_activity,false)AND coalesce(pr.show_activity,false)AND EXISTS(SELECT 1 FROM public.friends a WHERE a.user_id=u AND a.friend_id=f.id)AND EXISTS(SELECT 1 FROM public.friends b WHERE b.user_id=f.id AND b.friend_id=u);
 SELECT coalesce(jsonb_agg(key ORDER BY ord),'[]'::jsonb)INTO missing FROM(VALUES(1,'username',nullif(btrim(pr.username),'')IS NULL),(2,'ign',nullif(btrim(pr.ign),'')IS NULL),(3,'avatar',nullif(btrim(pr.avatar_url),'')IS NULL),(4,'city',nullif(btrim(pr.geo_district),'')IS NULL))v(ord,key,is_missing)WHERE is_missing;
 complete:=jsonb_build_object('percent',(4-jsonb_array_length(missing))*25,'missing',missing,'basis','username_ign_avatar_city','creator_apply_gate',false);
 SELECT jsonb_build_object('name','streak '||threshold,'key','streak_'||threshold,'requirement',threshold||' consecutive IST daily check-ins','progress',CASE WHEN st.last_checkin>=today-1 THEN least(coalesce(st.streak,0),threshold)ELSE 0 END,'target',threshold)INTO nextbadge FROM(VALUES(3),(7),(30))t(threshold)WHERE NOT EXISTS(SELECT 1 FROM public.badges WHERE user_id=u AND badge='streak_'||threshold)ORDER BY threshold LIMIT 1;
 END IF;
 clip_shed:=coalesce((public.xr_ops_status()->>'clips_shed')::boolean,false);
 IF NOT clip_shed THEN SELECT count(*)INTO clips FROM public.feed_posts WHERE user_id=u AND kind='clip'AND status='approved';END IF;
 RETURN jsonb_build_object('ok',true,'growth_enabled',coalesce(on_now,false),'verified_friends_count',vfriends,'city_rank',NULL,'badges',jsonb_build_object('earned',earned,'next',nextbadge),'friends_comparison','[]'::jsonb,'profile_completeness',complete,'avatar_xp_reward',NULL,'first_win_reward',NULL,'daily_tasks',NULL,'top_player_today',NULL,'my_clips_count',clips,'clips_temporarily_unavailable',clip_shed,'streak_reward_coins',CASE WHEN on_now THEN reward END,'streak_reward_already_claimed',claimed,'streak_reward_unit','AP','streak_reward_source','existing_daily_checkin','streak_days',CASE WHEN st.last_checkin>=today-1 THEN coalesce(st.streak,0)ELSE 0 END,'share_referral_attribution_available',false,'notification_delivery_available',false,'cache_seconds',30,'count_refresh_seconds',300);
END $fn$;
REVOKE ALL ON FUNCTION public.xr_profile_growth()FROM PUBLIC,anon,authenticated;GRANT EXECUTE ON FUNCTION public.xr_profile_growth()TO authenticated;
NOTIFY pgrst,'reload schema';
COMMIT;
