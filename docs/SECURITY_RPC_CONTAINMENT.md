# RPC access containment, 8 November 2026

Live database access changes:

- `security_scan()` and `award_xp(uuid,text,integer,jsonb)` are server helpers, not client APIs. EXECUTE revoked from PUBLIC, anon, authenticated; retained for service_role and owner postgres. Authorized nested SECURITY DEFINER rewards continue to call award_xp as the function owner.
- `phone_available(text)` is a legacy compatibility validator, not an account lookup. For normalized valid input it returns `{ok:true,available:true,phone:normalized}` irrespective of registration state. Invalid format retains the validation error. Native release72 reads ok+available; removing this RPC breaks signup. New callers should not interpret available as account existence.
- `signup_precheck` is unchanged, including its risk/rate/device rules and grants. Auth signup and error behavior are not claimed to be enumeration-free.
- Hourly cron job2 remains active at minute07, runs as postgres, and can execute security_scan. No real scan invoked for testing.

Verification:

- Live effective EXECUTE checks: scanner/award anon=false, authenticated=false, service_role=true, postgres=true.
- Anonymous REST denial verified for scanner and award helper.
- Rolled-back authenticated daily_checkin fixture traversed the real nested award path successfully. No persistent balance/account changes. Rollback fixtures may consume nontransactional sequence numbers.
- No global Auth/SMTP, account deletion, email feature, eligibility or native release changes.

Privacy boundaries:

- Signup lacks a collection/privacy notice and age/guardian workflow. Public policy and eligibility decisions require owner input; this migration makes no compliance claim.
- Web registration label explicitly asks for "Captain in-game name" and sends it as registration `ign`. Public roster discloses submitted IGN and squad, not phone/UID/email. This does not make IGN nonpersonal or prove consent. Historical data has no separately recorded public-display consent. Live stream tournament remained unassigned during assessment.
- Do not equate base-table RLS with a full audit. Sampled private tables deny anonymous row reads; public views intentionally expose limited profile/player fields. Private media buckets and their read policies were inspected, but this is not exhaustive penetration testing.

The undo migration restores unsafe historical grants and phone lookup behavior. Use only for controlled emergency recovery, not routine rollback.
