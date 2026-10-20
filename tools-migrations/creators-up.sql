-- CREATOR MODE v1 (additive, isolated from tournaments/registrations/matches). Free-entry only, no money.
create table if not exists public.creator_applications(
  id bigserial primary key, user_id uuid not null references auth.users(id) on delete cascade,
  channel_url text not null, self_followers int not null default 0, note text,
  status text not null default 'pending' check (status in ('pending','approved','rejected','revoked')),
  created_at timestamptz not null default now(), reviewed_at timestamptz);
create unique index if not exists creator_app_one_pending on public.creator_applications(user_id) where status='pending';
create table if not exists public.creators(
  user_id uuid primary key references auth.users(id) on delete cascade,
  channel_url text, self_followers int not null default 0, tier text not null default 'Rising',
  approved_by uuid, approved_at timestamptz not null default now(), active boolean not null default true);
create table if not exists public.creator_events(
  id uuid primary key default gen_random_uuid(), host uuid not null references auth.users(id) on delete cascade,
  title text not null check (char_length(title) between 3 and 80), description text check (char_length(description)<=400),
  mode text not null default 'Squad' check (mode in ('Solo','Duo','Squad')), map text not null default 'Bermuda',
  starts_at timestamptz not null, slots int not null default 48 check (slots between 2 and 100),
  room_id text, room_pass text, status text not null default 'scheduled' check (status in ('scheduled','live','ended','cancelled')),
  stream_url text, stream_live boolean not null default false, created_at timestamptz not null default now());
create index if not exists creator_events_host_idx on public.creator_events(host, created_at desc);
create index if not exists creator_events_status_idx on public.creator_events(status, starts_at);
create table if not exists public.creator_event_players(
  event_id uuid not null references public.creator_events(id) on delete cascade, user_id uuid not null references auth.users(id) on delete cascade,
  status text not null default 'joined' check (status in ('joined','left','kicked','banned')), joined_at timestamptz not null default now(),
  primary key(event_id,user_id));
create table if not exists public.creator_event_reports(
  id bigserial primary key, event_id uuid not null references public.creator_events(id) on delete cascade,
  reporter uuid not null, reason text not null check (char_length(reason) between 3 and 300), created_at timestamptz not null default now(),
  unique(event_id,reporter));
alter table public.creator_applications enable row level security; alter table public.creators enable row level security;
alter table public.creator_events enable row level security; alter table public.creator_event_players enable row level security;
alter table public.creator_event_reports enable row level security;
revoke all on public.creator_applications, public.creators, public.creator_events, public.creator_event_players, public.creator_event_reports from anon, authenticated;

-- no money / cash words in creator event text
create or replace function public.creator_events_text_guard() returns trigger language plpgsql as $$
declare t text := lower(coalesce(new.title,'')||' '||coalesce(new.description,''));
begin
  if t ~ '(\y(cash|paid entry|entry fee|prize ?money|inr|paytm|phonepe|gpay|upi|bet|bets|betting|wager|gambl\w*|real money|withdraw|deposit|payout)\y|₹|\yrs\.? ?[0-9])' then
    raise exception 'Creator events must be free entry. No cash, money or prize-money wording.';
  end if;
  return new;
end $$;
drop trigger if exists creator_events_text_guard on public.creator_events;
create trigger creator_events_text_guard before insert or update of title,description on public.creator_events for each row execute function public.creator_events_text_guard();

create or replace function public.creator_tier(f int) returns text language sql immutable as $$
  select case when f>=100000 then 'Star' when f>=10000 then 'Pro' when f>=1000 then 'Established' else 'Rising' end $$;

-- public views (read only): badge + events without room details
create or replace view public.creators_public as
  select c.user_id, p.username, p.ign, p.avatar_url, c.tier, c.self_followers, c.channel_url, true as verified_creator
  from creators c join profiles p on p.id=c.user_id where c.active;
create or replace view public.creator_events_public as
  select e.id, e.title, e.description, e.mode, e.map, e.starts_at, e.slots, e.status, e.stream_url, e.stream_live,
    e.host, p.username as host_username, p.ign as host_ign, p.avatar_url as host_avatar, c.tier as host_tier, true as host_verified,
    (select count(*) from creator_event_players x where x.event_id=e.id and x.status='joined') as joined
  from creator_events e join creators c on c.user_id=e.host and c.active join profiles p on p.id=e.host
  where e.status<>'cancelled' or e.starts_at>now()-interval '1 day';
