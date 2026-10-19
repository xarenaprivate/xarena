update public.profiles set avatar_url = null where avatar_id is not null;
drop trigger if exists profiles_avatar_guard on public.profiles;
drop function if exists public.trg_profiles_avatar_guard();
drop function if exists public.my_avatars(); drop function if exists public.unequip_avatar(); drop function if exists public.equip_avatar(text); drop function if exists public.buy_avatar(text);
alter table public.profiles drop column if exists avatar_id;
drop table if exists public.avatar_owners, public.avatar_catalog;
notify pgrst, 'reload schema';
