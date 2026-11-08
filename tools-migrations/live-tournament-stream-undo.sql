drop function if exists public.admin_set_live_stream(text,text,boolean,text,uuid);
drop function if exists public.xr_live_roster(text);
-- Existing stream settings and all account/registration data are preserved.
-- Revert admin UI alongside this rollback; remove relation through admin only.
-- Export real comments first if rolling back after launch.
drop function if exists public.xr_live_thread_list(text,int);
drop function if exists public.xr_live_thread_post(text,text);
drop function if exists public.xr_live_thread_report(bigint,text);
drop table if exists public.live_tournament_reports;
drop table if exists public.live_tournament_posts;
