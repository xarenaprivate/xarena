# Clan chat media and 14-day retention - 15 November 2026

## Live contract
- Bucket: `clan-media`, PRIVATE, maximum 8 MiB per object. No overwrites/upserts.
- Path: `<squad UUID>/<authenticated user UUID>/<random UUID>.<extension>`.
- Allowed image MIME: `image/jpeg`, `image/png`, `image/webp`.
- Allowed audio MIME: `audio/mp4`, `audio/x-m4a`, `audio/aac`, `audio/ogg`, `audio/webm`.
- Upload with the player's authenticated Storage API session. Then RPC `room_post_media(p_kind text, p_media_path text, p_body text DEFAULT '', p_duration_ms integer DEFAULT NULL)`.
- `p_kind`: `image` or `voice`. Caption maximum 300 characters. Image duration must be null. Voice duration must be 1 through 120000 ms.
- Response: `{ok:true,id:<bigint>}`, with `duplicate:true` for an existing message using that same uploaded path. Reusing an uploaded object never creates duplicate messages.
- Existing `room_post(p_body)` text/link API remains compatible. Link parsing stays client-side.
- `room_feed(p_after)` keeps existing fields and adds `kind`, `media_path`, `duration_ms`, `expires_at`; top-level `retention_days:14`. No permanent/public media URL stored. Generate a signed URL via authenticated Storage API on demand, with a short expiry, at most 300 seconds or the remaining message lifetime, whichever is smaller. Refresh only while the message remains visible/unexpired. Never cache the signed URL as permanent message data.
- Native Android confirmed this contract through the owner conversation before deployment. AAC/M4A recording fits the audio MIME/size/duration limits.

## Permissions
All message and media reads/posts require current nonbanned squad membership. Upload namespace also binds the uploader UUID. Uploaded MIME is checked before a media message is posted. No outside URL accepted as a media path. Anonymous and unrelated users cannot read/upload these objects or post media. Users can delete their own objects; no update/upsert policy exists.

The read policy lets the uploader read an unposted upload, but another member needs a live message referring to it. Clan text/media privacy is independent of voice channels, which do not enforce membership and must not be called private.

## Retention and space
The owner explicitly requested that old clan chats disappear after 14 days to save space. Feed RPC and message/storage RLS hide expired data at the cutoff. Web room.html prunes already-rendered expired messages every second; native must also prune its local state using expires_at, because a server cannot remove pixels/caches already on a device.

Cron job `xarena-clan-retention` (job 24) calls `xr_clan_cleanup_tick()` hourly at minute23 UTC. It deletes expired message rows and requests deletion of up to500 expired objects, including abandoned uploads, through the actual Storage API. It does NOT directly delete storage metadata and leave blob bytes behind. Failed object deletions remain eligible for retry on the next tick. The service credential is loaded server-side from an existing vault secret, never included in this migration or cron command. Audit rows live in server-only `xr_clan_cleanup_runs`; use `storage_request_id` to verify net._http_response. A queued request is not proof that bytes were removed.

First real tick removed one pre-existing clan message older than14days. A real isolated1px Storage upload was backdated for the cleanup smoke test: Storage deletion returned HTTP200 and the object disappeared. No user-facing test chat message was retained. No other buckets, DM history, wallet data or voice state changed.

Signed URLs already issued can work until their expiry, which is why clients must cap them to the shorter of300seconds or the remaining message lifetime. Existing already-downloaded copies cannot be recalled.

## Verification
- Rollback-only SQL tests on production: member media post/read; retry idempotency; image/voice MIME mismatch refused; expired message absent from feed and direct SELECT; anonymous/outsider storage/message reads denied; outsider upload denied; anonymous media RPC denied. No fixture persisted.
- Real Storage API upload and actual cleanup DELETE HTTP200, not merely metadata deletion.
- Existing web clan authenticated realtime/auth/filter/coalescing/dedup/cleanup regression passed.
- Added web expired-visible-message removal and empty-state restoration test passed.
- Website's legacy chat renderer continues showing text/captions/placeholders for media; native owns image/audio rendering and uploads in its next build. Backend deployment is not proof that an already-installed APK supports media.
- No paid services or plans added. Storage quotas/rate limits remain those of the existing free project; this is not an unlimited-storage promise.

## Recovery
`pause.sql` stops the new retention job and new uploads/posts without deleting any data or dropping media columns. Restore the prior web script if needed. Original text RPC definitions are archived beside this note for review, but do not restore old definitions without considering banned-member checks and retention. Already expired/deleted messages and Storage objects cannot be restored by rolling back code.
