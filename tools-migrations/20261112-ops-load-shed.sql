BEGIN;
CREATE TABLE public.xr_ops_settings(
 singleton boolean PRIMARY KEY DEFAULT true CHECK(singleton),
 feed_shed boolean NOT NULL DEFAULT false,clips_shed boolean NOT NULL DEFAULT false,stories_shed boolean NOT NULL DEFAULT false,
 retry_after_seconds integer NOT NULL DEFAULT 10 CHECK(retry_after_seconds BETWEEN 5 AND 300),
 revision bigint NOT NULL DEFAULT 1,updated_at timestamptz NOT NULL DEFAULT clock_timestamp()
);
INSERT INTO public.xr_ops_settings(singleton)VALUES(true);
ALTER TABLE public.xr_ops_settings ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.xr_ops_settings FROM PUBLIC,anon,authenticated;
CREATE FUNCTION public.xr_ops_status()RETURNS jsonb LANGUAGE sql STABLE SECURITY DEFINER SET search_path TO pg_catalog AS $fn$
 SELECT jsonb_build_object('ok',true,'feed_shed',feed_shed OR clips_shed,'clips_shed',clips_shed OR feed_shed,'configured_feed_shed',feed_shed,'configured_clips_shed',clips_shed,'stories_shed',stories_shed,'text_only_home',feed_shed OR clips_shed OR stories_shed,'match_center_available',true,'live_available',true,'retry_after_seconds',retry_after_seconds,'message','Server busy. Match Center and Live remain available. Try again shortly.','revision',revision,'updated_at',updated_at)FROM public.xr_ops_settings WHERE singleton
$fn$;
REVOKE ALL ON FUNCTION public.xr_ops_status()FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.xr_ops_status()TO anon,authenticated;
CREATE FUNCTION public.xr_ops_admin(p_patch jsonb DEFAULT '{}'::jsonb)RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path TO pg_catalog AS $fn$
DECLARE s public.xr_ops_settings;BEGIN
 IF auth.uid()IS NULL OR NOT public.is_admin()THEN RAISE EXCEPTION 'Admin only.' USING ERRCODE='42501';END IF;
 IF jsonb_typeof(p_patch)IS DISTINCT FROM 'object' OR EXISTS(SELECT 1 FROM jsonb_object_keys(p_patch)k WHERE k NOT IN('feed_shed','clips_shed','stories_shed','retry_after_seconds'))THEN RETURN jsonb_build_object('ok',false,'error','Invalid operations setting.');END IF;
 SELECT * INTO s FROM public.xr_ops_settings WHERE singleton FOR UPDATE;
 SELECT * INTO s FROM jsonb_populate_record(s,p_patch);
 IF p_patch<>'{}'::jsonb THEN UPDATE public.xr_ops_settings SET feed_shed=s.feed_shed,clips_shed=s.clips_shed,stories_shed=s.stories_shed,retry_after_seconds=s.retry_after_seconds,revision=revision+1,updated_at=clock_timestamp()WHERE singleton;END IF;
 RETURN public.xr_ops_status();
EXCEPTION WHEN check_violation OR not_null_violation OR invalid_text_representation THEN RETURN jsonb_build_object('ok',false,'error','Invalid flag or retry window (5-300 seconds).');END
$fn$;
REVOKE ALL ON FUNCTION public.xr_ops_admin(jsonb)FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.xr_ops_admin(jsonb)TO authenticated;
CREATE FUNCTION public.xr_ops_assert(p_feature text)RETURNS void LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path TO pg_catalog AS $fn$
DECLARE s public.xr_ops_settings;blocked boolean;BEGIN
 SELECT * INTO s FROM public.xr_ops_settings WHERE singleton;
 blocked:=CASE p_feature WHEN 'feed' THEN s.feed_shed OR s.clips_shed WHEN 'clips' THEN s.clips_shed OR s.feed_shed WHEN 'stories' THEN s.stories_shed ELSE false END;
 IF coalesce(blocked,false)THEN
 RAISE SQLSTATE 'PGRST' USING
 MESSAGE=jsonb_build_object('code','XR_BUSY','message','Server busy. Match Center and Live remain available. Try again in '||s.retry_after_seconds||' seconds.','details',jsonb_build_object('feature',p_feature,'retry_after_seconds',s.retry_after_seconds)::text,'hint','Open Match Center or Live, or retry after the indicated delay.')::text,
 DETAIL=jsonb_build_object('status',503,'headers',jsonb_build_object('Retry-After',s.retry_after_seconds::text,'Cache-Control','no-store'))::text;
 END IF;
