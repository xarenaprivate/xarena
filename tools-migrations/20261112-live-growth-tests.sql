BEGIN;
DO $test$
DECLARE own uuid;a uuid;b uuid;j jsonb;pollid uuid;tid text:='__live_'||gen_random_uuid()::text;rid text:='__lr_'||gen_random_uuid()::text;points_before bigint;chat_before bigint;
BEGIN
 SELECT user_id INTO own FROM public.admins LIMIT 1;SELECT id INTO a FROM public.profiles WHERE id<>own AND NOT coalesce(banned,false)ORDER BY id LIMIT 1;SELECT id INTO b FROM public.profiles WHERE id NOT IN(own,a)AND NOT coalesce(banned,false)ORDER BY id LIMIT 1;
 SELECT coalesce(sum(delta),0)INTO points_before FROM public.points_ledger;SELECT count(*)INTO chat_before FROM public.live_tournament_posts;
 PERFORM set_config('request.jwt.claim.sub','',true);j:=public.xr_live_overview();ASSERT NOT(j->>'growth_enabled')::boolean,'Dark';ASSERT j->'viewer_count'='null'::jsonb,'No viewer fiction';ASSERT j->'prediction'='null'::jsonb AND j->'drop'='null'::jsonb AND j->'watch_streak'='null'::jsonb AND j->'early_viewer'='null'::jsonb,'Unsupported absent';ASSERT NOT(j->>'notification_delivery_available')::boolean,'No reminder';
 PERFORM set_config('request.jwt.claim.sub',a::text,true);BEGIN PERFORM public.xr_live_growth_admin('{"enabled":true}');RAISE EXCEPTION 'Player admin bypass';EXCEPTION WHEN insufficient_privilege THEN NULL;END;
 PERFORM set_config('request.jwt.claim.sub',own::text,true);
 INSERT INTO public.tournaments(id,name,status,reg_open,slots,entry_cost_ap)VALUES(tid,'Fixture live','live',true,12,0);
 INSERT INTO public.registrations(id,tournament_id,squad,captain,uid,phone,status,ign,user_id,hold)VALUES(rid,tid,'Fixture Squad','Captain','86753090999','+910000000041','confirmed','Fixture IGL',a,false);
 INSERT INTO public.xr_registration_players(registration_id,tournament_id,player_no,ign,uid)VALUES(rid,tid,1,'Fixture IGL','86753090999'),(rid,tid,2,'Fixture Teammate','86753090998');
 UPDATE public.site_settings SET value=jsonb_build_object('video_id','abcdefghijk','active',true,'title','Fixture stream','tournament_id',tid)WHERE key='youtube_live';
 ASSERT NOT(public.xr_live_growth_admin('{"enabled":true,"video_id":"wrongvideo1"}')->>'ok')::boolean,'Wrong video bind rejected';
 j:=public.xr_live_growth_admin(jsonb_build_object('enabled',true,'video_id','abcdefghijk','host_user_id',b,'scheduled_start_at',now()+interval '1 hour','welcome_body','Fixture welcome','welcome_published',true));ASSERT(j->>'ok')::boolean,'Fixture enabled';
 PERFORM set_config('request.jwt.claim.sub',a::text,true);j:=public.xr_live_overview();ASSERT(j->>'growth_enabled')::boolean,'Bound enabled';ASSERT j->'stream'->'scheduled_start_at'<>'null'::jsonb,'Real configured start';ASSERT jsonb_array_length(j->'squads')>=1,'Canonical squad';ASSERT jsonb_array_length(j->'squads'->0->'members')=2,'Actual teammate names';ASSERT(j::text NOT LIKE '%86753090%'AND j::text NOT LIKE '%+910000%'),'No UID phone';ASSERT NOT(j->'gifts'->>'enabled')::boolean,'Gifts disabled';
 j:=public.xr_live_follow_set(true);ASSERT(j->>'ok')::boolean,'Follow works';PERFORM public.xr_live_follow_set(true);ASSERT(SELECT count(*)FROM public.follows WHERE follower_id=a AND followee_id=b)=1,'Follow idempotent';ASSERT(public.xr_live_overview()->'follow'->>'following')::boolean,'Read following';PERFORM public.xr_live_follow_set(false);ASSERT NOT(public.xr_live_overview()->'follow'->>'following')::boolean,'Unfollow read';
 PERFORM set_config('request.jwt.claim.sub',b::text,true);ASSERT NOT(public.xr_live_follow_set(true)->>'ok')::boolean,'Self follow denied';
 PERFORM set_config('request.jwt.claim.sub',own::text,true);ASSERT NOT(public.xr_live_poll_admin('Pick map','["Same","same"]',now()+interval '1 hour')->>'ok')::boolean,'Duplicate choices rejected';
 j:=public.xr_live_poll_admin('Pick map','["Bermuda","Purgatory"]',now()+interval '1 hour');ASSERT(j->>'ok')::boolean,'Real admin poll';pollid:=(j->>'poll_id')::uuid;
 PERFORM set_config('request.jwt.claim.sub',a::text,true);ASSERT(public.xr_live_poll_vote(pollid,0)->>'ok')::boolean,'Vote works';ASSERT(public.xr_live_poll_vote(pollid,1)->>'already_voted')::boolean,'Vote idempotent';ASSERT NOT(public.xr_live_poll_vote(pollid,8)->>'ok')::boolean,'Invalid choice denied';j:=public.xr_live_overview();ASSERT(j->'poll'->'counts'->>0)::int=1,'Real vote count';ASSERT(j->'poll'->>'my_vote')::int=0,'Own vote';
 PERFORM set_config('request.jwt.claim.sub',own::text,true);UPDATE public.xr_live_polls SET closes_at=now()-interval '1 minute'WHERE id=pollid;PERFORM set_config('request.jwt.claim.sub',a::text,true);ASSERT NOT(public.xr_live_poll_vote(pollid,1)->>'ok')::boolean,'Closed poll denied';
 INSERT INTO public.blocks(user_id,blocked_id)VALUES(a,b)ON CONFLICT DO NOTHING;ASSERT public.xr_live_overview()->'follow'='null'::jsonb,'Blocked host hidden';ASSERT NOT(public.xr_live_follow_set(true)->>'ok')::boolean,'Blocked follow denied';
 PERFORM set_config('request.jwt.claim.sub',own::text,true);UPDATE public.site_settings SET value=jsonb_set(value,'{video_id}','"lmnopqrstuv"')WHERE key='youtube_live';j:=public.xr_live_overview();ASSERT NOT(j->>'growth_enabled')::boolean,'Changed video disables growth';ASSERT j->'poll'='null'::jsonb AND j->'welcome_pin'='null'::jsonb,'No stale poll/pin';ASSERT j->'stream'->'scheduled_start_at'='null'::jsonb,'No stale timer';
 UPDATE public.profiles SET banned=true WHERE id=a;PERFORM set_config('request.jwt.claim.sub',a::text,true);BEGIN PERFORM public.xr_live_overview();RAISE EXCEPTION 'Banned overview allowed';EXCEPTION WHEN insufficient_privilege THEN NULL;END;
 ASSERT(SELECT coalesce(sum(delta),0)FROM public.points_ledger)=points_before,'No points minted/spent';ASSERT(SELECT count(*)FROM public.live_tournament_posts)=chat_before,'No thread fiction';
END $test$;

SELECT 'live growth tests passed; fixtures reverted' result;
ROLLBACK;