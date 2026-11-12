BEGIN;
LOCK TABLE public.creators IN SHARE ROW EXCLUSIVE MODE;
DO $baseline$ BEGIN IF EXISTS(SELECT 1 FROM public.creators)THEN RAISE EXCEPTION 'Creator baseline changed: review historical verification order before deployment.';END IF;END $baseline$;
CREATE TABLE public.xr_creator_settings (
 singleton boolean PRIMARY KEY DEFAULT true CHECK(singleton), enabled boolean NOT NULL DEFAULT false,
 default_city_slots integer NOT NULL DEFAULT 10 CHECK(default_city_slots BETWEEN 0 AND 10000),
 verification_counter bigint NOT NULL DEFAULT 0,application_deadline timestamptz, founding_limit integer NOT NULL DEFAULT 10 CHECK(founding_limit BETWEEN 0 AND 100000),
 drop_limit integer NOT NULL DEFAULT 50 CHECK(drop_limit BETWEEN 0 AND 100000),drop_key text NOT NULL DEFAULT 'limited_creator_cosmetic',
 waitlist_boost_per_referral integer NOT NULL DEFAULT 1 CHECK(waitlist_boost_per_referral BETWEEN 0 AND 100),
 bonus_enabled boolean NOT NULL DEFAULT false,bonus_starts_at timestamptz,bonus_ends_at timestamptz,
 bonus_inr numeric(12,2) NOT NULL DEFAULT 0 CHECK(bonus_inr>=0),bonus_coins integer NOT NULL DEFAULT 0 CHECK(bonus_coins>=0),
 bonus_funding_confirmed boolean NOT NULL DEFAULT false,
 CHECK(NOT bonus_enabled OR (bonus_starts_at IS NOT NULL AND bonus_ends_at>bonus_starts_at))
);
INSERT INTO public.xr_creator_settings(singleton)VALUES(true);
CREATE TABLE public.xr_creator_cities (
 city_key text PRIMARY KEY,city text NOT NULL,capacity integer NOT NULL CHECK(capacity BETWEEN 0 AND 10000)
);
CREATE TABLE public.xr_creator_application_details (
 application_id bigint PRIMARY KEY REFERENCES public.creator_applications(id)ON DELETE CASCADE,
 user_id uuid NOT NULL REFERENCES public.profiles(id)ON DELETE CASCADE,
 display_name text NOT NULL CHECK(char_length(display_name)BETWEEN 1 AND 80),
 city_key text NOT NULL REFERENCES public.xr_creator_cities(city_key),instagram text NOT NULL,
 player_count integer NOT NULL CHECK(player_count BETWEEN 0 AND 1000000),
 stage text NOT NULL DEFAULT 'applied' CHECK(stage IN('applied','under_review','waitlisted','verified','rejected','revoked')),
 public_activity_opt_in boolean NOT NULL DEFAULT false,friends_applied_opt_in boolean NOT NULL DEFAULT false,
 updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX xr_creator_details_user ON public.xr_creator_application_details(user_id,application_id DESC);
CREATE TABLE public.xr_creator_verifications (
 user_id uuid PRIMARY KEY REFERENCES public.creators(user_id)ON DELETE CASCADE,
 ordinal bigint UNIQUE NOT NULL,verified_at timestamptz NOT NULL DEFAULT clock_timestamp(),city_key text REFERENCES public.xr_creator_cities(city_key)
);
CREATE TABLE public.xr_creator_entitlements (
 user_id uuid NOT NULL REFERENCES public.profiles(id)ON DELETE CASCADE,kind text NOT NULL CHECK(kind IN('founding_badge','limited_drop','early_bonus')),
 entitlement_key text NOT NULL,amount_inr numeric(12,2) NOT NULL DEFAULT 0,coins integer NOT NULL DEFAULT 0,
 status text NOT NULL CHECK(status IN('granted','pending_funding','pending_owner_fulfillment')),created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 PRIMARY KEY(user_id,kind),CHECK(amount_inr>=0 AND coins>=0)
);
CREATE TABLE public.xr_creator_activity (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,user_id uuid NOT NULL REFERENCES public.profiles(id)ON DELETE CASCADE,
 application_id bigint REFERENCES public.creator_applications(id)ON DELETE CASCADE,kind text NOT NULL CHECK(kind IN('applied','slot_taken')),created_at timestamptz NOT NULL DEFAULT clock_timestamp()
);

ALTER TABLE public.xr_creator_settings ENABLE ROW LEVEL SECURITY;REVOKE ALL ON public.xr_creator_settings FROM PUBLIC,anon,authenticated;
ALTER TABLE public.xr_creator_cities ENABLE ROW LEVEL SECURITY;REVOKE ALL ON public.xr_creator_cities FROM PUBLIC,anon,authenticated;
ALTER TABLE public.xr_creator_application_details ENABLE ROW LEVEL SECURITY;REVOKE ALL ON public.xr_creator_application_details FROM PUBLIC,anon,authenticated;
ALTER TABLE public.xr_creator_verifications ENABLE ROW LEVEL SECURITY;REVOKE ALL ON public.xr_creator_verifications FROM PUBLIC,anon,authenticated;
ALTER TABLE public.xr_creator_entitlements ENABLE ROW LEVEL SECURITY;REVOKE ALL ON public.xr_creator_entitlements FROM PUBLIC,anon,authenticated;
ALTER TABLE public.xr_creator_activity ENABLE ROW LEVEL SECURITY;REVOKE ALL ON public.xr_creator_activity FROM PUBLIC,anon,authenticated;
CREATE OR REPLACE FUNCTION public.xr_creator_admin() RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path TO pg_catalog,public AS $fn$
BEGIN IF auth.uid() IS NULL OR NOT public.is_admin() THEN RAISE EXCEPTION 'Admin only.' USING ERRCODE='42501';END IF;END
$fn$;
CREATE OR REPLACE FUNCTION public.xr_creator_settings_admin(p_patch jsonb DEFAULT '{}'::jsonb) RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path TO pg_catalog,public AS $fn$
DECLARE s public.xr_creator_settings;BEGIN
 PERFORM public.xr_creator_admin();IF jsonb_typeof(p_patch)IS DISTINCT FROM 'object' THEN RETURN jsonb_build_object('ok',false,'error','Expected object.');END IF;
 IF EXISTS(SELECT 1 FROM jsonb_object_keys(p_patch)k WHERE k NOT IN('enabled','default_city_slots','application_deadline','founding_limit','drop_limit','drop_key','waitlist_boost_per_referral','bonus_enabled','bonus_starts_at','bonus_ends_at','bonus_inr','bonus_coins','bonus_funding_confirmed'))THEN RETURN jsonb_build_object('ok',false,'error','Unknown setting.');END IF;
 SELECT * INTO s FROM public.xr_creator_settings WHERE singleton FOR UPDATE;
 SELECT * INTO s FROM jsonb_populate_record(s,p_patch);
 IF length(s.drop_key)NOT BETWEEN 1 AND 80 THEN RETURN jsonb_build_object('ok',false,'error','Invalid cosmetic key.');END IF;
 UPDATE public.xr_creator_settings SET enabled=s.enabled,default_city_slots=s.default_city_slots,application_deadline=s.application_deadline,founding_limit=s.founding_limit,drop_limit=s.drop_limit,drop_key=s.drop_key,waitlist_boost_per_referral=s.waitlist_boost_per_referral,bonus_enabled=s.bonus_enabled,bonus_starts_at=s.bonus_starts_at,bonus_ends_at=s.bonus_ends_at,bonus_inr=s.bonus_inr,bonus_coins=s.bonus_coins,bonus_funding_confirmed=s.bonus_funding_confirmed WHERE singleton;
 RETURN jsonb_build_object('ok',true,'settings',to_jsonb(s)-'singleton'-'verification_counter');
 EXCEPTION WHEN check_violation OR not_null_violation OR invalid_text_representation OR invalid_datetime_format THEN RETURN jsonb_build_object('ok',false,'error','Invalid settings or window.');END
$fn$;
CREATE OR REPLACE FUNCTION public.xr_creator_city_admin(p_city text,p_capacity integer) RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path TO pg_catalog,public AS $fn$
DECLARE k text:=lower(btrim(p_city));BEGIN PERFORM public.xr_creator_admin();
 IF k IS NULL OR char_length(k)NOT BETWEEN 1 AND 60 OR k~'[[:cntrl:]<>]' OR p_capacity IS NULL OR p_capacity NOT BETWEEN 0 AND 10000 THEN RETURN jsonb_build_object('ok',false,'error','Invalid city or capacity.');END IF;
 INSERT INTO public.xr_creator_cities(city_key,city,capacity)VALUES(k,btrim(p_city),p_capacity)ON CONFLICT(city_key)DO UPDATE SET capacity=excluded.capacity;
 RETURN jsonb_build_object('ok',true);END
$fn$;
CREATE OR REPLACE FUNCTION public.xr_creator_apply(p_name text,p_city text,p_instagram text,p_player_count integer,p_public_activity boolean DEFAULT false,p_friends_visible boolean DEFAULT false) RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path TO pg_catalog,public AS $fn$
DECLARE u uuid:=public.xr_sf_actor();s public.xr_creator_settings;k text:=lower(btrim(p_city));a bigint;st text;n integer;cap integer;ig text:=regexp_replace(btrim(p_instagram),'^@','');BEGIN
 PERFORM 1 FROM public.profiles WHERE id=u FOR UPDATE;SELECT * INTO s FROM public.xr_creator_settings WHERE singleton;
 IF NOT s.enabled THEN RETURN jsonb_build_object('ok',false,'error','not_open');END IF;
 IF nullif(btrim(p_name),'')IS NULL OR char_length(btrim(p_name))>80 OR p_name~'[[:cntrl:]<>0-9@]'  OR k IS NULL OR char_length(k)NOT BETWEEN 1 AND 60 OR k~'[[:cntrl:]<>]' OR ig IS NULL OR ig!~'^[A-Za-z0-9._]{1,30}$' OR p_player_count IS NULL OR p_player_count NOT BETWEEN 0 AND 1000000 THEN RETURN jsonb_build_object('ok',false,'error','Check name, city, Instagram ID and player count.');END IF;
 IF EXISTS(SELECT 1 FROM public.creators WHERE user_id=u AND active)THEN RETURN jsonb_build_object('ok',false,'error','Already a verified creator.');END IF;
 IF EXISTS(SELECT 1 FROM public.creator_applications WHERE user_id=u AND status='pending')THEN RETURN jsonb_build_object('ok',false,'error','Application already pending.');END IF;
 IF(SELECT count(*)FROM public.creator_applications WHERE user_id=u AND created_at>now()-interval '24 hours')>=3 THEN RETURN jsonb_build_object('ok',false,'error','Try again later.');END IF;
 INSERT INTO public.xr_creator_cities(city_key,city,capacity)VALUES(k,btrim(p_city),s.default_city_slots)ON CONFLICT DO NOTHING;
 SELECT capacity INTO cap FROM public.xr_creator_cities WHERE city_key=k FOR UPDATE;
 SELECT count(*) INTO n FROM public.xr_creator_verifications v JOIN public.creators c USING(user_id)WHERE v.city_key=k AND c.active;
 st:=CASE WHEN(s.application_deadline IS NOT NULL AND statement_timestamp()>=s.application_deadline)OR n>=cap THEN 'waitlisted' ELSE 'applied' END;
 INSERT INTO public.creator_applications(user_id,channel_url,self_followers,note)VALUES(u,'https://www.instagram.com/'||ig||'/',0,NULL)RETURNING id INTO a;
 INSERT INTO public.xr_creator_application_details(application_id,user_id,display_name,city_key,instagram,player_count,stage,public_activity_opt_in,friends_applied_opt_in)VALUES(a,u,btrim(p_name),k,ig,p_player_count,st,coalesce(p_public_activity,false),coalesce(p_friends_visible,false));
 INSERT INTO public.xr_creator_activity(user_id,application_id,kind)VALUES(u,a,'applied');
 IF s.bonus_enabled AND statement_timestamp()>=s.bonus_starts_at AND statement_timestamp()<s.bonus_ends_at THEN
 INSERT INTO public.xr_creator_entitlements(user_id,kind,entitlement_key,amount_inr,coins,status)VALUES(u,'early_bonus','early_applicant',s.bonus_inr,s.bonus_coins,CASE WHEN s.bonus_funding_confirmed THEN 'pending_owner_fulfillment' ELSE 'pending_funding' END)ON CONFLICT DO NOTHING;END IF;
 RETURN jsonb_build_object('ok',true,'application_id',a,'stage',st);END
$fn$;
CREATE OR REPLACE FUNCTION public.xr_creator_verified_guard() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path TO pg_catalog,public AS $fn$
DECLARE s public.xr_creator_settings;d public.xr_creator_application_details;n bigint;cap integer;filled integer;BEGIN
 IF NOT new.active OR(TG_OP='UPDATE' AND old.active)THEN RETURN new;END IF;
 SELECT * INTO s FROM public.xr_creator_settings WHERE singleton FOR UPDATE;
 SELECT * INTO d FROM public.xr_creator_application_details WHERE user_id=new.user_id ORDER BY application_id DESC LIMIT 1;
 IF d.application_id IS NOT NULL THEN
 SELECT capacity INTO cap FROM public.xr_creator_cities WHERE city_key=d.city_key FOR UPDATE;
 SELECT count(*) INTO filled FROM public.xr_creator_verifications v JOIN public.creators c USING(user_id)WHERE v.city_key=d.city_key AND c.active AND c.user_id<>new.user_id;
 IF filled>=cap THEN RAISE EXCEPTION 'City creator slots are full.' USING ERRCODE='P1501';END IF;
 END IF;
 IF NOT EXISTS(SELECT 1 FROM public.xr_creator_verifications WHERE user_id=new.user_id)THEN
 UPDATE public.xr_creator_settings SET verification_counter=verification_counter+1 WHERE singleton RETURNING verification_counter INTO n;
 INSERT INTO public.xr_creator_verifications(user_id,ordinal,city_key)VALUES(new.user_id,n,d.city_key);
 IF n<=s.founding_limit THEN INSERT INTO public.xr_creator_entitlements(user_id,kind,entitlement_key,status)VALUES(new.user_id,'founding_badge','founding_creator','granted')ON CONFLICT DO NOTHING;END IF;
 IF n<=s.drop_limit THEN INSERT INTO public.xr_creator_entitlements(user_id,kind,entitlement_key,status)VALUES(new.user_id,'limited_drop',s.drop_key,'pending_owner_fulfillment')ON CONFLICT DO NOTHING;END IF;
 END IF;
 IF d.application_id IS NOT NULL THEN
 UPDATE public.xr_creator_application_details SET stage='verified',updated_at=now()WHERE application_id=d.application_id;
 INSERT INTO public.xr_creator_activity(user_id,application_id,kind)VALUES(new.user_id,d.application_id,'slot_taken');
 END IF;
 RETURN new;END
$fn$;
CREATE TRIGGER xr_creator_verified AFTER INSERT OR UPDATE OF active ON public.creators FOR EACH ROW EXECUTE FUNCTION public.xr_creator_verified_guard();
CREATE OR REPLACE FUNCTION public.xr_creator_review(p_application bigint,p_action text) RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path TO pg_catalog,public AS $fn$
DECLARE a public.creator_applications;d public.xr_creator_application_details;BEGIN
 PERFORM public.xr_creator_admin();PERFORM 1 FROM public.xr_creator_settings WHERE singleton FOR UPDATE;
 SELECT * INTO a FROM public.creator_applications WHERE id=p_application FOR UPDATE;
 IF NOT FOUND THEN RETURN jsonb_build_object('ok',false,'error','Application not found.');END IF;
 IF a.status<>'pending' THEN RETURN jsonb_build_object('ok',true,'already_reviewed',true,'status',a.status);END IF;
 SELECT * INTO d FROM public.xr_creator_application_details WHERE application_id=a.id;
 IF p_action='under_review' THEN UPDATE public.xr_creator_application_details SET stage='under_review',updated_at=now()WHERE application_id=a.id;RETURN jsonb_build_object('ok',true,'stage','under_review');END IF;
 IF p_action NOT IN('verify','reject')THEN RETURN jsonb_build_object('ok',false,'error','Unknown action.');END IF;
 IF p_action='reject' THEN UPDATE public.creator_applications SET status='rejected',reviewed_at=now()WHERE id=a.id;UPDATE public.xr_creator_application_details SET stage='rejected',updated_at=now()WHERE application_id=a.id;RETURN jsonb_build_object('ok',true,'stage','rejected');END IF;
 INSERT INTO public.creators(user_id,channel_url,self_followers,tier,approved_by)VALUES(a.user_id,a.channel_url,a.self_followers,public.creator_tier(a.self_followers),auth.uid())ON CONFLICT(user_id)DO UPDATE SET active=true,approved_by=auth.uid(),approved_at=now(),channel_url=excluded.channel_url;
 UPDATE public.creator_applications SET status='approved',reviewed_at=now()WHERE id=a.id;
 RETURN jsonb_build_object('ok',true,'stage','verified');
 EXCEPTION WHEN SQLSTATE 'P1501' THEN RETURN jsonb_build_object('ok',false,'error','city_full');END
$fn$;
CREATE OR REPLACE FUNCTION public.admin_creator_review(p_id bigint,p_approve boolean) RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path TO pg_catalog,public AS $fn$
DECLARE r jsonb;BEGIN r:=public.xr_creator_review(p_id,CASE WHEN p_approve THEN 'verify' ELSE 'reject' END);IF coalesce((r->>'ok')::boolean,false)THEN r:=r||jsonb_build_object('approved',p_approve);END IF;RETURN r;END
$fn$;
CREATE OR REPLACE FUNCTION public.xr_creator_admin_queue() RETURNS jsonb
LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path TO pg_catalog,public AS $fn$
BEGIN PERFORM public.xr_creator_admin();RETURN jsonb_build_object('ok',true,'applications',coalesce((SELECT jsonb_agg(jsonb_build_object('id',a.id,'name',d.display_name,'city',c.city,'instagram',d.instagram,'player_count',d.player_count,'player_count_source','self_reported','stage',coalesce(d.stage,a.status),'created_at',a.created_at)ORDER BY a.created_at)FROM public.creator_applications a LEFT JOIN public.xr_creator_application_details d ON d.application_id=a.id LEFT JOIN public.xr_creator_cities c ON c.city_key=d.city_key WHERE a.status='pending'),'[]'::jsonb));END
$fn$;
CREATE OR REPLACE FUNCTION public.xr_creator_my_status() RETURNS jsonb
LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path TO pg_catalog,public AS $fn$
DECLARE u uuid:=public.xr_sf_actor();a public.creator_applications;d public.xr_creator_application_details;r bigint;refs bigint;s public.xr_creator_settings;BEGIN
 SELECT * INTO s FROM public.xr_creator_settings WHERE singleton;
 SELECT * INTO a FROM public.creator_applications WHERE user_id=u ORDER BY id DESC LIMIT 1;
 SELECT * INTO d FROM public.xr_creator_application_details WHERE application_id=a.id;
 SELECT count(DISTINCT rf.referred_id)INTO refs FROM public.referrals rf JOIN public.creator_applications x ON x.user_id=rf.referred_id JOIN public.profiles p ON p.id=rf.referred_id WHERE rf.referrer_id=u AND rf.referred_id<>u AND rf.created_at>=a.created_at AND x.created_at>=a.created_at AND NOT coalesce(p.banned,false);
 IF d.stage='waitlisted' THEN
 WITH q AS(SELECT x.user_id,x.application_id,x.created_at,count(DISTINCT rf.referred_id)*s.waitlist_boost_per_referral boost FROM(SELECT d2.user_id,d2.application_id,a2.created_at FROM public.xr_creator_application_details d2 JOIN public.creator_applications a2 ON a2.id=d2.application_id WHERE d2.stage='waitlisted' AND a2.status='pending' AND d2.city_key=d.city_key)x LEFT JOIN public.referrals rf ON rf.referrer_id=x.user_id AND rf.created_at>=x.created_at AND EXISTS(SELECT 1 FROM public.creator_applications y JOIN public.profiles p ON p.id=y.user_id WHERE y.user_id=rf.referred_id AND y.created_at>=x.created_at AND NOT coalesce(p.banned,false))GROUP BY x.user_id,x.application_id,x.created_at),ranked AS(SELECT user_id,row_number()OVER(ORDER BY boost DESC,created_at,application_id)rank FROM q)SELECT rank INTO r FROM ranked WHERE user_id=u;END IF;
 RETURN jsonb_build_object('ok',true,'is_creator',EXISTS(SELECT 1 FROM public.creators WHERE user_id=u AND active),'application_id',a.id,'stage',CASE WHEN a.status='revoked' THEN 'revoked' ELSE coalesce(d.stage,a.status)END,'name',d.display_name,'city',(SELECT city FROM public.xr_creator_cities WHERE city_key=d.city_key),'instagram',d.instagram,'player_count',d.player_count,'public_activity_opt_in',d.public_activity_opt_in,'friends_applied_opt_in',d.friends_applied_opt_in,'waitlist_rank',r,'qualified_referrals',refs,'boost_points',refs*s.waitlist_boost_per_referral,'entitlements',coalesce((SELECT jsonb_agg(jsonb_build_object('kind',kind,'key',entitlement_key,'status',status,'amount_inr',amount_inr,'coins',coins))FROM public.xr_creator_entitlements WHERE user_id=u),'[]'::jsonb));END
$fn$;
CREATE OR REPLACE FUNCTION public.xr_creator_sharing(p_public_activity boolean,p_friends_visible boolean) RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path TO pg_catalog,public AS $fn$
DECLARE u uuid:=public.xr_sf_actor();BEGIN UPDATE public.xr_creator_application_details SET public_activity_opt_in=coalesce(p_public_activity,false),friends_applied_opt_in=coalesce(p_friends_visible,false),updated_at=now()WHERE user_id=u;RETURN jsonb_build_object('ok',true);END
$fn$;
CREATE OR REPLACE FUNCTION public.xr_creator_city_status(p_city text) RETURNS jsonb
LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path TO pg_catalog,public AS $fn$
DECLARE u uuid:=public.xr_sf_actor();k text:=lower(btrim(p_city));s public.xr_creator_settings;cap integer;n integer;BEGIN
 SELECT * INTO s FROM public.xr_creator_settings WHERE singleton;SELECT capacity INTO cap FROM public.xr_creator_cities WHERE city_key=k;cap:=coalesce(cap,s.default_city_slots);
 SELECT count(*)INTO n FROM public.xr_creator_verifications v JOIN public.creators c USING(user_id)JOIN public.profiles p ON p.id=c.user_id WHERE city_key=k AND c.active;
 RETURN jsonb_build_object('ok',true,'city',coalesce((SELECT city FROM public.xr_creator_cities WHERE city_key=k),btrim(p_city)),'capacity',cap,'filled',n,'remaining',greatest(cap-n,0),'applications_open',s.enabled AND(s.application_deadline IS NULL OR statement_timestamp()<s.application_deadline),'deadline',s.application_deadline,'waitlist_only',(s.application_deadline IS NOT NULL AND statement_timestamp()>=s.application_deadline)OR n>=cap,'founding_limit',s.founding_limit,'limited_drop_limit',s.drop_limit,'bonus',CASE WHEN s.enabled AND s.bonus_enabled AND s.bonus_funding_confirmed AND statement_timestamp()>=s.bonus_starts_at AND statement_timestamp()<s.bonus_ends_at THEN jsonb_build_object('amount_inr',s.bonus_inr,'coins',s.bonus_coins,'ends_at',s.bonus_ends_at,'status','pending_owner_fulfillment')ELSE NULL END);END
$fn$;
CREATE OR REPLACE FUNCTION public.xr_creator_activity_list(p_limit integer DEFAULT 20) RETURNS jsonb
LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path TO pg_catalog,public AS $fn$
DECLARE u uuid:=public.xr_sf_actor();BEGIN RETURN jsonb_build_object('ok',true,'events',coalesce((SELECT jsonb_agg(jsonb_build_object('kind',q.kind,'first_name',split_part(q.display_name,' ',1),'city',q.city,'created_at',q.created_at)ORDER BY q.created_at DESC,q.id DESC)FROM(SELECT e.id,e.kind,e.created_at,d.display_name,c.city FROM public.xr_creator_activity e JOIN public.xr_creator_application_details d ON d.application_id=e.application_id JOIN public.xr_creator_cities c ON c.city_key=d.city_key JOIN public.profiles p ON p.id=d.user_id WHERE d.public_activity_opt_in AND NOT coalesce(p.banned,false)AND NOT public.is_blocked(u,p.id)ORDER BY e.created_at DESC,e.id DESC LIMIT greatest(1,least(coalesce(p_limit,20),50)))q),'[]'::jsonb));END
$fn$;
CREATE OR REPLACE FUNCTION public.xr_creator_friends_applied() RETURNS jsonb
LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path TO pg_catalog,public AS $fn$
DECLARE u uuid:=public.xr_sf_actor();BEGIN RETURN jsonb_build_object('ok',true,'friends',coalesce((SELECT jsonb_agg(jsonb_build_object('id',q.id,'first_name',split_part(q.display_name,' ',1),'city',q.city))FROM(SELECT DISTINCT p.id,d.display_name,c.city FROM public.friends f JOIN public.xr_creator_application_details d ON d.user_id=f.friend_id JOIN public.creator_applications a ON a.id=d.application_id JOIN public.profiles p ON p.id=d.user_id JOIN public.xr_creator_cities c ON c.city_key=d.city_key WHERE f.user_id=u AND EXISTS(SELECT 1 FROM public.friends rev WHERE rev.user_id=f.friend_id AND rev.friend_id=u)AND d.friends_applied_opt_in AND a.status IN('pending','approved')AND NOT coalesce(p.banned,false)AND NOT public.is_blocked(u,p.id)ORDER BY p.id LIMIT 50)q),'[]'::jsonb));END
$fn$;
CREATE OR REPLACE FUNCTION public.xr_creator_tournament_seats(p_creator uuid DEFAULT NULL) RETURNS jsonb
LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path TO pg_catalog,public AS $fn$
DECLARE u uuid:=public.xr_sf_actor();BEGIN RETURN jsonb_build_object('ok',true,'tournaments',coalesce((SELECT jsonb_agg(jsonb_build_object('id',q.id,'creator_id',q.host,'title',q.title,'status',q.status,'starts_at',q.starts_at,'capacity',q.slots,'registered',q.joined,'remaining',greatest(q.slots-q.joined,0),'entry','free'))FROM(SELECT e.id,e.host,e.title,e.status,e.starts_at,e.slots,(SELECT count(*)FROM public.creator_event_players x WHERE x.event_id=e.id AND x.status='joined')joined FROM public.creator_events e JOIN public.creators c ON c.user_id=e.host JOIN public.profiles p ON p.id=e.host WHERE e.status IN('scheduled','live')AND c.active AND NOT coalesce(p.banned,false)AND NOT public.is_blocked(u,e.host)AND(p_creator IS NULL OR e.host=p_creator)ORDER BY e.starts_at LIMIT 50)q),'[]'::jsonb));END
$fn$;
CREATE OR REPLACE FUNCTION public.xr_creator_city_leaderboard(p_city text DEFAULT NULL) RETURNS jsonb
LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path TO pg_catalog,public AS $fn$
DECLARE u uuid:=public.xr_sf_actor();start_week timestamptz:=date_trunc('week',statement_timestamp()AT TIME ZONE 'Asia/Kolkata')AT TIME ZONE 'Asia/Kolkata';rows jsonb;winner jsonb;BEGIN
 SELECT coalesce(jsonb_agg(q.row ORDER BY q.joins DESC,q.username),'[]'::jsonb)INTO rows FROM(
 SELECT p.username,(SELECT count(*)FROM public.creator_event_players ep JOIN public.creator_events e ON e.id=ep.event_id WHERE e.host=c.user_id AND e.status<>'cancelled' AND ep.status='joined')joins,
 jsonb_build_object('id',c.user_id,'username',p.username,'name',coalesce(p.ign,p.username),'avatar_url',p.avatar_url,'city',ct.city,'followers',(SELECT count(*)FROM public.follows WHERE followee_id=c.user_id),'event_joins',(SELECT count(*)FROM public.creator_event_players ep JOIN public.creator_events e ON e.id=ep.event_id WHERE e.host=c.user_id AND e.status<>'cancelled' AND ep.status='joined'),'founding',EXISTS(SELECT 1 FROM public.xr_creator_entitlements en WHERE en.user_id=c.user_id AND en.kind='founding_badge'))row
 FROM public.creators c JOIN public.profiles p ON p.id=c.user_id LEFT JOIN public.xr_creator_verifications v ON v.user_id=c.user_id LEFT JOIN public.xr_creator_cities ct ON ct.city_key=v.city_key
 WHERE c.active AND NOT coalesce(p.banned,false)AND NOT public.is_blocked(u,p.id)AND(p_city IS NULL OR v.city_key=lower(btrim(p_city)))
 ORDER BY joins DESC,p.username LIMIT 50)q;
 SELECT jsonb_build_object('id',c.user_id,'name',coalesce(p.ign,p.username),'avatar_url',p.avatar_url,'city',ct.city,'event_joins',count(*))INTO winner FROM public.creators c JOIN public.profiles p ON p.id=c.user_id JOIN public.creator_events e ON e.host=c.user_id JOIN public.creator_event_players ep ON ep.event_id=e.id LEFT JOIN public.xr_creator_verifications v ON v.user_id=c.user_id LEFT JOIN public.xr_creator_cities ct ON ct.city_key=v.city_key WHERE c.active AND NOT coalesce(p.banned,false)AND NOT public.is_blocked(u,p.id)AND e.status<>'cancelled'AND ep.status='joined'AND ep.joined_at>=start_week AND ep.joined_at<start_week+interval '7 days' AND(p_city IS NULL OR v.city_key=lower(btrim(p_city)))GROUP BY c.user_id,p.ign,p.username,p.avatar_url,ct.city ORDER BY count(*)DESC,c.user_id LIMIT 1;
 RETURN jsonb_build_object('ok',true,'creators',rows,'metric','current_joined_event_entries','creator_of_week',winner,'week_starts_at',start_week,'week_timezone','Asia/Kolkata');END
$fn$;
CREATE OR REPLACE FUNCTION public.xr_creator_dashboard() RETURNS jsonb
LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path TO pg_catalog,public AS $fn$
DECLARE u uuid:=public.xr_sf_actor();BEGIN
 IF NOT EXISTS(SELECT 1 FROM public.creators WHERE user_id=u AND active)THEN RETURN jsonb_build_object('ok',false,'error','Creators only.');END IF;
 RETURN jsonb_build_object('ok',true,'followers',(SELECT count(*)FROM public.follows WHERE followee_id=u),'events_hosted',(SELECT count(*)FROM public.creator_events WHERE host=u),'joined_entries',(SELECT count(*)FROM public.creator_event_players ep JOIN public.creator_events e ON e.id=ep.event_id WHERE e.host=u AND e.status<>'cancelled' AND ep.status='joined'),'unique_players',(SELECT count(DISTINCT ep.user_id)FROM public.creator_event_players ep JOIN public.creator_events e ON e.id=ep.event_id WHERE e.host=u AND e.status<>'cancelled' AND ep.status='joined'),'coins_earned',NULL,'coins_source','No creator commission ledger configured.','tournaments',public.xr_creator_tournament_seats(u)->'tournaments','status',public.xr_creator_my_status());END
$fn$;
REVOKE ALL ON FUNCTION public.xr_creator_admin() FROM PUBLIC,anon,authenticated;
REVOKE ALL ON FUNCTION public.xr_creator_settings_admin(jsonb) FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.xr_creator_settings_admin(jsonb) TO authenticated;
REVOKE ALL ON FUNCTION public.xr_creator_city_admin(text,integer) FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.xr_creator_city_admin(text,integer) TO authenticated;
REVOKE ALL ON FUNCTION public.xr_creator_apply(text,text,text,integer,boolean,boolean) FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.xr_creator_apply(text,text,text,integer,boolean,boolean) TO authenticated;
REVOKE ALL ON FUNCTION public.xr_creator_verified_guard() FROM PUBLIC,anon,authenticated;
REVOKE ALL ON FUNCTION public.xr_creator_review(bigint,text) FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.xr_creator_review(bigint,text) TO authenticated;
REVOKE ALL ON FUNCTION public.xr_creator_admin_queue() FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.xr_creator_admin_queue() TO authenticated;
REVOKE ALL ON FUNCTION public.xr_creator_my_status() FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.xr_creator_my_status() TO authenticated;
REVOKE ALL ON FUNCTION public.xr_creator_sharing(boolean,boolean) FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.xr_creator_sharing(boolean,boolean) TO authenticated;
REVOKE ALL ON FUNCTION public.xr_creator_city_status(text) FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.xr_creator_city_status(text) TO authenticated;
REVOKE ALL ON FUNCTION public.xr_creator_activity_list(integer) FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.xr_creator_activity_list(integer) TO authenticated;
REVOKE ALL ON FUNCTION public.xr_creator_friends_applied() FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.xr_creator_friends_applied() TO authenticated;
REVOKE ALL ON FUNCTION public.xr_creator_tournament_seats(uuid) FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.xr_creator_tournament_seats(uuid) TO authenticated;
REVOKE ALL ON FUNCTION public.xr_creator_city_leaderboard(text) FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.xr_creator_city_leaderboard(text) TO authenticated;
REVOKE ALL ON FUNCTION public.xr_creator_dashboard() FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.xr_creator_dashboard() TO authenticated;
NOTIFY pgrst,'reload schema';
COMMIT;