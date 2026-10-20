# XARENA Native - big bundle status (Oct 20-21 night build)
Legend: DONE=in tonight's build | SHIP=already live | BACK=waiting on web/backend contract | GATE=built but behind flag until post-Oct-24 regression test | NEXT=queued, not started

## 25-point list
1 Daily check-in streak: DONE (home card + StreakScreen)
2 Daily challenge: SHIP (Play screen - daily spin + quiz XP)
3 Daily quiz: SHIP (quiz_feed/quiz_answer in Play)
4 Match poll "Kaun jeetega?": poll infra DONE; needs admin-created match polls
5 Aaj kya hai card: DONE
6 Squad of the Week wall: BACK
7 Clips wall: SHIP (clips screen exists)
8 Practice scrim list: BACK
9 Roz naya avatar/frame: DONE (daily_avatar card)
10 Achievement badges: streak badges DONE; match badges BACK
11 Match check-in: SHIP (MatchCenter match_checkin)
12 Points table: DONE (points_table RPC live)
13 Live bracket/lobby: GATE (post Oct 24)
14 "1 hour pehle" reminder: BACK (server push timing, web-builder owned)
15 Winners squad photo/logo: DONE (hall_of_fame strip w/ logos)
16 Team Finder: BACK
17 Squad chat room: SHIP (clan room in X-Panel); in-app room NEXT
18 Friends + invite link: BACK
19 City leaderboard: SHIP (Leaderboard state/district scopes)
20 Pinned announcements: SHIP (RemoteConfig) + tips/pinned keys DONE
21 Share button: DONE (hero share)
22 3-step walkthrough: DONE
23 Theme toggle: DONE (Look palettes existed, now exposed in Settings: Liquid/Classic + Gold/Red-Gold)
24 Language toggle: NEXT - high risk, large surface
25 Profile stats card + share image: SHIP (ShareCard.kt)

## Home 9: ALL DONE (hero countdown, X/48 bar, aaj kya hai, room ID line, check-in, winners strip, top-3, fan vote up, register CTA + hero=Ignition)

## Extras 10
1 Share my squad link: BACK | 2 IG story poster: NEXT | 3 Squad profile page: DONE (squad_profile RPC + taps from Top Squads) | 4 Player card+share: NEXT | 5 Match recap card: BACK | 6 "match tonight" push: BACK | 7 Rules/FAQ: SHIP (rules) + Match Checklist DONE | 8 Report/feedback box: DONE (feedback_submit) | 9 Referral count: BACK | 10 Hall of fame: DONE

## 12 ideas
1 Tournament history: SHIP (Winners/Hall of Fame) | 2 H2H squads: DONE (head_to_head RPC) | 3 MVP of match/week: DONE (hall_of_fame categories) | 4 Highlight reel wall: NEXT | 5 Creator spotlight: DONE | 6 Scrim schedule: BACK | 7 Aaj ka tip: DONE | 8 Notice board: DONE (pinned) | 9 Squad invite link: BACK | 10 Per-tournament reminder toggle: BACK | 11 Low-data mode: DONE (Data Saver) | 12 Offline cache: DONE (last-good cache on public GETs)

## 8 picks
1 Weekly mini-tournament: GATE | 2 Rank/level: SHIP (my_header/levelFromXp) | 3 Live match thread: GATE | 4 Spectator room page: GATE | 5 Player of the Day: BACK | 6 Meme/clip voting: DONE (poll infra reusable) | 7 Sound/haptics+anims: DONE part (Rise3D, Data Saver gate); haptics/sound toggle NEXT | 8 (logo maker not picked)

## 7 ideas: NEXT batch (squad mini-page, challenge squad, pre-match checklist, season recap, Hinglish guide, calendar, feedback box - feedback BACK)

## Store: cosmetic Store tab (user-confirmed): NEXT (Store.kt exists, needs catalog contract)

## Creator 9: 1 DONE (spotlight); 2-9 BACK/NEXT (no money, additive)

## Admin 8: web/admin-owned; register_squad NOT touched; dup-check GATE

## Look/feel 10: Rise3D entrance DONE; parallax/tilt NEXT; splash anim NEXT; confetti NEXT; neon theme NEXT (extra option); pull-refresh NEXT; skeleton DONE; haptics+sounds NEXT; animated badges NEXT; LIVE pulse NEXT; widget DONE

## 3D effects: DONE (Rise3D, gated by Data Saver)
