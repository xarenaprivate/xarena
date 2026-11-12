# Home active-player row

Authenticated `POST /rest/v1/rpc/xr_sf_active_players` with `{}` or `{"p_limit":30}`. Limit defaults to 30 and clamps to 1..50. Use normal signed-in JWT/refresh behavior, not a public RPC client.

Response: `{"ok":true,"players":[{"id":"uuid","username":"name","ign":"IGN or null","avatar_url":"URL or null","presence":"online or in_match"}]}`.

Render the home row horizontally. Use IGN with username fallback, avatar or the existing initials fallback, and a green indicator only for entries returned by this source. Both allowed states indicate current player-reported presence, not independent proof of game participation. No fabricated online players, example faces or guessed last-seen state. Empty array is a valid empty state; hide the row or explain that no visible players are active.

Only explicit player-reported presence whose two-minute `expires_at` is still in the future is included. Explicit offline, expired presence and last_seen-only activity are excluded. This read does not heartbeat or change presence. Use the existing presence setter for actual player state, not this list. Keep the row fresh while home is visible, for example every 60 seconds and on resume. Do not retain a green indicator as live after a failed refresh; show stale/unavailable or hide it until refreshed. This is not a Realtime subscription.

Privacy rules match the existing Squad Finder card visibility: the player must be self, discoverable, related, a friend or a squadmate. Both viewer and listed player must allow show_activity. Bilateral blocks, banned viewers and banned listed accounts are excluded. A hidden player does not become discoverable because the home row exists. Authenticated but restricted/missing profiles receive the existing 42501 exception `Login with an unrestricted account required.` Anonymous calls have no EXECUTE grant and return HTTP 401/403. Raw presence tables remain private.

Exactly five per-player fields are returned: id, username, ign, avatar_url, presence. No Free Fire UID, phone, email, device, coordinates, reasons, last_seen, trust scores or other private metadata.

Pre-deploy and post-deploy rollback tests passed: active, in-match, expiry even with a recent last_seen, explicit offline, both activity opt-outs, both directions of blocking, banned target, bounded limit, payload-key allowlist and grants. Actual authenticated-role response was an honest empty list at deployment. Anonymous HTTP was 401. Before/after fingerprints verified that profiles, preferences, presence and block rows did not change. Tests are transactional with no committed fixture data. No native UI E2E or multi-session load test claimed.

Rollback: `BEGIN; DROP FUNCTION public.xr_sf_active_players(integer); NOTIFY pgrst,'reload schema'; COMMIT;`. No schema data changes need reverting.
