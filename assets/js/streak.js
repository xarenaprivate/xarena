/* Daily Streak Badges - pixel-faithful to mockup 4f4e9deb. Real streak (player_game_stats via my_game_stats),
   real check-in (daily_checkin RPC), real badges. XP only - never gold coins, never cash. */
(function(){
'use strict';
function esc(s){return String(s==null?'':s).replace(/[&<>"']/g,function(c){return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c];});}
var FLAME='<svg width="34" height="34" viewBox="0 0 24 24" fill="none" stroke="#ffd23f" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M12 2.5c1 3-1.5 4.5-2.5 6C8.2 10.3 8 11.6 8 13a4.5 4.5 0 0 0 9 .5c.5-2-.5-3.5-1.5-4.7C14 7 13 4.5 12 2.5Z" fill="rgba(255,210,63,.16)"/><path d="M10.5 14.5a2 2 0 1 0 4 .5"/></svg>';
var CHECK='<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="M4 12.5l5 5L20 6.5"/></svg>';
var LOCK='<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#9d8cc2" stroke-width="1.8" stroke-linecap="round"><rect x="5" y="11" width="14" height="9" rx="2"/><path d="M8 11V8a4 4 0 0 1 8 0v3"/></svg>';
var MILESTONES=[
 {key:'streak_3',days:3,name:'Warming Up',desc:'3-day login streak'},
 {key:'streak_7',days:7,name:'On Fire',desc:'7-day login streak'},
 {key:'streak_30',days:30,name:'Unstoppable',desc:'30-day login streak'}
];
function boot(){
 if(!window.xrApi)return;
 var api=window.xrApi,app=document.getElementById('streak-app');
 if(!app)return;
 api.user().then(function(u){
  if(!u){app.innerHTML='<div class="xst-hero"><p style="color:#c9b8e6;margin:6px 0">Log in on the <a href="account.html" style="color:#ffd23f">Account page</a> - your real streak and badges show here.</p></div>';return;}
  app.innerHTML='<p style="text-align:center;color:#c9b8e6">Loading your streak…</p>';
  load();
  function load(){
   api.uRpc('my_game_stats',{}).then(function(r){
    if(!r||!r.ok){app.innerHTML='<p style="text-align:center;color:#c9b8e6">Could not load your streak - try again in a moment.</p>';return;}
    render(r);
   }).catch(function(){app.innerHTML='<p style="text-align:center;color:#c9b8e6">Could not load your streak - try again in a moment.</p>';});
  }
  function render(s){
   var streak=s.streak||0,claimed=!!s.checked_in_today,badges=Array.isArray(s.badges)?s.badges:[];
   var next=MILESTONES.filter(function(m){return badges.indexOf(m.key)<0;})[0];
   var pct=next?Math.min(100,Math.round(100*streak/next.days)):100;
   var weekDay=((streak-1)%7)+1;if(streak===0)weekDay=0;
   app.innerHTML=
    '<div class="xst-hero"><div style="display:flex;align-items:center;justify-content:center;gap:10px">'+FLAME+'<div style="font-family:Orbitron;font-weight:900;font-size:26px;color:#ffd23f;letter-spacing:1px">'+streak+'-DAY STREAK</div></div>'+
     '<p style="color:#c9b8e6;font-size:13.5px;margin:8px 0 0">'+(next?('Next badge: <b style="color:#ffe9a8">'+esc(next.name)+'</b> at '+next.days+' days'):'Every streak badge earned - keep the run alive')+'</p>'+
     '<div style="height:9px;border-radius:6px;background:rgba(13,5,24,.8);border:1px solid rgba(255,210,63,.3);margin-top:12px;overflow:hidden"><div style="height:100%;width:'+pct+'%;background:linear-gradient(90deg,#f5b301,#ffd23f)"></div></div>'+
     '<p style="font-size:12.5px;color:#9d8cc2;margin:10px 0 0">'+(claimed?'Today claimed ✓ - come back tomorrow':'Today is still open - claim it below')+'</p></div>'+
    '<div class="xnlv-h2">DAILY LOGIN STREAK</div>'+
    '<div class="xst-days">'+[1,2,3,4,5,6,7].map(function(d){
      return '<div class="xst-day'+(d<=weekDay?' on':'')+'"><div class="xst-dot">'+(d<=weekDay?CHECK:'<span style="font-size:12px">'+d+'</span>')+'</div><div class="dl">DAY '+d+'</div></div>';
     }).join('')+'</div>'+
    '<p class="xvt-note">'+(streak>0?'Day '+Math.min(streak,7)+' of this streak'+(streak>7?' - day '+streak+' total':''):'Check in today to start day 1')+'</p>'+
    (claimed?'':'<button type="button" class="xvt-confirm" id="xst-claim">CLAIM TODAY</button>')+
    '<div class="xnlv-h2">STREAK BADGES</div>'+
    MILESTONES.map(function(m){
     var got=badges.indexOf(m.key)>=0;
     return '<div class="xst-ms'+(got?'':' lock')+'"><div class="xst-badge-ic">'+(got?FLAME:LOCK)+'</div><div style="flex:1"><h3>'+esc(m.name)+'</h3><p>'+esc(m.desc)+'</p></div><span class="xst-state '+(got?'got':'lock')+'">'+(got?'EARNED ✓':streak+'/'+m.days)+'</span></div>';
    }).join('')+
    '<p class="xvt-note">Every check-in pays real XP (5 XP + up to 10 bonus for long streaks). XP only - never cash.</p>';
   var btn=document.getElementById('xst-claim');
   if(btn)btn.addEventListener('click',function(){
    btn.disabled=true;btn.textContent='CLAIMING…';
    api.uRpc('daily_checkin',{}).then(function(r){
     if(r&&r.ok){
      if(window.xrSfx)xrSfx.play('win');
      if(window.xrToast)xrToast(r.already?'Already claimed today':'Checked in - +'+(r.awarded||0)+' XP'+(r.new_badges&&r.new_badges.length?' · new badge!':''));
      load();
     }else{btn.disabled=false;btn.textContent='CLAIM TODAY';if(window.xrToast)xrToast((r&&r.error)||'Check-in failed');}
    }).catch(function(){btn.disabled=false;btn.textContent='CLAIM TODAY';});
   });
  }
 }).catch(function(){});
}
if(document.readyState==='complete')boot();else window.addEventListener('load',boot);
})();
