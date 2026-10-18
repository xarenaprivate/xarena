(function(){
'use strict';
function esc(s){return String(s==null?'':s).replace(/[&<>"']/g,function(c){return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c];});}
function ago(ts){var t=new Date(ts);if(!Number.isFinite(t.getTime()))return '';var s=Math.max(0,(Date.now()-t.getTime())/1e3);if(s<90)return Math.max(1,Math.round(s))+'s ago';var m=s/60;if(m<60)return Math.round(m)+'m ago';var h=m/60;if(h<24)return Math.round(h)+'h ago';var d=h/24;if(d<7)return Math.round(d)+'d ago';return t.toLocaleDateString('en-IN',{day:'numeric',month:'short'});}
function boot(){
 var anchor=document.getElementById('my-notifs');if(!anchor||!window.xrApi)return;
 var api=window.xrApi,look=localStorage.getItem('xr_notif_look')||'nova';
 var classic1=anchor,classic2=document.getElementById('notif-out');
 var root=document.createElement('div');root.id='notif-nova';anchor.parentNode.insertBefore(root,anchor);
 var toggle=document.createElement('button');toggle.className='btn btn-ghost btn-sm';toggle.style.marginBottom='12px';
 anchor.parentNode.insertBefore(toggle,root);
 function apply(){var nova=look==='nova';root.style.display=nova?'':'none';classic1.style.display=nova?'none':'';if(classic2)classic2.style.display=nova?'none':'';toggle.textContent=nova?'Classic notifications':'Nova notifications';}
 toggle.addEventListener('click',function(){look=look==='nova'?'classic':'nova';localStorage.setItem('xr_notif_look',look);apply();});apply();
function svg(p,c){return '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="'+(c||'#221332')+'" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">'+p+'</svg>';}
 var ICO={
  clock:svg('<circle cx="12" cy="13" r="8"/><path d="M12 9v4l2.5 2.5M9 2h6"/>'),
  mega:svg('<path d="M3 11v2a1 1 0 0 0 1 1h2l4 4V6L6 10H4a1 1 0 0 0-1 1z"/><path d="M14 8a5 5 0 0 1 0 8M17 5a9 9 0 0 1 0 14"/>'),
  chart:svg('<path d="M4 20V10M10 20V4M16 20v-7M22 20H2"/>'),
  bell:svg('<path d="M18 8a6 6 0 1 0-12 0c0 7-3 8-3 8h18s-3-1-3-8"/><path d="M10.3 21a2 2 0 0 0 3.4 0"/>'),
  spark:svg('<path d="M12 3l1.9 5.6L19.5 10l-5.6 1.9L12 17.5l-1.9-5.6L4.5 10l5.6-1.4z"/>')
 };
 ICO.bellH=svg('<path d="M18 8a6 6 0 1 0-12 0c0 7-3 8-3 8h18s-3-1-3-8"/><path d="M10.3 21a2 2 0 0 0 3.4 0"/>','#ffd783');
 function icon(kind){return kind==='match_reminder'?ICO.clock:kind==='stats_weekly'?ICO.chart:kind==='luna'?ICO.spark:kind==='announcement'?ICO.mega:ICO.bell;}
 function card(n){
  var chips='';
  if(!n.read)chips='<span class="nchip">NEW</span>';
  else if(n.kind==='match_reminder')chips='<span class="nchip live">LIVE SOON</span>';
  else if(n.kind==='luna')chips='<span class="nchip ai">AI</span>';
  else chips='<span class="ndot" title="read"></span>';
  return '<div class="ncard"><div class="nico">'+icon(n.kind)+'</div><div class="nbody"><h3>'+esc(n.title)+'</h3><p>'+esc(n.body||'')+(n.at?' <span class="ntime">· '+ago(n.at)+'</span>':'')+(n.link?' <a href="'+esc(n.link)+'" style="color:#ffd783">Open</a>':'')+'</p></div>'+chips+'</div>';
 }
 function render(list,unread){
  var today=list.filter(function(n){var t=new Date(n.at),d=new Date();return t.getDate()===d.getDate()&&t.getMonth()===d.getMonth()&&t.getFullYear()===d.getFullYear();}).length;
  root.innerHTML='<div class="xnn"><div class="xnn-head"><h2>← Notifications Center</h2><span class="xnn-bell">'+ICO.bellH+'</span></div>'+
   '<div class="xnn-emblem"><div class="xlogo">XARENA ESPORTS</div></div>'+
   '<div class="xnn-sub">Notifications · Today · '+today+' update'+(today===1?'':'s')+(unread?' · '+unread+' new':'')+'</div>'+
   (list.length?list.map(card).join(''):'<div class="nempty"><h3>All caught up</h3><p>Registration updates, match reminders and announcements will appear here. Nothing yet.</p></div>')+
   (list.length?'<div class="nactions"><button class="btn btn-ghost btn-sm" id="xnn-readall">Mark all read</button></div>':'')+'</div>';
  var b=root.querySelector('#xnn-readall');
  if(b)b.addEventListener('click',function(){api.uRpc('notifications_read_all',{}).then(function(){load();});});
 }
 function load(){
  root.innerHTML='<div class="xnn"><div class="xnn-head"><h2>← Notifications Center</h2><span class="xnn-bell">'+ICO.bellH+'</span></div><p style="text-align:center;color:#c5b3d6">Loading…</p></div>';
  api.uRpc('my_notifications',{}).then(function(r){
   if(!r||!r.ok){render([],0);return;}
   render(Array.isArray(r.list)?r.list:[],Number(r.unread||0));
  }).catch(function(){root.innerHTML='<div class="xnn"><div class="xnn-head"><h2>← Notifications Center</h2><span class="xnn-bell">'+ICO.bellH+'</span></div><div class="nempty"><h3>Could not load</h3><p>Check your connection and refresh.</p></div></div>';});
 }
 load();
}
if(document.readyState==='complete')boot();else window.addEventListener('load',boot);
})();
