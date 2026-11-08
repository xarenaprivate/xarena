begin;
create or replace function public.xr_weekly_news_events() returns jsonb language sql stable security definer set search_path=public as $f$
 select coalesce(jsonb_agg(to_jsonb(e) order by e.next_at,e.id),'[]'::jsonb) from (
 select t.id,t.name,t.status,
 case when t.start_at>=now() then t.start_at else m.next_at end as next_at
 from tournaments t
 cross join lateral(select count(*) total,count(*)filter(where lower(coalesce(status,''))not in('cancelled','canceled','postponed'))active,
 min(starts_at)filter(where starts_at>=now()and lower(coalesce(status,''))not in('cancelled','canceled','postponed','completed','finished'))next_at
 from matches where tournament_id=t.id)m
 where t.status in('upcoming','live') and (m.total=0 or m.active>0)
 and ((m.total=0 and t.start_at>=now())or(m.total>0 and m.next_at is not null))
 order by next_at,t.id limit 3)e;
$f$;
revoke all on function public.xr_weekly_news_events() from public,anon,authenticated;
grant execute on function public.xr_weekly_news_events()to service_role;
create or replace function public.xr_weekly_news_request_review(p_news uuid,p_events jsonb) returns void language plpgsql security definer set search_path=public as $f$
declare n news%rowtype;
begin
 select * into n from news where id=p_news and published=false;
 if not found then raise exception 'Unpublished draft required';end if;
 insert into approvals(agent_id,action,details)values('content-agent','publish_news',jsonb_build_object('news_id',p_news,'title',n.title,'reason','Weekly news drafted. Review the exact words and approve to publish.','weekly_guard','v1','weekly_events',p_events,'weekly_content_hash',md5(coalesce(n.title,'')||chr(10)||coalesce(n.body,''))));
end$f$;
revoke all on function public.xr_weekly_news_request_review(uuid,jsonb)from public,anon,authenticated;
grant execute on function public.xr_weekly_news_request_review(uuid,jsonb)to service_role;
-- Only newly generated weekly approvals opt in. No historical rows are changed.
create or replace function public.xr_weekly_news_review_guard()returns trigger language plpgsql security definer set search_path=public as $f$
declare n news%rowtype; reason text;
begin
 if new.action<>'publish_news' or new.details->>'weekly_guard' is distinct from 'v1' or new.status<>'approved' or old.status<>'pending' then return new;end if;
 select * into n from news where id=(new.details->>'news_id')::uuid;
 if not found then reason:='Draft is missing. Review required.';
 elsif new.details->'weekly_events' is distinct from xr_weekly_news_events() then reason:='Event facts changed since drafting. Refresh the draft and review again before publishing.';
 elsif new.details->>'weekly_content_hash' is distinct from md5(coalesce(n.title,'')||chr(10)||coalesce(n.body,''))then reason:='Draft words changed since approval was requested. Review required.';end if;
 if reason is not null then
 new.status:='pending';new.decided_at:=null;
 new.details:=new.details||jsonb_build_object('review_required',reason);
 end if;
 return new;
end$f$;
create trigger xr_weekly_news_review_guard before update of status on public.approvals for each row execute function public.xr_weekly_news_review_guard();
-- New protected drafts also cannot be published by a separate direct publish RPC.
create or replace function public.xr_weekly_news_publish_guard()returns trigger language plpgsql security definer set search_path=public as $f$
declare a approvals%rowtype;
begin
 if new.published and not coalesce(old.published,false) then
 select * into a from approvals where action='publish_news'and details->>'news_id'=new.id::text and details->>'weekly_guard'='v1'order by created_at desc limit 1;
 if found and(a.status<>'approved' or a.details->'weekly_events' is distinct from xr_weekly_news_events()or a.details->>'weekly_content_hash' is distinct from md5(coalesce(new.title,'')||chr(10)||coalesce(new.body,'')))then
 raise exception 'Weekly news needs current facts and owner review before publishing.';
 end if;
 end if;
 return new;
end$f$;
create trigger xr_weekly_news_publish_guard before update of published on public.news for each row execute function public.xr_weekly_news_publish_guard();
commit;
