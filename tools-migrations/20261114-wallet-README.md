# Wallet repair, 14 November 2026

Deployed approximately 11:47 IST with owner approval for wallet concurrency and earned-only provenance repair. No real-money system, withdrawals or paid tournament entry were enabled.

## Scope

- Every points_ledger insert now acquires the existing account advisory lock and a wallet-account row lock before updating an atomic bucket balance. This covers existing and future debit paths, even if their caller read a stale balance.
- All total balances and original ledger rows were preserved. Historical admin gift AP remains granted, not earned. Historical store ownership remains intact. Historical debit replay consumes granted, then unverified, then earned AP without changing the original total.
- New credits are certified earned only when the whitelisted earning source has matching server-side evidence. Admin gift credits have a separate granted bucket. Unknown sources and unlinked refunds are unverified, never silently certified earned.
- New earned-only integrations set wallet_scope=earned_only on their server-side ledger debit. That rejects spending granted or unverified AP. This is not an exposed client debit API. Existing cosmetic stores retain their original total-AP behavior.
- Linked refunds use wallet_refund_ledger to restore the exact original bucket split once. The redemption-review path now uses this explicit link.
- points_ledger is append-only. Corrections must be new entries. Insufficient-balance corrections fail closed rather than creating debt. Normal auth account-deletion cascades are exempt from the append-only deletion check.
- No cash value. No gifts, drops, predictions, challenge or clan-fund activation as a side effect of this migration.

## APIs

Authenticated xr_wallet_balance() returns own total, earned_spendable, granted, unverified and cash_value=false. It accepts no user identifier. Private tables are not client-readable/writable. The legacy _ap_bal(user) helper is no longer directly executable by anon/authenticated; server functions retain access. Existing points_balance shape remains unchanged for compatibility, so its legacy earned field still means gross credits, not the new certified earned balance. Consumers of earned-only funds MUST use xr_wallet_balance().earned_spendable.

## Caller fixes

- redeem_reward and buy_squad_logo lock before ownership/pending/balance reads.
- Negative catalog costs rejected in avatar/logo/redemption paths.
- Redemption debit includes its redemption ID; review locks that row, prevents duplicate review/refund and carries the exact debit reference.
- Proof review locks its source row before awarding.
- Daily spin locks the stats row; mission claims use the account lock before checking/claiming.

## Validation

- Transactional trial and post-deployment tests passed: full reconciliation, overspend rejection without partial rows, earned-only rejection, unknown-credit classification, linked/duplicate refunds, immutability and privileges.
- Isolated scratch-schema concurrency test ran the same insert-guard implementation: two concurrent 7 AP debits against a 10 AP balance produced one commit, one rejection and final balance 3. Scratch schema removed.
- Rollback tested within a transaction and then rolled back. Production rollback was not performed.
- Before/after fingerprints of all 27 original ledger rows, original squad-logo ownership and registrations match exactly.
- Seven existing accounts reconcile. At deployment, remaining earned=213 AP, granted=0, unverified=0. The original 1500 AP admin gift had already been spent on a 1500 AP logo; no clawback or reclassification of ownership.

## Limits

No real-device UI test. This repair does not certify unrelated future earning mechanics or activate a wallet-bearing feature. Future multi-account transfer code needs deterministic account-lock ordering, idempotent transaction references and source-preserving refund tests. Unknown credit kinds remain unverified until their source is audited. Historical malformed/negative balances abort migration rather than being silently written off. All known current debit writers are guarded at the ledger boundary.

## Files

- up.sql: migration plus caller changes
- down.sql: controlled rollback. It removes added provenance enforcement and restores caller definitions without modifying original ledger rows. Do not apply automatically.
- tests.sql: run inside BEGIN/ROLLBACK, never as a standalone committed script.
- concurrency.py: isolated scratch-schema concurrency validation (set SUPABASE_ACCESS_TOKEN in your private environment).
- caller-tests.sql: rollback-only catalog fixtures verify purchase/redemption/review idempotency.

Credentials and user-identifying ledger dumps are not included in publication.
