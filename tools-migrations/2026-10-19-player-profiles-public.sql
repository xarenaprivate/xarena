-- Player profiles public view: powers the Players list + full player profile screens.
-- Additive only. Exposes safe public columns from profiles + XP from player_game_stats.
-- last_seen_at is only exposed when the player has show_activity on. Phone/email/PII never exposed.
-- Banned players are hidden. Mirrors the existing profiles_public security-definer view pattern.

create or replace view public.player_profiles_public as
select p.id,
       p.username,
       p.ign,
       p.verified,
       p.role,
       p.kd,
       p.matches,
       p.wins,
       p.avatar_url,
       p.created_at,
       case when coalesce(p.show_activity, true) then p.last_seen_at end as last_seen_at,
       coalesce(g.xp, 0) as xp
from public.profiles p
left join public.player_game_stats g on g.user_id = p.id
where p.username is not null
  and coalesce(p.banned, false) = false;

grant select on public.player_profiles_public to anon, authenticated;
