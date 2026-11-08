const fs=require('fs'),vm=require('vm'),assert=require('assert/strict');
const source=fs.readFileSync('assets/js/app.js','utf8').split('/* Hybrid OS')[0];
async function run(query,setting,opts={}){
 let reads=[];const ctx={URLSearchParams,Promise,Number,Object,location:{search:query},encodeURIComponent,window:{},fetch:async url=>{reads.push(url);return {ok:!opts.fail,json:async()=>url.includes('site_settings')?[{value:{id:setting}}]:opts.missing?[]:[{id:opts.wrong?'wrong':new URL(url).searchParams.get('id').slice(3),name:'Xarena Season 2',entry_cost_ap:opts.cost??0,reg_open:!opts.closed,status:'upcoming',slots:12}]};}};
 vm.createContext(ctx);vm.runInContext(source,ctx);await ctx.window.xrRegReady;let quote,error;try{quote=await ctx.window.xrRegQuote;}catch(e){error=e;}return{ctx,reads,quote,error};
}
(async()=>{
 let x=await run('?tournament=XR%20-%20S2','xr-5oar');assert.equal(x.ctx.xrRegT(),'XR - S2');assert(!x.reads.some(u=>u.includes('site_settings')));assert.equal(x.quote.id,'XR - S2');assert.equal(x.quote.cost,0);
 assert.equal(x.ctx.xrRegistrationUrl('XR - S2'),'tournaments.html?tournament=XR%20-%20S2#register');assert.equal(x.ctx.xrRegistrationUrl('a&b#c'),'tournaments.html?tournament=a%26b%23c#register');
 x=await run('','XR - S2');assert.equal(x.quote.id,'XR - S2');x=await run('','xr-5oar');assert.equal(x.quote.id,'xr-5oar');
 for(const query of ['?tournament=','?tournament=%0A']){x=await run(query,'xr-5oar');assert.equal(x.ctx.xrRegT(),null);assert(x.error);assert.equal(x.reads.length,0);}
 for(const opts of [{fail:true},{wrong:true},{missing:true},{cost:-1},{cost:'invalid'}]){x=await run('?tournament=XR%20-%20S2','xr-5oar',opts);assert(x.error);assert.equal(x.quote,undefined);}
 x=await run('?tournament=XR%20-%20S2','xr-5oar',{cost:25});assert.equal(x.quote.cost,25);x=await run('?tournament=XR%20-%20S2','xr-5oar',{closed:true});assert.equal(x.quote.open,false);
 console.log('PASS: exact opaque selection, encoded links/filters, global fallback, invalid selection, failed/wrong/missing event and invalid cost failclosed, free/AP/closed quote');
})();
