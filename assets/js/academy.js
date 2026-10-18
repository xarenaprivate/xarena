/* Training Academy (v7.15). Real guides; progress saved on this device (honest label). */
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
function boot(){
 var host=document.getElementById('academy-list');if(!host)return;
 var done={};try{done=JSON.parse(localStorage.getItem('xr_academy')||'{}');}catch(e){}
 var cats=[];DRILLS.forEach(function(d){if(cats.indexOf(d.cat)<0)cats.push(d.cat);});
 function render(){
  var doneCount=DRILLS.filter(function(d){return done[d.id];}).length;
  host.innerHTML='<p style="font-size:13px;color:var(--muted);margin-bottom:12px">'+doneCount+' of '+DRILLS.length+' drills done - progress is saved on this device only.</p>'+
   cats.map(function(c){
    return '<h3 style="margin:16px 0 8px;color:var(--gold2)">'+c+'</h3>'+DRILLS.filter(function(d){return d.cat===c;}).map(function(d){
     return '<div class="card" style="margin-bottom:10px"><div style="display:flex;align-items:center;gap:10px"><div style="flex:1"><h3 style="margin:0;font-size:16px">'+d.title+'</h3><p style="font-size:12.5px;color:var(--muted);margin:2px 0 0">'+d.mins+' min drill</p></div>'+
      '<button class="btn '+(done[d.id]?'btn-gold':'btn-ghost')+' btn-sm" data-drill="'+d.id+'">'+(done[d.id]?'Done ✓':'Mark done')+'</button></div>'+
      '<ol style="margin:10px 0 0;padding-left:20px;font-size:14px;line-height:1.6;color:var(--fg)">'+d.steps.map(function(s){return '<li>'+s+'</li>';}).join('')+'</ol></div>';
    }).join('');
   }).join('');
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