revoke all on public.creators_public, public.creator_events_public from anon, authenticated;
grant select on public.creators_public, public.creator_events_public to anon, authenticated;

create or replace function public.creator_apply(p_channel text, p_followers int, p_note text default null) returns jsonb
language plpgsql security definer set search_path to 'public' as $$
declare v uuid := auth.uid(); ch text := left(trim(coalesce(p_channel,'')),200);
begin
  if v is null then return jsonb_build_object('ok',false,'error','Log in first.'); end if;
  if exists(select 1 from creators where user_id=v and active) then return jsonb_build_object('ok',false,'error','You are already a creator.'); end if;
  if ch !~* '^https://(www\.)?(youtube\.com|youtu\.be|instagram\.com)/' then return jsonb_build_object('ok',false,'error','Enter your YouTube or Instagram channel link (https://...).'); end if;
  if coalesce(p_followers,-1) < 0 or p_followers > 100000000 then return jsonb_build_object('ok',false,'error','Enter your follower count (self-reported).'); end if;
  if (select count(*) from creator_applications where user_id=v and created_at>now()-interval '24 hours')>=3 then return jsonb_build_object('ok',false,'error','Too many requests. Try later.'); end if;
  if exists(select 1 from creator_applications where user_id=v and status='pending') then return jsonb_build_object('ok',false,'error','Your application is already pending.'); end if;
  insert into creator_applications(user_id,channel_url,self_followers,note) values (v,ch,p_followers,left(coalesce(p_note,''),300));
  return jsonb_build_object('ok',true,'status','pending');
end $$;

create or replace function public.creator_status() returns jsonb
language plpgsql stable security definer set search_path to 'public' as $$
declare v uuid := auth.uid(); c record; a record; isc boolean;
begin
  if v is null then return jsonb_build_object('is_creator',false,'application',null); end if;
  select * into c from creators where user_id=v and active;
  isc := found;
  select status, created_at into a from creator_applications where user_id=v order by id desc limit 1;
  return jsonb_build_object('is_creator',isc,'tier',c.tier,'self_reported_followers',c.self_followers,'application_status',a.status);
end $$;

create or replace function public.creator_dashboard() returns jsonb
language plpgsql stable security definer set search_path to 'public' as $$
declare v uuid := auth.uid(); c record; pr record;
begin
  select * into c from creators where user_id=v and active;
  if c is null then return jsonb_build_object('ok',false,'error','Creators only.'); end if;
  select username, ign, avatar_url, wins, matches into pr from profiles where id=v;
  return jsonb_build_object('ok',true,
    'name',coalesce(pr.ign,pr.username),'username',pr.username,'avatar_url',pr.avatar_url,'verified',true,'tier',c.tier,
    'self_reported_followers',c.self_followers,'followers_note','Self-reported by the creator',
    'tournaments_hosted',(select count(*) from creator_events where host=v and status in ('live','ended')),
    'players_hosted',(select count(*) from creator_event_players x join creator_events e on e.id=x.event_id where e.host=v and x.status='joined'),
    'wins',coalesce(pr.wins,0),
    'live_now',(select count(*) from creator_events where status='live'),
    'my_live',(select count(*) from creator_events where host=v and status='live'),
    'my_events',coalesce((select jsonb_agg(row_to_json(t)) from (select id,title,status,starts_at,slots,stream_live,(select count(*) from creator_event_players x where x.event_id=e.id and x.status='joined') joined from creator_events e where host=v order by created_at desc limit 20) t),'[]'::jsonb));
end $$;

