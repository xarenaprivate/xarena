# Email auth security review

2026-11-08. Source checkpoint, featureOFF. No proposed shared-auth hooks below are deployed or approved for automatic execution.

## Findings

- Handler verifies a bound current address, then Admin API GET checks UUID/email, then a separate PUT changes password. An email change between GET and PUT is a time-of-check/time-of-use gap.
- Comparing current address does not invalidate historical proof: changing away and back can revive the old binding.
- Quota reservation is serialized by a PostgreSQL advisory transaction lock. Challenge verify uses SELECT FOR UPDATE then atomic reserved state. This prevents duplicate same-challenge create in normal execution, but separate reserved challenges and admin network timeouts still require reconciliation, never blind retry.
- Unknown-source IP uses a single restricted bucket. The client cannot select an IP via forwarding headers. Official examples reading X-Forwarded-For do not establish an anti-spoofing guarantee for this project's managed ingress. A CLI gateway change is not proof about managed production ingress.
- Private challenge cleanup is lazy. Under no traffic it can retain data beyond48h. No clock-driven cleanup job has been installed.

## Recommended narrow recovery design, pending approval and tests

Preserve email changes; do not impose immutable addresses. Use a server-only per-recovery operation marker, in the same AdminUpdateUser request as the new password. Add an auth.users metadata-update trigger which, when that marker changes, checks the reserved operation, exact UUID, current email and existing private binding. Fail closed inside the Auth transaction. Also invalidate the private binding on any actual email change for that UUID. An away/back change must not restore proof.

Current upstream Auth code places UpdatePassword and UpdateAppMetaData within the same database transaction. A trigger error should therefore roll back both password update and refresh-session revocation. Pin/verify the managed Auth version and test rollback before relying on this. Do not assume password and metadata share one SQL UPDATE: the upstream implementation uses separate statements within the transaction. The guard belongs on metadata change, after password storage, so an error must roll back the transaction.

Affected subset: users newly created and bound by this XARENA lane, plus a fresh reserved recovery operation. No-op for existing phone/Google/shared-service users with no such binding or operation. A new-lane user's email change remains allowed; it revokes email-code recovery until fresh ownership verification. A re-verification product flow is separate work, not silently included. Shared-table trigger permission, database-role access to private tables, ordering with current triggers, possible deadlocks and Auth-version behavior all need explicit validation. A trigger bug can break auth transactions, so keepOFF throughout.

Proposed rollback: featureOFF; remove only the two uniquely named XARENA triggers and their functions; preserve accounts and passwords; remove temporary recovery operation metadata with a scoped reviewed migration only if needed. Removing guards reopens the race, so recovery must not be re-enabled after rollback.

## Supporting sources

- Admin API contract, direct updates without confirmation: https://supabase.com/docs/reference/javascript/auth-admin-updateuserbyid
- Upstream Auth admin transaction: https://github.com/supabase/auth/blob/master/internal/api/admin.go
- Password update/session logout model: https://github.com/supabase/auth/blob/master/internal/models/user.go
- PostgreSQL trigger transaction/rollback semantics: https://www.postgresql.org/docs/current/trigger-definition.html
- Trigger mechanics: https://supabase.com/docs/guides/database/postgres/triggers
- Forwarded-header trust requirements: https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/X-Forwarded-For
- Local CLI gateway change, not managed-ingress proof: https://github.com/supabase/cli/pull/7345
- Official Edge example, not an anti-spoofing contract: https://supabase.com/docs/guides/functions/examples/cloudflare-turnstile
