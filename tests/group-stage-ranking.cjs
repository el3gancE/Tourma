const assert=require('node:assert/strict');
global.window={};
require('../web/js/group-standing.js');
require('../web/js/round-robin-algorithm.js');
const gs=window.TourmaGroupStanding;
const names=['Alpha','Beta','Gamma','Delta'];
gs.groups={A:names.map((name,i)=>({id:String(i),name,seed:4-i})),
    B:[{id:'B1',name:'Zulu',seed:1},{id:'B2',name:'Echo',seed:2}]};
gs.rules={winPoints:5,drawPoints:2,lossPoints:1,advanceCount:2};
let seed=3;
const random=()=>{seed=(seed*1664525+1013904223)>>>0;return seed%5;};
// Compare independently implemented GS and RR rankings over many complete schedules.
for(let trial=0;trial<200;trial++) {
    const games=[];
    for(let a=0;a<4;a++) for(let b=a+1;b<4;b++) games.push({
        status:'COMPLETED',team1:{id:String(a),name:names[a],score:random()},
        team2:{id:String(b),name:names[b],score:random()}
    });
    gs.groupMatches={A:games,B:[]};
    const result=gs.calculateAllGroupStandings();
    const rr=window.TourmaRoundRobinAlgorithm.calculateStandings(names,games,gs.rules);
    assert.deepEqual(result.A.map(r=>r.name),rr.map(r=>r.team));
    assert.deepEqual(result.B.map(r=>r.name),['Echo','Zulu']);
    for(const row of result.A) assert.equal(row.pts,row.w*5+row.d*2+row.l);
}
gs.groupMatches={A:[
    {status:'COMPLETED',team1:{id:'0',score:1},team2:{id:'1',score:0}},
    {status:'COMPLETED',team1:{id:'1',score:1},team2:{id:'2',score:0}},
    {status:'COMPLETED',team1:{id:'2',score:1},team2:{id:'0',score:0}}
],B:[]};
assert.deepEqual(gs.calculateAllGroupStandings().A.slice(0,3).map(r=>r.name),['Alpha','Beta','Gamma']);
// Inspect qualification before physical bracket pairing: one direct place per group,
// followed by the best remaining team, using the same alphabet fallback.
gs.pairCrossoverForKnockout=teams=>teams;
const qualified=gs.getQualifiedTeamsCrossover('test',gs.groups,gs.groupMatches,3);
assert.deepEqual(qualified.map(r=>r.name),['Alpha','Echo','Beta']);
assert.deepEqual(qualified.map(r=>r.isWildcard),[false,false,true]);
console.log('GS: 200 complete schedules, group isolation, custom scoring and cyclic tie passed');
