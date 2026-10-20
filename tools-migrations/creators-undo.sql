drop function if exists public.admin_creator_revoke(uuid), public.admin_creator_review(bigint,boolean), public.admin_creator_queue(),
 public.creator_event_report(uuid,text), public.creator_event_set_room(uuid,text,text), public.creator_event_room(uuid), public.creator_event_kick(uuid,uuid,boolean),
 public.creator_event_players(uuid), public.creator_event_leave(uuid), public.creator_event_join(uuid), public.creator_event_set_stream(uuid,text,boolean),
 public.creator_event_manage(uuid,text,timestamptz), public.creator_event_create(text,text,text,timestamptz,int,text,text,text), public.creator_dashboard(),
 public.creator_status(), public.creator_apply(text,int,text), public.creator_tier(int), public.creator_events_text_guard() cascade;
drop view if exists public.creator_events_public, public.creators_public;
drop table if exists public.creator_event_reports, public.creator_event_players, public.creator_events, public.creators, public.creator_applications;
notify pgrst, 'reload schema';