END $fn$;
REVOKE ALL ON FUNCTION public.xr_ops_assert(text)FROM PUBLIC,anon,authenticated;
CREATE FUNCTION public.xr_ops_pre_request()RETURNS void LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path TO pg_catalog AS $fn$
DECLARE route text:=btrim(coalesce(current_setting('request.path',true),''),'/');name text;
BEGIN
 -- PostgREST paths are /table or /rpc/function, without /rest/v1 prefix.
 IF route LIKE 'rest/v1/%'THEN route:=substr(route,9);END IF;
 name:=CASE WHEN route LIKE 'rpc/%'THEN substr(route,5)ELSE route END;
 IF name IN('feed_posts','feed_likes','feed_comments','feed_saves','feed_user','feed_list','feed_toggle_like','feed_comments_list','feed_comment','feed_create','feed_toggle_save','feed_saved')THEN PERFORM set_config('response.headers',coalesce(nullif(current_setting('response.headers',true),''),'[]')::jsonb::text,true);PERFORM set_config('response.headers',((current_setting('response.headers')::jsonb)||jsonb_build_array(jsonb_build_object('X-Xarena-Ops-Guard','feed')))::text,true);PERFORM public.xr_ops_assert('feed');
 ELSIF name IN('weekly_top_clip')THEN PERFORM set_config('response.headers',(coalesce(nullif(current_setting('response.headers',true),''),'[]')::jsonb||jsonb_build_array(jsonb_build_object('X-Xarena-Ops-Guard','clips')))::text,true);PERFORM public.xr_ops_assert('clips');
 ELSIF name IN('stories','story_views','story_create','story_trays','story_list','story_mark_seen','story_viewers')THEN PERFORM set_config('response.headers',(coalesce(nullif(current_setting('response.headers',true),''),'[]')::jsonb||jsonb_build_array(jsonb_build_object('X-Xarena-Ops-Guard','stories')))::text,true);PERFORM public.xr_ops_assert('stories');END IF;
END $fn$;
REVOKE ALL ON FUNCTION public.xr_ops_pre_request()FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.xr_ops_pre_request()TO anon,authenticated,service_role;
CREATE OR REPLACE FUNCTION public.feed_user(p_name text, p_limit integer DEFAULT 60)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
begin
  PERFORM public.xr_ops_assert('feed');
  return jsonb_build_object('ok', true, 'posts', coalesce((
    select jsonb_agg(jsonb_build_object(
      'id', t.id, 'kind', t.kind, 'media_path', t.media_path, 'caption', t.caption,
      'likes', t.likes, 'comments', t.comments, 'at', t.created_at
    ) order by t.id desc)
    from (
      select f.id, f.kind, f.media_path, f.caption, f.created_at,
             (select count(*) from feed_likes l where l.post_id = f.id) as likes,
             (select count(*) from feed_comments c where c.post_id = f.id) as comments
      from feed_posts f join profiles p on p.id = f.user_id
      where f.status = 'approved' and lower(p.username) = lower(trim(p_name))
      order by f.id desc limit least(greatest(coalesce(p_limit, 60), 1), 100)
    ) t
  ), '[]'::jsonb));
end $function$
;
CREATE OR REPLACE FUNCTION public.feed_list(p_before bigint DEFAULT NULL::bigint)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare v_uid uuid := auth.uid();
begin
  PERFORM public.xr_ops_assert('feed');
  return jsonb_build_object('ok', true, 'posts', coalesce((
    select jsonb_agg(jsonb_build_object(
      'id', t.id, 'kind', t.kind, 'media_path', t.media_path, 'caption', t.caption,
      'author', t.author, 'author_id', t.user_id, 'likes', t.likes, 'liked', t.liked,
      'saved', t.saved, 'comments', t.comments, 'following', t.following, 'at', t.created_at
    ) order by t.fo, t.id desc)
    from (
      select f.id, f.kind, f.media_path, f.caption, f.user_id, f.created_at,
             coalesce(nullif(p.ign,''), p.username, 'Player') as author,
             (select count(*) from feed_likes l where l.post_id = f.id) as likes,
             (v_uid is not null and exists (select 1 from feed_likes l where l.post_id = f.id and l.user_id = v_uid)) as liked,
             (v_uid is not null and exists (select 1 from public.feed_saves s where s.post_id = f.id and s.user_id = v_uid)) as saved,
             (select count(*) from feed_comments c where c.post_id = f.id) as comments,
             case when v_uid is not null and exists (select 1 from public.follows fo where fo.follower_id = v_uid and fo.followee_id = f.user_id) then 0 else 1 end as fo,
             (v_uid is not null and exists (select 1 from public.follows fo where fo.follower_id = v_uid and fo.followee_id = f.user_id)) as following
      from public.feed_posts f join public.profiles p on p.id = f.user_id
      where f.status = 'approved' and (p_before is null or f.id < p_before)
      order by fo, f.id desc limit 20
    ) t
  ), '[]'::jsonb));
