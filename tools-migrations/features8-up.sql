-- 1) Daily missions v2: check-in, play a match (match check-in), add a friend. Verified from server tables.
create table if not exists mission_claims_v2 (user_id uuid not null, day date not null, kind text not null check (kind in ('checkin','match','friend')), claimed_at timestamptz default now(), primary key (user_id, day, kind));
alter table mission_claims_v2 enable row level security;
create or replace function daily_missions_v2() returns jsonb language plpgsql security definer set search_path to 'public' as $f$
declare u uuid := auth.uid(); d date := (now() at time zone 'Asia/Kolkata')::date; c bool; m bool; fr bool;
begin
  if u is null then return jsonb_build_object('ok',false,'error','Log in first.'); end if;
  select coalesce(last_checkin = d,false) into c from player_game_stats where user_id=u; c := coalesce(c,false);
  select exists(select 1 from match_checkins where user_id=u and (checked_in_at at time zone 'Asia/Kolkata')::date = d) into m;
  select exists(select 1 from friends where user_id=u and (created_at at time zone 'Asia/Kolkata')::date = d) into fr;
  return jsonb_build_object('ok',true,'day',d,'missions', jsonb_build_array(
    jsonb_build_object('kind','checkin','title','Daily check-in','done',c,'claimed',exists(select 1 from mission_claims_v2 where user_id=u and day=d and kind='checkin'),'reward',10),
    jsonb_build_object('kind','match','title','Check in to a match','done',m,'claimed',exists(select 1 from mission_claims_v2 where user_id=u and day=d and kind='match'),'reward',20),
    jsonb_build_object('kind','friend','title','Add a friend','done',fr,'claimed',exists(select 1 from mission_claims_v2 where user_id=u and day=d and kind='friend'),'reward',10)));
end $f$;
create or replace function claim_mission_v2(p_kind text) returns jsonb language plpgsql security definer set search_path to 'public' as $f$
declare u uuid := auth.uid(); d date := (now() at time zone 'Asia/Kolkata')::date; s jsonb; it jsonb;
begin
  if u is null then return jsonb_build_object('ok',false,'error','Log in first.'); end if;
  s := daily_missions_v2();
  select x into it from jsonb_array_elements(s->'missions') x where x->>'kind'=p_kind;
  if it is null then return jsonb_build_object('ok',false,'error','Unknown mission.'); end if;
  if not (it->>'done')::bool then return jsonb_build_object('ok',false,'error','Mission not complete yet.'); end if;
  if (it->>'claimed')::bool then return jsonb_build_object('ok',false,'error','Already claimed today.'); end if;
  insert into mission_claims_v2(user_id,day,kind) values (u,d,p_kind);
  perform award_xp(u,'mission',(it->>'reward')::int,jsonb_build_object('day',d,'mission',p_kind));
  return jsonb_build_object('ok',true,'reward',(it->>'reward')::int);
end $f$;
grant execute on function daily_missions_v2(), claim_mission_v2(text) to authenticated;

-- 2) Season pass (free): unlocks by level, claimable once. Rewards are badges and AP, never money.
create table if not exists season_rewards (level int primary key, title text not null, kind text not null check (kind in ('badge','frame','logo','ap')), badge text, ap int);
insert into season_rewards(level,title,kind,badge,ap) values
 (2,'Rookie badge','badge','season1_rookie',null),(3,'+20 AP','ap',null,20),(4,'Silver frame','frame','frame_silver',null),
 (5,'Contender badge','badge','season1_contender',null),(6,'+30 AP','ap',null,30),(8,'Gold frame','frame','frame_gold',null),
 (10,'Veteran badge','badge','season1_veteran',null),(12,'+50 AP','ap',null,50),(15,'Legend logo','logo','logo_legend',null)
 on conflict (level) do nothing;
