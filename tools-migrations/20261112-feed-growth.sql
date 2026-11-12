BEGIN;
CREATE TABLE public.xr_feed_growth_settings(singleton boolean PRIMARY KEY DEFAULT true CHECK(singleton),enabled boolean NOT NULL DEFAULT false,trending_threshold integer NOT NULL DEFAULT 100 CHECK(trending_threshold BETWEEN 1 AND 100000),next_pick_at timestamptz);
INSERT INTO public.xr_feed_growth_settings(singleton)VALUES(true);
CREATE TABLE public.xr_feed_highlight_picks(day date PRIMARY KEY,post_id bigint NOT NULL REFERENCES public.feed_posts(id)ON DELETE CASCADE,chosen_by uuid NOT NULL REFERENCES public.profiles(id),chosen_at timestamptz NOT NULL DEFAULT clock_timestamp());
CREATE TABLE public.xr_feed_activity_state(user_id uuid PRIMARY KEY REFERENCES public.profiles(id)ON DELETE CASCADE,seen_at timestamptz NOT NULL DEFAULT clock_timestamp());
CREATE TABLE public.xr_feed_author_sharing(user_id uuid PRIMARY KEY REFERENCES public.profiles(id)ON DELETE CASCADE,city_visible boolean NOT NULL DEFAULT false);
CREATE INDEX IF NOT EXISTS xr_feed_posts_status_time ON public.feed_posts(status,created_at DESC,id DESC);
CREATE INDEX IF NOT EXISTS xr_feed_comments_post_time ON public.feed_comments(post_id,created_at DESC,id DESC);
CREATE INDEX IF NOT EXISTS xr_feed_likes_post_time ON public.feed_likes(post_id,created_at DESC);

ALTER TABLE public.xr_feed_growth_settings ENABLE ROW LEVEL SECURITY;REVOKE ALL ON public.xr_feed_growth_settings FROM PUBLIC,anon,authenticated;
ALTER TABLE public.xr_feed_highlight_picks ENABLE ROW LEVEL SECURITY;REVOKE ALL ON public.xr_feed_highlight_picks FROM PUBLIC,anon,authenticated;
ALTER TABLE public.xr_feed_activity_state ENABLE ROW LEVEL SECURITY;REVOKE ALL ON public.xr_feed_activity_state FROM PUBLIC,anon,authenticated;
ALTER TABLE public.xr_feed_author_sharing ENABLE ROW LEVEL SECURITY;REVOKE ALL ON public.xr_feed_author_sharing FROM PUBLIC,anon,authenticated;
CREATE OR REPLACE FUNCTION public.xr_feed_growth_admin(p_patch jsonb DEFAULT '{}'::jsonb)RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path TO pg_catalog,public AS $fn$
DECLARE cfg public.xr_feed_growth_settings;BEGIN
 IF auth.uid()IS NULL OR NOT public.is_admin()THEN RAISE EXCEPTION 'Admin only.'USING ERRCODE='42501';END IF;
 IF jsonb_typeof(p_patch)IS DISTINCT FROM 'object'OR EXISTS(SELECT 1 FROM jsonb_object_keys(p_patch)k WHERE k NOT IN('enabled','trending_threshold','next_pick_at'))THEN RETURN jsonb_build_object('ok',false,'error','Invalid feed setting.');END IF;
 SELECT * INTO cfg FROM public.xr_feed_growth_settings WHERE singleton FOR UPDATE;SELECT * INTO cfg FROM jsonb_populate_record(cfg,p_patch);
 UPDATE public.xr_feed_growth_settings SET enabled=cfg.enabled,trending_threshold=cfg.trending_threshold,next_pick_at=cfg.next_pick_at WHERE singleton;
 RETURN jsonb_build_object('ok',true,'settings',to_jsonb(cfg)-'singleton');EXCEPTION WHEN check_violation OR not_null_violation OR invalid_text_representation OR invalid_datetime_format THEN RETURN jsonb_build_object('ok',false,'error','Invalid feed settings.');END
