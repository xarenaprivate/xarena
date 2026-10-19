CREATE OR REPLACE FUNCTION public.push_dispatch()
 RETURNS TABLE(user_id uuid, kind text, title text, body text)
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
#variable_conflict use_variable
declare
  u record; r record;
  v_squad uuid; v_squad_name text; v_now timestamptz := now();
begin
  insert into public.push_dispatch_state (user_id)
  select distinct dt.user_id from public.device_tokens dt
  where dt.user_id is not null
    and not exists (select 1 from public.push_dispatch_state x where x.user_id = dt.user_id);

  for u in select ds.* from public.push_dispatch_state ds
    where exists (select 1 from public.device_tokens dt where dt.user_id = ds.user_id)
  loop
    if not u.baseline_done then
      update public.push_dispatch_state ds2 set
        since_notif = coalesce((select max(n.id) from public.notifications n where n.user_id = u.user_id), -1),
        since_dm = v_now, since_room = v_now, since_tourn = v_now,
        baseline_done = true, updated_at = v_now
      where ds2.user_id = u.user_id;
      continue;
    end if;

    for r in select n.id, n.title as n_title, n.body as n_body from public.notifications n
      where n.user_id = u.user_id and n.id > u.since_notif order by n.id asc limit 10
    loop
      user_id := u.user_id; kind := 'notification'; title := r.n_title; body := r.n_body; return next;
      update public.push_dispatch_state ds2 set since_notif = greatest(ds2.since_notif, r.id) where ds2.user_id = u.user_id;
    end loop;

    for r in select coalesce(pr.ign, pr.username, 'player') as sender_name, count(*)::int as c
      from public.dm_messages m
      join public.dm_threads th on th.id = m.thread_id
      left join public.profiles pr on pr.id = m.sender_id
      where (th.user_a = u.user_id or th.user_b = u.user_id)
        and m.sender_id <> u.user_id and m.created_at > u.since_dm
      group by 1 limit 10
    loop
      user_id := u.user_id; kind := 'dm'; title := r.sender_name;
      body := case when r.c > 1 then r.c || ' new encrypted messages - open XARENA to read'
                   else 'New encrypted message - open XARENA to read' end;
      return next;
    end loop;

    select sm.squad_id into v_squad from public.squad_members sm where sm.user_id = u.user_id;
    if v_squad is not null then
      select sq.name into v_squad_name from public.squads sq where sq.id = v_squad;
      for r in select left(m.body, 80) as m_body, coalesce(pr.ign, pr.username, 'player') as sender_name
        from public.squad_room_messages m
        left join public.profiles pr on pr.id = m.user_id
        where m.squad_id = v_squad and m.user_id <> u.user_id and m.created_at > u.since_room
        order by m.id asc limit 10
      loop
        user_id := u.user_id; kind := 'room';
        title := r.sender_name || ' in ' || coalesce(v_squad_name, 'your clan');
        body := coalesce(nullif(r.m_body, ''), 'New clan room message');
        return next;
      end loop;
    end if;

    for r in select t.name as t_name, t.date_text as t_date from public.tournaments t
      where t.status = 'upcoming' and t.created_at > u.since_tourn order by t.created_at asc limit 5
    loop
      user_id := u.user_id; kind := 'tournament';
      title := 'New tournament: ' || r.t_name;
      body := coalesce(r.t_date, 'Dates revealed soon');
      return next;
    end loop;

    update public.push_dispatch_state ds2 set since_dm = v_now, since_room = v_now, since_tourn = v_now, updated_at = v_now
    where ds2.user_id = u.user_id;
  end loop;
end $function$


notify pgrst, 'reload schema';
