# Creator growth backend (staged)

Deployed November 12, 2026. **Staged: new application flow is disabled.** All player RPCs require a signed-in, unrestricted XARENA account. Anonymous denied. Native must use its authenticated API helper, handle `ok:false` and propagate honest server exceptions. Existing Creator hosting, event join, room unlock, following and admin account checks remain in place.

Current defaults are policy, not activity: 10 creator slots per city, first 10 verified creators eligible for a founding badge, first 50 eligible for a limited cosmetic entitlement. No actual city fill, application, creator, activity, friend, entitlement or event data was seeded. No application deadline or bonus campaign was invented. No money/points were credited.

## Player RPC contract

All calls are POST `/rest/v1/rpc/<name>`, authenticated JWT. Defaults may be omitted. Human-readable validation failures generally return `{ok:false,error:<text>}`. Restricted/unauthenticated calls raise 42501. Existing backend exceptions must be shown honestly, not collapsed into a dead button.

### `xr_creator_apply`

```json
{
  "p_name": "Applicant Name",
  "p_city": "Patna",
  "p_instagram": "handle.without.url",
  "p_player_count": 25,
  "p_public_activity": false,
  "p_friends_visible": false
}
```

Required: name 1-80 characters (no numbers, @, control characters or angle brackets), city 1-60 (no control characters/angle brackets), Instagram handle 1-30 letters/digits/dot/underscore, player count integer 0..1000000. Leading @ is removed from Instagram; no URL accepted. City matching is case-insensitive after outer trim. These are applicant-supplied facts; player count and Instagram ownership are not independently verified.

Opt-ins default FALSE. Explain each before setting it: public activity publishes first name + city + actual application/slot event; friend visibility shares first name + city with real mutual friends. Never silently opt a player in. The private Instagram handle and player count go only to the applicant and admin review queue.

Returns `{ok:true,application_id:<bigint>,stage:"applied"|"waitlisted"}`. While staged, `{ok:false,error:"not_open"}`. No charge. One pending canonical application per player through this flow; at most 3 applications per 24 hours. Existing verified creators cannot apply again. A full city or passed configured deadline routes to waitlist, not a fictional rejected/accepted creator.

### `xr_creator_my_status` body `{}`

Returns `ok,is_creator,application_id,stage,name,city,instagram,player_count,public_activity_opt_in,friends_applied_opt_in,waitlist_rank,qualified_referrals,boost_points,entitlements`.

Stages: applied, under_review, waitlisted, verified, rejected, revoked. No application means null stage/id and empty entitlements. Canonical old applications without new form details retain old pending/approved status strings, not guessed city/name/player counts. `is_creator` comes from the active canonical creator record.

Waitlist rank is within the applicant's city. Priority: qualified real referral boost points descending, then actual application time and id. Qualifying referrals must exist in the real referrals table, refer to another unrestricted player, and that player must actually apply after the inviter's application. No arbitrary invite counter, simulated boost or guaranteed "3 invites = #12" claim. `qualified_referrals` and `boost_points` are actual counts; ranks can change as other people apply. There is no new outbound invite sender in this migration.

Entitlements contain `kind,key,status,amount_inr,coins`. Founding badge is `granted`, cosmetic is `pending_owner_fulfillment`, bonus is `pending_funding` or `pending_owner_fulfillment`. **An entitlement is not a payout, an AP balance, a delivered asset or proof of funding.** Show its status explicitly. No auto-payment, no points-ledger mutation, no redemption/withdrawal claim.

### `xr_creator_sharing`

Body `{p_public_activity:boolean,p_friends_visible:boolean}`. Updates the caller's application sharing choices; returns `{ok:true}`. Revoking opt-in immediately removes those application events/friend entries from corresponding lists. No other account can change them.

### `xr_creator_city_status`

Body `{p_city:"Patna"}`. Returns `ok,city,capacity,filled,remaining,applications_open,deadline,waitlist_only,founding_limit,limited_drop_limit,bonus`.

`filled` counts real active creator verifications assigned to the city. Banned but still active creators continue consuming a slot until the owner revokes them. `remaining=max(capacity-filled,0)`. If no city row exists, default configured capacity is used with actual filled=0; the read creates no city or activity. Do not say "3 full, 7 left" unless those are returned numbers.

`applications_open` is false while staged or after a configured deadline. `waitlist_only` indicates deadline/full-city routing, but is NOT permission to ignore staging. No deadline means no countdown. `bonus` is null unless an explicitly enabled, time-valid, owner-funding-confirmed campaign exists. It never says a bonus was paid.

### `xr_creator_activity_list`

Body `{}` or `{p_limit:20}`, clamp1..50. Returns `{ok:true,events:[{kind,first_name,city,created_at}]}`. Kinds: applied, slot_taken. Slot taken is emitted only by real admin verification/reactivation. The feed reads actual recorded events only and filters current opt-in, bans and bilateral blocks. No profile/user id, full name, Instagram or player count in feed. Empty array is normal.

### `xr_creator_friends_applied` body `{}`

Returns `{ok:true,friends:[{id,first_name,city}]}`, maximum50. Requires a real reciprocal friend relationship, applicant's explicit opt-in, pending/approved application and no ban/block. Empty means no visible consenting friends, not an invitation to invent examples. Native can show the count as array length (up to50); this is not an unlimited total.

### `xr_creator_tournament_seats`

Body `{}` or `{p_creator:<creator uuid>}`. Returns `{ok:true,tournaments:[{id,creator_id,title,status,starts_at,capacity,registered,remaining,entry:"free"}]}`, up to50 actual scheduled/live canonical creator events.

