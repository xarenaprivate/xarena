/* Settings · Security (mockup 2977249b, v7.11). Every row is REAL:
   Privacy toggle -> profiles.show_activity via set_my_prefs; Alerts toggle -> profiles.alerts_opt_in;
   Password -> real Supabase reset email; Report player -> real cheat_report flow; Log out -> real signOut.
   No fake 2FA / device lists / anti-cheat claims. */
(function(){
'use strict';
function esc(s){return String(s==null?'':s).replace(/[&<>"']/g,function(c){return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c];});}
function svg(p){return '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#c9b8e6" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">'+p+'</svg>';}
var IC_USER=svg('<circle cx="12" cy="8" r="3.6"/><path d="M5 20a7 7 0 0 1 14 0"/>');
var IC_PRIV=svg('<path d="M12 3l7 3v5c0 4.5-3 8-7 9-4-1-7-4.5-7-9V6Z"/><circle cx="12" cy="11" r="2"/><path d="M12 13v3"/>');
var IC_PHONE=svg('<rect x="7" y="3" width="10" height="18" rx="2"/><path d="M11 17.5h2"/>');
var IC_KEY=svg('<circle cx="8" cy="14" r="4"/><path d="M11 11l8-8M16 5l2.5 2.5M13.5 7.5 16 10"/>');
var IC_SCALE=svg('<path d="M12 4v16M5 20h14M12 6l-5 3 5 3 5-3Z"/><path d="M4 13l2 5 2-5M16 13l2 5 2-5"/>');
var IC_BELL=svg('<path d="M6 9a6 6 0 0 1 12 0c0 5 2 6 2 6H4s2-1 2-6"/><path d="M10 19a2 2 0 0 0 4 0"/>');
var IC_OUT=svg('<path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><path d="M16 17l5-5-5-5M21 12H9"/>');
var IC_GEAR=svg('<circle cx="12" cy="12" r="3"/><path d="M19 12a7 7 0 0 0-.1-1.2l2-1.6-2-3.4-2.4 1a7 7 0 0 0-2-1.2L14 3h-4l-.5 2.6a7 7 0 0 0-2 1.2l-2.4-1-2 3.4 2 1.6A7 7 0 0 0 5 12c0 .4 0 .8.1 1.2l-2 1.6 2 3.4 2.4-1a7 7 0 0 0 2 1.2L10 21h4l.5-2.6a7 7 0 0 0 2-1.2l2.4 1 2-3.4-2-1.6c.07-.4.1-.8.1-1.2Z"/>');
function boot(){
 var anchor=document.querySelector('#xr-settings-anchor');
 if(!anchor||!window.xrApi)return;
 var api=window.xrApi,look=localStorage.getItem('xr_settings_look')||'nova';
 var root=document.createElement('div');root.id='settings-nova';anchor.parentNode.insertBefore(root,anchor);
 var toggle=document.createElement('button');toggle.className='btn btn-ghost btn-sm';toggle.style.margin='14px 0 4px';anchor.parentNode.insertBefore(toggle,anchor);
 function apply(){var nova=look==='nova';root.style.display=nova?'':'none';anchor.style.display=nova?'none':'';toggle.textContent=nova?'Classic settings':'Nova settings';}
 toggle.addEventListener('click',function(){look=look==='nova'?'classic':'nova';localStorage.setItem('xr_settings_look',look);apply();});apply();
 var me=null,prof=null,prefs={show_activity:true,alerts_opt_in:true},xp=0;
 function lvl(x){return Math.floor((Number(x)||0)/100)+1;}
 function render(){
  if(!me){root.innerHTML='<div class="xss"><div class="xss-head">'+IC_GEAR+'<h3>SETTINGS · SECURITY</h3></div><p style="text-align:center;color:#b7a6d8;font-size:14px;padding:12px 0">Log in to manage your security and privacy.</p></div>';return;}
  var name=(prof&&(prof.ign||prof.username))||'Player';
  var av=(prof&&prof.avatar_url)?'<img src="'+esc(prof.avatar_url)+'" alt="">':IC_USER;
  var rank=(prof&&prof.ff_rank)?' · '+esc(prof.ff_rank):'';
  root.innerHTML='<div class="xss">'+
   '<div class="xss-head">'+IC_GEAR+'<h3>SETTINGS · SECURITY</h3></div>'+
   '<div class="xss-brand"><b>XARENA ESPORTS</b><span>PLAYER SECURITY &amp; PRIVACY</span></div>'+
   '<a class="xss-me" href="profile.html"><span class="av">'+av+'</span><span><b>'+esc(name)+'</b><span>Player · LVL '+lvl(xp)+rank+'</span></span><span class="chev">›</span></a>'+
   '<div class="xss-lbl">ACCOUNT</div>'+
   '<a class="xss-row" href="profile.html"><span class="ic">'+IC_USER+'</span><span class="tx"><b>Profile</b><span>Name, game ID, linked accounts</span></span><span class="chev">›</span></a>'+
   '<button class="xss-row" data-priv><span class="ic">'+IC_PRIV+'</span><span class="tx"><b>Privacy</b><span>Visibility, data, activity status</span></span><span class="xss-pill'+(prefs.show_activity?'':' off')+'" data-priv-pill>'+(prefs.show_activity?'ON':'OFF')+'</span></button>'+
   '<div class="xss-lbl">SECURITY</div>'+
   '<div class="xss-row" style="cursor:default"><span class="ic">'+IC_PHONE+'</span><span class="tx"><b>Login Devices</b><span>This device</span></span></div>'+
   '<button class="xss-row" data-pass><span class="ic">'+IC_KEY+'</span><span class="tx"><b>Password</b><span>Email me a secure reset link</span></span><span class="chev">›</span></button>'+
   '<div class="xss-note" data-note></div>'+
   '<div class="xss-lbl">FAIR PLAY</div>'+
   '<a class="xss-row" href="feed.html#report"><span class="ic">'+IC_SCALE+'</span><span class="tx"><b>Fair Play &amp; Reports</b><span>Report a player - the owner reviews every report</span></span><span class="chev">›</span></a>'+
   '<button class="xss-row" data-alerts><span class="ic">'+IC_BELL+'</span><span class="tx"><b>Security Alerts</b><span>Account &amp; winner alerts in the app</span></span><span class="xss-pill'+(prefs.alerts_opt_in?'':' off')+'" data-alerts-pill>'+(prefs.alerts_opt_in?'ON':'OFF')+'</span></button>'+
   '<button class="xss-out" data-out>'+IC_OUT+' LOG OUT</button>'+
   '<p class="xss-foot">XARENA ESPORTS · v7.11.0 · Secure Session</p>'+
  '</div>';
  var note=root.querySelector('[data-note]');
  root.querySelector('[data-priv]').onclick=function(){prefs.show_activity=!prefs.show_activity;save();};
  root.querySelector('[data-alerts]').onclick=function(){prefs.alerts_opt_in=!prefs.alerts_opt_in;save();};
  function save(){api.uRpc('set_my_prefs',{p_show_activity:prefs.show_activity,p_alerts:prefs.alerts_opt_in}).then(function(r){if(!(r&&r.ok)){note.style.color='#ff9d9d';note.textContent=(r&&r.error)||'Could not save.';}render();}).catch(function(){render();});}
  root.querySelector('[data-pass]').onclick=function(){
   note.style.color='#9d8cc2';note.textContent='Sending reset email…';
   if(!(window.xrSb&&me.email)){note.style.color='#ff9d9d';note.textContent='No email on this account.';return;}
   window.xrSb.auth.resetPasswordForEmail(me.email,{redirectTo:location.origin+'/account.html'}).then(function(r){note.style.color=r.error?'#ff9d9d':'#7ee787';note.textContent=r.error?r.error.message:'Reset link sent to '+me.email;});
  };
  root.querySelector('[data-out]').onclick=function(){if(window.xrSb)window.xrSb.auth.signOut().then(function(){location.href='account.html';});};
 }
 function load(){
  api.user().then(function(u){me=u;}).catch(function(){me=null;}).then(function(){
   if(!me){render();return null;}
   return Promise.all([
    api.get('profiles','?id=eq.'+me.id+'&select=username,ign,ff_rank,avatar_url,show_activity,alerts_opt_in&limit=1').catch(function(){return [];}),
    api.uRpc('points_balance',{}).catch(function(){return null;})
   ]).then(function(d){
    if(Array.isArray(d[0])&&d[0][0]){prof=d[0][0];prefs.show_activity=prof.show_activity!==false;prefs.alerts_opt_in=prof.alerts_opt_in!==false;}
    if(d[1]&&d[1].ok)xp=d[1].balance;
   });
  }).then(function(){render();}).catch(function(){render();});
 }
 window.__xssReload=load;
 load();
}
if(document.readyState==='complete')boot();else window.addEventListener('load',boot);
})();
