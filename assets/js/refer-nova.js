/* Refer & Earn - Nova look (mockup 242fcdee). Real data: my_referral / referral_apply RPCs.
   Pool label: Store Points (points_ledger) - separate from Predict XP, per owner decision. */
(function(){
'use strict';
function esc(s){return String(s==null?'':s).replace(/[&<>"']/g,function(c){return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c];});}
function svg(p){return '<svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="#ffd783" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">'+p+'</svg>';}
var IC_STEP2=svg('<circle cx="9" cy="8" r="3.4"/><path d="M3 20a6 6 0 0 1 12 0"/><path d="M18 8v6M15 11h6"/>');
var IC_STEP1B=svg('<circle cx="10" cy="8" r="3.4"/><path d="M4 20a6 6 0 0 1 12 0"/><path d="M15 12c2.5 0 5-2 5-5"/>');
var IC_STEP3=svg('<path d="M8 4h8v4a4 4 0 0 1-8 0Z"/><path d="M8 5H5a3 3 0 0 0 3 4M16 5h3a3 3 0 0 1-3 4"/><path d="M12 12v3M8 20h8M10 15h4l1 5h-6Z"/>');
var IC_SHARE=svg('<path d="M12 15V4M8 8l4-4 4 4"/><path d="M5 12v7h14v-7"/>');
var IC_FRIENDS=svg('<circle cx="9" cy="8" r="3.2"/><path d="M3.5 19a5.5 5.5 0 0 1 11 0"/><circle cx="17" cy="9" r="2.4"/><path d="M15.5 13.6A4.5 4.5 0 0 1 20.5 18"/>');
var IC_EARN=svg('<circle cx="12" cy="12" r="9"/><path d="M12 8.5v7M8.5 12.5L12 9l3.5 3.5"/>');
function boot(){
 var anchor=document.getElementById('ref-card');if(!anchor||!window.xrApi)return;
 var api=window.xrApi,look=localStorage.getItem('xr_refer_look')||'nova';
 var root=document.createElement('div');root.id='refer-nova';anchor.parentNode.insertBefore(root,anchor);
 var toggle=document.createElement('button');toggle.className='btn btn-ghost btn-sm';toggle.style.marginBottom='12px';anchor.parentNode.insertBefore(toggle,anchor);
 function apply(){var nova=look==='nova';root.style.display=nova?'':'none';anchor.style.display=nova?'none':'';toggle.textContent=nova?'Classic invite':'Nova refer';}
 toggle.addEventListener('click',function(){look=look==='nova'?'classic':'nova';localStorage.setItem('xr_refer_look',look);apply();});apply();
 var me=null,info=null;
 function fmt(n){n=Number(n)||0;return n.toLocaleString('en-IN');}
 function render(){
  if(!me){root.innerHTML='<div class="xrf"><div class="xrf-head"><a class="xrf-back" href="play.html">←</a><h2>Refer &amp; Earn</h2><span style="width:30px"></span></div><div class="xrf-login"><p>Log in to get your invite code and start earning Store Points.</p><a href="account.html">Log in / Sign up</a></div><p class="xrf-disc">*Store Points can be used to unlock exclusive in-game cosmetics, badges, and leaderboard boosts. No monetary value. XP only.</p></div>';return;}
  if(!info){root.innerHTML='<div class="xrf"><div class="xrf-head"><a class="xrf-back" href="play.html">←</a><h2>Refer &amp; Earn</h2><span style="width:30px"></span></div><p class="xrf-note">Loading…</p></div>';return;}
  var link='https://xarenaprivate.github.io/xarena/account.html?ref='+encodeURIComponent(info.code);
  var msg='Join me on XARENA ESPORTS - free Free Fire tournaments. Sign up with my code '+info.code+' and we both get 50 Store Points (XP only, never cash): '+link;
  root.innerHTML='<div class="xrf">'+
   '<div class="xrf-head"><a class="xrf-back" href="play.html">←</a><h2>Refer &amp; Earn</h2><button class="xrf-help" data-help>?</button></div>'+
   '<div class="xrf-brand"><b>XARENA ESPORTS</b><span>EARN POINTS · LEVEL UP · UNLOCK REWARDS</span></div>'+
   '<div class="xrf-bal"><div class="lbl">Your Store Points Balance</div><div class="amt" data-bal>…</div><div class="sub">No cash rewards - Store Points only</div></div>'+
   '<div class="xrf-sect"><h3>Refer &amp; Earn Store Points</h3><p>Invite friends and earn 50 Store Points for each friend who joins XARENA ESPORTS</p><span class="xrf-chip">50 Store Points per successful invite · XP only, no cash</span>'+
   '<div class="xrf-steps" style="margin-top:14px">'+
    '<div class="xrf-step"><span class="n">1</span><div class="ic">'+IC_SHARE+'</div><div class="t">Share your code</div></div>'+
    '<div class="xrf-step"><span class="n">2</span><div class="ic">'+IC_STEP1B+'</div><div class="t">Friend signs up</div></div>'+
    '<div class="xrf-step"><span class="n">3</span><div class="ic">'+IC_STEP3+'</div><div class="t">You both earn 50 Store Points</div></div></div></div>'+
   '<div class="xrf-codelbl">Your Referral Code</div>'+
   '<div class="xrf-code"><b>'+esc(info.code)+'</b><button class="xrf-copy" data-copy>Copy</button></div>'+
   '<button class="xrf-share" data-share>'+IC_SHARE+' Share Code with Friends</button>'+
   '<div class="xrf-stats">'+
    '<div class="xrf-stat">'+IC_FRIENDS+'<div><b>'+fmt(info.count)+'</b><span>Friends Joined</span></div></div>'+
    '<div class="xrf-stat">'+IC_EARN+'<div><b>+'+fmt(info.earned)+'</b><span>Store Points Earned</span></div></div></div>'+
   (info.applied?'':'<div class="xrf-apply"><p>Have a friend\'s code? New accounts only (first 48 hours).</p><div class="xrf-applyrow"><input data-code-in maxlength="12" placeholder="FRIEND\'S CODE" autocapitalize="characters"><button data-apply>Apply</button></div><p class="xrf-note" data-note></p></div>')+
   '<p class="xrf-disc">*Store Points can be used to unlock exclusive in-game cosmetics, badges, and leaderboard boosts. No monetary value. XP only.</p>'+
  '</div>';
  var balEl=root.querySelector('[data-bal]');
  api.uRpc('points_balance',{}).then(function(b){balEl.textContent=fmt(b&&b.ok?b.balance:0)+' PTS';}).catch(function(){balEl.textContent='—';});
  root.querySelector('[data-help]').onclick=function(){alert('Share your code. When a friend signs up with it (new accounts, first 48 hours), you both get 50 Store Points. Store Points unlock rewards in the XP Store - they are never cash.');};
  function copyText(t,cb){if(navigator.clipboard&&navigator.clipboard.writeText)navigator.clipboard.writeText(t).then(cb,cb);else{var x=document.createElement('textarea');x.value=t;document.body.appendChild(x);x.select();try{document.execCommand('copy');}catch(e){}x.remove();cb();}}
  root.querySelector('[data-copy]').onclick=function(){copyText(info.code,function(){if(window.xrToast)xrToast('Code copied');});};
  root.querySelector('[data-share]').onclick=function(){if(navigator.share)navigator.share({title:'XARENA ESPORTS',text:msg}).catch(function(){});else copyText(msg,function(){if(window.xrToast)xrToast('Invite copied - paste it anywhere');});};
  var ap=root.querySelector('[data-apply]');
  if(ap)ap.onclick=function(){var code=root.querySelector('[data-code-in]').value.trim();if(!code)return;ap.disabled=true;
   api.uRpc('referral_apply',{p_code:code}).then(function(a){var note=root.querySelector('[data-note]');
    if(a&&a.ok){note.innerHTML='<b style="color:#8ff0b8">Applied - you got '+a.you_got+' Store Points, your friend got '+a.friend_got+'.</b>';load();}
    else{note.textContent=(a&&a.error)||'Could not apply the code.';ap.disabled=false;}}).catch(function(){ap.disabled=false;});};
 }
 function load(){
  api.user().then(function(u){me=u;}).catch(function(){me=null;}).then(function(){
   if(!me){render();return null;}
   return api.uRpc('my_referral',{}).then(function(r){info=(r&&r.ok)?r:null;});
  }).then(function(){render();}).catch(function(){render();});
 }
 window.__xrfReload=load;
 load();
}
if(document.readyState==='complete')boot();else window.addEventListener('load',boot);
})();
