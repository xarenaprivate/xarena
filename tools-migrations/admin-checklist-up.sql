-- Additive checklist controls. No account or message is changed by installation.
CREATE TABLE IF NOT EXISTS public.dm_read_cursors (
 user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
 thread_id uuid NOT NULL REFERENCES public.dm_threads(id) ON DELETE CASCADE,
 read_at timestamptz NOT NULL, message_id uuid NOT NULL,
 PRIMARY KEY(user_id,thread_id)
);
ALTER TABLE public.dm_read_cursors ENABLE ROW LEVEL SECURITY;
CREATE POLICY own_read_cursor ON public.dm_read_cursors FOR SELECT TO authenticated USING(user_id=auth.uid());
REVOKE ALL ON public.dm_read_cursors FROM anon,authenticated;
GRANT SELECT ON public.dm_read_cursors TO authenticated;
CREATE OR REPLACE FUNCTION public.dm_mark_read(p_thread uuid,p_message uuid) RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path=public AS $$
DECLARE stamp timestamptz;
BEGIN
 IF auth.uid() IS NULL OR NOT EXISTS(SELECT 1 FROM public.dm_threads WHERE id=p_thread AND auth.uid() IN(user_a,user_b)) THEN
 RETURN jsonb_build_object('ok',false,'error','Conversation not found.'); END IF;
 SELECT created_at INTO stamp FROM public.dm_messages WHERE id=p_message AND thread_id=p_thread;
 IF stamp IS NULL THEN RETURN jsonb_build_object('ok',false,'error','Message not found.'); END IF;
 INSERT INTO public.dm_read_cursors(user_id,thread_id,read_at,message_id) VALUES(auth.uid(),p_thread,stamp,p_message)
 ON CONFLICT(user_id,thread_id) DO UPDATE SET read_at=excluded.read_at,message_id=excluded.message_id WHERE (excluded.read_at,excluded.message_id)>=(dm_read_cursors.read_at,dm_read_cursors.message_id);
 RETURN jsonb_build_object('ok',true);
END $$;
CREATE OR REPLACE FUNCTION public.dm_unread_summary() RETURNS jsonb
LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path=public AS $$
DECLARE rows jsonb;
BEGIN
 IF auth.uid() IS NULL THEN RETURN jsonb_build_object('ok',false,'error','Log in first.'); END IF;
 SELECT coalesce(jsonb_agg(x ORDER BY x.last_message_at DESC NULLS LAST),'[]'::jsonb) INTO rows FROM (
 SELECT t.id AS thread_id,p.id AS peer_id,p.username AS peer_username,p.ign AS peer_ign,
 (SELECT count(*) FROM public.dm_messages m WHERE m.thread_id=t.id AND m.sender_id<>auth.uid() AND (c.read_at IS NULL OR (m.created_at,m.id)>(c.read_at,c.message_id))) AS unread_count,
 (SELECT m.id FROM public.dm_messages m WHERE m.thread_id=t.id ORDER BY m.created_at DESC,m.id DESC LIMIT 1) AS last_message_id,
 (SELECT max(m.created_at) FROM public.dm_messages m WHERE m.thread_id=t.id) AS last_message_at
 FROM public.dm_threads t LEFT JOIN public.profiles p ON p.id=CASE WHEN t.user_a=auth.uid() THEN t.user_b ELSE t.user_a END
 LEFT JOIN public.dm_read_cursors c ON c.thread_id=t.id AND c.user_id=auth.uid()
 WHERE auth.uid() IN(t.user_a,t.user_b)
 ) x;
 RETURN jsonb_build_object('ok',true,'threads',rows,'total_unread',(SELECT coalesce(sum((r->>'unread_count')::int),0) FROM jsonb_array_elements(rows) r));
END $$;
CREATE OR REPLACE FUNCTION public.admin_account_directory(p_search text DEFAULT '',p_status text DEFAULT '',p_offset integer DEFAULT 0) RETURNS jsonb
LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path=public AS $$
DECLARE rows jsonb; n integer;
BEGIN
 IF NOT public.is_admin() THEN RETURN jsonb_build_object('ok',false,'error','Admin only.'); END IF;
 SELECT count(*) INTO n FROM public.profiles p WHERE (p.username ILIKE '%'||left(coalesce(p_search,''),80)||'%' OR p.ign ILIKE '%'||left(coalesce(p_search,''),80)||'%') AND (p_status='' OR (p_status='banned' AND p.banned) OR (p_status='unverified' AND NOT coalesce(p.phone_verified,false)));
 SELECT coalesce(jsonb_agg(x),'[]'::jsonb) INTO rows FROM(
 SELECT p.id,p.username,p.ign,p.created_at,p.verified,p.phone_verified,p.banned,p.banned_reason,p.warned,p.last_seen_at,
 (SELECT count(*) FROM auth.sessions s WHERE s.user_id=p.id AND (s.not_after IS NULL OR s.not_after>now())) AS session_count,
 (SELECT count(*) FROM public.device_tokens d WHERE d.user_id=p.id) AS push_devices
 FROM public.profiles p WHERE (p.username ILIKE '%'||left(coalesce(p_search,''),80)||'%' OR p.ign ILIKE '%'||left(coalesce(p_search,''),80)||'%') AND (p_status='' OR (p_status='banned' AND p.banned) OR (p_status='unverified' AND NOT coalesce(p.phone_verified,false)))
 ORDER BY p.created_at DESC,p.id LIMIT 50 OFFSET greatest(coalesce(p_offset,0),0)
 )x;
 RETURN jsonb_build_object('ok',true,'users',rows,'total',n);
