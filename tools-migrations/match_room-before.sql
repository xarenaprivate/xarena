CREATE OR REPLACE FUNCTION public.match_room(p_match uuid)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare v_uid uuid := auth.uid(); v_m public.matches; v_reg boolean;
begin
  if v_uid is null then return jsonb_build_object('ok', false, 'error', 'Log in first.'); end if;
  select * into v_m from public.matches where id = p_match;
  if v_m.id is null then return jsonb_build_object('ok', false, 'error', 'Match not found.'); end if;
  if v_m.room_id is null then return jsonb_build_object('ok', false, 'error', 'Room details are not posted yet.'); end if;
  if v_m.starts_at is not null and now() < v_m.starts_at - make_interval(mins => v_m.reveal_minutes) then
    return jsonb_build_object('ok', false, 'error', 'Room ID unlocks ' || v_m.reveal_minutes || ' minutes before the match (' || to_char(v_m.starts_at at time zone 'Asia/Kolkata', 'DD Mon HH24:MI') || ' IST start).');
  end if;
  select exists (select 1 from public.registrations r where r.tournament_id = v_m.tournament_id and r.user_id is not null and (r.user_id = v_uid or r.user_id in (select sm2.user_id from public.squad_members sm1 join public.squad_members sm2 on sm2.squad_id = sm1.squad_id where sm1.user_id = v_uid))) into v_reg;
  if not v_reg then return jsonb_build_object('ok', false, 'error', 'Room details are for registered players of this tournament only.'); end if;
  return jsonb_build_object('ok', true, 'room_id', v_m.room_id, 'room_pass', v_m.room_pass, 'title', v_m.title, 'starts_at', v_m.starts_at);
end $function$

