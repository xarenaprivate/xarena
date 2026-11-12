BEGIN;
CREATE TABLE public.xr_tournament_settings(
 tournament_id text PRIMARY KEY REFERENCES public.tournaments(id)ON DELETE CASCADE,
 enabled boolean NOT NULL DEFAULT false,registration_closes_at timestamptz,waitlist_enabled boolean NOT NULL DEFAULT false,
 founding_limit integer NOT NULL DEFAULT 3 CHECK(founding_limit BETWEEN 0 AND 100),early_bird_total integer NOT NULL DEFAULT 4 CHECK(early_bird_total BETWEEN 0 AND 100),
 registration_counter bigint NOT NULL DEFAULT 0,prize_pool text CHECK(char_length(prize_pool)<=200),prize_reveal_at timestamptz,
 ended_at timestamptz,winner_squad text,highlights_url text,CHECK(highlights_url IS NULL OR highlights_url~'^https://')
);
CREATE TABLE public.xr_tournament_registration_meta(registration_id text PRIMARY KEY,tournament_id text NOT NULL REFERENCES public.tournaments(id)ON DELETE CASCADE,eligible_new boolean NOT NULL DEFAULT true,ordinal bigint,is_founding boolean NOT NULL DEFAULT false,is_early_bird boolean NOT NULL DEFAULT false,public_activity boolean NOT NULL DEFAULT false,friends_visible boolean NOT NULL DEFAULT false,city_visible boolean NOT NULL DEFAULT false,created_at timestamptz NOT NULL DEFAULT clock_timestamp());
CREATE TABLE public.xr_tournament_waitlist(tournament_id text NOT NULL REFERENCES public.tournaments(id)ON DELETE CASCADE,user_id uuid NOT NULL REFERENCES public.profiles(id)ON DELETE CASCADE,squad_name text NOT NULL CHECK(char_length(squad_name)BETWEEN 2 AND 80),created_at timestamptz NOT NULL DEFAULT clock_timestamp(),PRIMARY KEY(tournament_id,user_id));
CREATE TABLE public.xr_prize_reveal_subscriptions(tournament_id text NOT NULL REFERENCES public.tournaments(id)ON DELETE CASCADE,user_id uuid NOT NULL REFERENCES public.profiles(id)ON DELETE CASCADE,created_at timestamptz NOT NULL DEFAULT clock_timestamp(),PRIMARY KEY(tournament_id,user_id));
CREATE TABLE public.xr_match_access_settings(match_id uuid PRIMARY KEY REFERENCES public.matches(id)ON DELETE CASCADE,enabled boolean NOT NULL DEFAULT false,checkin_opens_at timestamptz,checkin_closes_at timestamptz,room_release_at timestamptz,require_checked_in boolean NOT NULL DEFAULT true,CHECK(checkin_closes_at IS NULL OR checkin_opens_at IS NULL OR checkin_closes_at>checkin_opens_at));
CREATE TABLE public.xr_chat_pins(room text PRIMARY KEY,body text NOT NULL CHECK(char_length(body)BETWEEN 1 AND 300),published boolean NOT NULL DEFAULT false,updated_at timestamptz NOT NULL DEFAULT clock_timestamp());
INSERT INTO public.xr_chat_pins(room,body,published)VALUES('general','Today''s question: Pochinki or Clock Tower? Share your answer.',true);

