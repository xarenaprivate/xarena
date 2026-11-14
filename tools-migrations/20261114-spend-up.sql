BEGIN;
CREATE TABLE public.xr_spend_settings(singleton boolean PRIMARY KEY DEFAULT true CHECK(singleton),release_authorized boolean NOT NULL DEFAULT false,gifts_enabled boolean NOT NULL DEFAULT false,predictions_enabled boolean NOT NULL DEFAULT false,drops_enabled boolean NOT NULL DEFAULT false,clan_fund_enabled boolean NOT NULL DEFAULT false,gift_mode text CHECK(gift_mode IS NULL OR gift_mode='transfer'),prediction_result_source text CHECK(prediction_result_source IS NULL OR prediction_result_source='owner_confirmed_match_result'),clan_fund_policy text CHECK(clan_fund_policy IS NULL OR clan_fund_policy='leader_member_rewards_keep_on_leave'),updated_at timestamptz NOT NULL DEFAULT clock_timestamp());
INSERT INTO public.xr_spend_settings(singleton)VALUES(true);
CREATE TABLE public.xr_live_gift_events(id uuid PRIMARY KEY DEFAULT gen_random_uuid(),request_id uuid NOT NULL,user_id uuid NOT NULL REFERENCES public.profiles(id),video_id text NOT NULL,gift_key text NOT NULL CHECK(gift_key IN('rose','fire','crown')),amount integer NOT NULL CHECK(amount IN(10,50,500)),recipient_id uuid NOT NULL REFERENCES public.profiles(id),credit_ledger bigint UNIQUE REFERENCES public.points_ledger(id),ledger_id bigint UNIQUE REFERENCES public.points_ledger(id),created_at timestamptz NOT NULL DEFAULT clock_timestamp(),UNIQUE(user_id,request_id));
CREATE TABLE public.xr_prediction_pools(id uuid PRIMARY KEY DEFAULT gen_random_uuid(),match_id uuid NOT NULL UNIQUE REFERENCES public.matches(id),video_id text NOT NULL,tournament_id text NOT NULL REFERENCES public.tournaments(id),closes_at timestamptz NOT NULL,status text NOT NULL DEFAULT 'open'CHECK(status IN('open','closed','settled','void')),winner_registration text REFERENCES public.registrations(id),result_evidence text,confirmed_by uuid REFERENCES public.profiles(id),settled_at timestamptz,created_at timestamptz NOT NULL DEFAULT clock_timestamp());
CREATE TABLE public.xr_prediction_choices(pool_id uuid NOT NULL REFERENCES public.xr_prediction_pools(id),user_id uuid NOT NULL REFERENCES public.profiles(id),registration_id text NOT NULL REFERENCES public.registrations(id),created_at timestamptz NOT NULL DEFAULT clock_timestamp(),awarded integer CHECK(awarded IN(10,200)),ledger_id bigint UNIQUE REFERENCES public.points_ledger(id),PRIMARY KEY(pool_id,user_id));
CREATE TABLE public.xr_live_drops(id uuid PRIMARY KEY DEFAULT gen_random_uuid(),video_id text NOT NULL,tournament_id text NOT NULL REFERENCES public.tournaments(id),opens_at timestamptz NOT NULL,closes_at timestamptz NOT NULL,max_winners integer NOT NULL DEFAULT 10 CHECK(max_winners=10),award integer NOT NULL DEFAULT 50 CHECK(award=50),status text NOT NULL DEFAULT 'open'CHECK(status IN('open','closed','void')),created_by uuid NOT NULL REFERENCES public.profiles(id),created_at timestamptz NOT NULL DEFAULT clock_timestamp(),CHECK(closes_at>opens_at));
CREATE TABLE public.xr_drop_claims(drop_id uuid NOT NULL REFERENCES public.xr_live_drops(id),user_id uuid NOT NULL REFERENCES public.profiles(id),comment_id bigint NOT NULL,award integer NOT NULL CHECK(award=50),ledger_id bigint UNIQUE REFERENCES public.points_ledger(id),created_at timestamptz NOT NULL DEFAULT clock_timestamp(),PRIMARY KEY(drop_id,user_id));
CREATE TABLE public.xr_clan_fund_entries(id uuid PRIMARY KEY DEFAULT gen_random_uuid(),request_id uuid NOT NULL,user_id uuid NOT NULL REFERENCES public.profiles(id),squad_id uuid NOT NULL REFERENCES public.squads(id),amount integer NOT NULL CHECK(amount>0),debit_ledger bigint NOT NULL UNIQUE REFERENCES public.points_ledger(id),created_at timestamptz NOT NULL DEFAULT clock_timestamp(),UNIQUE(user_id,request_id));
DO $$DECLARE t text;BEGIN FOREACH t IN ARRAY ARRAY['xr_spend_settings','xr_live_gift_events','xr_prediction_pools','xr_prediction_choices','xr_live_drops','xr_drop_claims','xr_clan_fund_entries']LOOP EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY',t);EXECUTE format('REVOKE ALL ON public.%I FROM PUBLIC,anon,authenticated',t);EXECUTE format('GRANT ALL ON public.%I TO service_role',t);END LOOP;END $$;
-- No enable/release setter is exposed in this staged migration.
CREATE FUNCTION public.xr_spend_assert(p_feature text)RETURNS void LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path=public,pg_temp AS $$DECLARE c public.xr_spend_settings;BEGIN
 SELECT * INTO c FROM public.xr_spend_settings WHERE singleton;
 IF NOT c.release_authorized OR NOT(CASE p_feature WHEN 'gifts'THEN c.gifts_enabled AND c.gift_mode IS NOT NULL WHEN 'predictions'THEN c.predictions_enabled AND c.prediction_result_source IS NOT NULL WHEN 'drops'THEN c.drops_enabled WHEN 'clan_fund'THEN c.clan_fund_enabled AND c.clan_fund_policy IS NOT NULL ELSE false END)THEN RAISE EXCEPTION 'Feature is staged and disabled.'USING ERRCODE='55000';END IF;END $$;
