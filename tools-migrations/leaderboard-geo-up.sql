-- Area (auto-detected on device, coarse) + scoped leaderboards + AP challenges. Additive.
alter table profiles add column if not exists geo_state text;
alter table profiles add column if not exists geo_district text;
alter table profiles add column if not exists geo_lat numeric(6,3);   -- rounded to 0.05 deg grid (~5 km), never exact
alter table profiles add column if not exists geo_lon numeric(6,3);
alter table profiles add column if not exists geo_updated_at timestamptz;

create or replace function set_my_area(p_state text, p_district text, p_lat double precision default null, p_lon double precision default null)
returns jsonb language plpgsql security definer set search_path to 'public' as $f$
declare v uuid := auth.uid(); s text; d text; la numeric; lo numeric;
begin
  if v is null then return jsonb_build_object('ok',false,'error','Log in first.'); end if;
  s := nullif(left(trim(coalesce(p_state,'')),60),''); d := nullif(left(trim(coalesce(p_district,'')),60),'');
  if s is null then return jsonb_build_object('ok',false,'error','State not detected.'); end if;
  if p_lat is not null and p_lon is not null and p_lat between -90 and 90 and p_lon between -180 and 180 then
    la := round((round(p_lat/0.05)*0.05)::numeric,3); lo := round((round(p_lon/0.05)*0.05)::numeric,3);
  end if;
  perform set_config('xarena.geo','1',true);
  update profiles set geo_state=s, geo_district=d, geo_lat=la, geo_lon=lo, geo_updated_at=now() where id=v;
  return jsonb_build_object('ok',true,'state',s,'district',d,'nearby_enabled',la is not null);
end $f$;
create or replace function clear_my_area() returns jsonb language plpgsql security definer set search_path to 'public' as $f$
begin
  if auth.uid() is null then return jsonb_build_object('ok',false,'error','Log in first.'); end if;
  perform set_config('xarena.geo','1',true);
  update profiles set geo_state=null, geo_district=null, geo_lat=null, geo_lon=null, geo_updated_at=null where id=auth.uid();
  return jsonb_build_object('ok',true);
end $f$;
grant execute on function set_my_area(text,text,double precision,double precision), clear_my_area() to authenticated;

-- scoped leaderboard. scope: global | state | district | near. Never returns coordinates or distances.
create or replace function leaderboard_scope(p_scope text default 'global', p_limit int default 50)
returns jsonb language plpgsql stable security definer set search_path to 'public' as $f$
declare me uuid := auth.uid(); mp profiles%rowtype; v_rows jsonb; v_lim int := least(greatest(coalesce(p_limit,50),1),100); v_myxp int := 0;
begin
  if me is not null then select * into mp from profiles where id=me; select coalesce(xp,0) into v_myxp from player_game_stats where user_id=me; end if;
  if p_scope in ('state','district','near') and (me is null or mp.geo_state is null) then
    return jsonb_build_object('ok',false,'error','location_required','scope',p_scope);
  end if;
  if p_scope = 'near' and mp.geo_lat is null then return jsonb_build_object('ok',false,'error','location_required','scope',p_scope); end if;
  select coalesce(jsonb_agg(t order by (t->>'rank')::int),'[]'::jsonb) into v_rows from (
    select jsonb_build_object('rank', rank() over (order by s.xp desc), 'id', p.id, 'username', p.username,
      'name', coalesce(nullif(p.ign,''),p.username,'Player'), 'avatar', p.avatar_url, 'xp', s.xp,
      'level', (select level from level_from_xp(s.xp)), 'kd', p.kd,
      'wins', (select count(*) from lb_entries e where e.player_name=p.ign and e.placement=1),
      'state', p.geo_state, 'district', p.geo_district, 'is_me', p.id = me,
      'rival', (me is not null and p.id <> me and mp.geo_district is not null and p.geo_district is not distinct from mp.geo_district and p.geo_state is not distinct from mp.geo_state and s.xp > v_myxp and s.xp <= v_myxp + 100)) as t
    from player_game_stats s join profiles p on p.id=s.user_id
    where s.xp > 0 and p.username is not null and not coalesce(p.banned,false)
      and (p_scope='global'
        or (p_scope='state' and p.geo_state is not distinct from mp.geo_state)
        or (p_scope='district' and p.geo_state is not distinct from mp.geo_state and p.geo_district is not distinct from mp.geo_district)
        or (p_scope='near' and p.geo_lat is not null and
            2*6371*asin(sqrt(power(sin(radians(p.geo_lat-mp.geo_lat)/2),2)+cos(radians(mp.geo_lat))*cos(radians(p.geo_lat))*power(sin(radians(p.geo_lon-mp.geo_lon)/2),2))) <= 10))
    order by s.xp desc, p.username limit v_lim) x;
  return jsonb_build_object('ok',true,'scope',p_scope,'state',mp.geo_state,'district',mp.geo_district,'rows',v_rows);