create table if not exists season_claims (user_id uuid not null, level int not null, claimed_at timestamptz default now(), primary key(user_id,level));
alter table season_rewards enable row level security; alter table season_claims enable row level security;
create policy season_rewards_read on season_rewards for select using (true);
create or replace function season_pass() returns jsonb language plpgsql stable security definer set search_path to 'public' as $f$
declare u uuid := auth.uid(); x int := 0; lv int := 1;
begin
  if u is not null then select coalesce(xp,0) into x from player_game_stats where user_id=u; x := coalesce(x,0); end if;
  lv := (select level from level_from_xp(x));
  return jsonb_build_object('ok',true,'logged_in',u is not null,'xp',x,'level',lv,'tiers',coalesce((select jsonb_agg(jsonb_build_object('level',r.level,'title',r.title,'kind',r.kind,'unlocked',lv>=r.level,
     'claimed',exists(select 1 from season_claims c where c.user_id=u and c.level=r.level)) order by r.level) from season_rewards r),'[]'::jsonb));
end $f$;
create or replace function season_claim(p_level int) returns jsonb language plpgsql security definer set search_path to 'public' as $f$
declare u uuid := auth.uid(); x int; r season_rewards%rowtype;
begin
  if u is null then return jsonb_build_object('ok',false,'error','Log in first.'); end if;
  select * into r from season_rewards where level=p_level; if r.level is null then return jsonb_build_object('ok',false,'error','No such tier.'); end if;
  select coalesce(xp,0) into x from player_game_stats where user_id=u; x := coalesce(x,0);
  if (select level from level_from_xp(x)) < r.level then return jsonb_build_object('ok',false,'error','Reach level '||r.level||' first.'); end if;
  begin insert into season_claims(user_id,level) values (u,r.level); exception when unique_violation then return jsonb_build_object('ok',false,'error','Already claimed.'); end;
  if r.kind='ap' then insert into points_ledger(user_id,delta,kind,meta) values (u,r.ap,'season_pass',jsonb_build_object('level',r.level));
  else perform award_badge_internal(u, r.badge); end if;
  return jsonb_build_object('ok',true,'title',r.title);
end $f$;
grant execute on function season_pass() to anon, authenticated; grant execute on function season_claim(int) to authenticated;

-- 3) Squad board: squads ranked by combined member EXP. scope global|state|district (caller's area, from the captain's area).
create or replace function squad_board(p_scope text default 'global') returns jsonb language plpgsql stable security definer set search_path to 'public' as $f$
declare u uuid := auth.uid(); mp profiles%rowtype;
begin
  if u is not null then select * into mp from profiles where id=u; end if;
  if p_scope in ('state','district') and (u is null or mp.geo_state is null) then return jsonb_build_object('ok',false,'error','location_required'); end if;
  return jsonb_build_object('ok',true,'scope',p_scope,'rows',coalesce((select jsonb_agg(t order by (t->>'rank')::int) from (
    select jsonb_build_object('rank',rank() over (order by sum(coalesce(g.xp,0)) desc),'id',s.id,'name',s.name,'tag',s.tag,'logo',s.logo_url,'members',count(*),'xp',sum(coalesce(g.xp,0)),
      'state',cp.geo_state,'district',cp.geo_district) t
    from squads s join squad_members m on m.squad_id=s.id left join player_game_stats g on g.user_id=m.user_id left join profiles cp on cp.id=s.captain
    where (p_scope='global' or (p_scope='state' and cp.geo_state=mp.geo_state) or (p_scope='district' and cp.geo_state=mp.geo_state and cp.geo_district=mp.geo_district))
    group by s.id,s.name,s.tag,s.logo_url,cp.geo_state,cp.geo_district order by sum(coalesce(g.xp,0)) desc limit 50) z),'[]'::jsonb));
end $f$;
grant execute on function squad_board(text) to anon, authenticated;

-- 4) Weekly top clip: most-liked approved clip/video/photo from the last 7 days (real likes only)
create or replace function weekly_top_clip() returns jsonb language plpgsql stable security definer set search_path to 'public' as $f$
declare r record;
begin
  select p.id,p.kind,p.media_path,p.caption,p.created_at,coalesce(nullif(pr.ign,''),pr.username) name,pr.username,(select count(*) from feed_likes l where l.post_id=p.id) likes
    into r from feed_posts p join profiles pr on pr.id=p.user_id
    where p.status='approved' and p.kind in ('clip','video') and p.created_at > now() - interval '7 days'
    order by (select count(*) from feed_likes l where l.post_id=p.id) desc, p.created_at desc limit 1;
  if r.id is null then return jsonb_build_object('ok',true,'clip',null); end if;
  return jsonb_build_object('ok',true,'clip',jsonb_build_object('id',r.id,'kind',r.kind,'media_path',r.media_path,'caption',r.caption,'name',r.name,'username',r.username,'likes',r.likes,'created_at',r.created_at));
