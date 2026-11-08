# Future weekly news event grounding

Deployed 8 November 2026. User approved the narrow future-news correction, with final owner publication approval retained.

## Scope

Only new weekly news drafts change. No historical news/approval rows, tournament rows, registrations or schedules are migrated. The existing weekly job remains Monday 04:30 UTC (10:00 Asia/Kolkata).

## Source rules

`xr_weekly_news_events()` returns at most three current live/upcoming events, ordered by next structured time. An event without matches needs a future `tournaments.start_at`. An event with matches needs a future noncancelled/nonpostponed/nonfinished match; all-cancelled events are excluded. Free-form `date_text` is not treated as a confirmed schedule. Undated or ambiguous events are excluded rather than invented or re-dated. Existing stored rows remain unchanged.

The worker keeps the real registrations/leaderboard recap generation. Event schedule text is separately rendered from the structured snapshot. A scheduling-language check replaces an event-claiming model intro with a plain registration-count recap. This check is a guard against common scheduling language, not a general proof that model prose can never make an unsupported claim. The owner must still review the exact draft.

## Approval and publication

New weekly drafts use `xr_weekly_news_request_review` to create a normal pending `publish_news` approval carrying `weekly_guard:v1`, the event snapshot and an MD5 change-detection hash of the stored title/body. MD5 is used only for change detection, not security authentication or credential hashing.

The BEFORE approval trigger rechecks current event facts and title/body. If facts changed, the approval remains pending, its decision time is cleared, and `review_required` explains why. It never substitutes new words under the old approval. Admin's current details JSON shows this review reason. A refreshed draft needs a fresh owner review.

A separate BEFORE news publication trigger checks approval, source snapshot and exact words again at the actual unpublished-to-published update. This also rejects alternate direct-publication paths for protected drafts. Existing unmarked articles/approvals do not opt in retroactively.

Helpers are server-only (owner/service_role), not anonymous/authenticated RPCs. Existing approval/news admin policies are unchanged.

## Verification

- TypeScript typecheck passed.
- Four production weekly-branch mock cases passed: events/no events, ordinary recap retained, model scheduling text fallback, unpublished insert and matching review snapshot.
- Rolled-back database fixtures passed before and after deployment: future/past/undated/all-cancelled eligibility; normal owner approval publication; stale approval stays pending with no changed words/publication; direct stale publication rejected; historical-shaped unmarked approval behavior retained.
- No live worker invocation, real news creation, publication or review decision was performed for testing.
- Agent-worker version29 ACTIVE. Deployed readback is ESZIP compiled JavaScript; inspected actual weekly branch and verified new snapshot/review RPCs and removed old event prompt.
- Next normal scheduled draft must be checked separately. Do not publish it as a test.

## Rollback

`undo.sql` removes new helper functions and triggers. Redeploy `worker.before.ts` at the same time. Rollback after protected drafts exist requires review of how those drafts remain blocked/reviewable; do not drop protection casually. There were zero real protected drafts immediately after deployment.

No legal/privacy/age/signup, global Auth/SMTP or parked email changes are part of this fix.
