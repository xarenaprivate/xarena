create or replace view public.players_directory as
  select p.username, p.ign, p.avatar_url, coalesce(p.verified,false) as verified, p.role,
         coalesce(g.xp,0) as xp, (select level from level_from_xp(coalesce(g.xp,0))) as level, p.id
  from profiles p left join player_game_stats g on g.user_id = p.id
  where p.username is not null and not coalesce(p.banned,false);
create or replace function public.player_profile_v2(p_username text) returns jsonb
language plpgsql stable security definer set search_path to 'public' as $f$
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
    'highlights', coalesce((select jsonb_agg(jsonb_build_object('id',id,'kind',kind,'media_path',media_path,'caption',caption,'created_at',created_at) order by created_at desc)
        from (select * from feed_posts where user_id = v_p.id and status='approved' and kind in ('clip','video','photo') order by created_at desc limit 12) t), '[]'::jsonb));
end $f$;
notify pgrst, 'reload schema';
