# AP spending and earned rewards - 14 November 2026

## Current release state
Backend deployed dark. All four features remain disabled and release_authorized=false. No campaign has been opened, no AP transfer/reward issued, no real user fixture writes retained. Activation is held until native v0.17.53 is published and the owner-side coordinator gives the agreed all-four-together green signal. Do not enable one feature independently.

The approved receiver is account speed for the existing stream BI6Ns45PmXQ / xr-brlord-nov12. Stream/tournament were NOT switched to Season2. New stream IDs need an explicit matching host binding, not a guessed receiver.

## Approved economy
- Gift transfers, not burns: Rose10AP, Fire50AP, Crown500AP. Sender spends certified earned AP. Bound host receives granted AP. No gifted-to-earned laundering. Crown highlight lasts10seconds; software cannot guarantee a spoken streamer shoutout.
- Predictions cost0AP. Exactly one immutable choice per user/pool. Correct200AP, incorrect10AP. Only admin can confirm a finished match's actual winning registration and reviewed result evidence. No odds, stakes, community result, automatic payout or cash value.
- Live Drop: first10unique eligible live commenters after an admin opens a drop receive50earnedAPeach. Uses the real existing moderated/rate-limited live-comment RPC and an atomic after-insert trigger. No claim-speed race and no claim of verified watch presence. Hidden/banned/wrong tournament/out-of-window comments do not qualify. Comment moderation after an award does not silently rewrite ledger history.
- Clan fund: current members contribute certified earnedAP. Contributions remain in the clan after leaving. Only current captain can reward current eligible members. Rewards are grantedAP. No personal cash withdrawal, no leave refund, no arbitrary external recipient.

## Security and concurrency
Private RLS tables with revoked anon/authenticated table access. Every action validates the authenticated unrestricted actor. Security-definer functions have fixed search paths. Private helpers/trigger functions are not RPC-accessible. Public read RPCs expose only intended live presentation data, not phone/UID/device/private wallet or arbitrary user balances.

Gift request UUID is unique per sender. Request parameters must match on retry. Receiver is bound server-side; client cannot choose recipient. Sender/receiver AP locks are sorted, under the common wallet ledger insert guard. Debit and receiver credit are one transaction.

Fund writers lock clan before AP accounts. Contribution/reward UUIDs are unique per actor. Captain and recipient membership rows are checked under locks. Fund balance is sum(contributions)-sum(distributions), under the same clan lock. Duplicate, competing or oversized spending cannot mint a second reward.

Prediction settlement locks the pool, then accounts in sorted order. Choice writers take pool SHARE. Whole settlement commits together or not at all; retry of a settled pool does not pay again. Rewards are earned only when linked to private server-created award evidence. No arbitrary ledger credit is certified as earned.

Drop claims are unique(drop,user), serialized under the drop lock. Award row and ledger credit commit in the same comment transaction. The tenth-winner cap is checked under that lock.

Existing AP balances and history were preserved. The older points_balance.earned field is gross credits for compatibility, NOT certified earned-spendable. New UI must use xr_wallet_balance().earned_spendable.

## API
User and public presentation:
- xr_spend_status()
- xr_spend_live_get()
- xr_wallet_balance() (authenticated own-only, delivered by wallet core)
- xr_live_gift_send(p_video text,p_gift text,p_request uuid)
- xr_prediction_choose(p_pool uuid,p_registration text)
- xr_prediction_get(p_pool uuid)
- xr_live_drop_get(p_drop uuid)
- xr_clan_fund_get(p_squad uuid)
- xr_clan_fund_contribute(p_squad uuid,p_amount integer,p_request uuid)
- xr_clan_fund_reward(p_squad uuid,p_recipient uuid,p_amount integer,p_request uuid)

Admin, web-only:
- xr_spend_admin_overview()
- xr_prediction_open_admin(p_match uuid,p_video text,p_closes timestamptz)
- xr_prediction_settle_admin(p_pool uuid,p_winner text,p_result_evidence text)
- xr_live_drop_open_admin(p_video text,p_closes timestamptz)
- xr_spend_campaign_close_admin(p_kind text,p_id uuid)
- xr_spend_disable_admin(p_feature text default 'all')

No client-facing enable function exists. Web admin can stop features and manage campaigns, never silently enable the release. The current BR Lord stream has no bound match records, so predictions cannot be opened for it until a real eligible match is configured. Enabling flags does not create a match, open a campaign, confirm results or issue rewards.

Native and web cards must use these new spend flags, independently of older Live/Clan growth_enabled flags. Those older flags remain off. Refresh30seconds while visible, pause off-screen. Use a stable UUID for each write attempt and retries. Always confirm receiver/member and AP amount before submission. Contribution confirmation states that AP stays after leaving.

## Verification
- Dark defaults, private table/helper permissions, non-admin campaign access rejection.
- Gift catalog, host binding, idempotency mismatch, earned-only debit, granted receipt.
- Free prediction entry/locked choice, no early settlement, done-match admin result gate, one payout per user, earned provenance.
- First10unique eligible commenters, duplicates/cap, hidden/wrong tournament,50APearned provenance.
- Fund member-only reads/contribution; captain-only current-member reward; duplicate reward; fund overspend; gifted receipt; no leave refund.
- Ledger and wallet reconciliation after every scenario. All production fixture tests enclosed in BEGIN/ROLLBACK.
- Isolated cloned implementation concurrency:2gifts against10earnedAP ->1transfer;12parallel commenters ->10awards;2x75APfund rewards against100AP ->1reward,25APremaining. Scratch schema removed.
- Migration rollback trial passed before feature activity.

## Activation and recovery
Activation is a separate owner-approved step after native publication. Set the confirmed economic modes and all four feature flags in one transaction only after the coordinator's green signal, then smoke-check actual RPC states and UI. Do not open a prediction/drop or make a test transfer as a side effect of enabling.

Emergency stop: authenticated admin calls xr_spend_disable_admin('all') or use the web admin Stop all four features button. This sets release_authorized and all flags false atomically. Existing requests that already committed remain in the audit; no ledger deletion. Inspect for any in-flight committed transaction before explaining balances. Keep tables and ledger after any activity.

20261114-spend-down.sql refuses destructive rollback after any feature activity. Before activity only, it restores the prior wallet source classifier and removes staged feature objects. After activity, disable flags and prepare source-preserving correction entries under separate authorization. Never delete or edit AP history. Clan dissolution/financial incident cleanup requires a reviewed balance plan, not blind table deletion.

## Files
- 20261114-spend-up.sql: final dark migration.
- 20261114-spend-down.sql: guarded pre-activity rollback.
- 20261114-spend-tests.sql: rollback-only functional tests with transaction-only fixture reclassification.
- 20261114-spend-concurrency.py: isolated parallel tests; needs SUPABASE_ACCESS_TOKEN, deployed wallet core file beside it and migration file beside it.
- spend-features.js / spend-admin.js / spend-features.css: web player and admin rendering, server flags respected.
