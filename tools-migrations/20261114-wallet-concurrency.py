import json,urllib.request,time,concurrent.futures,uuid,re,os
from pathlib import Path
T=os.environ['SUPABASE_ACCESS_TOKEN'];url='https://api.supabase.com/v1/projects/oskccuajgbyabogbvedj/database/query'
def q(s):
 req=urllib.request.Request(url,data=json.dumps({'query':s,'read_only':False}).encode(),headers={'Authorization':'Bearer '+T,'Content-Type':'application/json'})
 try:return True,json.load(urllib.request.urlopen(req,timeout=30))
 except urllib.error.HTTPError as e:return False,e.read().decode()
schema='xr_wallet_trial_'+uuid.uuid4().hex[:12];u=str(uuid.uuid4());p=Path(__file__).resolve().parent;source=(p/'core.sql').read_text();fn=source[source.index('CREATE FUNCTION public.xr_wallet_ledger_insert()'):source.index('REVOKE ALL ON FUNCTION public.xr_wallet_ledger_insert()')];fn=fn.replace('public.xr_wallet_accounts',schema+'.xr_wallet_accounts').replace('public.xr_wallet_allocations',schema+'.xr_wallet_allocations').replace('public.points_ledger',schema+'.points_ledger').replace('public.xr_wallet_credit_source',schema+'.xr_wallet_credit_source').replace('public.xr_wallet_ledger_insert',schema+'.xr_wallet_ledger_insert')
setup=f'''CREATE SCHEMA {schema};CREATE TABLE {schema}.points_ledger(id bigint PRIMARY KEY,user_id uuid,delta int,kind text,meta jsonb DEFAULT '{{}}',created_at timestamptz DEFAULT now());CREATE TABLE {schema}.xr_wallet_accounts(user_id uuid PRIMARY KEY,earned bigint DEFAULT 0 CHECK(earned>=0),granted bigint DEFAULT 0 CHECK(granted>=0),unverified bigint DEFAULT 0 CHECK(unverified>=0),updated_at timestamptz DEFAULT now());CREATE TABLE {schema}.xr_wallet_allocations(ledger_id bigint PRIMARY KEY,user_id uuid,earned_delta bigint,granted_delta bigint,unverified_delta bigint,reason text,refund_of bigint);CREATE FUNCTION {schema}.xr_wallet_credit_source(p {schema}.points_ledger)RETURNS text LANGUAGE sql AS $$SELECT 'unverified'::text$$;{fn}CREATE TRIGGER guard BEFORE INSERT ON {schema}.points_ledger FOR EACH ROW EXECUTE FUNCTION {schema}.xr_wallet_ledger_insert();INSERT INTO {schema}.points_ledger(id,user_id,delta,kind)VALUES(1,'{u}',10,'fixture');'''
ok,r=q(setup);assert ok,r
try:
 def debit(i):return q(f"BEGIN;INSERT INTO {schema}.points_ledger(id,user_id,delta,kind)VALUES({i},'{u}',-7,'debit');SELECT pg_sleep(1);COMMIT;")
 with concurrent.futures.ThreadPoolExecutor(max_workers=2)as ex:r=list(ex.map(debit,[2,3]))
 ok,bal=q(f'SELECT earned+granted+unverified balance FROM {schema}.xr_wallet_accounts;');print('parallel_debit_results',[(v[0],str(v[1])[:250])for v in r],'balance',bal);assert sorted(v[0]for v in r)==[False,True];assert bal[0]['balance']==3
 print('PASS: cloned production insert-guard implementation, two concurrent7APdebits from10AP, exactly1commit and balance3; isolated scratch schema, no production ledger entries')
finally:print('cleanup',q(f'DROP SCHEMA {schema} CASCADE;'))
