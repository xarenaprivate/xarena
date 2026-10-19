-- register_device: logged-in users only; basic token/platform validation; no anonymous inserts.
create or replace function public.register_device(p_token text, p_platform text default 'android', p_app_version text default null)
returns void language plpgsql security definer set search_path to 'public' as $f$
begin
  if auth.uid() is null then raise exception 'Login required to register a device.' using errcode = '28000'; end if;
  if p_token is null or length(p_token) < 20 or length(p_token) > 4096 then raise exception 'Invalid device token.' using errcode = '22023'; end if;
  if coalesce(p_platform,'android') not in ('android','android-native','ios','web') then raise exception 'Invalid platform.' using errcode = '22023'; end if;
  insert into public.device_tokens (user_id, token, platform, app_version, last_seen)
  values (auth.uid(), p_token, coalesce(p_platform,'android'), left(p_app_version, 40), now())
  on conflict (token) do update set user_id = auth.uid(), platform = excluded.platform, app_version = excluded.app_version, last_seen = now();
end $f$;
revoke execute on function public.register_device(text,text,text) from public, anon;
grant execute on function public.register_device(text,text,text) to authenticated;

-- direct table writes: own rows only (no null-user rows)
drop policy if exists device_tokens_insert_own on public.device_tokens;
create policy device_tokens_insert_own on public.device_tokens for insert to authenticated with check (auth.uid() = user_id);
drop policy if exists device_tokens_update_own on public.device_tokens;
create policy device_tokens_update_own on public.device_tokens for update to authenticated using (auth.uid() = user_id) with check (auth.uid() = user_id);
notify pgrst, 'reload schema';