end $f$;
grant execute on function leaderboard_scope(text,int) to anon, authenticated;

create or replace function my_ranks() returns jsonb language plpgsql stable security definer set search_path to 'public' as $f$
declare me uuid := auth.uid(); mp profiles%rowtype; x int; g int; st int; di int;
begin
  if me is null then return jsonb_build_object('ok',false,'error','Log in first.'); end if;
  select * into mp from profiles where id=me;
  select coalesce(xp,0) into x from player_game_stats where user_id=me; x := coalesce(x,0);
  if x <= 0 then return jsonb_build_object('ok',true,'xp',0,'global',null,'state',null,'district',null,'state_name',mp.geo_state,'district_name',mp.geo_district); end if;
  select count(*)+1 into g from player_game_stats s join profiles p on p.id=s.user_id where s.xp > x and p.username is not null and not coalesce(p.banned,false);
  if mp.geo_state is not null then
    select count(*)+1 into st from player_game_stats s join profiles p on p.id=s.user_id where s.xp > x and p.geo_state=mp.geo_state and not coalesce(p.banned,false);
    if mp.geo_district is not null then
      select count(*)+1 into di from player_game_stats s join profiles p on p.id=s.user_id where s.xp > x and p.geo_state=mp.geo_state and p.geo_district=mp.geo_district and not coalesce(p.banned,false);
    end if;
  end if;
  return jsonb_build_object('ok',true,'xp',x,'global',g,'state',st,'district',di,'state_name',mp.geo_state,'district_name',mp.geo_district);
end $f$;
grant execute on function my_ranks() to authenticated;

create or replace function my_header() returns jsonb language plpgsql stable security definer set search_path to 'public' as $f$
declare me uuid := auth.uid(); x int; b int;
begin
  if me is null then return jsonb_build_object('ok',false,'error','Log in first.'); end if;
  select coalesce(xp,0) into x from player_game_stats where user_id=me;
  select coalesce(sum(delta),0) into b from points_ledger where user_id=me;
  return jsonb_build_object('ok',true,'exp',coalesce(x,0),'ap',b,'level',(select level from level_from_xp(coalesce(x,0))));
end $f$;
grant execute on function my_header() to authenticated;

-- AP challenges: stakes escrowed in points_ledger; closed virtual economy, no cash path.
create table if not exists challenges (
  id uuid primary key default gen_random_uuid(),
  challenger uuid not null references profiles(id), challenged uuid not null references profiles(id),
  stake int not null check (stake between 10 and 100),
  district text, state text,
  status text not null default 'pending' check (status in ('pending','active','declined','cancelled','expired','settled','void')),
  created_at timestamptz not null default now(), expires_at timestamptz not null default now() + interval '24 hours',
  accepted_at timestamptz, ends_at timestamptz, winner uuid, settled_at timestamptz, gain_challenger int, gain_challenged int,
  check (challenger <> challenged)
);
alter table challenges enable row level security;
create policy challenges_party_read on challenges for select to authenticated using (auth.uid() in (challenger, challenged));
create index if not exists challenges_status on challenges(status, expires_at);

create or replace function _ap_bal(u uuid) returns int language sql stable as $f$ select coalesce(sum(delta),0)::int from points_ledger where user_id=u $f$;

