begin;
drop trigger if exists xr_weekly_news_publish_guard on public.news;
drop trigger if exists xr_weekly_news_review_guard on public.approvals;
drop function if exists public.xr_weekly_news_publish_guard(),public.xr_weekly_news_review_guard(),public.xr_weekly_news_request_review(uuid,jsonb),public.xr_weekly_news_events();
commit;
-- Redeploy the saved worker.before.ts together with rollback. Do not run after protected drafts exist without reviewing their handling.
