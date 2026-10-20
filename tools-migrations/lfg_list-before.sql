CREATE OR REPLACE FUNCTION public.lfg_list()
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare v_uid uuid := auth.uid();
begin
  -- stale lobbies die after 12 hours
  update public.lfg_lobbies set status = 'closed' where status in ('open','full') and created_at < now() - interval '12 hours';
  return jsonb_build_object('ok', true, 'lobbies', coalesce((
    select jsonb_agg(jsonb_build_object(
      'id', l.id, 'mode', l.mode, 'map', l.map, 'rank_min', l.rank_min, 'rank_max', l.rank_max,
      'mic', l.mic, 'note', l.note, 'slots', l.slots, 'status', l.status, 'created_at', l.created_at,
      'host', coalesce(hp.ign, hp.username),
      'mine', (v_uid is not null and exists (select 1 from public.lfg_members lm where lm.lobby_id = l.id and lm.user_id = v_uid)),
      'host_mine', (v_uid is not null and l.host = v_uid),
      'members', (select jsonb_agg(jsonb_build_object('name', coalesce(mp.ign, mp.username), 'me', (v_uid is not null and lm.user_id = v_uid)))
                  from public.lfg_members lm left join public.profiles mp on mp.id = lm.user_id where lm.lobby_id = l.id)
    ) order by l.created_at desc)
    from public.lfg_lobbies l left join public.profiles hp on hp.id = l.host
    where l.status in ('open','full')), '[]'::jsonb));
end $function$

