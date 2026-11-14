-- PRE-ACTIVITY ONLY. Reject rollback if any real feature activity exists.
BEGIN;
DO $$BEGIN IF EXISTS(SELECT 1 FROM public.xr_live_gift_events)OR EXISTS(SELECT 1 FROM public.xr_prediction_choices)OR EXISTS(SELECT 1 FROM public.xr_drop_claims)OR EXISTS(SELECT 1 FROM public.xr_clan_fund_entries)OR EXISTS(SELECT 1 FROM public.xr_clan_fund_distributions)THEN RAISE EXCEPTION 'Activity exists. Disable flags, retain ledger and audit; do not roll back tables.';END IF;END $$;
DROP TRIGGER IF EXISTS xr_live_drop_comment ON public.live_tournament_posts;
DROP FUNCTION public.xr_spend_campaign_close_admin(text,uuid);
DROP FUNCTION public.xr_spend_disable_admin(text);
DROP FUNCTION public.xr_spend_admin_overview();
DROP FUNCTION public.xr_spend_live_get();
DROP FUNCTION public.xr_wallet_credit_source(public.points_ledger);
DROP FUNCTION public.xr_live_drop_get(uuid);
DROP FUNCTION public.xr_drop_comment_award();
DROP FUNCTION public.xr_live_drop_open_admin(text,timestamptz);
DROP FUNCTION public.xr_prediction_get(uuid);
DROP FUNCTION public.xr_prediction_settle_admin(uuid,text,text);
DROP FUNCTION public.xr_prediction_choose(uuid,text);
DROP FUNCTION public.xr_prediction_open_admin(uuid,text,timestamptz);
DROP FUNCTION public.xr_clan_fund_get(uuid);
DROP FUNCTION public.xr_clan_fund_reward(uuid,uuid,integer,uuid);
DROP FUNCTION public.xr_clan_fund_contribute(uuid,integer,uuid);
DROP FUNCTION public.xr_live_gift_send(text,text,uuid);
DROP FUNCTION public.xr_spend_status();
DROP FUNCTION public.xr_spend_live_assert(text);
DROP FUNCTION public.xr_spend_assert(text);
ALTER FUNCTION public.xr_wallet_credit_source_20261114_base(public.points_ledger)RENAME TO xr_wallet_credit_source;
DROP TABLE public.xr_clan_fund_distributions;
DROP TABLE public.xr_clan_fund_entries;
DROP TABLE public.xr_drop_claims;
DROP TABLE public.xr_live_drops;
DROP TABLE public.xr_prediction_choices;
DROP TABLE public.xr_prediction_pools;
DROP TABLE public.xr_live_gift_events;
DROP TABLE public.xr_spend_settings;
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

NOTIFY pgrst,'reload schema';COMMIT;