ALTER TABLE public.xr_tournament_settings ENABLE ROW LEVEL SECURITY;REVOKE ALL ON public.xr_tournament_settings FROM PUBLIC,anon,authenticated;
ALTER TABLE public.xr_tournament_registration_meta ENABLE ROW LEVEL SECURITY;REVOKE ALL ON public.xr_tournament_registration_meta FROM PUBLIC,anon,authenticated;
ALTER TABLE public.xr_tournament_waitlist ENABLE ROW LEVEL SECURITY;REVOKE ALL ON public.xr_tournament_waitlist FROM PUBLIC,anon,authenticated;
ALTER TABLE public.xr_prize_reveal_subscriptions ENABLE ROW LEVEL SECURITY;REVOKE ALL ON public.xr_prize_reveal_subscriptions FROM PUBLIC,anon,authenticated;
ALTER TABLE public.xr_match_access_settings ENABLE ROW LEVEL SECURITY;REVOKE ALL ON public.xr_match_access_settings FROM PUBLIC,anon,authenticated;
ALTER TABLE public.xr_chat_pins ENABLE ROW LEVEL SECURITY;REVOKE ALL ON public.xr_chat_pins FROM PUBLIC,anon,authenticated;
CREATE OR REPLACE FUNCTION public.xr_tournament_admin()RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path TO pg_catalog,public AS $fn$
BEGIN IF auth.uid()IS NULL OR NOT public.is_admin()THEN RAISE EXCEPTION 'Admin only.'USING ERRCODE='42501';END IF;END
$fn$;
REVOKE ALL ON FUNCTION public.xr_tournament_admin()FROM PUBLIC,anon,authenticated;
CREATE OR REPLACE FUNCTION public.xr_tournament_settings_admin(p_tournament text,p_patch jsonb DEFAULT '{}'::jsonb)RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path TO pg_catalog,public AS $fn$
DECLARE cfg public.xr_tournament_settings;BEGIN PERFORM public.xr_tournament_admin();
 IF NOT EXISTS(SELECT 1 FROM public.tournaments WHERE id=p_tournament)THEN RETURN jsonb_build_object('ok',false,'error','Tournament not found.');END IF;
 IF jsonb_typeof(p_patch)IS DISTINCT FROM 'object'OR EXISTS(SELECT 1 FROM jsonb_object_keys(p_patch)k WHERE k NOT IN('enabled','registration_closes_at','waitlist_enabled','founding_limit','early_bird_total','prize_pool','prize_reveal_at','ended_at','winner_squad','highlights_url'))THEN RETURN jsonb_build_object('ok',false,'error','Invalid setting.');END IF;
 PERFORM 1 FROM public.tournaments WHERE id=p_tournament FOR UPDATE;
 INSERT INTO public.xr_tournament_settings(tournament_id)VALUES(p_tournament)ON CONFLICT DO NOTHING;SELECT * INTO cfg FROM public.xr_tournament_settings WHERE tournament_id=p_tournament FOR UPDATE;SELECT * INTO cfg FROM jsonb_populate_record(cfg,p_patch);
 IF cfg.winner_squad IS NOT NULL AND NOT EXISTS(SELECT 1 FROM public.lb_entries e JOIN public.matches m ON m.id=e.match_id WHERE m.tournament_id=p_tournament AND m.status='done'AND e.placement=1 AND lower(btrim(e.squad))=lower(btrim(cfg.winner_squad)))THEN RETURN jsonb_build_object('ok',false,'error','Winner requires a real first-place result in a completed match.');END IF;
 UPDATE public.xr_tournament_settings SET enabled=cfg.enabled,registration_closes_at=cfg.registration_closes_at,waitlist_enabled=cfg.waitlist_enabled,founding_limit=cfg.founding_limit,early_bird_total=cfg.early_bird_total,prize_pool=cfg.prize_pool,prize_reveal_at=cfg.prize_reveal_at,ended_at=cfg.ended_at,winner_squad=cfg.winner_squad,highlights_url=cfg.highlights_url WHERE tournament_id=p_tournament;
 RETURN jsonb_build_object('ok',true,'settings',to_jsonb(cfg)-'registration_counter');EXCEPTION WHEN check_violation OR not_null_violation OR invalid_text_representation OR invalid_datetime_format THEN RETURN jsonb_build_object('ok',false,'error','Invalid tournament settings.');END
