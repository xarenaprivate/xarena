select cron.unschedule('match-reminder-15');
drop function if exists match_reminder_15_tick(), admin_handle_player_report(bigint,text,text), admin_player_reports(), report_player(text,text,text), scrim_close(uuid), scrim_reach(uuid), scrim_find(), scrim_post(text,text,text), weekly_top_clip(), squad_board(text), season_claim(int), season_pass(), claim_mission_v2(text), daily_missions_v2();
-- tables kept (drop manually if needed): mission_claims_v2, season_rewards, season_claims, scrim_requests, player_reports
