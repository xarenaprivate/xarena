/* Luna Cast (v7.16) - Luna commentates REAL match data on the Live page.
   Hidden unless the luna-chat backend answers AND there is real match data. */
(function(){
'use strict';
var LUNA_URL='https://oskccuajgbyabogbvedj.supabase.co/functions/v1/luna-chat';
var ANON_KEY='sb_publishable_RltZjW7bXLT9DssLJVHkDA_cuHlcJo7';
function esc(s){return String(s==null?'':s).replace(/[&<>"']/g,function(c){return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c];});}
function boot(){
 if(!window.xrApi)return;
 var api=window.xrApi;
 fetch(LUNA_URL,{method:'POST',headers:{'Content-Type':'application/json','apikey':ANON_KEY,'Authorization':'Bearer '+ANON_KEY},body:JSON.stringify({session_id:'cast-probe',ping:true})}).then(function(r){
  if(r.status===404||r.status===503)return;
  api.get('matches_public','?order=starts_at.desc&limit=8&select=title,starts_at,status').then(function(matches){
   if(!Array.isArray(matches)||!matches.length)return;
   var box=document.createElement('div');box.className='card';box.style.marginTop='16px';box.id='luna-cast';
   box.innerHTML='<div style="display:flex;align-items:center;gap:10px"><svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="#c9b8e6" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><path d="M12 3v3M12 18v3M3 12h3M18 12h3"/><circle cx="12" cy="12" r="5"/><circle cx="12" cy="12" r="1.6" fill="#c9b8e6"/></svg><h3 style="margin:0">Luna Cast</h3><span style="font-size:11px;color:#7ee787;border:1px solid #2f6b46;padding:2px 9px;border-radius:12px;font-weight:800;letter-spacing:1px">AI COMMENTARY</span></div>'+
    '<p style="font-size:12.5px;color:var(--muted);margin:6px 0 10px">Luna calls the action from the real match schedule - commentary, not scores. Scores above are the official record.</p>'+
    '<div id="luna-cast-body"><p style="color:var(--muted)">Luna is warming up…</p></div>';
   var up=document.getElementById('live-upcoming');
   var host=(up&&up.closest('.card'))||document.querySelector('main .wrap');
   host.parentNode.insertBefore(box,host.nextSibling);
   var brief=matches.map(function(m){var d=m.starts_at?new Date(m.starts_at):null;return (m.title||'Match')+' ('+(m.status||'scheduled')+(d?', '+d.toLocaleString('en-IN',{day:'numeric',month:'short',hour:'numeric',minute:'2-digit'}):'')+')';}).join('; ');
   fetch(LUNA_URL,{method:'POST',headers:{'Content-Type':'application/json','apikey':ANON_KEY,'Authorization':'Bearer '+ANON_KEY},
    body:JSON.stringify({session_id:'cast-'+Date.now(),message:'You are Luna, the XARENA esports caster. Give a short, hype but honest caster preview (max 90 words) for these REAL scheduled matches: '+brief+'. Do not invent scores, winners or teams that are not listed. If details are thin, say the matchups are still under wraps.'})})
    .then(function(r){return r.json();}).then(function(d){
     var txt=(d&&(d.reply||d.message||d.text))||'';
     document.getElementById('luna-cast-body').innerHTML=txt?'<p style="font-size:14.5px;line-height:1.55;color:#e6d8ff">'+esc(txt)+'</p>':'<p style="color:var(--muted)">Luna is quiet right now - try again later.</p>';
    }).catch(function(){document.getElementById('luna-cast-body').innerHTML='<p style="color:var(--muted)">Luna is quiet right now - try again later.</p>';});
  }).catch(function(){});
 }).catch(function(){});
}
if(document.readyState==='complete')boot();else window.addEventListener('load',boot);
})();
