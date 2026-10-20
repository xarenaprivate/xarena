-- Home "Today" work: public registration counts, hall-of-fame logo, daily free cosmetic avatar.
create or replace function public.tournament_reg_counts() returns jsonb
language sql stable security definer set search_path to 'public' as $$
  select coalesce(jsonb_agg(jsonb_build_object('tournament_id', t.id, 'registered',
      (select count(*) from registrations r where r.tournament_id=t.id and r.status not in ('rejected','cancelled','declined')), 'slots', t.slots) order by t.sort, t.created_at), '[]'::jsonb)
  from tournaments t
$$;
grant execute on function public.tournament_reg_counts() to anon, authenticated;

alter table public.hall_of_fame add column if not exists logo_url text;

create or replace function public._daily_avatar_id() returns text
language sql stable set search_path to 'public' as $$
  select id from (select id, row_number() over (order by id) rn, count(*) over () n from avatar_catalog where active and cost_ap > 0) z
  where rn = 1 + (abs(hashtext(((now() at time zone 'Asia/Kolkata')::date)::text)) % n)
$$;

create or replace function public.daily_avatar() returns jsonb
language plpgsql stable security definer set search_path to 'public' as $$
declare v_id text := public._daily_avatar_id(); a avatar_catalog%rowtype; me uuid := auth.uid();
begin
  if v_id is null then return jsonb_build_object('ok', true, 'avatar', null); end if;
  select * into a from avatar_catalog where id = v_id;
  return jsonb_build_object('ok', true, 'date', ((now() at time zone 'Asia/Kolkata')::date)::text,
    'avatar', jsonb_build_object('id', a.id, 'title', a.title, 'theme', a.theme, 'tier', a.tier, 'asset', a.asset),
    'owned', case when me is null then null else exists(select 1 from avatar_owners where user_id=me and avatar_id=a.id) end);
end $$;
grant execute on function public.daily_avatar() to anon, authenticated;

create or replace function public.daily_avatar_claim() returns jsonb
language plpgsql security definer set search_path to 'public' as $$
declare v_id text := public._daily_avatar_id(); me uuid := auth.uid(); n int;
begin
  if me is null then return jsonb_build_object('ok', false, 'error', 'Log in first.'); end if;
  if v_id is null then return jsonb_build_object('ok', false, 'error', 'No avatar today.'); end if;
  insert into avatar_owners(user_id, avatar_id) values (me, v_id) on conflict do nothing;
  get diagnostics n = row_count;
  return jsonb_build_object('ok', true, 'already', n = 0, 'avatar_id', v_id);
end $$;
revoke all on function public.daily_avatar_claim() from public, anon; grant execute on function public.daily_avatar_claim() to authenticated;
