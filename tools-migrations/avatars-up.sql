-- Profile avatars: own original art, AP only (no money path). Additive; undo in avatars-undo.sql
create table if not exists public.avatar_catalog (id text primary key, title text not null, theme text not null, tier text not null check (tier in ('free','common','rare','epic','legendary')), cost_ap int not null check (cost_ap >= 0), asset text not null, sort int not null default 0, active boolean not null default true, created_at timestamptz default now());
create table if not exists public.avatar_owners (user_id uuid not null references auth.users(id) on delete cascade, avatar_id text not null references public.avatar_catalog(id), acquired_at timestamptz default now(), primary key (user_id, avatar_id));
alter table public.profiles add column if not exists avatar_id text references public.avatar_catalog(id);
alter table public.avatar_catalog enable row level security; alter table public.avatar_owners enable row level security;
drop policy if exists avatar_catalog_read on public.avatar_catalog; create policy avatar_catalog_read on public.avatar_catalog for select to anon, authenticated using (active = true);
drop policy if exists avatar_owners_read_own on public.avatar_owners; create policy avatar_owners_read_own on public.avatar_owners for select to authenticated using (auth.uid() = user_id);
revoke all on public.avatar_catalog, public.avatar_owners from anon, authenticated;
grant select on public.avatar_catalog to anon, authenticated; grant select on public.avatar_owners to authenticated;
insert into public.avatar_catalog (id,title,theme,tier,cost_ap,asset,sort) values
('av-starter-fox','Starter Fox','animals','free',0,'starter-fox',0),
('av-starter-owl','Starter Owl','animals','free',0,'starter-owl',1),
('av-starter-crosshair','Starter Crosshair','gaming','free',0,'starter-crosshair',2),
('av-starter-shield','Starter Shield','esports','free',0,'starter-shield',3),
('av-starter-hex','Starter Hex','abstract','free',0,'starter-hex',4),
('av-starter-star','Starter Star','esports','free',0,'starter-star',5),
('av-ember-fox','Ember Fox','animals','common',100,'ember-fox',6),
('av-frost-wolf','Frost Wolf','animals','common',100,'frost-wolf',7),
('av-night-cat','Night Cat','animals','common',100,'night-cat',8),
('av-moss-owl','Moss Owl','animals','common',100,'moss-owl',9),
('av-iron-bull','Iron Bull','animals','common',100,'iron-bull',10),
('av-dust-lion','Dust Lion','animals','common',100,'dust-lion',11),
('av-pixel-heart','Pixel Heart','gaming','common',100,'pixel-heart',12),
('av-dice-roll','Dice Roll','gaming','common',100,'dice-roll',13),
('av-rookie-controller','Rookie Controller','gaming','common',100,'rookie-controller',14),
('av-plain-headset','Plain Headset','gaming','common',100,'plain-headset',15),
('av-tin-trophy','Tin Trophy','esports','common',100,'tin-trophy',16),
('av-spark-bolt','Spark Bolt','esports','common',100,'spark-bolt',17),
('av-wave-rider','Wave Rider','abstract','common',100,'wave-rider',18),
('av-calm-rings','Calm Rings','abstract','common',100,'calm-rings',19),
('av-triple-peak','Triple Peak','abstract','common',100,'triple-peak',20),
('av-chevron-rush','Chevron Rush','abstract','common',100,'chevron-rush',21),
('av-shadow-wolf','Shadow Wolf','animals','rare',300,'shadow-wolf',22),
('av-sky-eagle','Sky Eagle','animals','rare',300,'sky-eagle',23),
('av-viper-coil','Viper Coil','animals','rare',300,'viper-coil',24),
('av-blaze-lion','Blaze Lion','animals','rare',300,'blaze-lion',25),
('av-bullseye-pro','Bullseye Pro','gaming','rare',300,'bullseye-pro',26),
('av-skull-squad','Skull Squad','gaming','rare',300,'skull-squad',27),
('av-ace-controller','Ace Controller','gaming','rare',300,'ace-controller',28),
('av-joystick-hero','Joystick Hero','gaming','rare',300,'joystick-hero',29),
('av-silver-crown','Silver Crown','esports','rare',300,'silver-crown',30),
('av-swift-rocket','Swift Rocket','esports','rare',300,'swift-rocket',31),
('av-hex-core','Hex Core','abstract','rare',300,'hex-core',32),
('av-orbit-ring','Orbit Ring','abstract','rare',300,'orbit-ring',33),
('av-diamond-cut','Diamond Cut','abstract','rare',300,'diamond-cut',34),
('av-sun-burst','Sun Burst','abstract','rare',300,'sun-burst',35),
('av-storm-eagle','Storm Eagle','animals','epic',750,'storm-eagle',36),
('av-venom-fang','Venom Fang','animals','epic',750,'venom-fang',37),
('av-raging-bull','Raging Bull','animals','epic',750,'raging-bull',38),
('av-war-swords','War Swords','esports','epic',750,'war-swords',39),
('av-phoenix-flame','Phoenix Flame','esports','epic',750,'phoenix-flame',40),
('av-thunder-bolt','Thunder Bolt','esports','epic',750,'thunder-bolt',41),
('av-sniper-lock','Sniper Lock','gaming','epic',750,'sniper-lock',42),
('av-pro-headset','Pro Headset','gaming','epic',750,'pro-headset',43),
('av-gold-trophy','Gold Trophy','esports','epic',750,'gold-trophy',44),
('av-guard-shield','Guard Shield','esports','epic',750,'guard-shield',45),
('av-nova-star','Nova Star','esports','epic',750,'nova-star',46),
('av-galaxy-orbit','Galaxy Orbit','abstract','epic',750,'galaxy-orbit',47),
('av-golden-lion','Golden Lion','animals','legendary',1500,'golden-lion',48),
('av-imperial-crown','Imperial Crown','esports','legendary',1500,'imperial-crown',49),
('av-dragon-flame','Dragon Flame','esports','legendary',1500,'dragon-flame',50),
('av-champion-swords','Champion Swords','esports','legendary',1500,'champion-swords',51),
('av-apex-eagle','Apex Eagle','animals','legendary',1500,'apex-eagle',52),
('av-celestial-star','Celestial Star','esports','legendary',1500,'celestial-star',53)
on conflict (id) do nothing;

