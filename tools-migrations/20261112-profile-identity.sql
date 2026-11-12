-- Own-account IGN/Free Fire UID edit. One shared rolling 14-day cooldown.
BEGIN;
CREATE TABLE public.xr_profile_identity_cooldowns (
 user_id uuid PRIMARY KEY REFERENCES public.profiles(id) ON DELETE CASCADE,
 changed_at timestamptz NOT NULL
);
ALTER TABLE public.xr_profile_identity_cooldowns ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.xr_profile_identity_cooldowns FROM PUBLIC,anon,authenticated;
CREATE TABLE public.xr_profile_identity_config (
 singleton boolean PRIMARY KEY DEFAULT true CHECK(singleton),
 opens_at timestamptz NOT NULL
);
ALTER TABLE public.xr_profile_identity_config ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.xr_profile_identity_config FROM PUBLIC,anon,authenticated;
INSERT INTO public.xr_profile_identity_config(singleton,opens_at) VALUES(true,'2026-11-13 04:00:00+05:30');

CREATE FUNCTION public.xr_profile_identity_guard() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path TO pg_catalog AS $fn$
DECLARE u uuid:=auth.uid(); t timestamptz; opens timestamptz; v_now timestamptz:=clock_timestamp(); initial_setup boolean;
BEGIN
 IF new.ign IS NOT DISTINCT FROM old.ign AND new.ff_uid IS NOT DISTINCT FROM old.ff_uid THEN RETURN new;END IF;
 -- Trusted service/DB maintenance and existing other-account admin operations stay unchanged.
 -- Authenticated admins editing their OWN identity obey the same timer as players.
 IF u IS NULL THEN RETURN new;END IF;
 IF u<>old.id THEN
  IF public.is_admin() THEN RETURN new;END IF;
  RAISE EXCEPTION USING ERRCODE='P1403',MESSAGE='Only your own profile can be edited.';
 END IF;
 IF coalesce(old.banned,false) THEN RAISE EXCEPTION USING ERRCODE='P1403',MESSAGE='Account restricted.';END IF;
 initial_setup:=nullif(btrim(old.ign),'') IS NULL AND nullif(btrim(old.ff_uid),'') IS NULL;
 -- Preserve the existing first-time complete_profile flow, including its optional fields.
 IF initial_setup THEN RETURN new;END IF;
 SELECT opens_at INTO opens FROM public.xr_profile_identity_config WHERE singleton;
 IF opens IS NULL OR v_now<opens THEN RAISE EXCEPTION USING ERRCODE='P1404',MESSAGE='Profile identity editing opens at 2026-11-13T04:00:00+05:30.';END IF;
 new.ign:=btrim(new.ign);new.ff_uid:=btrim(new.ff_uid);
 IF new.ign IS NULL OR char_length(new.ign) NOT BETWEEN 1 AND 40 OR octet_length(new.ign)>160 OR new.ign ~ '[[:cntrl:]<>]' OR new.ign ~ ('['||chr(8203)||'-'||chr(8207)||chr(8234)||'-'||chr(8238)||chr(8294)||'-'||chr(8297)||chr(65279)||']') THEN
  RAISE EXCEPTION USING ERRCODE='P1400',MESSAGE='IGN must be 1-40 characters without control characters or markup.';
 END IF;
 IF new.ff_uid IS NULL OR new.ff_uid !~ '^[0-9]{5,15}$' THEN RAISE EXCEPTION USING ERRCODE='P1400',MESSAGE='Free Fire UID must be 5-15 digits.';END IF;
 IF new.ign IS NOT DISTINCT FROM old.ign AND new.ff_uid IS NOT DISTINCT FROM old.ff_uid THEN RETURN new;END IF;
 SELECT changed_at INTO t FROM public.xr_profile_identity_cooldowns WHERE user_id=old.id;
 IF t IS NOT NULL AND v_now<t+interval '336 hours' THEN
  RAISE EXCEPTION USING ERRCODE='P1414',MESSAGE='IGN and UID can be changed once every 14 days. Next change: '||to_char((t+interval '336 hours') AT TIME ZONE 'UTC','YYYY-MM-DD"T"HH24:MI:SS.US"Z"');
 END IF;
 INSERT INTO public.xr_profile_identity_cooldowns(user_id,changed_at)VALUES(old.id,v_now)ON CONFLICT(user_id)DO UPDATE SET changed_at=excluded.changed_at;
 IF new.ff_uid IS DISTINCT FROM old.ff_uid THEN new.verified:=false;END IF;
 RETURN new;
