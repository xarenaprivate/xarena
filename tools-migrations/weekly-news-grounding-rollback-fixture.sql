begin;
do $test$
declare tid text:='news-fixture-'||gen_random_uuid()::text; n uuid; a uuid; n2 uuid; a2 uuid; legacy uuid; legacy_a uuid; snapshot jsonb; st text; pub boolean; d text;
begin
 insert into tournaments(id,name,status,start_at,date_text)values(tid,'Rollback fixture','upcoming',now()+interval '1 hour','Never public fixture');
 snapshot:=xr_weekly_news_events();
 if not exists(select 1 from jsonb_array_elements(snapshot)e where e->>'id'=tid)then raise exception 'Future event absent';end if;
 insert into news(title,body,published)values('Fixture review','Exact words',false)returning id into n;
 perform xr_weekly_news_request_review(n,snapshot);
 select id into a from approvals where details->>'news_id'=n::text;
 update approvals set status='approved',decided_at=now()where id=a;
 select published into pub from news where id=n;
 if not pub then raise exception 'Unchanged approved draft did not publish';end if;
 insert into news(title,body,published)values('Fixture stale','Unchanged stale words',false)returning id into n2;
 perform xr_weekly_news_request_review(n2,snapshot);
 select id into a2 from approvals where details->>'news_id'=n2::text;
 insert into matches(tournament_id,title,status,starts_at)values(tid,'Fixture cancelled','cancelled',now()+interval '1 hour');
 if exists(select 1 from jsonb_array_elements(xr_weekly_news_events())e where e->>'id'=tid)then raise exception 'All-cancelled event was not excluded';end if;
 update approvals set status='approved',decided_at=now()where id=a2;
 select status into st from approvals where id=a2;
 select published,body into pub,d from news where id=n2;
 if st<>'pending' or pub or d<>'Unchanged stale words' then raise exception 'Stale review gate failed';end if;
 begin
 update news set published=true where id=n2;
 raise exception 'TEST direct publication was not blocked';
 exception when others then
 if sqlerrm='TEST direct publication was not blocked' then raise;end if;
 end;
 -- Unmarked historical-shaped approvals retain existing behavior.
 insert into news(title,body,published)values('Legacy fixture','Legacy exact words',false)returning id into legacy;
 insert into approvals(agent_id,action,details)values('content-agent','publish_news',jsonb_build_object('news_id',legacy))returning id into legacy_a;
 update approvals set status='approved'where id=legacy_a;
 select published into pub from news where id=legacy;
 if not pub then raise exception 'Legacy approval behavior changed';end if;
 update tournaments set start_at=now()-interval '1 day'where id=tid;
 delete from matches where tournament_id=tid;
 if exists(select 1 from jsonb_array_elements(xr_weekly_news_events())e where e->>'id'=tid)then raise exception 'Past event was not excluded';end if;
 update tournaments set start_at=null where id=tid;
 if exists(select 1 from jsonb_array_elements(xr_weekly_news_events())e where e->>'id'=tid)then raise exception 'Undated event was not excluded';end if;
end$test$;
select 'future/past/undated/all-cancelled, unchanged approval, stale return-to-review, direct publish rejection, legacy behavior passed' as fixture_result;

rollback;
