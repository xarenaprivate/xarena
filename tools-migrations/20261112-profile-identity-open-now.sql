-- Owner's Nov12 evening override: open the already-deployed feature now.
BEGIN;
UPDATE public.xr_profile_identity_config SET opens_at=least(opens_at,clock_timestamp()) WHERE singleton;
SELECT opens_at FROM public.xr_profile_identity_config WHERE singleton;
COMMIT;