CREATE FUNCTION public.xr_spend_live_assert(p_video text)RETURNS text LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path=public,pg_temp AS $$DECLARE l jsonb;BEGIN
 SELECT value INTO l FROM public.site_settings WHERE key='youtube_live';
 IF NOT coalesce((l->>'active')::boolean,false)OR l->>'video_id'IS DISTINCT FROM p_video OR nullif(l->>'tournament_id','')IS NULL THEN RAISE EXCEPTION 'Stream binding changed or inactive.'USING ERRCODE='55000';END IF;RETURN l->>'tournament_id';END $$;
CREATE FUNCTION public.xr_spend_status()RETURNS jsonb LANGUAGE sql STABLE SECURITY DEFINER SET search_path=public,pg_temp AS $$SELECT jsonb_build_object('ok',true,'release_authorized',release_authorized,'gifts_enabled',release_authorized AND gifts_enabled AND gift_mode IS NOT NULL,'predictions_enabled',release_authorized AND predictions_enabled AND prediction_result_source IS NOT NULL,'drops_enabled',release_authorized AND drops_enabled,'clan_fund_enabled',release_authorized AND clan_fund_enabled AND clan_fund_policy IS NOT NULL,'gift_mode',gift_mode,'prediction_result_source',prediction_result_source,'clan_fund_policy',clan_fund_policy,'cash_value',false,'gift_catalog',jsonb_build_array(jsonb_build_object('key','rose','ap',10),jsonb_build_object('key','fire','ap',50),jsonb_build_object('key','crown','ap',500)),'prediction_entry_cost',0,'correct_prediction_ap',200,'incorrect_prediction_ap',10,'drop_winners',10,'drop_award_ap',50)FROM public.xr_spend_settings WHERE singleton$$;

CREATE TABLE public.xr_clan_fund_distributions(id uuid PRIMARY KEY DEFAULT gen_random_uuid(),request_id uuid NOT NULL,user_id uuid NOT NULL REFERENCES public.profiles(id),squad_id uuid NOT NULL REFERENCES public.squads(id),recipient_id uuid NOT NULL REFERENCES public.profiles(id),amount integer NOT NULL CHECK(amount>0),ledger_id bigint UNIQUE REFERENCES public.points_ledger(id),created_at timestamptz NOT NULL DEFAULT clock_timestamp(),UNIQUE(user_id,request_id));
ALTER TABLE public.xr_clan_fund_distributions ENABLE ROW LEVEL SECURITY;REVOKE ALL ON public.xr_clan_fund_distributions FROM PUBLIC,anon,authenticated;GRANT ALL ON public.xr_clan_fund_distributions TO service_role;
CREATE INDEX xr_live_gift_recent_idx ON public.xr_live_gift_events(video_id,created_at DESC);
CREATE INDEX xr_prediction_recent_idx ON public.xr_prediction_pools(video_id,created_at DESC);
CREATE INDEX xr_drop_recent_idx ON public.xr_live_drops(video_id,created_at DESC);
CREATE INDEX xr_clan_fund_squad_idx ON public.xr_clan_fund_entries(squad_id);
CREATE INDEX xr_clan_fund_distribution_idx ON public.xr_clan_fund_distributions(squad_id);

CREATE FUNCTION public.xr_live_gift_send(p_video text,p_gift text,p_request uuid)RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=public,pg_temp AS $$DECLARE u uuid:=public.xr_sf_actor();n integer;e public.xr_live_gift_events;lid bigint;cid bigint;t text;receiver uuid;lockuser uuid;BEGIN
 PERFORM public.xr_spend_assert('gifts');t:=public.xr_spend_live_assert(p_video);
 IF p_request IS NULL THEN RETURN jsonb_build_object('ok',false,'error','Request ID required.');END IF;
 n:=CASE p_gift WHEN 'rose'THEN 10 WHEN 'fire'THEN 50 WHEN 'crown'THEN 500 END;
 IF n IS NULL THEN RETURN jsonb_build_object('ok',false,'error','Unknown gift.');END IF;
 -- Lock request before choosing/locking accounts; repeated requests survive host changes safely.
 PERFORM pg_advisory_xact_lock(hashtextextended('gift-request:'||u::text||':'||p_request::text,0));
 SELECT * INTO e FROM public.xr_live_gift_events WHERE user_id=u AND request_id=p_request;
 IF FOUND THEN IF e.video_id IS DISTINCT FROM p_video OR e.gift_key IS DISTINCT FROM p_gift THEN RETURN jsonb_build_object('ok',false,'error','Request ID already used for different gift.');END IF;RETURN jsonb_build_object('ok',true,'already',true,'gift_id',e.id,'ap_spent',e.amount);END IF;
 SELECT host_user_id INTO receiver FROM public.xr_live_growth_settings WHERE singleton AND video_id=p_video FOR SHARE;
 IF receiver IS NULL OR receiver=u OR NOT EXISTS(SELECT 1 FROM public.profiles WHERE id=receiver AND NOT coalesce(banned,false))OR public.is_blocked(u,receiver)THEN RETURN jsonb_build_object('ok',false,'error','Stream host unavailable.');END IF;
 FOR lockuser IN SELECT x FROM unnest(ARRAY[u,receiver])x ORDER BY x LOOP PERFORM pg_advisory_xact_lock(hashtext('ap:'||lockuser::text));END LOOP;
 IF NOT EXISTS(SELECT 1 FROM public.xr_wallet_accounts WHERE user_id=u AND earned>=n)THEN RETURN jsonb_build_object('ok',false,'error','Not enough verified earned AP.');END IF;
 INSERT INTO public.xr_live_gift_events(request_id,user_id,video_id,gift_key,amount,recipient_id)VALUES(p_request,u,p_video,p_gift,n,receiver)RETURNING * INTO e;
 INSERT INTO public.points_ledger(user_id,delta,kind,meta)VALUES(u,-n,'live_gift',jsonb_build_object('gift_event',e.id,'wallet_scope','earned_only','mode','transfer'))RETURNING id INTO lid;
 UPDATE public.xr_live_gift_events SET ledger_id=lid WHERE id=e.id;
 INSERT INTO public.points_ledger(user_id,delta,kind,meta)VALUES(receiver,n,'live_gift_received',jsonb_build_object('gift_event',e.id))RETURNING id INTO cid;
 UPDATE public.xr_live_gift_events SET credit_ledger=cid WHERE id=e.id;
 RETURN jsonb_build_object('ok',true,'already',false,'gift_id',e.id,'ap_spent',n,'highlight_seconds',CASE WHEN p_gift='crown'THEN 10 ELSE 0 END,'audio_shoutout_guaranteed',false,'streamer_transfer',true,'receiver_bucket','granted');
