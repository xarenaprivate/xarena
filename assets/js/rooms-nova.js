(function(){
'use strict';
function esc(s){return String(s==null?'':s).replace(/[&<>"']/g,function(c){return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c];});}
function svg(p,c){return '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="'+(c||'#221332')+'" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">'+p+'</svg>';}
var ICO={
 clock:svg('<circle cx="12" cy="13" r="8"/><path d="M12 9v4l2.5 2.5M9 2h6"/>','#ffd783'),
 copy:svg('<rect x="9" y="9" width="12" height="12" rx="2"/><path d="M5 15V5a2 2 0 0 1 2-2h10"/>','#ffd783'),
 trophy:svg('<path d="M8 21h8M12 17v4M7 4h10v5a5 5 0 0 1-10 0zM7 6H4a1 1 0 0 0-1 1c0 2 1.5 3.5 4 3.5M17 6h3a1 1 0 0 1 1 1c0 2-1.5 3.5-4 3.5"/>'),
 users:svg('<path d="M17 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9.5" cy="7" r="4"/><path d="M22 21v-2a4 4 0 0 0-3-3.87M15.5 3.13a4 4 0 0 1 0 7.75"/>','#ffd783')
};
function boot(){
 var anchor=document.getElementById('match-list');if(!anchor||!window.xrApi)return;
 var api=window.xrApi,look=localStorage.getItem('xr_rooms_look')||'nova';
 var root=document.createElement('div');root.id='rooms-nova';anchor.parentNode.insertBefore(root,anchor);
 var toggle=document.createElement('button');toggle.className='btn btn-ghost btn-sm';toggle.style.marginBottom='12px';anchor.parentNode.insertBefore(toggle,root);
 function apply(){var nova=look==='nova';root.style.display=nova?'':'none';anchor.style.display=nova?'none':'';toggle.textContent=nova?'Classic rooms':'Nova rooms';}
 toggle.addEventListener('click',function(){look=look==='nova'?'classic':'nova';localStorage.setItem('xr_rooms_look',look);apply();});apply();
 var matches=[],me=null,status={};
 function nextMatch(){var up=matches.filter(function(m){return m.status!=='done'&&m.starts_at;});up.sort(function(a,b){return new Date(a.starts_at)-new Date(b.starts_at);});return up[0]||null;}
 function cdText(m){if(!m)return '';var t=new Date(m.starts_at).getTime()-Date.now();if(t<=0)return '00:00:00';var s=Math.floor(t/1e3),h=Math.floor(s/3600),mm=Math.floor(s%3600/60),ss=s%60;function p(n){return (n<10?'0':'')+n;}return p(h)+':'+p(mm)+':'+p(ss);}
 function fmt(m){return m.starts_at?new Date(m.starts_at).toLocaleString('en-IN',{timeZone:'Asia/Kolkata',day:'numeric',month:'short',hour:'2-digit',minute:'2-digit'})+' IST':'time TBA';}
 function render(){
  var nm=nextMatch(),done=matches.filter(function(m){return m.status==='done';});
  var html='<div class="xrn"><div class="xrn-head"><a class="xrn-back" href="tournaments.html">←</a><h2>MATCH ROOMS</h2></div>';
  if(!matches.length){html+='<div class="xrn-empty"><h3>No matches scheduled yet</h3><p>Match rooms appear here as match control schedules them. Registered players see the room unlock 15 minutes before start.</p></div></div>';root.innerHTML=html;return;}
  if(nm){
   var st=status[nm.id]||{},cnt=Number(st.count||0),mine=!!st.me;
   html+='<div class="xrn-cd"><div class="cd-line">'+ICO.clock+' <span data-cd>Check-in closes in '+cdText(nm)+'</span></div><div class="cd-sub">'+esc(nm.title)+' · '+fmt(nm)+' · Room locks at match start · check-in required</div></div>';
   html+='<div class="xrn-card"><div class="xrn-readytop"><b>'+cnt+' player'+(cnt===1?'':'s')+' ready</b><span class="xrn-chip'+(mine?' on':'')+'">'+(mine?'✓ Ready':'Not checked in')+'</span></div>'+
    '<div class="xrn-avs">'+(mine?'<div class="xrn-av"><div class="avc">You</div><div class="avn">You</div><div class="avr">Ready</div></div>':'')+(cnt>(mine?1:0)?'<div class="xrn-av"><div class="avc">+'+(cnt-(mine?1:0))+'</div><div class="avn">checked in</div><div class="avr">Ready</div></div>':'')+(cnt===0?'<div class="xrn-av"><div class="avc">'+ICO.users+'</div><div class="avn">No one yet</div></div>':'')+'</div></div>';
   html+='<div class="xrn-card"><div class="xrn-roomid"><span style="font-size:12px;color:#c5b3d6">Room ID</span><span class="rid" data-rid>'+(st.room_id?esc(st.room_id):'••••••')+'</span>'+(st.room_id?'<button class="xrn-ghost xrn-btn" style="width:auto;margin:0;padding:6px 10px" data-copy>'+ICO.copy+'</button>':'')+'</div>'+
    (st.room_pass?'<div style="font-size:12px;color:#c5b3d6;margin-top:6px">Password: <b style="color:#ffd783;user-select:all">'+esc(st.room_pass)+'</b></div>':'')+
    '<button class="xrn-btn" data-checkin="'+nm.id+'"'+(mine?' disabled':'')+'>'+(mine?'CHECKED IN':'CHECK-IN NOW')+'</button>'+
    (st.room_id?'':'<button class="xrn-btn xrn-ghost" data-room="'+nm.id+'">REVEAL ROOM ID</button>')+
    '<div class="xrn-msg" data-msg></div></div>';
  }
  html+='<div class="xrn-sect">Live Results <span style="float:right;font-size:11px;color:#7ee787">● '+(matches.some(function(m){return m.status==='live';})?'LIVE':'SCHEDULED')+'</span></div>';
  html+=done.length?done.map(function(m){return '<div class="xrn-res"><div class="ric">'+ICO.trophy+'</div><div><h4>'+esc(m.title)+'</h4><p>'+esc(m.tournament_id||'')+'</p><p>Finished · '+fmt(m)+'</p></div><div class="rst">COMPLETED</div></div>';}).join(''):'<div class="xrn-empty" style="padding:14px"><p>No finished matches yet. Results appear here after each match.</p></div>';
  html+='</div>';
  root.innerHTML=html;
  var msg=root.querySelector('[data-msg]');
  var cb=root.querySelector('[data-checkin]');
  if(cb)cb.addEventListener('click',function(){if(!me){msg.innerHTML='<a href="account.html" style="color:#ffd783">Log in</a> first - check-in is for registered players.';return;}cb.disabled=true;api.uRpc('match_checkin',{p_match:cb.getAttribute('data-checkin')}).then(function(r){if(r&&r.ok){load();}else{cb.disabled=false;msg.textContent=(r&&r.error)||'Check-in failed - try again.';}}).catch(function(){cb.disabled=false;msg.textContent='Check-in failed - try again.';});});
  var rb=root.querySelector('[data-room]');
  if(rb)rb.addEventListener('click',function(){if(!me){msg.innerHTML='<a href="account.html" style="color:#ffd783">Log in</a> first - rooms are for registered players.';return;}rb.disabled=true;rb.textContent='CHECKING…';api.uRpc('match_room',{p_match:rb.getAttribute('data-room')}).then(function(r){if(r&&r.ok){status[nm.id].room_id=r.room_id;status[nm.id].room_pass=r.room_pass;render();}else{rb.disabled=false;rb.textContent='REVEAL ROOM ID';msg.textContent=(r&&r.error)||'Room not available yet.';}}).catch(function(){rb.disabled=false;rb.textContent='REVEAL ROOM ID';msg.textContent='Room not available yet.';});});
  var cp=root.querySelector('[data-copy]');
  if(cp)cp.addEventListener('click',function(){var t=root.querySelector('[data-rid]').textContent;if(navigator.clipboard)navigator.clipboard.writeText(t);msg.textContent='Room ID copied.';});
 }
 var timer=null;
 function tick(){var nm=nextMatch();var el=root.querySelector('[data-cd]');if(el&&nm)el.textContent='Check-in closes in '+cdText(nm);}
 function load(){
  root.innerHTML='<div class="xrn"><div class="xrn-head"><h2>MATCH ROOMS</h2></div><p style="text-align:center;color:#c5b3d6">Loading real matches…</p></div>';
  Promise.all([
   fetch(api.url+'/rest/v1/matches_public?select=*&order=starts_at.asc.nullslast',{headers:{apikey:api.key}}).then(function(r){return r.json();}).catch(function(){return [];}),
   api.user().catch(function(){return null;})
  ]).then(function(d){
   matches=Array.isArray(d[0])?d[0]:[];me=d[1];
   var ups=matches.filter(function(m){return m.status!=='done';});
   return Promise.all(ups.map(function(m){return api.uRpc('match_checkin_status',{p_match:m.id}).then(function(st){status[m.id]=(st&&st.ok)?st:{count:0,me:false};}).catch(function(){status[m.id]={count:0,me:false};});}));
  }).then(function(){render();if(!timer)timer=setInterval(tick,1000);}).catch(function(){root.innerHTML='<div class="xrn"><div class="xrn-head"><h2>MATCH ROOMS</h2></div><div class="xrn-empty"><h3>Could not load</h3><p>Check your connection and refresh.</p></div></div>';});
 }
 load();
}
if(document.readyState==='complete')boot();else window.addEventListener('load',boot);
})();
