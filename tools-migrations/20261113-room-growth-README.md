# Clan Room growth reads

Deployed November 13, 2026. Staged dark. Additive authenticated overview and admin settings only. Existing room_info/room_feed/room_post, squad membership, plans, check-ins and voice APIs are unchanged. No sample messages, plans, requests, match entries, wars or rewards were seeded.

## Read contract

`xr_room_growth({})` requires an unrestricted authenticated account that is a current squad member. No target-squad argument exists. No squad returns `{ok:false,error:'no_squad'}`. A blocked captain returns Squad unavailable. Anonymous/banned callers are denied.

Returns ok, growth_enabled, cache_seconds:30, count_refresh_seconds:300 and:

### Squad header and roster

`squad:{id,name,tag,city:null,tier:null,city_rank:null,region,member_count,max_members,open_seats,is_captain,members:[]}`.

- Real capacity is `xr_sf_squad_settings.max_members`, default6. Do NOT force a four-seat row or invent two remaining places. The actual UNDEFEATED read has2 members, max6, open4. A squad's capacity can differ from a tournament's four-player lineup.
- Member count includes actual membership rows; visible member cards exclude banned/blocked people. At most20 member cards. Open seats use actual total count, not the visible card count. This avoids pretending a hidden member is a vacant place.
- Cards: `{id,name,avatar_url,membership_role,combat_role,is_captain,is_me}` from canonical bound profiles/members/preferences. Role captain is distinct from combat_role IGL. IGL is shown only if that role is actually stored. Never infer it from captain.
- No squad city/tier/rank source is established. Region is an explicit Squad Finder setting, not a verified city; exposed only while growth is enabled. No profile city or name-bound leaderboard is repurposed as a squad ranking.
- Safe own-squad header/member reads are available while dark. Client may still choose to hide additions under the master gate.

### Pending requests

`pending_join_requests`: null while dark or for a noncaptain. For captain only, real pending unexpired Squad Finder requests, excluding banned/blocked applicants. No applicant names or private requests are exposed to ordinary members. Count is not a guarantee those users are eligible or will join. Zero is real zero.

### Match/check-in card

`next_match`: null while dark or without a recorded future match attached to an eligible current roster member's registration. Otherwise:

`{match_id,title,tournament_id,starts_at,registration_owner:{id,name},registered_squad_name,checkin_scope:'registration_owner_only',registration_owner_checked_in,per_member_checkins:null,access_enabled,checkin_opens_at,checkin_closes_at}`.

Canonical registrations bind to user id, not squad id. Therefore the card says an actual roster member registered, not that the current clan or all members entered that tournament. It does not join squad names to prove identity. A different registered squad name remains visible as recorded. Excludes rejected/cancelled/declined/waitlisted/held registrations, done/cancelled matches, banned/blocked owners, and missing/past start times.

The registration owner's actual match_checkins row is known. Per-member attendance is not, so it stays null rather than copying the captain's state to everyone. A configured real check-in open/close time is exposed only when that match's opt-in access setting is enabled; defaults then follow the existing staged match rules (T-60/T+15). If disabled, timestamps are null: do not invent a countdown or claim the checked-in-only gate is active. No room ID/password is returned. No no-show eviction or slot-loss behavior is added.

### Pin / plan

`pin`: null while dark or with no actual private captain plan. Otherwise `{body,revision,updated_at,source:'captain_strategy_plan'}` from existing xr_sf_strategy_plans. The captain can use the existing revision-safe xr_sf_plan_save route; this migration adds no plan-writing route and publishes no words. This is a real private plan, not an invented match announcement or a member-impersonating system message. No expiring/secret strategy protection is claimed. Render the plan only to the current authorized room member, never as public feed content.

`poll:null`. Global/live/voice polls do not have this clan audience; reusing them could cross private scopes. No clan poll was added in this release.

### Actual room activity

`activity`: null while dark; otherwise:

- messages_today: real visible nonbanned/nonblocked room messages on the IST day.
- weekly_counts: up to20 current members with actual current IST Monday-start week message counts. Other members must have show_activity enabled to be named. Own count remains available. `{id,handle,messages}` sorted by count then id. No awarded CLAN STAR badge or winner is inferred.
- last_message_at/days_since_last_message: last actual visible room message, null when none.
- my_last_message_at/days_since_my_last_message: caller's own actual message history only, null when none. This does not mean the user last visited at that time.
- streak_days: consecutive visible room-message days ending today or yesterday, bounded to90-day lookback. basis `visible_room_message_days_IST`, streak_lookback_days90. It is display-only activity, not verified member attendance. Block/banned filters can change the visible aggregate.
- reward_coins:null. No first-message, streak, comeback, top-contributor or other grant.

