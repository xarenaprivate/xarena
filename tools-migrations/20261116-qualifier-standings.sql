BEGIN;
CREATE TABLE public.tournament_standings(
 tournament_id text NOT NULL,stage text NOT NULL,squad_name text NOT NULL,
 points integer NOT NULL CHECK(points>=0),rank integer NOT NULL CHECK(rank>0),
 squad_id text,tag text,logo_url text,matches_played integer CHECK(matches_played>=0),
 updated_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY(tournament_id,stage,squad_name),UNIQUE(tournament_id,stage,rank),
 FOREIGN KEY(tournament_id,stage)REFERENCES public.tournament_stages(tournament_id,stage)ON DELETE CASCADE
);
ALTER TABLE public.tournament_standings ENABLE ROW LEVEL SECURITY;
GRANT SELECT ON public.tournament_standings TO anon,authenticated;
GRANT INSERT,UPDATE,DELETE ON public.tournament_standings TO authenticated;
CREATE POLICY standings_public_read ON public.tournament_standings FOR SELECT TO anon,authenticated USING(true);
CREATE POLICY standings_admin_write ON public.tournament_standings FOR ALL TO authenticated USING(public.is_admin())WITH CHECK(public.is_admin());
INSERT INTO public.tournament_standings(tournament_id,stage,rank,squad_name,points)VALUES
('XR - S2','qualifier',1,'Team Nonchalant',118),
('XR - S2','qualifier',2,'ZEX ESP',81),
('XR - S2','qualifier',3,'Prime era',73),
('XR - S2','qualifier',4,'TEAM UNIBOND',66),
('XR - S2','qualifier',5,'UNDEFEATED',54),
('XR - S2','qualifier',6,'Team Libert',48),
('XR - S2','qualifier',7,'TEAM ESP OG',38),
('XR - S2','qualifier',8,'TEAM LEGIT UNITED',36),
('XR - S2','qualifier',9,'TJ EMPIRE',30),
('XR - S2','qualifier',10,'FLAME LORDS',26),
('XR - S2','qualifier',11,'DEADLY ELITE',21),
('XR - S2','qualifier',12,'High speed',4);
NOTIFY pgrst,'reload schema';
COMMIT;