end $f$;
grant execute on function weekly_top_clip() to anon, authenticated;

-- 5) Scrim finder
create table if not exists scrim_requests (id uuid primary key default gen_random_uuid(), squad_id uuid not null references squads(id) on delete cascade, user_id uuid not null,
  state text, district text, day_text text, time_text text, note text, status text not null default 'open' check (status in ('open','closed')), created_at timestamptz default now(), expires_at timestamptz default now() + interval '2 days');
alter table scrim_requests enable row level security;
create or replace function scrim_post(p_day text, p_time text, p_note text) returns jsonb language plpgsql security definer set search_path to 'public' as $f$
declare u uuid := auth.uid(); sq squads%rowtype; mp profiles%rowtype; v uuid;
begin
  if u is null then return jsonb_build_object('ok',false,'error','Log in first.'); end if;
  select * into sq from squads where captain=u; if sq.id is null then return jsonb_build_object('ok',false,'error','Only a squad captain can post a scrim request.'); end if;
  select * into mp from profiles where id=u;
  if mp.geo_district is null then return jsonb_build_object('ok',false,'error','location_required'); end if;
  if (select count(*) from scrim_requests where squad_id=sq.id and status='open' and expires_at>now()) >= 2 then return jsonb_build_object('ok',false,'error','You already have 2 open scrim requests.'); end if;
  insert into scrim_requests(squad_id,user_id,state,district,day_text,time_text,note) values (sq.id,u,mp.geo_state,mp.geo_district,left(coalesce(p_day,'Tonight'),30),left(coalesce(p_time,''),30),left(coalesce(p_note,''),160)) returning id into v;
  return jsonb_build_object('ok',true,'id',v);
end $f$;
create or replace function scrim_find() returns jsonb language plpgsql stable security definer set search_path to 'public' as $f$
declare u uuid := auth.uid(); mp profiles%rowtype;
begin
  if u is null then return jsonb_build_object('ok',false,'error','Log in first.'); end if;
  select * into mp from profiles where id=u; if mp.geo_district is null then return jsonb_build_object('ok',false,'error','location_required'); end if;
  return jsonb_build_object('ok',true,'district',mp.geo_district,'rows',coalesce((select jsonb_agg(jsonb_build_object('id',r.id,'squad',s.name,'tag',s.tag,'day',r.day_text,'time',r.time_text,'note',r.note,'mine',r.user_id=u) order by r.created_at desc)
    from scrim_requests r join squads s on s.id=r.squad_id where r.status='open' and r.expires_at>now() and r.district=mp.geo_district and r.state=mp.geo_state),'[]'::jsonb));
end $f$;
create or replace function scrim_reach(p_id uuid) returns jsonb language plpgsql security definer set search_path to 'public' as $f$
declare u uuid := auth.uid(); r scrim_requests%rowtype; mine squads%rowtype; theirs text;
begin
  if u is null then return jsonb_build_object('ok',false,'error','Log in first.'); end if;
  select * into mine from squads where captain=u; if mine.id is null then return jsonb_build_object('ok',false,'error','Only a squad captain can reply.'); end if;
  select * into r from scrim_requests where id=p_id and status='open' and expires_at>now(); if r.id is null then return jsonb_build_object('ok',false,'error','Request not available.'); end if;
  if r.squad_id=mine.id then return jsonb_build_object('ok',false,'error','That is your own request.'); end if;
  perform notify(r.user_id,'scrim',mine.name||' wants to scrim','They replied to your '||r.day_text||' scrim request. Message their captain in XARENA Messages.','messages.html','scrim:'||p_id||':'||mine.id);
  return jsonb_build_object('ok',true,'captain',(select username from profiles where id=mine.captain));
end $f$;
create or replace function scrim_close(p_id uuid) returns jsonb language plpgsql security definer set search_path to 'public' as $f$
begin update scrim_requests set status='closed' where id=p_id and user_id=auth.uid(); return jsonb_build_object('ok',true); end $f$;
grant execute on function scrim_post(text,text,text), scrim_find(), scrim_reach(uuid), scrim_close(uuid) to authenticated;

