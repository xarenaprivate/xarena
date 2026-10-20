/* Fan Voting (v7.12). Real polls only: active_polls / cast_vote / poll_results. */
(function(){
'use strict';
function esc(s){return String(s==null?'':s).replace(/[&<>"']/g,function(c){return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c];});}
var IC_VOTE='<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#ffd783" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M4 8h16v12H4Z"/><path d="M9 8V5a3 3 0 0 1 6 0v3"/><path d="M9 14l2 2 4-4"/></svg>';
function boot(){
 if(!window.xrApi)return;
 var api=window.xrApi;
 api.uRpc('active_polls',{}).then(function(polls){
  var anchor=document.querySelector('main')||document.body;
  var root=document.createElement('div');root.id='vote-nova';
  var hero=document.getElementById('today')||document.querySelector('.hero')||anchor.firstChild;
  if(hero&&hero.nextSibling)anchor.insertBefore(root,hero.nextSibling);else anchor.insertBefore(root,anchor.firstChild);
  if(!Array.isArray(polls)||!polls.length){root.innerHTML='<div class="xvt"><div class="xvt-head">'+IC_VOTE+'<h3>Fan Vote</h3></div><p class="xvt-empty">No vote is open right now - the next one lands here.</p></div>';return;}
  var poll=polls[0],res=null;
  function render(){
   var total=res&&res.total;
   root.innerHTML='<div class="xvt"><div class="xvt-head">'+IC_VOTE+'<h3>Fan Vote</h3><span class="xvt-live">LIVE</span></div>'+
    '<p class="xvt-q">'+esc(res.poll.title)+'</p>'+(res.poll.description?'<p class="xvt-d">'+esc(res.poll.description)+'</p>':'')+
    res.options.map(function(o){
     var pct=(total&&o.votes!=null)?Math.round(100*o.votes/Math.max(total,1)):null;
     var mine=res.my_vote===o.id;
     return '<button class="xvt-opt'+(mine?' mine':'')+'" data-opt="'+o.id+'" '+(res.my_vote?'disabled':'')+'>'+(pct!==null?'<span class="bar" style="width:'+pct+'%"></span>':'')+'<span class="row"><span>'+esc(o.label)+'</span>'+(pct!==null?'<span class="pct">'+pct+'%</span>':'')+'</span></button>';
    }).join('')+
    '<p class="xvt-foot">'+(total!=null?total+' vote'+(total===1?'':'s')+' so far':'Results hidden until voting closes')+' · one vote per player</p></div>';
   root.querySelectorAll('[data-opt]').forEach(function(b){b.addEventListener('click',function(){
    if(res.my_vote)return;b.disabled=true;
    api.uRpc('cast_vote',{p_poll:poll.id,p_option:b.getAttribute('data-opt')}).then(function(r){
     if(r&&r.ok){if(window.xrSfx)xrSfx.play('win');load();}
     else{b.disabled=false;if(window.xrToast)xrToast((r&&r.error)||'Could not vote');}
    }).catch(function(){b.disabled=false;});
   });});
  }
  function load(){api.uRpc('poll_results',{p_poll:poll.id}).then(function(r){if(r&&r.ok){res=r;render();}});}
  load();
 }).catch(function(){});
}
if(document.readyState==='complete')boot();else window.addEventListener('load',boot);
})();
