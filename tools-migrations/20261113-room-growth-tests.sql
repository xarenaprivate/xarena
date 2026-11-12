BEGIN;
DO $test$
DECLARE own uuid;a uuid;b uuid;sid uuid;other uuid;j jsonb;tid text:='__room_'||gen_random_uuid()::text;rid text:='__rr_'||gen_random_uuid()::text;mid uuid:=gen_random_uuid();points_before bigint;
BEGIN
 SELECT user_id INTO own FROM public.admins LIMIT 1;SELECT id INTO a FROM public.profiles WHERE id<>own AND NOT coalesce(banned,false)ORDER BY id LIMIT 1;SELECT id INTO b FROM public.profiles WHERE id NOT IN(own,a)AND NOT coalesce(banned,false)ORDER BY id LIMIT 1;SELECT coalesce(sum(delta),0)INTO points_before FROM public.points_ledger;
 PERFORM set_config('request.jwt.claim.sub','',true);BEGIN PERFORM public.xr_room_growth();RAISE EXCEPTION 'Anonymous allowed';EXCEPTION WHEN insufficient_privilege THEN NULL;END;
 DELETE FROM public.squad_members WHERE user_id IN(a,b);PERFORM set_config('request.jwt.claim.sub',a::text,true);ASSERT public.xr_room_growth()->>'error'='no_squad','No squad fails closed';
 PERFORM set_config('request.jwt.claim.sub',own::text,true);INSERT INTO public.squads(name,tag,captain)VALUES('Fixture '||left(gen_random_uuid()::text,12),'TEST',a)RETURNING id INTO sid;INSERT INTO public.squad_members(squad_id,user_id,role)VALUES(sid,a,'captain'),(sid,b,'member');
 PERFORM set_config('request.jwt.claim.sub',a::text,true);j:=public.xr_room_growth();ASSERT NOT(j->>'growth_enabled')::boolean,'Dark';ASSERT(j->'squad'->>'max_members')::integer=6,'Actual default6 not fake4';ASSERT jsonb_array_length(j->'squad'->'members')=2,'Actual roster';ASSERT j->'activity'='null'::jsonb,'Activity dark';BEGIN PERFORM public.xr_room_growth_admin('{"enabled":true}');RAISE EXCEPTION 'Player admin bypass';EXCEPTION WHEN insufficient_privilege THEN NULL;END;
 PERFORM set_config('request.jwt.claim.sub',own::text,true);PERFORM public.xr_room_growth_admin('{"enabled":true,"auto_break_requested":true}');
 INSERT INTO public.squad_room_messages(squad_id,user_id,body,created_at)VALUES(sid,a,'Fixture today',now()),(sid,b,'Fixture yesterday',now()-interval '1 day');
 INSERT INTO public.xr_sf_strategy_plans(squad_id,body,updated_by)VALUES(sid,'Fixture real plan',a);
 INSERT INTO public.tournaments(id,name,status,reg_open,slots,entry_cost_ap)VALUES(tid,'Fixture tournament','upcoming',true,12,0);
 INSERT INTO public.matches(id,tournament_id,title,status,starts_at)VALUES(mid,tid,'Fixture next match','scheduled',now()+interval '1 hour');
 INSERT INTO public.registrations(id,tournament_id,squad,captain,uid,phone,status,ign,user_id,hold)VALUES(rid,tid,'Different registered name','Captain','86753090001','+910000000051','confirmed','Fixture',a,false);
 INSERT INTO public.xr_match_access_settings(match_id,enabled)VALUES(mid,true);
 INSERT INTO public.xr_sf_join_requests(squad_id,user_id,status,expires_at)VALUES(sid,own,'pending',now()+interval '1 hour');
 INSERT INTO public.squads(name,tag,captain)VALUES('Opponent '||left(gen_random_uuid()::text,12),'OPPO',own)RETURNING id INTO other;
 INSERT INTO public.squad_wars(challenger_id,opponent_id,status,scheduled_at)VALUES(sid,other,'scheduled',now()+interval '2 hours');
 PERFORM set_config('request.jwt.claim.sub',a::text,true);j:=public.xr_room_growth();ASSERT(j->>'pending_join_requests')::int=1,'Actual pending count';ASSERT j->'rivalry'->>'status'='scheduled','Actual id-bound rivalry';ASSERT j->'rivalry'->'ready_states'='null'::jsonb,'No invented war readiness';ASSERT(j->'activity'->>'messages_today')::integer=1,'Real messages today';ASSERT(j->'activity'->>'streak_days')::integer=2,'Real two-day room activity';ASSERT j->'pin'->>'body'='Fixture real plan','Actual plan';ASSERT(j->'next_match'->>'match_id')::uuid=mid,'Account-bound next registration';ASSERT j->'next_match'->'per_member_checkins'='null'::jsonb,'No per-member fiction';ASSERT j->'next_match'->'checkin_closes_at'<>'null'::jsonb,'Real configured window';ASSERT NOT(j->>'auto_break_enabled')::boolean AND j->'auto_break_deadline'='null'::jsonb,'No dissolution behavior';ASSERT(j::text NOT LIKE '%86753090001%'AND j::text NOT LIKE '%+910000000051%'),'Private identifiers absent';
 PERFORM set_config('request.jwt.claim.sub',b::text,true);ASSERT public.xr_room_growth()->'pending_join_requests'='null'::jsonb,'Captain-only requests';
 INSERT INTO public.blocks(user_id,blocked_id)VALUES(b,a)ON CONFLICT DO NOTHING;ASSERT public.xr_room_growth()->>'error'='Squad unavailable.','Blocked captain access denied';
 PERFORM set_config('request.jwt.claim.sub',own::text,true);UPDATE public.profiles SET banned=true WHERE id=b;PERFORM set_config('request.jwt.claim.sub',b::text,true);BEGIN PERFORM public.xr_room_growth();RAISE EXCEPTION 'Banned allowed';EXCEPTION WHEN insufficient_privilege THEN NULL;END;
 ASSERT(SELECT coalesce(sum(delta),0)FROM public.points_ledger)=points_before,'No coin grant';
END $test$;

SELECT 'room growth tests passed; fixtures reverted' result;ROLLBACK;