$fn$;
REVOKE ALL ON FUNCTION public.xr_feed_growth_admin(jsonb)FROM PUBLIC,anon,authenticated;GRANT EXECUTE ON FUNCTION public.xr_feed_growth_admin(jsonb)TO authenticated;
CREATE OR REPLACE FUNCTION public.xr_feed_highlight_pick(p_post bigint)RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path TO pg_catalog,public AS $fn$
DECLARE d date:=(statement_timestamp()AT TIME ZONE 'Asia/Kolkata')::date;BEGIN
 IF auth.uid()IS NULL OR NOT public.is_admin()THEN RAISE EXCEPTION 'Admin only.'USING ERRCODE='42501';END IF;
 IF NOT EXISTS(SELECT 1 FROM public.feed_posts f JOIN public.profiles pr ON pr.id=f.user_id WHERE f.id=p_post AND f.status='approved'AND NOT coalesce(pr.banned,false)AND(f.created_at AT TIME ZONE 'Asia/Kolkata')::date=d)THEN RETURN jsonb_build_object('ok',false,'error','Choose an approved highlight posted today.');END IF;
 INSERT INTO public.xr_feed_highlight_picks(day,post_id,chosen_by)VALUES(d,p_post,auth.uid())ON CONFLICT(day)DO UPDATE SET post_id=excluded.post_id,chosen_by=excluded.chosen_by,chosen_at=clock_timestamp();
 RETURN jsonb_build_object('ok',true,'post_id',p_post,'reward_available',false);END
