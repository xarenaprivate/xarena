# Feed growth backend

Deployed November 12, 2026. Growth is staged off. Additive RPCs only: existing feed_list/feed_saved/create/comment/like/save signatures and behavior are unchanged. No coins, badges, likes, posts, comments, saves, highlight picks, or battles were seeded.

## Live truth and unsupported claims

At deployment: one approved photo from October 19, zero approved posts today, two nonself/nonbanned likes on that post, one comment, two saves. The legacy like count includes a self-like; the new route intentionally excludes it. These are real counts, not targets or examples.

- `post_reward_coins:null`. Canonical feed_create does not grant coins. Do not say "post today, earn 50 coins".
- `first_comment_reward:{enabled:false,slots_total:0,slots_left:0,coins:0}`; per-post first_comment_reward_left is 0. No first-comment reward is implemented.
- Streak bonus_coins is null. No reward/ledger mutation is added, and no TOP FAN or creator badge is granted by this change.
- `battle:null`. Battle setup, consent, voting, outcome, and payout are not implemented.
- No share/referral attribution or share-to-status reward. No highlight reward or automatic Home placement.
- `notification_delivery_available:false`. There is no push, scheduled reminder, or in-app notification-delivery worker. Actual activity reads can be rendered as in-app information lines, but are not a reminder subscription.
- `author_rank:null`. Self-edited Free Fire rank is not a confirmed competition rank. Do not infer it.

## `xr_feed_overview({})`

Anonymous-safe read with optional authenticated personalization. Returns:

- ok, growth_enabled, cache_seconds:30, count_refresh_seconds:300.
- new_today_count: actual approved posts from nonbanned authors, using the IST calendar day. Authenticated callers exclude blocked authors.
- trending_threshold: default 100. This is an admin setting, not fabricated popularity.
- highlight_of_the_day: null while dark, otherwise null or `{post_id,author,chosen_at}` from a real admin pick of an approved post created today. A rejected/deleted/banned/blocked author's post is not displayed.
- next_pick_at: null while dark or unset. Setting a real planned time does NOT create a picker job. Do not promise automatic selection.
- weekly_top_fan: null while dark or when there is no eligible activity, otherwise `{handle,my_rank,score,score_basis}`. Score is the number of distinct other-author approved posts engaged with by like or comment in the current IST Monday-start week. Repeating comments on one post cannot add points. Self-interactions do not count. Only nonbanned users with show_activity enabled are named; authenticated callers exclude blocked people/posts. Ties break by stable user id. This is a current computed ranking, not an awarded badge, immutable contest result, or fraud-proof award system.
- my_streak: null anonymously, otherwise `{days,posted_today,bonus_coins:null,basis:'approved_posts_IST',bounded_to_days:366}`. Actual consecutive approved-post dates, capped by the 366-day lookback. Yesterday's streak remains visible until today is missed. No reward or invented urgency.
- friends_posted_today: up to 10 actual mutual friends with an approved post today; both activity preferences must be on. Blocked/banned profiles excluded. Empty while dark.
- my_post_activity: up to 5 own approved posts with real subsequent nonself/nonbanned/nonblocked likes/comments, including post_id/new_likes/new_comments/latest_commenter. Empty anonymously, while dark, or before a seen baseline exists. This is recent stored interaction activity, not delivered notifications or proof someone viewed a post. Unlikes/deleted comments are not retained as new events.
- activity_since: caller's actual seen baseline or null.
- my_city_visible: caller's saved feed-city opt-in, false by default.
- post_reward_coins, first_comment_reward, battle, notification_delivery_available as above.

The new functions check the existing load-shed switch. A shed feed returns the same XR_BUSY / HTTP503 / Retry-After behavior as the existing routes. Banned authenticated accounts are rejected, not silently treated as anonymous.

## `xr_feed_list`

Arguments: `{p_mode?:'latest'|'saved'|'trending',p_cursor?:object,p_limit?:10}`. Default latest; max page size 10. Returns `{ok,posts:[],next_cursor,growth_enabled,cache_seconds:30,count_refresh_seconds:300}`.

Feed cards preserve id/kind/media_path/caption/author/author_id/likes/liked/saved/comments/following/at and add author_city/author_rank/save_count/trending/likes_to_trending/first_comment_reward_left plus a cursor object.

