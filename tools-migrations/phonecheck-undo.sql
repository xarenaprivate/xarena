drop function if exists public.admin_flag_account(uuid,text);
drop function if exists public.admin_phone_check();
drop function if exists public._phone_risk(text);
delete from public.abuse_flags where kind='phone_check';
notify pgrst, 'reload schema';
