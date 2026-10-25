# XARENA Native - big bundle status (Oct 20-21 night build)
Legend: DONE=in tonight's build | SHIP=already live | BACK=waiting on web/backend contract | GATE=built but behind flag until post-Oct-24 regression test | NEXT=queued, not started

## 25-point list
SHIPPED v0.17.19-native (64), Oct 24 ~8:08 PM IST: Ignition Cup postponement display fix - the 4 postponed xr-5oar matches render POSTPONED (not CANCELLED), no countdown/check-in/room-reveal UI, excluded from next-match pickers, LIVE NOW swiper and chat chips; inert on reschedule (NATIVE_CONTRACTS.md 6195e0c). Untested on a real phone.
SHIPPED v0.17.20-native (65), Oct 24 ~8:22 PM IST: fixed Match Center room/check-in/proof/report RPCs sending p_match=0 (match id is a UUID string; optLong silently yielded 0) - UUID now passed end to end in MatchCenter, Home next-match card, X panel. Supersedes v0.17.19 (64). 15 OTA patches incl 63->65. Untested on a real phone.
SHIPPED v0.17.21-native (66), Oct 25 ~1:07 PM IST: fixed stuck pull-to-refresh black circle (replaced material3 PullToRefreshContainer with isRefreshing-gated gold spinner) and CupHeroCard REGISTER button hiding SHARE/POSTER (weight(1f)). Untested on a real phone.
1 Daily check-in streak: DONE (home card + StreakScreen)
2 Daily challenge: SHIP (Play screen - daily_missions_v2 + quiz)
3 Daily quiz: SHIP (quiz_feed/quiz_answer in Play)
4 Match poll "Kaun jeetega?": poll infra DONE; needs admin-created match polls
5 Aaj kya hai card: DONE
6 Squad of the Week wall: DONE (spotlights table, SpotlightsRow on Home)
7 Clips wall: SHIP (clips screen exists)
8 Practice scrim list: SHIP (ScrimsScreen on scrim_find - RPC live-verified, auth-gated)
9 Roz naya avatar/frame: DONE (daily_avatar card)
10 Achievement badges: streak badges DONE (+ animated pulse); match badges BACK
11 Match check-in: SHIP (MatchCenter match_checkin)
12 Points table: DONE (points_table RPC live)
13 Live bracket/lobby: GATE (post Oct 24)
14 "1 hour pehle" reminder: DONE (MatchReminderRow toggle per tournament; my_match_reminders opted_out; server pushes 1h+15min)
15 Winners squad photo/logo: DONE (hall_of_fame strip w/ logos)
16 Team Finder: DONE (TeamFinderScreen: lfg_list/create/join/leave/set_role)
17 Squad chat room: SHIP (clan room + Squads room screen)
18 Friends + invite: DONE (friend_request/_by_name/respond/remove + referral code share; invite LINK format BACK)
19 City leaderboard: SHIP (Leaderboard state/district scopes)
20 Pinned announcements: SHIP (RemoteConfig) + tips/pinned keys DONE
21 Share button: DONE (hero share)
22 3-step walkthrough: DONE
23 Theme toggle: DONE (Look palettes existed, now exposed in Settings: Liquid/Classic + Gold/Red-Gold)
24 Language toggle: DONE (English/Hinglish fixed-label toggle in Settings; tabs, home headers, key buttons; posts/names untouched - web parity)
25 Profile stats card + share image: SHIP (ShareCard.kt)

## Home 9: ALL DONE (hero countdown, X/48 bar, aaj kya hai, room ID line, check-in, winners strip, top-3, fan vote up, register CTA + hero=Ignition)

## Extras 10
1 Share my squad link: BACK (no link contract; by-name squad_invite exists) | 2 IG story poster: NEXT | 3 Squad profile page: DONE (squad_profile RPC + taps from Top Squads) | 4 Player card+share: NEXT | 5 Match recap card: BACK | 6 "match tonight" push: DONE (per-tournament MatchReminderRow on Tournaments; server pushes 1h+15min before to registered squads) | 7 Rules/FAQ: SHIP (rules) + Match Checklist DONE | 8 Report/feedback box: DONE (feedback_submit) | 9 Referral count: SHIP (ReferScreen on my_referral code/count/earned) | 10 Hall of fame: DONE

## 12 ideas
1 Tournament history: SHIP (Winners/Hall of Fame) | 2 H2H squads: DONE (head_to_head RPC) | 3 MVP of match/week: DONE (hall_of_fame categories) | 4 Highlight reel wall: SHIP (ClipsScreen = approved clip posts, same query as site) | 5 Creator spotlight: DONE | 6 Scrim schedule: BACK | 7 Aaj ka tip: DONE | 8 Notice board: DONE (pinned) | 9 Squad invite: DONE by-name (squad_invite/my_squad_invites/respond in Squads); shareable LINK format BACK (no contract) | 10 Per-tournament reminder toggle: DONE | 11 Low-data mode: DONE (Data Saver) | 12 Offline cache: DONE (last-good cache on public GETs)