END $$;
CREATE FUNCTION public.xr_clan_fund_contribute(p_squad uuid,p_amount integer,p_request uuid)RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=public,pg_temp AS $$DECLARE u uuid:=public.xr_sf_actor();e public.xr_clan_fund_entries;lid bigint;BEGIN
 PERFORM public.xr_spend_assert('clan_fund');
 IF p_amount IS NULL OR p_amount<1 OR p_request IS NULL THEN RETURN jsonb_build_object('ok',false,'error','Positive AP amount and request ID required.');END IF;
 -- All fund writers take squad lock BEFORE any AP lock, then account locks sorted.
 PERFORM pg_advisory_xact_lock(hashtextextended('clan-fund:'||p_squad::text,0));
 PERFORM pg_advisory_xact_lock(hashtext('ap:'||u::text));
 SELECT * INTO e FROM public.xr_clan_fund_entries WHERE user_id=u AND request_id=p_request;
 IF FOUND THEN IF e.squad_id IS DISTINCT FROM p_squad OR e.amount IS DISTINCT FROM p_amount THEN RETURN jsonb_build_object('ok',false,'error','Request ID already used for a different contribution.');END IF;RETURN jsonb_build_object('ok',true,'already',true,'contribution_id',e.id);END IF;
 PERFORM 1 FROM public.squad_members WHERE user_id=u AND squad_id=p_squad FOR SHARE;
 IF NOT FOUND OR NOT EXISTS(SELECT 1 FROM public.squads s JOIN public.profiles pr ON pr.id=s.captain WHERE s.id=p_squad AND NOT coalesce(pr.banned,false)AND NOT public.is_blocked(u,s.captain))THEN RETURN jsonb_build_object('ok',false,'error','Clan membership required.');END IF;
 IF NOT EXISTS(SELECT 1 FROM public.xr_wallet_accounts WHERE user_id=u AND earned>=p_amount)THEN RETURN jsonb_build_object('ok',false,'error','Not enough verified earned AP.');END IF;
 e.id:=gen_random_uuid();
 INSERT INTO public.points_ledger(user_id,delta,kind,meta)VALUES(u,-p_amount,'clan_fund',jsonb_build_object('contribution',e.id,'squad',p_squad,'wallet_scope','earned_only'))RETURNING id INTO lid;
 INSERT INTO public.xr_clan_fund_entries(id,request_id,user_id,squad_id,amount,debit_ledger)VALUES(e.id,p_request,u,p_squad,p_amount,lid);
 RETURN jsonb_build_object('ok',true,'already',false,'contribution_id',e.id,'ap_contributed',p_amount,'refundable_on_leave',false);
END $$;
CREATE FUNCTION public.xr_clan_fund_reward(p_squad uuid,p_recipient uuid,p_amount integer,p_request uuid)RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=public,pg_temp AS $$DECLARE u uuid:=public.xr_sf_actor();d public.xr_clan_fund_distributions;lid bigint;fund bigint;BEGIN
 PERFORM public.xr_spend_assert('clan_fund');
 IF p_squad IS NULL OR p_recipient IS NULL OR p_amount IS NULL OR p_amount<1 OR p_request IS NULL THEN RETURN jsonb_build_object('ok',false,'error','Clan, member, positive AP amount and request ID required.');END IF;
 PERFORM pg_advisory_xact_lock(hashtextextended('clan-fund:'||p_squad::text,0));
 SELECT * INTO d FROM public.xr_clan_fund_distributions WHERE user_id=u AND request_id=p_request;
 IF FOUND THEN IF d.squad_id IS DISTINCT FROM p_squad OR d.recipient_id IS DISTINCT FROM p_recipient OR d.amount IS DISTINCT FROM p_amount THEN RETURN jsonb_build_object('ok',false,'error','Request ID already used for another reward.');END IF;RETURN jsonb_build_object('ok',true,'already',true,'distribution_id',d.id);END IF;
 PERFORM 1 FROM public.squads WHERE id=p_squad AND captain=u FOR SHARE;
 IF NOT FOUND THEN RAISE EXCEPTION 'Only the current clan leader can give rewards.'USING ERRCODE='42501';END IF;
 PERFORM 1 FROM public.squad_members m JOIN public.profiles pr ON pr.id=m.user_id WHERE m.squad_id=p_squad AND m.user_id=p_recipient AND NOT coalesce(pr.banned,false)AND NOT public.is_blocked(u,pr.id)FOR SHARE OF m,pr;
 IF NOT FOUND THEN RETURN jsonb_build_object('ok',false,'error','Current eligible clan member required.');END IF;
 SELECT coalesce((SELECT sum(amount)FROM public.xr_clan_fund_entries WHERE squad_id=p_squad),0)-coalesce((SELECT sum(amount)FROM public.xr_clan_fund_distributions WHERE squad_id=p_squad),0)INTO fund;
 IF fund<p_amount THEN RETURN jsonb_build_object('ok',false,'error','Not enough clan fund AP.');END IF;
 PERFORM pg_advisory_xact_lock(hashtext('ap:'||p_recipient::text));
 INSERT INTO public.xr_clan_fund_distributions(request_id,user_id,squad_id,recipient_id,amount)VALUES(p_request,u,p_squad,p_recipient,p_amount)RETURNING * INTO d;
 INSERT INTO public.points_ledger(user_id,delta,kind,meta)VALUES(p_recipient,p_amount,'clan_fund_reward',jsonb_build_object('distribution',d.id))RETURNING id INTO lid;
 UPDATE public.xr_clan_fund_distributions SET ledger_id=lid WHERE id=d.id;
 RETURN jsonb_build_object('ok',true,'already',false,'distribution_id',d.id,'ap_rewarded',p_amount,'recipient_bucket','granted','fund_remaining',fund-p_amount);END $$;
