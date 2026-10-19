-- Squad logo store (v0.16.3) - additive only. Applied via Supabase management API.
-- UNDO at the bottom (commented).

create table if not exists public.squad_logos (
  id uuid primary key default gen_random_uuid(),
  title text not null,
  image_url text not null,
  tier text not null default 'common' check (tier in ('common','rare','epic','legendary')),
  cost_ap integer not null default 100,
  sort integer not null default 0,
  active boolean not null default true,
  created_at timestamptz not null default now()
);

create table if not exists public.squad_logo_owners (
  user_id uuid not null references auth.users(id) on delete cascade,
  logo_id uuid not null references public.squad_logos(id) on delete cascade,
  bought_at timestamptz not null default now(),
  primary key (user_id, logo_id)
);

alter table public.squad_logos enable row level security;
alter table public.squad_logo_owners enable row level security;

drop policy if exists squad_logos_read on public.squad_logos;
create policy squad_logos_read on public.squad_logos for select using (active);
drop policy if exists squad_logo_owners_read on public.squad_logo_owners;
create policy squad_logo_owners_read on public.squad_logo_owners for select using (auth.uid() = user_id);

-- buy a logo with AP (mirrors redeem_reward's ledger pattern; instant - digital good)
create or replace function public.buy_squad_logo(p_logo uuid)
returns jsonb language plpgsql security definer set search_path = public as $fn$
declare v_uid uuid := auth.uid(); v_logo public.squad_logos%rowtype; v_balance int;
begin
  if v_uid is null then return jsonb_build_object('ok', false, 'error', 'Log in first.'); end if;
  select * into v_logo from public.squad_logos where id = p_logo and active = true;
  if not found then return jsonb_build_object('ok', false, 'error', 'That logo is not available.'); end if;
  if exists (select 1 from public.squad_logo_owners where user_id = v_uid and logo_id = p_logo) then
    select coalesce(sum(delta), 0) into v_balance from public.points_ledger where user_id = v_uid;
    return jsonb_build_object('ok', true, 'already', true, 'balance', v_balance);
  end if;
  select coalesce(sum(delta), 0) into v_balance from public.points_ledger where user_id = v_uid;
  if v_balance < v_logo.cost_ap then
    return jsonb_build_object('ok', false, 'error', 'Not enough AP - this logo costs ' || v_logo.cost_ap || ' AP.', 'balance', v_balance);
  end if;
  insert into public.points_ledger (user_id, delta, kind, meta) values (v_uid, -v_logo.cost_ap, 'squad_logo', jsonb_build_object('logo', v_logo.title));
  insert into public.squad_logo_owners (user_id, logo_id) values (v_uid, p_logo);
  return jsonb_build_object('ok', true, 'balance', v_balance - v_logo.cost_ap);
end $fn$;

-- equip an owned store logo (captain only); the (uuid, text) custom-URL variant stays untouched
create or replace function public.set_squad_logo(p_squad uuid, p_logo uuid)
returns jsonb language plpgsql security definer set search_path = public as $fn$
declare v_uid uuid := auth.uid(); v_url text;
begin
  if v_uid is null then return jsonb_build_object('ok', false, 'error', 'Log in first.'); end if;
  if not exists (select 1 from public.squad_members where squad_id = p_squad and user_id = v_uid and role = 'captain') then
    return jsonb_build_object('ok', false, 'error', 'Only the captain can set the team logo.');
  end if;
  if not exists (select 1 from public.squad_logo_owners where user_id = v_uid and logo_id = p_logo) then
    return jsonb_build_object('ok', false, 'error', 'Buy this logo from the store first.');
  end if;
  select image_url into v_url from public.squad_logos where id = p_logo;
  update public.squads set logo_url = v_url where id = p_squad;
  return jsonb_build_object('ok', true);
end $fn$;

-- my owned logos
create or replace function public.my_squad_logos()
returns jsonb language plpgsql security definer set search_path = public as $fn$
declare v_uid uuid := auth.uid();
begin
  if v_uid is null then return jsonb_build_object('ok', false, 'error', 'Log in first.'); end if;
  return jsonb_build_object('ok', true, 'logos', coalesce((
    select jsonb_agg(jsonb_build_object('id', l.id, 'title', l.title, 'image_url', l.image_url, 'tier', l.tier, 'cost_ap', l.cost_ap) order by l.sort)
    from public.squad_logos l join public.squad_logo_owners o on o.logo_id = l.id
    where o.user_id = v_uid and l.active), '[]'::jsonb));
end $fn$;

-- catalog seed (40 original generated emblems, CDN-hosted)
insert into public.squad_logos (title, image_url, tier, cost_ap, sort) values
('Apex Shield', 'https://xarenaprivate.github.io/xarena/logos/apex-shield.webp', 'common', 100, 1),
  ('Bolt Core', 'https://xarenaprivate.github.io/xarena/logos/bolt-core.webp', 'common', 100, 2),
  ('Iron Chevron', 'https://xarenaprivate.github.io/xarena/logos/iron-chevron.webp', 'common', 100, 3),
  ('Night Fang', 'https://xarenaprivate.github.io/xarena/logos/night-fang.webp', 'common', 100, 4),
  ('Pulse Ring', 'https://xarenaprivate.github.io/xarena/logos/pulse-ring.webp', 'common', 100, 5),
  ('Steel Claw', 'https://xarenaprivate.github.io/xarena/logos/steel-claw.webp', 'common', 100, 6),
  ('Valor Star', 'https://xarenaprivate.github.io/xarena/logos/valor-star.webp', 'common', 100, 7),
  ('War Diamond', 'https://xarenaprivate.github.io/xarena/logos/war-diamond.webp', 'common', 100, 8),
  ('Cobalt Spear', 'https://xarenaprivate.github.io/xarena/logos/cobalt-spear.webp', 'common', 100, 9),
  ('Onyx Gate', 'https://xarenaprivate.github.io/xarena/logos/onyx-gate.webp', 'common', 100, 10),
  ('Crimson Mark', 'https://xarenaprivate.github.io/xarena/logos/crimson-mark.webp', 'common', 100, 11),
  ('Jade Tusk', 'https://xarenaprivate.github.io/xarena/logos/jade-tusk.webp', 'common', 100, 12),
  ('Amber Watch', 'https://xarenaprivate.github.io/xarena/logos/amber-watch.webp', 'common', 100, 13),
  ('Violet Edge', 'https://xarenaprivate.github.io/xarena/logos/violet-edge.webp', 'common', 100, 14),
  ('Ember Triad', 'https://xarenaprivate.github.io/xarena/logos/ember-triad.webp', 'common', 100, 15),
  ('Frost Bar', 'https://xarenaprivate.github.io/xarena/logos/frost-bar.webp', 'common', 100, 16),
  ('Skywing', 'https://xarenaprivate.github.io/xarena/logos/skywing.webp', 'rare', 300, 17),
  ('Venom Strike', 'https://xarenaprivate.github.io/xarena/logos/venom-strike.webp', 'rare', 300, 18),
  ('Solar Crown', 'https://xarenaprivate.github.io/xarena/logos/solar-crown.webp', 'rare', 300, 19),
  ('Nova Claw', 'https://xarenaprivate.github.io/xarena/logos/nova-claw.webp', 'rare', 300, 20),
  ('Twin Serpent', 'https://xarenaprivate.github.io/xarena/logos/twin-serpent.webp', 'rare', 300, 21),
  ('Blaze Shield', 'https://xarenaprivate.github.io/xarena/logos/blaze-shield.webp', 'rare', 300, 22),
  ('Aqua Reaper', 'https://xarenaprivate.github.io/xarena/logos/aqua-reaper.webp', 'rare', 300, 23),
  ('Ember Phoenix', 'https://xarenaprivate.github.io/xarena/logos/ember-phoenix.webp', 'rare', 300, 24),
  ('Thorn Star', 'https://xarenaprivate.github.io/xarena/logos/thorn-star.webp', 'rare', 300, 25),
  ('Cobalt Hornet', 'https://xarenaprivate.github.io/xarena/logos/cobalt-hornet.webp', 'rare', 300, 26),
  ('Ruby Vanguard', 'https://xarenaprivate.github.io/xarena/logos/ruby-vanguard.webp', 'rare', 300, 27),
  ('Mint Raptor', 'https://xarenaprivate.github.io/xarena/logos/mint-raptor.webp', 'rare', 300, 28),
  ('Void Emperor', 'https://xarenaprivate.github.io/xarena/logos/void-emperor.webp', 'epic', 750, 29),
  ('Inferno Drake', 'https://xarenaprivate.github.io/xarena/logos/inferno-drake.webp', 'epic', 750, 30),
  ('Celestial Guard', 'https://xarenaprivate.github.io/xarena/logos/celestial-guard.webp', 'epic', 750, 31),
  ('Storm Leviathan', 'https://xarenaprivate.github.io/xarena/logos/storm-leviathan.webp', 'epic', 750, 32),
  ('Phantom Owl', 'https://xarenaprivate.github.io/xarena/logos/phantom-owl.webp', 'epic', 750, 33),
  ('Titan Breaker', 'https://xarenaprivate.github.io/xarena/logos/titan-breaker.webp', 'epic', 750, 34),
  ('Aurora Viper', 'https://xarenaprivate.github.io/xarena/logos/aurora-viper.webp', 'epic', 750, 35),
  ('Savage Kraken', 'https://xarenaprivate.github.io/xarena/logos/savage-kraken.webp', 'epic', 750, 36),
  ('Golden Imperium', 'https://xarenaprivate.github.io/xarena/logos/golden-imperium.webp', 'legendary', 1500, 37),
  ('Radiant Falcon', 'https://xarenaprivate.github.io/xarena/logos/radiant-falcon.webp', 'legendary', 1500, 38),
  ('Eternal Dragon', 'https://xarenaprivate.github.io/xarena/logos/eternal-dragon.webp', 'legendary', 1500, 39),
  ('Celestial X', 'https://xarenaprivate.github.io/xarena/logos/celestial-x.webp', 'legendary', 1500, 40)
on conflict do nothing;

-- UNDO:
-- drop function if exists public.my_squad_logos();
-- drop function if exists public.set_squad_logo(uuid, uuid);
-- drop function if exists public.buy_squad_logo(uuid);
-- drop table if exists public.squad_logo_owners;
-- drop table if exists public.squad_logos;
