drop function if exists public.admin_handle_abuse_flag(bigint,text,text);
drop function if exists public.admin_suspicious_accounts();
drop function if exists public.bind_device(text,text,text);
drop function if exists public.signup_precheck(text,text,text);
drop function if exists public._req_ip();
drop function if exists public._abuse_h(text);
drop table if exists public.abuse_flags, public.account_devices, public.signup_attempts, public.abuse_secret;
notify pgrst, 'reload schema';
