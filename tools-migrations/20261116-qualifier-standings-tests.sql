BEGIN;
SET LOCAL ROLE anon;
DO $$DECLARE n integer;BEGIN
 IF(SELECT count(*)FROM public.tournament_standings WHERE tournament_id='XR - S2' AND stage='qualifier')<>12 THEN RAISE EXCEPTION 'anon read failed';END IF;
 BEGIN
  UPDATE public.tournament_standings SET points=0 WHERE tournament_id='XR - S2';
  GET DIAGNOSTICS n=ROW_COUNT;
  IF n<>0 THEN RAISE EXCEPTION 'anon update affected rows';END IF;
 EXCEPTION WHEN insufficient_privilege THEN NULL;END;
END $$;
RESET ROLE;
SET LOCAL ROLE authenticated;
DO $$DECLARE n integer;BEGIN
 IF public.is_admin()THEN RAISE EXCEPTION 'fixture unexpectedly admin';END IF;
 UPDATE public.tournament_standings SET points=0 WHERE tournament_id='XR - S2';
 GET DIAGNOSTICS n=ROW_COUNT;
 IF n<>0 THEN RAISE EXCEPTION 'nonadmin update passed';END IF;
 BEGIN
  INSERT INTO public.tournament_standings(tournament_id,stage,squad_name,points,rank)VALUES('XR - S2','semi_final','test',1,1);
  RAISE EXCEPTION 'nonadmin insert unexpectedly allowed';
 EXCEPTION WHEN insufficient_privilege THEN NULL;END;
END $$;
RESET ROLE;
SELECT 'PASS anonymous read, anonymous denied write, authenticated nonadmin denied insert/update'AS tests;
ROLLBACK;
