# Stage standings

Public REST relation `tournament_standings` supports every configured tournament stage. Query with tournament_id/stage filters and `order=rank.asc`. It is a base table (not a view); REST shape is identical to the requested relation.

Columns: tournament_id text, stage text, squad_name text, points integer, rank integer, squad_id text nullable, tag text nullable, logo_url text nullable, matches_played integer nullable, updated_at timestamptz.

Public reads; writes require is_admin(). Stage composite FK, nonnegative points, positive unique stage rank. Unknown optional values remain NULL. updated_at defaults on insert; writers must explicitly set it on later updates.

Seed is the host-supplied 16 November 2026 Season 2 qualifier aggregate, corrected names at ranks 5 and 7. Not an independent audit. No fabricated per-match scores, kills, squad IDs, qualification flags or match counts. Semi Final/Final remain empty.

Tests run in a rolled-back transaction: anonymous read, anonymous update has no effects, authenticated nonadmin insert/update denied. Run with a privileged SQL session.

Rollback: DROP TABLE public.tournament_standings; (also removes seeded totals). Does not affect matches, stages, registrations, or news. News body publication was a separate operation from an owner-approved caption.
