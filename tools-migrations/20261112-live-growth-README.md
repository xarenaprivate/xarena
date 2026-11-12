# Live screen growth backend

Deployed November 12, 2026. Safe subset staged dark. Existing YouTube player configuration, live roster/thread APIs, and live_thread flag are unchanged. No poll, welcome pin, watch session, gift, follow, badge, prediction, drop, or points transaction was seeded.

## Current state

The existing active stream is `Normal Tournament`, video `BI6Ns45PmXQ`, linked to `xr-brlord-nov12`. No scheduled start is recorded in this migration. Viewer count is unknown, not zero. Linked eligible squad list is currently empty. Growth remains disabled, video binding/start/host/pin are unset, and poll/vote tables are empty.

## `xr_live_overview({})`

Public read with optional authenticated personalization. An authenticated banned/restricted actor is denied, not treated as anonymous. Reply:

- ok, growth_enabled, cache_seconds:30, count_refresh_seconds:300.
- stream: null when the canonical stream is inactive or has no video; otherwise `{title,video_id,tournament_id,scheduled_start_at,scheduled_start_source}`. Title/video/tournament come from existing youtube_live settings. Start is null while dark or unconfigured. A configured start has source `admin_set`. No YouTube scheduledStartTime fetching or automatic stream-state detection is installed.
- viewer_count:null, viewer_count_source:null. No YouTube API concurrent-viewer worker or credential is wired. Hide the viewer count, never replace with an estimate or zero.
- squads: up to 12 eligible registered squads for the linked tournament. Each has `{name,members:[{name,avatar_url}],igl:null,captain_name}`. Members are the actual canonical registration-player names, up to 4. Where no detailed roster exists, only the actual registered captain is returned. Captain fallback avatar uses the registration's bound user profile; teammate name strings do not authorize profile matching. UID/phone/device are never exposed. Held, rejected, cancelled, declined, waitlisted and banned/blocked captains are excluded. This is the overview's structured extension of the same registration source used by xr_live_roster; the legacy API remains unchanged. Captain does not prove IGL, so igl stays null rather than inventing that role.
- welcome_pin: null while dark, unpublished or live_thread is off; otherwise `{author:'XARENA',body,updated_at}` from real admin content. This is a pin read, not an inserted member message. No sample welcome was published.
- poll: null while dark or with no actual poll; otherwise `{id,question,options,counts,my_vote,closes_at,status}` for the most recently created poll bound to this video. Counts are actual nonbanned voters. Choice indexes are zero-based. Closed polls can display real results with status `closed`; never render them as still accepting votes.
- follow: null while dark, without an explicit host, or for a banned/blocked host; otherwise `{following,followers,host_id}` from canonical follows. Follower counts exclude banned accounts. Following is viewer-specific; host identity is explicit admin configuration, not guessed from the title or tournament name.
- prediction:null, drop:null, early_viewer:null, watch_streak:null. These workflows are not implemented by this migration.
- gifts:null while dark. After activation, returns `{enabled:false,catalog:[{key:'rose',name:'Rose',coin_cost:10},{key:'fire',name:'Fire',coin_cost:50},{key:'crown',name:'Crown',coin_cost:500}],disabled_reason:'earned_balance_and_shared_debit_safety_not_available'}`. This is disabled configuration only, not a purchase promise. No send RPC exists and no coins can be spent through this feature.
- notification_delivery_available:false. No push, in-app delivery worker, Bell/reminder, missed-kill or replay notification exists.

`growth_enabled` is effective state, not the raw switch: the raw switch must be true AND the bound video must equal the current active canonical video. Changing the canonical video or deactivating it hides old schedules/pins/polls/follow configuration and rejects votes/follows. This prevents stale data from carrying into another stream.

## Admin settings

`xr_live_growth_admin({p_patch:{...}})` requires the existing authenticated admin role. Allowed keys:

- enabled: default false. Wait for the owner's go before activation.
- video_id: explicit current video binding, null initially. Valid 11-character YouTube id.
- scheduled_start_at: nullable actual announced time, never guessed. Null means no countdown. Admin-set time is not proof the player is currently live and starts no job.
- host_user_id: nullable explicit existing nonbanned profile id. Configure only the actual host. It is not inferred from an unverified display name.
- welcome_body: nullable plain text, 1..200 characters, no control/HTML characters.
- welcome_published: default false; publishing requires nonempty body. Appropriate wording/publication authority is still required. Pin timestamp changes only when body/publication fields are changed.