end $function$
;
CREATE OR REPLACE FUNCTION public.feed_toggle_like(p_post bigint)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare v_uid uuid := auth.uid(); v_liked boolean;
begin
  PERFORM public.xr_ops_assert('feed');
  if v_uid is null then return jsonb_build_object('ok', false, 'error', 'Login required.'); end if;
  if not exists (select 1 from feed_posts where id = p_post and status = 'approved') then
    return jsonb_build_object('ok', false, 'error', 'Post not found.'); end if;
  if exists (select 1 from feed_likes where post_id = p_post and user_id = v_uid) then
    delete from feed_likes where post_id = p_post and user_id = v_uid; v_liked := false;
  else
    insert into feed_likes (post_id, user_id) values (p_post, v_uid) on conflict do nothing; v_liked := true;
  end if;
  return jsonb_build_object('ok', true, 'liked', v_liked,
    'likes', (select count(*) from feed_likes where post_id = p_post));
end $function$
;
CREATE OR REPLACE FUNCTION public.feed_comments_list(p_post bigint)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
begin
  PERFORM public.xr_ops_assert('feed');
  return jsonb_build_object('ok', true, 'comments', coalesce((
    select jsonb_agg(jsonb_build_object('id', t.id, 'body', t.body, 'author', t.author, 'at', t.created_at) order by t.created_at)
    from (
      select c.id, c.body, c.created_at, coalesce(nullif(p.ign,''), p.username, 'Player') as author
      from feed_comments c join profiles p on p.id = c.user_id
      where c.post_id = p_post
      order by c.created_at desc limit 50
    ) t
  ), '[]'::jsonb));
end $function$
;
CREATE OR REPLACE FUNCTION public.feed_comment(p_post bigint, p_body text)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare v_uid uuid := auth.uid();
begin
  PERFORM public.xr_ops_assert('feed');
  if v_uid is null then return jsonb_build_object('ok', false, 'error', 'Login required.'); end if;
  if not exists (select 1 from feed_posts where id = p_post and status = 'approved') then
    return jsonb_build_object('ok', false, 'error', 'Post not found.'); end if;
  if char_length(trim(coalesce(p_body,''))) < 1 then
    return jsonb_build_object('ok', false, 'error', 'Write something first.'); end if;
  insert into feed_comments (post_id, user_id, body) values (p_post, v_uid, trim(p_body));
  return jsonb_build_object('ok', true, 'comments', (select count(*) from feed_comments where post_id = p_post));
end $function$
;
CREATE OR REPLACE FUNCTION public.feed_create(p_kind text, p_media_path text DEFAULT NULL::text, p_caption text DEFAULT NULL::text)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare v_uid uuid := auth.uid(); v_id bigint;
begin
  PERFORM public.xr_ops_assert('feed');
  if v_uid is null then return jsonb_build_object('ok', false, 'error', 'Login required.'); end if;
  if p_kind not in ('photo','clip','text') then return jsonb_build_object('ok', false, 'error', 'Unknown post type.'); end if;
  if p_kind = 'text' and char_length(trim(coalesce(p_caption,''))) < 3 then
    return jsonb_build_object('ok', false, 'error', 'Write at least a few words.'); end if;
  if p_kind in ('photo','clip') then
    if p_media_path is null or (
         p_media_path not like (v_uid::text || '/%')
         and p_media_path not like 'https://res.cloudinary.com/r0k6xxw1/image/upload/%'
         and p_media_path not like 'https://res.cloudinary.com/r0k6xxw1/video/upload/%'
       ) then
      return jsonb_build_object('ok', false, 'error', 'Media upload missing.'); end if;
  end if;
  -- fair-use: max 20 posts/day
  if (select count(*) from feed_posts where user_id = v_uid and created_at > now() - interval '1 day') >= 20 then
    return jsonb_build_object('ok', false, 'error', 'Daily post limit reached - back tomorrow.');
  end if;
  insert into feed_posts (user_id, kind, media_path, caption)
    values (v_uid, p_kind, case when p_kind='text' then null else p_media_path end, nullif(trim(coalesce(p_caption,'')),''))
    returning id into v_id;
  return jsonb_build_object('ok', true, 'id', v_id, 'status', 'pending');