create or replace function challenge_create(p_target uuid, p_stake int) returns jsonb language plpgsql security definer set search_path to 'public' as $f$
declare me uuid := auth.uid(); a profiles%rowtype; b profiles%rowtype; xa int; xb int; v_id uuid; v_name text;
begin
  if me is null then return jsonb_build_object('ok',false,'error','Log in first.'); end if;
  if p_target is null or p_target = me then return jsonb_build_object('ok',false,'error','Pick another player.'); end if;
  if p_stake is null or p_stake < 10 or p_stake > 100 then return jsonb_build_object('ok',false,'error','Stake must be 10 to 100 AP.'); end if;
  perform pg_advisory_xact_lock(hashtext('ap:'||me::text));
  select * into a from profiles where id=me; select * into b from profiles where id=p_target;
  if b.id is null or coalesce(b.banned,false) or coalesce(a.banned,false) then return jsonb_build_object('ok',false,'error','Player not available.'); end if;
  if a.geo_district is null or a.geo_district is distinct from b.geo_district or a.geo_state is distinct from b.geo_state then
    return jsonb_build_object('ok',false,'error','You can only challenge players in your own district.'); end if;
  select coalesce(xp,0) into xa from player_game_stats where user_id=me; select coalesce(xp,0) into xb from player_game_stats where user_id=p_target;
  if coalesce(xb,0) <= coalesce(xa,0) or xb > coalesce(xa,0) + 100 then return jsonb_build_object('ok',false,'error','Challenges are for rivals up to 100 EXP above you.'); end if;
  if (select count(*) from challenges where challenger=me and status in ('pending','active')) >= 3 then return jsonb_build_object('ok',false,'error','You already have 3 open challenges.'); end if;
  if exists (select 1 from challenges where status in ('pending','active') and ((challenger=me and challenged=p_target) or (challenger=p_target and challenged=me))) then
    return jsonb_build_object('ok',false,'error','There is already an open challenge between you two.'); end if;
  if _ap_bal(me) < p_stake then return jsonb_build_object('ok',false,'error','Not enough AP. You have '||_ap_bal(me)||'.'); end if;
  insert into challenges (challenger, challenged, stake, district, state) values (me, p_target, p_stake, a.geo_district, a.geo_state) returning id into v_id;
  insert into points_ledger (user_id, delta, kind, meta) values (me, -p_stake, 'challenge_escrow', jsonb_build_object('challenge', v_id));
  v_name := coalesce(nullif(a.ign,''), a.username, 'A player');
  perform notify(p_target, 'challenge', v_name || ' challenged you for ' || a.geo_district || ' King', 'Stake ' || p_stake || ' AP each. Open XARENA to accept or decline (expires in 24h).', 'leaderboard.html', 'ch:' || v_id);
  return jsonb_build_object('ok',true,'id',v_id,'balance',_ap_bal(me));
end $f$;

create or replace function challenge_respond(p_id uuid, p_accept boolean) returns jsonb language plpgsql security definer set search_path to 'public' as $f$
declare me uuid := auth.uid(); c challenges%rowtype; nm text;
begin
  if me is null then return jsonb_build_object('ok',false,'error','Log in first.'); end if;
  perform pg_advisory_xact_lock(hashtext('ap:'||me::text));
  select * into c from challenges where id=p_id for update;
  if c.id is null or c.challenged <> me or c.status <> 'pending' then return jsonb_build_object('ok',false,'error','Challenge not available.'); end if;
  if c.expires_at < now() then return jsonb_build_object('ok',false,'error','Challenge expired.'); end if;
  nm := coalesce((select nullif(ign,'') from profiles where id=me),(select username from profiles where id=me),'Player');
  if not p_accept then
    update challenges set status='declined', settled_at=now() where id=c.id;
    insert into points_ledger (user_id, delta, kind, meta) values (c.challenger, c.stake, 'challenge_refund', jsonb_build_object('challenge', c.id, 'why','declined'));
    perform notify(c.challenger,'challenge', nm||' declined your challenge','Your '||c.stake||' AP was returned.','leaderboard.html','chd:'||c.id);
    return jsonb_build_object('ok',true,'status','declined');
  end if;
  if _ap_bal(me) < c.stake then return jsonb_build_object('ok',false,'error','Not enough AP to accept. You need '||c.stake||'.'); end if;
  insert into points_ledger (user_id, delta, kind, meta) values (me, -c.stake, 'challenge_escrow', jsonb_build_object('challenge', c.id));
  update challenges set status='active', accepted_at=now(), ends_at=now() + interval '24 hours' where id=c.id;
  perform notify(c.challenger,'challenge', nm||' accepted your challenge','Most EXP earned in the next 24 hours wins '||(c.stake*2)||' AP.','leaderboard.html','cha:'||c.id);
  return jsonb_build_object('ok',true,'status','active','ends_at',now() + interval '24 hours');
end $f$;

create or replace function challenge_cancel(p_id uuid) returns jsonb language plpgsql security definer set search_path to 'public' as $f$
declare me uuid := auth.uid(); c challenges%rowtype;
begin
  if me is null then return jsonb_build_object('ok',false,'error','Log in first.'); end if;
  select * into c from challenges where id=p_id for update;
  if c.id is null or c.challenger <> me or c.status <> 'pending' then return jsonb_build_object('ok',false,'error','Only your own pending challenge can be cancelled.'); end if;
  update challenges set status='cancelled', settled_at=now() where id=c.id;
  insert into points_ledger (user_id, delta, kind, meta) values (c.challenger, c.stake, 'challenge_refund', jsonb_build_object('challenge', c.id, 'why','cancelled'));
  return jsonb_build_object('ok',true);
end $f$;

