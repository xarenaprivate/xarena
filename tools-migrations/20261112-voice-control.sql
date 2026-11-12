BEGIN;
CREATE TABLE public.xr_voice_settings(singleton boolean PRIMARY KEY DEFAULT true CHECK(singleton),enabled boolean NOT NULL DEFAULT false,default_seat_cap integer NOT NULL DEFAULT 20 CHECK(default_seat_cap BETWEEN 2 AND 50),first_speaker_limit integer NOT NULL DEFAULT 5 CHECK(first_speaker_limit BETWEEN 0 AND 20),streak_days integer NOT NULL DEFAULT 4 CHECK(streak_days BETWEEN 2 AND 365),streak_reward_coins integer NOT NULL DEFAULT 0 CHECK(streak_reward_coins BETWEEN 0 AND 10000),rewards_enabled boolean NOT NULL DEFAULT false,min_attendance_seconds integer NOT NULL DEFAULT 300 CHECK(min_attendance_seconds BETWEEN 60 AND 7200));
INSERT INTO public.xr_voice_settings(singleton)VALUES(true);
CREATE TABLE public.xr_voice_rooms(id uuid PRIMARY KEY DEFAULT gen_random_uuid(),title text NOT NULL CHECK(char_length(title)BETWEEN 1 AND 80),topic text CHECK(char_length(topic)<=200),next_topic text CHECK(char_length(next_topic)<=200),status text NOT NULL DEFAULT 'scheduled'CHECK(status IN('scheduled','live','ended')),seat_cap integer NOT NULL DEFAULT 20 CHECK(seat_cap BETWEEN 2 AND 50),starts_at timestamptz NOT NULL,ends_at timestamptz,ended_at timestamptz,peak_count integer NOT NULL DEFAULT 0 CHECK(peak_count>=0),is_secret boolean NOT NULL DEFAULT false,required_rank text CHECK(required_rank IN('Bronze','Silver','Gold','Platinum','Diamond','Heroic','Grandmaster')),speaker_counter integer NOT NULL DEFAULT 0,created_at timestamptz NOT NULL DEFAULT now(),CHECK(ends_at IS NULL OR ends_at>starts_at),CHECK(NOT is_secret OR(required_rank IS NOT NULL AND ends_at IS NOT NULL)));
CREATE UNIQUE INDEX xr_voice_one_public_live ON public.xr_voice_rooms((true))WHERE status='live'AND NOT is_secret;
CREATE TABLE public.xr_voice_members(room_id uuid NOT NULL REFERENCES public.xr_voice_rooms(id)ON DELETE CASCADE,user_id uuid NOT NULL REFERENCES public.profiles(id)ON DELETE CASCADE,session_id uuid NOT NULL DEFAULT gen_random_uuid(),state text NOT NULL CHECK(state IN('joined','waitlisted','left')),joined_at timestamptz,waitlisted_at timestamptz,heartbeat_at timestamptz NOT NULL DEFAULT clock_timestamp(),expires_at timestamptz NOT NULL DEFAULT clock_timestamp()+interval '90 seconds',hand_raised boolean NOT NULL DEFAULT false,speaking boolean NOT NULL DEFAULT false,speaking_reported_at timestamptz,attended_seconds integer NOT NULL DEFAULT 0 CHECK(attended_seconds>=0),PRIMARY KEY(room_id,user_id));
CREATE INDEX xr_voice_fresh_members ON public.xr_voice_members(room_id,state,expires_at);
CREATE TABLE public.xr_voice_speaker_grants(room_id uuid NOT NULL REFERENCES public.xr_voice_rooms(id),user_id uuid NOT NULL REFERENCES public.profiles(id)ON DELETE CASCADE,ordinal integer NOT NULL,confirmed_by uuid NOT NULL,confirmed_at timestamptz NOT NULL DEFAULT clock_timestamp(),PRIMARY KEY(room_id,user_id),UNIQUE(room_id,ordinal));
CREATE TABLE public.xr_voice_attendance_days(user_id uuid NOT NULL REFERENCES public.profiles(id)ON DELETE CASCADE,day date NOT NULL,room_id uuid NOT NULL REFERENCES public.xr_voice_rooms(id),confirmed_by uuid NOT NULL,confirmed_at timestamptz NOT NULL DEFAULT clock_timestamp(),PRIMARY KEY(user_id,day));
CREATE TABLE public.xr_voice_streak_rewards(user_id uuid NOT NULL REFERENCES public.profiles(id)ON DELETE CASCADE,milestone_day date NOT NULL,period_start date NOT NULL,amount integer NOT NULL CHECK(amount>0),ledger_id bigint REFERENCES public.points_ledger(id),PRIMARY KEY(user_id,milestone_day));
CREATE TABLE public.xr_voice_verified_ranks(user_id uuid PRIMARY KEY REFERENCES public.profiles(id)ON DELETE CASCADE,rank text NOT NULL CHECK(rank IN('Bronze','Silver','Gold','Platinum','Diamond','Heroic','Grandmaster')),confirmed_by uuid NOT NULL,confirmed_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE public.xr_voice_polls(id uuid PRIMARY KEY DEFAULT gen_random_uuid(),room_id uuid NOT NULL REFERENCES public.xr_voice_rooms(id)ON DELETE CASCADE,question text NOT NULL CHECK(char_length(question)BETWEEN 3 AND 160),options jsonb NOT NULL CHECK(jsonb_typeof(options)='array'AND jsonb_array_length(options)BETWEEN 2 AND 6),created_by uuid NOT NULL REFERENCES public.profiles(id),created_at timestamptz NOT NULL DEFAULT clock_timestamp(),closes_at timestamptz NOT NULL,CHECK(closes_at>created_at));
CREATE TABLE public.xr_voice_poll_votes(poll_id uuid NOT NULL REFERENCES public.xr_voice_polls(id)ON DELETE CASCADE,user_id uuid NOT NULL REFERENCES public.profiles(id)ON DELETE CASCADE,choice integer NOT NULL CHECK(choice BETWEEN 0 AND 5),created_at timestamptz NOT NULL DEFAULT clock_timestamp(),PRIMARY KEY(poll_id,user_id));

ALTER TABLE public.xr_voice_settings ENABLE ROW LEVEL SECURITY;REVOKE ALL ON public.xr_voice_settings FROM PUBLIC,anon,authenticated;
ALTER TABLE public.xr_voice_rooms ENABLE ROW LEVEL SECURITY;REVOKE ALL ON public.xr_voice_rooms FROM PUBLIC,anon,authenticated;
ALTER TABLE public.xr_voice_members ENABLE ROW LEVEL SECURITY;REVOKE ALL ON public.xr_voice_members FROM PUBLIC,anon,authenticated;
ALTER TABLE public.xr_voice_speaker_grants ENABLE ROW LEVEL SECURITY;REVOKE ALL ON public.xr_voice_speaker_grants FROM PUBLIC,anon,authenticated;
ALTER TABLE public.xr_voice_attendance_days ENABLE ROW LEVEL SECURITY;REVOKE ALL ON public.xr_voice_attendance_days FROM PUBLIC,anon,authenticated;
ALTER TABLE public.xr_voice_streak_rewards ENABLE ROW LEVEL SECURITY;REVOKE ALL ON public.xr_voice_streak_rewards FROM PUBLIC,anon,authenticated;
ALTER TABLE public.xr_voice_verified_ranks ENABLE ROW LEVEL SECURITY;REVOKE ALL ON public.xr_voice_verified_ranks FROM PUBLIC,anon,authenticated;
ALTER TABLE public.xr_voice_polls ENABLE ROW LEVEL SECURITY;REVOKE ALL ON public.xr_voice_polls FROM PUBLIC,anon,authenticated;
ALTER TABLE public.xr_voice_poll_votes ENABLE ROW LEVEL SECURITY;REVOKE ALL ON public.xr_voice_poll_votes FROM PUBLIC,anon,authenticated;
CREATE FUNCTION public.xr_voice_admin()RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path TO pg_catalog,public AS $fn$
BEGIN IF auth.uid()IS NULL OR NOT public.is_admin()THEN RAISE EXCEPTION 'Admin only.'USING ERRCODE='42501';END IF;END
$fn$;
REVOKE ALL ON FUNCTION public.xr_voice_admin()FROM PUBLIC,anon,authenticated;
CREATE FUNCTION public.xr_voice_settings_admin(p_patch jsonb DEFAULT '{}'::jsonb)RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path TO pg_catalog,public AS $fn$
DECLARE cfg public.xr_voice_settings;BEGIN PERFORM public.xr_voice_admin();IF jsonb_typeof(p_patch)IS DISTINCT FROM 'object'OR EXISTS(SELECT 1 FROM jsonb_object_keys(p_patch)k WHERE k NOT IN('enabled','default_seat_cap','first_speaker_limit','streak_days','streak_reward_coins','rewards_enabled','min_attendance_seconds'))THEN RETURN jsonb_build_object('ok',false,'error','Invalid voice setting.');END IF;
 SELECT * INTO cfg FROM public.xr_voice_settings WHERE singleton FOR UPDATE;SELECT * INTO cfg FROM jsonb_populate_record(cfg,p_patch);
 UPDATE public.xr_voice_settings SET enabled=cfg.enabled,default_seat_cap=cfg.default_seat_cap,first_speaker_limit=cfg.first_speaker_limit,streak_days=cfg.streak_days,streak_reward_coins=cfg.streak_reward_coins,rewards_enabled=cfg.rewards_enabled,min_attendance_seconds=cfg.min_attendance_seconds WHERE singleton;
 RETURN jsonb_build_object('ok',true,'settings',to_jsonb(cfg)-'singleton');EXCEPTION WHEN check_violation OR not_null_violation OR invalid_text_representation THEN RETURN jsonb_build_object('ok',false,'error','Invalid voice settings.');END
$fn$;
REVOKE ALL ON FUNCTION public.xr_voice_settings_admin(jsonb)FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.xr_voice_settings_admin(jsonb)TO authenticated;
CREATE FUNCTION public.xr_voice_room_admin(p_patch jsonb,p_room uuid DEFAULT NULL)RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path TO pg_catalog,public AS $fn$
DECLARE r public.xr_voice_rooms;cfg public.xr_voice_settings;BEGIN PERFORM public.xr_voice_admin();
 IF jsonb_typeof(p_patch)IS DISTINCT FROM 'object'OR EXISTS(SELECT 1 FROM jsonb_object_keys(p_patch)k WHERE k NOT IN('title','topic','next_topic','status','seat_cap','starts_at','ends_at','is_secret','required_rank'))THEN RETURN jsonb_build_object('ok',false,'error','Invalid room field.');END IF;
 PERFORM 1 FROM public.xr_voice_settings WHERE singleton FOR UPDATE;
 IF p_room IS NULL THEN SELECT * INTO cfg FROM public.xr_voice_settings WHERE singleton;r.id:=gen_random_uuid();r.seat_cap:=cfg.default_seat_cap;r.status:='scheduled';r.is_secret:=false;
 ELSE SELECT * INTO r FROM public.xr_voice_rooms WHERE id=p_room FOR UPDATE;IF NOT FOUND THEN RETURN jsonb_build_object('ok',false,'error','Room not found.');END IF;IF r.status='ended' THEN RETURN jsonb_build_object('ok',false,'error','Ended rooms are immutable.');END IF;END IF;
 SELECT * INTO r FROM jsonb_populate_record(r,p_patch);
 IF r.title~'[[:cntrl:]<>]'OR r.topic~'[[:cntrl:]<>]'OR r.next_topic~'[[:cntrl:]<>]'THEN RETURN jsonb_build_object('ok',false,'error','Invalid room text.');END IF;
 IF r.is_secret AND r.status='live' THEN RETURN jsonb_build_object('ok',false,'error','Secret audio rooms need private authorized media signaling; staged only.');END IF;
 IF r.status='live'AND(r.starts_at>clock_timestamp()OR(r.ends_at IS NOT NULL AND r.ends_at<=clock_timestamp()))THEN RETURN jsonb_build_object('ok',false,'error','Room outside schedule.');END IF;
 IF r.status='ended'THEN r.ended_at:=clock_timestamp();END IF;
 IF p_room IS NULL THEN INSERT INTO public.xr_voice_rooms(id,title,topic,next_topic,status,seat_cap,starts_at,ends_at,is_secret,required_rank,ended_at)VALUES(r.id,r.title,nullif(btrim(r.topic),''),nullif(btrim(r.next_topic),''),r.status,r.seat_cap,r.starts_at,r.ends_at,r.is_secret,r.required_rank,r.ended_at);
 ELSE UPDATE public.xr_voice_rooms SET title=r.title,topic=nullif(btrim(r.topic),''),next_topic=nullif(btrim(r.next_topic),''),status=r.status,seat_cap=r.seat_cap,starts_at=r.starts_at,ends_at=r.ends_at,is_secret=r.is_secret,required_rank=r.required_rank,ended_at=r.ended_at WHERE id=r.id;END IF;
 IF r.status='ended'THEN UPDATE public.xr_voice_members SET state='left',speaking=false,hand_raised=false,expires_at=clock_timestamp()WHERE room_id=r.id;END IF;
 RETURN jsonb_build_object('ok',true,'room_id',r.id);EXCEPTION WHEN check_violation OR not_null_violation OR unique_violation OR invalid_text_representation OR invalid_datetime_format THEN RETURN jsonb_build_object('ok',false,'error','Invalid room settings or another public room is live.');END
$fn$;
REVOKE ALL ON FUNCTION public.xr_voice_room_admin(jsonb,uuid)FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.xr_voice_room_admin(jsonb,uuid)TO authenticated;
CREATE FUNCTION public.xr_voice_rank_admin(p_user uuid,p_rank text)RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path TO pg_catalog,public AS $fn$
BEGIN PERFORM public.xr_voice_admin();IF NOT EXISTS(SELECT 1 FROM public.profiles WHERE id=p_user)OR p_rank IS NULL OR p_rank NOT IN('Bronze','Silver','Gold','Platinum','Diamond','Heroic','Grandmaster')THEN RETURN jsonb_build_object('ok',false,'error','Invalid verified rank.');END IF;INSERT INTO public.xr_voice_verified_ranks(user_id,rank,confirmed_by)VALUES(p_user,p_rank,auth.uid())ON CONFLICT(user_id)DO UPDATE SET rank=excluded.rank,confirmed_by=auth.uid(),confirmed_at=now();RETURN jsonb_build_object('ok',true);END
$fn$;
REVOKE ALL ON FUNCTION public.xr_voice_rank_admin(uuid,text)FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.xr_voice_rank_admin(uuid,text)TO authenticated;
CREATE FUNCTION public.xr_voice_resolve(p_room uuid DEFAULT NULL)RETURNS public.xr_voice_rooms LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path TO pg_catalog,public AS $fn$
DECLARE r public.xr_voice_rooms;BEGIN
 IF p_room IS NULL THEN SELECT * INTO r FROM public.xr_voice_rooms WHERE status='live'AND NOT is_secret AND starts_at<=statement_timestamp()AND(ends_at IS NULL OR ends_at>statement_timestamp())ORDER BY starts_at DESC LIMIT 1;
 ELSE SELECT * INTO r FROM public.xr_voice_rooms WHERE id=p_room;END IF;
 RETURN r;END
$fn$;
REVOKE ALL ON FUNCTION public.xr_voice_resolve(uuid)FROM PUBLIC,anon,authenticated;
CREATE FUNCTION public.xr_voice_room_eligibility(p_room uuid)RETURNS jsonb LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path TO pg_catalog,public AS $fn$
DECLARE u uuid:=public.xr_sf_actor();r public.xr_voice_rooms;v text;eligible boolean;BEGIN
 SELECT * INTO r FROM public.xr_voice_rooms WHERE id=p_room;IF NOT FOUND THEN RETURN jsonb_build_object('ok',false,'error','Room not found.');END IF;
 SELECT rank INTO v FROM public.xr_voice_verified_ranks WHERE user_id=u;
 eligible:=NOT r.is_secret OR array_position(ARRAY['Bronze','Silver','Gold','Platinum','Diamond','Heroic','Grandmaster'],v)>=array_position(ARRAY['Bronze','Silver','Gold','Platinum','Diamond','Heroic','Grandmaster'],r.required_rank);
 RETURN jsonb_build_object('ok',true,'rank_eligible',coalesce(eligible,false),'rank_source','admin_confirmed','required_rank',r.required_rank,'within_window',r.starts_at<=statement_timestamp()AND(r.ends_at IS NULL OR r.ends_at>statement_timestamp()),'audio_admission_available',NOT r.is_secret,'secret_audio_phase',CASE WHEN r.is_secret THEN 'private_signaling_required' ELSE NULL END);END
$fn$;
REVOKE ALL ON FUNCTION public.xr_voice_room_eligibility(uuid)FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.xr_voice_room_eligibility(uuid)TO authenticated;
CREATE FUNCTION public.xr_voice_lobby_presence(p_room uuid DEFAULT NULL)RETURNS jsonb LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path TO pg_catalog,public AS $fn$
DECLARE u uuid:=public.xr_sf_actor();r public.xr_voice_rooms;visible jsonb;cnt integer;cfg public.xr_voice_settings;BEGIN
 SELECT * INTO cfg FROM public.xr_voice_settings WHERE singleton;r:=public.xr_voice_resolve(p_room);
 IF r.is_secret AND NOT public.is_admin()THEN RETURN jsonb_build_object('ok',true,'enabled',cfg.enabled,'room',NULL,'participants','[]'::jsonb,'error','secret_room_staged');END IF;
 IF r.id IS NULL THEN RETURN jsonb_build_object('ok',true,'enabled',cfg.enabled,'room',NULL,'participants','[]'::jsonb,'joined_count',0,'visible_count',0,'speaking_count',0,'presence_source','authenticated_client_reported','sneak_peek_available',false,'secret_audio_available',false);END IF;
 SELECT count(*)INTO cnt FROM public.xr_voice_members WHERE room_id=r.id AND state='joined'AND expires_at>statement_timestamp();
 SELECT coalesce(jsonb_agg(jsonb_build_object('id',p.id,'username',p.username,'ign',p.ign,'avatar_url',p.avatar_url,'speaking',m.speaking AND m.speaking_reported_at>statement_timestamp()-interval '15 seconds','hand_raised',m.hand_raised,'is_friend',EXISTS(SELECT 1 FROM public.friends f WHERE f.user_id=u AND f.friend_id=p.id AND EXISTS(SELECT 1 FROM public.friends rev WHERE rev.user_id=p.id AND rev.friend_id=u)),'is_squadmate',EXISTS(SELECT 1 FROM public.squad_members a JOIN public.squad_members b ON a.squad_id=b.squad_id WHERE a.user_id=u AND b.user_id=p.id))ORDER BY m.joined_at,p.id),'[]'::jsonb)INTO visible
 FROM public.xr_voice_members m JOIN public.profiles p ON p.id=m.user_id WHERE m.room_id=r.id AND m.state='joined'AND m.expires_at>statement_timestamp()AND NOT coalesce(p.banned,false)AND NOT public.is_blocked(u,p.id)AND(p.id=u OR(coalesce(p.show_activity,false)AND coalesce((SELECT show_activity FROM public.profiles WHERE id=u),false)AND(coalesce((SELECT discoverable FROM public.xr_sf_player_prefs WHERE user_id=p.id),false)OR public.xr_sf_related(p.id)OR EXISTS(SELECT 1 FROM public.friends WHERE user_id=u AND friend_id=p.id)OR EXISTS(SELECT 1 FROM public.squad_members a JOIN public.squad_members b ON a.squad_id=b.squad_id WHERE a.user_id=u AND b.user_id=p.id))));
 RETURN jsonb_build_object('ok',true,'enabled',cfg.enabled,'room',jsonb_build_object('id',r.id,'title',r.title,'topic',r.topic,'next_topic',r.next_topic,'seat_cap',r.seat_cap,'starts_at',r.starts_at,'ends_at',r.ends_at,'status',r.status),'participants',visible,'joined_count',cnt,'visible_count',jsonb_array_length(visible),'speaking_count',(SELECT count(*)FROM jsonb_array_elements(visible)x WHERE(x->>'speaking')::boolean),'presence_source','authenticated_client_reported','speaking_source','client_vad_reported','sneak_peek_available',false,'secret_audio_available',false);END
$fn$;
REVOKE ALL ON FUNCTION public.xr_voice_lobby_presence(uuid)FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.xr_voice_lobby_presence(uuid)TO authenticated;
CREATE FUNCTION public.xr_voice_join(p_room uuid DEFAULT NULL)RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path TO pg_catalog,public AS $fn$
DECLARE u uuid:=public.xr_sf_actor();r public.xr_voice_rooms;m public.xr_voice_members;cfg public.xr_voice_settings;n integer;session uuid;BEGIN
 SELECT * INTO cfg FROM public.xr_voice_settings WHERE singleton;IF NOT cfg.enabled THEN RETURN jsonb_build_object('ok',false,'error','not_open');END IF;
 r:=public.xr_voice_resolve(p_room);IF r.id IS NULL THEN RETURN jsonb_build_object('ok',false,'error','no_live_room');END IF;
 SELECT * INTO r FROM public.xr_voice_rooms WHERE id=r.id FOR UPDATE;
 IF r.is_secret THEN RETURN jsonb_build_object('ok',false,'error','private_signaling_required');END IF;
 IF r.status<>'live'OR r.starts_at>clock_timestamp()OR(r.ends_at IS NOT NULL AND r.ends_at<=clock_timestamp())THEN RETURN jsonb_build_object('ok',false,'error','room_closed');END IF;
 SELECT * INTO m FROM public.xr_voice_members WHERE room_id=r.id AND user_id=u;
 IF m.state='joined'AND m.expires_at>clock_timestamp()THEN RETURN jsonb_build_object('ok',true,'state','joined','room_id',r.id,'session_id',m.session_id,'expires_at',m.expires_at,'already_joined',true);END IF;
 SELECT count(*)INTO n FROM public.xr_voice_members WHERE room_id=r.id AND state='joined'AND expires_at>clock_timestamp();
 IF n>=r.seat_cap THEN
 INSERT INTO public.xr_voice_members(room_id,user_id,state,waitlisted_at,expires_at)VALUES(r.id,u,'waitlisted',clock_timestamp(),clock_timestamp()+interval '5 minutes')ON CONFLICT(room_id,user_id)DO UPDATE SET state='waitlisted',waitlisted_at=CASE WHEN xr_voice_members.state='waitlisted'AND xr_voice_members.expires_at>clock_timestamp()THEN xr_voice_members.waitlisted_at ELSE clock_timestamp()END,expires_at=clock_timestamp()+interval '5 minutes';
 RETURN jsonb_build_object('ok',true,'state','waitlisted','room_id',r.id,'rank',(SELECT count(*)FROM public.xr_voice_members q WHERE q.room_id=r.id AND q.state='waitlisted'AND q.expires_at>clock_timestamp()AND(q.waitlisted_at,q.user_id)<=(SELECT waitlisted_at,user_id FROM public.xr_voice_members WHERE room_id=r.id AND user_id=u)));END IF;
 -- Earlier live waiters have priority over new arrivals; no synthetic queue position.
 IF EXISTS(SELECT 1 FROM public.xr_voice_members q WHERE q.room_id=r.id AND q.state='waitlisted'AND q.expires_at>clock_timestamp()AND(q.waitlisted_at,q.user_id)<(CASE WHEN m.state='waitlisted'AND m.expires_at>clock_timestamp()THEN m.waitlisted_at ELSE 'infinity'::timestamptz END,u))THEN
 INSERT INTO public.xr_voice_members(room_id,user_id,state,waitlisted_at,expires_at)VALUES(r.id,u,'waitlisted',clock_timestamp(),clock_timestamp()+interval '5 minutes')ON CONFLICT(room_id,user_id)DO UPDATE SET state='waitlisted',waitlisted_at=CASE WHEN xr_voice_members.state='waitlisted'AND xr_voice_members.expires_at>clock_timestamp()THEN xr_voice_members.waitlisted_at ELSE clock_timestamp()END,expires_at=clock_timestamp()+interval '5 minutes';
 RETURN jsonb_build_object('ok',true,'state','waitlisted','room_id',r.id,'rank',(SELECT count(*)FROM public.xr_voice_members q WHERE q.room_id=r.id AND q.state='waitlisted'AND q.expires_at>clock_timestamp()AND(q.waitlisted_at,q.user_id)<=(SELECT waitlisted_at,user_id FROM public.xr_voice_members WHERE room_id=r.id AND user_id=u)),'message','Earlier waiters have priority. Retry after a short delay.');END IF;
 session:=gen_random_uuid();
 INSERT INTO public.xr_voice_members(room_id,user_id,session_id,state,joined_at,expires_at)VALUES(r.id,u,session,'joined',clock_timestamp(),clock_timestamp()+interval '90 seconds')ON CONFLICT(room_id,user_id)DO UPDATE SET session_id=excluded.session_id,state='joined',joined_at=excluded.joined_at,heartbeat_at=clock_timestamp(),expires_at=excluded.expires_at,speaking=false,hand_raised=false,attended_seconds=0;
 UPDATE public.xr_voice_rooms SET peak_count=greatest(peak_count,n+1)WHERE id=r.id;
 RETURN jsonb_build_object('ok',true,'state','joined','room_id',r.id,'session_id',session,'expires_at',clock_timestamp()+interval '90 seconds','signaling_authorization_enforced',false);END
$fn$;
REVOKE ALL ON FUNCTION public.xr_voice_join(uuid)FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.xr_voice_join(uuid)TO authenticated;
CREATE FUNCTION public.xr_voice_heartbeat(p_room uuid,p_session uuid,p_speaking boolean DEFAULT false)RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path TO pg_catalog,public AS $fn$
DECLARE u uuid:=public.xr_sf_actor();m public.xr_voice_members;r public.xr_voice_rooms;v_now timestamptz:=clock_timestamp();elapsed integer;BEGIN
 SELECT * INTO r FROM public.xr_voice_rooms WHERE id=p_room FOR SHARE;
 IF r.status IS DISTINCT FROM 'live'OR r.is_secret OR(r.ends_at IS NOT NULL AND r.ends_at<=v_now)THEN RETURN jsonb_build_object('ok',false,'error','room_closed');END IF;
 SELECT * INTO m FROM public.xr_voice_members WHERE room_id=p_room AND user_id=u FOR UPDATE;
 IF m.session_id IS DISTINCT FROM p_session OR m.state IS DISTINCT FROM 'joined'OR m.expires_at<=v_now THEN RETURN jsonb_build_object('ok',false,'error','session_expired');END IF;
 elapsed:=least(greatest(extract(epoch FROM(v_now-m.heartbeat_at))::integer,0),45);
 UPDATE public.xr_voice_members SET attended_seconds=attended_seconds+elapsed,heartbeat_at=v_now,expires_at=v_now+interval '90 seconds',speaking=coalesce(p_speaking,false),speaking_reported_at=v_now WHERE room_id=p_room AND user_id=u;
 RETURN jsonb_build_object('ok',true,'expires_at',v_now+interval '90 seconds','speaking_source','client_vad_reported');END
$fn$;
REVOKE ALL ON FUNCTION public.xr_voice_heartbeat(uuid,uuid,boolean)FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.xr_voice_heartbeat(uuid,uuid,boolean)TO authenticated;
CREATE FUNCTION public.xr_voice_leave(p_room uuid,p_session uuid)RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path TO pg_catalog,public AS $fn$
DECLARE u uuid:=public.xr_sf_actor();BEGIN UPDATE public.xr_voice_members SET state='left',expires_at=clock_timestamp(),speaking=false,hand_raised=false WHERE room_id=p_room AND user_id=u AND session_id=p_session;RETURN jsonb_build_object('ok',true);END
$fn$;
REVOKE ALL ON FUNCTION public.xr_voice_leave(uuid,uuid)FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.xr_voice_leave(uuid,uuid)TO authenticated;
CREATE FUNCTION public.xr_voice_hand_raise(p_on boolean,p_room uuid DEFAULT NULL)RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path TO pg_catalog,public AS $fn$
DECLARE u uuid:=public.xr_sf_actor();r public.xr_voice_rooms;BEGIN r:=public.xr_voice_resolve(p_room);IF r.id IS NULL THEN RETURN jsonb_build_object('ok',false,'error','no_live_room');END IF;
 UPDATE public.xr_voice_members SET hand_raised=coalesce(p_on,false)WHERE room_id=r.id AND user_id=u AND state='joined'AND expires_at>clock_timestamp();IF NOT FOUND THEN RETURN jsonb_build_object('ok',false,'error','join_first');END IF;RETURN jsonb_build_object('ok',true,'hand_raised',coalesce(p_on,false));END
$fn$;
REVOKE ALL ON FUNCTION public.xr_voice_hand_raise(boolean,uuid)FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.xr_voice_hand_raise(boolean,uuid)TO authenticated;
CREATE FUNCTION public.xr_voice_history()RETURNS jsonb LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path TO pg_catalog,public AS $fn$
DECLARE u uuid:=public.xr_sf_actor();prev jsonb;nxt jsonb;BEGIN
 SELECT jsonb_build_object('id',id,'title',title,'topic',topic,'peak_count',peak_count,'ended_at',ended_at)INTO prev FROM public.xr_voice_rooms WHERE status='ended'AND NOT is_secret ORDER BY ended_at DESC NULLS LAST LIMIT 1;
 SELECT jsonb_build_object('id',id,'title',title,'topic',topic,'starts_at',starts_at,'seat_cap',seat_cap)INTO nxt FROM public.xr_voice_rooms WHERE status='scheduled'AND NOT is_secret AND starts_at>statement_timestamp()ORDER BY starts_at LIMIT 1;
 RETURN jsonb_build_object('ok',true,'last_room',prev,'next_room',nxt);END
$fn$;
REVOKE ALL ON FUNCTION public.xr_voice_history()FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.xr_voice_history()TO authenticated;
CREATE FUNCTION public.xr_voice_speaker_confirm(p_room uuid,p_user uuid)RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path TO pg_catalog,public AS $fn$
DECLARE cfg public.xr_voice_settings;r public.xr_voice_rooms;n integer;BEGIN PERFORM public.xr_voice_admin();SELECT * INTO cfg FROM public.xr_voice_settings WHERE singleton;SELECT * INTO r FROM public.xr_voice_rooms WHERE id=p_room FOR UPDATE;
 IF NOT FOUND OR NOT EXISTS(SELECT 1 FROM public.xr_voice_members WHERE room_id=p_room AND user_id=p_user AND joined_at IS NOT NULL)THEN RETURN jsonb_build_object('ok',false,'error','No actual joined session.');END IF;
 IF EXISTS(SELECT 1 FROM public.xr_voice_speaker_grants WHERE room_id=p_room AND user_id=p_user)THEN RETURN jsonb_build_object('ok',true,'already_confirmed',true);END IF;
 n:=r.speaker_counter+1;UPDATE public.xr_voice_rooms SET speaker_counter=n WHERE id=p_room;
 INSERT INTO public.xr_voice_speaker_grants(room_id,user_id,ordinal,confirmed_by)VALUES(p_room,p_user,n,auth.uid());
 IF n<=cfg.first_speaker_limit THEN INSERT INTO public.badges(user_id,badge)VALUES(p_user,'VOICE STAR')ON CONFLICT DO NOTHING;END IF;
 RETURN jsonb_build_object('ok',true,'ordinal',n,'badge_granted',n<=cfg.first_speaker_limit,'source','admin_observed_speech');END
$fn$;
REVOKE ALL ON FUNCTION public.xr_voice_speaker_confirm(uuid,uuid)FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.xr_voice_speaker_confirm(uuid,uuid)TO authenticated;
CREATE FUNCTION public.xr_voice_streak_status()RETURNS jsonb LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path TO pg_catalog,public AS $fn$
DECLARE u uuid:=public.xr_sf_actor();d date;last_day date;st integer:=0;cfg public.xr_voice_settings;BEGIN SELECT * INTO cfg FROM public.xr_voice_settings WHERE singleton;
 SELECT max(day)INTO last_day FROM public.xr_voice_attendance_days WHERE user_id=u;
 IF last_day>=(statement_timestamp()AT TIME ZONE 'Asia/Kolkata')::date-1 THEN d:=last_day;WHILE EXISTS(SELECT 1 FROM public.xr_voice_attendance_days WHERE user_id=u AND day=d)LOOP st:=st+1;d:=d-1;END LOOP;END IF;
 RETURN jsonb_build_object('ok',true,'streak_days',st,'last_attendance_day',last_day,'timezone','Asia/Kolkata','threshold_days',cfg.streak_days,'reward_coins',CASE WHEN cfg.rewards_enabled THEN cfg.streak_reward_coins ELSE 0 END,'rewards_enabled',cfg.rewards_enabled,'attendance_source','admin_confirmed_session','currency','AP_not_cash');END
$fn$;
REVOKE ALL ON FUNCTION public.xr_voice_streak_status()FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.xr_voice_streak_status()TO authenticated;
CREATE FUNCTION public.xr_voice_attendance_confirm(p_room uuid,p_user uuid)RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path TO pg_catalog,public AS $fn$
DECLARE cfg public.xr_voice_settings;m public.xr_voice_members;day_key date;d date;st integer:=0;last_reward date;ledger bigint;BEGIN
 PERFORM public.xr_voice_admin();PERFORM 1 FROM public.profiles WHERE id=p_user FOR UPDATE;SELECT * INTO cfg FROM public.xr_voice_settings WHERE singleton;
 SELECT * INTO m FROM public.xr_voice_members WHERE room_id=p_room AND user_id=p_user;
 IF m.joined_at IS NULL OR m.attended_seconds<cfg.min_attendance_seconds THEN RETURN jsonb_build_object('ok',false,'error','Session has insufficient bounded attendance evidence.');END IF;
 day_key:=(m.joined_at AT TIME ZONE 'Asia/Kolkata')::date;
 IF (clock_timestamp()AT TIME ZONE 'Asia/Kolkata')::date-day_key>7 THEN RETURN jsonb_build_object('ok',false,'error','Confirm attendance within seven days.');END IF;
 INSERT INTO public.xr_voice_attendance_days(user_id,day,room_id,confirmed_by)VALUES(p_user,day_key,p_room,auth.uid())ON CONFLICT DO NOTHING;
 d:=day_key;WHILE EXISTS(SELECT 1 FROM public.xr_voice_attendance_days WHERE user_id=p_user AND day=d)LOOP st:=st+1;d:=d-1;END LOOP;
 SELECT max(milestone_day)INTO last_reward FROM public.xr_voice_streak_rewards WHERE user_id=p_user;
 IF cfg.rewards_enabled AND cfg.streak_reward_coins>0 AND st>=cfg.streak_days AND(last_reward IS NULL OR last_reward<=day_key-cfg.streak_days)THEN
 PERFORM pg_advisory_xact_lock(hashtext('ap:'||p_user::text));
 INSERT INTO public.xr_voice_streak_rewards(user_id,milestone_day,period_start,amount)VALUES(p_user,day_key,day_key-cfg.streak_days+1,cfg.streak_reward_coins)ON CONFLICT DO NOTHING;
 IF FOUND THEN INSERT INTO public.points_ledger(user_id,delta,kind,meta)VALUES(p_user,cfg.streak_reward_coins,'voice_streak',jsonb_build_object('milestone_day',day_key,'threshold',cfg.streak_days,'source','admin_confirmed_attendance'))RETURNING id INTO ledger;UPDATE public.xr_voice_streak_rewards SET ledger_id=ledger WHERE user_id=p_user AND milestone_day=day_key;END IF;END IF;
 RETURN jsonb_build_object('ok',true,'attendance_day',day_key,'streak_days',st,'reward_granted',ledger IS NOT NULL);END
$fn$;
REVOKE ALL ON FUNCTION public.xr_voice_attendance_confirm(uuid,uuid)FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.xr_voice_attendance_confirm(uuid,uuid)TO authenticated;
CREATE FUNCTION public.xr_voice_poll_create(p_room uuid,p_question text,p_options jsonb,p_duration_seconds integer DEFAULT 300)RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path TO pg_catalog,public AS $fn$
DECLARE u uuid:=public.xr_sf_actor();pid uuid;BEGIN
 IF NOT EXISTS(SELECT 1 FROM public.xr_voice_rooms WHERE id=p_room AND status='live'AND NOT is_secret AND(ends_at IS NULL OR ends_at>clock_timestamp()))THEN RETURN jsonb_build_object('ok',false,'error','room_closed');END IF;
 IF NOT public.is_admin()AND NOT(EXISTS(SELECT 1 FROM public.xr_voice_members WHERE room_id=p_room AND user_id=u AND state='joined'AND expires_at>clock_timestamp())AND EXISTS(SELECT 1 FROM public.xr_voice_speaker_grants WHERE room_id=p_room AND user_id=u))THEN RETURN jsonb_build_object('ok',false,'error','Confirmed speaker or admin only.');END IF;
 PERFORM 1 FROM public.xr_voice_rooms WHERE id=p_room FOR UPDATE;
 IF NOT EXISTS(SELECT 1 FROM public.xr_voice_rooms WHERE id=p_room AND status='live'AND NOT is_secret AND(ends_at IS NULL OR ends_at>clock_timestamp()))THEN RETURN jsonb_build_object('ok',false,'error','room_closed');END IF;
 IF EXISTS(SELECT 1 FROM public.xr_voice_polls WHERE room_id=p_room AND closes_at>clock_timestamp())THEN RETURN jsonb_build_object('ok',false,'error','A poll is already open.');END IF;
 IF p_question IS NULL OR char_length(btrim(p_question))NOT BETWEEN 3 AND 160 OR p_question~'[[:cntrl:]<>]'OR jsonb_typeof(p_options)IS DISTINCT FROM 'array'OR jsonb_array_length(p_options)NOT BETWEEN 2 AND 6 OR EXISTS(SELECT 1 FROM jsonb_array_elements(p_options)x WHERE jsonb_typeof(x)<>'string'OR char_length(btrim(x#>>'{}'))NOT BETWEEN 1 AND 60 OR(x#>>'{}')~'[[:cntrl:]<>]')OR p_duration_seconds IS NULL OR p_duration_seconds NOT BETWEEN 30 AND 3600 THEN RETURN jsonb_build_object('ok',false,'error','Invalid poll.');END IF;
 INSERT INTO public.xr_voice_polls(room_id,question,options,created_by,closes_at)VALUES(p_room,btrim(p_question),p_options,u,clock_timestamp()+make_interval(secs=>p_duration_seconds))RETURNING id INTO pid;
 RETURN jsonb_build_object('ok',true,'poll_id',pid);END
$fn$;
REVOKE ALL ON FUNCTION public.xr_voice_poll_create(uuid,text,jsonb,integer)FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.xr_voice_poll_create(uuid,text,jsonb,integer)TO authenticated;
CREATE FUNCTION public.xr_voice_poll_vote(p_poll uuid,p_choice integer)RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path TO pg_catalog,public AS $fn$
DECLARE u uuid:=public.xr_sf_actor();p public.xr_voice_polls;BEGIN
 SELECT * INTO p FROM public.xr_voice_polls WHERE id=p_poll FOR SHARE;IF NOT FOUND OR p.closes_at<=clock_timestamp()OR NOT EXISTS(SELECT 1 FROM public.xr_voice_rooms WHERE id=p.room_id AND status='live'AND(ends_at IS NULL OR ends_at>clock_timestamp()))THEN RETURN jsonb_build_object('ok',false,'error','poll_closed');END IF;
 IF NOT EXISTS(SELECT 1 FROM public.xr_voice_members WHERE room_id=p.room_id AND user_id=u AND state='joined'AND expires_at>clock_timestamp())THEN RETURN jsonb_build_object('ok',false,'error','join_first');END IF;
 IF p_choice IS NULL OR p_choice<0 OR p_choice>=jsonb_array_length(p.options)THEN RETURN jsonb_build_object('ok',false,'error','Invalid choice.');END IF;
 INSERT INTO public.xr_voice_poll_votes(poll_id,user_id,choice)VALUES(p_poll,u,p_choice)ON CONFLICT DO NOTHING;
 RETURN jsonb_build_object('ok',true,'already_voted',NOT FOUND);END
$fn$;
REVOKE ALL ON FUNCTION public.xr_voice_poll_vote(uuid,integer)FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.xr_voice_poll_vote(uuid,integer)TO authenticated;
CREATE FUNCTION public.xr_voice_poll_results(p_room uuid)RETURNS jsonb LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path TO pg_catalog,public AS $fn$
DECLARE u uuid:=public.xr_sf_actor();p public.xr_voice_polls;topv jsonb;BEGIN
 IF NOT public.is_admin()AND NOT EXISTS(SELECT 1 FROM public.xr_voice_members WHERE room_id=p_room AND user_id=u AND state='joined'AND expires_at>statement_timestamp())THEN RETURN jsonb_build_object('ok',false,'error','join_first');END IF;
 SELECT * INTO p FROM public.xr_voice_polls WHERE room_id=p_room ORDER BY created_at DESC LIMIT 1;
 IF p.id IS NULL THEN RETURN jsonb_build_object('ok',true,'poll',NULL,'top_voters','[]'::jsonb);END IF;
 SELECT coalesce(jsonb_agg(jsonb_build_object('id',q.id,'username',q.username,'ign',q.ign,'votes',q.votes)ORDER BY q.votes DESC,q.first_vote,q.id),'[]'::jsonb)INTO topv FROM(
 SELECT pr.id,pr.username,pr.ign,count(*)votes,min(v.created_at)first_vote FROM public.xr_voice_poll_votes v JOIN public.xr_voice_polls pol ON pol.id=v.poll_id JOIN public.profiles pr ON pr.id=v.user_id WHERE pol.room_id=p_room AND NOT coalesce(pr.banned,false)AND NOT public.is_blocked(u,pr.id)AND(pr.id=u OR(coalesce(pr.show_activity,false)AND coalesce((SELECT show_activity FROM public.profiles WHERE id=u),false)AND(coalesce((SELECT discoverable FROM public.xr_sf_player_prefs WHERE user_id=pr.id),false)OR public.xr_sf_related(pr.id)OR EXISTS(SELECT 1 FROM public.friends WHERE user_id=u AND friend_id=pr.id)OR EXISTS(SELECT 1 FROM public.squad_members a JOIN public.squad_members b ON a.squad_id=b.squad_id WHERE a.user_id=u AND b.user_id=pr.id))))GROUP BY pr.id,pr.username,pr.ign ORDER BY votes DESC,first_vote,pr.id LIMIT 3)q;
 RETURN jsonb_build_object('ok',true,'poll',jsonb_build_object('id',p.id,'question',p.question,'options',p.options,'closes_at',p.closes_at,'votes',(SELECT count(*)FROM public.xr_voice_poll_votes WHERE poll_id=p.id),'counts',(SELECT jsonb_agg((SELECT count(*)FROM public.xr_voice_poll_votes v WHERE v.poll_id=p.id AND v.choice=i)ORDER BY i)FROM generate_series(0,jsonb_array_length(p.options)-1)i),'my_choice',(SELECT choice FROM public.xr_voice_poll_votes WHERE poll_id=p.id AND user_id=u)),'top_voters',topv,'top_voters_metric','votes_cast_in_this_room');END
$fn$;
REVOKE ALL ON FUNCTION public.xr_voice_poll_results(uuid)FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.xr_voice_poll_results(uuid)TO authenticated;
NOTIFY pgrst,'reload schema';
COMMIT;