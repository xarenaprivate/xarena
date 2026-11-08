-- NEW isolated hourly privacy job. Review cron privileges/availability first.
create or replace function xarena_private.email_auth_cleanup()
returns void language plpgsql security definer set search_path=pg_catalog as $$
begin
 perform pg_advisory_xact_lock(842003711);
 delete from xarena_private.email_challenges c where c.created_at<clock_timestamp()-interval '48 hours'
 and not exists(select 1 from xarena_private.email_ownership b where b.challenge_id=c.id);
 update xarena_private.email_challenges c set email='',code_hash=repeat('0',64),email_hash='',ip_hash='',device_hash=''
 where c.created_at<clock_timestamp()-interval '24 hours' and c.state='complete' and c.email<>'';
end $$;
revoke all on function xarena_private.email_auth_cleanup() from public,anon,authenticated;
-- Hourly cleanup, not an exact48h deletion promise: up to~49h normally.
select cron.schedule('xarena-email-auth-privacy','17 * * * *','select xarena_private.email_auth_cleanup();');
