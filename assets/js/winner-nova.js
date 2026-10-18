/* Winner Celebration (mockup d6f57231). Renders ONLY from a real final winner in bracket_matches.
   Claim Title writes a real badge via claim_champion_title (membership-verified, audit-logged). */
(function(){
'use strict';
function esc(s){return String(s==null?'':s).replace(/[&<>"']/g,function(c){return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c];});}
var TROPHY='<svg width="120" height="120" viewBox="0 0 24 24" fill="none" stroke="#f5b301" stroke-width="1.1" stroke-linecap="round" stroke-linejoin="round"><path d="M7 3.5h10v5.5a5 5 0 0 1-10 0Z" fill="rgba(245,179,1,.18)"/><path d="M7 4.5H3.8a3.2 3.2 0 0 0 3.4 4.4M17 4.5h3.2a3.2 3.2 0 0 1-3.4 4.4"/><path d="M12 14v2.6M8.2 21h7.6M9.8 16.6h4.4l1 4.4h-6.4Z" fill="rgba(245,179,1,.12)"/><path d="M12 5.4l.85 1.72 1.9.28-1.38 1.34.33 1.9L12 9.75l-1.7.9.33-1.9-1.38-1.35 1.9-.28Z" fill="#f5b301" stroke="none"/></svg>';
var CROWN='<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#f5b301" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M3 18h18M4 18l-1.5-8L8 13l4-7 4 7 5.5-3L20 18"/></svg>';
var champion=null;
function confetti(cv){
 var ctx=cv.getContext('2d'),W=cv.width=cv.offsetWidth*2,H=cv.height=cv.offsetHeight*2,ps=[],cols=['#f5b301','#ffe9a8','#8b3dff','#c9b8e6','#ffd783'];
 for(var i=0;i<90;i++)ps.push({x:Math.random()*W,y:-Math.random()*H*.6,w:5+Math.random()*7,h:8+Math.random()*10,c:cols[i%cols.length],vy:1+Math.random()*2.2,vx:-.6+Math.random()*1.2,r:Math.random()*Math.PI,vr:-.05+Math.random()*.1});
 var t0=Date.now();
 (function tick(){var el=1e3*6;if(Date.now()-t0>el||!document.body.contains(cv))return;ctx.clearRect(0,0,W,H);ps.forEach(function(p){p.y+=p.vy;p.x+=p.vx;p.r+=p.vr;if(p.y>H+20){p.y=-20;p.x=Math.random()*W;}ctx.save();ctx.translate(p.x,p.y);ctx.rotate(p.r);ctx.fillStyle=p.c;ctx.fillRect(-p.w/2,-p.h/2,p.w,p.h);ctx.restore();});requestAnimationFrame(tick);})();
}
function fmtDate(iso){if(!iso)return '';try{var d=new Date(iso);return d.toLocaleDateString('en-IN',{day:'numeric',month:'short',year:'numeric'});}catch(e){return '';}}
function open(){
 if(!champion||document.getElementById('xwn'))return;
 var el=document.createElement('div');el.className='xwn';el.id='xwn';
 el.innerHTML='<div class="xwn-card"><canvas class="xwn-canvas"></canvas>'+
  '<div class="xwn-topbar"><span>✦ XARENA ESPORTS</span><button class="xwn-close" data-x aria-label="Close">✕</button></div>'+
  '<div class="xwn-title">WINNER CELEBRATION</div>'+
  '<div class="xwn-ribbon">Booyah Champions</div>'+
  '<div class="xwn-trophy">'+TROPHY+'</div>'+
  '<div class="xwn-team">'+esc(champion.winner)+'</div>'+
  '<div class="xwn-meta">'+esc(champion.tournament_name||'Tournament')+' · Finals'+(champion.date?' — '+esc(champion.date):'')+'</div>'+
  '<div class="xwn-congrats"><b>CONGRATULATIONS!</b><p>'+(champion.mine?'Your team has claimed the championship title!':esc(champion.winner)+' has claimed the championship title!')+'</p></div>'+
  '<div class="xwn-reward" data-reward hidden>'+CROWN+'<div><b>Rewards Unlocked</b><span data-reward-txt></span></div></div>'+
  '<button class="xwn-claim" data-claim>Claim Title</button>'+
  '<button class="xwn-share" data-share>Share Victory</button>'+
  '<p class="xwn-note">Champion title is recorded on the squad members\' profiles. XP only - never cash.</p>'+
 '</div>';
 document.body.appendChild(el);
 confetti(el.querySelector('.xwn-canvas'));
 function close(){el.remove();}
 el.querySelector('[data-x]').onclick=close;
 el.addEventListener('click',function(e){if(e.target===el)close();});
 el.querySelector('[data-share]').onclick=function(){
  var msg=champion.winner+' are the '+ (champion.tournament_name||'XARENA') +' champions! Booyah! - XARENA ESPORTS';
  if(navigator.share)navigator.share({title:'XARENA champions',text:msg}).catch(function(){});else{if(window.xrToast)xrToast(msg);}
 };
 el.querySelector('[data-claim]').onclick=function(){
  var b=el.querySelector('[data-claim]');b.disabled=true;
  window.xrApi.uRpc('claim_champion_title',{p_tournament:champion.tournament_id}).then(function(r){
   if(r&&r.ok){var rw=el.querySelector('[data-reward]');rw.hidden=false;el.querySelector('[data-reward-txt]').textContent='Champion title on your profile: '+r.badge;b.textContent='Title Claimed';if(window.xrSfx)xrSfx.play('win');}
   else{b.disabled=false;b.textContent=(r&&r.error)||'Could not claim';setTimeout(function(){b.textContent='Claim Title';},2600);}
  }).catch(function(){b.disabled=false;b.textContent='Network error - retry';});
 };
}
function boot(){
 if(!window.xrApi)return;
 var api=window.xrApi;
 fetch(api.url+'/rest/v1/bracket_matches?winner=not.is.null&order=updated_at.desc&limit=20&select=tournament_id,winner,round,updated_at,starts_at',{headers:{apikey:api.key}}).then(function(r){return r.json();}).then(function(rows){
  if(!Array.isArray(rows)||!rows.length)return;
  var byT={};rows.forEach(function(m){(byT[m.tournament_id]=byT[m.tournament_id]||[]).push(m);});
  var best=null;
  Object.keys(byT).forEach(function(t){var ms=byT[t];var maxR=Math.max.apply(null,ms.map(function(m){return m.round;}));var fin=ms.filter(function(m){return m.round===maxR;})[0];if(!best||new Date(fin.updated_at)>new Date(best.updated_at))best=fin;});
  if(!best)return;
  champion={tournament_id:best.tournament_id,winner:best.winner,date:fmtDate(best.starts_at||best.updated_at),mine:false};
  fetch(api.url+'/rest/v1/tournaments?id=eq.'+encodeURIComponent(best.tournament_id)+'&select=name&limit=1',{headers:{apikey:api.key}}).then(function(r){return r.json();}).then(function(t){if(Array.isArray(t)&&t[0])champion.tournament_name=t[0].name;}).catch(function(){});
  api.user().then(function(u){if(!u)return null;return fetch(api.url+'/rest/v1/squad_members?user_id=eq.'+u.id+'&select=squad_id',{headers:{apikey:api.key}}).then(function(r){return r.json();}).then(function(sm){if(!Array.isArray(sm)||!sm.length)return null;var ids=sm.map(function(x){return x.squad_id;});return fetch(api.url+'/rest/v1/squads?id=in.('+ids.join(',')+')&select=name',{headers:{apikey:api.key}}).then(function(r){return r.json();}).then(function(sq){if(Array.isArray(sq))champion.mine=sq.some(function(s){return String(s.name).toUpperCase()===String(champion.winner).toUpperCase();});});});}).catch(function(){}).then(function(){
   var chip=document.createElement('button');chip.className='xwn-openchip';chip.innerHTML=CROWN+' Champions: '+esc(champion.winner);chip.onclick=open;
   var anchor=document.getElementById('bracket-nova')||document.querySelector('main');if(anchor&&anchor.parentNode)anchor.parentNode.insertBefore(chip,anchor);
   var seen='xwn_seen_'+champion.tournament_id+'_'+champion.winner;
   try{if(!localStorage.getItem(seen)){localStorage.setItem(seen,'1');setTimeout(open,900);}}catch(e){setTimeout(open,900);}
  });
 }).catch(function(){});
}
if(document.readyState==='complete')boot();else window.addEventListener('load',boot);
})();
