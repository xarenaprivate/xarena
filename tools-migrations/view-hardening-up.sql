-- 1) mask UID/device in public player_cards (columns kept for compatibility)
create or replace view public.player_cards as
 select username, ign, null::text as ff_uid, role, null::text as device, ff_rank, kd, instagram, verified, matches, wins, created_at,
  (select r.squad from registrations r where r.user_id=p.id order by r.created_at desc limit 1) as squad,
  (select string_agg(a.title,' · ') from awards a where a.user_id=p.id) as titles,
  (select bool_or(a.kind='champion') from awards a where a.user_id=p.id) as champion
 from profiles p where username is not null;
-- 2) read-only: remove write privileges on public views for anon/authenticated
revoke insert,update,delete,truncate,references,trigger on
 public.player_cards, public.public_players_search, public.profiles_public, public.dm_player_search, public.matches_public,
 public.quizzes_public, public.public_squads, public.squads_public, public.sponsors_wall, public.chat_avatars,
 public.players_directory, public.player_profiles_public, public.v_leaderboard, public.v_task_scores,
 public.leaderboard_points, public.player_task_rewards, public.daily_security_summary from anon, authenticated;
notify pgrst, 'reload schema';
