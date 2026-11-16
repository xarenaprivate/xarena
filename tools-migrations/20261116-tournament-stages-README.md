# Tournament stages - 16 November 2026

Owner approved Qualifier lobby / Semi Final / Final, with stage-to-matches navigation, as the default for new tournaments. Season 2's existing six noon matches are all Qualifier. No future-stage dates or matches were created. Closed historical tournaments are not backfilled.

## Public app contract
- `matches_public`: existing columns unchanged, appended nullable `stage` text. Values `qualifier`, `semi_final`, `final`; NULL means legacy flat rendering.
- `tournament_stages`: `tournament_id` text, `stage` text, `label` text, `ord` integer. No status/date columns. Derive stage activity from actual match statuses; an empty stage is Coming soon, not started.
- Rows for Season 2: qualifier / Qualifier lobby / 1; semi_final / Semi Final / 2; final / Final / 3.
- Internal `matches.stage_code` is the structured reference. Existing `matches.stage` map notes are preserved. New match inserts default to qualifier only where stage definitions exist.

New tournament INSERT creates the three stage rows in the same transaction. Public SELECT allowed; writes require existing admin authorization. Composite foreign key prevents cross-tournament or unknown stage assignment. No credentials are added to the public view.

## Web
Existing room/check-in/result handlers are retained while match cards move into keyboard-accessible native details controls. Qualifier opens its six cards. Semi Final and Final show undated placeholders. Staged events use this hierarchy instead of Nova's flat next-match rendering. Unstaged events retain legacy rendering. Registration code is unchanged. Admin Add Match selects a stage; the explicit match wizard makes six qualifier matches, leaving later stages unscheduled.

Tests: rollback-only DB fixture checks new-event defaults, qualifier default, invalid-stage rejection, anonymous public read and schedule retention. DOM tests cover collapsed stage hierarchy, six cards, preserved handlers, legacy flat rendering, empty new event and mixed listing. JavaScript syntax checked.

## Rollback
Revert web commit first. Preserve/backup any newly entered stage assignments before database rollback. Remove triggers `xr_match_default_stage` and `xr_tournament_default_stages`; recreate matches_public with its original seven columns (requires dropping/recreating view and restoring grants, as removing a view column cannot use CREATE OR REPLACE); drop matches_stage_fk, matches.stage_code, tournament_stages, then the two trigger functions. No schedule, registration, news or Live rollback is needed because this migration changes none of those values.
