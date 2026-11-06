CREATE TABLE public.player_recovery_keys(user_id uuid PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,key_hash text UNIQUE NOT NULL,created_at timestamptz NOT NULL DEFAULT now(),consumed_at timestamptz);
CREATE TABLE public.player_recovery_limits(bucket text PRIMARY KEY,window_start timestamptz NOT NULL,attempts integer NOT NULL);
ALTER TABLE public.player_recovery_keys ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.player_recovery_limits ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.player_recovery_keys,public.player_recovery_limits FROM anon,authenticated;
CREATE OR REPLACE FUNCTION public.recovery_enroll(p_user uuid,p_hash text) RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=public AS $$
BEGIN
IF length(p_hash)<>64 OR NOT EXISTS(SELECT 1 FROM auth.users WHERE id=p_user AND email LIKE '%@phone.xarena') THEN RETURN jsonb_build_object('ok',false); END IF;
IF EXISTS(SELECT 1 FROM public.profiles WHERE id=p_user AND banned) OR EXISTS(SELECT 1 FROM public.admins WHERE user_id=p_user) THEN RETURN jsonb_build_object('ok',false); END IF;
INSERT INTO public.player_recovery_keys(user_id,key_hash) VALUES(p_user,p_hash) ON CONFLICT(user_id) DO UPDATE SET key_hash=excluded.key_hash,created_at=now(),consumed_at=null;
RETURN jsonb_build_object('ok',true);
END $$;
CREATE OR REPLACE FUNCTION public.recovery_reserve(p_hash text,p_bucket text) RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=public AS $$
DECLARE v uuid;n integer;
BEGIN
INSERT INTO public.player_recovery_limits(bucket,window_start,attempts) VALUES(p_bucket,now(),1) ON CONFLICT(bucket) DO UPDATE SET window_start=CASE WHEN player_recovery_limits.window_start<now()-interval '15 minutes' THEN now() ELSE player_recovery_limits.window_start END,attempts=CASE WHEN player_recovery_limits.window_start<now()-interval '15 minutes' THEN 1 ELSE player_recovery_limits.attempts+1 END RETURNING attempts INTO n;
IF n>5 THEN RETURN jsonb_build_object('ok',false);END IF;
UPDATE public.player_recovery_keys k SET consumed_at=now() WHERE key_hash=p_hash AND consumed_at IS NULL AND NOT EXISTS(SELECT 1 FROM public.profiles p WHERE p.id=k.user_id AND p.banned) AND NOT EXISTS(SELECT 1 FROM public.admins a WHERE a.user_id=k.user_id) RETURNING user_id INTO v;
IF v IS NULL THEN RETURN jsonb_build_object('ok',false);END IF;
RETURN jsonb_build_object('ok',true,'user_id',v);
END $$;
CREATE OR REPLACE FUNCTION public.recovery_key_status() RETURNS jsonb LANGUAGE sql STABLE SECURITY DEFINER SET search_path=public AS $$
SELECT jsonb_build_object('ok',auth.uid() IS NOT NULL,'enrolled',EXISTS(SELECT 1 FROM public.player_recovery_keys WHERE user_id=auth.uid() AND consumed_at IS NULL))
$$;
REVOKE ALL ON FUNCTION public.recovery_enroll(uuid,text),public.recovery_reserve(text,text),public.recovery_key_status() FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.recovery_enroll(uuid,text),public.recovery_reserve(text,text) TO service_role;
GRANT EXECUTE ON FUNCTION public.recovery_key_status() TO authenticated;
NOTIFY pgrst,'reload schema';
