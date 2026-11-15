-- Stop new writes/automatic deletion without dropping saved objects or messages.
SELECT cron.unschedule('xarena-clan-retention');
REVOKE EXECUTE ON FUNCTION public.room_post_media(text,text,text,integer)FROM authenticated;
DROP POLICY IF EXISTS xr_clan_media_upload ON storage.objects;
-- Existing read expiry remains. No historical data restoration is implied.
