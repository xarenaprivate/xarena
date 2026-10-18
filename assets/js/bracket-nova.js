(function(){
'use strict';
function esc(s){return String(s==null?'':s).replace(/[&<>"']/g,function(c){return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c];});}
function boot(){
 var old=document.getElementById('bracket-view');if(!old||!window.xrApi)return;
 var api=window.xrApi, look=localStorage.getItem('xr_bracket_look')||'nova';
 var root=document.createElement('div');root.id='bracket-nova';old.parentNode.insertBefore(root,old);
 var toggle=document.createElement('button');toggle.className='btn btn-ghost btn-sm';toggle.textContent=look==='nova'?'Classic bracket':'Nova bracket';toggle.style.marginBottom='12px';old.parentNode.insertBefore(toggle,root);
 function apply(){old.style.display=look==='nova'?'none':'';root.style.display=look==='nova'?'':'none';toggle.textContent=look==='nova'?'Classic bracket':'Nova bracket';}
 toggle.addEventListener('click',function(){look=look==='nova'?'classic':'nova';localStorage.setItem('xr_bracket_look',look);apply();});apply();
 var rows=[],mine=[],counts=[],tourMap={};
 function render(){
  var byTour={};
  rows.forEach(function(r){var k=r.tournament_id||'';(byTour[k]||(byTour[k]=[])).push(r);});
  var tourKeys=Object.keys(byTour);
  var sections=tourKeys.map(function(tk){
   var trows=byTour[tk],tname=tourMap[tk]||(tk||'Tournament');
   var rounds={};trows.forEach(function(r){(rounds[r.round]||(rounds[r.round]=[])).push(r);});
   var nums=Object.keys(rounds).map(Number).sort(function(a,b){return a-b;});
   var max=nums.length?nums[nums.length-1]:null,finals=max===null?[]:rounds[max];
   var summary=finals.length===1?'<div class="final"><div>ROAD TO THE CROWN</div><h3>'+ (finals[0].winner?esc(finals[0].winner)+' · CHAMPION':'GRAND FINAL')+'</h3><div class="time">'+esc(finals[0].squad_a||'To be decided')+' vs '+esc(finals[0].squad_b||'To be decided')+'</div></div><div class="line"></div>':'';
   return '<div class="xbn-tour"><h3 class="xbn-tour-title">'+esc(tname)+'</h3>'+summary+
    nums.map(function(rd){return '<div class="round"><div class="round-title">'+(rd===max&&finals.length===1?'Final':'Round '+rd)+'</div><div class="games">'+rounds[rd].map(card).join('')+'</div></div>';}).join('')+'</div>';
  }).join('');
  root.innerHTML='<div class="xbn"><h2>TOURNAMENT BRACKET</h2><div class="season">XARENA ESPORTS · OFFICIAL RESULTS</div>'+
   (rows.length?sections:'<div class="empty"><h3>Bracket not drawn yet</h3><p>Real squads, match times and results will appear after match control publishes the bracket.</p></div>')+
   '<div class="note">XP only, no cash. Free predictions. One pick per account per match; changes close when the match starts.</div><div class="xbn-nav"><a href="#bracket">Bracket</a><a href="leaderboard.html">Standings</a><a href="tournaments.html#rooms">Matches</a><a href="play.html">Rewards</a><a href="profile.html">Profile</a></div></div>';
  root.querySelectorAll('[data-predict]').forEach(function(b){b.addEventListener('click',function(){
   var id=Number(b.dataset.predict),pick=b.dataset.pick,box=root.querySelector('[data-message="'+id+'"]');
   b.disabled=true;box.textContent='Saving your pick…';
   api.user().then(function(u){if(!u){box.innerHTML='<a href="account.html">Log in to predict</a>';b.disabled=false;return null;}return api.uRpc('predict_bracket',{p_match:id,p_pick:pick});}).then(function(r){if(!r)return;if(!r.ok){box.textContent=r.error||'Could not save your pick.';b.disabled=false;return;}load();}).catch(function(){box.textContent='Could not save. Please try again.';b.disabled=false;});
  });});
 }
 function card(m){
  var p=mine.find(function(x){return x.match_id===m.id;}),st=counts.find(function(x){return x.match_id===m.id;})||{},total=Number(st.a_count||0)+Number(st.b_count||0),start=m.starts_at?new Date(m.starts_at):null;
  var open=!m.winner&&m.squad_a&&m.squad_b&&start&&Number.isFinite(start.getTime())&&Date.now()<start.getTime();
  function team(name,score){return '<div class="team"><div class="avatar">'+esc((name||'?').charAt(0).toUpperCase())+'</div><div class="name">'+esc(name||'To be decided')+'</div><div class="score">'+(score==null?'–':esc(score))+'</div>'+(m.winner&&m.winner===name?'<span class="won">✓ WON</span>':'')+'</div>';}
  return '<div class="game"><div class="game-title">Match '+esc(m.position)+' · '+(m.winner?'Completed':start&&Date.now()>=start.getTime()?'Predictions locked':'Scheduled')+'</div><div class="teams">'+team(m.squad_a,m.score_a)+team(m.squad_b,m.score_b)+'</div><div class="time">'+(start?esc(start.toLocaleString('en-IN',{timeZone:'Asia/Kolkata',day:'numeric',month:'short',hour:'2-digit',minute:'2-digit'}))+' IST':'Start time not announced')+'</div><div class="time">'+total+' prediction'+(total===1?'':'s')+(p?' · Your pick: '+esc(p.pick==='a'?m.squad_a:m.squad_b):'')+'</div>'+
   (open?'<div class="predict"><button data-predict="'+m.id+'" data-pick="a" aria-pressed="'+!!(p&&p.pick==='a')+'">'+esc(m.squad_a)+'</button><button data-predict="'+m.id+'" data-pick="b" aria-pressed="'+!!(p&&p.pick==='b')+'">'+esc(m.squad_b)+'</button></div>':'<div class="note">'+(!start?'Predictions open after a start time is set.':m.winner?'Result recorded. Correct picks earn 50 XP after settlement.':'Predictions are locked.')+'</div>')+'<div class="message" data-message="'+m.id+'"></div></div>';
 }
 function load(){root.innerHTML='<div class="xbn"><h2>TOURNAMENT BRACKET</h2><p class="note">Loading real matches…</p></div>';Promise.all([
  fetch(api.url+'/rest/v1/bracket_matches?select=*&order=tournament_id.asc,round.asc,position.asc',{headers:{apikey:api.key}}).then(function(r){if(!r.ok)throw Error();return r.json();}),
  api.uRpc('bracket_prediction_stats'),api.uRpc('my_bracket_predictions'),
  fetch(api.url+'/rest/v1/tournaments?select=id,name',{headers:{apikey:api.key}}).then(function(r){if(!r.ok)return [];return r.json();})
 ]).then(function(d){rows=Array.isArray(d[0])?d[0]:[];counts=Array.isArray(d[1])?d[1]:[];mine=Array.isArray(d[2])?d[2]:[];tourMap={};(Array.isArray(d[3])?d[3]:[]).forEach(function(t){tourMap[t.id]=t.name;});render();}).catch(function(){root.innerHTML='<div class="xbn"><h2>TOURNAMENT BRACKET</h2><div class="empty"><h3>Could not load the bracket</h3><p>Check your connection and refresh. No result has been changed.</p></div></div>';});}
 load();
}
if(document.readyState==='complete')boot();else window.addEventListener('load',boot);
})();
