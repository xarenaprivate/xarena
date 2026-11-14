BEGIN;
INSERT INTO public.site_settings(key,value) VALUES('scoring_XR-S2','{"tournament_id":"XR - S2","published":true,"label":"Season 2 scoring rules","placement_points":[12,9,8,7,6,5,4,3,2,1,0,0],"kill_points":1,"match_count":6,"tiebreakers":["booyahs","total_kills","match_6_placement"],"auto_scoring":false}'::jsonb)ON CONFLICT(key)DO UPDATE SET value=excluded.value;
CREATE OR REPLACE FUNCTION public.xr_scoring_rules(p_tournament text)RETURNS jsonb LANGUAGE sql STABLE SECURITY DEFINER SET search_path=public,pg_temp AS $$ SELECT coalesce((SELECT value FROM public.site_settings WHERE key='scoring_XR-S2'AND value->>'tournament_id'=p_tournament AND value->>'published'='true'),'{"published":false}'::jsonb); $$;
REVOKE ALL ON FUNCTION public.xr_scoring_rules(text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.xr_scoring_rules(text) TO anon,authenticated,service_role;
COMMIT;
