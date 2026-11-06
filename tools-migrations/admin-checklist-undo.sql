DROP FUNCTION IF EXISTS public.admin_checklist_stats();
DROP FUNCTION IF EXISTS public.admin_compose_notification(uuid,text,text);
DROP FUNCTION IF EXISTS public.admin_resolve_feedback(uuid,text);
-- Deletes only this feature's read cursors, never messages/accounts.
DROP FUNCTION IF EXISTS public.admin_account_action(uuid,text,text);
DROP FUNCTION IF EXISTS public.admin_account_directory(text,text,integer);
DROP FUNCTION IF EXISTS public.dm_unread_summary();
DROP FUNCTION IF EXISTS public.dm_mark_read(uuid,uuid);
DROP TABLE IF EXISTS public.dm_read_cursors;
NOTIFY pgrst,'reload schema';
