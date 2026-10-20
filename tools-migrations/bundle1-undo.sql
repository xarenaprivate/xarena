drop function if exists public.head_to_head(text,text); drop function if exists public.squad_profile(uuid); drop function if exists public.points_table(text);
drop function if exists public.feedback_submit(text,text);
drop table if exists public.feedback; drop table if exists public.notices; drop table if exists public.spotlights;
delete from public.site_settings where key='flags';
