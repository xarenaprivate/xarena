-- Wallet-wide, additive balances and provenance. Original ledger rows remain unchanged.
CREATE TABLE public.xr_wallet_accounts(
 user_id uuid PRIMARY KEY REFERENCES auth.users(id)ON DELETE CASCADE,
 earned bigint NOT NULL DEFAULT 0 CHECK(earned>=0),
 granted bigint NOT NULL DEFAULT 0 CHECK(granted>=0),
 unverified bigint NOT NULL DEFAULT 0 CHECK(unverified>=0),
 updated_at timestamptz NOT NULL DEFAULT clock_timestamp()
);
CREATE TABLE public.xr_wallet_allocations(
 ledger_id bigint PRIMARY KEY REFERENCES public.points_ledger(id)ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED,
 user_id uuid NOT NULL REFERENCES auth.users(id)ON DELETE CASCADE,
 earned_delta bigint NOT NULL,granted_delta bigint NOT NULL,unverified_delta bigint NOT NULL,
 reason text NOT NULL,refund_of bigint REFERENCES public.points_ledger(id),created_at timestamptz NOT NULL DEFAULT clock_timestamp()
);
CREATE UNIQUE INDEX xr_wallet_one_refund ON public.xr_wallet_allocations(refund_of)WHERE refund_of IS NOT NULL;
ALTER TABLE public.xr_wallet_accounts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.xr_wallet_allocations ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.xr_wallet_accounts,public.xr_wallet_allocations FROM PUBLIC,anon,authenticated;
GRANT ALL ON public.xr_wallet_accounts,public.xr_wallet_allocations TO service_role;
CREATE FUNCTION public.xr_wallet_credit_source(p public.points_ledger)RETURNS text LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path=public,pg_temp AS $$
BEGIN
 IF p.kind='admin_gift'AND EXISTS(SELECT 1 FROM public.gift_mailbox g WHERE g.id::text=p.meta->>'gift'AND g.recipient_id=p.user_id AND g.reward_kind='ap'AND g.amount=p.delta)THEN RETURN 'granted';END IF;
 IF p.kind IN('checkin','spin','quiz','mission','referral','referral_bonus')AND EXISTS(SELECT 1 FROM public.xp_events x WHERE x.user_id=p.user_id AND x.kind=p.kind AND x.amount=p.delta AND x.meta=p.meta)THEN RETURN 'earned';END IF;
 IF p.kind='match'AND EXISTS(SELECT 1 FROM public.xp_events x WHERE x.user_id=p.user_id AND x.kind='match'AND x.amount=p.delta AND x.meta=p.meta)AND NOT coalesce((p.meta->>'auto')::boolean,false)AND EXISTS(SELECT 1 FROM public.result_proofs r WHERE r.id::text=p.meta->>'proof'AND r.user_id=p.user_id AND r.status='approved'AND r.points=p.delta AND coalesce(r.admin_note,'')NOT LIKE 'auto-approved%')THEN RETURN 'earned';END IF;
 IF p.kind='season_pass'AND EXISTS(SELECT 1 FROM public.season_claims c JOIN public.season_rewards r ON r.level=c.level WHERE c.user_id=p.user_id AND c.level::text=p.meta->>'level'AND r.kind='ap'AND r.ap=p.delta)THEN RETURN 'earned';END IF;
 IF p.kind='voice_streak'AND EXISTS(SELECT 1 FROM public.xr_voice_streak_rewards r WHERE r.user_id=p.user_id AND r.milestone_day::text=p.meta->>'milestone_day'AND r.amount=p.delta)THEN RETURN 'earned';END IF;
 RETURN 'unverified';
