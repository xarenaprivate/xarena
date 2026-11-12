BEGIN;
DO $test$
DECLARE a uuid;b uuid;j jsonb;t timestamptz;before_other jsonb;before_fields jsonb;u_name text;opens timestamptz;
BEGIN
 SELECT id INTO a FROM public.profiles p WHERE NOT coalesce(banned,false) AND NOT EXISTS(SELECT 1 FROM public.admins WHERE user_id=p.id) ORDER BY id LIMIT 1;
 SELECT id INTO b FROM public.profiles p WHERE id<>a AND NOT coalesce(banned,false) AND NOT EXISTS(SELECT 1 FROM public.admins WHERE user_id=p.id) ORDER BY id LIMIT 1;
 ASSERT a IS NOT NULL AND b IS NOT NULL,'Rollback fixtures available';
 PERFORM set_config('request.jwt.claim.sub','',true);
 ASSERT public.xr_profile_identity_update('Test','123456789')->>'error'='unauthorized','No auth update denied';
 ASSERT public.xr_profile_identity_status()->>'error'='unauthorized','No auth status denied';
 UPDATE public.profiles SET ign='Before',ff_uid='123450001',verified=true WHERE id=a;
 SELECT to_jsonb(p)-'ign'-'ff_uid'-'verified',username INTO before_fields,u_name FROM public.profiles p WHERE id=a;
 SELECT to_jsonb(p) INTO before_other FROM public.profiles p WHERE id=b;
 PERFORM set_config('request.jwt.claim.sub',a::text,true);
 j:=public.xr_profile_identity_status();ASSERT (j->>'ok')::boolean AND NOT(j->>'available')::boolean,'Future gate status';
 ASSERT public.xr_profile_identity_update('After','123450002')->>'error'='not_open','Future endpoint gate';
 j:=public.complete_profile(u_name,'After','123450002');ASSERT NOT(j->>'ok')::boolean,'Legacy future bypass denied';
 BEGIN UPDATE public.profiles SET ign='Gate bypass' WHERE id=a;RAISE EXCEPTION 'Direct gate bypass';EXCEPTION WHEN SQLSTATE 'P1404' THEN NULL;END;
 UPDATE public.xr_profile_identity_config SET opens_at=clock_timestamp()-interval '1 hour';
 ASSERT public.xr_profile_identity_update(NULL,'123450002')->>'error'='invalid_ign','Null IGN';
 ASSERT public.xr_profile_identity_update('','123450002')->>'error'='invalid_ign','Empty IGN';
 ASSERT public.xr_profile_identity_update(repeat('x',41),'123450002')->>'error'='invalid_ign','Long IGN';
 ASSERT public.xr_profile_identity_update('<script>','123450002')->>'error'='invalid_ign','Markup IGN';
 ASSERT public.xr_profile_identity_update(E'bad\nname','123450002')->>'error'='invalid_ign','Control IGN';
 ASSERT public.xr_profile_identity_update('Hidden'||chr(8238),'123450002')->>'error'='invalid_ign','Bidi IGN';
 ASSERT public.xr_profile_identity_update('Okay',NULL)->>'error'='invalid_uid','Null UID';
 ASSERT public.xr_profile_identity_update('Okay','x1234')->>'error'='invalid_uid','Nondigit UID';
 ASSERT public.xr_profile_identity_update('Okay','1234')->>'error'='invalid_uid','Short UID';
 ASSERT public.xr_profile_identity_update('Okay',repeat('1',16))->>'error'='invalid_uid','Long UID';
 ASSERT NOT EXISTS(SELECT 1 FROM public.xr_profile_identity_cooldowns WHERE user_id=a),'Invalid input never consumes timer';
 j:=public.xr_profile_identity_update(' After★ ',' 123450002 ');ASSERT(j->>'ok')::boolean AND(j->>'changed')::boolean,'First update succeeds';
 ASSERT(j->>'verification_reset')::boolean,'Changed UID reverifies';
 SELECT changed_at INTO t FROM public.xr_profile_identity_cooldowns WHERE user_id=a;
 ASSERT(j->>'next_allowed_at')::timestamptz=t+interval '336 hours','Exact rolling 14 days';
 ASSERT(SELECT ign='After★' AND ff_uid='123450002' AND NOT verified FROM public.profiles WHERE id=a),'Values saved and verified cleared';
 ASSERT(SELECT to_jsonb(p)-'ign'-'ff_uid'-'verified' FROM public.profiles p WHERE id=a)=before_fields,'Other fields preserved';
 ASSERT(SELECT to_jsonb(p) FROM public.profiles p WHERE id=b)=before_other,'Other account untouched';
 ASSERT j::text NOT LIKE '%123450002%' AND j::text NOT LIKE '%phone%' AND j::text NOT LIKE '%device%','No identity in output';
 j:=public.xr_profile_identity_update('After★','123450002');ASSERT(j->>'ok')::boolean AND NOT(j->>'changed')::boolean,'Retry is no-op during cooldown';
 ASSERT(SELECT changed_at FROM public.xr_profile_identity_cooldowns WHERE user_id=a)=t,'Retry preserves timer';
 j:=public.xr_profile_identity_update('Again','123450003');ASSERT j->>'error'='cooldown' AND (j->>'next_allowed_at')::timestamptz=t+interval '336 hours','Early endpoint rejected honestly';
 j:=public.complete_profile(u_name,'Again','123450003');ASSERT j->>'code'='cooldown','Legacy bypass closed';
 BEGIN UPDATE public.profiles SET ign='Direct bypass' WHERE id=a;RAISE EXCEPTION 'Direct cooldown bypass';EXCEPTION WHEN SQLSTATE 'P1414' THEN ASSERT SQLERRM LIKE '%Next change:%','Direct error has next time';END;
 BEGIN UPDATE public.profiles SET ign=NULL,ff_uid=NULL WHERE id=a;RAISE EXCEPTION 'Blank reset bypass';EXCEPTION WHEN SQLSTATE 'P1400' THEN NULL;END;
 UPDATE public.xr_profile_identity_cooldowns SET changed_at=clock_timestamp()-interval '336 hours'+interval '1 minute' WHERE user_id=a;
 ASSERT public.xr_profile_identity_update('Soon','123450003')->>'error'='cooldown','Before exact boundary denied';
 UPDATE public.xr_profile_identity_cooldowns SET changed_at=clock_timestamp()-interval '336 hours' WHERE user_id=a;
 PERFORM set_config('request.jwt.claim.sub','',true);UPDATE public.profiles SET verified=true WHERE id=a;PERFORM set_config('request.jwt.claim.sub',a::text,true);
 j:=public.xr_profile_identity_update('IGN Only','123450002');ASSERT(j->>'ok')::boolean AND NOT(j->>'verification_reset')::boolean,'IGN-only after boundary succeeds';
 ASSERT(SELECT verified FROM public.profiles WHERE id=a),'IGN-only retains verification';
 -- Exact same table guard covers direct/legacy writes after a timer expires.
 UPDATE public.xr_profile_identity_cooldowns SET changed_at=clock_timestamp()-interval '337 hours' WHERE user_id=a;
 UPDATE public.profiles SET ign='Direct valid' WHERE id=a;
 ASSERT(SELECT changed_at>clock_timestamp()-interval '10 seconds' FROM public.xr_profile_identity_cooldowns WHERE user_id=a),'Direct edit starts timer';
 PERFORM set_config('request.jwt.claim.sub','',true);UPDATE public.profiles SET banned=true WHERE id=a;PERFORM set_config('request.jwt.claim.sub',a::text,true);
 ASSERT public.xr_profile_identity_update('No','123450004')->>'error'='account_restricted','Banned edit denied';
 ASSERT public.xr_profile_identity_status()->>'error'='account_restricted','Banned status denied';
 -- Existing initial profile setup remains available even before launch.
 PERFORM set_config('request.jwt.claim.sub','',true);
 UPDATE public.profiles SET ign=NULL,ff_uid=NULL,verified=false WHERE id=b;
 UPDATE public.xr_profile_identity_config SET opens_at='2026-11-13 04:00:00+05:30';
 PERFORM set_config('request.jwt.claim.sub',b::text,true);
 SELECT username INTO u_name FROM public.profiles WHERE id=b;
 j:=public.complete_profile(u_name,'New Player','123450009');ASSERT(j->>'ok')::boolean,'Signup setup remains available';
 ASSERT NOT EXISTS(SELECT 1 FROM public.xr_profile_identity_cooldowns WHERE user_id=b),'Signup does not consume edit timer';
 ASSERT public.xr_profile_identity_update('New Edit','123450008')->>'error'='not_open','Setup cannot bypass edit release';
