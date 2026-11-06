CREATE TABLE public.player_banner_catalog(id text PRIMARY KEY,title text NOT NULL,color_start text NOT NULL,color_end text NOT NULL,active boolean NOT NULL DEFAULT true,sort integer NOT NULL DEFAULT 0);
INSERT INTO public.player_banner_catalog(id,title,color_start,color_end,sort) VALUES('arena-violet','Arena Violet','#24113D','#7C3AED',1),('ember-gold','Ember Gold','#341206','#B7791F',2),('arctic-blue','Arctic Blue','#071B35','#0369A1',3);
CREATE TABLE public.player_banner_owners(user_id uuid REFERENCES auth.users(id) ON DELETE CASCADE,banner_id text REFERENCES public.player_banner_catalog(id),acquired_at timestamptz NOT NULL DEFAULT now(),PRIMARY KEY(user_id,banner_id));
CREATE TABLE public.player_banner_settings(user_id uuid PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,banner_id text REFERENCES public.player_banner_catalog(id),updated_at timestamptz NOT NULL DEFAULT now());
ALTER TABLE public.player_banner_catalog ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.player_banner_owners ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.player_banner_settings ENABLE ROW LEVEL SECURITY;
CREATE POLICY banner_catalog_read ON public.player_banner_catalog FOR SELECT USING(active OR public.is_admin());
CREATE POLICY banner_owners_read ON public.player_banner_owners FOR SELECT TO authenticated USING(user_id=auth.uid() OR public.is_admin());
CREATE POLICY banner_settings_read ON public.player_banner_settings FOR SELECT USING(true);
REVOKE ALL ON public.player_banner_catalog,public.player_banner_owners,public.player_banner_settings FROM anon,authenticated;
GRANT SELECT ON public.player_banner_catalog,public.player_banner_settings TO anon,authenticated;
GRANT SELECT ON public.player_banner_owners TO authenticated;
CREATE OR REPLACE FUNCTION public.equip_player_banner(p_banner text) RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=public AS $$
BEGIN
IF auth.uid() IS NULL THEN RETURN jsonb_build_object('ok',false,'error','Log in first.'); END IF;
IF p_banner IS NOT NULL AND NOT EXISTS(SELECT 1 FROM public.player_banner_owners o JOIN public.player_banner_catalog c ON c.id=o.banner_id WHERE o.user_id=auth.uid() AND o.banner_id=p_banner AND c.active) THEN RETURN jsonb_build_object('ok',false,'error','Claim this banner first.'); END IF;
INSERT INTO public.player_banner_settings(user_id,banner_id) VALUES(auth.uid(),p_banner) ON CONFLICT(user_id) DO UPDATE SET banner_id=excluded.banner_id,updated_at=now();
RETURN jsonb_build_object('ok',true,'banner_id',p_banner);
END $$;
REVOKE ALL ON FUNCTION public.equip_player_banner(text) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.equip_player_banner(text) TO authenticated;
ALTER TABLE public.gift_mailbox DROP CONSTRAINT gift_mailbox_reward_kind_check;
ALTER TABLE public.gift_mailbox DROP CONSTRAINT gift_mailbox_check;
ALTER TABLE public.gift_mailbox ADD CONSTRAINT gift_mailbox_reward_kind_check CHECK(reward_kind IN('ap','avatar','logo','banner'));
ALTER TABLE public.gift_mailbox ADD CONSTRAINT gift_mailbox_check CHECK((reward_kind='ap' AND amount BETWEEN 1 AND 100000 AND item_id IS NULL) OR (reward_kind IN('avatar','logo','banner') AND amount=0 AND item_id IS NOT NULL));
CREATE OR REPLACE FUNCTION public.admin_send_gift(p_recipient uuid,p_kind text,p_item text,p_amount integer,p_title text,p_body text,p_request uuid) RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path=public AS $$
DECLARE g uuid;existing public.gift_mailbox%rowtype;
BEGIN
 IF NOT public.is_admin() THEN RETURN jsonb_build_object('ok',false,'error','Admin only.'); END IF;
 IF p_request IS NULL THEN RETURN jsonb_build_object('ok',false,'error','Request identifier required.'); END IF;
 PERFORM pg_advisory_xact_lock(hashtext(p_request::text));
 SELECT * INTO existing FROM public.gift_mailbox WHERE request_id=p_request;
 IF FOUND THEN
 IF existing.created_by<>auth.uid() OR existing.recipient_id<>p_recipient OR existing.reward_kind<>p_kind OR existing.item_id IS DISTINCT FROM nullif(p_item,'') OR existing.amount<>coalesce(p_amount,0) THEN RETURN jsonb_build_object('ok',false,'error','Request identifier already used.'); END IF;
 RETURN jsonb_build_object('ok',true,'gift_id',existing.id,'already',true); END IF;
 IF NOT EXISTS(SELECT 1 FROM public.profiles WHERE id=p_recipient) THEN RETURN jsonb_build_object('ok',false,'error','Recipient not found.'); END IF;
 IF length(trim(coalesce(p_title,''))) NOT BETWEEN 1 AND 100 OR length(coalesce(p_body,''))>500 THEN RETURN jsonb_build_object('ok',false,'error','Check title/message length.'); END IF;
 IF p_kind='ap' THEN
 IF p_amount IS NULL OR p_amount NOT BETWEEN 1 AND 100000 OR nullif(p_item,'') IS NOT NULL THEN RETURN jsonb_build_object('ok',false,'error','AP must be 1-100000, with no item.'); END IF;
 ELSIF p_kind='banner' THEN
 IF coalesce(p_amount,0)<>0 OR NOT EXISTS(SELECT 1 FROM public.player_banner_catalog WHERE id=p_item AND active) THEN RETURN jsonb_build_object('ok',false,'error','Choose an available banner.'); END IF;
 ELSIF p_kind='avatar' THEN
 IF coalesce(p_amount,0)<>0 OR NOT EXISTS(SELECT 1 FROM public.avatar_catalog WHERE id=p_item AND active) THEN RETURN jsonb_build_object('ok',false,'error','Choose an available avatar.'); END IF;
 ELSIF p_kind='logo' THEN
 IF coalesce(p_amount,0)<>0 OR NOT EXISTS(SELECT 1 FROM public.squad_logos WHERE id::text=p_item AND active) THEN RETURN jsonb_build_object('ok',false,'error','Choose an available logo.'); END IF;
 ELSE RETURN jsonb_build_object('ok',false,'error','Unsupported reward.'); END IF;
 INSERT INTO public.gift_mailbox(recipient_id,created_by,request_id,title,body,reward_kind,item_id,amount)
 VALUES(p_recipient,auth.uid(),p_request,trim(p_title),coalesce(p_body,''),p_kind,nullif(p_item,''),coalesce(p_amount,0)) RETURNING id INTO g;
 PERFORM public.notify(p_recipient,'gift',trim(p_title),'A gift is waiting in your mailbox. Claim it to add the reward.','notifications.html','gift:'||g::text);
 INSERT INTO public.audit_log(actor,action,details) VALUES(auth.uid()::text,'gift_sent',jsonb_build_object('gift',g,'recipient',p_recipient,'kind',p_kind,'amount',p_amount,'item',p_item));
 RETURN jsonb_build_object('ok',true,'gift_id',g,'already',false);