$fn$;
REVOKE ALL ON FUNCTION public.xr_feed_highlight_pick(bigint)FROM PUBLIC,anon,authenticated;GRANT EXECUTE ON FUNCTION public.xr_feed_highlight_pick(bigint)TO authenticated;
CREATE OR REPLACE FUNCTION public.xr_feed_activity_ack()RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path TO pg_catalog,public AS $fn$
DECLARE u uuid:=public.xr_sf_actor();stamp timestamptz:=clock_timestamp();BEGIN PERFORM public.xr_ops_assert('feed');INSERT INTO public.xr_feed_activity_state(user_id,seen_at)VALUES(u,stamp)ON CONFLICT(user_id)DO UPDATE SET seen_at=excluded.seen_at;RETURN jsonb_build_object('ok',true,'seen_at',stamp);END
$fn$;
REVOKE ALL ON FUNCTION public.xr_feed_activity_ack()FROM PUBLIC,anon,authenticated;GRANT EXECUTE ON FUNCTION public.xr_feed_activity_ack()TO authenticated;
CREATE OR REPLACE FUNCTION public.xr_feed_author_sharing_set(p_city_visible boolean)RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path TO pg_catalog,public AS $fn$
DECLARE u uuid:=public.xr_sf_actor();BEGIN INSERT INTO public.xr_feed_author_sharing(user_id,city_visible)VALUES(u,coalesce(p_city_visible,false))ON CONFLICT(user_id)DO UPDATE SET city_visible=excluded.city_visible;RETURN jsonb_build_object('ok',true,'city_visible',coalesce(p_city_visible,false));END
$fn$;
REVOKE ALL ON FUNCTION public.xr_feed_author_sharing_set(boolean)FROM PUBLIC,anon,authenticated;GRANT EXECUTE ON FUNCTION public.xr_feed_author_sharing_set(boolean)TO authenticated;
CREATE OR REPLACE FUNCTION public.xr_feed_list(p_mode text DEFAULT 'latest',p_cursor jsonb DEFAULT NULL,p_limit integer DEFAULT 10)RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path TO pg_catalog,public AS $fn$
DECLARE u uuid:=auth.uid();cfg public.xr_feed_growth_settings;items jsonb;take integer:=greatest(1,least(coalesce(p_limit,10),10));lastrow jsonb;BEGIN
 PERFORM public.xr_ops_assert('feed');SELECT * INTO cfg FROM public.xr_feed_growth_settings WHERE singleton;
 IF u IS NOT NULL THEN PERFORM public.xr_sf_actor();END IF;
 IF p_mode NOT IN('latest','saved','trending')OR p_mode IS NULL THEN RETURN jsonb_build_object('ok',false,'error','Unknown feed mode.');END IF;
 IF p_mode='saved'AND u IS NULL THEN RETURN jsonb_build_object('ok',false,'error','Login required.');END IF;
 IF p_cursor IS NOT NULL AND(jsonb_typeof(p_cursor)IS DISTINCT FROM 'object'OR NOT(p_cursor?'id')OR NOT(p_cursor?'at')OR NOT(p_cursor?'score')OR coalesce(p_cursor->>'mode','')<>p_mode)THEN RETURN jsonb_build_object('ok',false,'error','Invalid feed cursor.');END IF;
 WITH eligible AS(
 SELECT f.*,pr.username,pr.ign,CASE WHEN sh.city_visible THEN nullif(btrim(pr.geo_district),'')END author_city,
 (SELECT count(DISTINCT l.user_id)FROM public.feed_likes l JOIN public.profiles lp ON lp.id=l.user_id WHERE l.post_id=f.id AND l.user_id<>f.user_id AND NOT coalesce(lp.banned,false))likes,
 (SELECT count(*)FROM public.feed_comments c JOIN public.profiles cp ON cp.id=c.user_id WHERE c.post_id=f.id AND NOT coalesce(cp.banned,false))comments,
 (SELECT count(*)FROM public.feed_saves z JOIN public.profiles zp ON zp.id=z.user_id WHERE z.post_id=f.id AND NOT coalesce(zp.banned,false))save_count,
 EXISTS(SELECT 1 FROM public.feed_likes l WHERE l.post_id=f.id AND l.user_id=u)liked,
 EXISTS(SELECT 1 FROM public.feed_saves z WHERE z.post_id=f.id AND z.user_id=u)saved,
 EXISTS(SELECT 1 FROM public.follows z WHERE z.follower_id=u AND z.followee_id=f.user_id)following,
 (SELECT z.created_at FROM public.feed_saves z WHERE z.post_id=f.id AND z.user_id=u)saved_at
 FROM public.feed_posts f JOIN public.profiles pr ON pr.id=f.user_id LEFT JOIN public.xr_feed_author_sharing sh ON sh.user_id=pr.id
 WHERE f.status='approved'AND NOT coalesce(pr.banned,false)AND(u IS NULL OR NOT public.is_blocked(u,pr.id))
 ), ranked AS(SELECT e.*,CASE WHEN p_mode='trending'THEN likes ELSE 0 END score,CASE WHEN p_mode='saved'THEN saved_at ELSE created_at END sort_at FROM eligible e WHERE(p_mode<>'saved'OR saved)AND(p_mode<>'trending'OR(cfg.enabled AND likes>=cfg.trending_threshold))), page AS(
 SELECT * FROM ranked WHERE p_cursor IS NULL OR(score,sort_at,id)<(coalesce((p_cursor->>'score')::bigint,0),(p_cursor->>'at')::timestamptz,(p_cursor->>'id')::bigint)ORDER BY score DESC,sort_at DESC,id DESC LIMIT take)
 SELECT coalesce(jsonb_agg(jsonb_build_object('id',id,'kind',kind,'media_path',media_path,'caption',caption,'author',coalesce(nullif(ign,''),username,'Player'),'author_id',user_id,'author_city',author_city,'author_rank',NULL,'likes',likes,'comments',comments,'save_count',save_count,'liked',liked,'saved',saved,'following',following,'at',created_at,'trending',cfg.enabled AND likes>=cfg.trending_threshold,'likes_to_trending',CASE WHEN cfg.enabled THEN greatest(cfg.trending_threshold-likes,0)ELSE NULL END,'first_comment_reward_left',0,'cursor',jsonb_build_object('mode',p_mode,'id',id,'at',sort_at,'score',score))ORDER BY score DESC,sort_at DESC,id DESC),'[]'::jsonb)INTO items FROM page;
 lastrow:=items->-1;RETURN jsonb_build_object('ok',true,'posts',items,'next_cursor',CASE WHEN jsonb_array_length(items)=take THEN lastrow->'cursor'END,'growth_enabled',cfg.enabled,'cache_seconds',30,'count_refresh_seconds',300);
 EXCEPTION WHEN invalid_text_representation OR invalid_datetime_format OR datetime_field_overflow THEN RETURN jsonb_build_object('ok',false,'error','Invalid feed cursor.');END
