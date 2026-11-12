BEGIN;
CREATE TABLE public.xr_live_growth_settings(singleton boolean PRIMARY KEY DEFAULT true CHECK(singleton),enabled boolean NOT NULL DEFAULT false,video_id text CHECK(video_id IS NULL OR video_id~'^[A-Za-z0-9_-]{11}$'),scheduled_start_at timestamptz,host_user_id uuid REFERENCES public.profiles(id),welcome_body text CHECK(welcome_body IS NULL OR char_length(welcome_body)BETWEEN 1 AND 200),welcome_published boolean NOT NULL DEFAULT false,welcome_updated_at timestamptz);
INSERT INTO public.xr_live_growth_settings(singleton)VALUES(true);
CREATE TABLE public.xr_live_polls(id uuid PRIMARY KEY DEFAULT gen_random_uuid(),video_id text NOT NULL,question text NOT NULL CHECK(char_length(question)BETWEEN 3 AND 160),options jsonb NOT NULL CHECK(jsonb_typeof(options)='array'AND jsonb_array_length(options)BETWEEN 2 AND 6),created_at timestamptz NOT NULL DEFAULT clock_timestamp(),closes_at timestamptz NOT NULL,status text NOT NULL DEFAULT 'open'CHECK(status IN('open','closed')),created_by uuid NOT NULL REFERENCES public.profiles(id));
CREATE TABLE public.xr_live_poll_votes(poll_id uuid NOT NULL REFERENCES public.xr_live_polls(id)ON DELETE CASCADE,user_id uuid NOT NULL REFERENCES public.profiles(id)ON DELETE CASCADE,choice integer NOT NULL CHECK(choice BETWEEN 0 AND 5),created_at timestamptz NOT NULL DEFAULT clock_timestamp(),PRIMARY KEY(poll_id,user_id));

ALTER TABLE public.xr_live_growth_settings ENABLE ROW LEVEL SECURITY;REVOKE ALL ON public.xr_live_growth_settings FROM PUBLIC,anon,authenticated;
ALTER TABLE public.xr_live_polls ENABLE ROW LEVEL SECURITY;REVOKE ALL ON public.xr_live_polls FROM PUBLIC,anon,authenticated;
ALTER TABLE public.xr_live_poll_votes ENABLE ROW LEVEL SECURITY;REVOKE ALL ON public.xr_live_poll_votes FROM PUBLIC,anon,authenticated;
CREATE OR REPLACE FUNCTION public.xr_live_growth_admin(p_patch jsonb DEFAULT '{}'::jsonb)RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path TO pg_catalog,public AS $fn$
DECLARE cfg public.xr_live_growth_settings;live jsonb;BEGIN
 IF auth.uid()IS NULL OR NOT public.is_admin()THEN RAISE EXCEPTION 'Admin only.'USING ERRCODE='42501';END IF;
 IF jsonb_typeof(p_patch)IS DISTINCT FROM 'object'OR EXISTS(SELECT 1 FROM jsonb_object_keys(p_patch)k WHERE k NOT IN('enabled','video_id','scheduled_start_at','host_user_id','welcome_body','welcome_published'))THEN RETURN jsonb_build_object('ok',false,'error','Invalid live setting.');END IF;
 SELECT value INTO live FROM public.site_settings WHERE key='youtube_live';
 SELECT * INTO cfg FROM public.xr_live_growth_settings WHERE singleton FOR UPDATE;SELECT * INTO cfg FROM jsonb_populate_record(cfg,p_patch);
 IF cfg.enabled AND(NOT coalesce((live->>'active')::boolean,false)OR cfg.video_id IS DISTINCT FROM live->>'video_id')THEN RETURN jsonb_build_object('ok',false,'error','Bind settings to the current active video.');END IF;
 IF cfg.host_user_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM public.profiles WHERE id=cfg.host_user_id AND NOT coalesce(banned,false))THEN RETURN jsonb_build_object('ok',false,'error','Host unavailable.');END IF;
 IF cfg.welcome_published AND nullif(btrim(cfg.welcome_body),'')IS NULL THEN RETURN jsonb_build_object('ok',false,'error','Write the welcome pin first.');END IF;
 IF cfg.welcome_body~'[[:cntrl:]<>]'THEN RETURN jsonb_build_object('ok',false,'error','Plain text welcome required.');END IF;
 IF p_patch?'welcome_body'OR p_patch?'welcome_published'THEN cfg.welcome_updated_at:=clock_timestamp();END IF;
 UPDATE public.xr_live_growth_settings SET enabled=cfg.enabled,video_id=cfg.video_id,scheduled_start_at=cfg.scheduled_start_at,host_user_id=cfg.host_user_id,welcome_body=cfg.welcome_body,welcome_published=cfg.welcome_published,welcome_updated_at=cfg.welcome_updated_at WHERE singleton;
 RETURN jsonb_build_object('ok',true,'settings',to_jsonb(cfg)-'singleton');EXCEPTION WHEN check_violation OR foreign_key_violation OR not_null_violation OR invalid_text_representation OR invalid_datetime_format THEN RETURN jsonb_build_object('ok',false,'error','Invalid live settings.');END