$fn$;
REVOKE ALL ON FUNCTION public.xr_tournament_settings_admin(text,jsonb)FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.xr_tournament_settings_admin(text,jsonb)TO authenticated;
CREATE OR REPLACE FUNCTION public.xr_match_access_admin(p_match uuid,p_patch jsonb DEFAULT '{}'::jsonb)RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path TO pg_catalog,public AS $fn$
DECLARE cfg public.xr_match_access_settings;m public.matches;BEGIN PERFORM public.xr_tournament_admin();SELECT * INTO m FROM public.matches WHERE id=p_match;IF NOT FOUND THEN RETURN jsonb_build_object('ok',false,'error','Match not found.');END IF;
 IF jsonb_typeof(p_patch)IS DISTINCT FROM 'object'OR EXISTS(SELECT 1 FROM jsonb_object_keys(p_patch)k WHERE k NOT IN('enabled','checkin_opens_at','checkin_closes_at','room_release_at','require_checked_in'))THEN RETURN jsonb_build_object('ok',false,'error','Invalid access setting.');END IF;
 INSERT INTO public.xr_match_access_settings(match_id)VALUES(p_match)ON CONFLICT DO NOTHING;SELECT * INTO cfg FROM public.xr_match_access_settings WHERE match_id=p_match FOR UPDATE;SELECT * INTO cfg FROM jsonb_populate_record(cfg,p_patch);
 IF cfg.enabled AND NOT cfg.require_checked_in THEN RETURN jsonb_build_object('ok',false,'error','Checked-in squad access is required.');END IF;
 IF cfg.enabled AND(m.starts_at IS NULL OR(cfg.room_release_at IS NOT NULL AND cfg.room_release_at<m.starts_at-interval '15 minutes'))THEN RETURN jsonb_build_object('ok',false,'error','Set a real match start; room release cannot be earlier than 15 minutes before start.');END IF;
 UPDATE public.xr_match_access_settings SET enabled=cfg.enabled,checkin_opens_at=cfg.checkin_opens_at,checkin_closes_at=cfg.checkin_closes_at,room_release_at=cfg.room_release_at,require_checked_in=cfg.require_checked_in WHERE match_id=p_match;
 RETURN jsonb_build_object('ok',true,'settings',to_jsonb(cfg));EXCEPTION WHEN check_violation OR not_null_violation OR invalid_text_representation OR invalid_datetime_format THEN RETURN jsonb_build_object('ok',false,'error','Invalid match access window.');END
$fn$;
REVOKE ALL ON FUNCTION public.xr_match_access_admin(uuid,jsonb)FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.xr_match_access_admin(uuid,jsonb)TO authenticated;
CREATE OR REPLACE FUNCTION public.xr_tournament_registration_guard()RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path TO pg_catalog,public AS $fn$
DECLARE cfg public.xr_tournament_settings;meta public.xr_tournament_registration_meta;n bigint;BEGIN
 IF coalesce(new.ap_paid,0)>0 THEN RAISE EXCEPTION 'Tournament registration must be free.'USING ERRCODE='P1600';END IF;
 PERFORM 1 FROM public.tournaments WHERE id=new.tournament_id FOR UPDATE;SELECT * INTO cfg FROM public.xr_tournament_settings WHERE tournament_id=new.tournament_id FOR UPDATE;
 IF TG_OP='INSERT'AND cfg.enabled THEN
  IF cfg.registration_closes_at IS NOT NULL AND clock_timestamp()>=cfg.registration_closes_at AND new.status<>'waitlist'THEN RAISE EXCEPTION 'Registration closed. Join the tournament waitlist.'USING ERRCODE='P1601';END IF;
  INSERT INTO public.xr_tournament_registration_meta(registration_id,tournament_id)VALUES(new.id,new.tournament_id)ON CONFLICT DO NOTHING;
 END IF;
 SELECT * INTO meta FROM public.xr_tournament_registration_meta WHERE registration_id=new.id;
 IF cfg.enabled AND meta.eligible_new AND meta.ordinal IS NULL AND new.status NOT IN('waitlist','rejected','cancelled','declined')AND NOT coalesce(new.hold,false)THEN
  UPDATE public.xr_tournament_settings SET registration_counter=registration_counter+1 WHERE tournament_id=new.tournament_id RETURNING registration_counter INTO n;
  UPDATE public.xr_tournament_registration_meta SET ordinal=n,is_founding=n<=cfg.founding_limit,is_early_bird=n<=cfg.early_bird_total WHERE registration_id=new.id;
 END IF;RETURN new;END
