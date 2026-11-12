-- XARENA Squad Finder. Additive data model, legacy privacy hardening, no user seed rows.
BEGIN;
CREATE TABLE public.xr_sf_player_prefs(
 user_id uuid PRIMARY KEY REFERENCES public.profiles(id) ON DELETE CASCADE,
 combat_role text CHECK(combat_role IN ('Rusher','Support','Sniper','IGL')),
 mic boolean, languages text[] CHECK(cardinality(languages)<=8), primary_language text,
 region text CHECK(length(region)<=60), discoverable boolean NOT NULL DEFAULT false,
 free_agent boolean NOT NULL DEFAULT false, backup_opt_in boolean NOT NULL DEFAULT false,
 available_from timestamptz,available_to timestamptz,availability_timezone text,
 updated_at timestamptz NOT NULL DEFAULT now(),
 CHECK(available_from IS NULL OR available_to IS NULL OR available_to>available_from),
 CHECK(NOT free_agent OR (available_from IS NOT NULL AND available_to IS NOT NULL AND availability_timezone IS NOT NULL))
);
CREATE TABLE public.xr_sf_squad_settings(
 squad_id uuid PRIMARY KEY REFERENCES public.squads(id) ON DELETE CASCADE,
 visibility text NOT NULL DEFAULT 'public' CHECK(visibility IN ('public','private')),
 discoverable boolean NOT NULL DEFAULT true,
 squad_code text NOT NULL UNIQUE DEFAULT upper(substr(replace(gen_random_uuid()::text,'-',''),1,10)),
 intro_line text CHECK(length(intro_line)<=160),mode text CHECK(length(mode)<=40),map text CHECK(length(map)<=40),
 region text CHECK(length(region)<=60),comms_voice boolean,max_members integer NOT NULL DEFAULT 4 CHECK(max_members BETWEEN 2 AND 6),
 roles_needed text[] CHECK(cardinality(roles_needed)<=4),updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE public.xr_sf_member_prefs(
 squad_id uuid NOT NULL,user_id uuid NOT NULL,combat_role text CHECK(combat_role IN ('Rusher','Support','Sniper','IGL')),
 ready boolean NOT NULL DEFAULT false,updated_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY(squad_id,user_id),FOREIGN KEY(squad_id,user_id) REFERENCES public.squad_members(squad_id,user_id) ON DELETE CASCADE
);
CREATE TABLE public.xr_sf_join_requests(
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),squad_id uuid NOT NULL REFERENCES public.squads(id) ON DELETE CASCADE,
 user_id uuid NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
 status text NOT NULL DEFAULT 'pending' CHECK(status IN ('pending','accepted','rejected','expired','withdrawn')),
 note text CHECK(length(note)<=200),created_at timestamptz NOT NULL DEFAULT now(),expires_at timestamptz NOT NULL DEFAULT now()+interval '10 minutes',
 decided_at timestamptz,decided_by uuid REFERENCES public.profiles(id)
);
CREATE UNIQUE INDEX xr_sf_pending_request ON public.xr_sf_join_requests(squad_id,user_id) WHERE status='pending';
CREATE INDEX xr_sf_requests_owner ON public.xr_sf_join_requests(user_id,created_at DESC);
ALTER TABLE public.squad_invites ADD COLUMN expires_at timestamptz;
ALTER TABLE public.squad_invites ADD COLUMN source text;
CREATE TABLE public.xr_sf_presence(
 user_id uuid PRIMARY KEY REFERENCES public.profiles(id) ON DELETE CASCADE,
 state text NOT NULL CHECK(state IN ('online','in_match','offline')),reported_at timestamptz NOT NULL DEFAULT now(),
 expires_at timestamptz NOT NULL DEFAULT now()+interval '2 minutes'
);
CREATE TABLE public.xr_sf_trust_evidence(
 user_id uuid PRIMARY KEY REFERENCES public.profiles(id) ON DELETE CASCADE,
 trust_score integer CHECK(trust_score BETWEEN 0 AND 100),policy_version text NOT NULL,evidence_reference text NOT NULL,
 assessed_by uuid NOT NULL REFERENCES public.profiles(id),assessed_at timestamptz NOT NULL DEFAULT now()
);
-- Reserved stable account/result binding. No names matched and no rows populated by this migration.
CREATE TABLE public.xr_sf_result_bindings(
 entry_id uuid PRIMARY KEY REFERENCES public.lb_entries(id) ON DELETE CASCADE,
 user_id uuid NOT NULL REFERENCES public.profiles(id),squad_id uuid REFERENCES public.squads(id),
 confirmed_by uuid NOT NULL REFERENCES public.profiles(id),confirmed_at timestamptz NOT NULL DEFAULT now(),
 deaths integer CHECK(deaths>=0),evidence_reference text NOT NULL
);
CREATE SEQUENCE public.xr_sf_plan_revision_seq;
CREATE TABLE public.xr_sf_strategy_plans(
 squad_id uuid PRIMARY KEY REFERENCES public.squads(id) ON DELETE CASCADE,body text NOT NULL CHECK(length(body) BETWEEN 1 AND 4000),
 revision bigint NOT NULL DEFAULT nextval('public.xr_sf_plan_revision_seq'),updated_by uuid NOT NULL REFERENCES public.profiles(id),updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE public.xr_sf_chat_messages(
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,squad_id uuid NOT NULL REFERENCES public.squads(id) ON DELETE CASCADE,
 user_id uuid NOT NULL REFERENCES public.profiles(id),body text NOT NULL CHECK(length(btrim(body)) BETWEEN 1 AND 400),hidden boolean NOT NULL DEFAULT false,
 created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX xr_sf_chat_idx ON public.xr_sf_chat_messages(squad_id,id);
CREATE TABLE public.xr_sf_feature_flags(key text PRIMARY KEY,enabled boolean NOT NULL DEFAULT false);
-- No flag row is inserted. Missing flag is OFF.
CREATE TABLE public.xr_sf_tryout_rooms(
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),host_id uuid NOT NULL REFERENCES public.profiles(id),
 squad_id uuid REFERENCES public.squads(id) ON DELETE CASCADE,
 room_code text NOT NULL UNIQUE DEFAULT upper(substr(replace(gen_random_uuid()::text,'-',''),1,10)),
 status text NOT NULL DEFAULT 'open' CHECK(status IN ('open','active','closed')),
 mode text NOT NULL CHECK(length(mode) BETWEEN 1 AND 40),map text CHECK(length(map)<=40),duration_min integer NOT NULL CHECK(duration_min BETWEEN 5 AND 120),
 created_at timestamptz NOT NULL DEFAULT now(),expires_at timestamptz NOT NULL,
 test_recorded_by uuid REFERENCES public.profiles(id),test_recorded_at timestamptz
);
CREATE TABLE public.xr_sf_tryout_members(
 room_id uuid NOT NULL REFERENCES public.xr_sf_tryout_rooms(id) ON DELETE CASCADE,user_id uuid NOT NULL REFERENCES public.profiles(id),
 joined_at timestamptz NOT NULL DEFAULT now(),PRIMARY KEY(room_id,user_id)
);
CREATE TABLE public.xr_sf_backup_limits(squad_id uuid PRIMARY KEY REFERENCES public.squads(id) ON DELETE CASCADE,last_called_at timestamptz NOT NULL);
CREATE UNIQUE INDEX IF NOT EXISTS xr_sf_one_squad_per_player ON public.squad_members(user_id);

ALTER TABLE public.xr_sf_player_prefs ENABLE ROW LEVEL SECURITY; REVOKE ALL ON public.xr_sf_player_prefs FROM PUBLIC,anon,authenticated;

ALTER TABLE public.xr_sf_squad_settings ENABLE ROW LEVEL SECURITY; REVOKE ALL ON public.xr_sf_squad_settings FROM PUBLIC,anon,authenticated;

ALTER TABLE public.xr_sf_member_prefs ENABLE ROW LEVEL SECURITY; REVOKE ALL ON public.xr_sf_member_prefs FROM PUBLIC,anon,authenticated;

ALTER TABLE public.xr_sf_join_requests ENABLE ROW LEVEL SECURITY; REVOKE ALL ON public.xr_sf_join_requests FROM PUBLIC,anon,authenticated;

ALTER TABLE public.xr_sf_presence ENABLE ROW LEVEL SECURITY; REVOKE ALL ON public.xr_sf_presence FROM PUBLIC,anon,authenticated;

ALTER TABLE public.xr_sf_trust_evidence ENABLE ROW LEVEL SECURITY; REVOKE ALL ON public.xr_sf_trust_evidence FROM PUBLIC,anon,authenticated;

ALTER TABLE public.xr_sf_result_bindings ENABLE ROW LEVEL SECURITY; REVOKE ALL ON public.xr_sf_result_bindings FROM PUBLIC,anon,authenticated;

ALTER TABLE public.xr_sf_strategy_plans ENABLE ROW LEVEL SECURITY; REVOKE ALL ON public.xr_sf_strategy_plans FROM PUBLIC,anon,authenticated;

ALTER TABLE public.xr_sf_chat_messages ENABLE ROW LEVEL SECURITY; REVOKE ALL ON public.xr_sf_chat_messages FROM PUBLIC,anon,authenticated;

ALTER TABLE public.xr_sf_feature_flags ENABLE ROW LEVEL SECURITY; REVOKE ALL ON public.xr_sf_feature_flags FROM PUBLIC,anon,authenticated;

ALTER TABLE public.xr_sf_tryout_rooms ENABLE ROW LEVEL SECURITY; REVOKE ALL ON public.xr_sf_tryout_rooms FROM PUBLIC,anon,authenticated;

ALTER TABLE public.xr_sf_tryout_members ENABLE ROW LEVEL SECURITY; REVOKE ALL ON public.xr_sf_tryout_members FROM PUBLIC,anon,authenticated;

ALTER TABLE public.xr_sf_backup_limits ENABLE ROW LEVEL SECURITY; REVOKE ALL ON public.xr_sf_backup_limits FROM PUBLIC,anon,authenticated;

REVOKE ALL ON SEQUENCE public.xr_sf_chat_messages_id_seq,public.xr_sf_plan_revision_seq FROM PUBLIC,anon,authenticated;

CREATE OR REPLACE FUNCTION public.xr_sf_actor() RETURNS uuid LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $sf$
DECLARE u uuid:=auth.uid(); BEGIN
 IF u IS NULL OR NOT EXISTS(SELECT 1 FROM public.profiles WHERE id=u AND NOT coalesce(banned,false)) THEN RAISE EXCEPTION 'Login with an unrestricted account required.' USING ERRCODE='42501';END IF;RETURN u;END
$sf$;

CREATE OR REPLACE FUNCTION public.xr_sf_is_member(p_squad uuid) RETURNS boolean LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path=pg_catalog AS $sf$
BEGIN RETURN EXISTS(SELECT 1 FROM public.squad_members m JOIN public.profiles p ON p.id=m.user_id WHERE m.squad_id=p_squad AND m.user_id=auth.uid() AND NOT coalesce(p.banned,false));END
$sf$;

CREATE OR REPLACE FUNCTION public.xr_sf_is_captain(p_squad uuid) RETURNS boolean LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path=pg_catalog AS $sf$
BEGIN RETURN EXISTS(SELECT 1 FROM public.squads WHERE id=p_squad AND captain=auth.uid());END
$sf$;

CREATE OR REPLACE FUNCTION public.xr_sf_public_squad(p_squad uuid) RETURNS boolean LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path=pg_catalog AS $sf$
BEGIN RETURN EXISTS(SELECT 1 FROM public.squads s LEFT JOIN public.xr_sf_squad_settings x ON x.squad_id=s.id WHERE s.id=p_squad AND coalesce(x.visibility,'public')='public');END
$sf$;

DROP POLICY squads_read ON public.squads;
CREATE POLICY squads_read ON public.squads FOR SELECT USING(public.xr_sf_public_squad(id) OR public.xr_sf_is_member(id));
DROP POLICY members_read ON public.squad_members;
CREATE POLICY members_read ON public.squad_members FOR SELECT USING(public.xr_sf_public_squad(squad_id) OR public.xr_sf_is_member(squad_id));

GRANT EXECUTE ON FUNCTION public.xr_sf_public_squad(uuid),public.xr_sf_is_member(uuid) TO anon,authenticated;

CREATE OR REPLACE FUNCTION public.xr_sf_member_guard() RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $sf$
DECLARE cap integer;BEGIN
 PERFORM 1 FROM public.profiles WHERE id=NEW.user_id FOR UPDATE;
 PERFORM 1 FROM public.squads WHERE id=NEW.squad_id FOR UPDATE;
 IF EXISTS(SELECT 1 FROM public.profiles WHERE id=NEW.user_id AND banned) THEN RAISE EXCEPTION 'Restricted player.';END IF;
 SELECT coalesce((SELECT max_members FROM public.xr_sf_squad_settings WHERE squad_id=NEW.squad_id),6) INTO cap;
 IF (SELECT count(*) FROM public.squad_members WHERE squad_id=NEW.squad_id)>=cap THEN RAISE EXCEPTION 'Squad is full.';END IF;
 IF EXISTS(SELECT 1 FROM public.squad_members WHERE user_id=NEW.user_id) THEN RAISE EXCEPTION 'Already in a squad.';END IF;
 IF NOT public.xr_sf_public_squad(NEW.squad_id) AND NEW.role<>'captain' AND NOT (
 EXISTS(SELECT 1 FROM public.xr_sf_join_requests WHERE squad_id=NEW.squad_id AND user_id=NEW.user_id AND status='accepted' AND decided_by=auth.uid() AND decided_at>=now()-interval '10 seconds') OR
 EXISTS(SELECT 1 FROM public.squad_invites WHERE squad_id=NEW.squad_id AND invited=NEW.user_id AND status='pending' AND (expires_at IS NULL OR expires_at>clock_timestamp()) AND invited=auth.uid())
 ) THEN RAISE EXCEPTION 'Private squad requires captain approval or a valid invite.';END IF;
 RETURN NEW;END
$sf$;

CREATE TRIGGER xr_sf_member_guard BEFORE INSERT ON public.squad_members FOR EACH ROW EXECUTE FUNCTION public.xr_sf_member_guard();

CREATE OR REPLACE FUNCTION public.xr_sf_invite_guard() RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $sf$
BEGIN
 IF NEW.status='accepted' AND OLD.status='pending' AND NEW.expires_at IS NOT NULL AND NEW.expires_at<=clock_timestamp() THEN RAISE EXCEPTION 'Invite expired.';END IF;
 RETURN NEW;END
$sf$;

CREATE TRIGGER xr_sf_invite_guard BEFORE UPDATE ON public.squad_invites FOR EACH ROW EXECUTE FUNCTION public.xr_sf_invite_guard();

CREATE OR REPLACE FUNCTION public.xr_sf_role_catalog() RETURNS jsonb LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path=pg_catalog AS $sf$
BEGIN RETURN jsonb_build_object('ok',true,'roles',jsonb_build_array('Rusher','Support','Sniper','IGL'));END
$sf$;

CREATE OR REPLACE FUNCTION public.xr_sf_player_preferences(p_patch jsonb DEFAULT '{}'::jsonb) RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $sf$
DECLARE u uuid:=public.xr_sf_actor();v public.xr_sf_player_prefs;langs text[]; BEGIN
 IF p_patch IS NULL OR jsonb_typeof(p_patch)<>'object' THEN RAISE EXCEPTION 'Expected object.';END IF;
 IF EXISTS(SELECT 1 FROM jsonb_object_keys(p_patch) k WHERE k NOT IN ('combat_role','mic','languages','primary_language','region','discoverable','free_agent','backup_opt_in','available_from','available_to','availability_timezone')) THEN RAISE EXCEPTION 'Unknown or protected preference.';END IF;
 IF p_patch<>'{}'::jsonb THEN
 INSERT INTO public.xr_sf_player_prefs(user_id) VALUES(u) ON CONFLICT DO NOTHING;
 SELECT * INTO v FROM public.xr_sf_player_prefs WHERE user_id=u FOR UPDATE;
 SELECT * INTO v FROM jsonb_populate_record(v,p_patch);
 IF v.languages IS NOT NULL THEN
 IF cardinality(v.languages)>8 OR EXISTS(SELECT 1 FROM unnest(v.languages) x WHERE x IS NULL OR length(btrim(x)) NOT BETWEEN 1 AND 30) THEN RAISE EXCEPTION 'Up to eight nonempty language names.';END IF;
 END IF;
 IF v.primary_language IS NOT NULL AND NOT coalesce(v.primary_language=ANY(v.languages),false) THEN RAISE EXCEPTION 'Primary language must be selected.';END IF;
 IF v.availability_timezone IS NOT NULL AND NOT EXISTS(SELECT 1 FROM pg_timezone_names WHERE name=v.availability_timezone) THEN RAISE EXCEPTION 'Use IANA timezone.';END IF;
 IF v.free_agent AND (v.available_from IS NULL OR v.available_to IS NULL OR v.availability_timezone IS NULL OR v.available_to<=v.available_from OR v.available_to>v.available_from+interval '7 days') THEN RAISE EXCEPTION 'Set a valid availability window of at most seven days.';END IF;
 IF v.backup_opt_in AND NOT(v.discoverable AND v.free_agent) THEN RAISE EXCEPTION 'Backup invites require discoverable free-agent opt-in.';END IF;
 UPDATE public.xr_sf_player_prefs SET combat_role=v.combat_role,mic=v.mic,languages=v.languages,primary_language=v.primary_language,region=v.region,discoverable=v.discoverable,free_agent=v.free_agent,backup_opt_in=v.backup_opt_in,available_from=v.available_from,available_to=v.available_to,availability_timezone=v.availability_timezone,updated_at=now() WHERE user_id=u;
 END IF;
 RETURN jsonb_build_object('ok',true,'preferences',(SELECT to_jsonb(x)-'user_id' FROM public.xr_sf_player_prefs x WHERE user_id=u));END
$sf$;

CREATE OR REPLACE FUNCTION public.xr_sf_presence_set(p_state text) RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $sf$
DECLARE u uuid:=public.xr_sf_actor();BEGIN
 IF p_state IS NULL OR p_state NOT IN ('online','in_match','offline') THEN RAISE EXCEPTION 'Invalid presence.';END IF;
 INSERT INTO public.xr_sf_presence(user_id,state,reported_at,expires_at) VALUES(u,p_state,clock_timestamp(),clock_timestamp()+interval '2 minutes') ON CONFLICT(user_id) DO UPDATE SET state=excluded.state,reported_at=excluded.reported_at,expires_at=excluded.expires_at;
 PERFORM public.presence_ping();RETURN jsonb_build_object('ok',true,'state',p_state,'source','player_reported','expires_at',clock_timestamp()+interval '2 minutes');END
$sf$;

CREATE OR REPLACE FUNCTION public.xr_sf_related(p_user uuid) RETURNS boolean LANGUAGE sql STABLE SECURITY DEFINER SET search_path=pg_catalog AS $$SELECT EXISTS(SELECT 1 FROM public.xr_sf_join_requests r JOIN public.squads s ON s.id=r.squad_id WHERE r.user_id=p_user AND s.captain=auth.uid() AND r.status='pending' AND r.expires_at>now()) OR EXISTS(SELECT 1 FROM public.squad_invites WHERE ((invited=auth.uid() AND invited_by=p_user) OR (invited_by=auth.uid() AND invited=p_user)) AND status='pending' AND (expires_at IS NULL OR expires_at>now()))$$;

CREATE OR REPLACE FUNCTION public.xr_sf_player_card(p_user uuid) RETURNS jsonb LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path=pg_catalog AS $sf$
DECLARE p public.profiles;f public.xr_sf_player_prefs;lv integer;xp integer;pr public.xr_sf_presence;show boolean;BEGIN
 SELECT * INTO p FROM public.profiles WHERE id=p_user AND NOT coalesce(banned,false);IF NOT FOUND THEN RETURN NULL;END IF;
 SELECT * INTO f FROM public.xr_sf_player_prefs WHERE user_id=p_user;
 IF p_user<>auth.uid() AND NOT coalesce(f.discoverable,false) AND NOT public.xr_sf_related(p_user) AND NOT EXISTS(SELECT 1 FROM public.friends WHERE user_id=auth.uid() AND friend_id=p_user) AND NOT EXISTS(SELECT 1 FROM public.squad_members a JOIN public.squad_members b ON a.squad_id=b.squad_id WHERE a.user_id=auth.uid() AND b.user_id=p_user) THEN RETURN NULL;END IF;
 IF public.is_blocked(auth.uid(),p_user) THEN RETURN NULL;END IF;
 SELECT g.xp INTO xp FROM public.player_game_stats g WHERE user_id=p_user;IF xp IS NOT NULL THEN SELECT level INTO lv FROM public.level_from_xp(xp) LIMIT 1;END IF;
 show:=p_user=auth.uid() OR (coalesce(p.show_activity,false) AND coalesce((SELECT show_activity FROM public.profiles WHERE id=auth.uid()),false));
 IF show THEN SELECT * INTO pr FROM public.xr_sf_presence WHERE user_id=p_user;END IF;
 RETURN jsonb_build_object('id',p.id,'username',p.username,'name',coalesce(p.ign,p.username),'avatar_url',p.avatar_url,'role',f.combat_role,'level',lv,'level_source',CASE WHEN lv IS NOT NULL THEN 'earned_xp' END,'tier',p.ff_rank,'verified',p.verified,
 'trust_score',(SELECT trust_score FROM public.xr_sf_trust_evidence WHERE user_id=p_user),'kd',p.kd,'kd_source',CASE WHEN p.kd IS NOT NULL THEN 'self_reported' END,'mic',f.mic,'languages',f.languages,'primary_language',f.primary_language,'region',f.region,
 'free_agent',CASE WHEN p_user=auth.uid() OR f.discoverable THEN f.free_agent END,'available_from',CASE WHEN p_user=auth.uid() OR (f.discoverable AND f.free_agent) THEN f.available_from END,'available_to',CASE WHEN p_user=auth.uid() OR (f.discoverable AND f.free_agent) THEN f.available_to END,'availability_timezone',CASE WHEN p_user=auth.uid() OR (f.discoverable AND f.free_agent) THEN f.availability_timezone END,
 'presence',CASE WHEN NOT show THEN NULL WHEN pr.expires_at>now() THEN pr.state WHEN p.last_seen_at>now()-interval '2 minutes' THEN 'online' ELSE 'offline' END,
 'presence_source',CASE WHEN show AND pr.expires_at>now() THEN 'player_reported' WHEN show THEN 'last_activity' END,'last_seen',CASE WHEN show THEN p.last_seen_at END,'spectate_allowed',false,
 'compatibility',NULL,'rating',NULL,'ping_ms',NULL);END
$sf$;

CREATE OR REPLACE FUNCTION public.xr_sf_player_get(p_user uuid) RETURNS jsonb LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path=pg_catalog AS $sf$
DECLARE u uuid:=public.xr_sf_actor();c jsonb;hist jsonb;activity jsonb;BEGIN
 c:=public.xr_sf_player_card(p_user);IF c IS NULL THEN RETURN jsonb_build_object('ok',false,'error','Player unavailable.');END IF;
 IF p_user=u OR (EXISTS(SELECT 1 FROM public.profiles WHERE id=p_user AND show_activity) AND EXISTS(SELECT 1 FROM public.profiles WHERE id=u AND show_activity)) THEN
 SELECT coalesce(jsonb_agg(to_jsonb(t)),'[]'::jsonb) INTO activity FROM (SELECT kind,amount,created_at FROM public.xp_events WHERE user_id=p_user ORDER BY created_at DESC LIMIT 10)t;
 SELECT coalesce(jsonb_agg(to_jsonb(t)),'[]'::jsonb) INTO hist FROM (SELECT m.id match_id,m.title,m.played_at ended_at,e.kills,b.deaths,(e.placement=1) won,'admin_confirmed_account_binding'::text source FROM public.xr_sf_result_bindings b JOIN public.lb_entries e ON e.id=b.entry_id JOIN public.matches m ON m.id=e.match_id WHERE b.user_id=p_user AND m.status='done' ORDER BY coalesce(m.played_at,m.starts_at) DESC LIMIT 5)t;
 END IF;
 RETURN jsonb_build_object('ok',true,'player',c,'activity',coalesce(activity,'[]'::jsonb),'activity_source','xp_events','last_matches',coalesce(hist,'[]'::jsonb));END
$sf$;

CREATE OR REPLACE FUNCTION public.xr_sf_player_list(p_role text DEFAULT NULL,p_language text DEFAULT NULL,p_mic boolean DEFAULT NULL,p_min_trust integer DEFAULT NULL,p_verified_only boolean DEFAULT false,p_free_agents boolean DEFAULT false,p_limit integer DEFAULT 30) RETURNS jsonb LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path=pg_catalog AS $sf$
DECLARE u uuid:=public.xr_sf_actor();rows jsonb;BEGIN
 SELECT coalesce(jsonb_agg(c),'[]'::jsonb) INTO rows FROM (
 SELECT public.xr_sf_player_card(p.id)c FROM public.profiles p JOIN public.xr_sf_player_prefs f ON f.user_id=p.id LEFT JOIN public.xr_sf_trust_evidence t ON t.user_id=p.id
 WHERE f.discoverable AND NOT coalesce(p.banned,false) AND NOT public.is_blocked(u,p.id)
 AND (p_role IS NULL OR f.combat_role=p_role) AND (p_language IS NULL OR p_language=ANY(f.languages)) AND (p_mic IS NULL OR f.mic=p_mic)
 AND (p_min_trust IS NULL OR t.trust_score>=p_min_trust) AND (NOT p_verified_only OR p.verified)
 AND (NOT p_free_agents OR (f.free_agent AND f.available_from<=now() AND f.available_to>now() AND NOT EXISTS(SELECT 1 FROM public.squad_members WHERE user_id=p.id)))
 ORDER BY f.updated_at DESC,p.id LIMIT greatest(1,least(coalesce(p_limit,30),50)))z WHERE c IS NOT NULL;
 RETURN jsonb_build_object('ok',true,'players',rows);END
$sf$;

CREATE OR REPLACE FUNCTION public.xr_sf_squad_settings(p_squad uuid,p_patch jsonb DEFAULT '{}'::jsonb) RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $sf$
DECLARE u uuid:=public.xr_sf_actor();v public.xr_sf_squad_settings;n integer;BEGIN
 IF NOT public.xr_sf_is_captain(p_squad) THEN RAISE EXCEPTION 'Captain only.' USING ERRCODE='42501';END IF;
 PERFORM 1 FROM public.squads WHERE id=p_squad FOR UPDATE;
 IF p_patch IS NULL OR jsonb_typeof(p_patch)<>'object' OR EXISTS(SELECT 1 FROM jsonb_object_keys(p_patch) k WHERE k NOT IN ('visibility','discoverable','intro_line','mode','map','region','comms_voice','max_members','roles_needed')) THEN RAISE EXCEPTION 'Unknown or protected setting.';END IF;
 INSERT INTO public.xr_sf_squad_settings(squad_id,max_members) VALUES(p_squad,6) ON CONFLICT DO NOTHING;
 SELECT * INTO v FROM public.xr_sf_squad_settings WHERE squad_id=p_squad FOR UPDATE;
 SELECT * INTO v FROM jsonb_populate_record(v,p_patch);
 IF v.roles_needed IS NOT NULL AND EXISTS(SELECT 1 FROM unnest(v.roles_needed) x WHERE x IS NULL OR x NOT IN ('Rusher','Support','Sniper','IGL')) THEN RAISE EXCEPTION 'Invalid role.';END IF;
 SELECT count(*) INTO n FROM public.squad_members WHERE squad_id=p_squad;IF v.max_members<n THEN RAISE EXCEPTION 'Capacity is below current roster.';END IF;
 UPDATE public.xr_sf_squad_settings SET visibility=v.visibility,discoverable=v.discoverable,intro_line=v.intro_line,mode=v.mode,map=v.map,region=v.region,comms_voice=v.comms_voice,max_members=v.max_members,roles_needed=v.roles_needed,updated_at=now() WHERE squad_id=p_squad;
 RETURN jsonb_build_object('ok',true,'settings',to_jsonb(v));END
$sf$;

CREATE OR REPLACE FUNCTION public.xr_sf_squad_create(p_name text,p_tag text DEFAULT NULL,p_intro_line text DEFAULT NULL,p_visibility text DEFAULT 'public') RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $sf$
DECLARE u uuid:=public.xr_sf_actor();r jsonb;sid uuid;BEGIN
 IF p_visibility IS NULL OR p_visibility NOT IN ('public','private') OR length(p_intro_line)>160 THEN RAISE EXCEPTION 'Invalid squad settings.';END IF;
 PERFORM 1 FROM public.profiles WHERE id=u FOR UPDATE;
 r:=public.create_squad(p_name,p_tag,p_intro_line);IF NOT coalesce((r->>'ok')::boolean,false) THEN RETURN r;END IF;sid:=(r->>'id')::uuid;
 INSERT INTO public.xr_sf_squad_settings(squad_id,visibility,intro_line,max_members) VALUES(sid,p_visibility,p_intro_line,4);
 RETURN r||jsonb_build_object('visibility',p_visibility,'squad_code',(SELECT squad_code FROM public.xr_sf_squad_settings WHERE squad_id=sid));END
$sf$;

CREATE OR REPLACE FUNCTION public.xr_sf_squad_get(p_squad uuid) RETURNS jsonb LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path=pg_catalog AS $sf$
DECLARE u uuid:=public.xr_sf_actor();s public.squads;x public.xr_sf_squad_settings;mine boolean;roster jsonb;BEGIN
 SELECT * INTO s FROM public.squads WHERE id=p_squad;IF NOT FOUND OR public.is_blocked(u,s.captain) THEN RETURN jsonb_build_object('ok',false,'error','Squad unavailable.');END IF;
 SELECT * INTO x FROM public.xr_sf_squad_settings WHERE squad_id=p_squad;mine:=public.xr_sf_is_member(p_squad);
 IF NOT mine AND NOT coalesce(x.discoverable,true) THEN RETURN jsonb_build_object('ok',false,'error','Squad unavailable.');END IF;
 IF mine OR coalesce(x.visibility,'public')='public' THEN
 SELECT coalesce(jsonb_agg(jsonb_build_object('player',public.xr_sf_player_card(m.user_id),'membership_role',m.role,'combat_role',mp.combat_role,'ready',mp.ready) ORDER BY m.joined_at),'[]'::jsonb) INTO roster FROM public.squad_members m LEFT JOIN public.xr_sf_member_prefs mp ON mp.squad_id=m.squad_id AND mp.user_id=m.user_id WHERE m.squad_id=p_squad;
 END IF;
 RETURN jsonb_build_object('ok',true,'squad',jsonb_build_object('id',s.id,'name',s.name,'logo_url',s.logo_url,'intro_line',coalesce(x.intro_line,s.bio),'visibility',coalesce(x.visibility,'public'),'mode',x.mode,'map',x.map,'region',x.region,'comms_voice',x.comms_voice,'roles_needed',x.roles_needed,'member_count',(SELECT count(*) FROM public.squad_members WHERE squad_id=p_squad),'max_members',coalesce(x.max_members,6),'squad_code',CASE WHEN mine THEN x.squad_code END,'is_member',mine,'is_captain',s.captain=u,'members',coalesce(roster,'[]'::jsonb),'wins',NULL,'matches_played',NULL,'rank',NULL,'streak',NULL,'compatibility',NULL,'ping_ms',NULL));END
$sf$;

CREATE OR REPLACE FUNCTION public.xr_sf_squad_list(p_role text DEFAULT NULL,p_visibility text DEFAULT NULL,p_mine boolean DEFAULT false,p_limit integer DEFAULT 30) RETURNS jsonb LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path=pg_catalog AS $sf$
DECLARE u uuid:=public.xr_sf_actor();rows jsonb;BEGIN
 SELECT coalesce(jsonb_agg(c),'[]'::jsonb) INTO rows FROM (SELECT public.xr_sf_squad_get(s.id)->'squad' c FROM public.squads s LEFT JOIN public.xr_sf_squad_settings x ON x.squad_id=s.id WHERE (coalesce(x.discoverable,true) OR public.xr_sf_is_member(s.id)) AND NOT public.is_blocked(u,s.captain) AND (NOT p_mine OR public.xr_sf_is_member(s.id)) AND (p_role IS NULL OR p_role=ANY(x.roles_needed)) AND (p_visibility IS NULL OR coalesce(x.visibility,'public')=p_visibility) ORDER BY s.created_at DESC,s.id LIMIT greatest(1,least(coalesce(p_limit,30),50)))z WHERE c IS NOT NULL;
 RETURN jsonb_build_object('ok',true,'squads',rows);END
$sf$;

CREATE OR REPLACE FUNCTION public.xr_sf_join_public(p_squad uuid) RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $sf$
DECLARE u uuid:=public.xr_sf_actor();BEGIN
 PERFORM 1 FROM public.profiles WHERE id=u FOR UPDATE;PERFORM 1 FROM public.squads WHERE id=p_squad FOR UPDATE;
 IF NOT public.xr_sf_public_squad(p_squad) OR NOT EXISTS(SELECT 1 FROM public.squads s LEFT JOIN public.xr_sf_squad_settings x ON x.squad_id=s.id WHERE s.id=p_squad AND coalesce(x.discoverable,true) AND NOT public.is_blocked(u,s.captain)) THEN RETURN jsonb_build_object('ok',false,'error','Public squad unavailable.');END IF;
 IF public.xr_sf_is_member(p_squad) THEN RETURN jsonb_build_object('ok',true,'already_member',true);END IF;
 INSERT INTO public.squad_members(squad_id,user_id,role) VALUES(p_squad,u,'member');
 RETURN jsonb_build_object('ok',true,'squad_id',p_squad);END
$sf$;

CREATE OR REPLACE FUNCTION public.xr_sf_join_request(p_squad uuid,p_note text DEFAULT NULL) RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $sf$
DECLARE u uuid:=public.xr_sf_actor();rid uuid;BEGIN
 PERFORM 1 FROM public.profiles WHERE id=u FOR UPDATE;
 IF NOT EXISTS(SELECT 1 FROM public.squads s JOIN public.xr_sf_squad_settings x ON x.squad_id=s.id WHERE s.id=p_squad AND x.visibility='private' AND x.discoverable AND NOT public.is_blocked(u,s.captain)) THEN RETURN jsonb_build_object('ok',false,'error','Private squad unavailable.');END IF;
 IF EXISTS(SELECT 1 FROM public.squad_members WHERE user_id=u) THEN RETURN jsonb_build_object('ok',false,'error','Already in a squad.');END IF;
 UPDATE public.xr_sf_join_requests SET status='expired' WHERE user_id=u AND status='pending' AND expires_at<=now();
 IF EXISTS(SELECT 1 FROM public.xr_sf_join_requests WHERE squad_id=p_squad AND user_id=u AND status='pending') THEN RETURN jsonb_build_object('ok',true,'request',(SELECT to_jsonb(r) FROM public.xr_sf_join_requests r WHERE squad_id=p_squad AND user_id=u AND status='pending'));END IF;
 IF (SELECT count(*) FROM public.xr_sf_join_requests WHERE user_id=u AND created_at>now()-interval '1 hour')>=10 THEN RETURN jsonb_build_object('ok',false,'error','Request rate limit: ten per hour.');END IF;
 INSERT INTO public.xr_sf_join_requests(squad_id,user_id,note) VALUES(p_squad,u,nullif(btrim(p_note),'')) RETURNING id INTO rid;
 RETURN jsonb_build_object('ok',true,'request',(SELECT to_jsonb(r) FROM public.xr_sf_join_requests r WHERE id=rid));END
$sf$;

CREATE OR REPLACE FUNCTION public.xr_sf_request_list(p_squad uuid DEFAULT NULL) RETURNS jsonb LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path=pg_catalog AS $sf$
DECLARE u uuid:=public.xr_sf_actor();rows jsonb;BEGIN
 IF p_squad IS NOT NULL AND NOT public.xr_sf_is_captain(p_squad) THEN RAISE EXCEPTION 'Captain only.' USING ERRCODE='42501';END IF;
 SELECT coalesce(jsonb_agg(jsonb_build_object('id',r.id,'squad_id',r.squad_id,'requester',public.xr_sf_player_card(r.user_id),'status',CASE WHEN r.status='pending' AND r.expires_at<=now() THEN 'expired' ELSE r.status END,'note',r.note,'created_at',r.created_at,'expires_at',r.expires_at,'decided_at',r.decided_at) ORDER BY r.created_at DESC),'[]'::jsonb) INTO rows FROM (SELECT * FROM public.xr_sf_join_requests WHERE (p_squad IS NULL AND user_id=u) OR (p_squad IS NOT NULL AND squad_id=p_squad) ORDER BY created_at DESC LIMIT 50)r;
 RETURN jsonb_build_object('ok',true,'requests',rows);END
$sf$;

CREATE OR REPLACE FUNCTION public.xr_sf_request_decide(p_requests uuid[],p_accept boolean) RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $sf$
DECLARE u uuid:=public.xr_sf_actor();r public.xr_sf_join_requests;results jsonb:='[]';rid uuid;err text;BEGIN
 IF p_requests IS NULL OR cardinality(p_requests) NOT BETWEEN 1 AND 20 OR p_accept IS NULL THEN RAISE EXCEPTION 'Choose one to twenty requests.';END IF;
 FOR rid IN SELECT DISTINCT unnest(p_requests) ORDER BY 1 LOOP
 BEGIN
 SELECT * INTO r FROM public.xr_sf_join_requests WHERE id=rid FOR UPDATE;
 IF NOT FOUND OR NOT public.xr_sf_is_captain(r.squad_id) THEN RAISE EXCEPTION 'Request unavailable or captain required.';END IF;
 IF r.status<>'pending' THEN RAISE EXCEPTION 'Request already answered.';END IF;
 IF r.expires_at<=clock_timestamp() THEN UPDATE public.xr_sf_join_requests SET status='expired' WHERE id=rid;results:=results||jsonb_build_array(jsonb_build_object('id',rid,'ok',false,'error','Expired.'));CONTINUE;END IF;
 IF public.is_blocked(u,r.user_id) THEN RAISE EXCEPTION 'Player unavailable.';END IF;
 UPDATE public.xr_sf_join_requests SET status=CASE WHEN p_accept THEN 'accepted' ELSE 'rejected' END,decided_at=now(),decided_by=u WHERE id=rid;
 IF p_accept THEN INSERT INTO public.squad_members(squad_id,user_id,role) VALUES(r.squad_id,r.user_id,'member');END IF;
 results:=results||jsonb_build_array(jsonb_build_object('id',rid,'ok',true,'accepted',p_accept));
 EXCEPTION WHEN OTHERS THEN GET STACKED DIAGNOSTICS err=MESSAGE_TEXT;results:=results||jsonb_build_array(jsonb_build_object('id',rid,'ok',false,'error',err));END;
 END LOOP;RETURN jsonb_build_object('ok',true,'results',results);END
$sf$;

CREATE OR REPLACE FUNCTION public.xr_sf_invite_send(p_squad uuid,p_player uuid) RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $sf$
DECLARE u uuid:=public.xr_sf_actor();v bigint;BEGIN
 IF NOT public.xr_sf_is_captain(p_squad) THEN RAISE EXCEPTION 'Captain only.' USING ERRCODE='42501';END IF;
 PERFORM 1 FROM public.squads WHERE id=p_squad FOR UPDATE;
 IF p_player IS NULL OR p_player=u OR public.is_blocked(u,p_player) OR NOT EXISTS(SELECT 1 FROM public.profiles WHERE id=p_player AND NOT coalesce(banned,false)) THEN RETURN jsonb_build_object('ok',false,'error','Player unavailable.');END IF;
 IF EXISTS(SELECT 1 FROM public.squad_members WHERE user_id=p_player) THEN RETURN jsonb_build_object('ok',false,'error','Player already in a squad.');END IF;
 IF (SELECT count(*) FROM public.squad_members WHERE squad_id=p_squad)>=coalesce((SELECT max_members FROM public.xr_sf_squad_settings WHERE squad_id=p_squad),6) THEN RETURN jsonb_build_object('ok',false,'error','Squad is full.');END IF;
 IF (SELECT count(*) FROM public.squad_invites WHERE invited_by=u AND created_at>now()-interval '1 minute')>=10 THEN RETURN jsonb_build_object('ok',false,'error','Invite limit: ten per minute.');END IF;
 UPDATE public.squad_invites SET status='declined' WHERE squad_id=p_squad AND invited=p_player AND status='pending' AND (source IS DISTINCT FROM 'sf' OR expires_at<=clock_timestamp());
 SELECT id INTO v FROM public.squad_invites WHERE squad_id=p_squad AND invited=p_player AND status='pending' AND source='sf' AND expires_at>clock_timestamp() ORDER BY id DESC LIMIT 1;
 IF v IS NULL THEN INSERT INTO public.squad_invites(squad_id,invited,invited_by,expires_at,source) VALUES(p_squad,p_player,u,clock_timestamp()+interval '30 seconds','sf') RETURNING id INTO v;END IF;
 RETURN jsonb_build_object('ok',true,'invite_id',v,'expires_at',(SELECT expires_at FROM public.squad_invites WHERE id=v));END
$sf$;

CREATE OR REPLACE FUNCTION public.xr_sf_invite_list() RETURNS jsonb LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path=pg_catalog AS $sf$
DECLARE u uuid:=public.xr_sf_actor();BEGIN
 RETURN jsonb_build_object('ok',true,'invites',coalesce((SELECT jsonb_agg(jsonb_build_object('id',i.id,'squad_id',i.squad_id,'squad_name',s.name,'inviter',public.xr_sf_player_card(i.invited_by),'created_at',i.created_at,'expires_at',i.expires_at,'status',CASE WHEN i.status='pending' AND i.expires_at<=now() THEN 'expired' ELSE i.status END) ORDER BY i.created_at DESC) FROM (SELECT * FROM public.squad_invites WHERE invited=u AND source='sf' ORDER BY id DESC LIMIT 50)i JOIN public.squads s ON s.id=i.squad_id WHERE NOT public.is_blocked(u,i.invited_by)),'[]'::jsonb));END
$sf$;

CREATE OR REPLACE FUNCTION public.xr_sf_invite_respond(p_invite bigint,p_accept boolean) RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $sf$
DECLARE u uuid:=public.xr_sf_actor();i public.squad_invites;BEGIN
 PERFORM 1 FROM public.profiles WHERE id=u FOR UPDATE;
 SELECT * INTO i FROM public.squad_invites WHERE id=p_invite AND invited=u AND source='sf' FOR UPDATE;
 IF NOT FOUND OR i.status<>'pending' THEN RETURN jsonb_build_object('ok',false,'error','Invite unavailable.');END IF;
 IF i.expires_at<=clock_timestamp() THEN RETURN jsonb_build_object('ok',false,'error','Invite expired.');END IF;
 IF p_accept IS NULL OR public.is_blocked(u,i.invited_by) OR NOT EXISTS(SELECT 1 FROM public.squads WHERE id=i.squad_id AND captain=i.invited_by) THEN RETURN jsonb_build_object('ok',false,'error','Invite unavailable.');END IF;
 IF p_accept THEN INSERT INTO public.squad_members(squad_id,user_id,role) VALUES(i.squad_id,u,'member');END IF;
 UPDATE public.squad_invites SET status=CASE WHEN p_accept THEN 'accepted' ELSE 'declined' END WHERE id=i.id;
 RETURN jsonb_build_object('ok',true,'accepted',p_accept,'squad_id',i.squad_id,'chat_requires_flag',true);END
$sf$;

CREATE OR REPLACE FUNCTION public.xr_sf_member_ready(p_squad uuid,p_ready boolean,p_combat_role text DEFAULT NULL) RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $sf$
DECLARE u uuid:=public.xr_sf_actor();BEGIN
 IF NOT public.xr_sf_is_member(p_squad) OR p_ready IS NULL THEN RAISE EXCEPTION 'Member required.' USING ERRCODE='42501';END IF;
 INSERT INTO public.xr_sf_member_prefs(squad_id,user_id,ready,combat_role) VALUES(p_squad,u,p_ready,p_combat_role) ON CONFLICT(squad_id,user_id) DO UPDATE SET ready=excluded.ready,combat_role=coalesce(excluded.combat_role,public.xr_sf_member_prefs.combat_role),updated_at=now();RETURN jsonb_build_object('ok',true);END
$sf$;

CREATE OR REPLACE FUNCTION public.xr_sf_friends_activity() RETURNS jsonb LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path=pg_catalog AS $sf$
DECLARE u uuid:=public.xr_sf_actor();BEGIN RETURN jsonb_build_object('ok',true,'friends',coalesce((SELECT jsonb_agg(c) FROM (SELECT public.xr_sf_player_card(friend_id)c FROM public.friends WHERE user_id=u ORDER BY created_at DESC LIMIT 50)z WHERE c IS NOT NULL),'[]'::jsonb));END
$sf$;


CREATE OR REPLACE FUNCTION public.xr_sf_plan_get(p_squad uuid) RETURNS jsonb LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path=pg_catalog AS $sf$
DECLARE u uuid:=public.xr_sf_actor();BEGIN
 IF NOT public.xr_sf_is_member(p_squad) THEN RAISE EXCEPTION 'Members only.' USING ERRCODE='42501';END IF;
 RETURN jsonb_build_object('ok',true,'plan',(SELECT to_jsonb(p)-'updated_by' FROM public.xr_sf_strategy_plans p WHERE squad_id=p_squad));END
$sf$;

CREATE OR REPLACE FUNCTION public.xr_sf_plan_save(p_squad uuid,p_body text,p_expected_revision bigint DEFAULT 0) RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $sf$
DECLARE u uuid:=public.xr_sf_actor();v bigint;BEGIN
 IF NOT public.xr_sf_is_captain(p_squad) THEN RAISE EXCEPTION 'Captain only.' USING ERRCODE='42501';END IF;
 PERFORM 1 FROM public.squads WHERE id=p_squad FOR UPDATE;
 SELECT revision INTO v FROM public.xr_sf_strategy_plans WHERE squad_id=p_squad;
 IF coalesce(v,0) IS DISTINCT FROM p_expected_revision THEN RETURN jsonb_build_object('ok',false,'error','Plan changed. Reload before saving.','current_revision',coalesce(v,0));END IF;
 INSERT INTO public.xr_sf_strategy_plans(squad_id,body,updated_by) VALUES(p_squad,btrim(p_body),u) ON CONFLICT(squad_id) DO UPDATE SET body=excluded.body,revision=nextval('public.xr_sf_plan_revision_seq'),updated_by=u,updated_at=now() RETURNING revision INTO v;
 RETURN jsonb_build_object('ok',true,'revision',v);END
$sf$;

CREATE OR REPLACE FUNCTION public.xr_sf_plan_delete(p_squad uuid,p_expected_revision bigint) RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $sf$
DECLARE u uuid:=public.xr_sf_actor();BEGIN
 IF NOT public.xr_sf_is_captain(p_squad) THEN RAISE EXCEPTION 'Captain only.' USING ERRCODE='42501';END IF;
 PERFORM 1 FROM public.squads WHERE id=p_squad FOR UPDATE;
 DELETE FROM public.xr_sf_strategy_plans WHERE squad_id=p_squad AND revision=p_expected_revision;
 IF NOT FOUND THEN RETURN jsonb_build_object('ok',false,'error','Plan not found or revision changed.');END IF;
 RETURN jsonb_build_object('ok',true);END
$sf$;

CREATE OR REPLACE FUNCTION public.xr_sf_chat_list(p_squad uuid,p_after bigint DEFAULT 0,p_limit integer DEFAULT 50) RETURNS jsonb LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path=pg_catalog AS $sf$
DECLARE u uuid:=public.xr_sf_actor();BEGIN
 IF NOT public.xr_sf_is_member(p_squad) THEN RAISE EXCEPTION 'Members only.' USING ERRCODE='42501';END IF;
 IF NOT coalesce((SELECT enabled FROM public.xr_sf_feature_flags WHERE key='squad_chat'),false) THEN RETURN jsonb_build_object('ok',false,'error','Squad chat is not enabled.','enabled',false,'voice_supported',false);END IF;
 RETURN jsonb_build_object('ok',true,'voice_supported',false,'messages',coalesce((SELECT jsonb_agg(to_jsonb(t)) FROM (SELECT m.id,m.body,m.created_at,m.user_id,coalesce(p.ign,p.username) name FROM public.xr_sf_chat_messages m JOIN public.profiles p ON p.id=m.user_id WHERE m.squad_id=p_squad AND m.id>greatest(0,p_after) AND NOT m.hidden AND NOT public.is_blocked(u,m.user_id) ORDER BY m.id LIMIT greatest(1,least(coalesce(p_limit,50),100)))t),'[]'::jsonb));END
$sf$;

CREATE OR REPLACE FUNCTION public.xr_sf_chat_post(p_squad uuid,p_body text) RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $sf$
DECLARE u uuid:=public.xr_sf_actor();v bigint;BEGIN
 IF NOT public.xr_sf_is_member(p_squad) THEN RAISE EXCEPTION 'Members only.' USING ERRCODE='42501';END IF;
 IF NOT coalesce((SELECT enabled FROM public.xr_sf_feature_flags WHERE key='squad_chat'),false) THEN RETURN jsonb_build_object('ok',false,'error','Squad chat is not enabled.','enabled',false);END IF;
 PERFORM 1 FROM public.profiles WHERE id=u FOR UPDATE;
 IF EXISTS(SELECT 1 FROM public.xr_sf_chat_messages WHERE user_id=u AND created_at>clock_timestamp()-interval '2 seconds') OR (SELECT count(*) FROM public.xr_sf_chat_messages WHERE user_id=u AND created_at>now()-interval '1 minute')>=20 THEN RETURN jsonb_build_object('ok',false,'error','Slow down.');END IF;
 INSERT INTO public.xr_sf_chat_messages(squad_id,user_id,body) VALUES(p_squad,u,btrim(p_body)) RETURNING id INTO v;RETURN jsonb_build_object('ok',true,'id',v);END
$sf$;

CREATE OR REPLACE FUNCTION public.xr_sf_tryout_get(p_room uuid) RETURNS jsonb LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path=pg_catalog AS $sf$
DECLARE u uuid:=public.xr_sf_actor();r public.xr_sf_tryout_rooms;n integer;BEGIN
 SELECT * INTO r FROM public.xr_sf_tryout_rooms WHERE id=p_room;IF NOT FOUND OR public.is_blocked(u,r.host_id) OR (r.squad_id IS NOT NULL AND NOT public.xr_sf_public_squad(r.squad_id) AND NOT public.xr_sf_is_member(r.squad_id) AND NOT EXISTS(SELECT 1 FROM public.xr_sf_tryout_members WHERE room_id=r.id AND user_id=u)) THEN RETURN jsonb_build_object('ok',false,'error','Room unavailable.');END IF;
 SELECT count(*) INTO n FROM public.xr_sf_tryout_members WHERE room_id=p_room;
 RETURN jsonb_build_object('ok',true,'room',jsonb_build_object('id',r.id,'room_code',r.room_code,'host_id',r.host_id,'squad_id',r.squad_id,'status',CASE WHEN r.expires_at<=now() THEN 'closed' ELSE r.status END,'mode',r.mode,'map',r.map,'duration_min',r.duration_min,'players',n,'max_players',4,'created_at',r.created_at,'expires_at',r.expires_at,'mine',r.host_id=u,'is_member',EXISTS(SELECT 1 FROM public.xr_sf_tryout_members WHERE room_id=p_room AND user_id=u),'test_recorded',r.test_recorded_at IS NOT NULL,'test_source',CASE WHEN r.test_recorded_at IS NOT NULL THEN 'captain_recorded' END,'test_recorded_at',r.test_recorded_at));END
$sf$;

CREATE OR REPLACE FUNCTION public.xr_sf_tryout_list(p_limit integer DEFAULT 30) RETURNS jsonb LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path=pg_catalog AS $sf$
DECLARE u uuid:=public.xr_sf_actor();BEGIN
 RETURN jsonb_build_object('ok',true,'rooms',coalesce((SELECT jsonb_agg(c) FROM (SELECT public.xr_sf_tryout_get(r.id)->'room' c FROM public.xr_sf_tryout_rooms r WHERE r.status IN ('open','active') AND r.expires_at>now() AND (r.squad_id IS NULL OR public.xr_sf_public_squad(r.squad_id) OR public.xr_sf_is_member(r.squad_id)) AND NOT public.is_blocked(u,r.host_id) ORDER BY r.created_at DESC LIMIT greatest(1,least(coalesce(p_limit,30),50)))z WHERE c IS NOT NULL),'[]'::jsonb));END
$sf$;

CREATE OR REPLACE FUNCTION public.xr_sf_tryout_create(p_mode text,p_map text,p_duration_min integer,p_squad uuid DEFAULT NULL) RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $sf$
DECLARE u uuid:=public.xr_sf_actor();rid uuid;BEGIN
 IF p_squad IS NOT NULL AND NOT public.xr_sf_is_captain(p_squad) THEN RAISE EXCEPTION 'Captain only.' USING ERRCODE='42501';END IF;
 PERFORM 1 FROM public.profiles WHERE id=u FOR UPDATE;
 IF (SELECT count(*) FROM public.xr_sf_tryout_rooms WHERE host_id=u AND status<>'closed' AND expires_at>now())>=2 OR (SELECT count(*) FROM public.xr_sf_tryout_rooms WHERE host_id=u AND created_at>now()-interval '1 hour')>=10 THEN RETURN jsonb_build_object('ok',false,'error','Tryout creation limit.');END IF;
 INSERT INTO public.xr_sf_tryout_rooms(host_id,squad_id,mode,map,duration_min,expires_at) VALUES(u,p_squad,btrim(p_mode),nullif(btrim(p_map),''),p_duration_min,clock_timestamp()+make_interval(mins=>p_duration_min)) RETURNING id INTO rid;
 INSERT INTO public.xr_sf_tryout_members(room_id,user_id) VALUES(rid,u);
 RETURN public.xr_sf_tryout_get(rid);END
$sf$;

CREATE OR REPLACE FUNCTION public.xr_sf_tryout_join(p_room uuid) RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $sf$
DECLARE u uuid:=public.xr_sf_actor();r public.xr_sf_tryout_rooms;BEGIN
 PERFORM 1 FROM public.profiles WHERE id=u FOR UPDATE;
 SELECT * INTO r FROM public.xr_sf_tryout_rooms WHERE id=p_room FOR UPDATE;
 IF NOT FOUND OR r.status<>'open' OR r.expires_at<=clock_timestamp() OR public.is_blocked(u,r.host_id) OR (r.squad_id IS NOT NULL AND NOT public.xr_sf_public_squad(r.squad_id) AND NOT public.xr_sf_is_member(r.squad_id)) THEN RETURN jsonb_build_object('ok',false,'error','Room unavailable.');END IF;
 IF EXISTS(SELECT 1 FROM public.xr_sf_tryout_members WHERE room_id=p_room AND user_id=u) THEN RETURN public.xr_sf_tryout_get(p_room);END IF;
 IF (SELECT count(*) FROM public.xr_sf_tryout_members WHERE room_id=p_room)>=4 THEN RETURN jsonb_build_object('ok',false,'error','Tryout is full.');END IF;
 INSERT INTO public.xr_sf_tryout_members(room_id,user_id) VALUES(p_room,u);RETURN public.xr_sf_tryout_get(p_room);END
$sf$;

CREATE OR REPLACE FUNCTION public.xr_sf_tryout_leave(p_room uuid) RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $sf$
DECLARE u uuid:=public.xr_sf_actor();BEGIN
 PERFORM 1 FROM public.xr_sf_tryout_rooms WHERE id=p_room FOR UPDATE;
 IF EXISTS(SELECT 1 FROM public.xr_sf_tryout_rooms WHERE id=p_room AND host_id=u) THEN RETURN jsonb_build_object('ok',false,'error','Host must close the room.');END IF;
 DELETE FROM public.xr_sf_tryout_members WHERE room_id=p_room AND user_id=u;
 RETURN jsonb_build_object('ok',true);END
$sf$;

CREATE OR REPLACE FUNCTION public.xr_sf_tryout_close(p_room uuid) RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $sf$
DECLARE u uuid:=public.xr_sf_actor();BEGIN
 UPDATE public.xr_sf_tryout_rooms SET status='closed' WHERE id=p_room AND host_id=u;
 IF NOT FOUND THEN RAISE EXCEPTION 'Host only.' USING ERRCODE='42501';END IF;RETURN jsonb_build_object('ok',true);END
$sf$;

CREATE OR REPLACE FUNCTION public.xr_sf_tryout_mark_test(p_room uuid) RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $sf$
DECLARE u uuid:=public.xr_sf_actor();r public.xr_sf_tryout_rooms;BEGIN
 SELECT * INTO r FROM public.xr_sf_tryout_rooms WHERE id=p_room FOR UPDATE;
 IF NOT FOUND OR r.host_id<>u OR (r.squad_id IS NOT NULL AND NOT public.xr_sf_is_captain(r.squad_id)) THEN RAISE EXCEPTION 'Host/captain only.' USING ERRCODE='42501';END IF;
 IF r.expires_at<=clock_timestamp() OR r.status='closed' THEN RETURN jsonb_build_object('ok',false,'error','Room closed.');END IF;
 UPDATE public.xr_sf_tryout_rooms SET status='active',test_recorded_by=u,test_recorded_at=clock_timestamp() WHERE id=p_room;
 RETURN jsonb_build_object('ok',true,'test_source','captain_recorded');END
$sf$;

CREATE OR REPLACE FUNCTION public.xr_sf_call_backup(p_squad uuid,p_role text DEFAULT NULL,p_limit integer DEFAULT 5) RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $sf$
DECLARE u uuid:=public.xr_sf_actor();v timestamptz;target uuid;res jsonb;ids jsonb:='[]';BEGIN
 IF NOT public.xr_sf_is_captain(p_squad) THEN RAISE EXCEPTION 'Captain only.' USING ERRCODE='42501';END IF;
 PERFORM 1 FROM public.squads WHERE id=p_squad FOR UPDATE;
 SELECT last_called_at INTO v FROM public.xr_sf_backup_limits WHERE squad_id=p_squad FOR UPDATE;
 IF v>clock_timestamp()-interval '5 minutes' THEN RETURN jsonb_build_object('ok',false,'error','Backup call limited to once per five minutes.','next_allowed_at',v+interval '5 minutes');END IF;
 INSERT INTO public.xr_sf_backup_limits(squad_id,last_called_at) VALUES(p_squad,clock_timestamp()) ON CONFLICT(squad_id) DO UPDATE SET last_called_at=excluded.last_called_at;
 FOR target IN SELECT f.user_id FROM public.xr_sf_player_prefs f JOIN public.profiles p ON p.id=f.user_id WHERE f.discoverable AND f.free_agent AND f.backup_opt_in AND f.available_from<=now() AND f.available_to>now() AND (p_role IS NULL OR f.combat_role=p_role) AND NOT coalesce(p.banned,false) AND NOT public.is_blocked(u,p.id) AND NOT EXISTS(SELECT 1 FROM public.squad_members WHERE user_id=p.id) ORDER BY f.updated_at DESC,p.id LIMIT greatest(1,least(coalesce(p_limit,5),5)) LOOP
 res:=public.xr_sf_invite_send(p_squad,target);IF (res->>'ok')::boolean THEN ids:=ids||jsonb_build_array(res);END IF;
 END LOOP;
 RETURN jsonb_build_object('ok',true,'invites',ids,'next_allowed_at',clock_timestamp()+interval '5 minutes','delivery','in_app_invite_inbox');END
$sf$;

CREATE OR REPLACE VIEW public.squads_public AS SELECT s.id,
    s.name,
    s.tag,
    s.bio,
    s.created_at,
    cp.username AS captain_username,
    cp.ign AS captain_ign,
    ( SELECT count(*) AS count
           FROM squad_members m
          WHERE m.squad_id = s.id) AS member_count,
    ( SELECT COALESCE(jsonb_agg(jsonb_build_object('username', p.username, 'ign', p.ign, 'role', m.role) ORDER BY m.joined_at), '[]'::jsonb) AS members
           FROM squad_members m
             JOIN profiles p ON p.id = m.user_id
          WHERE m.squad_id = s.id) AS members,
    s.logo_url
   FROM squads s
     JOIN profiles cp ON cp.id = s.captain
  WHERE public.xr_sf_public_squad(s.id)
  ORDER BY s.created_at DESC;

CREATE OR REPLACE FUNCTION public.squad_profile(p_squad uuid)
 RETURNS jsonb
 LANGUAGE plpgsql
 STABLE SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare s squads%rowtype; rec jsonb; mem jsonb;
begin
  if not (public.xr_sf_public_squad(p_squad) or public.xr_sf_is_member(p_squad)) then return jsonb_build_object('ok',false,'error','Squad unavailable.'); end if;
  select * into s from squads where id=p_squad; if not found then return jsonb_build_object('ok',false); end if;
  select coalesce(jsonb_agg(jsonb_build_object('ign',coalesce(p.ign,p.username),'role',m.role,'avatar_url',p.avatar_url) order by m.joined_at),'[]'::jsonb)
    into mem from squad_members m join profiles p on p.id=m.user_id where m.squad_id=p_squad;
  select jsonb_build_object('matches',count(distinct e.match_id),'kills',coalesce(sum(e.kills),0),'points',coalesce(sum(e.points),0),'best_place',min(e.placement))
    into rec from lb_entries e where lower(trim(e.squad))=lower(trim(s.name));
  return jsonb_build_object('ok',true,'id',s.id,'name',s.name,'tag',s.tag,'bio',s.bio,'logo_url',s.logo_url,'members',mem,'record',rec,
    'tournaments',(select coalesce(jsonb_agg(distinct r.tournament_id),'[]'::jsonb) from registrations r where lower(trim(r.squad))=lower(trim(s.name)) and r.status not in ('rejected','cancelled','declined')));
end $function$
;

CREATE OR REPLACE FUNCTION public.squad_board(p_scope text DEFAULT 'global'::text)
 RETURNS jsonb
 LANGUAGE plpgsql
 STABLE SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare u uuid := auth.uid(); mp profiles%rowtype;
begin
  if u is not null then select * into mp from profiles where id=u; end if;
  if p_scope in ('state','district') and (u is null or mp.geo_state is null) then return jsonb_build_object('ok',false,'error','location_required'); end if;
  return jsonb_build_object('ok',true,'scope',p_scope,'rows',coalesce((select jsonb_agg(t order by (t->>'rank')::int) from (
    select jsonb_build_object('rank',rank() over (order by sum(coalesce(g.xp,0)) desc),'id',s.id,'name',s.name,'tag',s.tag,'logo',s.logo_url,'members',count(*),'xp',sum(coalesce(g.xp,0)),
      'state',cp.geo_state,'district',cp.geo_district) t
    from squads s join squad_members m on m.squad_id=s.id left join player_game_stats g on g.user_id=m.user_id left join profiles cp on cp.id=s.captain
    where public.xr_sf_public_squad(s.id) and (p_scope='global' or (p_scope='state' and cp.geo_state=mp.geo_state) or (p_scope='district' and cp.geo_state=mp.geo_state and cp.geo_district=mp.geo_district))
    group by s.id,s.name,s.tag,s.logo_url,cp.geo_state,cp.geo_district order by sum(coalesce(g.xp,0)) desc limit 50) z),'[]'::jsonb));
end $function$
;

CREATE OR REPLACE FUNCTION public.squad_battle_board()
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
begin
  return jsonb_build_object('ok', true, 'rows', coalesce((
    with match_pts as (
      select squad, sum(points) as pts, sum(kills) as kills, count(*) filter (where placement = 1) as wins
      from lb_entries group by squad
    ),
    squad_xp as (
      select s.name, coalesce(sum(g.xp),0) as xp
      from squads s
      left join squad_members m on m.squad_id = s.id
      left join player_game_stats g on g.user_id = m.user_id
      where public.xr_sf_public_squad(s.id) group by s.name
    )
    select jsonb_agg(jsonb_build_object(
      'squad', coalesce(mp.squad, sx.name),
      'match_points', coalesce(mp.pts,0), 'kills', coalesce(mp.kills,0), 'wins', coalesce(mp.wins,0),
      'squad_xp', coalesce(sx.xp,0),
      'total', coalesce(mp.pts,0) + coalesce(sx.xp,0)
    ) order by (coalesce(mp.pts,0) + coalesce(sx.xp,0)) desc)
    from match_pts mp full outer join squad_xp sx on sx.name = mp.squad
  ), '[]'::jsonb));
end $function$
;

CREATE OR REPLACE FUNCTION public.matchmaker_tick()
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare v record; v_lobbies jsonb; v_squads jsonb; v_n int := 0; v_week text := to_char(now(), 'IYYY-IW');
begin
  select jsonb_agg(jsonb_build_object('kind','lobby','label','LFG lobby: ' || coalesce(l.mode,'BR') || ' (' || coalesce(l.rank_min,'any') || '-' || coalesce(l.rank_max,'any') || ')', 'id', l.id))
    into v_lobbies from public.lfg_lobbies l where l.status = 'open' limit 3;
  select jsonb_agg(jsonb_build_object('kind','squad','label','Squad recruiting: ' || s.name, 'id', s.id))
    into v_squads from (
      select s.id, s.name, count(m.user_id) as members from public.squads s
      left join public.squad_members m on m.squad_id = s.id
      where public.xr_sf_public_squad(s.id) group by s.id, s.name having count(m.user_id) < 4 order by s.created_at desc limit 3) s;
  if v_lobbies is null and v_squads is null then return jsonb_build_object('ok', true, 'sent', 0, 'note', 'nothing open to suggest'); end if;
  for v in
    select p.id, coalesce(p.ign, p.username, 'player') as name
    from public.profiles p
    where p.id is not null
      and not exists (select 1 from public.squad_members m where m.user_id = p.id)
      and exists (select 1 from public.player_game_stats s where s.user_id = p.id and s.updated_at > now() - interval '14 days')
    limit 25
  loop
    perform public.notify(v.id, 'matchmaker', 'Squad suggestions for you',
      'You are squadless. Open spots now: ' ||
        coalesce((select string_agg(x->>'label', ' · ') from jsonb_array_elements(coalesce(v_lobbies,'[]'::jsonb) || coalesce(v_squads,'[]'::jsonb)) x), 'check the Squads page') ||
        '. Find them on the Squads page.',
      'squads.html', 'mm:' || v_week);
    v_n := v_n + 1;
  end loop;
  if v_n > 0 then
    insert into public.audit_log (actor, action, details) values ('squad-matchmaker', 'suggestions_sent', jsonb_build_object('players', v_n));
  end if;
  return jsonb_build_object('ok', true, 'sent', v_n);
end $function$
;

CREATE OR REPLACE FUNCTION public.wars_board()
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
begin
  return jsonb_build_object('ok', true, 'wars', coalesce((select jsonb_agg(jsonb_build_object(
      'id', w.id, 'challenger', c.name, 'opponent', o.name, 'status', w.status,
      'scheduled_at', w.scheduled_at, 'result', w.result,
      'winner', case when w.winner_id = c.id then c.name when w.winner_id = o.id then o.name else null end,
      'challenger_id', w.challenger_id, 'opponent_id', w.opponent_id) order by w.created_at desc)
    from public.squad_wars w join public.squads c on c.id = w.challenger_id join public.squads o on o.id = w.opponent_id where public.xr_sf_public_squad(c.id) and public.xr_sf_public_squad(o.id)), '[]'::jsonb));
end $function$
;

CREATE OR REPLACE FUNCTION public.scrim_find()
 RETURNS jsonb
 LANGUAGE plpgsql
 STABLE SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare u uuid := auth.uid(); mp profiles%rowtype;
begin
  if u is null then return jsonb_build_object('ok',false,'error','Log in first.'); end if;
  select * into mp from profiles where id=u; if mp.geo_district is null then return jsonb_build_object('ok',false,'error','location_required'); end if;
  return jsonb_build_object('ok',true,'district',mp.geo_district,'rows',coalesce((select jsonb_agg(jsonb_build_object('id',r.id,'squad',s.name,'tag',s.tag,'day',r.day_text,'time',r.time_text,'note',r.note,'mine',r.user_id=u) order by r.created_at desc)
    from scrim_requests r join squads s on s.id=r.squad_id where public.xr_sf_public_squad(s.id) and r.status='open' and r.expires_at>now() and r.district=mp.geo_district and r.state=mp.geo_state),'[]'::jsonb));
end $function$
;

CREATE OR REPLACE FUNCTION public.my_squad_invites()
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare v_uid uuid := auth.uid();
begin
  if v_uid is null then return jsonb_build_object('ok', false, 'error', 'Log in first.'); end if;
  return jsonb_build_object('ok', true, 'invites', coalesce((
    select jsonb_agg(jsonb_build_object('id', i.id, 'squad', s.name, 'tag', s.tag, 'by', coalesce(p.ign, p.username)))
    from public.squad_invites i
    join public.squads s on s.id = i.squad_id
    left join public.profiles p on p.id = i.invited_by
    where i.invited = v_uid and i.status = 'pending' and (i.expires_at is null or i.expires_at>clock_timestamp())), '[]'::jsonb));
end $function$
;

CREATE OR REPLACE FUNCTION public.squad_invite(p_name text)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare v_uid uuid := auth.uid(); v_squad uuid; v_target uuid; v_count int;
begin
  if v_uid is null then return jsonb_build_object('ok', false, 'error', 'Log in first.'); end if;
  select squad_id into v_squad from public.squad_members where user_id = v_uid and role = 'captain';
  if v_squad is null then return jsonb_build_object('ok', false, 'error', 'Only the squad captain can invite players.'); end if;
  if not public.xr_sf_public_squad(v_squad) then return jsonb_build_object('ok',false,'error','Use Squad Finder invites for a private squad.');end if;
  select count(*) into v_count from public.squad_members where squad_id = v_squad;
  if v_count >= 6 then return jsonb_build_object('ok', false, 'error', 'Squad is full (6 players).'); end if;
  select id into v_target from public.profiles where lower(username) = lower(trim(coalesce(p_name, ''))) or lower(ign) = lower(trim(coalesce(p_name, ''))) limit 1;
  if v_target is null then return jsonb_build_object('ok', false, 'error', 'No player with that name. They need an account first.'); end if;
  if v_target = v_uid then return jsonb_build_object('ok', false, 'error', 'You are already the captain.'); end if;
  if exists (select 1 from public.squad_members where user_id = v_target) then return jsonb_build_object('ok', false, 'error', 'That player is already in a squad.'); end if;
  if exists (select 1 from public.squad_invites where squad_id = v_squad and invited = v_target and status = 'pending') then
    return jsonb_build_object('ok', false, 'error', 'Invite already sent.'); end if;
  insert into public.squad_invites (squad_id, invited, invited_by) values (v_squad, v_target, v_uid);
  return jsonb_build_object('ok', true);
end $function$
;

CREATE OR REPLACE FUNCTION public.join_squad(p_squad uuid) RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $sf$
BEGIN RETURN public.xr_sf_join_public(p_squad);END
$sf$;

DO $sfgrants$ DECLARE r record;BEGIN
 FOR r IN SELECT p.oid::regprocedure sig,p.proname FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname='public' AND p.proname LIKE 'xr_sf_%' LOOP
 EXECUTE format('REVOKE ALL ON FUNCTION %s FROM PUBLIC,anon,authenticated',r.sig);
 END LOOP;
END $sfgrants$;

DO $g$ DECLARE sig regprocedure;BEGIN SELECT p.oid::regprocedure INTO sig FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname='public' AND p.proname='xr_sf_role_catalog';EXECUTE format('GRANT EXECUTE ON FUNCTION %s TO authenticated',sig);END $g$;

DO $g$ DECLARE sig regprocedure;BEGIN SELECT p.oid::regprocedure INTO sig FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname='public' AND p.proname='xr_sf_player_preferences';EXECUTE format('GRANT EXECUTE ON FUNCTION %s TO authenticated',sig);END $g$;

DO $g$ DECLARE sig regprocedure;BEGIN SELECT p.oid::regprocedure INTO sig FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname='public' AND p.proname='xr_sf_presence_set';EXECUTE format('GRANT EXECUTE ON FUNCTION %s TO authenticated',sig);END $g$;

DO $g$ DECLARE sig regprocedure;BEGIN SELECT p.oid::regprocedure INTO sig FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname='public' AND p.proname='xr_sf_player_get';EXECUTE format('GRANT EXECUTE ON FUNCTION %s TO authenticated',sig);END $g$;

DO $g$ DECLARE sig regprocedure;BEGIN SELECT p.oid::regprocedure INTO sig FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname='public' AND p.proname='xr_sf_player_list';EXECUTE format('GRANT EXECUTE ON FUNCTION %s TO authenticated',sig);END $g$;

DO $g$ DECLARE sig regprocedure;BEGIN SELECT p.oid::regprocedure INTO sig FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname='public' AND p.proname='xr_sf_squad_settings';EXECUTE format('GRANT EXECUTE ON FUNCTION %s TO authenticated',sig);END $g$;

DO $g$ DECLARE sig regprocedure;BEGIN SELECT p.oid::regprocedure INTO sig FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname='public' AND p.proname='xr_sf_squad_create';EXECUTE format('GRANT EXECUTE ON FUNCTION %s TO authenticated',sig);END $g$;

DO $g$ DECLARE sig regprocedure;BEGIN SELECT p.oid::regprocedure INTO sig FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname='public' AND p.proname='xr_sf_squad_get';EXECUTE format('GRANT EXECUTE ON FUNCTION %s TO authenticated',sig);END $g$;

DO $g$ DECLARE sig regprocedure;BEGIN SELECT p.oid::regprocedure INTO sig FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname='public' AND p.proname='xr_sf_squad_list';EXECUTE format('GRANT EXECUTE ON FUNCTION %s TO authenticated',sig);END $g$;

DO $g$ DECLARE sig regprocedure;BEGIN SELECT p.oid::regprocedure INTO sig FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname='public' AND p.proname='xr_sf_join_public';EXECUTE format('GRANT EXECUTE ON FUNCTION %s TO authenticated',sig);END $g$;

DO $g$ DECLARE sig regprocedure;BEGIN SELECT p.oid::regprocedure INTO sig FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname='public' AND p.proname='xr_sf_join_request';EXECUTE format('GRANT EXECUTE ON FUNCTION %s TO authenticated',sig);END $g$;

DO $g$ DECLARE sig regprocedure;BEGIN SELECT p.oid::regprocedure INTO sig FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname='public' AND p.proname='xr_sf_request_list';EXECUTE format('GRANT EXECUTE ON FUNCTION %s TO authenticated',sig);END $g$;

DO $g$ DECLARE sig regprocedure;BEGIN SELECT p.oid::regprocedure INTO sig FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname='public' AND p.proname='xr_sf_request_decide';EXECUTE format('GRANT EXECUTE ON FUNCTION %s TO authenticated',sig);END $g$;

DO $g$ DECLARE sig regprocedure;BEGIN SELECT p.oid::regprocedure INTO sig FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname='public' AND p.proname='xr_sf_invite_send';EXECUTE format('GRANT EXECUTE ON FUNCTION %s TO authenticated',sig);END $g$;

DO $g$ DECLARE sig regprocedure;BEGIN SELECT p.oid::regprocedure INTO sig FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname='public' AND p.proname='xr_sf_invite_list';EXECUTE format('GRANT EXECUTE ON FUNCTION %s TO authenticated',sig);END $g$;

DO $g$ DECLARE sig regprocedure;BEGIN SELECT p.oid::regprocedure INTO sig FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname='public' AND p.proname='xr_sf_invite_respond';EXECUTE format('GRANT EXECUTE ON FUNCTION %s TO authenticated',sig);END $g$;

DO $g$ DECLARE sig regprocedure;BEGIN SELECT p.oid::regprocedure INTO sig FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname='public' AND p.proname='xr_sf_member_ready';EXECUTE format('GRANT EXECUTE ON FUNCTION %s TO authenticated',sig);END $g$;

DO $g$ DECLARE sig regprocedure;BEGIN SELECT p.oid::regprocedure INTO sig FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname='public' AND p.proname='xr_sf_friends_activity';EXECUTE format('GRANT EXECUTE ON FUNCTION %s TO authenticated',sig);END $g$;

DO $g$ DECLARE sig regprocedure;BEGIN SELECT p.oid::regprocedure INTO sig FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname='public' AND p.proname='xr_sf_plan_get';EXECUTE format('GRANT EXECUTE ON FUNCTION %s TO authenticated',sig);END $g$;

DO $g$ DECLARE sig regprocedure;BEGIN SELECT p.oid::regprocedure INTO sig FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname='public' AND p.proname='xr_sf_plan_save';EXECUTE format('GRANT EXECUTE ON FUNCTION %s TO authenticated',sig);END $g$;

DO $g$ DECLARE sig regprocedure;BEGIN SELECT p.oid::regprocedure INTO sig FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname='public' AND p.proname='xr_sf_plan_delete';EXECUTE format('GRANT EXECUTE ON FUNCTION %s TO authenticated',sig);END $g$;

DO $g$ DECLARE sig regprocedure;BEGIN SELECT p.oid::regprocedure INTO sig FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname='public' AND p.proname='xr_sf_chat_list';EXECUTE format('GRANT EXECUTE ON FUNCTION %s TO authenticated',sig);END $g$;

DO $g$ DECLARE sig regprocedure;BEGIN SELECT p.oid::regprocedure INTO sig FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname='public' AND p.proname='xr_sf_chat_post';EXECUTE format('GRANT EXECUTE ON FUNCTION %s TO authenticated',sig);END $g$;

DO $g$ DECLARE sig regprocedure;BEGIN SELECT p.oid::regprocedure INTO sig FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname='public' AND p.proname='xr_sf_tryout_get';EXECUTE format('GRANT EXECUTE ON FUNCTION %s TO authenticated',sig);END $g$;

DO $g$ DECLARE sig regprocedure;BEGIN SELECT p.oid::regprocedure INTO sig FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname='public' AND p.proname='xr_sf_tryout_list';EXECUTE format('GRANT EXECUTE ON FUNCTION %s TO authenticated',sig);END $g$;

DO $g$ DECLARE sig regprocedure;BEGIN SELECT p.oid::regprocedure INTO sig FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname='public' AND p.proname='xr_sf_tryout_create';EXECUTE format('GRANT EXECUTE ON FUNCTION %s TO authenticated',sig);END $g$;

DO $g$ DECLARE sig regprocedure;BEGIN SELECT p.oid::regprocedure INTO sig FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname='public' AND p.proname='xr_sf_tryout_join';EXECUTE format('GRANT EXECUTE ON FUNCTION %s TO authenticated',sig);END $g$;

DO $g$ DECLARE sig regprocedure;BEGIN SELECT p.oid::regprocedure INTO sig FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname='public' AND p.proname='xr_sf_tryout_leave';EXECUTE format('GRANT EXECUTE ON FUNCTION %s TO authenticated',sig);END $g$;

DO $g$ DECLARE sig regprocedure;BEGIN SELECT p.oid::regprocedure INTO sig FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname='public' AND p.proname='xr_sf_tryout_close';EXECUTE format('GRANT EXECUTE ON FUNCTION %s TO authenticated',sig);END $g$;

DO $g$ DECLARE sig regprocedure;BEGIN SELECT p.oid::regprocedure INTO sig FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname='public' AND p.proname='xr_sf_tryout_mark_test';EXECUTE format('GRANT EXECUTE ON FUNCTION %s TO authenticated',sig);END $g$;

DO $g$ DECLARE sig regprocedure;BEGIN SELECT p.oid::regprocedure INTO sig FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname='public' AND p.proname='xr_sf_call_backup';EXECUTE format('GRANT EXECUTE ON FUNCTION %s TO authenticated',sig);END $g$;

GRANT EXECUTE ON FUNCTION public.xr_sf_public_squad(uuid),public.xr_sf_is_member(uuid) TO anon,authenticated;

NOTIFY pgrst,'reload schema';

COMMIT;
