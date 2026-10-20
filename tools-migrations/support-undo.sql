select cron.unschedule('support-purge') where exists(select 1 from cron.job where jobname='support-purge');
drop function if exists public.support_purge_old(), public.admin_support_ack_delete(bigint), public.admin_support_ack_seen(bigint), public.admin_support_acks(), public.support_ack_can_upload(), public.support_ack_submit(text,text,text);
drop policy if exists support_proofs_insert on storage.objects; drop policy if exists support_proofs_admin_read on storage.objects; drop policy if exists support_proofs_admin_delete on storage.objects;
drop function if exists public.support_upload_ok(uuid);
drop table if exists public.support_acks;
-- delete bucket files via dashboard/API, then: delete from storage.buckets where id='support-proofs';
