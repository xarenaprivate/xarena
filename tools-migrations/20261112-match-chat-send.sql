-- Live match rooms in existing chat_messages. No rows seeded, flags unchanged.
BEGIN;
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
  perform pg_advisory_xact_lock(hashtextextended('xr-chat-send:' || p_handle,0));
  select count(*) into recent from chat_messages where handle = p_handle and created_at > now() - interval '60 seconds';
  if recent >= 8 then
    return json_build_object('error','rate_limited');
  end if;
  clean := btrim(p_body);
  clean := regexp_replace(clean, '[\x00-\x08\x0B\x0C\x0E-\x1F]', '', 'g');
  if clean is null or char_length(clean) < 1 or char_length(clean) > 400 then
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
  insert into chat_messages (handle, body, room) values (p_handle, clean, public.chat_room_for(p_handle));
  return json_build_object('ok', true);
end $function$
;
CREATE OR REPLACE FUNCTION public.xr_match_chat_send(p_handle text, p_token uuid, p_body text, p_room text)
 RETURNS json
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare
  match_id uuid;
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
  if p_room is null or p_room !~ '^match-[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$' then
    return json_build_object('error','invalid_room');
  end if;
  match_id := substring(p_room from 7)::uuid;
  -- Match row lock prevents a close/cancel race while the message is being committed.
  perform 1 from public.matches where id=match_id and status in ('live','ongoing','in_progress') for share;
  if not found then return json_build_object('error','match_not_live');end if;
  perform pg_advisory_xact_lock(hashtextextended('xr-chat-send:' || p_handle,0));
  select count(*) into recent from chat_messages where handle = p_handle and created_at > now() - interval '60 seconds';
  if recent >= 8 then
    return json_build_object('error','rate_limited');
  end if;
  clean := btrim(p_body);
  clean := regexp_replace(clean, '[\x00-\x08\x0B\x0C\x0E-\x1F]', '', 'g');
  if clean is null or char_length(clean) < 1 or char_length(clean) > 400 then
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
  insert into chat_messages (handle, body, room) values (p_handle, clean, 'match-' || match_id::text);
  return json_build_object('ok', true);
end $function$
;
REVOKE ALL ON FUNCTION public.xr_match_chat_send(text,uuid,text,text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.xr_match_chat_send(text,uuid,text,text) TO anon,authenticated;
NOTIFY pgrst,'reload schema';
COMMIT;
