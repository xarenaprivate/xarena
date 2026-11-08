# Live tournament video

Admin selects the relation explicitly in Admin > Live Stream. Nothing assigns a tournament automatically. Current video/title/active values and owner feature settings remain unchanged on deployment.

`site_settings.youtube_live` retains `video_id`, `title`, `active` and adds nullable `tournament_id` (text) and nullable `match_id` (UUID, optional existing match-thread context). Tournament comments below do not require a match. Default relation is absent/unconnected. Do not infer from a tournament name, current date or current live match.

Admin saves through authenticated `admin_set_live_stream(p_video text,p_title text,p_active boolean,p_tournament text|null,p_match uuid|null)`. Checks admin membership, video format, tournament existence, and match belonging to selected tournament. Returns `{ok:true}` or `{ok:false,error}`. No setting changes to `live_thread`.

## Registered display names

`xr_live_roster(p_tournament text)` -> `{ok,players:[{name,squad}],count,error?}`. Only works for the exact admin-connected tournament. Reads registration IGN and squad, excludes rejected/cancelled/declined/waitlist, skips empty IGN. At most1000display entries. No phone, email, game UID, owner UUID, private proof or captain field returned. Does not claim a complete squad roster: registration stores one display IGN, not a full team snapshot. Never join arbitrary squad names to current memberships and call those registered players.

## App-only tournament comments

- `xr_live_thread_list(p_tournament text,p_limit int=50)` -> `{ok,posts:[{id,name,body,created_at,mine}],error?}` newest first, max100.
- `xr_live_thread_post(p_tournament text,p_body text)` authenticated -> `{ok,error?}`.
- `xr_live_thread_report(p_post bigint,p_reason text|null)` authenticated -> `{ok,error?}`.

No external video-platform posting. New independent tournament thread storage, no invented match records. Exact connected tournament required by list/post. Existing owner `live_thread` setting gates list/post and is currentlyOFF; deployment does not turn it on. Render honest disabled/no-connection/empty/error states. Native should poll only while this panel is visible, pause offscreen, avoid duplicate posting on timeout.

Bodies1-200characters, login and nonbanned account required. Link/phone-like ID/email filtering, five-second gap and30/hour per user, quota check serialized per user. Reports unique per reporter/post; three reports hide a comment. Admin Live Stream view loads up to100latest comments for selected tournament and can hide/restore. Existing match-thread RPCs and tables unchanged.

## Validation and deployment

SQL transaction rollback tests passed for admin-only save, wrong-match rejection, exact registered display output, private-field omission, unlinked/invalid-body rejection, serialized-rate check, list and three-report hide. Synthetic rows and setting changes rolled back. No real post, tournament connection or account mutation was made.

Undo SQL removes only new functions/tables; preserve/export real comments before destructive rollback. Revert UI alongside rollback. Existing stream values are deliberately preserved.
