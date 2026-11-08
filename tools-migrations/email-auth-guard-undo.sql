-- FIRST disable endpoint. This removes only new narrow hooks.
drop trigger if exists xr_email_recovery_guard on auth.users;
drop trigger if exists xr_email_binding_invalidate on auth.users;
drop function if exists xarena_private.email_recovery_guard();
drop function if exists xarena_private.email_binding_invalidate();
-- Restore xr_email_finish from email-auth-up.sql ONLY while endpoint OFF.
-- Binding invalidations are intentionally not restored: historical ownership
-- proof must never be revived after an address change.
