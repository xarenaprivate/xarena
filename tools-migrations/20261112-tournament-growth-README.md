# Home and tournament growth backend

Deployed November 12, 2026. Growth rules and checked-in room access are staged off. Existing registration and room access behavior is unchanged until each corresponding setting is enabled. The free-entry guards and the authorized general-chat XARENA question pin are live.

## Current truthful state

- `XR - S2` is the exact Season 2 id. It has 0 registered squads, 12 slots, 6 scheduled matches, and no growth settings.
- `xr-s1` is done, with 0 recorded registrations and no recorded end time, winner, or highlights link. These are not claims about historic participation outside the database.
- All new configuration, registration metadata, access configuration, waitlist, and Bell subscription tables are empty. No awards or sample registrations were seeded.
- General chat has one real XARENA pin: `Today's question: Pochinki or Clock Tower? Share your answer.` No member message, online count, or coin reward was fabricated.
- No deadline or prize-reveal time was invented. All entry stays free after early-bird places run out.

## Client reads

User RPCs below require an authenticated, non-banned actor. Admin setters require the existing authenticated admin role. Raw private tables have RLS enabled and no anon/authenticated table grants.

### `xr_tournament_overview`

Arguments: `{p_tournament?:text, p_after?:text, p_limit?:integer}`. Default/max limit 10. Cursor is the last returned tournament id. Exact ids are case-sensitive. Reply: `{ok,tournaments:[],next_cursor,cache_seconds:30,count_refresh_seconds:300}`.

Each card returns id/title/game/status/enabled, slots_total/slots_taken, registration_open, registration_closes_at, early_bird_free_total/left, founding_slots_left, entry:`free`, early_bird_note, prize_pool/prize_reveal_at, prize_bell_subscribed, notification_delivery_available:`false`, waitlist_open, my_registration, my_waitlist_rank, checked_in_count, city_breakdown, friends_registered, recent_registrations, and matches.

- No countdown when its timestamp is null. `enabled:false` means growth rules remain off, not that canonical registration is closed.
- Counts come from canonical registrations, excluding waitlist/rejected/cancelled/declined. They are not a claim that squads played. Held entries remain in registration totals, but cannot use staged room access.
- `my_registration` is null or `{squad_name,is_founding,checked_in}`. The check-in flag means the registered captain checked into any match in this tournament. Use the existing match-specific check-in read for one match.
- `checked_in_count` is distinct eligible nonheld registered captains across the tournament, not an online count or match-specific count.
- `city_breakdown` has up to 10 real, consenting city groups. `friends_registered` has up to 10 consenting mutual friends with both activity settings on, excluding blocked/banned profiles. No phone, UID, device, or room credentials are returned.
- `recent_registrations` has up to 5 consenting real joins with `squad_name`, `at`, and `registered_at` (alias).
- `matches` has up to 10 matches, ordered by start then id, with id/status/starts_at/access_enabled/checkin_opens_at/checkin_closes_at/room_release_at/require_checked_in. No room secret is returned here.
- These refresh hints are for clients. No background server cache or load test is implied. Large tournaments with more than 10 matches need a separate match-list route.

### `xr_tournament_season_summary({p_tournament})`

Returns actual status, nullable ended_at/winner_squad/winner_source/highlights_url and squads_count. Winner/end time are hidden unless status is done. `squads_count` is recorded registrations, NOT squads that actually played. Do not label it `played`.

A winner must be admin-confirmed and backed by a real placement-1 result in a completed match. `winner_source` is `admin_confirmed_completed_first_place_result`. This does not calculate a full-season scoring champion. No winner is inferred from an example name.

### `xr_home_chat_preview({p_room?:'general'})`

Allows general or the caller's own normalized state room. Returns `{ok,pin,latest_message,online_count:null,online_count_source:null,first_message_reward_available:false}`. Pin is null or `{author:'XARENA',body,updated_at}`. Latest message is null or `{handle,body,created_at}`. Empty state is honest. No online-count source or first-message reward exists in this change.

## Private user writes