$fn$;
REVOKE ALL ON FUNCTION public.xr_tournament_registration_guard()FROM PUBLIC,anon,authenticated;
CREATE TRIGGER xr_tournament_registration_meta AFTER INSERT OR UPDATE OF status,hold,ap_paid ON public.registrations FOR EACH ROW EXECUTE FUNCTION public.xr_tournament_registration_guard();
CREATE OR REPLACE FUNCTION public.xr_tournament_free_guard()RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path TO pg_catalog,public AS $fn$
BEGIN IF coalesce(new.entry_cost_ap,0)>0 THEN RAISE EXCEPTION 'XARENA tournament entry must be free.'USING ERRCODE='P1600';END IF;RETURN new;END
$fn$;
REVOKE ALL ON FUNCTION public.xr_tournament_free_guard()FROM PUBLIC,anon,authenticated;
CREATE TRIGGER xr_tournament_free BEFORE INSERT OR UPDATE OF entry_cost_ap ON public.tournaments FOR EACH ROW EXECUTE FUNCTION public.xr_tournament_free_guard();
CREATE OR REPLACE FUNCTION public.register_squad(p_id text, p_tournament text, p_squad text, p_captain text, p_uid text, p_ign text, p_phone text, p_proof text, p_client text, p_ip text)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare v_flags jsonb := coalesce((select value from site_settings where key='flags'),'{}'::jsonb); v_wait boolean := false; v_slots int; v_hold boolean := false; v_flag text := null; v_burst int; v_token text; v_user uuid := auth.uid();
begin
  if coalesce((select entry_cost_ap from public.tournaments where id=p_tournament),0)>0 then return jsonb_build_object('ok',false,'error','Tournament entry must be free.');end if;
  if exists(select 1 from public.xr_tournament_settings where tournament_id=p_tournament and enabled and registration_closes_at<=clock_timestamp())then return jsonb_build_object('ok',false,'error','Registration closed. Join the tournament waitlist.','code','registration_closed');end if;
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
CREATE OR REPLACE FUNCTION public.xr_tournament_waitlist_join(p_tournament text,p_squad text)RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path TO pg_catalog,public AS $fn$
DECLARE u uuid:=public.xr_sf_actor();cfg public.xr_tournament_settings;rnk bigint;BEGIN
 PERFORM 1 FROM public.tournaments WHERE id=p_tournament FOR UPDATE;SELECT * INTO cfg FROM public.xr_tournament_settings WHERE tournament_id=p_tournament;
 IF NOT coalesce(cfg.enabled AND cfg.waitlist_enabled,false)THEN RETURN jsonb_build_object('ok',false,'error','Waitlist not open.');END IF;
 IF p_squad IS NULL OR char_length(btrim(p_squad))NOT BETWEEN 2 AND 80 OR p_squad~'[[:cntrl:]<>]'THEN RETURN jsonb_build_object('ok',false,'error','Invalid squad name.');END IF;
 IF EXISTS(SELECT 1 FROM public.registrations WHERE tournament_id=p_tournament AND user_id=u AND status NOT IN('waitlist','rejected','cancelled','declined'))THEN RETURN jsonb_build_object('ok',false,'error','Already registered.');END IF;
 INSERT INTO public.xr_tournament_waitlist(tournament_id,user_id,squad_name)VALUES(p_tournament,u,btrim(p_squad))ON CONFLICT DO NOTHING;
 SELECT rank INTO rnk FROM(SELECT user_id,row_number()OVER(ORDER BY created_at,user_id)rank FROM public.xr_tournament_waitlist WHERE tournament_id=p_tournament)q WHERE user_id=u;
 RETURN jsonb_build_object('ok',true,'rank',rnk,'registered',false);END
$fn$;
REVOKE ALL ON FUNCTION public.xr_tournament_waitlist_join(text,text)FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.xr_tournament_waitlist_join(text,text)TO authenticated;
CREATE OR REPLACE FUNCTION public.xr_tournament_registration_sharing(p_tournament text,p_public_activity boolean,p_friends_visible boolean,p_city_visible boolean)RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path TO pg_catalog,public AS $fn$
DECLARE u uuid:=public.xr_sf_actor();BEGIN
 INSERT INTO public.xr_tournament_registration_meta(registration_id,tournament_id,eligible_new,public_activity,friends_visible,city_visible)SELECT id,tournament_id,false,coalesce(p_public_activity,false),coalesce(p_friends_visible,false),coalesce(p_city_visible,false)FROM public.registrations WHERE tournament_id=p_tournament AND user_id=u ON CONFLICT(registration_id)DO UPDATE SET public_activity=excluded.public_activity,friends_visible=excluded.friends_visible,city_visible=excluded.city_visible;
 RETURN jsonb_build_object('ok',true);END
$fn$;
REVOKE ALL ON FUNCTION public.xr_tournament_registration_sharing(text,boolean,boolean,boolean)FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.xr_tournament_registration_sharing(text,boolean,boolean,boolean)TO authenticated;
CREATE OR REPLACE FUNCTION public.xr_prize_reveal_subscribe(p_tournament text,p_on boolean)RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path TO pg_catalog,public AS $fn$
DECLARE u uuid:=public.xr_sf_actor();BEGIN IF NOT EXISTS(SELECT 1 FROM public.tournaments WHERE id=p_tournament)THEN RETURN jsonb_build_object('ok',false,'error','Tournament not found.');END IF;
 IF coalesce(p_on,false)THEN INSERT INTO public.xr_prize_reveal_subscriptions(tournament_id,user_id)VALUES(p_tournament,u)ON CONFLICT DO NOTHING;ELSE DELETE FROM public.xr_prize_reveal_subscriptions WHERE tournament_id=p_tournament AND user_id=u;END IF;
 RETURN jsonb_build_object('ok',true,'subscribed',coalesce(p_on,false),'notification_delivery_available',false);END