END $$;
REVOKE ALL ON FUNCTION public.xr_wallet_credit_source(public.points_ledger)FROM PUBLIC,anon,authenticated;
-- One common write lock covers *all* ledger insert paths, including future code.
CREATE FUNCTION public.xr_wallet_ledger_insert()RETURNS trigger LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=public,pg_temp AS $$
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
REVOKE ALL ON FUNCTION public.xr_wallet_ledger_insert()FROM PUBLIC,anon,authenticated;
CREATE FUNCTION public.xr_wallet_immutable()RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path=public,pg_temp AS $$BEGIN
 -- Preserve auth account deletion cascades; ordinary wallet history is append-only.
 IF TG_OP='DELETE'AND NOT EXISTS(SELECT 1 FROM auth.users WHERE id=OLD.user_id)THEN RETURN OLD;END IF;
 RAISE EXCEPTION 'Wallet history is append-only; use a linked correction' USING ERRCODE='23514';END $$;
REVOKE ALL ON FUNCTION public.xr_wallet_immutable()FROM PUBLIC,anon,authenticated;
-- Migrate every historical row under a write lock. Preserve all balances and ownership.
DO $$DECLARE r public.points_ledger;a public.xr_wallet_accounts;s text;e bigint;g bigint;u bigint;c bigint;rest bigint;BEGIN
 FOR r IN SELECT * FROM public.points_ledger ORDER BY created_at,id LOOP
 INSERT INTO public.xr_wallet_accounts(user_id)VALUES(r.user_id)ON CONFLICT DO NOTHING;
 SELECT * INTO a FROM public.xr_wallet_accounts WHERE user_id=r.user_id FOR UPDATE;
 e:=0;g:=0;u:=0;
 IF r.delta>=0 THEN s:=public.xr_wallet_credit_source(r);IF s='earned'THEN e:=r.delta;ELSIF s='granted'THEN g:=r.delta;ELSE u:=r.delta;END IF;
 ELSE
 c:=-r.delta::bigint;
 IF a.earned+a.granted+a.unverified<c THEN RAISE EXCEPTION 'Historical negative balance at ledger%, owner review required',r.id;END IF;
 g:=-least(a.granted,c);rest:=c+g;u:=-least(a.unverified,rest);e:=-(rest+u);s:='historical_debit_granted_first';END IF;
 UPDATE public.xr_wallet_accounts SET earned=earned+e,granted=granted+g,unverified=unverified+u WHERE user_id=r.user_id;
 INSERT INTO public.xr_wallet_allocations(ledger_id,user_id,earned_delta,granted_delta,unverified_delta,reason)VALUES(r.id,r.user_id,e,g,u,s);
 END LOOP;
 IF EXISTS(SELECT 1 FROM public.xr_wallet_accounts acct WHERE acct.earned+acct.granted+acct.unverified<>(SELECT coalesce(sum(delta),0)FROM public.points_ledger p WHERE p.user_id=acct.user_id))THEN RAISE EXCEPTION 'Wallet reconciliation failed';END IF;
END $$;
CREATE TRIGGER xr_wallet_insert_guard BEFORE INSERT ON public.points_ledger FOR EACH ROW EXECUTE FUNCTION public.xr_wallet_ledger_insert();
CREATE TRIGGER xr_wallet_append_only BEFORE UPDATE OR DELETE ON public.points_ledger FOR EACH ROW EXECUTE FUNCTION public.xr_wallet_immutable();
CREATE FUNCTION public.xr_wallet_balance()RETURNS jsonb LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path=public,pg_temp AS $$DECLARE a public.xr_wallet_accounts;BEGIN
IF auth.uid()IS NULL THEN RETURN jsonb_build_object('ok',false,'error','Login required.');END IF;
SELECT * INTO a FROM public.xr_wallet_accounts WHERE user_id=auth.uid();
RETURN jsonb_build_object('ok',true,'balance',coalesce(a.earned,0)+coalesce(a.granted,0)+coalesce(a.unverified,0),'earned_spendable',coalesce(a.earned,0),'granted',coalesce(a.granted,0),'unverified',coalesce(a.unverified,0),'cash_value',false,'earned_only_certified',true);END $$;
REVOKE ALL ON FUNCTION public.xr_wallet_balance()FROM PUBLIC,anon;GRANT EXECUTE ON FUNCTION public.xr_wallet_balance()TO authenticated,service_role;
