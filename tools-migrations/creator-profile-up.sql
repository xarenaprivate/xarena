alter table public.creators add column if not exists bio text;
create or replace function public.creator_profile(p_id uuid) returns jsonb
language sql stable security definer set search_path to 'public' as $$
  select jsonb_build_object('name', coalesce(c.ign, c.username), 'tier', c.tier, 'avatar_url', c.avatar_url,
    'channel_url', c.channel_url, 'bio', (select bio from creators where user_id=c.user_id),
    'joined', (select count(*) from referrals r where r.referrer_id = c.user_id),
    'posts', (select coalesce(jsonb_agg(jsonb_build_object('caption', f.caption, 'at', f.created_at) order by f.created_at desc), '[]'::jsonb)
      from (select caption, created_at from feed_posts where user_id=c.user_id and status='approved' order by created_at desc limit 10) f))
  from creators_public c where c.user_id = p_id
$$;
grant execute on function public.creator_profile(uuid) to anon, authenticated;
create or replace function public.creator_set_bio(p_bio text) returns jsonb
language plpgsql security definer set search_path to 'public' as $$
begin
  if auth.uid() is null then return jsonb_build_object('ok',false,'error','Login required'); end if;
  update creators set bio = left(coalesce(p_bio,''),300) where user_id = auth.uid() and active;
  if not found then return jsonb_build_object('ok',false,'error','Not a verified creator'); end if;
  return jsonb_build_object('ok',true);
end $$;
grant execute on function public.creator_set_bio(text) to authenticated;
-- creator_board gains user_id so the board can link to profiles (additive key)
create or replace function public.creator_board() returns jsonb
language sql stable security definer set search_path to 'public' as $$
  select coalesce(jsonb_agg(x order by (x->>'joined')::int desc, x->>'name'), '[]'::jsonb) from (
    select jsonb_build_object('user_id', c.user_id, 'name', coalesce(c.ign, c.username), 'tier', c.tier, 'verified', c.verified_creator,
      'avatar_url', c.avatar_url, 'channel_url', c.channel_url,
      'joined', (select count(*) from referrals r where r.referrer_id = c.user_id)) x
    from creators_public c) s
$$;