Aggregates derive from existing squad_room_messages, not last_seen/profile presence. A room message is not proof of a visit by every member. No online count or private member read receipt is inferred.

### Rivalry

`rivalry`: null while dark or with no future `scheduled` squad_wars row involving this squad. Otherwise `{id,opponent,scheduled_at,status,ready_states:null,source:'squad_wars_squad_ids'}`. Real opponent is joined by stable squad ids; banned/blocked opponent captain is excluded. The actual schema supports a scheduled squad challenge, but not per-war readiness. Generic member ready preference is not copied here. No win history, revenge claim, result verification or automatic match is invented. Pending/accepted challenges without a scheduled record are not advertised as a scheduled war.

### Unsupported/private fields

- voice_presence:null: voice rooms have no squad binding. Shared public voice participants do not prove a clan-private room, so no "N in voice now" claim.
- friends_without_clan:null, member_last_seen:null: no new disclosure of friends' membership or private last-seen status.
- birthday:null, secret_expiring_strategy:null, clan_fund:null, igl_shoutout:null. No real source/workflow is installed for them.
- coin_rewards_available:false. No invite/join/first-message/gift/birthday/poster/streak reward, clan fund, coin transfer or debit path exists in this change. Shared wallet earned-only provenance/concurrency repair remains outside scope. Existing unrelated grants do not authorize advertising clan rewards.
- notification_delivery_available:false. No push, in-app reminder worker or comeback delivery. Actual activity is readable information only.
- auto_break_requested:false initially; auto_break_enabled:false ALWAYS; auto_break_deadline:null. The requested settings placeholder never dissolves a squad, starts a timer, threatens a member or creates a job.

## Admin setter

`xr_room_growth_admin({p_patch:{enabled?,auto_break_requested?}})` requires existing authenticated admin. Unknown keys rejected; both fields defaultfalse and stayfalse at deployment. Wait for owner's go before enabling growth.

`auto_break_requested` is deliberately a nonoperational placeholder. Setting it true does not make auto_break_enabled true and triggers no automatic break. It must not create client countdown/threat text. Actual auto-dissolve would require a separately approved behavior, policy and implementation; it is not hidden behind this flag.

## Privacy, existing routes and load

New private settings table has RLS and no anon/authenticated direct grants. Read is caller's current squad only. No phone, UID, device, proof path, room password, IP or precise location is exposed. Own room plan stays member-scoped; captain-only request counts stay captain-only. Member names/avatars are from actual identities, not fabricated rows or name matching.

Old room_info/feed/post remain unchanged, including their current behavior. This migration does not claim to harden or replace old posting/rate-limit policies. No auto-send/pin write is performed. It adds a squad-message/date index, bounded arrays and30/300 client cache hints; these are not a shared cache or a5000-user load-test. Existing chat must avoid aggressive duplicate polling; no new polling monitor is installed. Feed/clip ops shedding does not block private clan text.

## Tests and live verification

Full transactional pre/post-deployment fixtures passed: auth/no-squad/banned gates, admin-only setter, actual default6 capacity, actual roster, dark state, message counts/two-day streak, real captain plan, pending captain-only request count, actual id-bound scheduled rivalry/no ready fiction, account-bound future match and real window, per-member check-in null, no UID/phone leakage, blocked-captain denial, auto-break inert, and no ledger mint. All fixtures reverted.

Actual authenticated-role read returned UNDEFEATED/tagUD,2 members with actual avatar/null, max6/open4, captain/member roles, growthfalse and promotion fieldsnull. Anonymous overview/admin/settings HTTP probes returned401. Canonical squads/members/messages/ledger fingerprints unchanged. Rollback was executed inside a reverted transaction. These are backend checks, not native UI end-to-end verification.

## Files and rollback

`20261113-room-growth.sql` is one-time additive migration. `20261113-room-growth-tests.sql` always rolls back. `20261113-room-growth-rollback.sql` disables both settings and removes only new overview/setter functions, retaining settings/index and all canonical history. Existing room/squad/plan/match APIs are unchanged. Reinstallation after rollback requires a deliberate repair migration, not rerunning CREATE TABLE. Native should tolerate unavailable RPCs. No activation or monitoring scheduled.
