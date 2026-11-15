CREATE OR REPLACE FUNCTION public.room_feed(p_after bigint DEFAULT 0)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare v_uid uuid := auth.uid(); v_squad uuid;
begin
  if v_uid is null then return jsonb_build_object('ok', false, 'error', 'Log in first.'); end if;
  select squad_id into v_squad from public.squad_members where user_id = v_uid;
  if v_squad is null then return jsonb_build_object('ok', false, 'error', 'no_squad'); end if;
  return jsonb_build_object('ok', true, 'squad', v_squad, 'rows', coalesce((
    select jsonb_agg(t) from (
      select m.id, m.body, m.created_at, m.user_id,
             coalesce(pr.ign, pr.username, 'player') as by
      from public.squad_room_messages m
      left join public.profiles pr on pr.id = m.user_id
      where m.squad_id = v_squad and m.id > p_after
      order by m.id asc limit 100
    ) t), '[]'::jsonb));
end $function$

CREATE OR REPLACE FUNCTION public.room_info()
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare v_uid uuid := auth.uid(); v_squad uuid; v_name text; v_tag text; v_members int;
begin
  if v_uid is null then return jsonb_build_object('ok', false, 'error', 'Log in to enter your clan room.'); end if;
  select squad_id into v_squad from public.squad_members where user_id = v_uid;
  if v_squad is null then return jsonb_build_object('ok', false, 'error', 'no_squad'); end if;
  select name, tag into v_name, v_tag from public.squads where id = v_squad;
  select count(*) into v_members from public.squad_members where squad_id = v_squad;
  return jsonb_build_object('ok', true, 'squad', v_squad, 'name', v_name, 'tag', v_tag, 'members', v_members);
end $function$

CREATE OR REPLACE FUNCTION public.room_post(p_body text)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare v_uid uuid := auth.uid(); v_squad uuid; v_body text := trim(coalesce(p_body, ''));
begin
  if v_uid is null then return jsonb_build_object('ok', false, 'error', 'Log in first.'); end if;
  select squad_id into v_squad from public.squad_members where user_id = v_uid;
  if v_squad is null then return jsonb_build_object('ok', false, 'error', 'Join a squad first - clan rooms are squad-only.'); end if;
  if char_length(v_body) < 1 or char_length(v_body) > 300 then
    return jsonb_build_object('ok', false, 'error', 'Message must be 1-300 characters.'); end if;
  insert into public.squad_room_messages (squad_id, user_id, body) values (v_squad, v_uid, v_body);
  return jsonb_build_object('ok', true);
end $function$
