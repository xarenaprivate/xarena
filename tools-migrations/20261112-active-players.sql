BEGIN;
CREATE FUNCTION public.xr_sf_active_players(p_limit integer DEFAULT 30) RETURNS jsonb
LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path TO pg_catalog AS $fn$
DECLARE u uuid:=public.xr_sf_actor();rows jsonb;
BEGIN
 IF NOT coalesce((SELECT show_activity FROM public.profiles WHERE id=u),false) THEN RETURN jsonb_build_object('ok',true,'players','[]'::jsonb);END IF;
 SELECT coalesce(jsonb_agg(jsonb_build_object('id',s.id,'username',s.username,'ign',s.ign,'avatar_url',s.avatar_url,'presence',s.state) ORDER BY s.reported_at DESC,s.id),'[]'::jsonb) INTO rows
 FROM (
  SELECT p.id,p.username,p.ign,p.avatar_url,r.state,r.reported_at
  FROM public.xr_sf_presence r JOIN public.profiles p ON p.id=r.user_id
  LEFT JOIN public.xr_sf_player_prefs f ON f.user_id=p.id
  WHERE r.expires_at>statement_timestamp() AND r.state IN ('online','in_match')
   AND coalesce(p.show_activity,false) AND NOT coalesce(p.banned,false) AND NOT public.is_blocked(u,p.id)
   AND (p.id=u OR coalesce(f.discoverable,false) OR public.xr_sf_related(p.id)
    OR EXISTS(SELECT 1 FROM public.friends WHERE user_id=u AND friend_id=p.id)
    OR EXISTS(SELECT 1 FROM public.squad_members a JOIN public.squad_members b ON a.squad_id=b.squad_id WHERE a.user_id=u AND b.user_id=p.id))
  ORDER BY r.reported_at DESC,p.id LIMIT greatest(1,least(coalesce(p_limit,30),50))
 )s;
 RETURN jsonb_build_object('ok',true,'players',rows);
END $fn$;
REVOKE ALL ON FUNCTION public.xr_sf_active_players(integer) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.xr_sf_active_players(integer) TO authenticated;
NOTIFY pgrst,'reload schema';
COMMIT;
