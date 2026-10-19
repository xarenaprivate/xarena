-- Undo: remove the player profiles public view entirely.
revoke select on public.player_profiles_public from anon, authenticated;
drop view if exists public.player_profiles_public;