$fn$;
REVOKE ALL ON FUNCTION public.xr_prize_reveal_subscribe(text,boolean)FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.xr_prize_reveal_subscribe(text,boolean)TO authenticated;
CREATE OR REPLACE FUNCTION public.xr_tournament_season_summary(p_tournament text)RETURNS jsonb LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path TO pg_catalog,public AS $fn$
DECLARE u uuid:=public.xr_sf_actor();t public.tournaments;cfg public.xr_tournament_settings;BEGIN
 SELECT * INTO t FROM public.tournaments WHERE id=p_tournament;IF NOT FOUND THEN RETURN jsonb_build_object('ok',false,'error','Tournament not found.');END IF;SELECT * INTO cfg FROM public.xr_tournament_settings WHERE tournament_id=p_tournament;
 RETURN jsonb_build_object('ok',true,'status',t.status,'ended_at',CASE WHEN t.status='done'THEN cfg.ended_at END,'winner_squad',CASE WHEN t.status='done'THEN cfg.winner_squad END,'winner_source',CASE WHEN t.status='done'AND cfg.winner_squad IS NOT NULL THEN 'admin_confirmed_completed_first_place_result'END,'squads_count',(SELECT count(*)FROM public.registrations WHERE tournament_id=t.id AND status NOT IN('waitlist','rejected','cancelled','declined')),'highlights_url',cfg.highlights_url);END
$fn$;
REVOKE ALL ON FUNCTION public.xr_tournament_season_summary(text)FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.xr_tournament_season_summary(text)TO authenticated;
CREATE OR REPLACE FUNCTION public.xr_chat_pin_admin(p_room text,p_body text,p_publish boolean DEFAULT false)RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path TO pg_catalog,public AS $fn$
BEGIN PERFORM public.xr_tournament_admin();IF p_room<>'general'AND p_room!~'^st-[a-z0-9-]{1,40}$'THEN RETURN jsonb_build_object('ok',false,'error','Unsupported public chat room.');END IF;
 IF p_body IS NULL OR char_length(btrim(p_body))NOT BETWEEN 1 AND 300 OR p_body~'[[:cntrl:]<>]'THEN RETURN jsonb_build_object('ok',false,'error','Invalid pinned message.');END IF;
 INSERT INTO public.xr_chat_pins(room,body,published)VALUES(p_room,btrim(p_body),coalesce(p_publish,false))ON CONFLICT(room)DO UPDATE SET body=excluded.body,published=excluded.published,updated_at=clock_timestamp();RETURN jsonb_build_object('ok',true,'published',coalesce(p_publish,false));END
$fn$;
REVOKE ALL ON FUNCTION public.xr_chat_pin_admin(text,text,boolean)FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.xr_chat_pin_admin(text,text,boolean)TO authenticated;
CREATE OR REPLACE FUNCTION public.xr_home_chat_preview(p_room text DEFAULT 'general')RETURNS jsonb LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path TO pg_catalog,public AS $fn$
DECLARE u uuid:=public.xr_sf_actor();pin jsonb;latest jsonb;allowed text;BEGIN
 SELECT 'st-'||left(regexp_replace(lower(geo_state),'[^a-z0-9]+','-','g'),40)INTO allowed FROM public.profiles WHERE id=u;
 IF p_room<>'general'AND p_room IS DISTINCT FROM allowed THEN RETURN jsonb_build_object('ok',false,'error','Chat unavailable.');END IF;
 SELECT jsonb_build_object('author','XARENA','body',body,'updated_at',updated_at)INTO pin FROM public.xr_chat_pins WHERE room=p_room AND published;
 SELECT jsonb_build_object('handle',handle,'body',body,'created_at',created_at)INTO latest FROM public.chat_messages WHERE room=p_room ORDER BY id DESC LIMIT 1;
 RETURN jsonb_build_object('ok',true,'pin',pin,'latest_message',latest,'online_count',NULL,'online_count_source',NULL,'first_message_reward_available',false);END
