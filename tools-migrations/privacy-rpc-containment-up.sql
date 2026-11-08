begin;
-- Server helpers are not client RPCs. Their owner, nested SECURITY DEFINER
-- reward functions, cron owner and service_role retain EXECUTE.
revoke execute on function public.security_scan() from public,anon,authenticated;
revoke execute on function public.award_xp(uuid,text,integer,jsonb) from public,anon,authenticated;
grant execute on function public.security_scan(),public.award_xp(uuid,text,integer,jsonb) to service_role;
commit;

begin;
-- Legacy clients still read `available`. It now means input can proceed,
-- never that a registered account does or does not exist.
create or replace function public.phone_available(p_phone text) returns jsonb language plpgsql security definer set search_path=public as $$
declare v_p text := normalize_phone(p_phone);
begin
 if v_p is null then return jsonb_build_object('ok',false,'error','Phone number must be 10 digits.');end if;
 return jsonb_build_object('ok',true,'available',true,'phone',v_p);
end$$;
grant execute on function public.phone_available(text) to anon,authenticated,service_role;
commit;
