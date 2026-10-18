/* Certificate Generator - pixel-faithful to mockup fd5c590d. REAL achievements only:
   verified registrations -> Participation certs, "Champion: X" badges -> Champion certs.
   QR encodes the real certificate payload (scan re-shows the same details). No fake certs, no fake "Munger Champion". */
(function(){
'use strict';
function esc(s){return String(s==null?'':s).replace(/[&<>"']/g,function(c){return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c];});}
var LAUREL='<svg class="xcrt-laurel" viewBox="0 0 120 54" fill="none" stroke="#f5b301" stroke-width="1.6" stroke-linecap="round"><path d="M60 20l4 8h-8Z" fill="#f5b301"/><path d="M60 8c-2 4-2 8 0 12 2-4 2-8 0-12Z" fill="rgba(245,179,1,.5)"/><path d="M50 16c-8-2-16-8-20-16 8 2 14 4 18 10M46 24c-9 0-18-4-24-12 8 1 15 4 20 9M70 16c8-2 16-8 20-16-8 2-14 4-18 10M74 24c9 0 18-4 24-12-8 1-15 4-20 9"/><path d="M44 32c-9 2-18 0-26-6 8-1 15 0 21 3M76 32c9 2 18 0 26-6-8-1-15 0-21 3"/><path d="M52 28h16l-3 14h-10Z" fill="rgba(245,179,1,.25)"/></svg>';
var SEAL='<svg width="64" height="64" viewBox="0 0 24 24" fill="none" stroke="#f5b301" stroke-width="1.4"><circle cx="12" cy="10" r="7" fill="rgba(245,179,1,.15)"/><circle cx="12" cy="10" r="5.2" stroke-dasharray="2 1.6"/><path d="M12 6.8l1 2 2.2.3-1.6 1.6.4 2.2-2-1-2 1 .4-2.2-1.6-1.6 2.2-.3Z" fill="#f5b301" stroke="none"/><path d="M9 16.5L7.5 22l4.5-2.4L16.5 22 15 16.5" stroke-linejoin="round"/></svg>';
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
function fmtDate(iso){try{return new Date(iso).toLocaleDateString('en-IN',{day:'numeric',month:'long',year:'numeric'});}catch(e){return '';}}
function qrSvg(text){
 try{var q=qrcode(0,'M');q.addData(text);q.make();
  var n=q.getModuleCount(),cell=4,pad=2,size=(n+pad*2)*cell,s='';
  for(var r=0;r<n;r++)for(var c=0;c<n;c++)if(q.isDark(r,c))s+='M'+((c+pad)*cell)+' '+((r+pad)*cell)+'h'+cell+'v'+cell+'h-'+cell+'Z';
  return '<svg viewBox="0 0 '+size+' '+size+'" xmlns="http://www.w3.org/2000/svg"><path d="'+s+'" fill="#1d0b38"/></svg>';
 }catch(e){return '';}
}
function boot(){
 if(!window.xrApi)return;
 var api=window.xrApi,app=document.getElementById('cert-app');
 if(!app)return;
 api.user().then(function(u){
  if(!u){app.innerHTML='<p style="text-align:center;color:#c9b8e6">Log in on the <a href="account.html" style="color:#ffd23f">Account page</a> - your real certificates appear here once you have a verified registration or a title.</p>';return;}
  app.innerHTML='<p style="text-align:center;color:#c9b8e6">Checking your tournament record…</p>';
  Promise.all([
   api.get('registrations','?user_id=eq.'+u.id+'&verified=eq.true&select=id,squad,ign,created_at,tournament_id&order=created_at.desc&limit=20').catch(function(){return [];}),
   api.get('badges','?user_id=eq.'+u.id+'&select=badge,earned_at&order=earned_at.desc&limit=20').catch(function(){return [];}),
   api.get('tournaments','?select=id,name').catch(function(){return [];}),
   api.get('profiles','?id=eq.'+u.id+'&select=ign,username&limit=1').catch(function(){return [];})
  ]).then(function(d){
   var regs=Array.isArray(d[0])?d[0]:[],badges=Array.isArray(d[1])?d[1]:[],tnames={};
   (Array.isArray(d[2])?d[2]:[]).forEach(function(t){tnames[t.id]=t.name;});
   var name='Player';if(Array.isArray(d[3])&&d[3][0])name=d[3][0].ign||d[3][0].username||'Player';
   var champs=badges.filter(function(b){return /^Champion: /.test(b.badge);});
   var items=[];
   champs.forEach(function(b){items.push({kind:'champion',name:name,tournament:b.badge.replace(/^Champion: /,''),date:fmtDate(b.earned_at)});});
   regs.forEach(function(r){items.push({kind:'participant',name:r.ign||name,squad:r.squad,tournament:tnames[r.tournament_id]||'XARENA Tournament',date:fmtDate(r.created_at)});});
   if(!items.length){app.innerHTML='<p style="text-align:center;color:#c9b8e6">No certificates yet - a participation certificate unlocks with your first verified tournament registration, and a champion certificate with a title.</p>';return;}
   var sel=0;
   function payload(it){return 'XARENA-CERT|'+it.kind+'|'+it.name+'|'+it.tournament+'|'+it.date;}
   function render(){
    var it=items[sel];
    app.innerHTML=
     '<div class="xcrt-doc">'+LAUREL+
      '<div class="xcrt-kind">'+(it.kind==='champion'?'CERTIFICATE OF CHAMPION':'CERTIFICATE OF PARTICIPATION')+'</div>'+
      '<div class="xcrt-tname">'+esc(it.tournament)+'</div>'+
      '<div class="xcrt-div"></div>'+
      '<div class="xcrt-pre">THIS CERTIFICATE IS PROUDLY PRESENTED TO</div>'+
      '<div class="xcrt-name">'+esc(it.name)+'</div>'+
      '<div class="xcrt-rows"><div><div class="lab">Tournament</div><div class="val">'+esc(it.tournament)+'</div></div><div><div class="lab">Date</div><div class="val">'+esc(it.date)+'</div></div></div>'+
      '<div class="xcrt-foot"><div><div class="xcrt-qr">'+qrSvg(payload(it))+'</div><span class="xcrt-qrlab">SCAN TO CHECK DETAILS</span></div>'+
      '<div class="xcrt-seal">'+SEAL+'<div class="lab">OFFICIAL<br>XARENA ESPORTS</div></div></div>'+
     '</div>'+
     '<div style="text-align:center"><span class="xcrt-status">✓ Status: Ready to download</span></div>'+
     '<div class="xcrt-actions"><button type="button" class="xcrt-btn" id="xc-share">↗ Share</button><button type="button" class="xcrt-btn gold" id="xc-dl">⬇ Download Certificate</button></div>'+
     (items.length>1?'<div class="xcrt-h2">YOUR CERTIFICATES</div>'+items.map(function(x,i){return '<button type="button" class="xcrt-item'+(i===sel?' on':'')+'" data-i="'+i+'"><div><h3>'+(x.kind==='champion'?'Champion Certificate':'Participation Certificate')+'</h3><p>'+esc(x.tournament)+' · '+esc(x.date)+'</p></div></button>';}).join(''):'');
    app.querySelectorAll('[data-i]').forEach(function(b){b.addEventListener('click',function(){sel=Number(b.getAttribute('data-i'));render();});});
    function canvas(){var c=document.createElement('canvas');c.width=1000;c.height=640;drawCert(c,{kind:it.kind,name:it.name,squad:it.squad,tournament:it.tournament,date:it.date});return c;}
    document.getElementById('xc-dl').addEventListener('click',function(){
     var a=document.createElement('a');a.download='xarena-certificate-'+it.kind+'.png';a.href=canvas().toDataURL('image/png');a.click();
     if(window.xrToast)xrToast('Certificate downloaded');
    });
    document.getElementById('xc-share').addEventListener('click',function(){
     canvas().toBlob(function(blob){
      var f=new File([blob],'xarena-certificate-'+it.kind+'.png',{type:'image/png'});
      if(navigator.canShare&&navigator.canShare({files:[f]})){navigator.share({files:[f],title:'XARENA Certificate'}).catch(function(){});}
      else{var a=document.createElement('a');a.download=f.name;a.href=URL.createObjectURL(blob);a.click();if(window.xrToast)xrToast('Share is not available here - certificate downloaded instead');}
     },'image/png');
    });
   }
   render();
  });
 }).catch(function(){});
}
if(document.readyState==='complete')boot();else window.addEventListener('load',boot);
})();
