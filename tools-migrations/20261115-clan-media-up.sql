BEGIN;
ALTER TABLE public.squad_room_messages
 ADD COLUMN IF NOT EXISTS kind text NOT NULL DEFAULT 'text',
 ADD COLUMN IF NOT EXISTS media_path text,
 ADD COLUMN IF NOT EXISTS duration_ms integer;
ALTER TABLE public.squad_room_messages ADD CONSTRAINT xr_clan_message_kind CHECK(kind IN('text','link','image','voice'));
ALTER TABLE public.squad_room_messages ADD CONSTRAINT xr_clan_message_media CHECK(
 (kind IN('text','link')AND media_path IS NULL AND duration_ms IS NULL)OR
 (kind='image'AND media_path IS NOT NULL AND duration_ms IS NULL)OR
 (kind='voice'AND media_path IS NOT NULL AND duration_ms IS NOT NULL AND duration_ms BETWEEN 1 AND 120000));
CREATE UNIQUE INDEX xr_clan_media_once ON public.squad_room_messages(media_path) WHERE media_path IS NOT NULL;
CREATE INDEX IF NOT EXISTS xr_clan_expiry ON public.squad_room_messages(created_at);
INSERT INTO storage.buckets(id,name,public,file_size_limit,allowed_mime_types)
 VALUES('clan-media','clan-media',false,8388608,ARRAY['image/jpeg','image/png','image/webp','audio/mp4','audio/x-m4a','audio/aac','audio/ogg','audio/webm'])
 ON CONFLICT(id)DO UPDATE SET public=false,file_size_limit=excluded.file_size_limit,allowed_mime_types=excluded.allowed_mime_types;
CREATE OR REPLACE FUNCTION public.xr_clan_media_member(p_path text)RETURNS boolean
 LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE sid uuid;
BEGIN
 IF p_path !~ '^[0-9a-fA-F-]{36}/[0-9a-fA-F-]{36}/[0-9a-fA-F-]{36}\.[a-zA-Z0-9]{2,5}$' THEN RETURN false;END IF;
 BEGIN sid:=split_part(p_path,'/',1)::uuid;EXCEPTION WHEN invalid_text_representation THEN RETURN false;END;
 RETURN public.xr_sf_is_member(sid);
END$$;
REVOKE ALL ON FUNCTION public.xr_clan_media_member(text)FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.xr_clan_media_member(text)TO authenticated;
CREATE POLICY xr_clan_media_upload ON storage.objects FOR INSERT TO authenticated WITH CHECK(
 bucket_id='clan-media'AND public.xr_clan_media_member(name)AND split_part(name,'/',2)=auth.uid()::text);
CREATE POLICY xr_clan_media_read ON storage.objects FOR SELECT TO authenticated USING(
 bucket_id='clan-media'AND created_at>now()-interval '14 days'AND public.xr_clan_media_member(name)
 AND(split_part(name,'/',2)=auth.uid()::text OR EXISTS(SELECT 1 FROM public.squad_room_messages m WHERE m.media_path=objects.name AND m.created_at>now()-interval '14 days')));
CREATE POLICY xr_clan_media_delete ON storage.objects FOR DELETE TO authenticated USING(
 bucket_id='clan-media'AND split_part(name,'/',2)=auth.uid()::text AND public.xr_clan_media_member(name));
DROP POLICY IF EXISTS xr_room_realtime_read_members ON public.squad_room_messages;
CREATE POLICY xr_room_realtime_read_members ON public.squad_room_messages FOR SELECT TO authenticated USING(public.xr_sf_is_member(squad_id)AND created_at>now()-interval '14 days');
CREATE OR REPLACE FUNCTION public.room_info()RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE uid uuid:=auth.uid();sid uuid;nm text;tg text;members integer;
BEGIN
 SELECT m.squad_id INTO sid FROM public.squad_members m WHERE m.user_id=uid LIMIT 1;
 IF uid IS NULL OR sid IS NULL OR NOT public.xr_sf_is_member(sid)THEN RETURN jsonb_build_object('ok',false,'error','no_squad');END IF;
 SELECT name,tag INTO nm,tg FROM public.squads WHERE id=sid;
 SELECT count(*)INTO members FROM public.squad_members WHERE squad_id=sid;
 RETURN jsonb_build_object('ok',true,'squad',sid,'name',nm,'tag',tg,'members',members,'retention_days',14);