Unknown keys are rejected, including any attempt to set viewer_count or rewards. Enabling requires current active-video binding. No activated settings were written.

## Polls and follow

`xr_live_poll_admin({p_question,p_options,p_closes_at})` requires admin. Settings must be bound to the current active video; the master switch may remain off while preparing a real poll. Question 3..160 characters, 2..6 distinct nonempty short text choices, close in the next 24 hours. Creating a poll closes previous open polls for the same video. Returns `{ok,poll_id}`. No choice/winner/count is fabricated. Polls have no reward and cannot change tournament rules or schedules.

`xr_live_poll_vote({p_poll:uuid,p_choice:integer})` requires an unrestricted authenticated account and effective growth enabled. One immutable choice per account/poll; retry returns already_voted:true, does not change an existing vote or add a second vote. Wrong video, invalid choice, closed poll, expired time or disabled growth is denied. Reads include the caller's actual my_vote. No client heartbeat or mic access is required to vote, and no claim of verified YouTube viewing is made.

`xr_live_follow_set({p_on:boolean})` requires unrestricted authenticated caller and an effective enabled stream with a configured valid host. Idempotently inserts/deletes canonical follows. Self-follow and blocked host are denied. It does not send a message, subscribe to a Bell, or promise notifications. Existing follow APIs remain unchanged.

## Features intentionally held

### Gifts and earned-only spending

The shared points ledger mixes ordinary earned rewards and admin grants, with no certified earned-only spendable balance. It has no universal debit/negative-balance guard. Several existing spending functions read balance and debit without one common account lock. A gift-only lock cannot prevent a simultaneous old-route spend from causing an overdraft. It is unsafe to claim bounded earned-only gifts without a separate wallet-wide concurrency and provenance repair.

No gift send/debit/recipient credit/event path was added. No gift system message was inserted. Even enabling Live growth cannot enable gifts. The catalog is not an active sales surface. This migration does not silently change unrelated wallet/spending functions.

### Prediction/drop/attendance rewards

No prediction or live-drop settlement/grant workflow is installed. No 200/10/50-coin reward claim is supported by this change. Admin-created real poll choices are not Booyah predictions. A client heartbeat does not prove YouTube viewing, so no early-viewer ranking/badge or watch streak/bonus is asserted. Existing badge/ledger tables alone do not prove attendance or safe settlement.

### Missing sources and dropped modules

Concurrent viewers and scheduled start are not scraped or inferred. Only the explicitly admin-set scheduled time is supported. Kill overlays, squad/map cams, live clip capture, missed-kill replay, and reminders are absent. No false urgency, automatic slot loss, or reward deadline is generated.

## Privacy and load

Private settings/polls/votes tables have RLS enabled and no anon/authenticated direct grants. Only scoped RPCs expose public aggregates and one's own vote. No private player identifiers are added. This migration does not weaken existing thread controls or change the legacy roster.

Live is intentionally not denied by the feed/clip match-day kill switch. Responses are bounded to 12 squads, 4 members each, and 6 poll options. Refresh hints are client instructions, not a shared cache or a 5000-user load-test result. No new Realtime/push/background polling job is installed.

## Tests, deployment, rollback

`20261112-live-growth.sql` is a one-time additive migration. `20261112-live-growth-tests.sql` rolls back all fixtures. Tests passed before and after deployment: dark state, anonymous personal nulls, no viewer/reward fiction, admin-only setter, current-video binding, real roster names/private UID/phone exclusion, idempotent follow/self/block handling, duplicate-choice validation, real vote counts/my_vote/immutable retry, invalid/closed vote denial, changed-video fail closed, banned-user denial, and unchanged ledger/thread counts.

Actual public HTTP overview returned200 with the existing stream title/video, zero eligible squads and unknown start/viewers; private settings/votes and admin setter returned401. Canonical youtube_live/flags, ledger, thread posts and follows fingerprints were unchanged. No fixture rows persisted. Production rollback SQL was exercised inside a reverted transaction. These are backend checks, not native UI end-to-end verification.

`20261112-live-growth-rollback.sql` disables growth and removes only new RPCs. Private history tables remain; existing player/thread/roster/follow APIs are unchanged. Retained tables mean reinstallation requires a deliberate repair migration, not rerunning CREATE TABLE. Native must handle unavailable RPCs after rollback. No activation/monitoring is scheduled.
