-- Creator Mode v2: sponsor requests (admin-mediated, no contact data), city squads (recruiting), public stats card. Additive.
create table if not exists public.creator_sponsor_requests(
  id bigserial primary key, creator uuid not null references auth.users(id) on delete cascade,
  sponsor_id bigint references public.sponsors(id) on delete set null, brand text, pitch text not null check (char_length(pitch) between 10 and 400),
  status text not null default 'pending' check (status in ('pending','introduced','declined')), created_at timestamptz not null default now());
alter table public.creator_events add column if not exists sponsor_id bigint references public.sponsors(id) on delete set null;
create table if not exists public.city_squads(
  id bigserial primary key, creator uuid not null references auth.users(id) on delete cascade,
  name text not null check (char_length(name) between 3 and 40), city text not null check (char_length(city) between 2 and 40), state text,
  description text check (char_length(description)<=300), slots int not null default 4 check (slots between 1 and 50),
  status text not null default 'open' check (status in ('open','closed')), created_at timestamptz not null default now());
create table if not exists public.city_squad_applications(
  id bigserial primary key, squad_id bigint not null references public.city_squads(id) on delete cascade, user_id uuid not null references auth.users(id) on delete cascade,
  note text check (char_length(note)<=200), status text not null default 'pending' check (status in ('pending','accepted','rejected','withdrawn')),
  created_at timestamptz not null default now(), unique(squad_id,user_id));
alter table public.creator_sponsor_requests enable row level security; alter table public.city_squads enable row level security; alter table public.city_squad_applications enable row level security;
revoke all on public.creator_sponsor_requests, public.city_squads, public.city_squad_applications from anon, authenticated;
-- guard wording (no money) on squads + sponsor pitch
create or replace function public.creator_text_guard2() returns trigger language plpgsql as $$
declare t text := lower(coalesce(to_jsonb(new)->>'name','')||' '||coalesce(to_jsonb(new)->>'description','')||' '||coalesce(to_jsonb(new)->>'pitch','')||' '||coalesce(to_jsonb(new)->>'note',''));
begin
  if t ~ '(\y(cash|paid|salary|fee|fees|paytm|phonepe|gpay|upi|bet|betting|wager|payout|deposit|withdraw)\y|₹|\yrs\.? ?[0-9])' or t ~ '[0-9]{8,}' then
    raise exception 'No money wording or phone numbers here.';
  end if; return new; end $$;
drop trigger if exists city_squads_guard on public.city_squads; create trigger city_squads_guard before insert or update on public.city_squads for each row execute function public.creator_text_guard2();
drop trigger if exists csr_guard on public.creator_sponsor_requests; create trigger csr_guard before insert on public.creator_sponsor_requests for each row execute function public.creator_text_guard2();
drop trigger if exists csa_guard on public.city_squad_applications; create trigger csa_guard before insert or update on public.city_squad_applications for each row execute function public.creator_text_guard2();

create or replace view public.city_squads_public as
 select s.id, s.name, s.city, s.state, s.description, s.slots, s.status, s.creator, p.ign as host_ign, p.username as host_username, p.avatar_url as host_avatar,
  (select count(*) from city_squad_applications a where a.squad_id=s.id and a.status='accepted') as members
 from city_squads s join creators c on c.user_id=s.creator and c.active join profiles p on p.id=s.creator;
revoke all on public.city_squads_public from anon, authenticated; grant select on public.city_squads_public to anon, authenticated;
create or replace view public.sponsors_public as select id,name,tier,url,image_url from sponsors where active and coalesce(image_approved,false);
revoke all on public.sponsors_public from anon, authenticated; grant select on public.sponsors_public to anon, authenticated;

create or replace function public.creator_sponsor_request(p_sponsor bigint, p_pitch text) returns jsonb
language plpgsql security definer set search_path to 'public' as $$
declare v uuid := auth.uid();
begin
  if v is null or not exists(select 1 from creators where user_id=v and active) then return jsonb_build_object('ok',false,'error','Creators only.'); end if;
  if (select count(*) from creator_sponsor_requests where creator=v and created_at>now()-interval '24 hours')>=3 then return jsonb_build_object('ok',false,'error','Limit: 3 requests per day.'); end if;
  insert into creator_sponsor_requests(creator,sponsor_id,brand,pitch) values (v,p_sponsor,(select name from sponsors where id=p_sponsor),btrim(coalesce(p_pitch,'')));
  return jsonb_build_object('ok',true,'note','XARENA admin will review and introduce you. Contact details are never shared automatically.');
