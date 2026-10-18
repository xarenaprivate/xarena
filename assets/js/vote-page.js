/* Fan Voting MVP - pixel-faithful to mockup 58f21885. Real polls only (active_polls/poll_results/cast_vote).
   One vote per account, changeable while open (RPC upserts). No fake players/avatars/counts. */
(function(){
'use strict';
function esc(s){return String(s==null?'':s).replace(/[&<>"']/g,function(c){return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c];});}
var IC_CLOCK='<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#ffe9a8" stroke-width="2" stroke-linecap="round"><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 3"/></svg>';
var IC_USERS='<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#ffe9a8" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="9" cy="8" r="3"/><path d="M3 20c.6-3.4 2.9-5 6-5s5.4 1.6 6 5"/><circle cx="17" cy="10" r="2.4"/><path d="M15.5 15.4c2.8.1 4.9 1.6 5.5 4.6"/></svg>';
function boot(){
 if(!window.xrApi)return;
 var api=window.xrApi,app=document.getElementById('vote-app');
 if(!app)return;
 var loggedIn=false,poll=null,res=null,sel=null,busy=false;
 function ends(){
  if(!poll||!poll.ends_at)return 'Open until the owner closes it';
  var ms=new Date(poll.ends_at)-Date.now();
  if(ms<=0)return 'Closing…';
  var m=Math.floor(ms/60000),h=Math.floor(m/60),d=Math.floor(h/24);
  if(d>0)return 'Ends in '+d+'d '+(h%24)+'h';
  if(h>0)return 'Ends in '+h+'h '+(m%60)+'m';
  return 'Ends in '+Math.max(m,1)+'m';
 }
 function render(){
  if(!poll){app.innerHTML='<div class="xst-hero" style="text-align:center"><p style="color:#c9b8e6;margin:6px 0">No vote is open right now - the next one lands here.</p></div>';return;}
  var pst=(res&&res.poll&&res.poll.status)||poll.status||'open';
  var closed=pst!=='open';
  var total=res&&res.total,hide=(total==null);
  var leader=null;
  if(!hide&&res.options.length){leader=res.options.reduce(function(a,b){return (b.votes||0)>(a.votes||0)?b:a;});}
  app.innerHTML=
   '<div style="text-align:center;margin-bottom:6px"><span class="xnlv-pill"><span class="dot"></span>'+(closed?'CLOSED':'LIVE')+'</span></div>'+
   '<h2 style="text-align:center;font-family:Orbitron;font-weight:800;font-size:19px;color:#ffe9a8;margin:0 0 14px">'+esc(poll.title)+'</h2>'+
   '<div class="xchips"><span class="xchip">'+IC_CLOCK+' '+esc(ends())+'</span>'+(hide?'':'<span class="xchip">'+IC_USERS+' '+total+' vote'+(total===1?'':'s')+'</span>')+'</div>'+
   res.options.map(function(o){
    var pct=(!hide&&total)?Math.round(100*(o.votes||0)/Math.max(total,1)):null;
    var isLead=leader&&leader.id===o.id&&total>0;
    var mine=res.my_vote===o.id;
    return '<button type="button" class="xvt-opt'+(sel===o.id?' sel':'')+'" data-opt="'+o.id+'"'+(closed?' disabled style="cursor:default"':'')+'>'+
     (pct!==null?'<span class="bar" style="width:'+pct+'%"></span>':'')+
     '<span class="row"><span class="lbl">'+esc(o.label)+'</span>'+(isLead?'<span class="xvt-lead">MVP<br>LEADING</span>':'')+'</span>'+
     '<span class="xvt-meta"><span>'+(mine?'<b>Your vote</b>':'&nbsp;')+'</span><span>'+(pct!==null?'<b>'+(o.votes||0)+' votes · '+pct+'%</b>':'')+'</span></span></button>';
   }).join('')+
   (closed?'<p class="xvt-note">Voting is closed - final counts above.</p>':
    '<button type="button" class="xvt-confirm" id="xvt-go" disabled>CONFIRM VOTE</button>'+
    '<p class="xvt-note">'+(loggedIn?'You can change your vote until voting ends.':'<a href="account.html" style="color:#ffd23f">Log in</a> to vote - one vote per player, changeable until voting ends.')+'</p>');
  app.querySelectorAll('[data-opt]').forEach(function(b){b.addEventListener('click',function(){if(closed)return;sel=b.getAttribute('data-opt');render();});});
  var go=document.getElementById('xvt-go');
  if(go)go.addEventListener('click',function(){
   if(!sel||busy)return;
   if(!loggedIn){if(window.xrToast)xrToast('Log in first - one vote per player account.');return;}
   busy=true;go.disabled=true;go.textContent='COUNTING…';
   api.uRpc('cast_vote',{p_poll:poll.id,p_option:sel}).then(function(r){
    busy=false;
    if(r&&r.ok){if(window.xrSfx)xrSfx.play('win');load();}
    else{go.disabled=false;go.textContent='CONFIRM VOTE';if(window.xrToast)xrToast((r&&r.error)||'Could not vote');}
   }).catch(function(){busy=false;go.disabled=false;go.textContent='CONFIRM VOTE';});
  });
 }
 function load(){
  api.uRpc('active_polls',{}).then(function(polls){
   poll=(Array.isArray(polls)&&polls.length)?polls[0]:null;
   if(!poll){res=null;render();return;}
   api.uRpc('poll_results',{p_poll:poll.id}).then(function(r){res=(r&&r.ok)?r:null;sel=res&&res.my_vote?res.my_vote:null;render();});
  }).catch(function(){app.innerHTML='<p style="text-align:center;color:#c9b8e6">Could not load the vote - check your connection.</p>';});
 }
 api.user().then(function(u){loggedIn=!!u;load();});
 setInterval(function(){if(poll&&poll.ends_at){var n=app.querySelector('.xchip');if(n)n.innerHTML=IC_CLOCK+' '+esc(ends());}},30000);
}
if(document.readyState==='complete')boot();else window.addEventListener('load',boot);
})();