`registered` is actual joined event entries (not checked-in/attended players). Excludes kicked/banned/left entries. No room ID/pass, phone, UID, email, proof, IP or private status token. Existing `creator_event_join` remains the lock-protected free join path; room unlock remains in `creator_event_room`.

### `xr_creator_city_leaderboard`

Body `{}` or `{p_city:"Patna"}`. Returns `ok,creators,metric:"current_joined_event_entries",creator_of_week,week_starts_at,week_timezone:"Asia/Kolkata"`.

Creator cards: `id,username,name,avatar_url,city,followers,event_joins,founding`. Followers are actual in-app follows, not the old self-reported social follower count. City may be null for legacy creators. Rank is actual current joined entries on non-cancelled events, not match wins, attendance, skill, cash or coin earnings. Zero remains zero. Week winner uses the same actual joined-entry metric for the IST week starting Monday; null if no qualifying joins. Ties are deterministic. No manual home-banner winner is fabricated.

### `xr_creator_dashboard` body `{}`

Active creator only. Returns `ok,followers,events_hosted,joined_entries,unique_players,coins_earned:null,coins_source,tournaments,status`. No creator commission ledger exists, so coins earned is unknown rather than a fake zero or promised cash commission. Joined entries and distinct participating accounts are separate counts. Use existing Creator free tournament creation/management and follow-toggle functions for those actions.

## Admin RPC contract

Authenticated real admin only. An admin link is not itself an integration: these controls still need wiring into the web admin UI.

- `xr_creator_settings_admin({p_patch:{...}})` reads/patches settings, returns `{ok:true,settings}`. Keys: `enabled` (defaultfalse), `default_city_slots` (10), `application_deadline` (null), `founding_limit` (10), `drop_limit` (50), `drop_key` (`limited_creator_cosmetic`), `waitlist_boost_per_referral` (1), `bonus_enabled` (false), `bonus_starts_at`/`bonus_ends_at` (null), `bonus_inr`/`bonus_coins` (0), `bonus_funding_confirmed` (false). All numeric fields bounded/nonnegative; enabled bonus needs a valid window. An internal monotonic verification counter cannot be patched/reset via this endpoint.
- `xr_creator_city_admin({p_city:"Patna",p_capacity:10})`: per-city override, 0..10000. Returns `{ok:true}`. Changing default capacity does not silently overwrite existing city overrides.
- `xr_creator_admin_queue({})`: private pending application queue with `id,name,city,instagram,player_count,player_count_source:"self_reported",stage,created_at`.
- `xr_creator_review({p_application:<id>,p_action:"under_review"|"verify"|"reject"})`: pipeline. Returns stage on success or `error:"city_full"` if verification would exceed capacity. Review retries are idempotent. Under-review keeps canonical status pending. Verified maps to canonical approved and creates/reactivates the canonical Creator. Legacy `admin_creator_review(p_id,p_approve)` routes through this same review logic and retains `approved` in success responses.

Approval/grant triggers serialize on owner settings and city rows. Real first-time verifications get monotonically increasing ordinals, including legacy admin grants. Reactivation does not mint another founding badge or drop. Account deletion cannot recycle the ordinal. First N entitlements are immutable snapshots at verification; later cap/key changes are prospective and do not retroactively allocate/revoke rewards. Founding badge survives normal revocation/reactivation as a lifetime record, but does not bypass bans or confer profile/ID verification. Limited drop grants record a claim awaiting owner fulfillment; they do not install a jersey/voice asset or enforce future shop exclusivity.

Legacy applications that omit city metadata remain unassigned. They must not be presented as occupying a named city's slots. Existing old apply flow is preserved while the new flow is staged. A city-bound applicant cannot bypass the city cap via the existing approve/grant functions.

No campaign is enabled, paid or funded by this deployment. Before publishing a real ₹/coin bonus offer, owner must set the actual amount, dates, funding and fulfillment terms. No player deposits, paid entry, wager, withdrawal or cash conversion is implemented. Existing AP/XP remain separate from money.

## Tests and rollback

Pre- and post-deploy rollback suites pass. Coverage: staging/admin authority, inputs, no duplicate pending application, applied/reviewed/verified pipeline, city count/cap enforcement, honest waitlist, first-N grants/idempotent regrant, lifetime ordinal, explicit sharing and withdrawal, real mutual friendships, blocks, real seat joins, city/week metrics, deadline routing, private unfunded bonus entitlement and grants. Actual authenticated-role live reads pass and show honest empty states. Raw canonical/private application and entitlement tables have no anon/authenticated direct grants. No canonical application, creator, event, event player, or points ledger row was changed by deployment/tests. Tests are for the initial empty staged rollout; do not rerun unchanged after real use. No multi-session load test or native/admin UI E2E claimed.

`20261112-creator-growth-rollback.sql` disables new applications, removes new endpoints/approval trigger and restores the original legacy admin review function. It intentionally retains private tables and entitlements so rollback cannot erase award history. It does not undo actual approvals, withdraw payouts or restore prior personal data. The rollback path was exercised while staged with zero applications/creators, then RPCs restored and tests rerun. The initial CREATE migration is not an idempotent re-enable script; use a reviewed forward migration.

## Not included

Creator follower-group chat, automatic room-host provisioning/promotion, cash payouts, coin commission rules, fulfilled cosmetics, new admin web controls, native UI/visual verification and invitation delivery are not built by this backend migration. Existing free event/follow/room features stay in use. Do not label these unsupported items live.
