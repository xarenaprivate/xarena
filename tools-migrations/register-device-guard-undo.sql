create or replace function public.register_device(p_token text, p_platform text default 'android', p_app_version text default null)
returns void language plpgsql security definer set search_path to 'public' as $f$
begin
  insert into public.device_tokens (user_id, token, platform, app_version, last_seen)
  values (auth.uid(), p_token, coalesce(p_platform,'android'), p_app_version, now())
  on conflict (token) do update set user_id = auth.uid(), app_version = excluded.app_version, last_seen = now();
end; $f$;
grant execute on function public.register_device(text,text,text) to public, anon, authenticated;
drop policy if exists device_tokens_insert_own on public.device_tokens;
create policy device_tokens_insert_own on public.device_tokens for insert to authenticated with check ((auth.uid() = user_id) or (user_id is null));
drop policy if exists device_tokens_update_own on public.device_tokens;
create policy device_tokens_update_own on public.device_tokens for update to authenticated using ((auth.uid() = user_id) or (user_id is null));
notify pgrst, 'reload schema';
