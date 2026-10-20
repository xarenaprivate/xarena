drop function if exists public.support_supporters(), public.admin_support_ack_hide(bigint), public.admin_support_ack_approve(bigint,integer), public.support_ack_submit(text,text,text,boolean);
alter table public.support_acks drop column if exists consent, drop column if exists amount_inr, drop column if exists status, drop column if exists approved_at;
-- then re-run support-up.sql function definitions for the previous submit/list/purge versions