END $$;
CREATE OR REPLACE FUNCTION public.my_gift_mailbox() RETURNS jsonb
LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path=public AS $$
BEGIN
 IF auth.uid() IS NULL THEN RETURN jsonb_build_object('ok',false,'error','Log in first.'); END IF;
 RETURN jsonb_build_object('ok',true,'gifts',coalesce((SELECT jsonb_agg(x ORDER BY x.created_at DESC) FROM (
 SELECT g.id,g.title,g.body,g.reward_kind,g.item_id,g.amount,g.created_at,g.expires_at,g.claimed_at,
 CASE WHEN g.reward_kind='ap' THEN g.amount||' AP' WHEN g.reward_kind='banner' THEN (SELECT title FROM public.player_banner_catalog WHERE id=g.item_id) WHEN g.reward_kind='avatar' THEN (SELECT title FROM public.avatar_catalog WHERE id=g.item_id) ELSE (SELECT title FROM public.squad_logos WHERE id::text=g.item_id) END AS reward_title
 FROM public.gift_mailbox g WHERE g.recipient_id=auth.uid() ORDER BY created_at DESC LIMIT 100)x),'[]'::jsonb));
END $$;
CREATE OR REPLACE FUNCTION public.claim_gift(p_gift uuid) RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path=public AS $$
DECLARE g public.gift_mailbox%rowtype;already_owned boolean:=false;
BEGIN
 IF auth.uid() IS NULL THEN RETURN jsonb_build_object('ok',false,'error','Log in first.'); END IF;
 SELECT * INTO g FROM public.gift_mailbox WHERE id=p_gift AND recipient_id=auth.uid() FOR UPDATE;
 IF NOT FOUND THEN RETURN jsonb_build_object('ok',false,'error','Gift not found.'); END IF;
 IF g.claimed_at IS NOT NULL THEN RETURN jsonb_build_object('ok',true,'already',true,'gift_id',g.id); END IF;
 IF g.expires_at IS NOT NULL AND g.expires_at<now() THEN RETURN jsonb_build_object('ok',false,'error','Gift expired.'); END IF;
 IF EXISTS(SELECT 1 FROM public.profiles WHERE id=auth.uid() AND banned) THEN RETURN jsonb_build_object('ok',false,'error','Account restricted.'); END IF;
 PERFORM pg_advisory_xact_lock(hashtext('ap:'||auth.uid()::text));
 IF g.reward_kind='ap' THEN
 INSERT INTO public.points_ledger(user_id,delta,kind,meta) VALUES(auth.uid(),g.amount,'admin_gift',jsonb_build_object('gift',g.id));
 ELSIF g.reward_kind='banner' THEN
 INSERT INTO public.player_banner_owners(user_id,banner_id) VALUES(auth.uid(),g.item_id) ON CONFLICT DO NOTHING;
 already_owned:=NOT FOUND;
 ELSIF g.reward_kind='avatar' THEN
 INSERT INTO public.avatar_owners(user_id,avatar_id) VALUES(auth.uid(),g.item_id) ON CONFLICT DO NOTHING;
 already_owned:=NOT FOUND;
 ELSIF g.reward_kind='logo' THEN
 INSERT INTO public.squad_logo_owners(user_id,logo_id) VALUES(auth.uid(),g.item_id::uuid) ON CONFLICT DO NOTHING;
 already_owned:=NOT FOUND;
 ELSE RAISE EXCEPTION 'Invalid gift reward'; END IF;
 UPDATE public.gift_mailbox SET claimed_at=now() WHERE id=g.id;
 INSERT INTO public.audit_log(actor,action,details) VALUES(auth.uid()::text,'gift_claimed',jsonb_build_object('gift',g.id,'kind',g.reward_kind,'already_owned',already_owned));
 RETURN jsonb_build_object('ok',true,'already',false,'already_owned',already_owned,'gift_id',g.id,'balance',public._ap_bal(auth.uid()));
END $$;
REVOKE ALL ON FUNCTION public.admin_send_gift(uuid,text,text,integer,text,text,uuid),public.my_gift_mailbox(),public.claim_gift(uuid) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.admin_send_gift(uuid,text,text,integer,text,text,uuid),public.my_gift_mailbox(),public.claim_gift(uuid) TO authenticated;
NOTIFY pgrst,'reload schema';
