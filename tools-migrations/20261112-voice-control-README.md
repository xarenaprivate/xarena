# Voice Lobby authenticated control plane

Deployed **staged**: enabled=false, rewards_enabled=false, reward amount0, no room/participant/topic/history/poll/rank/badge/attendance data seeded. Existing peer-to-peer voice is untouched. This is not a media server, recording system, private signaling migration or proof of audio delivery.

## Integration constraints

Existing native `Voice.kt` uses public Supabase Realtime signaling, random peer identifiers and client-provided handles. Its peer metadata has muted/unmuted, NOT voice activity. SQL membership cannot stop a bypassing old client connecting to that public channel. Seat cap is enforced for the authenticated control plane, not a security cap on public audio. Secret audio is explicitly unavailable and cannot be opened through the room admin RPC. No private-room claim is permitted.

Speaking is false unless native measures actual local voice activity and submits it through its own fresh session. Do not map unmuted to speaking. Server labels this client_vad_reported, not independently confirmed speech. First Speaker badges require admin confirmation of observed speech, not client true. Streak attendance is also admin confirmed using bounded session time; no automatic coin farming from joins.

Sneak peek is phase2: current engine creates a microphone track and joins peers. A genuine10-second receive-only peer connection with timed cleanup requires native/media changes; no audio or fake listen counter is supplied here. Real listen-only mode without microphone capture also requires native changes. Nothing in these RPCs alone makes mic permission optional.

## Auth and lifecycle

All RPCs below require authenticated JWT and unrestricted account. Raw tables have no anon/authenticated grants. No phone, email, Free Fire UID, device, peer connection candidates, SDP or auth token is returned. Use normal authenticated helper/refresh. Inspect ok:false AND HTTP errors.

### `xr_voice_lobby_presence({p_room?:uuid})`

Omit p_room to resolve the actual current live public room. Returns:

- ok, enabled
- room: null or `{id,title,topic,next_topic,seat_cap,starts_at,ends_at,status}`
- participants: `[{id,username,ign,avatar_url,speaking,hand_raised,is_friend,is_squadmate}]`
- joined_count: actual unexpired admitted control-plane sessions, including anonymous aggregate seats for members whose identity isn't visible
- visible_count: number of privacy-visible participant cards
- speaking_count: count of visible fresh client-VAD reports
- presence_source: authenticated_client_reported
- speaking_source: client_vad_reported when a room exists
- sneak_peek_available:false, secret_audio_available:false

No current room means null room, empty participants and real zero counts. No topic means null; hide it. Participant visibility mirrors Squad Finder: show_activity on both sides, no bans/blocks, self/discoverable/related/friend/squad scope. Friends flag requires reciprocal actual friend rows. Hidden identities are not invented to fill seats. A secret room id does not expose participant identities to ordinary viewers. Presence expires90 seconds after last heartbeat; speaking clears from UI after15 seconds without a speaking report.

### `xr_voice_join({p_room?:uuid})`

Staged returns `{ok:false,error:"not_open"}`. Requires real currently live nonsecret room. Admitted response: `{ok:true,state:"joined",room_id,session_id,expires_at,signaling_authorization_enforced:false}`. An idempotent retry may return already_joined:true. Do not treat control-plane admission as a connected peer or delivered audio.

When full or an earlier waiter has priority: `{ok:true,state:"waitlisted",room_id,rank,...}`. Rank counts actual live waiters ordered by waiting time/account, not fictional queue positions. Waitlist lease5 minutes; call join again with backoff to renew/retry. It does not auto-connect a promoted participant. Room row lock serializes admissions/cap checks. Earlier unexpired waiters have priority.

Native must coordinate real media connection and session lifecycle carefully: use real connection status, not screen entry or a loading spinner, for active/inside claims. A failed media connection must leave/cancel its control session promptly. Do not start public audio for a waitlisted player or claim the current public transport prevents bypass. Current implementation cannot combine strict pre-media secure admission with server-proven audio presence; private authorized signaling is future work.

### `xr_voice_heartbeat({p_room:uuid,p_session:uuid,p_speaking:false})`

Own admitted fresh session only. Returns `{ok:true,expires_at,speaking_source:"client_vad_reported"}` or session_expired/room_closed. Refresh at a moderate interval, e.g.30 seconds, only while actual media remains connected. VAD transition updates should be debounced/coalesced, never per audio frame. Keep p_speaking=false until actual VAD exists. Session id is private operational state: never expose it in public cards/logs.

Bounded attended seconds accrue from server clock between heartbeats, maximum45 seconds per call, never caller-supplied durations. Immediate repeated calls cannot add time and a long gap cannot add hours. This is connection evidence, not proof that the person listened; admin confirmation still required for attendance rewards.

### `xr_voice_leave({p_room:uuid,p_session:uuid})`

Own matching session only, idempotent `{ok:true}`. Call on leave, audio failure, disposal/disconnect. Expiry is fallback for process loss, not a reason to leave ghost sessions visible.

### `xr_voice_hand_raise({p_on:boolean,p_room?:uuid})`

Current admitted fresh member only. Returns `{ok:true,hand_raised:boolean}` or join_first/no_live_room. Raising a hand does not make someone a speaker or grant the badge.

### `xr_voice_history({})`

`{ok:true,last_room:null|{id,title,topic,peak_count,ended_at},next_room:null|{id,title,topic,starts_at,seat_cap}}`. Only actual ended/scheduled nonsecret rooms. No transcript or fictional recap. Peak is observed admitted control-session concurrency, not independently counted audio listeners. Admin must end room to create ended_at history. No schedule means hide countdown/reminder copy. Notification/remind delivery is not implemented.

