-- Anti fake-account: device/IP rate limits, cluster flags, admin view. Additive; undo in antifake-undo.sql
create table if not exists public.abuse_secret (id int primary key default 1, pepper text not null default encode(extensions.gen_random_bytes(24),'hex'), check (id=1));
insert into public.abuse_secret(id) values (1) on conflict do nothing;
alter table public.abuse_secret enable row level security;
revoke all on public.abuse_secret from anon, authenticated;

create table if not exists public.signup_attempts (id bigserial primary key, ts timestamptz not null default now(), ip_hash text, device_hash text, phone_hash text, result text);
create index if not exists signup_attempts_ts on public.signup_attempts(ts);
create table if not exists public.account_devices (user_id uuid not null references auth.users(id) on delete cascade, device_hash text not null, kind text not null check (kind in ('install','fp')), platform text, ip_hash text, first_seen timestamptz default now(), last_seen timestamptz default now(), primary key(user_id, device_hash, kind));
create index if not exists account_devices_hash on public.account_devices(device_hash, kind);
create index if not exists account_devices_ip on public.account_devices(ip_hash);
create table if not exists public.abuse_flags (id bigserial primary key, kind text not null, key_hash text not null, user_ids uuid[] not null default '{}', status text not null default 'open' check (status in ('open','dismissed','actioned')), note text, created_at timestamptz default now(), updated_at timestamptz default now(), handled_at timestamptz);
create unique index if not exists abuse_flags_open_uniq on public.abuse_flags(kind, key_hash) where status='open';
alter table public.signup_attempts enable row level security; alter table public.account_devices enable row level security; alter table public.abuse_flags enable row level security;
revoke all on public.signup_attempts, public.account_devices, public.abuse_flags from anon, authenticated;

create or replace function public._abuse_h(p text) returns text language sql stable security definer set search_path=public as $$
  select case when p is null or p='' then null else encode(extensions.digest(p || (select pepper from abuse_secret where id=1), 'sha256'),'hex') end $$;
revoke all on function public._abuse_h(text) from public, anon, authenticated;

create or replace function public._req_ip() returns text language plpgsql stable as $$
declare h json; x text;
begin
  begin h := current_setting('request.headers', true)::json; exception when others then return null; end;
  if h is null then return null; end if;
  x := coalesce(h->>'cf-connecting-ip', h->>'x-real-ip', split_part(coalesce(h->>'x-forwarded-for',''), ',', 1));
  return nullif(trim(x),'');
end $$;
revoke all on function public._req_ip() from public, anon, authenticated;

create or replace function public.signup_precheck(p_phone text, p_device_id text default null, p_fp text default null)
returns jsonb language plpgsql security definer set search_path=public as $$
declare v_p text := normalize_phone(p_phone); ip text := _abuse_h(_req_ip()); dv text := _abuse_h(left(p_device_id,128)); ph text; n int;
begin
  if v_p is null then return jsonb_build_object('ok',false,'error','Phone number must be 10 digits.'); end if;
  ph := _abuse_h(v_p);
  if (_phone_risk(v_p)->>'score')::int >= 50 then
    insert into signup_attempts(ip_hash,device_hash,phone_hash,result) values (_abuse_h(_req_ip()),_abuse_h(left(p_device_id,128)),ph,'junk_phone');
    return jsonb_build_object('ok',false,'error','Please enter your real 10-digit mobile number (starts with 6, 7, 8 or 9).'); end if;
  delete from signup_attempts where ts < now() - interval '3 days';
  if ip is not null and (select count(*) from signup_attempts where ip_hash=ip and ts > now() - interval '1 hour') >= 15 then
    insert into signup_attempts(ip_hash,device_hash,phone_hash,result) values (ip,dv,ph,'rate_ip');
    return jsonb_build_object('ok',false,'error','Too many sign-up attempts from this network. Try again in an hour.'); end if;
  if dv is not null and (select count(*) from signup_attempts where device_hash=dv and ts > now() - interval '1 hour') >= 5 then
    insert into signup_attempts(ip_hash,device_hash,phone_hash,result) values (ip,dv,ph,'rate_device');
    return jsonb_build_object('ok',false,'error','Too many sign-up attempts from this device. Try again in an hour.'); end if;
  if (select count(*) from signup_attempts where phone_hash=ph and ts > now() - interval '1 hour') >= 5 then
    return jsonb_build_object('ok',false,'error','Too many attempts for this number. Try again later.'); end if;
  if ip is not null and (select count(distinct user_id) from account_devices where ip_hash=ip and first_seen > now() - interval '24 hours') >= 8 then
    insert into signup_attempts(ip_hash,device_hash,phone_hash,result) values (ip,dv,ph,'cap_ip');
    return jsonb_build_object('ok',false,'error','Too many new accounts from this network today. Try again tomorrow.'); end if;
  if dv is not null then
    select count(distinct user_id) into n from account_devices where device_hash=dv and kind='install';
    if n >= 2 then
      insert into signup_attempts(ip_hash,device_hash,phone_hash,result) values (ip,dv,ph,'cap_device');
      return jsonb_build_object('ok',false,'error','This device already has 2 accounts. Please log in to one of them.'); end if;
  end if;
  insert into signup_attempts(ip_hash,device_hash,phone_hash,result) values (ip,dv,ph,'ok');
  return jsonb_build_object('ok',true,'available',true,'phone',v_p);
