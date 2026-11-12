BEGIN;
UPDATE public.xr_live_growth_settings SET enabled=false;
DROP FUNCTION public.xr_live_overview();
DROP FUNCTION public.xr_live_follow_set(boolean);
DROP FUNCTION public.xr_live_poll_vote(uuid,integer);
DROP FUNCTION public.xr_live_poll_admin(text,jsonb,timestamptz);
DROP FUNCTION public.xr_live_growth_admin(jsonb);
NOTIFY pgrst,'reload schema';
COMMIT;