-- settle: expire pending (refund), finish active (winner = more EXP earned from xp_events in the window; tie = refund both)
create or replace function challenges_settle_due() returns int language plpgsql security definer set search_path to 'public' as $f$
declare c challenges%rowtype; ga int; gb int; n int := 0;
begin
  for c in select * from challenges where status='pending' and expires_at < now() for update skip locked loop
    update challenges set status='expired', settled_at=now() where id=c.id;
    insert into points_ledger (user_id, delta, kind, meta) values (c.challenger, c.stake, 'challenge_refund', jsonb_build_object('challenge', c.id,'why','expired'));
    n := n+1;
  end loop;
  for c in select * from challenges where status='active' and ends_at <= now() for update skip locked loop
    select coalesce(sum(amount),0) into ga from xp_events where user_id=c.challenger and created_at >= c.accepted_at and created_at < c.ends_at;
    select coalesce(sum(amount),0) into gb from xp_events where user_id=c.challenged and created_at >= c.accepted_at and created_at < c.ends_at;
    if ga = gb then
      update challenges set status='void', settled_at=now(), gain_challenger=ga, gain_challenged=gb where id=c.id;
      insert into points_ledger (user_id, delta, kind, meta) values (c.challenger, c.stake, 'challenge_refund', jsonb_build_object('challenge', c.id,'why','tie')), (c.challenged, c.stake, 'challenge_refund', jsonb_build_object('challenge', c.id,'why','tie'));
      perform notify(c.challenger,'challenge','Challenge ended in a tie','Both stakes were returned.','leaderboard.html','cht:'||c.id);
      perform notify(c.challenged,'challenge','Challenge ended in a tie','Both stakes were returned.','leaderboard.html','cht:'||c.id);
    else
      update challenges set status='settled', settled_at=now(), gain_challenger=ga, gain_challenged=gb, winner=case when ga>gb then c.challenger else c.challenged end where id=c.id;
      insert into points_ledger (user_id, delta, kind, meta) values (case when ga>gb then c.challenger else c.challenged end, c.stake*2, 'challenge_win', jsonb_build_object('challenge', c.id));
      perform notify(case when ga>gb then c.challenger else c.challenged end,'challenge','You won the challenge','+'||(c.stake*2)||' AP added.','leaderboard.html','chw:'||c.id);
      perform notify(case when ga>gb then c.challenged else c.challenger end,'challenge','You lost the challenge','Better luck next time.','leaderboard.html','chl:'||c.id);
    end if;
    n := n+1;
  end loop;
  return n;
end $f$;

create or replace function my_challenges() returns jsonb language plpgsql security definer set search_path to 'public' as $f$
declare me uuid := auth.uid();
begin
  if me is null then return jsonb_build_object('ok',false,'error','Log in first.'); end if;
  perform challenges_settle_due();
  return jsonb_build_object('ok',true,'rows',coalesce((select jsonb_agg(jsonb_build_object('id',c.id,'status',c.status,'stake',c.stake,'incoming',c.challenged=me,
      'other_name',coalesce(nullif(o.ign,''),o.username),'other_id',o.id,'district',c.district,'expires_at',c.expires_at,'ends_at',c.ends_at,
      'won', case when c.winner is null then null else c.winner=me end) order by c.created_at desc)
    from (select * from challenges where challenger=me or challenged=me order by created_at desc limit 20) c
    join profiles o on o.id = case when c.challenger=me then c.challenged else c.challenger end),'[]'::jsonb));
end $f$;
grant execute on function challenge_create(uuid,int), challenge_respond(uuid,boolean), challenge_cancel(uuid), my_challenges() to authenticated;
revoke execute on function challenges_settle_due() from public, anon, authenticated;
select cron.schedule('challenges-settle','*/5 * * * *','select public.challenges_settle_due()');
notify pgrst, 'reload schema';

-- geo columns may only be written through set_my_area / clear_my_area (auto-detected, never typed)
create or replace function trg_profiles_geo_guard() returns trigger language plpgsql as $f$
begin
  if auth.uid() is not null and coalesce(current_setting('xarena.geo',true),'') <> '1'
     and (new.geo_state is distinct from old.geo_state or new.geo_district is distinct from old.geo_district
          or new.geo_lat is distinct from old.geo_lat or new.geo_lon is distinct from old.geo_lon or new.geo_updated_at is distinct from old.geo_updated_at) then
    raise exception 'Area is detected automatically and cannot be edited by hand.';
  end if;
  return new;
end $f$;
drop trigger if exists profiles_geo_guard on profiles;
create trigger profiles_geo_guard before update on profiles for each row execute function trg_profiles_geo_guard();
