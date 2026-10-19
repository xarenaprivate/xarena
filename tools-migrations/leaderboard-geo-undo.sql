select cron.unschedule('challenges-settle');
drop function if exists challenge_create(uuid,int), challenge_respond(uuid,boolean), challenge_cancel(uuid), my_challenges(), challenges_settle_due(), _ap_bal(uuid), my_header(), my_ranks(), leaderboard_scope(text,int), set_my_area(text,text,double precision,double precision), clear_my_area();
-- WARNING: only run drop table when no active challenges hold escrowed AP (settle/refund first)
-- drop table challenges; alter table profiles drop column geo_state, drop column geo_district, drop column geo_lat, drop column geo_lon, drop column geo_updated_at;
drop trigger if exists profiles_geo_guard on profiles; drop function if exists trg_profiles_geo_guard();
