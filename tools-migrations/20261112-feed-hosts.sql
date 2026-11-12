BEGIN;
CREATE TABLE public.xr_feed_hosts(user_id uuid PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,created_at timestamptz NOT NULL DEFAULT now());
ALTER TABLE public.xr_feed_hosts ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.xr_feed_hosts FROM PUBLIC,anon,authenticated;
CREATE FUNCTION public.xr_feed_capability() RETURNS jsonb LANGUAGE sql STABLE SECURITY DEFINER SET search_path=pg_catalog AS $$
 SELECT jsonb_build_object('ok',true,'can_post',auth.uid() IS NOT NULL AND EXISTS(SELECT 1 FROM public.profiles WHERE id=auth.uid() AND NOT coalesce(banned,false)) AND (public.is_helper() OR EXISTS(SELECT 1 FROM public.xr_feed_hosts WHERE user_id=auth.uid())))
$$;
REVOKE ALL ON FUNCTION public.xr_feed_capability() FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.xr_feed_capability() TO anon,authenticated;
-- post/hide definitions appended by build script; signature and data semantics unchanged.
CREATE OR REPLACE FUNCTION public.xr_kill_feed_hide(p_event bigint, p_hidden boolean DEFAULT true)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'pg_catalog'
AS $function$
BEGIN
 IF NOT coalesce((public.xr_feed_capability()->>'can_post')::boolean,false) THEN RETURN jsonb_build_object('ok',false,'error','Feed host access required.');END IF;
 IF EXISTS(SELECT 1 FROM public.profiles WHERE id=auth.uid() AND coalesce(banned,false)) THEN RETURN jsonb_build_object('ok',false,'error','Your account cannot moderate.');END IF;
 UPDATE public.xr_kill_feed SET hidden=coalesce(p_hidden,true) WHERE id=p_event;
 IF NOT FOUND THEN RETURN jsonb_build_object('ok',false,'error','Feed event not found.');END IF;
 RETURN jsonb_build_object('ok',true);
END $function$
;
CREATE OR REPLACE FUNCTION public.xr_kill_feed_post(p_match uuid, p_killer text, p_victim text, p_weapon text DEFAULT NULL::text, p_elapsed_seconds integer DEFAULT NULL::integer)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'pg_catalog'
AS $function$
DECLARE nid bigint; txt text; st text;
BEGIN
 IF NOT coalesce((public.xr_feed_capability()->>'can_post')::boolean,false) THEN RETURN jsonb_build_object('ok',false,'error','Feed host access required.');END IF;
 IF EXISTS(SELECT 1 FROM public.profiles WHERE id=auth.uid() AND coalesce(banned,false)) THEN RETURN jsonb_build_object('ok',false,'error','Your account cannot post.');END IF;
 SELECT status INTO st FROM public.matches WHERE id=p_match;
 IF st IS NULL OR st NOT IN ('scheduled','live') THEN RETURN jsonb_build_object('ok',false,'error','Choose a scheduled or live match.');END IF;
 IF coalesce(char_length(btrim(p_killer)),0) NOT BETWEEN 2 AND 40 OR coalesce(char_length(btrim(p_victim)),0) NOT BETWEEN 2 AND 40 OR coalesce(char_length(btrim(p_weapon)),0)>40 OR (p_elapsed_seconds IS NOT NULL AND p_elapsed_seconds NOT BETWEEN 0 AND 86400) THEN RETURN jsonb_build_object('ok',false,'error','Check player names, weapon and elapsed seconds.');END IF;
 txt:=p_killer||' '||p_victim||' '||coalesce(p_weapon,'');
 IF txt ~ '[0-9][ -]?[0-9][ -]?[0-9][ -]?[0-9][ -]?[0-9][ -]?[0-9][ -]?[0-9][ -]?[0-9]' OR txt ~* '(https?://|www\.|@)' THEN RETURN jsonb_build_object('ok',false,'error','Use player names only, not UIDs, phone numbers or links.');END IF;
 INSERT INTO public.xr_kill_feed(match_id,killer,victim,weapon,elapsed_seconds,created_by) VALUES(p_match,btrim(p_killer),btrim(p_victim),nullif(btrim(p_weapon),''),p_elapsed_seconds,auth.uid()) RETURNING id INTO nid;
 RETURN jsonb_build_object('ok',true,'id',nid);
END $function$
;
COMMIT;
