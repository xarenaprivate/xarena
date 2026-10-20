# Contracts for the native app (base https://oskccuajgbyabogbvedj.supabase.co, header apikey = anon key; user RPCs also send Authorization: Bearer <user token>)

## Spotlights (Squad of the Week, Player of the Day, MVP, Clip of week) - table, no separate tables
GET /rest/v1/spotlights?select=kind,name,detail,logo_url,ref_url,created_at&order=created_at.desc&limit=40
kind in: squad_week | player_day | mvp_match | mvp_week | clip_week. Show the newest row per kind. Admin writes in the web admin (Spotlights tab).

## Team finder (LFG) - existing RPCs
POST rpc/lfg_list {} -> {ok, lobbies:[{id,mode,map,rank_min,rank_max,mic,note,role_wanted,slots,status,created_at,host,mine,host_mine,members:[{name,me}]}]}
POST rpc/lfg_create {p_mode,p_map,p_rank_min,p_rank_max,p_mic,p_note,p_slots} -> {ok,id?}  (user)
POST rpc/lfg_set_role {p_lobby, p_role} (host only; IGL|Rusher|Sniper|Support|Grenadier|Any) -> {ok}  (NEW). Filter by role_wanted on the client.
POST rpc/lfg_join {p_lobby} / rpc/lfg_leave {p_lobby}. Lobbies close after 12 h.

## Daily challenges (missions) - existing
POST rpc/daily_missions_v2 {} -> missions list with progress (user); POST rpc/claim_mission_v2 {p_kind}. Also daily_mission {} / daily_mission_claim {} (legacy). Daily quiz: rpc/quiz_feed {} , rpc/quiz_answer {p_quiz,p_choice}. Daily check-in: rpc/my_game_stats {}, rpc/daily_checkin {}.

## Match reminders
Global alerts: POST rpc/set_my_prefs {p_show_activity:null|bool, p_alerts:bool} (alerts_opt_in).
Per tournament (NEW): POST rpc/set_match_reminder {p_tournament:"xr-5oar", p_on:bool}; POST rpc/my_match_reminders {} -> {ok, opted_out:[tournament_id]}. Server pushes: 1 hour before and 15 minutes before (cron every 5 min) as in-app notifications (rpc/my_notifications {p_limit}); only for active registrations (not rejected/cancelled/waitlist) and not opted out.

## Friends / referrals
rpc/friend_request {p_to uuid} | friend_request_by_name {p_name} | friend_respond {p_request,p_accept} | friend_remove {p_friend}. rpc/my_referral {} -> {ok,code,count,earned,applied}; rpc/referral_apply {p_code}. Squad invites: rpc/squad_invite {p_name}, my_squad_invites {}, squad_invite_respond {p_id,p_accept}. Challenges: rpc/challenge_create {p_target,p_stake}, challenge_respond {p_id,p_accept}, my_challenges {}.

## Registration
rpc/register_squad: p_tournament = site_settings reg_tournament ("xr-5oar"). Response may contain waitlist:true. match_room only works for active registrations (waitlist/rejected/cancelled are refused).

## Other new
points_table {p_tournament}, head_to_head {p_a,p_b}, squad_profile {p_squad}, creator_board {}, feedback_submit {p_kind,p_body}, tournament_reg_counts {}, GET notices (RLS shows active), GET site_settings?key=eq.tips / pinned / flags.

## Not built yet (flags OFF): live match thread, spectator page, mini-cup.
- live thread + spectator RPCs (bundle5): thread_post {p_match,p_body}, thread_list {p_match,p_limit}, thread_report {p_post,p_reason}, spectator_info {p_tournament}; all return ok:false while flags live_thread/spectator are OFF. No Room ID. Auto-hide at 3 reports.
- helper-admin (staff only): is_helper(), helper_matches(), helper_set_room {p_match,p_room_id,p_room_pass}, helper_add_result {p_match,p_player,p_squad,p_kills,p_placement,p_points}, helper_registrations {p_tournament} (no phone/UID/IP). Owner: admin_add_helper {p_username}, admin_remove_helper {p_user}, admin_list_helpers {}.

## Creator profile
- `creator_profile(p_id uuid)` (anon ok) -> {name, tier, avatar_url, channel_url, bio, joined, posts:[{caption, at}]} or null if not an active creator. No phone/UID/IG login.
- `creator_set_bio(p_bio text)` (auth, creator only, max 300 chars) -> {ok} or {ok:false,error}.
- `creator_board()` now also returns user_id per row.

## Squad invite link + match recap card data (v0.17.17)
- Squad invite / share link: `https://xarenaprivate.github.io/xarena/more.html?squad=<squad_id>#squad` (web profile; native may deep-link it or call `squad_profile(p_squad)` for the data). Share text: "<squad name> on XARENA - <link>". The squad_id is the squads-table uuid (squad_profile takes p_squad uuid), never a phone or UID.
- Match/result recap card data: `points_table(p_tournament text|null)` -> array of {squad, matches, points, kills, ...} sorted by points (empty array = no results yet, show empty state). Top fragger: `GET lb_entries?select=player_name,kills&order=kills.desc&limit=1` (show only if kills > 0). Season totals: `tournament_reg_counts()` -> [{tournament_id, slots, registered, waitlist}]. The web card draws kicker, title, up to 4 lines and the footer "Free Fire tournaments - free forever". Real data only; no fake numbers.
