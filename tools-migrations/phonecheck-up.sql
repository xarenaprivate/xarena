-- Admin Phone Check: rule-based estimate (cannot prove ownership). Additive; undo in phonecheck-undo.sql
alter table public.abuse_flags drop constraint if exists abuse_flags_kind_chk;
create or replace function public._phone_risk(p_phone text) returns jsonb language plpgsql immutable as $$
declare n text := right(coalesce(p_phone,''),10); s int := 0; r text[] := '{}'; d int; mx int := 0; run int; i int; c text; asc_run int := 1; desc_run int := 1; mxa int := 1; mxd int := 1;
begin
  if n !~ '^[0-9]{10}$' then return jsonb_build_object('score',60,'reasons',jsonb_build_array('Not a 10-digit number')); end if;
  if n !~ '^[6-9]' then s := s+50; r := array_append(r, ('Does not start with 6-9, so it is not a valid Indian mobile number')::text); end if;
  select count(distinct ch) into d from regexp_split_to_table(n,'') ch;
  if d = 1 then s := s+60; r := array_append(r, 'All digits are the same');
  elsif d <= 3 then s := s+25; r := array_append(r, ('Only ' || d || ' different digits')::text); end if;
  -- longest run of one digit
  select max(length(x)) into mx from (select (regexp_matches(n,'((.)\2{4,})','g'))[1] x) t;
  if coalesce(mx,0) >= 5 then s := s+30; r := array_append(r, ('A digit repeats ' || mx || ' times in a row')::text); end if;
  for i in 2..10 loop
    if (substr(n,i,1))::int = (substr(n,i-1,1))::int + 1 then asc_run := asc_run+1; else asc_run := 1; end if;
    if (substr(n,i,1))::int = (substr(n,i-1,1))::int - 1 then desc_run := desc_run+1; else desc_run := 1; end if;
    mxa := greatest(mxa,asc_run); mxd := greatest(mxd,desc_run);
  end loop;
  if mxa >= 6 or mxd >= 6 then s := s+40; r := array_append(r, ('Sequential digits (' || greatest(mxa,mxd) || ' in a row)')::text); end if;
  if n ~ '^(..)\1{3,}' or n ~ '(..)\1{2,}$' or n ~ '(...)\1{2}' then s := s+25; r := array_append(r, ('Repeating digit pattern')::text); end if;
  if n in ('9999999999','8888888888','7777777777','6666666666','9876543210','9123456789','9000000000','9999900000','9012345678','8123456789','7890123456','9898989898','9090909090','9191919191') then s := s+50; r := array_append(r, ('On the common fake-number list')::text); end if;
  if n ~ '0{5}$' then s := s+15; r := array_append(r, ('Ends in five or more zeros')::text); end if;
  return jsonb_build_object('score',least(s,100),'reasons',to_jsonb(r));
end $$;

create or replace function public.admin_phone_check()
returns jsonb language plpgsql security definer set search_path=public as $$
begin
  if not (select is_admin()) then return jsonb_build_object('ok',false,'error','admin only'); end if;
  return jsonb_build_object('ok',true,'rows',coalesce((select jsonb_agg(x order by (x->>'score')::int desc, x->>'created_at' desc) from (
    select jsonb_build_object('id',p.id,'username',p.username,'ign',p.ign,'created_at',p.created_at,'verified',p.verified,'phone_verified',coalesce(p.phone_verified,false),'banned',coalesce(p.banned,false),
      'masked', case when p.phone is null then null else left(right(p.phone,10),2) || 'XXXXXX' || right(p.phone,2) end,
      'score', least(100, greatest(0, (_phone_risk(p.phone)->>'score')::int + b.extra - (case when p.phone_verified then 15 else 0 end))),
      'reasons', (_phone_risk(p.phone)->'reasons') || to_jsonb(b.reasons || (case when p.phone_verified then array['Owner confirmed the code manually (-15)'] else array['Owner code not confirmed yet (+10)'] end))) as x
    from profiles p
    cross join lateral (
      select
        (case when not coalesce(p.phone_verified,false) then 10 else 0 end)
        + (select coalesce(sum(case g.kind when 'device' then 30 when 'fingerprint' then 10 when 'network' then 15 else 10 end),0)::int from abuse_flags g where g.status='open' and p.id = any(g.user_ids))
        + (case when (select count(*) from profiles q where q.id<>p.id and q.phone is not null and p.phone is not null and left(right(q.phone,10),8)=left(right(p.phone,10),8))>0 then 15 else 0 end)
        + (case when (select count(*) from profiles q where q.id<>p.id and q.created_at between p.created_at - interval '1 hour' and p.created_at + interval '1 hour')>=2 then 10 else 0 end) as extra,
        array_remove(array[
          (select string_agg(case g.kind when 'device' then 'Shares a device with another account (+30)' when 'fingerprint' then 'Similar device profile to other accounts (+10)' when 'network' then 'Same network, several new accounts (+15)' else 'Flagged by admin (+10)' end, '; ') from abuse_flags g where g.status='open' and p.id = any(g.user_ids)),
          case when (select count(*) from profiles q where q.id<>p.id and q.phone is not null and p.phone is not null and left(right(q.phone,10),8)=left(right(p.phone,10),8))>0 then 'Another account has a number differing only in the last 2 digits (+15)' end,
          case when (select count(*) from profiles q where q.id<>p.id and q.created_at between p.created_at - interval '1 hour' and p.created_at + interval '1 hour')>=2 then '3 or more sign-ups within an hour (+10)' end
        ], null) as reasons
    ) b where p.phone is not null) t),'[]'::jsonb));
end $$;
grant execute on function public.admin_phone_check() to authenticated;

create or replace function public.admin_flag_account(p_user uuid, p_note text default null)
returns jsonb language plpgsql security definer set search_path=public as $$
begin
  if not (select is_admin()) then return jsonb_build_object('ok',false,'error','admin only'); end if;
  insert into abuse_flags(kind,key_hash,user_ids,note) values ('phone_check', p_user::text, array[p_user], nullif(trim(coalesce(p_note,'')),''))
  on conflict (kind,key_hash) where status='open' do update set note=excluded.note, updated_at=now();
  return jsonb_build_object('ok',true);
end $$;
grant execute on function public.admin_flag_account(uuid,text) to authenticated;
notify pgrst, 'reload schema';