CREATE FUNCTION public.xr_clan_fund_get(p_squad uuid)RETURNS jsonb LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path=public,pg_temp AS $$DECLARE u uuid:=public.xr_sf_actor();isleader boolean;BEGIN
 IF NOT EXISTS(SELECT 1 FROM public.squad_members WHERE user_id=u AND squad_id=p_squad)THEN RAISE EXCEPTION 'Clan membership required.'USING ERRCODE='42501';END IF;
 SELECT captain=u INTO isleader FROM public.squads WHERE id=p_squad;
 RETURN jsonb_build_object('ok',true,'balance',coalesce((SELECT sum(amount)FROM public.xr_clan_fund_entries WHERE squad_id=p_squad),0)-coalesce((SELECT sum(amount)FROM public.xr_clan_fund_distributions WHERE squad_id=p_squad),0),'can_reward',isleader,'refundable_on_leave',false,'contributors',coalesce((SELECT jsonb_agg(jsonb_build_object('name',name,'ap',ap))FROM(SELECT coalesce(nullif(pr.ign,''),pr.username,'Player')name,sum(e.amount)ap FROM public.xr_clan_fund_entries e JOIN public.profiles pr ON pr.id=e.user_id WHERE e.squad_id=p_squad AND NOT coalesce(pr.banned,false)AND NOT public.is_blocked(u,pr.id)GROUP BY pr.id,pr.ign,pr.username)s),'[]'::jsonb),'my_contributions',coalesce((SELECT jsonb_agg(jsonb_build_object('id',id,'amount',amount,'created_at',created_at)ORDER BY created_at DESC)FROM public.xr_clan_fund_entries WHERE squad_id=p_squad AND user_id=u),'[]'::jsonb),'cash_value',false);END $$;

CREATE FUNCTION public.xr_prediction_open_admin(p_match uuid,p_video text,p_closes timestamptz)RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=public,pg_temp AS $$DECLARE m public.matches;i uuid;t text;BEGIN
 IF auth.uid()IS NULL OR NOT public.is_admin()THEN RAISE EXCEPTION 'Admin only.'USING ERRCODE='42501';END IF;
 PERFORM public.xr_spend_assert('predictions');t:=public.xr_spend_live_assert(p_video);
 SELECT * INTO m FROM public.matches WHERE id=p_match FOR SHARE;
 IF m.id IS NULL OR m.tournament_id IS DISTINCT FROM t OR m.status NOT IN('scheduled','upcoming')OR m.starts_at IS NULL OR m.starts_at<=clock_timestamp()OR p_closes IS NULL OR p_closes>m.starts_at OR p_closes<=clock_timestamp()THEN RETURN jsonb_build_object('ok',false,'error','Choose an upcoming bound match and a close time before its start.');END IF;
 INSERT INTO public.xr_prediction_pools(match_id,video_id,tournament_id,closes_at)VALUES(p_match,p_video,t,p_closes)ON CONFLICT(match_id)DO NOTHING RETURNING id INTO i;
 RETURN jsonb_build_object('ok',i IS NOT NULL,'pool_id',i);END $$;
CREATE FUNCTION public.xr_prediction_choose(p_pool uuid,p_registration text)RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=public,pg_temp AS $$DECLARE u uuid:=public.xr_sf_actor();p public.xr_prediction_pools;r text;BEGIN
 PERFORM public.xr_spend_assert('predictions');SELECT * INTO p FROM public.xr_prediction_pools WHERE id=p_pool FOR SHARE;
 IF p.id IS NULL OR p.status<>'open'OR p.closes_at<=clock_timestamp()OR NOT EXISTS(SELECT 1 FROM public.matches WHERE id=p.match_id AND status IN('scheduled','upcoming')AND starts_at>clock_timestamp())THEN RETURN jsonb_build_object('ok',false,'error','Prediction closed.');END IF;
 PERFORM public.xr_spend_live_assert(p.video_id);
 IF NOT EXISTS(SELECT 1 FROM public.registrations reg JOIN public.profiles pr ON pr.id=reg.user_id WHERE reg.id=p_registration AND reg.tournament_id=p.tournament_id AND reg.status='confirmed'AND reg.verified AND NOT coalesce(reg.hold,false)AND NOT coalesce(pr.banned,false))THEN RETURN jsonb_build_object('ok',false,'error','Eligible confirmed squad required.');END IF;
 INSERT INTO public.xr_prediction_choices(pool_id,user_id,registration_id)VALUES(p_pool,u,p_registration)ON CONFLICT DO NOTHING;
 SELECT registration_id INTO r FROM public.xr_prediction_choices WHERE pool_id=p_pool AND user_id=u;
 RETURN jsonb_build_object('ok',r=p_registration,'choice',r,'entry_ap',0,'error',CASE WHEN r<>p_registration THEN 'Prediction is already locked to another squad.'END);END $$;
