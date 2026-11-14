BEGIN;
-- Private clan delivery uses the same current-member boundary as room_feed.
CREATE POLICY xr_room_realtime_read_members ON public.squad_room_messages FOR SELECT TO authenticated USING(public.xr_sf_is_member(squad_id));
-- SELECT grants already exist, RLS still rejects anonymous/nonmembers.
DO $$BEGIN
 IF NOT EXISTS(SELECT 1 FROM pg_publication_tables WHERE pubname='supabase_realtime'AND schemaname='public'AND tablename='dm_messages')THEN ALTER PUBLICATION supabase_realtime ADD TABLE public.dm_messages;END IF;
 IF NOT EXISTS(SELECT 1 FROM pg_publication_tables WHERE pubname='supabase_realtime'AND schemaname='public'AND tablename='squad_room_messages')THEN ALTER PUBLICATION supabase_realtime ADD TABLE public.squad_room_messages;END IF;
END $$;
COMMIT;
