import {makeHandler} from './handler.mjs';
import {smtpSession} from './smtp.ts';
const url=Deno.env.get('SUPABASE_URL')||'',key=Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')||'';
const pepper=Deno.env.get('XR_EMAIL_PEPPER')||'',password=Deno.env.get('XR_GMAIL_APP_PASSWORD')||'';
async function post(path:string,body:unknown){const r=await fetch(url+path,{method:'POST',headers:{apikey:key,Authorization:'Bearer '+key,'Content-Type':'application/json'},body:JSON.stringify(body),signal:AbortSignal.timeout(7000)});if(!r.ok)throw Error('Service request failed');return r.json();}
const handler=makeHandler({
 enabled:Deno.env.get('XR_EMAIL_ENABLED')==='true' && pepper.length>=32 && !!password,
 pepper,
 testAllowlist:Deno.env.get('XR_EMAIL_TEST_ALLOWLIST')||'',
 rpc:(name:string,body:unknown)=>post('/rest/v1/rpc/'+name,body),
 adminCreate:async(body:unknown)=>{
  const r=await fetch(url+'/auth/v1/admin/users',{method:'POST',headers:{apikey:key,Authorization:'Bearer '+key,'Content-Type':'application/json'},body:JSON.stringify(body),signal:AbortSignal.timeout(7000)});
  // Only definitive client errors are non-ambiguous. 5xx, malformed success and
  // timeouts need reconciliation, never a repeated create/reset.
  if(!r.ok){if(r.status>=400&&r.status<500)return{ok:false};throw Error('Ambiguous account create');}
  const d=await r.json();if(typeof d.id!=='string')throw Error('Ambiguous account create');return{ok:true,user_id:d.id};
 },
 adminReset:async(userId:string,password:string,email:string)=>{
  const current=await fetch(url+'/auth/v1/admin/users/'+encodeURIComponent(userId),{headers:{apikey:key,Authorization:'Bearer '+key},signal:AbortSignal.timeout(7000)});
  if(!current.ok)return{ok:false};const u=await current.json();if(u.id!==userId||u.email?.toLowerCase()!==email)return{ok:false};
  const updated=await fetch(url+'/auth/v1/admin/users/'+encodeURIComponent(userId),{method:'PUT',headers:{apikey:key,Authorization:'Bearer '+key,'Content-Type':'application/json'},body:JSON.stringify({password}),signal:AbortSignal.timeout(7000)});
  if(!updated.ok)return{ok:false};return{ok:true};
 },
 deliver:(mail:{email:string,code:string})=>smtpSession(password,mail),
 // Unknown is one shared restricted bucket. Configure ONLY after trusted
 // upstream header semantics are verified; client headers cannot choose IP.
 trustedIp:()=>null,
});
Deno.serve(handler);