CREATE FUNCTION public.xr_prediction_settle_admin(p_pool uuid,p_winner text,p_result_evidence text)RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=public,pg_temp AS $$DECLARE p public.xr_prediction_pools;c record;n integer;lid bigint;cnt integer:=0;BEGIN
 IF auth.uid()IS NULL OR NOT public.is_admin()THEN RAISE EXCEPTION 'Admin only.'USING ERRCODE='42501';END IF;
 PERFORM public.xr_spend_assert('predictions');SELECT * INTO p FROM public.xr_prediction_pools WHERE id=p_pool FOR UPDATE;
 IF p.id IS NULL THEN RETURN jsonb_build_object('ok',false,'error','Pool not found.');END IF;
 IF p.status='settled'THEN RETURN jsonb_build_object('ok',p.winner_registration=p_winner,'already',true);END IF;
 IF p.status='void'OR p.closes_at>clock_timestamp()OR NOT EXISTS(SELECT 1 FROM public.matches WHERE id=p.match_id AND status='done')OR NOT EXISTS(SELECT 1 FROM public.registrations WHERE id=p_winner AND tournament_id=p.tournament_id)OR char_length(btrim(coalesce(p_result_evidence,'')))NOT BETWEEN 10 AND 500 THEN RETURN jsonb_build_object('ok',false,'error','Finished match, actual winning registration and owner-confirmed result evidence required.');END IF;
 UPDATE public.xr_prediction_pools SET status='closed',winner_registration=p_winner,result_evidence=btrim(p_result_evidence),confirmed_by=auth.uid()WHERE id=p.id;
 -- Sorted account locks for batch settlement. Choice writers only hold pool SHARE, not AP locks.
 FOR c IN SELECT pc.user_id,pc.registration_id FROM public.xr_prediction_choices pc JOIN public.profiles pr ON pr.id=pc.user_id WHERE pc.pool_id=p.id AND NOT coalesce(pr.banned,false)ORDER BY pc.user_id LOOP
 PERFORM pg_advisory_xact_lock(hashtext('ap:'||c.user_id::text));
 n:=CASE WHEN c.registration_id=p_winner THEN 200 ELSE 10 END;
 UPDATE public.xr_prediction_choices SET awarded=n WHERE pool_id=p.id AND user_id=c.user_id;
 INSERT INTO public.points_ledger(user_id,delta,kind,meta)VALUES(c.user_id,n,'live_prediction',jsonb_build_object('pool',p.id))RETURNING id INTO lid;
 UPDATE public.xr_prediction_choices SET ledger_id=lid WHERE pool_id=p.id AND user_id=c.user_id;cnt:=cnt+1;
 END LOOP;
 UPDATE public.xr_prediction_pools SET status='settled',settled_at=clock_timestamp()WHERE id=p.id;RETURN jsonb_build_object('ok',true,'already',false,'rewarded',cnt,'cash_value',false);END $$;
CREATE FUNCTION public.xr_prediction_get(p_pool uuid)RETURNS jsonb LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path=public,pg_temp AS $$DECLARE u uuid:=public.xr_sf_actor();p public.xr_prediction_pools;BEGIN
 SELECT * INTO p FROM public.xr_prediction_pools WHERE id=p_pool;
 IF p.id IS NULL THEN RETURN jsonb_build_object('ok',false,'error','Not found.');END IF;
 RETURN jsonb_build_object('ok',true,'pool_id',p.id,'match_id',p.match_id,'closes_at',p.closes_at,'status',CASE WHEN p.status='open'AND p.closes_at<=statement_timestamp()THEN 'closed'ELSE p.status END,'winning_registration',p.winner_registration,'my_choice',(SELECT registration_id FROM public.xr_prediction_choices WHERE pool_id=p.id AND user_id=u),'my_award',(SELECT awarded FROM public.xr_prediction_choices WHERE pool_id=p.id AND user_id=u),'entry_ap',0,'correct_ap',200,'incorrect_ap',10,'choices',coalesce((SELECT jsonb_agg(jsonb_build_object('registration_id',id,'squad',squad))FROM public.registrations WHERE tournament_id=p.tournament_id AND status='confirmed'AND verified AND NOT coalesce(hold,false)),'[]'::jsonb));END $$;

CREATE FUNCTION public.xr_live_drop_open_admin(p_video text,p_closes timestamptz)RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=public,pg_temp AS $$DECLARE t text;i uuid;BEGIN
 IF auth.uid()IS NULL OR NOT public.is_admin()THEN RAISE EXCEPTION 'Admin only.'USING ERRCODE='42501';END IF;PERFORM public.xr_spend_assert('drops');t:=public.xr_spend_live_assert(p_video);
 IF NOT public._flag('live_thread')OR p_closes IS NULL OR p_closes<=clock_timestamp()OR p_closes>clock_timestamp()+interval '1 hour'THEN RETURN jsonb_build_object('ok',false,'error','Open live comments and close drop within an hour.');END IF;
 PERFORM pg_advisory_xact_lock(hashtextextended('drop-video:'||p_video,0));
 IF EXISTS(SELECT 1 FROM public.xr_live_drops WHERE video_id=p_video AND status='open'AND closes_at>clock_timestamp())THEN RETURN jsonb_build_object('ok',false,'error','A drop is already open.');END IF;
 INSERT INTO public.xr_live_drops(video_id,tournament_id,opens_at,closes_at,created_by)VALUES(p_video,t,clock_timestamp(),p_closes,auth.uid())RETURNING id INTO i;RETURN jsonb_build_object('ok',true,'drop_id',i,'winner_limit',10,'award_ap',50);END $$;
-- Trigger runs in the SAME transaction as the moderated/rate-limited live comment RPC.
-- It ranks serialized eligible comments, not fastest claim calls. No watch presence claim.
CREATE FUNCTION public.xr_drop_comment_award()RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path=public,pg_temp AS $$DECLARE d public.xr_live_drops;l jsonb;lid bigint;BEGIN
 IF NEW.hidden OR NEW.user_id IS NULL OR NOT EXISTS(SELECT 1 FROM public.profiles WHERE id=NEW.user_id AND NOT coalesce(banned,false))THEN RETURN NEW;END IF;
 IF NOT EXISTS(SELECT 1 FROM public.xr_spend_settings WHERE singleton AND release_authorized AND drops_enabled)OR NOT public._flag('live_thread')THEN RETURN NEW;END IF;
 SELECT value INTO l FROM public.site_settings WHERE key='youtube_live';IF NOT coalesce((l->>'active')::boolean,false)OR l->>'tournament_id'IS DISTINCT FROM NEW.tournament_id THEN RETURN NEW;END IF;
 FOR d IN SELECT * FROM public.xr_live_drops WHERE tournament_id=NEW.tournament_id AND video_id=l->>'video_id'AND status='open'AND opens_at<=NEW.created_at AND closes_at>clock_timestamp()ORDER BY id FOR UPDATE LOOP
 IF EXISTS(SELECT 1 FROM public.xr_drop_claims WHERE drop_id=d.id AND user_id=NEW.user_id)OR(SELECT count(*)FROM public.xr_drop_claims WHERE drop_id=d.id)>=10 THEN CONTINUE;END IF;
 INSERT INTO public.xr_drop_claims(drop_id,user_id,comment_id,award)VALUES(d.id,NEW.user_id,NEW.id,50);
 INSERT INTO public.points_ledger(user_id,delta,kind,meta)VALUES(NEW.user_id,50,'live_drop',jsonb_build_object('drop',d.id,'comment',NEW.id))RETURNING id INTO lid;
 UPDATE public.xr_drop_claims SET ledger_id=lid WHERE drop_id=d.id AND user_id=NEW.user_id;
 END LOOP;RETURN NEW;END $$;
