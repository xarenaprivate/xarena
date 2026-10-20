alter table public.support_acks add column if not exists consent boolean not null default false;
alter table public.support_acks add column if not exists amount_inr integer;
alter table public.support_acks add column if not exists status text not null default 'pending';
alter table public.support_acks add column if not exists approved_at timestamptz;
alter table public.support_acks alter column user_id drop not null;
alter table public.support_acks alter column path drop not null;
do $$ begin
  if not exists(select 1 from pg_constraint where conname='support_acks_status_chk') then
    alter table public.support_acks add constraint support_acks_status_chk check (status in ('pending','approved','hidden'));
  end if; end $$;

drop function if exists public.support_ack_submit(text,text,text);
create or replace function public.support_ack_submit(p_path text, p_txn text default null, p_name text default null, p_consent boolean default false) returns jsonb
language plpgsql security definer set search_path to 'public','storage' as $$
declare v uuid := auth.uid();
begin
  if v is null then return jsonb_build_object('ok',false,'error','Log in first.'); end if;
  if p_path is null or split_part(p_path,'/',1) <> v::text or not exists(select 1 from storage.objects where bucket_id='support-proofs' and name=p_path) then
    return jsonb_build_object('ok',false,'error','Upload a screenshot first.'); end if;
  if (select count(*) from support_acks where user_id=v and created_at>now()-interval '24 hours')>=3 then
    return jsonb_build_object('ok',false,'error','Limit reached (3 per day). Try tomorrow.'); end if;
  insert into support_acks(user_id,path,txn_id,donor_name,consent) values (v,p_path,nullif(left(trim(coalesce(p_txn,'')),40),''),nullif(left(trim(coalesce(p_name,'')),40),''),coalesce(p_consent,false));
  return jsonb_build_object('ok',true);
end $$;
grant execute on function public.support_ack_submit(text,text,text,boolean) to authenticated;

create or replace function public.admin_support_acks() returns jsonb
language plpgsql stable security definer set search_path to 'public' as $$
begin
  if not public.is_admin() then raise exception 'admin only'; end if;
  return coalesce((select jsonb_agg(jsonb_build_object('id',a.id,'path',a.path,'txn_id',a.txn_id,'name',a.donor_name,'consent',a.consent,'amount',a.amount_inr,'status',a.status,'seen',a.seen,'created_at',a.created_at,'username',p.username) order by a.id desc)
    from support_acks a left join profiles p on p.id=a.user_id),'[]'::jsonb);
end $$;

create or replace function public.admin_support_ack_approve(p_id bigint, p_amount integer) returns jsonb
language plpgsql security definer set search_path to 'public' as $$
begin
  if not public.is_admin() then raise exception 'admin only'; end if;
  if p_amount is null or p_amount < 1 or p_amount > 1000000 then return jsonb_build_object('ok',false,'error','Enter the verified amount in INR (1 to 1000000).'); end if;
  update support_acks set status='approved', amount_inr=p_amount, approved_at=coalesce(approved_at,now()), seen=true where id=p_id;
  return jsonb_build_object('ok',found);
end $$;
revoke all on function public.admin_support_ack_approve(bigint,integer) from public, anon; grant execute on function public.admin_support_ack_approve(bigint,integer) to authenticated;

create or replace function public.admin_support_ack_hide(p_id bigint) returns jsonb
language plpgsql security definer set search_path to 'public' as $$
begin
  if not public.is_admin() then raise exception 'admin only'; end if;
  update support_acks set status='hidden' where id=p_id; return jsonb_build_object('ok',found);
end $$;
revoke all on function public.admin_support_ack_hide(bigint) from public, anon; grant execute on function public.admin_support_ack_hide(bigint) to authenticated;

-- Public list: approved entries only; name only with consent; no txn id, screenshot, user or contact data.
create or replace function public.support_supporters() returns jsonb
language sql stable security definer set search_path to 'public' as $$
  select coalesce(jsonb_agg(jsonb_build_object('name',case when consent and donor_name is not null then donor_name else 'Anonymous' end,'amount',amount_inr,'date',to_char(approved_at at time zone 'Asia/Kolkata','YYYY-MM-DD')) order by approved_at desc),'[]'::jsonb)
  from (select * from support_acks where status='approved' and amount_inr is not null order by approved_at desc limit 200) s
$$;
grant execute on function public.support_supporters() to anon, authenticated;

-- Purge: proofs older than 30 days are deleted. Unapproved/hidden rows are deleted. Approved rows keep only name (if consented), amount and date.
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
  delete from support_acks where created_at<now()-interval '30 days' and status<>'approved';
  update support_acks set path=null, txn_id=null, user_id=null, donor_name=case when consent then donor_name else null end where created_at<now()-interval '30 days' and status='approved' and (path is not null or txn_id is not null or user_id is not null);
  return n;
end $$;
revoke all on function public.support_purge_old() from public, anon, authenticated;
