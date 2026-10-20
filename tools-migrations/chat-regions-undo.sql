-- undo chat regions: restore original chat_send, drop room column + helpers + purge cron
select cron.unschedule('chat-purge');
drop function if exists public.chat_purge_old();
drop function if exists public.chat_my_room(text,uuid);
drop function if exists public.chat_room_for(text);
CREATE OR REPLACE FUNCTION public.chat_send(p_handle text, p_token uuid, p_body text)
 RETURNS json
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare
  clean text;
  recent int;
  norm text;
  compact text;
begin
  if not exists (select 1 from chat_handles where handle = p_handle and token = p_token) then
    return json_build_object('error','unauthorized');
  end if;
  if exists (select 1 from chat_mutes where handle = p_handle and (until is null or until > now())) then
    return json_build_object('error','muted');
  end if;
  select count(*) into recent from chat_messages where handle = p_handle and created_at > now() - interval '60 seconds';
  if recent >= 8 then
    return json_build_object('error','rate_limited');
  end if;
  clean := btrim(p_body);
  clean := regexp_replace(clean, '[\x00-\x08\x0B\x0C\x0E-\x1F]', '', 'g');
  if char_length(clean) < 1 or char_length(clean) > 400 then
    return json_build_object('error','invalid');
  end if;
  if clean ~ '[<>]' or clean ~* '(https?://|www\.|\.(com|in|net|org|gg|io|me|xyz|tk|ly)([^a-z]|$))' then
    return json_build_object('error','invalid');
  end if;
  -- language filter (Hindi/English abuse). Violations are logged for Clean Cup tracking.
  norm := ' ' || lower(regexp_replace(clean, '[^a-zA-Z0-9 ]', ' ', 'g')) || ' ';
  norm := regexp_replace(norm, '\s+', ' ', 'g');
  compact := replace(norm, ' ', '');
  if compact ~ '(madarchod|behenchod|bhenchod|bhosdike|bsdk|chutiya|chutia|choot|laude|laudu|lund|randi|bhadwa|bhadwe|bhadve|chodu|kuttiya|haramkhor|gaandu|gandu|jhantu|tatte|tatti|chuslet)'
     or norm ~ ' (mc|bc|mkc|bkc|lodu|loda|harami|saale|suar|suvar|kamina|kameena|kutte|kutta|bitch|fuck|fuk|fck|chod|gand) ' then
    insert into chat_violations (handle, body) values (p_handle, left(clean, 100));
    return json_build_object('error','blocked_language');
  end if;
  insert into chat_messages (handle, body) values (p_handle, clean);
  return json_build_object('ok', true);
end $function$;
drop index if exists chat_messages_room_idx;
alter table public.chat_messages drop column if exists room;
notify pgrst, 'reload schema';
