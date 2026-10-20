drop function if exists public.player_stats_card(text), public.city_squad_close(bigint), public.city_squad_decide(bigint,boolean), public.city_squad_applicants(bigint), public.city_squad_apply(bigint,text), public.city_squad_create(text,text,text,text,int), public.admin_creator_event_sponsor(uuid,bigint), public.admin_creator_sponsor_handle(bigint,text), public.admin_creator_sponsor_requests(), public.creator_sponsor_request(bigint,text), public.creator_text_guard2() cascade;
drop view if exists public.sponsors_public, public.city_squads_public;
drop table if exists public.city_squad_applications, public.city_squads, public.creator_sponsor_requests;
alter table public.creator_events drop column if exists sponsor_id;
notify pgrst, 'reload schema';
