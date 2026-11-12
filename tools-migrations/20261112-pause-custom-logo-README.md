# v82 pause request and custom squad logo backend

Backend only, additive. No game-control API, seed request, uploaded asset or AP purchase. Authenticated Supabase JWT required. Admin list remains for the web admin, not an in-app admin console. Existing result/ban/warn/broadcast functions are untouched.

## Emergency pause request

`xr_pause_request(p_match uuid) RETURNS jsonb`

One request per account per match, enforced by UNIQUE(match_id,user_id), with idempotent retries. A request expires two minutes after creation, but it is not removed or reusable after expiry. This is a request for the host to see, not a claim that Free Fire paused. Every success explicitly returns game_paused=false. No acknowledgement/acceptance/start-pause/resume action exists here.

Eligibility: unrestricted logged-in account, match currently live, and an active non-held registration for that match's tournament either belonging to that account or a current member of its canonical squad. Registration statuses rejected/cancelled/declined/waitlist are excluded. This matches existing squad-based match room eligibility, with held registrations excluded. Rosters without an account link cannot be guessed by UID or IGN. No name-based identity binding.

Success:
`{ok:true,already_requested,request_id,match_id,created_at,expires_at,status,pause_seconds:120,game_paused:false}`

status is pending before expiry, expired after expiry, or match_closed once the match is no longer live. A retry after match closure returns the existing request without creating a second one. It does not mean a closed game accepted the request.

Errors `{ok:false,error:<code>}`: unauthorized, account_restricted, match_not_found, match_not_live, not_registered. Null/unknown match returns match_not_found. Existing requests are retrievable idempotently via the same RPC; there is no arbitrary player-history read.

`xr_pause_request_list(p_match uuid,p_limit integer DEFAULT 50) RETURNS jsonb`

Unrestricted actual admins only. Helpers, feed hosts, squad captains and callers claiming to be organizers are not admins. No new permission grants. Returns `{ok:true,match_id,match_status,game_paused:false,requests:[...]}`. Each request includes request_id,user_id,player_name,username,created_at,expires_at,status,pause_seconds. Never phone/Free Fire UID/device or room credentials. Newest first; limit clamped 1..100. Expiry/status derived at read time with no scheduler. Existing requests remain for review after closure.

Errors: unauthorized, account_restricted, forbidden, match_not_found. Nonadmins see forbidden before match lookup, avoiding a privileged existence probe. No admin UI change was made; the web owner can wire this endpoint separately.

Private table `xr_match_pause_requests` has RLS, no client table grants. Match row share lock prevents a committed match-close race; profile row lock and unique constraint prevent double requests. No automatic notifications, broadcasts, DM or email.

## Custom logo

`xr_squad_custom_logo_set(p_squad uuid,p_logo_url text) RETURNS jsonb`

Uses the existing `set_squad_logo(p_squad uuid,p_logo text) RETURNS void`, which was already present and captain-gated. New wrapper supplies a consistent JSON result, rejects restricted accounts and validates HTTPS Cloudinary image/upload resource URLs. URL trimmed, max 2048 chars, exact res.cloudinary.com host, nonempty cloud/image-upload path, no query/fragment, no traversal or control encodings, no raw/video resource. It does not fetch or inspect pixels or prove asset ownership, so native must pass the actual secure_url returned by its existing signed image-upload flow, not an arbitrary typed URL. No client Cloudinary secret is needed or exposed.

Success `{ok:true,squad_id,logo_url}`. Errors `{ok:false,error:<code>}`: unauthorized, account_restricted, invalid_logo_url, not_found_or_not_captain.

Only the current captain can update. No admin bypass. Existing squad/roster preserved. The URL field changes only, no AP debit, catalog unlock, purchase or equipment ownership grant. Existing buy/equip catalog flow and AP rules are unchanged; wrapper is not a substitute for buying a catalog logo. Old set_squad_logo remains unchanged for compatibility. The upload occurs separately client-side; this function only saves the returned image URL. It does not delete the previous asset or the new asset on a failed save.

## Validation and rollback

Migration compiles and regression fixtures run inside BEGIN/ROLLBACK. Tests cover anonymous/restricted account refusal, missing/scheduled match, nonparticipant refusal, registered request, exact two-minute expiry, one-request retry and expired retry, admin-only list, feed-host denial, closed-match status, malformed/null/lookalike/video/traversal/query URLs, captain save and noncaptain refusal, actual anon/authenticated grants and no direct request-table reads. All test fixtures and profile/registration/squad/logo changes are rolled back. No real logo upload or production request is submitted by tests. Multi-session load stress and native end-to-end device checks are not performed here.

Single-apply transactional migration. Rollback should first stop new pause/logo callers, preserve/export real request rows if any, then remove the three new functions and the request table only with explicit approval to delete request history. Existing set_squad_logo/equip APIs need no rollback because unchanged. Do not delete real Cloudinary assets as part of a database rollback.

Deployment verified: predeploy and postdeploy rollback regression suites passed. Real counts unchanged (1 squad, 1 member, 10 matches, 2 registrations, 0 catalog owners, 0 feed hosts), original squad logo unchanged and pause table empty. No AP ledger or catalog mutation.
