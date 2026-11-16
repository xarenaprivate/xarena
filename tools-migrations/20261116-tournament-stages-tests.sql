BEGIN;
DO $$DECLARE t text:='xr-stage-rollback-test';m uuid;BEGIN
 IF (SELECT count(*)FROM public.tournament_stages WHERE tournament_id='XR - S2')<>3 THEN RAISE EXCEPTION 'Season 2 stages missing';END IF;
 IF (SELECT count(*)FROM public.matches_public WHERE tournament_id='XR - S2'AND stage='qualifier')<>6 THEN RAISE EXCEPTION 'Qualifier grouping missing';END IF;
 INSERT INTO public.tournaments(id,name)VALUES(t,'Rollback stage fixture');
 IF(SELECT count(*)FROM public.tournament_stages WHERE tournament_id=t)<>3 THEN RAISE EXCEPTION 'New event defaults missing';END IF;
 INSERT INTO public.matches(tournament_id,title,status)VALUES(t,'Rollback match','scheduled')RETURNING id INTO m;
 IF(SELECT stage_code FROM public.matches WHERE id=m)<>'qualifier' THEN RAISE EXCEPTION 'New match default missing';END IF;
 BEGIN UPDATE public.matches SET stage_code='unknown'WHERE id=m;RAISE EXCEPTION 'Invalid stage accepted';EXCEPTION WHEN foreign_key_violation THEN NULL;END;
 IF EXISTS(SELECT 1 FROM public.matches_public WHERE tournament_id='XR - S2'AND starts_at<'2026-11-16T06:30:00Z')THEN RAISE EXCEPTION 'Schedule regressed';END IF;
END $$;
SET LOCAL ROLE anon;
SELECT count(*)AS anon_stages FROM public.tournament_stages WHERE tournament_id='XR - S2';
SELECT count(*)AS anon_matches FROM public.matches_public WHERE tournament_id='XR - S2' AND stage='qualifier';
RESET ROLE;
ROLLBACK;
