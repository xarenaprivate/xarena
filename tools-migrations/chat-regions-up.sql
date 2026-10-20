-- chat regions: room per coarse state (server-derived), 6h retention purge. Additive.
alter table public.chat_messages add column if not exists room text not null default 'general';
create index if not exists chat_messages_room_idx on public.chat_messages(room, id desc);

create or replace function public.chat_room_for(p_handle text) returns text
language sql stable security definer set search_path to 'public' as $$
  select coalesce(
    (select 'st-' || left(regexp_replace(lower(p.geo_state),'[^a-z0-9]+','-','g'),40)
       from chat_handles h join profiles p on p.id = h.user_id
      where h.handle = p_handle and nullif(trim(p.geo_state),'') is not null limit 1),
    'general');
$$;

create or replace function public.chat_my_room(p_handle text, p_token uuid) returns jsonb
language plpgsql stable security definer set search_path to 'public' as $$
declare r text; lbl text;
begin
  if not exists (select 1 from chat_handles where handle = p_handle and token = p_token) then
    return jsonb_build_object('room','general','label','General','scope','general');
  end if;
  r := chat_room_for(p_handle);
  if r = 'general' then return jsonb_build_object('room','general','label','General','scope','general'); end if;
  select p.geo_state into lbl from chat_handles h join profiles p on p.id=h.user_id where h.handle=p_handle;
  return jsonb_build_object('room',r,'label',lbl,'scope','state');
end $$;
grant execute on function public.chat_room_for(text) to anon, authenticated;
grant execute on function public.chat_my_room(text,uuid) to anon, authenticated;

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
  insert into chat_messages (handle, body, room) values (p_handle, clean, public.chat_room_for(p_handle));
  return json_build_object('ok', true);
end $function$;

create or replace function public.chat_purge_old() returns int
language plpgsql security definer set search_path to 'public' as $$
declare n int;
begin
  delete from chat_messages m where m.created_at < now() - interval '6 hours'
    and not exists (select 1 from chat_reports r where r.message_id = m.id and not coalesce(r.resolved,false));
  get diagnostics n = row_count;
  return n;
end $$;
revoke all on function public.chat_purge_old() from public, anon, authenticated;
select cron.schedule('chat-purge','7 * * * *','select public.chat_purge_old()');
notify pgrst, 'reload schema';