$fn$;
REVOKE ALL ON FUNCTION public.xr_feed_list(text,jsonb,integer)FROM PUBLIC,anon,authenticated;GRANT EXECUTE ON FUNCTION public.xr_feed_list(text,jsonb,integer)TO authenticated,anon;
CREATE OR REPLACE FUNCTION public.xr_feed_overview()RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path TO pg_catalog,public AS $fn$
DECLARE u uuid:=auth.uid();cfg public.xr_feed_growth_settings;d date:=(statement_timestamp()AT TIME ZONE 'Asia/Kolkata')::date;day_start timestamptz;week_start timestamptz;seen timestamptz;topfan jsonb;streak jsonb;friends jsonb:='[]'::jsonb;activity jsonb:='[]'::jsonb;highlight jsonb;ranked_me bigint;streak_days integer;posted boolean;BEGIN
 PERFORM public.xr_ops_assert('feed');IF u IS NOT NULL THEN PERFORM public.xr_sf_actor();END IF;
 SELECT * INTO cfg FROM public.xr_feed_growth_settings WHERE singleton;day_start:=d::timestamp AT TIME ZONE 'Asia/Kolkata';week_start:=date_trunc('week',statement_timestamp()AT TIME ZONE 'Asia/Kolkata')AT TIME ZONE 'Asia/Kolkata';
 IF cfg.enabled THEN
 SELECT jsonb_build_object('post_id',f.id,'author',coalesce(nullif(pr.ign,''),pr.username,'Player'),'chosen_at',pick.chosen_at)INTO highlight FROM public.xr_feed_highlight_picks pick JOIN public.feed_posts f ON f.id=pick.post_id JOIN public.profiles pr ON pr.id=f.user_id WHERE pick.day=d AND f.status='approved'AND NOT coalesce(pr.banned,false)AND(u IS NULL OR NOT public.is_blocked(u,pr.id));
 WITH interactions AS(
 SELECT l.user_id,l.post_id FROM public.feed_likes l JOIN public.feed_posts f ON f.id=l.post_id JOIN public.profiles author ON author.id=f.user_id WHERE l.created_at>=week_start AND l.created_at<=statement_timestamp()AND f.status='approved'AND l.user_id<>f.user_id AND NOT coalesce(author.banned,false)AND(u IS NULL OR NOT public.is_blocked(u,f.user_id))
 UNION ALL SELECT c.user_id,c.post_id FROM public.feed_comments c JOIN public.feed_posts f ON f.id=c.post_id JOIN public.profiles author ON author.id=f.user_id WHERE c.created_at>=week_start AND c.created_at<=statement_timestamp()AND f.status='approved'AND c.user_id<>f.user_id AND NOT coalesce(author.banned,false)AND(u IS NULL OR NOT public.is_blocked(u,f.user_id))
 ),scores AS(SELECT i.user_id,count(DISTINCT i.post_id)score FROM interactions i JOIN public.profiles pr ON pr.id=i.user_id WHERE NOT coalesce(pr.banned,false)AND coalesce(pr.show_activity,false)AND(u IS NULL OR NOT public.is_blocked(u,pr.id))GROUP BY i.user_id),ranks AS(SELECT *,row_number()OVER(ORDER BY score DESC,user_id)rank FROM scores)
 SELECT jsonb_build_object('handle',coalesce(nullif(pr.ign,''),pr.username,'Player'),'my_rank',(SELECT rank FROM ranks WHERE user_id=u),'score',r.score,'score_basis','distinct_other_authors_posts_engaged_this_week')INTO topfan FROM ranks r JOIN public.profiles pr ON pr.id=r.user_id WHERE r.rank=1;
 END IF;
 IF u IS NOT NULL THEN
 SELECT seen_at INTO seen FROM public.xr_feed_activity_state WHERE user_id=u;
 WITH post_days AS(SELECT DISTINCT(created_at AT TIME ZONE 'Asia/Kolkata')::date post_day FROM public.feed_posts WHERE user_id=u AND status='approved'AND created_at>=day_start-interval '366 days'), recent AS(SELECT post_day,row_number()OVER(ORDER BY post_day DESC)n FROM post_days WHERE post_day<=d),anchor AS(SELECT max(post_day)post_day FROM recent)
 SELECT count(*)FILTER(WHERE r.post_day=a.post_day-(r.n::integer-1))::integer,coalesce(bool_or(r.post_day=d),false)INTO streak_days,posted FROM recent r CROSS JOIN anchor a WHERE a.post_day>=d-1;
 streak:=jsonb_build_object('days',coalesce(streak_days,0),'posted_today',coalesce(posted,false),'bonus_coins',NULL,'basis','approved_posts_IST','bounded_to_days',366);
 IF cfg.enabled THEN
 SELECT coalesce(jsonb_agg(z.item),'[]'::jsonb)INTO friends FROM(SELECT jsonb_build_object('id',pr.id,'username',pr.username,'ign',pr.ign,'avatar_url',pr.avatar_url)item FROM public.profiles pr WHERE pr.id<>u AND NOT coalesce(pr.banned,false)AND coalesce(pr.show_activity,false)AND coalesce((SELECT show_activity FROM public.profiles WHERE id=u),false)AND NOT public.is_blocked(u,pr.id)AND EXISTS(SELECT 1 FROM public.friends f WHERE f.user_id=u AND f.friend_id=pr.id)AND EXISTS(SELECT 1 FROM public.friends f WHERE f.user_id=pr.id AND f.friend_id=u)AND EXISTS(SELECT 1 FROM public.feed_posts f WHERE f.user_id=pr.id AND f.status='approved'AND f.created_at>=day_start AND f.created_at<day_start+interval '1 day')ORDER BY pr.id LIMIT 10)z;
 IF seen IS NOT NULL THEN
 SELECT coalesce(jsonb_agg(z.item ORDER BY z.last_event DESC),'[]'::jsonb)INTO activity FROM(
 SELECT jsonb_build_object('post_id',f.id,'new_likes',(SELECT count(*)FROM public.feed_likes l JOIN public.profiles lp ON lp.id=l.user_id WHERE l.post_id=f.id AND l.created_at>seen AND l.user_id<>u AND NOT coalesce(lp.banned,false)AND NOT public.is_blocked(u,lp.id)),
 'new_comments',(SELECT count(*)FROM public.feed_comments c JOIN public.profiles cp ON cp.id=c.user_id WHERE c.post_id=f.id AND c.created_at>seen AND c.user_id<>u AND NOT coalesce(cp.banned,false)AND NOT public.is_blocked(u,cp.id)),
 'latest_commenter',(SELECT coalesce(nullif(cp.ign,''),cp.username,'Player')FROM public.feed_comments c JOIN public.profiles cp ON cp.id=c.user_id WHERE c.post_id=f.id AND c.created_at>seen AND c.user_id<>u AND NOT coalesce(cp.banned,false)AND NOT public.is_blocked(u,cp.id)ORDER BY c.created_at DESC,c.id DESC LIMIT 1))item,
 greatest((SELECT max(l.created_at)FROM public.feed_likes l WHERE l.post_id=f.id),(SELECT max(c.created_at)FROM public.feed_comments c WHERE c.post_id=f.id))last_event
 FROM public.feed_posts f WHERE f.user_id=u AND f.status='approved'ORDER BY last_event DESC NULLS LAST LIMIT 5)z WHERE(z.item->>'new_likes')::integer>0 OR(z.item->>'new_comments')::integer>0;
 END IF;END IF;END IF;
 RETURN jsonb_build_object('ok',true,'growth_enabled',cfg.enabled,'new_today_count',(SELECT count(*)FROM public.feed_posts f JOIN public.profiles pr ON pr.id=f.user_id WHERE f.status='approved'AND f.created_at>=day_start AND f.created_at<day_start+interval '1 day'AND NOT coalesce(pr.banned,false)AND(u IS NULL OR NOT public.is_blocked(u,pr.id))),
 'trending_threshold',cfg.trending_threshold,'highlight_of_the_day',highlight,'next_pick_at',CASE WHEN cfg.enabled THEN cfg.next_pick_at END,'weekly_top_fan',topfan,'my_streak',streak,'friends_posted_today',friends,'my_post_activity',activity,'activity_since',seen,'my_city_visible',coalesce((SELECT city_visible FROM public.xr_feed_author_sharing WHERE user_id=u),false),
 'post_reward_coins',NULL,'first_comment_reward',jsonb_build_object('enabled',false,'slots_total',0,'slots_left',0,'coins',0),'battle',NULL,'notification_delivery_available',false,'cache_seconds',30,'count_refresh_seconds',300);END
$fn$;
REVOKE ALL ON FUNCTION public.xr_feed_overview()FROM PUBLIC,anon,authenticated;GRANT EXECUTE ON FUNCTION public.xr_feed_overview()TO authenticated,anon;
NOTIFY pgrst,'reload schema';
COMMIT;