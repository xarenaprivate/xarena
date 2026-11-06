const url=Deno.env.get('SUPABASE_URL')||'',service=Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')||'';
const headers={'Access-Control-Allow-Origin':'*','Access-Control-Allow-Headers':'authorization,apikey,content-type','Access-Control-Allow-Methods':'POST,OPTIONS','Content-Type':'application/json','Cache-Control':'no-store'};
function response(body:unknown,status=200){return new Response(JSON.stringify(body),{status,headers});}
async function digest(s:string){return Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',new TextEncoder().encode(s)))).map(x=>x.toString(16).padStart(2,'0')).join('');}
async function call(path:string,body:unknown){const r=await fetch(url+path,{method:'POST',headers:{apikey:service,Authorization:'Bearer '+service,'Content-Type':'application/json'},body:JSON.stringify(body),signal:AbortSignal.timeout(5000)});if(!r.ok)throw Error('service');return r.json();}
Deno.serve(async(req)=>{
if(req.method==='OPTIONS')return response({});if(req.method!=='POST')return response({ok:false,error:'POST required.'},405);
try{
if(Number(req.headers.get('content-length')||0)>4096)return response({ok:false,error:'Invalid request.'},400);
const raw=await req.text();if(raw.length>4096)return response({ok:false,error:"Invalid request."},400);const b=JSON.parse(raw);
if(b.action==='enroll'){
 const token=req.headers.get('Authorization')||'';if(!token.startsWith('Bearer '))return response({ok:false,error:'Log in first.'},401);
 const user=await fetch(url+'/auth/v1/user',{headers:{apikey:service,Authorization:token},signal:AbortSignal.timeout(5000)});if(!user.ok)return response({ok:false,error:'Log in first.'},401);const u=await user.json();
 if(typeof b.current_password!=='string'||b.current_password.length>256)return response({ok:false,error:'Confirm your current password.'},400);
 // Fresh password proof prevents an abandoned unlocked session from enrolling a takeover key.
 const proof=await fetch(url+'/auth/v1/token?grant_type=password',{method:'POST',headers:{apikey:service,'Content-Type':'application/json'},body:JSON.stringify({email:u.email,password:b.current_password}),signal:AbortSignal.timeout(5000)});
 if(!proof.ok)return response({ok:false,error:'Password confirmation failed.'},403);
 const ps=await proof.json();if(ps.user?.id!==u.id)return response({ok:false,error:'Password confirmation failed.'},403);
 // Terminate only the short-lived proof session, not the player's current session.
 await fetch(url+'/auth/v1/logout?scope=local',{method:'POST',headers:{apikey:service,Authorization:'Bearer '+ps.access_token},signal:AbortSignal.timeout(5000)});
 const key=Array.from(crypto.getRandomValues(new Uint8Array(32))).map(x=>x.toString(16).padStart(2,'0')).join('');
 const saved=await call('/rest/v1/rpc/recovery_enroll',{p_user:u.id,p_hash:await digest(key)});
 if(!saved.ok)return response({ok:false,error:'Recovery keys are available only for eligible phone-only player accounts.'},403);
 return response({ok:true,recovery_key:key,warning:'Save privately now. This key is shown once and replaces any older key. Anyone with it can reset your password. Never share it in chat.'});
}
if(b.action==='redeem'){
 const key=String(b.recovery_key||'').trim().toLowerCase();const password=b.new_password;
 if(!/^[a-f0-9]{64}$/.test(key)||typeof password!=='string'||password.length<8||password.length>128)return response({ok:false,error:'Check your recovery key and password (8-128 characters).'},400);
 const ip=(req.headers.get('x-forwarded-for')||'unknown').split(',')[0].trim();
 const reserved=await call('/rest/v1/rpc/recovery_reserve',{p_hash:await digest(key),p_bucket:await digest('recovery-ip:'+ip)});
 if(!reserved.ok)return response({ok:false,error:'Recovery could not be completed. Check the key or wait 15 minutes.'},400);
 // Reserve consumes the key atomically before Auth work. A network-ambiguous result never restores it.
 // Fail closed rather than permit duplicate concurrent password changes.
 const changed=await fetch(url+'/auth/v1/admin/users/'+encodeURIComponent(reserved.user_id),{method:'PUT',headers:{apikey:service,Authorization:'Bearer '+service,'Content-Type':'application/json'},body:JSON.stringify({password}),signal:AbortSignal.timeout(7000)});
 if(!changed.ok)return response({ok:false,error:'Recovery was not completed. The one-time key is now used. If you can still log in, create a new key; otherwise contact the owner for identity-verified assistance.'},503);
 return response({ok:true,message:'Password changed. Log in with your new password and create a fresh recovery key. Other refresh sessions are revoked; already-issued access tokens may last until expiry.'});
}
return response({ok:false,error:'Invalid request.'},400);
}catch{return response({ok:false,error:'Recovery request could not be confirmed. If you submitted a key, do not share it; try logging in with your new password before further recovery.'},503);}
});
