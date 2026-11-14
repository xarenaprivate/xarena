# Voice and chat latency work - 14 November 2026

## What was actually present
Web voice is a peer-to-peer WebRTC mesh. Supabase Realtime handles signaling and presence. Google STUN is configured, TURN is not. Audio does not pass through a Supabase voice server, so moving a nonexistent voice media server region would not fix voice delay. Supabase project is already ap-south-1.

World Chat already used Postgres Changes websockets. DM and Clan Room fetched messages every4seconds. The scheduler alone added0-4seconds waiting, approximately2seconds mean for uniformly timed arrivals, before network/decryption/rendering. This is a code-derived interval, not a measured end-to-end latency distribution.

## Changes
- Realtime publication adds dm_messages and squad_room_messages, retaining chat_messages.
- Clan SELECT policy checks current nonbanned membership through the existing server helper. Anonymous/nonmembers stay unable to read. DM's participant-only RLS and E2EE unchanged.
- Web DM and Clan subscribe only to the currently open thread/clan, with the logged-in session. INSERT events trigger the existing authenticated fetch/decryption path. No private content is broadcast on public topics.
- Catch-up on subscription/reconnect, fetch coalescing, duplicate suppression, stale-thread guard, channel cleanup and foreground-only subscriptions. Healthy realtime uses30second safety catch-up; broken realtime retains4second polling fallback. No universal faster polling load.
- World Chat adds catch-up on subscribe/return-online and duplicate suppression. It was already realtime; this is not described as replacing polling there.
- Voice requests mono capture and20ms latency hint while retaining echo cancellation/noise suppression. Audio send bitrate is capped at32kbps when supported; this is congestion control, not a claim that lower bitrate alone lowers every call's delay. Receiver requests40ms jitter-buffer hint when supported. Browser/network can override.
- ICE candidates received before the offer/answer are queued and flushed after the remote description, rather than dropped. Signaling is serialized per peer. Regional-room lookup has a bounded wait before joining.
- Voice shows actual peer RTT, average receiver jitter buffer and packet loss from getStats. These are transport/buffer diagnostics, NOT microphone-to-ear latency. No audio is recorded or uploaded by diagnostics.

## Evidence and limits
No two-phone voice test or real recipient delivery measurement was available. Zero delay is physically impossible. Network RTT, capture/codec buffering, jitter, packet loss, device audio/Bluetooth routing and mesh room size still matter. STUN-only connectivity can fail on restrictive NAT; no public credentialed TURN relay or paid service was added. No promise of universal audio connectivity or zero delay.

Public API probes from the test host measured3 World Chat id-only GETs at799/722/775ms, median775ms; unrelated public status RPC815/288/752ms, median752ms. These are API round trips from this test host, not user phone latency, voice latency, or before/after delivery proof.

## Verification
Rollback-only SQL tests: member can SELECT a transaction-local clan message; unrelated account and anonymous cannot; all3intendedtables in publication. No test message persisted. DM policy unchanged.
JS parsing passed. Local client tests: early ICE retained then flushed after remote description; clan websocket filter/auth-refetch, duplicate suppression, coalescing and channel cleanup. These are controlled tests, not delivery measurements between real users.

Native Android changes are owned by its separate app implementation and require its own build and testing. Backend support alone does not change the old installed APK's polling behavior.

## Recovery
Restore prior web assets/page inline scripts to revert client behavior; existing4second authorized fallback remains in new clients if realtime is unavailable. Server down.sql removes only the2new publication tables and member policy. Do not remove World Chat from publication, change encryption or widen read access to debug latency.

Sources:
- https://supabase.com/docs/guides/realtime/postgres-changes
- https://developer.mozilla.org/en-US/docs/Web/API/RTCRtpReceiver/jitterBufferTarget
- https://developer.mozilla.org/en-US/docs/Web/API/MediaTrackConstraints/latency
