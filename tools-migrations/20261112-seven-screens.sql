-- XARENA additive seven-screen backend, 2026-11-12.
-- Existing rows are not backfilled, published, verified or deleted.
BEGIN;
ALTER TABLE public.registrations ADD COLUMN IF NOT EXISTS slot_no integer CHECK (slot_no BETWEEN 1 AND 48);
CREATE UNIQUE INDEX IF NOT EXISTS registrations_active_slot_uniq ON public.registrations(tournament_id,slot_no) WHERE slot_no IS NOT NULL AND status NOT IN ('rejected','cancelled','declined','waitlist');
CREATE TABLE public.xr_slot_claims (
 tournament_id text NOT NULL REFERENCES public.tournaments(id) ON DELETE CASCADE,
 slot_no integer NOT NULL CHECK(slot_no BETWEEN 1 AND 48),
 user_id uuid NOT NULL REFERENCES auth.users(id),
 expires_at timestamptz NOT NULL,
 registration_id text UNIQUE REFERENCES public.registrations(id) ON DELETE CASCADE,
 PRIMARY KEY(tournament_id,slot_no), UNIQUE(tournament_id,user_id)
);
CREATE TABLE public.xr_registration_players (
 registration_id text NOT NULL REFERENCES public.registrations(id) ON DELETE CASCADE,
 tournament_id text NOT NULL REFERENCES public.tournaments(id) ON DELETE CASCADE,
 player_no integer NOT NULL CHECK(player_no BETWEEN 1 AND 4),
 ign text NOT NULL CHECK(char_length(btrim(ign)) BETWEEN 2 AND 40),
 uid text NOT NULL CHECK(uid ~ '^[0-9]{5,15}$'),
 PRIMARY KEY(registration_id,player_no), UNIQUE(tournament_id,uid)
);
CREATE TABLE public.xr_mvp_votes (
 match_id uuid NOT NULL REFERENCES public.matches(id) ON DELETE CASCADE,
 user_id uuid NOT NULL REFERENCES auth.users(id),
 entry_id uuid NOT NULL REFERENCES public.lb_entries(id) ON DELETE CASCADE,
 created_at timestamptz NOT NULL DEFAULT now(), PRIMARY KEY(match_id,user_id)
);
CREATE INDEX xr_mvp_entry_idx ON public.xr_mvp_votes(match_id,entry_id);
CREATE TABLE public.xr_kill_feed (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 match_id uuid NOT NULL REFERENCES public.matches(id) ON DELETE CASCADE,
 killer text NOT NULL CHECK(char_length(btrim(killer)) BETWEEN 2 AND 40),
 victim text NOT NULL CHECK(char_length(btrim(victim)) BETWEEN 2 AND 40),
 weapon text CHECK(char_length(weapon)<=40),
 elapsed_seconds integer CHECK(elapsed_seconds BETWEEN 0 AND 86400),
 created_by uuid NOT NULL REFERENCES auth.users(id),
 hidden boolean NOT NULL DEFAULT false,
 created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX xr_kill_match_idx ON public.xr_kill_feed(match_id,id);
ALTER TABLE public.xr_slot_claims ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.xr_registration_players ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.xr_mvp_votes ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.xr_kill_feed ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.xr_slot_claims, public.xr_registration_players,public.xr_mvp_votes,public.xr_kill_feed FROM PUBLIC,anon,authenticated;
REVOKE ALL ON SEQUENCE public.xr_kill_feed_id_seq FROM PUBLIC,anon,authenticated;

CREATE FUNCTION public.xr_slot_list(p_tournament text) RETURNS jsonb
LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE t public.tournaments; cap integer; me uuid:=auth.uid();
BEGIN
 SELECT * INTO t FROM public.tournaments WHERE id=p_tournament;
 IF NOT FOUND THEN RETURN jsonb_build_object('ok',false,'error','Tournament not found.');END IF;
 IF t.slots IS NULL OR t.slots<1 THEN RETURN jsonb_build_object('ok',false,'error','Tournament capacity is not set.');END IF;
 cap:=least(t.slots,48);
 RETURN jsonb_build_object('ok',true,'capacity',cap,'registered',(SELECT count(*) FROM public.registrations WHERE tournament_id=p_tournament AND status NOT IN ('rejected','cancelled','declined','waitlist')),'hold_seconds',300,
 'my_slot',(SELECT c.slot_no FROM public.xr_slot_claims c LEFT JOIN public.registrations r ON r.id=c.registration_id WHERE c.tournament_id=p_tournament AND c.user_id=me AND ((c.registration_id IS NULL AND c.expires_at>now()) OR (r.status NOT IN ('rejected','cancelled','declined','waitlist')))),
 'slots',(SELECT jsonb_agg(jsonb_build_object('slot_no',g,'state',CASE WHEN c.registration_id IS NOT NULL THEN 'booked' WHEN c.user_id IS NOT NULL THEN 'held' ELSE 'available' END,'mine',coalesce(c.user_id=me,false),'squad',r.squad) ORDER BY g)
 FROM generate_series(1,cap) g LEFT JOIN public.xr_slot_claims c ON c.tournament_id=p_tournament AND c.slot_no=g AND ((c.registration_id IS NULL AND c.expires_at>now()) OR EXISTS(SELECT 1 FROM public.registrations r0 WHERE r0.id=c.registration_id AND r0.status NOT IN ('rejected','cancelled','declined','waitlist'))) LEFT JOIN public.registrations r ON r.id=c.registration_id));
END $$;

CREATE FUNCTION public.xr_slot_hold(p_tournament text,p_slot integer) RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE t public.tournaments; me uuid:=auth.uid(); c public.xr_slot_claims; n integer; rid text;
BEGIN
 IF me IS NULL THEN RETURN jsonb_build_object('ok',false,'error','Log in first.');END IF;
 IF NOT EXISTS(SELECT 1 FROM public.profiles WHERE id=me AND phone_verified AND NOT coalesce(banned,false)) THEN RETURN jsonb_build_object('ok',false,'error','A phone-verified, unrestricted account is required.');END IF;
 SELECT * INTO t FROM public.tournaments WHERE id=p_tournament FOR UPDATE;
 IF NOT FOUND THEN RETURN jsonb_build_object('ok',false,'error','Tournament not found.');END IF;
 IF NOT coalesce(t.reg_open,false) OR coalesce(t.status,'') NOT IN ('upcoming','live') THEN RETURN jsonb_build_object('ok',false,'error','Registration is closed for this tournament.');END IF;
 IF p_slot IS NULL OR p_slot<1 OR p_slot>least(coalesce(t.slots,0),48) THEN RETURN jsonb_build_object('ok',false,'error','Choose a valid tournament slot.');END IF;
 DELETE FROM public.xr_slot_claims WHERE tournament_id=p_tournament AND ((registration_id IS NULL AND expires_at<=now()) OR (registration_id IS NOT NULL AND EXISTS(SELECT 1 FROM public.registrations WHERE id=registration_id AND status IN ('rejected','cancelled','declined','waitlist'))));
 SELECT * INTO c FROM public.xr_slot_claims WHERE tournament_id=p_tournament AND user_id=me;
 IF c.registration_id IS NOT NULL THEN
  IF c.slot_no=p_slot THEN RETURN jsonb_build_object('ok',true,'slot_no',c.slot_no,'expires_at',c.expires_at,'confirmed',true);END IF;
  RETURN jsonb_build_object('ok',false,'error','Your registration already has a confirmed slot.');
 END IF;
 IF EXISTS(SELECT 1 FROM public.xr_slot_claims WHERE tournament_id=p_tournament AND slot_no=p_slot AND user_id<>me) THEN RETURN jsonb_build_object('ok',false,'error','That slot is no longer available.');END IF;
 SELECT id INTO rid FROM public.registrations WHERE tournament_id=p_tournament AND user_id=me AND status NOT IN ('rejected','cancelled','declined','waitlist');
 SELECT (SELECT count(*) FROM public.registrations WHERE tournament_id=p_tournament AND status NOT IN ('rejected','cancelled','declined','waitlist'))+(SELECT count(*) FROM public.xr_slot_claims WHERE tournament_id=p_tournament AND registration_id IS NULL AND user_id<>me) INTO n;
 IF rid IS NULL AND n>=t.slots THEN RETURN jsonb_build_object('ok',false,'error','No tournament capacity is available.');END IF;
 DELETE FROM public.xr_slot_claims WHERE tournament_id=p_tournament AND user_id=me AND registration_id IS NULL;
 INSERT INTO public.xr_slot_claims(tournament_id,slot_no,user_id,expires_at) VALUES(p_tournament,p_slot,me,now()+interval '5 minutes') RETURNING * INTO c;
 RETURN jsonb_build_object('ok',true,'slot_no',p_slot,'expires_at',c.expires_at,'confirmed',false);
END $$;

CREATE FUNCTION public.xr_slot_release(p_tournament text) RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
BEGIN
 IF auth.uid() IS NULL THEN RETURN jsonb_build_object('ok',false,'error','Log in first.');END IF;
 PERFORM 1 FROM public.tournaments WHERE id=p_tournament FOR UPDATE;
 DELETE FROM public.xr_slot_claims WHERE tournament_id=p_tournament AND user_id=auth.uid() AND registration_id IS NULL;
 RETURN jsonb_build_object('ok',true);
END $$;

CREATE FUNCTION public.xr_slot_confirm(p_tournament text,p_slot integer,p_registration jsonb,p_players jsonb) RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE me uuid:=auth.uid(); t public.tournaments; r public.registrations; c public.xr_slot_claims; p jsonb; j jsonb; i integer:=0; rid text; ids text[]:='{}'; cap_uid text;
BEGIN
 IF me IS NULL THEN RETURN jsonb_build_object('ok',false,'error','Log in first.');END IF;
 IF NOT EXISTS(SELECT 1 FROM public.profiles WHERE id=me AND phone_verified AND NOT coalesce(banned,false)) THEN RETURN jsonb_build_object('ok',false,'error','A phone-verified, unrestricted account is required.');END IF;
 SELECT * INTO t FROM public.tournaments WHERE id=p_tournament FOR UPDATE;
 IF NOT FOUND THEN RETURN jsonb_build_object('ok',false,'error','Tournament not found.');END IF;
 IF NOT coalesce(t.reg_open,false) OR coalesce(t.status,'') NOT IN ('upcoming','live') THEN RETURN jsonb_build_object('ok',false,'error','Registration is closed for this tournament.');END IF;
 IF p_slot IS NULL OR p_slot NOT BETWEEN 1 AND least(coalesce(t.slots,0),48) THEN RETURN jsonb_build_object('ok',false,'error','Choose a valid tournament slot.');END IF;
 SELECT * INTO c FROM public.xr_slot_claims WHERE tournament_id=p_tournament AND slot_no=p_slot AND user_id=me FOR UPDATE;
 IF NOT FOUND OR (c.registration_id IS NULL AND c.expires_at<=now()) THEN RETURN jsonb_build_object('ok',false,'error','Your slot hold expired or belongs to another account. Choose it again.');END IF;
 SELECT * INTO r FROM public.registrations WHERE tournament_id=p_tournament AND user_id=me;
 IF FOUND AND r.status IN ('rejected','cancelled','declined','waitlist') THEN RETURN jsonb_build_object('ok',false,'error','Your existing registration needs owner review before slot confirmation.');END IF;
 IF r.slot_no IS NOT NULL AND r.slot_no<>p_slot THEN RETURN jsonb_build_object('ok',false,'error','Your registration already has a different slot.');END IF;
 IF jsonb_typeof(p_players) IS DISTINCT FROM 'array' THEN RETURN jsonb_build_object('ok',false,'error','Supply an array of 1 to 4 players.');END IF;
 IF jsonb_array_length(p_players) NOT BETWEEN 1 AND 4 THEN RETURN jsonb_build_object('ok',false,'error','Supply 1 to 4 players.');END IF;
 cap_uid:=coalesce(r.uid,btrim(p_registration->>'uid'));
 FOR p IN SELECT value FROM jsonb_array_elements(p_players) LOOP
  IF jsonb_typeof(p) IS DISTINCT FROM 'object' OR coalesce(btrim(p->>'uid'),'') !~ '^[0-9]{5,15}$' OR coalesce(char_length(btrim(p->>'ign')),0) NOT BETWEEN 2 AND 40 THEN RETURN jsonb_build_object('ok',false,'error','Every player needs an IGN of 2-40 characters and a UID of 5-15 digits.');END IF;
  IF btrim(p->>'uid')=ANY(ids) THEN RETURN jsonb_build_object('ok',false,'error','Player UIDs must be unique.');END IF;
  ids:=array_append(ids,btrim(p->>'uid'));
 END LOOP;
 IF cap_uid IS NULL OR NOT cap_uid=ANY(ids) THEN RETURN jsonb_build_object('ok',false,'error','Include the registered captain UID in the player list.');END IF;
 IF EXISTS(SELECT 1 FROM public.registrations WHERE tournament_id=p_tournament AND uid=ANY(ids) AND id IS DISTINCT FROM r.id) OR EXISTS(SELECT 1 FROM public.xr_registration_players WHERE tournament_id=p_tournament AND uid=ANY(ids) AND registration_id IS DISTINCT FROM r.id) THEN RETURN jsonb_build_object('ok',false,'error','A player UID is already registered to another squad in this tournament.');END IF;
 IF r.id IS NULL THEN
  IF coalesce(t.entry_cost_ap,0)>0 THEN RETURN jsonb_build_object('ok',false,'error','Complete the existing AP entry flow before choosing a slot.');END IF;
  IF jsonb_typeof(p_registration) IS DISTINCT FROM 'object' OR coalesce(btrim(p_registration->>'squad'),'')='' OR coalesce(btrim(p_registration->>'captain'),'')='' OR coalesce(btrim(p_registration->>'ign'),'')='' OR coalesce(btrim(p_registration->>'phone'),'')='' THEN RETURN jsonb_build_object('ok',false,'error','Squad, captain, IGN, UID and phone are required.');END IF;
  IF (SELECT count(*) FROM public.registrations WHERE tournament_id=p_tournament AND status NOT IN ('rejected','cancelled','declined','waitlist'))>=t.slots THEN RETURN jsonb_build_object('ok',false,'error','This tournament filled while the slot was held.');END IF;
  rid:='XR-'||gen_random_uuid()::text;
  j:=public.register_squad(rid,p_tournament,btrim(p_registration->>'squad'),btrim(p_registration->>'captain'),cap_uid,btrim(p_registration->>'ign'),btrim(p_registration->>'phone'),nullif(btrim(p_registration->>'proof_path'),''),'native-slot-v1',NULL);
  IF NOT coalesce((j->>'ok')::boolean,false) THEN RETURN j;END IF;
  SELECT * INTO r FROM public.registrations WHERE id=rid;
 END IF;
 -- A confirmed roster is immutable through the player API; owner review is required to change it.
 IF c.registration_id IS NOT NULL THEN
  IF p_players IS DISTINCT FROM (SELECT jsonb_agg(jsonb_build_object('ign',ign,'uid',uid) ORDER BY player_no) FROM public.xr_registration_players WHERE registration_id=r.id) THEN RETURN jsonb_build_object('ok',false,'error','The confirmed roster cannot be changed here. Ask the owner.');END IF;
  RETURN jsonb_build_object('ok',true,'id',r.id,'slot_no',p_slot,'status',r.status,'verified',r.verified,'status_token',r.status_token);
 END IF;
 UPDATE public.registrations SET slot_no=p_slot WHERE id=r.id;
 FOR p IN SELECT value FROM jsonb_array_elements(p_players) LOOP
  i:=i+1;
  INSERT INTO public.xr_registration_players(registration_id,tournament_id,player_no,ign,uid) VALUES(r.id,p_tournament,i,btrim(p->>'ign'),btrim(p->>'uid'));
 END LOOP;
 UPDATE public.xr_slot_claims SET registration_id=r.id WHERE tournament_id=p_tournament AND slot_no=p_slot AND user_id=me;
 RETURN jsonb_build_object('ok',true,'id',r.id,'slot_no',p_slot,'status',r.status,'verified',r.verified,'status_token',r.status_token);
EXCEPTION WHEN unique_violation THEN RETURN jsonb_build_object('ok',false,'error','A slot or player was taken by another registration. Refresh and try again.');
END $$;

CREATE FUNCTION public.xr_slot_my(p_tournament text) RETURNS jsonb
LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path=pg_catalog AS $$
BEGIN
 IF auth.uid() IS NULL THEN RETURN jsonb_build_object('ok',false,'error','Log in first.');END IF;
 RETURN jsonb_build_object('ok',true,'registration',(SELECT jsonb_build_object('id',r.id,'slot_no',r.slot_no,'squad',r.squad,'captain',r.captain,'ign',r.ign,'uid',r.uid,'phone',r.phone,'status',r.status,'verified',r.verified,'hold',r.hold,'players',coalesce((SELECT jsonb_agg(jsonb_build_object('player_no',player_no,'ign',ign,'uid',uid) ORDER BY player_no) FROM public.xr_registration_players WHERE registration_id=r.id),'[]'::jsonb)) FROM public.registrations r WHERE tournament_id=p_tournament AND user_id=auth.uid()));
END $$;

CREATE FUNCTION public.xr_mvp_tally(p_match uuid) RETURNS jsonb
LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE st text;
BEGIN
 SELECT status INTO st FROM public.matches WHERE id=p_match;
 IF NOT FOUND THEN RETURN jsonb_build_object('ok',false,'error','Match not found.');END IF;
 RETURN jsonb_build_object('ok',true,'open',st='done','my_vote',(SELECT entry_id FROM public.xr_mvp_votes WHERE match_id=p_match AND user_id=auth.uid()),'candidates',coalesce((SELECT jsonb_agg(jsonb_build_object('entry_id',e.id,'player_name',e.player_name,'squad',e.squad,'kills',e.kills,'votes',(SELECT count(*) FROM public.xr_mvp_votes v WHERE v.match_id=p_match AND v.entry_id=e.id)) ORDER BY e.player_name,e.id) FROM public.lb_entries e WHERE e.match_id=p_match),'[]'::jsonb));
END $$;
CREATE FUNCTION public.xr_mvp_vote(p_match uuid,p_entry uuid) RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE me uuid:=auth.uid(); old uuid; st text;
BEGIN
 IF me IS NULL THEN RETURN jsonb_build_object('ok',false,'error','Log in first.');END IF;
 IF NOT EXISTS(SELECT 1 FROM public.profiles WHERE id=me AND NOT coalesce(banned,false)) THEN RETURN jsonb_build_object('ok',false,'error','Your account cannot vote.');END IF;
 SELECT status INTO st FROM public.matches WHERE id=p_match FOR SHARE;
 IF st IS DISTINCT FROM 'done' THEN RETURN jsonb_build_object('ok',false,'error','MVP voting opens after the match is marked done.');END IF;
 IF NOT EXISTS(SELECT 1 FROM public.lb_entries WHERE id=p_entry AND match_id=p_match) THEN RETURN jsonb_build_object('ok',false,'error','Choose a player in this match result.');END IF;
 PERFORM pg_advisory_xact_lock(hashtextextended('xr-mvp:'||me::text||':'||p_match::text,0));
 SELECT entry_id INTO old FROM public.xr_mvp_votes WHERE match_id=p_match AND user_id=me;
 IF FOUND THEN
  IF old=p_entry THEN RETURN jsonb_build_object('ok',true,'entry_id',old);END IF;
  RETURN jsonb_build_object('ok',false,'error','You already voted in this match.');
 END IF;
 INSERT INTO public.xr_mvp_votes(match_id,user_id,entry_id) VALUES(p_match,me,p_entry);
 RETURN jsonb_build_object('ok',true,'entry_id',p_entry);
END $$;

CREATE FUNCTION public.xr_kill_feed_post(p_match uuid,p_killer text,p_victim text,p_weapon text DEFAULT NULL,p_elapsed_seconds integer DEFAULT NULL) RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE nid bigint; txt text; st text;
BEGIN
 IF auth.uid() IS NULL OR NOT public.is_helper() THEN RETURN jsonb_build_object('ok',false,'error','Owner or helper only.');END IF;
 IF EXISTS(SELECT 1 FROM public.profiles WHERE id=auth.uid() AND coalesce(banned,false)) THEN RETURN jsonb_build_object('ok',false,'error','Your account cannot post.');END IF;
 SELECT status INTO st FROM public.matches WHERE id=p_match;
 IF st IS NULL OR st NOT IN ('scheduled','live') THEN RETURN jsonb_build_object('ok',false,'error','Choose a scheduled or live match.');END IF;
 IF coalesce(char_length(btrim(p_killer)),0) NOT BETWEEN 2 AND 40 OR coalesce(char_length(btrim(p_victim)),0) NOT BETWEEN 2 AND 40 OR coalesce(char_length(btrim(p_weapon)),0)>40 OR (p_elapsed_seconds IS NOT NULL AND p_elapsed_seconds NOT BETWEEN 0 AND 86400) THEN RETURN jsonb_build_object('ok',false,'error','Check player names, weapon and elapsed seconds.');END IF;
 txt:=p_killer||' '||p_victim||' '||coalesce(p_weapon,'');
 IF txt ~ '[0-9][ -]?[0-9][ -]?[0-9][ -]?[0-9][ -]?[0-9][ -]?[0-9][ -]?[0-9][ -]?[0-9]' OR txt ~* '(https?://|www\.|@)' THEN RETURN jsonb_build_object('ok',false,'error','Use player names only, not UIDs, phone numbers or links.');END IF;
 INSERT INTO public.xr_kill_feed(match_id,killer,victim,weapon,elapsed_seconds,created_by) VALUES(p_match,btrim(p_killer),btrim(p_victim),nullif(btrim(p_weapon),''),p_elapsed_seconds,auth.uid()) RETURNING id INTO nid;
 RETURN jsonb_build_object('ok',true,'id',nid);
END $$;
CREATE FUNCTION public.xr_kill_feed_list(p_match uuid,p_after bigint DEFAULT 0,p_limit integer DEFAULT 100) RETURNS jsonb
LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path=pg_catalog AS $$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM public.matches WHERE id=p_match) THEN RETURN jsonb_build_object('ok',false,'error','Match not found.');END IF;
 RETURN jsonb_build_object('ok',true,'events',coalesce((SELECT jsonb_agg(to_jsonb(e) ORDER BY id) FROM (SELECT id,killer,victim,weapon,elapsed_seconds,created_at FROM public.xr_kill_feed WHERE match_id=p_match AND NOT hidden AND id>greatest(coalesce(p_after,0),0) ORDER BY id LIMIT least(greatest(coalesce(p_limit,100),1),200))e),'[]'::jsonb));