create or replace function public.trg_profiles_avatar_guard() returns trigger language plpgsql as $f$
begin
  if new.avatar_id is distinct from old.avatar_id and auth.uid() is not null and coalesce(current_setting('xr.avatar_rpc', true),'') <> '1' and not coalesce((select is_admin()), false) then
    raise exception 'Avatars are changed from the store only'; end if;
  return new;
end $f$;
drop trigger if exists profiles_avatar_guard on public.profiles;
create trigger profiles_avatar_guard before update on public.profiles for each row execute function public.trg_profiles_avatar_guard();

create or replace function public.buy_avatar(p_id text) returns jsonb language plpgsql security definer set search_path=public as $f$
declare me uuid := auth.uid(); a avatar_catalog%rowtype; v_bal int;
begin
  if me is null then return jsonb_build_object('ok',false,'error','Log in first.'); end if;
  select * into a from avatar_catalog where id = p_id and active;
  if not found then return jsonb_build_object('ok',false,'error','That avatar is not available.'); end if;
  perform pg_advisory_xact_lock(hashtext('ap:'||me::text));
  if a.cost_ap = 0 or exists (select 1 from avatar_owners where user_id=me and avatar_id=p_id) then
    return jsonb_build_object('ok',true,'already',true,'balance',_ap_bal(me)); end if;
  v_bal := _ap_bal(me);
  if v_bal < a.cost_ap then return jsonb_build_object('ok',false,'error','Not enough AP - this avatar costs '||a.cost_ap||' AP. You have '||v_bal||'.','balance',v_bal); end if;
  insert into points_ledger(user_id,delta,kind,meta) values (me,-a.cost_ap,'avatar',jsonb_build_object('avatar',a.title));
  insert into avatar_owners(user_id,avatar_id) values (me,p_id);
  return jsonb_build_object('ok',true,'balance',v_bal - a.cost_ap);
end $f$;

create or replace function public.equip_avatar(p_id text) returns jsonb language plpgsql security definer set search_path=public as $f$
declare me uuid := auth.uid(); a avatar_catalog%rowtype;
begin
  if me is null then return jsonb_build_object('ok',false,'error','Log in first.'); end if;
  select * into a from avatar_catalog where id = p_id and active;
  if not found then return jsonb_build_object('ok',false,'error','That avatar is not available.'); end if;
  if a.cost_ap > 0 and not exists (select 1 from avatar_owners where user_id=me and avatar_id=p_id) then
    return jsonb_build_object('ok',false,'error','Buy this avatar in the store first.'); end if;
  perform set_config('xr.avatar_rpc','1',true);
  update profiles set avatar_id = a.id, avatar_url = 'https://xarenaprivate.github.io/xarena/assets/avatars/' || a.asset || '.webp' where id = me;
  return jsonb_build_object('ok',true,'avatar_id',a.id,'avatar_url','https://xarenaprivate.github.io/xarena/assets/avatars/' || a.asset || '.webp');
end $f$;

create or replace function public.unequip_avatar() returns jsonb language plpgsql security definer set search_path=public as $f$
declare me uuid := auth.uid();
begin
  if me is null then return jsonb_build_object('ok',false,'error','Log in first.'); end if;
  perform set_config('xr.avatar_rpc','1',true);
  update profiles set avatar_id = null, avatar_url = null where id = me and avatar_id is not null;
  return jsonb_build_object('ok',true);
end $f$;

create or replace function public.my_avatars() returns jsonb language plpgsql security definer set search_path=public as $f$
declare me uuid := auth.uid();
begin
  if me is null then return jsonb_build_object('ok',false,'error','Log in first.'); end if;
  return jsonb_build_object('ok',true,'balance',_ap_bal(me),'equipped',(select avatar_id from profiles where id=me),
    'owned',coalesce((select jsonb_agg(avatar_id) from avatar_owners where user_id=me),'[]'::jsonb));
end $f$;
revoke all on function public.buy_avatar(text), public.equip_avatar(text), public.unequip_avatar(), public.my_avatars() from public, anon;
grant execute on function public.buy_avatar(text), public.equip_avatar(text), public.unequip_avatar(), public.my_avatars() to authenticated;
notify pgrst, 'reload schema';