create or replace function public.creator_event_create(p_title text, p_mode text, p_map text, p_starts timestamptz, p_slots int, p_desc text, p_room_id text, p_room_pass text) returns jsonb
language plpgsql security definer set search_path to 'public' as $$
declare v uuid := auth.uid(); nid uuid;
begin
  if v is null or not exists(select 1 from creators where user_id=v and active) then return jsonb_build_object('ok',false,'error','Only approved creators can host.'); end if;
  if exists(select 1 from profiles where id=v and banned) then return jsonb_build_object('ok',false,'error','Account restricted.'); end if;
  if p_starts is null or p_starts < now() + interval '10 minutes' or p_starts > now() + interval '30 days' then return jsonb_build_object('ok',false,'error','Start time must be between 10 minutes and 30 days from now.'); end if;
  if (select count(*) from creator_events where host=v and created_at>now()-interval '24 hours')>=3 then return jsonb_build_object('ok',false,'error','Limit: 3 events per 24 hours.'); end if;
  if (select count(*) from creator_events where host=v and status in ('scheduled','live'))>=3 then return jsonb_build_object('ok',false,'error','You already have 3 active events.'); end if;
  insert into creator_events(host,title,description,mode,map,starts_at,slots,room_id,room_pass)
  values (v,btrim(p_title),nullif(btrim(coalesce(p_desc,'')),''),coalesce(nullif(p_mode,''),'Squad'),coalesce(nullif(left(p_map,30),''),'Bermuda'),p_starts,coalesce(p_slots,48),left(nullif(btrim(coalesce(p_room_id,'')),''),20),left(nullif(btrim(coalesce(p_room_pass,'')),''),20))
  returning id into nid;
  return jsonb_build_object('ok',true,'id',nid,'entry','free');
exception when check_violation then return jsonb_build_object('ok',false,'error','Check the title (3-80 chars), mode (Solo/Duo/Squad) and slots (2-100).');
  when others then return jsonb_build_object('ok',false,'error',sqlerrm);
end $$;

create or replace function public.creator_event_manage(p_event uuid, p_action text, p_starts timestamptz default null) returns jsonb
language plpgsql security definer set search_path to 'public' as $$
declare v uuid := auth.uid(); e record;
begin
  select * into e from creator_events where id=p_event;
  if e is null or (e.host<>v and not is_admin()) then return jsonb_build_object('ok',false,'error','Not your event.'); end if;
  if e.status in ('ended','cancelled') then return jsonb_build_object('ok',false,'error','Event is closed.'); end if;
  if p_action='start' then update creator_events set status='live' where id=p_event;
  elsif p_action='end' then update creator_events set status='ended', stream_live=false where id=p_event;
  elsif p_action='cancel' then update creator_events set status='cancelled', stream_live=false where id=p_event;
  elsif p_action='delay' then
    if p_starts is null or p_starts < now() or p_starts > now()+interval '30 days' then return jsonb_build_object('ok',false,'error','Pick a valid new time.'); end if;
    update creator_events set starts_at=p_starts where id=p_event;
  else return jsonb_build_object('ok',false,'error','Unknown action.'); end if;
  return jsonb_build_object('ok',true,'action',p_action);
end $$;

create or replace function public.creator_event_set_stream(p_event uuid, p_url text, p_live boolean) returns jsonb
language plpgsql security definer set search_path to 'public' as $$
declare v uuid := auth.uid(); u text := left(trim(coalesce(p_url,'')),200);
begin
  if not exists(select 1 from creator_events where id=p_event and host=v and status in ('scheduled','live')) then return jsonb_build_object('ok',false,'error','Not your active event.'); end if;
  if u<>'' and u !~* '^https://(www\.)?(youtube\.com|youtu\.be)/' then return jsonb_build_object('ok',false,'error','Use a YouTube link.'); end if;
  update creator_events set stream_url=nullif(u,''), stream_live=(coalesce(p_live,false) and u<>'') where id=p_event;
  return jsonb_build_object('ok',true);
end $$;

create or replace function public.creator_event_join(p_event uuid) returns jsonb
language plpgsql security definer set search_path to 'public' as $$
declare v uuid := auth.uid(); e record; cur record; n int;
begin
  if v is null then return jsonb_build_object('ok',false,'error','Log in first.'); end if;
  if exists(select 1 from profiles where id=v and banned) then return jsonb_build_object('ok',false,'error','Account restricted.'); end if;
  select * into e from creator_events where id=p_event for update;
  if e is null or e.status<>'scheduled' then return jsonb_build_object('ok',false,'error','Registration is closed.'); end if;
  if e.host=v then return jsonb_build_object('ok',false,'error','You are the host.'); end if;
  select * into cur from creator_event_players where event_id=p_event and user_id=v;
  if cur.status in ('kicked','banned') then return jsonb_build_object('ok',false,'error','The host removed you from this event.'); end if;
  if cur.status='joined' then return jsonb_build_object('ok',true,'already',true); end if;
  select count(*) into n from creator_event_players where event_id=p_event and status='joined';
  if n >= e.slots then return jsonb_build_object('ok',false,'error','Event is full.'); end if;
  insert into creator_event_players(event_id,user_id,status) values (p_event,v,'joined')
    on conflict (event_id,user_id) do update set status='joined', joined_at=now();
  return jsonb_build_object('ok',true,'entry','free');
