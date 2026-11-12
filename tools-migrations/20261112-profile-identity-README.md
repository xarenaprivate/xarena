# Own-profile IGN and Free Fire UID editing

Deployed server-side November 12, 2026. **Open now**, following the owner's November 12 evening override of the original 4 AM release. The forward migration `20261112-profile-identity-open-now.sql` advances the private gate without changing any cooldown. Native UI release is separate.

## Auth and calls

Use the signed-in player's Supabase access JWT, with the normal authenticated API client and refresh behavior. Do not use a public/anonymous RPC client. No caller-supplied user ID is accepted.

### Read edit availability

`POST /rest/v1/rpc/xr_profile_identity_status`

Body: `{}`

Success fields:

- `ok: true`
- `available: boolean`, whether the server release time has passed
- `opens_at: ISO timestamp`
- `changed_at: ISO timestamp | null`
- `next_allowed_at: ISO timestamp`
- `can_change: boolean`
- `cooldown_days: 14`

There is no listing or other-account lookup. This call returns no IGN, UID, phone, device or account identifier. Read the player's current IGN/UID through the existing own-profile read, never a public player card.

### Save identity

`POST /rest/v1/rpc/xr_profile_identity_update`

Body: `{ "p_ign": "the desired IGN", "p_ff_uid": "the desired UID" }`

Both fields required. Pass UID as a string, never an integer. Do not log request bodies or identity values. Trimmed values are saved. IGN supports Unicode, 1-40 characters and at most 160 UTF-8 bytes. Control characters, angle brackets, zero-width/direction-control characters and BOM are rejected. UID uses existing signup format: 5-15 ASCII digits. Format validation does not claim the UID was checked with Garena or belongs to the player.

Success:

```json
{
  "ok": true,
  "changed": true,
  "changed_at": "ISO timestamp",
  "next_allowed_at": "ISO timestamp",
  "verification_reset": false,
  "cooldown_days": 14
}
```

An identical normalized save returns `changed:false` and does not advance the timer, including during cooldown. Re-read the own-profile after a changed save. Refresh the verification badge if `verification_reset:true`.

Cooldown failure:

```json
{
  "ok": false,
  "error": "cooldown",
  "message": "IGN and UID can be changed once every 14 days.",
  "next_allowed_at": "ISO timestamp",
  "cooldown_days": 14
}
```

Show the next allowed timestamp in the player's local timezone. Do not depend on HTTP status alone: check `ok` on every RPC response.

Other errors: `unauthorized`, `account_restricted`, `not_open` (includes `opens_at`), `invalid_ign`, `invalid_uid`, `invalid_identity`, `update_failed`. Every error includes a human-readable `message`. No submitted identity values are echoed in successes or errors.

## Semantics and bypass protection

- One shared timer covers changing either IGN or UID, or both in a single save. The interval is exactly 336 hours from the last successful change, not a calendar-day counter.
- The own profile row is locked before checking and saving. Concurrent edits to the same account serialize; only one different identity can win a cooldown window.
- A profile-table trigger also covers direct own-profile updates and the legacy `complete_profile` RPC. Authenticated admins editing their own identity obey the same timer.
- First-time legacy signup profile setup (both old identity fields empty) remains available and does not consume the edit timer. Once identity exists, clearing fields is rejected and cannot reset the timer. Calling the new edit endpoint for an initially empty account does consume its timer.
- UID changes clear `profiles.verified` so old gameplay-identity verification does not transfer to a new UID. IGN-only changes preserve that badge. Phone verification is unchanged.
- Username, phone, phone code, device, moderation status, earned matches/wins, AP/XP and other profile fields are not edited by the new endpoint. Historical registrations, proofs, leaderboard entries and result bindings are not rewritten.
- Existing trusted service/database maintenance and other-account admin maintenance are unchanged. Normal players cannot access other profile rows because the endpoint has no target ID and direct writes remain RLS-scoped.
- The private cooldown table stores only the owner reference and last-change time. No new UID history, request-body log, phone log or audit payload is introduced. HTTP infrastructure logging is outside this SQL change; clients must not log sensitive bodies.
- No fee, points debit, external verification call or paid service.

## Deployment and checks

The migration is transactional and requests PostgREST schema reload. Pre-deploy and post-deploy rollback tests cover release gating, input validation, both-field update, exact interval, boundary behavior, no-op retry, UID reverification, IGN-only verification preservation, legacy/direct bypasses, banned accounts, other-account RLS and private-table privileges. All existing profile rows were unchanged after deployment and rollback tests. No test identity was committed.

The fixture suite intentionally uses transactions and rolls everything back. It temporarily moves the release gate inside the test transaction only. It temporarily restores a future release time inside that same rollback transaction to test pre-release assertions. This is not a multi-session load/stress test or native UI end-to-end test.

Rollback: run `20261112-profile-identity-rollback.sql`. It removes new endpoints/trigger and restores the original `complete_profile` and `profiles_guard` definitions, including the old verified UID lock. Private timing tables are intentionally retained so restoring the feature later cannot erase cooldown history. Rollback does not undo player identity changes or restore verification badges. Do not run the initial CREATE migration again over existing tables; use a reviewed forward migration to re-enable.
