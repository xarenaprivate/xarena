BEGIN;
DO $test$
DECLARE owner_id uuid;player_id uuid;j jsonb;routes text[];r text;d text;m text;critical text;before_critical text;after_critical text;before_posts bigint;
BEGIN
 SELECT user_id INTO owner_id FROM public.admins LIMIT 1;SELECT id INTO player_id FROM public.profiles p WHERE NOT coalesce(banned,false)AND NOT EXISTS(SELECT 1 FROM public.admins WHERE user_id=p.id)LIMIT 1;
 SELECT md5(string_agg(pg_get_functiondef(p.oid),''ORDER BY p.oid))INTO before_critical FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname='public'AND p.prokind='f'AND p.proname IN('xr_live_thread_list','xr_live_thread_post','xr_kill_feed_list','xr_match_chat_send','xr_match_chat_list','check_in','match_center','register_squad');
 SELECT count(*)INTO before_posts FROM public.feed_posts;
 ASSERT NOT(public.xr_ops_status()->>'feed_shed')::boolean,'Dark default';
 PERFORM set_config('request.jwt.claim.sub',player_id::text,true);
 BEGIN PERFORM public.xr_ops_admin('{"feed_shed":true}');RAISE EXCEPTION 'Player switch bypass';EXCEPTION WHEN insufficient_privilege THEN NULL;END;
 PERFORM set_config('request.jwt.claim.sub',owner_id::text,true);
 ASSERT NOT(public.xr_ops_admin('{"retry_after_seconds":1}')->>'ok')::boolean,'Retry bound';
 ASSERT NOT(public.xr_ops_admin('{"live_available":false}')->>'ok')::boolean,'Cannot disable live';
 j:=public.xr_ops_admin('{"feed_shed":true,"clips_shed":true,"stories_shed":true,"retry_after_seconds":10}');ASSERT(j->>'ok')::boolean,'Admin flip';ASSERT(j->>'match_center_available')::boolean AND(j->>'live_available')::boolean,'Critical not disabled';
 FOREACH r IN ARRAY ARRAY['/feed_posts','/rest/v1/feed_posts','/feed_comments','/feed_likes','/feed_saves','/rpc/feed_list','/rpc/feed_saved','/rpc/feed_create','/rpc/weekly_top_clip','/stories','/story_views','/rpc/story_list']LOOP
  PERFORM set_config('request.path',r,true);
  BEGIN PERFORM public.xr_ops_pre_request();RAISE EXCEPTION 'Route not shed: %',r;EXCEPTION WHEN SQLSTATE 'PGRST' THEN GET STACKED DIAGNOSTICS d=PG_EXCEPTION_DETAIL,m=MESSAGE_TEXT;ASSERT(d::jsonb->>'status')::integer=503,'HTTP503';ASSERT d::jsonb->'headers'->>'Retry-After'='10','Retry header';ASSERT m::jsonb->>'code'='XR_BUSY','Busy code';END;
 END LOOP;
 FOREACH r IN ARRAY ARRAY['/rpc/xr_ops_status','/rpc/xr_ops_admin','/matches','/tournaments','/registrations','/rpc/register_squad','/rpc/xr_live_thread_list','/rpc/xr_kill_feed_list','/rpc/xr_match_chat_send','/rpc/room_feed','/rpc/creator_event_room']LOOP PERFORM set_config('request.path',r,true);PERFORM public.xr_ops_pre_request();END LOOP;
 BEGIN PERFORM public.feed_list(NULL);RAISE EXCEPTION 'SQL RPC bypass';EXCEPTION WHEN SQLSTATE 'PGRST' THEN NULL;END;
 BEGIN PERFORM public.feed_create('text',NULL,'Should never write');RAISE EXCEPTION 'Write bypass';EXCEPTION WHEN SQLSTATE 'PGRST' THEN NULL;END;
 BEGIN PERFORM public.story_trays();RAISE EXCEPTION 'Story bypass';EXCEPTION WHEN SQLSTATE 'PGRST' THEN NULL;END;
 ASSERT(SELECT count(*)FROM public.feed_posts)=before_posts,'No test write';
 j:=public.player_profile_v2((SELECT username FROM public.profiles WHERE id=player_id));ASSERT coalesce((j->>'ok')::boolean,false),'Profile stays usable';ASSERT j->'highlights'='[]'::jsonb AND(j->>'highlights_temporarily_unavailable')::boolean,'Embedded heavy query shed explicitly';
 SELECT md5(string_agg(pg_get_functiondef(p.oid),''ORDER BY p.oid))INTO after_critical FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname='public'AND p.prokind='f'AND p.proname IN('xr_live_thread_list','xr_live_thread_post','xr_kill_feed_list','xr_match_chat_send','xr_match_chat_list','check_in','match_center','register_squad');
 ASSERT before_critical=after_critical,'Critical definitions unchanged';
 ASSERT(public.xr_live_thread_list('xr-brlord-nov12',50)->>'ok')::boolean,'Live comments still readable';
 PERFORM public.xr_ops_admin('{"feed_shed":false,"clips_shed":false,"stories_shed":false}');
 PERFORM set_config('request.path','/feed_posts',true);PERFORM set_config('response.headers','[]',true);PERFORM public.xr_ops_pre_request();ASSERT current_setting('response.headers')LIKE '%X-Xarena-Ops-Guard%','Dark hook marks guarded route';
 ASSERT(public.feed_list(NULL)->>'ok')::boolean,'Recovery feed';ASSERT(public.story_trays()->>'ok')::boolean,'Recovery stories';
 -- Clips-only intentionally sheds mixed Feed surfaces to prevent old clients
 -- fetching clips through unfiltered mixed lists.
 PERFORM public.xr_ops_admin('{"clips_shed":true}');BEGIN PERFORM public.feed_list(NULL);RAISE EXCEPTION 'Mixed list bypass';EXCEPTION WHEN SQLSTATE 'PGRST' THEN NULL;END;
 PERFORM public.xr_ops_admin('{"clips_shed":false}');
END $test$;
SET LOCAL ROLE anon;
DO $grant$BEGIN ASSERT has_function_privilege(current_user,'public.xr_ops_status()','EXECUTE'),'Public-safe status readable';ASSERT NOT has_function_privilege(current_user,'public.xr_ops_admin(jsonb)','EXECUTE'),'Anonymous admin denied';ASSERT NOT has_table_privilege(current_user,'public.xr_ops_settings','SELECT,INSERT,UPDATE,DELETE'),'Private settings';END $grant$;
RESET ROLE;
SET LOCAL ROLE authenticated;
DO $grant$BEGIN ASSERT NOT has_table_privilege(current_user,'public.xr_ops_settings','SELECT,INSERT,UPDATE,DELETE'),'Cannot edit raw switch';END $grant$;
RESET ROLE;

SELECT 'ops tests passed; fixtures reverted' result;
ROLLBACK;