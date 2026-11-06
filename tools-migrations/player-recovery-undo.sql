DROP FUNCTION IF EXISTS public.recovery_enroll(uuid,text),public.recovery_reserve(text,text),public.recovery_key_status();
DROP TABLE IF EXISTS public.player_recovery_keys,public.player_recovery_limits;
-- Disable player-recovery edge separately. Already changed passwords are not rolled back.
NOTIFY pgrst,'reload schema';
