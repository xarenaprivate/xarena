-- Bundle 1 (additive): spotlights (MVP/squad of week/player of day), notices, feedback box, points table, squad profile, head-to-head, feature flags.
create table if not exists public.spotlights(
  id uuid primary key default gen_random_uuid(),
  kind text not null check (kind in ('mvp_match','mvp_week','squad_week','player_day','clip_week')),
  tournament_id text, name text not null check (char_length(name) between 1 and 80),
  detail text check (detail is null or char_length(detail)<=240), logo_url text, ref_url text,
  created_at timestamptz not null default now());
alter table public.spotlights enable row level security;
drop policy if exists "spot read" on public.spotlights; create policy "spot read" on public.spotlights for select using (true);
drop policy if exists "spot admin" on public.spotlights; create policy "spot admin" on public.spotlights for all to authenticated using (is_admin()) with check (is_admin());

create table if not exists public.notices(
  id uuid primary key default gen_random_uuid(),
  title text not null check (char_length(title) between 1 and 80),
  body text check (body is null or char_length(body)<=400), link text,
  pinned boolean not null default false,
  starts_at timestamptz not null default now(), expires_at timestamptz,
  created_at timestamptz not null default now());
alter table public.notices enable row level security;
drop policy if exists "notices read" on public.notices; create policy "notices read" on public.notices for select using (starts_at <= now() and (expires_at is null or expires_at > now()) or is_admin());
drop policy if exists "notices admin" on public.notices; create policy "notices admin" on public.notices for all to authenticated using (is_admin()) with check (is_admin());

create table if not exists public.feedback(
  id uuid primary key default gen_random_uuid(),
  user_id uuid, kind text not null check (kind in ('feedback','bug','idea')),
  body text not null check (char_length(body) between 5 and 600),
  status text not null default 'new', created_at timestamptz not null default now());
alter table public.feedback enable row level security;
drop policy if exists "feedback admin" on public.feedback; create policy "feedback admin" on public.feedback for all to authenticated using (is_admin()) with check (is_admin());
create or replace function public.feedback_submit(p_kind text, p_body text) returns jsonb
language plpgsql security definer set search_path to 'public' as $$
declare me uuid := auth.uid();
begin
  if me is null then return jsonb_build_object('ok',false,'error','Log in to send feedback.'); end if;
  if p_kind not in ('feedback','bug','idea') then return jsonb_build_object('ok',false,'error','Pick a type.'); end if;
  if char_length(trim(coalesce(p_body,''))) < 5 or char_length(p_body) > 600 then return jsonb_build_object('ok',false,'error','Write 5 to 600 characters.'); end if;
  if (select count(*) from feedback where user_id=me and created_at > now()-interval '1 day') >= 5 then return jsonb_build_object('ok',false,'error','Daily limit reached. Try tomorrow.'); end if;
  insert into feedback(user_id,kind,body) values (me,p_kind,trim(p_body));
  return jsonb_build_object('ok',true);
end $$;
grant execute on function public.feedback_submit(text,text) to authenticated;

create or replace function public.points_table(p_tournament text default null) returns jsonb
language sql stable security definer set search_path to 'public' as $$
  select coalesce(jsonb_agg(x order by (x->>'points')::int desc, (x->>'kills')::int desc), '[]'::jsonb) from (
    select jsonb_build_object('squad', coalesce(nullif(trim(e.squad),''),'(no squad)'), 'matches', count(distinct e.match_id),
      'kills', coalesce(sum(e.kills),0), 'points', coalesce(sum(e.points),0), 'best_place', min(e.placement)) x
    from lb_entries e join matches m on m.id=e.match_id
    where p_tournament is null or m.tournament_id=p_tournament
    group by coalesce(nullif(trim(e.squad),''),'(no squad)')) s
$$;
grant execute on function public.points_table(text) to anon, authenticated;

create or replace function public.squad_profile(p_squad uuid) returns jsonb
language plpgsql stable security definer set search_path to 'public' as $$
declare s squads%rowtype; rec jsonb; mem jsonb;
begin
  select * into s from squads where id=p_squad; if not found then return jsonb_build_object('ok',false); end if;
  select coalesce(jsonb_agg(jsonb_build_object('ign',coalesce(p.ign,p.username),'role',m.role,'avatar_url',p.avatar_url) order by m.joined_at),'[]'::jsonb)
    into mem from squad_members m join profiles p on p.id=m.user_id where m.squad_id=p_squad;
  select jsonb_build_object('matches',count(distinct e.match_id),'kills',coalesce(sum(e.kills),0),'points',coalesce(sum(e.points),0),'best_place',min(e.placement))
    into rec from lb_entries e where lower(trim(e.squad))=lower(trim(s.name));
  return jsonb_build_object('ok',true,'id',s.id,'name',s.name,'tag',s.tag,'bio',s.bio,'logo_url',s.logo_url,'members',mem,'record',rec,
    'tournaments',(select coalesce(jsonb_agg(distinct r.tournament_id),'[]'::jsonb) from registrations r where lower(trim(r.squad))=lower(trim(s.name)) and r.status not in ('rejected','cancelled','declined')));
end $$;
grant execute on function public.squad_profile(uuid) to anon, authenticated;

create or replace function public.head_to_head(p_a text, p_b text) returns jsonb
language sql stable security definer set search_path to 'public' as $$
  with a as (select match_id, min(placement) pl, sum(kills) k from lb_entries where lower(trim(squad))=lower(trim(p_a)) group by 1),
       b as (select match_id, min(placement) pl, sum(kills) k from lb_entries where lower(trim(squad))=lower(trim(p_b)) group by 1),
       j as (select a.match_id, a.pl apl, b.pl bpl, a.k ak, b.k bk from a join b using(match_id))
  select jsonb_build_object('played',(select count(*) from j),
    'a_wins',(select count(*) from j where apl is not null and (bpl is null or apl<bpl)),
    'b_wins',(select count(*) from j where bpl is not null and (apl is null or bpl<apl)),
    'a_kills',(select coalesce(sum(ak),0) from j),'b_kills',(select coalesce(sum(bk),0) from j))
$$;
grant execute on function public.head_to_head(text,text) to anon, authenticated;

insert into public.site_settings(key,value) values ('flags','{"live_thread":false,"mini_cup":false,"spectator":false,"dup_check":false,"waitlist":false}'::jsonb) on conflict (key) do nothing;
notify pgrst, 'reload schema';
