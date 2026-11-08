-- Explicit admin-selected relation; no automatic event or match assignment.
create or replace function public.admin_set_live_stream(p_video text,p_title text,p_active boolean,p_tournament text default null,p_match uuid default null)
returns jsonb language plpgsql security definer set search_path=pg_catalog as $$
declare t text:=nullif(trim(p_tournament),''); v text:=trim(coalesce(p_video,'')); data jsonb;
begin
 if auth.uid() is null or not public.is_admin() then return jsonb_build_object('ok',false,'error','Admin only.');end if;
 if v<>'' and v !~ '^[A-Za-z0-9_-]{11}$' then return jsonb_build_object('ok',false,'error','Invalid video ID.');end if;
 if p_active and v='' then return jsonb_build_object('ok',false,'error','Add a video before going live.');end if;
 if t is not null and not exists(select 1 from public.tournaments where id=t) then return jsonb_build_object('ok',false,'error','Tournament not found.');end if;
 if p_match is not null and (t is null or not exists(select 1 from public.matches where id=p_match and tournament_id=t)) then return jsonb_build_object('ok',false,'error','Select a match from the connected tournament.');end if;
 data:=jsonb_build_object('video_id',v,'title',left(trim(coalesce(p_title,'')),80),'active',coalesce(p_active,false),'tournament_id',t,'match_id',p_match);
 insert into public.site_settings(key,value,updated_at)values('youtube_live',data,clock_timestamp())on conflict(key)do update set value=excluded.value,updated_at=excluded.updated_at;
 return jsonb_build_object('ok',true);
end $$;
revoke all on function public.admin_set_live_stream(text,text,boolean,text,uuid) from public,anon;
grant execute on function public.admin_set_live_stream(text,text,boolean,text,uuid) to authenticated;
create or replace function public.xr_live_roster(p_tournament text)
returns jsonb language plpgsql stable security definer set search_path=pg_catalog as $$
declare cfg jsonb;
begin
 select value into cfg from public.site_settings where key='youtube_live';
 if p_tournament is null or p_tournament='' or cfg->>'tournament_id' is distinct from p_tournament then return jsonb_build_object('ok',false,'error','No tournament is connected to this stream.','players','[]'::jsonb,'count',0);end if;
 return (select jsonb_build_object('ok',true,'players',coalesce(jsonb_agg(jsonb_build_object('name',name,'squad',squad) order by squad,name),'[]'::jsonb),'count',count(*))from(select left(trim(r.ign),80)as name,left(r.squad,100)as squad from public.registrations r where r.tournament_id=p_tournament and r.status not in('rejected','cancelled','declined','waitlist') and nullif(trim(r.ign),'') is not null limit 1000)s);
end $$;
revoke all on function public.xr_live_roster(text) from public;
grant execute on function public.xr_live_roster(text) to anon,authenticated;
create table if not exists public.live_tournament_posts(id bigint generated always as identity primary key,tournament_id text not null references public.tournaments(id)on delete cascade,user_id uuid not null,body text not null check(char_length(body) between 1 and 200),hidden boolean not null default false,created_at timestamptz not null default now());
create index if not exists live_tournament_posts_t on public.live_tournament_posts(tournament_id,id desc);
alter table public.live_tournament_posts enable row level security;
revoke all on public.live_tournament_posts from public,anon,authenticated;
drop policy if exists live_posts_admin on public.live_tournament_posts;
create policy live_posts_admin on public.live_tournament_posts for all to authenticated using(public.is_admin())with check(public.is_admin());
grant select,update on public.live_tournament_posts to authenticated;
create table if not exists public.live_tournament_reports(post_id bigint not null references public.live_tournament_posts(id)on delete cascade,reporter uuid not null,reason text,created_at timestamptz not null default now(),primary key(post_id,reporter));
alter table public.live_tournament_reports enable row level security;
revoke all on public.live_tournament_reports from public,anon,authenticated;
drop policy if exists live_reports_admin on public.live_tournament_reports;
create policy live_reports_admin on public.live_tournament_reports for select to authenticated using(public.is_admin());
grant select on public.live_tournament_reports to authenticated;
create or replace function public.xr_live_thread_list(p_tournament text,p_limit int default 50)
returns jsonb language plpgsql stable security definer set search_path=pg_catalog as $$
begin
 if not public._flag('live_thread') then return jsonb_build_object('ok',false,'error','Live comments are not open yet.','posts','[]'::jsonb);end if;
 if not exists(select 1 from public.site_settings where key='youtube_live'and value->>'tournament_id'=p_tournament)then return jsonb_build_object('ok',false,'error','This tournament is not connected.','posts','[]'::jsonb);end if;
 return jsonb_build_object('ok',true,'posts',coalesce((select jsonb_agg(x order by (x->>'id')::bigint desc)from(select jsonb_build_object('id',t.id,'name',coalesce(nullif(p.ign,''),p.username,'player'),'body',t.body,'created_at',t.created_at,'mine',coalesce(t.user_id=auth.uid(),false))x from public.live_tournament_posts t left join public.profiles p on p.id=t.user_id where t.tournament_id=p_tournament and not t.hidden order by t.id desc limit least(greatest(coalesce(p_limit,50),1),100))s),'[]'::jsonb));