END $fn$;
REVOKE ALL ON FUNCTION public.xr_profile_identity_guard() FROM PUBLIC,anon,authenticated;
CREATE TRIGGER a_xr_profile_identity_guard BEFORE UPDATE OF ign,ff_uid ON public.profiles FOR EACH ROW EXECUTE FUNCTION public.xr_profile_identity_guard();

-- Keep every existing moderation/phone/stat guard. Replace only the permanent
-- UID lock with the table-level 14-day policy and automatic UID reverification.
CREATE OR REPLACE FUNCTION public.profiles_guard() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path TO public AS $fn$
BEGIN
 IF auth.uid() IS NOT NULL AND NOT (SELECT is_admin()) THEN
  IF new.verified IS DISTINCT FROM old.verified AND NOT (new.ff_uid IS DISTINCT FROM old.ff_uid AND new.verified=false) THEN RAISE EXCEPTION 'verified flag is owner-only';END IF;
  IF new.phone IS DISTINCT FROM old.phone THEN RAISE EXCEPTION 'phone is account identity';END IF;
  IF new.phone_code IS DISTINCT FROM old.phone_code THEN RAISE EXCEPTION 'phone code is system-generated';END IF;
  IF new.phone_verified IS DISTINCT FROM old.phone_verified THEN RAISE EXCEPTION 'phone verification is owner-only';END IF;
  IF new.matches IS DISTINCT FROM old.matches THEN RAISE EXCEPTION 'matches are XARENA-earned';END IF;
  IF new.wins IS DISTINCT FROM old.wins THEN RAISE EXCEPTION 'wins are XARENA-earned';END IF;
 END IF;
 RETURN new;
END $fn$;

CREATE FUNCTION public.xr_profile_identity_status() RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path TO pg_catalog AS $fn$
DECLARE u uuid:=auth.uid();t timestamptz;opens timestamptz;v_now timestamptz:=clock_timestamp();
BEGIN
 IF u IS NULL THEN RETURN jsonb_build_object('ok',false,'error','unauthorized','message','Log in first.');END IF;
 IF NOT EXISTS(SELECT 1 FROM public.profiles WHERE id=u AND NOT coalesce(banned,false)) THEN RETURN jsonb_build_object('ok',false,'error','account_restricted','message','Account unavailable.');END IF;
 SELECT changed_at INTO t FROM public.xr_profile_identity_cooldowns WHERE user_id=u;
 SELECT opens_at INTO opens FROM public.xr_profile_identity_config WHERE singleton;
 RETURN jsonb_build_object('ok',true,'available',coalesce(v_now>=opens,false),'opens_at',opens,'changed_at',t,'next_allowed_at',CASE WHEN t IS NOT NULL THEN greatest(opens,t+interval '336 hours') ELSE opens END,'can_change',coalesce(v_now>=opens AND (t IS NULL OR v_now>=t+interval '336 hours'),false),'cooldown_days',14);
END $fn$;

CREATE FUNCTION public.xr_profile_identity_update(p_ign text,p_ff_uid text) RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path TO pg_catalog,public AS $fn$
DECLARE u uuid:=auth.uid();p public.profiles;v_ign text:=btrim(p_ign);v_uid text:=btrim(p_ff_uid);t timestamptz;opens timestamptz;v_now timestamptz;
BEGIN
 IF u IS NULL THEN RETURN jsonb_build_object('ok',false,'error','unauthorized','message','Log in first.');END IF;
 SELECT * INTO p FROM public.profiles WHERE id=u FOR UPDATE;
 IF NOT FOUND OR coalesce(p.banned,false) THEN RETURN jsonb_build_object('ok',false,'error','account_restricted','message','Account unavailable.');END IF;
 v_now:=clock_timestamp();
 SELECT opens_at INTO opens FROM public.xr_profile_identity_config WHERE singleton;
 IF opens IS NULL OR v_now<opens THEN RETURN jsonb_build_object('ok',false,'error','not_open','message','Profile identity editing opens at 4 AM IST on November 13, 2026.','opens_at',opens);END IF;
 IF v_ign IS NULL OR char_length(v_ign) NOT BETWEEN 1 AND 40 OR octet_length(v_ign)>160 OR v_ign ~ '[[:cntrl:]<>]' OR v_ign ~ ('['||chr(8203)||'-'||chr(8207)||chr(8234)||'-'||chr(8238)||chr(8294)||'-'||chr(8297)||chr(65279)||']') THEN RETURN jsonb_build_object('ok',false,'error','invalid_ign','message','IGN must be 1-40 characters without control characters or markup.');END IF;
 IF v_uid IS NULL OR v_uid !~ '^[0-9]{5,15}$' THEN RETURN jsonb_build_object('ok',false,'error','invalid_uid','message','Free Fire UID must be 5-15 digits.');END IF;
 SELECT changed_at INTO t FROM public.xr_profile_identity_cooldowns WHERE user_id=u;
 IF v_ign IS NOT DISTINCT FROM p.ign AND v_uid IS NOT DISTINCT FROM p.ff_uid THEN RETURN jsonb_build_object('ok',true,'changed',false,'changed_at',t,'next_allowed_at',CASE WHEN t IS NULL THEN opens ELSE greatest(opens,t+interval '336 hours') END,'verification_reset',false,'cooldown_days',14);END IF;
 IF t IS NOT NULL AND v_now<t+interval '336 hours' THEN RETURN jsonb_build_object('ok',false,'error','cooldown','message','IGN and UID can be changed once every 14 days.','next_allowed_at',t+interval '336 hours','cooldown_days',14);END IF;
 -- Initial setup via this edit endpoint also starts its timer; legacy signup
 -- initial setup stays exempt from the edit cooldown.
 UPDATE public.profiles SET ign=v_ign,ff_uid=v_uid WHERE id=u;
 IF nullif(btrim(p.ign),'') IS NULL AND nullif(btrim(p.ff_uid),'') IS NULL THEN
  INSERT INTO public.xr_profile_identity_cooldowns(user_id,changed_at)VALUES(u,v_now)ON CONFLICT(user_id)DO UPDATE SET changed_at=excluded.changed_at;
 END IF;
 SELECT changed_at INTO t FROM public.xr_profile_identity_cooldowns WHERE user_id=u;
 RETURN jsonb_build_object('ok',true,'changed',true,'changed_at',t,'next_allowed_at',t+interval '336 hours','verification_reset',coalesce(p.verified,false) AND v_uid IS DISTINCT FROM p.ff_uid,'cooldown_days',14);