exception when check_violation then return jsonb_build_object('ok',false,'error','Write a short pitch (10-400 characters).');
  when others then return jsonb_build_object('ok',false,'error',sqlerrm);
end $$;
create or replace function public.admin_creator_sponsor_requests() returns jsonb
language plpgsql stable security definer set search_path to 'public' as $$
begin
  if auth.uid() is null or not is_admin() then return jsonb_build_object('ok',false,'error','Admin only.'); end if;
  return jsonb_build_object('ok',true,'requests',coalesce((select jsonb_agg(jsonb_build_object('id',r.id,'creator',coalesce(p.ign,p.username),'brand',r.brand,'pitch',r.pitch,'status',r.status,'created_at',r.created_at) order by r.id desc) from creator_sponsor_requests r join profiles p on p.id=r.creator),'[]'::jsonb));
end $$;
create or replace function public.admin_creator_sponsor_handle(p_id bigint, p_status text) returns jsonb
language plpgsql security definer set search_path to 'public' as $$
begin
  if auth.uid() is null or not is_admin() then return jsonb_build_object('ok',false,'error','Admin only.'); end if;
  if p_status not in ('introduced','declined') then return jsonb_build_object('ok',false,'error','Bad status.'); end if;
  update creator_sponsor_requests set status=p_status where id=p_id; return jsonb_build_object('ok',true);
end $$;
create or replace function public.admin_creator_event_sponsor(p_event uuid, p_sponsor bigint) returns jsonb
language plpgsql security definer set search_path to 'public' as $$
begin
  if auth.uid() is null or not is_admin() then return jsonb_build_object('ok',false,'error','Admin only.'); end if;
  update creator_events set sponsor_id=p_sponsor where id=p_event; return jsonb_build_object('ok',true);
end $$;

create or replace function public.city_squad_create(p_name text, p_city text, p_state text, p_desc text, p_slots int) returns jsonb
language plpgsql security definer set search_path to 'public' as $$
declare v uuid := auth.uid(); nid bigint;
begin
  if v is null or not exists(select 1 from creators where user_id=v and active) then return jsonb_build_object('ok',false,'error','Creators only.'); end if;
  if (select count(*) from city_squads where creator=v and status='open')>=2 then return jsonb_build_object('ok',false,'error','You can have 2 open city squads.'); end if;
  insert into city_squads(creator,name,city,state,description,slots) values (v,btrim(p_name),btrim(p_city),nullif(btrim(coalesce(p_state,'')),''),nullif(btrim(coalesce(p_desc,'')),''),coalesce(p_slots,4)) returning id into nid;
  return jsonb_build_object('ok',true,'id',nid);
exception when check_violation then return jsonb_build_object('ok',false,'error','Check name (3-40), city and slots (1-50). No money wording.');
  when others then return jsonb_build_object('ok',false,'error',sqlerrm);
end $$;
create or replace function public.city_squad_apply(p_squad bigint, p_note text) returns jsonb
language plpgsql security definer set search_path to 'public' as $$
declare v uuid := auth.uid(); s record;
begin
  if v is null then return jsonb_build_object('ok',false,'error','Log in first.'); end if;
  select * into s from city_squads where id=p_squad;
  if s.id is null or s.status<>'open' then return jsonb_build_object('ok',false,'error','This squad is closed.'); end if;
  if s.creator=v then return jsonb_build_object('ok',false,'error','You are the host.'); end if;
  if exists(select 1 from profiles where id=v and banned) then return jsonb_build_object('ok',false,'error','Account restricted.'); end if;
  if (select count(*) from city_squad_applications where user_id=v and created_at>now()-interval '24 hours')>=5 then return jsonb_build_object('ok',false,'error','Limit: 5 applications per day.'); end if;
  insert into city_squad_applications(squad_id,user_id,note) values (p_squad,v,left(coalesce(p_note,''),200))
   on conflict (squad_id,user_id) do update set status='pending', note=excluded.note where city_squad_applications.status in ('withdrawn','rejected');
  return jsonb_build_object('ok',true,'status','pending');
