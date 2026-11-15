DO $$
DECLARE uid uuid;sid uuid;outsider uuid;path text;r jsonb;oldid bigint;newid bigint;visible integer;
BEGIN
 SELECT m.user_id,m.squad_id INTO uid,sid FROM public.squad_members m JOIN public.profiles p ON p.id=m.user_id WHERE NOT coalesce(p.banned,false)LIMIT 1;
 SELECT p.id INTO outsider FROM public.profiles p WHERE p.id<>uid AND NOT coalesce(p.banned,false)AND NOT EXISTS(SELECT 1 FROM public.squad_members m WHERE m.user_id=p.id AND m.squad_id=sid)LIMIT 1;
 IF uid IS NULL OR outsider IS NULL THEN RAISE EXCEPTION 'Test identities unavailable';END IF;
 path:=sid::text||'/'||uid::text||'/'||gen_random_uuid()::text||'.jpg';
 INSERT INTO storage.objects(bucket_id,name,metadata)VALUES('clan-media',path,'{"mimetype":"image/jpeg","size":100}'::jsonb);
 INSERT INTO public.squad_room_messages(squad_id,user_id,body,created_at)VALUES(sid,uid,'rollback old fixture',now()-interval '15 days')RETURNING id INTO oldid;
 PERFORM set_config('request.jwt.claim.sub',uid::text,true);SET LOCAL ROLE authenticated;
 r:=public.room_post_media('image',path,'rollback image fixture',NULL);
 IF NOT(r->>'ok')::boolean THEN RAISE EXCEPTION 'Member media rejected %',r;END IF;newid:=(r->>'id')::bigint;
 r:=public.room_feed(0);IF NOT(r->>'ok')::boolean THEN RAISE EXCEPTION 'Feed failed';END IF;
 IF EXISTS(SELECT 1 FROM jsonb_array_elements(r->'rows')v WHERE(v->>'id')::bigint=oldid)THEN RAISE EXCEPTION 'Expired row in feed';END IF;
 IF NOT EXISTS(SELECT 1 FROM jsonb_array_elements(r->'rows')v WHERE(v->>'id')::bigint=newid AND v->>'kind'='image'AND v->>'media_path'=path AND v ? 'expires_at')THEN RAISE EXCEPTION 'Image fields missing';END IF;
 r:=public.room_post_media('image',path,'retry',NULL);IF NOT coalesce((r->>'duplicate')::boolean,false)THEN RAISE EXCEPTION 'Duplicate media not idempotent';END IF;
 r:=public.room_post_media('voice',path,'wrong mime',1000);IF(r->>'ok')::boolean THEN RAISE EXCEPTION 'Wrong MIME accepted';END IF;
 SELECT count(*)INTO visible FROM storage.objects WHERE bucket_id='clan-media'AND name=path;IF visible<>1 THEN RAISE EXCEPTION 'Uploader cannot read';END IF;
 SELECT count(*)INTO visible FROM public.squad_room_messages WHERE id=oldid;IF visible<>0 THEN RAISE EXCEPTION 'Expired row visible via RLS';END IF;
 RESET ROLE;PERFORM set_config('request.jwt.claim.sub',outsider::text,true);SET LOCAL ROLE authenticated;
 SELECT count(*)INTO visible FROM storage.objects WHERE bucket_id='clan-media'AND name=path;IF visible<>0 THEN RAISE EXCEPTION 'Outsider storage leak';END IF;
 SELECT count(*)INTO visible FROM public.squad_room_messages WHERE id=newid;IF visible<>0 THEN RAISE EXCEPTION 'Outsider message leak';END IF;
 BEGIN INSERT INTO storage.objects(bucket_id,name,metadata)VALUES('clan-media',sid::text||'/'||outsider::text||'/'||gen_random_uuid()::text||'.jpg','{}');RAISE EXCEPTION 'Outsider upload accepted';EXCEPTION WHEN insufficient_privilege THEN NULL;END;
 RESET ROLE;PERFORM set_config('request.jwt.claim.sub','',true);SET LOCAL ROLE anon;
 SELECT count(*)INTO visible FROM storage.objects WHERE bucket_id='clan-media'AND name=path;IF visible<>0 THEN RAISE EXCEPTION 'Anonymous storage leak';END IF;
 BEGIN PERFORM public.room_post_media('image',path,'',NULL);RAISE EXCEPTION 'Anonymous RPC allowed';EXCEPTION WHEN insufficient_privilege THEN NULL;END;
 RESET ROLE;
END$$;
SELECT 'PASS member upload/post/read + idempotency; outsider/anonymous denied; expired feed/RLS hidden'AS result;