CREATE TRIGGER xr_live_drop_comment AFTER INSERT ON public.live_tournament_posts FOR EACH ROW EXECUTE FUNCTION public.xr_drop_comment_award();
CREATE FUNCTION public.xr_live_drop_get(p_drop uuid)RETURNS jsonb LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path=public,pg_temp AS $$DECLARE u uuid:=public.xr_sf_actor();d public.xr_live_drops;BEGIN SELECT * INTO d FROM public.xr_live_drops WHERE id=p_drop;
 IF d.id IS NULL THEN RETURN jsonb_build_object('ok',false,'error','Not found.');END IF;
 RETURN jsonb_build_object('ok',true,'drop_id',d.id,'opens_at',d.opens_at,'closes_at',d.closes_at,'status',CASE WHEN d.status='open'AND d.closes_at<=statement_timestamp()THEN 'closed'ELSE d.status END,'winner_limit',10,'winners',(SELECT count(*)FROM public.xr_drop_claims WHERE drop_id=d.id),'my_award',(SELECT award FROM public.xr_drop_claims WHERE drop_id=d.id AND user_id=u),'award_ap',50,'qualification','first_10_unique_eligible_live_commenters','viewer_presence_verified',false);END $$;

ALTER FUNCTION public.xr_wallet_credit_source(public.points_ledger)RENAME TO xr_wallet_credit_source_20261114_base;
CREATE FUNCTION public.xr_wallet_credit_source(p public.points_ledger)RETURNS text LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path=public,pg_temp AS $$BEGIN
 IF p.delta>0 AND p.kind='live_prediction'AND EXISTS(SELECT 1 FROM public.xr_prediction_choices c JOIN public.xr_prediction_pools r ON r.id=c.pool_id WHERE r.id::text=p.meta->>'pool'AND c.user_id=p.user_id AND c.awarded=p.delta AND c.ledger_id IS NULL AND r.status='closed'AND r.confirmed_by IS NOT NULL AND r.winner_registration IS NOT NULL AND p.delta=CASE WHEN c.registration_id=r.winner_registration THEN 200 ELSE 10 END)THEN RETURN 'earned';END IF;
 IF p.delta=50 AND p.kind='live_drop'AND EXISTS(SELECT 1 FROM public.xr_drop_claims c JOIN public.xr_live_drops d ON d.id=c.drop_id JOIN public.live_tournament_posts m ON m.id=c.comment_id WHERE c.drop_id::text=p.meta->>'drop'AND c.comment_id::text=p.meta->>'comment'AND c.user_id=p.user_id AND c.ledger_id IS NULL AND c.award=50 AND m.user_id=p.user_id AND m.tournament_id=d.tournament_id AND NOT m.hidden AND m.created_at>=d.opens_at AND m.created_at<d.closes_at)THEN RETURN 'earned';END IF;

 IF p.delta>0 AND p.kind='live_gift_received'AND EXISTS(SELECT 1 FROM public.xr_live_gift_events e JOIN public.points_ledger d ON d.id=e.ledger_id WHERE e.id::text=p.meta->>'gift_event'AND e.recipient_id=p.user_id AND e.amount=p.delta AND e.credit_ledger IS NULL AND d.user_id=e.user_id AND d.delta=-e.amount AND d.kind='live_gift')THEN RETURN 'granted';END IF;
 IF p.delta>0 AND p.kind='clan_fund_reward'AND EXISTS(SELECT 1 FROM public.xr_clan_fund_distributions d WHERE d.id::text=p.meta->>'distribution'AND d.recipient_id=p.user_id AND d.amount=p.delta AND d.ledger_id IS NULL)THEN RETURN 'granted';END IF;
 RETURN public.xr_wallet_credit_source_20261114_base(p);END $$;
REVOKE ALL ON FUNCTION public.xr_wallet_credit_source_20261114_base(public.points_ledger)FROM PUBLIC,anon,authenticated;

