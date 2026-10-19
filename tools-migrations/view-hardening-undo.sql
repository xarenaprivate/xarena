create or replace view public.player_cards as
 select username, ign, ff_uid, role, device, ff_rank, kd, instagram, verified, matches, wins, created_at,
  (select r.squad from registrations r where r.user_id=p.id order by r.created_at desc limit 1) as squad,
  (select string_agg(a.title,' · ') from awards a where a.user_id=p.id) as titles,
  (select bool_or(a.kind='champion') from awards a where a.user_id=p.id) as champion
 from profiles p where username is not null;
-- grants intentionally not restored (anon write on views was unintended)
