# Match-day load shedding (deployed dark)

All switches default OFF. This is a manual operations control, not automatic CPU monitoring, a capacity guarantee, traffic queue, CDN migration or Realtime implementation. It can reduce social Feed/Clips/Stories database requests; it cannot guarantee the database or Live service is available under every load.

## Native contract

`POST /rest/v1/rpc/xr_ops_status` with `{}`. This public-safe status is allowed for anon and authenticated, contains no personal data and must remain readable while shedding. Example normal response:

```json
{
  "ok": true,
  "feed_shed": false,
  "clips_shed": false,
  "stories_shed": false,
  "text_only_home": false,
  "retry_after_seconds": 10,
  "message": "Server busy. Match Center and Live remain available. Try again shortly.",
  "revision": 1,
  "updated_at": "ISO timestamp",
  "match_center_available": true,
  "live_available": true,
  "configured_feed_shed": false,
  "configured_clips_shed": false
}
```

`feed_shed` and `clips_shed` are EFFECTIVE flags: either configured Feed or Clips flag sheds mixed Feed lists, because old clients can fetch clips through unfiltered Feed routes. `configured_*` fields expose the raw switches for operations debugging. `stories_shed` is independent. No setting permits disabling Match Center or Live. Their `available:true` fields mean this switch does not disable them, not a health check or uptime promise.

- Cache status for about30 seconds, refresh on resume, stagger background refreshes. Do not poll per second.
- When a feature is shed, show server-busy/retry copy, stop requesting its lists, pause uploads/actions exposed through that feature, and stop its image/video prefetch/rendering. `text_only_home` means drop heavy social media sections while keeping match and Live navigation.
- On an actual feature HTTP503 `XR_BUSY`, immediately treat that feature as shed and honor the `Retry-After` response header; do not wait for the next status poll. Do not busy-loop, automatically replay POST writes, or turn a failed request into a fake empty successful Feed.
- On status-read failure, keep Match Center, Live, registration/check-in and room routes usable. Do not treat an unknown ops status as a global login/logout or app-crash condition. Back off retries.
- Existing posts/highlights can already be cached on the phone/CDN. The database cannot recall cached image/video URLs; native must stop rendering/prefetching them.

## Exact manual match-day steps

Use the existing signed-in owner's/admin's JWT and authenticated API helper. The sending client must not log the token. The private settings table has no client grants; use RPCs.

1. Read current status:
   `POST /rest/v1/rpc/xr_ops_status` body `{}`.
2. Shed social features:
   `POST /rest/v1/rpc/xr_ops_admin` body:
   ```json
   {"p_patch":{"feed_shed":true,"clips_shed":true,"stories_shed":true,"retry_after_seconds":10}}
   ```
   Only real admins are accepted. A player/anonymous call is denied. This is immediate, no app update needed for old guarded server endpoints. No paid service is used.
3. Verify returned effective flags are all true and `revision` advanced. Re-read `xr_ops_status` independently. Test an existing Feed read (`feed_list` or direct `feed_posts?select=id&limit=1`), which should return HTTP503 with `code:"XR_BUSY"`, busy message and `Retry-After:10`. Check a safe Match Center read and `xr_live_thread_list` remain available under their existing permissions. Do NOT post fake chat, register a real player or publish a test result.
4. Native should switch to text-only social home sections within its status cache window or on the first503. Older clients may show their existing generic error UI, but their guarded requests still stop at the server before the heavy query.
5. Restore after the burst:
   ```json
   {"p_patch":{"feed_shed":false,"clips_shed":false,"stories_shed":false}}
   ```
   Verify effective flags false, revision advanced, Feed reads successful and critical routes still available. Resume normal app features, do not auto-resubmit failed writes.

`xr_ops_admin({p_patch:{}})` is a read-only admin check. Allowed patch keys only: feed_shed,clips_shed,stories_shed,retry_after_seconds (5..300). Unknown/invalid settings return `ok:false`; inspect it. No automatic threshold, expiry, schedule, operator-role delegation or match-day monitor is installed. Main owns monitoring and decisions to flip/restore.

## Server enforcement and coverage