$fn$;
REVOKE ALL ON FUNCTION public.xr_live_growth_admin(jsonb)FROM PUBLIC,anon,authenticated;GRANT EXECUTE ON FUNCTION public.xr_live_growth_admin(jsonb)TO authenticated;
CREATE OR REPLACE FUNCTION public.xr_live_poll_admin(p_question text,p_options jsonb,p_closes_at timestamptz)RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path TO pg_catalog,public AS $fn$
DECLARE cfg public.xr_live_growth_settings;live jsonb;poll_id uuid;BEGIN
 IF auth.uid()IS NULL OR NOT public.is_admin()THEN RAISE EXCEPTION 'Admin only.'USING ERRCODE='42501';END IF;
 SELECT * INTO cfg FROM public.xr_live_growth_settings WHERE singleton FOR UPDATE;SELECT value INTO live FROM public.site_settings WHERE key='youtube_live';
 IF cfg.video_id IS NULL OR cfg.video_id IS DISTINCT FROM live->>'video_id'OR NOT coalesce((live->>'active')::boolean,false)THEN RETURN jsonb_build_object('ok',false,'error','Bind settings to the current active video.');END IF;
 IF p_question IS NULL OR char_length(btrim(p_question))NOT BETWEEN 3 AND 160 OR p_question~'[[:cntrl:]<>]'OR jsonb_typeof(p_options)IS DISTINCT FROM 'array'THEN RETURN jsonb_build_object('ok',false,'error','Invalid poll.');END IF;
 IF jsonb_array_length(p_options)NOT BETWEEN 2 AND 6 OR EXISTS(SELECT 1 FROM jsonb_array_elements(p_options)x WHERE jsonb_typeof(x)<>'string'OR char_length(btrim(x#>>'{}'))NOT BETWEEN 1 AND 80 OR(x#>>'{}')~'[[:cntrl:]<>]')OR(SELECT count(DISTINCT lower(btrim(x)))FROM jsonb_array_elements_text(p_options)x)<>jsonb_array_length(p_options)THEN RETURN jsonb_build_object('ok',false,'error','Use 2 to 6 distinct short choices.');END IF;
 IF p_closes_at IS NULL OR p_closes_at<=clock_timestamp()OR p_closes_at>clock_timestamp()+interval '24 hours'THEN RETURN jsonb_build_object('ok',false,'error','Poll close must be within the next 24 hours.');END IF;
 UPDATE public.xr_live_polls SET status='closed'WHERE video_id=cfg.video_id AND status='open';
 INSERT INTO public.xr_live_polls(video_id,question,options,closes_at,created_by)VALUES(cfg.video_id,btrim(p_question),p_options,p_closes_at,auth.uid())RETURNING id INTO poll_id;RETURN jsonb_build_object('ok',true,'poll_id',poll_id);END
$fn$;
REVOKE ALL ON FUNCTION public.xr_live_poll_admin(text,jsonb,timestamptz)FROM PUBLIC,anon,authenticated;GRANT EXECUTE ON FUNCTION public.xr_live_poll_admin(text,jsonb,timestamptz)TO authenticated;
CREATE OR REPLACE FUNCTION public.xr_live_poll_vote(p_poll uuid,p_choice integer)RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path TO pg_catalog,public AS $fn$
DECLARE u uuid:=public.xr_sf_actor();cfg public.xr_live_growth_settings;poll public.xr_live_polls;live jsonb;BEGIN
 SELECT * INTO cfg FROM public.xr_live_growth_settings WHERE singleton FOR SHARE;SELECT value INTO live FROM public.site_settings WHERE key='youtube_live';SELECT * INTO poll FROM public.xr_live_polls WHERE id=p_poll FOR SHARE;
 IF NOT coalesce(cfg.enabled,false)OR NOT coalesce((live->>'active')::boolean,false)OR cfg.video_id IS DISTINCT FROM live->>'video_id'OR poll.id IS NULL OR poll.video_id IS DISTINCT FROM cfg.video_id OR poll.status<>'open'OR poll.closes_at<=clock_timestamp()THEN RETURN jsonb_build_object('ok',false,'error','Poll closed.');END IF;
 IF p_choice IS NULL OR p_choice<0 OR p_choice>=jsonb_array_length(poll.options)THEN RETURN jsonb_build_object('ok',false,'error','Invalid choice.');END IF;
 INSERT INTO public.xr_live_poll_votes(poll_id,user_id,choice)VALUES(p_poll,u,p_choice)ON CONFLICT DO NOTHING;RETURN jsonb_build_object('ok',true,'already_voted',NOT FOUND);END
$fn$;
REVOKE ALL ON FUNCTION public.xr_live_poll_vote(uuid,integer)FROM PUBLIC,anon,authenticated;GRANT EXECUTE ON FUNCTION public.xr_live_poll_vote(uuid,integer)TO authenticated;
CREATE OR REPLACE FUNCTION public.xr_live_follow_set(p_on boolean)RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path TO pg_catalog,public AS $fn$
DECLARE u uuid:=public.xr_sf_actor();cfg public.xr_live_growth_settings;live jsonb;BEGIN
 SELECT * INTO cfg FROM public.xr_live_growth_settings WHERE singleton FOR SHARE;SELECT value INTO live FROM public.site_settings WHERE key='youtube_live';
 IF NOT cfg.enabled OR cfg.video_id IS DISTINCT FROM live->>'video_id'OR NOT coalesce((live->>'active')::boolean,false)OR cfg.host_user_id IS NULL OR cfg.host_user_id=u OR NOT EXISTS(SELECT 1 FROM public.profiles WHERE id=cfg.host_user_id AND NOT coalesce(banned,false))OR public.is_blocked(u,cfg.host_user_id)THEN RETURN jsonb_build_object('ok',false,'error','Follow unavailable.');END IF;
 IF coalesce(p_on,false)THEN INSERT INTO public.follows(follower_id,followee_id)VALUES(u,cfg.host_user_id)ON CONFLICT DO NOTHING;ELSE DELETE FROM public.follows WHERE follower_id=u AND followee_id=cfg.host_user_id;END IF;
 RETURN jsonb_build_object('ok',true,'following',coalesce(p_on,false));END
$fn$;
REVOKE ALL ON FUNCTION public.xr_live_follow_set(boolean)FROM PUBLIC,anon,authenticated;GRANT EXECUTE ON FUNCTION public.xr_live_follow_set(boolean)TO authenticated;
CREATE OR REPLACE FUNCTION public.xr_live_overview()RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path TO pg_catalog,public AS $fn$
DECLARE u uuid:=auth.uid();cfg public.xr_live_growth_settings;live jsonb;on_now boolean;stream jsonb;pin jsonb;poll jsonb;follow jsonb;squads jsonb:='[]'::jsonb;BEGIN
 IF u IS NOT NULL THEN PERFORM public.xr_sf_actor();END IF;SELECT * INTO cfg FROM public.xr_live_growth_settings WHERE singleton;SELECT value INTO live FROM public.site_settings WHERE key='youtube_live';
 on_now:=coalesce(cfg.enabled AND coalesce((live->>'active')::boolean,false)AND cfg.video_id=live->>'video_id',false);
 IF coalesce((live->>'active')::boolean,false)AND nullif(live->>'video_id','')IS NOT NULL THEN stream:=jsonb_build_object('title',live->>'title','video_id',live->>'video_id','tournament_id',live->>'tournament_id','scheduled_start_at',CASE WHEN on_now THEN cfg.scheduled_start_at END,'scheduled_start_source',CASE WHEN on_now AND cfg.scheduled_start_at IS NOT NULL THEN 'admin_set'END);
 SELECT coalesce(jsonb_agg(z.squad ORDER BY z.name),'[]'::jsonb)INTO squads FROM(
 SELECT r.squad name,jsonb_build_object('name',r.squad,'igl',NULL,'captain_name',nullif(btrim(r.ign),''),'members',coalesce((SELECT jsonb_agg(z.member ORDER BY z.no)FROM(
 SELECT rp.player_no no,jsonb_build_object('name',left(rp.ign,80),'avatar_url',NULL)member FROM public.xr_registration_players rp WHERE rp.registration_id=r.id AND nullif(btrim(rp.ign),'')IS NOT NULL ORDER BY rp.player_no LIMIT 4)z),jsonb_build_array(jsonb_build_object('name',coalesce(nullif(btrim(r.ign),''),nullif(btrim(r.captain),''),'Player'),'avatar_url',pr.avatar_url))))squad
 FROM public.registrations r LEFT JOIN public.profiles pr ON pr.id=r.user_id WHERE r.tournament_id=live->>'tournament_id'AND r.status NOT IN('rejected','cancelled','declined','waitlist')AND NOT coalesce(r.hold,false)AND NOT coalesce(pr.banned,false)AND(u IS NULL OR NOT public.is_blocked(u,pr.id))ORDER BY r.squad,r.id LIMIT 12)z;
 END IF;
 IF on_now THEN
 IF cfg.welcome_published AND public._flag('live_thread')THEN pin:=jsonb_build_object('author','XARENA','body',cfg.welcome_body,'updated_at',cfg.welcome_updated_at);END IF;
 SELECT jsonb_build_object('id',p.id,'question',p.question,'options',p.options,'counts',(SELECT jsonb_agg((SELECT count(*)FROM public.xr_live_poll_votes v JOIN public.profiles vp ON vp.id=v.user_id WHERE v.poll_id=p.id AND v.choice=opt.i AND NOT coalesce(vp.banned,false))ORDER BY opt.i)FROM generate_series(0,jsonb_array_length(p.options)-1)opt(i)),'my_vote',(SELECT choice FROM public.xr_live_poll_votes WHERE poll_id=p.id AND user_id=u),'closes_at',p.closes_at,'status',CASE WHEN p.closes_at>statement_timestamp()AND p.status='open'THEN 'open'ELSE 'closed'END)INTO poll FROM public.xr_live_polls p WHERE p.video_id=cfg.video_id ORDER BY p.created_at DESC,p.id DESC LIMIT 1;
 IF cfg.host_user_id IS NOT NULL AND EXISTS(SELECT 1 FROM public.profiles WHERE id=cfg.host_user_id AND NOT coalesce(banned,false))AND(u IS NULL OR NOT public.is_blocked(u,cfg.host_user_id))THEN follow:=jsonb_build_object('following',EXISTS(SELECT 1 FROM public.follows WHERE follower_id=u AND followee_id=cfg.host_user_id),'followers',(SELECT count(*)FROM public.follows fo JOIN public.profiles fp ON fp.id=fo.follower_id WHERE fo.followee_id=cfg.host_user_id AND NOT coalesce(fp.banned,false)),'host_id',cfg.host_user_id);END IF;
 END IF;
 RETURN jsonb_build_object('ok',true,'growth_enabled',on_now,'stream',stream,'viewer_count',NULL,'viewer_count_source',NULL,'squads',squads,'welcome_pin',pin,'prediction',NULL,'drop',NULL,'poll',poll,'gifts',CASE WHEN on_now THEN jsonb_build_object('enabled',false,'catalog',jsonb_build_array(jsonb_build_object('key','rose','name','Rose','coin_cost',10),jsonb_build_object('key','fire','name','Fire','coin_cost',50),jsonb_build_object('key','crown','name','Crown','coin_cost',500)),'disabled_reason','earned_balance_and_shared_debit_safety_not_available')ELSE NULL END,'early_viewer',NULL,'watch_streak',NULL,'follow',follow,'notification_delivery_available',false,'cache_seconds',30,'count_refresh_seconds',300);END
$fn$;
REVOKE ALL ON FUNCTION public.xr_live_overview()FROM PUBLIC,anon,authenticated;GRANT EXECUTE ON FUNCTION public.xr_live_overview()TO authenticated,anon;
NOTIFY pgrst,'reload schema';
COMMIT;