END $test$;
SET LOCAL ROLE anon;
DO $grants$ BEGIN
 ASSERT NOT has_function_privilege(current_user,'public.xr_profile_identity_update(text,text)','EXECUTE'),'Anon update denied';
 ASSERT NOT has_function_privilege(current_user,'public.xr_profile_identity_status()','EXECUTE'),'Anon status denied';
 ASSERT NOT has_table_privilege(current_user,'public.xr_profile_identity_cooldowns','SELECT,INSERT,UPDATE,DELETE'),'Anon timer private';
END $grants$;
RESET ROLE;
SET LOCAL ROLE authenticated;
DO $grants$ BEGIN
 ASSERT has_function_privilege(current_user,'public.xr_profile_identity_update(text,text)','EXECUTE'),'Authenticated endpoint allowed';
 ASSERT has_function_privilege(current_user,'public.xr_profile_identity_status()','EXECUTE'),'Authenticated status allowed';
 ASSERT NOT has_table_privilege(current_user,'public.xr_profile_identity_cooldowns','SELECT,INSERT,UPDATE,DELETE'),'Timer cannot read/reset';
 ASSERT NOT has_table_privilege(current_user,'public.xr_profile_identity_config','SELECT,INSERT,UPDATE,DELETE'),'Gate cannot read/reset';
 ASSERT NOT has_function_privilege(current_user,'public.xr_profile_identity_guard()','EXECUTE'),'Trigger not exposed as RPC';
