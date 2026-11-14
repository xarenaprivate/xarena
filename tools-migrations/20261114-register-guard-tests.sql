BEGIN;
DO $$DECLARE me uuid;v jsonb;definition text;BEGIN
SELECT user_id INTO me FROM public.registrations WHERE tournament_id='XR - S2'LIMIT 1;
PERFORM set_config('request.jwt.claim.sub',me::text,true);
v:=public.register_squad('XR-GUARD-TEST','xr-s1','Test squad','Test captain','12345678','Test captain','+910000000000',NULL,'guard-test',NULL);
IF v->>'ok'<>'false'OR v->>'error'<>'Registration is closed for this tournament.'THEN RAISE EXCEPTION 'Closed-event bypass guard failed: %',v;END IF;
v:=public.register_squad('XR-GUARD-TEST','xr-5oar','Test squad','Test captain','12345678','Test captain','+910000000000',NULL,'guard-test',NULL);
IF v->>'ok'<>'false'OR v->>'error'<>'Registration is closed for this tournament.'THEN RAISE EXCEPTION 'Ignition bypass guard failed: %',v;END IF;
IF EXISTS(SELECT 1 FROM public.registrations WHERE id='XR-GUARD-TEST')THEN RAISE EXCEPTION 'Guard inserted row';END IF;
SELECT pg_get_functiondef(p.oid)INTO definition FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname='public'AND p.proname='register_squad';
IF position('reg_open=true and status in (''upcoming'',''live'') for update'IN definition)=0 THEN RAISE EXCEPTION 'Serialized guard missing';END IF;
RAISE NOTICE 'PASS: direct RPC closed-event guards and serialized capacity guard present';END $$;
ROLLBACK;
