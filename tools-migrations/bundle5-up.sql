-- Bundle 5 (additive, flag-gated OFF): live match thread + spectator info. No Room ID, phone or UID is ever returned.
create table if not exists public.match_thread_posts(
  id bigint generated always as identity primary key,
  match_id uuid not null references public.matches(id) on delete cascade,
  user_id uuid not null, body text not null check (char_length(body) between 1 and 200),
  hidden boolean not null default false, created_at timestamptz not null default now());
create index if not exists match_thread_posts_m on public.match_thread_posts(match_id, id desc);
alter table public.match_thread_posts enable row level security;
drop policy if exists "thread admin" on public.match_thread_posts;
create policy "thread admin" on public.match_thread_posts for all to authenticated using (is_admin()) with check (is_admin());
create table if not exists public.match_thread_reports(
  id bigint generated always as identity primary key, post_id bigint not null references public.match_thread_posts(id) on delete cascade,
  reporter uuid not null, reason text check (reason is null or char_length(reason)<=120), created_at timestamptz default now(), unique(post_id, reporter));
alter table public.match_thread_reports enable row level security;
drop policy if exists "threadrep admin" on public.match_thread_reports;
create policy "threadrep admin" on public.match_thread_reports for all to authenticated using (is_admin()) with check (is_admin());

create or replace function public._flag(p_key text) returns boolean language sql stable security definer set search_path to 'public' as $$
  select coalesce((select (value->>p_key)::boolean from site_settings where key='flags'), false) $$;

create or replace function public.thread_post(p_match uuid, p_body text) returns jsonb
language plpgsql security definer set search_path to 'public' as $$
declare me uuid := auth.uid(); b text := trim(coalesce(p_body,''));
begin
  if not _flag('live_thread') then return jsonb_build_object('ok',false,'error','The live thread is not open yet.'); end if;
  if me is null then return jsonb_build_object('ok',false,'error','Log in first.'); end if;
  if exists (select 1 from profiles where id=me and coalesce(banned,false)) then return jsonb_build_object('ok',false,'error','Your account cannot post.'); end if;
  if not exists (select 1 from matches where id=p_match) then return jsonb_build_object('ok',false,'error','Match not found.'); end if;
  if char_length(b) < 1 or char_length(b) > 200 then return jsonb_build_object('ok',false,'error','Write 1 to 200 characters.'); end if;
  if b ~ '[0-9][ -]?[0-9][ -]?[0-9][ -]?[0-9][ -]?[0-9][ -]?[0-9][ -]?[0-9][ -]?[0-9]' or b ~* '(https?://|www\.|t\.me/|wa\.me/)' then
    return jsonb_build_object('ok',false,'error','No phone numbers, IDs or links in the thread.'); end if;
  if exists (select 1 from match_thread_posts where user_id=me and created_at > now()-interval '5 seconds') then return jsonb_build_object('ok',false,'error','Slow down a little.'); end if;
  if (select count(*) from match_thread_posts where user_id=me and created_at > now()-interval '1 hour') >= 30 then return jsonb_build_object('ok',false,'error','Hourly limit reached.'); end if;
  insert into match_thread_posts(match_id,user_id,body) values (p_match,me,b);
  return jsonb_build_object('ok',true);
end $$;
grant execute on function public.thread_post(uuid,text) to authenticated;

create or replace function public.thread_list(p_match uuid, p_limit int default 50) returns jsonb
language plpgsql stable security definer set search_path to 'public' as $$
begin
  if not _flag('live_thread') then return jsonb_build_object('ok',false,'error','The live thread is not open yet.','posts','[]'::jsonb); end if;
  return jsonb_build_object('ok',true,'posts',coalesce((select jsonb_agg(x order by (x->>'id')::bigint desc) from (
    select jsonb_build_object('id',t.id,'name',coalesce(p.ign,p.username,'player'),'body',t.body,'created_at',t.created_at,'mine',(t.user_id=auth.uid())) x
    from match_thread_posts t left join profiles p on p.id=t.user_id where t.match_id=p_match and not t.hidden
    order by t.id desc limit least(greatest(coalesce(p_limit,50),1),100)) s),'[]'::jsonb));
end $$;
grant execute on function public.thread_list(uuid,int) to anon, authenticated;

create or replace function public.thread_report(p_post bigint, p_reason text default null) returns jsonb
language plpgsql security definer set search_path to 'public' as $$
declare me uuid := auth.uid(); n int;
begin
  if me is null then return jsonb_build_object('ok',false,'error','Log in first.'); end if;
  insert into match_thread_reports(post_id,reporter,reason) values (p_post,me,left(p_reason,120)) on conflict do nothing;
  select count(*) into n from match_thread_reports where post_id=p_post;
  if n >= 3 then update match_thread_posts set hidden=true where id=p_post; end if;
  return jsonb_build_object('ok',true);
end $$;
grant execute on function public.thread_report(bigint,text) to authenticated;

create or replace function public.admin_thread_hide(p_post bigint, p_hidden boolean default true) returns jsonb
language plpgsql security definer set search_path to 'public' as $$
begin
  if not is_admin() then return jsonb_build_object('ok',false,'error','Admin only.'); end if;
  update match_thread_posts set hidden=p_hidden where id=p_post; return jsonb_build_object('ok',true);
end $$;
grant execute on function public.admin_thread_hide(bigint,boolean) to authenticated;

create or replace function public.spectator_info(p_tournament text) returns jsonb
language plpgsql stable security definer set search_path to 'public' as $$
begin
  if not _flag('spectator') then return jsonb_build_object('ok',false,'error','The watch page is not open yet.'); end if;
  return jsonb_build_object('ok',true,
    'tournament',(select jsonb_build_object('id',id,'name',name,'status',status,'date_text',date_text,'slots',slots) from tournaments where id=p_tournament),
    'matches',coalesce((select jsonb_agg(jsonb_build_object('id',id,'title',title,'starts_at',starts_at,'status',status) order by starts_at) from matches_public where tournament_id=p_tournament),'[]'::jsonb),
    'points',points_table(p_tournament),
    'stream',(select value from site_settings where key='stream'));
end $$;
grant execute on function public.spectator_info(text) to anon, authenticated;
notify pgrst, 'reload schema';
