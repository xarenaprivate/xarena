import {normalizeEmail,validCode,validPassword,makeCode,codeDigest} from './security.mjs';
const uuid=/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
// Dependencies must be server-only. No implementation may log RPC bodies or raw errors.
export function makeHandler({rpc,adminCreate,adminReset,deliver,pepper,enabled=false,testAllowlist="",trustedIp=()=>null}) {
 const response=(body,status=200)=>new Response(JSON.stringify(body),{status,headers:{'Content-Type':'application/json','Cache-Control':'no-store','Access-Control-Allow-Origin':'*','Access-Control-Allow-Headers':'authorization,apikey,content-type','Access-Control-Allow-Methods':'POST,OPTIONS'}});
 const fingerprint=async(v)=>{
  const key=await crypto.subtle.importKey('raw',new TextEncoder().encode(pepper),{name:'HMAC',hash:'SHA-256'},false,['sign']);
  return Array.from(new Uint8Array(await crypto.subtle.sign('HMAC',key,new TextEncoder().encode(v))),x=>x.toString(16).padStart(2,'0')).join('');
 };
 return async req=>{
  if(req.method==='OPTIONS')return response({});
  if(req.method!=='POST')return response({ok:false,error:'POST required.'},405);
  if(!enabled)return response({ok:false,error:'Email signup is not available yet. Use phone signup.'},503);
  try{
   if(Number(req.headers.get('content-length')||0)>4096)return response({ok:false,error:'Invalid request.'},400);
   const raw=await req.text();if(new TextEncoder().encode(raw).length>4096)return response({ok:false,error:'Invalid request.'},400);
   const b=JSON.parse(raw);
   if(['signup_start','signup_resend','recovery_start','recovery_resend'].includes(b.action)){
    const email=normalizeEmail(b.email),id=crypto.randomUUID(),code=makeCode();
    if(testAllowlist && email!==testAllowlist)return response({ok:false,error:'Email signup is not available yet. Use phone signup.'},503);
    const device=typeof b.device_id==='string' && b.device_id.length<=128?b.device_id:'';
    // Unknown IP deliberately shares a single restricted bucket. Never trust an
    // arbitrary forwarding header. Source-specific IP extraction is a deployment gate.
    const ip=trustedIp(req)||'unknown-shared-network';
    const allocated=await rpc('xr_email_start',{p_id:id,p_purpose:b.action.startsWith('recovery')?'recovery':'signup',p_email:email,p_email_hash:await fingerprint('email:'+email),p_ip_hash:await fingerprint('ip:'+ip),p_device_hash:await fingerprint('device:'+(device||'unknown-shared-device')),p_code_hash:await codeDigest(pepper,id,email,code)});
    if(!allocated.ok)return response({ok:false,error:'Please wait before requesting another code.'},429);
    let sent=false;try{await deliver({email,code});sent=true;}catch{}
    const marked=await rpc('xr_email_delivery',{p_id:id,p_sent:sent});
    if(!sent||!marked.ok)return response({ok:false,error:'Code delivery could not be confirmed. Please try again later.'},503);
    return response({ok:true,challenge_id:id,expires_in:600,resend_after:60,message:'Check your email for the code.'});
   }
   if(['signup_verify','recovery_verify'].includes(b.action)){
    if(!uuid.test(b.challenge_id||'')||!validCode(b.code)||!validPassword(b.password))return response({ok:false,error:'Check the code and password (8-72 bytes).'},400);
    const row=await rpc('xr_email_lookup',{p_id:b.challenge_id});
    if(testAllowlist && row.email!==testAllowlist)return response({ok:false,error:'Email signup is not available yet. Use phone signup.'},503);
    if(!row.email)return response({ok:false,error:'Code is invalid, expired or already used.'},400);
    const operation=crypto.randomUUID();
    const reserved=await rpc('xr_email_reserve',{p_id:b.challenge_id,p_purpose:b.action==='recovery_verify'?'recovery':'signup',p_code_hash:await codeDigest(pepper,b.challenge_id,row.email,b.code),p_operation:operation});
    if(!reserved.ok)return response({ok:false,error:'Code is invalid, expired or already used.'},400);
    if(b.action==='recovery_verify'){
     if(!reserved.user_id)return response({ok:false,error:'Recovery could not be completed.'},400);
     try{
      const reset=await adminReset(reserved.user_id,b.password,reserved.email,operation);
      if(!reset.ok)throw Error('Reset failed');
      const finish=await rpc('xr_email_finish',{p_id:b.challenge_id,p_operation:operation,p_user:reserved.user_id,p_state:'recovered'});
      if(!finish.ok)throw Error('Reset result ambiguous');
      return response({ok:true,message:'Password changed. Sign in with your new password. Existing access tokens may last until expiry.'});
     }catch{
      try{await rpc('xr_email_finish',{p_id:b.challenge_id,p_operation:operation,p_user:reserved.user_id,p_state:'ambiguous'});}catch{}
      return response({ok:false,error:'Recovery result could not be confirmed. Try signing in with your new password before starting again.'},503);
     }
    }
    // No lookup/merge/password-reset of an existing email, regardless of ownership.
    let result;
    try{result=await adminCreate({email:reserved.email,password:b.password,email_confirm:true,app_metadata:{xarena_email_operation:operation}});}catch{
     try{await rpc('xr_email_finish',{p_id:b.challenge_id,p_operation:operation,p_user:null,p_state:'ambiguous'});}catch{}
     return response({ok:false,error:'Account result could not be confirmed. Try signing in before starting again.'},503);
    }
    if(!result.ok){
     await rpc('xr_email_finish',{p_id:b.challenge_id,p_operation:operation,p_user:null,p_state:'auth_failed'});
     return response({ok:false,error:'Account could not be created. If you already have an account, sign in instead.'},400);
    }
    const finished=await rpc('xr_email_finish',{p_id:b.challenge_id,p_operation:operation,p_user:result.user_id,p_state:'complete'});
    if(!finished.ok)return response({ok:false,error:'Account result needs checking. Try signing in; do not repeat signup.'},503);
    return response({ok:true,message:'Email checked and account created. Sign in with your email and password.'});
   }
   return response({ok:false,error:'Invalid request.'},400);
  }catch{return response({ok:false,error:'Request could not be confirmed. Please try again later.'},503);}
 };
}