## 8 picks
1 Weekly mini-tournament: GATE-OFF-READY (MiniCupScreen built, flag mini_cup=false so hidden; shows real mini tournaments when flag flips) | 2 Rank/level: SHIP (my_header/levelFromXp) | 3 Live match thread: GATE-OFF-READY (LiveThreadScreen on thread_list/thread_post/thread_report, flag-gated hidden; server ok:false while flag off) | 4 Spectator room page: GATE-OFF-READY (SpectatorScreen on spectator_info contract - strips room_id/pass defensively; flag hidden) | 5 Player of the Day: DONE (spotlights kind player_day in SpotlightsRow) | 6 Meme/clip voting: DONE (poll infra reusable) | 7 Sound/haptics+anims: DONE part (Rise3D, Data Saver gate); haptics/sound toggle NEXT | 8 (logo maker not picked)

## 7 ideas: squad mini-page DONE (SquadProfileScreen) | challenge squad SHIP (challenge_create/respond in Leaderboard) | pre-match checklist DONE | season recap DONE (ShareCard.buildRecap + share button on Streak page) | Hinglish guide DONE | calendar SHIP (grid) | feedback box DONE (feedback_submit)

## Store: cosmetic Store tab (user-confirmed): SHIP (Store.kt full: points_balance, rewards_catalog, avatar_catalog, logo shop - verified tonight; AP cosmetic-only)

## Creator 9: 1 DONE (spotlight); 2-9 BACK/NEXT (no money, additive)

## Admin 8: web/admin-owned; register_squad NOT touched; dup-check GATE-OFF-READY (client pre-submit re-check of my_registrations gated on flags.dup_check=false; server-side dup rules unchanged)

## Look/feel 10: Rise3D DONE; parallax/tilt NEXT; splash anim DONE (SplashAnim, Data-Saver fast path); confetti DONE (ConfettiBurst); neon theme DONE (Dark Gold palette, site parity, Settings); pull-refresh DONE (Home PullToRefreshContainer); skeleton DONE; haptics+sounds DONE (Fx, Settings toggles, Data-Saver gated); animated badges DONE (streak number pulse); LIVE pulse DONE (LivePulse); widget DONE

## 3D effects: DONE (Rise3D, gated by Data Saver)

## Batch-7 (Oct 20 ~9:50 PM): spotlights row, spectator_info, live thread screen, team finder all wired to web contracts. match_room ok:false already handled gracefully (MatchCenter shows server error text).

## Registration retarget fix (Oct 20 ~9:35 PM, parent-relayed from web)
- register_squad / enter_tournament_with_ap: p_tournament was HARDCODED "xr-s1" (Season 1) in Api.kt; my_registrations filter + entry-cost lookup + UI copy also xr-s1. FIXED: target resolved dynamically from site_settings reg_tournament {id} (same as web app.js xrRegT()), fallback "xr-5oar" (Ignition Cup, user-confirmed). Live value verified = xr-5oar.
- Reg ID prefix kept "XR-S1-" (web bindForm prefix unchanged - parity).
- Waitlist states added per web contract: response waitlist:true -> "You are on the waitlist" note; my_registrations status "waitlist" -> "WAITLIST - you are in line if a slot opens". tournament_reg_counts now returns waitlist field (live-verified, currently 0).
- Registration logic otherwise untouched (parent constraint).
- TEST: throwaway account p919000000010@phone.xarena (uuid cb786fdb-f77f-44c8-af9f-efa2c759ec57). my_registrations empty OK; register_squad params accepted, blocked at server phone-verification gate (manual Instagram verification by owner) - so no registration row was created, nothing to roll back in registrations. THROWAWAY AUTH USER REMAINS - web builder asked to purge it (service key needed).

## SHIPPED v0.17.16-native (61) - Oct 20 ~10:12 PM
- Release: https://github.com/xarenaprivate/xarena/releases/tag/v0.17.16-native (prerelease, xarena-native.apk 28,833,160 B, sha 355bc92df48ded7d941dfa37ff5e03491be326481f09a8921b0980eec249348a)
- 13 patch bases (47-52, 54-60) all PatchCheck-verified byte-identical; manifest vc 61 live on CDN; /releases/latest untouched (app-v7.22.1).
- Rides v0.17.17: shareable squad-invite link format, match recap card, remaining web batches.

## SHIPPED v0.17.17-native (62) - 2026-10-20 ~11:25 PM IST
- Creator board + creator profile (bio, posts, joined) + in-app bio edit (creator_board/creator_profile/creator_set_bio)
- Squad invite share link (web format more.html?squad=<uuid>#squad)
- Match result recap share card on points table (top 3 + top fragger + reg counts)
- Sandbox reaped mid-build; full toolchain restore; 2GB-box OOM fixed by killing lingering KotlinCompileDaemon + in-process kotlin (local gradle.properties tweak, NOT committed)
- Release: v0.17.17-native (prerelease). SHA256 311c795f68886577231bd37ee705d0475892a9eb9f883ef584996f0c9cc4e188, 28,849,544 B
- 14 patches (47-52,54-61 -> 62), all PatchCheck-verified byte-identical
- CDN vc 62 live; /releases/latest stays app-v7.22.1; download hashes verified
- Untested on a real phone (emulator-free build env)

## SHIPPED v0.17.18-native (63) - 2026-10-22 ~3:17 PM IST
- Fix: Home check-in + avatar-of-the-day cards and chat SEND row stretched blank (GoldButton fillMaxWidth inside weighted Row). GoldButton now takes a modifier; Row uses fixed width.
- SHA256 5e1fe740425a38ed957c530d3c421b06ec0a1734259af6dcc0b72cada7acc75b, 28849548 B, 14 patches (48-52,54-62), PatchCheck 14/14, CDN vc 63
