# Live match room send - 12 November 2026

Deployed additive RPC:

`xr_match_chat_send(p_handle text, p_token uuid, p_body text, p_room text) RETURNS json`

Call through existing public Supabase RPC transport, with the exact chat-handle token already used by chat_send. Example request shape (not actual credentials):

```
{p_handle: savedHandle, p_token: savedChatToken, p_body: typedMessage, p_room: "match-" + match.id}
```

This is chat handle/token authentication, not the separate authenticated match_thread_posts API. No new table, seeded message, new token, registration requirement or feature activation. Native should use this RPC only when the person sends a message in the selected live match chat.

## Routing and reads

Only `match-<UUID>` is accepted. Match row must exist with status live/ongoing/in_progress; the current matches status enum permits only scheduled/live/done/cancelled, so live is the only applicable status today. No enum was changed. UUID room suffix is normalized to lowercase before insertion. Read existing `chat_messages` using exactly `room=eq.match-<lowercase UUID>`, as chatHistory(room) already does. No history or private account fields are returned by the send call.

The matched row is locked until transaction end, so a concurrent close/cancel waits rather than admitting a new message after its status update has committed. Completed/cancelled/scheduled/missing matches refuse sending. Past public chat history is not deleted or hidden when a match ends.

Existing `chat_send(p_handle text,p_token uuid,p_body text)` still has the same signature and continues to derive general/state room with chat_room_for(handle). No overload ambiguity. Both old/new send acquire the same per-handle transaction advisory lock before the global rate check. Eight messages in sixty seconds across ALL rooms, not eight per room. Null body returns invalid instead of a database insert error.

## Results

Success: `{ok:true}`. Refusals use `{error:<code>}` (legacy JSON shape, no guaranteed ok:false):

- unauthorized: handle/token does not match.
- muted: handle has an unexpired or indefinite mute.
- invalid_room: null/non-match/malformed UUID room.
- match_not_live: match missing or not active.
- rate_limited: eight accepted messages already sent in sixty seconds across all rooms.
- invalid: null/empty/over-400-character message, forbidden markup or link pattern.
- blocked_language: same existing Hindi/English moderation filter, violation logged as before.

Validation priority is token, mute, room and match state, shared rate check, body/link validation, language check, insert. Network/database errors must also be handled by native; they are not fake success. No new ban policy or body-filter vocabulary added. Existing chat moderation behavior is reused, not described as a complete anti-abuse solution.

## LIVE COMMENTS is a separate service

Inspected native MorePages.kt and live database definitions. Its `LIVE COMMENTS - app only, not YouTube` block calls `xr_live_thread_list/post/report` and uses `live_tournament_posts`/reports. It is gated by `_flag('live_thread')` and the connected youtube_live tournament_id. It does NOT use chat_messages or this new send RPC. The existing off message must remain until that separate feature is explicitly enabled. No stream flag/settings changes were made.

Observed native source: https://raw.githubusercontent.com/xarenaprivate/xarena/native/app/src/main/kotlin/app/xarena/nativeapp/MorePages.kt
Existing read/send wrapper: https://raw.githubusercontent.com/xarenaprivate/xarena/native/app/src/main/kotlin/app/xarena/nativeapp/Api.kt

The older `xr_match_chat_post/list` APIs remain separate, JWT-authenticated match_thread_posts wrappers and retain their existing feature gate. This change does not enable or replace those either.

## Tests and rollout

Migration compiled and full rolled-back fixtures passed before and after deployment. Tests cover wrong token, mute, malformed/null/arbitrary room, nonexistent/scheduled/done/cancelled match, null/empty/long/markup/link body, abuse violation log, active-match insertion, UUID canonical routing, unchanged world send signature/routing, and shared eight-message budget across world + match. Anonymous execute grants were verified under the actual anon role; token validation remains inside the RPC. No real messages were sent.

All fixtures are wrapped by BEGIN/ROLLBACK in the tests file. Never execute selected fixture statements separately. Existing counts before/after: 6 handles, 10 matches, 0 chat messages, 0 violations, 0 stream comments. Stream/settings snapshots and original chat_send grants unchanged. Existing chat_send definition was rechecked immediately before deployment for concurrent changes.

Multi-session load stress testing and native UI end-to-end testing were not performed here. The shared lock and global rate-limit assertions cover the intended concurrency path. No UI changes or visual deliverable in this backend-only task.

Rollback: stop new native match sends first; drop xr_match_chat_send(text,uuid,text,text) if no client uses it, and restore chat_send-before.sql only if deliberately removing the shared rate lock/null-body fix. Do not delete real match messages. Migration is single-apply and transactional.
