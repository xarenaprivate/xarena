-- Narrow recovery guard. Feature must remain OFF pending provider rollback E2E.
-- Existing users with neither binding nor a new operation are no-ops.
create or replace function xarena_private.email_recovery_guard()
returns trigger language plpgsql security definer set search_path=pg_catalog as $$
declare marker text; c xarena_private.email_challenges;
begin
 marker:=new.raw_app_meta_data->>'xarena_email_recovery_operation';
 if marker is null or marker is not distinct from (old.raw_app_meta_data->>'xarena_email_recovery_operation') then return new; end if;
 if marker !~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' then raise exception 'Invalid recovery operation'; end if;
 select * into c from xarena_private.email_challenges where operation_id=marker::uuid and user_id=new.id and purpose='recovery' and state='reserved' for update;
 if not found or c.expires_at<=clock_timestamp() or lower(new.email) is distinct from c.email
 or not exists(select 1 from xarena_private.email_ownership b where b.user_id=new.id and b.email=c.email)
 then raise exception 'Recovery ownership changed'; end if;
 update xarena_private.email_challenges set state='recovered',finished_at=clock_timestamp() where id=c.id;
 return new;
end $$;
revoke all on function xarena_private.email_recovery_guard() from public,anon,authenticated;
create or replace function xarena_private.email_binding_invalidate()
returns trigger language plpgsql security definer set search_path=pg_catalog as $$
begin
 if lower(old.email) is distinct from lower(new.email) then
  delete from xarena_private.email_ownership where user_id=new.id;
 end if;
 return new;
end $$;
revoke all on function xarena_private.email_binding_invalidate() from public,anon,authenticated;
drop trigger if exists xr_email_recovery_guard on auth.users;
create trigger xr_email_recovery_guard after update of raw_app_meta_data on auth.users for each row
 when ((new.raw_app_meta_data->>'xarena_email_recovery_operation') is distinct from (old.raw_app_meta_data->>'xarena_email_recovery_operation'))
 execute function xarena_private.email_recovery_guard();
drop trigger if exists xr_email_binding_invalidate on auth.users;
create trigger xr_email_binding_invalidate after update of email on auth.users for each row
 when (lower(old.email) is distinct from lower(new.email)) execute function xarena_private.email_binding_invalidate();
-- Trigger consumes the reserved operation inside the Auth password transaction.
-- Finish must accept that exact successful operation, without reviving another.
create or replace function public.xr_email_finish(p_id uuid,p_operation uuid,p_user uuid,p_state text)
returns jsonb language plpgsql security definer set search_path=pg_catalog as $$
declare r xarena_private.email_challenges; actual_email text; actual_op text;
begin
 select * into r from xarena_private.email_challenges where id=p_id for update;
 if not found or r.operation_id is distinct from p_operation then return jsonb_build_object('ok',false); end if;
 if r.state='recovered' and p_state='recovered' and r.purpose='recovery' and r.user_id=p_user then return jsonb_build_object('ok',true); end if;
 if r.state<>'reserved' then return jsonb_build_object('ok',false); end if;
 if p_state='complete' then
  select lower(email),raw_app_meta_data->>'xarena_email_operation' into actual_email,actual_op from auth.users where id=p_user;
  if r.purpose<>'signup' then raise exception 'Invalid signup challenge';end if;
  if actual_email is distinct from r.email or actual_op is distinct from p_operation::text then raise exception 'Ownership binding mismatch'; end if;
  insert into xarena_private.email_ownership(user_id,email,verified_at,challenge_id) values(p_user,r.email,clock_timestamp(),p_id);
 elsif p_state='recovered' then
  -- A successful guard must have consumed the operation already.
  return jsonb_build_object('ok',false);
 elsif p_state not in ('auth_failed','ambiguous') then raise exception 'Invalid result state';
 end if;
 update xarena_private.email_challenges set state=p_state,user_id=p_user,finished_at=clock_timestamp() where id=p_id;
 return jsonb_build_object('ok',true);
end $$;
revoke all on function public.xr_email_finish(uuid,uuid,uuid,text) from public,anon,authenticated;
grant execute on function public.xr_email_finish(uuid,uuid,uuid,text) to service_role;