END $$;
CREATE OR REPLACE FUNCTION public.admin_account_action(p_user uuid,p_action text,p_reason text DEFAULT '') RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path=public AS $$
BEGIN
 IF NOT public.is_admin() THEN RETURN jsonb_build_object('ok',false,'error','Admin only.'); END IF;
 IF NOT EXISTS(SELECT 1 FROM public.profiles WHERE id=p_user) THEN RETURN jsonb_build_object('ok',false,'error','Account not found.'); END IF;
 IF EXISTS(SELECT 1 FROM public.admins WHERE user_id=p_user) THEN RETURN jsonb_build_object('ok',false,'error','Owner accounts cannot be changed here.'); END IF;
 IF p_action IN('ban','warn') AND length(trim(coalesce(p_reason,'')))<3 THEN RETURN jsonb_build_object('ok',false,'error','Add a reason.'); END IF;
 IF p_action='ban' THEN UPDATE public.profiles SET banned=true,banned_reason=left(p_reason,300) WHERE id=p_user;
 ELSIF p_action='unban' THEN UPDATE public.profiles SET banned=false,banned_reason=NULL WHERE id=p_user;
 ELSIF p_action='warn' THEN UPDATE public.profiles SET warned=true,warned_reason=left(p_reason,300) WHERE id=p_user;
 ELSE RETURN jsonb_build_object('ok',false,'error','Unknown action.'); END IF;
 INSERT INTO public.audit_log(actor,action,details) VALUES(auth.uid()::text,'account_'||p_action,jsonb_build_object('user',p_user,'reason',left(p_reason,300)));
 RETURN jsonb_build_object('ok',true);
END $$;
CREATE OR REPLACE FUNCTION public.admin_checklist_stats() RETURNS jsonb
LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path=public AS $$
BEGIN
 IF NOT public.is_admin() THEN RETURN jsonb_build_object('ok',false,'error','Admin only.'); END IF;
 RETURN jsonb_build_object('ok',true,'as_of',now(),
 'accounts',(SELECT count(*) FROM public.profiles),
 'signups_24h',(SELECT count(*) FROM public.profiles WHERE created_at>now()-interval '24 hours'),
 'active_10m',(SELECT count(*) FROM public.profiles WHERE last_seen_at>now()-interval '10 minutes'),
 'banned',(SELECT count(*) FROM public.profiles WHERE banned),
 'unverified',(SELECT count(*) FROM public.profiles WHERE NOT coalesce(phone_verified,false)),
 'open_feedback',(SELECT count(*) FROM public.feedback WHERE status='open'),
 'registrations',(SELECT count(*) FROM public.registrations),
 'push_devices',(SELECT count(*) FROM public.device_tokens));
END $$;
CREATE OR REPLACE FUNCTION public.admin_compose_notification(p_user uuid,p_title text,p_body text) RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path=public AS $$
DECLARE n bigint;
BEGIN
 IF NOT public.is_admin() THEN RETURN jsonb_build_object('ok',false,'error','Admin only.'); END IF;
 IF length(trim(coalesce(p_title,''))) NOT BETWEEN 1 AND 100 OR length(trim(coalesce(p_body,''))) NOT BETWEEN 1 AND 500 THEN RETURN jsonb_build_object('ok',false,'error','Title: 1-100 characters. Message: 1-500 characters.'); END IF;
 IF NOT EXISTS(SELECT 1 FROM public.profiles WHERE id=p_user) THEN RETURN jsonb_build_object('ok',false,'error','Choose an account.'); END IF;
 INSERT INTO public.notifications(user_id,kind,title,body,link) VALUES(p_user,'owner',trim(p_title),trim(p_body),'notifications.html') RETURNING id INTO n;
 INSERT INTO public.audit_log(actor,action,details) VALUES(auth.uid()::text,'owner_notification',jsonb_build_object('user',p_user,'notification',n));
 RETURN jsonb_build_object('ok',true,'notification_id',n);
END $$;
CREATE OR REPLACE FUNCTION public.admin_resolve_feedback(p_id uuid,p_status text) RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path=public AS $$
BEGIN
 IF NOT public.is_admin() THEN RETURN jsonb_build_object('ok',false,'error','Admin only.'); END IF;
 IF p_status NOT IN ('open','resolved') THEN RETURN jsonb_build_object('ok',false,'error','Invalid status.'); END IF;
 UPDATE public.feedback SET status=p_status WHERE id=p_id;
 IF NOT FOUND THEN RETURN jsonb_build_object('ok',false,'error','Request not found.'); END IF;
 INSERT INTO public.audit_log(actor,action,details) VALUES(auth.uid()::text,'feedback_status',jsonb_build_object('id',p_id,'status',p_status));
 RETURN jsonb_build_object('ok',true);
END $$;
REVOKE ALL ON FUNCTION public.admin_checklist_stats(),public.admin_compose_notification(uuid,text,text),public.admin_resolve_feedback(uuid,text) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.admin_checklist_stats(),public.admin_compose_notification(uuid,text,text),public.admin_resolve_feedback(uuid,text) TO authenticated;
REVOKE ALL ON FUNCTION public.dm_mark_read(uuid,uuid),public.dm_unread_summary(),public.admin_account_directory(text,text,integer),public.admin_account_action(uuid,text,text) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.dm_mark_read(uuid,uuid),public.dm_unread_summary(),public.admin_account_directory(text,text,integer),public.admin_account_action(uuid,text,text) TO authenticated;
NOTIFY pgrst,'reload schema';
