select cron.unschedule(jobid) from cron.job where jobname='xarena-email-auth-privacy';
drop function if exists xarena_private.email_auth_cleanup();
