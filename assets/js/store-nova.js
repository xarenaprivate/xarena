(function(){
'use strict';
function esc(s){return String(s==null?'':s).replace(/[&<>"']/g,function(c){return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c];});}
function svg(p){return '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="#ffd783" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">'+p+'</svg>';}
var ICO={
 frame:svg('<circle cx="12" cy="9" r="3.2"/><path d="M5 20a7 7 0 0 1 14 0"/><circle cx="12" cy="12" r="10" stroke-dasharray="3 3"/>'),
 badge:svg('<circle cx="12" cy="9" r="5"/><path d="M8.5 13.5L7 22l5-3 5 3-1.5-8.5"/>'),
 title:svg('<path d="M4 18h16M5 18l-1.5-9L9 13l3-7 3 7 5.5-4L19 18"/>'),
 reward:svg('<rect x="3" y="8" width="18" height="4"/><path d="M5 12v8h14v-8M12 8v12M12 8s-1.5-5-4.5-5S5 8 12 8zm0 0s1.5-5 4.5-5S19 8 12 8z"/>')
};
function kindMeta(k){k=String(k||'reward');return k==='avatar_frame'?{label:'AVATAR FRAME',icon:ICO.frame,tab:'Avatar Frames'}:k==='name_badge'?{label:'NAME BADGE',icon:ICO.badge,tab:'Name Badges'}:k==='title'?{label:'TITLE',icon:ICO.title,tab:'Titles'}:{label:'REWARD',icon:ICO.reward,tab:'All'};}
function boot(){
 var anchor=document.getElementById('rw-card');if(!anchor||!window.xrApi)return;
 var api=window.xrApi,look=localStorage.getItem('xr_store_look')||'nova';
 var root=document.createElement('div');root.id='store-nova';anchor.parentNode.insertBefore(root,anchor);
 var toggle=document.createElement('button');toggle.className='btn btn-ghost btn-sm';toggle.style.marginBottom='12px';anchor.parentNode.insertBefore(toggle,anchor);
 function apply(){var nova=look==='nova';root.style.display=nova?'':'none';anchor.style.display=nova?'none':'';toggle.textContent=nova?'Classic rewards':'Nova store';}
 toggle.addEventListener('click',function(){look=look==='nova'?'classic':'nova';localStorage.setItem('xr_store_look',look);apply();});apply();
 var me=null,balance=null,catalog=[],mine=[],tab='All';
 function render(){
  var tabs=['All','Avatar Frames','Name Badges','Titles'];
  var shown=catalog.filter(function(it){return tab==='All'||kindMeta(it.kind).tab===tab;});
  var html='<div class="xst"><div class="xst-head"><a class="xst-back" href="play.html">←</a><h2>XP REWARDS STORE</h2></div>'+
   '<div class="xst-bal"><span class="xst-free">FREE · NO CASH</span><div class="lbl">YOUR XP BALANCE</div><div class="amt">'+(balance===null?'—':esc(balance)+' XP')+'</div><div class="sub">Earn more XP by completing matches & quests - No cash needed. FREE rewards only.</div></div>'+
   '<div class="xst-tabs">'+tabs.map(function(t){return '<button class="xst-tab'+(t===tab?' on':'')+'" data-tab="'+t+'">'+t+'</button>';}).join('')+'</div>'+
   '<div class="xst-sect">Featured Rewards - Claim with XP</div>'+
   (catalog.length===0?'<div class="xst-empty"><h3>Catalog opens soon</h3><p>The owner is adding real rewards. Keep stacking XP - every check-in, spin and quiz win counts. XP is never cash.</p></div>':
    shown.length===0?'<div class="xst-empty"><p>Nothing in this category yet.</p></div>':
    shown.map(function(it){var km=kindMeta(it.kind);var afford=balance!==null&&Number(balance)>=Number(it.cost);
     return '<div class="xst-item"><div class="iic">'+km.icon+'</div><div class="ibody"><span class="ikind">'+km.label+'</span><h4>'+esc(it.title)+'</h4>'+(it.description?'<p>'+esc(it.description)+'</p>':'')+'</div><div class="iside"><span class="icost">'+esc(it.cost)+' XP · FREE</span><button class="xst-claim" data-claim="'+it.id+'" data-title="'+esc(it.title)+'" data-cost="'+it.cost+'"'+(!me||!afford?' disabled':'')+'>'+(me?afford?'CLAIM':'LOCKED':'LOG IN')+'</button></div></div>';}).join(''))+
   '<div class="xst-msg" data-msg></div>'+
   '<div class="xst-sect">My requests</div>'+
   (mine.length?mine.map(function(x){var col=x.status==='fulfilled'?'#7ee787':x.status==='rejected'?'#ff9d9d':'#ffd783';return '<div class="xst-req"><span>'+esc(x.title)+' · '+esc(x.cost)+' XP</span><b style="color:'+col+'">'+esc(x.status)+'</b></div>';}).join(''):'<p style="font-size:13px;color:#c5b3d6">Nothing yet - your claim requests will show here. The owner reviews each one.</p>')+
   '</div>';
  root.innerHTML=html;
  root.querySelectorAll('[data-tab]').forEach(function(b){b.addEventListener('click',function(){tab=b.getAttribute('data-tab');render();});});
  root.querySelectorAll('[data-claim]').forEach(function(b){b.addEventListener('click',function(){
   if(b.disabled)return;
   if(!confirm('Claim "'+b.getAttribute('data-title')+'" for '+b.getAttribute('data-cost')+' XP? The owner reviews every request.'))return;
   b.disabled=true;var msg=root.querySelector('[data-msg]');msg.textContent='Sending claim…';
   api.uRpc('redeem_reward',{p_item:parseInt(b.getAttribute('data-claim'),10)}).then(function(r){
    if(r&&r.ok){msg.textContent='Claim sent! The owner will review it. New balance: '+r.balance+' XP.';load();}
    else{msg.textContent=(r&&r.error)||'Could not claim right now.';b.disabled=false;}
   }).catch(function(){msg.textContent='Network error - try again.';b.disabled=false;});
  });});
 }
 function load(){
  root.innerHTML='<div class="xst"><div class="xst-head"><h2>XP REWARDS STORE</h2></div><p style="text-align:center;color:#c5b3d6">Loading…</p></div>';
  Promise.all([
   api.user().catch(function(){return null;}),
   fetch(api.url+'/rest/v1/rewards_catalog?active=eq.true&select=id,title,description,cost,kind&order=sort.asc,cost.asc',{headers:{apikey:api.key}}).then(function(r){return r.json();}).catch(function(){return [];})
  ]).then(function(d){
   me=d[0];catalog=Array.isArray(d[1])?d[1]:[];
   if(!me){balance=null;mine=[];render();return null;}
   return api.uRpc('points_balance',{}).then(function(b){balance=(b&&b.ok)?b.balance:null;}).then(function(){return api.uRpc('my_redemptions',{});}).then(function(r){mine=(r&&r.rows)||[];});
  }).then(function(){render();}).catch(function(){render();});
 }
 load();
}
if(document.readyState==='complete')boot();else window.addEventListener('load',boot);
})();
