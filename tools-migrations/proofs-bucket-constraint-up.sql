begin;
update storage.buckets set file_size_limit=8388608,allowed_mime_types=array['image/*']::text[] where id='proofs';
commit;
select id,public,file_size_limit,allowed_mime_types from storage.buckets where id='proofs';
