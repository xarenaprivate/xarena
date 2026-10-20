CREATE OR REPLACE FUNCTION public.match_reminder_tick()
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare v_m record; v_r record; v_n int := 0;
begin
  for v_m in select * from public.matches where status = 'scheduled' and starts_at is not null
      and starts_at between now() + interval '50 minutes' and now() + interval '65 minutes' loop
    for v_r in select user_id from public.registrations where tournament_id = v_m.tournament_id and user_id is not null loop
      perform public.notify(v_r.user_id, 'match_reminder', 'Match in ~1 hour: ' || v_m.title,
        'Room ID unlocks ' || v_m.reveal_minutes || ' minutes before start. Check in on the Tournaments page when it opens.',
        'tournaments.html', 'match:' || v_m.id::text);
      v_n := v_n + 1;
    end loop;
  end loop;
  return jsonb_build_object('ok', true, 'sent', v_n);
end $function$

