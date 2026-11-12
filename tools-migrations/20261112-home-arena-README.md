# Arena home layout, 2026-11-12

Reference-led website-only home. Existing visual themes unchanged; saved Arena/Classic home selection is device-local. Classic home contains every original section and feature. Main nav and footer preserved; Arena exposes all original main-nav links under All arena features.

Data: live matches_public status=live only, upcoming open tournaments, approved feed_list media, existing public sponsor posters and active banners, public general chat preview. Friend activity uses authenticated own friends edges and player_profiles_public, respecting show_activity. It never reads private message bodies, invents wins or estimates online/viewers. No seed rows or backend settings changed.

Actual empty/loading/error states are separate. Public refresh is manual; no new polling. No reference example teams, currency prizes, online numbers, views, or messages are copied. Private friend activity is never persisted to local storage. Stored layout preference does not change theme. Sponsor posters use contain instead of cropping. Existing mobile PWA shell remains intact; Arena shortcut bar adds rather than deletes destinations.

Tests: JS syntax; live endpoint smoke checks; original link/button retention; desktop and mobile visual inspection; Classic/Arena toggle and persistence; no invented data; existing homepage scripts/IDs preserved.
