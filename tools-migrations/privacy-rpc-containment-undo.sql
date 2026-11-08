-- Emergency rollback only: reinstates known unsafe client grants.
begin;
CREATE OR REPLACE FUNCTION public.phone_available(p_phone text)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare v_p text := normalize_phone(p_phone);
begin
  if v_p is null then return jsonb_build_object('ok', false, 'error', 'Phone number must be 10 digits.'); end if;
  return jsonb_build_object('ok', true, 'available', not exists (select 1 from profiles where phone = v_p), 'phone', v_p);
end $function$
;

grant execute on function public.phone_available(text),public.security_scan(),public.award_xp(uuid,text,integer,jsonb) to anon,authenticated;
grant execute on function public.award_xp(uuid,text,integer,jsonb) to public;
commit;
