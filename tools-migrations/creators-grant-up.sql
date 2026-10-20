create or replace function public.admin_creator_grant(p_username text, p_followers int default 0, p_channel text default null) returns jsonb
language plpgsql security definer set search_path to 'public' as $$
declare u uuid;
begin
  if auth.uid() is null or not is_admin() then return jsonb_build_object('ok',false,'error','Admin only.'); end if;
  select id into u from profiles where lower(username)=lower(trim(p_username));
  if u is null then return jsonb_build_object('ok',false,'error','No such username.'); end if;
  insert into creators(user_id,channel_url,self_followers,tier,approved_by) values (u,p_channel,coalesce(p_followers,0),creator_tier(coalesce(p_followers,0)),auth.uid())
  on conflict (user_id) do update set active=true, approved_by=auth.uid(), approved_at=now(),
    self_followers=excluded.self_followers, tier=excluded.tier, channel_url=coalesce(excluded.channel_url,creators.channel_url);
  return jsonb_build_object('ok',true,'user_id',u);
end $$;
revoke all on function public.admin_creator_grant(text,int,text) from public, anon;
grant execute on function public.admin_creator_grant(text,int,text) to authenticated;
notify pgrst, 'reload schema';
