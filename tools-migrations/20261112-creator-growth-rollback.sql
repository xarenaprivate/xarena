BEGIN;
UPDATE public.xr_creator_settings SET enabled=false WHERE singleton;
DROP TRIGGER IF EXISTS xr_creator_verified ON public.creators;
CREATE OR REPLACE FUNCTION public.admin_creator_review(p_id bigint, p_approve boolean)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare a record;
begin
  if auth.uid() is null or not is_admin() then return jsonb_build_object('ok',false,'error','Admin only.'); end if;
  select * into a from creator_applications where id=p_id and status='pending';
  if a is null then return jsonb_build_object('ok',false,'error','Not found or already reviewed.'); end if;
  update creator_applications set status=case when p_approve then 'approved' else 'rejected' end, reviewed_at=now() where id=p_id;
  if p_approve then
    insert into creators(user_id,channel_url,self_followers,tier,approved_by) values (a.user_id,a.channel_url,a.self_followers,creator_tier(a.self_followers),auth.uid())
    on conflict (user_id) do update set active=true, channel_url=excluded.channel_url, self_followers=excluded.self_followers, tier=excluded.tier, approved_by=excluded.approved_by, approved_at=now();
  end if;
  return jsonb_build_object('ok',true,'approved',p_approve);
end $function$
;
DROP FUNCTION IF EXISTS public.xr_creator_admin();
DROP FUNCTION IF EXISTS public.xr_creator_settings_admin(jsonb);
DROP FUNCTION IF EXISTS public.xr_creator_city_admin(text,integer);
DROP FUNCTION IF EXISTS public.xr_creator_apply(text,text,text,integer,boolean,boolean);
DROP FUNCTION IF EXISTS public.xr_creator_verified_guard();
DROP FUNCTION IF EXISTS public.xr_creator_review(bigint,text);
DROP FUNCTION IF EXISTS public.xr_creator_admin_queue();
DROP FUNCTION IF EXISTS public.xr_creator_my_status();
DROP FUNCTION IF EXISTS public.xr_creator_sharing(boolean,boolean);
DROP FUNCTION IF EXISTS public.xr_creator_city_status(text);
DROP FUNCTION IF EXISTS public.xr_creator_activity_list(integer);
DROP FUNCTION IF EXISTS public.xr_creator_friends_applied();
DROP FUNCTION IF EXISTS public.xr_creator_tournament_seats(uuid);
DROP FUNCTION IF EXISTS public.xr_creator_city_leaderboard(text);
DROP FUNCTION IF EXISTS public.xr_creator_dashboard();
NOTIFY pgrst,'reload schema';
COMMIT;