end $$;
grant execute on function public.signup_precheck(text,text,text) to anon, authenticated;

create or replace function public.bind_device(p_device_id text, p_fp text default null, p_platform text default 'web')
returns jsonb language plpgsql security definer set search_path=public as $$
declare me uuid := auth.uid(); ip text := _abuse_h(_req_ip()); dv text := _abuse_h(left(p_device_id,128)); fp text := _abuse_h(left(p_fp,128)); ids uuid[];
begin
  if me is null then return jsonb_build_object('ok',false,'error','Log in first.'); end if;
  if coalesce(p_platform,'web') not in ('web','android','android-native','ios') then p_platform := 'web'; end if;
  if dv is not null then
    insert into account_devices(user_id,device_hash,kind,platform,ip_hash) values (me,dv,'install',p_platform,ip)
    on conflict (user_id,device_hash,kind) do update set last_seen=now(), ip_hash=coalesce(excluded.ip_hash, account_devices.ip_hash);
    select array_agg(distinct user_id) into ids from account_devices where device_hash=dv and kind='install';
    if array_length(ids,1) >= 2 then
      insert into abuse_flags(kind,key_hash,user_ids) values ('device',dv,ids)
      on conflict (kind,key_hash) where status='open' do update set user_ids=excluded.user_ids, updated_at=now(); end if;
  end if;
  if fp is not null then
    insert into account_devices(user_id,device_hash,kind,platform,ip_hash) values (me,fp,'fp',p_platform,ip)
    on conflict (user_id,device_hash,kind) do update set last_seen=now();
    select array_agg(distinct user_id) into ids from account_devices where device_hash=fp and kind='fp';
    if array_length(ids,1) >= 3 then
      insert into abuse_flags(kind,key_hash,user_ids) values ('fingerprint',fp,ids)
      on conflict (kind,key_hash) where status='open' do update set user_ids=excluded.user_ids, updated_at=now(); end if;
  end if;
  if ip is not null then
    select array_agg(distinct user_id) into ids from account_devices where ip_hash=ip and first_seen > now() - interval '24 hours';
    if array_length(ids,1) >= 3 then
      insert into abuse_flags(kind,key_hash,user_ids) values ('network',ip,ids)
      on conflict (kind,key_hash) where status='open' do update set user_ids=excluded.user_ids, updated_at=now(); end if;
  end if;
  return jsonb_build_object('ok',true);
end $$;
grant execute on function public.bind_device(text,text,text) to authenticated;

create or replace function public.admin_suspicious_accounts()
returns jsonb language plpgsql security definer set search_path=public as $$
begin
  if not (select is_admin()) then return jsonb_build_object('ok',false,'error','admin only'); end if;
  return jsonb_build_object('ok',true,'rows',coalesce((select jsonb_agg(f order by f.created_at desc) from (
    select g.id, g.kind, g.status, g.note, g.created_at, g.updated_at,
      (select coalesce(jsonb_agg(jsonb_build_object('id',p.id,'username',p.username,'ign',p.ign,'created_at',p.created_at,'verified',p.verified,'banned',coalesce(p.banned,false))),'[]'::jsonb) from profiles p where p.id = any(g.user_ids)) accounts
    from abuse_flags g order by (g.status='open') desc, g.updated_at desc limit 100) f),'[]'::jsonb));
end $$;
grant execute on function public.admin_suspicious_accounts() to authenticated;

create or replace function public.admin_handle_abuse_flag(p_id bigint, p_status text, p_note text default null)
returns jsonb language plpgsql security definer set search_path=public as $$
begin
  if not (select is_admin()) then return jsonb_build_object('ok',false,'error','admin only'); end if;
  if p_status not in ('dismissed','actioned') then return jsonb_build_object('ok',false,'error','Bad status.'); end if;
  update abuse_flags set status=p_status, note=nullif(trim(coalesce(p_note,'')),''), handled_at=now(), updated_at=now() where id=p_id and status='open';
  return jsonb_build_object('ok',true);
end $$;
grant execute on function public.admin_handle_abuse_flag(bigint,text,text) to authenticated;
notify pgrst, 'reload schema';
