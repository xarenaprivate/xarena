-- Admins could read matches but had NO insert/update/delete policy, so Admin > Match Results "Add match" always failed. Additive fix.
drop policy if exists "matches_admin_write" on public.matches;
create policy "matches_admin_write" on public.matches for all to authenticated using ((select is_admin())) with check ((select is_admin()));
