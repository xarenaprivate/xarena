-- restore previous definitions from match_room-before.sql, match_reminder_tick-before.sql, match_reminder_15_tick-before.sql, lfg_list-before.sql
drop function if exists public.lfg_set_role(bigint,text); drop function if exists public.my_match_reminders(); drop function if exists public.set_match_reminder(text,boolean);