CREATE FUNCTION public.xr_spend_live_get()RETURNS jsonb LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path=public,pg_temp AS $$DECLARE u uuid:=auth.uid();l jsonb;h uuid;hn text;giftlist jsonb;prediction jsonb;dropitem jsonb;BEGIN
 IF u IS NOT NULL THEN PERFORM public.xr_sf_actor();END IF;
 SELECT value INTO l FROM public.site_settings WHERE key='youtube_live';
 IF NOT coalesce((l->>'active')::boolean,false)THEN RETURN jsonb_build_object('ok',true,'active',false,'settings',public.xr_spend_status(),'gifts','[]'::jsonb);END IF;
 SELECT cfg.host_user_id,coalesce(nullif(pr.ign,''),pr.username,'Player')INTO h,hn FROM public.xr_live_growth_settings cfg JOIN public.profiles pr ON pr.id=cfg.host_user_id WHERE cfg.singleton AND cfg.video_id=l->>'video_id'AND NOT coalesce(pr.banned,false)AND(u IS NULL OR NOT public.is_blocked(u,pr.id));
 SELECT coalesce(jsonb_agg(item ORDER BY created_at DESC),'[]'::jsonb)INTO giftlist FROM(SELECT e.created_at,jsonb_build_object('id',e.id,'sender',coalesce(nullif(pr.ign,''),pr.username,'Player'),'gift_key',e.gift_key,'amount',e.amount,'created_at',e.created_at,'highlight_until',CASE WHEN e.gift_key='crown'THEN e.created_at+interval '10 seconds'END)item FROM public.xr_live_gift_events e JOIN public.profiles pr ON pr.id=e.user_id WHERE e.video_id=l->>'video_id'AND NOT coalesce(pr.banned,false)AND(u IS NULL OR NOT public.is_blocked(u,pr.id))ORDER BY e.created_at DESC LIMIT 20)s;
 SELECT jsonb_build_object('id',id,'match_id',match_id,'closes_at',closes_at,'status',CASE WHEN status='open'AND closes_at<=statement_timestamp()THEN 'closed'ELSE status END,'my_choice',(SELECT registration_id FROM public.xr_prediction_choices WHERE pool_id=p.id AND user_id=u),'my_award',(SELECT awarded FROM public.xr_prediction_choices WHERE pool_id=p.id AND user_id=u),'winner_registration',winner_registration,'entry_ap',0,'correct_ap',200,'incorrect_ap',10,'choices',coalesce((SELECT jsonb_agg(jsonb_build_object('registration_id',r.id,'squad',r.squad))FROM public.registrations r JOIN public.profiles pr ON pr.id=r.user_id WHERE r.tournament_id=p.tournament_id AND r.status='confirmed'AND r.verified AND NOT coalesce(r.hold,false)AND NOT coalesce(pr.banned,false)),'[]'::jsonb))INTO prediction FROM public.xr_prediction_pools p WHERE video_id=l->>'video_id'AND tournament_id=l->>'tournament_id'ORDER BY created_at DESC LIMIT 1;
 SELECT jsonb_build_object('id',id,'opens_at',opens_at,'closes_at',closes_at,'status',CASE WHEN status='open'AND closes_at<=statement_timestamp()THEN 'closed'ELSE status END,'winners',(SELECT count(*)FROM public.xr_drop_claims WHERE drop_id=d.id),'my_award',(SELECT award FROM public.xr_drop_claims WHERE drop_id=d.id AND user_id=u),'winner_limit',10,'award_ap',50,'qualification','first_10_unique_eligible_live_commenters')INTO dropitem FROM public.xr_live_drops d WHERE video_id=l->>'video_id'AND tournament_id=l->>'tournament_id'ORDER BY created_at DESC LIMIT 1;
 RETURN jsonb_build_object('ok',true,'active',true,'settings',public.xr_spend_status(),'video_id',l->>'video_id','tournament_id',l->>'tournament_id','host_name',hn,'host_available',h IS NOT NULL AND h IS DISTINCT FROM u,'gifts',giftlist,'prediction',prediction,'drop',dropitem,'cache_seconds',30);END $$;
CREATE FUNCTION public.xr_spend_admin_overview()RETURNS jsonb LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path=public,pg_temp AS $$BEGIN
 IF auth.uid()IS NULL OR NOT public.is_admin()THEN RAISE EXCEPTION 'Admin only.'USING ERRCODE='42501';END IF;
 RETURN jsonb_build_object('ok',true,'settings',public.xr_spend_status(),'live',(SELECT value FROM public.site_settings WHERE key='youtube_live'),'host_user_id',(SELECT host_user_id FROM public.xr_live_growth_settings WHERE singleton),'matches',coalesce((SELECT jsonb_agg(jsonb_build_object('id',id,'title',title,'starts_at',starts_at,'status',status,'tournament_id',tournament_id))FROM public.matches WHERE tournament_id=(SELECT value->>'tournament_id'FROM public.site_settings WHERE key='youtube_live')),'[]'::jsonb),'pools',coalesce((SELECT jsonb_agg(jsonb_build_object('id',id,'match_id',match_id,'status',status,'winner_registration',winner_registration,'closes_at',closes_at,'settled_at',settled_at))FROM public.xr_prediction_pools WHERE video_id=(SELECT value->>'video_id'FROM public.site_settings WHERE key='youtube_live')),'[]'::jsonb));END $$;

CREATE FUNCTION public.xr_spend_disable_admin(p_feature text DEFAULT 'all')RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=public,pg_temp AS $$BEGIN
 IF auth.uid()IS NULL OR NOT public.is_admin()THEN RAISE EXCEPTION 'Admin only.'USING ERRCODE='42501';END IF;
 IF p_feature NOT IN('all','gifts','predictions','drops','clan_fund')OR p_feature IS NULL THEN RETURN jsonb_build_object('ok',false,'error','Unknown feature.');END IF;
 UPDATE public.xr_spend_settings SET release_authorized=CASE WHEN p_feature='all'THEN false ELSE release_authorized END,gifts_enabled=CASE WHEN p_feature IN('all','gifts')THEN false ELSE gifts_enabled END,predictions_enabled=CASE WHEN p_feature IN('all','predictions')THEN false ELSE predictions_enabled END,drops_enabled=CASE WHEN p_feature IN('all','drops')THEN false ELSE drops_enabled END,clan_fund_enabled=CASE WHEN p_feature IN('all','clan_fund')THEN false ELSE clan_fund_enabled END,updated_at=clock_timestamp()WHERE singleton;
 RETURN jsonb_build_object('ok',true,'settings',public.xr_spend_status());END $$;
CREATE FUNCTION public.xr_spend_campaign_close_admin(p_kind text,p_id uuid)RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=public,pg_temp AS $$BEGIN
 IF auth.uid()IS NULL OR NOT public.is_admin()THEN RAISE EXCEPTION 'Admin only.'USING ERRCODE='42501';END IF;
 IF p_kind='prediction'THEN UPDATE public.xr_prediction_pools SET status='void'WHERE id=p_id AND status IN('open','closed');ELSIF p_kind='drop'THEN UPDATE public.xr_live_drops SET status='closed'WHERE id=p_id AND status='open';ELSE RETURN jsonb_build_object('ok',false,'error','Unknown campaign type.');END IF;
 RETURN jsonb_build_object('ok',true,'changed',FOUND);END $$;

