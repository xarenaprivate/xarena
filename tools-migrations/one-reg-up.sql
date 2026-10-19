-- 1 account = 1 registration per tournament. Already enforced by unique index registrations_user_tourn_uniq
-- and the register_squad check; this adds a read RPC so clients can show a "Registered" state.
create or replace function my_registrations() returns jsonb language sql security definer set search_path to 'public' stable as $f$
  select case when auth.uid() is null then jsonb_build_object('ok', false, 'error', 'Login required.')
  else jsonb_build_object('ok', true, 'registrations', coalesce((select jsonb_agg(jsonb_build_object(
    'id', id, 'tournament_id', tournament_id, 'squad', squad, 'status', status, 'verified', verified, 'hold', hold, 'ap_paid', ap_paid, 'status_token', status_token, 'created_at', created_at) order by created_at)
    from registrations where user_id = auth.uid()), '[]'::jsonb)) end $f$;
grant execute on function my_registrations() to authenticated;
notify pgrst, 'reload schema';
