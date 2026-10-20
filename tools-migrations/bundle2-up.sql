-- Creator board: ranked by players who joined with the creator's invite code (in-app count only; no rewards, no views/clicks).
create or replace function public.creator_board() returns jsonb
language sql stable security definer set search_path to 'public' as $$
  select coalesce(jsonb_agg(x order by (x->>'joined')::int desc, x->>'name'), '[]'::jsonb) from (
    select jsonb_build_object('name', coalesce(c.ign, c.username), 'tier', c.tier, 'verified', c.verified_creator,
      'avatar_url', c.avatar_url, 'channel_url', c.channel_url,
      'joined', (select count(*) from referrals r where r.referrer_id = c.user_id)) x
    from creators_public c) s
$$;
grant execute on function public.creator_board() to anon, authenticated;
notify pgrst, 'reload schema';