END$$;
CREATE OR REPLACE FUNCTION public.room_post(p_body text)RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE uid uuid:=auth.uid();sid uuid;txt text:=trim(coalesce(p_body,''));mid bigint;
BEGIN
 SELECT m.squad_id INTO sid FROM public.squad_members m WHERE m.user_id=uid LIMIT 1;
 IF uid IS NULL OR sid IS NULL OR NOT public.xr_sf_is_member(sid)THEN RETURN jsonb_build_object('ok',false,'error','Join a squad first.');END IF;
 IF length(txt)<1 OR length(txt)>300 THEN RETURN jsonb_build_object('ok',false,'error','Message must be 1-300 characters.');END IF;
 INSERT INTO public.squad_room_messages(squad_id,user_id,body)VALUES(sid,uid,txt)RETURNING id INTO mid;
 RETURN jsonb_build_object('ok',true,'id',mid);
END$$;
CREATE OR REPLACE FUNCTION public.room_post_media(p_kind text,p_media_path text,p_body text DEFAULT '',p_duration_ms integer DEFAULT NULL)RETURNS jsonb
 LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE uid uuid:=auth.uid();sid uuid;txt text:=trim(coalesce(p_body,''));mid bigint;mime text;
BEGIN
 SELECT m.squad_id INTO sid FROM public.squad_members m WHERE m.user_id=uid LIMIT 1;
 IF uid IS NULL OR sid IS NULL OR NOT public.xr_sf_is_member(sid)THEN RETURN jsonb_build_object('ok',false,'error','Join a squad first.');END IF;
 IF NOT coalesce(public.xr_clan_media_member(p_media_path),false)OR split_part(p_media_path,'/',1)<>sid::text OR split_part(p_media_path,'/',2)<>uid::text THEN RETURN jsonb_build_object('ok',false,'error','Invalid media path.');END IF;
 IF p_kind IS NULL OR p_kind NOT IN('image','voice')OR length(txt)>300 OR (p_kind='voice'AND(p_duration_ms IS NULL OR p_duration_ms NOT BETWEEN 1 AND 120000))OR(p_kind='image'AND p_duration_ms IS NOT NULL)THEN RETURN jsonb_build_object('ok',false,'error','Invalid media type, caption or duration.');END IF;
 SELECT metadata->>'mimetype'INTO mime FROM storage.objects WHERE bucket_id='clan-media'AND name=p_media_path AND created_at>now()-interval '14 days';
 IF mime IS NULL OR(p_kind='image'AND mime NOT IN('image/jpeg','image/png','image/webp'))OR(p_kind='voice'AND mime NOT IN('audio/mp4','audio/x-m4a','audio/aac','audio/ogg','audio/webm'))THEN RETURN jsonb_build_object('ok',false,'error','Upload valid media first.');END IF;
 SELECT id INTO mid FROM public.squad_room_messages WHERE media_path=p_media_path AND user_id=uid AND squad_id=sid AND created_at>now()-interval '14 days';
 IF mid IS NOT NULL THEN RETURN jsonb_build_object('ok',true,'id',mid,'duplicate',true);END IF;
 INSERT INTO public.squad_room_messages(squad_id,user_id,body,kind,media_path,duration_ms)
 VALUES(sid,uid,coalesce(nullif(txt,''),CASE WHEN p_kind='image'THEN '[Image]'ELSE '[Voice note]'END),p_kind,p_media_path,p_duration_ms)RETURNING id INTO mid;
 RETURN jsonb_build_object('ok',true,'id',mid);
EXCEPTION WHEN unique_violation THEN
 SELECT id INTO mid FROM public.squad_room_messages WHERE media_path=p_media_path AND user_id=uid AND squad_id=sid;
 IF mid IS NULL THEN RETURN jsonb_build_object('ok',false,'error','Media already used.');END IF;
 RETURN jsonb_build_object('ok',true,'id',mid,'duplicate',true);
