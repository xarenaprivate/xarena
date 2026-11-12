# Seven-screen backend extension

Deployed 2026-11-12. Additive API for slot booking, roster capture, MVP voting and host-entered match feed. No sample rows, automatic verification, results or announcements are created.

## API

All RPCs return JSON with `ok` and, on expected failure, `error`. Mutations require authenticated JWTs. Public lists expose names and counts, never roster UIDs, phones or user IDs.

- `xr_slot_list(p_tournament text)` returns `capacity`, `registered`, `hold_seconds:300`, `my_slot`, `slots:[{slot_no,state,mine,squad}]`. State is available/held/booked. Supported slot numbers stop at min(tournament.slots,48). Legacy entries without a chosen slot still consume registration capacity.
- `xr_slot_hold(p_tournament text,p_slot integer)` returns slot_no/expires_at/confirmed. Requires phone-verified, non-banned account and open upcoming/live tournament. Provisional holds last five minutes. Same user's confirmed slot cannot be moved.
- `xr_slot_release(p_tournament text)` releases only own provisional hold.
- `xr_slot_confirm(p_tournament text,p_slot integer,p_registration jsonb,p_players jsonb)` returns id/slot_no/status/verified/status_token. New registration object: `{squad,captain,ign,uid,phone,proof_path}`. Proof is optional server-side; absent proof retains the original hold/review semantics. Existing own registration: pass `{}` and its identity is retained. Players: 1-4 objects `{ign,uid}`; captain UID must be included, UIDs unique, IGN 2-40 characters, UID 5-15 digits. Confirm does not grant verified status. Confirmed roster is immutable through player API. A retry with the same trimmed roster is idempotent. Changed roster needs owner review. AP-paid entry must go through the existing payment flow first, then attach a slot to its own registration. No spending occurs in these RPCs.
- `xr_slot_my(p_tournament text)` returns only the caller's registration, slot_no, identity fields, status/verified/hold and players with player_no/ign/uid. No other user's private registration can be retrieved here.
- `xr_mvp_tally(p_match uuid)` returns open/my_vote/candidates:[{entry_id,player_name,squad,kills,votes}]. Voting opens only when match status is done. Candidates are actual lb_entries, not a separate invented nominee list.
- `xr_mvp_vote(p_match uuid,p_entry uuid)` returns entry_id. One immutable vote per authenticated non-banned user per match. Same-choice retry succeeds; second different choice fails. Candidate must belong to that match.
- `xr_kill_feed_post(p_match uuid,p_killer text,p_victim text,p_weapon text DEFAULT NULL,p_elapsed_seconds integer DEFAULT NULL)` returns id. Owner/helper and non-banned account required. Match must be scheduled/live. Names 2-40 chars, weapon at most 40, elapsed_seconds 0-86400 or NULL. Numeric identifier/phone-like text, links and @ are refused. These are manually entered real events, not game telemetry. Posting does not award kills, points or prizes.
- `xr_kill_feed_list(p_match uuid,p_after bigint DEFAULT 0,p_limit integer DEFAULT 100)` returns events:[{id,killer,victim,weapon,elapsed_seconds,created_at}], ascending IDs, up to 200. Hidden events omitted.
- `xr_kill_feed_hide(p_event bigint,p_hidden boolean DEFAULT true)` owner/helper correction control. Does not delete the event.
- `xr_match_chat_post(p_match uuid,p_body text)` wraps existing thread_post, using the same history, feature gate and moderation; additionally serializes new-wrapper rate checks and refuses email addresses.
- `xr_match_chat_list(p_match uuid,p_limit integer DEFAULT 50)` wraps existing thread_list; returns newest-first posts:[{id,name,body,created_at,mine}]. Existing `live_thread` OFF is preserved. Show its disabled error, never fake comments.

## Compatibility and concurrency

Existing register_squad argument list, returns, phone-verification requirements, proof handling, duplicate indexes and legacy client behavior are preserved. The only modification to that function adds an open/status check under the tournament lock. Closed/done events cannot be registered through it. Existing rows are not backfilled.

Slot mutations lock the tournament row. All normal registration capacity checks serialize on the same row; partial unique registration slot index and claim primary key prevent two active confirmations for the same slot. New roster UIDs have tournament-scoped uniqueness. Old register_squad does not collect a roster and cannot check every non-captain legacy teammate, so organizer roster reconciliation is still needed. Phone uniqueness in the existing registration flow remains exact-string, not a new phone-ownership guarantee.

A provisional hold is not a new entitlement against a simultaneous legacy registration: legacy entries may consume remaining capacity and final confirmation rechecks. All available numbered slots does not mean that many registration spaces remain. Rejected/cancelled/waitlisted entries require owner review before attaching a slot. Existing cancelled roster UID restrictions are not relaxed by this API.

New table data is private behind RLS plus explicit table privilege revocation. Only functions are exposed. Public lists do not reveal voter identities, roster UIDs, phone numbers, or event creator IDs.

## Verification

`20261112-seven-screens-tests.sql` runs inside a transaction and ends in ROLLBACK. Its fixtures, temporary helper grant, claims, votes, feed and chat messages never persist. It uses existing authenticated identities for request context without creating accounts or saving credentials. Test environment needs existing phone-verified non-admin profiles and an admin. Assertions cover legacy compatibility, slot range/holds/expiry/retry/roster/capacity, AP-entry refusal, closed-event guard, immutable one-vote rules, feed role checks, privacy, comments gate/moderation, and actual anon/authenticated database-role denial.

Live HTTP checks additionally verify public RPC discovery and private-table denial. Post-deployment comparison checks existing event, match, registration, result and setting values unchanged (apart from new NULL slot_no key).

## Rollback

Do not drop tables or delete real registrations. Stop calling new UI APIs, revoke execution of new functions, and preserve captured data. No old client requires the new columns/functions. The open-event guard should remain in place unless the owner explicitly wants it removed. The adjacent register_squad-before file records the previous function definition for reviewed recovery only; restoring it would reintroduce the closed-event bypass. No automatic rollback is run on production data.