### `xr_voice_streak_status({})`

`{ok:true,streak_days,last_attendance_day,timezone:"Asia/Kolkata",threshold_days,reward_coins,rewards_enabled,attendance_source:"admin_confirmed_session",currency:"AP_not_cash"}`. Consecutive confirmed local dates; streak0 if latest confirmed date is older than yesterday. No attendance means honest0. Rewards disabled reports amount0. No cash value or withdrawal promise.

### Polls

- `xr_voice_poll_create({p_room,p_question,p_options:["A","B"],p_duration_seconds?:300})`: actual live room. Admin or fresh joined admin-confirmed speaker. One open poll per room. Question3..160 chars;2..6 nonempty string options1..60 chars, no control/markup; duration30..3600 seconds. Returns ok,poll_id. A hand raise alone is insufficient.
- `xr_voice_poll_vote({p_poll,p_choice:0})`: actual fresh joined member, live room/open poll. Zero-based valid option. First vote wins; retries return already_voted:true without changing/duplicating it.
- `xr_voice_poll_results({p_room})`: fresh joined member or admin. Returns `{ok:true,poll:null|{id,question,options,closes_at,votes,counts,my_choice},top_voters:[{id,username,ign,votes}],top_voters_metric:"votes_cast_in_this_room"}`. Top3 means actual number of votes cast across this room's polls, ties by first vote/id. Identities apply profile sharing/block rules; anonymous aggregate totals remain real. It does not generate spoken shoutouts or claim an admin read a name aloud.

### `xr_voice_room_eligibility({p_room})`

Returns rank_eligible,rank_source:admin_confirmed,required_rank,within_window,audio_admission_available,secret_audio_phase. Editable profile.ff_rank is NOT trusted. Admin-confirmed verified-rank record required for secret rank comparison. Rank order Bronze/Silver/Gold/Platinum/Diamond/Heroic/Grandmaster. Secret audio always audio_admission_available:false with private_signaling_required; rank eligible is NOT permission to claim secure audio works. Public nonsecret field means no rank restriction, not proof the room is currently connected.

## Admin setup and evidence

Actual authenticated admin only. New admin web controls are not built by these SQL endpoints. No operator grants were added.

- `xr_voice_settings_admin({p_patch:{}})` read/patch keys: enabled(defaultfalse),default_seat_cap(20,2..50),first_speaker_limit(5,0..20),streak_days(4,2..365),streak_reward_coins(0,0..10000),rewards_enabled(false),min_attendance_seconds(300,60..7200). No financial cost/player charge.
- `xr_voice_room_admin({p_patch:{title,topic?,next_topic?,status?,seat_cap?,starts_at,ends_at?,is_secret?,required_rank?},p_room?:uuid})`: create or update. New default scheduled, public and configured seat cap. Status scheduled/live/ended. Only one public live room. Live must be within schedule. Secret rooms must have rank + end time but cannot be made live until private media support exists. Ended immutable; ending marks members left and records actual ended_at. Capacity updates do not evict existing audio participants; lowering cap controls new admissions only.
- `xr_voice_rank_admin({p_user,p_rank})`: owner confirmation of real rank evidence, not a self-report verifier. Does not query Garena or assert a fresh rank without admin evidence.
- `xr_voice_speaker_confirm({p_room,p_user})`: admin attests they actually observed this joined player speak. One immutable ordinal/person/room. First configured N receive existing profile badge string `VOICE STAR` idempotently; later confirmed speakers do not get a false first-five grant. Returns ordinal,badge_granted,source:admin_observed_speech. No automatic grant from VAD/hand raise. Confirm in observed order; the server cannot infer historical speaker order.
- `xr_voice_attendance_confirm({p_room,p_user})`: admin attests attendance after at least configured bounded session seconds, within7 days. One attendance day/person, date from actual session join in IST. If enabled, enough consecutive confirmed days and enough days since last reward create exactly one points_ledger credit kind voice_streak with AP metadata plus unique reward record. Retry cannot duplicate. Reward0/disabled does not credit. Admin confirmation is a real operational obligation, not automatic rewards. No new cash/credit spend, no external payout, no fake presence.

## Scaling/privacy

No per-frame/per-second polling is required. Presence is at most the cap-limited membership; use30-second heartbeat and reasonable read refresh while visible, stop on disconnect/background. Lists use bounded room membership and top3 voters; history resolves1 each. Room joins lock one room, not global player scans. Speaking source remains client-reported. Real Realtime fanout/audio connections and future private signaling quotas require measurement; this migration does not guarantee20 working mesh audio peers or5,000 app users. Existing mesh/STUN/TURN services and quotas are unchanged. No paid infrastructure added.

## Verification and rollback

Pre/post deploy rollback suite passed: staging, admin settings, room schedule, real admitted counts and cap, waitlist retry, correct session ownership, raised hand, privacy allowlist/opt-out, speaking expiry, gap-time cap, no fake secret admission, admin-confirmed badge cap/idempotency, polls/vote idempotency/totals, actual history peak, bounded attendance threshold, disabled reward, real threshold ledger credit once, streak dates and client grants. Fixture data/rewards were entirely rolled back. Existing points/badge counts unchanged. Actual authenticated-role live response: no room, empty participants, history null/null, streak0,rewards disabled. No multi-session stress/native media/on-device E2E claimed.

Rollback disables settings and removes new RPCs, keeping private session/evidence/reward tables and any earned records. It does not delete existing badges or reverse actual points grants. Existing voice audio code/signaling is untouched. Initial migration is not idempotent; forward re-enable must preserve evidence and award history.