exception when others then return jsonb_build_object('ok',false,'error','No money wording or phone numbers in the note.');
end $$;
-- host sees applicants: public-safe stats only (no phone, no UID)
create or replace function public.city_squad_applicants(p_squad bigint) returns jsonb
language plpgsql stable security definer set search_path to 'public' as $$
begin
  if not exists(select 1 from city_squads where id=p_squad and creator=auth.uid()) then return jsonb_build_object('ok',false,'error','Host only.'); end if;
  return jsonb_build_object('ok',true,'applicants',coalesce((select jsonb_agg(jsonb_build_object('id',a.id,'name',coalesce(p.ign,p.username),'username',p.username,'status',a.status,'note',a.note,'kd',p.kd,'matches',p.matches,'wins',p.wins,'rank',p.ff_rank) order by a.id) from city_squad_applications a join profiles p on p.id=a.user_id where a.squad_id=p_squad and a.status<>'withdrawn'),'[]'::jsonb));
end $$;
create or replace function public.city_squad_decide(p_app bigint, p_accept boolean) returns jsonb
language plpgsql security definer set search_path to 'public' as $$
declare a record; s record; n int;
begin
  select * into a from city_squad_applications where id=p_app; select * into s from city_squads where id=a.squad_id;
  if a.id is null or s.creator<>auth.uid() then return jsonb_build_object('ok',false,'error','Host only.'); end if;
  if p_accept then select count(*) into n from city_squad_applications where squad_id=s.id and status='accepted';
    if n>=s.slots then return jsonb_build_object('ok',false,'error','Squad is full.'); end if; end if;
  update city_squad_applications set status=case when p_accept then 'accepted' else 'rejected' end where id=p_app;
  return jsonb_build_object('ok',true);
end $$;
create or replace function public.city_squad_close(p_squad bigint) returns jsonb
language plpgsql security definer set search_path to 'public' as $$
begin
  update city_squads set status='closed' where id=p_squad and (creator=auth.uid() or is_admin());
  if not found then return jsonb_build_object('ok',false,'error','Not your squad.'); end if; return jsonb_build_object('ok',true);
end $$;

-- public-safe player stats card (anon ok): no phone/UID/device/location
create or replace function public.player_stats_card(p_username text) returns jsonb
language plpgsql stable security definer set search_path to 'public' as $$
declare p record; lv int; xpv int;
begin
  select * into p from profiles where lower(username)=lower(trim(p_username)) and not coalesce(banned,false);
  if p.id is null then return jsonb_build_object('ok',false,'error','Player not found.'); end if;
  select coalesce(x.xp,0) into xpv from player_game_stats x where x.user_id=p.id;
  select level into lv from level_from_xp(coalesce(xpv,0)) limit 1;
  return jsonb_build_object('ok',true,'username',p.username,'name',coalesce(p.ign,p.username),'avatar_url',p.avatar_url,'role',p.role,'rank',p.ff_rank,'kd',p.kd,'matches',p.matches,'wins',p.wins,
    'level',coalesce(lv,1),'verified_creator',exists(select 1 from creators c where c.user_id=p.id and c.active),'tier',(select tier from creators c where c.user_id=p.id and c.active));
end $$;
do $$ declare f text; begin
 foreach f in array array['creator_sponsor_request(bigint,text)','admin_creator_sponsor_requests()','admin_creator_sponsor_handle(bigint,text)','admin_creator_event_sponsor(uuid,bigint)','city_squad_create(text,text,text,text,int)','city_squad_apply(bigint,text)','city_squad_applicants(bigint)','city_squad_decide(bigint,boolean)','city_squad_close(bigint)'] loop
  execute format('revoke all on function public.%s from public, anon', f); execute format('grant execute on function public.%s to authenticated', f); end loop;
 revoke all on function public.player_stats_card(text) from public; grant execute on function public.player_stats_card(text) to anon, authenticated;
end $$;
notify pgrst, 'reload schema';
