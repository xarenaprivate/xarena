/* Luna Cast - pixel-faithful to mockup 5bae0845. Luna commentates REAL match data on the Live page.
   Hidden unless the luna-chat backend answers AND there is real match data. No fake scores/win%/viewers. */
(function(){
'use strict';
var LUNA_URL='https://oskccuajgbyabogbvedj.supabase.co/functions/v1/luna-chat';
var ANON_KEY='sb_publishable_RltZjW7bXLT9DssLJVHkDA_cuHlcJo7';
function esc(s){return String(s==null?'':s).replace(/[&<>"']/g,function(c){return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c];});}
var MOON='<svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="#ffd23f" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M20 13.5A8.5 8.5 0 0 1 10.5 4 7 7 0 1 0 20 13.5Z" fill="rgba(255,210,63,.14)"/><path d="M17 4l.6 1.6L19 6l-1.4.6L17 8l-.6-1.4L15 6l1.4-.4Z" fill="#ffd23f" stroke="none"/></svg>';
function boot(){
 if(!window.xrApi)return;
 var api=window.xrApi;
 fetch(LUNA_URL,{method:'POST',headers:{'Content-Type':'application/json','apikey':ANON_KEY,'Authorization':'Bearer '+ANON_KEY},body:JSON.stringify({session_id:'cast-probe',ping:true})}).then(function(r){
  if(r.status===404||r.status===503)return;
  api.get('matches_public','?order=starts_at.desc&limit=8&select=title,starts_at,status').then(function(matches){
   if(!Array.isArray(matches)||!matches.length)return;
   var box=document.createElement('div');box.id='luna-cast';
   box.innerHTML='<div class="xlc-head"><div class="xlc-moon">'+MOON+'</div><div><div class="xlc-name">Luna AI</div><div class="xlc-sub">XARENA ESPORTS · CASTER</div></div><span class="xlc-chip">CASTER COMMENTARY</span></div>'+
    '<div class="xlc-insight"><div class="who">LUNA AI · LIVE PREVIEW</div><p id="luna-cast-body">Luna is warming up…</p></div>'+
    '<p class="xlc-note">Luna previews the real match schedule - commentary, not scores. The schedule above is the official record.</p>';
   var anchor=document.getElementById('luna-cast-anchor');
   if(anchor)anchor.appendChild(box);else{var w=document.querySelector('main .wrap');if(w)w.appendChild(box);}
   var brief=matches.map(function(m){var d=m.starts_at?new Date(m.starts_at):null;return (m.title||'Match')+' ('+(m.status||'scheduled')+(d?', '+d.toLocaleString('en-IN',{day:'numeric',month:'short',hour:'numeric',minute:'2-digit'}):'')+')';}).join('; ');
   fetch(LUNA_URL,{method:'POST',headers:{'Content-Type':'application/json','apikey':ANON_KEY,'Authorization':'Bearer '+ANON_KEY},
    body:JSON.stringify({session_id:'cast-'+Date.now(),message:'You are Luna, the XARENA esports caster. Give a short, hype but honest caster preview (max 90 words) for these REAL scheduled matches: '+brief+'. Do not invent scores, winners or teams that are not listed. If details are thin, say the matchups are still under wraps.'})})
    .then(function(r){return r.json();}).then(function(d){
     var txt=(d&&(d.reply||d.message||d.text))||'';
     document.getElementById('luna-cast-body').textContent=txt||'Luna is quiet right now - try again later.';
    }).catch(function(){document.getElementById('luna-cast-body').textContent='Luna is quiet right now - try again later.';});
  }).catch(function(){});
 }).catch(function(){});
}
if(document.readyState==='complete')boot();else window.addEventListener('load',boot);
})();