end $$;

create or replace function public.creator_event_leave(p_event uuid) returns jsonb
language plpgsql security definer set search_path to 'public' as $$
begin
  update creator_event_players set status='left' where event_id=p_event and user_id=auth.uid() and status='joined';
  return jsonb_build_object('ok',true);
end $$;

create or replace function public.creator_event_players(p_event uuid) returns jsonb
language plpgsql stable security definer set search_path to 'public' as $$
declare v uuid := auth.uid();
begin
  if not exists(select 1 from creator_events where id=p_event and host=v) and not is_admin() then return jsonb_build_object('ok',false,'error','Host only.'); end if;
  return jsonb_build_object('ok',true,'players',coalesce((select jsonb_agg(jsonb_build_object('user_id',x.user_id,'name',coalesce(p.ign,p.username),'status',x.status,'joined_at',x.joined_at) order by x.joined_at) from creator_event_players x join profiles p on p.id=x.user_id where x.event_id=p_event and x.status in ('joined','kicked','banned')),'[]'::jsonb));
end $$;

create or replace function public.creator_event_kick(p_event uuid, p_user uuid, p_ban boolean default false) returns jsonb
language plpgsql security definer set search_path to 'public' as $$
begin
  if not exists(select 1 from creator_events where id=p_event and host=auth.uid()) and not is_admin() then return jsonb_build_object('ok',false,'error','Host only.'); end if;
  update creator_event_players set status=case when coalesce(p_ban,false) then 'banned' else 'kicked' end where event_id=p_event and user_id=p_user;
  return jsonb_build_object('ok',true);
end $$;

-- room ID/pass: server-side only, for joined players from 15 min before start, or host/admin
create or replace function public.creator_event_room(p_event uuid) returns jsonb
language plpgsql stable security definer set search_path to 'public' as $$
declare v uuid := auth.uid(); e record;
begin
  if v is null then return jsonb_build_object('ok',false,'error','Log in first.'); end if;
  select * into e from creator_events where id=p_event;
  if e is null or e.status='cancelled' then return jsonb_build_object('ok',false,'error','Event not available.'); end if;
  if e.host=v or is_admin() then return jsonb_build_object('ok',true,'room_id',e.room_id,'room_pass',e.room_pass,'starts_at',e.starts_at); end if;
  if not exists(select 1 from creator_event_players where event_id=p_event and user_id=v and status='joined') then return jsonb_build_object('ok',false,'error','Join this event to see room details.'); end if;
  if now() < e.starts_at - interval '15 minutes' and e.status<>'live' then return jsonb_build_object('ok',false,'error','Room details unlock 15 minutes before start.','starts_at',e.starts_at); end if;
  if e.room_id is null then return jsonb_build_object('ok',false,'error','The host has not added room details yet.'); end if;
  return jsonb_build_object('ok',true,'room_id',e.room_id,'room_pass',e.room_pass,'starts_at',e.starts_at);
end $$;

create or replace function public.creator_event_set_room(p_event uuid, p_room_id text, p_room_pass text) returns jsonb
language plpgsql security definer set search_path to 'public' as $$
begin
  update creator_events set room_id=left(nullif(btrim(coalesce(p_room_id,'')),''),20), room_pass=left(nullif(btrim(coalesce(p_room_pass,'')),''),20) where id=p_event and host=auth.uid() and status in ('scheduled','live');
  if not found then return jsonb_build_object('ok',false,'error','Not your active event.'); end if;
  return jsonb_build_object('ok',true);
end $$;

