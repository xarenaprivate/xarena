# Profile growth reads

Deployed November 13, 2026. Growth staged off. Authenticated read only, plus one admin activation setter. No reward, XP, badge, profile field, task, result, or referral was created or changed.

## Contract

`xr_profile_growth({})` requires an existing unrestricted authenticated account. Replies with ok, growth_enabled, cache_seconds:30, count_refresh_seconds:300 and:

- verified_friends_count: null while dark, otherwise actual verified mutual friends. Both activity preferences must be on; banned/blocked users excluded. This is the profile verified flag, not a promise of phone verification or a reward.
- city_rank:null. The current location leaderboard ranks XP, not wins. No wins_to_next number is invented, and editable player names are not treated as stable result identity.
- badges: `{earned:[{key,name}],next:null|{key,name,requirement,progress,target}}`. Earned badges come only from the caller's real badges rows, bounded to 100. Name is the stored key with underscores replaced by spaces, not an invented achievement. Next is hidden while dark; when enabled, it shows the next unearned existing daily-check-in milestone among streak_3, streak_7, streak_30. Progress is actual current check-in streak, reset to zero if the last check-in is older than yesterday in IST. No new badge is granted by reading.
- friends_comparison: `[]`. Account-bound verified wins are not available for a trustworthy comparison. The legacy leaderboard joins result player names to editable IGN strings; that does not establish identity. Self-edited profile win counters are not used as confirmed results.
- profile_completeness: null while dark, otherwise `{percent,missing:[],basis:'username_ign_avatar_city',creator_apply_gate:false}`. Four equally weighted, explicit fields: username, IGN, avatar URL, city/district. Nonempty field is 25%; missing lists field keys. It is an informational profile checklist, not proof of rank/identity or a condition for Creator apply. No private UID, phone, or exact location is returned. The percentage is not an external eligibility rule.
- avatar_xp_reward:null. Existing set_avatar updates the avatar URL and does not award XP. No photo reward was added.
- first_win_reward:null. No account-bound first-win badge/coin grant is implemented here.
- daily_tasks:null. Existing tasks are admin-reviewed submissions, not a daily reset task system. No fake daily checklist, midnight reset, or combined reward is implied.
- top_player_today:null. No verified daily account-bound win ranking is available. Neither example names nor editable stats are used.
- my_clips_count: actual own approved feed_posts with kind='clip', including zero. It is null while the existing clip/feed ops switch sheds clips. `clips_temporarily_unavailable` distinguishes that state from zero. No legacy photo/video is relabeled as a clip. This safe own-data read is available even while growth is dark; client may still hide its tile under the master gate.
- streak_reward_coins: null while dark. When enabled, this is the existing daily_checkin AP grant for the current IST day's check-in, NOT a new streak bonus. Formula: 5 + min(next actual streak,10), giving 6..15 AP. If already claimed today, returns the amount for today's already-claimed check-in. `streak_reward_already_claimed` MUST be respected: do not promise a second claim. `streak_reward_unit:'AP'`, `streak_reward_source:'existing_daily_checkin'`, and actual current `streak_days` are explicit. Use the existing daily_checkin flow; this read never awards points.
- share_referral_attribution_available:false. There is a general new-account referral-code system, with existing 50-point rewards, but no link between it and a stats-card share, three installs, or a 100-coin share reward. Do not advertise stats-card rewards from that general mechanism.
- notification_delivery_available:false. No comeback/rank-drop reminder worker, push, or in-app delivery exists. Reading actual information is not notification delivery.

## Existing daily-check-in grounding

Canonical daily_checkin locks player_game_stats, returns already:true for a repeat on the same IST date, advances or resets the streak, and calls award_xp with 5 + min(streak,10). Existing award_xp records both XP and a positive AP points-ledger entry. Existing code awards streak3/7/30 badges when actually reached. None of those functions are replaced or weakened by this migration. No fixed 50-coin/500-coin streak claim is supported.

This contract describes the current calculation, not a promise about a future day or an unconditional grant. Missed days reset it; account restrictions and the canonical write flow still apply. AP/XP are not money or paid entry. Do not label AP as cash.

## Admin activation

`xr_profile_growth_admin({p_enabled:boolean})` requires the existing authenticated admin role; null is rejected. Returns `{ok,growth_enabled}`. Settings default false and remain false at deployment. Wait for the owner's go before enabling. No time-based activation or monitor is scheduled.

Unsupported fields remain null after activation. Turning on growth does not create photo/verification/first-win/share rewards, a city-win ranking, daily tasks, or a notification service. Creator application has no new completeness gate. No 100-coin verification grant is added.

## Privacy and operational behavior

Only the caller can read this profile-growth response. Private settings have RLS enabled and no anon/authenticated direct table grants. No arbitrary target-user argument exists. Verified-friend count uses real mutual edges plus activity/block/banned checks. The read never leaks friend's phone/UID/device, private verification details, or exact location.

Profile text/badges remain readable when feed/clips are shed. Own clip count avoids the feed count read and returns null while clips are shed. Cache hints are client hints, not a shared cache or a load-test claim. There is no new background refresh or persistent notification job.

## Tests and live checks

Pre/post-deployment transactional tests passed: unrestricted auth required, default dark, player cannot use admin activation, bounded checklist, actual check-in reward formula6..15, real badge shape, unsupported reward/rank/referral fields absent, no private identifiers, clip-shed behavior, banned-account denial, and no ledger/badge mutation. All fixtures reverted.

Actual authenticated-role read for the tested account returned dark, empty earned badges, zero current streak, zero approved clips, null unsupported claims and unclaimed check-in status. Anonymous HTTP profile/admin/settings probes returned401. Points/badges/player-stats/profile fingerprints were unchanged. Rollback SQL was tested inside a reverted transaction. These are backend checks, not native UI end-to-end verification.

## Files and rollback

`20261113-profile-growth.sql` is a one-time additive migration. Tests in `20261113-profile-growth-tests.sql` always roll back. `20261113-profile-growth-rollback.sql` disables growth and removes only the new read/setter functions; private settings table remains. Existing profile, daily_checkin, ledger, badge and task functions are untouched. Retained settings mean a later reinstall needs a deliberate repair migration, not rerunning CREATE TABLE. Native should tolerate unavailable RPCs after rollback.
