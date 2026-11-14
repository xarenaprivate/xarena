BEGIN;
DO $$BEGIN
IF clock_timestamp()<'2026-11-15T14:30:00Z'::timestamptz OR clock_timestamp()>'2026-11-15T15:20:00Z'::timestamptz THEN RAISE EXCEPTION 'Outside approved reminder delivery window; manual review';END IF;
IF NOT EXISTS(SELECT 1 FROM public.tournaments WHERE id='XR - S2'AND start_at='2026-11-15T15:30:00Z'AND status='upcoming')THEN RAISE EXCEPTION 'Season2 schedule/state changed; do not send stale reminder';END IF;
END $$;
-- Baseline only brand-new dispatch users before inserting this reminder, not after it.
INSERT INTO public.push_dispatch_state(user_id,since_notif,since_dm,since_room,since_tourn,baseline_done,updated_at)
SELECT DISTINCT d.user_id,coalesce((SELECT max(n.id)FROM public.notifications n WHERE n.user_id=d.user_id),-1),clock_timestamp(),clock_timestamp(),clock_timestamp(),true,clock_timestamp()FROM public.device_tokens d JOIN public.profiles p ON p.id=d.user_id WHERE NOT coalesce(p.banned,false)ON CONFLICT(user_id)DO NOTHING;
UPDATE public.push_dispatch_state ds SET since_notif=coalesce((SELECT max(n.id)FROM public.notifications n WHERE n.user_id=ds.user_id),-1),since_dm=clock_timestamp(),since_room=clock_timestamp(),since_tourn=clock_timestamp(),baseline_done=true,updated_at=clock_timestamp()WHERE NOT baseline_done AND EXISTS(SELECT 1 FROM public.device_tokens d WHERE d.user_id=ds.user_id);
INSERT INTO public.notifications(user_id,kind,title,body,link,ref)
SELECT id,'tournament_reminder','XARENA Season 2 starts at 9 PM IST','Season 2 starts today, 15 Nov, at 9:00 PM IST. Open Tournaments for the match schedule. Free entry. Room details unlock 15 minutes before each match for eligible registered players.','tournaments.html?tournament=XR%20-%20S2#event-details','season2-20261115-2000'FROM public.profiles WHERE NOT coalesce(banned,false)ON CONFLICT(user_id,kind,ref)DO NOTHING;
SELECT count(*)enqueued FROM public.notifications WHERE kind='tournament_reminder'AND ref='season2-20261115-2000';
COMMIT;