-- 7) Report player + admin queue
create table if not exists player_reports (id bigserial primary key, reporter uuid not null, reported uuid not null, reason text not null, details text, status text not null default 'open' check (status in ('open','dismissed','actioned')), admin_note text, created_at timestamptz default now(), handled_at timestamptz);
alter table player_reports enable row level security;
create or replace function report_player(p_username text, p_reason text, p_details text default null) returns jsonb language plpgsql security definer set search_path to 'public' as $f$
declare u uuid := auth.uid(); t uuid;
begin
  if u is null then return jsonb_build_object('ok',false,'error','Log in first.'); end if;
  select id into t from profiles where lower(username)=lower(trim(p_username)); if t is null then return jsonb_build_object('ok',false,'error','Player not found.'); end if;
  if t=u then return jsonb_build_object('ok',false,'error','You cannot report yourself.'); end if;
  if p_reason not in ('cheating','abuse','spam','fake_account','other') then return jsonb_build_object('ok',false,'error','Pick a reason.'); end if;
  if (select count(*) from player_reports where reporter=u and created_at > now() - interval '1 day') >= 5 then return jsonb_build_object('ok',false,'error','Report limit reached for today.'); end if;
  if exists (select 1 from player_reports where reporter=u and reported=t and status='open') then return jsonb_build_object('ok',false,'error','You already reported this player. Admins will review it.'); end if;
  insert into player_reports(reporter,reported,reason,details) values (u,t,p_reason,left(p_details,500));
  return jsonb_build_object('ok',true);
end $f$;
create or replace function admin_player_reports() returns jsonb language plpgsql security definer set search_path to 'public' as $f$
begin
  if not is_admin() then return jsonb_build_object('ok',false,'error','Admins only.'); end if;
  return jsonb_build_object('ok',true,'rows',coalesce((select jsonb_agg(jsonb_build_object('id',r.id,'reason',r.reason,'details',r.details,'status',r.status,'admin_note',r.admin_note,'created_at',r.created_at,
    'reported',(select username from profiles where id=r.reported),'reported_id',r.reported,'reporter',(select username from profiles where id=r.reporter),
    'reports_against',(select count(*) from player_reports x where x.reported=r.reported)) order by (r.status='open') desc, r.created_at desc) from (select * from player_reports order by created_at desc limit 100) r),'[]'::jsonb));
end $f$;
create or replace function admin_handle_player_report(p_id bigint, p_status text, p_note text default null) returns jsonb language plpgsql security definer set search_path to 'public' as $f$
begin
  if not is_admin() then return jsonb_build_object('ok',false,'error','Admins only.'); end if;
  if p_status not in ('dismissed','actioned') then return jsonb_build_object('ok',false,'error','Bad status.'); end if;
  update player_reports set status=p_status, admin_note=left(p_note,300), handled_at=now() where id=p_id;
  return jsonb_build_object('ok',true);
end $f$;
grant execute on function report_player(text,text,text), admin_player_reports(), admin_handle_player_report(bigint,text,text) to authenticated;

-- 8) Match reminder 15 minutes before (adds to the existing 1-hour one). Delivered as a notification, which push_dispatch turns into an FCM push.
create or replace function match_reminder_15_tick() returns jsonb language plpgsql security definer set search_path to 'public' as $f$
declare m record; r record; n int := 0;
begin
  for m in select * from matches where status='scheduled' and starts_at is not null and starts_at between now() + interval '10 minutes' and now() + interval '20 minutes' loop
    for r in select distinct user_id from registrations where tournament_id=m.tournament_id and user_id is not null loop
      perform notify(r.user_id,'match_reminder','Match starts in ~15 minutes: '||m.title,'Get ready and open XARENA to check in.','tournaments.html','match15:'||m.id::text); n := n+1;
    end loop;
  end loop;
  return jsonb_build_object('ok',true,'sent',n);
end $f$;
revoke execute on function match_reminder_15_tick() from public, anon, authenticated;
select cron.schedule('match-reminder-15','*/5 * * * *','select public.match_reminder_15_tick()');
notify pgrst, 'reload schema';
