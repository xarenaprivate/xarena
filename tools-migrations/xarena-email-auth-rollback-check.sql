-- Execute after migration inside BEGIN/ROLLBACK. Synthetic rows never reach auth.users.
do $$
declare a uuid:='aaaaaaaa-0000-0000-0000-000000000001'; o uuid:='bbbbbbbb-0000-0000-0000-000000000001'; r jsonb;
begin
 if has_function_privilege('anon','public.xr_email_start(uuid,text,text,text,text,text,text)','EXECUTE') or has_function_privilege('authenticated','public.xr_email_reserve(uuid,text,uuid,text)','EXECUTE') then raise exception 'Client execute leak'; end if;
 r:=public.xr_email_start(a,'rollback-test@example.invalid',repeat('a',64),repeat('b',64),repeat('c',64),repeat('d',64));
 if not (r->>'ok')::boolean then raise exception 'Reservation failed'; end if;
 r:=public.xr_email_start(gen_random_uuid(),'rollback-test@example.invalid',repeat('a',64),repeat('b',64),repeat('c',64),repeat('d',64));
 if (r->>'ok')::boolean then raise exception 'Cooldown bypass'; end if;
 r:=public.xr_email_delivery(a,true); if not (r->>'ok')::boolean then raise exception 'Delivery state failed';end if;
 for i in 1..4 loop r:=public.xr_email_reserve(a,repeat('e',64),o); if (r->>'ok')::boolean then raise exception 'Wrong code accepted';end if;end loop;
 r:=public.xr_email_reserve(a,repeat('d',64),o); if not (r->>'ok')::boolean then raise exception 'Correct code rejected';end if;
 r:=public.xr_email_reserve(a,repeat('d',64),gen_random_uuid());if (r->>'ok')::boolean then raise exception 'Replay accepted';end if;
 r:=public.xr_email_finish(a,o,null,'auth_failed');if not (r->>'ok')::boolean then raise exception 'Finish failed';end if;
 if exists(select 1 from xarena_private.email_ownership where challenge_id=a) then raise exception 'Failure got binding';end if;
end $$;
select 'rollback checks passed' as result,
 has_function_privilege('service_role','public.xr_email_reserve(uuid,text,uuid,text)','EXECUTE') as service_only,
 (select relrowsecurity from pg_class where oid='xarena_private.email_challenges'::regclass) as rls_on;