end $function$
;
CREATE OR REPLACE FUNCTION public.feed_toggle_save(p_post bigint)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare v_uid uuid := auth.uid(); v_saved boolean;
begin
  PERFORM public.xr_ops_assert('feed');
  if v_uid is null then return jsonb_build_object('ok', false, 'error', 'Login required.'); end if;
  if not exists (select 1 from public.feed_posts where id = p_post and status = 'approved') then
    return jsonb_build_object('ok', false, 'error', 'Post not found.'); end if;
  if exists (select 1 from public.feed_saves where post_id = p_post and user_id = v_uid) then
    delete from public.feed_saves where post_id = p_post and user_id = v_uid; v_saved := false;
  else
    insert into public.feed_saves (post_id, user_id) values (p_post, v_uid) on conflict do nothing; v_saved := true;
  end if;
  return jsonb_build_object('ok', true, 'saved', v_saved);
end $function$
;
CREATE OR REPLACE FUNCTION public.feed_saved()
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare v_uid uuid := auth.uid();
begin
  PERFORM public.xr_ops_assert('feed');
  if v_uid is null then return jsonb_build_object('ok', false, 'error', 'Login required.'); end if;
  return jsonb_build_object('ok', true, 'posts', coalesce((
    select jsonb_agg(jsonb_build_object(
      'id', t.id, 'kind', t.kind, 'media_path', t.media_path, 'caption', t.caption,
      'author', t.author, 'author_id', t.user_id, 'likes', t.likes, 'liked', t.liked,
      'saved', true, 'comments', t.comments, 'at', t.created_at
    ) order by t.saved_at desc)
    from (
      select f.id, f.kind, f.media_path, f.caption, f.user_id, f.created_at, s.created_at as saved_at,
             coalesce(nullif(p.ign,''), p.username, 'Player') as author,
             (select count(*) from feed_likes l where l.post_id = f.id) as likes,
             (exists (select 1 from feed_likes l where l.post_id = f.id and l.user_id = v_uid)) as liked,
             (select count(*) from feed_comments c where c.post_id = f.id) as comments
      from public.feed_saves s
      join public.feed_posts f on f.id = s.post_id and f.status = 'approved'
      join public.profiles p on p.id = f.user_id
      where s.user_id = v_uid
      order by s.created_at desc limit 60
    ) t
  ), '[]'::jsonb));
end $function$
;
CREATE OR REPLACE FUNCTION public.story_create(p_kind text, p_media_path text, p_caption text DEFAULT NULL::text)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare v_uid uuid := auth.uid(); v_id bigint;
begin
  PERFORM public.xr_ops_assert('stories');
  if v_uid is null then return jsonb_build_object('ok', false, 'error', 'Login required.'); end if;
  if p_kind not in ('photo','clip') then return jsonb_build_object('ok', false, 'error', 'Photo or clip only.'); end if;
  if p_media_path is null or length(trim(p_media_path)) < 5 then return jsonb_build_object('ok', false, 'error', 'Media upload missing.'); end if;
  insert into public.stories (user_id, kind, media_path, caption)
  values (v_uid, p_kind, left(trim(p_media_path), 500), nullif(left(trim(coalesce(p_caption,'')), 220), ''))
  returning id into v_id;
  return jsonb_build_object('ok', true, 'id', v_id, 'status', 'pending');
