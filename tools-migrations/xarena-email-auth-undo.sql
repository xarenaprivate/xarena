-- Rollback new additive feature only. Disable its endpoint first.
-- Export private ownership/challenge records before destructive rollback if real users exist.
drop function if exists public.xr_email_finish(uuid,uuid,uuid,text);
drop function if exists public.xr_email_reserve(uuid,text,uuid,text);
drop function if exists public.xr_email_lookup(uuid);
drop function if exists public.xr_email_delivery(uuid,boolean);
drop function if exists public.xr_email_start(uuid,text,text,text,text,text,text);
drop table if exists xarena_private.email_ownership;
drop table if exists xarena_private.email_challenges;
-- Do not CASCADE/drop shared schema or existing accounts.
