begin;
select set_config('request.jwt.claim.sub',(select id::text from public.profiles order by created_at limit 1),true);
update public.player_game_stats set last_checkin=null where user_id=auth.uid();
set local role authenticated;
do $$declare r jsonb;begin
 r:=public.daily_checkin();
 if coalesce((r->>'ok')::boolean,false)=false or coalesce((r->>'awarded')::int,0)<6 then raise exception 'checkin did not award in fixture';end if;
end$$;
select 'daily_checkin authenticated nested reward passed; all fixture changes rolled back' as result;
reset role;
rollback;