END$$;
CREATE OR REPLACE FUNCTION public.room_feed(p_after bigint DEFAULT 0)RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE uid uuid:=auth.uid();sid uuid;
BEGIN
 SELECT m.squad_id INTO sid FROM public.squad_members m WHERE m.user_id=uid LIMIT 1;
 IF uid IS NULL OR sid IS NULL OR NOT public.xr_sf_is_member(sid)THEN RETURN jsonb_build_object('ok',false,'error','no_squad');END IF;
 RETURN jsonb_build_object('ok',true,'squad',sid,'retention_days',14,'rows',coalesce((SELECT jsonb_agg(t)FROM(
 SELECT m.id,m.body,m.created_at,m.user_id,coalesce(pr.ign,pr.username,'player')AS by,m.kind,m.media_path,m.duration_ms,m.created_at+interval '14 days'AS expires_at
 FROM public.squad_room_messages m LEFT JOIN public.profiles pr ON pr.id=m.user_id
 WHERE m.squad_id=sid AND m.id>p_after AND m.created_at>now()-interval '14 days'ORDER BY m.id LIMIT 100)t),'[]'::jsonb));
END$$;
REVOKE ALL ON FUNCTION public.room_info(),public.room_feed(bigint),public.room_post(text),public.room_post_media(text,text,text,integer)FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.room_info(),public.room_feed(bigint),public.room_post(text),public.room_post_media(text,text,text,integer)TO authenticated;
CREATE TABLE public.xr_clan_cleanup_runs(id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,created_at timestamptz NOT NULL DEFAULT now(),messages_deleted integer NOT NULL,objects_requested integer NOT NULL,storage_request_id bigint);
ALTER TABLE public.xr_clan_cleanup_runs ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.xr_clan_cleanup_runs FROM PUBLIC,anon,authenticated;
CREATE OR REPLACE FUNCTION public.xr_clan_cleanup_tick()RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE deleted integer;paths jsonb;rid bigint;key text;
BEGIN
 IF NOT pg_try_advisory_xact_lock(hashtext('xr_clan_cleanup'))THEN RETURN jsonb_build_object('ok',false,'busy',true);END IF;
 DELETE FROM public.squad_room_messages WHERE created_at<=now()-interval '14 days';GET DIAGNOSTICS deleted=ROW_COUNT;
 SELECT coalesce(jsonb_agg(name),'[]'::jsonb)INTO paths FROM(SELECT name FROM storage.objects WHERE bucket_id='clan-media'AND created_at<=now()-interval '14 days'ORDER BY created_at LIMIT 500)t;
 IF jsonb_array_length(paths)>0 THEN
  SELECT decrypted_secret INTO key FROM vault.decrypted_secrets WHERE name='xarena_service_role'LIMIT 1;
  IF key IS NULL THEN RAISE EXCEPTION 'Cleanup service credential unavailable';END IF;
  SELECT net.http_delete(url:='https://oskccuajgbyabogbvedj.supabase.co/storage/v1/object/clan-media',headers:=jsonb_build_object('Authorization','Bearer '||key,'apikey',key,'Content-Type','application/json'),body:=jsonb_build_object('prefixes',paths),timeout_milliseconds:=15000)INTO rid;
 END IF;
 INSERT INTO public.xr_clan_cleanup_runs(messages_deleted,objects_requested,storage_request_id)VALUES(deleted,jsonb_array_length(paths),rid);
 DELETE FROM public.xr_clan_cleanup_runs WHERE created_at<now()-interval '30 days';
 RETURN jsonb_build_object('ok',true,'messages_deleted',deleted,'objects_requested',jsonb_array_length(paths),'storage_request_id',rid);
END$$;
REVOKE ALL ON FUNCTION public.xr_clan_cleanup_tick()FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.xr_clan_cleanup_tick()TO service_role;
SELECT cron.schedule('xarena-clan-retention','23 * * * *','select public.xr_clan_cleanup_tick();');
NOTIFY pgrst,'reload schema';
COMMIT;
