drop function if exists public.player_profile_v2(text);
drop view if exists public.players_directory;
notify pgrst, 'reload schema';