end $$;
create or replace function public.xr_live_thread_post(p_tournament text,p_body text)
returns jsonb language plpgsql security definer set search_path=pg_catalog as $$
declare me uuid:=auth.uid();b text:=trim(coalesce(p_body,''));
begin
 if not public._flag('live_thread')then return jsonb_build_object('ok',false,'error','Live comments are not open yet.');end if;
 if me is null then return jsonb_build_object('ok',false,'error','Log in first.');end if;
 if exists(select 1 from public.profiles where id=me and coalesce(banned,false))then return jsonb_build_object('ok',false,'error','Your account cannot post.');end if;
 if not exists(select 1 from public.site_settings where key='youtube_live'and value->>'tournament_id'=p_tournament)then return jsonb_build_object('ok',false,'error','This tournament is not connected.');end if;
 if char_length(b)<1 or char_length(b)>200 then return jsonb_build_object('ok',false,'error','Write 1 to 200 characters.');end if;
 if b ~ '[0-9][ -]?[0-9][ -]?[0-9][ -]?[0-9][ -]?[0-9][ -]?[0-9][ -]?[0-9][ -]?[0-9]'or b ~* '(https?://|www\.|t\.me/|wa\.me/|[[:alnum:]._%+-]+@[[:alnum:].-]+\.[[:alpha:]]{2,})'then return jsonb_build_object('ok',false,'error','No phone numbers, IDs, email addresses or links in comments.');end if;
 -- Serialize each user's rate check across concurrent requests.
 perform pg_advisory_xact_lock(hashtextextended('live-post:'||me::text,0));
 if exists(select 1 from public.live_tournament_posts where user_id=me and created_at>clock_timestamp()-interval '5 seconds')then return jsonb_build_object('ok',false,'error','Slow down a little.');end if;
 if(select count(*)from public.live_tournament_posts where user_id=me and created_at>clock_timestamp()-interval '1 hour')>=30 then return jsonb_build_object('ok',false,'error','Hourly limit reached.');end if;
 insert into public.live_tournament_posts(tournament_id,user_id,body)values(p_tournament,me,b);return jsonb_build_object('ok',true);
end $$;
create or replace function public.xr_live_thread_report(p_post bigint,p_reason text default null)
returns jsonb language plpgsql security definer set search_path=pg_catalog as $$
declare me uuid:=auth.uid();
begin
 if me is null then return jsonb_build_object('ok',false,'error','Log in first.');end if;
 if not exists(select 1 from public.live_tournament_posts where id=p_post)then return jsonb_build_object('ok',false,'error','Comment not found.');end if;
 perform pg_advisory_xact_lock(p_post);
 insert into public.live_tournament_reports(post_id,reporter,reason)values(p_post,me,left(p_reason,120))on conflict do nothing;
 if(select count(*)from public.live_tournament_reports where post_id=p_post)>=3 then update public.live_tournament_posts set hidden=true where id=p_post;end if;
 return jsonb_build_object('ok',true);
end $$;
revoke all on function public.xr_live_thread_list(text,int) from public;
revoke all on function public.xr_live_thread_post(text,text) from public,anon;
revoke all on function public.xr_live_thread_report(bigint,text) from public,anon;
grant execute on function public.xr_live_thread_list(text,int) to anon,authenticated;
grant execute on function public.xr_live_thread_post(text,text),public.xr_live_thread_report(bigint,text) to authenticated;