- latest: approved posts ordered by actual created_at then id descending. Unlike the legacy route, this is truly latest, not followed authors first.
- saved: authenticated caller's actual saved approved posts, ordered by saved_at then id descending. Anonymous request returns ok:false, Login required.
- trending: while growth is enabled, approved posts with at least the configured number of distinct nonself likes from nonbanned users, ordered by those likes, post time, and id descending. While dark it returns an empty array. All-time likes are used; there is no recency claim.
- author_city is null unless that author explicitly opts into this feed-city display. author_rank is always null in this version.
- likes counts exclude self-likes and banned likers. comments/save_count exclude banned contributors. No public list of savers is disclosed. Counts do not reveal private phone, UID, device, precise location, or new private profile fields.
- Blocked/banned authors and nonapproved posts are excluded. Growth flags are false/null while dark, even on posts that would otherwise reach the threshold.
- Pass next_cursor back unchanged as p_cursor. It contains mode/id/at/score. A cursor from another mode is rejected. Ranking can move as likes change, so pages are not a fixed snapshot; pull to refresh for current order.
- Legacy APIs remain available for old clients; the new privacy/count/order semantics are on this route. No legacy counts were rewritten.

## User writes

`xr_feed_activity_ack({})`: authenticated nonbanned caller. Sets a private actual seen_at baseline, idempotent in effect. Read and render overview first, then call it when the user has reviewed activity. First visit has no baseline and no fake "new likes" count. Subsequent activity is counted since that baseline. Do not call before rendering or on a silent background refresh, which would mark unseen activity as seen. A concurrent interaction between read and ack can be cleared by this simple acknowledgement model; exact delivery receipts are not claimed.

`xr_feed_author_sharing_set({p_city_visible:boolean})`: authenticated nonbanned caller. Explicit city opt-in, false by default. Getter is overview.my_city_visible. Only the profile's district/city label is used, never coordinates. It does not change any broader profile privacy setting.

## Admin controls and activation

`xr_feed_growth_admin({p_patch:{enabled?,trending_threshold?,next_pick_at?}})` requires the existing authenticated admin role. Unknown keys are rejected. Threshold must be 1..100000. No settings other than default disabled/threshold100/null pick time were created. Wait for the owner's go before enabling.

`xr_feed_highlight_pick({p_post:bigint})` requires admin. The post must be approved, created today in IST, and from a nonbanned author. Stores one real pick per IST date, with actual admin identity/time. Picking again replaces today's pick with a new actual chosen_at. It grants no coins, starts no cron job, and sends no message. The overview does not show a pick while growth is off.

No automatic pick, battle, reward, referral, or notification job is installed. Do not invent an evening deadline. `next_pick_at` is optional admin planning data and does not enforce an automatic pick.

## Privacy, scaling and limits

Private settings/picks/seen/sharing tables have RLS enabled and no anon/authenticated table grants; access is through scoped RPCs. Public reads personalize only when a valid account is present. They expose existing public post identity but no phone/UID/device. Friends and fan names use activity preferences and block filters; city is a separate explicit opt-in.

Responses are bounded: 10 posts/page, 10 friends, 5 activity posts. Refresh values are client hints, not a shared server cache. Counts/rankings still query canonical data. Supporting post-status/date, comment-post/date and like-post/date indexes were added; this is not a 5000-user load-test result or a claim of constant-cost aggregate scans. Respect the existing match-day feed/clip load-shed switch.

## Verification and rollback

- Transactional pre/post-deploy fixtures passed: default dark/public null personal data, bounded pagination, no repeat for a stable keyset, real saved/trending ordering, self-like exclusion, opt-in getter, seen activity, mutual friend/activity privacy, blocks, real weekly fan, admin-only settings, real today-only picks, rejected-content hiding, banned-account denial, load shedding and no ledger mint.
- Actual public HTTP overview/list returned200 with honest zero/old-post state; saved was login-required. Admin RPC and private state/sharing tables returned401 anonymously.
- Actual authenticated-role overview returned a real zero approved-post streak and empty saved list for the tested account. This is not native UI end-to-end verification.
- Canonical post/like/comment/save/points fingerprints were unchanged. All fixtures rolled back. Rollback SQL was exercised inside a transaction and reverted successfully.

`20261112-feed-growth.sql` is one-time migration. `20261112-feed-growth-tests.sql` always rolls back its fixtures. `20261112-feed-growth-rollback.sql` disables growth and removes the additive RPCs. It leaves private configuration/history tables and supporting indexes intact, and does not change legacy APIs or posts/points. Retained tables mean a later reinstallation needs a deliberate repair migration, not rerunning the original CREATE TABLE script. New native clients must handle unavailable RPCs on rollback. No activation/monitoring is scheduled.