EXCEPTION
 WHEN SQLSTATE 'P1414' THEN RETURN jsonb_build_object('ok',false,'error','cooldown','message','IGN and UID can be changed once every 14 days.','next_allowed_at',(SELECT changed_at+interval '336 hours' FROM public.xr_profile_identity_cooldowns WHERE user_id=u),'cooldown_days',14);
 WHEN SQLSTATE 'P1400' THEN RETURN jsonb_build_object('ok',false,'error','invalid_identity','message','Check the IGN and UID format.');
 WHEN unique_violation OR check_violation THEN RETURN jsonb_build_object('ok',false,'error','update_failed','message','Profile change could not be saved.');
END $fn$;
REVOKE ALL ON FUNCTION public.xr_profile_identity_status() FROM PUBLIC,anon;
REVOKE ALL ON FUNCTION public.xr_profile_identity_update(text,text) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.xr_profile_identity_status() TO authenticated;
GRANT EXECUTE ON FUNCTION public.xr_profile_identity_update(text,text) TO authenticated;

-- Legacy JSON callers get honest, privacy-safe errors instead of raw exceptions.
CREATE OR REPLACE FUNCTION public.complete_profile(p_username text,p_ign text,p_ff_uid text) RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path TO public AS $fn$
DECLARE v_uid uuid:=auth.uid();
BEGIN
 IF v_uid IS NULL THEN RETURN jsonb_build_object('ok',false,'error','Login required.');END IF;
 IF p_username !~ '^[A-Za-z0-9_.]{3,16}$' THEN RETURN jsonb_build_object('ok',false,'error','Username 3-16 chars: letters, numbers, dot, underscore.');END IF;
 IF p_ff_uid IS NOT NULL AND p_ff_uid<>'' AND p_ff_uid !~ '^[0-9]{5,15}$' THEN RETURN jsonb_build_object('ok',false,'error','FF UID must be 5-15 digits (numbers only).');END IF;
 BEGIN
  UPDATE profiles SET username=lower(p_username),ign=nullif(trim(p_ign),''),ff_uid=nullif(trim(p_ff_uid),'') WHERE id=v_uid;
 EXCEPTION
  WHEN unique_violation THEN RETURN jsonb_build_object('ok',false,'error','That username is already taken.');
  WHEN SQLSTATE 'P1414' THEN RETURN jsonb_build_object('ok',false,'error',SQLERRM,'code','cooldown','next_allowed_at',(SELECT changed_at+interval '336 hours' FROM public.xr_profile_identity_cooldowns WHERE user_id=v_uid));
  WHEN SQLSTATE 'P1400' OR SQLSTATE 'P1403' OR SQLSTATE 'P1404' THEN RETURN jsonb_build_object('ok',false,'error',SQLERRM);
 END;
 RETURN jsonb_build_object('ok',true);
END $fn$;
NOTIFY pgrst,'reload schema';
COMMIT;