END $grants$;
RESET ROLE;

-- Exercise real RLS/integration roles, not only owner-role unit calls.
DO $fixture$ DECLARE a uuid;BEGIN
 SELECT id INTO a FROM public.profiles p WHERE NOT coalesce(banned,false) AND NOT EXISTS(SELECT 1 FROM public.admins WHERE user_id=p.id) ORDER BY id LIMIT 1;
 PERFORM set_config('request.jwt.claim.sub',a::text,true);
END $fixture$;
SET LOCAL ROLE authenticated;
DO $real_role$ DECLARE n integer;j jsonb;BEGIN
 j:=public.xr_profile_identity_status();ASSERT(j->>'ok')::boolean,'Real JWT role reads own status';
 ASSERT public.xr_profile_identity_update('Role fixture','123450006')->>'error'='not_open','Real JWT role obeys gate';
 UPDATE public.profiles SET ign='Other account bypass' WHERE id<>auth.uid();GET DIAGNOSTICS n=ROW_COUNT;ASSERT n=0,'RLS blocks other account writes';
 BEGIN UPDATE public.xr_profile_identity_cooldowns SET changed_at=now()-interval '100 days' WHERE user_id=auth.uid();RAISE EXCEPTION 'Private cooldown writable';EXCEPTION WHEN insufficient_privilege THEN NULL;END;
END $real_role$;
RESET ROLE;

SELECT 'all profile identity tests passed; fixtures rolled back' result;
ROLLBACK;
