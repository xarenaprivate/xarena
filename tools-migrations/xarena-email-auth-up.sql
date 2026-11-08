-- PREPARATION ONLY. Reviewed deployment + rollback and delivery tests still required.
-- No modification to existing auth settings, profile flags, phone signup or recovery.
create schema if not exists xarena_private;
revoke all on schema xarena_private from public, anon, authenticated;
create table if not exists xarena_private.email_challenges (
 id uuid primary key, purpose text not null default 'signup' check(purpose in ('signup','recovery')), email text not null, email_hash text not null,
 ip_hash text not null, device_hash text not null,
 code_hash text not null check(code_hash ~ '^[a-f0-9]{64}$'),
 created_at timestamptz not null default now(), expires_at timestamptz not null,
 failures int not null default 0 check(failures between 0 and 5),
 state text not null default 'sending' check(state in ('sending','sent','failed','reserved','complete','recovered','auth_failed','ambiguous')),
 operation_id uuid, user_id uuid, finished_at timestamptz
);
create index if not exists email_challenges_created on xarena_private.email_challenges(created_at);
create index if not exists email_challenges_email on xarena_private.email_challenges(email_hash,created_at);
create table if not exists xarena_private.email_ownership (
 user_id uuid primary key references auth.users(id) on delete cascade,
 email text not null unique, verified_at timestamptz not null,
 challenge_id uuid not null unique references xarena_private.email_challenges(id),
 method text not null default 'xarena-email-code-v1'
);
alter table xarena_private.email_challenges enable row level security;
alter table xarena_private.email_ownership enable row level security;
revoke all on all tables in schema xarena_private from public, anon, authenticated;

create or replace function public.xr_email_start(p_id uuid,p_email text,p_email_hash text,p_ip_hash text,p_device_hash text,p_code_hash text,p_purpose text default 'signup')
returns jsonb language plpgsql security definer set search_path=pg_catalog as $$
declare n timestamptz:=clock_timestamp();
begin
 -- Cleanup never touches existing auth or phone data. Keep rate history48h.
 delete from xarena_private.email_challenges c where c.created_at<clock_timestamp()-interval '48 hours' and not exists(select 1 from xarena_private.email_ownership b where b.challenge_id=c.id);
 update xarena_private.email_challenges set email='',code_hash=repeat('0',64),email_hash='',ip_hash='',device_hash='' where created_at<clock_timestamp()-interval '24 hours' and state='complete' and email<>'';
 -- Global advisory lock serializes quota reservation, including concurrent starts.
 perform pg_advisory_xact_lock(842003711);
 if p_purpose not in ('signup','recovery') then raise exception 'Invalid purpose'; end if;
 if length(p_email)>254 or p_email not like '%@%' or p_email_hash !~ '^[a-f0-9]{64}$' or p_ip_hash !~ '^[a-f0-9]{64}$' or p_device_hash !~ '^[a-f0-9]{64}$' or p_code_hash !~ '^[a-f0-9]{64}$' then raise exception 'Invalid request'; end if;
 if (select count(*) from xarena_private.email_challenges where created_at>n-interval '24 hours')>=100
 or (select count(*) from xarena_private.email_challenges where email_hash=p_email_hash and created_at>n-interval '24 hours')>=10
 or (select count(*) from xarena_private.email_challenges where email_hash=p_email_hash and created_at>n-interval '1 hour')>=3
 or exists(select 1 from xarena_private.email_challenges where email_hash=p_email_hash and created_at>n-interval '60 seconds')
 or (select count(*) from xarena_private.email_challenges where ip_hash=p_ip_hash and created_at>n-interval '1 hour')>=10
 or (select count(*) from xarena_private.email_challenges where device_hash=p_device_hash and created_at>n-interval '1 hour')>=5
 then return jsonb_build_object('ok',false,'error','Please wait before requesting another code.'); end if;
 -- Invalidate prior unresolved sends for the same email, never restore an old code.
 update xarena_private.email_challenges set state='failed',finished_at=n where email_hash=p_email_hash and state in ('sending','sent');
 insert into xarena_private.email_challenges(id,purpose,email,email_hash,ip_hash,device_hash,code_hash,created_at,expires_at)
 values(p_id,p_purpose,p_email,p_email_hash,p_ip_hash,p_device_hash,p_code_hash,n,n+interval '10 minutes');
 return jsonb_build_object('ok',true);
