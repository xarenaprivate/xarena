# Voice room parity - 15 November 2026

The website Voice page always joins the shared arena lobby. Regional chat_my_room routing is removed. Supabase JS channel("voice:arena-lobby") uses wire topic realtime:voice:arena-lobby, matching native branch commit 9173d1445b0b43c21d1b058dd5c4a2b8229c4fdc Voice.kt.

Clan Room offers Join clan voice, opening voice.html?clan=1. Before microphone/channel access, authenticated room_info resolves the current user's squad. Caller-supplied squad IDs are ignored. The JS topic is voice:squad:<resolved UUID>, matching native's wire topic realtime:voice:squad:<UUID>. A missing login or clan stops the join; it never falls back into the global lobby.

IMPORTANT: existing voice presence/broadcast channels accept anonymous joins. Separate clan naming is NOT access control. Both pages warn that anyone who knows the channel can join. No backend permission change made. Authenticated clan text chat remains membership-restricted.

Native Voice.kt inspected: sig broadcast offer/answer/ice fields, random presence key and smaller-ID-offers rule match the web. No real two-device or native-to-web audio test was run. No measured latency or universal connectivity claim. STUN-only web connectivity limitation remains.

Controlled client tests passed: global routing without regional lookup; clan resolution from room_info; injected URL squad ignored; logged-out and nonmember denied before mic/channel. Prior early ICE queue regression test retained. UI inspected separately. No paid providers, no notifications or messages sent.

Rollback: restore assets/js/voice.js and room.html from the parent commit and cache-bust voice.html. No SQL migration to undo.