END $$;
CREATE FUNCTION public.xr_kill_feed_hide(p_event bigint,p_hidden boolean DEFAULT true) RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
BEGIN
 IF auth.uid() IS NULL OR NOT public.is_helper() THEN RETURN jsonb_build_object('ok',false,'error','Owner or helper only.');END IF;
 IF EXISTS(SELECT 1 FROM public.profiles WHERE id=auth.uid() AND coalesce(banned,false)) THEN RETURN jsonb_build_object('ok',false,'error','Your account cannot moderate.');END IF;
 UPDATE public.xr_kill_feed SET hidden=coalesce(p_hidden,true) WHERE id=p_event;
 IF NOT FOUND THEN RETURN jsonb_build_object('ok',false,'error','Feed event not found.');END IF;
 RETURN jsonb_build_object('ok',true);
END $$;

CREATE FUNCTION public.xr_match_chat_post(p_match uuid,p_body text) RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
BEGIN
 IF auth.uid() IS NOT NULL THEN PERFORM pg_advisory_xact_lock(hashtextextended('xr-match-chat:'||auth.uid()::text,0));END IF;
 IF coalesce(p_body,'') ~* '[[:alnum:]._%+-]+@[[:alnum:].-]+\.[[:alpha:]]{2,}' THEN RETURN jsonb_build_object('ok',false,'error','Do not post email addresses in chat.');END IF;
 RETURN public.thread_post(p_match,p_body);