end $function$
;
CREATE OR REPLACE FUNCTION public.story_trays()
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare v_uid uuid := auth.uid();
begin
  PERFORM public.xr_ops_assert('stories');
  return jsonb_build_object('ok', true, 'trays', coalesce((
    select jsonb_agg(jsonb_build_object(
      'author_id', t.user_id, 'author', t.author, 'count', t.cnt, 'unseen', t.unseen,
      'mine', t.mine, 'latest', t.latest
    ) order by t.mine desc, t.fo, t.latest desc)
    from (
      select s.user_id,
             coalesce(nullif(p.ign,''), p.username, 'Player') as author,
             count(*) as cnt,
             count(*) filter (where v_uid is null or not exists (select 1 from public.story_views v where v.story_id = s.id and v.viewer_id = v_uid)) as unseen,
             (v_uid is not null and s.user_id = v_uid) as mine,
             max(s.created_at) as latest,
             case when v_uid is not null and exists (select 1 from public.follows fo where fo.follower_id = v_uid and fo.followee_id = s.user_id) then 0 else 1 end as fo
      from public.stories s join public.profiles p on p.id = s.user_id
      where s.status = 'approved' and s.expires_at > now()
      group by s.user_id, p.ign, p.username
    ) t
  ), '[]'::jsonb));
end $function$
;
CREATE OR REPLACE FUNCTION public.story_list(p_author uuid)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare v_uid uuid := auth.uid();
begin
  PERFORM public.xr_ops_assert('stories');
  return jsonb_build_object('ok', true, 'author', (
    select coalesce(nullif(p.ign,''), p.username, 'Player') from public.profiles p where p.id = p_author
  ), 'stories', coalesce((
    select jsonb_agg(jsonb_build_object(
      'id', s.id, 'kind', s.kind, 'media_path', s.media_path, 'caption', s.caption, 'at', s.created_at,
      'status', s.status,
      'seen', (v_uid is not null and exists (select 1 from public.story_views v where v.story_id = s.id and v.viewer_id = v_uid)),
      'views', case when v_uid = s.user_id then (select count(*) from public.story_views v where v.story_id = s.id) else null end
    ) order by s.created_at)
    from public.stories s
    where s.user_id = p_author and s.expires_at > now()
      and (s.status = 'approved' or (v_uid is not null and s.user_id = v_uid and s.status in ('pending','held')))
  ), '[]'::jsonb));
end $function$
;
CREATE OR REPLACE FUNCTION public.story_mark_seen(p_story bigint)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare v_uid uuid := auth.uid();
begin
  PERFORM public.xr_ops_assert('stories');
  if v_uid is null then return jsonb_build_object('ok', false, 'error', 'Login required.'); end if;
  if exists (select 1 from public.stories where id = p_story and status = 'approved' and expires_at > now() and user_id <> v_uid) then
    insert into public.story_views (story_id, viewer_id) values (p_story, v_uid) on conflict do nothing;
  end if;
  return jsonb_build_object('ok', true);
end $function$
;
CREATE OR REPLACE FUNCTION public.story_viewers(p_story bigint)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare v_uid uuid := auth.uid();
begin
  PERFORM public.xr_ops_assert('stories');
  if v_uid is null then return jsonb_build_object('ok', false, 'error', 'Login required.'); end if;
  if not exists (select 1 from public.stories where id = p_story and user_id = v_uid) then
    return jsonb_build_object('ok', false, 'error', 'Only the story owner can see viewers.'); end if;
  return jsonb_build_object('ok', true, 'viewers', coalesce((
    select jsonb_agg(jsonb_build_object('author', coalesce(nullif(p.ign,''), p.username, 'Player'), 'at', v.created_at) order by v.created_at desc)
    from public.story_views v join public.profiles p on p.id = v.viewer_id
    where v.story_id = p_story
  ), '[]'::jsonb));
end $function$
;
CREATE OR REPLACE FUNCTION public.weekly_top_clip()
 RETURNS jsonb
 LANGUAGE plpgsql
 STABLE SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare r record;
begin
  PERFORM public.xr_ops_assert('clips');
  select p.id,p.kind,p.media_path,p.caption,p.created_at,coalesce(nullif(pr.ign,''),pr.username) name,pr.username,(select count(*) from feed_likes l where l.post_id=p.id) likes
    into r from feed_posts p join profiles pr on pr.id=p.user_id
    where p.status='approved' and p.kind in ('clip','video') and p.created_at > now() - interval '7 days'
    order by (select count(*) from feed_likes l where l.post_id=p.id) desc, p.created_at desc limit 1;
  if r.id is null then return jsonb_build_object('ok',true,'clip',null); end if;
  return jsonb_build_object('ok',true,'clip',jsonb_build_object('id',r.id,'kind',r.kind,'media_path',r.media_path,'caption',r.caption,'name',r.name,'username',r.username,'likes',r.likes,'created_at',r.created_at));
