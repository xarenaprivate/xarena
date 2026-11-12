BEGIN;
DO $test$
DECLARE owner_id uuid;a uuid;b uuid;app_a bigint;app_b bigint;j jsonb;city text:='__Creator Test';ev uuid;before_profiles jsonb;
BEGIN
 SELECT user_id INTO owner_id FROM public.admins LIMIT 1;
 SELECT id INTO a FROM public.profiles p WHERE NOT coalesce(banned,false)AND NOT EXISTS(SELECT 1 FROM public.admins WHERE user_id=p.id)ORDER BY id LIMIT 1;
 SELECT id INTO b FROM public.profiles p WHERE id<>a AND NOT coalesce(banned,false)AND NOT EXISTS(SELECT 1 FROM public.admins WHERE user_id=p.id)ORDER BY id LIMIT 1;
 ASSERT owner_id IS NOT NULL AND a IS NOT NULL AND b IS NOT NULL,'Fixtures';
 PERFORM set_config('request.jwt.claim.sub','',true);
 BEGIN PERFORM public.xr_creator_city_status(city);RAISE EXCEPTION 'Anon allowed';EXCEPTION WHEN insufficient_privilege THEN NULL;END;
 PERFORM set_config('request.jwt.claim.sub',a::text,true);
 ASSERT public.xr_creator_apply('Test Person',city,'test.creator',2)->>'error'='not_open','Staged';
 BEGIN PERFORM public.xr_creator_settings_admin('{"enabled":true}');RAISE EXCEPTION 'Player settings bypass';EXCEPTION WHEN insufficient_privilege THEN NULL;END;
 PERFORM set_config('request.jwt.claim.sub',owner_id::text,true);
 j:=public.xr_creator_settings_admin('{"enabled":true,"default_city_slots":1,"founding_limit":1,"drop_limit":1}');ASSERT(j->>'ok')::boolean,'Settings';
 ASSERT NOT(public.xr_creator_settings_admin('{"verification_counter":0}')->>'ok')::boolean,'Counter cannot reset';
 ASSERT NOT(public.xr_creator_settings_admin('{"default_city_slots":-1}')->>'ok')::boolean,'Settings checks';
 PERFORM set_config('request.jwt.claim.sub',a::text,true);
 j:=public.xr_creator_apply('Test Person',city,'@test.creator',25,false,false);ASSERT(j->>'ok')::boolean,j::text;app_a:=(j->>'application_id')::bigint;
 ASSERT j->>'stage'='applied','Applied stage';
 ASSERT public.xr_creator_apply('Test Person',city,'test.creator',25)->>'error'='Application already pending.','No duplicate';
 ASSERT jsonb_array_length(public.xr_creator_activity_list()->'events')=0,'Sharing defaults private';
 j:=public.xr_creator_my_status();ASSERT j->>'application_id'=app_a::text,'Own status';ASSERT NOT(j->>'is_creator')::boolean,'Application not a creator';
 PERFORM public.xr_creator_sharing(true,true);ASSERT jsonb_array_length(public.xr_creator_activity_list()->'events')=1,'Explicit feed opt-in';
 j:=public.xr_creator_activity_list();ASSERT j->'events'->0->>'first_name'='Test','Only first name';ASSERT j::text NOT LIKE '%instagram%'AND j::text NOT LIKE '%test.creator%'AND j::text NOT LIKE '%player_count%','Feed privacy';
 PERFORM set_config('request.jwt.claim.sub',owner_id::text,true);
 ASSERT public.xr_creator_review(app_a,'under_review')->>'stage'='under_review','Under review';
 j:=public.xr_creator_review(app_a,'verify');ASSERT(j->>'ok')::boolean,j::text;
 ASSERT(SELECT count(*)FROM public.xr_creator_entitlements WHERE user_id=a AND kind IN('founding_badge','limited_drop'))=2,'Real first badge and drop';
 ASSERT(SELECT status FROM public.xr_creator_entitlements WHERE user_id=a AND kind='limited_drop')='pending_owner_fulfillment','No fake delivered drop';
 PERFORM set_config('request.jwt.claim.sub',b::text,true);
 j:=public.xr_creator_city_status(city);ASSERT(j->>'filled')::integer=1 AND(j->>'remaining')::integer=0,'Real capacity';
 j:=public.xr_creator_apply('Other Person',city,'other.creator',0);ASSERT j->>'stage'='waitlisted','Full city waitlists';app_b:=(j->>'application_id')::bigint;
 ASSERT(public.xr_creator_my_status()->>'waitlist_rank')::integer=1,'Real waitlist rank';
 ASSERT jsonb_array_length(public.xr_creator_friends_applied()->'friends')=0,'No guessed friendships';
 INSERT INTO public.friends(user_id,friend_id)VALUES(b,a),(a,b)ON CONFLICT DO NOTHING;
 ASSERT jsonb_array_length(public.xr_creator_friends_applied()->'friends')=1,'Mutual friend with consent';
 PERFORM set_config('request.jwt.claim.sub',a::text,true);PERFORM public.xr_creator_sharing(false,false);
 PERFORM set_config('request.jwt.claim.sub',b::text,true);ASSERT jsonb_array_length(public.xr_creator_friends_applied()->'friends')=0,'Consent withdrawal immediate';
 PERFORM set_config('request.jwt.claim.sub',owner_id::text,true);
 j:=public.xr_creator_review(app_b,'verify');ASSERT j->>'error'='city_full','Overcapacity blocked';
 ASSERT NOT EXISTS(SELECT 1 FROM public.creators WHERE user_id=b),'Failed review atomic';
 ASSERT(public.xr_creator_city_admin(city,2)->>'ok')::boolean,'Admin expands capacity';
 ASSERT(public.xr_creator_review(app_b,'verify')->>'ok')::boolean,'Review after expansion';
 ASSERT NOT EXISTS(SELECT 1 FROM public.xr_creator_entitlements WHERE user_id=b AND kind IN('founding_badge','limited_drop')),'No fake beyond cap';
 ASSERT(public.xr_creator_review(app_b,'verify')->>'already_reviewed')::boolean,'Idempotent retry';
 ASSERT(SELECT verification_counter FROM public.xr_creator_settings)=2,'Unique verified ordinal';
 -- Existing free event creation remains the canonical tournament source.
 PERFORM set_config('request.jwt.claim.sub',a::text,true);
 j:=public.creator_event_create('Test Free Event','Squad','Bermuda',now()+interval '1 day',12,'Rollback-only test',NULL,NULL);ASSERT(j->>'ok')::boolean,j::text;ev:=(j->>'id')::uuid;
 PERFORM set_config('request.jwt.claim.sub',b::text,true);ASSERT(public.creator_event_join(ev)->>'ok')::boolean,'Real join';
 j:=public.xr_creator_tournament_seats(a);ASSERT(j->'tournaments'->0->>'registered')::integer=1 AND(j->'tournaments'->0->>'capacity')::integer=12 AND(j->'tournaments'->0->>'remaining')::integer=11,'Seat counters';
 ASSERT j::text NOT LIKE '%room_pass%'AND j::text NOT LIKE '%phone%'AND j::text NOT LIKE '%room_id%','Seat privacy';
 j:=public.xr_creator_city_leaderboard(city);ASSERT j->'creator_of_week'->>'id'=a::text AND(j->'creator_of_week'->>'event_joins')::integer=1,'Week actual joins';
 ASSERT(j->'creators'->0->>'event_joins')::integer=1,'City ranking';
 PERFORM set_config('request.jwt.claim.sub',owner_id::text,true);
 j:=public.xr_creator_settings_admin(jsonb_build_object('application_deadline',now()-interval '1 second'));ASSERT(j->>'ok')::boolean,'Deadline';
 ASSERT(public.xr_creator_city_status(city)->>'waitlist_only')::boolean,'Deadline closes normal applications';
 -- Revoking and regranting does not recycle the first N or drop grants.
 PERFORM public.admin_creator_revoke(a);
 ASSERT(public.admin_creator_grant((SELECT username FROM public.profiles WHERE id=a),0,NULL)->>'ok')::boolean,'Regrant existing creator';
 ASSERT(SELECT verification_counter FROM public.xr_creator_settings)=2,'No new ordinal on regrant';
 ASSERT(SELECT count(*)FROM public.xr_creator_entitlements WHERE user_id=a AND kind='founding_badge')=1,'Lifetime founding survives revocation';
 -- Actual mutual-friend and feed consent stay filtered under blocks.
 PERFORM set_config('request.jwt.claim.sub',a::text,true);PERFORM public.xr_creator_sharing(true,true);
 PERFORM set_config('request.jwt.claim.sub',b::text,true);
 INSERT INTO public.blocks(user_id,blocked_id)VALUES(b,a)ON CONFLICT DO NOTHING;
 ASSERT jsonb_array_length(public.xr_creator_friends_applied()->'friends')=0,'Blocked friends private';
 ASSERT NOT EXISTS(SELECT 1 FROM jsonb_array_elements(public.xr_creator_activity_list()->'events')x WHERE x->>'first_name'='Test'),'Blocked feed private';
 DELETE FROM public.blocks WHERE user_id=b AND blocked_id=a;
 PERFORM set_config('request.jwt.claim.sub',owner_id::text,true);
 -- No bonus allocation without an explicitly configured campaign.
 ASSERT NOT EXISTS(SELECT 1 FROM public.xr_creator_entitlements WHERE kind='early_bonus'),'Default no money promise';
 -- Bonus records are eligibility only, not cash/points credits. Rollback fixture.
 DELETE FROM public.creators WHERE user_id=b;
 UPDATE public.creator_applications SET status='rejected'WHERE user_id=b;
 j:=public.xr_creator_settings_admin(jsonb_build_object('bonus_enabled',true,'bonus_starts_at',now()-interval '1 day','bonus_ends_at',now()+interval '1 day','bonus_inr',500,'bonus_coins',1000,'bonus_funding_confirmed',false));ASSERT(j->>'ok')::boolean,'Configured pending funding bonus';
 PERFORM set_config('request.jwt.claim.sub',b::text,true);
 j:=public.xr_creator_apply('Bonus Person','__Other City','bonus.creator',2);ASSERT j->>'stage'='waitlisted','Deadline creates honest waitlist';
 ASSERT(SELECT status FROM public.xr_creator_entitlements WHERE user_id=b AND kind='early_bonus')='pending_funding','No unfunded payout claim';
 ASSERT public.xr_creator_city_status('__Other City')->'bonus'='null'::jsonb,'No unfunded public offer';
 ASSERT public.xr_creator_dashboard()->>'error'='Creators only.','Dashboard creator gate';
 PERFORM set_config('request.jwt.claim.sub',owner_id::text,true);
 UPDATE public.xr_creator_settings SET bonus_enabled=false;

END $test$;
SET LOCAL ROLE anon;
DO $grants$ BEGIN ASSERT NOT has_function_privilege(current_user,'public.xr_creator_apply(text,text,text,integer,boolean,boolean)','EXECUTE'),'Anonymous apply denied';END $grants$;
RESET ROLE;
SET LOCAL ROLE authenticated;
DO $grants$ BEGIN ASSERT NOT has_table_privilege(current_user,'public.xr_creator_application_details','SELECT,INSERT,UPDATE,DELETE'),'Private application protected';ASSERT NOT has_table_privilege(current_user,'public.xr_creator_entitlements','SELECT,INSERT,UPDATE,DELETE'),'Private entitlements protected';ASSERT has_function_privilege(current_user,'public.xr_creator_city_status(text)','EXECUTE'),'City endpoint available';END $grants$;
RESET ROLE;

SELECT 'creator tests passed; fixtures reverted' result;
ROLLBACK;