REVOKE ALL ON FUNCTION public.xr_spend_assert(text) FROM PUBLIC,anon,authenticated;
REVOKE ALL ON FUNCTION public.xr_spend_live_assert(text) FROM PUBLIC,anon,authenticated;
REVOKE ALL ON FUNCTION public.xr_spend_status() FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.xr_spend_status() TO authenticated;
REVOKE ALL ON FUNCTION public.xr_live_gift_send(text,text,uuid) FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.xr_live_gift_send(text,text,uuid) TO authenticated;
REVOKE ALL ON FUNCTION public.xr_clan_fund_contribute(uuid,integer,uuid) FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.xr_clan_fund_contribute(uuid,integer,uuid) TO authenticated;
REVOKE ALL ON FUNCTION public.xr_clan_fund_reward(uuid,uuid,integer,uuid) FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.xr_clan_fund_reward(uuid,uuid,integer,uuid) TO authenticated;
REVOKE ALL ON FUNCTION public.xr_clan_fund_get(uuid) FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.xr_clan_fund_get(uuid) TO authenticated;
REVOKE ALL ON FUNCTION public.xr_prediction_open_admin(uuid,text,timestamptz) FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.xr_prediction_open_admin(uuid,text,timestamptz) TO authenticated;
REVOKE ALL ON FUNCTION public.xr_prediction_choose(uuid,text) FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.xr_prediction_choose(uuid,text) TO authenticated;
REVOKE ALL ON FUNCTION public.xr_prediction_settle_admin(uuid,text,text) FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.xr_prediction_settle_admin(uuid,text,text) TO authenticated;
REVOKE ALL ON FUNCTION public.xr_prediction_get(uuid) FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.xr_prediction_get(uuid) TO authenticated;
REVOKE ALL ON FUNCTION public.xr_live_drop_open_admin(text,timestamptz) FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.xr_live_drop_open_admin(text,timestamptz) TO authenticated;
REVOKE ALL ON FUNCTION public.xr_drop_comment_award() FROM PUBLIC,anon,authenticated;
REVOKE ALL ON FUNCTION public.xr_live_drop_get(uuid) FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.xr_live_drop_get(uuid) TO authenticated;
REVOKE ALL ON FUNCTION public.xr_wallet_credit_source(public.points_ledger) FROM PUBLIC,anon,authenticated;
REVOKE ALL ON FUNCTION public.xr_spend_live_get() FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.xr_spend_live_get() TO authenticated;
REVOKE ALL ON FUNCTION public.xr_spend_admin_overview() FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.xr_spend_admin_overview() TO authenticated;
REVOKE ALL ON FUNCTION public.xr_spend_disable_admin(text) FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.xr_spend_disable_admin(text) TO authenticated;
REVOKE ALL ON FUNCTION public.xr_spend_campaign_close_admin(text,uuid) FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.xr_spend_campaign_close_admin(text,uuid) TO authenticated;
CREATE OR REPLACE FUNCTION public.xr_wallet_ledger_insert()RETURNS trigger LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=public,pg_temp AS $$
DECLARE a public.xr_wallet_accounts;source text;e bigint:=0;g bigint:=0;u bigint:=0;cost bigint;rest bigint;original public.xr_wallet_allocations;original_row public.points_ledger;ref bigint;
BEGIN
 PERFORM pg_advisory_xact_lock(hashtext('ap:'||NEW.user_id::text));
 INSERT INTO public.xr_wallet_accounts(user_id)VALUES(NEW.user_id)ON CONFLICT DO NOTHING;
 SELECT * INTO a FROM public.xr_wallet_accounts WHERE user_id=NEW.user_id FOR UPDATE;
 IF NEW.delta>0 THEN
  source:=public.xr_wallet_credit_source(NEW);
  IF NEW.meta->>'wallet_refund_ledger'IS NOT NULL THEN
   ref:=(NEW.meta->>'wallet_refund_ledger')::bigint;
   SELECT * INTO original FROM public.xr_wallet_allocations WHERE ledger_id=ref AND user_id=NEW.user_id;
   SELECT * INTO original_row FROM public.points_ledger WHERE id=ref AND user_id=NEW.user_id;
   IF original.ledger_id IS NULL OR original_row.delta<>-NEW.delta OR EXISTS(SELECT 1 FROM public.xr_wallet_allocations WHERE refund_of=ref)THEN RAISE EXCEPTION 'Invalid or duplicate wallet refund' USING ERRCODE='23514';END IF;
   e:=-original.earned_delta;g:=-original.granted_delta;u:=-original.unverified_delta;source:='linked_refund';
  ELSIF source='earned'THEN e:=NEW.delta;
  ELSIF source='granted'THEN g:=NEW.delta;
  ELSE u:=NEW.delta;END IF;
 ELSIF NEW.delta<0 THEN
  cost:=-NEW.delta::bigint;
  IF a.earned+a.granted+a.unverified<cost THEN RAISE EXCEPTION 'Not enough AP; wallet debit rejected' USING ERRCODE='23514';END IF;
  source:='debit_granted_then_unverified_then_earned';
  IF coalesce(NEW.meta->>'wallet_scope','')='earned_only'THEN
   IF a.earned<cost THEN RAISE EXCEPTION 'Not enough verified earned AP' USING ERRCODE='23514';END IF;e:=-cost;source:='earned_only_debit';
  ELSE
   g:=-least(a.granted,cost);rest:=cost+g;u:=-least(a.unverified,rest);rest:=rest+u;e:=-rest;
  END IF;
 ELSE source:='zero';END IF;
 UPDATE public.xr_wallet_accounts SET earned=earned+e,granted=granted+g,unverified=unverified+u,updated_at=clock_timestamp()WHERE user_id=NEW.user_id;
 INSERT INTO public.xr_wallet_allocations(ledger_id,user_id,earned_delta,granted_delta,unverified_delta,reason,refund_of)VALUES(NEW.id,NEW.user_id,e,g,u,source,ref);
 RETURN NEW;
END $$;

GRANT EXECUTE ON FUNCTION public.xr_spend_status(),public.xr_spend_live_get()TO anon;
NOTIFY pgrst,'reload schema';
COMMIT;
