const assert = require('node:assert/strict');
global.window = {};
require('../web/js/round-robin-algorithm.js');
const rank = window.TourmaRoundRobinAlgorithm.calculateStandings;
const names = ['Alpha', 'Beta', 'Gamma', 'Delta'];
function matches(games) {
    return Object.fromEntries(games.map(([a,b,x,y],i) => [i, {
        status:'COMPLETED', team1:{name:names[a],score:x}, team2:{name:names[b],score:y}
    }]));
}
// A cyclic three-way tie must fall back deterministically to alphabet, regardless of seed.
assert.deepEqual(rank(['Gamma','Beta','Alpha'], matches([[0,1,1,0],[1,2,1,0],[0,2,0,1]]))
    .map(r=>r.team), ['Alpha','Beta','Gamma']);
assert.deepEqual(rank([{name:'Beta',seed:1},{name:'Alpha',seed:99}], {}).map(r=>r.team), ['Alpha','Beta']);
// Exercise real complete schedules and verify head-to-head outranks total scored.
let seed=3, fixture;
const random=()=>{seed=(seed*1664525+1013904223)>>>0;return seed%5;};
for(let trial=0;trial<50000 && !fixture;trial++) {
    const games=[];
    for(let a=0;a<4;a++) for(let b=a+1;b<4;b++) games.push([a,b,random(),random()]);
    const rows=rank(names,matches(games));
    const a=rows.find(r=>r.team==='Alpha'), b=rows.find(r=>r.team==='Beta');
    if(a.points===b.points && a.goalDifference===b.goalDifference && a.goalsFor<b.goalsFor
        && games[0][2]>games[0][3] && rows.filter(r=>r.points===a.points && r.goalDifference===a.goalDifference).length===2) {
        assert.ok(rows.indexOf(a)<rows.indexOf(b)); fixture=games;
    }
}
assert.ok(fixture, 'Expected a complete schedule exercising head-to-head');
const rows=rank(names,matches(fixture),{winPoints:5,drawPoints:2,lossPoints:1});
for(const row of rows) assert.equal(row.points,row.won*5+row.drawn*2+row.lost);
console.log('RR frontend: cycle, alphabet, head-to-head, custom scoring passed');
