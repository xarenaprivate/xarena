-- Rollback removes pending/claimed mailbox history, not already granted inventory/AP.
-- Never reverse legitimately claimed rewards automatically.
DROP FUNCTION IF EXISTS public.admin_send_gift(uuid,text,text,integer,text,text,uuid);
DROP FUNCTION IF EXISTS public.my_gift_mailbox();
DROP FUNCTION IF EXISTS public.claim_gift(uuid);
DROP TABLE IF EXISTS public.gift_mailbox;
NOTIFY pgrst,'reload schema';