end $$;

create or replace function public.xr_email_delivery(p_id uuid,p_sent boolean)
returns jsonb language plpgsql security definer set search_path=pg_catalog as $$
begin
 update xarena_private.email_challenges set state=case when p_sent then 'sent' else 'failed' end
 where id=p_id and state='sending' and expires_at>clock_timestamp();
 return jsonb_build_object('ok',found);
end $$;

create or replace function public.xr_email_lookup(p_id uuid)
returns jsonb language sql security definer set search_path=pg_catalog as $$
 select coalesce((select jsonb_build_object('email',email) from xarena_private.email_challenges where id=p_id),'{}'::jsonb)
$$;

create or replace function public.xr_email_reserve(p_id uuid,p_code_hash text,p_operation uuid,p_purpose text default 'signup')
returns jsonb language plpgsql security definer set search_path=pg_catalog as $$
declare r xarena_private.email_challenges; n timestamptz:=clock_timestamp(); v_user uuid;
begin
 select * into r from xarena_private.email_challenges where id=p_id for update;
 if p_operation is null or not found or r.purpose is distinct from p_purpose or r.state<>'sent' or r.failures>=5 or r.expires_at<=n then return jsonb_build_object('ok',false); end if;
 if r.code_hash is distinct from p_code_hash then
  update xarena_private.email_challenges set failures=failures+1,state=case when failures+1>=5 then 'failed' else state end where id=p_id;
  return jsonb_build_object('ok',false);
 end if;
 if r.purpose='recovery' then
  select b.user_id into v_user from xarena_private.email_ownership b join auth.users u on u.id=b.user_id where b.email=r.email and lower(u.email)=b.email and coalesce(u.banned_until,'-infinity'::timestamptz)<=n;
  if v_user is null then return jsonb_build_object('ok',false);end if;
 end if;
 update xarena_private.email_challenges set state='reserved',operation_id=p_operation,user_id=v_user where id=p_id;
 return jsonb_build_object('ok',true,'email',r.email,'user_id',v_user);
end $$;

create or replace function public.xr_email_finish(p_id uuid,p_operation uuid,p_user uuid,p_state text)
returns jsonb language plpgsql security definer set search_path=pg_catalog as $$
declare r xarena_private.email_challenges; actual_email text; actual_op text;
begin
 select * into r from xarena_private.email_challenges where id=p_id for update;
 if not found or r.state<>'reserved' or r.operation_id is distinct from p_operation then return jsonb_build_object('ok',false); end if;
 if p_state='complete' then
  select lower(email),raw_app_meta_data->>'xarena_email_operation' into actual_email,actual_op from auth.users where id=p_user;
  if r.purpose<>'signup' then raise exception 'Invalid signup challenge';end if;
  if actual_email is distinct from r.email or actual_op is distinct from p_operation::text then raise exception 'Ownership binding mismatch'; end if;
  insert into xarena_private.email_ownership(user_id,email,verified_at,challenge_id) values(p_user,r.email,clock_timestamp(),p_id);
 elsif p_state='recovered' then
  if r.purpose<>'recovery' or r.user_id is distinct from p_user then raise exception 'Invalid recovery result';end if;
 elsif p_state not in ('auth_failed','ambiguous') then raise exception 'Invalid result state';
 end if;
 update xarena_private.email_challenges set state=p_state,user_id=p_user,finished_at=clock_timestamp() where id=p_id;
 return jsonb_build_object('ok',true);
end $$;

-- Service role only, never client grants, including default PUBLIC execute.
revoke all on function public.xr_email_start(uuid,text,text,text,text,text,text) from public,anon,authenticated;
revoke all on function public.xr_email_delivery(uuid,boolean) from public,anon,authenticated;
revoke all on function public.xr_email_lookup(uuid) from public,anon,authenticated;
revoke all on function public.xr_email_reserve(uuid,text,uuid,text) from public,anon,authenticated;
revoke all on function public.xr_email_finish(uuid,uuid,uuid,text) from public,anon,authenticated;
grant execute on function public.xr_email_start(uuid,text,text,text,text,text,text),public.xr_email_delivery(uuid,boolean),public.xr_email_lookup(uuid),public.xr_email_reserve(uuid,text,uuid,text),public.xr_email_finish(uuid,uuid,uuid,text) to service_role;
