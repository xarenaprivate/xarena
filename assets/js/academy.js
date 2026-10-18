/* Training Academy - pixel-faithful to mockup b859c2f2. Real drills; search + category chips filter the real list;
   progress saved on this device (honest label). No PREMIUM locks, no fake view counts. */
(function(){
'use strict';
var DRILLS=[
 {id:'aim-1',cat:'Aim',title:'Crosshair placement basics',mins:10,steps:['Keep your crosshair at head height wherever you move - never at the floor.','Walk common angles on Bermuda in training grounds for 5 minutes, holding head level.','Snap between two fixed points (door frames) 30 times, smooth not fast.']},
 {id:'aim-2',cat:'Aim',title:'Drag headshots (one-tap style)',mins:15,steps:['In training grounds, scope a red dot and drag upward through the head line - 20 reps slow.','Repeat at 2x speed. Misses are fine; rhythm first.','Finish with 10 drag attempts on moving dummies.']},
 {id:'move-1',cat:'Movement',title:'Jiggle peeking',mins:10,steps:['Pick a corner. Peek out 0.3s, back in - bait the shot, never commit.','Add a crouch at the end of every third peek.','Drill both left and right sides, 2 minutes each.']},
 {id:'move-2',cat:'Movement',title:'Gloo wall under pressure',mins:15,steps:['Have a friend (or a bot lobby) push you - wall first, then think.','Drill: wall + side-step + heal in under 4 seconds.','Practice walling mid-sprint, 15 reps.']},
 {id:'zone-1',cat:'Rotation',title:'Reading the zone',mins:10,steps:['Before the first zone closes, say your rotation route out loud.','Rotate EARLY on edge, LATE only with hard cover.','After each match, write one line: what killed my rotation?']},
 {id:'zone-2',cat:'Rotation',title:'High ground priority',mins:10,steps:['In your next 5 matches, take the highest ground inside zone before minute 6.','Note which compounds give sightlines into the next zone.','Trade kills for position - never the other way.']},
 {id:'team-1',cat:'Squad',title:'Trade fragging',mins:15,steps:['Never peek alone: pair up and peek 1 second apart.','If your teammate goes down, your job is the trade, not the rescue.','Call damage numbers instantly - "70 on one" wins fights.']},
 {id:'team-2',cat:'Squad',title:'IGL comms drill',mins:10,steps:['One voice per fight: the IGL calls, everyone else reports only.','Use short calls: location, count, action. "Two, hill 30, push on my mark."','Debrief 2 minutes after each match: one good call, one bad call.']}
];
var ICONS={
 Aim:'<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="#ffd23f" stroke-width="1.8" stroke-linecap="round"><circle cx="12" cy="12" r="7"/><circle cx="12" cy="12" r="2.5"/><path d="M12 2v3M12 19v3M2 12h3M19 12h3"/></svg>',
 Movement:'<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="#ffd23f" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M13 4l6 6-6 6M5 4l6 6-6 6"/></svg>',
 Rotation:'<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="#ffd23f" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M21 12a9 9 0 1 1-3-6.7"/><path d="M21 3v6h-6"/></svg>',
 Squad:'<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="#ffd23f" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><circle cx="8" cy="8" r="3"/><circle cx="17" cy="10" r="2.4"/><path d="M2.5 20c.6-3.4 2.8-5 5.5-5s4.9 1.6 5.5 5M13.5 15.6c2.9-.4 5.6 1 6.2 4.4"/></svg>'
};
function esc(s){return String(s==null?'':s).replace(/[&<>"']/g,function(c){return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c];});}
function boot(){
 var host=document.getElementById('academy-list');if(!host)return;
 var chipsBox=document.getElementById('academy-chips'),search=document.getElementById('academy-search'),prog=document.getElementById('academy-progress');
 var done={};try{done=JSON.parse(localStorage.getItem('xr_academy')||'{}');}catch(e){}
 var cats=['All'];DRILLS.forEach(function(d){if(cats.indexOf(d.cat)<0)cats.push(d.cat);});
 var activeCat='All',q='';
 if(chipsBox){chipsBox.innerHTML=cats.map(function(c){return '<button type="button" class="xnac-chip'+(c==='All'?' on':'')+'" data-cat="'+c+'">'+c.toUpperCase()+'</button>';}).join('');
  chipsBox.querySelectorAll('[data-cat]').forEach(function(b){b.addEventListener('click',function(){activeCat=b.getAttribute('data-cat');chipsBox.querySelectorAll('.xnac-chip').forEach(function(x){x.classList.toggle('on',x===b);});render();});});}
 if(search)search.addEventListener('input',function(){q=search.value.trim().toLowerCase();render();});
 function render(){
  var list=DRILLS.filter(function(d){if(activeCat!=='All'&&d.cat!==activeCat)return false;if(!q)return true;return (d.title+' '+d.cat+' '+d.steps.join(' ')).toLowerCase().indexOf(q)>=0;});
  var doneCount=DRILLS.filter(function(d){return done[d.id];}).length;
  if(prog)prog.textContent=doneCount+' of '+DRILLS.length+' drills done - progress is saved on this device only.';
  if(!list.length){host.innerHTML='<p style="text-align:center;color:#9d8cc2;padding:18px 0">No drills match that search.</p>';return;}
  var html='',lastCat=null;
  list.forEach(function(d){
   if(activeCat==='All'&&d.cat!==lastCat){html+='<div class="xnac-cat">'+esc(d.cat).toUpperCase()+'</div>';lastCat=d.cat;}
   html+='<div class="xnac-drill"><div class="top"><div class="xnac-ico">'+(ICONS[d.cat]||ICONS.Aim)+'</div><div><h3>'+esc(d.title)+'</h3><span class="mins">'+d.mins+' min drill</span></div>'+
    '<button type="button" class="xnac-done'+(done[d.id]?' yes':'')+'" data-drill="'+d.id+'">'+(done[d.id]?'DONE ✓':'MARK DONE')+'</button></div>'+
    '<ol>'+d.steps.map(function(s){return '<li>'+esc(s)+'</li>';}).join('')+'</ol></div>';
  });
  host.innerHTML=html;
  host.querySelectorAll('[data-drill]').forEach(function(b){b.addEventListener('click',function(){
   var id=b.getAttribute('data-drill');done[id]=!done[id];if(!done[id])delete done[id];
   try{localStorage.setItem('xr_academy',JSON.stringify(done));}catch(e){}
   render();
  });});
 }
 render();
}
if(document.readyState==='complete')boot();else window.addEventListener('load',boot);
})();