end $function$
;
CREATE OR REPLACE FUNCTION public.player_profile_v2(p_username text)
 RETURNS jsonb
 LANGUAGE plpgsql
 STABLE SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare v_p record; v_base jsonb; v_online jsonb := null;
begin
  v_base := player_profile_nova(p_username);
  if not coalesce((v_base->>'ok')::boolean,false) then return v_base; end if;
  select id, kd, ff_rank, show_activity, last_seen_at, created_at into v_p from profiles where lower(username)=lower(trim(p_username));
  if coalesce(v_p.show_activity,false) and v_p.last_seen_at is not null then
    v_online := jsonb_build_object('active', v_p.last_seen_at > now() - interval '2 minutes', 'last_seen', v_p.last_seen_at);
  end if;
  return v_base || jsonb_build_object(
    'id', v_p.id, 'kd', v_p.kd, 'ff_rank', v_p.ff_rank, 'joined', v_p.created_at, 'presence', v_online,
    'highlights', CASE WHEN (public.xr_ops_status()->>'clips_shed')::boolean THEN '[]'::jsonb ELSE coalesce((select jsonb_agg(jsonb_build_object('id',id,'kind',kind,'media_path',media_path,'caption',caption,'created_at',created_at) order by created_at desc)
        from (select * from feed_posts where user_id = v_p.id and status='approved' and kind in ('clip','video','photo') order by created_at desc limit 12) t), '[]'::jsonb) END, 'highlights_temporarily_unavailable',(public.xr_ops_status()->>'clips_shed')::boolean);
end $function$
;
CREATE OR REPLACE FUNCTION public.creator_profile(p_id uuid)
 RETURNS jsonb
 LANGUAGE sql
 STABLE SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
  select jsonb_build_object('name', coalesce(c.ign, c.username), 'tier', c.tier, 'avatar_url', c.avatar_url,
    'channel_url', c.channel_url, 'bio', (select bio from creators where user_id=c.user_id),
    'joined', (select count(*) from referrals r where r.referrer_id = c.user_id),
    'posts', CASE WHEN (public.xr_ops_status()->>'feed_shed')::boolean OR (public.xr_ops_status()->>'clips_shed')::boolean THEN '[]'::jsonb ELSE (select coalesce(jsonb_agg(jsonb_build_object('caption', f.caption, 'at', f.created_at) order by f.created_at desc), '[]'::jsonb)
      from (select caption, created_at from feed_posts where user_id=c.user_id and status='approved' order by created_at desc limit 10) f) END, 'posts_temporarily_unavailable',(public.xr_ops_status()->>'feed_shed')::boolean OR (public.xr_ops_status()->>'clips_shed')::boolean)
  from creators_public c where c.user_id = p_id
$function$
;
CREATE FUNCTION public.xr_ops_read_allowed(p_feature text)RETURNS boolean LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path TO pg_catalog AS $fn$
BEGIN PERFORM public.xr_ops_assert(p_feature);RETURN true;END $fn$;
REVOKE ALL ON FUNCTION public.xr_ops_read_allowed(text)FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.xr_ops_read_allowed(text)TO anon,authenticated;
CREATE POLICY xr_ops_shed_read ON public.feed_posts AS RESTRICTIVE FOR SELECT TO anon,authenticated USING((SELECT public.xr_ops_read_allowed('feed')));
CREATE POLICY xr_ops_shed_read ON public.feed_likes AS RESTRICTIVE FOR SELECT TO anon,authenticated USING((SELECT public.xr_ops_read_allowed('feed')));
CREATE POLICY xr_ops_shed_read ON public.feed_comments AS RESTRICTIVE FOR SELECT TO anon,authenticated USING((SELECT public.xr_ops_read_allowed('feed')));
CREATE POLICY xr_ops_shed_read ON public.feed_saves AS RESTRICTIVE FOR SELECT TO anon,authenticated USING((SELECT public.xr_ops_read_allowed('feed')));
CREATE POLICY xr_ops_shed_read ON public.stories AS RESTRICTIVE FOR SELECT TO anon,authenticated USING((SELECT public.xr_ops_read_allowed('stories')));
CREATE POLICY xr_ops_shed_read ON public.story_views AS RESTRICTIVE FOR SELECT TO anon,authenticated USING((SELECT public.xr_ops_read_allowed('stories')));
ALTER ROLE authenticator SET pgrst.db_pre_request='public.xr_ops_pre_request';
NOTIFY pgrst,'reload config';
NOTIFY pgrst,'reload schema';
COMMIT;