create or replace function public.creator_event_report(p_event uuid, p_reason text) returns jsonb
language plpgsql security definer set search_path to 'public' as $$
begin
  if auth.uid() is null then return jsonb_build_object('ok',false,'error','Log in first.'); end if;
  insert into creator_event_reports(event_id,reporter,reason) values (p_event,auth.uid(),left(coalesce(p_reason,''),300)) on conflict do nothing;
  return jsonb_build_object('ok',true);
exception when others then return jsonb_build_object('ok',false,'error','Add a short reason (3+ characters).');
end $$;

-- admin
create or replace function public.admin_creator_queue() returns jsonb
language plpgsql stable security definer set search_path to 'public' as $$
begin
  if auth.uid() is null or not is_admin() then return jsonb_build_object('ok',false,'error','Admin only.'); end if;
  return jsonb_build_object('ok',true,
   'pending',coalesce((select jsonb_agg(jsonb_build_object('id',a.id,'user_id',a.user_id,'name',coalesce(p.ign,p.username),'channel_url',a.channel_url,'self_followers',a.self_followers,'note',a.note,'created_at',a.created_at) order by a.id) from creator_applications a join profiles p on p.id=a.user_id where a.status='pending'),'[]'::jsonb),
   'creators',coalesce((select jsonb_agg(jsonb_build_object('user_id',c.user_id,'name',coalesce(p.ign,p.username),'tier',c.tier,'self_followers',c.self_followers,'channel_url',c.channel_url,'active',c.active)) from creators c join profiles p on p.id=c.user_id),'[]'::jsonb),
   'events',coalesce((select jsonb_agg(jsonb_build_object('id',e.id,'title',e.title,'status',e.status,'starts_at',e.starts_at,'host',coalesce(p.ign,p.username),'reports',(select count(*) from creator_event_reports r where r.event_id=e.id)) order by e.created_at desc) from (select * from creator_events order by created_at desc limit 50) e join profiles p on p.id=e.host),'[]'::jsonb));
end $$;

create or replace function public.admin_creator_review(p_id bigint, p_approve boolean) returns jsonb
language plpgsql security definer set search_path to 'public' as $$
declare a record;
begin
  if auth.uid() is null or not is_admin() then return jsonb_build_object('ok',false,'error','Admin only.'); end if;
  select * into a from creator_applications where id=p_id and status='pending';
  if a is null then return jsonb_build_object('ok',false,'error','Not found or already reviewed.'); end if;
  update creator_applications set status=case when p_approve then 'approved' else 'rejected' end, reviewed_at=now() where id=p_id;
  if p_approve then
    insert into creators(user_id,channel_url,self_followers,tier,approved_by) values (a.user_id,a.channel_url,a.self_followers,creator_tier(a.self_followers),auth.uid())
    on conflict (user_id) do update set active=true, channel_url=excluded.channel_url, self_followers=excluded.self_followers, tier=excluded.tier, approved_by=excluded.approved_by, approved_at=now();
  end if;
  return jsonb_build_object('ok',true,'approved',p_approve);
end $$;

create or replace function public.admin_creator_revoke(p_user uuid) returns jsonb
language plpgsql security definer set search_path to 'public' as $$
begin
  if auth.uid() is null or not is_admin() then return jsonb_build_object('ok',false,'error','Admin only.'); end if;
  update creators set active=false where user_id=p_user;
  update creator_events set status='cancelled', stream_live=false where host=p_user and status in ('scheduled','live');
  update creator_applications set status='revoked', reviewed_at=now() where user_id=p_user and status='approved';
  return jsonb_build_object('ok',true);
end $$;

do $$ declare f text; begin
 foreach f in array array['creator_apply(text,int,text)','creator_status()','creator_dashboard()','creator_event_create(text,text,text,timestamptz,int,text,text,text)','creator_event_manage(uuid,text,timestamptz)','creator_event_set_stream(uuid,text,boolean)','creator_event_join(uuid)','creator_event_leave(uuid)','creator_event_players(uuid)','creator_event_kick(uuid,uuid,boolean)','creator_event_room(uuid)','creator_event_set_room(uuid,text,text)','creator_event_report(uuid,text)','admin_creator_queue()','admin_creator_review(bigint,boolean)','admin_creator_revoke(uuid)'] loop
  execute format('revoke all on function public.%s from public, anon', f);
  execute format('grant execute on function public.%s to authenticated', f);
 end loop; end $$;
notify pgrst, 'reload schema';
