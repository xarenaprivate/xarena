-- FIRST disable endpoint. This removes only new narrow hooks.
drop trigger if exists xr_email_recovery_guard on auth.users;
drop trigger if exists xr_email_binding_invalidate on auth.users;
drop function if exists xarena_private.email_recovery_guard();
drop function if exists xarena_private.email_binding_invalidate();
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

revoke all on function public.xr_email_finish(uuid,uuid,uuid,text) from public,anon,authenticated;
grant execute on function public.xr_email_finish(uuid,uuid,uuid,text) to service_role;
-- Keep endpoint OFF after rollback; it restores the pre-guard unsafe recovery path.
-- Do not restore invalidated bindings or delete Auth users.
