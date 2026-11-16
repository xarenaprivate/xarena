BEGIN;
CREATE TABLE public.tournament_stages (
 tournament_id text NOT NULL REFERENCES public.tournaments(id) ON DELETE CASCADE,
 stage text NOT NULL CHECK(stage IN('qualifier','semi_final','final')),
 label text NOT NULL, ord integer NOT NULL,
 PRIMARY KEY(tournament_id,stage), UNIQUE(tournament_id,ord)
);
ALTER TABLE public.tournament_stages ENABLE ROW LEVEL SECURITY;
GRANT SELECT ON public.tournament_stages TO anon,authenticated;
GRANT INSERT,UPDATE,DELETE ON public.tournament_stages TO authenticated;
CREATE POLICY stages_public_read ON public.tournament_stages FOR SELECT TO anon,authenticated USING(true);
CREATE POLICY stages_admin_write ON public.tournament_stages FOR ALL TO authenticated USING(public.is_admin()) WITH CHECK(public.is_admin());
ALTER TABLE public.matches ADD COLUMN stage_code text;
ALTER TABLE public.matches ADD CONSTRAINT matches_stage_fk FOREIGN KEY(tournament_id,stage_code) REFERENCES public.tournament_stages(tournament_id,stage);
CREATE FUNCTION public.xr_tournament_default_stages() RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
BEGIN
 INSERT INTO public.tournament_stages(tournament_id,stage,label,ord)VALUES
 (NEW.id,'qualifier','Qualifier lobby',1),(NEW.id,'semi_final','Semi Final',2),(NEW.id,'final','Final',3);
 RETURN NEW;
END $$;
REVOKE ALL ON FUNCTION public.xr_tournament_default_stages() FROM PUBLIC,anon,authenticated;
CREATE TRIGGER xr_tournament_default_stages AFTER INSERT ON public.tournaments FOR EACH ROW EXECUTE FUNCTION public.xr_tournament_default_stages();
CREATE FUNCTION public.xr_match_default_stage() RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
BEGIN
 IF NEW.stage_code IS NULL AND EXISTS(SELECT 1 FROM public.tournament_stages WHERE tournament_id=NEW.tournament_id AND stage='qualifier') THEN NEW.stage_code:='qualifier';END IF;
 RETURN NEW;
END $$;
REVOKE ALL ON FUNCTION public.xr_match_default_stage() FROM PUBLIC,anon,authenticated;
CREATE TRIGGER xr_match_default_stage BEFORE INSERT ON public.matches FOR EACH ROW EXECUTE FUNCTION public.xr_match_default_stage();
INSERT INTO public.tournament_stages(tournament_id,stage,label,ord)VALUES
 ('XR - S2','qualifier','Qualifier lobby',1),('XR - S2','semi_final','Semi Final',2),('XR - S2','final','Final',3);
UPDATE public.matches SET stage_code='qualifier' WHERE tournament_id='XR - S2';
CREATE OR REPLACE VIEW public.matches_public AS SELECT id,tournament_id,title,starts_at,status,reveal_minutes,created_at,stage_code AS stage FROM public.matches;
NOTIFY pgrst,'reload schema';
COMMIT;
