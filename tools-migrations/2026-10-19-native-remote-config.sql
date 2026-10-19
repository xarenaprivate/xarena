-- Remote config row for the native app (Free Fire style content updates without an APK install).
-- Additive: one site_settings row, same table the web already uses for site/luna/youtube_live.
-- Shape:
--   announcement: gold banner on the Home tab (active + text, optional link opened in browser)
--   min_version_code: installed builds below this get a MANDATORY update prompt (no skip)
--   features: More-grid entries hidden only when explicitly false, e.g. {"voice": false}
insert into public.site_settings (key, value)
values ('native', '{"announcement": {"active": false, "text": "", "link": ""}, "min_version_code": 0, "features": {}}'::jsonb)
on conflict (key) do nothing;