$fn$;
REVOKE ALL ON FUNCTION public.xr_home_chat_preview(text)FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.xr_home_chat_preview(text)TO authenticated;
CREATE OR REPLACE FUNCTION public.match_room(p_match uuid)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare v_uid uuid := auth.uid(); v_m public.matches; v_reg boolean;
begin
  if exists(select 1 from public.xr_match_access_settings where match_id=p_match and enabled)then
    return public.xr_match_room_checked(p_match);
  end if;
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
CREATE OR REPLACE FUNCTION public.xr_match_room_checked(p_match uuid)RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path TO pg_catalog,public AS $fn$
DECLARE u uuid:=public.xr_sf_actor();m public.matches;cfg public.xr_match_access_settings;eligible boolean;checked boolean;release_at timestamptz;BEGIN
 SELECT * INTO m FROM public.matches WHERE id=p_match;SELECT * INTO cfg FROM public.xr_match_access_settings WHERE match_id=p_match;
 IF m.id IS NULL OR m.status IN('done','cancelled')THEN RETURN jsonb_build_object('ok',false,'error','Match unavailable.');END IF;
 IF m.starts_at IS NULL THEN RETURN jsonb_build_object('ok',false,'error','Match start not set.');END IF;
 release_at:=greatest(coalesce(cfg.room_release_at,m.starts_at-interval '15 minutes'),m.starts_at-interval '15 minutes',m.starts_at-make_interval(mins=>coalesce(m.reveal_minutes,15)));
 IF clock_timestamp()<release_at THEN RETURN jsonb_build_object('ok',false,'error','Room details are not released yet.','room_release_at',release_at);END IF;
 SELECT EXISTS(SELECT 1 FROM public.registrations r WHERE r.tournament_id=m.tournament_id AND r.status NOT IN('rejected','cancelled','declined','waitlist')AND NOT coalesce(r.hold,false)AND(r.user_id=u OR EXISTS(SELECT 1 FROM public.squad_members a JOIN public.squad_members b ON a.squad_id=b.squad_id WHERE a.user_id=u AND b.user_id=r.user_id)))INTO eligible;
 SELECT EXISTS(SELECT 1 FROM public.match_checkins c JOIN public.registrations r ON r.tournament_id=m.tournament_id AND r.user_id=c.user_id WHERE c.match_id=m.id AND r.status NOT IN('rejected','cancelled','declined','waitlist')AND NOT coalesce(r.hold,false)AND(r.user_id=u OR EXISTS(SELECT 1 FROM public.squad_members a JOIN public.squad_members b ON a.squad_id=b.squad_id WHERE a.user_id=u AND b.user_id=r.user_id)))INTO checked;
 IF NOT eligible OR(coalesce(cfg.require_checked_in,true)AND NOT checked)THEN RETURN jsonb_build_object('ok',false,'error','Room details require an eligible checked-in registered squad.');END IF;
 IF m.room_id IS NULL THEN RETURN jsonb_build_object('ok',false,'error','Room details are not posted yet.');END IF;
 RETURN jsonb_build_object('ok',true,'room_id',m.room_id,'room_pass',m.room_pass,'title',m.title,'starts_at',m.starts_at);END
$fn$;
REVOKE ALL ON FUNCTION public.xr_match_room_checked(uuid)FROM PUBLIC,anon,authenticated;
CREATE OR REPLACE FUNCTION public.match_checkin(p_match uuid)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare v_uid uuid := auth.uid(); v_m public.matches; v_count int;
begin
  if exists(select 1 from public.xr_match_access_settings where match_id=p_match and enabled)then return public.xr_match_checkin_guarded(p_match);end if;
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
CREATE OR REPLACE FUNCTION public.xr_match_checkin_guarded(p_match uuid)RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path TO pg_catalog,public AS $fn$
DECLARE u uuid:=public.xr_sf_actor();m public.matches;cfg public.xr_match_access_settings;already boolean;BEGIN
 SELECT * INTO m FROM public.matches WHERE id=p_match;SELECT * INTO cfg FROM public.xr_match_access_settings WHERE match_id=p_match;
 IF m.id IS NULL OR m.starts_at IS NULL OR m.status IN('done','cancelled')THEN RETURN jsonb_build_object('ok',false,'error','Match unavailable or start not set.');END IF;
 IF clock_timestamp()<coalesce(cfg.checkin_opens_at,m.starts_at-interval '60 minutes')OR clock_timestamp()>coalesce(cfg.checkin_closes_at,m.starts_at+interval '15 minutes')THEN RETURN jsonb_build_object('ok',false,'error','Outside check-in window.');END IF;
 IF NOT EXISTS(SELECT 1 FROM public.registrations WHERE tournament_id=m.tournament_id AND user_id=u AND status NOT IN('rejected','cancelled','declined','waitlist')AND NOT coalesce(hold,false))THEN RETURN jsonb_build_object('ok',false,'error','An eligible registration is required.');END IF;
 INSERT INTO public.match_checkins(match_id,user_id)VALUES(p_match,u)ON CONFLICT DO NOTHING;already:=NOT FOUND;
 RETURN jsonb_build_object('ok',true,'already',already,'count',(SELECT count(*)FROM public.match_checkins WHERE match_id=p_match));END
