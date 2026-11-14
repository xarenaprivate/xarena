/* XARENA live AP features. One refresh every30s while visible, never a new AP action on refresh. */
(function(){'use strict';
const e=s=>String(s==null?'':s).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const api=()=>window.xrApi;
const uuid=()=>crypto.randomUUID();
let state=null,busy=false,pending=null,me=null,roomInfo=null;
const root=document.getElementById('xr-spend-live')||document.getElementById('xr-spend-clan');if(!root)return;
const isLive=root.id==='xr-spend-live';
function msg(text,bad){const x=document.getElementById('xr-spend-msg');if(x){x.textContent=text;x.style.color=bad?'#ff9e9e':'#a6f3c8';}}
function guard(r){if(!r||r.ok===false)throw Error(r&&r.error||'Could not complete.');return r;}
function lock(on){busy=on;root.querySelectorAll('button').forEach(b=>b.disabled=on||b.dataset.off==='true');}
async function action(name,args,key){if(busy)return;lock(true);msg('Working...',false);try{const r=guard(await api().uRpc(name,args));pending=null;await load(true);msg(r.already?'Already completed. No duplicate charge.':'Done.',false);}catch(err){msg(err.message+'. If the connection failed, tap the same action to retry with the same request ID.',true);}finally{lock(false);}}
function request(key,args){if(!pending||pending.key!==key)pending={key,id:uuid()};return Object.assign({},args,{p_request:pending.id});}
async function load(force){if(busy&&!force)return;try{
 me=await api().user();const settings=await api().uRpc('xr_spend_status',{});
 if(!settings.release_authorized){root.hidden=true;return;}root.hidden=false;
 let wallet=null;if(me)wallet=await api().uRpc('xr_wallet_balance',{});
 const balance=wallet?'Verified earned AP: '+e(wallet.earned_spendable)+' | Gifted AP: '+e(wallet.granted):'Log in to use your AP.';
 const walletHtml='<p class="xsp-muted">'+balance+'. AP has no cash value.</p>';
 if(isLive){
 state=await api().uRpc('xr_spend_live_get',{});if(!state.ok)throw Error(state.error||'Unavailable');
 let html='<h2>Live rewards</h2>'+walletHtml;
 if(!me)html+='<p><a href="account.html">Log in</a> to send gifts, predict or comment.</p>';
 if(settings.gifts_enabled){html+='<section><h3>Send a gift</h3><p>Receiver: '+e(state.host_name||'Not set')+'. Received AP is gifted AP, not new earned AP.</p><div class="xsp-buttons">';
 for(const gift of settings.gift_catalog||[])html+='<button type="button" data-gift="'+e(gift.key)+'"'+(!me||!state.host_available?' disabled data-off="true"':'')+'>'+e(gift.key[0].toUpperCase()+gift.key.slice(1))+' · '+e(gift.ap)+' AP</button>';
 html+='</div>'+(!state.host_available?'<p class="xsp-muted">Gifts are unavailable for this account or stream.</p>':'')+'<div class="xsp-gifts">'+(state.gifts||[]).map(g=>'<p'+(g.highlight_until&&Date.parse(g.highlight_until)>Date.now()?' class="xsp-crown"':'')+'>'+e(g.sender)+' sent '+e(g.gift_key)+' · '+e(g.amount)+' AP</p>').join('')+'</div></section>';}
 const p=state.prediction;
 if(settings.predictions_enabled){html+='<section><h3>Booyah prediction</h3><p>Free entry. Correct: 200 AP. Incorrect: 10 AP. One locked choice. Admin confirms the finished match result.</p>';
 if(!p)html+='<p class="xsp-muted">No prediction is open. The admin opens one before a match.</p>';
 else{html+='<p>Status: '+e(p.status)+' | Closes: '+e(new Date(p.closes_at).toLocaleString())+'</p>';
 if(p.my_award!=null)html+='<p>Your award: '+e(p.my_award)+' AP</p>';
 if(p.my_choice)html+='<p>Your locked choice: '+e((p.choices||[]).find(c=>c.registration_id===p.my_choice)?.squad||'Selected squad')+'</p>';
 else if(p.status==='open')html+='<select id="xsp-choice"><option value="">Choose a squad</option>'+(p.choices||[]).map(c=>'<option value="'+e(c.registration_id)+'">'+e(c.squad)+'</option>').join('')+'</select><button type="button" id="xsp-predict"'+(!me?' disabled data-off="true"':'')+'>Lock free prediction</button>';}
 html+='</section>';}
 if(settings.drops_enabled){const d=state.drop;html+='<section><h3>Live Drop</h3><p>First 10 unique eligible live commenters receive 50 AP each. This is based on comments, not verified watch time.</p>'+(d?'<p>'+e(d.status)+' | '+e(d.winners)+' / 10 awarded'+(d.my_award!=null?' | You earned '+e(d.my_award)+' AP':'')+'</p>':'<p class="xsp-muted">No drop is open. Wait for an admin to open one.</p>')+'</section>';}
 html+='<section><h3>Live comments</h3><div id="xsp-comments" class="xsp-comments"></div><label for="xsp-comment">Your comment</label><input id="xsp-comment" maxlength="200" placeholder="BOOYAH or GG WP"><button type="button" id="xsp-comment-send"'+(!me?' disabled data-off="true"':'')+'>Send comment</button></section><p id="xr-spend-msg" role="status" aria-live="polite"></p>';
 root.innerHTML=html;
 root.querySelectorAll('[data-gift]').forEach(b=>b.onclick=()=>{const g=settings.gift_catalog.find(x=>x.key===b.dataset.gift);if(confirm('Send '+g.key+' to '+state.host_name+' for '+g.ap+' verified earned AP?'))action('xr_live_gift_send',request('gift:'+state.video_id+':'+g.key,{p_video:state.video_id,p_gift:g.key}));});
 const pred=document.getElementById('xsp-predict');if(pred)pred.onclick=()=>{const c=document.getElementById('xsp-choice').value;if(!c)return msg('Choose a squad.',true);const n=p.choices.find(x=>x.registration_id===c)?.squad;if(confirm('Lock '+n+'? Entry is free. You cannot change this choice.'))action('xr_prediction_choose',{p_pool:p.id,p_registration:c});};
 const send=document.getElementById('xsp-comment-send');if(send)send.onclick=async()=>{const text=document.getElementById('xsp-comment').value.trim();if(!text||busy)return;lock(true);try{guard(await api().uRpc('xr_live_thread_post',{p_tournament:state.tournament_id,p_body:text}));document.getElementById('xsp-comment').value='';await comments();msg('Comment sent.',false);}catch(err){msg(err.message,true);}finally{lock(false);}};
 await comments();
 }else{
 if(!settings.clan_fund_enabled){root.hidden=true;return;}
 if(!me){root.innerHTML='<h2>Clan fund</h2><p><a href="account.html">Log in</a> to open your clan fund.</p>';return;}
 roomInfo=await api().uRpc('xr_room_growth',{});if(!roomInfo.ok){root.innerHTML='<h2>Clan fund</h2><p>Join a clan to contribute or receive rewards.</p>';return;}
 const squad=roomInfo.squad,fund=guard(await api().uRpc('xr_clan_fund_get',{p_squad:squad.id}));state={squad,fund};
 root.innerHTML='<h2>Clan fund · '+e(squad.name)+'</h2>'+walletHtml+'<p class="xsp-total">'+e(fund.balance)+' AP</p><p>Contributions remain in this clan if you leave. No cash value. Only the current clan leader can give AP rewards to current clan members.</p><div class="xsp-buttons"><label for="xsp-contribution">Contribute verified earned AP</label><input id="xsp-contribution" type="number" min="1" step="1" placeholder="AP amount"><button type="button" id="xsp-contribute">Contribute</button></div>'+(fund.can_reward?'<h3>Give a clan reward</h3><select id="xsp-recipient"><option value="">Choose current member</option>'+(squad.members||[]).map(m=>'<option value="'+e(m.id)+'">'+e(m.name)+'</option>').join('')+'</select><input id="xsp-reward-amount" type="number" min="1" step="1" placeholder="AP amount"><button type="button" id="xsp-reward">Give AP reward</button><p class="xsp-muted">The member receives gifted AP, not new earned AP.</p>':'')+'<h3>Contributors</h3>'+(fund.contributors||[]).map(c=>'<p>'+e(c.name)+' · '+e(c.ap)+' AP</p>').join('')+'<p id="xr-spend-msg" role="status" aria-live="polite"></p>';
 document.getElementById('xsp-contribute').onclick=()=>{const n=Number(document.getElementById('xsp-contribution').value);if(!Number.isSafeInteger(n)||n<1)return msg('Enter a positive whole AP amount.',true);if(confirm('Contribute '+n+' earned AP to '+squad.name+'? These AP stay in the clan even if you leave.'))action('xr_clan_fund_contribute',request('fund:'+squad.id+':'+n,{p_squad:squad.id,p_amount:n}));};
 const reward=document.getElementById('xsp-reward');if(reward)reward.onclick=()=>{const id=document.getElementById('xsp-recipient').value,n=Number(document.getElementById('xsp-reward-amount').value);if(!id||!Number.isSafeInteger(n)||n<1)return msg('Choose a member and positive whole AP amount.',true);const member=squad.members.find(m=>m.id===id);if(confirm('Give '+n+' clan AP to '+member.name+' as gifted AP?'))action('xr_clan_fund_reward',request('reward:'+squad.id+':'+id+':'+n,{p_squad:squad.id,p_recipient:id,p_amount:n}));};
 }
 }catch(err){root.hidden=false;root.innerHTML='<h2>AP rewards</h2><p>Could not load. Refresh to try again.</p><p class="xsp-muted">'+e(err.message)+'</p>';}}
async function comments(){if(!isLive||!state?.tournament_id)return;const r=await api().uRpc('xr_live_thread_list',{p_tournament:state.tournament_id,p_limit:20});const box=document.getElementById('xsp-comments');if(box)box.innerHTML=(r.posts||[]).slice().reverse().map(m=>'<p><b>'+e(m.name)+'</b> '+e(m.body)+'</p>').join('')||'<p class="xsp-muted">No live comments yet.</p>';}
window.addEventListener('load',load);setInterval(()=>{if(!document.hidden&&!busy&&!root.contains(document.activeElement))load();},30000);
})();
