BEGIN;
-- Revert only this migration's publication additions and policy.
ALTER PUBLICATION supabase_realtime DROP TABLE public.dm_messages,public.squad_room_messages;
DROP POLICY xr_room_realtime_read_members ON public.squad_room_messages;
COMMIT;
