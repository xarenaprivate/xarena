create or replace function public.creator_status() returns jsonb
language plpgsql stable security definer set search_path to 'public' as $$
declare v uuid := auth.uid(); c record; a record; isc boolean;
begin
  if v is null then return jsonb_build_object('is_creator',false,'application',null); end if;
  select * into c from creators where user_id=v and active;
  isc := found;
  select status, created_at into a from creator_applications where user_id=v order by id desc limit 1;
  return jsonb_build_object('is_creator',isc,'tier',c.tier,'self_reported_followers',c.self_followers,'application_status',a.status);
end $$;
notify pgrst, 'reload schema';
