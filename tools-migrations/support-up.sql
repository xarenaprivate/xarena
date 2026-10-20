-- Support "I paid" acknowledgements: private table + private bucket, admin-only read, 30-day auto-delete.
insert into storage.buckets(id,name,public,file_size_limit,allowed_mime_types)
values('support-proofs','support-proofs',false,5242880,array['image/jpeg','image/png'])
on conflict (id) do update set public=false,file_size_limit=5242880,allowed_mime_types=array['image/jpeg','image/png'];

create table if not exists public.support_acks(
  id bigserial primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  path text not null,
  txn_id text,
  donor_name text,
  seen boolean not null default false,
  created_at timestamptz not null default now());
alter table public.support_acks enable row level security;
revoke all on public.support_acks from anon, authenticated;

create or replace function public.support_upload_ok(p_uid uuid) returns boolean
language sql stable security definer set search_path to 'public','storage' as $$
  select p_uid is not null and (select count(*) from storage.objects where bucket_id='support-proofs' and owner_id=p_uid::text and created_at>now()-interval '24 hours')<3
$$;
revoke all on function public.support_upload_ok(uuid) from public, anon;
grant execute on function public.support_upload_ok(uuid) to authenticated;

drop policy if exists support_proofs_insert on storage.objects;
create policy support_proofs_insert on storage.objects for insert to authenticated
  with check (bucket_id='support-proofs' and (storage.foldername(name))[1]=auth.uid()::text and public.support_upload_ok(auth.uid()));
drop policy if exists support_proofs_admin_read on storage.objects;
create policy support_proofs_admin_read on storage.objects for select to authenticated
  using (bucket_id='support-proofs' and public.is_admin());
drop policy if exists support_proofs_admin_delete on storage.objects;
create policy support_proofs_admin_delete on storage.objects for delete to authenticated
  using (bucket_id='support-proofs' and public.is_admin());

create or replace function public.support_ack_submit(p_path text, p_txn text default null, p_name text default null) returns jsonb
language plpgsql security definer set search_path to 'public','storage' as $$
declare v uuid := auth.uid();
begin
  if v is null then return jsonb_build_object('ok',false,'error','Log in first.'); end if;
  if p_path is null or split_part(p_path,'/',1) <> v::text or not exists(select 1 from storage.objects where bucket_id='support-proofs' and name=p_path) then
    return jsonb_build_object('ok',false,'error','Upload a screenshot first.'); end if;
  if (select count(*) from support_acks where user_id=v and created_at>now()-interval '24 hours')>=3 then
    return jsonb_build_object('ok',false,'error','Limit reached (3 per day). Try tomorrow.'); end if;
  insert into support_acks(user_id,path,txn_id,donor_name) values (v,p_path,nullif(left(trim(coalesce(p_txn,'')),40),''),nullif(left(trim(coalesce(p_name,'')),40),''));
  return jsonb_build_object('ok',true);
end $$;
grant execute on function public.support_ack_submit(text,text,text) to authenticated;

create or replace function public.support_ack_can_upload() returns boolean
language sql stable security definer set search_path to 'public' as $$ select public.support_upload_ok(auth.uid()) $$;
grant execute on function public.support_ack_can_upload() to authenticated;

create or replace function public.admin_support_acks() returns jsonb
language plpgsql stable security definer set search_path to 'public' as $$
begin
  if not public.is_admin() then raise exception 'admin only'; end if;
  return coalesce((select jsonb_agg(jsonb_build_object('id',a.id,'path',a.path,'txn_id',a.txn_id,'name',a.donor_name,'seen',a.seen,'created_at',a.created_at,'username',p.username) order by a.id desc)
    from support_acks a left join profiles p on p.id=a.user_id),'[]'::jsonb);
end $$;
revoke all on function public.admin_support_acks() from public, anon; grant execute on function public.admin_support_acks() to authenticated;

create or replace function public.admin_support_ack_seen(p_id bigint) returns jsonb
language plpgsql security definer set search_path to 'public' as $$
begin
  if not public.is_admin() then raise exception 'admin only'; end if;
  update support_acks set seen=true where id=p_id; return jsonb_build_object('ok',true);
end $$;
revoke all on function public.admin_support_ack_seen(bigint) from public, anon; grant execute on function public.admin_support_ack_seen(bigint) to authenticated;

create or replace function public.admin_support_ack_delete(p_id bigint) returns jsonb
language plpgsql security definer set search_path to 'public' as $$
begin
  if not public.is_admin() then raise exception 'admin only'; end if;
  delete from support_acks where id=p_id; return jsonb_build_object('ok',true);
end $$;
revoke all on function public.admin_support_ack_delete(bigint) from public, anon; grant execute on function public.admin_support_ack_delete(bigint) to authenticated;

-- 30-day purge: removes files through the Storage API (service key kept in Vault), then rows.
create or replace function public.support_purge_old() returns int
language plpgsql security definer set search_path to 'public','storage','vault','net' as $$
declare r record; k text; n int := 0;
begin
  select decrypted_secret into k from vault.decrypted_secrets where name='xarena_service_role' limit 1;
  if k is not null then
    for r in select name from storage.objects where bucket_id='support-proofs' and created_at<now()-interval '30 days' loop
      perform net.http_delete(url:='https://oskccuajgbyabogbvedj.supabase.co/storage/v1/object/support-proofs/'||r.name, headers:=jsonb_build_object('Authorization','Bearer '||k,'apikey',k));
      n := n+1;
    end loop;
  end if;
  delete from support_acks where created_at<now()-interval '30 days';
  return n;
end $$;
revoke all on function public.support_purge_old() from public, anon, authenticated;
