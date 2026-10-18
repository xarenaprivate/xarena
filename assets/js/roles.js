/* Team Roles Formation - pixel-faithful to mockup 80555039. Real roles only (profiles.role via update_player_card).
   Squad formation shows real squad_members when squads exist; honest empty state until then.
   Never: fake LVL/K/D/SPEED/WINRATE from the mockup. */
(function(){
'use strict';
function esc(s){return String(s==null?'':s).replace(/[&<>"']/g,function(c){return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c];});}
var ROLES=['IGL','Rusher','Support','Sniper','Grenadier','Flex'];
var ROLE_DESC={IGL:'Shotcaller - team strategist',Rusher:'Entry - frontline aggressor',Support:'Defend - heals and cover',Sniper:'Long range - precision',Grenadier:'Utility - nades and zone control',Flex:'Fills whatever the squad needs'};
function boot(){
 if(!window.xrApi)return;
 var api=window.xrApi,app=document.getElementById('roles-app');
 if(!app)return;
 api.user().then(function(u){
  if(!u){app.innerHTML='<div class="xst-hero"><p style="color:#c9b8e6;margin:6px 0">Log in on the <a href="account.html" style="color:#ffd23f">Account page</a> to set your role and see your squad formation.</p></div>';return;}
  Promise.all([
   api.get('profiles','?id=eq.'+u.id+'&select=role,ign,username&limit=1').catch(function(){return [];}),
   api.get('squad_members','?user_id=eq.'+u.id+'&select=squad_id,role&limit=1').catch(function(){return [];})
  ]).then(function(d){
   var prof=Array.isArray(d[0])&&d[0][0]?d[0][0]:{};
   var mySquad=Array.isArray(d[1])&&d[1][0]?d[1][0]:null;
   var current=prof.role||'';
   function render(){
    app.innerHTML=
     '<div class="xnlv-h2" style="margin-top:4px">YOUR ROLE</div>'+
     '<div style="display:flex;gap:8px;flex-wrap:wrap">'+ROLES.map(function(r){return '<button type="button" class="xrl-chip'+(current===r?' on':'')+'" data-role="'+r+'">'+r.toUpperCase()+'</button>';}).join('')+'</div>'+
     '<p class="xvt-note" id="xrl-desc">'+(current?esc(ROLE_DESC[current]||''):'Pick the role you actually play - it shows on your player card.')+'</p>'+
     '<div class="xnlv-h2">SQUAD FORMATION</div><div id="xrl-squad"></div>';
    app.querySelectorAll('[data-role]').forEach(function(b){b.addEventListener('click',function(){
     var r=b.getAttribute('data-role');
     app.querySelectorAll('.xrl-chip').forEach(function(x){x.classList.toggle('on',x===b);});
     document.getElementById('xrl-desc').textContent='Saving…';
     api.uRpc('update_player_card',{p_role:r}).then(function(res){
      if(res&&res.ok){current=r;document.getElementById('xrl-desc').textContent=ROLE_DESC[r];if(window.xrToast)xrToast('Role saved - it shows on your player card.');}
      else{document.getElementById('xrl-desc').textContent=(res&&res.error)||'Could not save - try again.';}
     }).catch(function(){document.getElementById('xrl-desc').textContent='Could not save - try again.';});
    });});
    var box=document.getElementById('xrl-squad');
    if(!mySquad){box.innerHTML='<div class="xst-hero" style="text-align:center"><p style="color:#c9b8e6;margin:6px 0 4px">No squad formation yet.</p><p style="color:#9d8cc2;font-size:13px;margin:0">When you join or create a squad on the <a href="squads.html" style="color:#ffd23f">Squads page</a>, its role layout shows here - captain on top, every member in their real role.</p></div>';return;}
    api.get('squad_members','?squad_id=eq.'+mySquad.squad_id+'&select=user_id,role').then(function(members){
     if(!Array.isArray(members)||!members.length){box.innerHTML='<p style="text-align:center;color:#9d8cc2">Your squad has no members listed yet.</p>';return;}
     var ids=members.map(function(m){return m.user_id;}).join(',');
     api.get('profiles','?id=in.('+ids+')&select=id,ign,username').then(function(profs){
      var names={};(Array.isArray(profs)?profs:[]).forEach(function(p){names[p.id]=p.ign||p.username||'Player';});
      box.innerHTML='<div class="xrl-tree">'+members.map(function(m){
       return '<div class="xrl-node'+(m.role==='IGL'?' xrl-cap':'')+'">'+esc(names[m.user_id]||'Player')+(m.role?' · '+esc(m.role):'')+'</div>';
      }).join('')+'</div><p class="xvt-note">Real members and their saved roles - nothing assigned automatically.</p>';
     });
    });
   }
   render();
  });
 }).catch(function(){});
}
if(document.readyState==='complete')boot();else window.addEventListener('load',boot);
})();
