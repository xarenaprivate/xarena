/* Certificate Generator (v7.14). Real achievements only: verified registrations -> Participant certs,
   Champion badges -> Champion certs. Canvas PNG, download/share. */
(function(){
'use strict';
function esc(s){return String(s==null?'':s).replace(/[&<>"']/g,function(c){return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c];});}
function drawCert(c,o){
 var ctx=c.getContext('2d'),W=c.width,H=c.height;
 var g=ctx.createLinearGradient(0,0,W,H);g.addColorStop(0,'#221338');g.addColorStop(1,'#12082a');
 ctx.fillStyle=g;ctx.fillRect(0,0,W,H);
 ctx.strokeStyle='#f5b301';ctx.lineWidth=6;ctx.strokeRect(24,24,W-48,H-48);
 ctx.strokeStyle='rgba(245,179,1,.35)';ctx.lineWidth=2;ctx.strokeRect(40,40,W-80,H-80);
 ctx.textAlign='center';
 ctx.fillStyle='#f5b301';ctx.font='900 44px Orbitron, sans-serif';
 ctx.fillText('XARENA ESPORTS',W/2,120);
 ctx.fillStyle='#b7a6d8';ctx.font='600 20px Rajdhani, sans-serif';
 ctx.fillText('OFFICIAL CERTIFICATE',W/2,152);
 ctx.strokeStyle='rgba(245,179,1,.5)';ctx.beginPath();ctx.moveTo(W/2-140,176);ctx.lineTo(W/2+140,176);ctx.stroke();
 ctx.fillStyle='#ffe9a8';ctx.font='700 26px Rajdhani, sans-serif';
 ctx.fillText(o.kind==='champion'?'CHAMPION':'CERTIFICATE OF PARTICIPATION',W/2,232);
 ctx.fillStyle='#d9ccf0';ctx.font='600 19px Rajdhani, sans-serif';
 ctx.fillText('This certifies that',W/2,272);
 ctx.fillStyle='#ffffff';ctx.font='800 40px Rajdhani, sans-serif';
 ctx.fillText(o.name,W/2,326);
 ctx.fillStyle='#d9ccf0';ctx.font='600 19px Rajdhani, sans-serif';
 ctx.fillText(o.kind==='champion'?'claimed the championship title in':'competed as '+(o.squad?'['+o.squad+'] ':'')+'in',W/2,362);
 ctx.fillStyle='#f5b301';ctx.font='800 30px Rajdhani, sans-serif';
 ctx.fillText(o.tournament,W/2,408);
 ctx.fillStyle='#9d8cc2';ctx.font='600 17px Rajdhani, sans-serif';
 ctx.fillText(o.date,W/2,448);
 ctx.fillText('XARENA ESPORTS - Free Fire Esports Made in India - XP only, never cash',W/2,H-56);
}
function boot(){
 if(!window.xrApi)return;
 var api=window.xrApi;
 api.user().then(function(u){
  if(!u)return;
  var card=document.createElement('div');card.className='card';card.style.marginTop='16px';card.id='cert-card';
  card.innerHTML='<h3>My certificates</h3><div id="cert-list"><p style="color:var(--muted)">Checking…</p></div>';
  var anchor=document.getElementById('prof-regs');
  var host=(anchor&&anchor.closest('.card'))||document.querySelector('main .wrap')||document.querySelector('main');
  host.parentNode.insertBefore(card,host.nextSibling);
  Promise.all([
   api.get('registrations','?user_id=eq.'+u.id+'&verified=eq.true&select=id,squad,ign,created_at,tournament_id&order=created_at.desc&limit=20').catch(function(){return [];}),
   api.get('badges','?user_id=eq.'+u.id+'&select=badge,earned_at&order=earned_at.desc&limit=20').catch(function(){return [];}),
   api.get('tournaments','?select=id,name').catch(function(){return [];})
  ]).then(function(d){
   var regs=Array.isArray(d[0])?d[0]:[],badges=Array.isArray(d[1])?d[1]:[],tnames={};
   (Array.isArray(d[2])?d[2]:[]).forEach(function(t){tnames[t.id]=t.name;});
   var champs=badges.filter(function(b){return /^Champion: /.test(b.badge);});
   var box=document.getElementById('cert-list');
   if(!regs.length&&!champs.length){box.innerHTML='<p style="color:var(--muted);font-size:13.5px">No certificates yet - certificates unlock with a verified tournament registration, and champion certificates with a title.</p>';return;}
   function fmtDate(iso){try{return new Date(iso).toLocaleDateString('en-IN',{day:'numeric',month:'long',year:'numeric'});}catch(e){return '';}}
   var items=[];
   champs.forEach(function(b){items.push({kind:'champion',tournament:b.badge.replace(/^Champion: /,''),date:fmtDate(b.earned_at)});});
   regs.forEach(function(r){items.push({kind:'participant',squad:r.squad,tournament:tnames[r.tournament_id]||'XARENA Tournament',date:fmtDate(r.created_at),ign:r.ign});});
   box.innerHTML=items.map(function(it,i){
    return '<div class="news-item" style="display:flex;align-items:center;gap:10px"><div style="flex:1"><h3 style="font-size:15px">'+(it.kind==='champion'?'Champion Certificate':'Participation Certificate')+'</h3><p>'+esc(it.tournament)+' · '+esc(it.date)+'</p></div><button class="btn btn-gold btn-sm" data-cert="'+i+'">Download</button></div>';
   }).join('');
   box.querySelectorAll('[data-cert]').forEach(function(btn){
    btn.addEventListener('click',function(){
     var it=items[Number(btn.getAttribute('data-cert'))];
     var name=(it.ign)||'Player';
     api.get('profiles','?id=eq.'+u.id+'&select=ign,username&limit=1').then(function(p){
      if(Array.isArray(p)&&p[0]&&(p[0].ign||p[0].username))name=p[0].ign||p[0].username;
     }).catch(function(){}).then(function(){
      var c=document.createElement('canvas');c.width=1000;c.height=640;
      drawCert(c,{kind:it.kind,name:name,squad:it.squad,tournament:it.tournament,date:it.date});
      var a=document.createElement('a');a.download='xarena-certificate-'+it.kind+'.png';a.href=c.toDataURL('image/png');a.click();
      if(window.xrToast)xrToast('Certificate downloaded');
     });
    });
   });
  });
 }).catch(function(){});
}
if(document.readyState==='complete')boot();else window.addEventListener('load',boot);
})();
