-- Restores original guards. Intentionally retains private timestamp data for re-enable.
BEGIN;
DROP TRIGGER IF EXISTS a_xr_profile_identity_guard ON public.profiles;
DROP FUNCTION IF EXISTS public.xr_profile_identity_guard();
DROP FUNCTION IF EXISTS public.xr_profile_identity_update(text,text);
DROP FUNCTION IF EXISTS public.xr_profile_identity_status();
CREATE OR REPLACE FUNCTION public.complete_profile(p_username text, p_ign text, p_ff_uid text)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare v_uid uuid := auth.uid();
begin
  if v_uid is null then return jsonb_build_object('ok', false, 'error', 'Login required.'); end if;
  if p_username !~ '^[A-Za-z0-9_.]{3,16}$' then return jsonb_build_object('ok', false, 'error', 'Username 3-16 chars: letters, numbers, dot, underscore.'); end if;
  if p_ff_uid is not null and p_ff_uid <> '' and p_ff_uid !~ '^[0-9]{5,15}$' then return jsonb_build_object('ok', false, 'error', 'FF UID must be 5-15 digits (numbers only).'); end if;
  begin
    update profiles set username = lower(p_username), ign = nullif(trim(p_ign), ''), ff_uid = nullif(trim(p_ff_uid), '') where id = v_uid;
  exception when unique_violation then
    return jsonb_build_object('ok', false, 'error', 'That username is already taken.');
  end;
  return jsonb_build_object('ok', true);
end $function$
;
CREATE OR REPLACE FUNCTION public.profiles_guard()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
begin
  if auth.uid() is not null and not (select is_admin()) then
    if new.verified is distinct from old.verified then raise exception 'verified flag is owner-only'; end if;
    if old.verified and new.ff_uid is distinct from old.ff_uid then raise exception 'FF UID locked after verification'; end if;
    if new.phone is distinct from old.phone then raise exception 'phone is account identity'; end if;
    if new.phone_code is distinct from old.phone_code then raise exception 'phone code is system-generated'; end if;
    if new.phone_verified is distinct from old.phone_verified then raise exception 'phone verification is owner-only'; end if;
    if new.matches is distinct from old.matches then raise exception 'matches are XARENA-earned'; end if;
    if new.wins is distinct from old.wins then raise exception 'wins are XARENA-earned'; end if;
  end if;
  return new;
end $function$
;
NOTIFY pgrst,'reload schema';
COMMIT;