$fn$;
REVOKE ALL ON FUNCTION public.xr_match_checkin_guarded(uuid)FROM PUBLIC,anon,authenticated;
CREATE OR REPLACE FUNCTION public.xr_tournament_overview(p_tournament text DEFAULT NULL,p_after text DEFAULT NULL,p_limit integer DEFAULT 10)RETURNS jsonb LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path TO pg_catalog,public AS $fn$
DECLARE u uuid:=public.xr_sf_actor();rows jsonb;BEGIN
 SELECT coalesce(jsonb_agg(q.card ORDER BY q.id),'[]'::jsonb)INTO rows FROM(
 SELECT t.id,jsonb_build_object('id',t.id,'title',t.name,'game',t.game,'status',t.status,'enabled',coalesce(cfg.enabled,false),
 'slots_total',t.slots,'slots_taken',(SELECT count(*)FROM public.registrations r WHERE r.tournament_id=t.id AND r.status NOT IN('waitlist','rejected','cancelled','declined')),
 'registration_open',t.reg_open AND t.status IN('upcoming','live')AND(NOT coalesce(cfg.enabled,false)OR cfg.registration_closes_at IS NULL OR statement_timestamp()<cfg.registration_closes_at),
 'registration_closes_at',CASE WHEN cfg.enabled THEN cfg.registration_closes_at END,
 'early_bird_free_total',CASE WHEN cfg.enabled THEN cfg.early_bird_total ELSE 0 END,'early_bird_free_left',CASE WHEN cfg.enabled THEN greatest(cfg.early_bird_total-cfg.registration_counter,0)ELSE 0 END,'entry','free','early_bird_note','All tournament entries are free, including after early-bird places.',
 'founding_slots_left',CASE WHEN cfg.enabled THEN greatest(cfg.founding_limit-cfg.registration_counter,0)ELSE 0 END,
 'prize_pool',CASE WHEN cfg.enabled AND cfg.prize_reveal_at IS NOT NULL AND statement_timestamp()>=cfg.prize_reveal_at THEN cfg.prize_pool END,'prize_reveal_at',CASE WHEN cfg.enabled THEN cfg.prize_reveal_at END,
 'prize_bell_subscribed',EXISTS(SELECT 1 FROM public.xr_prize_reveal_subscriptions WHERE tournament_id=t.id AND user_id=u),'notification_delivery_available',false,
 'waitlist_open',coalesce(cfg.enabled AND cfg.waitlist_enabled,false),
 'my_registration',(SELECT jsonb_build_object('squad_name',r.squad,'is_founding',coalesce(meta.is_founding,false),'checked_in',EXISTS(SELECT 1 FROM public.match_checkins ch JOIN public.matches m ON m.id=ch.match_id WHERE m.tournament_id=t.id AND ch.user_id=r.user_id))FROM public.registrations r LEFT JOIN public.xr_tournament_registration_meta meta ON meta.registration_id=r.id WHERE r.tournament_id=t.id AND r.user_id=u ORDER BY r.created_at DESC LIMIT 1),
 'my_waitlist_rank',(SELECT rank FROM(SELECT user_id,row_number()OVER(ORDER BY created_at,user_id)rank FROM public.xr_tournament_waitlist WHERE tournament_id=t.id)w WHERE user_id=u),
 'checked_in_count',(SELECT count(DISTINCT ch.user_id)FROM public.match_checkins ch JOIN public.matches m ON m.id=ch.match_id JOIN public.registrations r ON r.tournament_id=t.id AND r.user_id=ch.user_id WHERE m.tournament_id=t.id AND r.status NOT IN('waitlist','rejected','cancelled','declined')AND NOT coalesce(r.hold,false)),
 'city_breakdown',coalesce((SELECT jsonb_agg(jsonb_build_object('city',city,'squads',n))FROM(SELECT pr.geo_district city,count(*)n FROM public.registrations r JOIN public.xr_tournament_registration_meta meta ON meta.registration_id=r.id JOIN public.profiles pr ON pr.id=r.user_id WHERE r.tournament_id=t.id AND meta.city_visible AND r.status NOT IN('waitlist','rejected','cancelled','declined')AND NOT coalesce(pr.banned,false)AND NOT public.is_blocked(u,pr.id)AND nullif(btrim(pr.geo_district),'')IS NOT NULL GROUP BY pr.geo_district ORDER BY count(*)DESC,pr.geo_district LIMIT 10)z),'[]'::jsonb),
 'friends_registered',coalesce((SELECT jsonb_agg(z.item)FROM(SELECT jsonb_build_object('id',pr.id,'username',pr.username,'ign',pr.ign,'avatar_url',pr.avatar_url,'squad_name',r.squad)item FROM public.registrations r JOIN public.xr_tournament_registration_meta meta ON meta.registration_id=r.id JOIN public.profiles pr ON pr.id=r.user_id WHERE r.tournament_id=t.id AND meta.friends_visible AND r.status NOT IN('waitlist','rejected','cancelled','declined')AND NOT coalesce(pr.banned,false)AND NOT public.is_blocked(u,pr.id)AND coalesce(pr.show_activity,false)AND coalesce((SELECT show_activity FROM public.profiles WHERE id=u),false)AND EXISTS(SELECT 1 FROM public.friends f WHERE f.user_id=u AND f.friend_id=pr.id AND EXISTS(SELECT 1 FROM public.friends rev WHERE rev.user_id=pr.id AND rev.friend_id=u))ORDER BY pr.id LIMIT 10)z),'[]'::jsonb),
 'recent_registrations',coalesce((SELECT jsonb_agg(jsonb_build_object('squad_name',z.squad,'at',z.created_at,'registered_at',z.created_at))FROM(SELECT r.squad,r.created_at FROM public.registrations r JOIN public.xr_tournament_registration_meta meta ON meta.registration_id=r.id JOIN public.profiles pr ON pr.id=r.user_id WHERE r.tournament_id=t.id AND meta.public_activity AND r.status NOT IN('waitlist','rejected','cancelled','declined')AND NOT coalesce(pr.banned,false)AND NOT public.is_blocked(u,pr.id)ORDER BY r.created_at DESC LIMIT 5)z),'[]'::jsonb),
 'matches',coalesce((SELECT jsonb_agg(jsonb_build_object('id',m.id,'starts_at',m.starts_at,'status',m.status,'access_enabled',coalesce(a.enabled,false),'checkin_opens_at',CASE WHEN a.enabled THEN coalesce(a.checkin_opens_at,m.starts_at-interval '60 minutes')END,'checkin_closes_at',CASE WHEN a.enabled THEN coalesce(a.checkin_closes_at,m.starts_at+interval '15 minutes')END,'room_release_at',CASE WHEN a.enabled THEN greatest(coalesce(a.room_release_at,m.starts_at-interval '15 minutes'),m.starts_at-interval '15 minutes',m.starts_at-make_interval(mins=>coalesce(m.reveal_minutes,15)))END,'require_checked_in',coalesce(a.enabled AND a.require_checked_in,false)))FROM(SELECT * FROM public.matches WHERE tournament_id=t.id ORDER BY starts_at NULLS LAST,id LIMIT 10)m LEFT JOIN public.xr_match_access_settings a ON a.match_id=m.id),'[]'::jsonb)
 )card FROM public.tournaments t LEFT JOIN public.xr_tournament_settings cfg ON cfg.tournament_id=t.id WHERE(p_tournament IS NULL OR t.id=p_tournament)AND(p_after IS NULL OR t.id>p_after)ORDER BY t.id LIMIT greatest(1,least(coalesce(p_limit,10),10)))q;
 RETURN jsonb_build_object('ok',true,'tournaments',rows,'next_cursor',CASE WHEN jsonb_array_length(rows)=greatest(1,least(coalesce(p_limit,10),10))THEN rows->-1->>'id'END,'cache_seconds',30,'count_refresh_seconds',300);END
$fn$;
REVOKE ALL ON FUNCTION public.xr_tournament_overview(text,text,integer)FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.xr_tournament_overview(text,text,integer)TO authenticated;
NOTIFY pgrst,'reload schema';
COMMIT;