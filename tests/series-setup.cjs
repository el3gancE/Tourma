const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict');
const values={targetTeamCount:'4',selectedFormat:'GROUP_STAGE',selectedTournamentType:'MULTI_STAGE'};
const fields=Object.fromEntries(Object.entries(values).map(([id,value])=>[id,{value}]));
const settings=[['format','GROUP_STAGE'],['tournamentType','MULTI_STAGE'],['stage1Format','GROUP_STAGE'],['stage2Format','SINGLE_ELIMINATION'],['multiConfig','{"stage1Config":{"totalAdvanceCount":4}}'],['copiedFrom','sibling']].map(([name,value])=>({name,value}));
const editor={querySelectorAll(){return settings;}};
let submitHandler,valid=true,checks=0,saves=0,count=4;
const storage=new Map();
const window={location:{href:'http://localhost/Tourma/rolling/tournament-teams?id=child'},currentSubTourneyId:'child',
 validateAndSubmitFormat(){checks++;return valid;},persistFormatSelection(){saves++;},proceedToNextStep(){}};
const document={getElementById(id){return id==='configureFormatForm'?editor:fields[id];},
 querySelectorAll(){return Array.from({length:count},(_,i)=>({querySelector(){return {textContent:'Team '+i};}}));},
 addEventListener(type,handler){submitHandler=handler;},createElement(){return {};}};
vm.runInNewContext(fs.readFileSync('web/js/rolling/series-setup.js','utf8'),{window,document,localStorage:{setItem(k,v){storage.set(k,v);}},URL,Array,Number,JSON,alert(){}});
function form(id){const inputs=new Map([['excludedTournamentIds',{value:'older-sibling'}]]);return {id,action:'http://localhost/Tourma/rolling/tournament-teams',inputs,
 querySelector(selector){return inputs.get(selector.match(/name="([^"]+)/)[1]);},appendChild(input){inputs.set(input.name,input);}};}
assert.equal(window.prepareSeriesSetup({}),true);assert.equal(window.targetTeamCount,4);
assert.equal(window.tourneyFormat,'GROUP_STAGE');assert.equal(JSON.parse(storage.get('tourma_teams_child')).length,4);
let next=form('saveOrderAndProceedForm'),blocked=false;
submitHandler({target:next,preventDefault(){blocked=true;}});
assert.equal(blocked,false);assert.equal(next.inputs.get('stage2Format').value,'SINGLE_ELIMINATION');
assert.equal(next.inputs.get('multiConfig').value,settings[4].value);assert.equal(next.inputs.get('copiedFrom').value,'sibling');
assert.equal(next.inputs.get('excludedTournamentIds').value,'older-sibling');
valid=false;blocked=false;submitHandler({target:next,preventDefault(){blocked=true;}});assert.equal(blocked,true);
const before=checks;let add=form('bulkAddForm');submitHandler({target:add,preventDefault(){throw Error('Adding teams must allow incomplete advance count');}});
assert.equal(checks,before);assert.equal(add.inputs.get('selectedTournamentType').value,'MULTI_STAGE');
count=1;assert.equal(window.prepareSeriesSetup({}),false);
console.log('PASS: Series stage configuration/copy fields, partial roster actions, partner exclusions, continue validation');
