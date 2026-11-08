# XARENA email signup contract

Status: source checkpoint of deployed edge version4. PUBLIC FEATURE OFF. This is not a public rollout or native-release approval. Existing global Auth mailer/autoconfirm settings and old phone/Google login and recovery are unchanged.

## Endpoint and server configuration

`POST /functions/v1/xarena-email-auth` on the existing project. POST JSON; no credentials in source. Configure Gmail app credential and code HMAC pepper as server secrets only. `XR_EMAIL_ENABLED` must stay false outside approved testing; `XR_EMAIL_TEST_ALLOWLIST` restricts both start and verify to one exact normalized recipient. Never clear the allowlist as part of a client build.

## Client payloads

| action | JSON fields besides action | success |
|---|---|---|
| signup_start, signup_resend | email, device_id | ok, challenge_id, expires_in600, resend_after60, message |
| signup_verify | challenge_id, code, password | ok, message; no session |
| recovery_start, recovery_resend | email, device_id | same challenge shape |
| recovery_verify | challenge_id, code, password (new) | ok, message; no session |

All failures return `{ok:false,error}` with actual HTTP400,429 or503. OFF returns503. Do not show fake sent/created success. Use the ordinary email/password login after signup or reset. Password8-72UTF8bytes, six ASCII-digit code, ten-minute expiry, five wrong guesses. Resend uses email/device again, not an old challenge handle. Code purposes are separate. Never log codes, passwords, bearer tokens, full request payloads or raw provider failures.

## Ownership and compatibility

Only new server-created accounts with a successful code check receive private UUID/email ownership binding. Existing phone, Google, shared-service or unverified accounts are never merged, linked or reset by signup. A duplicate Auth email fails closed. Public Auth can still preclaim an email under the retained global settings; this flow does not solve that cross-app limitation. Do not call a generic Auth confirmed-email flag XARENA ownership proof.

Recovery requires a fresh code and the exact existing binding/current UUID-email match. Reset invalidates refresh sessions; issued access JWTs may last until expiry. No promise of instant access-token revocation.

## Rate and storage policy

Atomic global quota reservation via an advisory transaction lock; row lock for verify reservation. Caps100starts/rolling24h,3/email/hour,10/email/day,60s resend,5/device/hour,10/unknown-network/hour. Device is a hint, not identity. Client forwarding headers never choose IP identity. Unknown clients currently share one restrictive bucket. Failed/ambiguous deliveries still consume quota. Never retry an ambiguous send/create/reset automatically.

Private challenge and ownership tables have RLS plus explicit client privilege revocation; RPCs are service-role-only. HMAC protects code and rate dimensions. Passwords are not pending database data. Cleanup currently runs lazily on starts, so48h is not a guaranteed wall-clock retention bound.

## Verified scope

A separately authorized disposable-only test completed three code deliveries: signup, resend, recovery. Wrong code, old challenge and replay failed400; signup succeeded200; two login sessions succeeded; duplicate account creation refused422; reset200; both prior refresh tokens refused400; new login200. Disposable account/profile/binding/challenges were removed, and the feature was returnedOFF. No real-user account reset or native-device compatibility claim is made.

`node --test supabase/functions/xarena-email-auth/*.test.mjs` passes19tests. The parallel handler test uses an atomic reservation stub; it is not a PostgreSQL contention benchmark. SQL rollback checks are in tools-migrations.

## Launch blockers

1. Native UI integration and real pixels/device paths, including old phone behavior.
2. Current recovery GET-email then password-PUT has a concurrent-email-change gap. Away/back changes can revive a binding. See SECURITY_REVIEW.md. Do not enable public recovery until fixed and tested.
3. Managed-ingress trusted-IP semantics remain unverified; shared10/hour bucket is safe against spoofed client headers but can deny unrelated players.
4. Clock-driven privacy cleanup and adversarial database-concurrency testing remain uncompleted.
5. Retained shared Auth preclaim behavior needs clear acceptance or a separately scoped migration.

Rollback: turn server flagOFF first. Source-only branch changes do not alter any account. Additive SQL removal is tools-migrations/xarena-email-auth-undo.sql; do not delete real Auth users to roll back this feature.