- `xr_prize_reveal_subscribe({p_tournament,p_on})`: idempotent private intent. Always returns `notification_delivery_available:false`. No push, in-app reminder, delivery worker, or scheduled send exists. Until delivery is implemented, render an information line rather than a reminder promise.
- `xr_tournament_registration_sharing({p_tournament,p_public_activity,p_friends_visible,p_city_visible})`: explicit opt-ins, all default false. Existing registrations can opt in without receiving awards. No registration means no metadata is created.
- `xr_tournament_waitlist_join({p_tournament,p_squad})`: separate interest queue when admin enables it. Stable real rank, idempotent. Returns `registered:false`. It does not grant entry, promote a squad, reserve a paid place, or evict a no-show.

## Admin controls and activation

`xr_tournament_settings_admin({p_tournament,p_patch:{...}})` accepts only:

- enabled (default false)
- registration_closes_at (nullable)
- waitlist_enabled (false)
- founding_limit (3, bounded 0..100)
- early_bird_total (4, bounded 0..100)
- prize_pool (nullable text, max 200), prize_reveal_at (nullable)
- ended_at, winner_squad, highlights_url (nullable HTTPS URL)

Do not activate without the owner's go. Use real announced times/rewards only. Prize value is not exposed until its explicit reveal time. No reminder worker is started by setting a time. Registration cutoffs are enforced in canonical `register_squad` and new-registration triggers when enabled.

Founding and early-bird awards are forward-only. Only new canonical registrations created while enabled become eligible. First qualifying nonheld/nonwaitlist/nonrejected entries consume a serialized monotonic ordinal; retries/status updates cannot mint extra places. First 3 get `is_founding`; first 4 get `is_early_bird`. Counts do not reset when a registration cancels. Old registrations are never relabeled. The canonical keyed metadata lives in private `xr_tournament_registration_meta`, not an added registrations column. It survives a feature rollback. A profile-wide lifetime badge UI is not added by this migration.

`xr_match_access_admin({p_match:uuid,p_patch:{...}})` accepts enabled, checkin_opens_at, checkin_closes_at, room_release_at, require_checked_in. Defaults: disabled, check-in T-60 through T+15, room T-15, checked-in required. Enabling requires a real start and require_checked_in=true. An explicit release cannot be earlier than T-15.

When enabled, existing `match_room`/`match_checkin` route through the guarded implementation. Room access requires an eligible nonheld registered captain or canonical squad teammate AND the registered captain's check-in. It denies banned actors, done/cancelled matches, missing starts, and early release. Release is the latest of custom time, T-15, and canonical reveal_minutes, preserving stricter existing limits. Check-in requires an eligible registered captain and a real start inside the configured window. Repeated check-in is idempotent. The old paths remain in effect when disabled; do not claim checked-in-only protection is globally active.

`xr_chat_pin_admin({p_room,p_body,p_publish?:false})`: general or `st-...` public room only, max 300 characters, admin-only. Future wording still requires appropriate publication authority. No per-state sample messages were seeded.

## Free-only enforcement

Tournament writes with positive entry_cost_ap and registration inserts/updates to positive ap_paid fail. Canonical registration refuses positive entry prices. No payment, cash, or AP spend route was added. Early-bird is a label on free places, never a later fee.

## Verification

- Full rollback-only fixtures passed before and after deployment: staged defaults, no retroactive awards, exact first3/first4, retry safety, opt-in activity, private fields excluded, cutoff enforcement, real queue ranks, Bell idempotency/no delivery, paid writes denied, admin-only writes, missing-start fail closed, check-in and room windows, hold/waitlist/banned denial, stricter reveal_minutes, and source-backed winner.
- A rollback of the deployed migration was exercised inside a transaction and reverted successfully.
- Actual authenticated-role reads returned Season 2's real 0/12 and Season 1's done/null summary. These are database-authenticated tests, not native UI end-to-end verification.
- Anonymous HTTP calls to overview/chat RPCs and private settings/meta/pin tables returned 401 permission denied.
- Canonical tournament/match/registration fingerprints and check-in/chat counts were unchanged. No fixtures persisted.

## Deployment and rollback

Files: `20261112-tournament-growth.sql`, `20261112-tournament-growth-tests.sql`, `20261112-tournament-growth-rollback.sql`.

The migration is one-time, not rerunnable. Tests always roll back. Rollback disables settings, removes new functions/triggers, and restores the prior canonical register/check-in/room bodies. It keeps private history tables and the question pin row, but the new read RPCs no longer exist. It also restores the prior entry behavior: review free-only policy before executing rollback. Reapplying after rollback requires a deliberate repair migration because retained tables still exist. No UI, deployment monitoring, or automatic activation is bundled.
