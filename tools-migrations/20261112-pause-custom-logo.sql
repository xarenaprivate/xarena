-- Requests only, no game-control integration or automatic pause. No data seeded.
BEGIN;
CREATE TABLE public.xr_match_pause_requests(
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 match_id uuid NOT NULL REFERENCES public.matches(id) ON DELETE CASCADE,
 user_id uuid NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
 created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 expires_at timestamptz NOT NULL DEFAULT clock_timestamp()+interval '2 minutes',
 UNIQUE(match_id,user_id)
);
CREATE INDEX xr_pause_match_created ON public.xr_match_pause_requests(match_id,created_at DESC);
ALTER TABLE public.xr_match_pause_requests ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.xr_match_pause_requests FROM PUBLIC,anon,authenticated;
CREATE FUNCTION public.xr_pause_request(p_match uuid) RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE u uuid:=auth.uid();m public.matches;r public.xr_match_pause_requests;eligible boolean;
BEGIN
 IF u IS NULL THEN RETURN jsonb_build_object('ok',false,'error','unauthorized');END IF;
 IF NOT EXISTS(SELECT 1 FROM public.profiles WHERE id=u AND NOT coalesce(banned,false)) THEN RETURN jsonb_build_object('ok',false,'error','account_restricted');END IF;
 SELECT * INTO m FROM public.matches WHERE id=p_match FOR SHARE;
 IF NOT FOUND THEN RETURN jsonb_build_object('ok',false,'error','match_not_found');END IF;
 -- Match row locks serialize closure; profile row lock serializes duplicate calls by a player.
 PERFORM 1 FROM public.profiles WHERE id=u FOR UPDATE;
 SELECT * INTO r FROM public.xr_match_pause_requests WHERE match_id=p_match AND user_id=u;
 IF FOUND THEN RETURN jsonb_build_object('ok',true,'already_requested',true,'request_id',r.id,'match_id',p_match,'created_at',r.created_at,'expires_at',r.expires_at,'status',CASE WHEN m.status<>'live' THEN 'match_closed' WHEN r.expires_at>clock_timestamp() THEN 'pending' ELSE 'expired' END,'pause_seconds',120,'game_paused',false);END IF;
 IF m.status<>'live' THEN RETURN jsonb_build_object('ok',false,'error','match_not_live');END IF;
 SELECT EXISTS(SELECT 1 FROM public.registrations rg WHERE rg.tournament_id=m.tournament_id AND rg.status NOT IN ('rejected','cancelled','declined','waitlist') AND NOT coalesce(rg.hold,false) AND rg.user_id IS NOT NULL AND (rg.user_id=u OR EXISTS(SELECT 1 FROM public.squad_members a JOIN public.squad_members b ON a.squad_id=b.squad_id WHERE a.user_id=u AND b.user_id=rg.user_id))) INTO eligible;
 IF NOT eligible THEN RETURN jsonb_build_object('ok',false,'error','not_registered');END IF;
 INSERT INTO public.xr_match_pause_requests(match_id,user_id) VALUES(p_match,u) RETURNING * INTO r;
 RETURN jsonb_build_object('ok',true,'already_requested',false,'request_id',r.id,'match_id',p_match,'created_at',r.created_at,'expires_at',r.expires_at,'status','pending','pause_seconds',120,'game_paused',false);
END $$;
CREATE FUNCTION public.xr_pause_request_list(p_match uuid,p_limit integer DEFAULT 50) RETURNS jsonb
LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE u uuid:=auth.uid();m public.matches;
BEGIN
 IF u IS NULL THEN RETURN jsonb_build_object('ok',false,'error','unauthorized');END IF;
 IF NOT EXISTS(SELECT 1 FROM public.profiles WHERE id=u AND NOT coalesce(banned,false)) THEN RETURN jsonb_build_object('ok',false,'error','account_restricted');END IF;
 IF NOT public.is_admin() THEN RETURN jsonb_build_object('ok',false,'error','forbidden');END IF;
 SELECT * INTO m FROM public.matches WHERE id=p_match;
 IF NOT FOUND THEN RETURN jsonb_build_object('ok',false,'error','match_not_found');END IF;
 RETURN jsonb_build_object('ok',true,'match_id',p_match,'match_status',m.status,'game_paused',false,'requests',coalesce((SELECT jsonb_agg(to_jsonb(t) ORDER BY t.created_at DESC) FROM (SELECT r.id request_id,r.user_id,coalesce(p.ign,p.username) player_name,p.username,r.created_at,r.expires_at,CASE WHEN m.status<>'live' THEN 'match_closed' WHEN r.expires_at>now() THEN 'pending' ELSE 'expired' END status,120 pause_seconds FROM public.xr_match_pause_requests r JOIN public.profiles p ON p.id=r.user_id WHERE r.match_id=p_match ORDER BY r.created_at DESC LIMIT greatest(1,least(coalesce(p_limit,50),100)))t),'[]'::jsonb));
END $$;
CREATE FUNCTION public.xr_squad_custom_logo_set(p_squad uuid,p_logo_url text) RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE u uuid:=auth.uid();url text:=btrim(p_logo_url);
BEGIN
 IF u IS NULL THEN RETURN jsonb_build_object('ok',false,'error','unauthorized');END IF;
 IF NOT EXISTS(SELECT 1 FROM public.profiles WHERE id=u AND NOT coalesce(banned,false)) THEN RETURN jsonb_build_object('ok',false,'error','account_restricted');END IF;
 -- Bound to HTTPS Cloudinary image-upload resource URLs, not raw/video URLs, userinfo or lookalike hosts.
 IF url IS NULL OR length(url)>2048 OR url !~ '^https://res[.]cloudinary[.]com/[A-Za-z0-9_-]+/image/upload/[A-Za-z0-9_.,!~%/=-]+$' OR url ~* '(%0[ad]|%00|[.][.])' THEN RETURN jsonb_build_object('ok',false,'error','invalid_logo_url');END IF;
 PERFORM 1 FROM public.squads WHERE id=p_squad AND captain=u FOR UPDATE;
 IF NOT FOUND THEN RETURN jsonb_build_object('ok',false,'error','not_found_or_not_captain');END IF;
 PERFORM public.set_squad_logo(p_squad,url);
 RETURN jsonb_build_object('ok',true,'squad_id',p_squad,'logo_url',url);
END $$;
REVOKE ALL ON FUNCTION public.xr_pause_request(uuid),public.xr_pause_request_list(uuid,integer),public.xr_squad_custom_logo_set(uuid,text) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.xr_pause_request(uuid),public.xr_pause_request_list(uuid,integer),public.xr_squad_custom_logo_set(uuid,text) TO authenticated;
NOTIFY pgrst,'reload schema';
COMMIT;
