const assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const source=fs.readFileSync(require('path').join(__dirname,'../assets/js/today.js'),'utf8');
const register=source.slice(source.indexOf('  function setRegister('),source.indexOf('  function setMatch('));
for(const mine of [null,{status:'pending'}]){
  const box={innerHTML:''};const scope={T:{t:{id:'XR - S2',name:'Season 2',slots:12,reg_open:true}},mine,el:()=>box,esc:String};
  vm.runInNewContext(register+';setRegister(2)',scope);
  assert.match(box.innerHTML,/href="tournaments\.html\?tournament=XR%20-%20S2#register"/);
}
const filter=source.match(/var up = ([\s\S]*?);\n        setMatch/)[1];
const scope={x:[null,null,[{status:'cancelled'},{status:'finished'},{status:'completed'},{status:'upcoming',title:'real upcoming'}]]};
const result=vm.runInNewContext(filter,scope);
assert.equal(result.length,1);assert.equal(result[0].title,'real upcoming');
console.log('PASS Today selected-event links for guest/registered and cancelled/finished match exclusion');
