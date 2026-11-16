const assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const source=fs.readFileSync('web/common/configure-tournament-format.jsp','utf8');
const start=source.indexOf('var savedType =',source.indexOf('// Restore format state on page load'));
const end=source.indexOf('// Apply Stage Locks UI',start);
assert.ok(start>=0 && end>start);
const body=source.slice(start,end);
function restore(type,format,config){
 const id='test', storage=new Map([['tourma_multi_config_'+id,JSON.stringify(config)]]);
 const state={};
 const context={tournamentId:id,storageKeyFormat:'tourma_format_'+id,localStorage:{getItem:k=>storage.get(k)||null},document:{getElementById:()=>null,querySelector:()=>null},window:{},
 selectStageType:t=>state.type=t,selectFormat:f=>state.format=f,
 selectStage1Format:f=>{state.stage1=f;state.format=f;},selectStage2Format:f=>state.stage2=f};
 const code=body.replace('<%= currentType %>',type).replace('<%= currentFormat %>',format);
 vm.runInNewContext(code,context);return state;
}
for(const kind of ['standalone','Series']){
 for(const format of ['SINGLE_ELIMINATION','DOUBLE_ELIMINATION','ROUND_ROBIN']){
  const result=restore('SINGLE_STAGE',format,{stage1Format:'GROUP_STAGE',stage2Format:'SINGLE_ELIMINATION',stage1Config:{},stage2Config:{}});
  assert.equal(result.format,format,kind+' single-stage choice overwritten by stale multi-stage cache');
  assert.equal(result.stage1,undefined);
 }
}
const multi=restore('MULTI_STAGE','DOUBLE_ELIMINATION',{stage1Format:'DOUBLE_ELIMINATION',stage2Format:'SINGLE_ELIMINATION',stage1Config:{},stage2Config:{}});
assert.equal(multi.stage1,'DOUBLE_ELIMINATION');assert.equal(multi.stage2,'SINGLE_ELIMINATION');
console.log('PASS: shared standalone/Series editor preserves SE, DE and RR despite stale GS cache; multi-stage restore retained');
