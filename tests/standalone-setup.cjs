const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const fields = new Map();
function field(id, value = '', name = id) {
    const element = {id, name, value, style:{}, classList:{add(){},remove(){},toggle(){}}, focus(){}};
    fields.set(id, element); return element;
}
const targets = new Map();
let submissions = 0;
const form = {querySelector(selector) {return targets.get(selector.match(/name="([^"]+)/)[1]);},
    appendChild(input) {targets.set(input.name,input);},requestSubmit(){submissions++;}};
const hidden = ['selectedTournamentType','selectedFormat','stage1Format','stage2Format','detailConfig','advancingSeatsCount','targetTeamCount','copiedFrom'].map(id=>field(id));
hidden.forEach(e => e.type = 'hidden');
Object.assign(fields.get('selectedTournamentType'),{name:'tournamentType'});
Object.assign(fields.get('selectedFormat'),{name:'format'});
Object.assign(fields.get('detailConfig'),{name:'multiConfig'});
const editor = {querySelectorAll(){return hidden;}};
fields.set('configureTeamsForm',form); fields.set('configureFormatForm',editor);
const store = new Map();
const document = {getElementById(id){return fields.get(id) || null;},
    querySelector(selector){if(selector.includes('name='))return fields.get(selector.match(/name="([^"]+)/)[1]) || null;return null;},
    querySelectorAll(){return [];}, createElement(){return {};}};
const window = {addEventListener(){}};
const context = vm.createContext({window,document,localStorage:{getItem(k){return store.get(k)||null;},setItem(k,v){store.set(k,String(v));},removeItem(k){store.delete(k);}},alert(){},console,Number,JSON,Array});
const jsp = fs.readFileSync('web/common/configure-tournament-format.jsp','utf8');
const inline = [...jsp.matchAll(/<script>([\s\S]*?)<\/script>/g)].map(m=>m[1]).join('\n')
    .replace(/<%=([\s\S]*?)%>/g,(_, expression)=>expression.includes('i < availableTournaments.size()') ? '' : '0')
    .replace(/<%[\s\S]*?%>/g,'');
vm.runInContext(inline,context);
// Browser globals share window; expose the actual configuration functions to the bridge.
window.persistFormatSelection = context.persistFormatSelection;
let teamValid = true, formatValid = true, teamChecks = 0, formatChecks = 0;
window.prepareFormSubmission = ()=>{teamChecks++;return teamValid;};
window.validateAndSubmitFormat = ()=>{formatChecks++;return formatValid;};
context.tournamentId = 'test';
vm.runInContext(fs.readFileSync('web/js/standalone-setup.js','utf8'),context);
function set(id,value){fields.get(id).value=value;}
set('selectedTournamentType','SINGLE_STAGE');set('selectedFormat','ROUND_ROBIN');
field('winPoints','5');field('drawPoints','2');field('lossPoints','0');field('legsCount','2');
assert.equal(window.prepareStandaloneSetup({}),true);
assert.equal(targets.get('format').value,'ROUND_ROBIN');
let config=JSON.parse(targets.get('multiConfig').value);
assert.equal(config.stage1Config.winPoints,5);assert.equal(config.stage1Config.legsCount,2);
set('selectedTournamentType','MULTI_STAGE');set('stage1Format','GROUP_STAGE');set('stage2Format','SINGLE_ELIMINATION');
field('stage1AdvanceGR','4');field('stage1WinPointsGR','3');field('stage1DrawPointsGR','1');field('stage1LossPointsGR','0');field('stage1LegsCountGR','2');
set('targetTeamCount','16');set('copiedFrom','source');
assert.equal(window.prepareStandaloneSetup({}),true);
config=JSON.parse(targets.get('multiConfig').value);
assert.equal(targets.get('format').value,'GROUP_STAGE');
assert.equal(config.stage2Format,'SINGLE_ELIMINATION');
assert.equal(config.stage1Config.totalAdvanceCount,4);
assert.equal(config.stage1Config.legsCount,2);
assert.equal(targets.get('advancingSeatsCount').value,4);
assert.equal(window.targetTeamCount,16);assert.equal(targets.get('copiedFrom').value,'source');
teamValid=false;const previousChecks=formatChecks;assert.equal(window.prepareStandaloneSetup({}),false);assert.equal(formatChecks,previousChecks);
teamValid=true;formatValid=false;assert.equal(window.prepareStandaloneSetup({}),false);
window.submitStandaloneSetup();assert.equal(submissions,1);
assert.equal(teamChecks,4);
console.log('PASS: actual format serialization, RR rules, GS-to-SE stages/cut, copy constraints, validation and confirmation submission');
// Exercise the existing format validator itself after merging the screens.
store.set('tourma_teams_test', JSON.stringify(['A','B','C','D','E','F','G','H']));
let prevented = 0;
const event = {preventDefault(){prevented++;}};
set('stage1AdvanceGR','8');
assert.equal(context.validateAndSubmitFormat(event),false);
assert.ok(prevented > 0);
set('stage1AdvanceGR','4');
assert.equal(context.validateAndSubmitFormat(event),true);
context.hasStage1Matches = true;
context.originalType = 'SINGLE_STAGE';context.originalFormat = 'ROUND_ROBIN';
assert.equal(context.validateAndSubmitFormat(event),false);
console.log('PASS: actual cut validation and existing-schedule format lock remain active');
// The copy dialog must wait for server persistence before reporting success.
(async function () {
    context.isRollingSubTournament = true;
    context.selectStageType = value => set('selectedTournamentType',value);
    context.selectStage1Format = value => {set('stage1Format',value);set('selectedFormat',value);};
    context.selectStage2Format = value => set('stage2Format',value);
    context.hasStage1Matches = false;
    context.URLSearchParams = URLSearchParams;
    window.availableTournamentsToCopy = [{id:'copy-source',name:'Source',teamCount:8,tournamentType:'MULTI_STAGE',
        multiStageConfig:{stage1Format:'GROUP_STAGE',stage2Format:'SINGLE_ELIMINATION',stage1Config:{totalAdvanceCount:4,winPoints:5,drawPoints:2,lossPoints:0,legsCount:2}}}];
    let closed=0,resolveSave,alerts=0;
    context.closeCopyConfigModal=()=>{closed++;};context.alert=()=>{alerts++;};
    context.fetch=(url,options)=>{
        assert.ok(url.endsWith('/rolling/tournament-teams'));
        const body=new URLSearchParams(options.body);
        assert.equal(body.get('action'),'copyConfiguration');assert.equal(body.get('sourceTournamentId'),'copy-source');
        assert.equal(JSON.parse(body.get('multiConfig')).stage1Config.winPoints,5);
        return new Promise(resolve=>{resolveSave=resolve;});
    };
    const copy=context.applyTournamentConfig('copy-source');
    assert.equal(closed,0);resolveSave({ok:true,json:async()=>({status:'success'})});await copy;
    assert.equal(closed,1);assert.equal(fields.get('copiedFrom').value,'copy-source');
    const failed=context.applyTournamentConfig('copy-source');
    resolveSave({ok:false,json:async()=>({status:'error',message:'Locked'})});await failed;
    assert.equal(closed,1);assert.equal(alerts,1);
    console.log('PASS: copy waits for persistence; failure stays visible and reports error');
})().catch(error=>{console.error(error);process.exitCode=1;});
