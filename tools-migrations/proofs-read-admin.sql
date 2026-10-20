drop policy if exists proofs_read on storage.objects;
create policy proofs_read on storage.objects for select to authenticated using (bucket_id='proofs' and public.is_admin());
