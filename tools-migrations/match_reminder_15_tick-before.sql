CREATE OR REPLACE FUNCTION public.match_reminder_15_tick()
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare m record; r record; n int := 0;
begin
  for m in select * from matches where status='scheduled' and starts_at is not null and starts_at between now() + interval '10 minutes' and now() + interval '20 minutes' loop
    for r in select distinct user_id from registrations where tournament_id=m.tournament_id and user_id is not null loop
      perform notify(r.user_id,'match_reminder','Match starts in ~15 minutes: '||m.title,'Get ready and open XARENA to check in.','tournaments.html','match15:'||m.id::text); n := n+1;
    end loop;
  end loop;
  return jsonb_build_object('ok',true,'sent',n);
end $function$