There are three layers:

1. PostgREST `db_pre_request` hook `public.xr_ops_pre_request`. Before this deployment the authenticator role had no db_pre_request setting; its other settings were preserved. The new hook checks ONLY the explicit route denylist below. It does not change Supabase Auth signup/login settings or SMTP and is not an auth bypass.
2. SQL function entry guards in existing legacy Feed/Story RPCs, retaining their signatures, ACLs and normal-mode behavior. Busy raises before query/write.
3. Restrictive SELECT RLS policies on social tables, preserving existing privacy policies. Security-definer RPCs have their own guard because they otherwise bypass RLS. RLS does not execute on an empty result scan; the request hook covers that case.

Guarded Feed tables: feed_posts,feed_comments,feed_likes,feed_saves. Guarded Feed RPCs: feed_user,feed_list,feed_toggle_like,feed_comments_list,feed_comment,feed_create,feed_toggle_save,feed_saved. Clips: weekly_top_clip plus all mixed Feed routes. Stories tables: stories,story_views. Story RPCs: story_create,story_trays,story_list,story_mark_seen,story_viewers.

The request hook supports normal `/table`, `/rpc/function` and `/rest/v1/...` paths. Dark guarded responses include `X-Xarena-Ops-Guard:feed|clips|stories`, allowing verification that the live hook is attached even when flags are off.

Busy raises PostgREST custom error `PGRST` carrying status503, code XR_BUSY, human-readable message, feature/retry details, `Retry-After` and `Cache-Control:no-store`. HTTP error details are not a successful empty Feed. The normal user's old binary may not display the improved message until upgraded, but the database work is still shed.

Embedded profile highlights/posts are short-circuited, not the whole profile: `player_profile_v2` returns highlights[] plus `highlights_temporarily_unavailable:true`; `creator_profile` similarly returns posts[] plus `posts_temporarily_unavailable:true`. Ordinary profile/stats remain available. Consumers must label the unavailable section rather than claim a user has no clips.

Not gated: match/tournament/check-in/registration/room endpoints, squad chat, Live comments, match chat, match kill-feed, auth, ops controls and admin moderation. `xr_kill_feed_list/post` is match-critical and deliberately excluded despite containing the word feed. Normal ops settings cannot disable it.

This control does NOT cancel in-flight queries, existing Realtime subscriptions, scheduled jobs, cached public media, direct storage/CDN downloads/uploads, arbitrary future RPCs, private messaging media, home posters unrelated to social endpoints or every profile statistics subquery. New Feed/Clip endpoints must be added to both the route and SQL guard coverage during future work. It is load shedding, not a DDoS firewall or universal bandwidth kill switch. Media migration, storage upload gating, resource telemetry, registration queueing and client UI enforcement are separate work.

## Validation and rollback

Pre-deploy and post-deploy rollback suites passed: dark defaults, admin-only switch, bounds, no way to disable Live, route503 payload/retry headers in SQL, legacy SQL bypass guards, no attempted write while shed, embedded profile short-circuit, explicit critical-route allowlist, Live comments read, restoration, mixed-list Clips behavior and grants. Critical match/chat/register function definitions were unchanged. Test flags were changed only within rollback transactions, never committed ON.

Actual production HTTP dark checks: ops status200/all false; feed_list200 and direct feed_posts200 both with the live ops-guard header; Live comments200/ok with honest posts[]. This proves the pre-request hook is active on real HTTP, including the direct table path. A production HTTP503 was deliberately not induced by committing the switch ON against real players; its custom-error payload/status/header logic was tested in database transactions. Manual match-day step3 remains the first real enabled HTTP check unless an authorized maintenance test is arranged earlier. No concurrent/load stress test or native visual/E2E claim.

Rollback script restores original Feed/profile function bodies, removes the restrictive policies and new hook/functions, and resets only the authenticator db_pre_request setting. It leaves the private settings table for history/review. It does not alter other authenticator settings or delete social/player data. Use a reviewed forward migration to re-enable; the initial CREATE migration is not idempotent. If another request hook is installed later, do not run the old rollback blindly: preserve/chain that hook first.
