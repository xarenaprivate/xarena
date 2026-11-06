CREATE OR REPLACE FUNCTION public.player_banner(p_username text) RETURNS jsonb LANGUAGE sql STABLE SECURITY DEFINER SET search_path=public AS $$
SELECT coalesce((SELECT jsonb_build_object('id',c.id,'title',c.title,'color_start',c.color_start,'color_end',c.color_end) FROM public.profiles p JOIN public.player_banner_settings s ON s.user_id=p.id JOIN public.player_banner_catalog c ON c.id=s.banner_id WHERE lower(p.username)=lower(p_username) AND c.active LIMIT 1),'null'::jsonb)
$$;
REVOKE ALL ON FUNCTION public.player_banner(text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.player_banner(text) TO anon,authenticated;
NOTIFY pgrst,'reload schema';