END $$;
CREATE FUNCTION public.xr_match_chat_list(p_match uuid,p_limit integer DEFAULT 50) RETURNS jsonb
LANGUAGE sql STABLE SECURITY DEFINER SET search_path=pg_catalog AS $$ SELECT public.thread_list(p_match,p_limit) $$;

CREATE OR REPLACE FUNCTION public.register_squad(p_id text, p_tournament text, p_squad text, p_captain text, p_uid text, p_ign text, p_phone text, p_proof text, p_client text, p_ip text)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare v_flags jsonb := coalesce((select value from site_settings where key='flags'),'{}'::jsonb); v_wait boolean := false; v_slots int; v_hold boolean := false; v_flag text := null; v_burst int; v_token text; v_user uuid := auth.uid();
begin
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
end $function$;

-- Explicit RPC permissions; no direct table writes/reads through the new public API.
REVOKE ALL ON FUNCTION public.xr_slot_list(text),public.xr_slot_hold(text,integer),public.xr_slot_release(text),public.xr_slot_confirm(text,integer,jsonb,jsonb),public.xr_slot_my(text),public.xr_mvp_tally(uuid),public.xr_mvp_vote(uuid,uuid),public.xr_kill_feed_post(uuid,text,text,text,integer),public.xr_kill_feed_list(uuid,bigint,integer),public.xr_kill_feed_hide(bigint,boolean),public.xr_match_chat_post(uuid,text),public.xr_match_chat_list(uuid,integer) FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.xr_slot_list(text),public.xr_mvp_tally(uuid),public.xr_kill_feed_list(uuid,bigint,integer),public.xr_match_chat_list(uuid,integer) TO anon,authenticated;
GRANT EXECUTE ON FUNCTION public.xr_slot_hold(text,integer),public.xr_slot_release(text),public.xr_slot_confirm(text,integer,jsonb,jsonb),public.xr_slot_my(text),public.xr_mvp_vote(uuid,uuid),public.xr_kill_feed_post(uuid,text,text,text,integer),public.xr_kill_feed_hide(bigint,boolean),public.xr_match_chat_post(uuid,text) TO authenticated;
COMMIT;
