/* Ping Check + Device Check - pixel-faithful to mockup aff1962c. REAL measurements only:
   latency/jitter timed against the XARENA API, device facts only where the browser exposes them.
   Never: fake server location, fake packet loss, fake CPU GHz. */
(function(){
'use strict';
function esc(s){return String(s==null?'':s).replace(/[&<>"']/g,function(c){return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c];});}
var API='https://oskccuajgbyabogbvedj.supabase.co/rest/v1/';
var KEY='sb_publishable_RltZjW7bXLT9DssLJVHkDA_cuHlcJo7';
var IC={
 wifi:'<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#ffd23f" stroke-width="1.8" stroke-linecap="round"><path d="M2.5 9a15 15 0 0 1 19 0M5.5 12.5a10 10 0 0 1 13 0M8.5 16a5 5 0 0 1 7 0"/><circle cx="12" cy="19" r="1.3" fill="#ffd23f"/></svg>',
 pulse:'<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#ffd23f" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M3 12h4l2-6 4 12 2-6h6"/></svg>',
 phone:'<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#ffd23f" stroke-width="1.8" stroke-linecap="round"><rect x="7" y="2.5" width="10" height="19" rx="2.5"/><path d="M11 18.5h2"/></svg>',
 chip:'<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#ffd23f" stroke-width="1.8" stroke-linecap="round"><rect x="6" y="6" width="12" height="12" rx="2"/><path d="M9 1.5v3M15 1.5v3M9 19.5v3M15 19.5v3M1.5 9h3M1.5 15h3M19.5 9h3M19.5 15h3"/></svg>',
 ram:'<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#ffd23f" stroke-width="1.8" stroke-linecap="round"><rect x="3" y="7" width="18" height="10" rx="2"/><path d="M7 11h2M11 11h2M15 11h2M7 17v2.5M17 17v2.5"/></svg>',
 disk:'<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#ffd23f" stroke-width="1.8" stroke-linecap="round"><ellipse cx="12" cy="6" rx="8" ry="3"/><path d="M4 6v6c0 1.7 3.6 3 8 3s8-1.3 8-3V6M4 12v6c0 1.7 3.6 3 8 3s8-1.3 8-3v-6"/></svg>'
};
function row(ic,label,val){return '<div class="xpg-row"><div class="ic">'+ic+'</div><div class="tx">'+label+'</div><div class="val">'+val+'</div></div>';}
function rate(ms){return ms<60?['Excellent','#7ee787']:ms<110?['Good','#7ee787']:ms<180?['Fair','#ffd23f']:['Slow','#ff8a8a'];}
function boot(){
 var app=document.getElementById('ping-app');if(!app)return;
 var running=false;
 function renderIdle(){
  app.innerHTML='<div class="xpg-circle" id="xpg-circle"><div class="xpg-ms" id="xpg-ms">--</div><div style="font-size:12px;color:#9d8cc2">ms</div><div class="xpg-rate" id="xpg-rate">Tap Run Full Check</div></div>'+
   '<div id="xpg-rows">'+row(IC.wifi,'Server','XARENA API')+row(IC.pulse,'Latency','not measured yet')+row(IC.pulse,'Jitter','not measured yet')+'</div>'+
   '<h2 class="xnlv-h2">DEVICE CHECK</h2><div id="xpg-device"></div>'+
   '<button type="button" class="xvt-confirm" id="xpg-go">RUN FULL CHECK</button>'+
   '<p class="xvt-note">Real measurements on your device and connection - nothing simulated.</p>';
  renderDevice();
  document.getElementById('xpg-go').addEventListener('click',run);
 }
 function renderDevice(){
  var rows='';
  var cores=navigator.hardwareConcurrency;
  rows+=row(IC.chip,'CPU cores',cores?cores+' cores':'not exposed by this browser');
  var mem=navigator.deviceMemory;
  rows+=row(IC.ram,'Memory (RAM)',mem?('about '+mem+' GB (browser estimate)'):'not exposed by this browser');
  rows+=row(IC.phone,'Screen',window.screen.width+' x '+window.screen.height+' px');
  var c=navigator.connection;
  rows+=row(IC.wifi,'Network',c?(esc(c.effectiveType||'?')+(c.downlink?' · ~'+c.downlink+' Mb/s':'')):'not exposed by this browser');
  var devBox=document.getElementById('xpg-device');
  devBox.innerHTML=rows+'<div id="xpg-storage">'+row(IC.disk,'Storage','measuring…')+'</div><div id="xpg-verdict"></div>';
  if(navigator.storage&&navigator.storage.estimate){
   navigator.storage.estimate().then(function(e){
    var free=e.quota?Math.round((e.quota-(e.usage||0))/1048576):null;
    var el=document.getElementById('xpg-storage');
    if(el)el.innerHTML=row(IC.disk,'App storage (this browser)',free!=null?('~'+free+' MB free of quota'):'not exposed');
   }).catch(function(){var el=document.getElementById('xpg-storage');if(el)el.innerHTML=row(IC.disk,'Storage','not exposed by this browser');});
  }else{var el=document.getElementById('xpg-storage');if(el)el.innerHTML=row(IC.disk,'Storage','not exposed by this browser');}
  var low=(mem&&mem<=3)||(cores&&cores<=4);
  document.getElementById('xpg-verdict').innerHTML='<div class="xpg-row" style="border-color:rgba(94,201,128,.5)"><div class="ic">'+IC.phone+'</div><div class="tx">'+(low?'<b>Low-end device detected.</b> The app is built light for phones like this - nothing heavy runs in the background.':'<b>Device looks comfortable</b> for the app - it is built to stay light either way.')+'</div></div>';
 }
 function sample(){
  var t0=performance.now();
  return fetch(API+'site_settings?select=key&limit=1&_=' + t0,{headers:{apikey:KEY},cache:'no-store'}).then(function(r){
   var dt=performance.now()-t0;
   if(!r.ok)throw new Error('bad');
   return dt;
  });
 }
 function run(){
  if(running)return;running=true;
  var go=document.getElementById('xpg-go');go.disabled=true;go.textContent='MEASURING…';
  var msEl=document.getElementById('xpg-ms'),rateEl=document.getElementById('xpg-rate');
  var samples=[],fails=0,i=0,N=8;
  function step(){
   sample().then(function(dt){samples.push(dt);}).catch(function(){fails++;}).then(function(){
    i++;
    if(samples.length){var cur=Math.round(samples[samples.length-1]);msEl.textContent=cur;var r=rate(cur);rateEl.textContent=r[0];rateEl.style.color=r[1];}
    if(i<N){setTimeout(step,180);return;}
    running=false;go.disabled=false;go.textContent='RUN FULL CHECK';
    if(!samples.length){
     msEl.textContent='--';rateEl.textContent='No connection';rateEl.style.color='#ff8a8a';
     document.getElementById('xpg-rows').innerHTML=row(IC.wifi,'Server','XARENA API - unreachable')+row(IC.pulse,'Latency','all '+N+' requests failed')+row(IC.pulse,'Jitter','-');
     return;
    }
    var avg=samples.reduce(function(a,b){return a+b;},0)/samples.length;
    var min=Math.min.apply(null,samples),max=Math.max.apply(null,samples);
    var jit=samples.length>1?samples.slice(1).reduce(function(a,b,k){return a+Math.abs(b-samples[k]);},0)/(samples.length-1):0;
    var r=rate(avg);msEl.textContent=Math.round(avg);rateEl.textContent=r[0]+' ping';rateEl.style.color=r[1];
    document.getElementById('xpg-rows').innerHTML=
     row(IC.wifi,'Server','XARENA API')+
     row(IC.pulse,'Latency (avg of '+samples.length+')',Math.round(avg)+' ms · best '+Math.round(min)+' · worst '+Math.round(max))+
     row(IC.pulse,'Jitter',Math.round(jit)+' ms'+(jit<15?' · steady':jit<40?' · a little uneven':' · uneven'))+
     (fails?row(IC.pulse,'Failed requests',fails+' of '+N+' - your connection dropped some'):'');
   });
  }
  step();
 }
 renderIdle();
}
if(document.readyState==='complete')boot();else window.addEventListener('load',boot);
})();
